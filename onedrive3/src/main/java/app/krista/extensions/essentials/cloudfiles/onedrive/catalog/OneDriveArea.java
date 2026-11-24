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
import app.krista.extension.executor.Invoker;
import app.krista.extension.impl.anno.Attribute;
import app.krista.extension.impl.anno.CatalogRequest;
import app.krista.extension.impl.anno.Domain;
import app.krista.extension.impl.anno.Field;
import app.krista.extensions.essentials.cloudfiles.onedrive.OnedriveSetUpService;
import app.krista.extensions.essentials.cloudfiles.onedrive.audit.AuditEventType;
import app.krista.extensions.essentials.cloudfiles.onedrive.audit.OneDriveAuditService;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.OperationsManagementDao;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.PermissionDao;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.util.FileUtil;
import app.krista.extensions.util.EventHandler;
import app.krista.ksdk.context.AuthorizationContext;
import app.krista.ksdk.context.RequestContext;
import app.krista.model.base.File;
import app.krista.model.base.FreeForm;
import com.kristasoft.common.holders.ThreadLocalProxy;

import javax.inject.Inject;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Domain(id = "catEntryDomain_30c63e77-37f2-4a1d-afd8-f6bfda07ad57",
        name = "Cloud Files",
        ecosystemId = "catEntryEcosystem_84b53163-327b-4b1b-8c96-9334d292f9f5",
        ecosystemName = "Essentials",
        ecosystemVersion = "fb35f83b-d270-4925-a896-7b677f04f126")
public class OneDriveArea {
    private final EventHandler eventHandler;
    private final RequestContext requestContext;
    private final AuthorizationContext authorizationContext;
    private final PermissionDao permissionDao;
    private final OperationsManagementDao operationsManagementDao;
    private final OneDriveAuditService auditService;
    private final OnedriveSetUpService onedriveSetUpService;
    private final String invokerId;
    private final ExecutorService executorService = Executors.newFixedThreadPool(5);

    @Inject
    public OneDriveArea(EventHandler eventHandler, RequestContext requestContext,
                        AuthorizationContext authorizationContext, PermissionDao permissionDao,
                        OperationsManagementDao operationsManagementDao,
                        OneDriveAuditService auditService, Invoker invoker,
                        OnedriveSetUpService onedriveSetUpService) {
        this.eventHandler = eventHandler;
        this.requestContext = requestContext;
        this.authorizationContext = authorizationContext;
        this.permissionDao = permissionDao;
        this.operationsManagementDao = operationsManagementDao;
        this.auditService = auditService;
        this.onedriveSetUpService = onedriveSetUpService;
        this.invokerId = invoker.getInvokerId();
    }

    @CatalogRequest(
            id = "localDomainRequest_80d6653c-955d-41e4-a77f-944004f8c2de",
            name = "Search By Permissions",
            description = "Accepts Permissions and returns Files based on permission given.",
            area = "OneDrive",
            type = CatalogRequest.Type.QUERY_SYSTEM)
    @Field.Text(name = "Task ID", required = false, attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'Unique identifier for tracking this asynchronous operation. Use this ID with the Get Result operation to retrieve files with the specified permission level'")})
    public String searchByPermissions(
            @Field.PickOne(name = "Permission", values = {"Read", "Write", "Read and Write", "Own"}, attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'The permission level to search for. Select from Read (view only), Write (edit only), Read and Write (view and edit), or Own (full control)'")}) String permission) {
        ensureUserAuthorized(); // Pre-check: verify user token synchronously before async submit (Jira pattern)
        final String taskId = UUID.randomUUID().toString();
        boolean useEmail = !requestContext.invokeAsUser();
        String accountID = authorizationContext.getAuthorizedAccount().getAccountId();
        Map<Class<?>, Object> threadLocals = ThreadLocalProxy.getAll();
        executorService.submit(() -> {
            try {
                ThreadLocalProxy.setAll(threadLocals);
                auditService.startRequest(invokerId, "Search By Permissions");
                FreeForm freeForm = permissionDao.getFileByPermission(permission, useEmail, accountID);
                auditService.record(invokerId, AuditEventType.CATALOG_REQUEST_SUCCESS, "success", "Search By Permissions", null, null, null);
                Thread.sleep(2000); // Adding 2 seconds delay to initialize wait for event in case of quick response
                eventHandler.handleEvent(taskId, freeForm);
            } catch (InterruptedException cause) {
                Thread.currentThread().interrupt();
            } catch (MustAuthorizeException cause) {
                auditService.record(invokerId, AuditEventType.CATALOG_REQUEST_FAILURE, "failure", "Search By Permissions",
                        cause.getMessage(), cause.getClass().getSimpleName(), null);
            } catch (Exception cause) {
                auditService.record(invokerId, AuditEventType.CATALOG_REQUEST_FAILURE, "failure", "Search By Permissions",
                        cause.getMessage(), cause.getClass().getSimpleName(), null);
            } finally {
                ThreadLocalProxy.removeAll();
            }
        });
        return taskId;
    }

    @CatalogRequest(
            id = "localDomainRequest_bafb5e96-3968-4ffa-b688-7d55b199331f",
            name = "Write with Custom File Name",
            description = "Upload the content of the file from the media server to the cloud storage at target path with Custom File name.",
            area = "OneDrive",
            type = CatalogRequest.Type.CHANGE_SYSTEM)
    @Field.Desc(name = "File", type = "Entity(File)", required = false)
    @Field.Boolean(name = "Success", required = false, attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'Indicates whether the file upload with custom name was successful'")})
    @Field.Text(name = "Message", required = false, attributes = {@Attribute(name = "visualWidth", value = "L"), @Attribute(name = "toolTip", value = "'Provides details about the upload operation result or any errors encountered'")})
    public ExtensionResponse writeWithCustomFileName(
            @Field.Text(name = "Target Path", attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'Full path where the file should be uploaded (e.g., /Documents or /Folders/MyFolder). For shared drives, use format driveId:/path/to/folder'")}) String targetPath,
            @Field.Text(name = "Custom Name", required = false, attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'Optional custom name to use for the uploaded file instead of the original filename. Include file extension (e.g., report.pdf)'")}) String customName,
            @Field.File(name = "Krista File", attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'The file to be uploaded to OneDrive. Supports files up to 50 MB in size'")}) File kristaFile) {
        auditService.startRequest(invokerId, "Write with Custom File Name");
        try {
            ExtensionResponse response = operationsManagementDao.uploadFiles(kristaFile, targetPath, FileUtil.validateTextInput(customName), false, false);
            auditService.record(invokerId, AuditEventType.CATALOG_REQUEST_SUCCESS, "success", "Write with Custom File Name", null, null, null);
            return response;
        } catch (MustAuthorizeException cause) {
            auditService.record(invokerId, AuditEventType.CATALOG_REQUEST_FAILURE, "failure", "Write with Custom File Name",
                    cause.getMessage(), cause.getClass().getSimpleName(), null);
            throw cause;
        } catch (Exception cause) {
            auditService.record(invokerId, AuditEventType.CATALOG_REQUEST_FAILURE, "failure", "Write with Custom File Name",
                    cause.getMessage(), cause.getClass().getSimpleName(), null);
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            throw new RuntimeException(cause);
        }
    }

    private void ensureUserAuthorized() {
        if (requestContext == null || !requestContext.invokeAsUser()) {
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
}
