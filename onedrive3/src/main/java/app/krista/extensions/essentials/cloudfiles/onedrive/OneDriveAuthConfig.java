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

package app.krista.extensions.essentials.cloudfiles.onedrive;

import app.krista.sdk.msauth.config.AuthFlow;
import app.krista.sdk.msauth.extension.annotations.*;

@MsAuthExtension(
        extensionType = "onedrive",
        callbackPathPrefix = "/onedrive",
        supportedFlows = {AuthFlow.AUTH_CODE_PUBLIC, AuthFlow.AUTH_CODE_PRIVATE, AuthFlow.CLIENT_CREDENTIALS_SECRET, AuthFlow.CLIENT_CREDENTIALS_CERT},
        defaultScopes = {"openid", "offline_access", "Files.ReadWrite.All", "Sites.ReadWrite.All", "User.Read"},
        headerTitle = "OneDrive Authentication",
        headerSubtitle = "Configure how Krista connects to your Microsoft OneDrive",
        showScopePanel = true,
        supportsOwnCredentials = true,
        enableAuditDashboard = true,
        setupGuideUrl = "../../../static/docs/#/pages/overview"
)
@AuthScope(value = "Files.ReadWrite.All", description = "Read and write all files", required = true,
        catalogRequests = {"Upload File", "Download File", "Delete File", "Copy File",
                "Move File", "Create Folder", "Get File Info", "List Files",
                "Get File Versions", "Share File", "Search Files"})
@AuthScope(value = "Sites.ReadWrite.All", description = "Read and write items in all site collections",
        catalogRequests = {"List Shared Files", "Access Shared Drives"})
@AuthScope(value = "User.Read", description = "Read user profile",
        catalogRequests = {"Test Connection", "Health Check"})
@AuditConfig(
        dashboardTitle = "OneDrive Audit Dashboard",
        enableCredentialHealth = true,
        enableCharts = true,
        csvExportPrefix = "onedrive-audit"
)
@AuditEventGroup(name = "File Events", events = {
        @AuditEvent(type = "FILE_UPLOADED", description = "File uploaded to OneDrive"),
        @AuditEvent(type = "FILE_DOWNLOADED", description = "File downloaded from OneDrive"),
        @AuditEvent(type = "FILE_DELETED", description = "File deleted from OneDrive"),
        @AuditEvent(type = "FILE_COPIED", description = "File copied within OneDrive"),
        @AuditEvent(type = "FILE_MOVED", description = "File moved within OneDrive"),
        @AuditEvent(type = "FOLDER_CREATED", description = "Folder created in OneDrive")
})
public class OneDriveAuthConfig {
}
