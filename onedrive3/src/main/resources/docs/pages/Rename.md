# Rename

## Overview

Renames the file or folder given at the Source path to the new name provided.

## Request Details

| Property          | Value                       |
|-------------------|-----------------------------|
| **Area**          | Management                  |
| **Type**          | CHANGE_SYSTEM               |
| **Retry Support** | Yes (with re-entry prompts) |

## Input Parameters

| Parameter Name   | Type | Required | Description                                                                                                                                              |
|------------------|------|----------|----------------------------------------------------------------------------------------------------------------------------------------------------------|
| Source           | Text | Yes      | Full path to the file or folder to be renamed (e.g., /Documents/old_name.txt or /Folders/OldFolder). For shared drives, use format driveId:/path/to/file |
| Target File Name | Text | Yes      | New name for the file or folder (without the path, e.g., new_name.txt or NewFolder)                                                                      |

> 📌 **Root folder path:** For an item in your OneDrive **root folder**, prefix its name with **`drive/root:/`** — e.g. **`drive/root:/report.pdf`** (`drive/root:/` alone refers to the root itself). For a shared drive / SharePoint / Teams library, use the Drive Path (`drives/{driveId}/root:/…`) returned by [Get Drives](pages/GetDrives.md).

## Output Parameters

| Parameter Name | Type   | Description                                                           |
|----------------|--------|-----------------------------------------------------------------------|
| Success        | Yes/No | Indicates whether the rename operation was successful                 |
| Message        | Text   | Provides details about the operation result or any errors encountered |

## Validation Rules

| Field            | Validation Rule                       | Error Message                                     |
|------------------|---------------------------------------|---------------------------------------------------|
| Source           | Must not be empty                     | "Source path is required"                         |
| Target File Name | Must not be empty                     | "Target file name is required"                    |
| Source           | Must exist                            | "Source file or folder not found"                 |
| Target File Name | Must not contain path separators      | "Target file name cannot contain path separators" |
| Target File Name | Must not already exist in same folder | "File or folder with this name already exists"    |

## Error Handling

### INPUT_ERROR

**When it occurs**: Invalid input parameters
**Common scenarios**: Empty source/target, invalid characters in name, path separators in target name
**Response**: `{"Success": false, "Message": "Invalid input: Target file name is required"}`
**Re-entry Support**: System prompts to re-enter source or target file name

### LOGIC_ERROR

**When it occurs**: Business logic violations
**Common scenarios**: Source not found, target name already exists, invalid file name
**Response**: `{"Success": false, "Message": "Source file or folder not found"}`
**Re-entry Support**: System prompts to re-enter source or target file name

### SYSTEM_ERROR

**When it occurs**: System-level failures
**Common scenarios**: Network issues, Microsoft Graph API unavailable, timeout
**Response**: `{"Success": false, "Message": "Failed to rename file: [error details]"}`

### Authorization Errors

**When it occurs**: Permission issues
**Common scenarios**: User does not have write permission on file/folder
**Response**: `{"Success": false, "Message": "Insufficient permissions to rename file"}`

## Usage Examples

### Example 1: Rename File

**Input**: `Source: drive/root:/Documents/old_report.pdf, Target File Name: new_report.pdf`
**Output**: `Success: Yes, Message: File renamed successfully`

### Example 2: Rename Folder

**Input**: `Source: drive/root:/Projects/OldProject, Target File Name: NewProject`
**Output**: `Success: Yes, Message: Folder renamed successfully`

### Example 3: Name Already Exists

**Input**: `Source: drive/root:/file.txt, Target File Name: existing_file.txt`
**Output**: `Success: No, Message: File or folder with this name already exists`

## Business Rules

1. **Files and Folders**: Supports renaming both files and folders
2. **Shared Drives Supported**: Can rename items on both personal and shared drives
3. **Name Only**: Target File Name should be name only, not full path
4. **Same Location**: File/folder remains in same location
5. **Extension Preserved**: Can change file extension if needed

## Limitations

- **No Path in Target**: Target File Name cannot contain path separators
- **No Batch Rename**: Can only rename one item at a time
- **No Overwrite**: Cannot rename to existing name in same folder
- **Same Folder Only**: Cannot move to different folder (use Move instead)

## Best Practices

1. Verify source exists using [List Files](pages/ListFiles.md) before renaming
2. Check target name doesn't already exist
3. Use [Move](pages/Move.md) if you need to change location too
4. Preserve file extensions when renaming files
5. Use meaningful, descriptive names
6. Avoid special characters in file names

## Common Use Cases

1. Correcting file names
2. Standardizing naming conventions
3. Adding version numbers to files
4. Updating folder names for organization
5. Renaming files based on workflow status

## Related Catalog Requests

- [Move](pages/Move.md) - Move and rename in one operation
- [Copy](pages/Copy.md) - Create copy with new name
- [List Files](pages/ListFiles.md) - Verify source exists

## Technical Implementation

**Helper Classes**: ManagementDaoImpl
**Telemetry Metrics**: `onedrive.management.rename.success`, `onedrive.management.rename.failure`,
`onedrive.management.rename.duration`
**API Endpoint**: `PATCH /me/drive/items/{item-id}`

## Troubleshooting

**Issue: "Source file or folder not found"** - Verify source path exists, check for typos
**Issue: "File or folder with this name already exists"** - Choose different target name, delete existing file first
**Issue: "Target file name cannot contain path separators"** - Remove / or \ from target name, use Move for path changes
**Issue: "Insufficient permissions"** - Verify write permission on file/folder

## Invalid Characters

The following characters cannot be used in file names:

- `/` (forward slash)
- `\` (backslash)
- `:` (colon)
- `*` (asterisk)
- `?` (question mark)
- `"` (double quote)
- `<` (less than)
- `>` (greater than)
- `|` (pipe)

## See Also

- [Move](pages/Move.md) - Move files/folders
- [Copy](pages/Copy.md) - Copy files/folders
- [List Files](pages/ListFiles.md) - List files

