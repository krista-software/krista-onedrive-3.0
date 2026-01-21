# Download as File

## Overview

Retrieves a file from OneDrive and provides it as a Krista File object that can be used in workflows and processed by
other Krista operations.

## Request Details

| Property          | Value                       |
|-------------------|-----------------------------|
| **Area**          | Management                  |
| **Type**          | CHANGE_SYSTEM               |
| **Retry Support** | Yes (with re-entry prompts) |

## Input Parameters

| Parameter Name | Type | Required | Description                                                                                                                                                                                           |
|----------------|------|----------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Source Path    | Text | Yes      | Full path to the file in the cloud storage that should be downloaded (e.g., /Documents/file.pdf or /Sites/TeamSite/Shared Documents/report.xlsx). For shared drives, use format driveId:/path/to/file |

## Output Parameters

| Parameter Name | Type      | Description                                                               |
|----------------|-----------|---------------------------------------------------------------------------|
| Krista File    | File      | The downloaded file as a Krista File object that can be used in workflows |
| Success        | Yes/No    | Indicates whether the download operation was successful                   |
| Message        | Rich Text | Provides details about the operation result or any errors encountered     |

## Validation Rules

| Field       | Validation Rule              | Error Message                   |
|-------------|------------------------------|---------------------------------|
| Source Path | Must not be empty            | "Source path is required"       |
| Source Path | Must be valid OneDrive path  | "Invalid source path format"    |
| Source Path | File must exist              | "Source file not found"         |
| Source Path | Must be a file, not a folder | "Cannot download folders"       |
| File Size   | Must be 50 MB or less        | "File size exceeds 50 MB limit" |

## Error Handling

### INPUT_ERROR

**When it occurs**: Invalid input parameters

**Common scenarios**:

- Empty source path
- Invalid path format
- Malformed path string

**Response**:

```json
{
  "Success": false,
  "Message": "Invalid input: Source path is required",
  "Krista File": null
}
```

**Re-entry Support**: If source path is invalid or file not found, system prompts to re-enter source path

### LOGIC_ERROR

**When it occurs**: Business logic violations

**Common scenarios**:

- Source file does not exist
- Source path points to a folder
- File size exceeds 50 MB limit

**Response**:

```json
{
  "Success": false,
  "Message": "File size exceeds 50 MB limit",
  "Krista File": null
}
```

**Re-entry Support**: System prompts to re-enter source path

### SYSTEM_ERROR

**When it occurs**: System-level failures

**Common scenarios**:

- Network connectivity issues
- Microsoft Graph API unavailable
- Timeout during download
- Internal extension error
- File corruption during download

**Response**:

```json
{
  "Success": false,
  "Message": "Failed to download file: [error details]",
  "Krista File": null
}
```

### Authorization Errors

**When it occurs**: Permission issues

**Common scenarios**:

- User does not have read permission on file
- File is protected or encrypted
- Access restrictions prevent download

**Response**:

```json
{
  "Success": false,
  "Message": "Insufficient permissions to download file",
  "Krista File": null
}
```

## Usage Examples

### Example 1: Download PDF File

**Input**:

```
Source Path: drives/b!absdbfbbvwevb/root:/Reports/Q4_Report.pdf
```

**Output**:

```
Success: Yes
Message: File is ready to download
Krista File: [File object: Q4_Report.pdf, 2.5 MB]
```

### Example 2: Download Excel File

**Input**:

```
Source Path: drives/b!xyz789/root:/Data/sales_data.xlsx
```

**Output**:

```
Success: Yes
Message: File is ready to download
Krista File: [File object: sales_data.xlsx, 1.8 MB]
```

### Example 3: File Too Large

**Input**:

```
Source Path: drives/b!abc123/root:/Videos/presentation.mp4
```

**Output**:

```
Success: No
Message: File size exceeds 50 MB limit
Krista File: null
```

### Example 4: File Not Found

**Input**:

```
Source Path: drives/b!abc123/root:/Documents/missing.pdf
```

**Output**:

```
Success: No
Message: Source file not found
Krista File: null
```

## Business Rules

1. **50 MB Size Limit**: Files larger than 50 MB cannot be downloaded
2. **Shared Drives Supported**: Download as File supports shared drives
3. **Files Only**: Cannot download folders
4. **Automatic Zip Conversion**: Certain file extensions are automatically downloaded as ZIP
5. **Krista File Object**: Downloaded file is available as Krista File for workflow use

## Limitations

- **50 MB Maximum**: Cannot download files larger than 50 MB
- **No Folder Download**: Cannot download entire folders
- **No Batch Download**: Can only download one file at a time
- **Automatic ZIP**: Some file types (html, php5, pht, phtml, shtml, asa, cer, asax, swf, xap, jsp, exe, js) are
  downloaded as ZIP
- **Memory Usage**: Large files consume memory during download

## Best Practices

1. **Check File Size**: Verify file is under 50 MB before downloading
2. **Use for Processing**: Best for files that need to be processed in workflows
3. **Handle ZIP Files**: Be aware of automatic ZIP conversion for certain extensions
4. **Error Handling**: Always check Success flag before using Krista File
5. **Memory Management**: Download large files (close to 50 MB) carefully
6. **Use Download as Link**: For files over 50 MB, use [Download as Link](pages/DownloadAsLink.md) instead
7. **Verify File Type**: Ensure file type is compatible with downstream processing

## Common Use Cases

