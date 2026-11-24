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

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests for AuditSummary — covers every event type branch in updateWith()
 * and rate calculation logic.
 */
public class AuditSummaryTest {

    private AuditSummary summary;

    @Before
    public void setUp() {
        summary = new AuditSummary();
    }

    // ── helper ────────────────────────────────────────────────────────────────

    private AuditRecord record(String eventType) {
        AuditRecord r = new AuditRecord();
        r.eventType = eventType;
        r.timestamp = System.currentTimeMillis();
        r.reason = "test reason";
        return r;
    }

    // ── default values ────────────────────────────────────────────────────────

    @Test
    public void testDefaultValues_areZeroOrNull() {
        assertEquals(0, summary.totalRequests);
        assertEquals(0, summary.successCount);
        assertEquals(0, summary.failureCount);
        assertEquals(0.0, summary.successRate, 0.0);
        assertEquals(0.0, summary.failureRate, 0.0);
        assertEquals(0L, summary.latestTimestamp);
        assertNull(summary.latestFailureReason);
        assertEquals(0, summary.passwordChangeCount);
        assertEquals(0, summary.tokenRefreshCount);
        assertEquals(0, summary.tokenExpiryCount);
        assertEquals(0, summary.invalidTokenCount);
        assertEquals(0, summary.clientSecretExpiryCount);
        assertEquals(0, summary.invalidClientSecretCount);
        assertEquals(0, summary.authConfigFailureCount);
        assertEquals(0, summary.fileUploadedCount);
        assertEquals(0, summary.fileDownloadedCount);
        assertEquals(0, summary.fileDeletedCount);
        assertEquals(0, summary.fileCopiedCount);
        assertEquals(0, summary.fileMovedCount);
        assertEquals(0, summary.folderCreatedCount);
        assertEquals(0, summary.fileNotificationReceived);
        assertEquals(0, summary.fileNotificationTriggered);
        assertEquals(0, summary.fileNotificationDroppedDuplicate);
        assertEquals(0, summary.fileNotificationError);
    }

    // ── catalog request events ─────────────────────────────────────────────────

    @Test
    public void testUpdateWith_catalogRequestTriggered_incrementsTotalRequests() {
        summary.updateWith(record("CATALOG_REQUEST_TRIGGERED"));
        assertEquals(1, summary.totalRequests);
        assertEquals(0, summary.successCount);
        assertEquals(0, summary.failureCount);
    }

    @Test
    public void testUpdateWith_catalogRequestSuccess_incrementsSuccessAndTimestamp() {
        AuditRecord r = record("CATALOG_REQUEST_SUCCESS");
        r.timestamp = 500000L;
        summary.updateWith(r);

        assertEquals(1, summary.successCount);
        assertEquals(0, summary.failureCount);
        assertEquals(500000L, summary.latestTimestamp);
    }

    @Test
    public void testUpdateWith_catalogRequestSuccess_doesNotUpdateOlderTimestamp() {
        AuditRecord r1 = record("CATALOG_REQUEST_SUCCESS");
        r1.timestamp = 3000L;
        summary.updateWith(r1);

        AuditRecord r2 = record("CATALOG_REQUEST_SUCCESS");
        r2.timestamp = 1000L; // older — should not replace
        summary.updateWith(r2);

        assertEquals(3000L, summary.latestTimestamp);
        assertEquals(2, summary.successCount);
    }

    @Test
    public void testUpdateWith_catalogRequestFailure_incrementsFailureAndSetsReason() {
        AuditRecord r = record("CATALOG_REQUEST_FAILURE");
        r.timestamp = 9999L;
        r.reason = "something broke";
        summary.updateWith(r);

        assertEquals(0, summary.successCount);
        assertEquals(1, summary.failureCount);
        assertEquals(9999L, summary.latestTimestamp);
        assertEquals("something broke", summary.latestFailureReason);
    }

    // ── auth events ───────────────────────────────────────────────────────────

