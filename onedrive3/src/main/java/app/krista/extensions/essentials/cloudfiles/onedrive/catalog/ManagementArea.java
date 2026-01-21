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

import app.krista.extension.executor.ExtensionResponse;
import app.krista.extension.executor.ExtensionResponseBuilder;
import app.krista.extension.impl.anno.Attribute;
import app.krista.extension.impl.anno.CatalogRequest;
import app.krista.extension.impl.anno.Domain;
import app.krista.extension.impl.anno.Field;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.ManagementDaoImpl;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.util.Constants;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.util.FileUtil;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.util.ResponseUtil;
import app.krista.extensions.util.EventHandler;
import app.krista.ksdk.context.AuthorizationContext;
import app.krista.ksdk.context.RequestContext;
import app.krista.model.base.FreeForm;
import com.kristasoft.common.holders.ThreadLocalProxy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Domain(id = "catEntryDomain_30c63e77-37f2-4a1d-afd8-f6bfda07ad57",
        name = "Cloud Files",
        ecosystemId = "catEntryEcosystem_84b53163-327b-4b1b-8c96-9334d292f9f5",
        ecosystemName = "Essentials",
        ecosystemVersion = "fb35f83b-d270-4925-a896-7b677f04f126")
public class ManagementArea {

    private static final Logger LOGGER = LoggerFactory.getLogger(ManagementArea.class);

    private final ManagementDaoImpl managementDao;
    private final EventHandler eventHandler;
    private final RequestContext requestContext;
    private final AuthorizationContext authorizationContext;
    private final ExecutorService executorService = Executors.newFixedThreadPool(5);


    @Inject
    public ManagementArea(ManagementDaoImpl managementDao, EventHandler eventHandler, RequestContext requestContext,
                          AuthorizationContext authorizationContext) {
        this.managementDao = managementDao;
        this.eventHandler = eventHandler;
        this.requestContext = requestContext;
        this.authorizationContext = authorizationContext;
    }

