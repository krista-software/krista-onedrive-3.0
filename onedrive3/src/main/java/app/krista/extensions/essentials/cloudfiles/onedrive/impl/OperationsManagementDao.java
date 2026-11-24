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
import app.krista.extensions.essentials.cloudfiles.onedrive.catalog.CatalogTypes;
import app.krista.extensions.essentials.cloudfiles.onedrive.catalog.FileHandlers.TelemetryHelper;
import app.krista.extensions.essentials.cloudfiles.onedrive.catalog.validators.Validator;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.connectors.GraphServiceClientProvider;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.connectors.GraphServiceClientProviderFactory;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.stores.KristaMediaClient;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.util.*;
import com.microsoft.graph.http.GraphServiceException;
import com.microsoft.graph.models.DriveItem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jvnet.hk2.annotations.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Data Access Object for managing file operations in OneDrive.
 * This class handles core file operations such as uploading, downloading, deleting, and renaming files.
 * It interacts with the Microsoft Graph API to perform these operations on OneDrive files.
 */
@Service
public class OperationsManagementDao {

    private static final Logger LOGGER = LoggerFactory.getLogger(OperationsManagementDao.class);
    private static final String FILE_SIZE_IS_TOO_LARGE_TO_READ = "File size is too large to read";
    private static final String INVALID_FILE_NAME_PLEASE_PROVIDE_FILE_EXTENSION_AS_TXT = "Invalid file name, please provide a .txt or .md file extension";
    private static final String MICROSOFT_GRAPH_DOWNLOAD_URL = "@microsoft.graph.downloadUrl";

    private final GraphServiceClientProviderFactory providerFactory;
    private final GraphServiceClientProvider provider;
    private final KristaMediaClient kristaMediaClient;
    private final ValidationTypeUtil validationTypeUtil;
    private final TelemetryHelper telemetryHelper;


    @Inject
    public OperationsManagementDao(GraphServiceClientProviderFactory factory, KristaMediaClient kristaMediaClient, ValidationTypeUtil validationTypeUtil, TelemetryHelper telemetryHelper) {
        this.providerFactory = factory;
        this.provider = null;
        this.kristaMediaClient = kristaMediaClient;
        this.validationTypeUtil = validationTypeUtil;
        this.telemetryHelper = telemetryHelper;
    }

    public OperationsManagementDao(GraphServiceClientProvider provider, KristaMediaClient kristaMediaClient, ValidationTypeUtil validationTypeUtil, TelemetryHelper telemetryHelper) {
        this.providerFactory = null;
        this.provider = provider;
        this.kristaMediaClient = kristaMediaClient;
        this.validationTypeUtil = validationTypeUtil;
        this.telemetryHelper = telemetryHelper;
    }

    private GraphServiceClientProvider createProvider() {
        return provider != null ? provider : providerFactory.create();
    }

    /**
     * Extracts an appropriate error message from an exception.
     * Maps specific error conditions to user-friendly messages.
     *
     * @param cause The exception to extract the message from
     * @return A user-friendly error message
     */
    @NotNull
    private static String getErrorMessage(Exception cause) {
        String message = cause.getMessage();
        if (message == null) {
            return Constants.PLEASE_PROVIDE_VALID_FILE_SOURCE_PATH;
        }
        if (message.contains(Constants.ITEM_NOT_FOUND)) {
            return Constants.FILE_NOT_FOUND;
        } else if (message.contains(Constants.ACCESS_DENIED)) {
            return Constants.FILE_IS_CURRENTLY_RESTRICTED_FOR_ASSISTANCE_CONTACT_YOUR_ADMINISTRATOR_OR_SUPPORT;
        } else {
            return Constants.PLEASE_PROVIDE_VALID_FILE_SOURCE_PATH;
        }
    }

    @Nullable
    private static String getDownloadURL(DriveItem driveItem) {
        String downloadLink = driveItem.additionalDataManager().get(MICROSOFT_GRAPH_DOWNLOAD_URL) != null
                ? driveItem.additionalDataManager().get(MICROSOFT_GRAPH_DOWNLOAD_URL).getAsString()
                : driveItem.webUrl;
        String parentPath = (driveItem.parentReference != null && driveItem.parentReference.path != null)
                ? FileUtil.getRelativeFilePath(driveItem.parentReference.path)
                : "";
        return "<a href=" + downloadLink + " target=_blank>"
                + parentPath
                + "/" + driveItem.name + "</a>";
    }

    /**
     * Downloads a file from OneDrive.
     * Supports downloading as a link or as a Krista file.
     *
     * @param downloadAsLink       Whether to download as a link
     * @param downloadAsKristaFile Whether to download as a Krista file
     * @param absolutePath         The absolute path of the file to download
     * @param isReentered          Whether this is a re-entered request after validation
     * @return ExtensionResponse containing the download result or an error
     */
    public ExtensionResponse downloadFiles(Boolean downloadAsLink, Boolean downloadAsKristaFile, String absolutePath, boolean isReentered) {
        Map<String, Object> payload = new HashMap<>();
        long startTime = System.currentTimeMillis();

        LOGGER.info("FileDownloadService.downloadFiles - Starting download operation");
        LOGGER.debug("Parameters - downloadAsLink: {}, downloadAsKristaFile: {}, absolutePath: {}, isReentered: {}",
                downloadAsLink, downloadAsKristaFile, absolutePath, isReentered);

        telemetryHelper.incrementCount(TelemetryConstants.TELEMETRY_DOWNLOAD_FILE);
        try {
            if (absolutePath != null) {
                if (FileUtil.checkDriveIDContains(absolutePath)) {
                    LOGGER.debug("Detected drive ID in path, attempting downloadByDriveIDByPath");
                    List<String> content = FileUtil.getDriveIDAndPath(absolutePath);
                    downloadByDriveIDByPath(content.get(0), content.get(1), downloadAsLink, downloadAsKristaFile, payload);
                    if (payload.isEmpty() || !Boolean.TRUE.equals(payload.get(Constants.SUCCESS))) {
                        LOGGER.debug("Download failed for drive ID path");
                        if (!isReentered) {
                            telemetryHelper.recordRetryPrompted(TelemetryConstants.TELEMETRY_DOWNLOAD_FILE, startTime,
                                    TelemetryHelper.safeTagMap("absolutePath", absolutePath, "reason", Constants.TELEMETRY_REASON_DOWNLOAD_FAILED_DRIVE_ID_PATH));
                            return reenterValidation(downloadAsLink, absolutePath);
                        }
                        setInvalidPathMessage(payload);
                        telemetryHelper.recordValidationError(TelemetryConstants.TELEMETRY_DOWNLOAD_FILE, startTime, "Download failed for drive ID path after reentry", TelemetryHelper.safeTagMap("absolutePath", absolutePath));
                    } else {
                        telemetryHelper.recordSuccess(TelemetryConstants.TELEMETRY_DOWNLOAD_FILE, startTime, TelemetryHelper.safeTagMap("absolutePath", absolutePath));
                    }

                } else if (FileUtil.isPersonalDrive(absolutePath)) {
                    LOGGER.debug("Detected personal drive path, attempting downloadByDriveIDByPath");
                    String path = FileUtil.getRelativeFilePath(absolutePath);
                    downloadByDriveIDByPath(null, path, downloadAsLink, downloadAsKristaFile, payload);

                    if (payload.isEmpty() || !Boolean.TRUE.equals(payload.get(Constants.SUCCESS))) {
                        LOGGER.debug("Download failed for personal drive path");
                        if (!isReentered) {
                            telemetryHelper.recordRetryPrompted(TelemetryConstants.TELEMETRY_DOWNLOAD_FILE, startTime, TelemetryHelper.safeTagMap("absolutePath", absolutePath, "reason", Constants.TELEMETRY_REASON_DOWNLOAD_FAILED_PERSONAL_DRIVE));
                            return reenterValidation(downloadAsLink, absolutePath);
                        }
                        setInvalidPathMessage(payload);
                        telemetryHelper.recordValidationError(TelemetryConstants.TELEMETRY_DOWNLOAD_FILE, startTime, "Download failed for personal drive path after reentry",
                                TelemetryHelper.safeTagMap("absolutePath", absolutePath));
                    } else {
                        telemetryHelper.recordSuccess(TelemetryConstants.TELEMETRY_DOWNLOAD_FILE, startTime,
                                TelemetryHelper.safeTagMap("absolutePath", absolutePath));
                    }
                } else {
                    LOGGER.debug("Unrecognized path format");
                    if (!isReentered) {
                        telemetryHelper.recordRetryPrompted(TelemetryConstants.TELEMETRY_DOWNLOAD_FILE, startTime,
                                TelemetryHelper.safeTagMap("absolutePath", absolutePath, "reason", Constants.TELEMETRY_REASON_INVALID_PATH_FORMAT));
                        return reenterValidation(downloadAsLink, absolutePath);
                    }
                    setInvalidPathMessage(payload);
                    telemetryHelper.recordValidationError(TelemetryConstants.TELEMETRY_DOWNLOAD_FILE, startTime, "Invalid path format after reentry",
                            TelemetryHelper.safeTagMap("absolutePath", absolutePath));
                }
            } else {
                LOGGER.debug("Absolute path is null");
                if (!isReentered) {
                    telemetryHelper.recordRetryPrompted(TelemetryConstants.TELEMETRY_DOWNLOAD_FILE, startTime,
                            TelemetryHelper.safeTagMap("reason", Constants.TELEMETRY_REASON_NULL_PATH));
                    return reenterValidation(downloadAsLink, absolutePath);
                }
                setInvalidPathMessage(payload);
                telemetryHelper.recordValidationError(TelemetryConstants.TELEMETRY_DOWNLOAD_FILE, startTime, "Absolute path was null after reentry",
                        TelemetryHelper.safeTagMap("reason", Constants.TELEMETRY_REASON_NULL_PATH));
            }
        } catch (GraphServiceException cause) {
            LOGGER.error("GraphServiceException during download", cause);
            ResponseUtil.errorResponse(false, "Failed to access file: " + cause.getMessage(), payload);
            telemetryHelper.recordError(TelemetryConstants.TELEMETRY_DOWNLOAD_FILE, startTime, cause,
                    TelemetryHelper.safeTagMap("absolutePath", absolutePath, "error_type", Constants.TELEMETRY_ERROR_GRAPH_SERVICE_EXCEPTION));

        } catch (Exception cause) {
            LOGGER.error("Unexpected exception during download", cause);
            ResponseUtil.errorResponse(false, "Unexpected error: " + cause.getMessage(), payload);
            telemetryHelper.recordError(TelemetryConstants.TELEMETRY_DOWNLOAD_FILE, startTime, cause,
                    TelemetryHelper.safeTagMap("absolutePath", absolutePath, "error_type", Constants.TELEMETRY_ERROR_UNEXPECTED_EXCEPTION));
        }
        LOGGER.debug("Final payload: {}", payload);
        return new ExtensionResponseBuilder().success(payload, null, null).build();
    }