    @Test
    public void testUpdateWith_passwordChangeDetected() {
        summary.updateWith(record("PASSWORD_CHANGE_DETECTED"));
        assertEquals(1, summary.passwordChangeCount);
        assertEquals(0, summary.failureCount); // password change does NOT increment failureCount
    }

    @Test
    public void testUpdateWith_tokenRefreshed() {
        summary.updateWith(record("TOKEN_REFRESHED"));
        assertEquals(1, summary.tokenRefreshCount);
        assertEquals(1, summary.totalRequests); // OAuth callback events count in Total Requests
    }

    @Test
    public void testUpdateWith_tokenExpiryDetected() {
        summary.updateWith(record("TOKEN_EXPIRY_DETECTED"));
        assertEquals(1, summary.tokenExpiryCount);
    }

    @Test
    public void testUpdateWith_invalidTokenDetected() {
        summary.updateWith(record("INVALID_TOKEN_DETECTED"));
        assertEquals(1, summary.invalidTokenCount);
    }

    @Test
    public void testUpdateWith_clientSecretExpiryDetected() {
        summary.updateWith(record("CLIENT_SECRET_EXPIRY_DETECTED"));
        assertEquals(1, summary.clientSecretExpiryCount);
    }

    @Test
    public void testUpdateWith_invalidClientSecretDetected() {
        summary.updateWith(record("INVALID_CLIENT_SECRET_DETECTED"));
        assertEquals(1, summary.invalidClientSecretCount);
    }

    @Test
    public void testUpdateWith_authConfigFailure_incrementsBothAuthConfigAndFailureCount() {
        AuditRecord r = record("AUTH_CONFIG_FAILURE");
        r.reason = "config error";
        r.timestamp = 8000L;
        summary.updateWith(r);

        assertEquals(1, summary.authConfigFailureCount);
        assertEquals(1, summary.failureCount);         // also bumps failureCount
        assertEquals(8000L, summary.latestTimestamp);
        assertEquals("config error", summary.latestFailureReason);
    }

    // ── file events ───────────────────────────────────────────────────────────

    @Test
    public void testUpdateWith_fileUploaded() {
        summary.updateWith(record("FILE_UPLOADED"));
        assertEquals(1, summary.fileUploadedCount);
    }

    @Test
    public void testUpdateWith_fileDownloaded() {
        summary.updateWith(record("FILE_DOWNLOADED"));
        assertEquals(1, summary.fileDownloadedCount);
    }

    @Test
    public void testUpdateWith_fileDeleted() {
        summary.updateWith(record("FILE_DELETED"));
        assertEquals(1, summary.fileDeletedCount);
    }

    @Test
    public void testUpdateWith_fileCopied() {
        summary.updateWith(record("FILE_COPIED"));
        assertEquals(1, summary.fileCopiedCount);
    }

    @Test
    public void testUpdateWith_fileMoved() {
        summary.updateWith(record("FILE_MOVED"));
        assertEquals(1, summary.fileMovedCount);
    }

    @Test
    public void testUpdateWith_folderCreated() {
        summary.updateWith(record("FOLDER_CREATED"));
        assertEquals(1, summary.folderCreatedCount);
    }

    // ── notification events ───────────────────────────────────────────────────

    @Test
    public void testUpdateWith_fileNotificationReceived() {
        summary.updateWith(record("FILE_NOTIFICATION_RECEIVED"));
        assertEquals(1, summary.fileNotificationReceived);
    }

    @Test
    public void testUpdateWith_fileNotificationTriggered() {
        summary.updateWith(record("FILE_NOTIFICATION_TRIGGERED"));
        assertEquals(1, summary.fileNotificationTriggered);
    }

    @Test
    public void testUpdateWith_fileNotificationDroppedDuplicate() {
        summary.updateWith(record("FILE_NOTIFICATION_DROPPED_DUPLICATE"));
        assertEquals(1, summary.fileNotificationDroppedDuplicate);
    }

