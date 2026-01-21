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
import app.krista.extensions.essentials.cloudfiles.onedrive.OnedriveAttributes;
import app.krista.extensions.essentials.cloudfiles.onedrive.catalog.CatalogTypes;
import app.krista.extensions.essentials.cloudfiles.onedrive.catalog.FileHandlers.TelemetryHelper;
import app.krista.extensions.essentials.cloudfiles.onedrive.catalog.validators.Validator;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.connectors.GraphServiceClientProvider;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.connectors.GraphServiceClientProviderFactory;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.util.*;
import com.microsoft.graph.http.GraphServiceException;
import com.microsoft.graph.models.DriveItem;
import com.microsoft.graph.models.DriveItemInviteParameterSet;
import com.microsoft.graph.models.DriveRecipient;
import com.microsoft.graph.requests.UserRequestBuilder;
import org.jvnet.hk2.annotations.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.*;

/**
 * Data Access Object for sharing OneDrive files with other users.
 * Provides functionality to share files with specified permissions and handle validation.
 */
@Service
public class SharedFileDao {

    private static final Logger LOGGER = LoggerFactory.getLogger(SharedFileDao.class);
    public static final String ERROR_OCCURRED_DURING_SHARING_FILE = "Error occurred during sharing file";
    public static final String INVITE_MAIL = "We couldn't share the file or folder due to an invalid email address. Please check the email address and try again.";
    public static final String FILE_HAS_BEEN_SHARED_SUCCESSFULLY = "File has been shared successfully";
    public static final String ERROR_CAUSE_WHILE_SHARING_FILE = "Error cause while sharing file.";
    private final GraphServiceClientProvider provider;
    private final OperationsManagementDao operationsManagementDao;
    private final OnedriveAttributes onedriveAttributes;
    private final ValidationTypeUtil validationTypeUtil;
    private final TelemetryHelper telemetryHelper;

    @Inject
    public SharedFileDao(GraphServiceClientProviderFactory factory, OperationsManagementDao operationsManagementDao,
                         OnedriveAttributes onedriveAttributes, ValidationTypeUtil validationTypeUtil, TelemetryHelper telemetryHelper) {
        this(factory.create(), operationsManagementDao, onedriveAttributes, validationTypeUtil, telemetryHelper);
    }

    /**
     * Constructs a SharedFileDao with provided dependencies.
     *
     * @param provider                Provider for Graph Service Client
     * @param operationsManagementDao DAO for file operations management
     * @param onedriveAttributes      OneDrive configuration attributes
     * @param validationTypeUtil      Utility for validation type operations
     * @param telemetryHelper         Helper for telemetry recording
     */
    public SharedFileDao(GraphServiceClientProvider provider, OperationsManagementDao operationsManagementDao,
                         OnedriveAttributes onedriveAttributes, ValidationTypeUtil validationTypeUtil, TelemetryHelper telemetryHelper) {
        this.provider = provider;
        this.operationsManagementDao = operationsManagementDao;
        this.onedriveAttributes = onedriveAttributes;
        this.validationTypeUtil = validationTypeUtil;
        this.telemetryHelper = telemetryHelper;
    }

