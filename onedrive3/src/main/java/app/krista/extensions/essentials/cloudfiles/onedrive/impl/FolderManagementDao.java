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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class FolderManagementDao {

    private static final Logger LOGGER = LoggerFactory.getLogger(FolderManagementDao.class);
    private final GraphServiceClientProvider provider;
    private final OnedriveAttributes onedriveAttributes;
    private final ValidationTypeUtil validationTypeUtil;
    private final TelemetryHelper telemetryHelper;

    @Inject
    public FolderManagementDao(GraphServiceClientProviderFactory factory, OnedriveAttributes onedriveAttributes, ValidationTypeUtil validationTypeUtil, TelemetryHelper telemetryHelper) {
        this(factory.create(), onedriveAttributes, validationTypeUtil, telemetryHelper);
    }

    public FolderManagementDao(GraphServiceClientProvider provider, OnedriveAttributes onedriveAttributes, ValidationTypeUtil validationTypeUtil, TelemetryHelper telemetryHelper) {
        this.provider = provider;
        this.onedriveAttributes = onedriveAttributes;
        this.validationTypeUtil = validationTypeUtil;
        this.telemetryHelper = telemetryHelper;
    }

    /**
     * Retrieves a list of folders from the specified path.
     * Supports filtering by search pattern and handles both personal and shared drives.
     *
     * @param relativePath The relative path to list folders from (e.g., "Documents/Projects")
     * @param search Optional regex pattern to filter folder names (can be null)
     * @param useEmail Whether to use email-based authentication (admin) or user-specific auth
     * @param accountID The account ID for user-specific authentication (null for admin)
     * @return FreeForm containing the list of folders and operation status
     */

    @NotNull
    public FreeForm getListOfFolders(String relativePath, String search, boolean useEmail, String accountID) {
        FreeForm payload = new FreeForm();
        long startTime = System.currentTimeMillis();
        LOGGER.info("FolderManagementDao.getListOfFolders - Starting folder list retrieval | relativePath: {}, search: {}, useEmail: {}, accountID: {}",
                relativePath, search, useEmail, accountID);
        telemetryHelper.incrementCount("OneDrive.getListOfFolders");

        try {
            List<File> files = new ArrayList<>();
            if (relativePath != null) {
                LOGGER.debug("FolderManagementDao.getListOfFolders - Processing non-null relativePath: {}", relativePath);

                if (FileUtil.checkDriveIDContains(relativePath)) {
                    LOGGER.debug("FolderManagementDao.getListOfFolders - Detected drive ID path, extracting drive ID and path");
                    List<String> content = FileUtil.getDriveIDAndPath(relativePath);
                    getRootFilesByDriveIDPath(content.get(0), content.get(1), files, useEmail, accountID, search, payload);
                    telemetryHelper.recordSuccess("OneDrive.getListOfFolders", startTime,
                            TelemetryHelper.safeTagMap("relativePath", relativePath, "pathType", "driveId"));
                    return payload;
                }
                if (FileUtil.isPersonalDrive(relativePath)) {
                    LOGGER.debug("FolderManagementDao.getListOfFolders - Detected personal drive path");
                    String path = FileUtil.getRelativeFilePath(relativePath);
                    getRootFilesByPath(path, files, useEmail, accountID, search, payload);
                    telemetryHelper.recordSuccess("OneDrive.getListOfFolders", startTime,
                            TelemetryHelper.safeTagMap("relativePath", relativePath, "pathType", "personal"));
                    LOGGER.info("FolderManagementDao.getListOfFolders - Successfully processed personal drive path | filesCount: {}", files.size());
                    return payload;
                }
                if (relativePath.startsWith(Constants.SHARED) || relativePath.startsWith(Constants.FORWARD_SLASH_SHARED)) {
                    LOGGER.info("FolderManagementDao.getListOfFolders - Processing shared folders | relativePath: {}", relativePath);
                    getSharedFolders(relativePath, files, search, useEmail, accountID, payload);
                    telemetryHelper.recordSuccess("OneDrive.getListOfFolders", startTime,
                            TelemetryHelper.safeTagMap("relativePath", relativePath, "pathType", "shared"));
                    return payload;
                }
                getRootFilesByPath(relativePath, files, useEmail, accountID, search, payload);
                telemetryHelper.recordSuccess("OneDrive.getListOfFolders", startTime,
                        TelemetryHelper.safeTagMap("relativePath", relativePath, "pathType", "default"));
                LOGGER.info("FolderManagementDao.getListOfFolders - Successfully processed default path | filesCount: {}", files.size());
                return payload;
            }
            telemetryHelper.recordValidationError("OneDrive.getListOfFolders", startTime,
                    "Relative path is null", TelemetryHelper.safeTagMap("relativePath", "null"));
            return payload;
        } catch (GraphServiceException | IllegalArgumentException cause) {
            LOGGER.error("FolderManagementDao.getListOfFolders - Exception occurred | relativePath: {}, error: {}", relativePath, cause.getMessage(), cause);
            telemetryHelper.recordError("OneDrive.getListOfFolders", startTime, cause,
                    TelemetryHelper.safeTagMap("relativePath", relativePath, "error_type", cause.getClass().getSimpleName()));
            ResponseUtil.getInfoResponse(false, Objects.requireNonNull(cause.getMessage())
                    .contains(Constants.ITEM_NOT_FOUND)
                    ? Constants.FILE_NOT_FOUND
                    : Constants.PLEASE_PROVIDE_VALID_FOLDER_PATH, payload);
        }
        return payload;
    }

    public void getRootFilesByDriveIDPath(String driveID, String relativePath, List<File> files, boolean useEmail, String accountID, String search, FreeForm payload) {
        try {
            DriveItem driveItem = provider.getUserRequestBuilder(useEmail, accountID)
                    .drives(driveID)
                    .root()
                    .itemWithPath(relativePath)
                    .buildRequest().expand(Constants.PERMISSIONS).get();

            if (driveItem != null && driveItem.id != null && driveItem.folder != null) {
                DriveItemCollectionPage page = provider.getUserRequestBuilder(useEmail, accountID)
                        .drives(driveID)
                        .root()
                        .itemWithPath(relativePath)
                        .children().buildRequest().get();
                if (page != null && page.getCurrentPage().isEmpty()) {
                    ResponseUtil.emptyFolderResponse(true, Constants.FOLDER_IS_EMPTY, payload);
                    return;
                }
                while (page != null && !page.getCurrentPage().isEmpty()) {
                    for (DriveItem driveInPage : page.getCurrentPage()) {
                        if (search != null && !search.isEmpty()
                                && driveInPage.id != null && driveInPage.folder != null
                                && driveInPage.name != null && driveInPage.name.contains(search)) {

                            PermissionCollectionPage permissionPage = provider.getUserRequestBuilder(useEmail, accountID)
                                    .drives(driveID)
                                    .items(driveInPage.id)
                                    .permissions()
                                    .buildRequest().get();

                            files.add(new FileImpl(driveInPage, permissionPage, onedriveAttributes));
                        }
                        if (search != null && search.isBlank() && driveInPage.folder != null && driveInPage.id != null) {
                            PermissionCollectionPage permissionPage = provider.getUserRequestBuilder(useEmail, accountID)
                                    .drives(driveID)
                                    .items(driveInPage.id)
                                    .permissions()
                                    .buildRequest().get();
                            files.add(new FileImpl(driveInPage, permissionPage, onedriveAttributes));
                        }
                    }

                    if (page.getNextPage() != null) {
                        page = page.getNextPage().buildRequest().get();
                    } else {
                        fetchFolderResponse(files, payload);
                        break;
                    }
                }
            } else {
                ResponseUtil.getInfoResponse(false, Constants.PLEASE_PROVIDE_VALID_FOLDER_PATH, payload);
            }
        } catch (GraphServiceException | IllegalArgumentException cause) {
            LOGGER.info(cause.getMessage(), cause);
            ResponseUtil.getInfoResponse(false, Objects.requireNonNull(cause.getMessage())
                    .contains(Constants.ITEM_NOT_FOUND)
                    ? Constants.FILE_NOT_FOUND
                    : Constants.PLEASE_PROVIDE_VALID_FOLDER_PATH, payload);
        }
    }

    private static void fetchFolderResponse(List<File> files, FreeForm payload) {
        if (files != null && !files.isEmpty()) {
            ResponseUtil.listOfFileEntityResponse(files.stream()
                    .map(CatalogTypes::fromFile)
                    .collect(Collectors.toList()), true, Constants.FOLDERS_FETCHED_SUCCESSFULLY, payload);
        } else {
            ResponseUtil.getInfoResponse(false, Constants.FOLDER_NAME_LIST_IS_EMPTY_OR_NULL, payload);
        }
    }

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
                                && driveInPage.folder != null && driveInPage.name != null
                                && driveInPage.name.contains(search)) {
                            PermissionCollectionPage permissionPage = getPermissionCollectionPage(useEmail, accountID, driveInPage);

                            files.add(new FileImpl(driveInPage, permissionPage, onedriveAttributes));
                        }
                        if (search != null && search.isBlank() && driveInPage.folder != null && driveInPage.id != null) {
                            PermissionCollectionPage permissionPage = getPermissionCollectionPage(useEmail, accountID, driveInPage);

                            files.add(new FileImpl(driveInPage, permissionPage, onedriveAttributes));
                        }
                    }
                    if (page.getNextPage() != null) {
                        page = page.getNextPage().buildRequest().get();
                    } else {
                        fetchFolderResponse(files, payload);
                        break;
                    }
                }
            } else {
                ResponseUtil.getInfoResponse(false, Constants.PLEASE_PROVIDE_VALID_FOLDER_PATH, payload);
            }
        } catch (GraphServiceException | IllegalArgumentException cause) {
            LOGGER.info(cause.getMessage(), cause);
            ResponseUtil.getInfoResponse(false, Objects.requireNonNull(cause.getMessage())
                    .contains(Constants.ITEM_NOT_FOUND)
                    ? Constants.FILE_NOT_FOUND
                    : Constants.PLEASE_PROVIDE_VALID_FOLDER_PATH, payload);
        }
    }

    private void getSharedFolders(String relativePath, List<File> files, String search, boolean useEmail, String accountID, FreeForm payload) {
        try {
            DriveSharedWithMeCollectionPage page = provider.getUserRequestBuilder(useEmail, accountID)
                    .drive().
                    sharedWithMe()
                    .buildRequest().top(100000).get();
            if (page != null && page.getCurrentPage().isEmpty()) {
                ResponseUtil.emptyFolderResponse(true, Constants.FOLDER_IS_EMPTY, payload);
                return;
            }
            while (page != null && !page.getCurrentPage().isEmpty()) {
                for (DriveItem driveInPage : page.getCurrentPage()) {
                    if (driveInPage != null && driveInPage.folder != null && driveInPage.id != null
                            && driveInPage.remoteItem != null
                            && driveInPage.remoteItem.parentReference != null
                            && driveInPage.remoteItem.parentReference.driveId != null) {

                        if (relativePath.equals(Constants.FORWARD_SLASH_SHARED) || relativePath.equals(Constants.SHARED)) {
                            if (search != null && !search.isEmpty() && driveInPage.name != null && driveInPage.name.contains(search)) {
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
                    fetchFolderResponse(files, payload);
                    break;
                }
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

    @Nullable
    private ExtensionResponse getExtensionResponse(String relativePath, String search, boolean useEmail, String accountID, boolean reenter, FreeForm payload) {
        if (payload.get(Constants.MESSAGE) != null && (payload.get(Constants.MESSAGE).equals(Constants.FOLDER_IS_EMPTY) || payload.get(Constants.MESSAGE).equals(Constants.FILE_NAME_LIST_IS_EMPTY_OR_NULL)) && !reenter) {
            return reenterValidateList(relativePath, search, useEmail, accountID);
        }
        return null;
    }

    public ExtensionResponse reenterValidateList(String source, String search, boolean useEmail, String accountID) {
        ExtensionResponse validationRes = validationTypeUtil.checkValidationsListFilesOrFolder(Validator.ValidationResource.SOURCE_PATH, SubCatalogConstants.CONFIRM_REENTER_SOURCE_PATH_LIST_FOLDER, source, search, useEmail, accountID);


        if (validationRes != null) return validationRes;

        ExtensionResponse.Error error = new ErrorBuilder()
                .exceptionType(ExtensionResponse.Error.ExceptionType.INPUT_ERROR).errorMessage(Constants.PROVIDE_VALID_PATH_FILE_NOT_FOUND)
                .build();
        return new ExtensionResponseBuilder()
                .failure(error)
                .build();
    }
}
