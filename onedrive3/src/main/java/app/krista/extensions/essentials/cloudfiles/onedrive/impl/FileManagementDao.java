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
import app.krista.extensions.essentials.cloudfiles.onedrive.service.File;
import app.krista.model.base.FreeForm;
import com.microsoft.graph.http.GraphServiceException;
import com.microsoft.graph.models.DriveItem;
import com.microsoft.graph.requests.DriveItemCollectionPage;
import com.microsoft.graph.requests.DriveItemRequestBuilder;
import com.microsoft.graph.requests.DriveSharedWithMeCollectionPage;
import com.microsoft.graph.requests.PermissionCollectionPage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jvnet.hk2.annotations.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Data Access Object for file management operations in OneDrive.
 * This class provides methods for listing, retrieving, and managing files in OneDrive,
 * including personal drives, shared drives, and drives accessed by ID.
 */
@Service
public class FileManagementDao {

    private static final Logger LOGGER = LoggerFactory.getLogger(FileManagementDao.class);
    private final GraphServiceClientProvider provider;
    private final OnedriveAttributes onedriveAttributes;
    private final ValidationTypeUtil validationTypeUtil;
    private final TelemetryHelper telemetryHelper;

    /**
     * Constructs a FileManagementDao with a factory-created provider.
     *
     * @param factory            Factory to create the GraphServiceClientProvider
     * @param onedriveAttributes Attributes for OneDrive configuration
     * @param validationTypeUtil Utility for validation operations
     * @param telemetryHelper    Helper for telemetry recording
     */
    @Inject
    public FileManagementDao(GraphServiceClientProviderFactory factory, OnedriveAttributes onedriveAttributes, ValidationTypeUtil validationTypeUtil, TelemetryHelper telemetryHelper) {
        this(factory.create(), onedriveAttributes, validationTypeUtil, telemetryHelper);
    }

    /**
     * Constructs a FileManagementDao with the specified provider and attributes.
     *
     * @param provider           The GraphServiceClientProvider for Microsoft Graph API calls
     * @param onedriveAttributes Attributes for OneDrive configuration
     * @param validationTypeUtil Utility for validation operations
     * @param telemetryHelper    Helper for telemetry recording
     */
    public FileManagementDao(GraphServiceClientProvider provider, OnedriveAttributes onedriveAttributes, ValidationTypeUtil validationTypeUtil, TelemetryHelper telemetryHelper) {
        this.provider = provider;
        this.onedriveAttributes = onedriveAttributes;
        this.validationTypeUtil = validationTypeUtil;
        this.telemetryHelper = telemetryHelper;
    }

    private static void fetchFileResponse(List<File> files, FreeForm payload) {
        if (files != null && !files.isEmpty()) {
            files = files.stream().distinct().collect(Collectors.toList());
            ResponseUtil.listOfFileEntityResponse(files.stream()
                    .map(CatalogTypes::fromFile)
                    .collect(Collectors.toList()), true, Constants.FILE_FETCHED_SUCCESSFULLY, payload);
        } else {
            ResponseUtil.getInfoResponse(false, Constants.FILE_NAME_LIST_IS_EMPTY_OR_NULL, payload);
        }
    }

