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

package app.krista.extensions.essentials.cloudfiles.onedrive.impl.util;

public class TelemetryConstants {
    // Telemetry Constants
    public static final String TELEMETRY_MOVE_FILE = "Onedrive.moveFile";
    public static final String TELEMETRY_COPY_FILE = "Onedrive.copyFile";
    // Telemetry Error Messages
    public static final String TELEMETRY_ERROR_SOURCE_ROOT_FILE = "Source is a root file";
    public static final String TELEMETRY_ERROR_TARGET_NOT_FOUND_AFTER_REENTRY = "Target path not found after reentry";
    public static final String TELEMETRY_ERROR_SOURCE_NOT_FOUND_AFTER_REENTRY = "Source file not found after reentry";
    public static final String TELEMETRY_ERROR_INVALID_SOURCE_PATH = "Invalid source path (root file)";
    public static final String TELEMETRY_ERROR_TARGET_PATH_NOT_FOUND = "Target path not found";
    public static final String TELEMETRY_ERROR_SOURCE_FILE_NOT_FOUND = "Source file not found";
    public static final String TELEMETRY_ERROR_FILE_ALREADY_EXISTS = "File already exists at target";
    // Telemetry Retry Reasons
    public static final String TELEMETRY_REASON_ROOT_FILE = "root_file";
    public static final String TELEMETRY_REASON_TARGET_NOT_FOUND = "target_not_found";
    public static final String TELEMETRY_REASON_SOURCE_NOT_FOUND = "source_not_found";
    public static final String TELEMETRY_REASON_INVALID_FOLDER_NAME = "invalid_folder_name";
    // Telemetry Tag Keys
    public static final String TELEMETRY_TAG_SOURCE = "source";
    public static final String TELEMETRY_TAG_TARGET = "target";
    public static final String TELEMETRY_TAG_REASON = "reason";
    public static final String TELEMETRY_TAG_REPLACED = "replaced";
    private static final String TELEMETRY_TAG_SHARE_COUNT = "shareCount";
    public static final String TELEMETRY_TAG_ERROR_TYPE = "error_type";
    // Telemetry Error Type Values
    public static final String TELEMETRY_ERROR_TYPE_GRAPH_SERVICE = "GraphServiceException";
    public static final String TELEMETRY_ERROR_TYPE_IO = "IOException";
    public static final String TELEMETRY_ERROR_TYPE_ILLEGAL_ARGUMENT = "IllegalArgumentException";
    public static final String TELEMETRY_ERROR_TYPE_UNEXPECTED = "UnexpectedException";
    // Telemetry Constants - Operation Names
    public static final String TELEMETRY_DOWNLOAD_FILE = "Onedrive.downloadFile";
    public static final String TELEMETRY_UPLOAD_FILES = "Onedrive.uploadFiles";
    public static final String TELEMETRY_DELETE_FILE = "Onedrive.deleteFile";
    public static final String TELEMETRY_RENAME_FILE = "Onedrive.renameFile";
    // Telemetry Error Messages
    public static final String TELEMETRY_ERROR_FILE_NOT_FOUND_AFTER_REENTRY = "File not found after re-entry";
    // Telemetry Retry Reasons
    public static final String TELEMETRY_REASON_INVALID_FILE_NAME = "invalid_file_name";
    public static final String TELEMETRY_REASON_FILE_NOT_EXIST = "file_not_exist";
    // Telemetry Tag Keys
    public static final String TELEMETRY_TAG_TARGET_PATH = "targetPath";
    public static final String TELEMETRY_TAG_IS_REENTERED = "is_reentered";
    // Telemetry Constants - Operation Names
    public static final String TELEMETRY_GET_LIST_OF_FILES = "OneDrive.getListOfFiles";
    public static final String TELEMETRY_GET_FILE_INFO = "OneDrive.getFileInfo";
    public static final String TELEMETRY_CREATE_FOLDER = "OneDrive.createFolder";
    // Telemetry Error Messages
    public static final String TELEMETRY_ERROR_RELATIVE_PATH_NULL = "Relative path is null";
    public static final String TELEMETRY_ERROR_FOLDER_NOT_EXIST = "Folder does not exist";
    public static final String TELEMETRY_ERROR_FOLDER_NAME_EMPTY = "Folder name is empty";
    public static final String TELEMETRY_ERROR_CREATE_FOLDER_FAILED_PERSONAL = "Create folder failed (personal drive)";
    // Telemetry Retry Reasons
    public static final String TELEMETRY_REASON_FOLDER_ALREADY_EXISTS = "folder_already_exists";
    public static final String TELEMETRY_REASON_INVALID_TARGET_PATH = "invalid_target_path";
    // Telemetry Tag Keys
    public static final String TELEMETRY_TAG_RELATIVE_PATH = "relativePath";
    public static final String TELEMETRY_TAG_SOURCE_PATH = "sourcePath";
    public static final String TELEMETRY_TAG_FOLDER_NAME = "folderName";
    public static final String TELEMETRY_TAG_PATH = "path";
    public static final String TELEMETRY_TAG_PATH_TYPE = "pathType";
    public static final String TELEMETRY_TAG_USE_EMAIL = "useEmail";

    // Telemetry Path Type Values
    public static final String TELEMETRY_PATH_TYPE_DRIVE_ID = "driveId";
    public static final String TELEMETRY_PATH_TYPE_PERSONAL = "personal";
    public static final String TELEMETRY_PATH_TYPE_SHARED = "shared";
    // Telemetry Constants - Operation Names
    public static final String TELEMETRY_FETCH_FILES_WITH_PERMISSIONS = "OneDrive.fetchFilesWithPermissions";
    // Telemetry Error Messages
    public static final String TELEMETRY_ERROR_UNSUPPORTED_PERMISSION = "Unsupported permission type";
    // Telemetry Tag Keys
    public static final String TELEMETRY_TAG_PERMISSION = "permission";
    public static final String TELEMETRY_TAG_ACCOUNT_ID = "accountID";
    public static final String TELEMETRY_TAG_FILE_COUNT = "fileCount";

}
