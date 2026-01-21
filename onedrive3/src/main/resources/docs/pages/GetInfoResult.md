# Get Info Result

## Overview

Accepts a Task ID from the Get Info catalog request and retrieves comprehensive statistics about a folder including file
counts, sizes, and metadata.

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

| Parameter Name                 | Type   | Description                                                                  |
|--------------------------------|--------|------------------------------------------------------------------------------|
| Files                          | Number | Total number of files found in the folder and its subfolders                 |
| Folders                        | Number | Total number of subfolders found in the folder                               |
| Size                           | Number | Total size of all files in the folder and its subfolders, in bytes           |
| Oldest File                    | Text   | Name of the oldest file in the folder based on creation date                 |
| Oldest File Create Date        | Date   | Creation timestamp of the oldest file in the folder                          |
| Newest File                    | Text   | Name of the newest file in the folder based on creation date                 |
| Newest File Date               | Date   | Creation timestamp of the newest file in the folder                          |
| Last Modified File Id          | Text   | Unique identifier of the most recently modified file in the folder           |
| Last Modified File Name        | Text   | Name of the most recently modified file in the folder                        |
| Last Modified File Create Date | Date   | Last modification timestamp of the most recently modified file in the folder |
| Message                        | Text   | Provides details about the operation result or any errors encountered        |
| Success                        | Yes/No | Indicates whether the folder information retrieval was successful            |

## Validation Rules

| Field   | Validation Rule                       | Error Message            |
|---------|---------------------------------------|--------------------------|
| Task ID | Must not be empty                     | "Task ID is required"    |
| Task ID | Must be valid UUID format             | "Invalid Task ID format" |
| Task ID | Must correspond to Get Info operation | "Invalid Task ID"        |

## Error Handling

### INPUT_ERROR

**When it occurs**: Invalid input parameters

**Common scenarios**:

- Empty Task ID
- Invalid Task ID format
- Task ID from wrong operation type

**Response**:

```json
{
  "Success": false,
  "Message": "Invalid Task ID",
  "Files": 0,
  "Folders": 0,
  "Size": 0
}
```

### LOGIC_ERROR

**When it occurs**: Business logic violations

**Common scenarios**:

- Task ID does not exist
- Task ID already consumed
- Get Info operation failed

**Response**:

```json
{
  "Success": false,
  "Message": "Task not found or already processed",
  "Files": 0,
  "Folders": 0,
  "Size": 0
}
```

### SYSTEM_ERROR

**When it occurs**: System-level failures during folder analysis

**Common scenarios**:

- Error during folder scanning
- Network issues during analysis
- Timeout during large folder analysis

**Response**:

```json
{
  "Success": false,
  "Message": "Error analyzing folder: [details]",
  "Files": 0,
  "Folders": 0,
  "Size": 0
}
```

### Authorization Errors

**When it occurs**: Permission issues during folder analysis

**Common scenarios**:

- User lost access to folder during analysis
- Some files/folders not accessible

**Response**:

```json
{
  "Success": false,
  "Message": "Insufficient permissions to analyze folder",
  "Files": 0,
  "Folders": 0,
  "Size": 0
}
```

## Usage Examples

### Example 1: Successful Folder Analysis

**Input**:

```
Task ID: a1b2c3d4-e5f6-7890-abcd-ef1234567890
```

**Output**:

```
Files: 127
Folders: 15
Size: 45678912
Oldest File: project_plan.docx
Oldest File Create Date: 2023-01-15T10:30:00Z
Newest File: status_report.pdf
Newest File Date: 2024-10-10T14:25:00Z
Last Modified File Id: abc123xyz
Last Modified File Name: budget.xlsx
Last Modified File Create Date: 2024-10-12T09:15:00Z
Message: Folder information fetch successfully
Success: Yes
```

### Example 2: Empty Folder

**Input**:

```
Task ID: 9876fedc-ba09-8765-4321-0fedcba98765
```

**Output**:

```
Files: 0
Folders: 0
Size: 0
Oldest File: 
Oldest File Create Date: 
Newest File: 
Newest File Date: 
Last Modified File Id: 
Last Modified File Name: 
Last Modified File Create Date: 
Message: Folder is empty
Success: Yes
```

### Example 3: Invalid Task ID

**Input**:

```
Task ID: invalid-task-id
```

**Output**:

```
Success: No
Message: Invalid Task ID
Files: 0
Folders: 0
Size: 0
```

## Business Rules

1. **Get Info Prerequisite**: Must be called after [Get Info](pages/GetInfo.md) operation
2. **Task ID Consumption**: Task ID is consumed after successful retrieval
3. **Shared Drives Supported**: Supports folders from both personal and shared drives
4. **Folders Only**: Only works with Task IDs from Get Info (folder analysis)
5. **Recursive Statistics**: Includes all files and subfolders recursively

## Limitations

- **Single Use**: Task ID can only be used once
- **No Partial Results**: Returns complete results or error, no partial data
- **No Real-Time Updates**: Statistics are snapshot from analysis time
- **No Filtering**: Cannot filter results by file type or date range
- **Task ID Expiration**: Task IDs may expire if not used promptly

## Best Practices

1. **Use Immediately**: Call Get Info Result soon after Get Info
2. **Wait for Completion**: Allow 2-5 seconds minimum after Get Info
3. **Check Success Flag**: Always verify Success before using statistics
4. **Handle Empty Folders**: Account for folders with zero files
5. **Convert Size**: Convert size from bytes to MB/GB for display
6. **Store Results**: Save statistics if needed for later use
7. **Error Handling**: Implement retry logic for transient failures

## Common Use Cases

