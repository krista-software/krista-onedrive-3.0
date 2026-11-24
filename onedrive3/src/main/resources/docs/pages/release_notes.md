# Release Notes - Krista OneDrive Files Extension

## Version 4.0.5 — Create Folder Retry & UI Copy

**Release Date**: September, 2026

### Resolved Issues

**"Reenter" button label** — The confirmation prompt shown when an invalid or unreachable file/folder path is entered rendered its action toggle as **"Reenter"**, which is inconsistent with standard product UI copy.

- Relabeled the re-entry toggle from **"Reenter"** to **"Re-enter"** (hyphenated), aligning it with conventional `Re-` prefixed actions (Re-send, Re-try, Re-submit).
- Because the switch field's name is also the key echoed back into the sub-catalog `inputMap`, the `SubCatalogConstants.REENTER` value and the matching `@Field.Desc` descriptor keys were updated together so the re-entry flow stays consistent end to end.

**KE-3443 — Create Folder retry & error handling** — Create Folder had no **Allow Retry** control and re-threw on unexpected errors, unlike Copy/Delete/Move/Write.

- Added an **"Allow Retry"** input to the **Create Folder** request. Re-entry prompts on validation failure are now gated on this flag; when disabled, a direct error message is returned instead.
- Create Folder no longer re-throws on unexpected errors — it now returns **`Success = false`** with a **Message** (`MustAuthorizeException` still propagates).
- Documented the **Allow Retry** input in `CreateFolder.md`.

### Backward Compatibility

**100% Backward Compatible** — The re-entry toggle change is a copy-only relabel with no change to the operation contract. Create Folder's existing method signatures are preserved via backward-compatible wrappers (`allowRetry = true`).

### Breaking Changes

**None**

---

## Version 4.0.4 — Documentation & Response Field Readability

**Release Date**: August, 2026

### Resolved Issues

**Root folder path documentation** — Users had no reference for how to point a request at the OneDrive root folder, causing prolonged trial and error.

- Added a **"Root folder path"** note (after the **Input Parameters** section) to all **18 catalog-request documentation pages** that accept a path: `ChangesSince`, `Copy`, `CopyWithName`, `CreateFolder`, `Delete`, `DownloadAsFile`, `DownloadAsLink`, `GetInfo`, `ListFiles`, `ListFolders`, `Move`, `Read`, `Rename`, `ShareFile`, `WhenAFileIsChanged`, `Write`, `WriteText`, `WriteWithCustomFileName`.
- Each note explains that the OneDrive **root folder** is referenced with **`drive/root:/`**, with a subfolder example (`drive/root:/Documents`) and a pointer to **Get Drives** for the shared drive / SharePoint / Teams format (`drives/{driveId}/root:/…`).

**Truncated response text boxes** — The **Message** and **Source Path** fields rendered as small single-line inputs, so long values (e.g. SharePoint paths) and error messages were truncated and unreadable without clicking into the field.

- Changed the **Message** (response) and **Source Path** fields across all catalog requests to render at **`visualWidth = "L"`** (large) so long paths and error messages are visible without interaction.

### Backward Compatibility

**100% Backward Compatible** — Documentation-only and field-appearance changes; no behavior, input, or output contract changes.

### Breaking Changes

**None**

---

## Version 4.0.3 — AI Assistant Tab (MCP Integration)

**Release Date**: July, 2026

### New Features

#### AI Assistant Tab

A new **AI Assistant** tab is now available in the OneDrive extension alongside the existing Authentication, Documentation, and OneDrive Audit tabs.

Users can type plain-English questions (e.g., *"List files in my Documents folder"* or *"Find files modified this week"*) and the extension automatically translates them into Microsoft Graph API calls — no manual workflow configuration required.

**Supported AI Providers:**

| Provider | Model |
|---|---|
| Anthropic (Claude) | claude-haiku-3-5 |
| OpenAI (GPT) | gpt-4o-mini |
| Google (Gemini) | gemini-2.5-flash |

**Key Highlights:**

- **Generic Graph API execution** — AI plans the exact `{method, path, body}`; no hardcoded intents
- **Dynamic columns** — result columns derived at runtime from the Graph API response fields
- **KQL support** — full Microsoft Keyword Query Language for advanced file searches via `https://graph.microsoft.com/v1.0/search/query`
- **App-only auth support** — automatically replaces `/me/` with `/users/{email}/` for Server-Server (Client Credentials) auth mode
- **Shared item support** — merges `remoteItem` fields for shared files/folders
- **Credential reloading** — loads fresh auth credentials on every query; real-time auth tab changes take effect immediately