    private void setInvalidPathMessage(Map<String, Object> payload) {
        ResponseUtil.errorResponse(false, Constants.PLEASE_PROVIDE_VALID_FILE_SOURCE_PATH, payload);
    }

    /**
     * Handles validation for re-entry when the initial file path is invalid.
     */
    private ExtensionResponse reenterValidation(Boolean downloadAsLink, String absolutePath) {
        LOGGER.info("FileDownloadService.reenterValidation - Starting reenter validation");
        LOGGER.debug("Reenter validation parameters - Download as link: {}, Path: {}", downloadAsLink, absolutePath);

        ExtensionResponse validationRes = downloadAsLink ?
                validationTypeUtil.checkValidationsWithErrorMessage(Validator.ValidationResource.SOURCE_FILE, SubCatalogConstants.CONFIRM_REENTER_SOURCE_LINK_DOWNLOAD, absolutePath, "")
                : validationTypeUtil.checkValidationsWithErrorMessage(Validator.ValidationResource.SOURCE_FILE, SubCatalogConstants.CONFIRM_REENTER_SOURCE_FILE_DOWNLOAD, absolutePath, "");

        if (validationRes != null) {
            LOGGER.info("FileDownloadService.reenterValidation - Validation response available, returning it");
            return validationRes;
        }

        LOGGER.debug("FileDownloadService.reenterValidation - Validation failed, returning error response");
        ExtensionResponse.Error error = new ErrorBuilder()
                .exceptionType(ExtensionResponse.Error.ExceptionType.INPUT_ERROR)
                .errorMessage(Constants.PROVIDE_VALID_PATH_FILE_NOT_FOUND)
                .build();
        return new ExtensionResponseBuilder()
                .failure(error)
                .build();
    }


