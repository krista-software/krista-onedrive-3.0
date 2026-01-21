# Write Text

## Overview

Uploads text content to OneDrive at the specified target location as a plain text file.

## Request Details

| Property          | Value                       |
|-------------------|-----------------------------|
| **Area**          | Management                  |
| **Type**          | CHANGE_SYSTEM               |
| **Retry Support** | Yes (with re-entry prompts) |

## Input Parameters

| Parameter Name | Type      | Required | Description                                                                                                                       |
|----------------|-----------|----------|-----------------------------------------------------------------------------------------------------------------------------------|
| File Content   | Paragraph | Yes      | The text content to be written to the file                                                                                        |
| Target         | Text      | Yes      | Full path where the text file should be created (e.g., /Documents/notes.txt). For shared drives, use format driveId:/path/to/file |

## Output Parameters

| Parameter Name | Type         | Description                                                           |
|----------------|--------------|-----------------------------------------------------------------------|
| File           | Entity(File) | File entity representing the created text file with metadata          |
| Success        | Yes/No       | Indicates whether the text file was successfully created              |
| Message        | Text         | Provides details about the operation result or any errors encountered |

## Validation Rules

| Field        | Validation Rule            | Error Message                  |
|--------------|----------------------------|--------------------------------|
| File Content | Must not be empty          | "File content is required"     |
| Target       | Must not be empty          | "Target path is required"      |
| Target       | Parent folder must exist   | "Target folder not found"      |
| Target       | Should have .txt extension | "Target should be a text file" |

## Error Handling

### INPUT_ERROR

**When it occurs**: Invalid input parameters
**Common scenarios**: Empty content, empty target, invalid path format
**Response**: `{"Success": false, "Message": "Invalid input: File content is required", "File": null}`
**Re-entry Support**: System prompts to re-enter target path

### LOGIC_ERROR

**When it occurs**: Business logic violations
**Common scenarios**: Target folder not found, invalid file extension
**Response**: `{"Success": false, "Message": "Target folder not found", "File": null}`
**Re-entry Support**: System prompts to re-enter target path

### SYSTEM_ERROR

**When it occurs**: System-level failures
**Common scenarios**: Network issues, upload timeout, Microsoft Graph API unavailable
**Response**: `{"Success": false, "Message": "Failed to create text file: [error details]", "File": null}`

### Authorization Errors

**When it occurs**: Permission issues
**Common scenarios**: User does not have write permission on target folder
**Response**: `{"Success": false, "Message": "Insufficient permissions to create file", "File": null}`

## Usage Examples

### Example 1: Create Simple Text File

**Input**: `File Content: "This is a test note.", Target: drive/root:/Documents/notes.txt`
**Output**: `Success: Yes, Message: Text file created successfully, File: [Entity]`

### Example 2: Create JSON Configuration

**Input**:
`File Content: "{ \"setting1\": \"value1\", \"setting2\": \"value2\" }", Target: drive/root:/Config/settings.json`
**Output**: `Success: Yes, Message: Text file created successfully, File: [Entity]`

### Example 3: Create Log File

**Input**:
`File Content: "2024-10-14 10:30:00 - Process started\n2024-10-14 10:31:00 - Process completed", Target: drive/root:/Logs/process.log`
**Output**: `Success: Yes, Message: Text file created successfully, File: [Entity]`

## Business Rules

1. **Text Content Only**: Designed for text-based content
2. **Shared Drives Supported**: Can create files on both personal and shared drives
3. **Overwrite**: Overwrites existing file if target path already exists
4. **UTF-8 Encoding**: Content is saved with UTF-8 encoding
5. **No Size Limit**: No explicit size limit for text content

## Limitations

- **Text Only**: Not suitable for binary content
- **No Batch Create**: Can only create one file at a time
- **Overwrites Existing**: Automatically overwrites if file exists
- **No Folder Creation**: Does not create parent folders automatically

## Best Practices

1. Use for text-based files (txt, json, xml, csv, log, etc.)
2. Verify target folder exists using [List Folders](pages/ListFolders.md)
3. Include appropriate file extension in target path
4. Use UTF-8 compatible characters
5. Check Success flag before assuming file created
6. Use [Write](pages/Write.md) for binary files instead

## Common Use Cases

1. Creating configuration files (JSON, XML, INI)
2. Generating log files
3. Creating CSV data files
4. Saving text reports
5. Creating template files
6. Storing text-based workflow outputs

## Related Catalog Requests

- [Write](pages/Write.md) - Upload binary files
- [Read](pages/Read.md) - Read text file contents
- [Create Folder](pages/CreateFolder.md) - Create target folder
- [List Files](pages/ListFiles.md) - Verify file creation

## Technical Implementation

**Helper Classes**: ManagementDaoImpl
**Telemetry Metrics**: `onedrive.management.write_text.success`, `onedrive.management.write_text.failure`,
`onedrive.management.write_text.duration`
**API Endpoint**: `PUT /me/drive/root:/{path}:/content`
**Content-Type**: `text/plain; charset=utf-8`

## Troubleshooting

**Issue: "Target folder not found"** - Create folder using Create Folder, verify path is correct
**Issue: "Insufficient permissions"** - Verify write permission on target folder
**Issue: Content appears garbled** - Ensure content is UTF-8 compatible
**Issue: File not created** - Check target path format, verify parent folder exists

## Supported File Types

**Recommended**:

- .txt (Plain text)
- .json (JSON)
- .xml (XML)
- .csv (CSV)
- .log (Log files)
- .md (Markdown)
- .html (HTML)
- .css (CSS)
- .js (JavaScript)
- .sql (SQL)

**Not Recommended**:

- Binary files (use Write instead)
- Large files (use Write instead)
- Files requiring specific encoding

## See Also

- [Write](pages/Write.md) - Upload binary files
- [Read](pages/Read.md) - Read text files
- [Create Folder](pages/CreateFolder.md) - Create folders
- [List Files](pages/ListFiles.md) - List files

