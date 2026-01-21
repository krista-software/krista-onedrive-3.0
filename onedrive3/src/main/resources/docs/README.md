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
- **Secure Authentication**: OAuth 2.0 authentication with Microsoft Azure AD
- **Retry Support**: Built-in retry mechanism for failed operations with validation

## Quick Start Guide

### 1. Prerequisites

Before using the OneDrive Files extension, you need:

- A Microsoft 365 account with OneDrive access
- Azure AD application registration with appropriate permissions
- Client ID, Client Secret, and Tenant ID from your Azure AD app

### 2. Initial Setup

1. **Register an Azure AD Application** - Follow the [Creating Microsoft App](pages/CreatingMicrosoftApp.md) guide
2. **Configure the Extension** - See [Extension Configuration](pages/ExtensionConfiguration.md) for setup parameters
3. **Authenticate** - Review [Authentication](pages/Authentication.md) to understand OAuth 2.0 flow
4. **Test Connection** - Use the Test Connection catalog request to verify setup

### 3. Start Using Catalog Requests

Once configured, you can use any of the 20+ catalog requests to manage your OneDrive files. See
the [Catalog Requests](#catalog-requests) section below for the complete list.

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

- **Extension Version**: 3.0.7
- **Java Version**: Java 21
- **API**: Microsoft Graph API v1.0

### Microsoft Graph API Scopes

This extension requires the following Microsoft Graph API permissions:

- `Files.ReadWrite.All` - Read and write access to all files
- `Sites.ReadWrite.All` - Read and write access to SharePoint sites
- `offline_access` - Maintain access to data

### Important Notes

- **File Size Limits**: Upload operations support files up to 50 MB
- **Path Formats**:
    - Personal drive: `drive/root:/path/to/file`
    - Shared drive: `drives/{driveId}/root:/path/to/file`
- **Async Operations**: Some operations return Task IDs for retrieving results later
- **Re-entry Support**: Most operations support re-entering corrected values on validation errors

### Common Use Cases

1. **Automated File Backup**: Upload files from Krista to OneDrive for backup
2. **Document Processing**: Download files, process them, and upload results
3. **File Sharing Workflows**: Automatically share files with team members
4. **Change Monitoring**: Trigger workflows when files are created or updated
5. **Folder Organization**: Automatically organize files into folders based on criteria

### Understanding Error Messages

This extension provides clear, actionable error messages to help you resolve issues quickly:

#### Common Error Types

**Path and File Errors**: These occur when files or folders can't be found
- File or folder doesn't exist at the specified path
- Invalid path format
- Missing permissions to access the location

**Authentication Errors**: These indicate issues with your Microsoft account connection
- Session expired - need to sign in again
- Invalid or missing credentials
- Insufficient permissions

**Service Errors**: These are temporary issues with Microsoft OneDrive
- OneDrive service temporarily unavailable
- Network connectivity problems
- File size exceeds limits (50MB for uploads)

**Validation Errors**: These occur when input doesn't meet requirements
- Invalid folder names (contains special characters: * : < > ? / \ |)
- File already exists at destination
- Empty or invalid paths

Each error message includes:
- A clear explanation of what went wrong
- Specific steps to resolve the issue
- Suggestions for alternative actions

### Getting Help

1. **Read the error message carefully** - It contains specific guidance on how to resolve the issue
2. Review the [Authentication](pages/Authentication.md) guide for connection issues
3. Check [Extension Configuration](pages/ExtensionConfiguration.md) for setup problems
4. See individual catalog request pages for operation-specific troubleshooting
5. Refer to [Microsoft Graph API Documentation](https://docs.microsoft.com/en-us/graph/api/resources/onedrive) for API
  details

### Release Notes

For version history and changes, see [Release Notes](pages/release_notes.md).

---

## Catalog Requests

The OneDrive Files extension provides 20+ catalog requests organized into three functional areas:

### Setup Area (3 requests)

Configuration and health monitoring operations

### Management Area (16 requests)

File and folder management operations

### OneDrive Area (2 requests)

Advanced OneDrive-specific operations

For detailed information about each catalog request, click on the links in
the [Documentation Structure](#documentation-structure) section above.

---

**Ready to get started?** Begin with [Extension Configuration](pages/ExtensionConfiguration.md) to set up your OneDrive
connection.

