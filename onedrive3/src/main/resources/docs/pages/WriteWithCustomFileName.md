# Write with Custom File Name

## Overview

Upload the content of a Krista File from the media server to OneDrive at the specified target path with an optional
custom file name.

## Request Details

| Property          | Value                       |
|-------------------|-----------------------------|
| **Area**          | OneDrive                    |
| **Type**          | CHANGE_SYSTEM               |
| **Retry Support** | Yes (with re-entry prompts) |

## Input Parameters

| Parameter Name | Type | Required | Description                                                                                                                                |
|----------------|------|----------|--------------------------------------------------------------------------------------------------------------------------------------------|
| Target Path    | Text | Yes      | Full path where the file should be uploaded (e.g., /Documents or /Folders/MyFolder). For shared drives, use format driveId:/path/to/folder |
| Custom Name    | Text | No       | Optional custom name to use for the uploaded file instead of the original filename. Include file extension (e.g., report.pdf)              |
| Krista File    | File | Yes      | The file to be uploaded to OneDrive. Supports files up to 50 MB in size                                                                    |

## Output Parameters

| Parameter Name | Type         | Description                                                                  |
|----------------|--------------|------------------------------------------------------------------------------|
| File           | Entity(File) | File entity representing the uploaded file with metadata                     |
| Success        | Yes/No       | Indicates whether the file upload with custom name was successful            |
| Message        | Text         | Provides details about the upload operation result or any errors encountered |

## Validation Rules

| Field       | Validation Rule                  | Error Message                                |
|-------------|----------------------------------|----------------------------------------------|
| Krista File | Must not be null                 | "Krista File is required"                    |
| Target Path | Must not be empty                | "Target path is required"                    |
| Krista File | Must be 50 MB or less            | "File size exceeds 50 MB limit"              |
| Target Path | Folder must exist                | "Target folder not found"                    |
| Custom Name | Must not contain path separators | "Custom name cannot contain path separators" |

## Error Handling

### INPUT_ERROR

**When it occurs**: Invalid input parameters
**Common scenarios**: Null file, empty target path, invalid custom name, path separators in custom name
**Response**: `{"Success": false, "Message": "Invalid input: Krista File is required", "File": null}`
**Re-entry Support**: System prompts to re-enter target path or custom name

### LOGIC_ERROR

**When it occurs**: Business logic violations
**Common scenarios**: File too large, target folder not found, file already exists with custom name
**Response**: `{"Success": false, "Message": "File size exceeds 50 MB limit", "File": null}`
**Re-entry Support**: System prompts to re-enter target path or custom name

### SYSTEM_ERROR

**When it occurs**: System-level failures
**Common scenarios**: Network issues, upload timeout, Microsoft Graph API unavailable
**Response**: `{"Success": false, "Message": "Failed to upload file: [error details]", "File": null}`

### Authorization Errors

**When it occurs**: Permission issues
**Common scenarios**: User does not have write permission on target folder
**Response**: `{"Success": false, "Message": "Insufficient permissions to upload file", "File": null}`

## Usage Examples

### Example 1: Upload with Custom Name

**Input**: `Target Path: drive/root:/Documents, Custom Name: monthly_report.pdf, Krista File: [original.pdf, 2.5 MB]`
**Output**: `Success: Yes, Message: File uploaded successfully, File: [Entity with name "monthly_report.pdf"]`

### Example 2: Upload with Original Name

**Input**: `Target Path: drive/root:/Documents, Custom Name: , Krista File: [report.pdf, 2.5 MB]`
**Output**: `Success: Yes, Message: File uploaded successfully, File: [Entity with name "report.pdf"]`

### Example 3: Upload to Shared Drive with Custom Name

**Input**:
`Target Path: drives/b!xyz789/root:/TeamFiles, Custom Name: team_data_2024.xlsx, Krista File: [data.xlsx, 1.8 MB]`
**Output**: `Success: Yes, Message: File uploaded successfully, File: [Entity with name "team_data_2024.xlsx"]`

### Example 4: File Too Large

**Input**: `Target Path: drive/root:/Videos, Custom Name: presentation.mp4, Krista File: [video.mp4, 75 MB]`
**Output**: `Success: No, Message: File size exceeds 50 MB limit, File: null`

## Business Rules

1. **50 MB Size Limit**: Files larger than 50 MB cannot be uploaded
2. **Shared Drives Supported**: Can upload to both personal and shared drives
3. **Optional Custom Name**: If not provided, uses original file name
4. **Overwrite**: Overwrites existing file if target path and name already exist
5. **Extension Preserved**: Custom name should include file extension

## Limitations

