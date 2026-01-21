# Download as Link

## Overview

Retrieves a file from OneDrive and provides a temporary download link that can be used to access the file directly.

## Request Details

| Property          | Value                       |
|-------------------|-----------------------------|
| **Area**          | Management                  |
| **Type**          | CHANGE_SYSTEM               |
| **Retry Support** | Yes (with re-entry prompts) |

## Input Parameters

| Parameter Name | Type | Required | Description                                                                                                                                                                 |
|----------------|------|----------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Source         | Text | Yes      | Full path to the file in the cloud storage (e.g., /Documents/file.pdf or /Sites/TeamSite/Shared Documents/report.xlsx). For shared drives, use format driveId:/path/to/file |

## Output Parameters

| Parameter Name | Type      | Description                                                           |
|----------------|-----------|-----------------------------------------------------------------------|
| Link           | Rich Text | The generated hyperlink to access the file directly in OneDrive       |
| Message        | Text      | Provides details about the operation result or any errors encountered |
| Success        | Yes/No    | Indicates whether the link generation was successful                  |

## Validation Rules

| Field  | Validation Rule              | Error Message                               |
|--------|------------------------------|---------------------------------------------|
| Source | Must not be empty            | "Source path is required"                   |
| Source | Must be valid OneDrive path  | "Invalid source path format"                |
| Source | File must exist              | "Source file not found"                     |
| Source | Must be a file, not a folder | "Cannot generate download link for folders" |

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
  "Link": ""
}
```

**Re-entry Support**: If source path is invalid or file not found, system prompts to re-enter source path

### LOGIC_ERROR

**When it occurs**: Business logic violations

**Common scenarios**:

- Source file does not exist
- Source path points to a folder
- File type not supported for download links

**Response**:

```json
{
  "Success": false,
  "Message": "Source file not found",
  "Link": ""
}
```

**Re-entry Support**: System prompts to re-enter source path

### SYSTEM_ERROR

**When it occurs**: System-level failures

**Common scenarios**:

- Network connectivity issues
- Microsoft Graph API unavailable
- Timeout during link generation
- Internal extension error

**Response**:

```json
{
  "Success": false,
  "Message": "Failed to generate download link: [error details]",
  "Link": ""
}
```

### Authorization Errors

**When it occurs**: Permission issues

**Common scenarios**:

- User does not have read permission on file
- File is protected or encrypted
- Sharing restrictions prevent link generation

**Response**:

```json
{
  "Success": false,
  "Message": "Insufficient permissions to access file",
  "Link": ""
}
```

## Usage Examples

### Example 1: Generate Download Link for PDF

**Input**:

```
Source: drives/b!absdbfbbvwevb/root:/Reports/Q4_Report.pdf
```

**Output**:

```
Success: Yes
Message: To Download file, click on the URL
Link: https://contoso-my.sharepoint.com/personal/user_contoso_com/_layouts/15/download.aspx?UniqueId=abc123...
```

### Example 2: Generate Link for Excel File

**Input**:

```
Source: drives/b!xyz789/root:/Data/sales_data.xlsx
```

**Output**:

```
Success: Yes
Message: To Download file, click on the URL
Link: https://contoso-my.sharepoint.com/personal/user_contoso_com/_layouts/15/download.aspx?UniqueId=def456...
```

### Example 3: File Not Found

**Input**:

```
Source: drives/b!abc123/root:/Documents/missing.pdf
```

**Output**:

```
Success: No
Message: Source file not found
Link: 
```

## Business Rules

1. **Shared Drives Supported**: Download as Link supports shared drives
2. **Files Only**: Cannot generate download links for folders
3. **Link Expiration**: Download links are valid for 1 hour only
4. **Temporary Access**: Links provide temporary access without authentication
5. **No Folder Support**: Operation does not support downloading folders

## Limitations

- **1 Hour Expiration**: Download links expire after 1 hour
- **Files Only**: Cannot generate links for folders
- **No Batch Links**: Can only generate one link at a time
- **No Custom Expiration**: Cannot customize link expiration time
- **No Password Protection**: Links are not password-protected
- **No Download Tracking**: Cannot track who downloads via the link

## Best Practices

1. **Use Immediately**: Use generated links within 1 hour of creation
2. **Verify File Exists**: Use [List Files](pages/ListFiles.md) to verify file before generating link
3. **Share Securely**: Share links only with intended recipients
4. **Monitor Expiration**: Regenerate links if they expire
5. **Use for Temporary Access**: Best for temporary file sharing needs
6. **Consider Alternatives**: For long-term sharing, use [Share File](pages/ShareFile.md) instead
7. **Check File Size**: Ensure file size is appropriate for download

## Common Use Cases

1. **Temporary File Sharing**: Share files temporarily without granting permanent access
2. **Email Attachments**: Include download links in emails instead of large attachments
3. **Workflow Integration**: Provide download links in automated workflows
4. **Report Distribution**: Distribute reports via download links
5. **Quick Access**: Provide quick access to files for external users
6. **Time-Limited Sharing**: Share files with automatic expiration

## Related Catalog Requests

- [Download as File](pages/DownloadAsFile.md) - Download file as Krista File object
- [Share File](pages/ShareFile.md) - Share files with permanent access
- [List Files](pages/ListFiles.md) - List files to find download targets
- [Read](pages/Read.md) - Read text file contents directly

## Technical Implementation

### Helper Classes

- **ManagementDaoImpl**: Handles download link generation logic
- **Microsoft Graph API**: Generates temporary download links
- **FileUtil**: Validates paths and formats

### Telemetry Metrics

The following metrics are tracked:

- `onedrive.management.download_as_link.success` - Successful link generations
- `onedrive.management.download_as_link.failure` - Failed link generations
- `onedrive.management.download_as_link.duration` - Time taken to generate link

### Download Link Generation Flow

1. Validate input parameter (source path)
2. Check source file exists
3. Verify file is not a folder
4. Verify user has read permission
5. Request download link from Microsoft Graph API
6. Receive temporary download URL (valid 1 hour)
7. Return link in response

### Microsoft Graph API Endpoint

```
GET /me/drive/items/{item-id}/content
```

Or for creating sharing link:

```
POST /me/drive/items/{item-id}/createLink
```

**Request Body**:

```json
{
  "type": "view",
  "scope": "anonymous"
}
```

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

### Issue: "Cannot generate download link for folders"

**Possible Causes**:

- Source path points to a folder
- Attempting to download folder contents

**Solutions**:

1. Specify a file path, not a folder path
2. Use [List Files](pages/ListFiles.md) to get individual file paths
3. Download files individually

### Issue: Link expired or not working

**Possible Causes**:

- More than 1 hour has passed since link generation
- Link was copied incorrectly
- Network issues preventing access

**Solutions**:

1. Regenerate the download link
2. Verify link was copied completely
3. Check network connectivity
4. Try accessing link in different browser

### Issue: "Insufficient permissions to access file"

**Possible Causes**:

- User does not have read permission
- File is protected or encrypted
- Organizational policies restrict access

**Solutions**:

1. Verify user has read permission on the file
2. Check file sharing settings in OneDrive
3. Contact file owner for access
4. Review organizational policies

## Link Expiration Details

### Expiration Timeline

| Time             | Link Status                    |
|------------------|--------------------------------|
| 0-60 minutes     | Active - Link works normally   |
| After 60 minutes | Expired - Link no longer works |

### Handling Expiration

**For Users**:

- Request new link if expired
- Download file within 1 hour of receiving link

**For Workflows**:

- Generate links just-in-time before use
- Implement link regeneration logic
- Monitor link age in automated processes

## Security Considerations

### Link Security

- **Anonymous Access**: Links can be accessed without authentication
- **No Tracking**: Cannot track who accesses the link
- **Time-Limited**: 1-hour expiration reduces exposure window
- **No Revocation**: Cannot revoke link before expiration

### Best Practices

1. **Share Carefully**: Only share links with trusted recipients
2. **Use Secure Channels**: Share links via secure communication channels
3. **Monitor Usage**: Be aware links can be forwarded
4. **Consider Alternatives**: For sensitive files, use [Share File](pages/ShareFile.md) with specific permissions
5. **Document Sharing**: Maintain record of when and with whom links were shared

## Comparison with Download as File

| Aspect            | Download as Link | Download as File    |
|-------------------|------------------|---------------------|
| **Output**        | URL link         | Krista File object  |
| **Expiration**    | 1 hour           | No expiration       |
| **File Size**     | No limit         | 50 MB limit         |
| **Shared Drives** | Supported        | Supported           |
| **Folders**       | Not supported    | Not supported       |
| **Use Case**      | External sharing | Internal processing |

## Path Format Reference

### Personal Drive Paths

- **File**: `drive/root:/Documents/file.pdf`
- **Subfolder File**: `drive/root:/Folder/SubFolder/file.xlsx`

### Shared Drive Paths

- **Format**: `drives/b!{driveId}/root:/path/to/file`
- **Example**: `drives/b!absdbfbbvwevb/root:/Reports/report.pdf`

### Finding Drive ID

To find the drive ID for shared drives:

1. Use [List Files](pages/ListFiles.md) with shared drive path
2. Check the Absolute Path field in returned File entities
3. Extract drive ID from the path

## See Also

- [Download as File](pages/DownloadAsFile.md) - Download as Krista File object
- [Share File](pages/ShareFile.md) - Share files with permanent access
- [List Files](pages/ListFiles.md) - List files in folders
- [Read](pages/Read.md) - Read text file contents

