# Copy With Name

## Overview

Atomically copies a file into the target folder with a specified name, avoiding renames and preventing collisions during
parallel execution. Unlike the standard [Copy](Copy.md) request which retains the original file name, this request
allows you to specify a custom name for the copied file in a single operation.

## Request Details

| Property          | Value          |
|-------------------|----------------|
| **Area**          | Management     |
| **Type**          | CHANGE_SYSTEM  |
| **Retry Support** | No             |

## Input Parameters

| Parameter Name   | Type   | Required | Description                                                                 |
|------------------|--------|----------|-----------------------------------------------------------------------------|
| Source           | Text   | Yes      | Full path to the source file to be copied                                   |
| Target           | Text   | Yes      | Full path to the target folder                                              |
| Target File Name | Text   | Yes      | Desired name for the copied file (e.g., 2026-04-16_ACN-SLOB-Worksheet.xlsx) |
| Replace          | Yes/No | No       | If true, replaces any existing file at the target location (default: false) |
| Create Folder    | Yes/No | No       | If true, creates any new folders in the target path (default: false)        |

> 📌 **Root folder path:** For an item in your OneDrive **root folder**, prefix its name with **`drive/root:/`** — e.g. **`drive/root:/report.pdf`** (`drive/root:/` alone refers to the root itself). For a shared drive / SharePoint / Teams library, use the Drive Path (`drives/{driveId}/root:/…`) returned by [Get Drives](pages/GetDrives.md).

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

| Field            | Validation Rule                                        | Error Message                                                                           |
|------------------|--------------------------------------------------------|-----------------------------------------------------------------------------------------|
| Source           | Must not be empty                                      | "Please enter a source path. This field is required."                                   |
| Source           | Must be valid OneDrive path                            | "The source path format isn't valid. Please use a format like /Documents/file.txt"      |
| Source           | File must exist                                        | "We couldn't find the file at the source path. Please verify it exists in your OneDrive." |
| Target           | Must not be empty                                      | "Please enter a target path. This field is required."                                   |
| Target           | Must be valid OneDrive path                            | "The target path format isn't valid. Please use a format like /Documents/NewFolder"     |
| Target           | Parent folder must exist (unless Create Folder = true) | "The target folder doesn't exist. Either create it first or enable 'Create Folder'."    |
| Target File Name | Must not be empty                                      | "Please enter a target file name. This field is required."                              |

## Error Handling

### Input Validation Errors (INPUT_ERROR)

**What it means**: The information you provided doesn't meet the requirements for the copy with name operation.

**Common scenarios**:

- You didn't enter a source path, target path, or target file name
- The path format is incorrect (should be like /Documents/file.txt)

**How to fix it**:

1. **Read the error message** - It tells you exactly what's wrong
2. Make sure source path, target path, and target file name are all filled in
3. Use the correct path format: /FolderName/SubFolder/file.txt
4. For shared drives, use: drives/{driveId}/root:/path/to/file

### File and Folder Errors (LOGIC_ERROR)

**What it means**: There's an issue with the files or folders involved in the operation.

**Common scenarios**:

- The source file doesn't exist at the path you specified
- The target folder doesn't exist and you haven't enabled "Create Folder"
- A file with the target name already exists and you haven't enabled "Replace"

**How to fix it**:

1. **Verify the source exists**: Check the source path is correct and the file exists
2. **Check the target location**: If the target folder doesn't exist, set "Create Folder" to Yes
3. **Handle conflicts**: If a file with the same name already exists, set "Replace" to Yes

### Service and Connection Errors (SYSTEM_ERROR)

**What it means**: There's a problem connecting to Microsoft OneDrive or completing the operation.

**How to fix it**:

1. **Check your connection**: Make sure you have a stable internet connection
2. **Wait and retry**: If OneDrive is experiencing issues, wait a few minutes and try again
3. **Verify authentication**: Make sure you're still signed in (session hasn't expired)

**Response**:

```json
{
  "Success": false,
  "Message": "Failed to copy file: [error details]"
}
```

## Usage Examples

### Example 1: Copy File with Date-Prefixed Name

**Input**:

```
Source: drive/root:/Templates/SLOB-Worksheet.xlsx
Target: drive/root:/Reports/April
Target File Name: 2026-04-16_ACN-SLOB-Worksheet.xlsx
Replace: No
Create Folder: No
```

