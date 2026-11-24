/*
 * Onedrive 3.0 Extension for Krista
 * Copyright (C) 2025 Krista Software
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package app.krista.extensions.essentials.cloudfiles.onedrive.impl;

import app.krista.extension.executor.ErrorBuilder;
import app.krista.extension.executor.ExtensionResponse;
import app.krista.extension.executor.ExtensionResponseBuilder;
import app.krista.extensions.essentials.cloudfiles.onedrive.catalog.FileHandlers.TelemetryHelper;
import app.krista.extensions.essentials.cloudfiles.onedrive.catalog.validators.Validator;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.connectors.GraphServiceClientProvider;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.connectors.GraphServiceClientProviderFactory;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.util.*;
import com.microsoft.graph.http.GraphServiceException;
import com.microsoft.graph.models.DriveItem;
import com.microsoft.graph.models.DriveItemVersion;
import com.microsoft.graph.requests.DriveItemDeltaCollectionPage;
import com.microsoft.graph.requests.DriveItemVersionCollectionPage;
import com.microsoft.graph.requests.UserRequestBuilder;
import org.jvnet.hk2.annotations.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * Service class for handling file version operations in OneDrive.
 * This class provides functionality to retrieve version history of files,
 * track file changes, and validate file paths.
 */
@Service
public class FileVersionOperations {

    public static final String UPDATED = "Updated";
    public static final String CREATED = "Created";
    private static final Logger LOGGER = LoggerFactory.getLogger(FileVersionOperations.class);
    private final GraphServiceClientProviderFactory providerFactory;
    private final GraphServiceClientProvider provider;
    private final ValidationTypeUtil validationTypeUtil;
    private final TelemetryHelper telemetryHelper;

    @Inject
    public FileVersionOperations(GraphServiceClientProviderFactory factory, ValidationTypeUtil validationTypeUtil, TelemetryHelper telemetryHelper) {
        this.providerFactory = factory;
        this.provider = null;
        this.validationTypeUtil = validationTypeUtil;
        this.telemetryHelper = telemetryHelper;
    }

    public FileVersionOperations(GraphServiceClientProvider provider, ValidationTypeUtil validationTypeUtil, TelemetryHelper telemetryHelper) {
        this.providerFactory = null;
        this.provider = provider;
        this.validationTypeUtil = validationTypeUtil;
        this.telemetryHelper = telemetryHelper;
    }

    private GraphServiceClientProvider createProvider() {
        return provider != null ? provider : providerFactory.create();
    }

    /**
     * Retrieves version history for files in a specified folder path.
     * This method checks if the source path exists, fetches version changes for files,
     * and handles re-entry validation if the path is invalid.
     *
     * @param sourcePath  The path to the folder to check for file versions
     * @param search      Optional search term to filter files by name
     * @param since       Optional timestamp to filter files modified after this time
     * @param isReentered Flag indicating if this is a re-entered request after validation
     * @return ExtensionResponse containing version history or validation error
     */
    public ExtensionResponse getVersionHistory(String sourcePath, String search, Long since, boolean isReentered) {
        LOGGER.info("getVersionHistory - Starting | sourcePath: {}, search: {}, since: {}, isReentered: {}",
                sourcePath, search, since, isReentered);

        Map<String, Object> payload = new HashMap<>();
        long startTime = System.currentTimeMillis();
        telemetryHelper.incrementCount("OneDrive.getVersionHistory");

        try {
            if (isFileExist(sourcePath)) {
                fetchFileVersionChange(sourcePath, search, since, payload);
                telemetryHelper.recordSuccess("OneDrive.getVersionHistory", startTime,
                        TelemetryHelper.safeTagMap("sourcePath", sourcePath));
            } else {
                if (!isReentered) {
                    LOGGER.debug("getVersionHistory - First attempt, prompting for re-entry");
                    telemetryHelper.recordRetryPrompted("OneDrive.getVersionHistory", startTime,
                            TelemetryHelper.safeTagMap("sourcePath", sourcePath, "reason", "file_not_found"));
                    return reenterValidate(sourcePath, search, since);
                }

                LOGGER.error("getVersionHistory - File does not exist even after re-entry: {}", sourcePath);
                telemetryHelper.recordValidationError("OneDrive.getVersionHistory", startTime,
                        "File does not exist after re-entry",
                        TelemetryHelper.safeTagMap("sourcePath", sourcePath, "isReentered", String.valueOf(isReentered)));

                LOGGER.debug("getVersionHistory - Setting error response for invalid folder path");
                ResponseUtil.errorResponse(false, Constants.PLEASE_PROVIDE_VALID_FOLDER_PATH, payload);
            }
        } catch (Exception e) {
            LOGGER.error("getVersionHistory - Unexpected error: {}", e.getMessage(), e);
            telemetryHelper.recordError("OneDrive.getVersionHistory", startTime, e,
                    TelemetryHelper.safeTagMap(
                            "sourcePath", sourcePath,
                            "search", String.valueOf(search),
                            "since", String.valueOf(since),
                            "error_type", e.getClass().getSimpleName()
                    ));
            ResponseUtil.errorResponse(false, "Unexpected error occurred while fetching version history", payload);
        }

        return new ExtensionResponseBuilder()
                .success(payload, null, null)
                .build();
    }


