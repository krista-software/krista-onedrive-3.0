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

public class AuditSummary {
    public long totalRequests;
    public long successCount;
    public long failureCount;
    public double successRate;
    public double failureRate;
    public long latestTimestamp;
    public String latestFailureReason;

    // Auth event counters
    public long passwordChangeCount;
    public long tokenRefreshCount;
    public long tokenExpiryCount;
    public long invalidTokenCount;
    public long clientSecretExpiryCount;
    public long invalidClientSecretCount;
    public long authConfigFailureCount;

    // File event counters (matching OneDriveAuthConfig @AuditEventGroup)
    public long fileUploadedCount;
    public long fileDownloadedCount;
    public long fileDeletedCount;
    public long fileCopiedCount;
    public long fileMovedCount;
    public long folderCreatedCount;

    // File change notification counters (Alert Monitoring tab)
    public long fileNotificationReceived;
    public long fileNotificationTriggered;
    public long fileNotificationDroppedDuplicate;
    public long fileNotificationError;

    synchronized void updateWith(AuditRecord record) {
        AuditEventType type;
        try {
            type = AuditEventType.valueOf(record.eventType);
        } catch (IllegalArgumentException e) {
            return;
        }

        switch (type) {
            case CATALOG_REQUEST_TRIGGERED -> totalRequests++;
            case CATALOG_REQUEST_SUCCESS -> {
                successCount++;
                if (record.timestamp > latestTimestamp) latestTimestamp = record.timestamp;
            }
            case CATALOG_REQUEST_FAILURE -> {
                failureCount++;
                if (record.timestamp > latestTimestamp) latestTimestamp = record.timestamp;
                latestFailureReason = record.reason;
            }
            case PASSWORD_CHANGE_DETECTED -> passwordChangeCount++;
            case TOKEN_REFRESHED -> { tokenRefreshCount++; totalRequests++; }
            case TOKEN_EXPIRY_DETECTED -> tokenExpiryCount++;
            case INVALID_TOKEN_DETECTED -> invalidTokenCount++;
            case CLIENT_SECRET_EXPIRY_DETECTED -> clientSecretExpiryCount++;
            case INVALID_CLIENT_SECRET_DETECTED -> invalidClientSecretCount++;
            case AUTH_CONFIG_FAILURE -> {
                authConfigFailureCount++;
                failureCount++;
                if (record.timestamp > latestTimestamp) latestTimestamp = record.timestamp;
                latestFailureReason = record.reason;
            }
            case FILE_UPLOADED -> fileUploadedCount++;
            case FILE_DOWNLOADED -> fileDownloadedCount++;
            case FILE_DELETED -> fileDeletedCount++;
            case FILE_COPIED -> fileCopiedCount++;
            case FILE_MOVED -> fileMovedCount++;
            case FOLDER_CREATED -> folderCreatedCount++;
            case FILE_NOTIFICATION_RECEIVED -> fileNotificationReceived++;
            case FILE_NOTIFICATION_TRIGGERED -> fileNotificationTriggered++;
            case FILE_NOTIFICATION_DROPPED_DUPLICATE -> fileNotificationDroppedDuplicate++;
            case FILE_NOTIFICATION_ERROR -> fileNotificationError++;
        }

        long completed = successCount + failureCount;
        if (completed > 0) {
            successRate = Math.round((double) successCount / completed * 1000.0) / 10.0;
            failureRate = Math.round((double) failureCount / completed * 1000.0) / 10.0;
        }
    }
}
