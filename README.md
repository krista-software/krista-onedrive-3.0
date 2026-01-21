# Onedrive 3.0 Extension for Krista

**Version:** 3.0.7
## Overview

The Microsoft OneDrive Extension provides comprehensive integration with Microsoft OneDrive cloud storage through the Microsoft Graph API v1.0. The extension follows a clean, layered architecture pattern with clear separation of concerns across five distinct layers.

### Key Capabilities

- **File Operations**: Upload, download, copy, move, rename, delete files
- **Folder Management**: List files/folders, create folders, get folder statistics
- **Permission-Based Search**: Search files by access level (Read, Write, Read and Write, Own)
- **Event-Driven Monitoring**: Real-time file change notifications (Created, Updated, Deleted, Shared)
- **Asynchronous Processing**: Task-based architecture for long-running operations
- **OAuth 2.0 Authentication**: Secure authentication via Microsoft Azure AD

### Extension Metadata

| Property | Value |
|----------|-------|
| Extension ID | `catEntryDomain_30c63e77-37f2-4a1d-afd8-f6bfda07ad57` |
| Domain | Cloud Files |
| Ecosystem | Essentials |
| Total Catalog Requests | 23 |
| Areas | 3 (Setup, Management, OneDrive) |
| Authentication | OAuth 2.0 (Microsoft Azure AD) |
| API Integration | Microsoft Graph API v1.0 |

---



## License

This project is licensed under the **GNU General Public License v3.0**.

```
Onedrive 3.0 Extension for Krista
Copyright (C) 2025 Krista Software

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <https://www.gnu.org/licenses/>.
```

For the full license text, see the [LICENSE](LICENSE) file or visit https://www.gnu.org/licenses/gpl-3.0.html.