    /**
     * Retrieves a list of files from the specified path.
     * Handles different path types (personal drive, shared drive, drive IDs) and
     * provides appropriate responses for each scenario.
     *
     * @param relativePath The path to retrieve files from
     * @param search       Optional search term to filter files
     * @param useEmail     Whether to use email for authentication
     * @param accountID    The account ID for authentication
     * @return ExtensionResponse containing the list of files or an error
     */
    @NotNull
    public FreeForm getListOfFiles(String relativePath, String search, boolean useEmail, String accountID) {
        FreeForm payload = new FreeForm();
        long startTime = System.currentTimeMillis();
        LOGGER.info("FileManagementDao.getListOfFiles - Starting file list retrieval | relativePath: {}, search: {}, useEmail: {}, accountID: {}",
                relativePath, search, useEmail, accountID);
        telemetryHelper.incrementCount(TelemetryConstants.TELEMETRY_GET_LIST_OF_FILES);

        try {
            List<File> files = new ArrayList<>();
            if (relativePath != null) {
                LOGGER.debug("FileManagementDao.getListOfFiles - Processing non-null relativePath: {}", relativePath);

                if (FileUtil.checkDriveIDContains(relativePath)) {
                    List<String> content = FileUtil.getDriveIDAndPath(relativePath);
                    LOGGER.info("FileManagementDao.getListOfFiles - Processing drive ID path | driveID: {}, path: {}", content.get(0), content.get(1));
                    getRootFilesByDriveIDPath(content.get(0), content.get(1), files, useEmail, accountID, search, payload);
                    telemetryHelper.recordSuccess(TelemetryConstants.TELEMETRY_GET_LIST_OF_FILES, startTime,
                            TelemetryHelper.safeTagMap(TelemetryConstants.TELEMETRY_TAG_RELATIVE_PATH, relativePath, TelemetryConstants.TELEMETRY_TAG_PATH_TYPE, TelemetryConstants.TELEMETRY_PATH_TYPE_DRIVE_ID));
                    LOGGER.info("FileManagementDao.getListOfFiles - Successfully processed drive ID path | filesCount: {}", files.size());
                    return payload;
                }
                if (FileUtil.isPersonalDrive(relativePath)) {
                    LOGGER.debug("FileManagementDao.getListOfFiles - Detected personal drive path");
                    String path = FileUtil.getRelativeFilePath(relativePath);
                    getRootFilesByPath(path, files, useEmail, accountID, search, payload);
                    telemetryHelper.recordSuccess(TelemetryConstants.TELEMETRY_GET_LIST_OF_FILES, startTime,
                            TelemetryHelper.safeTagMap(TelemetryConstants.TELEMETRY_TAG_RELATIVE_PATH, relativePath, TelemetryConstants.TELEMETRY_TAG_PATH_TYPE, TelemetryConstants.TELEMETRY_PATH_TYPE_PERSONAL));
                    LOGGER.info("FileManagementDao.getListOfFiles - Successfully processed personal drive path | filesCount: {}", files.size());
                    return payload;
                }
                if (relativePath.startsWith(Constants.SHARED) || relativePath.startsWith(Constants.FORWARD_SLASH_SHARED)) {
                    LOGGER.info("FileManagementDao.getListOfFiles - Processing shared files | relativePath: {}", relativePath);
                    getSharedFiles(relativePath, files, search, useEmail, accountID, payload);
                    telemetryHelper.recordSuccess(TelemetryConstants.TELEMETRY_GET_LIST_OF_FILES, startTime,
                            TelemetryHelper.safeTagMap(TelemetryConstants.TELEMETRY_TAG_RELATIVE_PATH, relativePath, TelemetryConstants.TELEMETRY_TAG_PATH_TYPE, TelemetryConstants.TELEMETRY_PATH_TYPE_SHARED));
                    return payload;
                }
                getRootFilesByPath(relativePath, files, useEmail, accountID, search, payload);
                telemetryHelper.recordSuccess(TelemetryConstants.TELEMETRY_GET_LIST_OF_FILES, startTime,
                        TelemetryHelper.safeTagMap(TelemetryConstants.TELEMETRY_TAG_RELATIVE_PATH, relativePath, TelemetryConstants.TELEMETRY_TAG_PATH_TYPE, "default"));
                LOGGER.info("FileManagementDao.getListOfFiles - Successfully processed default path | filesCount: {}", files.size());
                return payload;
            }
            telemetryHelper.recordValidationError(TelemetryConstants.TELEMETRY_GET_LIST_OF_FILES, startTime,
                    TelemetryConstants.TELEMETRY_ERROR_RELATIVE_PATH_NULL, TelemetryHelper.safeTagMap(TelemetryConstants.TELEMETRY_TAG_RELATIVE_PATH, "null"));
            return payload;
        } catch (GraphServiceException | IllegalArgumentException cause) {
            LOGGER.error("FileManagementDao.getListOfFiles - Exception occurred | relativePath: {}, error: {}", relativePath, cause.getMessage(), cause);
            telemetryHelper.recordError(TelemetryConstants.TELEMETRY_GET_LIST_OF_FILES, startTime, cause,
                    TelemetryHelper.safeTagMap(TelemetryConstants.TELEMETRY_TAG_RELATIVE_PATH, relativePath, TelemetryConstants.TELEMETRY_TAG_ERROR_TYPE, cause.getClass().getSimpleName()));
            ResponseUtil.getInfoResponse(false, Objects.requireNonNull(cause.getMessage())
                    .contains(Constants.ITEM_NOT_FOUND)
                    ? Constants.FILE_NOT_FOUND
                    : Constants.PLEASE_PROVIDE_VALID_SOURCE_PATH, payload);
        }
        return payload;
    }

    /**
     * Retrieves files from a shared location.
     * Processes files shared with the current user and adds them to the files list.
     *
     * @param relativePath The shared path to retrieve files from
     * @param files        The list to add the files to
     * @param search       Optional search term to filter files
     * @param useEmail     Whether to use email for authentication
     * @param accountID    The account ID for authentication
     * @param payload      The payload to update with the results
     */
    private void getSharedFiles(String relativePath, List<File> files, String search, boolean useEmail, String accountID, FreeForm payload) {
        try {
            DriveSharedWithMeCollectionPage page = provider.getUserRequestBuilder(useEmail, accountID)
                    .drive().sharedWithMe().buildRequest().top(100000).get();

            while (page != null && !page.getCurrentPage().isEmpty()) {
                for (DriveItem driveInPage : page.getCurrentPage()) {
                    if (driveInPage.id != null && driveInPage.remoteItem != null
                            && driveInPage.remoteItem.parentReference != null
                            && driveInPage.remoteItem.parentReference.driveId != null) {

                        // Handle root shared path
                        if (relativePath.equals(Constants.SHARED) || relativePath.equals(Constants.FORWARD_SLASH_SHARED)) {
                            if (search != null && !search.isEmpty()
                                    && driveInPage.name != null && driveInPage.name.contains(search)) {
                                DriveItem driveItem = provider.getUserRequestBuilder(useEmail, accountID)
                                        .drives(driveInPage.remoteItem.parentReference.driveId)
                                        .items(driveInPage.id)
                                        .buildRequest().expand(Constants.PERMISSIONS).get();
                                files.add(new FileImpl(driveItem, null, onedriveAttributes));
                            }
                            if (search != null && search.isBlank()) {
                                DriveItem driveItem = provider.getUserRequestBuilder(useEmail, accountID)
                                        .drives(driveInPage.remoteItem.parentReference.driveId)
                                        .items(driveInPage.id)
                                        .buildRequest().expand(Constants.PERMISSIONS).get();
                                files.add(new FileImpl(driveItem, null, onedriveAttributes));
                            }
                        } else {
                            ResponseUtil.getInfoResponse(false, Constants.PLEASE_PROVIDE_VALID_SOURCE_PATH, payload);
                            return;
                        }
                    }
                }
                if (page.getNextPage() != null) {
                    page = page.getNextPage().buildRequest().get();
                } else {
                    fetchFileResponse(files, payload);
                    break;
                }
            }

        } catch (GraphServiceException | IllegalArgumentException cause) {
            LOGGER.info(cause.getMessage(), cause);
            ResponseUtil.getInfoResponse(true, Objects
                    .requireNonNull(cause.getMessage())
                    .contains(Constants.ITEM_NOT_FOUND)
                    ? Constants.FILE_NOT_FOUND
                    : Constants.PLEASE_PROVIDE_VALID_SOURCE_PATH, payload);
        }
    }

