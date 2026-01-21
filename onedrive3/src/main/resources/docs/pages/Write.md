[# Write

## Overview

Upload the content of a Krista File from the media server to OneDrive at the specified target path.

## Request Details

| Property          | Value                       |
|-------------------|-----------------------------|
| **Area**          | Management                  |
| **Type**          | CHANGE_SYSTEM               |
| **Retry Support** | Yes (with re-entry prompts) |

## Input Parameters

| Parameter Name | Type | Required | Description                                                                                                                  |
|----------------|------|----------|------------------------------------------------------------------------------------------------------------------------------|
| Krista File    | File | Yes      | The file to be uploaded to OneDrive. Supports files up to 50 MB in size                                                      |
| Target Path    | Text | Yes      | Full path where the file should be uploaded (e.g., /Documents/file.pdf). For shared drives, use format driveId:/path/to/file |

## Output Parameters

| Parameter Name | Type         | Description                                                                  |
|----------------|--------------|------------------------------------------------------------------------------|
| File           | Entity(File) | File entity representing the uploaded file with metadata                     |
| Success        | Yes/No       | Indicates whether the file upload was successful                             |
| Message        | Text         | Provides details about the upload operation result or any errors encountered |

## Validation Rules

| Field       | Validation Rule          | Error Message                   |
|-------------|--------------------------|---------------------------------|
| Krista File | Must not be null         | "Please select a file to upload. This field is required."       |
| Target Path | Must not be empty        | "Please enter a target path where the file should be uploaded."       |
| Krista File | Must be 50 MB or less    | "This file is larger than 50MB. Please use the download link instead of uploading directly, or split the file into smaller parts." |
| Target Path | Parent folder must exist | "The target folder doesn't exist. Please create the folder first or verify the path is correct."       |

## Error Handling

### Input Validation Errors (INPUT_ERROR)

**What it means**: The information you provided doesn't meet the requirements for uploading files.

**Common scenarios**:
- You didn't select a file to upload
- You didn't enter a target path
- The path format is incorrect

**How to fix it**:
1. **Read the error message** - It tells you exactly what's missing
2. Make sure you've selected a Krista File to upload
3. Enter a valid target path (like /Documents/file.pdf)
4. For shared drives, use the format: drives/{driveId}/root:/path/to/file

**Re-entry Support**: The system will prompt you to enter the correct target path

### File Size and Location Errors (LOGIC_ERROR)

**What it means**: There's an issue with the file size or destination folder.

**Common scenarios**:
- Your file is larger than 50 MB
- The target folder doesn't exist
- A file with the same name already exists at the destination

**How to fix it**:
1. **For large files (over 50 MB)**:
   - Split the file into smaller parts
   - Use a file compression tool to reduce the size
   - Upload the file directly through OneDrive's web interface

2. **For missing target folder**:
   - Create the folder in OneDrive first
   - Verify the path is correct
   - Check you have permission to access the folder

3. **For existing files**:
   - Choose a different file name
   - Delete the existing file first
   - Use a different target path

**Re-entry Support**: The system will prompt you to enter a different target path

### Service and Connection Errors (SYSTEM_ERROR)

**What it means**: There's a problem connecting to Microsoft OneDrive or completing the upload.

**Common scenarios**:
- Your internet connection is unstable
- Microsoft OneDrive service is temporarily unavailable
- The upload operation timed out
- An unexpected error occurred

**How to fix it**:
1. **Check your connection**: Make sure you have a stable internet connection
2. **Wait and retry**: If OneDrive is experiencing issues, wait a few minutes and try again
3. **Check Microsoft's status**: Visit [Microsoft 365 Status](https://status.office.com) for known outages
4. **For large files**: The upload may take longer; ensure your connection stays stable
5. **Verify authentication**: Make sure you're still signed in (session hasn't expired)

### Permission Errors (Authorization)

**What it means**: You don't have permission to upload files to the specified location.

**Common scenarios**:
- You don't have write permission on the target folder
- The folder is read-only
- Organization policies restrict uploads to this location

