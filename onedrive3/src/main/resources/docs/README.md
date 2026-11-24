# Krista OneDrive Files Extension

## Overview

The Krista OneDrive Files Extension enables seamless integration with Microsoft OneDrive, allowing you to manage files
and folders in your OneDrive storage directly from Krista workflows. This extension provides comprehensive file
operations including upload, download, copy, move, delete, share, and real-time change notifications.

## Key Features

- **File Management**: Upload, download, copy, move, rename, and delete files and folders
- **Search & Query**: Search files by permissions, list files and folders, get folder statistics
- **File Sharing**: Share files with specific users and permission levels
- **Change Notifications**: Monitor folders for file changes in real-time
- **Folder Analytics**: Get detailed information about folder contents and statistics
- **Batch Operations**: Perform operations on multiple files efficiently
- **Secure Authentication**: OAuth 2.0 with three flexible modes (Quick Setup, User Auth, Server-Server)
- **Retry Support**: Built-in retry mechanism for failed operations with validation

## Quick Start Guide

### 1. Choose Authentication Mode

#### Quick Setup *(Recommended)*
- Krista manages Microsoft app credentials on your behalf
- No Azure AD app registration required
- Just provide your Microsoft 365 email address and sign in

#### User Authentication (Own Azure AD App)
- Your organization provides its own Azure AD app registration
- Full control over permissions and redirect URIs
- Required for compliance-constrained environments

#### Server to Server Authentication
- No user sign-in — app authenticates directly with Microsoft
- For automated workflows, background jobs, and service integrations

### 2. Setup Steps

#### For Quick Setup:
1. Open the **Authentication** tab of the OneDrive extension
2. Enter your **Microsoft 365 email address**
3. Click **Authorize** and sign in with your Microsoft account
4. Click **Test Connection** to verify

#### For User Authentication or Server-Server:
1. **Create Azure AD Application** — Follow the [Creating Microsoft App](pages/CreatingMicrosoftApp.md) guide
2. Open the **Authentication** tab and click **Advanced Setup**
3. Fill in your credentials and click **Save Changes**
4. For User Auth: click **Authorize** to complete the OAuth sign-in
5. Click **Test Connection** to verify

See the full [Authentication Tab Guide](pages/Authentication.md) for detailed setup instructions.

### 3. Monitor with the Audit Tab

The **OneDrive Audit** tab provides a real-time dashboard for all operations:
- Tracks success/failure per catalog request with execution logs
- Shows token health, client secret expiry status, and auth event history
- Displays file change notification counts and subscription status
- Full request history with duration and triggered-by user

## Documentation Structure

### Getting Started

- [Extension Configuration](pages/ExtensionConfiguration.md) - Setup parameters and configuration steps
- [Authentication](pages/Authentication.md) - OAuth 2.0 authentication flow and token management
- [Creating Microsoft App](pages/CreatingMicrosoftApp.md) - Step-by-step guide to register Azure AD application

### Setup Operations

- [Save Configuration](pages/SaveConfiguration.md) - Validate and save connection credentials
- [Test Connection](pages/TestConnection.md) - Verify OneDrive connectivity
- [Health Check](pages/HealthCheck.md) - Check extension health status

### File Operations

- [Copy](pages/Copy.md) - Copy files and folders to target location
- [Copy With Name](pages/CopyWithName.md) - Copy files with a specified target name
- [Delete](pages/Delete.md) - Delete files and folders
- [Download as Link](pages/DownloadAsLink.md) - Get download link for files
- [Download as File](pages/DownloadAsFile.md) - Download files as Krista File objects
- [Move](pages/Move.md) - Move files and folders to new location
- [Read](pages/Read.md) - Read text file contents
- [Rename](pages/Rename.md) - Rename files and folders
- [Write](pages/Write.md) - Upload files to OneDrive
- [Write Text](pages/WriteText.md) - Upload text content as file
- [Write with Custom File Name](pages/WriteWithCustomFileName.md) - Upload with custom filename

### Folder Operations

