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
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.util.Constants;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.util.FileUtil;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.util.PermissionUtil;
import app.krista.extensions.essentials.cloudfiles.onedrive.service.File;
import com.microsoft.graph.models.DriveItem;
import com.microsoft.graph.requests.PermissionCollectionPage;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

/**
 * Implementation of the File interface for OneDrive files.
 * This class wraps a Microsoft Graph DriveItem and provides access to its properties
 * in a standardized way according to the File interface.
 */
public class FileImpl implements File {

    /**
     * The underlying Microsoft Graph DriveItem that this File represents.
     */
    private final DriveItem driveItem;

    /**
     * Collection of permissions associated with this file.
     * May be null if permissions were not requested or are not available.
     */
    private final PermissionCollectionPage permissionCollectionPage;

    /**
     * OneDrive attributes for configuration and authentication.
     */
    private final OnedriveAttributes onedriveAttributes;

    /**
     * Constructs a FileImpl with the specified DriveItem, permissions, and attributes.
     *
     * @param driveItem                The Microsoft Graph DriveItem to wrap
     * @param permissionCollectionPage The permissions associated with the DriveItem, may be null
     * @param onedriveAttributes       The OneDrive attributes for configuration, may be null
     */
    public FileImpl(DriveItem driveItem, PermissionCollectionPage permissionCollectionPage, OnedriveAttributes onedriveAttributes) {
        this.driveItem = driveItem;
        this.permissionCollectionPage = permissionCollectionPage;
        this.onedriveAttributes = onedriveAttributes;
    }

    /**
     * Gets the name of the file.
     *
     * @return The file name, or null if not available
     */
    @Override
    public String getName() {
        return driveItem.name;
    }

    /**
     * Gets the base name of the file (name without extension).
     *
     * @return The base name of the file
     */
    @Override
    public String getBaseName() {
        return FileUtil.getBaseName(driveItem.name != null ? driveItem.name : null).getFirst();
    }

    /**
     * Gets the file extension.
     *
     * @return The file extension, or an empty string if no extension exists
     */
    @Override
    public String getFileExtension() {
        return FileUtil.getBaseName(driveItem.name != null ? driveItem.name : null).get(1);
    }

    /**
     * Gets the path of the parent folder.
     *
     * @return The parent folder path, or null if not available
     */
    @Override
    public String getParentFolder() {
        return driveItem.parentReference != null ? driveItem.parentReference.path : null;
    }

    /**
     * Gets the full path of the file.
     *
     * @return The full file path
     */
    @Override
    public String getPath() {
        return FileUtil.getFilePath((driveItem.parentReference != null ? driveItem.parentReference.path : null), driveItem);
    }

    /**
     * Gets the display name of the file owner.
     *
     * @return The owner's display name, or null if not available
     */
    @Override
    public String getOwner() {
        if (driveItem.createdBy != null) {
            assert driveItem.createdBy.user != null;
            return driveItem.createdBy.user.displayName;
        } else {
            return null;
        }
    }

    /**
     * Gets the creation timestamp of the file.
     *
     * @return The creation time in milliseconds since epoch, or null if not available
     */
    @Override
    public Long getCreated() {
        return driveItem.createdDateTime != null ? Timestamp.valueOf(driveItem.createdDateTime.toLocalDateTime()).getTime() : null;
    }

    /**
     * Gets the last modification timestamp of the file.
     *
     * @return The last modification time in milliseconds since epoch, or null if not available
     */
    @Override
    public Long getModified() {
        return driveItem.lastModifiedDateTime != null ? Timestamp.valueOf(driveItem.lastModifiedDateTime.toLocalDateTime()).getTime() : null;
    }

    /**
     * Gets the size of the file in bytes.
     *
     * @return The file size as a Double, or null if not available
     */
    @Override
    public Double getSize() {
        return driveItem.size != null ? Double.valueOf(driveItem.size) : null;
    }

    /**
     * Gets the permissions of the current user for this file.
     * Checks both the DriveItem permissions and the permission collection page.
     *
     * @return A string representing the permissions (e.g., "Read", "Write", "Read and Write", "Own")
     */
    @Override
    public String getPermissions() {
        return driveItem.permissions != null ? PermissionUtil.getOwnerPermissions(driveItem.permissions, onedriveAttributes) : PermissionUtil.getOwnerPermissions(permissionCollectionPage, onedriveAttributes);
    }

    /**
     * Gets the tags associated with the file.
     * Note: This is not supported in OneDrive Files Extension.
     *
     * @return An empty list as tags are not supported
     */
    @Override
    public List<String> getTags() {
        return List.of();
    }

    /**
     * Determines if the file is a folder.
     *
     * @return true if the file is a folder, false otherwise
     */
    @Override
    public Boolean getIsFolder() {
        return driveItem.folder != null;
    }

    /**
     * Gets a list of users with whom the file is shared, along with their permissions.
     *
     * @return A list of maps containing user information and permissions
     */
    @Override
    public List<Map<String, Object>> getSharedWith() {
        return driveItem.permissions != null ? PermissionUtil.getPermissions(driveItem.permissions) : PermissionUtil.getPermissions(permissionCollectionPage);
    }

    /**
     * Gets the email or display name of the user who created the file.
     *
     * @return The email or display name of the creator, or null if not available
     */
    @Override
    public String getCreatedBy() {
        if (driveItem.createdBy == null) {
            return null;
        }
        return getCreatedByUser(driveItem);
    }

    /**
     * Gets the email or display name of the user who last modified the file.
     *
     * @return The email or display name of the modifier, or null if not available
     */
    @Override
    public String getModifiedBy() {
        if (driveItem.lastModifiedBy == null) {
            return null;
        }
        return getModifiedByUser(driveItem);
    }

    /**
     * Helper method to extract the email or display name of the user who last modified the file.
     * Prioritizes email over display name if both are available.
     *
     * @param driveItem The DriveItem containing the modifier information
     * @return The email or display name of the modifier, or an empty string if not available
     */
    private String getModifiedByUser(DriveItem driveItem) {
        String modifiedBy = "";
        if (driveItem.lastModifiedBy != null && driveItem.lastModifiedBy.user != null
                && driveItem.lastModifiedBy.user.additionalDataManager().get(Constants.EMAIL) != null) {
            return driveItem.lastModifiedBy.user.additionalDataManager().get(Constants.EMAIL).getAsString();

        } else if (driveItem.lastModifiedBy != null && driveItem.lastModifiedBy.user != null && driveItem.lastModifiedBy.user.displayName != null) {
            return driveItem.lastModifiedBy.user.displayName;
        }
        return modifiedBy;
    }

    /**
     * Helper method to extract the email or display name of the user who created the file.
     * Prioritizes email over display name if both are available.
     *
     * @param driveItem The DriveItem containing the creator information
     * @return The email or display name of the creator, or an empty string if not available
     */
    private String getCreatedByUser(DriveItem driveItem) {
        String createdBy = "";
        if (driveItem.createdBy != null && driveItem.createdBy.user != null
                && driveItem.createdBy.user.additionalDataManager().get(Constants.EMAIL) != null) {
            return driveItem.createdBy.user.additionalDataManager().get(Constants.EMAIL).getAsString();

        } else if (driveItem.createdBy != null && driveItem.createdBy.user != null && driveItem.createdBy.user.displayName != null) {
            return driveItem.createdBy.user.displayName;
        }
        return createdBy;
    }
}