- **50 MB Maximum**: Cannot upload files larger than 50 MB
- **No Batch Upload**: Can only upload one file at a time
- **No Folder Upload**: Cannot upload entire folders
- **No Resume**: Cannot resume interrupted uploads
- **Overwrites Existing**: Automatically overwrites if file exists
- **No Path in Custom Name**: Custom name cannot contain path separators

## Best Practices

1. Check file size is under 50 MB before uploading
2. Include file extension in custom name
3. Verify target folder exists using [List Folders](pages/ListFolders.md)
4. Use meaningful custom names for organization
5. Handle large files (close to 50 MB) carefully
6. Check Success flag before assuming upload completed
7. Use [Create Folder](pages/CreateFolder.md) to create target folder if needed

## Common Use Cases

1. Uploading files with standardized naming conventions
2. Renaming files during upload for organization
3. Uploading generated reports with timestamp names
4. Saving workflow outputs with descriptive names
5. Uploading files with version numbers
6. Batch processing with custom naming

## Related Catalog Requests

- [Write](pages/Write.md) - Upload without custom name
- [WriteText](pages/WriteText.md) - Upload text content
- [Download as File](pages/DownloadAsFile.md) - Download files
- [Create Folder](pages/CreateFolder.md) - Create target folder
- [List Files](pages/ListFiles.md) - Verify upload success

## Technical Implementation

**Helper Classes**: OperationsManagementDao, FileUtil
**Telemetry Metrics**: `onedrive.onedrive.write_custom_name.success`, `onedrive.onedrive.write_custom_name.failure`,
`onedrive.onedrive.write_custom_name.duration`
**API Endpoint**: `PUT /me/drive/root:/{path}/{customName}:/content`

### Upload Operation Flow

1. Validate input parameters (file, target path, custom name)
2. Check file size is under 50 MB
3. Verify target folder exists
4. Determine file name (custom name or original)
5. Validate custom name (no path separators)
6. Upload file content to Microsoft Graph API
7. Create File entity with metadata
8. Return response with File entity

## Troubleshooting

**Issue: "File size exceeds 50 MB limit"** - Compress file, split into smaller parts, or use alternative upload method
**Issue: "Target folder not found"** - Create folder using Create Folder, verify path is correct
**Issue: "Custom name cannot contain path separators"** - Remove / or \ from custom name, use Target Path for location
**Issue: Upload timeout** - Retry upload, check network connectivity, reduce file size
**Issue: "Insufficient permissions"** - Verify write permission on target folder

## Custom Name Examples

### Good Custom Names

- `monthly_report_2024_10.pdf`
- `sales_data.xlsx`
- `config_v2.json`
- `backup_20241014.zip`
- `team_photo.jpg`

### Bad Custom Names

- `Documents/report.pdf` (contains path separator)
- `file?.txt` (contains invalid character)
- `report` (missing extension)
- `my:file.txt` (contains invalid character)

## File Size Considerations

| File Size | Recommendation                        |
|-----------|---------------------------------------|
| 0-10 MB   | Optimal for upload                    |
| 10-30 MB  | Good performance                      |
| 30-50 MB  | Use with caution, slower upload       |
| 50+ MB    | Not supported, use alternative method |

## Comparison with Write

| Aspect          | Write with Custom File Name        | Write                 |
|-----------------|------------------------------------|-----------------------|
| **Custom Name** | Supported                          | Not supported         |
| **Use Case**    | Rename during upload               | Keep original name    |
| **Parameters**  | 3 (Target Path, Custom Name, File) | 2 (Target Path, File) |
| **Flexibility** | High                               | Standard              |
| **Area**        | OneDrive                           | Management            |

## Workflow Integration

### Example Workflow: Upload with Timestamp

1. Get current date/time
2. Format as string (e.g., "2024-10-14_10-30")
3. Construct custom name: "report_" + timestamp + ".pdf"
4. Execute Write with Custom File Name:
    - Target Path: drive/root:/Reports
    - Custom Name: constructed name
    - Krista File: report file
5. Verify Success flag

### Example Workflow: Standardized Naming

1. Get file from workflow
2. Determine file type
3. Construct standard name based on type:
    - Invoice: "INV_" + invoice_number + ".pdf"
    - Report: "RPT_" + date + ".xlsx"
4. Execute Write with Custom File Name
5. Log upload result

## See Also

- [Write](pages/Write.md) - Upload without custom name
- [WriteText](pages/WriteText.md) - Upload text content
- [Download as File](pages/DownloadAsFile.md) - Download files
- [Create Folder](pages/CreateFolder.md) - Create folders
- [Rename](pages/Rename.md) - Rename existing files

