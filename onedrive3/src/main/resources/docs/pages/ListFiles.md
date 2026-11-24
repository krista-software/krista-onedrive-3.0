# List Files

## Overview

Produces a non-recursive list of files that is present within the folder denoted by the source path and returns a Task
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

**Common scenarios**:

- Empty source path
- Invalid path format
- Malformed search pattern

**Response**:

```json
{
  "Task ID": null,
  "Error": "Invalid input: Source path is required"
}
```

### LOGIC_ERROR

**When it occurs**: Business logic violations

**Common scenarios**:

- Source folder does not exist
- User does not have access to folder

**Response**:

```json
{
  "Task ID": null,
  "Error": "Source folder not found"
}
```

### SYSTEM_ERROR

**When it occurs**: System-level failures

**Common scenarios**:

- Network connectivity issues
- Microsoft Graph API unavailable
- Internal extension error

**Response**:

```json
{
  "Task ID": null,
  "Error": "System error: [details]"
}
```

### Authorization Errors

**When it occurs**: Permission issues

**Common scenarios**:

- User does not have read permission on folder

**Response**:

```json
{
  "Task ID": null,
  "Error": "Insufficient permissions to access folder"
}
```

## Usage Examples

### Example 1: List All Files in Folder

**Input**:

```
Source Path: drive/root:/Documents
Search: 
```

**Output**:

```
Task ID: a1b2c3d4-e5f6-7890-abcd-ef1234567890
```

**Next Step**: Use this Task ID with [Get Result](pages/GetResult.md)

### Example 2: List Files with Search Filter

**Input**:

```
Source Path: drive/root:/Documents
Search: *.pdf
```

**Output**:

```
Task ID: 9876fedc-ba09-8765-4321-0fedcba98765
```

**Next Step**: Use this Task ID with [Get Result](pages/GetResult.md) to get filtered list

### Example 3: List Files in Shared Drive

**Input**:

```
Source Path: drives/b!absdbfbbvwevb/root:/TeamFiles
Search: report*
```

**Output**:

```
Task ID: xyz123abc-456d-789e-0123-456789abcdef
```

## Business Rules

1. **Non-Recursive**: Lists only direct children, not nested files
2. **Shared Drives Supported**: Works with both personal and shared drives
3. **Asynchronous Operation**: Returns Task ID immediately, processing happens in background
4. **Two-Step Process**: Requires List Files followed by Get Result
5. **Search Optional**: Can list all files or filter by search pattern
6. **Mixed Results**: May return both files and folders

## Limitations

- **Non-Recursive**: Does not list files in subfolders
- **No Direct Results**: Must use Get Result to retrieve file list
- **Processing Time**: Large folders may take time to list
- **No Progress Tracking**: Cannot track listing progress
- **Single Folder**: Can only list one folder at a time
- **No Pagination**: All results returned at once

## Best Practices

1. **Verify Folder Exists**: Use [List Folders](pages/ListFolders.md) to verify folder before listing
2. **Use Task ID Immediately**: Pass Task ID to Get Result without delay
3. **Use Search Filter**: Filter large folders with search pattern
4. **Handle Large Folders**: Be patient with large folder listings
5. **Check Permissions**: Ensure user has read access to folder
6. **Store Task ID**: Save Task ID for later retrieval if needed
7. **Error Handling**: Check for null Task ID indicating failure

## Common Use Cases

1. **File Discovery**: Find files in specific folders
2. **File Inventory**: Generate list of files for reporting
3. **Batch Processing**: Get file list for batch operations
4. **Search Files**: Find files matching specific patterns
5. **Workflow Input**: Use file list as input to workflows
6. **Validation**: Verify expected files exist
7. **File Management**: List files before cleanup or archiving

## Related Catalog Requests

- [Get Result](pages/GetResult.md) - Retrieve file list using Task ID
- [List Folders](pages/ListFolders.md) - List subfolders
- [Get Info](pages/GetInfo.md) - Get folder statistics
- [Search By Permissions](pages/SearchByPermissions.md) - Search by permissions

## Technical Implementation

### Helper Classes

- **ManagementDaoImpl**: Handles file listing logic
- **EventHandler**: Manages asynchronous task completion
- **FileUtil**: Validates search patterns
- **Microsoft Graph API**: Retrieves file metadata

### Telemetry Metrics

The following metrics are tracked:

- `onedrive.management.list_files.initiated` - List Files operations started
- `onedrive.management.list_files.duration` - Time to return Task ID

