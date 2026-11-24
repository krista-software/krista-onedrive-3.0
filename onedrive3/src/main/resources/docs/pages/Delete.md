# Delete

## Overview

Removes a file or folder from OneDrive using the exact file path.

## Request Details

| Property          | Value                       |
|-------------------|-----------------------------|
| **Area**          | Management                  |
| **Type**          | CHANGE_SYSTEM               |
| **Retry Support** | Yes (with re-entry prompts) |

## Input Parameters

| Parameter Name | Type | Required | Description                                                                                                                               |
|----------------|------|----------|-------------------------------------------------------------------------------------------------------------------------------------------|
| Target         | Text | Yes      | Full path to the file to be deleted (e.g., /Documents/file.txt or /Folders/MyFolder). For shared drives, use format driveId:/path/to/file |

> 📌 **Root folder path:** For an item in your OneDrive **root folder**, prefix its name with **`drive/root:/`** — e.g. **`drive/root:/report.pdf`** (`drive/root:/` alone refers to the root itself). For a shared drive / SharePoint / Teams library, use the Drive Path (`drives/{driveId}/root:/…`) returned by [Get Drives](pages/GetDrives.md).

## Output Parameters

| Parameter Name | Type   | Description                                   |
|----------------|--------|-----------------------------------------------|
| Success        | Yes/No | Indicates whether the deletion was successful |
| Message        | Text   | Provides details about the operation result   |

## Validation Rules

| Field  | Validation Rule                  | Error Message                             |
|--------|----------------------------------|-------------------------------------------|
| Target | Must not be empty                | "Target path is required"                 |
| Target | Must be valid OneDrive path      | "Invalid target path format"              |
| Target | File or folder must exist        | "File not found"                          |
| Target | User must have delete permission | "Insufficient permissions to delete file" |

## Error Handling

### INPUT_ERROR

**When it occurs**: Invalid input parameters

**Common scenarios**:

- Empty target path
- Invalid path format
- Malformed path string

**Response**:

```json
{
  "Success": false,
  "Message": "Invalid input: Target path is required"
}
```

**Re-entry Support**: System prompts to re-enter invalid target path

### LOGIC_ERROR

**When it occurs**: Business logic violations

**Common scenarios**:

- Target file or folder does not exist
- File is locked or in use
- Attempting to delete root folder

**Response**:

```json
{
  "Success": false,
  "Message": "Failed to delete file: Please provide valid target path"
}
```

**Re-entry Support**: If target path is invalid or file not found, system prompts to re-enter target path

### SYSTEM_ERROR

**When it occurs**: System-level failures

**Common scenarios**:

- Network connectivity issues
- Microsoft Graph API unavailable
- Timeout during delete operation
- Internal extension error

**Response**:

```json
{
  "Success": false,
  "Message": "Failed to delete file: [error details]"
}
```

### Authorization Errors

**When it occurs**: Permission issues

**Common scenarios**:

- User does not have delete permission
- File is protected or read-only
- Organizational policies prevent deletion

**Response**:

```json
{
  "Success": false,
  "Message": "Insufficient permissions to delete file"
}
```

## Usage Examples

### Example 1: Delete File from Personal Drive

**Input**:

```
Target: drive/root:/Documents/old_report.pdf
```

**Output**:

```
Success: Yes
Message: File deleted successfully
```

### Example 2: Delete Folder

**Input**:

```
Target: drive/root:/Archive/OldProjects
```

**Output**:

```
Success: Yes
Message: File deleted successfully
```

### Example 3: Delete File Not Found

**Input**:

```
Target: drive/root:/Documents/nonexistent.txt
```

**Output**:

```
Success: No
Message: Failed to delete file: Please provide valid target path
```

### Example 4: Delete with Relative Path

**Input**:

```
Target: /Documents/temp.txt
```

**Output**:

```
Success: Yes
Message: File deleted successfully
```

## Business Rules

1. **Personal Drive Only**: Delete operation supports only personal drive files and folders
2. **Shared Drive Limitation**: Files in shared drives (paths starting with `drives/b!`) are not supported
3. **Permanent Deletion**: Deleted files are moved to OneDrive Recycle Bin, not permanently deleted
4. **Folder Deletion**: Deleting a folder also deletes all its contents
5. **No Undo**: Operation cannot be undone via the extension (use OneDrive Recycle Bin to restore)

## Limitations

- **Personal Drive Only**: Does not support deleting files in shared drives
- **No Batch Delete**: Can only delete one file or folder at a time
- **No Confirmation**: No confirmation prompt before deletion
- **Recycle Bin Dependency**: Recovery depends on OneDrive Recycle Bin retention policy
- **No Permanent Delete**: Cannot permanently delete files (bypass Recycle Bin)

## Best Practices

1. **Verify Before Delete**: Confirm target path is correct before executing
2. **Use List Files**: Use [List Files](pages/ListFiles.md) to verify file exists before deleting
3. **Backup Important Files**: Copy important files before deletion
4. **Check Permissions**: Ensure user has delete permission on target
5. **Handle Errors**: Always check Success flag and handle failures appropriately
6. **Log Deletions**: Maintain audit log of deleted files for compliance
7. **Test with Non-Critical Files**: Test deletion with non-critical files first

