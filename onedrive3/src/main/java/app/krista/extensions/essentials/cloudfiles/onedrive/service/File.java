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

package app.krista.extensions.essentials.cloudfiles.onedrive.service;

import java.util.List;
import java.util.Map;

public interface File {

    /**
     * This function returns Name of File  or returns null for exception
     *
     * @return {@link String}
     */
    String getName();

    /**
     * This function returns Name of File without File or returns null for empty value or exception
     *
     * @return {@link String}
     */
    String getBaseName();

    /**
     * This function returns File Extension if Any or returns null for empty value or exception
     *
     * @return {@link String}
     */
    String getFileExtension();

    /**
     * This function returns Folder in which File resides or returns null for empty value or exception
     *
     * @return {@link String}
     */
    String getParentFolder();

    /**
     * This function returns Absolute Path of File or returns null for empty value or exception
     *
     * @return {@link String}
     */
    String getPath();

    /**
     * This function returns Owner of File or returns null for empty value or exception
     *
     * @return {@link String}
     */
    String getOwner();

    /**
     * This function returns Created Date of File or returns null for empty value or exception
     *
     * @return {@link Long}
     */
    Long getCreated();

    /**
     * This function returns Modified Date of File or returns null for empty value or exception
     *
     * @return {@link Long}
     */
    Long getModified();

    /**
     * This function returns Size of File in Bytes or returns null for empty value or exception
     *
     * @return {@link Double}
     */
    Double getSize();

    /**
     * This function returns Permissions Given to User for File or returns null for empty value or exception
     *
     * @return {@link String}
     */
    String getPermissions();

    /**
     * This function is Not Supported in OneDrive Files Extension
     *
     * @return {@link List<String>}
     */
    List<String> getTags();

    /**
     * This function returns True if File Entity is Folder
     *
     * @return {@link Boolean}
     */
    Boolean getIsFolder();

    /**
     * This function returns List of User with their Permissions on File or returns null for empty value or exception
     *
     * @return {@link List<Map<String, Object>> }
     */
    List<Map<String, Object>> getSharedWith();

    /**
     * This function returns Name of User who created File or returns null for empty value or exception
     *
     * @return {@link String}
     */
    String getCreatedBy();

    /**
     * This function returns Name of User who Last Modified File or returns null for empty value or exception
     *
     * @return {@link String}
     */
    String getModifiedBy();
}
