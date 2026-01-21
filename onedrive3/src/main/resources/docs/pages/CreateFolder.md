# Create Folder

## Overview

Accept folder name and path as input and create a new folder at the specified location in OneDrive.

## Request Details

| Property          | Value                       |
|-------------------|-----------------------------|
| **Area**          | Management                  |
| **Type**          | CHANGE_SYSTEM               |
| **Retry Support** | Yes (with re-entry prompts) |

## Input Parameters

| Parameter Name | Type | Required | Description                                                                                |
|----------------|------|----------|--------------------------------------------------------------------------------------------|
| Folder Name    | Text | Yes      | The name for the new folder. This should not include path separators                       |
| Path           | Text | Yes      | The full path where the new folder should be created. The parent folder must already exist |

## Output Parameters

| Parameter Name | Type         | Description                                                           |
|----------------|--------------|-----------------------------------------------------------------------|
| File           | Entity(File) | File entity representing the created folder with metadata             |
| Success        | Yes/No       | Indicates whether the folder was successfully created                 |
| Message        | Text         | Provides details about the operation result or any errors encountered |

## Validation Rules

| Field       | Validation Rule                  | Error Message                                |
|-------------|----------------------------------|----------------------------------------------|
| Folder Name | Must not be empty                | "Folder name is required"                    |
| Path        | Must not be empty                | "Path is required"                           |
| Folder Name | Must not contain path separators | "Folder name cannot contain path separators" |
| Path        | Parent folder must exist         | "Parent folder not found"                    |
| Folder Name | Must not already exist at path   | "Folder already exists"                      |

## Error Handling

### INPUT_ERROR

**When it occurs**: Invalid input parameters
**Common scenarios**: Empty folder name, empty path, path separators in folder name
**Response**: `{"Success": false, "Message": "Invalid input: Folder name is required", "File": null}`
**Re-entry Support**: System prompts to re-enter folder name or path

### LOGIC_ERROR

**When it occurs**: Business logic violations
**Common scenarios**: Parent folder not found, folder already exists, invalid folder name
**Response**: `{"Success": false, "Message": "Parent folder not found", "File": null}`
**Re-entry Support**: System prompts to re-enter folder name or path

### SYSTEM_ERROR

**When it occurs**: System-level failures
**Common scenarios**: Network issues, Microsoft Graph API unavailable, timeout
**Response**: `{"Success": false, "Message": "Failed to create folder: [error details]", "File": null}`

### Authorization Errors

**When it occurs**: Permission issues
**Common scenarios**: User does not have write permission on parent folder
**Response**: `{"Success": false, "Message": "Insufficient permissions to create folder", "File": null}`

## Usage Examples

### Example 1: Create Folder in Documents

**Input**: `Folder Name: NewProject, Path: drive/root:/Documents`
**Output**: `Success: Yes, Message: Folder created successfully, File: [Entity with folder metadata]`

### Example 2: Create Nested Folder

**Input**: `Folder Name: 2024, Path: drive/root:/Archive/Reports`
**Output**: `Success: Yes, Message: Folder created successfully, File: [Entity]`

### Example 3: Folder Already Exists

**Input**: `Folder Name: ExistingFolder, Path: drive/root:/Documents`
**Output**: `Success: No, Message: Folder already exists, File: null`

### Example 4: Parent Folder Not Found

**Input**: `Folder Name: NewFolder, Path: drive/root:/NonExistent`
**Output**: `Success: No, Message: Parent folder not found, File: null`

## Business Rules

1. **Parent Must Exist**: Parent folder must exist before creating subfolder
2. **Shared Drives Supported**: Can create folders on both personal and shared drives
3. **Name Only**: Folder Name should be name only, not full path
4. **No Overwrite**: Cannot create folder if one with same name already exists
5. **Immediate Creation**: Folder is created immediately

## Limitations

- **No Path in Name**: Folder Name cannot contain path separators
- **No Batch Create**: Can only create one folder at a time
- **No Recursive Create**: Cannot create nested folders in one operation
- **Parent Must Exist**: Cannot create parent folders automatically

## Best Practices

1. Verify parent folder exists using [List Folders](pages/ListFolders.md)
2. Check folder doesn't already exist
3. Use meaningful, descriptive folder names
4. Avoid special characters in folder names
5. Create parent folders first for nested structures
6. Check Success flag before assuming folder created

## Common Use Cases

1. Organizing files into new folders
2. Creating project folders
3. Setting up folder structures for workflows
4. Creating archive folders
5. Organizing uploads by date or category

## Related Catalog Requests

- [List Folders](pages/ListFolders.md) - Verify parent folder exists
- [Move](pages/Move.md) - Move files to new folder
- [Write](pages/Write.md) - Upload files to new folder

## Technical Implementation

**Helper Classes**: ManagementDaoImpl
**Telemetry Metrics**: `onedrive.management.create_folder.success`, `onedrive.management.create_folder.failure`,
`onedrive.management.create_folder.duration`
**API Endpoint**: `POST /me/drive/root:/{path}:/children`

**Request Body**:

```json
{
  "name": "FolderName",
  "folder": {},
  "@microsoft.graph.conflictBehavior": "fail"
}
```

## Troubleshooting

**Issue: "Parent folder not found"** - Verify parent path exists, create parent folders first, check for typos
**Issue: "Folder already exists"** - Choose different folder name, delete existing folder first, use existing folder
**Issue: "Folder name cannot contain path separators"** - Remove / or \ from folder name, use Path parameter for
location
**Issue: "Insufficient permissions"** - Verify write permission on parent folder

## Invalid Characters

The following characters cannot be used in folder names:

- `/` (forward slash)
- `\` (backslash)
- `:` (colon)
- `*` (asterisk)
- `?` (question mark)
- `"` (double quote)
- `<` (less than)
- `>` (greater than)
- `|` (pipe)

## Creating Nested Folder Structures

To create nested folders, create them one at a time from parent to child:

**Example: Create /Documents/Projects/2024**

1. Create Folder:
    - Folder Name: Projects
    - Path: drive/root:/Documents

2. Create Folder:
    - Folder Name: 2024
    - Path: drive/root:/Documents/Projects

## Path Format Reference

### Personal Drive Paths

- **Root**: `drive/root:/`
- **Folder**: `drive/root:/Documents`
- **Subfolder**: `drive/root:/Documents/Projects`

### Shared Drive Paths

- **Format**: `drives/b!{driveId}/root:/path/to/folder`
- **Example**: `drives/b!absdbfbbvwevb/root:/TeamFiles`

### Relative Paths (Personal Drive)

- **Root**: `/`
- **Folder**: `/Documents`
- **Subfolder**: `/Documents/Projects`

## See Also

- [List Folders](pages/ListFolders.md) - List existing folders
- [Move](pages/Move.md) - Move files/folders
- [Write](pages/Write.md) - Upload files to folder
- [Delete](pages/Delete.md) - Delete folders

