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
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.connectors.RestClient;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.util.*;
import com.google.gson.JsonObject;
import com.microsoft.graph.http.GraphServiceException;
import com.microsoft.graph.models.DriveItem;
import com.microsoft.graph.requests.DriveItemRequestBuilder;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.jvnet.hk2.annotations.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Service
public class CopyFileDao {

    private static final String MICROSOFT_GRAPH_CONFLICT_BEHAVIOR_REPLACE = "?@microsoft.graph.conflictBehavior=replace";
    private static final Logger LOGGER = LoggerFactory.getLogger(CopyFileDao.class);

    private final GraphServiceClientProvider provider;
    private final OperationsManagementDao operationsManagementDao;
    private final OnedriveAttributes onedriveAttributes;
    private final ValidationTypeUtil validationTypeUtil;
    private final TelemetryHelper telemetryHelper;

    @Inject
    public CopyFileDao(GraphServiceClientProviderFactory factory, OperationsManagementDao operationsManagementDao, OnedriveAttributes onedriveAttributes, ValidationTypeUtil validationTypeUtil, TelemetryHelper telemetryHelper) {
        this(factory.create(), operationsManagementDao, onedriveAttributes, validationTypeUtil, telemetryHelper);
    }

    public CopyFileDao(GraphServiceClientProvider provider, OperationsManagementDao operationsManagementDao,
                       OnedriveAttributes onedriveAttributes, ValidationTypeUtil validationTypeUtil, TelemetryHelper telemetryHelper) {
        this.provider = provider;
        this.operationsManagementDao = operationsManagementDao;
        this.onedriveAttributes = onedriveAttributes;
        this.validationTypeUtil = validationTypeUtil;
        this.telemetryHelper = telemetryHelper;
    }

    /**
     * Moves a file from the source path to the target path.
     * This method handles validation of source and target paths, checks for file existence,
     * and executes the move operation using the Microsoft Graph API.
     *
     * @param source            The source path of the file to be moved
     * @param target            The target path where the file should be moved to
     * @param isSourceReentered Flag indicating if the source path has been reentered after validation
     * @param isTargetReentered Flag indicating if the target path has been reentered after validation
     * @return ExtensionResponse containing the result of the move operation
     */
    public ExtensionResponse moveFile(String source, String target, boolean isSourceReentered, boolean isTargetReentered) {
        // Default to allowRetry = true for backward compatibility
        return moveFile(source, target, isSourceReentered, isTargetReentered, true);
    }