### List Files Operation Flow

1. Validate input parameters (source path, search)
2. Check source folder exists
3. Generate unique Task ID (UUID)
4. Start asynchronous file listing
5. Return Task ID immediately
6. Background process lists files:
    - Retrieve files from Microsoft Graph API
    - Apply search filter if provided
    - Format as File entities
7. Store results with Task ID
8. Trigger event when complete (2-second minimum delay)

### Microsoft Graph API Endpoint

```
GET /me/drive/root:/{path}:/children
```

**Query Parameters**:

- `$filter`: Applied if search pattern provided
- `$select`: Specifies fields to retrieve

## Troubleshooting

### Issue: Task ID is null

**Possible Causes**:

- Source folder does not exist
- Invalid source path format
- User does not have access to folder

**Solutions**:

1. Verify folder exists using [List Folders](pages/ListFolders.md)
2. Check path format is correct
3. Ensure user has read permission on folder
4. Review error message for specific issue

### Issue: "Source folder not found"

**Possible Causes**:

- Folder path does not exist
- Typo in folder path
- Folder was deleted

**Solutions**:

1. Use [List Folders](pages/ListFolders.md) to find correct path
2. Check for typos in path
3. Verify folder still exists in OneDrive

### Issue: Get Result returns no files

**Possible Causes**:

- Folder is empty
- Search filter excluded all files
- Processing not yet complete

**Solutions**:

1. Verify folder has files in OneDrive
2. Check search filter is not too restrictive
3. Wait a few moments for processing to complete
4. Retry List Files operation

### Issue: Get Result returns folders too

**Possible Causes**:

- This is expected behavior
- List Files returns both files and folders

**Solutions**:

1. Filter results by Is Folder = false
2. Use [List Folders](pages/ListFolders.md) if only folders needed
3. Process both files and folders as needed

## Search Pattern Examples

### Wildcard Patterns

| Pattern     | Matches                      | Example                         |
|-------------|------------------------------|---------------------------------|
| `*.pdf`     | All PDF files                | report.pdf, data.pdf            |
| `report*`   | Files starting with "report" | report.pdf, report_2024.xlsx    |
| `*2024*`    | Files containing "2024"      | report_2024.pdf, data_2024.xlsx |
| `file?.txt` | Files with single character  | file1.txt, fileA.txt            |

### Regular Expression Patterns

| Pattern   | Matches                      | Example                       |
|-----------|------------------------------|-------------------------------|
| `^report` | Files starting with "report" | report.pdf, report_final.docx |
| `\.pdf$`  | Files ending with .pdf       | document.pdf, report.pdf      |
| `[0-9]+`  | Files containing numbers     | file123.txt, report2024.pdf   |

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

## Performance Considerations

### Small Folders (< 100 files)

- **Processing Time**: 2-5 seconds
- **Impact**: Minimal

### Medium Folders (100-1,000 files)

- **Processing Time**: 5-15 seconds
- **Impact**: Moderate

### Large Folders (1,000-10,000 files)

- **Processing Time**: 15-60 seconds
- **Impact**: Significant

### Very Large Folders (10,000+ files)

- **Processing Time**: 1-5+ minutes
- **Impact**: High, consider using search filter

## Workflow Integration

### Example Workflow: Process All PDFs

1. Execute List Files for /Documents with Search = "*.pdf"
2. Receive Task ID
3. Execute [Get Result](pages/GetResult.md) with Task ID
4. For each file in Files:
    - Execute [Download as File](pages/DownloadAsFile.md)
    - Process PDF content

### Example Workflow: File Inventory Report

1. Execute List Files for each folder
2. Collect Task IDs
3. Execute [Get Result](pages/GetResult.md) for each Task ID
4. Aggregate file lists
5. Generate inventory report

## Comparison with List Folders

| Aspect        | List Files        | List Folders       |
|---------------|-------------------|--------------------|
| **Returns**   | Files and folders | Folders only       |
| **Use Case**  | Get all items     | Navigate structure |
| **Search**    | Supported         | Supported          |
| **Recursive** | No                | No                 |
| **Output**    | Task ID           | Task ID            |

## See Also

- [Get Result](pages/GetResult.md) - Retrieve file list
- [List Folders](pages/ListFolders.md) - List subfolders only
- [Get Info](pages/GetInfo.md) - Get folder statistics
- [Search By Permissions](pages/SearchByPermissions.md) - Search by permissions

