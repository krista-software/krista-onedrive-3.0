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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class Constants {

    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static final String ENABLE_ERROR_HANDLING = "Allow Retry";
    public static final String GTE = "gte";
    public static final String LTE = "lte";
    public static final String GT = "gt";
    public static final String LT = "lt";
    public static final String EQUALS = "equals";
    public static final String STARTS_WITH = "startswith";
    public static final String ENDS_WITH = "endswith";
    public static final String CONTAINS = "contains";
    public static final String NOT_EQUALS = "notequals";
    public static final String USER_AUTHENTICATED_SUCCESSFULLY_PLEASE_PROCEED_WITH_REQUEST = "You're successfully authenticated! You can now proceed with your request.";
    public static final String RESULT = "Result";
    public static final String NO_CHANGE = "No Change";
    public static final String UPDATE = "Update";
    public static final String PATH = "Path";
    public static final String CLIENT_ID = "clientId";
    public static final String CLIENT_SECRET = "clientSecret";
    public static final String TENANT_ID = "tenantId";
    public static final String CODE = "code";
    public static final String STATE = "state";
    public static final String INVALID_STATE_PARAMETERS = "We couldn't verify the request parameters. Please try your request again from the beginning.";
    public static final String WS_CONTACT = "wsContact";
    public static final String IGNORED_EXCEPTION = "IGNORED EXCEPTION";
    public static final String INVALID_FILE_NAME_OR_INVALID_FILE_EXTENSION = "This file type isn't supported. Please use a .txt file instead.";
    public static final String EMPTY_STRING = "";
    public static final String NUMBER = "Number";
    public static final String DATE = "Date";
    public static final String DELETED = "Deleted";
    public static final String CREATED = "Created";
    public static final String SWITCH = "Switch";
    public static final String EVENT = "Event";
    public static final String IS_FILE_EXIST = "isFileExist";
    public static final String FILE_FOUND = "File Found";
    public static final String SHARED = "shared";
    public static final String READ = "Read";
    public static final String WRITE = "Write";
    public static final String OWN = "Own";
    public static final String FORWARD_SLASH_SHARED = "/shared";
    public static final String CHANGE_TYPE = "updated";
    public static final String UPDATED = "Updated";
    public static final String SUBSCRIPTION_ID = "subscriptionId";
    public static final String LIFECYCLE_EVENT = "lifecycleEvent";
    public static final String SUBSCRIPTION_REMOVED = "subscriptionRemoved";
    public static final String REAUTHORIZATION_REQUIRED = "reauthorizationRequired";
    public static final String VALIDATION_TOKEN = "validationToken";
    public static final String VALUE = "value";
    public static final String ONEDRIVE_FILE_ID = "onedriveFileId";
    public static final String FILE_RECEIVED = "fileReceived";
    public static final String YYYY_MM_DD_T_HH_MM_SS_SSSSSSS_Z = "yyyy-MM-dd'T'HH:mm:ss.SSSSSSS'Z'";
    public static final String ME_ONEDRIVE_FOLDERS_ALERT_MESSAGES = "/me/drive/root";
    public static final String REST_ONEDRIVE_LIFECYCLE_NOTIFICATION = "/rest/onedrive/lifecycleNotification";
    public static final String REST_ONEDRIVE_MAIL_NOTIFICATION = "/rest/onedrive/onedriveNotification";
    public static final String FILE_DELETED_SUCCESSFULLY = "Your file has been deleted successfully.";
    public static final String DRIVE_ITEM = "DriveItem";
    public static final String FILE_SIZE_IS_GREATER_THAN_50_MB_PLEASE_DOWNLOAD_FILE_BY_URL = "This file is larger than 50MB. Please use the download link instead of downloading directly.";
    public static final String TO_DOWNLOAD_FILE_PLEASE_COPY_URL = "To download this file, click on the URL link provided.";
    public static final String FILE_ID_UPLOADED_SUCCESSFULLY = "Your file has been uploaded successfully!";
    public static final String FOLDERS_FETCHED_SUCCESSFULLY = "Folders retrieved successfully.";
    public static final String FILE_FETCHED_SUCCESSFULLY = "Files and folders retrieved successfully.";
    public static final String FOLDER_NAME_LIST_IS_EMPTY_OR_NULL = "The folder at the specified path is empty or no items match your search criteria.";
    public static final String FILE_NAME_LIST_IS_EMPTY_OR_NULL = "No files or folders found at the specified path, or no items match your search criteria.";
    public static final String PLEASE_PROVIDE_VALID_TARGET_PATH = "Please enter a valid target path. The path you provided couldn't be found or is incorrect.";
    public static final String PLEASE_PROVIDE_VALID_SOURCE_PATH = "Please enter a valid source path. The path you provided couldn't be found or is incorrect.";
    public static final String PLEASE_PROVIDE_VALID_FILE_SOURCE_PATH = "Please enter a valid file path. The file you're looking for couldn't be found at the specified location.";
    public static final String PLEASE_PROVIDE_VALID_FOLDER_PATH = "Please enter a valid folder path. The folder you're looking for couldn't be found at the specified location.";

    public static final String FILE_NOT_FOUND = "We couldn't find the file or folder you're looking for. Please check the name and path, then try again.";
    public static final String FILE_SPECIFIC_FILE_NOT_FOUND = "We couldn't find a file with that name at the specified path. Please verify the file name and location.";
    public static final String ERROR_DURING_SERVICE = "We're experiencing issues connecting to Microsoft OneDrive. This is usually temporary. Please wait a moment and try again.";
    public static final String FOLDER_IS_EMPTY = "The folder at the specified path doesn't contain any files or subfolders.";
    public static final String ACCESS_DENIED = "accessDenied";
    public static final String FILE_HAS_BEEN_COPIED = "Your file has been copied successfully!";
    public static final String FILE_HAS_BEEN_MOVED = "Your file has been moved successfully!";
    public static final String FILE_ALREADY_EXIST = "A file with this name already exists at the destination. Please choose a different file name or delete the existing file first.";
    public static final String FOLDER_ALREADY_EXIST = "A folder with this name already exists at the destination. Please choose a different folder name or use the existing folder.";
    public static final String COPY_IN_PROGRESS = "Your copy operation is in progress. This may take a few moments for large files.";
    public static final String FILE_IS_CURRENTLY_RESTRICTED_FOR_ASSISTANCE_CONTACT_YOUR_ADMINISTRATOR_OR_SUPPORT = "You don't have permission to download this file. This might be due to:\n• File sharing restrictions\n• Your access level\n• Organization policies\n\nPlease contact your administrator or support team for assistance.";
    public static final String FILE_IS_TO_SHARE_CURRENTLY_RESTRICTED_FOR_ASSISTANCE_CONTACT_YOUR_ADMINISTRATOR_OR_SUPPORT = "You don't have permission to share this file or folder. This might be due to:\n• Sharing restrictions set by the owner\n• Your access level\n• Organization policies\n\nPlease contact your administrator or support team for assistance.";
    public static final String DRIVE_ROOT = "drive/root:/";
    public static final String ITEM_NOT_FOUND = "itemNotFound";
    public static final String UNAUTHORISED_USER = "You're not authorized to access this resource. Please sign in with an authorized account.";
    public static final String USER_AUTHENTICATED_SUCCESSFULLY_SAVE_THE_CHANGES = "You're successfully authenticated! Please save your changes to complete the setup.";
    public static final String ERROR_OCCURRED_DURING_AUTHORIZATION = "We encountered an issue during authorization. Please try signing in again.";
    public static final String ERROR_DESCRIPTION = "error_description";
    public static final String AUTH_URL_QUERY_PARAMS = "&access_type=offline&approval_prompt=force&prompt=login";
    public static final String TEXT = "Text";
    public static final String HASH = "#";
    public static final String POST_METHOD = "POST";
    public static final String GET_REQUEST = "GET";
    public static final String PATCH_METHOD = "PATCH";
    public static final String ERROR_OCCURRED_DURING_UPLOADING_ATTACHMENT = "We couldn't upload your file. Please check your connection and try again.";
    public static final String ERROR_OCCURRED_DURING_MOVING_FILE = "We couldn't move your file. Please verify the source and destination paths, then try again.";
    public static final String ERROR_OCCURRED_DURING_COPYING_FILE = "We couldn't copy your file. Please verify the source and destination paths, then try again.";
    public static final String REQUIRED_SCOPE = "openid User.Read offline_access Files.ReadWrite.All";
    public static final String SCOPE_SEPARATOR = " ";
    public static final String APPLICATION_JSON = "application/json";
    public static final String AUTHORITY = "https://login.microsoftonline.com/";
    public static final String USER_ID = "userId";
    public static final String AUTH_CONTEXT_ID = "authContextId";
    public static final String YOU_ARE_NOT_AUTHORIZED_PLEASE_AUTHORIZE_YOURSELF = "You're not currently authorized. Please sign in to your Microsoft account to continue.";
    public static final String REFRESH_TOKEN_IS_NO_LONGER_VALID_PLEASE_REAUTHORIZE_YOURSELF = "Your session has expired. Please sign in again to continue using OneDrive.";
    public static final String FAILED_TO_GET_ACCOUNT = "We couldn't retrieve your account information. Please try signing in again.";
    public static final String REST_OUTLOOK_CALLBACK = "/rest/onedrive/callback";
    public static final String READ_AND_WRITE = "Read and Write";
    public static final String EMAIL = "email";
    public static final String PERMISSION = "Permission";
    public static final String USER_EMAIL = "User Email";
    public static final String PERMISSIONS = "permissions";
    public static final String FOLDERS = "Folders";
    public static final String SIZE = "Size";
    public static final String OLDEST_FILE = "Oldest File";
    public static final String OLDEST_FILE_CREATE_DATE = "Oldest File Create Date";
    public static final String NEWEST_FILE = "Newest File";
    public static final String NEWEST_FILE_DATE = "Newest File Date";
    public static final String LAST_MODIFIED_FILE_ID = "Last Modified File Id";
    public static final String LAST_MODIFIED_FILE_NAME = "Last Modified File Name";
    public static final String LAST_MODIFIED_FILE_CREATE_DATE = "Last Modified File Create Date";
    public static final String ODATA_CONTEXT = "@odata.context";
    public static final String MESSAGE = "Message";
    public static final String FILE = "File";
    public static final String FILES = "Files";
    public static final String SUCCESS = "Success";
    public static final String GOT_ERROR_FOR_AUTHENTICATION_SO_SENDING_FOR_RE_AUTHENTICATION = "Your authentication has expired. Please sign in again to continue.";
    public static final String GOT_ERROR_FOR_AUTHENTICATION_SO_SENDING_FOR_AUTHENTICATION = "We need you to sign in to access OneDrive. Please complete the authentication process.";
    public static final String PROVIDE_VALID_PATH_FILE_NOT_FOUND = "We couldn't find a file at the specified path. Please check the path and try again.";
    public static final String INVALID_FOLDER_NAME = "The folder name contains invalid characters. Please use a name without these special characters: * : < > ? / \\ |";

    public static final String TELEMETRY_REASON_DOWNLOAD_FAILED_DRIVE_ID_PATH = "download_failed_drive_id_path";
    public static final String TELEMETRY_REASON_DOWNLOAD_FAILED_PERSONAL_DRIVE = "download_failed_personal_drive";
    public static final String TELEMETRY_REASON_INVALID_PATH_FORMAT = "invalid_path_format";
    public static final String TELEMETRY_REASON_NULL_PATH = "null_path";

    public static final String TELEMETRY_ERROR_GRAPH_SERVICE_EXCEPTION = "graph_service_exception";
    public static final String TELEMETRY_ERROR_UNEXPECTED_EXCEPTION = "unexpected_exception";
    public static final String IS_FROM_CATALOG_REQUEST = "isFromCatalogRequest";

    private Constants() {
    }
}
