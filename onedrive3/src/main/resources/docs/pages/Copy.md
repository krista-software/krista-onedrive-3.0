# Copy

## Overview

Copies the provided file or folder from the source location to the target location in OneDrive.

## Request Details

| Property          | Value                       |
|-------------------|-----------------------------|
| **Area**          | Management                  |
| **Type**          | CHANGE_SYSTEM               |
| **Retry Support** | Yes (with re-entry prompts) |

## Input Parameters

| Parameter Name | Type   | Required | Description                                                                 |
|----------------|--------|----------|-----------------------------------------------------------------------------|
| Source         | Text   | Yes      | Full path to the source file or folder to be copied                         |
| Target         | Text   | Yes      | Full path where the file or folder should be copied                         |
| Replace        | Yes/No | No       | If true, replaces any existing file at the target location (default: false) |
| Create Folder  | Yes/No | No       | If true, creates any new folders in the target path (default: false)        |

## Output Parameters

| Parameter Name | Type   | Description                                                                             |
|----------------|--------|-----------------------------------------------------------------------------------------|
| File           | Entity | File entity representing the copied file with metadata (name, extension, absolute path) |
| Message        | Text   | Provides details about the operation result                                             |
| Success        | Yes/No | Indicates whether the copy operation was successful                                     |

### File Entity Fields

| Field         | Type      | Description                                 |
|---------------|-----------|---------------------------------------------|
| Name          | Text      | Name of the copied file including extension |
| Base Name     | Text      | Name without extension                      |
| Extension     | Text      | File extension (e.g., .txt, .pdf)           |
| Absolute Path | Text      | Full path to the copied file in OneDrive    |
| Parent Folder | Text      | Path to the parent folder                   |
| Size          | Number    | File size in bytes                          |
| Created       | Date/Time | When the file was created                   |
| Modified      | Date/Time | When the file was last modified             |

## Validation Rules

| Field         | Validation Rule                                        | Error Message                     |
|---------------|--------------------------------------------------------|-----------------------------------|
| Source        | Must not be empty                                      | "Please enter a source path. This field is required."         |
| Source        | Must be valid OneDrive path                            | "The source path format isn't valid. Please use a format like /Documents/file.txt"      |
| Source        | File or folder must exist                              | "We couldn't find the file or folder at the source path. Please verify it exists in your OneDrive." |
| Target        | Must not be empty                                      | "Please enter a target path. This field is required."         |
| Target        | Must be valid OneDrive path                            | "The target path format isn't valid. Please use a format like /Documents/NewFolder"      |
| Target        | Parent folder must exist (unless Create Folder = true) | "The target folder doesn't exist. Either create it first or enable 'Create Folder'."    |
| Replace       | Must be boolean                                        | "Please select Yes or No for the Replace option."           |
| Create Folder | Must be boolean                                        | "Please select Yes or No for the Create Folder option."     |

## Error Handling

### Input Validation Errors (INPUT_ERROR)

**What it means**: The information you provided doesn't meet the requirements for copying files.

**Common scenarios**:

- You didn't enter a source or target path
- The path format is incorrect (should be like /Documents/file.txt)
- Invalid Yes/No values for Replace or Create Folder options

**How to fix it**:

1. **Read the error message** - It tells you exactly what's wrong
2. Make sure both source and target paths are filled in
3. Use the correct path format: /FolderName/SubFolder/file.txt
4. For shared drives, use: drives/{driveId}/root:/path/to/file
5. Ensure Replace and Create Folder are set to Yes or No

**Re-entry Support**: The system will prompt you to enter the correct path

### File and Folder Errors (LOGIC_ERROR)

**What it means**: There's an issue with the files or folders you're trying to copy.

**Common scenarios**:

- The source file or folder doesn't exist at the path you specified
- The target folder doesn't exist and you haven't enabled "Create Folder"
- A file with the same name already exists at the target and you haven't enabled "Replace"
- You're trying to replace a folder (only files can be replaced)

**How to fix it**:

1. **Verify the source exists**:
   - Check the source path is correct
   - Make sure the file or folder exists in your OneDrive
   - Verify you have permission to access it