    /**
     * Moves a file from the source path to the target path with retry option.
     * This method handles validation of source and target paths, checks for file existence,
     * and executes the move operation using the Microsoft Graph API.
     *
     * @param source            The source path of the file to be moved
     * @param target            The target path where the file should be moved to
     * @param isSourceReentered Flag indicating if the source path has been reentered after validation
     * @param isTargetReentered Flag indicating if the target path has been reentered after validation
     * @param allowRetry        Whether to allow retry on validation failure
     * @return ExtensionResponse containing the result of the move operation
     */
    public ExtensionResponse moveFile(String source, String target, boolean isSourceReentered, boolean isTargetReentered, Boolean allowRetry) {
        Map<String, Object> payload = new HashMap<>();
        long startTime = System.currentTimeMillis();
        telemetryHelper.incrementCount(TelemetryConstants.TELEMETRY_MOVE_FILE);
        LOGGER.info("CopyFileDao.moveFile - Starting move operation. Source: {}, Target: {}", source, target);
        LOGGER.debug("CopyFileDao.moveFile - Parameters: isSourceReentered={}, isTargetReentered={}", isSourceReentered, isTargetReentered);

        try {
            operationsManagementDao.checkIfFileExist(source, payload);
            LOGGER.debug("CopyFileDao.moveFile - Source file existence check result: {}", payload.get(Constants.IS_FILE_EXIST));

            if ((boolean) payload.get(Constants.IS_FILE_EXIST)) {
                if (checkIfRootFile((DriveItem) payload.get(Constants.DRIVE_ITEM))) {
                    LOGGER.debug("CopyFileDao.moveFile - Source is a root file, cannot move");
                    ResponseUtil.errorResponse(false, Constants.PLEASE_PROVIDE_VALID_SOURCE_PATH, payload);
                    telemetryHelper.recordValidationError(TelemetryConstants.TELEMETRY_MOVE_FILE, startTime,
                            TelemetryConstants.TELEMETRY_ERROR_SOURCE_ROOT_FILE, TelemetryHelper.safeTagMap(TelemetryConstants.TELEMETRY_TAG_SOURCE, source));

                    if (!isSourceReentered) {
                        LOGGER.info("CopyFileDao.moveFile - Requesting source path reentry");
                        telemetryHelper.recordRetryPrompted(TelemetryConstants.TELEMETRY_MOVE_FILE, startTime,
                                TelemetryHelper.safeTagMap(TelemetryConstants.TELEMETRY_TAG_SOURCE, source, TelemetryConstants.TELEMETRY_TAG_REASON, TelemetryConstants.TELEMETRY_REASON_ROOT_FILE));

                        // Check allowRetry flag
                        if (Boolean.TRUE.equals(allowRetry)) {
                            return reenterValidate(source, target, false, "Source path not found");
                        }
                        // If allowRetry is false, fall through to return error response below
                    }
                    return new ExtensionResponseBuilder()
                            .success(payload, null, null)
                            .build();
                }

                Map<String, Object> response = new HashMap<>();
                LOGGER.debug("CopyFileDao.moveFile - Checking if target exists: {}", target);
                checkIfTargetFileExist(target, response);
                LOGGER.debug("CopyFileDao.moveFile - Target existence check result: {}", response.get(Constants.IS_FILE_EXIST));

                if ((boolean) response.get(Constants.IS_FILE_EXIST)) {
                    LOGGER.info("CopyFileDao.moveFile - Executing move operation from {} to {}", source, target);
                    executeMove((DriveItem) payload.get(Constants.DRIVE_ITEM), (DriveItem) response.get(Constants.DRIVE_ITEM), payload);
                    telemetryHelper.recordSuccess(TelemetryConstants.TELEMETRY_MOVE_FILE, startTime,
                            TelemetryHelper.safeTagMap(TelemetryConstants.TELEMETRY_TAG_SOURCE, source, TelemetryConstants.TELEMETRY_TAG_TARGET, target));
                    LOGGER.info("CopyFileDao.moveFile - Move operation completed successfully");
                } else {
                    if (!isTargetReentered) {
                        LOGGER.info("CopyFileDao.moveFile - Requesting target path reentry");
                        telemetryHelper.recordRetryPrompted(TelemetryConstants.TELEMETRY_MOVE_FILE, startTime,
                                TelemetryHelper.safeTagMap(TelemetryConstants.TELEMETRY_TAG_TARGET, target, TelemetryConstants.TELEMETRY_TAG_REASON, TelemetryConstants.TELEMETRY_REASON_TARGET_NOT_FOUND));

                        // Check allowRetry flag
                        if (Boolean.TRUE.equals(allowRetry)) {
                            return reenterValidate(target, source, true, "Target path not found");
                        }
                        // If allowRetry is false, fall through to error response below
                    }

                    LOGGER.debug("CopyFileDao.moveFile - Target path invalid after reentry: {}", target);
                    ResponseUtil.errorResponse(false, Constants.PLEASE_PROVIDE_VALID_TARGET_PATH, payload);
                    telemetryHelper.recordValidationError(TelemetryConstants.TELEMETRY_MOVE_FILE, startTime,
                            TelemetryConstants.TELEMETRY_ERROR_TARGET_NOT_FOUND_AFTER_REENTRY, TelemetryHelper.safeTagMap(TelemetryConstants.TELEMETRY_TAG_TARGET, target));
                }
                return new ExtensionResponseBuilder()
                        .success(payload, null, null)
                        .build();
            } else if (!isSourceReentered) {
                LOGGER.info("CopyFileDao.moveFile - Requesting source path reentry");
                telemetryHelper.recordRetryPrompted(TelemetryConstants.TELEMETRY_MOVE_FILE, startTime,
                        TelemetryHelper.safeTagMap(TelemetryConstants.TELEMETRY_TAG_SOURCE, source, TelemetryConstants.TELEMETRY_TAG_REASON, TelemetryConstants.TELEMETRY_REASON_SOURCE_NOT_FOUND));

                // Check allowRetry flag
                if (Boolean.TRUE.equals(allowRetry)) {
                    return reenterValidate(source, target, false, "Source path not found");
                }
                // If allowRetry is false, fall through to error response below
            }

            telemetryHelper.recordValidationError(TelemetryConstants.TELEMETRY_MOVE_FILE, startTime,
                    TelemetryConstants.TELEMETRY_ERROR_SOURCE_NOT_FOUND_AFTER_REENTRY, TelemetryHelper.safeTagMap(TelemetryConstants.TELEMETRY_TAG_SOURCE, source));
            return new ExtensionResponseBuilder()
                    .success(payload, null, null)
                    .build();

        } catch (GraphServiceException | IOException | IllegalArgumentException cause) {
            LOGGER.error("CopyFileDao.moveFile - Exception occurred during move", cause);
            String message = cause.getMessage();
            String errorMsg = message != null && message.contains(Constants.ITEM_NOT_FOUND)
                    ? Constants.FILE_NOT_FOUND
                    : Constants.ERROR_OCCURRED_DURING_MOVING_FILE;

            ResponseUtil.errorResponse(false, errorMsg, payload);
            telemetryHelper.recordError(TelemetryConstants.TELEMETRY_MOVE_FILE, startTime, cause,
                    TelemetryHelper.safeTagMap(TelemetryConstants.TELEMETRY_TAG_SOURCE, source, TelemetryConstants.TELEMETRY_TAG_TARGET, target));
        }

        LOGGER.debug("CopyFileDao.moveFile - Returning final payload: {}", payload);
        return new ExtensionResponseBuilder()
                .success(payload, null, null)
                .build();
    }