    /**
     * Fetches file version changes for a specific source path.
     * This method retrieves all files in the drive and filters them based on the source path,
     * then processes each file to determine if it has been updated or created.
     *
     * @param sourcePath The path to filter files by
     * @param search     Optional search term to filter files by name
     * @param since      Optional timestamp to filter files modified after this time
     * @param payload    The map to populate with the results
     */
    private void fetchFileVersionChange(String sourcePath, String search, Long since, Map<String, Object> payload) {
        try {
            DriveItemDeltaCollectionPage page = getUserRequestBuilder().drive().root().delta().buildRequest().get();
            List<Map<String, Object>> result = new ArrayList<>();
            while (page != null && !page.getCurrentPage().isEmpty()) {
                processFileVersion(sourcePath, search, since, page, result);
                if (page.getNextPage() != null) {
                    page = page.getNextPage().buildRequest().get();
                } else {
                    ResponseUtil.changesSinceResponse(true, "Files Content", result, payload);
                    break;
                }
            }
        } catch (GraphServiceException | IllegalArgumentException cause) {
            LOGGER.info(cause.getMessage(), cause);
            ResponseUtil.errorResponse(false, Constants.FILE_NOT_FOUND, payload);
        }
    }

    /**
     * Processes a page of drive items to find file versions.
     * This method filters drive items based on the source path, search term, and timestamp,
     * then processes each matching item to determine its version status.
     *
     * @param sourcePath The path to filter files by
     * @param search     Optional search term to filter files by name
     * @param since      Optional timestamp to filter files modified after this time
     * @param page       The page of drive items to process
     * @param result     The list to populate with the results
     */
    private void processFileVersion(String sourcePath, String search, Long since, DriveItemDeltaCollectionPage page, List<Map<String, Object>> result) {
        for (DriveItem driveInPage : page.getCurrentPage()) {
            if (driveInPage.name != null && !driveInPage.name.equals("root")
                    && driveInPage.parentReference != null
                    && driveInPage.parentReference.path != null
                    && driveInPage.parentReference.path.contains(sourcePath)
                    && driveInPage.folder == null && driveInPage.id != null && getFileDate(driveInPage.lastModifiedDateTime) > (since != null ? since : 0)) {
                if (search == null || search.isBlank() || driveInPage.name.equalsIgnoreCase(search)) {
                    processDriveItem(result, driveInPage);
                }
            }

        }
    }