2. **Check the target location**:
   - If the target folder doesn't exist, set "Create Folder" to Yes
   - If a file already exists, set "Replace" to Yes to overwrite it
   - Note: You cannot replace folders, only files

3. **Use the re-entry option**:
   - For invalid source → system prompts to re-enter source path
   - For invalid target → system prompts to re-enter target path

### Service and Connection Errors (SYSTEM_ERROR)

**What it means**: There's a problem connecting to Microsoft OneDrive or completing the operation.

**Common scenarios**:

- Your internet connection is unstable
- Microsoft OneDrive service is temporarily unavailable
- The copy operation timed out (usually for very large files)
- An unexpected error occurred in the system

**How to fix it**:

1. **Check your connection**: Make sure you have a stable internet connection
2. **Wait and retry**: If OneDrive is experiencing issues, wait a few minutes and try again
3. **Check Microsoft's status**: Visit [Microsoft 365 Status](https://status.office.com) for known outages
4. **For large files**: Consider copying smaller batches or individual files
5. **Verify authentication**: Make sure you're still signed in (session hasn't expired)

**Response**:

```json
{
  "Success": false,
  "Message": "Failed to copy file: [error details]"
}
```

### Authorization Errors

**When it occurs**: Permission issues

**Common scenarios**:

- User does not have read permission on source
- User does not have write permission on target
- File is locked or in use

**Response**:

```json
{
  "Success": false,
  "Message": "Insufficient permissions to copy file"
}
```

## Usage Examples

### Example 1: Copy File to Existing Folder

**Input**:

```
Source: drive/root:/Documents/report.pdf
Target: drive/root:/Archive
Replace: No
Create Folder: No
```

**Output**:

```
File: {
  "Name": "report.pdf",
  "Extension": ".pdf",
  "Absolute Path": "drive/root:/Archive/report.pdf"
}
Message: File has been copied successfully
Success: Yes
```

### Example 2: Copy File with Replace

**Input**:

```
Source: drive/root:/Documents/data.xlsx
Target: drive/root:/Backup
Replace: Yes
Create Folder: No
```

**Output**:

```
File: {
  "Name": "data.xlsx",
  "Extension": ".xlsx",
  "Absolute Path": "drive/root:/Backup/data.xlsx"
}
Message: File has been copied successfully
Success: Yes
```

### Example 3: Copy File and Create Target Folder

**Input**:

```
Source: drive/root:/Documents/presentation.pptx
Target: drive/root:/NewFolder/Subfolder
Replace: No
Create Folder: Yes
```

**Output**:

```
File: {
  "Name": "presentation.pptx",
  "Extension": ".pptx",
  "Absolute Path": "drive/root:/NewFolder/Subfolder/presentation.pptx"
}
Message: File has been copied successfully
Success: Yes
```

### Example 4: Copy Folder

**Input**:

```
Source: drive/root:/Projects/ProjectA
Target: drive/root:/Archive
Replace: No
Create Folder: No
```

**Output**:

```
File: {
  "Name": "ProjectA",
  "Absolute Path": "drive/root:/Archive/ProjectA"
}
Message: File has been copied successfully
Success: Yes
```

## Business Rules

1. **Personal Drive Only**: Copy operation supports only personal drive files and folders
2. **Shared Drive Limitation**: Files in shared drives (paths starting with `drives/b!`) are not supported
3. **Asynchronous Operation**: Microsoft Graph performs copy asynchronously for large files
4. **Replace Limitation**: Replace flag does not work for folders, only files
5. **Path Preservation**: Copied file retains original name unless target specifies new name
6. **Metadata Preservation**: File metadata (created date, modified date) may be updated

## Limitations

- **Personal Drive Only**: Does not support copying files in shared drives
- **Replace Not for Folders**: Replace If Exists flag does not work when copying folders
- **Asynchronous Behavior**: Large file copies may complete after response is returned
- **Response Timing**: Due to async nature, success message may appear before copy completes
- **No Progress Tracking**: Cannot track progress of large file copies
- **No Batch Copy**: Can only copy one file or folder at a time

