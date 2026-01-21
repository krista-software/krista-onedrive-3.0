# Get Result

## Overview

Accepts a Task ID generated from List Files, List Folders, or Search By Permissions operations and returns the
corresponding list of files and folders.

## Request Details

| Property          | Value          |
|-------------------|----------------|
| **Area**          | Management     |
| **Type**          | WAIT_FOR_EVENT |
| **Retry Support** | No             |

## Input Parameters

| Parameter Name | Type | Required | Description                                                                                                                            |
|----------------|------|----------|----------------------------------------------------------------------------------------------------------------------------------------|
| Task ID        | Text | Yes      | The task identifier returned by operations like List Files or List Folders, used to retrieve the results of the asynchronous operation |

## Output Parameters

| Parameter Name | Type               | Description                                                           |
|----------------|--------------------|-----------------------------------------------------------------------|
| Files          | List<Entity(File)> | List of File entities representing the files and folders found        |
| Success        | Yes/No             | Indicates whether the file retrieval operation was successful         |
| Message        | Text               | Provides details about the operation result or any errors encountered |

### File Entity Fields

Each File entity in the list contains:

| Field         | Type      | Description                          |
|---------------|-----------|--------------------------------------|
| Name          | Text      | Name of the file including extension |
| Base Name     | Text      | Name without extension               |
| Extension     | Text      | File extension (e.g., .txt, .pdf)    |
| Absolute Path | Text      | Full path to the file in OneDrive    |
| Parent Folder | Text      | Path to the parent folder            |
| Size          | Number    | File size in bytes                   |
| Created       | Date/Time | When the file was created            |
| Modified      | Date/Time | When the file was last modified      |
| Owner         | Text      | File owner name                      |
| Is Folder     | Yes/No    | Whether the item is a folder         |

## Validation Rules

| Field   | Validation Rule                        | Error Message            |
|---------|----------------------------------------|--------------------------|
| Task ID | Must not be empty                      | "Task ID is required"    |
| Task ID | Must be valid UUID format              | "Invalid Task ID format" |
| Task ID | Must correspond to supported operation | "Invalid Input"          |

## Error Handling

### INPUT_ERROR

**When it occurs**: Invalid input parameters

**Common scenarios**:

- Empty Task ID
- Invalid Task ID format
- Task ID from unsupported operation

**Response**:

```json
{
  "Success": false,
  "Message": "Invalid Input",
  "Files": []
}
```

### LOGIC_ERROR

**When it occurs**: Business logic violations

**Common scenarios**:

- Task ID does not exist
- Task ID already consumed
- Source operation failed

**Response**:

```json
{
  "Success": false,
  "Message": "Task not found or already processed",
  "Files": []
}
```

### SYSTEM_ERROR

**When it occurs**: System-level failures during file retrieval

**Common scenarios**:

- Error during file listing
- Network issues during operation
- Timeout during large folder listing

**Response**:

```json
{
  "Success": false,
  "Message": "Error retrieving files: [details]",
  "Files": []
}
```

### Authorization Errors

**When it occurs**: Permission issues during file retrieval

**Common scenarios**:

- User lost access to folder during operation
- Some files not accessible

**Response**:

```json
{
  "Success": false,
  "Message": "Insufficient permissions to access files",
  "Files": []
}
```

## Usage Examples

### Example 1: Get Results from List Files

**Input**:

```
Task ID: a1b2c3d4-e5f6-7890-abcd-ef1234567890
```

**Output**:

```
Success: Yes
Message: File information
Files: [
  {
    "Name": "report.pdf",
    "Extension": ".pdf",
    "Absolute Path": "drive/root:/Documents/report.pdf",
    "Size": 2456789,
    "Is Folder": false
  },
  {
    "Name": "data.xlsx",
    "Extension": ".xlsx",
    "Absolute Path": "drive/root:/Documents/data.xlsx",
    "Size": 1234567,
    "Is Folder": false
  }
]
```

### Example 2: Get Results from List Folders

**Input**:

```
Task ID: 9876fedc-ba09-8765-4321-0fedcba98765
```

**Output**:

```
Success: Yes
Message: File information
Files: [
  {
    "Name": "Projects",
    "Absolute Path": "drive/root:/Documents/Projects",
    "Is Folder": true
  },
  {
    "Name": "Archive",
    "Absolute Path": "drive/root:/Documents/Archive",
    "Is Folder": true
  }
]
```

### Example 3: Empty Result

**Input**:

```
Task ID: xyz123abc-456d-789e-0123-456789abcdef
```

**Output**:

```
Success: Yes
Message: No files found
Files: []
```

## Business Rules

1. **Multiple Operation Support**: Supports Task IDs from List Files, List Folders, and Search By Permissions
2. **Task ID Consumption**: Task ID is consumed after successful retrieval
3. **Non-Recursive**: Returns only direct children, not nested files/folders
4. **Mixed Results**: List Files may return both files and folders
5. **Entity Format**: All results returned as File entities

## Limitations

- **Single Use**: Task ID can only be used once
- **No Partial Results**: Returns complete results or error
- **No Real-Time Updates**: Results are snapshot from operation time
- **No Pagination**: All results returned at once
- **Task ID Expiration**: Task IDs may expire if not used promptly

## Best Practices

1. **Use Immediately**: Call Get Result soon after initiating operation
2. **Wait for Completion**: Allow 2-5 seconds minimum after operation start
3. **Check Success Flag**: Always verify Success before processing Files list
4. **Handle Empty Lists**: Account for operations returning no files
5. **Process Large Lists**: Be prepared for large result sets
6. **Store Results**: Save file list if needed for later use
7. **Error Handling**: Implement retry logic for transient failures