    /**
     * Retrieves files from a path with a specific drive ID.
     * Processes files in the specified drive and adds them to the files list.
     *
     * @param driveID      The ID of the drive to retrieve files from
     * @param relativePath The path within the drive to retrieve files from
     * @param files        The list to add the files to
     * @param useEmail     Whether to use email for authentication
     * @param accountID    The account ID for authentication
     * @param search       Optional search term to filter files
     * @param payload      The payload to update with the results
     */
    public void getRootFilesByDriveIDPath(String driveID, String relativePath, List<File> files, boolean useEmail, String accountID, String search, FreeForm payload) {

        try {
            DriveItem driveItem = provider.getUserRequestBuilder(useEmail, accountID)
                    .drives(driveID).root()
                    .itemWithPath(relativePath)
                    .buildRequest().expand(Constants.PERMISSIONS).get();

            if (driveItem != null && driveItem.id != null && driveItem.folder != null) {
                DriveItemCollectionPage page = provider.getUserRequestBuilder(useEmail, accountID)
                        .drives(driveID).root()
                        .itemWithPath(relativePath).children().buildRequest().get();
                if (page != null && page.getCurrentPage().isEmpty()) {
                    ResponseUtil.emptyFolderResponse(true, Constants.FOLDER_IS_EMPTY, payload);
                    return;
                }
                while (page != null && !page.getCurrentPage().isEmpty()) {
                    for (DriveItem driveInPage : page.getCurrentPage()) {
                        if (search != null && !search.isEmpty() && driveInPage.id != null
                                && driveInPage.name != null && driveInPage.name.contains(search)) {
                            PermissionCollectionPage permissionPage = provider.getUserRequestBuilder(useEmail, accountID)
                                    .drives(driveID).items(driveInPage.id)
                                    .permissions().buildRequest().get();
                            files.add(new FileImpl(driveInPage, permissionPage, onedriveAttributes));
                        }
                        if (search != null && search.isBlank() && driveInPage.id != null) {
                            PermissionCollectionPage permissionPage = provider.getUserRequestBuilder(useEmail, accountID)
                                    .drives(driveID).items(driveInPage.id).permissions()
                                    .buildRequest().get();

                            files.add(new FileImpl(driveInPage, permissionPage, onedriveAttributes));
                        }
                    }
                    if (page.getNextPage() != null) {
                        page = page.getNextPage().buildRequest().get();
                    } else {
                        fetchFileResponse(files, payload);
                        break;
                    }
                }
            } else {
                files.add(new FileImpl(driveItem, null, onedriveAttributes));
                fetchFileResponse(files, payload);
            }
        } catch (GraphServiceException | IllegalArgumentException cause) {
            LOGGER.info(cause.getMessage(), cause);
            ResponseUtil.getInfoResponse(false, Constants.FILE_NOT_FOUND, payload);
        }
    }

    /**
     * Retrieves files from a path in the personal drive.
     * Processes files in the specified path and adds them to the files list.
     *
     * @param relativePath The path to retrieve files from
     * @param files        The list to add the files to
     * @param useEmail     Whether to use email for authentication
     * @param accountID    The account ID for authentication
     * @param search       Optional search term to filter files
     * @param payload      The payload to update with the results
     */
    public void getRootFilesByPath(String relativePath, List<File> files, boolean useEmail, String accountID, String search, FreeForm payload) {
        try {
            DriveItemRequestBuilder builderRoot = getRequestBuilder(useEmail, accountID);
            if (!relativePath.isEmpty()) {
                builderRoot = builderRoot.itemWithPath(relativePath);
            }
            DriveItem driveItem = builderRoot.buildRequest().expand(Constants.PERMISSIONS).get();

            if (driveItem != null && driveItem.id != null && driveItem.folder != null) {
                DriveItemRequestBuilder builderRootChildren = getRequestBuilder(useEmail, accountID);
                if (!relativePath.isEmpty()) {
                    builderRootChildren = builderRootChildren.itemWithPath(relativePath);
                }
                DriveItemCollectionPage page = builderRootChildren.children().buildRequest().get();
                if (page != null && page.getCurrentPage().isEmpty()) {
                    ResponseUtil.emptyFolderResponse(true, Constants.FOLDER_IS_EMPTY, payload);
                    return;
                }
                while (page != null && !page.getCurrentPage().isEmpty()) {
                    for (DriveItem driveInPage : page.getCurrentPage()) {
                        if (search != null && !search.isEmpty() && driveInPage.id != null
                                && driveInPage.name != null && driveInPage.name.contains(search)) {
                            PermissionCollectionPage permissionPage = getPermissionCollectionPage(useEmail, accountID, driveInPage);
                            files.add(new FileImpl(driveInPage, permissionPage, onedriveAttributes));
                        }
                        if (search != null && search.isBlank() && driveInPage.id != null) {
                            PermissionCollectionPage permissionPage = getPermissionCollectionPage(useEmail, accountID, driveInPage);
                            files.add(new FileImpl(driveInPage, permissionPage, onedriveAttributes));
                        }
                    }
                    if (page.getNextPage() != null) {
                        page = page.getNextPage().buildRequest().get();
                    } else {
                        fetchFileResponse(files, payload);
                        break;
                    }
                }
            } else {
                files.add(new FileImpl(driveItem, null, onedriveAttributes));
                fetchFileResponse(files, payload);
            }
        } catch (GraphServiceException | IllegalArgumentException cause) {
            LOGGER.info(cause.getMessage(), cause);
            ResponseUtil.getInfoResponse(false, Objects.requireNonNull(cause.getMessage())
                    .contains(Constants.ITEM_NOT_FOUND)
                    ? Constants.FILE_NOT_FOUND
                    : Constants.PLEASE_PROVIDE_VALID_SOURCE_PATH, payload);
        }
    }

