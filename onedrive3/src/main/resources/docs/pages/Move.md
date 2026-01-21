# Move

## Overview

Move a file or folder on the drive from source to target where both denote a full path.

## Request Details

| Property          | Value                       |
|-------------------|-----------------------------|
| **Area**          | Management                  |
| **Type**          | CHANGE_SYSTEM               |
| **Retry Support** | Yes (with re-entry prompts) |

## Input Parameters

| Parameter Name | Type | Required | Description                                                                                                                                                          |
|----------------|------|----------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Source         | Text | Yes      | Full path to the source file or folder to be moved (e.g., /Documents/file.txt or /Folders/MyFolder). For shared drives, use format driveId:/path/to/file             |
| Target         | Text | Yes      | Full path where the file or folder should be moved to (e.g., /Documents/NewLocation or /Folders/Archive). For shared drives, use format driveId:/path/to/destination |

## Output Parameters

| Parameter Name | Type         | Description                                                                     |
|----------------|--------------|---------------------------------------------------------------------------------|
| File           | Entity(File) | File entity representing the moved file or folder with updated path information |
| Success        | Yes/No       | Indicates whether the move operation was successful                             |
| Message        | Text         | Provides details about the operation result or any errors encountered           |

## Validation Rules

| Field  | Validation Rule          | Error Message                          |
|--------|--------------------------|----------------------------------------|
| Source | Must not be empty        | "Source path is required"              |
| Target | Must not be empty        | "Target path is required"              |
| Source | Must exist               | "Source file or folder not found"      |
| Target | Parent folder must exist | "Target folder not found"              |
| Target | Must not already exist   | "Target file or folder already exists" |

## Error Handling

### INPUT_ERROR

**When it occurs**: Invalid input parameters
**Common scenarios**: Empty source/target, invalid path format
**Response**: `{"Success": false, "Message": "Invalid input: Source path is required", "File": null}`
**Re-entry Support**: System prompts to re-enter source or target path

### LOGIC_ERROR

**When it occurs**: Business logic violations
**Common scenarios**: Source not found, target already exists, cannot move to subfolder of itself
**Response**: `{"Success": false, "Message": "Source file or folder not found", "File": null}`
**Re-entry Support**: System prompts to re-enter source or target path

### SYSTEM_ERROR

**When it occurs**: System-level failures
**Common scenarios**: Network issues, Microsoft Graph API unavailable, timeout
**Response**: `{"Success": false, "Message": "Failed to move file: [error details]", "File": null}`

### Authorization Errors

**When it occurs**: Permission issues
**Common scenarios**: No write permission on source or target folder
**Response**: `{"Success": false, "Message": "Insufficient permissions to move file", "File": null}`

## Usage Examples

### Example 1: Move File to Different Folder

**Input**: `Source: drive/root:/Documents/report.pdf, Target: drive/root:/Archive/report.pdf`
**Output**: `Success: Yes, Message: File moved successfully, File: [Entity with new path]`

### Example 2: Move Folder

**Input**: `Source: drive/root:/Projects/OldProject, Target: drive/root:/Archive/OldProject`
**Output**: `Success: Yes, Message: Folder moved successfully, File: [Entity with new path]`

### Example 3: Target Already Exists

**Input**: `Source: drive/root:/file.txt, Target: drive/root:/Documents/file.txt`
**Output**: `Success: No, Message: Target file or folder already exists, File: null`

## Business Rules

1. **Files and Folders**: Supports moving both files and folders
2. **Shared Drives Supported**: Can move between personal and shared drives
3. **Rename During Move**: Target can have different name than source
4. **Atomic Operation**: Move is atomic - either succeeds completely or fails
5. **Preserve Metadata**: File metadata is preserved during move

## Limitations

- **No Overwrite**: Cannot overwrite existing target
- **No Batch Move**: Can only move one item at a time
- **No Cross-Account**: Cannot move between different OneDrive accounts
- **Subfolder Restriction**: Cannot move folder into its own subfolder

## Best Practices

1. Verify source exists using [List Files](pages/ListFiles.md) before moving
2. Check target doesn't exist to avoid errors
3. Use [Copy](pages/Copy.md) if you need to keep original
4. Verify target folder exists before moving
5. Handle re-entry prompts for correcting paths
6. Check Success flag before assuming move completed

## Common Use Cases

1. File organization and cleanup
2. Archiving old files
3. Reorganizing folder structure
4. Moving files between projects
5. Workflow-based file routing

## Related Catalog Requests

- [Copy](pages/Copy.md) - Copy instead of move
- [Rename](pages/Rename.md) - Rename without changing location
- [Delete](pages/Delete.md) - Delete files/folders
- [List Files](pages/ListFiles.md) - Verify source exists

## Technical Implementation

**Helper Classes**: ManagementDaoImpl
**Telemetry Metrics**: `onedrive.management.move.success`, `onedrive.management.move.failure`,
`onedrive.management.move.duration`
**API Endpoint**: `PATCH /me/drive/items/{item-id}`

## Troubleshooting

**Issue: "Source file or folder not found"** - Verify source path exists, check for typos, ensure user has access
**Issue: "Target file or folder already exists"** - Delete or rename existing target, choose different target path
**Issue: "Cannot move folder into its own subfolder"** - Choose target outside source folder hierarchy
**Issue: "Insufficient permissions"** - Verify write permission on both source and target folders

## See Also

- [Copy](pages/Copy.md) - Copy files/folders
- [Rename](pages/Rename.md) - Rename files/folders
- [Delete](pages/Delete.md) - Delete files/folders