    /**
     * Downloads a file using drive ID and path.
     * Handles different download modes (as link or as file).
     *
     * @param driveID              The drive ID
     * @param path                 The path within the drive
     * @param downloadAsLink       Whether to download as a link
     * @param downloadAsKristaFile Whether to download as a Krista file
     * @param payload              The payload to store the result
     */
    private void downloadByDriveIDByPath(String driveID, String path, Boolean downloadAsLink, Boolean downloadAsKristaFile, Map<String, Object> payload) {
        try {
            DriveItem driveItem = getDriveItem(driveID, path);
            if (driveItem != null && driveItem.folder == null) {
                String downloadURL = getDownloadURL(driveItem);
                if (downloadAsLink != null && downloadAsLink) {
                    ResponseUtil.downloadAsLinkResponse("[ " + downloadURL + " ]", true
                            , Constants.TO_DOWNLOAD_FILE_PLEASE_COPY_URL, payload);
                } else if (downloadAsKristaFile != null && downloadAsKristaFile && driveItem.size != null && driveItem.size > (1024 * 1024 * 50)) {
                    ResponseUtil.errorResponse(false, Constants.FILE_SIZE_IS_GREATER_THAN_50_MB_PLEASE_DOWNLOAD_FILE_BY_URL
                            + " \n Use link to download file : " + "[ " + downloadURL + " ]", payload);
                } else {
                    if (driveItem.size != null && driveItem.size > (1024 * 1024 * 50)) { // 50MB
                        ResponseUtil.errorResponse(false, Constants.FILE_SIZE_IS_GREATER_THAN_50_MB_PLEASE_DOWNLOAD_FILE_BY_URL
                                + " \n Use link to download file : " + "[ " + downloadURL + " ]", payload);
                    } else if (driveItem.size != null && driveItem.size <= (1024 * 1024 * 50)
                            && driveItem.parentReference != null
                            && driveItem.parentReference.driveId != null) {
                        assert driveItem.id != null;
                        String rawDownloadUrl = driveItem.additionalDataManager().get(MICROSOFT_GRAPH_DOWNLOAD_URL) != null
                                ? driveItem.additionalDataManager().get(MICROSOFT_GRAPH_DOWNLOAD_URL).getAsString()
                                : null;
                        InputStream inputStream;
                        if (rawDownloadUrl != null) {
                            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) new java.net.URL(rawDownloadUrl).openConnection();
                            conn.setConnectTimeout(30000);
                            conn.setReadTimeout(60000);
                            conn.setInstanceFollowRedirects(true);
                            int responseCode = conn.getResponseCode();
                            if (responseCode >= 200 && responseCode < 300) {
                                inputStream = conn.getInputStream();
                            } else {
                                throw new IOException("Failed to download file content: HTTP " + responseCode + " for file: " + driveItem.name);
                            }
                        } else {
                            inputStream = FileUtil.getUserRequestBuilderWithItemId(createProvider(), driveItem.parentReference.driveId, driveItem.id)
                                    .content().buildRequest().get();
                        }
                        java.io.File content = DriveUtil.getFileFromInputStream(inputStream, driveItem.name);
                        app.krista.model.base.File kristaFile = kristaMediaClient.toKristaFile(content);
                        ResponseUtil.downloadAsKristaFileResponse(kristaFile, true, "File is ready to download", payload);
                    } else {
                        LOGGER.warn("downloadByDriveIDByPath - cannot download file: size={}, parentReference={}, driveId={}",
                                driveItem.size,
                                driveItem.parentReference != null ? "present" : "NULL",
                                (driveItem.parentReference != null) ? driveItem.parentReference.driveId : "NULL");
                        ResponseUtil.errorResponse(false, Constants.PLEASE_PROVIDE_VALID_FILE_SOURCE_PATH, payload);
                    }
                }
            } else {
                ResponseUtil.errorResponse(false, Constants.PLEASE_PROVIDE_VALID_FILE_SOURCE_PATH, payload);
            }
        } catch (GraphServiceException | IllegalArgumentException | IOException cause) {
            LOGGER.error("Error during download by drive ID path: {}", cause.getMessage(), cause);
            ResponseUtil.errorResponse(false, getErrorMessage(cause), payload);
        } catch (Exception cause) {
            LOGGER.error("Unexpected error during download by drive ID path: {}", cause.getMessage(), cause);
            ResponseUtil.errorResponse(false, getErrorMessage(cause), payload);
        }
    }

    /**
     * Gets a DriveItem based on drive ID and path.
     *
     * @param driveID The drive ID (null for personal drive)
     * @param path    The path to the item
     * @return The DriveItem, or null if not found
     */
    @Nullable
    DriveItem getDriveItem(String driveID, String path) {
        return (driveID != null)
                ? getSharedDriveItem(driveID, path)
                : getRootDriveItem(path);
    }

    /**
     * Gets a DriveItem from a shared drive.
     *
     * @param driveID The drive ID
     * @param path    The path within the drive
     * @return The DriveItem, or null if not found
     */
    @Nullable
    private DriveItem getSharedDriveItem(String driveID, String path) {
        return (path != null && !path.isEmpty())
                ? FileUtil.getSharedUserRequestBuilder(createProvider(), driveID).itemWithPath(path).buildRequest().get()
                : null;
    }

    /**
     * Gets a DriveItem from the root (personal) drive.
     *
     * @param path The path within the drive
     * @return The DriveItem, or null if not found
     */
    @Nullable
    private DriveItem getRootDriveItem(String path) {
        return (path != null && !path.isEmpty())
                ? FileUtil.getRootUserRequestBuilder(createProvider()).itemWithPath(path).buildRequest().get()
                : null;
    }

    /**
     * Uploads a file to OneDrive.
     * Simplified version that uses default values for custom name and re-entry flags.
     *
     * @param kristaFile The file to upload
     * @param targetPath The target path to upload to
     * @return ExtensionResponse containing the upload result or an error
     */
    public ExtensionResponse uploadFiles(app.krista.model.base.File kristaFile, String targetPath) {
        return uploadFiles(kristaFile, targetPath, "", false, false);
    }

    /**
     * Uploads a file to OneDrive with retry option.
     * Simplified version that uses default values for custom name and re-entry flags.
     *
     * @param kristaFile The file to upload
     * @param targetPath The target path to upload to
     * @param allowRetry Whether to allow retry on validation failure
     * @return ExtensionResponse containing the upload result or an error
     */
    public ExtensionResponse uploadFiles(app.krista.model.base.File kristaFile, String targetPath, Boolean allowRetry) {
        return uploadFiles(kristaFile, targetPath, "", false, false, allowRetry);
    }

    /**
     * Uploads a file to OneDrive at the specified target path.
     * <p>
     * This method handles the complete upload process including:
     * 1. Path validation for different drive types (personal drive, shared drive with driveID)
     * 2. File existence checks to prevent unintended overwrites
     * 3. Custom file naming with validation of illegal characters
     * 4. Conversion of Krista file format to Java file format
     * 5. Actual file upload to the appropriate OneDrive location
     * 6. Detailed telemetry tracking for monitoring and diagnostics
     * <p>
     * The method supports both personal drives and shared drives. For shared drives,
     * the target path should be in the format "driveId:/path/to/file".
     * <p>
     * If validation fails and this is not a re-entered request, it will prompt
     * for re-entry with appropriate error messages. If validation fails on a re-entered
     * request, it will return an error response.
     *
     * @param kristaFile            The file to upload in Krista's file format
     * @param targetPath            The destination path where the file should be uploaded
     * @param customName            Optional custom name for the file (without extension)
     * @param isReentered           Whether this is a re-entered request after path validation failure
     * @param isCustomNameReentered Whether this is a re-entered request after custom name validation failure
     * @return ExtensionResponse containing:
     * - Success: true/false indicating if the upload succeeded
     * - Message: Status message or error description
     * - File: File entity representing the uploaded file (if successful)
     */
    public ExtensionResponse uploadFiles(app.krista.model.base.File kristaFile, String targetPath, String customName,
                                         boolean isReentered, boolean isCustomNameReentered) {
        // Default to allowRetry = true for backward compatibility
        return uploadFiles(kristaFile, targetPath, customName, isReentered, isCustomNameReentered, true);
    }

    /**
     * Uploads a file to OneDrive at the specified target path with retry option.
     * <p>
     * This method handles the complete upload process including:
     * 1. Path validation for different drive types (personal drive, shared drive with driveID)
     * 2. File existence checks to prevent unintended overwrites
     * 3. Custom file naming with validation of illegal characters
     * 4. Conversion of Krista file format to Java file format
     * 5. Actual file upload to the appropriate OneDrive location
     * 6. Detailed telemetry tracking for monitoring and diagnostics
     * <p>
     * The method supports both personal drives and shared drives. For shared drives,
     * the target path should be in the format "driveId:/path/to/file".
     * <p>
     * If validation fails and allowRetry is true, it will prompt for re-entry.
     * If validation fails and allowRetry is false, it will return a direct error.
     *
     * @param kristaFile            The file to upload in Krista's file format
     * @param targetPath            The destination path where the file should be uploaded
     * @param customName            Optional custom name for the file (without extension)
     * @param isReentered           Whether this is a re-entered request after path validation failure
     * @param isCustomNameReentered Whether this is a re-entered request after custom name validation failure
     * @param allowRetry            Whether to allow retry on validation failure
     * @return ExtensionResponse containing:
     * - Success: true/false indicating if the upload succeeded
     * - Message: Status message or error description
     * - File: File entity representing the uploaded file (if successful)
     */
    public ExtensionResponse uploadFiles(app.krista.model.base.File kristaFile, String targetPath, String customName,
                                         boolean isReentered, boolean isCustomNameReentered, Boolean allowRetry) {
        LOGGER.info("OperationsManagementDao.uploadFiles - Starting upload operation | targetPath: {}, customName: {}, isReentered: {}, isCustomNameReentered: {}, fileName: {}",
                targetPath, customName, isReentered, isCustomNameReentered, kristaFile != null ? kristaFile.getFileName() : "null");

        Map<String, Object> payload = new HashMap<>();
        long startTime = System.currentTimeMillis();
        telemetryHelper.incrementCount(TelemetryConstants.TELEMETRY_UPLOAD_FILES);
        try {
            if (targetPath != null) {
                LOGGER.debug("OperationsManagementDao.uploadFiles - Target path is not null: {}", targetPath);

                if (FileUtil.checkDriveIDContains(targetPath)) {
                    List<String> content = FileUtil.getDriveIDAndPath(targetPath);
                    uploadFileByPathByDriveID(content.get(0), content.get(1), kristaFile, customName, payload);
                    LOGGER.debug("OperationsManagementDao.uploadFiles - Upload attempt completed, payload status: success={}, message={}, isEmpty={}",
                            payload.get(Constants.SUCCESS), payload.get(Constants.MESSAGE), payload.isEmpty());

                    if (Constants.INVALID_FOLDER_NAME.equals(payload.get(Constants.MESSAGE)) && !isCustomNameReentered) {
                        telemetryHelper.recordRetryPrompted(TelemetryConstants.TELEMETRY_UPLOAD_FILES, startTime,
                                TelemetryHelper.safeTagMap(TelemetryConstants.TELEMETRY_TAG_REASON, TelemetryConstants.TELEMETRY_REASON_INVALID_FILE_NAME,
                                        TelemetryConstants.TELEMETRY_TAG_TARGET_PATH, targetPath));

                        // Check allowRetry flag
                        if (Boolean.TRUE.equals(allowRetry)) {
                            return reenterValidateWrite(kristaFile, targetPath, customName, true, Constants.INVALID_FOLDER_NAME);
                        } else {
                            // Return direct error without retry
                            ResponseUtil.errorResponse(false, Constants.INVALID_FOLDER_NAME, payload);
                            return new ExtensionResponseBuilder()
                                    .success(payload, null, null)
                                    .build();
                        }
                    }
                    if (payload.isEmpty()) {
                        if (!isReentered) {
                            LOGGER.debug("OperationsManagementDao.uploadFiles - Empty payload for driveID path, requesting path reentry | driveID: {}", content.get(0));
                            telemetryHelper.recordRetryPrompted(TelemetryConstants.TELEMETRY_UPLOAD_FILES, startTime,
                                    TelemetryHelper.safeTagMap(TelemetryConstants.TELEMETRY_TAG_TARGET_PATH, targetPath, TelemetryConstants.TELEMETRY_TAG_REASON, "payload_empty_driveId"));

                            // Check allowRetry flag
                            if (Boolean.TRUE.equals(allowRetry)) {
                                return reenterValidateWrite(kristaFile, targetPath, customName, false, "Please Provide valid target path");
                            } else {
                                // Return direct error without retry
                                ResponseUtil.errorResponse(false, Constants.PLEASE_PROVIDE_VALID_TARGET_PATH, payload);
                                return new ExtensionResponseBuilder()
                                        .success(payload, null, null)
                                        .build();
                            }
                        }
                        LOGGER.error("uploadFiles - Empty payload after re-entry, path is invalid");
                        telemetryHelper.recordValidationError(TelemetryConstants.TELEMETRY_UPLOAD_FILES, startTime,
                                "Upload failed - invalid driveId path", TelemetryHelper.safeTagMap(TelemetryConstants.TELEMETRY_TAG_TARGET_PATH, targetPath));
                        ResponseUtil.errorResponse(false, Constants.PLEASE_PROVIDE_VALID_TARGET_PATH, payload);
                    } else {
                        telemetryHelper.recordSuccess(TelemetryConstants.TELEMETRY_UPLOAD_FILES, startTime,
                                TelemetryHelper.safeTagMap(TelemetryConstants.TELEMETRY_TAG_TARGET_PATH, targetPath, "driveId", content.get(0)));
                    }

                    LOGGER.debug("OperationsManagementDao.uploadFiles - Returning response for driveID path | payload: {}", payload);
                    return new ExtensionResponseBuilder()
                            .success(payload, null, null)
                            .build();
                }

                if (FileUtil.isPersonalDrive(targetPath)) {
                    String path = FileUtil.getRelativeFilePath(targetPath);
                    LOGGER.debug("OperationsManagementDao.uploadFiles - Personal drive detected, relative path: {}", path);

                    uploadFileByPathByDriveID(null, path, kristaFile, customName, payload);
                    if (Constants.INVALID_FOLDER_NAME.equals(payload.get(Constants.MESSAGE)) && !isCustomNameReentered) {
                        telemetryHelper.recordRetryPrompted(TelemetryConstants.TELEMETRY_UPLOAD_FILES, startTime,
                                TelemetryHelper.safeTagMap(TelemetryConstants.TELEMETRY_TAG_REASON, TelemetryConstants.TELEMETRY_REASON_INVALID_FILE_NAME,
                                        TelemetryConstants.TELEMETRY_TAG_TARGET_PATH, targetPath));

                        // Check allowRetry flag
                        if (Boolean.TRUE.equals(allowRetry)) {
                            return reenterValidateWrite(kristaFile, targetPath, customName, true, Constants.INVALID_FOLDER_NAME);
                        } else {
                            // Return direct error without retry
                            ResponseUtil.errorResponse(false, Constants.INVALID_FOLDER_NAME, payload);
                            return new ExtensionResponseBuilder()
                                    .success(payload, null, null)
                                    .build();
                        }
                    }

                    if (payload.isEmpty()) {
                        if (!isReentered) {
                            telemetryHelper.recordRetryPrompted(TelemetryConstants.TELEMETRY_UPLOAD_FILES, startTime,
                                    TelemetryHelper.safeTagMap(TelemetryConstants.TELEMETRY_TAG_TARGET_PATH, targetPath, TelemetryConstants.TELEMETRY_TAG_REASON, "payload_empty_personal_drive"));

                            // Check allowRetry flag
                            if (Boolean.TRUE.equals(allowRetry)) {
                                return reenterValidateWrite(kristaFile, targetPath, customName, false, "Please Provide valid target path");
                            } else {
                                // Return direct error without retry
                                ResponseUtil.errorResponse(false, Constants.PLEASE_PROVIDE_VALID_TARGET_PATH, payload);
                                return new ExtensionResponseBuilder()
                                        .success(payload, null, null)
                                        .build();
                            }
                        }
                        telemetryHelper.recordValidationError(TelemetryConstants.TELEMETRY_UPLOAD_FILES, startTime,
                                "Upload failed - invalid personal drive path", TelemetryHelper.safeTagMap(TelemetryConstants.TELEMETRY_TAG_TARGET_PATH, targetPath));
                        ResponseUtil.errorResponse(false, Constants.PLEASE_PROVIDE_VALID_TARGET_PATH, payload);
                    } else {
                        telemetryHelper.recordSuccess(TelemetryConstants.TELEMETRY_UPLOAD_FILES, startTime,
                                TelemetryHelper.safeTagMap(TelemetryConstants.TELEMETRY_TAG_TARGET_PATH, targetPath));
                    }

                    return new ExtensionResponseBuilder()
                            .success(payload, null, null)
                            .build();
                }

                if (!isReentered) {
                    LOGGER.debug("uploadFiles - First attempt with unrecognized path, prompting for re-entry");
                    telemetryHelper.recordRetryPrompted(TelemetryConstants.TELEMETRY_UPLOAD_FILES, startTime,
                            TelemetryHelper.safeTagMap(TelemetryConstants.TELEMETRY_TAG_TARGET_PATH, targetPath, TelemetryConstants.TELEMETRY_TAG_REASON, "unknown_path_format"));

                    // Check allowRetry flag
                    if (Boolean.TRUE.equals(allowRetry)) {
                        return reenterValidateWrite(kristaFile, targetPath, customName, false, "Please Provide valid target path");
                    } else {
                        // Return direct error without retry
                        ResponseUtil.errorResponse(false, Constants.PLEASE_PROVIDE_VALID_TARGET_PATH, payload);
                        return new ExtensionResponseBuilder()
                                .success(payload, null, null)
                                .build();
                    }
                }

                telemetryHelper.recordValidationError(TelemetryConstants.TELEMETRY_UPLOAD_FILES, startTime,
                        "Upload failed - unrecognized path format", TelemetryHelper.safeTagMap(TelemetryConstants.TELEMETRY_TAG_TARGET_PATH, targetPath));
                ResponseUtil.errorResponse(false, Constants.PLEASE_PROVIDE_VALID_TARGET_PATH, payload);
            } else {
                if (!isReentered) {
                    LOGGER.warn("OperationsManagementDao.uploadFiles - Target path is null, prompting for re-entry");
                    telemetryHelper.recordRetryPrompted(TelemetryConstants.TELEMETRY_UPLOAD_FILES, startTime,
                            TelemetryHelper.safeTagMap(TelemetryConstants.TELEMETRY_TAG_TARGET_PATH, "null", TelemetryConstants.TELEMETRY_TAG_REASON, "targetPath_null"));

                    // Check allowRetry flag
                    if (Boolean.TRUE.equals(allowRetry)) {
                        return reenterValidateWrite(kristaFile, targetPath, customName, false, "Please Provide valid target path");
                    } else {
                        // Return direct error without retry
                        ResponseUtil.errorResponse(false, Constants.PLEASE_PROVIDE_VALID_TARGET_PATH, payload);
                        return new ExtensionResponseBuilder()
                                .success(payload, null, null)
                                .build();
                    }
                }
                telemetryHelper.recordValidationError(TelemetryConstants.TELEMETRY_UPLOAD_FILES, startTime,
                        "Upload failed - targetPath was null after reentry", TelemetryHelper.safeTagMap(TelemetryConstants.TELEMETRY_TAG_TARGET_PATH, "null"));
                ResponseUtil.errorResponse(false, Constants.PLEASE_PROVIDE_VALID_TARGET_PATH, payload);
            }
        } catch (GraphServiceException | IllegalArgumentException ex) {
            LOGGER.error("uploadFiles - Exception occurred: {}", ex.getMessage(), ex);
            telemetryHelper.recordError(TelemetryConstants.TELEMETRY_UPLOAD_FILES, startTime, ex,
                    TelemetryHelper.safeTagMap(TelemetryConstants.TELEMETRY_TAG_TARGET_PATH, targetPath, TelemetryConstants.TELEMETRY_TAG_ERROR_TYPE, ex.getClass().getSimpleName()));
            ResponseUtil.errorResponse(false, "Upload failed: " + ex.getMessage(), payload);
        }

        LOGGER.debug("OperationsManagementDao.uploadFiles - Returning final response after validation failures or exceptions | payload: {}", payload);
        return new ExtensionResponseBuilder()
                .success(payload, null, null)
                .build();
    }


    /**
     * Uploads a file to a specific path in a drive.
     * Handles both shared drives and personal drives.
     *
     * @param driveID    The drive ID (null for personal drive)
     * @param targetPath The target path within the drive
     * @param kristaFile The file to upload
     * @param customName Custom name for the uploaded file
     * @param payload    The payload to store the result
     */
    private void uploadFileByPathByDriveID(String driveID, String targetPath, app.krista.model.base.File kristaFile, String customName, Map<String, Object> payload) {
        try {
            DriveItem driveItem = driveID != null
                    ? getShareRootDriveItem(targetPath, driveID)
                    : getPersonalDriveItem(targetPath);
            if (customName != null && !customName.isBlank()) {
                if (containsInvalidCharacters(customName)) {
                    LOGGER.debug("OperationsManagementDao.uploadFileByPathByDriveID - Invalid custom name detected: {}", customName);
                    ResponseUtil.errorResponse(false, Constants.INVALID_FOLDER_NAME, payload);
                    return;
                }
            }
            String fileName = (customName != null && !customName.isBlank()) ? customName + "." + kristaFile.getExtension() : kristaFile.getFileName();
            getFileInfo(driveID, targetPath + "/" + fileName, payload);
            if (driveItem != null && driveItem.folder != null && driveItem.id != null && isFileExist(payload)) {
                java.io.File javaFile = kristaMediaClient.toJavaFile(kristaFile, fileName);
                DriveUtil.listValidator(List.of(javaFile), Constants.ERROR_OCCURRED_DURING_UPLOADING_ATTACHMENT);
                DriveItem uploadedDriveItem = getUploadedDriveItem(driveID, driveItem, javaFile);
                if (uploadedDriveItem != null) {
                    app.krista.extensions.essentials.cloudfiles.onedrive.catalog.entities.File fileEntity = CatalogTypes.fromFile(new FileImpl(uploadedDriveItem, null, null));
                    ResponseUtil.fileEntityResponse(fileEntity, true, Constants.FILE_ID_UPLOADED_SUCCESSFULLY, payload);
                }
            } else {
                app.krista.extensions.essentials.cloudfiles.onedrive.catalog.entities.File existedFile = CatalogTypes.fromFile(new FileImpl((DriveItem) payload.get(Constants.DRIVE_ITEM), null, null));
                ResponseUtil.fileEntityResponse(existedFile, false, Constants.FILE_ALREADY_EXIST, payload);
            }
        } catch (GraphServiceException | IllegalArgumentException | IOException cause) {
            LOGGER.info(Constants.ERROR_OCCURRED_DURING_UPLOADING_ATTACHMENT + ": {}", cause.getMessage(), cause);
            ResponseUtil.errorResponse(false, Objects.requireNonNull(cause.getMessage()).contains(Constants.ITEM_NOT_FOUND)
                    ? Constants.FILE_NOT_FOUND
                    : Constants.ERROR_OCCURRED_DURING_UPLOADING_ATTACHMENT, payload);
        }

    }

    /**
     * Checks if a string contains characters that are invalid for file or folder names.
     * Invalid characters include: * : < > ? / \ |
     *
     * @param input The string to check for invalid characters
     * @return true if the string contains invalid characters, false otherwise
     */
    public boolean containsInvalidCharacters(String input) {
        LOGGER.debug("CopyFileDao.containsInvalidCharacters - Checking input: {}", input);
        String invalidChars = "*:<>?/\\|";
        for (char c : input.toCharArray()) {
            if (invalidChars.indexOf(c) != -1) {
                LOGGER.debug("CopyFileDao.containsInvalidCharacters - Invalid character found: {}", c);
                return true;
            }
        }
        return false;
    }

    /**
     * Uploads a file to OneDrive.
     * Handles the actual upload operation after validation.
     *
     * @param driveID   The drive ID (null for personal drive)
     * @param driveItem The parent folder DriveItem
     * @param javaFile  The Java File to upload
     * @return The uploaded DriveItem, or null if upload failed
     * @throws IOException If an I/O error occurs during upload
     */
    @Nullable
    private DriveItem getUploadedDriveItem(String driveID, DriveItem driveItem, File javaFile) throws IOException {
        return driveID != null
                ? FileUtil.getUserRequestBuilderWithItemId(createProvider(), driveID, driveItem.id)
                .itemWithPath(javaFile.getName())
                .content().buildRequest().put(DriveUtil.getByteArray(javaFile))
                : FileUtil.getUserRequestBuilderWithItemId(createProvider(), null, driveItem.id)
                .itemWithPath(javaFile.getName())
                .content().buildRequest().put(DriveUtil.getByteArray(javaFile));
    }

    /**
     * Checks if a file exists based on the payload.
     *
     * @param payload The payload containing file existence information
     * @return true if the file does not exist, false if it exists
     */
    private static boolean isFileExist(Map<String, Object> payload) {
        return !(payload.get(Constants.IS_FILE_EXIST).equals(Boolean.TRUE));
    }

    /**
     * Deletes a file from OneDrive.
     * Handles validation and re-entry for confirmation.
     *
     * @param target      The path of the file to delete
     * @param isReentered Whether this is a re-entered request after validation
     * @return ExtensionResponse containing the deletion result or an error
     */
    public ExtensionResponse deleteFile(String target, Boolean isReentered) {
        // Default to allowRetry = true for backward compatibility
        return deleteFile(target, isReentered, true);
    }

    /**
     * Deletes a file from OneDrive with retry option.
     * Handles validation and re-entry for confirmation.
     *
     * @param target      The path of the file to delete
     * @param isReentered Whether this is a re-entered request after validation
     * @param allowRetry  Whether to allow retry on validation failure
     * @return ExtensionResponse containing the deletion result or an error
     */
    public ExtensionResponse deleteFile(String target, Boolean isReentered, Boolean allowRetry) {
        LOGGER.info("OperationsManagementDao.deleteFile - Starting with target: {}, isReentered: {}", target, isReentered);
        long startTime = System.currentTimeMillis();
        Map<String, Object> payload = new HashMap<>();
        telemetryHelper.incrementCount(TelemetryConstants.TELEMETRY_DELETE_FILE);

        try {
            if (target != null && !FileUtil.hasFileExtension(target) && !isReentered) {
                LOGGER.debug("Target lacks file extension and not re-entered, triggering validation");
                telemetryHelper.recordRetryPrompted(TelemetryConstants.TELEMETRY_DELETE_FILE, startTime,
                        TelemetryHelper.safeTagMap(TelemetryConstants.TELEMETRY_TAG_TARGET, target, TelemetryConstants.TELEMETRY_TAG_REASON, "no_file_extension"));

                // Check allowRetry flag
                if (Boolean.TRUE.equals(allowRetry)) {
                    return reenterValidate(target);
                } else {
                    // Return direct error without retry
                    return new ExtensionResponseBuilder()
                            .failure("Please provide a valid file path with extension")
                            .build();
                }
            }

            LOGGER.debug("Checking if file exists for target: {}", target);
            checkIfFileExist(target, payload);

            if ((boolean) payload.getOrDefault(Constants.IS_FILE_EXIST, false)) {
                DriveItem driveItem = (DriveItem) payload.get(Constants.DRIVE_ITEM);

                if (driveItem == null || driveItem.parentReference == null || driveItem.id == null) {
                    LOGGER.error("DriveItem or its parentReference/id is null");
                    ResponseUtil.errorResponse(false, "File reference is incomplete", payload);
                    telemetryHelper.recordValidationError(TelemetryConstants.TELEMETRY_DELETE_FILE, startTime,
                            "DriveItem is null or missing reference/id",
                            TelemetryHelper.safeTagMap(TelemetryConstants.TELEMETRY_TAG_TARGET, target));
                    return new ExtensionResponseBuilder()
                            .success(payload, null, null)
                            .build();
                }

                String path = FileUtil.getRelativeFilePath(target);
                FileUtil.getRootUserRequestBuilder(createProvider()).itemWithPath(path).buildRequest().delete();

                LOGGER.info("File deleted successfully at path: {}", path);
                String successMessage = driveItem.folder != null ?
                        "Folder deleted successfully" : Constants.FILE_DELETED_SUCCESSFULLY;
                ResponseUtil.errorResponse(true, successMessage, payload);

                telemetryHelper.recordSuccess(TelemetryConstants.TELEMETRY_DELETE_FILE, startTime,
                        TelemetryHelper.safeTagMap(TelemetryConstants.TELEMETRY_TAG_TARGET, target, "drive_id", driveItem.parentReference.driveId, "item_id", driveItem.id));

            } else {
                if (!isReentered) {
                    LOGGER.debug("File does not exist and not re-entered, triggering validation");
                    telemetryHelper.recordRetryPrompted(TelemetryConstants.TELEMETRY_DELETE_FILE, startTime,
                            TelemetryHelper.safeTagMap(TelemetryConstants.TELEMETRY_TAG_TARGET, target, TelemetryConstants.TELEMETRY_TAG_REASON, TelemetryConstants.TELEMETRY_REASON_FILE_NOT_EXIST));

                    // Check allowRetry flag
                    if (Boolean.TRUE.equals(allowRetry)) {
                        return reenterValidate(target);
                    } else {
                        // Return direct error without retry
                        ResponseUtil.errorResponse(false, "The specified file does not exist", payload);
                        return new ExtensionResponseBuilder()
                                .success(payload, null, null)
                                .build();
                    }
                }
                LOGGER.error("File does not exist even after re-entry");
                ResponseUtil.errorResponse(false, Constants.PLEASE_PROVIDE_VALID_TARGET_PATH, payload);
                telemetryHelper.recordValidationError(TelemetryConstants.TELEMETRY_DELETE_FILE, startTime,
                        TelemetryConstants.TELEMETRY_ERROR_FILE_NOT_FOUND_AFTER_REENTRY,
                        TelemetryHelper.safeTagMap(TelemetryConstants.TELEMETRY_TAG_TARGET, target, TelemetryConstants.TELEMETRY_TAG_IS_REENTERED, String.valueOf(isReentered)));
            }

            return new ExtensionResponseBuilder()
                    .success(payload, null, null)
                    .build();

        } catch (GraphServiceException gse) {
            LOGGER.error("GraphServiceException occurred while deleting file", gse);
            Map<String, Object> errorPayload = new HashMap<>();
            ResponseUtil.errorResponse(false, Constants.PLEASE_PROVIDE_VALID_TARGET_PATH, errorPayload);
            telemetryHelper.recordError(TelemetryConstants.TELEMETRY_DELETE_FILE, startTime, gse,
                    TelemetryHelper.safeTagMap(TelemetryConstants.TELEMETRY_TAG_TARGET, target, TelemetryConstants.TELEMETRY_TAG_ERROR_TYPE, "graph_service_exception"));
            return new ExtensionResponseBuilder()
                    .success(errorPayload, null, null)
                    .build();

        } catch (Exception e) {
            LOGGER.error("Unexpected error occurred while deleting file", e);
            Map<String, Object> errorPayload = new HashMap<>();
            ResponseUtil.errorResponse(false, "Unexpected error: " + e.getMessage(), errorPayload);
            telemetryHelper.recordError(TelemetryConstants.TELEMETRY_DELETE_FILE, startTime, e,
                    TelemetryHelper.safeTagMap(TelemetryConstants.TELEMETRY_TAG_TARGET, target, TelemetryConstants.TELEMETRY_TAG_ERROR_TYPE, "unexpected_exception"));
            return new ExtensionResponseBuilder()
                    .success(errorPayload, null, null)
                    .build();
        }
    }


    /**
     * Initiates validation for re-entry of a target file path.
     * This method checks validations for the target file and returns an appropriate
     * response based on the validation results. If validation fails, it returns
     */
    public ExtensionResponse reenterValidate(String target) {
        ExtensionResponse validationRes = validationTypeUtil.checkValidationsWithErrorMessage(Validator.ValidationResource.TARGET_FILE, SubCatalogConstants.CONFIRM_REENTER_TARGET_FILE_DELETION, target, "");

        if (validationRes != null) return validationRes;

        ExtensionResponse.Error error = new ErrorBuilder()
                .exceptionType(ExtensionResponse.Error.ExceptionType.INPUT_ERROR).errorMessage(Constants.PROVIDE_VALID_PATH_FILE_NOT_FOUND)
                .build();
        return new ExtensionResponseBuilder()
                .failure(error)
                .build();
    }


    /**
     * This Method will fetch file exist in given path and update payload , if file found then payload will be updated with true and DriveItem
     * and if File not found then payload will be updated with false and message File Not Found.
     *
     * @param target  Absolute path of file to fetch details
     * @param payload It is Map which hold collected information
     */

    public void checkIfFileExist(String target, Map<String, Object> payload) {
        try {
            if (target != null) {
                DriveItem driveItem;
                ResponseUtil.addDriveItemMessage(false, false, Constants.PLEASE_PROVIDE_VALID_SOURCE_PATH, payload);
                if (FileUtil.isPersonalDrive(target)) {
                    String path = FileUtil.getRelativeFilePath(target);
                    driveItem = !path.isEmpty()
                            ? FileUtil.getRootUserRequestBuilder(createProvider()).itemWithPath(path).buildRequest().get()
                            : FileUtil.getRootUserRequestBuilder(createProvider()).buildRequest().get();
                    if (driveItem != null) {
                        ResponseUtil.addDriveItem(true, driveItem, payload);
                    }
                }
                if (FileUtil.checkDriveIDContains(target)) {
                    List<String> content = FileUtil.getDriveIDAndPath(target);
                    driveItem = FileUtil.getSharedUserRequestBuilder(createProvider(), content.get(0)).itemWithPath(content.get(1)).buildRequest().get();

                    if (driveItem != null) {
                        ResponseUtil.addDriveItem(true, driveItem, payload);
                    }
                }
            } else {
                ResponseUtil.addDriveItemMessage(false, false, Constants.PLEASE_PROVIDE_VALID_SOURCE_PATH, payload);
            }

        } catch (GraphServiceException | IllegalArgumentException cause) {
            LOGGER.info(cause.getMessage(), cause);
            ResponseUtil.addDriveItemMessage(false, false, Objects.requireNonNull(cause.getMessage())
                    .contains(Constants.ITEM_NOT_FOUND)
                    ? Constants.FILE_NOT_FOUND
                    : Constants.PLEASE_PROVIDE_VALID_TARGET_PATH, payload);
        }
    }

    /**
     * Renames a file in OneDrive.
     * Handles validation and re-entry for confirmation.
     *
     * @param source            The path of the file to rename
     * @param targetFileName    The new name for the file
     * @param isSourceReentered Whether the source path was re-entered after validation
     * @param isTargetReentered Whether the target name was re-entered after validation
     * @return ExtensionResponse containing the rename result or an error
     */
    public ExtensionResponse renameFile(String source, String targetFileName, boolean isSourceReentered, boolean isTargetReentered) {
        Map<String, Object> payload = new HashMap<>();
        long startTime = System.currentTimeMillis();
        telemetryHelper.incrementCount(TelemetryConstants.TELEMETRY_RENAME_FILE);

        LOGGER.info("renameFile - Starting rename operation | source: {}, targetFileName: {}, isSourceReentered: {}, isTargetReentered: {}",
                source, targetFileName, isSourceReentered, isTargetReentered);

        try {
            String driveId = null;
            String path;

            if (FileUtil.checkDriveIDContains(source)) {
                LOGGER.debug("renameFile - Source is a shared drive path");
                List<String> content = FileUtil.getDriveIDAndPath(source);
                driveId = content.get(0);
                path = content.get(1);
            } else if (FileUtil.isPersonalDrive(source)) {
                LOGGER.debug("renameFile - Source is a personal drive path");
                path = FileUtil.getRelativeFilePath(source);
            } else {
                telemetryHelper.recordValidationError(TelemetryConstants.TELEMETRY_RENAME_FILE, startTime,
                        "Invalid drive path", TelemetryHelper.safeTagMap(TelemetryConstants.TELEMETRY_TAG_SOURCE, source));
                if (!isSourceReentered) {
                    telemetryHelper.recordRetryPrompted(TelemetryConstants.TELEMETRY_RENAME_FILE, startTime,
                            TelemetryHelper.safeTagMap(TelemetryConstants.TELEMETRY_TAG_SOURCE, source, TelemetryConstants.TELEMETRY_TAG_TARGET, targetFileName, "stage", "invalid_path"));
                    return reenterValidateRename(source, targetFileName, true, "Source file not found");
                }
                ResponseUtil.errorResponse(false, Constants.PLEASE_PROVIDE_VALID_SOURCE_PATH, payload);
                return new ExtensionResponseBuilder().success(payload, null, null).build();
            }

            Map<String, Object> response = new HashMap<>();
            getFileInfo(driveId, path, response);

            if ((boolean) response.get(Constants.IS_FILE_EXIST)) {
                LOGGER.debug("renameFile - Source file exists, proceeding with rename validation");
                DriveItem renameFile = (DriveItem) response.get(Constants.DRIVE_ITEM);
                String fileExtension = FileUtil.getBaseName(renameFile.name).get(1);
                String targetExtension = FileUtil.getBaseName(targetFileName).get(1);

                if (fileExtension != null && targetExtension == null) {
                    LOGGER.warn("renameFile - Target filename missing extension | source: {}, target: {}", source, targetFileName);
                    ResponseUtil.errorResponse(false, "File extension is not available", payload);
                    telemetryHelper.recordValidationError(TelemetryConstants.TELEMETRY_RENAME_FILE, startTime,
                            "Missing file extension in target", TelemetryHelper.safeTagMap(
                                    TelemetryConstants.TELEMETRY_TAG_SOURCE, source, TelemetryConstants.TELEMETRY_TAG_TARGET, targetFileName, TelemetryConstants.TELEMETRY_TAG_REASON, "missing_target_extension"));

                    if (!isSourceReentered) {
                        LOGGER.debug("renameFile - Prompting for re-entry due to missing extension");
                        telemetryHelper.recordRetryPrompted(TelemetryConstants.TELEMETRY_RENAME_FILE, startTime,
                                TelemetryHelper.safeTagMap(TelemetryConstants.TELEMETRY_TAG_SOURCE, source, TelemetryConstants.TELEMETRY_TAG_TARGET, targetFileName, "stage", "extension_check"));
                        return reenterValidateRename(source, targetFileName, false, "Source File extension is not available");
                    }
                    return new ExtensionResponseBuilder().success(payload, null, null).build();
                }

                if (fileExtension != null && !fileExtension.equals(targetExtension)) {
                    LOGGER.warn("renameFile - Extension mismatch | source ext: {}, target ext: {}", fileExtension, targetExtension);
                    telemetryHelper.recordValidationError(TelemetryConstants.TELEMETRY_RENAME_FILE, startTime,
                            "Mismatched file extension", TelemetryHelper.safeTagMap(
                                    TelemetryConstants.TELEMETRY_TAG_SOURCE, source, TelemetryConstants.TELEMETRY_TAG_TARGET, targetFileName, "expected_extension", fileExtension));

                    if (!isTargetReentered) {
                        telemetryHelper.recordRetryPrompted(TelemetryConstants.TELEMETRY_RENAME_FILE, startTime,
                                TelemetryHelper.safeTagMap(TelemetryConstants.TELEMETRY_TAG_SOURCE, source, TelemetryConstants.TELEMETRY_TAG_TARGET, targetFileName, "stage", "mismatch_extension"));
                        return reenterValidateRename(targetFileName, source, false, "Target File extension is not available");
                    }

                    ResponseUtil.errorResponse(false, "File extension is Invalid, correct file extension is : " + fileExtension, payload);
                    return new ExtensionResponseBuilder().success(payload, null, null).build();
                }

                DriveItem driveItem = new DriveItem();
                driveItem.name = targetFileName;
                LOGGER.debug("renameFile - Sending PATCH request to rename file at path: {}", path);
                if (driveId != null) {
                    FileUtil.getSharedUserRequestBuilder(createProvider(), driveId)
                            .itemWithPath(path)
                            .buildRequest()
                            .patch(driveItem);
                } else {
                    FileUtil.getRootUserRequestBuilder(createProvider())
                            .itemWithPath(path)
                            .buildRequest()
                            .patch(driveItem);
                }

                ResponseUtil.errorResponse(true, "Renaming is successful", payload);
                telemetryHelper.recordSuccess(TelemetryConstants.TELEMETRY_RENAME_FILE, startTime,
                        TelemetryHelper.safeTagMap(TelemetryConstants.TELEMETRY_TAG_SOURCE, source, TelemetryConstants.TELEMETRY_TAG_TARGET, targetFileName));
            } else {
                telemetryHelper.recordValidationError(TelemetryConstants.TELEMETRY_RENAME_FILE, startTime,
                        "Source file not found", TelemetryHelper.safeTagMap(TelemetryConstants.TELEMETRY_TAG_SOURCE, source));
                if (!isSourceReentered) {
                    telemetryHelper.recordRetryPrompted(TelemetryConstants.TELEMETRY_RENAME_FILE, startTime,
                            TelemetryHelper.safeTagMap(TelemetryConstants.TELEMETRY_TAG_SOURCE, source, TelemetryConstants.TELEMETRY_TAG_TARGET, targetFileName, "stage", "source_missing"));
                    return reenterValidateRename(source, targetFileName, true, "Source file not found");
                }
                ResponseUtil.errorResponse(false, Constants.FILE_NOT_FOUND, payload);
            }

            return new ExtensionResponseBuilder().success(payload, null, null).build();
        } catch (GraphServiceException | IllegalArgumentException ex) {
            LOGGER.error("renameFile - Exception occurred: {}", ex.getMessage(), ex);
            String message = ex.getMessage() != null && ex.getMessage().contains(Constants.ITEM_NOT_FOUND)
                    ? Constants.FILE_NOT_FOUND
                    : "Error occurred during renaming file";
            ResponseUtil.errorResponse(false, message, payload);
            telemetryHelper.recordError(TelemetryConstants.TELEMETRY_RENAME_FILE, startTime, ex,
                    TelemetryHelper.safeTagMap(TelemetryConstants.TELEMETRY_TAG_SOURCE, source, TelemetryConstants.TELEMETRY_TAG_TARGET, targetFileName, TelemetryConstants.TELEMETRY_TAG_ERROR_TYPE, ex.getClass().getSimpleName()));
        }

        LOGGER.debug("renameFile - Returning final response after exception handling");
        return new ExtensionResponseBuilder().success(payload, null, null).build();
    }


    /**
     * Reads a text file from OneDrive and returns its content.
     * This method performs several validation steps:
     * 1. Verifies the source path is a personal drive path
     * 2. Checks if the file exists at the specified path
     * 3. Validates the file size (rejects files larger than 30MB)
     * 4. Confirms the file has a .txt extension
     * 5. Reads and returns the file content if all validations pass
     * If any validation fails and this is not a re-entered request, it will prompt
     * for re-entry with appropriate error messages. If validation fails on a re-entered
     * request, it will return an error response.
     * The method uses telemetry to track success, validation errors, and exceptions.
     *
     * @param source      The path of the text file to read (e.g., /Documents/notes.txt)
     * @param isReentered Whether this is a re-entered request after validation failure
     * @return ExtensionResponse containing:
     * - Success: true/false indicating if the operation succeeded
     * - Message: Status message or error description
     * - Content: The text content of the file (if successful)
     */
    public ExtensionResponse readTxtFile(String source, boolean isReentered) {
        Map<String, Object> payload = new HashMap<>();
        long startTime = System.currentTimeMillis();
        telemetryHelper.incrementCount("Onedrive.readTxtFile");
        LOGGER.info("readTxtFile - Starting read operation for source: {}", source);

        try {
            if (FileUtil.isPersonalDrive(source) || FileUtil.checkDriveIDContains(source)) {
                String driveId = null;
                String path;
                if (FileUtil.checkDriveIDContains(source)) {
                    List<String> content = FileUtil.getDriveIDAndPath(source);
                    driveId = content.get(0);
                    path = content.get(1);
                } else {
                    path = FileUtil.getRelativeFilePath(source);
                }
                Map<String, Object> response = new HashMap<>();
                getFileInfo(driveId, path, response);

                if ((boolean) response.get(Constants.IS_FILE_EXIST)) {
                    DriveItem driveItem = (DriveItem) response.get(Constants.DRIVE_ITEM);

                    if (driveItem != null && driveItem.folder == null && driveItem.size != null && driveItem.size > (1024 * 1024 * 30)) {
                        LOGGER.debug("readTxtFile - File too large to read: {} bytes", driveItem.size);
                        ResponseUtil.errorResponse(false, FILE_SIZE_IS_TOO_LARGE_TO_READ, payload);
                        telemetryHelper.recordValidationError("Onedrive.readTxtFile", startTime,
                                "File too large", TelemetryHelper.safeTagMap("source", source, "size", String.valueOf(driveItem.size)));
                    } else if (driveItem != null && driveItem.name != null
                            && driveItem.folder == null
                            && FileUtil.getBaseName(driveItem.name).get(1) != null
                            && driveItem.id != null
                            && (FileUtil.getBaseName(driveItem.name).get(1).equalsIgnoreCase("txt")
                                || FileUtil.getBaseName(driveItem.name).get(1).equalsIgnoreCase("md"))) {

                        LOGGER.info("readTxtFile - Valid TXT/MD file found. Reading content.");
                        InputStream inputStream = FileUtil.getUserRequestBuilderWithItemId(createProvider(), driveId, driveItem.id)
                                .content().buildRequest().get();
                        payload.put("Content", FileUtil.readFile(inputStream));
                        payload.put(Constants.MESSAGE, Constants.FILE_FOUND);
                        payload.put(Constants.SUCCESS, true);

                        telemetryHelper.recordSuccess("Onedrive.readTxtFile", startTime,
                                TelemetryHelper.safeTagMap("source", source, "filename", driveItem.name));
                    } else {
                        LOGGER.debug("readTxtFile - Invalid file name or extension");
                        if (!isReentered) {
                            telemetryHelper.recordRetryPrompted("Onedrive.readTxtFile", startTime,
                                    TelemetryHelper.safeTagMap("source", source, "reason", "invalid_file_extension"));
                            return reenterValidation(source, Constants.INVALID_FILE_NAME_OR_INVALID_FILE_EXTENSION);
                        }
                        ResponseUtil.errorResponse(false, Constants.INVALID_FILE_NAME_OR_INVALID_FILE_EXTENSION, payload);
                        telemetryHelper.recordValidationError("Onedrive.readTxtFile", startTime,
                                "Invalid file name or extension", TelemetryHelper.safeTagMap("source", source));
                    }
                } else {
                    LOGGER.debug("readTxtFile - File not found");
                    if (!isReentered) {
                        telemetryHelper.recordRetryPrompted("Onedrive.readTxtFile", startTime,
                                TelemetryHelper.safeTagMap("source", source, "reason", "file_not_found"));
                        return reenterValidation(source, Constants.FILE_SPECIFIC_FILE_NOT_FOUND);
                    }
                    ResponseUtil.errorResponse(false, Constants.FILE_SPECIFIC_FILE_NOT_FOUND, payload);
                    telemetryHelper.recordValidationError("Onedrive.readTxtFile", startTime,
                            "File not found after reentry", TelemetryHelper.safeTagMap("source", source));
                }
            } else {
                LOGGER.debug("readTxtFile - Invalid personal drive path");
                if (!isReentered) {
                    telemetryHelper.recordRetryPrompted("Onedrive.readTxtFile", startTime,
                            TelemetryHelper.safeTagMap("source", source, "reason", "not_personal_drive"));
                    return reenterValidation(source, Constants.FILE_SPECIFIC_FILE_NOT_FOUND);
                }
                ResponseUtil.errorResponse(false, Constants.FILE_SPECIFIC_FILE_NOT_FOUND, payload);
                telemetryHelper.recordValidationError("Onedrive.readTxtFile", startTime,
                        "Invalid drive path", TelemetryHelper.safeTagMap("source", source));
            }

            return new ExtensionResponseBuilder()
                    .success(payload, null, null)
                    .build();

        } catch (GraphServiceException | IllegalArgumentException ex) {
            LOGGER.error("readTxtFile - Exception occurred", ex);
            String msg = ex.getMessage() != null && ex.getMessage().contains(Constants.ITEM_NOT_FOUND)
                    ? Constants.FILE_SPECIFIC_FILE_NOT_FOUND
                    : "Error occurred during reading attachment";
            ResponseUtil.errorResponse(false, msg, payload);
            telemetryHelper.recordError("Onedrive.readTxtFile", startTime, ex,
                    TelemetryHelper.safeTagMap("source", source, "error_type", ex.getClass().getSimpleName()));
        }

        return new ExtensionResponseBuilder()
                .success(payload, null, null)
                .build();
    }

    /**
     * Creates a new text file in OneDrive with the specified content.
     * <p>
     * This method uploads text content as a .txt file to the specified target path in OneDrive.
     * It performs several validation steps:
     * 1. Verifies the target path is a personal drive path
     * 2. Extracts the parent folder path and checks if it exists
     * 3. Validates that the target filename has a .txt extension
     * 4. Checks if a file with the same name already exists to prevent overwrites
     * 5. Creates and uploads the text file if all validations pass
     * <p>
     * If validation fails and this is not a re-entered request, it will prompt
     * for re-entry with appropriate error messages. If validation fails on a re-entered
     * request, it will return an error response.
     *
     * @param fileContent The text content to write to the file
     * @param target      The path where the text file should be created (must end with .txt)
     * @param isReentered Whether this is a re-entered request after validation failure
     * @return ExtensionResponse containing:
     * - Success: true/false indicating if the operation succeeded
     * - Message: Status message or error description
     * - File: File entity representing the created file (if successful)
     */
    public ExtensionResponse uploadTxtFile(String fileContent, String target, boolean isReentered) {
        // Default to allowRetry = true for backward compatibility
        return uploadTxtFile(fileContent, target, isReentered, true);
    }

    /**
     * Creates a new text file in OneDrive with the specified content and retry option.
     * <p>
     * This method uploads text content as a .txt file to the specified target path in OneDrive.
     * It performs several validation steps:
     * 1. Verifies the target path is a personal drive path
     * 2. Extracts the parent folder path and checks if it exists
     * 3. Validates that the target filename has a .txt extension
     * 4. Checks if a file with the same name already exists to prevent overwrites
     * 5. Creates and uploads the text file if all validations pass
     * <p>
     * If validation fails and allowRetry is true, it will prompt for re-entry.
     * If validation fails and allowRetry is false, it will return a direct error.
     *
     * @param fileContent The text content to write to the file
     * @param target      The path where the text file should be created (must end with .txt)
     * @param isReentered Whether this is a re-entered request after validation failure
     * @param allowRetry  Whether to allow retry on validation failure
     * @return ExtensionResponse containing:
     * - Success: true/false indicating if the operation succeeded
     * - Message: Status message or error description
     * - File: File entity representing the created file (if successful)
     */
    public ExtensionResponse uploadTxtFile(String fileContent, String target, boolean isReentered, Boolean allowRetry) {
        LOGGER.info("uploadTxtFile - Starting text file upload operation | target: {}, contentLength: {}, isReentered: {}",
                target, fileContent != null ? fileContent.length() : 0, isReentered);

        Map<String, Object> payload = new HashMap<>();
        long startTime = System.currentTimeMillis();
        telemetryHelper.incrementCount("Onedrive.uploadTxtFile");

        try {
            if (FileUtil.isPersonalDrive(target)) {
                String path = FileUtil.getRelativeFilePath(target);
                String parentPath = FileUtil.getParentPath(path);

                Map<String, Object> response = new HashMap<>();
                getFileInfo(null, parentPath, response);
                processUploadFile(fileContent, target, payload, response);

                if (!(boolean) response.get(Constants.SUCCESS)) {
                    if (!isReentered) {
                        LOGGER.debug("uploadTxtFile - First attempt, prompting for re-entry");
                        telemetryHelper.recordRetryPrompted("Onedrive.uploadTxtFile", startTime,
                                TelemetryHelper.safeTagMap("target", target, "reason", "parent_folder_not_found"));

                        // Check allowRetry flag
                        if (Boolean.TRUE.equals(allowRetry)) {
                            return reenterValidateWriteAsText(target, fileContent);
                        } else {
                            // Return direct error without retry
                            return new ExtensionResponseBuilder()
                                    .failure((String) response.get(Constants.MESSAGE))
                                    .build();
                        }
                    }
                    telemetryHelper.recordValidationError("Onedrive.uploadTxtFile", startTime,
                            "Parent folder not found after reentry",
                            TelemetryHelper.safeTagMap("target", target, "isReentered", String.valueOf(isReentered)));
                } else {
                    LOGGER.info("uploadTxtFile - Text file upload successful to path: {}", target);
                    telemetryHelper.recordSuccess("Onedrive.uploadTxtFile", startTime,
                            TelemetryHelper.safeTagMap("target", target, "parentPath", parentPath));
                }

            } else {
                if (!isReentered) {
                    LOGGER.debug("uploadTxtFile - First attempt with non-personal drive path, prompting for re-entry");
                    telemetryHelper.recordRetryPrompted("Onedrive.uploadTxtFile", startTime,
                            TelemetryHelper.safeTagMap("target", target, "reason", "not_personal_drive"));

                    // Check allowRetry flag
                    if (Boolean.TRUE.equals(allowRetry)) {
                        return reenterValidateWriteAsText(target, fileContent);
                    } else {
                        // Return direct error without retry
                        return new ExtensionResponseBuilder()
                                .failure("Please provide a valid personal drive path")
                                .build();
                    }
                }

                LOGGER.error("uploadTxtFile - Invalid target path (not personal drive) after re-entry: {}", target);
                telemetryHelper.recordValidationError("Onedrive.uploadTxtFile", startTime,
                        "Invalid target path - not a personal drive",
                        TelemetryHelper.safeTagMap("target", target));
                ResponseUtil.errorResponse(false, Constants.PLEASE_PROVIDE_VALID_FOLDER_PATH, payload);
            }

            return new ExtensionResponseBuilder()
                    .success(payload, null, null)
                    .build();

        } catch (GraphServiceException | IllegalArgumentException | IOException cause) {
            LOGGER.error("uploadTxtFile - Exception occurred: {}", cause.getMessage(), cause);
            String errorType = cause.getClass().getSimpleName();
            String errorMsg = cause.getMessage();

            LOGGER.debug("uploadTxtFile - Error details: type={}, message={}", errorType, errorMsg);

            telemetryHelper.recordError("Onedrive.uploadTxtFile", startTime, cause,
                    TelemetryHelper.safeTagMap("target", target, "error_type", errorType));

            ResponseUtil.errorResponse(false,
                    errorMsg != null && errorMsg.contains(Constants.ITEM_NOT_FOUND)
                            ? Constants.FILE_NOT_FOUND
                            : "Error occurred during uploading attachment", payload);
        }

        LOGGER.debug("uploadTxtFile - Returning final response after exception handling");
        return new ExtensionResponseBuilder()
                .success(payload, null, null)
                .build();
    }


    /**
     * Processes the upload of a text file to OneDrive.
     * <p>
     * This helper method handles the actual text file upload process after
     * parent folder validation has been performed. It:
     * 1. Verifies the parent folder exists and is actually a folder
     * 2. Delegates to uploadTextFileHelper for the actual file creation and upload
     * 3. Sets appropriate error responses if validation fails
     *
     * @param fileContent The text content to upload
     * @param target      The target path for the text file
     * @param payload     The map to populate with the response data
     * @param response    The map containing parent folder information from previous validation
     * @throws IOException If an error occurs during file handling
     */
    private void processUploadFile(String fileContent, String target, Map<String, Object> payload, Map<String, Object> response) throws IOException {
        if ((boolean) response.get(Constants.IS_FILE_EXIST)) {
            DriveItem driveItem = (DriveItem) response.get(Constants.DRIVE_ITEM);
            if (driveItem.name != null && driveItem.folder != null && driveItem.id != null) {
                uploadTextFileHelper(fileContent, target, payload, driveItem);
            }
        } else {
            ResponseUtil.errorResponse(false, Constants.PLEASE_PROVIDE_VALID_FOLDER_PATH, payload);
        }
    }

    /**
     * Creates and uploads a text file to OneDrive.
     * <p>
     * This helper method performs the actual text file creation and upload:
     * 1. Extracts the filename from the target path and validates it has a .txt extension
     * 2. Creates a temporary file with the provided content
     * 3. Checks if a file with the same name already exists at the target location
     * 4. Uploads the file if it doesn't exist, or returns an "already exists" message if it does
     * 5. Converts the uploaded DriveItem to a File entity for the response
     *
     * @param fileContent The text content to write to the file
     * @param target      The full target path including filename
     * @param payload     The map to populate with the response data
     * @param driveItem   The DriveItem representing the parent folder
     * @throws IOException If an error occurs during file creation or upload
     */
    private void uploadTextFileHelper(String fileContent, String target, Map<String, Object> payload, DriveItem driveItem) throws IOException {
        String[] stream = target.split("/");
        String lastSegment = stream[stream.length - 1];
        String fileName = (lastSegment.contains(".txt") || lastSegment.contains(".md")) ? lastSegment : null;
        if (fileName != null) {
            java.io.File javaFile = FileUtil.createTxtFile(fileName, fileContent);
            String path = FileUtil.getRelativeFilePath(target);
            getFileInfo(null, path, payload);
            if (!(boolean) payload.get(Constants.IS_FILE_EXIST)) {
                DriveItem uploadFile = FileUtil.getUserRequestBuilderWithItemId(createProvider(), null, driveItem.id)
                        .itemWithPath(javaFile.getName())
                        .content().buildRequest()
                        .put(DriveUtil.getByteArray(javaFile));
                if (uploadFile != null) {
                    app.krista.extensions.essentials.cloudfiles.onedrive.catalog.entities.File fileEntity = CatalogTypes.fromFile(new FileImpl(uploadFile, null, null));
                    ResponseUtil.fileEntityResponse(fileEntity, true, Constants.FILE_ID_UPLOADED_SUCCESSFULLY, payload);
                }
            } else {
                app.krista.extensions.essentials.cloudfiles.onedrive.catalog.entities.File existedFile = CatalogTypes.fromFile(new FileImpl((DriveItem) payload.get(Constants.DRIVE_ITEM), null, null));
                ResponseUtil.fileEntityResponse(existedFile, false, Constants.FILE_ALREADY_EXIST, payload);
            }
        } else {
            ResponseUtil.errorResponse(false, INVALID_FILE_NAME_PLEASE_PROVIDE_FILE_EXTENSION_AS_TXT, payload);
        }
    }

    /**
     * Retrieves information about a file or folder and populates the provided response map.
     * <p>
     * This method attempts to retrieve a DriveItem from OneDrive based on the provided
     * drive ID and path. It populates the response map with:
     * - IS_FILE_EXIST: Boolean indicating if the file/folder exists
     * - DRIVE_ITEM: The DriveItem object if found
     * - SUCCESS: Boolean indicating if the operation succeeded
     * <p>
     * If an error occurs, it sets IS_FILE_EXIST to false and adds an appropriate
     * error message to the response.
     *
     * @param driveId  The drive ID for shared drives, or null for personal drive
     * @param path     The relative path to the file or folder
     * @param response The map to populate with the file information
     */
    public void getFileInfo(String driveId, String path, Map<String, Object> response) {
        try {
            DriveItem driveItem = driveId != null
                    ? getShareRootDriveItem(path, driveId)
                    : getPersonalDriveItem(path);

            response.put(Constants.DRIVE_ITEM, driveItem);
            response.put(Constants.SUCCESS, true);
            response.put(Constants.IS_FILE_EXIST, true);
        } catch (GraphServiceException | IllegalArgumentException cause) {
            LOGGER.info(cause.getMessage(), cause);
            response.put(Constants.IS_FILE_EXIST, false);
            ResponseUtil.errorResponse(false, Objects.requireNonNull(cause.getMessage())
                    .contains(Constants.ITEM_NOT_FOUND)
                    ? Constants.FILE_NOT_FOUND
                    : Constants.PLEASE_PROVIDE_VALID_SOURCE_PATH, response);
        }
    }

    /**
     * Retrieves a DriveItem from a shared drive.
     * <p>
     * This helper method gets a DriveItem from a shared drive using the provided
     * drive ID and path. If the path is empty, it returns the root of the drive.
     *
     * @param path    The path to the item within the shared drive
     * @param driveId The ID of the shared drive
     * @return The DriveItem at the specified path, or null if not found
     */
    @Nullable
    private DriveItem getShareRootDriveItem(String path, String driveId) {
        return path != null && !path.isEmpty()
                ? FileUtil.getSharedUserRequestBuilder(createProvider(), driveId).itemWithPath(path).buildRequest().get()
                : FileUtil.getSharedUserRequestBuilder(createProvider(), driveId).buildRequest().get();
    }

    /**
     * Retrieves a DriveItem from the personal drive.
     * <p>
     * This helper method gets a DriveItem from the user's personal drive using
     * the provided path. If the path is empty, it returns the root of the drive.
     *
     * @param path The path to the item within the personal drive
     * @return The DriveItem at the specified path, or null if not found
     */
    @Nullable
    private DriveItem getPersonalDriveItem(String path) {
        return path != null && !path.isEmpty()
                ? FileUtil.getRootUserRequestBuilder(createProvider()).itemWithPath(path).buildRequest().get()
                : FileUtil.getRootUserRequestBuilder(createProvider()).buildRequest().get();
    }

    /**
     * Creates a re-entry validation response for text file read operations.
     * <p>
     * This helper method creates an ExtensionResponse that prompts the user to
     * re-enter or confirm a file path after validation failure during a read operation.
     * It uses the ValidationTypeUtil to perform validation checks and generate
     * appropriate responses.
     *
     * @param source       The original source path that failed validation
     * @param errorMessage The error message explaining the validation failure
     * @return ExtensionResponse configured for re-entry, or an error response if validation fails
     */
    private ExtensionResponse reenterValidation(String source, String errorMessage) {
        LOGGER.debug("DriveTextFileManager.reenterValidation - Starting validation for source path: {}", source);

        try {
            LOGGER.debug("DriveTextFileManager.reenterValidation - Checking validations with ValidationTypeUtil");
            ExtensionResponse validationRes = validationTypeUtil.checkValidationsReadText(
                    Validator.ValidationResource.SOURCE_FILE,
                    SubCatalogConstants.CONFIRM_REENTER_SOURCE_FILE_READ,
                    source, errorMessage);

            if (validationRes != null) {
                LOGGER.debug("DriveTextFileManager.reenterValidation - Validation response received, returning it");
                return validationRes;
            }

            LOGGER.debug("DriveTextFileManager.reenterValidation - No validation response, creating error response");
            ExtensionResponse.Error error = new ErrorBuilder()
                    .exceptionType(ExtensionResponse.Error.ExceptionType.INPUT_ERROR)
                    .errorMessage(Constants.PROVIDE_VALID_PATH_FILE_NOT_FOUND)
                    .build();

            LOGGER.info("DriveTextFileManager.reenterValidation - Returning failure response with error message: {}",
                    Constants.PROVIDE_VALID_PATH_FILE_NOT_FOUND);
            return new ExtensionResponseBuilder()
                    .failure(error)
                    .build();
        } catch (Exception e) {
            LOGGER.error("DriveTextFileManager.reenterValidation - Exception during validation", e);
            ExtensionResponse.Error error = new ErrorBuilder()
                    .exceptionType(ExtensionResponse.Error.ExceptionType.INPUT_ERROR)
                    .errorMessage("Validation error: " + e.getMessage())
                    .build();
            return new ExtensionResponseBuilder()
                    .failure(error)
                    .build();
        }
    }

    /**
     * Creates a re-entry validation response for file rename operations.
     * <p>
     * This helper method creates an ExtensionResponse that prompts the user to
     * re-enter or confirm information for rename operations after validation failure.
     * It validates either the source path or target filename based on which one failed validation.
     *
     * @param source            The source file path
     * @param targetFileName    The target filename
     * @param isSourceReentered Whether the source path is being re-entered (if false, target is being re-entered)
     * @param errorMessage      The error message explaining the validation failure
     * @return ExtensionResponse configured for re-entry, or an error response if validation fails
     */
    public ExtensionResponse reenterValidateRename(String source, String targetFileName, boolean isSourceReentered, String errorMessage) {
        ExtensionResponse validationRes = validationTypeUtil.checkValidationsRename(isSourceReentered ? Validator.ValidationResource.SOURCE_PATH : Validator.ValidationResource.TARGET_FILE_NAME, SubCatalogConstants.CONFIRM_REENTER_SOURCE_FILE_RENAME, source, targetFileName, isSourceReentered, errorMessage);

        if (validationRes != null) return validationRes;

        ExtensionResponse.Error error = new ErrorBuilder()
                .exceptionType(ExtensionResponse.Error.ExceptionType.INPUT_ERROR).errorMessage(Constants.PROVIDE_VALID_PATH_FILE_NOT_FOUND)
                .build();
        return new ExtensionResponseBuilder()
                .failure(error)
                .build();
    }

    /**
     * Initiates validation for re-entry of target path for write operations.
     *
     * @param kristaFile            The file to upload
     * @param target                The target path
     * @param customName            The custom name for the file
     * @param isCustomNameReentered Whether the custom name was re-entered
     * @param errorMessage          The error message to display
     * @return ExtensionResponse for validation or an error
     */
    public ExtensionResponse reenterValidateWrite(app.krista.model.base.File kristaFile, String target, String customName, boolean isCustomNameReentered, String errorMessage) {
        ExtensionResponse validationRes = validationTypeUtil.checkValidationsWrite(isCustomNameReentered ? Validator.ValidationResource.CUSTOM_NAME : Validator.ValidationResource.TARGET_PATH, SubCatalogConstants.CONFIRM_REENTER_TARGET_FILE_WRITE, kristaFile, target, customName, isCustomNameReentered, errorMessage);

        if (validationRes != null) return validationRes;

        ExtensionResponse.Error error = new ErrorBuilder()
                .exceptionType(ExtensionResponse.Error.ExceptionType.INPUT_ERROR).errorMessage(Constants.PROVIDE_VALID_PATH_FILE_NOT_FOUND)
                .build();
        return new ExtensionResponseBuilder()
                .failure(error)
                .build();
    }

    /**
     * Initiates validation for re-entry of target path for text file upload operations.
     *
     * @param target      The target path
     * @param fileContent The file content to upload
     * @return ExtensionResponse for validation or an error
     */
    public ExtensionResponse reenterValidateWriteAsText(String target, String fileContent) {
        ExtensionResponse validationRes = validationTypeUtil.checkValidationsWriteAsText(Validator.ValidationResource.TARGET_PATH, SubCatalogConstants.CONFIRM_REENTER_TARGET_FILE_UPLOAD, target, fileContent);

        if (validationRes != null) return validationRes;

        ExtensionResponse.Error error = new ErrorBuilder()
                .exceptionType(ExtensionResponse.Error.ExceptionType.INPUT_ERROR).errorMessage(Constants.PROVIDE_VALID_PATH_FILE_NOT_FOUND)
                .build();
        return new ExtensionResponseBuilder()
                .failure(error)
                .build();
    }
}