    @Nullable
    private PermissionCollectionPage getPermissionCollectionPage(boolean useEmail, String accountID, DriveItem driveInPage) {
        return provider.getUserRequestBuilder(useEmail, accountID)
                .drive().items(Objects.requireNonNull(driveInPage.id))
                .permissions().buildRequest().get();
    }

    @NotNull
    private DriveItemRequestBuilder getRequestBuilder(boolean useEmail, String accountID) {
        return provider.getUserRequestBuilder(useEmail, accountID).drive().root();
    }

    /**
     * Retrieves information about a file or folder.
     * Provides details such as file count, folder count, oldest/newest files, etc.
     *
     * @param sourcePath The path to retrieve information for
     * @param useEmail   Whether to use email for authentication
     * @param accountID  The account ID for authentication
     * @return ExtensionResponse containing the file information or an error
     */
    public FreeForm getFileInfo(String sourcePath, boolean useEmail, String accountID) {
        FreeForm payload = new FreeForm();
        long startTime = System.currentTimeMillis();
        LOGGER.info("FileManagementDao.getFileInfo - Starting file info retrieval | sourcePath: {}, useEmail: {}, accountID: {}",
                sourcePath, useEmail, accountID);
        telemetryHelper.incrementCount(TelemetryConstants.TELEMETRY_GET_FILE_INFO);

        try {
            LOGGER.debug("FileManagementDao.getFileInfo - Checking if folder exists | sourcePath: {}", sourcePath);
            checkIfFolderExist(sourcePath, payload, useEmail, accountID);
            if ((boolean) payload.get(Constants.SUCCESS)) {
                LOGGER.info("FileManagementDao.getFileInfo - Folder exists, proceeding to fetch file info | sourcePath: {}", sourcePath);
                fetchFileInfo(sourcePath, payload, useEmail, accountID);
                telemetryHelper.recordSuccess(TelemetryConstants.TELEMETRY_GET_FILE_INFO, startTime,
                        TelemetryHelper.safeTagMap(TelemetryConstants.TELEMETRY_TAG_SOURCE_PATH, sourcePath, TelemetryConstants.TELEMETRY_TAG_USE_EMAIL, String.valueOf(useEmail)));
                LOGGER.info("FileManagementDao.getFileInfo - Successfully retrieved file info | sourcePath: {}", sourcePath);
            } else {
                telemetryHelper.recordValidationError(TelemetryConstants.TELEMETRY_GET_FILE_INFO, startTime,
                        TelemetryConstants.TELEMETRY_ERROR_FOLDER_NOT_EXIST, TelemetryHelper.safeTagMap(TelemetryConstants.TELEMETRY_TAG_SOURCE_PATH, sourcePath));
            }
            return payload;
        } catch (GraphServiceException | IllegalArgumentException cause) {
            LOGGER.error("FileManagementDao.getFileInfo - Exception occurred | sourcePath: {}, error: {}", sourcePath, cause.getMessage(), cause);
            telemetryHelper.recordError(TelemetryConstants.TELEMETRY_GET_FILE_INFO, startTime, cause,
                    TelemetryHelper.safeTagMap(TelemetryConstants.TELEMETRY_TAG_SOURCE_PATH, sourcePath, TelemetryConstants.TELEMETRY_TAG_ERROR_TYPE, cause.getClass().getSimpleName()));
        }
        LOGGER.debug("FileManagementDao.getFileInfo - Returning payload after exception handling");
        return payload;
    }

    /**
     * Fetches detailed information about files in a folder.
     * Collects statistics such as file count, folder count, oldest/newest files, etc.
     *
     * @param sourcePath The path to fetch information for
     * @param payload    The payload to update with the results
     * @param useEmail   Whether to use email for authentication
     * @param accountID  The account ID for authentication
     */
    private void fetchFileInfo(String sourcePath, FreeForm payload, boolean useEmail, String accountID) {
        try {
            DriveItemCollectionPage driveItemCollectionPage;
            if (sourcePath.contains(Constants.DRIVE_ROOT)) {
                String path = FileUtil.getRelativeFilePath(sourcePath);
                driveItemCollectionPage = provider.getUserRequestBuilder(useEmail, accountID)
                        .drive().root()
                        .itemWithPath(path).children().buildRequest().get();
                if (driveItemCollectionPage != null) {
                    processResponse(driveItemCollectionPage, payload);
                }
            }
            if (FileUtil.checkDriveIDContains(sourcePath)) {
                List<String> content = FileUtil.getDriveIDAndPath(sourcePath);
                driveItemCollectionPage = provider.getUserRequestBuilder(useEmail, accountID)
                        .drives(content.get(0)).root()
                        .itemWithPath(content.get(1)).children().buildRequest().get();
                if (driveItemCollectionPage != null) {
                    processResponse(driveItemCollectionPage, payload);
                }
            }
        } catch (GraphServiceException | IllegalArgumentException cause) {
            LOGGER.info(cause.getMessage(), cause);
        }
    }