## Common Use Cases

1. **File Discovery**: Find files in specific folders
2. **Folder Navigation**: List subfolders for navigation
3. **Search Results**: Retrieve search results by permissions
4. **Batch Processing**: Get file list for batch operations
5. **Reporting**: Generate file inventory reports
6. **Workflow Input**: Use file list as input to workflows
7. **Validation**: Verify expected files exist

## Related Catalog Requests

- [List Files](pages/ListFiles.md) - Initiate file listing operation
- [List Folders](pages/ListFolders.md) - Initiate folder listing operation
- [Search By Permissions](pages/SearchByPermissions.md) - Search files by permissions
- [Get Info Result](pages/GetInfoResult.md) - Get folder statistics

## Technical Implementation

### Helper Classes

- **EventHandler**: Manages asynchronous task completion
- **ManagementDaoImpl**: Performs file/folder listing
- **ResponseUtil**: Formats file entities

### Telemetry Metrics

The following metrics are tracked:

- `onedrive.management.get_result.success` - Successful retrievals
- `onedrive.management.get_result.failure` - Failed retrievals
- `onedrive.management.get_result.duration` - Time to retrieve results
- `onedrive.management.get_result.file_count` - Number of files returned

### Get Result Flow

1. Validate Task ID parameter
2. Check Task ID format (UUID)
3. Wait for event with matching Task ID
4. Retrieve file list from event data
5. Format files into File entities
6. Return file list in response

### Event-Driven Architecture

**Wait for Event**:

- Catalog request waits for event with matching Task ID
- Event triggered when source operation completes
- Event contains file list data

**Supported Source Operations**:

- List Files → Returns files and folders
- List Folders → Returns folders only
- Search By Permissions → Returns files matching permissions

## Troubleshooting

### Issue: "Invalid Input"

**Possible Causes**:

- Task ID format is incorrect
- Task ID from unsupported operation (e.g., Get Info)
- Typo in Task ID

**Solutions**:

1. Verify Task ID from List Files, List Folders, or Search By Permissions
2. Check Task ID is complete UUID format
3. Ensure no extra spaces or characters
4. Retry source operation to get new Task ID

### Issue: "Task not found or already processed"

**Possible Causes**:

- Task ID already used
- Task ID expired
- Source operation failed

**Solutions**:

1. Execute source operation again to get new Task ID
2. Use Task ID immediately after receiving it
3. Check source operation completed successfully

### Issue: Empty Files list but folder not empty

**Possible Causes**:

- Search filter excluded all files
- User lost access to files during operation
- Files were deleted during operation

**Solutions**:

1. Verify folder contents in OneDrive
2. Check search filter if used
3. Retry source operation
4. Review user permissions

### Issue: Timeout waiting for results

**Possible Causes**:

- Very large folder taking long to list
- Source operation failed
- Network issues during operation

**Solutions**:

1. Wait longer for large folders (up to 5 minutes)
2. Check source operation returned valid Task ID
3. Retry source operation
4. List smaller subfolders instead

## Task ID Sources

### List Files

**Returns**: Files and folders in specified path
**Use Case**: Get all items in a folder
**Example**: List all files in /Documents

### List Folders

**Returns**: Folders only in specified path
**Use Case**: Navigate folder structure
**Example**: List all subfolders in /Projects

### Search By Permissions

**Returns**: Files matching permission criteria
**Use Case**: Find files user can read/write
**Example**: Find all files with Write permission

## Processing Large Result Sets

### Small Result Sets (< 100 files)

- **Processing Time**: Instant
- **Memory Impact**: Minimal
- **Recommendation**: Process normally

### Medium Result Sets (100-1,000 files)

- **Processing Time**: 1-5 seconds
- **Memory Impact**: Moderate
- **Recommendation**: Process in batches if needed

### Large Result Sets (1,000-10,000 files)

- **Processing Time**: 5-30 seconds
- **Memory Impact**: Significant
- **Recommendation**: Process in batches, consider pagination

### Very Large Result Sets (> 10,000 files)

- **Processing Time**: 30+ seconds
- **Memory Impact**: High
- **Recommendation**: Break into smaller operations, use filtering

## File Entity Processing

### Filtering Files

```
Filter by extension:
  For each file in Files:
    If file.Extension = ".pdf":
      Process file
```

### Sorting Files

```
Sort by size:
  Sort Files by Size descending
```

### Grouping Files

```
Group by folder:
  For each file in Files:
    Group by file.Parent Folder
```

## Workflow Integration

### Example Workflow: Process All PDFs

1. Execute [List Files](pages/ListFiles.md) for /Documents
2. Receive Task ID
3. Execute Get Result with Task ID
4. Filter Files where Extension = ".pdf"
5. For each PDF file:
    - Execute [Download as File](pages/DownloadAsFile.md)
    - Process PDF content
    - Execute [Write](pages/Write.md) to save results

### Example Workflow: Folder Navigation

1. Execute [List Folders](pages/ListFolders.md) for current path
2. Receive Task ID
3. Execute Get Result with Task ID
4. Display folder list to user
5. User selects folder
6. Repeat for selected folder

## See Also

- [List Files](pages/ListFiles.md) - List files in folder
- [List Folders](pages/ListFolders.md) - List subfolders
- [Search By Permissions](pages/SearchByPermissions.md) - Search by permissions
- [Get Info Result](pages/GetInfoResult.md) - Get folder statistics

