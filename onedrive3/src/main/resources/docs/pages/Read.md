# Read

## Overview

Read the content of a text file as plain text from OneDrive.

## Request Details

| Property          | Value                       |
|-------------------|-----------------------------|
| **Area**          | Management                  |
| **Type**          | CHANGE_SYSTEM               |
| **Retry Support** | Yes (with re-entry prompts) |

## Input Parameters

| Parameter Name | Type | Required | Description                                                                                                                                              |
|----------------|------|----------|----------------------------------------------------------------------------------------------------------------------------------------------------------|
| Source         | Text | Yes      | Full path to the text file to be read (e.g., /Documents/notes.txt or /Folders/MyFolder/config.json). For shared drives, use format driveId:/path/to/file |

## Output Parameters

| Parameter Name | Type      | Description                                                           |
|----------------|-----------|-----------------------------------------------------------------------|
| Content        | Paragraph | The text content of the file                                          |
| Success        | Yes/No    | Indicates whether the read operation was successful                   |
| Message        | Text      | Provides details about the operation result or any errors encountered |

## Validation Rules

| Field  | Validation Rule              | Error Message                |
|--------|------------------------------|------------------------------|
| Source | Must not be empty            | "Source path is required"    |
| Source | Must be valid OneDrive path  | "Invalid source path format" |
| Source | File must exist              | "Source file not found"      |
| Source | Must be a file, not a folder | "Cannot read folders"        |
| Source | Must be text file            | "File is not a text file"    |

## Error Handling

### INPUT_ERROR

**When it occurs**: Invalid input parameters
**Common scenarios**: Empty source path, invalid path format
**Response**: `{"Success": false, "Message": "Invalid input: Source path is required", "Content": ""}`
**Re-entry Support**: System prompts to re-enter source path

### LOGIC_ERROR

**When it occurs**: Business logic violations
**Common scenarios**: Source file not found, source is a folder, file is not text
**Response**: `{"Success": false, "Message": "Source file not found", "Content": ""}`
**Re-entry Support**: System prompts to re-enter source path

### SYSTEM_ERROR

**When it occurs**: System-level failures
**Common scenarios**: Network issues, Microsoft Graph API unavailable, timeout, file corruption
**Response**: `{"Success": false, "Message": "Failed to read file: [error details]", "Content": ""}`

### Authorization Errors

**When it occurs**: Permission issues
**Common scenarios**: User does not have read permission on file
**Response**: `{"Success": false, "Message": "Insufficient permissions to read file", "Content": ""}`

## Usage Examples

### Example 1: Read Text File

**Input**: `Source: drive/root:/Documents/notes.txt`
**Output**: `Success: Yes, Message: File read successfully, Content: "This is the content of the notes file..."`

### Example 2: Read JSON Configuration

**Input**: `Source: drive/root:/Config/settings.json`
**Output**:
`Success: Yes, Message: File read successfully, Content: "{ \"setting1\": \"value1\", \"setting2\": \"value2\" }"`

### Example 3: File Not Found

**Input**: `Source: drive/root:/Documents/missing.txt`
**Output**: `Success: No, Message: Source file not found, Content: ""`

## Business Rules

1. **Text Files Only**: Designed for text-based files (txt, json, xml, csv, etc.)
2. **Shared Drives Supported**: Can read from both personal and shared drives
3. **Encoding Support**: Supports UTF-8 and other common text encodings
4. **Size Limit**: Large files may have performance impact
5. **Binary Files**: Not suitable for binary files (use Download as File instead)

## Limitations

- **Text Files Only**: Not suitable for binary files (images, PDFs, etc.)
- **No Folder Support**: Cannot read folder contents
- **No Batch Read**: Can only read one file at a time
- **Memory Usage**: Very large text files may consume significant memory
- **No Partial Read**: Reads entire file content

## Best Practices

1. Use for text-based files only (txt, json, xml, csv, log, etc.)
2. For binary files, use [Download as File](pages/DownloadAsFile.md) instead
3. Verify file exists using [List Files](pages/ListFiles.md) before reading
4. Handle large files carefully (consider file size limits)
5. Check Success flag before processing Content
6. Use for configuration files, logs, and text data

## Common Use Cases

1. Reading configuration files (JSON, XML, INI)
2. Processing log files
3. Reading CSV data files
4. Extracting text content for analysis
5. Reading template files
6. Accessing text-based reports

## Related Catalog Requests

- [Write](pages/Write.md) - Write content to text files
- [WriteText](pages/WriteText.md) - Write text content to files
- [Download as File](pages/DownloadAsFile.md) - Download binary files
- [List Files](pages/ListFiles.md) - Verify file exists

## Technical Implementation

**Helper Classes**: ManagementDaoImpl
**Telemetry Metrics**: `onedrive.management.read.success`, `onedrive.management.read.failure`,
`onedrive.management.read.duration`
**API Endpoint**: `GET /me/drive/items/{item-id}/content`

## Troubleshooting

**Issue: "Source file not found"** - Verify file path exists, check for typos, ensure user has access
**Issue: "File is not a text file"** - Use Download as File for binary files, verify file extension
**Issue: Content is garbled** - File may be binary or use unsupported encoding
**Issue: "Insufficient permissions"** - Verify user has read permission on file

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

- .pdf (Use Download as File)
- .docx (Use Download as File)
- .xlsx (Use Download as File)
- .jpg, .png (Use Download as File)
- .zip (Use Download as File)

## See Also

- [Write](pages/Write.md) - Write files to OneDrive
- [WriteText](pages/WriteText.md) - Write text content
- [Download as File](pages/DownloadAsFile.md) - Download binary files