- [Create Folder](pages/CreateFolder.md) - Create new folders
- [Get Drives](pages/GetDrives.md) - List all accessible drives (personal, business, SharePoint libraries)
- [Get Info](pages/GetInfo.md) - Get folder statistics (async)
- [Get Info Result](pages/GetInfoResult.md) - Retrieve folder statistics results
- [List Files](pages/ListFiles.md) - List files in a folder (async)
- [List Folders](pages/ListFolders.md) - List subfolders in a folder (async)

### Query Operations

- [Get Result](pages/GetResult.md) - Retrieve results from async operations
- [Search By Permissions](pages/SearchByPermissions.md) - Find files by permission level

### Sharing Operations

- [Share File](pages/ShareFile.md) - Share files with users

### Change Tracking

- [Changes Since](pages/ChangesSince.md) - Get file changes since a specific date
- [When a File is Changed](pages/WhenAFileIsChanged.md) - Monitor folder for changes (event-driven)

## Support & Resources

### Version Information

- **Extension Version**: 3.0.9
- **Java Version**: Java 21
- **API**: Microsoft Graph API v1.0

### Microsoft Graph API Scopes

This extension requires the following Microsoft Graph API permissions:

- `Files.ReadWrite.All` - Read and write access to all files
- `Sites.ReadWrite.All` - Read and write access to SharePoint sites
- `User.Read` - Sign in and read the user profile
- `offline_access` - Maintain access via refresh token (delegated auth only)

### Important Notes

- **File Size Limits**: Upload operations support files up to 50 MB
- **Path Formats**:
    - Personal OneDrive (no Get Drives needed): `drive/root:/Documents/subfolder`
    - SharePoint / Shared drive (use [Get Drives](pages/GetDrives.md) first to obtain the path): `drives/{driveId}/root:/FolderName`
    - Key difference: `drive/root:/` (singular, no ID) = your own OneDrive; `drives/{id}/root:/` (plural, with drive ID) = SharePoint or any shared storage
- **Async Operations**: Some operations (List Files, List Folders, Get Info, Search By Permissions) return a Task ID — use the **Get Result** operation to retrieve results
- **Re-entry Support**: Most operations support re-entering corrected values on validation errors

### Common Use Cases

1. **Automated File Backup**: Upload files from Krista to OneDrive for backup
2. **Document Processing**: Download files, process them, and upload results
3. **File Sharing Workflows**: Automatically share files with team members
4. **Change Monitoring**: Trigger workflows when files are created or updated
5. **Folder Organization**: Automatically organize files into folders based on criteria

### Authentication Quick Reference

| Authentication Mode | Azure App Required | Sign-in Required | Best For |
|---|---|---|---|
| Quick Setup | No | Yes (via Krista's app) | Individual users, quick start |
| User Authentication | Yes | Yes (via your app) | Organization compliance, custom permissions |
| Server to Server | Yes | No | Automation, background jobs, schedulers |

### Understanding Error Messages

This extension provides clear, actionable error messages to help you resolve issues quickly:

**Path and File Errors**: File or folder not found, invalid path format, insufficient permissions

**Authentication Errors**: Session expired (click Authorize to re-authenticate), invalid credentials, `AADSTS500112` redirect URI mismatch (re-save credentials in the Authentication tab)

**Service Errors**: OneDrive temporarily unavailable, network issues, file size exceeds 50 MB limit

**Validation Errors**: Invalid folder names (characters `* : < > ? / \ |`), file already exists, empty paths

Each error message includes a clear explanation and specific steps to resolve the issue.

### Additional Resources

- [Authentication Tab Guide](pages/Authentication.md) - Detailed auth setup for all three modes
- [Release Notes](pages/release_notes.md) - Version history and changes
- [Microsoft Graph API Documentation](https://docs.microsoft.com/en-us/graph/api/resources/onedrive) - API reference

---

## Catalog Requests

The OneDrive Files extension provides 20+ catalog requests organized into three functional areas:

### Setup Area (3 requests)

Configuration and health monitoring operations

### Management Area (19 requests)

File and folder management operations

### OneDrive Area (2 requests)

Advanced OneDrive-specific operations including permission search and custom file name uploads

For detailed information about each catalog request, click on the links in
the [Documentation Structure](#documentation-structure) section above.

---

**Ready to get started?** Open the **Authentication** tab in the extension to configure your OneDrive connection.
