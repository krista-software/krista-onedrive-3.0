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


import app.krista.extensions.essentials.cloudfiles.onedrive.OnedriveAttributes;
import app.krista.extensions.essentials.cloudfiles.onedrive.catalog.CatalogTypes;
import app.krista.extensions.essentials.cloudfiles.onedrive.catalog.FileHandlers.TelemetryHelper;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.connectors.GraphServiceClientProvider;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.connectors.GraphServiceClientProviderFactory;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.util.Constants;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.util.ResponseUtil;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.util.TelemetryConstants;
import app.krista.extensions.essentials.cloudfiles.onedrive.service.File;
import app.krista.model.base.FreeForm;
import com.microsoft.graph.http.GraphServiceException;
import com.microsoft.graph.models.DriveItem;
import com.microsoft.graph.models.Permission;
import com.microsoft.graph.models.SharePointIdentitySet;
import com.microsoft.graph.requests.DriveItemDeltaCollectionPage;
import com.microsoft.graph.requests.PermissionCollectionPage;
import org.jetbrains.annotations.NotNull;
import org.jvnet.hk2.annotations.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Data Access Object for managing permissions on OneDrive files.
 * This class provides functionality to retrieve files based on permission levels
 * and interact with the Microsoft Graph API for permission-related operations.
 */
@Service
public class PermissionDao {

    private static final Logger LOGGER = LoggerFactory.getLogger(PermissionDao.class);

    private final GraphServiceClientProvider provider;
    private final OnedriveAttributes onedriveAttributes;
    private final TelemetryHelper telemetryHelper;

    @Inject
    public PermissionDao(GraphServiceClientProviderFactory factory, OnedriveAttributes onedriveAttributes, TelemetryHelper telemetryHelper) {
        this(factory.create(), onedriveAttributes, telemetryHelper);
    }

    /**
     * Constructs a PermissionDao with explicit dependencies.
     * This constructor is primarily used for testing or when manual instantiation is required.
     *
     * @param provider           The provider for Microsoft Graph API client
     * @param onedriveAttributes The OneDrive attributes containing user configuration
     * @param telemetryHelper    Helper for recording telemetry data
     */
    public PermissionDao(GraphServiceClientProvider provider, OnedriveAttributes onedriveAttributes, TelemetryHelper telemetryHelper) {
        this.provider = provider;
        this.onedriveAttributes = onedriveAttributes;
        this.telemetryHelper = telemetryHelper;
    }

    /**
     * Retrieves files with a specific permission level.
     * This method serves as a public entry point with exception handling.
     *
     * @param permission The permission level to filter by (Read, Write, etc.)
     * @param useEmail   Whether to use email for authentication
     * @param accountID  The account ID for authentication
     * @return FreeForm payload containing the list of files or error information
     * @throws IllegalArgumentException If an error occurs during the operation
     */
    public FreeForm getFileByPermission(String permission, boolean useEmail, String accountID) {
        try {
            return fetchFilesWithPermissions(permission, useEmail, accountID);
        } catch (GraphServiceException | IllegalArgumentException cause) {
            LOGGER.info(cause.getMessage(), cause);
            throw new IllegalArgumentException(cause.getMessage(), cause);
        }
    }

