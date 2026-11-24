# Get Drives

## Overview

Returns a list of all drives accessible to the authenticated user, including personal OneDrive, business drives, and SharePoint document libraries. Use the **Drive Path** from the response as the source path in other catalog requests (e.g., List Files, List Folders, Upload File) to access files in shared drives.

## Request Details

| Property          | Value        |
|-------------------|--------------|
| **Area**          | Management   |
| **Type**          | QUERY_SYSTEM |
| **Retry Support** | No           |

## Input Parameters

| Parameter Name | Type | Required | Description                                                                                   |
|----------------|------|----------|-----------------------------------------------------------------------------------------------|
| Search         | Text | No       | Optional filter applied to the drive display name (case-insensitive). Leave blank to return all drives. |

## Output Parameters

| Parameter Name | Type    | Description                                                                                    |
|----------------|---------|------------------------------------------------------------------------------------------------|
| Drives         | List    | List of accessible drives. Each entry contains Drive Path, Drive Name, Drive Type, and Owner. |
| Success        | Boolean | `true` if the request completed successfully, `false` otherwise.                              |
| Message        | Text    | Summary message, e.g. "3 drive(s) found".                                                    |

### Drive Object Fields

| Field       | Type | Description                                                                                                         |
|-------------|------|---------------------------------------------------------------------------------------------------------------------|
| Drive Path  | Text | Path to use as source in other requests (e.g., `drives/b!abc123/root:/`).                                         |
| Drive Name  | Text | Display name of the drive.                                                                                          |
| Drive Type  | Text | Type of drive: `personal` (OneDrive personal), `business` (OneDrive for Business), or `documentLibrary` (SharePoint). |
| Owner       | Text | Display name of the drive owner, if available.                                                                     |

## Validation Rules

| Field  | Validation Rule               | Error Message                    |
|--------|-------------------------------|----------------------------------|
| Search | No validation (optional text) | N/A                              |

## Error Handling

### SYSTEM_ERROR

**When it occurs**: System-level failures

**Common scenarios**:

- Microsoft Graph API unavailable
- Authentication token expired or invalid
- Network connectivity issues

**Response**:

```json
{
  "Drives": [],
  "Success": false,
  "Message": "System error: [details]"
}
```

### Authorization Errors

**When it occurs**: Permission issues

**Common scenarios**:

- User has not completed MCP authorization
- Token missing required `Files.ReadWrite.All` scope

**Response**:

```json
{
  "Drives": [],
  "Success": false,
  "Message": "Insufficient permissions to list drives"
}
```

## Usage Examples

### Example 1: List All Drives

**Input**:

```
Search: (empty)
```

**Output**:

```json
{
  "Drives": [
    {
      "Drive Path": "drives/b!abc123XYZ/root:/",
      "Drive Name": "OneDrive",
      "Drive Type": "personal",
      "Owner": "John Smith"
    },
    {
      "Drive Path": "drives/b!def456UVW/root:/",
      "Drive Name": "Marketing Team",
      "Drive Type": "documentLibrary",
      "Owner": "Marketing Site"
    },
    {
      "Drive Path": "drives/b!ghi789RST/root:/",
      "Drive Name": "HR Documents",
      "Drive Type": "documentLibrary",
      "Owner": "HR Site"
    }
  ],
  "Success": true,
  "Message": "3 drive(s) found"
}
```

### Example 2: Filter Drives by Name

**Input**:

```
Search: Marketing
```

**Output**:

```json
{
  "Drives": [
    {
      "Drive Path": "drives/b!def456UVW/root:/",
      "Drive Name": "Marketing Team",
      "Drive Type": "documentLibrary",
      "Owner": "Marketing Site"
    }
  ],
  "Success": true,
  "Message": "1 drive(s) found"
}
```

### Example 3: Using Drive Path in List Files

Once you have a Drive Path from Get Drives, pass it directly as the **Source Path** in List Files:

```
Source Path: drives/b!def456UVW/root:/
```

To list files inside a subfolder on that drive:

```
Source Path: drives/b!def456UVW/root:/Reports/2024
```

## Business Rules