**Output**:

```
File: {
  "Name": "2026-04-16_ACN-SLOB-Worksheet.xlsx",
  "Extension": ".xlsx",
  "Absolute Path": "drive/root:/Reports/April/2026-04-16_ACN-SLOB-Worksheet.xlsx"
}
Message: File has been copied successfully
Success: Yes
```

### Example 2: Copy with Replace and Create Folder

**Input**:

```
Source: drive/root:/Documents/template.docx
Target: drive/root:/Output/NewFolder
Target File Name: final-report.docx
Replace: Yes
Create Folder: Yes
```

**Output**:

```
File: {
  "Name": "final-report.docx",
  "Extension": ".docx",
  "Absolute Path": "drive/root:/Output/NewFolder/final-report.docx"
}
Message: File has been copied successfully
Success: Yes
```

### Example 3: Copy with Custom Name Only

**Input**:

```
Source: drive/root:/Templates/invoice.pdf
Target: drive/root:/Invoices
Target File Name: INV-2026-0042.pdf
Replace: No
Create Folder: No
```

**Output**:

```
File: {
  "Name": "INV-2026-0042.pdf",
  "Extension": ".pdf",
  "Absolute Path": "drive/root:/Invoices/INV-2026-0042.pdf"
}
Message: File has been copied successfully
Success: Yes
```

## Business Rules

1. **Personal Drive Only**: Copy With Name supports only personal drive files
2. **Atomic Operation**: The copy and rename are performed as a single operation — no intermediate file name is visible
3. **Parallel Safe**: Multiple workflows can copy to the same folder without name collisions when using unique target names
4. **Asynchronous Operation**: Microsoft Graph may perform the copy asynchronously for large files

## Limitations

- **Personal Drive Only**: Does not support copying files in shared drives
- **Files Only**: Does not support copying folders (use the standard [Copy](Copy.md) request for folders)
- **Asynchronous Behavior**: Large file copies may complete after the response is returned
- **No Batch Copy**: Can only copy one file at a time

## Best Practices

1. **Use Unique Names**: Include timestamps or identifiers in target file names to avoid collisions
2. **Enable Create Folder**: Set Create Folder = true to avoid target folder errors
3. **Validate Names**: Ensure target file names don't contain invalid characters (: * ? " < > | / \)
4. **Include Extension**: Always include the file extension in the target file name
5. **Parallel Workflows**: Use this request instead of Copy + Rename when multiple workflows operate on the same folder

## Common Use Cases

1. **Date-Stamped Copies**: Copy template files with date-prefixed names for daily/weekly reports
2. **Parallel Processing**: Multiple workflows copying files to the same folder with unique names
3. **Invoice Generation**: Copy invoice templates with unique invoice numbers
4. **Archival**: Copy files to archive folders with timestamped names

## Related Catalog Requests

- [Copy](Copy.md) - Copy files retaining original name
- [Move](Move.md) - Move files instead of copying
- [Rename](Rename.md) - Rename existing files
- [Write](Write.md) - Upload new files
- [Write with Custom File Name](WriteWithCustomFileName.md) - Upload with custom filename

## Technical Implementation

### Copy With Name Operation Flow

1. Validate input parameters (source, target, target file name)
2. Check source file exists
3. Validate target path
4. Create target folder if Create Folder = true
5. Check if target file with specified name exists
6. If exists and Replace = false, return error
7. Initiate copy operation via Microsoft Graph API with specified name
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
  "name": "custom-filename.ext"
}
```

## Path Format Reference

### Personal Drive Paths

- **Root**: `drive/root:/`
- **Folder**: `drive/root:/FolderName`
- **Subfolder**: `drive/root:/FolderName/SubFolder`
- **File**: `drive/root:/FolderName/file.txt`

### Shared Drive Paths (Not Supported for Copy With Name)

- **Format**: `drives/b!{driveId}/root:/path/to/file`
- **Note**: Copy With Name operation does not support shared drives

## See Also

- [Copy](Copy.md) - Standard copy operation
- [Move](Move.md) - Move files to new location
- [Rename](Rename.md) - Rename files
- [Write](Write.md) - Upload files
- [List Files](ListFiles.md) - List files in folders
