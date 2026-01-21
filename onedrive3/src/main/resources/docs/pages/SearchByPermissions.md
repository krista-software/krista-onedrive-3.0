# Search By Permissions

## Overview

Accepts a permission level and returns a Task ID for retrieving files that the user has the specified permission level
on.

## Request Details

| Property          | Value        |
|-------------------|--------------|
| **Area**          | OneDrive     |
| **Type**          | QUERY_SYSTEM |
| **Retry Support** | No           |

## Input Parameters

| Parameter Name | Type    | Required | Description                                                                                                                                |
|----------------|---------|----------|--------------------------------------------------------------------------------------------------------------------------------------------|
| Permission     | PickOne | Yes      | The permission level to search for. Select from Read (view only), Write (edit only), Read and Write (view and edit), or Own (full control) |

## Output Parameters

| Parameter Name | Type | Description                                                                                                                                                 |
|----------------|------|-------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Task ID        | Text | Unique identifier for tracking this asynchronous operation. Use this ID with the Get Result operation to retrieve files with the specified permission level |

## Validation Rules

| Field      | Validation Rule                             | Error Message             |
|------------|---------------------------------------------|---------------------------|
| Permission | Must not be empty                           | "Permission is required"  |
| Permission | Must be Read, Write, Read and Write, or Own | "Invalid permission type" |

## Error Handling

### INPUT_ERROR

**When it occurs**: Invalid input parameters
**Common scenarios**: Empty permission, invalid permission type
**Response**: `{"Task ID": null, "Error": "Invalid input: Permission is required"}`

### LOGIC_ERROR

**When it occurs**: Business logic violations
**Common scenarios**: User has no files with specified permission
**Response**: Task ID returned, but Get Result returns empty list

### SYSTEM_ERROR

**When it occurs**: System-level failures
**Common scenarios**: Network issues, Microsoft Graph API unavailable
**Response**: `{"Task ID": null, "Error": "System error: [details]"}`

### Authorization Errors

**When it occurs**: Permission issues
**Common scenarios**: User authentication failed
**Response**: `{"Task ID": null, "Error": "Authentication failed"}`

## Usage Examples

### Example 1: Find Files with Read Permission

**Input**: `Permission: Read`
**Output**: `Task ID: a1b2c3d4-e5f6-7890-abcd-ef1234567890`
**Next Step**: Use this Task ID with [Get Result](pages/GetResult.md)

### Example 2: Find Files with Read and Write Permission

**Input**: `Permission: Read and Write`
**Output**: `Task ID: 9876fedc-ba09-8765-4321-0fedcba98765`
**Next Step**: Use this Task ID with [Get Result](pages/GetResult.md)

### Example 3: Find Files User Owns

**Input**: `Permission: Own`
**Output**: `Task ID: xyz123abc-456d-789e-0123-456789abcdef`
**Next Step**: Use this Task ID with [Get Result](pages/GetResult.md)

## Business Rules

1. **Permission-Based Search**: Returns files based on user's permission level
2. **Asynchronous Operation**: Returns Task ID immediately, processing happens in background
3. **Two-Step Process**: Requires Search By Permissions followed by Get Result
4. **All Drives**: Searches across personal and shared drives
5. **Four Permission Levels**: Read, Write, Read and Write, Own

## Limitations

- **No Path Filter**: Cannot filter by specific folder path
- **No Direct Results**: Must use Get Result to retrieve file list
- **Processing Time**: Large result sets may take time to process
- **No Progress Tracking**: Cannot track search progress
- **Single Permission**: Can only search one permission level at a time

## Best Practices

1. Use Task ID immediately with [Get Result](pages/GetResult.md)
2. Handle empty result lists (user may have no files with permission)
3. Store Task ID for later retrieval if needed
4. Check for null Task ID indicating failure
5. Use appropriate permission level for use case

## Common Use Cases

1. Finding files user can edit (Read and Write)
2. Discovering owned files for management
3. Identifying read-only files
4. Permission auditing
5. Access control reporting
6. File inventory by permission

## Related Catalog Requests

- [Get Result](pages/GetResult.md) - Retrieve file list using Task ID
- [Share File](pages/ShareFile.md) - Share files with permissions
- [List Files](pages/ListFiles.md) - List files in specific folder

## Technical Implementation

**Helper Classes**: PermissionDao, EventHandler
**Telemetry Metrics**: `onedrive.onedrive.search_by_permissions.initiated`,
`onedrive.onedrive.search_by_permissions.duration`
**API Endpoint**: `GET /me/drive/sharedWithMe` and permission filtering

### Search By Permissions Operation Flow

1. Validate permission parameter
2. Generate unique Task ID (UUID)
3. Start asynchronous permission search
4. Return Task ID immediately
5. Background process searches files:
    - Query Microsoft Graph API for files
    - Filter by permission level
    - Format as File entities
6. Store results with Task ID
7. Trigger event when complete (2-second minimum delay)

## Permission Types

| Permission         | Description   | User Can                                |
|--------------------|---------------|-----------------------------------------|
| **Read**           | View only     | View, download files                    |
| **Write**          | Edit only     | Edit files (rare permission)            |
| **Read and Write** | View and edit | View, download, edit, upload            |
| **Own**            | Full control  | All actions, manage permissions, delete |

## Troubleshooting

**Issue: Task ID is null** - Check permission value is valid, verify authentication, review error message
**Issue: Get Result returns no files** - User may have no files with specified permission, verify permission level is
correct
**Issue: Get Result returns too many files** - This is expected for common permissions like Read, process results in
batches
**Issue: Processing takes long time** - Large result sets take time, be patient, consider using more specific permission

## Performance Considerations

### Permission Level Impact

| Permission         | Typical Result Count  | Processing Time          |
|--------------------|-----------------------|--------------------------|
| **Own**            | Low (user's files)    | Fast (2-10 seconds)      |
| **Read and Write** | Medium                | Moderate (10-30 seconds) |
| **Read**           | High (all accessible) | Slower (30-60+ seconds)  |
| **Write**          | Low (rare permission) | Fast (2-10 seconds)      |

## Workflow Integration

### Example Workflow: Process Editable Files

1. Execute Search By Permissions with Permission = "Read and Write"
2. Receive Task ID
3. Execute [Get Result](pages/GetResult.md) with Task ID
4. For each file in Files:
    - Check if file needs processing
    - Execute [Download as File](pages/DownloadAsFile.md)
    - Process file content
    - Execute [Write](pages/Write.md) to save changes

### Example Workflow: Audit Owned Files

1. Execute Search By Permissions with Permission = "Own"
2. Receive Task ID
3. Execute [Get Result](pages/GetResult.md) with Task ID
4. Generate report of owned files
5. Identify files for cleanup or archiving

## Comparison with List Files

| Aspect          | Search By Permissions      | List Files        |
|-----------------|----------------------------|-------------------|
| **Scope**       | All drives                 | Specific folder   |
| **Filter**      | By permission              | By path/search    |
| **Use Case**    | Permission-based discovery | Folder browsing   |
| **Result Size** | Potentially large          | Limited to folder |
| **Processing**  | Slower                     | Faster            |

## See Also

- [Get Result](pages/GetResult.md) - Retrieve file list
- [Share File](pages/ShareFile.md) - Share files with permissions
- [List Files](pages/ListFiles.md) - List files in folder
- [Download as File](pages/DownloadAsFile.md) - Download files