    /**
     * Processes a collection of drive items to extract file information.
     * Calculates statistics such as file count, folder count, oldest/newest files, etc.
     *
     * @param page    The collection of drive items to process
     * @param payload The payload to update with the results
     */
    private void processResponse(DriveItemCollectionPage page, FreeForm payload) {
        try {
            int filesCount = 0;
            int folderCount = 0;
            long oldestFileCreatedate = 0L;
            long newestFileCreateDate = 0L;
            long lastModifiedFileCreateDate = 0L;
            String oldestFile = null;
            String newestFile = null;
            String lastModifiedFileName = null;
            String lastModifiedFileId = null;
            if (page != null && page.getCurrentPage().isEmpty()) {
                ResponseUtil.emptyFolderResponse(true, Constants.FOLDER_IS_EMPTY, payload);
                return;
            }
            while (page != null && !page.getCurrentPage().isEmpty()) {
                for (DriveItem driveInPage : page.getCurrentPage()) {
                    if (driveInPage.folder != null) {
                        folderCount++;
                    }
                    if (driveInPage.file != null) {
                        filesCount++;
                    }
                    newestFileCreateDate = Math.max(newestFileCreateDate, getFileDate(driveInPage.createdDateTime));
                    if (oldestFileCreatedate == 0L) {
                        oldestFileCreatedate = getFileDate(driveInPage.createdDateTime);
                    }
                    oldestFileCreatedate = Math.min(oldestFileCreatedate, getFileDate(driveInPage.createdDateTime));
                    lastModifiedFileCreateDate = Math.max(lastModifiedFileCreateDate, getFileDate(driveInPage.lastModifiedDateTime));
                }
                String oldFile = getFileNameByDate(page, oldestFileCreatedate);
                oldestFile = oldFile == null && oldestFile != null ? oldestFile : oldFile;
                String newFile = getFileNameByDate(page, newestFileCreateDate);
                newestFile = newFile == null && newestFile != null ? newestFile : newFile;
                String lastFileName = getFileNameAndIDByLastModified(page, lastModifiedFileCreateDate).get(0);
                lastModifiedFileName = lastFileName.isBlank() && lastModifiedFileName != null ? lastModifiedFileName : lastFileName;
                String lastFileID = getFileNameAndIDByLastModified(page, lastModifiedFileCreateDate).get(1);
                lastModifiedFileId = lastFileID.isBlank() && lastModifiedFileId != null ? lastModifiedFileId : lastFileID;

                if (page.getNextPage() != null) {
                    page = page.getNextPage().buildRequest().get();
                } else {
                    ResponseUtil.getInfoResponse(filesCount, folderCount
                            , (long) payload.get(Constants.SIZE)
                            , oldestFile
                            , oldestFileCreatedate
                            , newestFile
                            , newestFileCreateDate
                            , lastModifiedFileId
                            , lastModifiedFileName
                            , lastModifiedFileCreateDate,
                            true, "Folder information fetched successfully"
                            , payload);
                    break;
                }
            }
        } catch (GraphServiceException | IllegalArgumentException cause) {
            LOGGER.info(cause.getMessage(), cause);
            return;
        }
    }

    private List<String> getFileNameAndIDByLastModified(DriveItemCollectionPage page, long lastModifiedFileCreateDate) {
        if (page != null && !page.getCurrentPage().isEmpty()) {
            for (DriveItem driveInPage : page.getCurrentPage()) {
                if (getFileDate(driveInPage.lastModifiedDateTime) == lastModifiedFileCreateDate) {
                    return List.of(driveInPage.name != null
                            ? driveInPage.name
                            : Constants.EMPTY_STRING, driveInPage.id != null
                            ? driveInPage.id
                            : Constants.EMPTY_STRING);
                }
            }
        }
        return List.of(Constants.EMPTY_STRING, Constants.EMPTY_STRING);
    }

    private String getFileNameByDate(DriveItemCollectionPage page, long fileCreateDate) {
        if (page != null && !page.getCurrentPage().isEmpty()) {
            for (DriveItem driveInPage : page.getCurrentPage()) {
                if (getFileDate(driveInPage.createdDateTime) == fileCreateDate) {
                    return driveInPage.name;
                }
            }
        }
        return null;
    }

    /**
     * Converts an OffsetDateTime to a Unix timestamp (milliseconds since epoch).
     *
     * @param dateTime The OffsetDateTime to convert
     * @return The Unix timestamp, or 0 if dateTime is null
     */
    private long getFileDate(OffsetDateTime dateTime) {
        return dateTime != null ? Timestamp.valueOf(dateTime.toLocalDateTime()).getTime() : 0L;
    }

    /**
     * Checks if a folder exists at the specified path.
     * Updates the payload with the result of the check.
     *
     * @param sourcePath The path to check
     * @param payload    The payload to update with the result
     * @param useEmail   Whether to use email for authentication
     * @param accountID  The account ID for authentication
     */
    private void checkIfFolderExist(String sourcePath, FreeForm payload, boolean useEmail, String accountID) {
        try {
            if (sourcePath != null) {
                DriveItem driveItem;

                if (sourcePath.contains(Constants.DRIVE_ROOT)) {
                    String path = FileUtil.getRelativeFilePath(sourcePath);
                    driveItem = provider.getUserRequestBuilder(useEmail, accountID)
                            .drive()
                            .root()
                            .itemWithPath(path)
                            .buildRequest().get();

                    if (driveItem != null && driveItem.folder != null) {
                        ResponseUtil.addFileSize(driveItem.size != null ? driveItem.size : 0L, payload);
                        ResponseUtil.getInfoResponse(true, Constants.FILE_FOUND, payload);
                    } else {
                        ResponseUtil.getInfoResponse(false, Constants.PLEASE_PROVIDE_VALID_FOLDER_PATH, payload);
                    }
                } else if (FileUtil.checkDriveIDContains(sourcePath)) {
                    List<String> content = FileUtil.getDriveIDAndPath(sourcePath);
                    driveItem = provider.getUserRequestBuilder(useEmail, accountID)
                            .drives(content.get(0))
                            .root()
                            .itemWithPath(content.get(1))
                            .buildRequest().get();

                    if (driveItem != null && driveItem.folder != null) {
                        ResponseUtil.addFileSize(driveItem.size != null ? driveItem.size : 0L, payload);
                        ResponseUtil.getInfoResponse(true, Constants.FILE_FOUND, payload);
                    } else {
                        ResponseUtil.getInfoResponse(false, Constants.PLEASE_PROVIDE_VALID_FOLDER_PATH, payload);
                    }
                } else {
                    ResponseUtil.getInfoResponse(false, Constants.PLEASE_PROVIDE_VALID_FOLDER_PATH, payload);
                }
            }

        } catch (GraphServiceException | IllegalArgumentException cause) {
            LOGGER.info(cause.getMessage(), cause);
            ResponseUtil.getInfoResponse(false, Objects.requireNonNull(cause.getMessage())
                    .contains(Constants.ITEM_NOT_FOUND)
                    ? Constants.FILE_NOT_FOUND
                    : Constants.PLEASE_PROVIDE_VALID_FOLDER_PATH, payload);
        }
    }