    /**
     * Fetches files with a specific permission level from OneDrive.
     * This method performs the actual retrieval of files based on permission,
     * handling pagination and filtering of results.
     *
     * @param permission The permission level to filter by (Read, Write, etc.)
     * @param useEmail   Whether to use email for authentication
     * @param accountID  The account ID for authentication
     * @return FreeForm payload containing the list of files or error information
     */
    @NotNull
    public FreeForm fetchFilesWithPermissions(String permission, boolean useEmail, String accountID) {
        long startTime = System.currentTimeMillis();
        LOGGER.info("PermissionDao.fetchFilesWithPermissions - Starting file retrieval by permission | permission: {}, useEmail: {}, accountID: {}",
                permission, useEmail, accountID);
        telemetryHelper.incrementCount(TelemetryConstants.TELEMETRY_FETCH_FILES_WITH_PERMISSIONS);
        FreeForm payload = new FreeForm();
        try {
            List<File> files = new ArrayList<>();
            boolean unsupportedPermission = permission.equalsIgnoreCase(Constants.READ_AND_WRITE)
                    || permission.equalsIgnoreCase(Constants.OWN);

            if (unsupportedPermission) {
                LOGGER.warn("PermissionDao.fetchFilesWithPermissions - Unsupported permission requested | permission: {}", permission);
                String message = "Permission [ " + permission + " ] is Not Supported in OneDrive";
                telemetryHelper.recordValidationError(TelemetryConstants.TELEMETRY_FETCH_FILES_WITH_PERMISSIONS, startTime,
                        TelemetryConstants.TELEMETRY_ERROR_UNSUPPORTED_PERMISSION,
                        TelemetryHelper.safeTagMap(TelemetryConstants.TELEMETRY_TAG_PERMISSION, permission,
                                TelemetryConstants.TELEMETRY_TAG_ACCOUNT_ID, accountID,
                                TelemetryConstants.TELEMETRY_TAG_USE_EMAIL, String.valueOf(useEmail)));

                ResponseUtil.listOfFileEntityResponse(
                        files.stream().map(CatalogTypes::fromFile).collect(Collectors.toList()),
                        false,
                        message,
                        payload
                );
                return payload;
            }

            LOGGER.debug("PermissionDao.fetchFilesWithPermissions - Permission validation passed, fetching drive items");
            DriveItemDeltaCollectionPage page = provider
                    .getUserRequestBuilder(useEmail, accountID)
                    .drive()
                    .root()
                    .delta()
                    .buildRequest()
                    .get();

            while (page != null && !page.getCurrentPage().isEmpty()) {
                for (DriveItem driveInPage : page.getCurrentPage()) {
                    if (driveInPage.name != null && !driveInPage.name.equals("root")) {
                        iterateDriveItemWithPermission(permission, driveInPage, useEmail, accountID, files);
                    }
                }
                if (page.getNextPage() != null) {
                    page = page.getNextPage().buildRequest().get();
                } else {
                    String successMessage = "File With [ " + permission + " ] permission Fetched Successfully";
                    telemetryHelper.recordSuccess(TelemetryConstants.TELEMETRY_FETCH_FILES_WITH_PERMISSIONS, startTime,
                            TelemetryHelper.safeTagMap(TelemetryConstants.TELEMETRY_TAG_PERMISSION, permission,
                                    TelemetryConstants.TELEMETRY_TAG_ACCOUNT_ID, accountID,
                                    TelemetryConstants.TELEMETRY_TAG_FILE_COUNT, String.valueOf(files.size()),
                                    TelemetryConstants.TELEMETRY_TAG_USE_EMAIL, String.valueOf(useEmail)));

                    ResponseUtil.listOfFileEntityResponse(
                            files.stream().map(CatalogTypes::fromFile).collect(Collectors.toList()),
                            true,
                            successMessage,
                            payload
                    );
                    break;
                }
            }

        } catch (GraphServiceException | IllegalArgumentException cause) {
            LOGGER.error("PermissionDao.fetchFilesWithPermissions - Exception occurred | permission: {}, error: {}",
                    permission, cause.getMessage(), cause);
            String errorMessage = cause.getMessage() != null && cause.getMessage().contains(Constants.ITEM_NOT_FOUND)
                    ? Constants.FILE_NOT_FOUND
                    : Constants.PLEASE_PROVIDE_VALID_SOURCE_PATH;
            telemetryHelper.recordError(TelemetryConstants.TELEMETRY_FETCH_FILES_WITH_PERMISSIONS, startTime, cause,
                    TelemetryHelper.safeTagMap(TelemetryConstants.TELEMETRY_TAG_PERMISSION, permission,
                            TelemetryConstants.TELEMETRY_TAG_ACCOUNT_ID, accountID,
                            TelemetryConstants.TELEMETRY_TAG_ERROR_TYPE, cause.getClass().getSimpleName(),
                            TelemetryConstants.TELEMETRY_TAG_USE_EMAIL, String.valueOf(useEmail)));

            ResponseUtil.getInfoResponse(false, errorMessage, payload);
        }
        return payload;
    }


    /**
     * Processes a drive item to check if it has the specified permission.
     * This method retrieves the permissions for a drive item and checks if any
     * of them match the requested permission level for the current user.
     *
     * @param permission  The permission level to check for
     * @param driveInPage The drive item to check permissions on
     * @param useEmail    Whether to use email for authentication
     * @param accountID   The account ID for authentication
     * @param files       The list to add matching files to
     */
    private void iterateDriveItemWithPermission(String permission, DriveItem driveInPage, boolean useEmail, String accountID, List<File> files) {
        PermissionCollectionPage permissionCollectionPage = provider.getUserRequestBuilder(useEmail, accountID).drive().items(driveInPage.id).permissions().buildRequest().get();
        while (permissionCollectionPage != null && !permissionCollectionPage.getCurrentPage().isEmpty()) {
            for (Permission permissionPage : permissionCollectionPage.getCurrentPage()) {
                String role = permission.equals(Constants.OWN) ? "owner" : permission;
                if (permissionPage.roles != null && permissionPage.roles.contains(role.toLowerCase())) {

                    if (permissionPage.grantedToV2 != null && permissionPage.grantedToV2.user != null) {
                        String email = permissionPage.grantedToV2.user.additionalDataManager().get(Constants.EMAIL) != null
                                ? permissionPage.grantedToV2.user.additionalDataManager().get(Constants.EMAIL).getAsString() : "";
                        if (!email.isBlank() && email.equalsIgnoreCase(onedriveAttributes.getMailId())) {
                            files.add(new FileImpl(driveInPage, null, null));
                        }

                    } else if (permissionPage.grantedToIdentitiesV2 != null) {
                        for (SharePointIdentitySet set : permissionPage.grantedToIdentitiesV2) {
                            String email = set.user != null ? Objects.requireNonNullElse(set.user.additionalDataManager().get(Constants.EMAIL), "").toString() : "";
                            if (!email.isBlank() && email.replace("\"", "").equalsIgnoreCase(onedriveAttributes.getMailId())) {
                                files.add(new FileImpl(driveInPage, null, null));
                            }


                        }
                    }
                }
            }
            if (permissionCollectionPage.getNextPage() != null) {
                permissionCollectionPage = permissionCollectionPage.getNextPage().buildRequest().get();
            } else {
                break;
            }
        }
    }

}