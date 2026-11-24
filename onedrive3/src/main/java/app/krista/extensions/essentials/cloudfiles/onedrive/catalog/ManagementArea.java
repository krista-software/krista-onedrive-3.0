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

import app.krista.extension.authorization.MustAuthorizeException;
import app.krista.extension.executor.ExtensionResponse;
import app.krista.extension.executor.ExtensionResponseBuilder;
import app.krista.extension.executor.Invoker;
import app.krista.extension.impl.anno.Attribute;
import app.krista.extension.impl.anno.CatalogRequest;
import app.krista.extension.impl.anno.Domain;
import app.krista.extension.impl.anno.Field;
import app.krista.extensions.essentials.cloudfiles.onedrive.OnedriveSetUpService;
import app.krista.extensions.essentials.cloudfiles.onedrive.audit.AuditEventType;
import app.krista.extensions.essentials.cloudfiles.onedrive.audit.OneDriveAuditService;
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
import java.io.IOException;
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
    private final OneDriveAuditService auditService;
    private final OnedriveSetUpService onedriveSetUpService;
    private final String invokerId;
    private final ExecutorService executorService = Executors.newFixedThreadPool(5);


    @Inject
    public ManagementArea(ManagementDaoImpl managementDao, EventHandler eventHandler, RequestContext requestContext,
                          AuthorizationContext authorizationContext, OneDriveAuditService auditService,
                          Invoker invoker, OnedriveSetUpService onedriveSetUpService) {
        this.managementDao = managementDao;
        this.eventHandler = eventHandler;
        this.requestContext = requestContext;
        this.authorizationContext = authorizationContext;
        this.auditService = auditService;
        this.onedriveSetUpService = onedriveSetUpService;
        this.invokerId = invoker.getInvokerId();
    }

    @CatalogRequest(
            id = "localDomainRequest_b277227d-746c-469e-8bed-75233b6b6133",
            name = "Copy",
            description = "Copy the provide file represented by the entity to the target location.",
            area = "Management",
            type = CatalogRequest.Type.CHANGE_SYSTEM)
    @Field.Desc(name = "File", type = "Entity(File)", required = false)
    @Field.Text(name = "Message", required = false, attributes = {@Attribute(name = "visualWidth", value = "L"), @Attribute(name = "toolTip", value = "'Provides details about the operation result'")})
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
        auditService.startRequest(invokerId, "Copy");
        try {
            LOGGER.debug("Delegating copy file operation to managementDao");
            ExtensionResponse response = managementDao.copyFile(source, target, replace, createFolder, allowRetry);
            LOGGER.info("Copy file operation completed");
            auditService.record(invokerId, AuditEventType.CATALOG_REQUEST_SUCCESS, "success", "Copy", null, null, null);
            return response;
        } catch (MustAuthorizeException cause) {
            auditService.record(invokerId, AuditEventType.CATALOG_REQUEST_FAILURE, "failure", "Copy",
                    cause.getMessage(), cause.getClass().getSimpleName(), null);
            throw cause;
        } catch (Exception e) {
            LOGGER.error("Copy file operation failed with exception", e);
            auditService.record(invokerId, AuditEventType.CATALOG_REQUEST_FAILURE, "failure", "Copy",
                    e.getMessage(), e.getClass().getSimpleName(), null);
            return new ExtensionResponseBuilder()
                    .success(Map.of("Success", false, "Message", "Failed to copy file: " + e.getMessage()), null, null)
                    .build();
        }
    }

    @CatalogRequest(
            id = "localDomainRequest_0f7c0ee1-2127-4a5d-bb33-fdb98b055012",
            name = "Copy With Name",
            description = "Atomically copies a file into the target folder with a specified name, avoiding renames and preventing collisions during parallel execution.",
            area = "Management",
            type = CatalogRequest.Type.CHANGE_SYSTEM)
    @Field.Desc(name = "File", type = "Entity(File)",required = false)
    @Field.Text(name = "Message", required = false, attributes = {@Attribute(name = "visualWidth", value = "L")}, options = {})
    @Field.Boolean(name = "Success", required = false, attributes = {@Attribute(name = "visualWidth", value = "S")}, options = {})
    public ExtensionResponse copyWithName(
            @Field.Text(name = "Source", required = true, attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'Full path to the source file to be copied'")}, options = {}) String source,
            @Field.Text(name = "Target", required = true, attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'Full path to the target folder'")}, options = {}) String target,
            @Field.Text(name = "Target File Name", required = true, attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'Name for the copied file (e.g., 2026-04-16_ACN-SLOB-Worksheet.xlsx'")}, options = {}) String targetFileName,
            @Field.Boolean(name = "Replace", required = false, attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'If true, replaces any existing file at the target location'")}, options = {}) Boolean replace,
            @Field.Boolean(name = "Create Folder", required = false, attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'If true, creates any new folders in the target path'")}, options = {}) Boolean createFolder) {
        LOGGER.info("Copy With Name operation initiated - Source: {}, Target: {}, Target File Name: {}, Replace: {}, Create Folder: {}",
                source, target, targetFileName, replace, createFolder);
        auditService.startRequest(invokerId, "Copy With Name");
        try {
            ExtensionResponse response = managementDao.copyFile(source, target, replace, createFolder, false, targetFileName);
            LOGGER.info("Copy With Name operation completed successfully");
            auditService.record(invokerId, AuditEventType.CATALOG_REQUEST_SUCCESS, "success", "Copy With Name", null, null, null);
            return response;
        } catch (MustAuthorizeException cause) {
            auditService.record(invokerId, AuditEventType.CATALOG_REQUEST_FAILURE, "failure", "Copy With Name",
                    cause.getMessage(), cause.getClass().getSimpleName(), null);
            throw cause;
        } catch (Exception e) {
            LOGGER.error("Copy With Name operation failed with exception", e);
            auditService.record(invokerId, AuditEventType.CATALOG_REQUEST_FAILURE, "failure", "Copy With Name",
                    e.getMessage(), e.getClass().getSimpleName(), null);
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
    @Field.Text(name = "Message", required = false, attributes = {@Attribute(name = "visualWidth", value = "L"), @Attribute(name = "toolTip", value = "'Provides details about the operation result'")})
    public ExtensionResponse delete(
            @Field.Text(name = "Target", attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'Full path to the file to be deleted (e.g., /Documents/file.txt or /Folders/MyFolder). For shared drives, use format driveId:/path/to/file'")}) String target,
            @Field.Boolean(name = Constants.ENABLE_ERROR_HANDLING, required = false,
                    attributes = {
                            @Attribute(name = "toolTip", value = "'Enable to allow re-entry of values if validation fails'")
                    }
            ) Boolean allowRetry) {
        LOGGER.info("Delete file operation initiated - Target: {}, Allow Retry: {}", target, allowRetry);
        auditService.startRequest(invokerId, "Delete");
        try {
            ExtensionResponse response = managementDao.deleteFile(target, allowRetry);
            LOGGER.info("Delete file operation completed");
            auditService.record(invokerId, AuditEventType.CATALOG_REQUEST_SUCCESS, "success", "Delete", null, null, null);
            return response;
        } catch (MustAuthorizeException cause) {
            auditService.record(invokerId, AuditEventType.CATALOG_REQUEST_FAILURE, "failure", "Delete",
                    cause.getMessage(), cause.getClass().getSimpleName(), null);
            throw cause;
        } catch (Exception cause) {
            LOGGER.error("Delete file operation failed with exception", cause);
            auditService.record(invokerId, AuditEventType.CATALOG_REQUEST_FAILURE, "failure", "Delete",
                    cause.getMessage(), cause.getClass().getSimpleName(), null);
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
            @Field.Text(name = "Source Path", attributes = {@Attribute(name = "visualWidth", value = "L"), @Attribute(name = "toolTip", value = "'Full path to the file in the cloud storage that should be downloaded (e.g., /Documents/file.pdf or /Sites/TeamSite/Shared Documents/report.xlsx). For shared drives, use format driveId:/path/to/file'")}) String source) {
        LOGGER.info("Download as file operation initiated - Source: {}", source);
        auditService.startRequest(invokerId, "Download as File");
        try {
            ExtensionResponse response = managementDao.downloadFileAsFile(source);
            LOGGER.info("Download as file operation completed");
            auditService.record(invokerId, AuditEventType.CATALOG_REQUEST_SUCCESS, "success", "Download as File", null, null, null);
            return response;
        } catch (MustAuthorizeException cause) {
            auditService.record(invokerId, AuditEventType.CATALOG_REQUEST_FAILURE, "failure", "Download as File",
                    cause.getMessage(), cause.getClass().getSimpleName(), null);
            throw cause;
        } catch (Exception e) {
            LOGGER.error("Download as file operation failed with exception", e);
            auditService.record(invokerId, AuditEventType.CATALOG_REQUEST_FAILURE, "failure", "Download as File",
                    e.getMessage(), e.getClass().getSimpleName(), null);
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
    @Field.Text(name = "Message", required = false, attributes = {@Attribute(name = "visualWidth", value = "L"), @Attribute(name = "toolTip", value = "'Provides details about the operation result or any errors encountered'")})
    @Field(name = "Link", type = "RichText", required = false, attributes = {@Attribute(name = "visualWidth", value = "L"), @Attribute(name = "toolTip", value = "'The generated hyperlink to access the file directly in OneDrive'")})
    public ExtensionResponse downloadAsLink(
            @Field.Text(name = "Source", attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'Full path to the file in the cloud storage (e.g., /Documents/file.pdf or /Sites/TeamSite/Shared Documents/report.xlsx). For shared drives, use format driveId:/path/to/file'")}) String source) {
        auditService.startRequest(invokerId, "Download as Link");
        try {
            ExtensionResponse response = managementDao.downloadFileAsLink(source);
            auditService.record(invokerId, AuditEventType.CATALOG_REQUEST_SUCCESS, "success", "Download as Link", null, null, null);
            return response;
        } catch (MustAuthorizeException cause) {
            auditService.record(invokerId, AuditEventType.CATALOG_REQUEST_FAILURE, "failure", "Download as Link",
                    cause.getMessage(), cause.getClass().getSimpleName(), null);
            throw cause;
        } catch (Exception cause) {
            auditService.record(invokerId, AuditEventType.CATALOG_REQUEST_FAILURE, "failure", "Download as Link",
                    cause.getMessage(), cause.getClass().getSimpleName(), null);
            throw cause;
        }
    }

    @CatalogRequest(
            id = "localDomainRequest_8d92b36c-792d-4cfe-b63d-c1dededbf075",
            name = "Get Info",
            description = "Accepts the source of the file and returns a Task ID, which will be further used to retrieve result information.",
            area = "Management",
            type = CatalogRequest.Type.QUERY_SYSTEM)
    @Field.Text(name = "Task ID", required = false, attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'Unique identifier for tracking this asynchronous operation. Use this ID with the Get Info Result operation to retrieve the complete folder statistics.'")})
    public String getInfo(
            @Field.Text(name = "Source Path", attributes = {@Attribute(name = "visualWidth", value = "L"), @Attribute(name = "toolTip", value = "'Full path to the folder in the cloud storage to analyze (e.g., /Documents or /Sites/TeamSite/Shared Documents). For shared drives, use format driveId:/path/to/folder'")}) String sourcePath) {
        LOGGER.info("Get Info operation initiated - Source Path: {}", sourcePath);
        ensureUserAuthorized(); // Pre-check: verify user token synchronously before async submit (Jira pattern)
        final String taskId = UUID.randomUUID().toString();
        boolean useEmail = !requestContext.invokeAsUser();
        String accountID = authorizationContext.getAuthorizedAccount().getAccountId();
        Map<Class<?>, Object> threadLocals = ThreadLocalProxy.getAll();

        executorService.submit(() -> {
            try {
                ThreadLocalProxy.setAll(threadLocals);
                auditService.startRequest(invokerId, "Get Info");
                FreeForm freeForm = managementDao.getInfo(sourcePath, useEmail, accountID);
                auditService.record(invokerId, AuditEventType.CATALOG_REQUEST_SUCCESS, "success", "Get Info", null, null, null);
                Thread.sleep(10000); // Adding 10 seconds delay to initialize wait for event in case of quick response
                eventHandler.handleEvent(taskId, freeForm);
            } catch (InterruptedException cause) {
                Thread.currentThread().interrupt();
            } catch (MustAuthorizeException cause) {
                auditService.record(invokerId, AuditEventType.CATALOG_REQUEST_FAILURE, "failure", "Get Info",
                        cause.getMessage(), cause.getClass().getSimpleName(), null);
            } catch (Exception cause) {
                auditService.record(invokerId, AuditEventType.CATALOG_REQUEST_FAILURE, "failure", "Get Info",
                        cause.getMessage(), cause.getClass().getSimpleName(), null);
            } finally {
                ThreadLocalProxy.removeAll();
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
    @Field.Text(name = "Message", required = false, attributes = {@Attribute(name = "visualWidth", value = "L"), @Attribute(name = "toolTip", value = "'Provides details about the operation result or any errors encountered'")})
    @Field.Boolean(name = "Success", required = false, attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'Indicates whether the folder information retrieval was successful'")})
    public Map<String, Object> getInfoResult(
            @Field(name = "eventName", type = "Text") String eventName,
            @Field(name = "eventData", type = "FreeForm") FreeForm eventData,
            @Field.Text(name = "Task ID", attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'The task identifier returned by operations like List Files or List Folders, used to retrieve the results of the asynchronous operation'")}) String taskID) {
        auditService.startRequest(invokerId, "Get Info Result");
        Map<String, Object> payload = new HashMap<>();
        if (eventName.equals(taskID)) {
            ResponseUtil.getFileEventData(eventData, payload);
            auditService.record(invokerId, AuditEventType.CATALOG_REQUEST_SUCCESS, "success", "Get Info Result", null, null, null);
            return payload;
        }
        auditService.record(invokerId, AuditEventType.CATALOG_REQUEST_SUCCESS, "success", "Get Info Result", null, null, null);
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
    @Field.Text(name = "Message", required = false, attributes = {@Attribute(name = "visualWidth", value = "L"), @Attribute(name = "toolTip", value = "'Provides details about the operation result or any errors encountered'")})
    public Map<String, Object> getResult(
            @Field(name = "eventName", type = "Text") String eventName,
            @Field(name = "eventData", type = "FreeForm") FreeForm eventData,
            @Field.Text(name = "Task ID", attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'The task identifier returned by operations like List Files or List Folders, used to retrieve the results of the asynchronous operation'")}) String taskID) {
        auditService.startRequest(invokerId, "Get Result");
        if (eventName.equals(taskID)) {
            Map<String, Object> map = new HashMap<>();
            map.put(Constants.FILES, eventData.get(Constants.FILES));
            map.put(Constants.MESSAGE, eventData.get(Constants.MESSAGE));
            map.put(Constants.SUCCESS, eventData.get(Constants.SUCCESS));
            auditService.record(invokerId, AuditEventType.CATALOG_REQUEST_SUCCESS, "success", "Get Result", null, null, null);
            return map;
        }
        auditService.record(invokerId, AuditEventType.CATALOG_REQUEST_FAILURE, "failure", "Get Result", "Invalid Input", null, null);
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
            @Field.Text(name = "Source Path", attributes = {@Attribute(name = "visualWidth", value = "L"), @Attribute(name = "toolTip", value = "'Full path to the folder in the cloud storage to list files from (e.g., /Documents or /Sites/TeamSite/Shared Documents). For shared drives, use format driveId:/path/to/folder'")}) String sourcePath,
            @Field.Text(name = "Search", required = false, attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'Optional search pattern to filter files by name (supports wildcards and regular expressions)'")}) String search) {
        LOGGER.info("List Files operation initiated - Source Path: {}, Search: {}", sourcePath, search);
        ensureUserAuthorized(); // Pre-check: verify user token synchronously before async submit (Jira pattern)
        final String taskId = UUID.randomUUID().toString();
        boolean useEmail = !requestContext.invokeAsUser();
        String accountID = authorizationContext.getAuthorizedAccount().getAccountId();
        Map<Class<?>, Object> threadLocals = ThreadLocalProxy.getAll();

        executorService.submit(() -> {
            try {
                ThreadLocalProxy.setAll(threadLocals);
                auditService.startRequest(invokerId, "List Files");
                FreeForm freeForm = managementDao.getFileByPath(sourcePath, FileUtil.validateTextInput(search), useEmail, accountID);
                auditService.record(invokerId, AuditEventType.CATALOG_REQUEST_SUCCESS, "success", "List Files", null, null, null);
                Thread.sleep(10000); // Adding 10 seconds delay to initialize wait for event in case of quick response
                eventHandler.handleEvent(taskId, freeForm);
            } catch (InterruptedException cause) {
                Thread.currentThread().interrupt();
            } catch (MustAuthorizeException cause) {
                auditService.record(invokerId, AuditEventType.CATALOG_REQUEST_FAILURE, "failure", "List Files",
                        cause.getMessage(), cause.getClass().getSimpleName(), null);
            } catch (Exception cause) {
                auditService.record(invokerId, AuditEventType.CATALOG_REQUEST_FAILURE, "failure", "List Files",
                        cause.getMessage(), cause.getClass().getSimpleName(), null);
            } finally {
                ThreadLocalProxy.removeAll();
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
            @Field.Text(name = "Source Path", attributes = {@Attribute(name = "visualWidth", value = "L"), @Attribute(name = "toolTip", value = "'Full path to the folder in the cloud storage to list files from (e.g., /Documents or /Sites/TeamSite/Shared Documents). For shared drives, use format driveId:/path/to/folder'")}) String sourcePath,
            @Field.Text(name = "Search", required = false, attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'Optional search pattern to filter files by name (supports wildcards and regular expressions)'")}) String search) {
        LOGGER.info("List Folders operation initiated - Source Path: {}, Search: {}", sourcePath, search);
        ensureUserAuthorized(); // Pre-check: verify user token synchronously before async submit (Jira pattern)
        final String taskId = UUID.randomUUID().toString();
        boolean useEmail = !requestContext.invokeAsUser();
        String accountID = authorizationContext.getAuthorizedAccount().getAccountId();
        Map<Class<?>, Object> threadLocals = ThreadLocalProxy.getAll();

        executorService.submit(() -> {
            try {
                ThreadLocalProxy.setAll(threadLocals);
                auditService.startRequest(invokerId, "List Folders");
                FreeForm freeForm = managementDao.getFoldersByPath(sourcePath, FileUtil.validateTextInput(search), useEmail, accountID);
                auditService.record(invokerId, AuditEventType.CATALOG_REQUEST_SUCCESS, "success", "List Folders", null, null, null);
                Thread.sleep(10000); // Adding 10 seconds delay to initialize wait for event in case of quick response
                eventHandler.handleEvent(taskId, freeForm);
            } catch (InterruptedException cause) {
                Thread.currentThread().interrupt();
            } catch (MustAuthorizeException cause) {
                auditService.record(invokerId, AuditEventType.CATALOG_REQUEST_FAILURE, "failure", "List Folders",
                        cause.getMessage(), cause.getClass().getSimpleName(), null);
            } catch (Exception cause) {
                LOGGER.error("Error occurred while listing folders", cause);
                auditService.record(invokerId, AuditEventType.CATALOG_REQUEST_FAILURE, "failure", "List Folders",
                        cause.getMessage(), cause.getClass().getSimpleName(), null);
            } finally {
                ThreadLocalProxy.removeAll();
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
    @Field.Text(name = "Message", required = false, attributes = {@Attribute(name = "visualWidth", value = "L"), @Attribute(name = "toolTip", value = "'Provides details about the operation result or any errors encountered'")})
    public ExtensionResponse move(
            @Field.Text(name = "Source", attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'Full path to the source file or folder to be moved (e.g., /Documents/file.txt or /Folders/MyFolder). For shared drives, use format driveId:/path/to/file'")}) String source,
            @Field.Text(name = "Target", attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'Full path where the file or folder should be moved to (e.g., /Documents/NewLocation or /Folders/Archive). For shared drives, use format driveId:/path/to/destination'")}) String target,
            @Field.Boolean(name = Constants.ENABLE_ERROR_HANDLING, required = false,
                    attributes = {
                            @Attribute(name = "toolTip", value = "'Enable to allow re-entry of values if validation fails'")
                    }
            ) Boolean allowRetry) {
        auditService.startRequest(invokerId, "Move");
        try {
            ExtensionResponse response = managementDao.moveFile(source, target, allowRetry);
            auditService.record(invokerId, AuditEventType.CATALOG_REQUEST_SUCCESS, "success", "Move", null, null, null);
            return response;
        } catch (MustAuthorizeException cause) {
            auditService.record(invokerId, AuditEventType.CATALOG_REQUEST_FAILURE, "failure", "Move",
                    cause.getMessage(), cause.getClass().getSimpleName(), null);
            throw cause;
        } catch (Exception cause) {
            auditService.record(invokerId, AuditEventType.CATALOG_REQUEST_FAILURE, "failure", "Move",
                    cause.getMessage(), cause.getClass().getSimpleName(), null);
            throw cause;
        }
    }

    @CatalogRequest(
            id = "localDomainRequest_4c6bb990-da44-473b-b697-4527494484fd",
            name = "Read",
            description = "Read the content of the file as plain text.",
            area = "Management",
            type = CatalogRequest.Type.CHANGE_SYSTEM,
            tool = true)
    @Field(name = "Content", type = "Paragraph", required = false, attributes = {@Attribute(name = "visualWidth", value = "L"), @Attribute(name = "toolTip", value = "'The text content of the file'")})
    @Field.Boolean(name = "Success", required = false, attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'Indicates whether the read operation was successful'")})
    @Field.Text(name = "Message", required = false, attributes = {@Attribute(name = "visualWidth", value = "L"), @Attribute(name = "toolTip", value = "'Provides details about the operation result or any errors encountered'")})
    public ExtensionResponse read(
            @Field.Text(name = "Source", attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'Full path to the text file to be read (e.g., /Documents/notes.txt or /Folders/MyFolder/config.json). For shared drives, use format driveId:/path/to/file'")}) String source) {
        auditService.startRequest(invokerId, "Read");
        try {
            ensureUserAuthorized();
            ExtensionResponse response = managementDao.readTxtFile(source);
            auditService.record(invokerId, AuditEventType.CATALOG_REQUEST_SUCCESS, "success", "Read", null, null, null);
            return response;
        } catch (MustAuthorizeException cause) {
            auditService.record(invokerId, AuditEventType.CATALOG_REQUEST_FAILURE, "failure", "Read",
                    cause.getMessage(), cause.getClass().getSimpleName(), null);
            throw cause;
        } catch (Exception cause) {
            auditService.record(invokerId, AuditEventType.CATALOG_REQUEST_FAILURE, "failure", "Read",
                    cause.getMessage(), cause.getClass().getSimpleName(), null);
            throw cause;
        }
    }

    @CatalogRequest(
            id = "localDomainRequest_7c5d230a-12eb-4eee-82cf-0b6c0a5256cf",
            name = "Rename",
            description = "Renames the file given at the Source File Path to the name Given.",
            area = "Management",
            type = CatalogRequest.Type.CHANGE_SYSTEM,
            tool = true)
    @Field.Boolean(name = "Success", required = false, attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'Indicates whether the rename operation was successful'")})
    @Field.Text(name = "Message", required = false, attributes = {@Attribute(name = "visualWidth", value = "L"), @Attribute(name = "toolTip", value = "'Provides details about the operation result or any errors encountered'")})
    public ExtensionResponse rename(
            @Field.Text(name = "Source", attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'Full path to the file or folder to be renamed (e.g., /Documents/old_name.txt or /Folders/OldFolder). For shared drives, use format driveId:/path/to/file'")}) String source,
            @Field.Text(name = "Target File Name", attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'New name for the file or folder (without the path, e.g., new_name.txt or NewFolder)'")}) String targetFileName) {
        auditService.startRequest(invokerId, "Rename");
        try {
            ensureUserAuthorized();
            ExtensionResponse response = managementDao.renameFile(source, targetFileName);
            auditService.record(invokerId, AuditEventType.CATALOG_REQUEST_SUCCESS, "success", "Rename", null, null, null);
            return response;
        } catch (MustAuthorizeException cause) {
            auditService.record(invokerId, AuditEventType.CATALOG_REQUEST_FAILURE, "failure", "Rename",
                    cause.getMessage(), cause.getClass().getSimpleName(), null);
            throw cause;
        } catch (Exception cause) {
            auditService.record(invokerId, AuditEventType.CATALOG_REQUEST_FAILURE, "failure", "Rename",
                    cause.getMessage(), cause.getClass().getSimpleName(), null);
            throw cause;
        }
    }

    @CatalogRequest(
            id = "localDomainRequest_d66a7825-5d45-4486-8a00-1de62dd1ddd3",
            name = "Share File",
            description = "Share the file denoted by source with the given users identified by their email address applying the provided Permissions.",
            area = "Management",
            type = CatalogRequest.Type.CHANGE_SYSTEM)
    @Field.Desc(name = "File", type = "Entity(File)", required = false)
    @Field.Text(name = "Message", required = false, attributes = {@Attribute(name = "visualWidth", value = "L"), @Attribute(name = "toolTip", value = "'Provides details about the sharing operation result or any errors encountered'")})
    @Field.Boolean(name = "Success", required = false, attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'Indicates whether the file sharing operation was successful'")})
    public ExtensionResponse shareFile(
            @Field.Text(name = "Source", attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'Full path to the file to be shared (e.g., /Documents/file.txt). For shared drives, use format driveId:/path/to/file'")}) String source,
            @Field.Desc(name = "Share", type = "[ { User: Email, Permissions: PickOne(Read|Write|Read and Write|Own) } ]") List<Map<String, Object>> share) {
        auditService.startRequest(invokerId, "Share File");
        try {
            ExtensionResponse response = managementDao.shareFile(source, share);
            auditService.record(invokerId, AuditEventType.CATALOG_REQUEST_SUCCESS, "success", "Share File", null, null, null);
            return response;
        } catch (MustAuthorizeException cause) {
            auditService.record(invokerId, AuditEventType.CATALOG_REQUEST_FAILURE, "failure", "Share File",
                    cause.getMessage(), cause.getClass().getSimpleName(), null);
            throw cause;
        } catch (Exception cause) {
            auditService.record(invokerId, AuditEventType.CATALOG_REQUEST_FAILURE, "failure", "Share File",
                    cause.getMessage(), cause.getClass().getSimpleName(), null);
            throw cause;
        }
    }

    @CatalogRequest(
            id = "localDomainRequest_7842cdaf-513b-4b52-a70a-1d7c1c1c5fc6",
            name = "Write",
            description = "Upload the content of the file from the media server to the cloud storage at target path.",
            area = "Management",
            type = CatalogRequest.Type.CHANGE_SYSTEM)
    @Field.Desc(name = "File", type = "Entity(File)", required = false)
    @Field.Boolean(name = "Success", required = false, attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'Indicates whether the file upload was successful'")})
    @Field.Text(name = "Message", required = false, attributes = {@Attribute(name = "visualWidth", value = "L"), @Attribute(name = "toolTip", value = "'Provides details about the upload operation result or any errors encountered'")})
    public ExtensionResponse write(
            @Field.File(name = "Krista File", attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'The file to be uploaded to OneDrive. Supports files up to 50 MB in size'")}) app.krista.model.base.File kristaFile,
            @Field.Text(name = "Target Path", attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'Full path where the file should be uploaded (e.g., /Documents/file.pdf). For shared drives, use format driveId:/path/to/file'")}) String targetPath,
            @Field.Boolean(name = Constants.ENABLE_ERROR_HANDLING, required = false,
                    attributes = {
                            @Attribute(name = "toolTip", value = "'Enable to allow re-entry of values if validation fails'")
                    }
            ) Boolean allowRetry) {
        auditService.startRequest(invokerId, "Write");
        try {
            ExtensionResponse response = managementDao.uploadFile(kristaFile, targetPath, allowRetry);
            auditService.record(invokerId, AuditEventType.CATALOG_REQUEST_SUCCESS, "success", "Write", null, null, null);
            return response;
        } catch (MustAuthorizeException cause) {
            auditService.record(invokerId, AuditEventType.CATALOG_REQUEST_FAILURE, "failure", "Write",
                    cause.getMessage(), cause.getClass().getSimpleName(), null);
            throw cause;
        } catch (Exception cause) {
            auditService.record(invokerId, AuditEventType.CATALOG_REQUEST_FAILURE, "failure", "Write",
                    cause.getMessage(), cause.getClass().getSimpleName(), null);
            throw cause;
        }
    }

    @CatalogRequest(
            id = "localDomainRequest_dcaada19-6e50-4b2d-95e6-6ec99a8d738d",
            name = "Write Text",
            description = "Uploads the file content to the cloud storage at the given target location as a plain text file.",
            area = "Management",
            type = CatalogRequest.Type.CHANGE_SYSTEM,
            tool = true)
    @Field.Desc(name = "File", type = "Entity(File)", required = false)
    @Field.Boolean(name = "Success", required = false, attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'Indicates whether the text file was successfully created'")})
    @Field.Text(name = "Message", required = false, attributes = {@Attribute(name = "visualWidth", value = "L"), @Attribute(name = "toolTip", value = "'Provides details about the operation result or any errors encountered'")})
    public ExtensionResponse writeText(
            @Field(name = "File Content", type = "Paragraph", attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'The text content to be written to the file.'")}) String fileContent,
            @Field.Text(name = "Target", attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'Full path where the text file should be created (e.g., /Documents/notes.txt). For shared drives, use format driveId:/path/to/file'")}) String target,
            @Field.Boolean(name = Constants.ENABLE_ERROR_HANDLING, required = false,
                    attributes = {
                            @Attribute(name = "toolTip", value = "'Enable to allow re-entry of values if validation fails'")
                    }
            ) Boolean allowRetry) {
        auditService.startRequest(invokerId, "Write Text");
        try {
            ensureUserAuthorized();
            ExtensionResponse response = managementDao.uploadFileAsTxt(fileContent, target, allowRetry);
            auditService.record(invokerId, AuditEventType.CATALOG_REQUEST_SUCCESS, "success", "Write Text", null, null, null);
            return response;
        } catch (MustAuthorizeException cause) {
            auditService.record(invokerId, AuditEventType.CATALOG_REQUEST_FAILURE, "failure", "Write Text",
                    cause.getMessage(), cause.getClass().getSimpleName(), null);
            throw cause;
        } catch (Exception cause) {
            auditService.record(invokerId, AuditEventType.CATALOG_REQUEST_FAILURE, "failure", "Write Text",
                    cause.getMessage(), cause.getClass().getSimpleName(), null);
            throw cause;
        }
    }

    private void ensureUserAuthorized() {
        if (!authenticateAsUser()) {
            return;
        }
        try {
            onedriveSetUpService.verifyUserAuthorization();
        } catch (MustAuthorizeException cause) {
            throw cause;
        } catch (IOException cause) {
            throw new IllegalStateException("Failed to verify OneDrive user authorization", cause);
        }
    }

    private boolean authenticateAsUser() {
        return requestContext != null && requestContext.invokeAsUser();
    }

    @CatalogRequest(
            id = "localDomainRequest_799daa46-a5c5-47ad-9b39-094ee2b9d48d",
            name = "Changes Since",
            description = "This endpoint produces a list of recent updates within a specified folder since a given date. The updates include information about the path, file status (e.g., Created, Updated, Deleted), and can be filtered using an optional regex in the search variable.",
            area = "Management",
            type = CatalogRequest.Type.CHANGE_SYSTEM)
    @Field.Desc(name = "Result", type = "[ { Path: Text, Date: Date, Update: PickOne(Created|Updated|Deleted) } ]", required = false)
    @Field.Boolean(name = "Success", required = false, attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'Indicates whether the change history was successfully retrieved'")})
    @Field.Text(name = "Message", required = false, attributes = {@Attribute(name = "visualWidth", value = "L"), @Attribute(name = "toolTip", value = "'Provides details about the operation result or any errors encountered'")})
    public ExtensionResponse changesSince(
            @Field.Text(name = "Source Path", attributes = {@Attribute(name = "visualWidth", value = "L"), @Attribute(name = "toolTip", value = "'Full path to the folder or location in the cloud storage that you want to monitor for changes (e.g., /Documents or /Sites/TeamSite/Shared Documents)'")}) String sourcePath,
            @Field.Text(name = "Search", required = false, attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'Optional regular expression pattern to filter the subfolder names (e.g., Project.* to only show folders starting with \"Project\")'")}) String search,
            @Field.Date(name = "Since", required = true, showHowManyDaysInViewer = 60, allowToday = false, allowFuture = false, attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'The starting date and time from which to retrieve changes. Only changes that occurred after this timestamp will be included.'")}) Long since) {
        auditService.startRequest(invokerId, "Changes Since");
        try {
            ExtensionResponse response = managementDao.getChangesSince(sourcePath, FileUtil.validateTextInput(search), since);
            auditService.record(invokerId, AuditEventType.CATALOG_REQUEST_SUCCESS, "success", "Changes Since", null, null, null);
            return response;
        } catch (MustAuthorizeException cause) {
            auditService.record(invokerId, AuditEventType.CATALOG_REQUEST_FAILURE, "failure", "Changes Since",
                    cause.getMessage(), cause.getClass().getSimpleName(), null);
            throw cause;
        } catch (Exception cause) {
            auditService.record(invokerId, AuditEventType.CATALOG_REQUEST_FAILURE, "failure", "Changes Since",
                    cause.getMessage(), cause.getClass().getSimpleName(), null);
            throw cause;
        }
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
        auditService.startRequest(invokerId, "When a File is Changed");
        if (eventName.equalsIgnoreCase(Constants.FILE_RECEIVED)
                && eventData.get(Constants.EVENT) != null && !eventData.get(Constants.EVENT).equals(Constants.NO_CHANGE)
                && isValidInfo(eventData, search, source, event)) {
            Map<String, Object> map = new HashMap<>();
            map.put(Constants.FILE, eventData.get(Constants.FILE));
            map.put(Constants.EVENT, eventData.get(Constants.EVENT));
            auditService.record(invokerId, AuditEventType.CATALOG_REQUEST_SUCCESS, "success", "When a File is Changed", null, null, null);
            return map;
        }
        auditService.record(invokerId, AuditEventType.CATALOG_REQUEST_FAILURE, "failure", "When a File is Changed", "Invalid Input", null, null);
        throw new IllegalStateException("Invalid Input");
    }

    @CatalogRequest(
            id = "localDomainRequest_a2f94c61-3e17-4b08-bc90-5d7f1e2a0c83",
            name = "Get Drives",
            description = "Returns a list of drives accessible to the authenticated user, including their pre-formatted source paths. Use the returned Drive Path values directly as the Source Path in List Files, List Folders, or any other operation to access shared folders and SharePoint document libraries.",
            area = "Management",
            type = CatalogRequest.Type.QUERY_SYSTEM)
    @Field.Desc(name = "Drives", type = "[ { Drive Path: Text, Drive Name: Text, Drive Type: Text, Owner: Text } ]", required = false)
    @Field.Boolean(name = "Success", required = false, attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'Indicates whether the drives were successfully retrieved'")})
    @Field.Text(name = "Message", required = false, attributes = {@Attribute(name = "visualWidth", value = "L"), @Attribute(name = "toolTip", value = "'Provides details about the operation result or any errors encountered'")})
    public ExtensionResponse getDrives(
            @Field.Text(name = "Search", required = false, attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'Optional text to filter drives by name. Leave blank to return all accessible drives.'")}) String search) {
        LOGGER.info("Get Drives operation initiated - Search: {}", search);
        auditService.startRequest(invokerId, "Get Drives");
        try {
            boolean useEmail = !requestContext.invokeAsUser();
            String accountID = authorizationContext.getAuthorizedAccount().getAccountId();
            ExtensionResponse response = managementDao.getDrives(FileUtil.validateTextInput(search), useEmail, accountID);
            auditService.record(invokerId, AuditEventType.CATALOG_REQUEST_SUCCESS, "success", "Get Drives", null, null, null);
            return response;
        } catch (MustAuthorizeException cause) {
            auditService.record(invokerId, AuditEventType.CATALOG_REQUEST_FAILURE, "failure", "Get Drives",
                    cause.getMessage(), cause.getClass().getSimpleName(), null);
            throw cause;
        } catch (Exception cause) {
            LOGGER.error("Get Drives operation failed with exception", cause);
            auditService.record(invokerId, AuditEventType.CATALOG_REQUEST_FAILURE, "failure", "Get Drives",
                    cause.getMessage(), cause.getClass().getSimpleName(), null);
            return new ExtensionResponseBuilder()
                    .success(Map.of("Success", false, "Message", "Failed to retrieve drives: " + cause.getMessage()), null, null)
                    .build();
        }
    }

    @CatalogRequest(
            id = "localDomainRequest_79e6315a-13aa-4e6c-97be-9e88f34c0a06",
            name = "Create Folder",
            description = "Accept folder name and path as input and create folder at given path.",
            area = "Management",
            type = CatalogRequest.Type.CHANGE_SYSTEM)
    @Field.Text(name = "Message", required = false, attributes = {@Attribute(name = "visualWidth", value = "L"), @Attribute(name = "toolTip", value = "'Provides details about the operation result or any errors encountered'")})
    @Field.Boolean(name = "Success", required = false, attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'Indicates whether the folder was successfully created'")})
    @Field.Desc(name = "File", type = "Entity(File)", required = false)
    public ExtensionResponse createFolder(
            @Field.Text(name = "Folder Name", attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'The name for the new folder. This should not include path separators .'")}) String folderName,
            @Field.Text(name = "Path", attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'The full path where the new folder should be created. The parent folder must already exist.'")}) String path,
            @Field.Boolean(name = Constants.ENABLE_ERROR_HANDLING, required = false,
                    attributes = {
                            @Attribute(name = "toolTip", value = "'Enable to allow re-entry of values if validation fails'")
                    }
            ) Boolean allowRetry) {
        LOGGER.info("Create Folder operation initiated - Folder Name: {}, Path: {}, Allow Retry: {}", folderName, path, allowRetry);
        auditService.startRequest(invokerId, "Create Folder");
        try {
            ExtensionResponse response = managementDao.createFolder(folderName, path, allowRetry);
            auditService.record(invokerId, AuditEventType.CATALOG_REQUEST_SUCCESS, "success", "Create Folder", null, null, null);
            return response;
        } catch (MustAuthorizeException cause) {
            auditService.record(invokerId, AuditEventType.CATALOG_REQUEST_FAILURE, "failure", "Create Folder",
                    cause.getMessage(), cause.getClass().getSimpleName(), null);
            throw cause;
        } catch (Exception cause) {
            LOGGER.error("Create Folder operation failed with exception", cause);
            auditService.record(invokerId, AuditEventType.CATALOG_REQUEST_FAILURE, "failure", "Create Folder",
                    cause.getMessage(), cause.getClass().getSimpleName(), null);
            return new ExtensionResponseBuilder()
                    .success(Map.of("Success", false, "Message", "Failed to create folder: " + cause.getMessage()), null, null)
                    .build();
        }
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
        boolean matches = source != null && fileEntity.absolutePath != null && FileUtil.isPathContainsInput(fileEntity.absolutePath, source);
        LOGGER.info("isContainsSource: absolutePath='{}', source='{}', matches={}", fileEntity.absolutePath, source, matches);
        return matches;
    }

    private boolean isContainsSearch(app.krista.extensions.essentials.cloudfiles.onedrive.catalog.entities.File eventData, String search) {
        if (search != null && !search.isEmpty() && eventData.name != null && eventData.name.contains(search)) {
            return true;
        }
        return search == null;
    }
}