    /**
     * Shares a file with specified users.
     * Validates the source file existence and processes sharing with appropriate permissions.
     * Handles re-entry for validation if needed.
     *
     * @param source      The path of the file to share
     * @param share       List of users to share the file with, including their permissions
     * @param isReentered Whether this is a re-entry after validation
     * @return ExtensionResponse containing the result of the operation
     */
    public ExtensionResponse shareFile(String source, List<Map<String, Object>> share, boolean isReentered) {
        Map<String, Object> payload = new HashMap<>();
        long startTime = System.currentTimeMillis();
        telemetryHelper.incrementCount("OneDrive.shareFile");

        LOGGER.info("shareFile - Starting file sharing operation | source: {}, shareCount: {}, isReentered: {}",
                source, share != null ? share.size() : 0, isReentered);

        try {
            if (source != null) {
                LOGGER.debug("shareFile - Source path is not null, checking if file exists");
                operationsManagementDao.checkIfFileExist(source, payload);

                if ((boolean) payload.get(Constants.IS_FILE_EXIST)) {
                    processSharing(source, share, payload);
                    LOGGER.debug("shareFile - Sharing process completed, payload: {}", payload);

                    telemetryHelper.recordSuccess("OneDrive.shareFile", startTime,
                            TelemetryHelper.safeTagMap("source", source, "shareCount", String.valueOf(share.size())));

                    LOGGER.info("shareFile - File shared successfully | source: {}", source);
                    return new ExtensionResponseBuilder()
                            .success(payload, null, null)
                            .build();
                }

                if (!isReentered) {
                    LOGGER.debug("shareFile - First attempt, prompting for re-entry");
                    telemetryHelper.recordRetryPrompted("OneDrive.shareFile", startTime,
                            TelemetryHelper.safeTagMap("source", source, "reason", "file_not_found"));
                    return reenterValidation(source, share);
                }

                ResponseUtil.errorResponse(false, Constants.PLEASE_PROVIDE_VALID_SOURCE_PATH, payload);
                telemetryHelper.recordValidationError("OneDrive.shareFile", startTime,
                        "File not found after reentry", TelemetryHelper.safeTagMap("source", source));
                return new ExtensionResponseBuilder()
                        .success(payload, null, null)
                        .build();

            } else if (!isReentered) {
                LOGGER.debug("shareFile - Source path is null, prompting for re-entry");
                telemetryHelper.recordRetryPrompted("OneDrive.shareFile", startTime,
                        TelemetryHelper.safeTagMap("source", "null", "reason", "null_source_path"));
                return reenterValidation(source, share);
            }

            LOGGER.error("shareFile - Source path is null after re-entry");
            telemetryHelper.recordValidationError("OneDrive.shareFile", startTime,
                    "Source path is null after reentry", TelemetryHelper.safeTagMap("source", "null"));

        } catch (GraphServiceException | IllegalArgumentException cause) {
            LOGGER.error("shareFile - Exception occurred: {}", cause.getMessage(), cause);

            String message = cause.getMessage() != null && cause.getMessage().contains(Constants.ITEM_NOT_FOUND)
                    ? Constants.FILE_NOT_FOUND
                    : ERROR_OCCURRED_DURING_SHARING_FILE;

            LOGGER.debug("shareFile - Setting error response message: {}", message);
            ResponseUtil.errorResponse(false, message, payload);

            telemetryHelper.recordError("OneDrive.shareFile", startTime, cause,
                    TelemetryHelper.safeTagMap("source", source, "error_type", cause.getClass().getSimpleName()));
        }

        LOGGER.debug("shareFile - Returning final response after exception handling");
        return new ExtensionResponseBuilder()
                .success(payload, null, null)
                .build();
    }


    /**
     * Processes the sharing operation for a file.
     * Separates users by permission type and adds appropriate permissions.
     * Updates the payload with the file information and success status.
     *
     * @param source  The path of the file to share
     * @param share   List of users to share the file with, including their permissions
     * @param payload The payload to update with results
     */
    private void processSharing(String source, List<Map<String, Object>> share, Map<String, Object> payload) {
        List<String> usersWithReadPermission = FileUtil.getUsersWithPermission(share, Constants.READ);
        List<String> usersWithWritePermission = FileUtil.getUsersWithPermission(share, Constants.WRITE);

        if (!usersWithReadPermission.isEmpty()) {
            addPermission(Constants.READ, usersWithReadPermission, source, payload);
        }
        if (!usersWithWritePermission.isEmpty()) {
            addPermission(Constants.WRITE, usersWithWritePermission, source, payload);
        }

        DriveItem upadatedDriveItem;
        if (FileUtil.checkDriveIDContains(source)) {
            List<String> content = FileUtil.getDriveIDAndPath(source);
            upadatedDriveItem = getFileInfo(content.get(0), content.get(1));
            payload.put(Constants.FILE, CatalogTypes.fromFile(new FileImpl(upadatedDriveItem, null, onedriveAttributes)));
        } else if (source.contains(Constants.DRIVE_ROOT)) {
            String path = FileUtil.getRelativeFilePath(source);
            upadatedDriveItem = getFileInfo(null, path);
            payload.put(Constants.FILE, CatalogTypes.fromFile(new FileImpl(upadatedDriveItem, null, onedriveAttributes)));
        }
        String message = payload.getOrDefault(Constants.MESSAGE, ERROR_CAUSE_WHILE_SHARING_FILE).toString();
        if (message.contains(Constants.SUCCESS)) {
            payload.put(Constants.SUCCESS, true);
            payload.put(Constants.MESSAGE, FILE_HAS_BEEN_SHARED_SUCCESSFULLY);
        }
    }

    /**
     * Retrieves file information from OneDrive.
     * Handles both personal drive and shared drive paths.
     *
     * @param driveId The drive ID (null for personal drive)
     * @param path    The path within the drive
     * @return DriveItem containing the file information
     */
    public DriveItem getFileInfo(String driveId, String path) {
        return driveId != null
                ? getUserRequestBuilder()
                .drives(driveId).root()
                .itemWithPath(path)
                .buildRequest().expand(Constants.PERMISSIONS).get()
                : getUserRequestBuilder()
                .drive().root()
                .itemWithPath(path)
                .buildRequest().expand(Constants.PERMISSIONS).get();
    }