**How to fix it**:
1. **Check your permissions**:
   - Verify you have write access to the target folder
   - Ask the folder owner to grant you edit permissions
   - Try uploading to a folder you own

2. **Contact your administrator**:
   - If you believe you should have access, contact your IT administrator
   - Organization policies may need to be adjusted

3. **Use a different location**:
   - Try uploading to your personal OneDrive folder
   - Choose a folder where you have confirmed write access

## Usage Examples

### Example 1: Upload PDF File

**Input**: `Krista File: [report.pdf, 2.5 MB], Target Path: drive/root:/Documents/report.pdf`
**Output**: `Success: Yes, Message: File uploaded successfully, File: [Entity with metadata]`

### Example 2: Upload to Shared Drive

**Input**: `Krista File: [data.xlsx, 1.8 MB], Target Path: drives/b!xyz789/root:/TeamFiles/data.xlsx`
**Output**: `Success: Yes, Message: File uploaded successfully, File: [Entity]`

### Example 3: File Too Large

**Input**: `Krista File: [video.mp4, 75 MB], Target Path: drive/root:/Videos/video.mp4`
**Output**: `Success: No, Message: File size exceeds 50 MB limit, File: null`

## Business Rules

1. **50 MB Size Limit**: Files larger than 50 MB cannot be uploaded
2. **Shared Drives Supported**: Can upload to both personal and shared drives
3. **Overwrite**: Overwrites existing file if target path already exists
4. **Automatic Folder Creation**: Does not create parent folders automatically
5. **File Metadata**: Preserves original file name and extension

## Limitations

- **50 MB Maximum**: Cannot upload files larger than 50 MB
- **No Batch Upload**: Can only upload one file at a time
- **No Folder Upload**: Cannot upload entire folders
- **No Resume**: Cannot resume interrupted uploads
- **Overwrites Existing**: Automatically overwrites if file exists

## Best Practices

1. Check file size is under 50 MB before uploading
2. Verify target folder exists using [List Folders](pages/ListFolders.md)
3. Use meaningful file names
4. Handle large files (close to 50 MB) carefully
5. Check Success flag before assuming upload completed
6. Use [Create Folder](pages/CreateFolder.md) to create target folder if needed

## Common Use Cases

1. Uploading documents from workflows
2. Saving generated reports to OneDrive
3. Backing up files to cloud storage
4. Uploading processed data files
5. Storing workflow outputs

## Related Catalog Requests

- [WriteText](pages/WriteText.md) - Upload text content directly
- [Download as File](pages/DownloadAsFile.md) - Download files from OneDrive
- [Create Folder](pages/CreateFolder.md) - Create target folder
- [List Files](pages/ListFiles.md) - Verify upload success

## Technical Implementation

**Helper Classes**: ManagementDaoImpl
**Telemetry Metrics**: `onedrive.management.write.success`, `onedrive.management.write.failure`,
`onedrive.management.write.duration`, `onedrive.management.write.file_size`
**API Endpoint**: `PUT /me/drive/root:/{path}:/content`

## Troubleshooting

**Issue: "File size exceeds 50 MB limit"** - Compress file, split into smaller parts, or use alternative upload method
**Issue: "Target folder not found"** - Create folder using Create Folder, verify path is correct
**Issue: Upload timeout** - Retry upload, check network connectivity, reduce file size
**Issue: "Insufficient permissions"** - Verify write permission on target folder

## File Size Considerations

| File Size | Recommendation                        |
|-----------|---------------------------------------|
| 0-10 MB   | Optimal for upload                    |
| 10-30 MB  | Good performance                      |
| 30-50 MB  | Use with caution, slower upload       |
| 50+ MB    | Not supported, use alternative method |

## See Also

- [WriteText](pages/WriteText.md) - Upload text content
- [Download as File](pages/DownloadAsFile.md) - Download files
- [Create Folder](pages/CreateFolder.md) - Create folders
- [Read](pages/Read.md) - Read text files