1. **Document Processing**: Download documents for automated processing
2. **Data Import**: Download data files for import into systems
3. **Report Generation**: Download templates for report generation
4. **File Transformation**: Download files for format conversion
5. **Workflow Integration**: Download files as input to workflow steps
6. **Backup Operations**: Download files for backup purposes
7. **Content Analysis**: Download files for content analysis or extraction

## Related Catalog Requests

- [Download as Link](pages/DownloadAsLink.md) - Get download link instead of file object
- [Read](pages/Read.md) - Read text file contents directly
- [List Files](pages/ListFiles.md) - List files to find download targets
- [Write](pages/Write.md) - Upload files to OneDrive

## Technical Implementation

### Helper Classes

- **ManagementDaoImpl**: Handles download operation logic
- **Microsoft Graph API**: Downloads file content
- **FileUtil**: Validates paths and file sizes
- **KristaFileConverter**: Converts downloaded content to Krista File object

### Telemetry Metrics

The following metrics are tracked:

- `onedrive.management.download_as_file.success` - Successful downloads
- `onedrive.management.download_as_file.failure` - Failed downloads
- `onedrive.management.download_as_file.duration` - Time taken to download
- `onedrive.management.download_as_file.file_size` - Size of downloaded files

### Download Operation Flow

1. Validate input parameter (source path)
2. Check source file exists
3. Verify file is not a folder
4. Check file size is under 50 MB
5. Verify user has read permission
6. Download file content from Microsoft Graph API
7. Check if file extension requires ZIP conversion
8. Convert to ZIP if necessary
9. Create Krista File object
10. Return file in response

### Microsoft Graph API Endpoint

```
GET /me/drive/items/{item-id}/content
```

**Response**: Binary file content

### Automatic ZIP Conversion

Files with the following extensions are automatically downloaded as ZIP:

- html
- php5
- pht
- phtml
- shtml
- asa
- cer
- asax
- swf
- xap
- jsp
- exe
- js

**Note**: This is a security measure to prevent execution of potentially harmful files.

## Troubleshooting

### Issue: "Source file not found"

**Possible Causes**:

- Source path does not exist
- Source path format is incorrect
- User does not have access to source

**Solutions**:

1. Verify source path exists using [List Files](pages/ListFiles.md)
2. Check path format for shared drives: `drives/b!{driveId}/root:/path/to/file`
3. Ensure user has read permission on the file
4. Check for typos in path

### Issue: "File size exceeds 50 MB limit"

**Possible Causes**:

- File is larger than 50 MB
- Attempting to download large media file

**Solutions**:

1. Use [Download as Link](pages/DownloadAsLink.md) instead
2. Compress file in OneDrive before downloading
3. Split large file into smaller parts
4. Use alternative download method outside Krista

### Issue: "Cannot download folders"

**Possible Causes**:

- Source path points to a folder
- Attempting to download folder contents

**Solutions**:

1. Specify a file path, not a folder path
2. Use [List Files](pages/ListFiles.md) to get individual file paths
3. Download files individually

### Issue: File downloaded as ZIP unexpectedly

**Possible Causes**:

- File extension is in automatic ZIP conversion list
- Security policy converts file to ZIP

**Solutions**:

1. This is expected behavior for security
2. Extract ZIP file after download
3. Rename file extension before downloading (if appropriate)
4. Handle ZIP extraction in workflow

### Issue: Download timeout

**Possible Causes**:

- File is very large (close to 50 MB)
- Slow network connection
- Microsoft API experiencing delays

**Solutions**:

1. Retry the download
2. Check network connectivity
3. Use [Download as Link](pages/DownloadAsLink.md) for large files
4. Contact support if issue persists

## File Size Considerations

### Size Limits

| File Size | Recommendation                      |
|-----------|-------------------------------------|
| 0-10 MB   | Download as File - Optimal          |
| 10-30 MB  | Download as File - Good             |
| 30-50 MB  | Download as File - Use with caution |
| 50+ MB    | Use Download as Link instead        |

### Performance Impact

- **Small files (< 10 MB)**: Minimal impact, fast download
- **Medium files (10-30 MB)**: Moderate impact, acceptable performance
- **Large files (30-50 MB)**: Significant impact, slower download, higher memory usage

## Comparison with Download as Link

| Aspect             | Download as File    | Download as Link |
|--------------------|---------------------|------------------|
| **Output**         | Krista File object  | URL link         |
| **Size Limit**     | 50 MB               | No limit         |
| **Expiration**     | No expiration       | 1 hour           |
| **Shared Drives**  | Supported           | Supported        |
| **Folders**        | Not supported       | Not supported    |
| **Use Case**       | Workflow processing | External sharing |
| **ZIP Conversion** | Yes (certain types) | No               |

## Path Format Reference

### Personal Drive Paths

- **File**: `drive/root:/Documents/file.pdf`
- **Subfolder File**: `drive/root:/Folder/SubFolder/file.xlsx`

### Shared Drive Paths

- **Format**: `drives/b!{driveId}/root:/path/to/file`
- **Example**: `drives/b!absdbfbbvwevb/root:/Reports/report.pdf`

### Relative Paths (Personal Drive)

- **File**: `/Documents/file.txt`
- **Subfolder File**: `/Archive/OldFiles/data.csv`

## See Also

- [Download as Link](pages/DownloadAsLink.md) - Get download link for large files
- [Read](pages/Read.md) - Read text file contents directly
- [Write](pages/Write.md) - Upload files to OneDrive
- [List Files](pages/ListFiles.md) - List files in folders

