/*
 * Onedrive 3.0 Extension for Krista
 * Copyright (C) 2025 Krista Software
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package app.krista.extensions.essentials.cloudfiles.onedrive.audit;

public enum AuditEventType {
    CATALOG_REQUEST_TRIGGERED,
    CATALOG_REQUEST_SUCCESS,
    CATALOG_REQUEST_FAILURE,
    PASSWORD_CHANGE_DETECTED,
    TOKEN_REFRESHED,
    TOKEN_EXPIRY_DETECTED,
    INVALID_TOKEN_DETECTED,
    CLIENT_SECRET_EXPIRY_DETECTED,
    INVALID_CLIENT_SECRET_DETECTED,
    AUTH_CONFIG_FAILURE,
    // File event tracking (matching OneDriveAuthConfig @AuditEventGroup)
    FILE_UPLOADED,
    FILE_DOWNLOADED,
    FILE_DELETED,
    FILE_COPIED,
    FILE_MOVED,
    FOLDER_CREATED,
    // File change notification tracking (Alert Monitoring tab)
    FILE_NOTIFICATION_RECEIVED,
    FILE_NOTIFICATION_TRIGGERED,
    FILE_NOTIFICATION_DROPPED_DUPLICATE,
    FILE_NOTIFICATION_ERROR
}
