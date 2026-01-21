# Share File

## Overview

Share a file with specified users by their email address and apply the provided permissions (Read, Write, Read and
Write, or Own).

## Request Details

| Property          | Value                       |
|-------------------|-----------------------------|
| **Area**          | Management                  |
| **Type**          | CHANGE_SYSTEM               |
| **Retry Support** | Yes (with re-entry prompts) |

## Input Parameters

| Parameter Name | Type | Required | Description                                                                                                            |
|----------------|------|----------|------------------------------------------------------------------------------------------------------------------------|
| Source         | Text | Yes      | Full path to the file to be shared (e.g., /Documents/file.txt). For shared drives, use format driveId:/path/to/file    |
| Share          | List | Yes      | List of users and their permissions. Each entry contains: User (Email), Permissions (Read, Write, Read and Write, Own) |

## Output Parameters

| Parameter Name | Type         | Description                                                                   |
|----------------|--------------|-------------------------------------------------------------------------------|
| File           | Entity(File) | File entity representing the shared file                                      |
| Success        | Yes/No       | Indicates whether the file sharing operation was successful                   |
| Message        | Text         | Provides details about the sharing operation result or any errors encountered |

## Validation Rules

| Field             | Validation Rule                             | Error Message             |
|-------------------|---------------------------------------------|---------------------------|
| Source            | Must not be empty                           | "Source path is required" |
| Source            | File must exist                             | "Source file not found"   |
| Share             | Must not be empty                           | "Share list is required"  |
| Share.User        | Must be valid email                         | "Invalid email address"   |
| Share.Permissions | Must be Read, Write, Read and Write, or Own | "Invalid permission type" |

## Error Handling

### INPUT_ERROR

**When it occurs**: Invalid input parameters
**Common scenarios**: Empty source, invalid email, invalid permission type
**Response**: `{"Success": false, "Message": "Invalid input: Source path is required", "File": null}`
**Re-entry Support**: System prompts to re-enter source or share list

### LOGIC_ERROR

**When it occurs**: Business logic violations
**Common scenarios**: Source file not found, user already has access, cannot share with self
**Response**: `{"Success": false, "Message": "Source file not found", "File": null}`
**Re-entry Support**: System prompts to re-enter source

### SYSTEM_ERROR

**When it occurs**: System-level failures
**Common scenarios**: Network issues, Microsoft Graph API unavailable
**Response**: `{"Success": false, "Message": "Failed to share file: [error details]", "File": null}`

### Authorization Errors

**When it occurs**: Permission issues
**Common scenarios**: User does not have permission to share file
**Response**: `{"Success": false, "Message": "Insufficient permissions to share file", "File": null}`

## Usage Examples

### Example 1: Share with Read Permission

**Input**: `Source: drive/root:/Documents/report.pdf, Share: [{User: "user@example.com", Permissions: "Read"}]`
**Output**: `Success: Yes, Message: File shared successfully, File: [Entity]`

### Example 2: Share with Multiple Users

**Input**:
`Source: drive/root:/Data/sales.xlsx, Share: [{User: "user1@example.com", Permissions: "Read and Write"}, {User: "user2@example.com", Permissions: "Read"}]`
**Output**: `Success: Yes, Message: File shared successfully, File: [Entity]`

### Example 3: Share with Own Permission

**Input**: `Source: drive/root:/Projects/project.docx, Share: [{User: "manager@example.com", Permissions: "Own"}]`
**Output**: `Success: Yes, Message: File shared successfully, File: [Entity]`

## Business Rules

1. **Files Only**: Can only share files, not folders
2. **Shared Drives Supported**: Can share files on both personal and shared drives
3. **Multiple Users**: Can share with multiple users in one operation
4. **Permission Levels**: Supports Read, Write, Read and Write, and Own permissions
5. **Email Required**: Users identified by email address

## Limitations

- **Files Only**: Cannot share folders
- **Email Required**: Cannot share with users without email
- **No Anonymous Sharing**: Must specify user email addresses
- **No Expiration**: Cannot set expiration date for sharing

## Best Practices

1. Verify file exists using [List Files](pages/ListFiles.md) before sharing
2. Use appropriate permission levels (least privilege principle)
3. Verify email addresses are correct
4. Use Read permission for view-only access
5. Use Read and Write for collaboration
6. Use Own permission sparingly (transfers ownership)

## Common Use Cases

1. Collaborating on documents
2. Sharing reports with stakeholders
3. Granting access to data files
4. Team file sharing
5. Client file delivery

## Related Catalog Requests

- [Download as Link](pages/DownloadAsLink.md) - Share via temporary link
- [List Files](pages/ListFiles.md) - Verify file exists
- [Copy](pages/Copy.md) - Create copy before sharing

## Technical Implementation

**Helper Classes**: ManagementDaoImpl
**Telemetry Metrics**: `onedrive.management.share_file.success`, `onedrive.management.share_file.failure`
**API Endpoint**: `POST /me/drive/items/{item-id}/invite`

## Permission Types

| Permission         | Description   | User Can                                |
|--------------------|---------------|-----------------------------------------|
| **Read**           | View only     | View, download                          |
| **Write**          | Edit only     | Edit, but not view (rare)               |
| **Read and Write** | View and edit | View, download, edit, upload            |
| **Own**            | Full control  | All actions, manage permissions, delete |

## Troubleshooting

**Issue: "Source file not found"** - Verify file path exists, check for typos
**Issue: "Invalid email address"** - Verify email format is correct
**Issue: "Insufficient permissions to share file"** - Verify user has permission to share
**Issue: User not receiving access** - Check email address, verify user has Microsoft account

## See Also

- [Download as Link](pages/DownloadAsLink.md) - Temporary file sharing
- [List Files](pages/ListFiles.md) - List files
- [Copy](pages/Copy.md) - Copy files

