# Get Info

## Overview

Accepts the source path of a folder and returns a Task ID, which is used with the Get Info Result catalog request to
retrieve comprehensive folder statistics.

## Request Details

| Property          | Value        |
|-------------------|--------------|
| **Area**          | Management   |
| **Type**          | QUERY_SYSTEM |
| **Retry Support** | No           |

## Input Parameters

| Parameter Name | Type | Required | Description                                                                                                                                                           |
|----------------|------|----------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Source Path    | Text | Yes      | Full path to the folder in the cloud storage to analyze (e.g., /Documents or /Sites/TeamSite/Shared Documents). For shared drives, use format driveId:/path/to/folder |

> 📌 **Root folder path:** To target your OneDrive **root folder**, enter **`drive/root:/`** as the path (leaving the field blank also works). For a subfolder, add it after — e.g. **`drive/root:/Documents`**. For a shared drive / SharePoint / Teams library, use the Drive Path (`drives/{driveId}/root:/…`) returned by [Get Drives](pages/GetDrives.md).

## Output Parameters

| Parameter Name | Type | Description                                                                                                                                           |
|----------------|------|-------------------------------------------------------------------------------------------------------------------------------------------------------|
| Task ID        | Text | Unique identifier for tracking this asynchronous operation. Use this ID with the Get Info Result operation to retrieve the complete folder statistics |

## Validation Rules

| Field       | Validation Rule              | Error Message                |
|-------------|------------------------------|------------------------------|
| Source Path | Must not be empty            | "Source path is required"    |
| Source Path | Must be valid OneDrive path  | "Invalid source path format" |
| Source Path | Folder must exist            | "Source folder not found"    |
| Source Path | Must be a folder, not a file | "Source must be a folder"    |

## Error Handling

### INPUT_ERROR

**When it occurs**: Invalid input parameters

**Common scenarios**:

- Empty source path
- Invalid path format
- Malformed path string

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
- Source path points to a file instead of folder
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
- Folder is protected or restricted

**Response**:

```json
{
  "Task ID": null,
  "Error": "Insufficient permissions to access folder"
}
```

## Usage Examples

### Example 1: Get Info for Personal Drive Folder

**Input**:

```
Source Path: drive/root:/Documents/Projects
```

**Output**:

```
Task ID: a1b2c3d4-e5f6-7890-abcd-ef1234567890
```

**Next Step**: Use this Task ID with [Get Info Result](pages/GetInfoResult.md)

### Example 2: Get Info for Shared Drive Folder

**Input**:

```
Source Path: drives/b!absdbfbbvwevb/root:/TeamFiles
```

**Output**:

```
Task ID: 9876fedc-ba09-8765-4321-0fedcba98765
```

**Next Step**: Use this Task ID with [Get Info Result](pages/GetInfoResult.md)

### Example 3: Get Info for Root Folder

**Input**:

```
Source Path: drive/root:/
```

**Output**:

```
Task ID: xyz123abc-456d-789e-0123-456789abcdef
```

## Business Rules

1. **Folders Only**: Get Info is applicable for folders only, not files
2. **Shared Drives Supported**: Supports both personal and shared drives
3. **Asynchronous Operation**: Returns Task ID immediately, processing happens in background
4. **Recursive Analysis**: Analyzes folder and all subfolders
5. **Two-Step Process**: Requires Get Info followed by Get Info Result

## Limitations

- **Folders Only**: Cannot get info for individual files
- **No Direct Results**: Must use Get Info Result to retrieve statistics
- **Processing Time**: Large folders may take time to analyze
- **No Progress Tracking**: Cannot track analysis progress
- **Single Folder**: Can only analyze one folder at a time

## Best Practices

1. **Verify Folder Exists**: Use [List Folders](pages/ListFolders.md) to verify folder before analysis
2. **Use Task ID Immediately**: Pass Task ID to Get Info Result without delay
3. **Handle Large Folders**: Be patient with large folder analysis
4. **Check Permissions**: Ensure user has read access to folder
5. **Store Task ID**: Save Task ID for later retrieval if needed
6. **Error Handling**: Check for null Task ID indicating failure

## Common Use Cases

1. **Folder Analytics**: Analyze folder size and file count
2. **Storage Management**: Identify large folders for cleanup
3. **Audit Reports**: Generate folder statistics for auditing
4. **Capacity Planning**: Assess storage usage patterns
5. **Data Governance**: Monitor folder growth over time
6. **Compliance Reporting**: Report on folder contents

## Related Catalog Requests

- [Get Info Result](pages/GetInfoResult.md) - Retrieve folder statistics using Task ID
- [List Folders](pages/ListFolders.md) - List subfolders
- [List Files](pages/ListFiles.md) - List files in folder

