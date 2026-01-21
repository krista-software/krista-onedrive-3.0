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

package app.krista.extensions.essentials.cloudfiles.onedrive.catalog;

import app.krista.extensions.essentials.cloudfiles.onedrive.catalog.entities.File;

public class CatalogTypes {

    private CatalogTypes() {
    }

    public static File fromFile(app.krista.extensions.essentials.cloudfiles.onedrive.service.File file) {
        if (file == null) {
            return new File();
        }
        File fileDetails = new File();
        fileDetails.name = file.getName();
        fileDetails.baseName = file.getBaseName();
        fileDetails.extension = file.getFileExtension();
        fileDetails.parentFolder = file.getParentFolder();
        fileDetails.size = file.getSize();
        fileDetails.modified = file.getModified();
        fileDetails.owner = file.getOwner();
        fileDetails.tags = file.getTags();
        fileDetails.isFolder = file.getIsFolder();
        fileDetails.created = file.getCreated();
        fileDetails.createdBy = file.getCreatedBy();
        fileDetails.modifiedBy = file.getModifiedBy();
        fileDetails.permissions = file.getPermissions();
        fileDetails.sharedWith = file.getSharedWith();
        fileDetails.absolutePath = file.getPath();
        return fileDetails;
    }

}