    /**
     * Creates a new folder in OneDrive.
     * This method handles folder creation in different drive types (personal, shared, drive by ID)
     * and manages validation and re-entry scenarios for both folder name and target path.
     *
     * @param folderName            The name of the folder to create
     * @param path                  The path where the folder should be created
     * @param isReenteredFolderName Whether this is a re-entered request after folder name validation
     * @param isReenteredTargetPath Whether this is a re-entered request after target path validation
     * @return ExtensionResponse containing the result of the operation or validation request
     */
    public ExtensionResponse createFolder(String folderName, String path, boolean isReenteredFolderName, boolean isReenteredTargetPath) {
        long startTime = System.currentTimeMillis();
        LOGGER.info("FileManagementDao.createFolder - Starting folder creation | folderName: {}, path: {}, isReenteredFolderName: {}, isReenteredTargetPath: {}",
                folderName, path, isReenteredFolderName, isReenteredTargetPath);
        telemetryHelper.incrementCount(TelemetryConstants.TELEMETRY_CREATE_FOLDER);

        try {
            if (folderName == null || folderName.isBlank()) {
                LOGGER.info("FileManagementDao.createFolder - Empty folder name provided | path: {}", path);
                telemetryHelper.recordValidationError(TelemetryConstants.TELEMETRY_CREATE_FOLDER, startTime, TelemetryConstants.TELEMETRY_ERROR_FOLDER_NAME_EMPTY,
                        TelemetryHelper.safeTagMap(TelemetryConstants.TELEMETRY_TAG_FOLDER_NAME, folderName, TelemetryConstants.TELEMETRY_TAG_PATH, path));

                if (!isReenteredFolderName) {
                    LOGGER.debug("FileManagementDao.createFolder - Requesting folder name reentry | path: {}", path);
                    return reenterValidate(Validator.ValidationResource.FOLDER_NAME,
                            SubCatalogConstants.CONFIRM_REENTER_CREATE_FOLDER, folderName, path, true);
                }

                LOGGER.debug("FileManagementDao.createFolder - Returning error for empty folder name after reentry");
                return new ExtensionResponseBuilder()
                        .success(ResponseUtil.errorResponse(false, "Folder name cannot be empty"), null, null)
                        .build();
            }

            Map<String, Object> payload = new HashMap<>();
            if (FileUtil.isPersonalDrive(path)) {
                getDriveItemForPath(path, payload);
                Map<String, Object> resultPayload = executeCreateFolder(folderName, payload, false);

                if (!(boolean) resultPayload.get(Constants.SUCCESS)) {
                    String failureReason = (String) resultPayload.get(Constants.MESSAGE);

                    if (!isReenteredFolderName && Constants.FOLDER_ALREADY_EXIST.equals(failureReason)) {
                        LOGGER.debug("FileManagementDao.createFolder - Folder already exists, requesting folder name reentry");
                        telemetryHelper.recordRetryPrompted(TelemetryConstants.TELEMETRY_CREATE_FOLDER, startTime,
                                TelemetryHelper.safeTagMap(TelemetryConstants.TELEMETRY_TAG_FOLDER_NAME, folderName, TelemetryConstants.TELEMETRY_TAG_REASON, TelemetryConstants.TELEMETRY_REASON_FOLDER_ALREADY_EXISTS, TelemetryConstants.TELEMETRY_TAG_PATH, path));
                        return reenterValidate(Validator.ValidationResource.FOLDER_NAME,
                                SubCatalogConstants.CONFIRM_REENTER_CREATE_FOLDER, folderName, path, false);
                    } else if (!isReenteredTargetPath) {
                        telemetryHelper.recordRetryPrompted(TelemetryConstants.TELEMETRY_CREATE_FOLDER, startTime,
                                TelemetryHelper.safeTagMap(TelemetryConstants.TELEMETRY_TAG_FOLDER_NAME, folderName, TelemetryConstants.TELEMETRY_TAG_REASON, TelemetryConstants.TELEMETRY_REASON_INVALID_TARGET_PATH, TelemetryConstants.TELEMETRY_TAG_PATH, path));
                        return reenterValidate(Validator.ValidationResource.TARGET_PATH,
                                SubCatalogConstants.CONFIRM_REENTER_CREATE_FOLDER, path, folderName, false);
                    }

                    telemetryHelper.recordValidationError(TelemetryConstants.TELEMETRY_CREATE_FOLDER, startTime, TelemetryConstants.TELEMETRY_ERROR_CREATE_FOLDER_FAILED_PERSONAL,
                            TelemetryHelper.safeTagMap(TelemetryConstants.TELEMETRY_TAG_FOLDER_NAME, folderName, TelemetryConstants.TELEMETRY_TAG_PATH, path, TelemetryConstants.TELEMETRY_TAG_REASON, failureReason));
                } else {
                    telemetryHelper.recordSuccess(TelemetryConstants.TELEMETRY_CREATE_FOLDER, startTime,
                            TelemetryHelper.safeTagMap(TelemetryConstants.TELEMETRY_TAG_FOLDER_NAME, folderName, TelemetryConstants.TELEMETRY_TAG_PATH, path));
                }

                return new ExtensionResponseBuilder()
                        .success(resultPayload, null, null)
                        .build();

            } else if (FileUtil.checkDriveIDContains(path)) {
                LOGGER.debug("FileManagementDao.createFolder - Processing path with drive ID | path: {}", path);
                List<String> content = FileUtil.getDriveIDAndPath(path);
                String driveID = content.get(0);
                String itemPath = content.get(1);
                getDriveItemForSharedPath(driveID, itemPath, payload);
                Map<String, Object> resultPayload = executeCreateFolder(folderName, payload, true);

                if (!(boolean) resultPayload.get(Constants.SUCCESS)) {
                    if (!isReenteredTargetPath) {
                        telemetryHelper.recordRetryPrompted("OneDrive.createFolder", startTime,
                                TelemetryHelper.safeTagMap("folderName", folderName, "path", path, "reason", "shared_path_failure"));
                        return reenterValidate(Validator.ValidationResource.TARGET_PATH,
                                SubCatalogConstants.CONFIRM_REENTER_CREATE_FOLDER, path, folderName, false);
                    }

                    telemetryHelper.recordValidationError("OneDrive.createFolder", startTime,
                            "Create folder failed (shared drive)",
                            TelemetryHelper.safeTagMap("folderName", folderName, "path", path));
                } else {
                    LOGGER.info("FileManagementDao.createFolder - Folder created successfully in shared drive | folderName: {}, path: {}",
                            folderName, path);
                    telemetryHelper.recordSuccess("OneDrive.createFolder", startTime,
                            TelemetryHelper.safeTagMap("folderName", folderName, "path", path));
                }

                return new ExtensionResponseBuilder()
                        .success(resultPayload, null, null)
                        .build();

            } else {
                if (!isReenteredTargetPath) {
                    LOGGER.debug("FileManagementDao.createFolder - Requesting target path reentry for invalid format");
                    telemetryHelper.recordRetryPrompted("OneDrive.createFolder", startTime,
                            TelemetryHelper.safeTagMap("folderName", folderName, "path", path, "reason", "invalid_path_format"));
                    return reenterValidate(Validator.ValidationResource.TARGET_PATH,
                            SubCatalogConstants.CONFIRM_REENTER_CREATE_FOLDER, path, folderName, false);
                }

                telemetryHelper.recordValidationError("OneDrive.createFolder", startTime,
                        "Invalid source path format",
                        TelemetryHelper.safeTagMap("folderName", folderName, "path", path));
            }

        } catch (Exception e) {
            LOGGER.error("FileManagementDao.createFolder - Unexpected error", e);
            telemetryHelper.recordError(TelemetryConstants.TELEMETRY_CREATE_FOLDER, startTime, e,
                    TelemetryHelper.safeTagMap(TelemetryConstants.TELEMETRY_TAG_FOLDER_NAME, folderName, TelemetryConstants.TELEMETRY_TAG_PATH, path, TelemetryConstants.TELEMETRY_TAG_ERROR_TYPE, e.getClass().getSimpleName()));
        }

        LOGGER.debug("FileManagementDao.createFolder - Returning default error response");
        return new ExtensionResponseBuilder()
                .success(ResponseUtil.errorResponse(false, Constants.PLEASE_PROVIDE_VALID_SOURCE_PATH), null, null)
                .build();
    }