## Technical Implementation

### Helper Classes

- **ManagementDaoImpl**: Handles folder analysis logic
- **EventHandler**: Manages asynchronous task completion
- **Microsoft Graph API**: Retrieves folder metadata

### Telemetry Metrics

The following metrics are tracked:

- `onedrive.management.get_info.initiated` - Get Info operations started
- `onedrive.management.get_info.duration` - Time to return Task ID

### Get Info Operation Flow

1. Validate input parameter (source path)
2. Check source folder exists
3. Verify source is a folder, not a file
4. Generate unique Task ID (UUID)
5. Start asynchronous folder analysis
6. Return Task ID immediately
7. Background process analyzes folder:
    - Count files and folders
    - Calculate total size
    - Find oldest and newest files
    - Find last modified file
8. Store results with Task ID
9. Trigger event when complete

### Asynchronous Processing

**Immediate Response**:

- Task ID returned within seconds
- Analysis continues in background

**Background Analysis**:

- Recursively scans folder and subfolders
- Collects file metadata
- Calculates statistics
- Stores results for retrieval

**Minimum Delay**:

- 2-second delay before event trigger
- Ensures Get Info Result is ready to receive

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

### Issue: "Source must be a folder"

**Possible Causes**:

- Source path points to a file
- Path includes filename

**Solutions**:

1. Remove filename from path
2. Specify folder path only
3. Use [List Folders](pages/ListFolders.md) to get folder paths

### Issue: Get Info Result returns no data

**Possible Causes**:

- Task ID not used with Get Info Result
- Processing not yet complete
- Error during background analysis

**Solutions**:

1. Ensure Task ID is passed to [Get Info Result](pages/GetInfoResult.md)
2. Wait a few moments for processing to complete
3. Retry Get Info operation
4. Check for errors in Get Info Result response

## Task ID Management

### Task ID Format

- **Type**: UUID (Universally Unique Identifier)
- **Example**: `a1b2c3d4-e5f6-7890-abcd-ef1234567890`
- **Uniqueness**: Each Get Info operation generates unique Task ID

### Task ID Lifecycle

1. **Generation**: Created when Get Info is called
2. **Active**: Valid until Get Info Result retrieves data
3. **Completion**: Consumed by Get Info Result
4. **Expiration**: May expire if not used within reasonable time

### Best Practices

- **Use Immediately**: Pass to Get Info Result as soon as received
- **Don't Reuse**: Each Task ID is single-use
- **Store Temporarily**: Save Task ID only until results retrieved
- **Handle Errors**: Check for null Task ID before using

## Folder Statistics Collected

The background analysis collects the following statistics (retrieved via Get Info Result):

| Statistic                      | Description                                    |
|--------------------------------|------------------------------------------------|
| Files                          | Total number of files in folder and subfolders |
| Folders                        | Total number of subfolders                     |
| Size                           | Total size of all files in bytes               |
| Oldest File                    | Name of oldest file by creation date           |
| Oldest File Create Date        | Creation date of oldest file                   |
| Newest File                    | Name of newest file by creation date           |
| Newest File Date               | Creation date of newest file                   |
| Last Modified File Id          | ID of most recently modified file              |
| Last Modified File Name        | Name of most recently modified file            |
| Last Modified File Create Date | Last modification date                         |

## Path Format Reference

### Personal Drive Paths

- **Root**: `drive/root:/`
- **Folder**: `drive/root:/Documents`
- **Subfolder**: `drive/root:/Documents/Projects`

### Shared Drive Paths

- **Format**: `drives/b!{driveId}/root:/path/to/folder`
- **Example**: `drives/b!absdbfbbvwevb/root:/TeamFiles`

### Relative Paths (Personal Drive)

- **Folder**: `/Documents`
- **Subfolder**: `/Documents/Projects`

## Performance Considerations

### Small Folders (< 100 files)

- **Processing Time**: 2-5 seconds
- **Impact**: Minimal

### Medium Folders (100-1000 files)

- **Processing Time**: 5-15 seconds
- **Impact**: Moderate

### Large Folders (1000+ files)

- **Processing Time**: 15-60+ seconds
- **Impact**: Significant, be patient

### Very Large Folders (10,000+ files)

- **Processing Time**: 1-5+ minutes
- **Impact**: High, consider breaking into smaller analyses

## See Also

- [Get Info Result](pages/GetInfoResult.md) - Retrieve folder statistics
- [List Files](pages/ListFiles.md) - List files in folder
- [List Folders](pages/ListFolders.md) - List subfolders
- [Get Result](pages/GetResult.md) - Retrieve file list results