1. **Authentication required**: The user must be authenticated before calling this request. In MCP mode the user must have completed the OAuth authorization flow.
2. **Search is case-insensitive**: Filtering on drive name ignores letter case.
3. **Drive Path is ready to use**: The returned Drive Path is pre-formatted and can be passed directly to any request that accepts a source path.
4. **Personal drive included**: The user's personal OneDrive is always included if accessible.
5. **SharePoint libraries included**: Document libraries from SharePoint sites the user has access to are also returned.

## Common Use Cases

1. **Discover shared drives**: Find all SharePoint document libraries accessible to the user before listing files in them.
2. **Dynamic drive selection**: Let users pick a drive at runtime instead of hard-coding a drive ID.
3. **Shared folder access**: Retrieve the Drive Path needed to read, write, or manage files in a shared drive.
4. **Drive inventory**: Enumerate all drives for reporting or auditing purposes.

## Path Format Reference

There are two distinct path formats depending on which storage location you are accessing. Understanding the difference is important for using the correct format in other catalog requests.

---

### Personal OneDrive (your own drive)

You do **not** need to call Get Drives for your personal OneDrive. Use the shorthand format directly in any catalog request that accepts a path:

| Location | Path Format | Example |
|---|---|---|
| Root of personal drive | `drive/root:/` | `drive/root:/` |
| Folder in personal drive | `drive/root:/FolderName` | `drive/root:/Documents` |
| Subfolder | `drive/root:/Folder/Subfolder` | `drive/root:/Documents/Reports/2024` |

> `drive/root:/` (singular `drive`, no drive ID) always refers to the **authenticated user's own personal OneDrive**. No Get Drives call is required.

---

### SharePoint Document Libraries and Shared Drives

For SharePoint document libraries, OneDrive for Business shared drives, or any drive that belongs to another user or site, you must first call **Get Drives** to obtain the Drive Path for that storage location.

**Step 1 — Call Get Drives** to discover accessible drives and their pre-formatted paths:

```json
{
  "Drives": [
    {
      "Drive Path": "drives/b!abc123XYZ/root:/",
      "Drive Name": "Marketing Team",
      "Drive Type": "documentLibrary",
      "Owner": "Marketing Site"
    },
    {
      "Drive Path": "drives/b!ghi789RST/root:/",
      "Drive Name": "HR Documents",
      "Drive Type": "documentLibrary",
      "Owner": "HR Site"
    }
  ]
}
```

**Step 2 — Copy the Drive Path** from the response and pass it directly into any catalog request that accepts a source or target path:

| Location | Path Format | Example |
|---|---|---|
| Root of the shared drive | `drives/{driveId}/root:/` | `drives/b!abc123XYZ/root:/` |
| Folder inside the shared drive | `drives/{driveId}/root:/FolderName` | `drives/b!abc123XYZ/root:/Reports` |
| Subfolder inside the shared drive | `drives/{driveId}/root:/Folder/Subfolder` | `drives/b!abc123XYZ/root:/Reports/2024` |

> The `{driveId}` is the opaque identifier (e.g., `b!abc123XYZ`) that Microsoft assigns to each drive. **Do not construct or guess this value manually** — always use the Drive Path exactly as returned by Get Drives.

---

### Summary: Personal Drive vs SharePoint Storage

| Storage Type | Get Drives required? | Path format | Example |
|---|---|---|---|
| Your personal OneDrive | No | `drive/root:/path` | `drive/root:/Documents` |
| OneDrive for Business (shared) | Yes | `drives/{driveId}/root:/path` | `drives/b!abc123/root:/TeamFolder` |
| SharePoint Document Library | Yes | `drives/{driveId}/root:/path` | `drives/b!def456/root:/Shared Documents` |

The key visual difference: `drive/root:/` (no `s`, no ID) is the personal shorthand. `drives/{id}/root:/` (with `s` and a drive ID) is used for all shared and SharePoint storage.

## Related Catalog Requests

- [List Files](pages/ListFiles.md) — Use Drive Path as Source Path to list files in a drive
- [List Folders](pages/ListFolders.md) — Use Drive Path as Source Path to list folders in a drive
- [Upload File](pages/Write.md) — Use Drive Path as Target Path to upload to a shared drive
- [Get Info](pages/GetInfo.md) — Use Drive Path to get metadata about a drive root