    /**
     * Checks if the provided DriveItem represents a root folder.
     * Root folders cannot be copied or moved.
     *
     * @param driveItem The DriveItem to check
     * @return true if the DriveItem is a root folder, false otherwise
     */
    private boolean checkIfRootFile(DriveItem driveItem) {
        return driveItem.name != null && driveItem.name.equalsIgnoreCase("root");
    }

    /**
     * Executes the move operation for a file using the Microsoft Graph API.
     * This method constructs the appropriate request and processes the response.
     *
     * @param source  The source DriveItem to be moved
     * @param target  The target DriveItem where the file should be moved to
     * @param payload The response payload to be populated with the result
     * @throws IOException If an I/O error occurs during the API request
     */
    private void executeMove(DriveItem source, DriveItem target, Map<String, Object> payload) throws IOException {
        if (source != null) {

            String endpoint = source.id;

            Response moveResponse = getRestClient(null).
                    executeHttpMethods(endpoint, Constants.PATCH_METHOD,
                            createRequestBody(target.id));
            prepareMovedResponse(moveResponse, payload);
        }
    }

    /**
     * Creates the folder structure for a given path if it doesn't already exist.
     * This method recursively creates each folder in the path.
     *
     * @param target  The target path where folders should be created
     * @param payload The response payload to be populated with the result
     */
    private void createFolderInPath(String target, Map<String, Object> payload) {
        LOGGER.info("CopyFileDao.createFolderInPath - Creating folder structure for path: {}", target);

        try {
            if (target != null && target.contains(Constants.DRIVE_ROOT)) {
                String path = FileUtil.getRelativeFilePath(target);
                String[] stream = path.split("/");
                StringBuilder absolutePathBuilder = new StringBuilder(Constants.DRIVE_ROOT);
                StringBuilder relativePathBuilder = new StringBuilder();
                LOGGER.debug("CopyFileDao.createFolderInPath - Relative path: {}, Folder count: {}", path, stream.length);

                for (String folderName : stream) {
                    if (folderName.isEmpty()) {
                        continue;
                    }

                    if (containsInvalidCharacters(folderName)) {
                        LOGGER.debug("CopyFileDao.createFolderInPath - Invalid folder name detected: {}", folderName);
                        ResponseUtil.errorResponse(false, Constants.INVALID_FOLDER_NAME, payload);
                        return;
                    }

                    LOGGER.debug("CopyFileDao.createFolderInPath - Processing folder: {}", folderName);
                    Map<String, Object> check = new HashMap<>();
                    absolutePathBuilder.append("/").append(folderName);
                    relativePathBuilder.append("/").append(folderName);

                    checkIfTargetFileExist(absolutePathBuilder.toString(), check);
                    if (!(boolean) check.get(Constants.IS_FILE_EXIST)) {
                        LOGGER.info("CopyFileDao.createFolderInPath - Creating folder: {}", folderName);
                        String parentPath = FileUtil.getParentPath(relativePathBuilder.toString());
                        DriveItemRequestBuilder builder = FileUtil.getRootUserRequestBuilder(provider);
                        if (!parentPath.isEmpty()) {
                            builder = builder.itemWithPath(FileUtil.getParentPath(relativePathBuilder.toString()));
                        }
                        builder.children().buildRequest().post(DriveUtil.getDriveItemForItem(folderName));
                        LOGGER.debug("CopyFileDao.createFolderInPath - Folder created: {}", folderName);
                    } else {
                        LOGGER.debug("CopyFileDao.createFolderInPath - Folder already exists: {}", folderName);
                    }
                }
            } else {
                LOGGER.debug("CopyFileDao.createFolderInPath - Invalid target path format: {}", target);
            }
        } catch (GraphServiceException | IllegalArgumentException cause) {
            LOGGER.error("CopyFileDao.createFolderInPath - Exception occurred", cause);
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
     * Copies a file from the source path to the target path.
     * This method handles validation of source and target paths, checks for file existence,
     * creates folder structure if needed, and executes the copy operation.
     *
     * @param source            The source path of the file to be copied
     * @param target            The target path where the file should be copied to
     * @param replace           Flag indicating whether to replace the file if it already exists at the target
     * @param createFolder      Flag indicating whether to create the folder structure if it doesn't exist
     * @param isTargetReentered Flag indicating if the target path has been reentered after validation
     * @param isSourceReentered Flag indicating if the source path has been reentered after validation
     * @return ExtensionResponse containing the result of the copy operation
     */
    public ExtensionResponse copyFile(String source, String target, Boolean replace, boolean createFolder,
                                      boolean isTargetReentered, boolean isSourceReentered) {
        // Default to allowRetry = true for backward compatibility
        return copyFile(source, target, replace, createFolder, isTargetReentered, isSourceReentered, true);
    }

    /**
     * Copies a file from the source path to the target path with retry option.
     * This method handles validation of source and target paths, checks for file existence,
     * and executes the copy operation using the Microsoft Graph API.
     *
     * @param source            The source path of the file to be copied
     * @param target            The target path where the file should be copied to
     * @param replace           Flag indicating whether to replace the file if it already exists at the target
     * @param createFolder      Flag indicating whether to create the folder structure if it doesn't exist
     * @param isTargetReentered Flag indicating if the target path has been reentered after validation
     * @param isSourceReentered Flag indicating if the source path has been reentered after validation
     * @param allowRetry        Whether to allow retry on validation failure
     * @return ExtensionResponse containing the result of the copy operation
     */
    public ExtensionResponse copyFile(String source, String target, Boolean replace, boolean createFolder,
                                      boolean isTargetReentered, boolean isSourceReentered, Boolean allowRetry) {
        long startTime = System.currentTimeMillis();
        telemetryHelper.incrementCount(TelemetryConstants.TELEMETRY_COPY_FILE);

        Map<String, Object> payload = new HashMap<>();
        LOGGER.info("copyFile - Starting file copy operation");
        LOGGER.debug("copyFile - Parameters -> Source: {}, Target: {}, Replace: {}, CreateFolder: {}, isTargetReentered: {}, isSourceReentered: {}",
                source, target, replace, createFolder, isTargetReentered, isSourceReentered);

        try {
            operationsManagementDao.checkIfFileExist(source, payload);
            LOGGER.debug("CopyFileDao.copyFile - Source file existence check result: {}", payload.get(Constants.IS_FILE_EXIST));

            if (Boolean.TRUE.equals(payload.get(Constants.IS_FILE_EXIST))) {
                DriveItem sourceDriveItem = (DriveItem) payload.get(Constants.DRIVE_ITEM);
                LOGGER.debug("CopyFileDao.copyFile - Source DriveItem retrieved: {}", sourceDriveItem.name);

                if (checkIfRootFile(sourceDriveItem)) {
                    LOGGER.debug("CopyFileDao.copyFile - Source is a root file, cannot copy");
                    telemetryHelper.recordValidationError(TelemetryConstants.TELEMETRY_COPY_FILE, startTime,
                            TelemetryConstants.TELEMETRY_ERROR_INVALID_SOURCE_PATH,
                            TelemetryHelper.safeTagMap(TelemetryConstants.TELEMETRY_TAG_SOURCE, source, TelemetryConstants.TELEMETRY_TAG_TARGET, target));

                    if (!isSourceReentered) {
                        LOGGER.info("CopyFileDao.copyFile - Requesting source path reentry");

                        // Check allowRetry flag
                        if (Boolean.TRUE.equals(allowRetry)) {
                            return reenterValidate(source, target, replace, createFolder, false, Constants.PLEASE_PROVIDE_VALID_SOURCE_PATH);
                        }
                        // If allowRetry is false, fall through to error response below
                    }
                    ResponseUtil.errorResponse(false, Constants.PLEASE_PROVIDE_VALID_SOURCE_PATH, payload);
                    return new ExtensionResponseBuilder().success(payload, null, null).build();
                }

                if (createFolder) {
                    LOGGER.info("CopyFileDao.copyFile - Creating folder structure for target path: {}", target);
                    createFolderInPath(target, payload);
                    if (Constants.INVALID_FOLDER_NAME.equals(payload.get(Constants.MESSAGE)) && !isTargetReentered) {
                        telemetryHelper.recordRetryPrompted(TelemetryConstants.TELEMETRY_COPY_FILE, startTime,
                                TelemetryHelper.safeTagMap(TelemetryConstants.TELEMETRY_TAG_REASON, TelemetryConstants.TELEMETRY_REASON_INVALID_FOLDER_NAME, TelemetryConstants.TELEMETRY_TAG_TARGET, target));

                        // Check allowRetry flag
                        if (Boolean.TRUE.equals(allowRetry)) {
                            return reenterValidate(target, source, replace, createFolder, true, Constants.INVALID_FOLDER_NAME);
                        } else {
                            // Return direct error without retry
                            return new ExtensionResponseBuilder().success(payload, null, null).build();
                        }
                    }
                }

                Map<String, Object> targetResponse = new HashMap<>();
                LOGGER.debug("CopyFileDao.copyFile - Checking if target exists: {}", target);
                checkIfTargetFileExist(target, targetResponse);
                LOGGER.debug("CopyFileDao.copyFile - Target existence check result: {}", targetResponse.get(Constants.IS_FILE_EXIST));

                if (Boolean.TRUE.equals(targetResponse.get(Constants.IS_FILE_EXIST))) {
                    DriveItem targetDriveItem = (DriveItem) targetResponse.get(Constants.DRIVE_ITEM);
                    LOGGER.debug("CopyFileDao.copyFile - Target DriveItem retrieved: {}", targetDriveItem.name);

                    LOGGER.info("CopyFileDao.copyFile - Executing copy operation from {} to {}", source, target);
                    executeCopy(sourceDriveItem, targetDriveItem, replace, payload);

                    if (Constants.FILE_ALREADY_EXIST.equals(payload.get(Constants.MESSAGE))) {
                        LOGGER.debug("CopyFileDao.copyFile - File already exists at target location");
                        telemetryHelper.recordValidationError(TelemetryConstants.TELEMETRY_COPY_FILE, startTime,
                                TelemetryConstants.TELEMETRY_ERROR_FILE_ALREADY_EXISTS,
                                TelemetryHelper.safeTagMap(TelemetryConstants.TELEMETRY_TAG_SOURCE, source, TelemetryConstants.TELEMETRY_TAG_TARGET, target));
                        if (!isSourceReentered) {
                            // Check allowRetry flag
                            if (Boolean.TRUE.equals(allowRetry)) {
                                return reenterValidate(source, target, replace, createFolder, false, Constants.FILE_ALREADY_EXIST);
                            }
                            // If allowRetry is false, return error response
                        }
                    }

                    LOGGER.info("CopyFileDao.copyFile - Copy operation completed successfully");
                    telemetryHelper.recordSuccess(TelemetryConstants.TELEMETRY_COPY_FILE, startTime,
                            TelemetryHelper.safeTagMap(TelemetryConstants.TELEMETRY_TAG_SOURCE, source, TelemetryConstants.TELEMETRY_TAG_TARGET, target, TelemetryConstants.TELEMETRY_TAG_REPLACED, String.valueOf(replace)));
                    return new ExtensionResponseBuilder().success(payload, null, null).build();

                } else {
                    LOGGER.debug("CopyFileDao.copyFile - Target path not found: {}", target);
                    telemetryHelper.recordValidationError(TelemetryConstants.TELEMETRY_COPY_FILE, startTime,
                            TelemetryConstants.TELEMETRY_ERROR_TARGET_PATH_NOT_FOUND,
                            TelemetryHelper.safeTagMap(TelemetryConstants.TELEMETRY_TAG_TARGET, target, TelemetryConstants.TELEMETRY_TAG_SOURCE, source));

                    if (!isTargetReentered) {
                        LOGGER.info("CopyFileDao.copyFile - Requesting target path reentry");

                        // Check allowRetry flag
                        if (Boolean.TRUE.equals(allowRetry)) {
                            return reenterValidate(target, source, replace, createFolder, true, Constants.PLEASE_PROVIDE_VALID_TARGET_PATH);
                        }
                        // If allowRetry is false, fall through to error response below
                    }
                    ResponseUtil.errorResponse(false, Constants.PLEASE_PROVIDE_VALID_TARGET_PATH, payload);
                    return new ExtensionResponseBuilder().success(payload, null, null).build();
                }

            } else {
                LOGGER.debug("CopyFileDao.copyFile - Source file not found: {}", source);
                telemetryHelper.recordValidationError(TelemetryConstants.TELEMETRY_COPY_FILE, startTime,
                        TelemetryConstants.TELEMETRY_ERROR_SOURCE_FILE_NOT_FOUND,
                        TelemetryHelper.safeTagMap(TelemetryConstants.TELEMETRY_TAG_SOURCE, source, TelemetryConstants.TELEMETRY_TAG_TARGET, target));

                if (!isSourceReentered) {
                    LOGGER.info("CopyFileDao.copyFile - Requesting source path reentry");

                    // Check allowRetry flag
                    if (Boolean.TRUE.equals(allowRetry)) {
                        return reenterValidate(source, target, replace, createFolder, false, Constants.FILE_NOT_FOUND);
                    }
                    // If allowRetry is false, fall through to error response below
                }
                ResponseUtil.errorResponse(false, Constants.FILE_NOT_FOUND, payload);
            }

        } catch (GraphServiceException e) {
            LOGGER.error("copyFile - GraphServiceException occurred", e);
            String msg = e.getMessage();
            telemetryHelper.recordError(TelemetryConstants.TELEMETRY_COPY_FILE, startTime, e,
                    TelemetryHelper.safeTagMap(TelemetryConstants.TELEMETRY_TAG_SOURCE, source, TelemetryConstants.TELEMETRY_TAG_TARGET, target, TelemetryConstants.TELEMETRY_TAG_ERROR_TYPE, TelemetryConstants.TELEMETRY_ERROR_TYPE_GRAPH_SERVICE));
            ResponseUtil.errorResponse(false,
                    msg != null && msg.contains(Constants.ITEM_NOT_FOUND)
                            ? Constants.FILE_NOT_FOUND
                            : Constants.ERROR_OCCURRED_DURING_COPYING_FILE,
                    payload);

        } catch (IOException e) {
            LOGGER.error("copyFile - IOException occurred", e);
            telemetryHelper.recordError(TelemetryConstants.TELEMETRY_COPY_FILE, startTime, e,
                    TelemetryHelper.safeTagMap(TelemetryConstants.TELEMETRY_TAG_SOURCE, source, TelemetryConstants.TELEMETRY_TAG_TARGET, target, TelemetryConstants.TELEMETRY_TAG_ERROR_TYPE, TelemetryConstants.TELEMETRY_ERROR_TYPE_IO));
            ResponseUtil.errorResponse(false, "I/O error during file copy: " + e.getMessage(), payload);

        } catch (IllegalArgumentException e) {
            LOGGER.error("copyFile - IllegalArgumentException occurred", e);
            telemetryHelper.recordError(TelemetryConstants.TELEMETRY_COPY_FILE, startTime, e,
                    TelemetryHelper.safeTagMap(TelemetryConstants.TELEMETRY_TAG_SOURCE, source, TelemetryConstants.TELEMETRY_TAG_TARGET, target, TelemetryConstants.TELEMETRY_TAG_ERROR_TYPE, TelemetryConstants.TELEMETRY_ERROR_TYPE_ILLEGAL_ARGUMENT));
            ResponseUtil.errorResponse(false, e.getMessage(), payload);

        } catch (Exception e) {
            LOGGER.error("copyFile - Unexpected exception occurred", e);
            telemetryHelper.recordError(TelemetryConstants.TELEMETRY_COPY_FILE, startTime, e,
                    TelemetryHelper.safeTagMap(TelemetryConstants.TELEMETRY_TAG_SOURCE, source, TelemetryConstants.TELEMETRY_TAG_TARGET, target, TelemetryConstants.TELEMETRY_TAG_ERROR_TYPE, TelemetryConstants.TELEMETRY_ERROR_TYPE_UNEXPECTED));
            ResponseUtil.errorResponse(false, "Unexpected error during file copy: " + e.getMessage(), payload);
        }

        LOGGER.debug("copyFile - Returning final payload: {}", payload);
        return new ExtensionResponseBuilder().success(payload, null, null).build();
    }


    /**
     * Executes the copy operation for a file using the Microsoft Graph API.
     * This method constructs the appropriate request with conflict behavior options
     * and processes the response.
     *
     * @param source  The source DriveItem to be copied
     * @param target  The target DriveItem where the file should be copied to
     * @param replace Flag indicating whether to replace the file if it already exists
     * @param payload The response payload to be populated with the result
     * @throws IOException If an I/O error occurs during the API request
     */
    private void executeCopy(DriveItem source, DriveItem target, Boolean replace, Map<String, Object> payload) throws IOException {
        if (source != null) {

            String endpoint = source.id + "/copy";
            if (Boolean.TRUE.equals(replace)) {
                endpoint += MICROSOFT_GRAPH_CONFLICT_BEHAVIOR_REPLACE;
            }
            Response copyResponse = getRestClient(null).
                    executeHttpMethods(endpoint, Constants.POST_METHOD,
                            createRequestBody(target.id));
            prepareCopyResponse(copyResponse, payload);

        }
    }

    /**
     * Processes the response from a move operation and updates the payload accordingly.
     * This method handles successful moves, conflicts, and error conditions.
     *
     * @param moveResponse The HTTP response from the move operation
     * @param payload      The response payload to be populated with the result
     */
    private void prepareMovedResponse(Response moveResponse, Map<String, Object> payload) {
        if (moveResponse != null && moveResponse.code() == 200) {
            try {
                String itemID = moveResponse.request().url()
                        .pathSegments()
                        .getLast();
                DriveItem driveItem = FileUtil.getUserRequestBuilderWithItemId(provider, null, itemID)
                        .buildRequest().expand(Constants.PERMISSIONS).get();

                ResponseUtil.fileEntityResponse(CatalogTypes.fromFile(new FileImpl(driveItem, null, onedriveAttributes))
                        , true, Constants.FILE_HAS_BEEN_MOVED, payload);
            } catch (GraphServiceException | IllegalArgumentException cause) {
                LOGGER.info(cause.getMessage(), cause);
                ResponseUtil.errorResponse(false, Objects.requireNonNull(cause.getMessage())
                        .contains(Constants.ITEM_NOT_FOUND)
                        ? Constants.FILE_NOT_FOUND : Constants.ERROR_OCCURRED_DURING_MOVING_FILE, payload);
            }
        } else if (moveResponse != null && moveResponse.code() == 409) {
            ResponseUtil.errorResponse(false, Constants.FILE_ALREADY_EXIST, payload);
        } else {
            ResponseUtil.errorResponse(false, Constants.ERROR_DURING_SERVICE, payload);
        }
    }

    /**
     * Processes the response from a copy operation and updates the payload accordingly.
     * This method handles the asynchronous nature of copy operations by checking the status
     * and polling for completion.
     *
     * @param response The HTTP response from the copy operation
     * @param payload  The response payload to be populated with the result
     * @throws IOException If an I/O error occurs during the API request
     */
    private void prepareCopyResponse(Response response, Map<String, Object> payload) throws IOException {
        if (response != null && response.code() == 202) {
            try (Response statusResponse = getRestClient(null).get(response.header("Location"))) {
                if (statusResponse.code() == 200) {
                    final ResponseBody statusResponseBody = statusResponse.body();
                    if (statusResponseBody != null) {
                        String responseBodyString = statusResponseBody.string(); // Read the response body as a string
                        JsonObject jsonObject = Constants.GSON.fromJson(responseBodyString, JsonObject.class);

                        if (jsonObject.isJsonObject()) {
                            String status = jsonObject.getAsJsonPrimitive("status").getAsString();
                            if ("completed".equalsIgnoreCase(status)) {
                                DriveItem driveItem = FileUtil
                                        .getUserRequestBuilderWithItemId(provider
                                                , null
                                                , jsonObject.getAsJsonPrimitive("resourceId").getAsString().trim())
                                        .buildRequest().expand(Constants.PERMISSIONS).get();

                                ResponseUtil.fileEntityResponse(CatalogTypes
                                                .fromFile(new FileImpl(driveItem, null, onedriveAttributes))
                                        , true, Constants.FILE_HAS_BEEN_COPIED, payload);
                            } else if ("inProgress".equalsIgnoreCase(status)) {
                                ResponseUtil.errorResponse(true, Constants.COPY_IN_PROGRESS, payload);
                            } else if ("failed".equalsIgnoreCase(status)) {
                                ResponseUtil.errorResponse(false, Constants.FILE_ALREADY_EXIST, payload);
                            }
                        }
                    }
                }
            }
        } else {
            ResponseUtil.errorResponse(false, Constants.ERROR_DURING_SERVICE, payload);
        }
    }

    /**
     * Creates a request body for copy and move operations.
     * The request body specifies the target folder ID.
     *
     * @param folderId The ID of the target folder
     * @return RequestBody containing the JSON payload for the API request
     */
    public RequestBody createRequestBody(String folderId) {
        String payload = getParentReferenceJson(folderId);
        MediaType jsonMediaType = MediaType.parse(Constants.APPLICATION_JSON);
        return RequestBody.create(jsonMediaType, payload);
    }

    /**
     * Generates the JSON string for the parent reference in copy and move operations.
     * This JSON specifies the target folder ID.
     *
     * @param folderId The ID of the target folder
     * @return JSON string representing the parent reference
     */
    private String getParentReferenceJson(String folderId) {
        JsonObject jsonObject = new JsonObject();
        JsonObject parentReference = new JsonObject();
        parentReference.addProperty("id", folderId);
        jsonObject.add("parentReference", parentReference);
        return jsonObject.toString();
    }

    /**
     * Checks if a target file or folder exists at the specified path.
     * Updates the response map with the result and the DriveItem if found.
     *
     * @param target   The path to check for existence
     * @param response The response map to be populated with the result
     */
    public void checkIfTargetFileExist(String target, Map<String, Object> response) {
        try {
            ResponseUtil.addDriveItemMessage(false, false, Constants.PLEASE_PROVIDE_VALID_FOLDER_PATH, response);
            if (target != null && target.contains(Constants.DRIVE_ROOT)) {
                String path = FileUtil.getRelativeFilePath(target);
                DriveItem driveItem = path.isEmpty()
                        ? FileUtil.getRootUserRequestBuilder(provider).buildRequest().get()
                        : FileUtil.getRootUserRequestBuilder(provider).itemWithPath(path).buildRequest().get();
                if (driveItem != null) {
                    ResponseUtil.addDriveItem(true, driveItem, response);
                }
            }

        } catch (GraphServiceException | IllegalArgumentException cause) {
            LOGGER.info(cause.getMessage(), cause);
            response.put(Constants.MESSAGE, Objects.requireNonNull(cause.getMessage()).contains(Constants.ITEM_NOT_FOUND)
                    ? Constants.FILE_NOT_FOUND : Constants.PLEASE_PROVIDE_VALID_TARGET_PATH);
            response.put(Constants.IS_FILE_EXIST, false);
        }
    }

    /**
     * Gets a REST client for the specified drive ID.
     * If driveId is null, returns the default REST client.
     *
     * @param driveId The drive ID to get a REST client for, or null for the default
     * @return RestClient for making API requests
     */
    private RestClient getRestClient(String driveId) {
        return provider.getRestClient(driveId);
    }

    /**
     * Validates reentry for copy operations and returns an appropriate response.
     * This method is called when a validation error occurs and the user needs to
     * reenter a path.
     *
     * @param target       The target path to validate
     * @param source       The source path
     * @param replace      Flag indicating whether to replace existing files
     * @param createFolder Flag indicating whether to create folder structure
     * @param isTargetFile Flag indicating whether the validation is for the target (true) or source (false)
     * @param errorMessage The error message to display to the user
     * @return ExtensionResponse for the validation result
     */
    public ExtensionResponse reenterValidate(String target, String source, Boolean replace, Boolean createFolder, Boolean isTargetFile, String errorMessage) {
        LOGGER.info("FileTransferService.reenterValidate - Validating for reentry");
        ExtensionResponse validationRes = validationTypeUtil.checkValidationsCopyFile(
                isTargetFile ? Validator.ValidationResource.TARGET_FILE : Validator.ValidationResource.SOURCE_FILE,
                SubCatalogConstants.CONFIRM_REENTER_SOURCE_FILE_COPY, target, source, replace, createFolder, isTargetFile, errorMessage);

        if (validationRes != null) {
            LOGGER.info("FileTransferService.reenterValidate - Validation succeeded, returning validation response");
            return validationRes;
        }

        ExtensionResponse.Error error = new ErrorBuilder()
                .exceptionType(ExtensionResponse.Error.ExceptionType.INPUT_ERROR)
                .errorMessage(Constants.PROVIDE_VALID_PATH_FILE_NOT_FOUND)
                .build();
        return new ExtensionResponseBuilder()
                .failure(error)
                .build();
    }

    /**
     * Validates reentry for move operations and returns an appropriate response.
     * This is a simplified version of the validation method used specifically for move operations.
     *
     * @return ExtensionResponse for the validation result
     */
    public ExtensionResponse reenterValidate(String source, String target, Boolean isTargetFile, String errorMessage) {
        ExtensionResponse validationRes = validationTypeUtil.checkValidationsMoveFile(SubCatalogConstants.CONFIRM_REENTER_SOURCE_FILE_MOVE,
                source, target, false, false, isTargetFile, errorMessage);

        if (validationRes != null) return validationRes;

        ExtensionResponse.Error error = new ErrorBuilder()
                .exceptionType(ExtensionResponse.Error.ExceptionType.INPUT_ERROR).errorMessage(Constants.PROVIDE_VALID_PATH_FILE_NOT_FOUND)
                .build();
        return new ExtensionResponseBuilder()
                .failure(error)
                .build();
    }
}