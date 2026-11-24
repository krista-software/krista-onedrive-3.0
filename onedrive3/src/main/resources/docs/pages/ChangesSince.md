# Changes Since

## Overview

Produces a list of recent updates within a specified folder since a given date, including information about the path,
file status (Created, Updated, Deleted), and can be filtered using an optional regex search pattern.

## Request Details

| Property          | Value         |
|-------------------|---------------|
| **Area**          | Management    |
| **Type**          | CHANGE_SYSTEM |
| **Retry Support** | No            |

## Input Parameters

| Parameter Name | Type | Required | Description                                                                                                                                          |
|----------------|------|----------|------------------------------------------------------------------------------------------------------------------------------------------------------|
| Source Path    | Text | Yes      | Full path to the folder or location in the cloud storage that you want to monitor for changes (e.g., /Documents or /Sites/TeamSite/Shared Documents) |
| Search         | Text | No       | Optional regular expression pattern to filter the subfolder names (e.g., Project.* to only show folders starting with "Project")                     |
| Since          | Date | Yes      | The starting date and time from which to retrieve changes. Only changes that occurred after this timestamp will be included                          |

> 📌 **Root folder path:** To target your OneDrive **root folder**, enter **`drive/root:/`** as the path (leaving the

## Output Parameters

| Parameter Name | Type   | Description                                                                                                  |
|----------------|--------|--------------------------------------------------------------------------------------------------------------|
| Result         | List   | List of change records. Each entry contains: Path (Text), Date (Date), Update (Created, Updated, or Deleted) |
| Success        | Yes/No | Indicates whether the change history was successfully retrieved                                              |
| Message        | Text   | Provides details about the operation result or any errors encountered                                        |

## Validation Rules

| Field       | Validation Rule     | Error Message                    |
|-------------|---------------------|----------------------------------|
| Source Path | Must not be empty   | "Source path is required"        |
| Since       | Must not be empty   | "Since date is required"         |
| Since       | Must be in the past | "Since date must be in the past" |
| Source Path | Folder must exist   | "Source folder not found"        |

## Error Handling

### INPUT_ERROR

**When it occurs**: Invalid input parameters
**Common scenarios**: Empty source path, empty since date, future date, invalid regex
**Response**: `{"Success": false, "Message": "Invalid input: Source path is required", "Result": []}`

### LOGIC_ERROR

**When it occurs**: Business logic violations
**Common scenarios**: Source folder not found, invalid date range
**Response**: `{"Success": false, "Message": "Source folder not found", "Result": []}`

### SYSTEM_ERROR

**When it occurs**: System-level failures
**Common scenarios**: Network issues, Microsoft Graph API unavailable, timeout
**Response**: `{"Success": false, "Message": "Failed to retrieve changes: [error details]", "Result": []}`

### Authorization Errors

**When it occurs**: Permission issues
**Common scenarios**: User does not have read permission on folder
**Response**: `{"Success": false, "Message": "Insufficient permissions to access folder", "Result": []}`

## Usage Examples

### Example 1: Get All Changes Since Date

**Input**: `Source Path: drive/root:/Documents, Search: , Since: 2024-10-01`
**Output**:

```
Success: Yes
Message: Changes retrieved successfully
Result: [
  {Path: "/Documents/report.pdf", Date: 2024-10-05, Update: "Created"},
  {Path: "/Documents/data.xlsx", Date: 2024-10-07, Update: "Updated"},
  {Path: "/Documents/old_file.txt", Date: 2024-10-10, Update: "Deleted"}
]
```

### Example 2: Filter Changes by Pattern

**Input**: `Source Path: drive/root:/Projects, Search: Project.*, Since: 2024-09-01`
**Output**:

```
Success: Yes
Message: Changes retrieved successfully
Result: [
  {Path: "/Projects/ProjectA/file.txt", Date: 2024-09-15, Update: "Created"},
  {Path: "/Projects/ProjectB/data.csv", Date: 2024-09-20, Update: "Updated"}
]
```

### Example 3: No Changes Found

**Input**: `Source Path: drive/root:/Archive, Search: , Since: 2024-10-01`
**Output**: `Success: Yes, Message: No changes found, Result: []`

## Business Rules

1. **Date Range**: Only returns changes after the specified Since date
2. **Recursive**: Includes changes in subfolders
3. **Three Change Types**: Created, Updated, Deleted
4. **Shared Drives Supported**: Works with both personal and shared drives
5. **Regex Filtering**: Optional search pattern for filtering results

## Limitations

- **Past Dates Only**: Cannot query future dates
- **No Real-Time**: Returns historical changes, not real-time monitoring
- **API Limitations**: May not capture all changes if too many occurred
- **No Pagination**: All results returned at once
- **60-Day Limit**: Changes older than 60 days may not be available

## Best Practices

1. Use reasonable date ranges (not too far in the past)
2. Use search filter to narrow results
3. Check Success flag before processing results
4. Handle empty result lists
5. Use [When a File is Changed](pages/WhenAFileIsChanged.md) for real-time monitoring
6. Store Since date for incremental change tracking

## Common Use Cases

1. Audit trail generation
2. Change tracking and reporting
3. Synchronization detection
4. Compliance monitoring
5. Activity reporting
6. Incremental backup detection

## Related Catalog Requests

- [When a File is Changed](pages/WhenAFileIsChanged.md) - Real-time change monitoring
- [List Files](pages/ListFiles.md) - List current files
- [Get Info](pages/GetInfo.md) - Get folder statistics

## Technical Implementation

**Helper Classes**: ManagementDaoImpl, FileUtil
**Telemetry Metrics**: `onedrive.management.changes_since.success`, `onedrive.management.changes_since.failure`,
`onedrive.management.changes_since.duration`
**API Endpoint**: `GET /me/drive/root:/{path}:/delta`

## Change Types

| Update Type | Description              | Example                   |
|-------------|--------------------------|---------------------------|
| **Created** | New file or folder added | New document uploaded     |
| **Updated** | Existing file modified   | Document edited and saved |
| **Deleted** | File or folder removed   | File moved to recycle bin |

## Troubleshooting

**Issue: "Source folder not found"** - Verify folder path exists, check for typos
**Issue: "Since date must be in the past"** - Use past date, not future or today
**Issue: No changes found but changes occurred** - Check date range, verify search pattern, ensure changes within 60
days
**Issue: Too many results** - Use search filter, narrow date range, process in batches

## Date Range Recommendations

| Range         | Use Case             | Performance |
|---------------|----------------------|-------------|
| Last 24 hours | Recent activity      | Excellent   |
| Last 7 days   | Weekly reports       | Good        |
| Last 30 days  | Monthly audits       | Moderate    |
| Last 60 days  | Quarterly reviews    | Slower      |
| > 60 days     | May not be available | N/A         |

## See Also

- [When a File is Changed](pages/WhenAFileIsChanged.md) - Real-time monitoring
- [List Files](pages/ListFiles.md) - List files
- [Get Info](pages/GetInfo.md) - Folder statistics