## Common Use Cases

1. **Cleanup Operations**: Remove temporary or obsolete files
2. **Automated Maintenance**: Delete old files based on age or criteria
3. **Workflow Cleanup**: Remove intermediate files after processing
4. **Space Management**: Delete large files to free up storage
5. **Data Retention**: Enforce data retention policies by deleting old files
6. **Error Recovery**: Delete corrupted or incomplete files

## Related Catalog Requests

- [Copy](pages/Copy.md) - Copy files before deleting
- [Move](pages/Move.md) - Move files instead of deleting
- [List Files](pages/ListFiles.md) - List files to identify deletion targets
- [Get Info](pages/GetInfo.md) - Get folder information before cleanup

## Technical Implementation

### Helper Classes

- **ManagementDaoImpl**: Handles delete operation logic
- **FileUtil**: Validates paths and formats
- **Microsoft Graph API**: Executes delete operation

### Telemetry Metrics

The following metrics are tracked:

- `onedrive.management.delete.success` - Successful delete operations
- `onedrive.management.delete.failure` - Failed delete operations
- `onedrive.management.delete.duration` - Time taken to delete
- `onedrive.management.delete.file_count` - Number of files deleted

### Delete Operation Flow

1. Validate input parameter (target path)
2. Check target file/folder exists
3. Verify user has delete permission
4. Execute delete operation via Microsoft Graph API
5. File/folder moved to OneDrive Recycle Bin
6. Return success or failure response

### Microsoft Graph API Endpoint

```
DELETE /me/drive/items/{item-id}
```

**Response**: 204 No Content (success) or error

## Troubleshooting

### Issue: "Failed to delete file: Please provide valid target path"

**Possible Causes**:

- Target path does not exist
- Target path format is incorrect
- User does not have access to target

**Solutions**:

1. Verify target path exists using [List Files](pages/ListFiles.md)
2. Check path format (should be `drive/root:/path/to/file` or `/path/to/file`)
3. Ensure user has access to the file
4. Check for typos in path

### Issue: "Insufficient permissions to delete file"

**Possible Causes**:

- User does not have delete permission
- File is read-only or protected
- Organizational policy prevents deletion

**Solutions**:

1. Verify user has delete permission on the file
2. Check file properties in OneDrive
3. Contact administrator if organizational policy blocks deletion
4. Ensure file is not locked by another process

### Issue: Delete appears successful but file still exists

**Possible Causes**:

- File is in OneDrive Recycle Bin (not permanently deleted)
- Sync delay between OneDrive and local cache
- Browser cache showing old data

**Solutions**:

1. Check OneDrive Recycle Bin
2. Refresh OneDrive view
3. Wait a few moments for sync to complete
4. Use [List Files](pages/ListFiles.md) to verify deletion

### Issue: Cannot delete folder with contents

**Possible Causes**:

- Some files in folder are locked
- Insufficient permissions on some files
- Folder contains protected items

**Solutions**:

1. Delete files individually first
2. Check permissions on all files in folder
3. Ensure no files are locked or in use
4. Contact administrator for protected items

## Path Format Reference

### Personal Drive Paths

- **Root**: `drive/root:/`
- **Folder**: `drive/root:/FolderName`
- **Subfolder**: `drive/root:/FolderName/SubFolder`
- **File**: `drive/root:/FolderName/file.txt`

### Relative Paths (Personal Drive)

- **File**: `/Documents/file.txt`
- **Folder**: `/Archive/OldProjects`

### Shared Drive Paths (Not Supported for Delete)

- **Format**: `drives/b!{driveId}/root:/path/to/file`
- **Note**: Delete operation does not support shared drives

## Recovery from Deletion

### OneDrive Recycle Bin

Deleted files can be recovered from OneDrive Recycle Bin:

1. **Retention Period**: Typically 30-93 days depending on configuration
2. **Access**: Users can access Recycle Bin via OneDrive web interface
3. **Restore**: Files can be restored to original location
4. **Permanent Delete**: Files can be permanently deleted from Recycle Bin

### Recovery Steps

1. Open OneDrive in web browser
2. Navigate to Recycle Bin
3. Locate deleted file
4. Select file and click "Restore"
5. File returns to original location

## Security Considerations

### Audit Trail

- All delete operations should be logged
- Include user, timestamp, and file path
- Maintain logs for compliance requirements

### Data Protection

- Ensure sensitive files are backed up before deletion
- Verify deletion complies with data retention policies
- Consider encryption for sensitive deleted data

### Access Control

- Limit delete permissions to authorized users
- Implement approval workflows for critical deletions
- Monitor delete operations for suspicious activity

## See Also

- [Copy](pages/Copy.md) - Copy files before deleting
- [Move](pages/Move.md) - Move files instead of deleting
- [List Files](pages/ListFiles.md) - List files in folders
- [Get Info](pages/GetInfo.md) - Get folder statistics

