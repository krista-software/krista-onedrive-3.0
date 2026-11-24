# AI Assistant (MCP)

## Overview

The AI Assistant tab enables natural language interaction with OneDrive. Instead of configuring catalog requests manually, you can ask questions in plain English — the extension translates them into Microsoft Graph API calls automatically.

Both the **NQL tab** in Krista Studio and **Krista Agent / MCP client** tool calls use the same pipeline: the AI determines the exact Graph API call needed (`method`, `path`, `body`), and the extension executes it generically. No hardcoded intents — any valid Graph drive endpoint works without code changes.

---

## Setup

### Step 1 — Configure OneDrive Authentication

The AI Assistant uses the same OneDrive connection as all other catalog requests. Complete the **Authentication** tab first (see [Authentication](pages/Authentication.md)).

### Step 2 — Configure an AI API Key

1. Open the extension's **AI Assistant** tab in Krista Studio.
2. Go to **AI Settings**.
3. Select a provider and paste in the API key:

| Provider | Model Used | Where to Get the Key |
|---|---|---|
| **Anthropic (Claude)** | claude-haiku-3-5 | [console.anthropic.com](https://console.anthropic.com) → API keys |
| **OpenAI (GPT)** | gpt-4o-mini | [platform.openai.com](https://platform.openai.com) → API keys |
| **Google (Gemini)** | gemini-2.5-flash | [aistudio.google.com](https://aistudio.google.com) → Get API key |

4. Click **Save**.

If no AI key is configured, queries return:
> _AI API key is not configured. Please set it in the AI Assistant tab → AI Settings._

---

## How It Works

Every natural language question goes through the same pipeline:

```
User question
  → AI plans the Graph API call  { method, path, body }
  → /me/ path auto-switched to /users/{email}/ for app-only auth
  → Extension executes the call against Microsoft Graph
  → sharedWithMe remoteItem wrappers merged transparently
  → @odata.* metadata fields filtered out
  → Columns derived dynamically from response fields
  → GENERATED KQL field shows the executed method + path
```

The AI returns one of two HTTP methods:

| Method | When Used |
|---|---|
| `GET` | List files, list folders, get a specific file, list drives, recent files, shared files |
| `POST` | Search files by keyword (via Graph Search API) |

> The OneDrive AI Assistant is focused on file discovery and metadata retrieval. Write operations (upload, rename, delete) are available as dedicated catalog requests — see [Write](pages/Write.md), [Rename](pages/Rename.md), [Delete](pages/Delete.md).

---

## Supported Operations

### Listing Files

| Operation | Example Question | Graph Call |
|---|---|---|
| All files (own + shared) | "Show all my files" | `POST /search/query` with `queryString: "*"` |
| Recent files | "Show my recent files" | `GET /me/drive/recent?$top=50` |
| Files shared with me | "Show files shared with me" | `GET /me/drive/sharedWithMe?$top=50` |
| Root drive contents | "List files in my OneDrive" | `GET /me/drive/root/children?$top=50` |
| Files in a folder | "List files in the Reports folder" | `GET /me/drive/root:/Reports:/children?$top=50` |
| Nested folder | "List files in Projects/2024/Q1" | `GET /me/drive/root:/Projects/2024/Q1:/children?$top=50` |
| Get file by ID | "Get file details for ID abc123" | `GET /me/drive/items/{itemId}` |
| List drives | "Show all my drives" | `GET /me/drives` |

### Searching Files

| Operation | Example Question | Graph Call |
|---|---|---|
| Search by keyword | "Find files about the budget" | `POST /search/query` with `queryString: "budget"` |
| Search by file type | "Show all PDF files" | `POST /search/query` with `queryString: "FileType:pdf"` |
| Search by author | "Find files created by Jane" | `POST /search/query` with `queryString: "Author:\"Jane\""` |
| Search by date | "Files modified this week" | `POST /search/query` with `queryString: "LastModifiedTime>today-7"` |
| Combined search | "Excel files by John from 2024" | `POST /search/query` with KQL `FileType:xlsx AND Author:"John" AND LastModifiedTime>2024-01-01` |

Search queries cover **both your own files and files shared with you** in a single call — the Graph Search API (`POST /search/query`) is the most comprehensive option for cross-drive discovery.

---

## Result Columns

Columns are **fully dynamic** — derived at runtime from whatever fields the Graph API returns. There is no fixed column list. Internal `@odata.*` metadata fields are automatically filtered out.

Common columns returned for file/folder queries:

| Column | Graph Field | Description |
|---|---|---|
| Name | `name` | File or folder name |
| Last Modified | `lastModifiedDateTime` | Date last modified (date only, time stripped) |
| Modified By | `lastModifiedBy.user.displayName` | Display name of the last person to modify the file |
| Created | `createdDateTime` | Date the file was created |
| Created By | `createdBy.user.displayName` | Display name of the file creator |
| Size | `size` | File size (formatted: B / KB / MB) |
| Folder | `parentReference.name` | Name of the parent folder |
| Mime Type | `file.mimeType` | MIME type (e.g. `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`) |
| Link | `webUrl` | URL to open the file in OneDrive on the web |
| File Id | `id` / `hitId` | Internal OneDrive item ID |

### Shared Files (sharedWithMe)

Files returned by `GET /me/drive/sharedWithMe` wrap remote details inside a `remoteItem` object. The extension automatically merges `remoteItem` fields into the top-level row so **Name**, **Size**, **Link**, and other columns appear correctly — no extra steps needed.

---

## KQL Reference (Search Queries)

When the AI uses `POST /search/query`, it generates a KQL string. Common patterns:

| KQL | Matches |
|---|---|
| `*` | All files (no filter) |
| `budget` | Files with "budget" in name or content |
| `FileType:pdf` | PDF files |
| `FileType:xlsx` | Excel workbook files |
| `FileType:docx` | Word document files |
| `Title:"Project Report"` | Files with that exact name phrase |
| `Author:"John Smith"` | Files created by that person |
| `LastModifiedTime>2024-01-01` | Files modified after a date |
| `LastModifiedTime>today-7` | Files modified in the last 7 days |
| `Path:"finance"` | Files in a path containing "finance" |
| `FileType:pdf AND Author:"Jane"` | PDFs created by Jane |

Boolean operators: `AND`, `OR`, `NOT` (uppercase). Wildcards: `*` (prefix only, e.g. `budget*`).

---

## Authentication Modes

The AI Assistant respects the authentication mode configured in the Authentication tab:

| Auth Mode | Graph API Path | Notes |
|---|---|---|
| **OAuth 2.0** (Krista Managed or Your Own Credentials) | `/me/drive/...` | Standard delegated access |
| **App-Only** (Private / Certificate auth) | `/users/{email}/drive/...` | Email is taken from Authentication tab configuration |

The path switch happens automatically on every call — no configuration change needed in the AI Assistant tab.

---

## Error Messages

| Message | Cause | Fix |
|---|---|---|
| `AI API key is not configured.` | No API key in AI Settings | Add a key in the AI Assistant tab → AI Settings |
| `Not connected. Configure credentials in the Authentication tab.` | OneDrive auth not set up or expired | Complete or refresh the Authentication tab setup |
| `Could not determine Graph API call. Please try rephrasing your question.` | AI returned a blank response | Rephrase with more specific terms |
| `Could not parse a valid Graph API call from the AI response.` | AI returned unparseable JSON | Rephrase the question more specifically |
| `Graph API call failed (HTTP 400): ...` | Invalid path, filter syntax, or query parameter | Check the extension logs; try a simpler question |
| `Graph API call failed (HTTP 403): ...` | Insufficient permissions on the Azure AD application | Add `Files.Read` or `Files.ReadWrite` scope (see [Authentication](pages/Authentication.md)) |
| `Graph API call failed (HTTP 404): ...` | File or folder path not found | Verify the folder path or file ID is correct |
| `Query failed: ...` | Unexpected exception | Check extension logs for the underlying error |

---

## Troubleshooting

### GENERATED KQL shows the original question instead of a Graph API path

This was a bug in earlier versions where the generated plan was not passed through to the UI. It is fixed — the GENERATED KQL field now always shows the actual `method + path` that was executed (e.g. `GET /me/drive/recent?$top=50`).

### Shared files show no Name or Size columns

This was caused by the `remoteItem` wrapper that the Graph API uses for shared files. The extension now automatically merges `remoteItem` fields into the result row, so **Name**, **Size**, and **Link** appear correctly for shared items.

### Listing a folder returns no results

Folder paths in the Graph API are case-sensitive and must be URI path-encoded. When asking about a folder, use the exact folder name as it appears in OneDrive:

> "List files in the My Documents folder"

The AI generates `GET /me/drive/root:/My Documents:/children`. If the folder name differs (e.g. capitalisation or spaces), no results are returned. Check the exact name by asking "List files in my root drive" first.

### "All files" question only returns my own files

Use "Show all my files including shared ones" to ensure the AI uses `POST /search/query` with `queryString: "*"`, which covers both personal and shared files. If the AI uses `GET /me/drive/root/children`, only files in your personal drive root are returned.

### Search returns files from unexpected locations

Graph Search (`POST /search/query`) searches across all drives the user has access to, including shared drives and SharePoint sites. To narrow results, include the path in your question:

> "Find budget files in the Finance folder"

This produces `Path:"finance" AND budget`.

### Large drives return too many results

Results are capped at 50 by default. To see more, specify a smaller scope:

> "Show the 10 most recently modified Excel files"

---

## Architecture Notes

- Both the NQL tab and Krista Agent tool calls use `OnedriveMcpQueryService.query()` — the same pipeline.
- The AI generates `{method, path, body}` and the extension executes it via a JAX-RS HTTP client.
- Only `GET` and `POST` are supported. Write operations (rename, delete, upload) are catalog requests only.
- `@odata.*` metadata fields are filtered from all responses.
- `sharedWithMe` items with a `remoteItem` wrapper are automatically merged so file metadata is visible at the top level.
- File sizes are formatted as B / KB / MB for readability.
- Authentication credentials are reloaded on every call so changes to the Authentication tab take effect immediately without a restart.
- For app-only auth, `/me/` in AI-generated paths is automatically replaced with `/users/{configured-email}/`.

---

## See Also

- [Authentication](pages/Authentication.md) — configure the OneDrive connection
- [List Files](pages/ListFiles.md) — catalog request for listing files programmatically
- [List Folders](pages/ListFolders.md) — catalog request for listing folders
- [Search By Permissions](pages/SearchByPermissions.md) — catalog request for permission-based search
- [Write](pages/Write.md) — catalog request for uploading files
- [Read](pages/Read.md) — catalog request for reading file content
- [Delete](pages/Delete.md) — catalog request for deleting files