    /**
     * Executes the folder creation operation after validation.
     * This method handles the actual creation of the folder in OneDrive
     * and processes the response.
     *
     * @param folderName The name of the folder to create
     * @param payload    The payload containing the parent drive item
     * @param isShared   Whether the folder is being created in a shared drive
     * @return Map containing the result of the operation
     */
    private Map<String, Object> executeCreateFolder(String folderName, Map<String, Object> payload, boolean isShared) {
        if (payload.containsKey(Constants.DRIVE_ITEM)) {
            DriveItem parentDriveItem = (DriveItem) payload.get(Constants.DRIVE_ITEM);
            DriveItem createdFolder = isShared ?
                    createFolderInDrive(parentDriveItem.id, parentDriveItem.parentReference.driveId, folderName, payload)
                    : createFolderInDrive(parentDriveItem.id, folderName, payload);

            if (createdFolder == null && payload.get(Constants.MESSAGE).toString().equals(Constants.FOLDER_ALREADY_EXIST)) {
                return ResponseUtil.errorResponse(false, Constants.FOLDER_ALREADY_EXIST);
            } else {
                app.krista.extensions.essentials.cloudfiles.onedrive.catalog.entities.File fileEntity =
                        CatalogTypes.fromFile(new FileImpl(createdFolder, null, null));
                return ResponseUtil.createFolderResponse(fileEntity, true, "Folder created successfully");
            }
        }
        return payload;
    }


    /**
     * Retrieves a drive item for a personal drive path.
     * This method gets the parent folder where a new folder will be created.
     *
     * @param path    The path to retrieve the drive item for
     * @param payload The payload to update with the drive item or error
     */
    private void getDriveItemForPath(String path, Map<String, Object> payload) {
        try {
            String relativeFilePath = FileUtil.getRelativeFilePath(path);
            DriveItem parentFolder = FileUtil.getRootUserRequestBuilder(provider)
                    .itemWithPath(relativeFilePath.isBlank() ? "/" : relativeFilePath)
                    .buildRequest()
                    .get();
            payload.put(Constants.DRIVE_ITEM, parentFolder);
        } catch (GraphServiceException cause) {
            LOGGER.error("Error in finding folder path:{} ", cause.getMessage());
            payload.put(Constants.MESSAGE, FileUtil.handleFolderExistenceMessage(Objects.requireNonNull(cause.getMessage())));
        }
    }