    /**
     * Adds permission for a list of users to a file.
     * Creates invitation parameters and sends sharing invitations.
     * Updates the payload with the result message.
     *
     * @param role      The permission role (read/write)
     * @param usersList List of user email addresses to share with
     * @param source    The path of the file to share
     * @param payload   The payload to update with results
     */
    private void addPermission(String role, List<String> usersList, String source, Map<String, Object> payload) {
        try {
            List<DriveRecipient> recipientsList = new LinkedList<>();
            for (String user : usersList) {
                DriveRecipient driveRecipient = new DriveRecipient();
                driveRecipient.email = user;
                recipientsList.add(driveRecipient);
            }
            DriveItemInviteParameterSet parameterSet = DriveItemInviteParameterSet
                    .newBuilder()
                    .withRecipients(recipientsList)
                    .withRoles(List.of(role.toLowerCase()))
                    .withRequireSignIn(true)
                    .withSendInvitation(true)
                    .withExpirationDateTime(null)
                    .withPassword(null)
                    .withMessage(null)
                    .withRetainInheritedPermissions(null).build();
            if (FileUtil.checkDriveIDContains(source)) {
                List<String> content = FileUtil.getDriveIDAndPath(source);
                payload.put(Constants.MESSAGE, getDriveItemBuilder(content.get(0), content.get(1), parameterSet));
            } else if (source.contains(Constants.DRIVE_ROOT)) {
                String path = FileUtil.getRelativeFilePath(source);
                payload.put(Constants.MESSAGE, getDriveItemBuilder(null, path, parameterSet));
            }
        } catch (GraphServiceException | IllegalArgumentException cause) {
            LOGGER.error(ERROR_CAUSE_WHILE_SHARING_FILE + " : {}", cause.getMessage(), cause);
            ResponseUtil.errorResponse(false, INVITE_MAIL, payload);
        }
    }

    /**
     * Builds and executes the drive item invitation request.
     * Handles both personal drive and shared drive paths.
     *
     * @param driveID      The drive ID (null for personal drive)
     * @param path         The path within the drive
     * @param parameterSet The invitation parameters
     * @return Success message or error message
     */
    private String getDriveItemBuilder(String driveID, String path, DriveItemInviteParameterSet parameterSet) {
        try {
            if (driveID != null) {
                getUserRequestBuilder()
                        .drives(driveID).root()
                        .itemWithPath(path)
                        .invite(parameterSet).buildRequest().post();
            } else {
                getUserRequestBuilder()
                        .drive().root()
                        .itemWithPath(path)
                        .invite(parameterSet).buildRequest().post();
            }
            return Constants.SUCCESS;
        } catch (GraphServiceException cause) {
            LOGGER.error("Error While Sending Invite : {}", cause.getMessage(), cause);
            return prepareMessage(Objects.requireNonNull(cause.getMessage()));
        }
    }

    /**
     * Prepares an appropriate error message based on the exception message.
     * Translates technical error messages into user-friendly messages.
     *
     * @param message The original error message
     * @return User-friendly error message
     */
    private String prepareMessage(String message) {
        if (message.contains("The action is not allowed by the system")) {
            return Constants.FILE_IS_TO_SHARE_CURRENTLY_RESTRICTED_FOR_ASSISTANCE_CONTACT_YOUR_ADMINISTRATOR_OR_SUPPORT;
        } else if (message.contains("organization's policies")) {
            return INVITE_MAIL;
        } else {
            return ERROR_CAUSE_WHILE_SHARING_FILE;
        }
    }

    /**
     * Gets a user request builder for Graph API operations.
     * Uses default authentication (no specific email or account ID).
     *
     * @return UserRequestBuilder for Graph API operations
     */
    private UserRequestBuilder getUserRequestBuilder() {
        return provider.getUserRequestBuilder(null, null);
    }

    /**
     * Handles validation re-entry for file sharing.
     * Creates a validation response or error response based on validation results.
     *
     * @param source The path of the file to share
     * @param share  List of users to share the file with
     * @return ExtensionResponse for validation re-entry
     */
    private ExtensionResponse reenterValidation(String source, List<Map<String, Object>> share) {
        ExtensionResponse validationRes = validationTypeUtil.checkValidations(Validator.ValidationResource.SOURCE_FILE, SubCatalogConstants.CONFIRM_REENTER_SOURCE_FILE_SHARE, source, share);

        if (validationRes != null) return validationRes;

        ExtensionResponse.Error error = new ErrorBuilder()
                .exceptionType(ExtensionResponse.Error.ExceptionType.INPUT_ERROR).errorMessage(Constants.PROVIDE_VALID_PATH_FILE_NOT_FOUND)
                .build();
        return new ExtensionResponseBuilder()
                .failure(error)
                .build();
    }
}