1. **Storage Reports**: Generate folder size and file count reports
2. **Cleanup Planning**: Identify folders for cleanup based on size
3. **Audit Compliance**: Document folder contents for audits
4. **Capacity Planning**: Analyze storage usage patterns
5. **Data Governance**: Monitor folder growth and file age
6. **Archive Decisions**: Identify old files for archiving

## Related Catalog Requests

- [Get Info](pages/GetInfo.md) - Initiate folder analysis and get Task ID
- [List Files](pages/ListFiles.md) - List individual files in folder
- [List Folders](pages/ListFolders.md) - List subfolders
- [Get Result](pages/GetResult.md) - Get file list from List Files/Folders

## Technical Implementation

### Helper Classes

- **ResponseUtil**: Processes event data and formats response
- **EventHandler**: Manages asynchronous task completion
- **ManagementDaoImpl**: Performs folder analysis

### Telemetry Metrics

The following metrics are tracked:

- `onedrive.management.get_info_result.success` - Successful retrievals
- `onedrive.management.get_info_result.failure` - Failed retrievals
- `onedrive.management.get_info_result.duration` - Time to retrieve results

### Get Info Result Flow

1. Validate Task ID parameter
2. Check Task ID format (UUID)
3. Wait for event with matching Task ID
4. Retrieve folder statistics from event data
5. Format statistics into response
6. Return comprehensive folder information

### Event-Driven Architecture

**Wait for Event**:

- Catalog request waits for event with matching Task ID
- Event triggered when Get Info analysis completes
- Event contains folder statistics

**Event Data Structure**:

```json
{
  "Files": 127,
  "Folders": 15,
  "Size": 45678912,
  "Oldest File": "project_plan.docx",
  "Oldest File Create Date": "2023-01-15T10:30:00Z",
  "Newest File": "status_report.pdf",
  "Newest File Date": "2024-10-10T14:25:00Z",
  "Last Modified File Id": "abc123xyz",
  "Last Modified File Name": "budget.xlsx",
  "Last Modified File Create Date": "2024-10-12T09:15:00Z",
  "Message": "Folder information fetch successfully",
  "Success": true
}
```

## Troubleshooting

### Issue: "Invalid Task ID"

**Possible Causes**:

- Task ID format is incorrect
- Task ID from wrong operation (not Get Info)
- Typo in Task ID

**Solutions**:

1. Verify Task ID was obtained from [Get Info](pages/GetInfo.md)
2. Check Task ID is complete UUID format
3. Ensure no extra spaces or characters
4. Retry Get Info to get new Task ID

### Issue: "Task not found or already processed"

**Possible Causes**:

- Task ID already used
- Task ID expired
- Get Info operation failed

**Solutions**:

1. Execute [Get Info](pages/GetInfo.md) again to get new Task ID
2. Use Task ID immediately after receiving it
3. Check Get Info completed successfully

### Issue: Results show zero files but folder not empty

**Possible Causes**:

- User lost access to files during analysis
- Files were deleted during analysis
- Permission issues prevented file counting

**Solutions**:

1. Verify user still has access to folder
2. Check folder contents in OneDrive
3. Retry Get Info operation
4. Review user permissions

### Issue: Timeout waiting for results

**Possible Causes**:

- Very large folder taking long to analyze
- Get Info operation failed
- Network issues during analysis

**Solutions**:

1. Wait longer for large folders (up to 5 minutes)
2. Check Get Info returned valid Task ID
3. Retry Get Info operation
4. Analyze smaller subfolders instead

## Size Conversion Reference

The Size field is returned in bytes. Use these conversions for display:

| Bytes         | Kilobytes (KB) | Megabytes (MB) | Gigabytes (GB) |
|---------------|----------------|----------------|----------------|
| 1,024         | 1 KB           | 0.001 MB       | 0.000001 GB    |
| 1,048,576     | 1,024 KB       | 1 MB           | 0.001 GB       |
| 1,073,741,824 | 1,048,576 KB   | 1,024 MB       | 1 GB           |

**Example Conversions**:

- 45,678,912 bytes = 43.56 MB
- 1,234,567,890 bytes = 1.15 GB

## Statistics Interpretation

### File Count Analysis

- **Low (< 100 files)**: Small folder, quick to manage
- **Medium (100-1,000 files)**: Moderate folder, consider organization
- **High (1,000-10,000 files)**: Large folder, may need subfolder organization
- **Very High (> 10,000 files)**: Very large folder, consider restructuring

### Size Analysis

- **Small (< 100 MB)**: Minimal storage impact
- **Medium (100 MB - 1 GB)**: Moderate storage usage
- **Large (1-10 GB)**: Significant storage, monitor growth
- **Very Large (> 10 GB)**: High storage usage, consider archiving

### File Age Analysis

- **Oldest File**: Indicates folder age and data retention
- **Newest File**: Shows recent activity
- **Last Modified**: Indicates most recent changes

## Workflow Integration

### Example Workflow: Storage Cleanup

1. Execute [Get Info](pages/GetInfo.md) for target folder
2. Receive Task ID
3. Execute Get Info Result with Task ID
4. Analyze Size and Files count
5. If Size > threshold:
    - Execute [List Files](pages/ListFiles.md)
    - Identify large or old files
    - Execute [Delete](pages/Delete.md) for selected files

### Example Workflow: Audit Report

1. Execute [Get Info](pages/GetInfo.md) for each folder
2. Collect Task IDs
3. Execute Get Info Result for each Task ID
4. Aggregate statistics
5. Generate report with:
    - Total files across all folders
    - Total size
    - Oldest and newest files
    - Folders needing attention

## See Also

- [Get Info](pages/GetInfo.md) - Initiate folder analysis
- [Get Result](pages/GetResult.md) - Get file lists from async operations
- [List Files](pages/ListFiles.md) - List files in folder
- [List Folders](pages/ListFolders.md) - List subfolders