## Best Practices

1. **Verify Paths**: Ensure source and target paths are correct before executing
2. **Use Create Folder**: Set Create Folder = true to avoid target folder errors
3. **Handle Async**: For large files, verify copy completion separately
4. **Check Permissions**: Ensure user has read access to source and write access to target
5. **Avoid Conflicts**: Use Replace = true only when intentionally overwriting
6. **Test First**: Test with small files before copying large files or folders
7. **Monitor Results**: Check Success flag and Message for operation status

## Common Use Cases

1. **File Backup**: Copy important files to backup folder
2. **File Distribution**: Copy files to shared team folders
3. **Archive Management**: Copy old files to archive folders
4. **Template Duplication**: Copy template files for new projects
5. **Folder Replication**: Copy entire folder structures
6. **Version Management**: Copy files before making changes

## Related Catalog Requests

- [Move](pages/Move.md) - Move files instead of copying
- [Delete](pages/Delete.md) - Delete files after copying
- [Write](pages/Write.md) - Upload new files
- [List Files](pages/ListFiles.md) - List files in source or target folder

## Technical Implementation

### Helper Classes

- **ManagementDaoImpl**: Handles copy operation logic
- **FileUtil**: Validates paths and formats
- **Microsoft Graph API**: Executes copy operation

### Telemetry Metrics

The following metrics are tracked:

- `onedrive.management.copy.success` - Successful copy operations
- `onedrive.management.copy.failure` - Failed copy operations
- `onedrive.management.copy.duration` - Time taken to initiate copy
- `onedrive.management.copy.file_size` - Size of files copied

### Copy Operation Flow

1. Validate input parameters
2. Check source file/folder exists
3. Validate target path
4. Create target folder if Create Folder = true
5. Check if target file exists
6. If exists and Replace = false, return error
7. Initiate copy operation via Microsoft Graph API
8. Return response (operation may continue asynchronously)

### Microsoft Graph API Endpoint

```
POST /me/drive/items/{item-id}/copy
```

**Request Body**:

```json
{
  "parentReference": {
    "path": "/drive/root:/target/path"
  },
  "name": "filename.ext"
}
```

## Troubleshooting

### Issue: "Please, Provide Valid Source Path"

**Possible Causes**:

- Source path does not exist
- Source path format is incorrect
- User does not have access to source

**Solutions**:

1. Verify source path exists in OneDrive
2. Check path format (should be `drive/root:/path/to/file`)
3. Ensure user has read permission on source
4. Use [List Files](pages/ListFiles.md) to verify correct path

### Issue: "Target folder does not exist"

**Possible Causes**:

- Target folder path does not exist
- Create Folder flag is set to false

**Solutions**:

1. Set Create Folder = true
2. Create target folder manually first
3. Verify target path is correct

### Issue: "File already exists at target"

**Possible Causes**:

- File with same name exists at target
- Replace flag is set to false

**Solutions**:

1. Set Replace = true to overwrite
2. Rename source file before copying
3. Delete existing target file first
4. Choose different target location

### Issue: Copy appears successful but file not found

**Possible Causes**:

- Asynchronous copy still in progress
- Large file taking time to copy

**Solutions**:

1. Wait a few moments and check again
2. Use [List Files](pages/ListFiles.md) to verify file in target
3. For large files, allow up to 5 minutes for completion

## Path Format Reference

### Personal Drive Paths

- **Root**: `drive/root:/`
- **Folder**: `drive/root:/FolderName`
- **Subfolder**: `drive/root:/FolderName/SubFolder`
- **File**: `drive/root:/FolderName/file.txt`

### Shared Drive Paths (Not Supported for Copy)

- **Format**: `drives/b!{driveId}/root:/path/to/file`
- **Note**: Copy operation does not support shared drives

## See Also

- [Move](pages/Move.md) - Move files to new location
- [Delete](pages/Delete.md) - Delete files
- [Rename](pages/Rename.md) - Rename files
- [Write](pages/Write.md) - Upload files
- [List Files](pages/ListFiles.md) - List files in folders

