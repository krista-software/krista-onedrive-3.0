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

import app.krista.extensions.essentials.cloudfiles.onedrive.catalog.CatalogTypes;
import app.krista.extensions.essentials.cloudfiles.onedrive.catalog.entities.File;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.FileImpl;
import app.krista.model.base.FreeForm;
import com.microsoft.graph.models.DriveItem;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ResponseUtil {

    private ResponseUtil() {
    }

    public static void fileEntityResponse(File fileEntity, boolean success, String message, Map<String, Object> payload) {
        payload.put(Constants.FILE, fileEntity);
        payload.put(Constants.MESSAGE, message);
        payload.put(Constants.SUCCESS, success);
    }

    public static Map<String, Object> createFolderResponse(File fileEntity, boolean success, String message) {
        Map<String, Object> payload = new HashMap<>();
        payload.put(Constants.FILE, fileEntity);
        payload.put(Constants.MESSAGE, message);
        payload.put(Constants.SUCCESS, success);
        return payload;
    }

    public static void listOfFileEntityResponse(List<File> fileEntity, boolean success, String message, FreeForm payload) {
        payload.put(Constants.FILES, "[ Entity(File) ]", fileEntity);
        payload.put(Constants.MESSAGE, Constants.TEXT, message);
        payload.put(Constants.SUCCESS, Constants.SWITCH, success);
    }

    public static void changesSinceResponse(boolean success, String message, List<Map<String, Object>> result, Map<String, Object> payload) {
        payload.put(Constants.RESULT, result);
        payload.put(Constants.MESSAGE, message);
        payload.put(Constants.SUCCESS, success);
    }

    public static void errorResponse(boolean success, String message, Map<String, Object> payload) {
        payload.put(Constants.MESSAGE, message);
        payload.put(Constants.SUCCESS, success);
    }

    public static void emptyFolderResponse(boolean success, String message, FreeForm payload) {
        getInfoResponse(success, message, payload);
    }

    public static void addDriveItem(boolean isFileExist, DriveItem driveItem, Map<String, Object> payload) {
        payload.put(Constants.DRIVE_ITEM, driveItem);
        payload.put(Constants.IS_FILE_EXIST, isFileExist);
    }

    public static void addDriveItemMessage(boolean isFileExist, boolean success, String message, Map<String, Object> payload) {
        payload.put(Constants.MESSAGE, message);
        payload.put(Constants.IS_FILE_EXIST, isFileExist);
        payload.put(Constants.SUCCESS, success);
    }

    public static void downloadAsKristaFileResponse(app.krista.model.base.File kristaFile, boolean success, String message, Map<String, Object> payload) {
        payload.put("Krista File", kristaFile);
        payload.put(Constants.MESSAGE, message);
        payload.put(Constants.SUCCESS, success);
    }

    public static void downloadAsLinkResponse(String downloadLink, boolean success, String message, Map<String, Object> payload) {
        payload.put("Link", downloadLink);
        payload.put(Constants.MESSAGE, message);
        payload.put(Constants.SUCCESS, success);
    }

    public static void getFileEventData(FreeForm eventData, Map<String, Object> payload) {
        payload.put(Constants.FILES, eventData.get(Constants.FILES));
        payload.put(Constants.FOLDERS, eventData.get(Constants.FOLDERS));
        payload.put(Constants.SIZE, eventData.get(Constants.SIZE));
        payload.put(Constants.OLDEST_FILE, eventData.get(Constants.OLDEST_FILE));
        payload.put(Constants.OLDEST_FILE_CREATE_DATE, eventData.get(Constants.OLDEST_FILE_CREATE_DATE));
        payload.put(Constants.NEWEST_FILE, eventData.get(Constants.NEWEST_FILE));
        payload.put(Constants.NEWEST_FILE_DATE, eventData.get(Constants.NEWEST_FILE_DATE));
        payload.put(Constants.LAST_MODIFIED_FILE_ID, eventData.get(Constants.LAST_MODIFIED_FILE_ID));
        payload.put(Constants.LAST_MODIFIED_FILE_NAME, eventData.get(Constants.LAST_MODIFIED_FILE_NAME));
        payload.put(Constants.LAST_MODIFIED_FILE_CREATE_DATE, eventData.get(Constants.LAST_MODIFIED_FILE_CREATE_DATE));
        payload.put(Constants.MESSAGE, eventData.get(Constants.MESSAGE));
        payload.put(Constants.SUCCESS, eventData.get(Constants.SUCCESS));
    }

    public static void getInfoResponse(int files, int folders, long size, String oldestFile, long oldestFileCreateDate, String newestFile, long newestFileCreteDate, String lastModifiedFileId, String lastModifiedFileName, long lastModifiedFileCreateDate, boolean success, String message, FreeForm payload) {
        payload.put(Constants.FILES, Constants.NUMBER, files);
        payload.put(Constants.FOLDERS, Constants.NUMBER, folders);
        payload.put(Constants.SIZE, Constants.NUMBER, size);
        payload.put(Constants.OLDEST_FILE, Constants.TEXT, oldestFile);
        payload.put(Constants.OLDEST_FILE_CREATE_DATE, Constants.DATE, oldestFileCreateDate);
        payload.put(Constants.NEWEST_FILE, Constants.TEXT, newestFile);
        payload.put(Constants.NEWEST_FILE_DATE, Constants.DATE, newestFileCreteDate);
        payload.put(Constants.LAST_MODIFIED_FILE_ID, Constants.TEXT, lastModifiedFileId);
        payload.put(Constants.LAST_MODIFIED_FILE_NAME, Constants.TEXT, lastModifiedFileName);
        payload.put(Constants.LAST_MODIFIED_FILE_CREATE_DATE, Constants.DATE, lastModifiedFileCreateDate);
        payload.put(Constants.MESSAGE, Constants.TEXT, message);
        payload.put(Constants.SUCCESS, Constants.SWITCH, success);
    }

    public static void getInfoResponse(boolean success, String message, FreeForm payload) {
        payload.put(Constants.MESSAGE, Constants.TEXT, message);
        payload.put(Constants.SUCCESS, Constants.SWITCH, success);
    }

    public static Map<String, Object> createResultContent(String path, long date, String update) {
        Map<String, Object> resultContent = new LinkedHashMap<>();
        resultContent.put(Constants.PATH, path);
        resultContent.put(Constants.DATE, date);
        resultContent.put(Constants.UPDATE, update);
        return resultContent;
    }

    public static void addUpdatedFile(DriveItem driveItem, Map<String, Object> payload) {
        app.krista.extensions.essentials.cloudfiles.onedrive.catalog.entities.File fileEntity = CatalogTypes.fromFile(new FileImpl(driveItem, null, null));
        payload.put(Constants.FILE, fileEntity);
        payload.put(Constants.EVENT, Constants.UPDATED);
    }

    public static void addDeletedFile(DriveItem driveItem, Map<String, Object> payload) {
        app.krista.extensions.essentials.cloudfiles.onedrive.catalog.entities.File fileEntity = CatalogTypes.fromFile(new FileImpl(driveItem, null, null));
        payload.put(Constants.FILE, fileEntity);
        payload.put(Constants.EVENT, Constants.DELETED);
    }

    public static void addCreatedFile(DriveItem driveItem, Map<String, Object> payload) {
        app.krista.extensions.essentials.cloudfiles.onedrive.catalog.entities.File fileEntity = CatalogTypes.fromFile(new FileImpl(driveItem, null, null));
        payload.put(Constants.FILE, fileEntity);
        payload.put(Constants.EVENT, Constants.CREATED);
    }

    public static void addEventToFreeForm(Map<String, Object> payload, FreeForm form) {
        form.put(Constants.FILE, "Entity(File)", payload.get(Constants.FILE));
        form.put(Constants.EVENT, Constants.TEXT, payload.get(Constants.EVENT));
    }

    public static void addFileSize(long size, FreeForm payload) {
        payload.put(Constants.SIZE, Constants.NUMBER, size);
    }

    public static Map<String, Object> errorResponse(boolean success, String message) {
        Map<String, Object> payload = new HashMap<>();
        payload.put(Constants.MESSAGE, message);
        payload.put(Constants.SUCCESS, success);
        return payload;
    }
}