    /**
     * Processes a single drive item to determine its version status.
     * This method checks if the drive item has multiple versions to determine
     * if it was updated or created, and adds the appropriate result to the list.
     *
     * @param result      The list to add the result to
     * @param driveInPage The drive item to process
     */
    private void processDriveItem(List<Map<String, Object>> result, DriveItem driveInPage) {
        DriveItemVersionCollectionPage driveItemVersionCollectionPage = getUserRequestBuilder().drive().items(Objects.requireNonNull(driveInPage.id)).versions().buildRequest().get();
        if (driveItemVersionCollectionPage != null
                && !driveItemVersionCollectionPage.getCurrentPage().isEmpty()
                && driveItemVersionCollectionPage.getCurrentPage().size() > 1) {

            DriveItemVersion version = driveItemVersionCollectionPage.getCurrentPage().getFirst();
            if (version.lastModifiedDateTime != null) {
                Map<String, Object> resultContent = ResponseUtil
                        .createResultContent(driveInPage.parentReference.path + "/" + driveInPage.name
                                , getFileDate(driveInPage.lastModifiedDateTime)
                                , UPDATED);
                result.add(resultContent);
            }
        } else {
            Map<String, Object> resultContent = ResponseUtil
                    .createResultContent(driveInPage.parentReference.path + "/" + driveInPage.name
                            , getFileDate(driveInPage.lastModifiedDateTime)
                            , CREATED);
            result.add(resultContent);
        }
    }

    /**
     * Checks if a file or folder exists at the specified path.
     * This method only supports personal drive paths.
     *
     * @param source The path to check
     * @return true if the file or folder exists, false otherwise
     */
    private boolean isFileExist(String source) {
        try {
            if (FileUtil.isPersonalDrive(source)) {
                String path = FileUtil.getRelativeFilePath(source);
                DriveItem driveItem = path.isEmpty()
                        ? FileUtil.getRootUserRequestBuilder(createProvider()).buildRequest().get()
                        : FileUtil.getRootUserRequestBuilder(createProvider()).itemWithPath(path).buildRequest().get();
                return driveItem != null && driveItem.folder != null;
            }
        } catch (GraphServiceException | IllegalArgumentException cause) {
            LOGGER.info(cause.getMessage(), cause);
            return false;
        }
        return false;
    }

    /**
     * Converts an OffsetDateTime to a Unix timestamp in milliseconds.
     * This method handles null dates by returning 0.
     *
     * @param dateTime The OffsetDateTime to convert
     * @return The timestamp in milliseconds, or 0 if the input is null
     */
    private long getFileDate(OffsetDateTime dateTime) {
        return dateTime != null ? Timestamp.valueOf(dateTime.toLocalDateTime()).getTime() : 0L;
    }

    /**
     * Gets a UserRequestBuilder for making Graph API requests.
     * This method uses the provider to get a builder with default settings.
     *
     * @return A UserRequestBuilder for making Graph API requests
     */
    private UserRequestBuilder getUserRequestBuilder() {
        return createProvider().getUserRequestBuilder(null, null);
    }

    /**
     * Validates and re-enters a request for version history.
     * This method is called when the initial request fails validation,
     * and prompts the user to provide a valid path.
     *
     * @param sourcePath The path to the folder
     * @param search     The search string to filter files
     * @param since      The timestamp to filter files by modification date
     * @return An ExtensionResponse containing validation results
     */
    public ExtensionResponse reenterValidate(String sourcePath, String search, Long since) {
        ExtensionResponse validationRes = validationTypeUtil.checkValidationsVersionHistory(Validator.ValidationResource.SOURCE_PATH, SubCatalogConstants.CONFIRM_REENTER_SOURCE_FILE_HISTORY, sourcePath, search, since);

        if (validationRes != null) return validationRes;

        ExtensionResponse.Error error = new ErrorBuilder()
                .exceptionType(ExtensionResponse.Error.ExceptionType.INPUT_ERROR).errorMessage(Constants.PROVIDE_VALID_PATH_FILE_NOT_FOUND)
                .build();
        return new ExtensionResponseBuilder()
                .failure(error)
                .build();
    }
}
