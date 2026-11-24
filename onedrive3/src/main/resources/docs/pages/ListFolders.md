# List Folders

## Overview

Produces a non-recursive list of folders that is present within the folder denoted by the source path and returns a Task
ID for retrieving results.

## Request Details

| Property          | Value        |
|-------------------|--------------|
| **Area**          | Management   |
| **Type**          | QUERY_SYSTEM |
| **Retry Support** | No           |

## Input Parameters

| Parameter Name | Type | Required | Description                                                                                                                                                                   |
|----------------|------|----------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Source Path    | Text | Yes      | Full path to the folder in the cloud storage to list files from (e.g., /Documents or /Sites/TeamSite/Shared Documents). For shared drives, use format driveId:/path/to/folder |
| Search         | Text | No       | Optional search pattern to filter files by name (supports wildcards and regular expressions)                                                                                  |

> 📌 **Root folder path:** To target your OneDrive **root folder**, enter **`drive/root:/`** as the path (leaving the field blank also works). For a subfolder, add it after — e.g. **`drive/root:/Documents`**. For a shared drive / SharePoint / Teams library, use the Drive Path (`drives/{driveId}/root:/…`) returned by [Get Drives](pages/GetDrives.md).

## Output Parameters

| Parameter Name | Type | Description                                                                                                                     |
|----------------|------|---------------------------------------------------------------------------------------------------------------------------------|
| Task ID        | Text | Unique identifier for tracking this asynchronous operation. Use this ID with the Get Result operation to retrieve the file list |

## Validation Rules

| Field       | Validation Rule             | Error Message                |
|-------------|-----------------------------|------------------------------|
| Source Path | Must not be empty           | "Source path is required"    |
| Source Path | Must be valid OneDrive path | "Invalid source path format" |
| Source Path | Folder must exist           | "Source folder not found"    |

## Error Handling

### INPUT_ERROR

**When it occurs**: Invalid input parameters
**Common scenarios**: Empty source path, invalid path format, malformed search pattern
**Response**: `{"Task ID": null, "Error": "Invalid input: Source path is required"}`

### LOGIC_ERROR

**When it occurs**: Business logic violations
**Common scenarios**: Source folder does not exist, user does not have access to folder
**Response**: `{"Task ID": null, "Error": "Source folder not found"}`

### SYSTEM_ERROR

**When it occurs**: System-level failures
**Common scenarios**: Network connectivity issues, Microsoft Graph API unavailable
**Response**: `{"Task ID": null, "Error": "System error: [details]"}`

### Authorization Errors

**When it occurs**: Permission issues
**Common scenarios**: User does not have read permission on folder
**Response**: `{"Task ID": null, "Error": "Insufficient permissions to access folder"}`

## Usage Examples

### Example 1: List All Subfolders

**Input**: `Source Path: drive/root:/Documents, Search: `
**Output**: `Task ID: a1b2c3d4-e5f6-7890-abcd-ef1234567890`

### Example 2: List Folders with Filter

**Input**: `Source Path: drive/root:/Projects, Search: 2024*`
**Output**: `Task ID: 9876fedc-ba09-8765-4321-0fedcba98765`

## Business Rules

1. **Folders Only**: Returns only folders, not files
2. **Non-Recursive**: Lists only direct subfolders
3. **Asynchronous Operation**: Returns Task ID immediately
4. **Two-Step Process**: Requires List Folders followed by Get Result
5. **Search Optional**: Can list all folders or filter by pattern

## Limitations

- **Non-Recursive**: Does not list nested subfolders
- **No Direct Results**: Must use Get Result to retrieve folder list
- **No Pagination**: All results returned at once

## Best Practices

1. Use Task ID immediately with [Get Result](pages/GetResult.md)
2. Use search filter for large directories
3. Check for null Task ID indicating failure
4. Verify folder exists before listing

## Common Use Cases

1. Folder navigation and browsing
2. Directory structure analysis
3. Subfolder discovery for batch operations
4. Folder inventory reporting

## Related Catalog Requests

- [Get Result](pages/GetResult.md) - Retrieve folder list using Task ID
- [List Files](pages/ListFiles.md) - List files in folder
- [Get Info](pages/GetInfo.md) - Get folder statistics

## Technical Implementation

**Helper Classes**: ManagementDaoImpl, EventHandler, FileUtil
**Telemetry Metrics**: `onedrive.management.list_folders.initiated`, `onedrive.management.list_folders.duration`
**API Endpoint**: `GET /me/drive/root:/{path}:/children?$filter=folder ne null`

## Troubleshooting

**Issue: Task ID is null** - Verify folder exists, check path format, ensure user has read permission
**Issue: Get Result returns no folders** - Folder may be empty, check search filter, wait for processing
**Issue: Get Result returns files too** - Filter results by Is Folder = true

## Path Format Reference

### Personal OneDrive (your own drive)

Use the shorthand format directly — no Get Drives call required:

| Location | Path Format | Example |
|---|---|---|
| Root of personal drive | `drive/root:/` | `drive/root:/` |
| Folder in personal drive | `drive/root:/FolderName` | `drive/root:/Documents` |
| Subfolder | `drive/root:/Folder/Subfolder` | `drive/root:/Documents/Projects` |

> `drive/root:/` (singular `drive`, no drive ID) always refers to the authenticated user's own personal OneDrive.

### SharePoint Document Libraries and Shared Drives

For SharePoint document libraries or any shared drive, call **[Get Drives](pages/GetDrives.md)** first to obtain the Drive Path, then use it directly as the Source Path:

| Location | Path Format | Example |
|---|---|---|
| Root of a shared drive | `drives/{driveId}/root:/` | `drives/b!abc123XYZ/root:/` |
| Folder inside a shared drive | `drives/{driveId}/root:/FolderName` | `drives/b!abc123XYZ/root:/TeamFiles` |
| Subfolder inside a shared drive | `drives/{driveId}/root:/Folder/Subfolder` | `drives/b!abc123XYZ/root:/TeamFiles/2024` |

> Always use the Drive Path exactly as returned by Get Drives. Do not manually construct or guess the `{driveId}` value.

### Quick Reference

| Storage Type | Source Path Example |
|---|---|
| Personal OneDrive | `drive/root:/Documents` |
| SharePoint / Shared Drive | `drives/b!abc123XYZ/root:/TeamFiles` |

## See Also

- [Get Result](pages/GetResult.md) - Retrieve folder list
- [List Files](pages/ListFiles.md) - List files and folders
- [Get Info](pages/GetInfo.md) - Get folder statistics
- [Get Drives](pages/GetDrives.md) - Discover SharePoint and shared drive paths