**Supported Operations:**

- List files and folders in any OneDrive path
- Search files by keyword (KQL)
- Get file metadata and details
- Filter by shared, modified date, file type, and more

---

### Dependency Updates

| Dependency | From | To |
|---|---|---|
| `microsoft-auth-sdk` | 1.0.3 | 1.0.5 |

---

### Code Changes

| File | Change |
|---|---|
| `OnedriveExtension.java` | Added "AI Assistant" tab in `customTabs()` map; version bumped to 4.0.3 |
| `OnedriveMcpQueryService.java` | New — AI query execution engine; plans and executes any Graph API call generically; handles app-only vs. delegated auth |
| `OnedriveMcpSetupResource.java` | New — JAX-RS resource at `/mcp-setup`; intercepts NLQ requests from AI Assistant tab; renders URLs as clickable links |
| `OnedriveMcpProductConfig.java` | New — product config for KQL search, Graph API planning prompt, query generation prompt, and response parsing |
| `build.gradle` | Upgraded `microsoft-auth-sdk` from 1.0.3 to 1.0.5 |

---

### Backward Compatibility

**100% Backward Compatible** — All existing workflows and catalog requests are unaffected. The AI Assistant tab is opt-in and requires an AI provider API key to activate.

### Breaking Changes

**None**

---

## Version 4.0.2

- **Krista Service APIs Java** : 1.0.121

## Resolved Issues
-  Documentation: Drive Path Clarity
Updated 4 pages (GetDrives.md, ListFiles.md, ListFolders.md, DownloadAsFile.md) with a consistent two-section format distinguishing:
- Personal OneDrive → drive/root:/path — no Get Drives call needed
- SharePoint / Shared Drive → drives/{driveId}/root:/path — must call Get Drives first to obtain the ID
-GraphServiceClientProvider constructor now initializes the delegate with PUBLIC credentials when invokeAsUser() == true (MCP mode), aligning the lookup key with
  what the callback stored. Non-MCP paths unaffected.
- Added **Get Drives** catalog request that returns all drives accessible to the authenticated user (drive path, name, type, and owner). Drive paths returned can be used directly as the source path for shared folder operations.

---

## Version 4.0.1

- **Krista Service APIs Java** : 1.0.121

## Resolved Issues

KE-3303 : Fixed MCP per-user OAuth flow to always use Krista default public credentials (from `config.properties`) for both the authorization URL and the callback token exchange. Previously, the flow attempted to load the operator's configured Azure app first, which caused an `AADSTS7000215` (invalid client secret) error when the operator's app credentials did not match the redirect URIs registered for the Krista default app. The MCP flow now consistently uses the Krista default app throughout, ensuring the client ID and secret match between the authorize request and the token exchange.

---

## Version 4.0.0

- **Krista Service APIs Java** : 1.0.121

## New Features

- Added three authentication modes: Quick Setup (Krista-managed), User Authentication (OAuth 2.0), and Server to Server Authentication (Client Credentials)
- Added Audit tab with real-time dashboard tracking operation history, token health, client secret expiry, and subscription status
- Added subscription cleanup on email/credential change — old Microsoft Graph subscriptions are automatically deleted when a different user is configured
- Fixed OAuth AADSTS500112 redirect URI mismatch by persisting `publicAuthMode` in the credential store
- Updated Authentication tab documentation with step-by-step Azure AD app registration guides and AADSTS error troubleshooting table

---

## Version 3.0.9

- **Krista Service APIs Java** : 1.0.117

## New Features
KE-3161 : Added "Copy With Name" catalog request to atomically copy a file into a target folder with a specified name.

## Version 3.0.8

- **Krista Service APIs Java** : 1.0.117

## Resolved Issues
KE-3153 : Increased async Thread.sleep delay from 2s to 10s in ManagementArea to prevent WAIT_FOR_EVENT race condition.

## Version 3.0.7

- **Developer Name** : Vrushali Gaikwad
- **Krista Service APIs Java** : 1.0.117
- **Global Catalog Version** : GC-2025.11.3

## Resolved Issues
KE-2433 : Added set up area requests (Save Configuration, Test Connection, Health Check)
KE-2448 : Bug Fix- Move request was not working for both invalid inputs.
KE-2444 : Documentation enhancements.