    /**
     * Retrieves a drive item for a shared drive path.
     * This method gets the parent folder in a shared drive where a new folder will be created.
     *
     * @param driveId The ID of the drive
     * @param path    The path within the drive
     * @param payload The payload to update with the drive item or error
     */
    private void getDriveItemForSharedPath(String driveId, String path, Map<String, Object> payload) {
        try {
            DriveItem parentFolder = FileUtil.getSharedUserRequestBuilder(provider, driveId).itemWithPath(path)
                    .buildRequest()
                    .get();
            payload.put(Constants.DRIVE_ITEM, parentFolder);
        } catch (GraphServiceException cause) {
            LOGGER.error("Error in finding folder path:{} ", cause.getMessage());
            payload.put(Constants.MESSAGE, FileUtil.handleFolderExistenceMessage(Objects.requireNonNull(cause.getMessage())));
        }
    }


    /**
     * Creates a folder in a personal drive.
     * This is a convenience method that calls the more general createFolderInDrive method.
     *
     * @param parentFolderId The ID of the parent folder
     * @param folderName     The name of the folder to create
     * @param payload        The payload to update with the result
     * @return The created drive item, or null if creation failed
     */
    private DriveItem createFolderInDrive(String parentFolderId, String folderName, Map<String, Object> payload) {
        return createFolderInDrive(parentFolderId, null, folderName, payload);
    }

    /**
     * Creates a folder in a drive.
     * This method handles the actual API call to create a folder in OneDrive.
     *
     * @param parentFolderId The ID of the parent folder
     * @param driveId        The ID of the drive (null for personal drive)
     * @param folderName     The name of the folder to create
     * @param payload        The payload to update with the result
     * @return The created drive item, or null if creation failed
     */
    private DriveItem createFolderInDrive(String parentFolderId, String driveId, String folderName, Map<String, Object> payload) {
        try {
            DriveItem createdFolder = FileUtil.getUserRequestBuilderWithItemId(provider, driveId, parentFolderId)
                    .children()
                    .buildRequest()
                    .post(DriveUtil.getDriveItemForItem(folderName));
            payload.put(Constants.MESSAGE, Constants.SUCCESS);
            return createdFolder;
        } catch (GraphServiceException cause) {
            LOGGER.error("Error creating folder: {}", cause.getMessage());
            payload.put(Constants.MESSAGE, FileUtil.handleFolderExistenceMessage(Objects.requireNonNull(cause.getMessage())));
        }
        return null;
    }

    /**
     * Validates and re-enters a request for file information.
     * This method is called when the initial request fails validation,
     * and prompts the user to provide a valid path.
     *
     * @param source    The path to validate
     * @param useEmail  Whether to use email for authentication
     * @param accountID The account ID for authentication
     * @return An ExtensionResponse containing validation results
     */
    public ExtensionResponse reenterValidate(String source, boolean useEmail, String accountID) {
        ExtensionResponse validationRes = validationTypeUtil.checkValidationsWithInfo(Validator.ValidationResource.SOURCE_PATH, SubCatalogConstants.CONFIRM_REENTER_SOURCE_FILE_INFO, source, useEmail, accountID);


        if (validationRes != null) return validationRes;

        ExtensionResponse.Error error = new ErrorBuilder()
                .exceptionType(ExtensionResponse.Error.ExceptionType.INPUT_ERROR).errorMessage(Constants.PROVIDE_VALID_PATH_FILE_NOT_FOUND)
                .build();
        return new ExtensionResponseBuilder()
                .failure(error)
                .build();
    }

    /**
     * Validates and re-enters a request for listing files.
     * This method is called when the initial request fails validation,
     * and prompts the user to provide a valid path.
     *
     * @param source    The path to validate
     * @param search    The search term to filter files
     * @param useEmail  Whether to use email for authentication
     * @param accountID The account ID for authentication
     * @return An ExtensionResponse containing validation results
     */
    public ExtensionResponse reenterValidateList(String source, String search, boolean useEmail, String accountID) {
        ExtensionResponse validationRes = validationTypeUtil.checkValidationsListFilesOrFolder(Validator.ValidationResource.SOURCE_PATH, SubCatalogConstants.CONFIRM_REENTER_SOURCE_FILE_PATH, source, search, useEmail, accountID);


        if (validationRes != null) return validationRes;

        ExtensionResponse.Error error = new ErrorBuilder()
                .exceptionType(ExtensionResponse.Error.ExceptionType.INPUT_ERROR).errorMessage(Constants.PROVIDE_VALID_PATH_FILE_NOT_FOUND)
                .build();
        return new ExtensionResponseBuilder()
                .failure(error)
                .build();
    }

    /**
     * Validates and re-enters a request with two fields.
     * This method is used for validation scenarios that involve two input fields,
     * such as folder name and path.
     *
     * @param validationType      The type of validation to perform
     * @param subCatalogConstants The sub catalog constant for the validation
     * @param field1              The first field to validate
     * @param field2              The second field to validate
     * @param isFolderName        Whether the first field is a folder name
     * @return An ExtensionResponse containing validation results
     */
    public ExtensionResponse reenterValidate(Validator.ValidationResource validationType, String subCatalogConstants, String field1, String field2, Boolean isFolderName) {
        ExtensionResponse validationRes = validationTypeUtil.checkValidationsTwoFields(validationType, subCatalogConstants, field1, field2, isFolderName);

        if (validationRes != null) return validationRes;

        ExtensionResponse.Error error = new ErrorBuilder()
                .exceptionType(ExtensionResponse.Error.ExceptionType.INPUT_ERROR).errorMessage(Constants.PROVIDE_VALID_PATH_FILE_NOT_FOUND)
                .build();
        return new ExtensionResponseBuilder()
                .failure(error)
                .build();
    }


}