    @Test
    public void testUpdateWith_fileNotificationError() {
        summary.updateWith(record("FILE_NOTIFICATION_ERROR"));
        assertEquals(1, summary.fileNotificationError);
    }

    // ── unknown event type ────────────────────────────────────────────────────

    @Test
    public void testUpdateWith_unknownEventType_doesNotThrowOrModifyAnyCount() {
        AuditRecord r = new AuditRecord();
        r.eventType = "TOTALLY_UNKNOWN_EVENT_XYZ";
        summary.updateWith(r); // must not throw

        assertEquals(0, summary.totalRequests);
        assertEquals(0, summary.successCount);
        assertEquals(0, summary.failureCount);
    }

    @Test(expected = NullPointerException.class)
    public void testUpdateWith_nullEventType_throwsNpe() {
        // AuditEventType.valueOf(null) throws NPE; only IllegalArgumentException is caught
        AuditRecord r = new AuditRecord();
        r.eventType = null;
        summary.updateWith(r);
    }

    // ── rate calculations ─────────────────────────────────────────────────────

    @Test
    public void testUpdateWith_noCompletedRequests_ratesRemainZero() {
        summary.updateWith(record("CATALOG_REQUEST_TRIGGERED"));
        assertEquals(0.0, summary.successRate, 0.0);
        assertEquals(0.0, summary.failureRate, 0.0);
    }

    @Test
    public void testUpdateWith_allSuccess_rateIs100() {
        summary.updateWith(record("CATALOG_REQUEST_SUCCESS"));
        assertEquals(100.0, summary.successRate, 0.01);
        assertEquals(0.0, summary.failureRate, 0.01);
    }

    @Test
    public void testUpdateWith_allFailure_failureRateIs100() {
        summary.updateWith(record("CATALOG_REQUEST_FAILURE"));
        assertEquals(0.0, summary.successRate, 0.01);
        assertEquals(100.0, summary.failureRate, 0.01);
    }

    @Test
    public void testUpdateWith_threeSuccessOneFailure_rates() {
        summary.updateWith(record("CATALOG_REQUEST_SUCCESS"));
        summary.updateWith(record("CATALOG_REQUEST_SUCCESS"));
        summary.updateWith(record("CATALOG_REQUEST_SUCCESS"));
        summary.updateWith(record("CATALOG_REQUEST_FAILURE"));

        assertEquals(75.0, summary.successRate, 0.1);
        assertEquals(25.0, summary.failureRate, 0.1);
    }

    @Test
    public void testUpdateWith_oneSuccessOneFailure_rates50() {
        summary.updateWith(record("CATALOG_REQUEST_SUCCESS"));
        summary.updateWith(record("CATALOG_REQUEST_FAILURE"));

        assertEquals(50.0, summary.successRate, 0.1);
        assertEquals(50.0, summary.failureRate, 0.1);
    }

    // ── accumulation ──────────────────────────────────────────────────────────

    @Test
    public void testUpdateWith_multipleFilesUploaded_accumulatesCount() {
        summary.updateWith(record("FILE_UPLOADED"));
        summary.updateWith(record("FILE_UPLOADED"));
        summary.updateWith(record("FILE_UPLOADED"));
        assertEquals(3, summary.fileUploadedCount);
    }

    @Test
    public void testUpdateWith_mixedEvents_eachCounterIndependent() {
        summary.updateWith(record("CATALOG_REQUEST_SUCCESS"));
        summary.updateWith(record("CATALOG_REQUEST_FAILURE"));
        summary.updateWith(record("FILE_UPLOADED"));
        summary.updateWith(record("TOKEN_EXPIRY_DETECTED"));

        assertEquals(1, summary.successCount);
        assertEquals(1, summary.failureCount);
        assertEquals(1, summary.fileUploadedCount);
        assertEquals(1, summary.tokenExpiryCount);
        // Other counters untouched
        assertEquals(0, summary.fileDownloadedCount);
        assertEquals(0, summary.passwordChangeCount);
    }
}