    @CatalogRequest(
            id = "localDomainRequest_b277227d-746c-469e-8bed-75233b6b6133",
            name = "Copy",
            description = "Copy the provide file represented by the entity to the target location.",
            area = "Management",
            type = CatalogRequest.Type.CHANGE_SYSTEM)
    @Field.Desc(name = "File", type = "Entity(File)", required = false)
    @Field.Text(name = "Message", required = false, attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'Provides details about the operation result'")})
    @Field.Boolean(name = "Success", required = false, attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'Indicates whether the copy operation was successful'")})
    public ExtensionResponse copy(
            @Field.Text(name = "Source", attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'Full path to the source file or folder to be copied'")}) String source,
            @Field.Text(name = "Target", attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'Full path where the file or folder should be copied'")}) String target,
            @Field.Boolean(name = "Replace", attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'If true, replaces any existing file at the target location'")}) Boolean replace,
            @Field.Boolean(name = "Create Folder", attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'If true, creates any new folders in the target path'")}) Boolean createFolder,
            @Field.Boolean(name = Constants.ENABLE_ERROR_HANDLING, required = false,
                    attributes = {
                            @Attribute(name = "toolTip", value = "'Enable to allow re-entry of values if validation fails'")
                    }
            ) Boolean allowRetry) {
        LOGGER.info("Copy file operation initiated - Source: {}, Target: {}, Replace: {}, Create Folder: {}, Allow Retry: {}",
                source, target, replace, createFolder, allowRetry);

        try {
            LOGGER.debug("Delegating copy file operation to managementDao");
            ExtensionResponse response = managementDao.copyFile(source, target, replace, createFolder, allowRetry);
            LOGGER.info("Copy file operation completed - Success: {}",
                    response.getResponseValue() != null && response.getResponseValue() != null &&
                            response.getResponseValue().containsKey("Success") ?
                            ((Map<?, ?>) response.getResponseValue()).get("Success") : "unknown");
            return response;
        } catch (Exception e) {
            LOGGER.error("Copy file operation failed with exception", e);
            return new ExtensionResponseBuilder()
                    .success(Map.of("Success", false, "Message", "Failed to copy file: " + e.getMessage()), null, null)
                    .build();
        }
    }

    @CatalogRequest(
            id = "localDomainRequest_6518b866-ac60-46f7-b77a-fb7f31af257d",
            name = "Delete",
            description = "Remove a file from a folder using the exact file name.",
            area = "Management",
            type = CatalogRequest.Type.CHANGE_SYSTEM)
    @Field.Boolean(name = "Success", required = false, attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'Indicates whether the deletion was successful'")})
    @Field.Text(name = "Message", required = false, attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'Provides details about the operation result'")})
    public ExtensionResponse delete(
            @Field.Text(name = "Target", attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'Full path to the file to be deleted (e.g., /Documents/file.txt or /Folders/MyFolder). For shared drives, use format driveId:/path/to/file'")}) String target,
            @Field.Boolean(name = Constants.ENABLE_ERROR_HANDLING, required = false,
                    attributes = {
                            @Attribute(name = "toolTip", value = "'Enable to allow re-entry of values if validation fails'")
                    }
            ) Boolean allowRetry) {
        LOGGER.info("Delete file operation initiated - Target: {}, Allow Retry: {}", target, allowRetry);
        try {
            ExtensionResponse response = managementDao.deleteFile(target, allowRetry);
            LOGGER.info("Delete file operation completed - Success: {}, Message: {}",
                    response.getResponseValue() != null && response.getResponseValue().containsKey("Success") ?
                            ((Map<?, ?>) response.getResponseValue()).get("Success") : "unknown",
                    response.getResponseValue() != null && response.getResponseValue().containsKey("Message") ?
                            ((Map<?, ?>) response.getResponseValue()).get("Message") : "");
            return response;
        } catch (Exception cause) {
            LOGGER.error("Delete file operation failed with exception", cause);
            return new ExtensionResponseBuilder()
                    .success(Map.of("Success", false, "Message", "Failed to delete file: " + Constants.PLEASE_PROVIDE_VALID_TARGET_PATH), null, null)
                    .build();
        }
    }

    @CatalogRequest(
            id = "localDomainRequest_d0aea3ac-2059-4ce4-85e8-626b42e64c0c",
            name = "Download as File",
            description = "Retrieves the File from the cloud storage and provides a reference as Krista File based on provided source location.",
            area = "Management",
            type = CatalogRequest.Type.CHANGE_SYSTEM)
    @Field.File(name = "Krista File", required = false, attributes = {@Attribute(name = "visualWidth", value = "S")})
    @Field.Boolean(name = "Success", required = false, attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'Indicates whether the download operation was successful'")})
    @Field(name = "Message", type = "RichText", required = false, attributes = {@Attribute(name = "visualWidth", value = "L"), @Attribute(name = "toolTip", value = "'Provides details about the operation result or any errors encountered'")})
    public ExtensionResponse downloadAsFile(
            @Field.Text(name = "Source Path", attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'Full path to the file in the cloud storage that should be downloaded (e.g., /Documents/file.pdf or /Sites/TeamSite/Shared Documents/report.xlsx). For shared drives, use format driveId:/path/to/file'")}) String source) {
        LOGGER.info("Download as file operation initiated - Source: {}", source);
        try {
            ExtensionResponse response = managementDao.downloadFileAsFile(source);
            LOGGER.info("Download as file operation completed - Success: {}, Message: {}",
                    response.getResponseValue() != null && response.getResponseValue().containsKey("Success") ?
                            ((Map<?, ?>) response.getResponseValue()).get("Success") : "unknown",
                    response.getResponseValue() != null && response.getResponseValue().containsKey("Message") ?
                            ((Map<?, ?>) response.getResponseValue()).get("Message") : "");
            return response;
        } catch (Exception e) {
            LOGGER.error("Download as file operation failed with exception", e);
            return new ExtensionResponseBuilder()
                    .success(Map.of("Success", false, "Message", "Failed to download file: " + e.getMessage()), null, null)
                    .build();
        }
    }

    @CatalogRequest(
            id = "localDomainRequest_37d526ce-6741-4523-b61f-d77451005159",
            name = "Download as Link",
            description = "Retrieves the File from the cloud storage as a hyperlink based on provided source location.",
            area = "Management",
            type = CatalogRequest.Type.CHANGE_SYSTEM)
    @Field.Boolean(name = "Success", required = false, attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'Indicates whether the link generation was successful'")})
    @Field.Text(name = "Message", required = false, attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'Provides details about the operation result or any errors encountered'")})
    @Field(name = "Link", type = "RichText", required = false, attributes = {@Attribute(name = "visualWidth", value = "L"), @Attribute(name = "toolTip", value = "'The generated hyperlink to access the file directly in OneDrive'")})
    public ExtensionResponse downloadAsLink(
            @Field.Text(name = "Source", attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'Full path to the file in the cloud storage (e.g., /Documents/file.pdf or /Sites/TeamSite/Shared Documents/report.xlsx). For shared drives, use format driveId:/path/to/file'")}) String source) {
        return managementDao.downloadFileAsLink(source);
    }

    @CatalogRequest(
            id = "localDomainRequest_8d92b36c-792d-4cfe-b63d-c1dededbf075",
            name = "Get Info",
            description = "Accepts the source of the file and returns a Task ID, which will be further used to retrieve result information.",
            area = "Management",
            type = CatalogRequest.Type.QUERY_SYSTEM)
    @Field.Text(name = "Task ID", required = false, attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'Unique identifier for tracking this asynchronous operation. Use this ID with the Get Info Result operation to retrieve the complete folder statistics.'")})
    public String getInfo(
            @Field.Text(name = "Source Path", attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'Full path to the folder in the cloud storage to analyze (e.g., /Documents or /Sites/TeamSite/Shared Documents). For shared drives, use format driveId:/path/to/folder'")}) String sourcePath) {
        LOGGER.info("Get Info operation initiated - Source Path: {}", sourcePath);

        final String taskId = UUID.randomUUID().toString();
        boolean useEmail = !requestContext.invokeAsUser();
        String accountID = authorizationContext.getAuthorizedAccount().getAccountId();
        Map<Class<?>, Object> threadLocals = ThreadLocalProxy.getAll();

        executorService.submit(() -> {
            try {
                ThreadLocalProxy.setAll(threadLocals);
                FreeForm freeForm = managementDao.getInfo(sourcePath, useEmail, accountID);
                Thread.sleep(2000); // Adding 2 seconds delay to initialize wait for event in case of quick response
                eventHandler.handleEvent(taskId, freeForm);
            } catch (InterruptedException cause) {
                Thread.currentThread().interrupt();
            }
        });
        return taskId;
    }

    @CatalogRequest(
            id = "localDomainRequest_be76f28f-ffca-46ee-9b27-6a8045cb7177",
            name = "Get Info Result",
            description = "Accepts Task ID and Retrieves key information around a single folder in the cloud storage.",
            area = "Management",
            type = CatalogRequest.Type.WAIT_FOR_EVENT)
    @Field(name = "Files", type = "Number", required = false, attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'Total number of files found in the folder and its subfolders'")})
    @Field(name = "Folders", type = "Number", required = false, attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'Total number of subfolders found in the folder'")})
    @Field(name = "Size", type = "Number", required = false, attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'Total size of all files in the folder and its subfolders, in bytes'")})
    @Field.Text(name = "Oldest File", required = false, attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'Name of the oldest file in the folder based on creation date'")})
    @Field.Text(name = "Newest File", required = false, attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'Name of the newest file in the folder based on creation date'")})
    @Field.Text(name = "Last Modified File Id", required = false, attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'Unique identifier of the most recently modified file in the folder'")})
    @Field.Text(name = "Last Modified File Name", required = false, attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'Name of the most recently modified file in the folder'")})
    @Field.Date(name = "Oldest File Create Date", showHowManyDaysInViewer = 60, attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'Creation timestamp of the oldest file in the folder'")})
    @Field.Date(name = "Newest File Date", showHowManyDaysInViewer = 60, attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'Creation timestamp of the newest file in the folder'")})
    @Field.Date(name = "Last Modified File Create Date", showHowManyDaysInViewer = 60, attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'Last modification timestamp of the most recently modified file in the folder'")})
    @Field.Text(name = "Message", required = false, attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'Provides details about the operation result or any errors encountered'")})
    @Field.Boolean(name = "Success", required = false, attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'Indicates whether the folder information retrieval was successful'")})
    public Map<String, Object> getInfoResult(
            @Field(name = "eventName", type = "Text") String eventName,
            @Field(name = "eventData", type = "FreeForm") FreeForm eventData,
            @Field.Text(name = "Task ID", attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'The task identifier returned by operations like List Files or List Folders, used to retrieve the results of the asynchronous operation'")}) String taskID) {
        Map<String, Object> payload = new HashMap<>();
        if (eventName.equals(taskID)) {
            ResponseUtil.getFileEventData(eventData, payload);
            return payload;
        }
        return Map.of(Constants.MESSAGE, eventData.get(Constants.MESSAGE), Constants.SUCCESS, eventData.get(Constants.SUCCESS));
    }

    @CatalogRequest(
            id = "localDomainRequest_39c4d468-6f5f-4176-a241-a01dcadad9a0",
            name = "Get Result",
            description = "Accept task ID as input and return List of Files.",
            area = "Management",
            type = CatalogRequest.Type.WAIT_FOR_EVENT)
    @Field.Desc(name = "Files", type = "[ Entity(File) ]", required = false)
    @Field.Boolean(name = "Success", required = false, attributes = {
            @Attribute(name = "visualWidth", value = "S"),
            @Attribute(name = "toolTip", value = "'Indicates whether the file retrieval operation was successful'")})
    @Field.Text(name = "Message", required = false, attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'Provides details about the operation result or any errors encountered'")})
    public Map<String, Object> getResult(
            @Field(name = "eventName", type = "Text") String eventName,
            @Field(name = "eventData", type = "FreeForm") FreeForm eventData,
            @Field.Text(name = "Task ID", attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'The task identifier returned by operations like List Files or List Folders, used to retrieve the results of the asynchronous operation'")}) String taskID) {
        if (eventName.equals(taskID)) {
            Map<String, Object> map = new HashMap<>();
            map.put(Constants.FILES, eventData.get(Constants.FILES));
            map.put(Constants.MESSAGE, eventData.get(Constants.MESSAGE));
            map.put(Constants.SUCCESS, eventData.get(Constants.SUCCESS));
            return map;
        }
        throw new IllegalStateException("Invalid Input");
    }

    @CatalogRequest(
            id = "localDomainRequest_f5f0a870-d4ab-4194-b673-1142f21c005a",
            name = "List Files",
            description = "Produces a non-recursive list of files that is present within the folder denoted by the source path.",
            area = "Management",
            type = CatalogRequest.Type.QUERY_SYSTEM)
    @Field.Text(name = "Task ID", required = false, attributes = {
            @Attribute(name = "visualWidth", value = "S"),
            @Attribute(name = "toolTip", value = "'Unique identifier for tracking this asynchronous operation. Use this ID with the Get Result operation to retrieve the file list.'")})
    public String listFiles(
            @Field.Text(name = "Source Path", attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'Full path to the folder in the cloud storage to list files from (e.g., /Documents or /Sites/TeamSite/Shared Documents). For shared drives, use format driveId:/path/to/folder'")}) String sourcePath,
            @Field.Text(name = "Search", required = false, attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'Optional search pattern to filter files by name (supports wildcards and regular expressions)'")}) String search) {
        LOGGER.info("List Files operation initiated - Source Path: {}, Search: {}", sourcePath, search);

        final String taskId = UUID.randomUUID().toString();
        boolean useEmail = !requestContext.invokeAsUser();
        String accountID = authorizationContext.getAuthorizedAccount().getAccountId();
        Map<Class<?>, Object> threadLocals = ThreadLocalProxy.getAll();

        executorService.submit(() -> {
            try {
                ThreadLocalProxy.setAll(threadLocals);
                FreeForm freeForm = managementDao.getFileByPath(sourcePath, FileUtil.validateTextInput(search), useEmail, accountID);
                Thread.sleep(2000); // Adding 2 seconds delay to initialize wait for event in case of quick response
                eventHandler.handleEvent(taskId, freeForm);
            } catch (InterruptedException cause) {
                Thread.currentThread().interrupt();
            }

        });
        return taskId;
    }

    @CatalogRequest(
            id = "localDomainRequest_3f16a853-5d28-4af7-8d4c-6f8eab2d341a",
            name = "List Folders",
            description = "Produces a non-recursive list of folders that is present within the folder denoted by the source path.",
            area = "Management",
            type = CatalogRequest.Type.QUERY_SYSTEM)
    @Field.Text(name = "Task ID", required = false, attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'Unique identifier for tracking this asynchronous operation. Use this ID with the Get Result operation to retrieve the file list.'")})
    public String listFolders(
            @Field.Text(name = "Source Path", attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'Full path to the folder in the cloud storage to list files from (e.g., /Documents or /Sites/TeamSite/Shared Documents). For shared drives, use format driveId:/path/to/folder'")}) String sourcePath,
            @Field.Text(name = "Search", required = false, attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'Optional search pattern to filter files by name (supports wildcards and regular expressions)'")}) String search) {
        LOGGER.info("List Folders operation initiated - Source Path: {}, Search: {}", sourcePath, search);

        final String taskId = UUID.randomUUID().toString();
        boolean useEmail = !requestContext.invokeAsUser();
        String accountID = authorizationContext.getAuthorizedAccount().getAccountId();
        Map<Class<?>, Object> threadLocals = ThreadLocalProxy.getAll();

        executorService.submit(() -> {
            try {
                ThreadLocalProxy.setAll(threadLocals);
                FreeForm freeForm = managementDao.getFoldersByPath(sourcePath, FileUtil.validateTextInput(search), useEmail, accountID);

                Thread.sleep(2000); // Adding 2 seconds delay to initialize wait for event in case of quick response
                eventHandler.handleEvent(taskId, freeForm);
            } catch (InterruptedException cause) {
                Thread.currentThread().interrupt();
            } catch (Exception cause) {
                LOGGER.error("Error occurred while listing folders", cause);
            }
        });
        return taskId;
    }


    @CatalogRequest(
            id = "localDomainRequest_691267f6-205e-4143-9032-88b2a81abdd9",
            name = "Move",
            description = "Move a file on the drive from source to target where both denote a full file path.",
            area = "Management",
            type = CatalogRequest.Type.CHANGE_SYSTEM)
    @Field.Desc(name = "File", type = "Entity(File)", required = false)
    @Field.Boolean(name = "Success", required = false, attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'Indicates whether the move operation was successful'")})
    @Field.Text(name = "Message", required = false, attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'Provides details about the operation result or any errors encountered'")})
    public ExtensionResponse move(
            @Field.Text(name = "Source", attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'Full path to the source file or folder to be moved (e.g., /Documents/file.txt or /Folders/MyFolder). For shared drives, use format driveId:/path/to/file'")}) String source,
            @Field.Text(name = "Target", attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'Full path where the file or folder should be moved to (e.g., /Documents/NewLocation or /Folders/Archive). For shared drives, use format driveId:/path/to/destination'")}) String target,
            @Field.Boolean(name = Constants.ENABLE_ERROR_HANDLING, required = false,
                    attributes = {
                            @Attribute(name = "toolTip", value = "'Enable to allow re-entry of values if validation fails'")
                    }
            ) Boolean allowRetry) {
        return managementDao.moveFile(source, target, allowRetry);
    }

    @CatalogRequest(
            id = "localDomainRequest_4c6bb990-da44-473b-b697-4527494484fd",
            name = "Read",
            description = "Read the content of the file as plain text.",
            area = "Management",
            type = CatalogRequest.Type.CHANGE_SYSTEM)
    @Field(name = "Content", type = "Paragraph", required = false, attributes = {@Attribute(name = "visualWidth", value = "L"), @Attribute(name = "toolTip", value = "'The text content of the file'")})
    @Field.Boolean(name = "Success", required = false, attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'Indicates whether the read operation was successful'")})
    @Field.Text(name = "Message", required = false, attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'Provides details about the operation result or any errors encountered'")})
    public ExtensionResponse read(
            @Field.Text(name = "Source", attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'Full path to the text file to be read (e.g., /Documents/notes.txt or /Folders/MyFolder/config.json). For shared drives, use format driveId:/path/to/file'")}) String source) {
        return managementDao.readTxtFile(source);
    }

    @CatalogRequest(
            id = "localDomainRequest_7c5d230a-12eb-4eee-82cf-0b6c0a5256cf",
            name = "Rename",
            description = "Renames the file given at the Source File Path to the name Given.",
            area = "Management",
            type = CatalogRequest.Type.CHANGE_SYSTEM)
    @Field.Boolean(name = "Success", required = false, attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'Indicates whether the rename operation was successful'")})
    @Field.Text(name = "Message", required = false, attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'Provides details about the operation result or any errors encountered'")})
    public ExtensionResponse rename(
            @Field.Text(name = "Source", attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'Full path to the file or folder to be renamed (e.g., /Documents/old_name.txt or /Folders/OldFolder). For shared drives, use format driveId:/path/to/file'")}) String source,
            @Field.Text(name = "Target File Name", attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'New name for the file or folder (without the path, e.g., new_name.txt or NewFolder)'")}) String targetFileName) {
        return managementDao.renameFile(source, targetFileName);
    }

    @CatalogRequest(
            id = "localDomainRequest_d66a7825-5d45-4486-8a00-1de62dd1ddd3",
            name = "Share File",
            description = "Share the file denoted by source with the given users identified by their email address applying the provided Permissions.",
            area = "Management",
            type = CatalogRequest.Type.CHANGE_SYSTEM)
    @Field.Desc(name = "File", type = "Entity(File)", required = false)
    @Field.Text(name = "Message", required = false, attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'Provides details about the sharing operation result or any errors encountered'")})
    @Field.Boolean(name = "Success", required = false, attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'Indicates whether the file sharing operation was successful'")})
    public ExtensionResponse shareFile(
            @Field.Text(name = "Source", attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'Full path to the file to be shared (e.g., /Documents/file.txt). For shared drives, use format driveId:/path/to/file'")}) String source,
            @Field.Desc(name = "Share", type = "[ { User: Email, Permissions: PickOne(Read|Write|Read and Write|Own) } ]") List<Map<String, Object>> share) {
        return managementDao.shareFile(source, share);
    }

    @CatalogRequest(
            id = "localDomainRequest_7842cdaf-513b-4b52-a70a-1d7c1c1c5fc6",
            name = "Write",
            description = "Upload the content of the file from the media server to the cloud storage at target path.",
            area = "Management",
            type = CatalogRequest.Type.CHANGE_SYSTEM)
    @Field.Desc(name = "File", type = "Entity(File)", required = false)
    @Field.Boolean(name = "Success", required = false, attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'Indicates whether the file upload was successful'")})
    @Field.Text(name = "Message", required = false, attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'Provides details about the upload operation result or any errors encountered'")})
    public ExtensionResponse write(
            @Field.File(name = "Krista File", attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'The file to be uploaded to OneDrive. Supports files up to 50 MB in size'")}) app.krista.model.base.File kristaFile,
            @Field.Text(name = "Target Path", attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'Full path where the file should be uploaded (e.g., /Documents/file.pdf). For shared drives, use format driveId:/path/to/file'")}) String targetPath,
            @Field.Boolean(name = Constants.ENABLE_ERROR_HANDLING, required = false,
                    attributes = {
                            @Attribute(name = "toolTip", value = "'Enable to allow re-entry of values if validation fails'")
                    }
            ) Boolean allowRetry) {
        return managementDao.uploadFile(kristaFile, targetPath, allowRetry);
    }

    @CatalogRequest(
            id = "localDomainRequest_dcaada19-6e50-4b2d-95e6-6ec99a8d738d",
            name = "Write Text",
            description = "Uploads the file content to the cloud storage at the given target location as a plain text file.",
            area = "Management",
            type = CatalogRequest.Type.CHANGE_SYSTEM)
    @Field.Desc(name = "File", type = "Entity(File)", required = false)
    @Field.Boolean(name = "Success", required = false, attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'Indicates whether the text file was successfully created'")})
    @Field.Text(name = "Message", required = false, attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'Provides details about the operation result or any errors encountered'")})
    public ExtensionResponse writeText(
            @Field(name = "File Content", type = "Paragraph", attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'The text content to be written to the file.'")}) String fileContent,
            @Field.Text(name = "Target", attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'Full path where the text file should be created (e.g., /Documents/notes.txt). For shared drives, use format driveId:/path/to/file'")}) String target,
            @Field.Boolean(name = Constants.ENABLE_ERROR_HANDLING, required = false,
                    attributes = {
                            @Attribute(name = "toolTip", value = "'Enable to allow re-entry of values if validation fails'")
                    }
            ) Boolean allowRetry) {
        return managementDao.uploadFileAsTxt(fileContent, target, allowRetry);
    }

    @CatalogRequest(
            id = "localDomainRequest_799daa46-a5c5-47ad-9b39-094ee2b9d48d",
            name = "Changes Since",
            description = "This endpoint produces a list of recent updates within a specified folder since a given date. The updates include information about the path, file status (e.g., Created, Updated, Deleted), and can be filtered using an optional regex in the search variable.",
            area = "Management",
            type = CatalogRequest.Type.CHANGE_SYSTEM)
    @Field.Desc(name = "Result", type = "[ { Path: Text, Date: Date, Update: PickOne(Created|Updated|Deleted) } ]", required = false)
    @Field.Boolean(name = "Success", required = false, attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'Indicates whether the change history was successfully retrieved'")})
    @Field.Text(name = "Message", required = false, attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'Provides details about the operation result or any errors encountered'")})
    public ExtensionResponse changesSince(
            @Field.Text(name = "Source Path", attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'Full path to the folder or location in the cloud storage that you want to monitor for changes (e.g., /Documents or /Sites/TeamSite/Shared Documents)'")}) String sourcePath,
            @Field.Text(name = "Search", required = false, attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'Optional regular expression pattern to filter the subfolder names (e.g., Project.* to only show folders starting with \"Project\")'")}) String search,
            @Field.Date(name = "Since", required = true, showHowManyDaysInViewer = 60, allowToday = false, allowFuture = false, attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'The starting date and time from which to retrieve changes. Only changes that occurred after this timestamp will be included.'")}) Long since) {
        return managementDao.getChangesSince(sourcePath, FileUtil.validateTextInput(search), since);

    }

    @CatalogRequest(
            id = "localDomainRequest_d6ab2648-df6b-4429-aeef-a56c75fde191",
            name = "When a File is Changed",
            description = "Monitors the Cloud Storage for changes. When a change arrives and the affected files/folders match the source path and event type, the event will be triggered.",
            area = "Management",
            type = CatalogRequest.Type.WAIT_FOR_EVENT)
    @Field.Desc(name = "File", type = "Entity(File)", required = false)
    @Field.PickOne(name = "Event", values = {"Created", "Updated", "Deleted", "Shared"}, required = false, attributes = {@Attribute(name = "visualWidth", value = "S")})
    public Map<String, Object> whenAFileIsChanged(
            @Field(name = "eventName", type = "Text") String eventName,
            @Field(name = "eventData", type = "FreeForm") FreeForm eventData,
            @Field.Text(name = "Source", attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'Path to monitor for changes. Can be a specific file path or a folder path to monitor all files within that folder.)'")}) String source,
            @Field.PickOne(name = "Event", values = {"Created", "Updated", "Deleted", "Shared"}, required = false, attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'Optional filter to only trigger on specific types of changes. If not specified, all change types will trigger this event.'")}) String event,
            @Field.Text(name = "Search", required = false, attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'Optional regular expression pattern to filter the subfolder names (e.g., Project.* to only show folders starting with \"Project\")'")}) String search) {
        if (eventName.equalsIgnoreCase(Constants.FILE_RECEIVED)
                && eventData.get(Constants.EVENT) != null && !eventData.get(Constants.EVENT).equals(Constants.NO_CHANGE)
                && isValidInfo(eventData, search, source, event)) {
            Map<String, Object> map = new HashMap<>();
            map.put(Constants.FILE, eventData.get(Constants.FILE));
            map.put(Constants.EVENT, eventData.get(Constants.EVENT));
            return map;
        }
        throw new IllegalStateException("Invalid Input");
    }

    @CatalogRequest(
            id = "localDomainRequest_79e6315a-13aa-4e6c-97be-9e88f34c0a06",
            name = "Create Folder",
            description = "Accept folder name and path as input and create folder at given path.",
            area = "Management",
            type = CatalogRequest.Type.CHANGE_SYSTEM)
    @Field.Text(name = "Message", required = false, attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'Provides details about the operation result or any errors encountered'")})
    @Field.Boolean(name = "Success", required = false, attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'Indicates whether the folder was successfully created'")})
    @Field.Desc(name = "File", type = "Entity(File)", required = false)
    public ExtensionResponse createFolder(
            @Field.Text(name = "Folder Name", attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'The name for the new folder. This should not include path separators .'")}) String folderName,
            @Field.Text(name = "Path", attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'The full path where the new folder should be created. The parent folder must already exist.'")}) String path) {
        return managementDao.createFolder(folderName, path);
    }


    private boolean isValidInfo(FreeForm eventData, String search, String source, String event) {
        if (event != null && !eventData.get(Constants.EVENT).equals(event)) {
            return false;
        }
        app.krista.extensions.essentials.cloudfiles.onedrive.catalog.entities.File fileEntity = (app.krista.extensions.essentials.cloudfiles.onedrive.catalog.entities.File) eventData.get(Constants.FILE);
        if (isContainsSource(fileEntity, source) && isContainsSearch(fileEntity, search)) {
            return true;

        } else return isContainsSource(fileEntity, source) && isContainsSearch(fileEntity, search);
    }

    private boolean isContainsSource(app.krista.extensions.essentials.cloudfiles.onedrive.catalog.entities.File fileEntity, String source) {
        return source != null && fileEntity.absolutePath != null && FileUtil.isPathContainsInput(fileEntity.absolutePath, source);
    }

    private boolean isContainsSearch(app.krista.extensions.essentials.cloudfiles.onedrive.catalog.entities.File eventData, String search) {
        if (search != null && !search.isEmpty() && eventData.name != null && eventData.name.contains(search)) {
            return true;
        }
        return search == null;
    }
}