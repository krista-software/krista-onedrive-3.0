# When a File is Changed

## Overview

Monitors OneDrive for changes and triggers an event when a file or folder matching the source path and event type is
created, updated, deleted, or shared.

## Request Details

| Property          | Value          |
|-------------------|----------------|
| **Area**          | Management     |
| **Type**          | WAIT_FOR_EVENT |
| **Retry Support** | No             |

## Input Parameters

| Parameter Name | Type    | Required | Description                                                                                                                                                  |
|----------------|---------|----------|--------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Source         | Text    | Yes      | Path to monitor for changes. Can be a specific file path or a folder path to monitor all files within that folder                                            |
| Event          | PickOne | No       | Optional filter to only trigger on specific types of changes (Created, Updated, Deleted, Shared). If not specified, all change types will trigger this event |
| Search         | Text    | No       | Optional regular expression pattern to filter the subfolder names (e.g., Project.* to only show folders starting with "Project")                             |

## Output Parameters

| Parameter Name | Type         | Description                                                          |
|----------------|--------------|----------------------------------------------------------------------|
| File           | Entity(File) | File entity representing the changed file or folder                  |
| Event          | PickOne      | The type of change that occurred (Created, Updated, Deleted, Shared) |

## Validation Rules

| Field  | Validation Rule                              | Error Message             |
|--------|----------------------------------------------|---------------------------|
| Source | Must not be empty                            | "Source path is required" |
| Event  | Must be Created, Updated, Deleted, or Shared | "Invalid event type"      |

## Error Handling

### INPUT_ERROR

**When it occurs**: Invalid input parameters
**Common scenarios**: Empty source, invalid event type, invalid regex pattern
**Response**: Exception thrown with "Invalid Input"

### LOGIC_ERROR

**When it occurs**: Business logic violations
**Common scenarios**: Source path does not match event data, search pattern does not match
**Response**: Event not triggered, waits for matching event

### SYSTEM_ERROR

**When it occurs**: System-level failures
**Common scenarios**: Event monitoring service unavailable
**Response**: Exception thrown with error details

### Authorization Errors

**When it occurs**: Permission issues
**Common scenarios**: User does not have access to monitor source path
**Response**: Exception thrown with permission error

## Usage Examples

### Example 1: Monitor All Changes in Folder

**Input**: `Source: drive/root:/Documents, Event: , Search: `
**Output**: Triggers on any change (Created, Updated, Deleted, Shared) in /Documents folder

### Example 2: Monitor Only Created Files

**Input**: `Source: drive/root:/Uploads, Event: Created, Search: `
**Output**: Triggers only when new files are created in /Uploads folder

### Example 3: Monitor Specific File Pattern

**Input**: `Source: drive/root:/Reports, Event: Updated, Search: .*\.pdf`
**Output**: Triggers only when PDF files are updated in /Reports folder

### Example 4: Monitor Specific File

**Input**: `Source: drive/root:/Config/settings.json, Event: Updated, Search: `
**Output**: Triggers only when settings.json is updated

## Business Rules

1. **Event-Driven**: Waits for events, does not poll
2. **Four Event Types**: Created, Updated, Deleted, Shared
3. **Optional Filtering**: Can filter by event type and search pattern
4. **Recursive Monitoring**: Monitors subfolders if source is a folder
5. **Real-Time**: Triggers as soon as change is detected

## Limitations

- **No Historical Events**: Only triggers on new changes, not past changes
- **Event Delay**: May have slight delay between change and trigger
- **No Batch Events**: Triggers once per change
- **Requires Active Monitoring**: Must be actively waiting for event

## Best Practices

1. Use specific source paths to reduce noise
2. Filter by event type when only specific changes matter
3. Use search pattern to filter by file name or pattern
4. Handle all event types if not filtering
5. Use [Changes Since](pages/ChangesSince.md) for historical changes
6. Implement error handling for event processing

## Common Use Cases

1. Real-time file synchronization
2. Automated workflow triggers
3. Change notifications
4. Audit logging
5. Backup automation
6. Content processing pipelines

## Related Catalog Requests

- [Changes Since](pages/ChangesSince.md) - Historical change tracking
- [List Files](pages/ListFiles.md) - List current files
- [Get Info](pages/GetInfo.md) - Get folder statistics

## Technical Implementation

**Helper Classes**: EventHandler, FileUtil
**Event Name**: `FILE_RECEIVED`
**Event Data**: Contains File entity and Event type
**Validation**: Checks source path, event type, and search pattern match

## Event Types

| Event Type  | Description                 | Triggered When                              |
|-------------|-----------------------------|---------------------------------------------|
| **Created** | New file or folder          | File uploaded, folder created               |
| **Updated** | File modified               | File content changed, metadata updated      |
| **Deleted** | File or folder removed      | File deleted, moved to recycle bin          |
| **Shared**  | Sharing permissions changed | File shared with user, permissions modified |

## Troubleshooting

**Issue: Event not triggering** - Verify source path is correct, check event type filter, ensure monitoring is active
**Issue: Too many events** - Use more specific source path, add event type filter, use search pattern
**Issue: "Invalid Input"** - Check source path format, verify event type is valid
**Issue: Events delayed** - This is normal, slight delay expected

## Monitoring Patterns

### Monitor Entire Folder

```
Source: drive/root:/Documents
Event: (empty - all events)
Search: (empty - all files)
```

### Monitor Specific Event Type

```
Source: drive/root:/Uploads
Event: Created
Search: (empty - all files)
```

### Monitor File Pattern

```
Source: drive/root:/Reports
Event: (empty - all events)
Search: .*\.pdf
```

### Monitor Single File

```
Source: drive/root:/Config/settings.json
Event: Updated
Search: (empty)
```

## Workflow Integration

### Example Workflow: Process New Uploads

1. Configure When a File is Changed:
    - Source: drive/root:/Uploads
    - Event: Created
2. When event triggers:
    - Get File from event output
    - Execute [Download as File](pages/DownloadAsFile.md)
    - Process file content
    - Execute [Move](pages/Move.md) to archive

### Example Workflow: Sync Changes

1. Configure When a File is Changed:
    - Source: drive/root:/Sync
    - Event: (all events)
2. When event triggers:
    - Check Event type
    - If Created or Updated: Download and sync
    - If Deleted: Remove from local storage
    - If Shared: Update permissions

## Comparison with Changes Since

| Aspect          | When a File is Changed   | Changes Since       |
|-----------------|--------------------------|---------------------|
| **Type**        | Real-time monitoring     | Historical query    |
| **Timing**      | Future changes           | Past changes        |
| **Use Case**    | Event-driven workflows   | Audit reports       |
| **Performance** | Event-driven (efficient) | Query-based         |
| **Date Range**  | N/A                      | Requires Since date |

## See Also

- [Changes Since](pages/ChangesSince.md) - Historical changes
- [List Files](pages/ListFiles.md) - List files
- [Download as File](pages/DownloadAsFile.md) - Download changed files
- [Move](pages/Move.md) - Move files

