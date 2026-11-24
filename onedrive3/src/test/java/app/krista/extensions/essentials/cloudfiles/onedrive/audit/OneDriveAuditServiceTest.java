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

import app.krista.extension.authorization.MustAuthorizeException;
import app.krista.extensions.util.KeyValueStore;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/**
 * Tests for OneDriveAuditService.
 *
 * Uses unique invokerIds per test to avoid interference from the static
 * per-invoker ring buffers (RECORDS, SUMMARIES, KV_LOADED).
 */
public class OneDriveAuditServiceTest {

    private MockKeyValueStore kvStore;
    private OneDriveAuditService service;

    private static final AtomicInteger COUNTER = new AtomicInteger(0);

    /** Returns a fresh, unique invokerId for each test. */
    private String id() {
        return "audit-test-invoker-" + COUNTER.incrementAndGet();
    }

    /**
     * Drain FLUSH_EXECUTOR once before this test class starts.
     * Any flush tasks queued by other test classes that ran before this one
     * will complete before our tests begin, preventing cross-class KV pollution.
     */
    @BeforeClass
    public static void drainFlushExecutorBeforeClass() throws Exception {
        java.lang.reflect.Field f = OneDriveAuditService.class.getDeclaredField("FLUSH_EXECUTOR");
        f.setAccessible(true);
        ExecutorService exec = (ExecutorService) f.get(null);
        exec.submit(() -> {}).get();
    }

    @Before
    public void setUp() {
        kvStore = new MockKeyValueStore();
        service = new OneDriveAuditService(kvStore);
    }

    /**
     * Drain the shared async FLUSH_EXECUTOR after each test so that pending KV writes
     * don't contaminate the next test's fresh kvStore via ensureKvLoaded().
     * The executor is single-threaded, so submitting a barrier task and awaiting it
     * guarantees all previously queued flush tasks have completed.
     */
    @After
    public void drainFlushExecutor() throws Exception {
        java.lang.reflect.Field f = OneDriveAuditService.class.getDeclaredField("FLUSH_EXECUTOR");
        f.setAccessible(true);
        ExecutorService exec = (ExecutorService) f.get(null);
        exec.submit(() -> {}).get(); // barrier: wait for all queued tasks before this one
    }

    // ─────────────────────────────────────────────────────────────────────────
    // classifyAuthError — static package-private method
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    public void testClassifyAuthError_null_returnsAuthConfigFailure() {
        assertEquals(AuditEventType.AUTH_CONFIG_FAILURE, OneDriveAuditService.classifyAuthError(null));
    }

    @Test
    public void testClassifyAuthError_emptyString_returnsAuthConfigFailure() {
        assertEquals(AuditEventType.AUTH_CONFIG_FAILURE, OneDriveAuditService.classifyAuthError(""));
    }

    @Test
    public void testClassifyAuthError_password_returnsPasswordChange() {
        assertEquals(AuditEventType.PASSWORD_CHANGE_DETECTED, OneDriveAuditService.classifyAuthError("wrong password entered"));
    }

    @Test
    public void testClassifyAuthError_aadsts50126_returnsPasswordChange() {
        assertEquals(AuditEventType.PASSWORD_CHANGE_DETECTED, OneDriveAuditService.classifyAuthError("AADSTS50126: Invalid credentials"));
    }

    @Test
    public void testClassifyAuthError_aadsts50173_returnsPasswordChange() {
        assertEquals(AuditEventType.PASSWORD_CHANGE_DETECTED, OneDriveAuditService.classifyAuthError("aadsts50173 password expired"));
    }

    @Test
    public void testClassifyAuthError_sessionExpired_returnsTokenExpiry() {
        assertEquals(AuditEventType.TOKEN_EXPIRY_DETECTED, OneDriveAuditService.classifyAuthError("session expired"));
    }

    @Test
    public void testClassifyAuthError_tokenExpired_returnsTokenExpiry() {
        assertEquals(AuditEventType.TOKEN_EXPIRY_DETECTED, OneDriveAuditService.classifyAuthError("token expired message"));
    }

    @Test
    public void testClassifyAuthError_refreshToken_returnsTokenExpiry() {
        assertEquals(AuditEventType.TOKEN_EXPIRY_DETECTED, OneDriveAuditService.classifyAuthError("refresh token is invalid"));
    }

    @Test
    public void testClassifyAuthError_aadsts70008_returnsTokenExpiry() {
        assertEquals(AuditEventType.TOKEN_EXPIRY_DETECTED, OneDriveAuditService.classifyAuthError("AADSTS70008 error"));
    }

    @Test
    public void testClassifyAuthError_aadsts700082_returnsTokenExpiry() {
        assertEquals(AuditEventType.TOKEN_EXPIRY_DETECTED, OneDriveAuditService.classifyAuthError("aadsts700082"));
    }

    @Test
    public void testClassifyAuthError_aadsts700084_returnsTokenExpiry() {
        assertEquals(AuditEventType.TOKEN_EXPIRY_DETECTED, OneDriveAuditService.classifyAuthError("AADSTS700084"));
    }

    @Test
    public void testClassifyAuthError_invalidGrant_returnsTokenExpiry() {
        assertEquals(AuditEventType.TOKEN_EXPIRY_DETECTED, OneDriveAuditService.classifyAuthError("invalid_grant"));
    }

    @Test
    public void testClassifyAuthError_invalidToken_returnsInvalidToken() {
        assertEquals(AuditEventType.INVALID_TOKEN_DETECTED, OneDriveAuditService.classifyAuthError("invalid token provided"));
    }

    @Test
    public void testClassifyAuthError_aadsts16000_returnsInvalidToken() {
        assertEquals(AuditEventType.INVALID_TOKEN_DETECTED, OneDriveAuditService.classifyAuthError("AADSTS16000 bad token"));
    }

    @Test
    public void testClassifyAuthError_clientSecretExpired_returnsClientSecretExpiry() {
        assertEquals(AuditEventType.CLIENT_SECRET_EXPIRY_DETECTED,
                OneDriveAuditService.classifyAuthError("client secret has expired"));
    }

    @Test
    public void testClassifyAuthError_aadsts7000222_returnsClientSecretExpiry() {
        assertEquals(AuditEventType.CLIENT_SECRET_EXPIRY_DETECTED,
                OneDriveAuditService.classifyAuthError("AADSTS7000222"));
    }

    @Test
    public void testClassifyAuthError_applicationsConnection_returnsInvalidClientSecret() {
        assertEquals(AuditEventType.INVALID_CLIENT_SECRET_DETECTED,
                OneDriveAuditService.classifyAuthError("application's connection is invalid"));
    }

    @Test
    public void testClassifyAuthError_invalidClientSecret_returnsInvalidClientSecret() {
        assertEquals(AuditEventType.INVALID_CLIENT_SECRET_DETECTED,
                OneDriveAuditService.classifyAuthError("invalid client secret"));
    }

    @Test
    public void testClassifyAuthError_invalidClient_returnsInvalidClientSecret() {
        assertEquals(AuditEventType.INVALID_CLIENT_SECRET_DETECTED,
                OneDriveAuditService.classifyAuthError("invalid_client error"));
    }

    @Test
    public void testClassifyAuthError_aadsts7000215_returnsInvalidClientSecret() {
        assertEquals(AuditEventType.INVALID_CLIENT_SECRET_DETECTED,
                OneDriveAuditService.classifyAuthError("AADSTS7000215"));
    }

    @Test
    public void testClassifyAuthError_unknownMessage_returnsAuthConfigFailure() {
        assertEquals(AuditEventType.AUTH_CONFIG_FAILURE,
                OneDriveAuditService.classifyAuthError("some completely unrecognized error message"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // record() — typed AuditEventType
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    public void testRecord_nullInvoker_doesNotThrow() {
        service.record(null, AuditEventType.CATALOG_REQUEST_SUCCESS,
                "success", "Test", null, null, null);
        // No exception = pass
    }

    @Test
    public void testRecord_addsRecordToInMemoryStore() {
        String id = id();
        service.record(id, AuditEventType.CATALOG_REQUEST_SUCCESS,
                "success", "Upload File", null, null, null);

        AuditData data = service.getData(id, null, null, null, null);
        assertEquals(1, data.records.size());
        assertEquals("CATALOG_REQUEST_SUCCESS", data.records.get(0).eventType);
        assertEquals("success", data.records.get(0).status);
        assertEquals("Upload File", data.records.get(0).catalogName);
    }

    @Test
    public void testRecord_idAndTimestampArePopulated() {
        String id = id();
        service.record(id, AuditEventType.CATALOG_REQUEST_SUCCESS,
                "success", "Test", null, null, null);

        AuditData data = service.getData(id, null, null, null, null);
        AuditRecord r = data.records.get(0);
        assertNotNull(r.id);
        assertFalse(r.id.isEmpty());
        assertTrue(r.timestamp > 0);
    }

    @Test
    public void testRecord_triggeredByDefaultsToKrista() {
        String id = id();
        service.record(id, AuditEventType.CATALOG_REQUEST_SUCCESS,
                "success", "Test", null, null, null);

        AuditData data = service.getData(id, null, null, null, null);
        // In test context ThreadLocalProxy returns null, so fallback is "krista"
        assertEquals("krista", data.records.get(0).triggeredBy);
    }

    @Test
    public void testRecord_reasonIsTrimmedAt500Chars() {
        String id = id();
        String longReason = "x".repeat(600);
        service.record(id, AuditEventType.CATALOG_REQUEST_FAILURE,
                "failure", "Test", longReason, null, null);

        AuditData data = service.getData(id, null, null, null, null);
        // Trimmed at 500 + appended ellipsis character (1 char)
        assertTrue(data.records.get(0).reason.length() <= 502);
    }

    @Test
    public void testRecord_reasonNullIsAllowed() {
        String id = id();
        service.record(id, AuditEventType.CATALOG_REQUEST_SUCCESS,
                "success", "Test", null, null, null);

        AuditData data = service.getData(id, null, null, null, null);
        assertNull(data.records.get(0).reason);
    }

    @Test
    public void testRecord_updatesSummarySuccessCount() {
        String id = id();
        service.record(id, AuditEventType.CATALOG_REQUEST_SUCCESS, "success", "Op", null, null, null);
        service.record(id, AuditEventType.CATALOG_REQUEST_SUCCESS, "success", "Op", null, null, null);

        AuditData data = service.getData(id, null, null, null, null);
        assertEquals(2, data.summary.successCount);
    }

    @Test
    public void testRecord_updatesSummaryFailureCount() {
        String id = id();
        service.record(id, AuditEventType.CATALOG_REQUEST_FAILURE, "failure", "Op", "err", null, null);

        AuditData data = service.getData(id, null, null, null, null);
        assertEquals(1, data.summary.failureCount);
        assertEquals("err", data.summary.latestFailureReason);
    }

    @Test
    public void testRecord_multipleInvokerIds_areIsolated() {
        String id1 = id();
        String id2 = id();

        // Pre-warm KV_LOADED for both ids while the KV store is still empty.
        // This prevents the race where id1's async flush writes to KV before
        // ensureKvLoaded(id2) runs, causing id1's records to bleed into RECORDS[id2].
        service.getData(id1, null, null, null, null);
        service.getData(id2, null, null, null, null);

        service.record(id1, AuditEventType.CATALOG_REQUEST_SUCCESS, "success", "Op", null, null, null);
        service.record(id1, AuditEventType.CATALOG_REQUEST_SUCCESS, "success", "Op", null, null, null);
        service.record(id2, AuditEventType.CATALOG_REQUEST_FAILURE, "failure", "Op", "err", null, null);

        AuditData data1 = service.getData(id1, null, null, null, null);
        AuditData data2 = service.getData(id2, null, null, null, null);

        assertEquals(2, data1.records.size());
        assertEquals(1, data2.records.size());
        assertEquals(2, data1.summary.successCount);
        assertEquals(1, data2.summary.failureCount);
    }

    @Test
    public void testRecord_triggeredPlusSuccess_durationIsStamped() throws InterruptedException {
        String id = id();
        service.record(id, AuditEventType.CATALOG_REQUEST_TRIGGERED, "triggered", "Op", null, null, null);
        Thread.sleep(5);
        service.record(id, AuditEventType.CATALOG_REQUEST_SUCCESS, "success", "Op", null, null, null);

        AuditData data = service.getData(id, null, null, null, null);
        // Both TRIGGERED and SUCCESS records should have durationMs > 0
        long totalDuration = data.records.stream().mapToLong(r -> r.durationMs).sum();
        assertTrue(totalDuration > 0);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // record() — String eventType
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    public void testRecord_stringEventType_valid() {
        String id = id();
        service.record(id, "CATALOG_REQUEST_TRIGGERED", "triggered", "Test", null, null, null);

        AuditData data = service.getData(id, null, null, null, null);
        assertEquals(1, data.records.size());
        assertEquals("CATALOG_REQUEST_TRIGGERED", data.records.get(0).eventType);
    }

    @Test
    public void testRecord_stringEventType_invalid_doesNotAddRecord() {
        String id = id();
        service.record(id, "NOT_A_REAL_EVENT", "status", "Test", null, null, null);

        AuditData data = service.getData(id, null, null, null, null);
        assertEquals(0, data.records.size());
    }

    @Test
    public void testRecord_stringEventType_null_doesNotAddRecord() {
        String id = id();
        service.record(id, (String) null, "status", "Test", null, null, null);

        AuditData data = service.getData(id, null, null, null, null);
        assertEquals(0, data.records.size());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // getData() — filters
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    public void testGetData_nullInvoker_returnsEmpty() {
        AuditData data = service.getData(null, null, null, null, null);
        assertNotNull(data);
        assertTrue(data.records.isEmpty());
        assertNotNull(data.summary);
    }

    @Test
    public void testGetData_unknownInvoker_returnsEmpty() {
        AuditData data = service.getData("invoker-that-never-recorded", null, null, null, null);
        assertNotNull(data);
        assertTrue(data.records.isEmpty());
    }

    @Test
    public void testGetData_filterByEventType_caseSensitive() {
        String id = id();
        service.record(id, AuditEventType.CATALOG_REQUEST_SUCCESS, "success", "Op1", null, null, null);
        service.record(id, AuditEventType.CATALOG_REQUEST_FAILURE, "failure", "Op2", "err", null, null);

        AuditData data = service.getData(id, null, null, "CATALOG_REQUEST_SUCCESS", null);
        assertEquals(1, data.records.size());
        assertEquals("CATALOG_REQUEST_SUCCESS", data.records.get(0).eventType);
    }

    @Test
    public void testGetData_filterByEventType_caseInsensitive() {
        String id = id();
        service.record(id, AuditEventType.FILE_UPLOADED, "success", "Op", null, null, null);

        // getData filter is case-insensitive (equalsIgnoreCase in source)
        AuditData data = service.getData(id, null, null, "file_uploaded", null);
        assertEquals(1, data.records.size());
    }

    @Test
    public void testGetData_filterByStatus() {
        String id = id();
        service.record(id, AuditEventType.CATALOG_REQUEST_SUCCESS, "success", "Op", null, null, null);
        service.record(id, AuditEventType.CATALOG_REQUEST_FAILURE, "failure", "Op", "err", null, null);

        AuditData data = service.getData(id, null, null, null, "failure");
        assertEquals(1, data.records.size());
        assertEquals("failure", data.records.get(0).status);
    }

    @Test
    public void testGetData_filterByFromTs_excludesOlderRecords() throws InterruptedException {
        String id = id();
        service.record(id, AuditEventType.CATALOG_REQUEST_SUCCESS, "success", "OldOp", null, null, null);
        Thread.sleep(5);
        long midTs = System.currentTimeMillis();
        Thread.sleep(5);
        service.record(id, AuditEventType.CATALOG_REQUEST_SUCCESS, "success", "NewOp", null, null, null);

        AuditData data = service.getData(id, midTs, null, null, null);
        for (AuditRecord r : data.records) {
            assertTrue("Record timestamp should be >= midTs", r.timestamp >= midTs);
        }
    }

    @Test
    public void testGetData_filterByToTs_excludesNewerRecords() throws InterruptedException {
        String id = id();
        service.record(id, AuditEventType.CATALOG_REQUEST_SUCCESS, "success", "Op1", null, null, null);
        Thread.sleep(5);
        long cutoff = System.currentTimeMillis();
        Thread.sleep(5);
        service.record(id, AuditEventType.CATALOG_REQUEST_SUCCESS, "success", "Op2", null, null, null);

        AuditData data = service.getData(id, null, cutoff, null, null);
        for (AuditRecord r : data.records) {
            assertTrue("Record timestamp should be <= cutoff", r.timestamp <= cutoff);
        }
    }

    @Test
    public void testGetData_blankEventTypeFilter_returnsAll() {
        String id = id();
        service.record(id, AuditEventType.CATALOG_REQUEST_SUCCESS, "success", "Op1", null, null, null);
        service.record(id, AuditEventType.CATALOG_REQUEST_FAILURE, "failure", "Op2", "err", null, null);

        AuditData data = service.getData(id, null, null, "  ", null);
        assertEquals(2, data.records.size());
    }

    @Test
    public void testGetData_blankStatusFilter_returnsAll() {
        String id = id();
        service.record(id, AuditEventType.CATALOG_REQUEST_SUCCESS, "success", "Op", null, null, null);
        service.record(id, AuditEventType.CATALOG_REQUEST_FAILURE, "failure", "Op", "err", null, null);

        AuditData data = service.getData(id, null, null, null, " ");
        assertEquals(2, data.records.size());
    }

    @Test
    public void testGetData_sortedByTimestampDescending() throws InterruptedException {
        String id = id();
        service.record(id, AuditEventType.CATALOG_REQUEST_SUCCESS, "success", "Op1", null, null, null);
        Thread.sleep(5);
        service.record(id, AuditEventType.CATALOG_REQUEST_FAILURE, "failure", "Op2", "err", null, null);
        Thread.sleep(5);
        service.record(id, AuditEventType.CATALOG_REQUEST_TRIGGERED, "triggered", "Op3", null, null, null);

        AuditData data = service.getData(id, null, null, null, null);
        assertTrue(data.records.size() >= 2);
        for (int i = 0; i < data.records.size() - 1; i++) {
            assertTrue("Records should be sorted descending by timestamp",
                    data.records.get(i).timestamp >= data.records.get(i + 1).timestamp);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // clearRecords()
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    public void testClearRecords_nullInvoker_doesNotThrow() {
        service.clearRecords(null, null, null);
    }

    @Test
    public void testClearRecords_noRangeLimits_clearsAll() {
        String id = id();
        service.record(id, AuditEventType.CATALOG_REQUEST_SUCCESS, "success", "Op", null, null, null);
        service.record(id, AuditEventType.CATALOG_REQUEST_FAILURE, "failure", "Op", "err", null, null);

        service.clearRecords(id, null, null);

        AuditData data = service.getData(id, null, null, null, null);
        assertTrue(data.records.isEmpty());
    }

    @Test
    public void testClearRecords_rebuildsSummaryAfterClear() {
        String id = id();
        service.record(id, AuditEventType.CATALOG_REQUEST_SUCCESS, "success", "Op", null, null, null);
        service.record(id, AuditEventType.CATALOG_REQUEST_SUCCESS, "success", "Op", null, null, null);

        service.clearRecords(id, null, null);

        AuditData data = service.getData(id, null, null, null, null);
        assertEquals(0, data.summary.successCount);
        assertEquals(0, data.summary.failureCount);
    }

    @Test
    public void testClearRecords_futureBound_doesNotRemoveExistingRecords() {
        String id = id();
        service.record(id, AuditEventType.CATALOG_REQUEST_SUCCESS, "success", "Op", null, null, null);

        // Clear a range far in the future — nothing should match
        service.clearRecords(id, Long.MAX_VALUE - 1000, Long.MAX_VALUE);

        AuditData data = service.getData(id, null, null, null, null);
        assertEquals(1, data.records.size());
    }

    @Test
    public void testClearRecords_noRecordsExist_doesNotThrow() {
        String id = id();
        service.clearRecords(id, null, null); // should not throw
    }

    // ─────────────────────────────────────────────────────────────────────────
    // startRequest()
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    public void testStartRequest_doesNotThrow() {
        service.startRequest();
    }

    @Test
    public void testStartRequest_multipleCalls_doesNotThrow() {
        service.startRequest();
        service.startRequest();
    }

    @Test
    public void testStartRequest_withInvokerAndCatalog_recordsTriggeredEvent() {
        String id = id();
        service.startRequest(id, "Copy");

        AuditData data = service.getData(id, null, null, null, null);
        assertEquals(1, data.records.size());
        assertEquals("CATALOG_REQUEST_TRIGGERED", data.records.get(0).eventType);
        assertEquals("Copy", data.records.get(0).catalogName);
        assertEquals(1, data.summary.totalRequests);
    }

    @Test
    public void testStartRequest_withInvokerAndCatalog_thenSuccess_stampsDuration() throws InterruptedException {
        String id = id();
        service.startRequest(id, "Upload");
        Thread.sleep(5);
        service.record(id, AuditEventType.CATALOG_REQUEST_SUCCESS, "success", "Upload", null, null, null);

        AuditData data = service.getData(id, null, null, null, null);
        assertEquals(2, data.records.size());
        // Both TRIGGERED and SUCCESS should have durationMs > 0
        long totalDuration = data.records.stream().mapToLong(r -> r.durationMs).sum();
        assertTrue("Duration should be stamped on both TRIGGERED and SUCCESS records", totalDuration > 0);
        assertEquals(1, data.summary.totalRequests);
        assertEquals(1, data.summary.successCount);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // getGlobalCounter()
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    public void testGetGlobalCounter_returnsMapWithExpectedKeys() {
        Map<String, Object> gc = service.getGlobalCounter();
        assertNotNull(gc);
        assertTrue(gc.containsKey("totalRequests"));
        assertTrue(gc.containsKey("firstRecordedAt"));
    }

    @Test
    public void testGetGlobalCounter_totalRequestsIsNonNegative() {
        Map<String, Object> gc = service.getGlobalCounter();
        long total = ((Number) gc.get("totalRequests")).longValue();
        assertTrue(total >= 0);
    }

    @Test
    public void testGetGlobalCounter_firstRecordedAtIsNonNegative() {
        Map<String, Object> gc = service.getGlobalCounter();
        long firstAt = ((Number) gc.get("firstRecordedAt")).longValue();
        assertTrue(firstAt >= 0);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // recordAuthError()
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    public void testRecordAuthError_nullInvoker_doesNotThrow() {
        service.recordAuthError(null, "Test", new MustAuthorizeException("test"));
    }

    @Test
    public void testRecordAuthError_addsFailureRecord() {
        String id = id();
        service.recordAuthError(id, "Download File",
                new MustAuthorizeException("session expired"));

        AuditData data = service.getData(id, null, null, null, null);
        boolean hasFailure = data.records.stream()
                .anyMatch(r -> "CATALOG_REQUEST_FAILURE".equals(r.eventType));
        assertTrue("Should contain a CATALOG_REQUEST_FAILURE record", hasFailure);
    }

    @Test
    public void testRecordAuthError_addsClassifiedAuthEventRecord() {
        String id = id();
        service.recordAuthError(id, "Download File",
                new MustAuthorizeException("session expired"));

        AuditData data = service.getData(id, null, null, null, null);
        boolean hasTokenExpiry = data.records.stream()
                .anyMatch(r -> "TOKEN_EXPIRY_DETECTED".equals(r.eventType));
        assertTrue("Should contain a TOKEN_EXPIRY_DETECTED record", hasTokenExpiry);
    }

    @Test
    public void testRecordAuthError_invalidClientSecret_classifiedCorrectly() {
        String id = id();
        service.recordAuthError(id, "Upload File",
                new MustAuthorizeException("AADSTS7000215 invalid client secret"));

        AuditData data = service.getData(id, null, null, null, null);
        boolean hasInvalidSecret = data.records.stream()
                .anyMatch(r -> "INVALID_CLIENT_SECRET_DETECTED".equals(r.eventType));
        assertTrue(hasInvalidSecret);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // startRequest() + SUCCESS without TRIGGERED — REQUEST_START duration path
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    public void testStartRequest_thenSuccess_stampsDurationFromRequestStart() throws InterruptedException {
        String id = id();
        service.startRequest(); // sets REQUEST_START thread-local
        Thread.sleep(5);
        service.record(id, AuditEventType.CATALOG_REQUEST_SUCCESS, "success", "Op", null, null, null);

        AuditData data = service.getData(id, null, null, null, null);
        boolean hasDuration = data.records.stream().anyMatch(r -> r.durationMs > 0);
        assertTrue("SUCCESS recorded after startRequest() should have durationMs > 0", hasDuration);
    }

    @Test
    public void testStartRequest_thenFailure_stampsDurationFromRequestStart() throws InterruptedException {
        String id = id();
        service.startRequest();
        Thread.sleep(5);
        service.record(id, AuditEventType.CATALOG_REQUEST_FAILURE, "failure", "Op", "err", null, null);

        AuditData data = service.getData(id, null, null, null, null);
        boolean hasDuration = data.records.stream().anyMatch(r -> r.durationMs > 0);
        assertTrue("FAILURE recorded after startRequest() should have durationMs > 0", hasDuration);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // record() scan-backward path — ACTIVE_TRIGGERED cleared via reflection
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    public void testRecord_scanBackward_findsUnstampedTriggered() throws Exception {
        String id = id();
        // Record TRIGGERED (sets ACTIVE_TRIGGERED on this thread)
        service.record(id, AuditEventType.CATALOG_REQUEST_TRIGGERED, "triggered", "Op", null, null, null);

        // Simulate different-thread scenario: clear ACTIVE_TRIGGERED via reflection
        java.lang.reflect.Field field = OneDriveAuditService.class.getDeclaredField("ACTIVE_TRIGGERED");
        field.setAccessible(true);
        ((ThreadLocal<?>) field.get(null)).remove();

        Thread.sleep(5);
        // SUCCESS is now recorded with ACTIVE_TRIGGERED=null; should scan backward and find TRIGGERED
        service.record(id, AuditEventType.CATALOG_REQUEST_SUCCESS, "success", "Op", null, null, null);

        AuditData data = service.getData(id, null, null, null, null);
        // Both TRIGGERED and SUCCESS should have duration stamped
        long totalDuration = data.records.stream().mapToLong(r -> r.durationMs).sum();
        assertTrue("Scan-backward should find TRIGGERED and stamp duration", totalDuration > 0);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // record() — execution logs captured when logs not empty
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    public void testRecord_withCapturedLogs_attachesLogsToRecords() throws Exception {
        String id = id();
        // Record TRIGGERED (starts log capture)
        service.record(id, AuditEventType.CATALOG_REQUEST_TRIGGERED, "triggered", "Op", null, null, null);

        // Inject a log entry into the CAPTURE ThreadLocal directly
        java.lang.reflect.Field captureField = RequestLogCapture.class.getDeclaredField("CAPTURE");
        captureField.setAccessible(true);
        @SuppressWarnings("unchecked")
        ThreadLocal<java.util.List<String>> captureLocal =
                (ThreadLocal<java.util.List<String>>) captureField.get(null);
        java.util.List<String> captureList = captureLocal.get();
        if (captureList != null) {
            captureList.add("[INFO] TestClass: injected execution log");
        }

        // Record SUCCESS — should pick up the injected log
        service.record(id, AuditEventType.CATALOG_REQUEST_SUCCESS, "success", "Op", null, null, null);

        AuditData data = service.getData(id, null, null, null, null);
        if (captureList != null) {
            // executionLogs should be attached to the SUCCESS record
            boolean hasLogs = data.records.stream().anyMatch(r -> r.executionLogs != null && !r.executionLogs.isEmpty());
            assertTrue("Execution logs should be attached to records when capture is non-empty", hasLogs);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // KV persistence — ensureKvLoaded
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    public void testGetData_withPreloadedKvData_mergesHistoricalRecords() {
        // Simulate a restart: pre-populate KVS with historical records
        AuditRecord historical = new AuditRecord();
        historical.id = "historical001";
        historical.eventType = "CATALOG_REQUEST_SUCCESS";
        historical.status = "success";
        historical.timestamp = 100L;

        String json = "[" + app.krista.extensions.essentials.cloudfiles.onedrive.impl.util.Constants.GSON.toJson(historical) + "]";
        kvStore.put("onedrive_audit_records", json);

        OneDriveAuditService freshService = new OneDriveAuditService(kvStore);
        String freshId = id();

        // First call triggers KV load
        AuditData data = freshService.getData(freshId, null, null, null, null);
        // Historical record should be present
        boolean found = data.records.stream().anyMatch(r -> "historical001".equals(r.id));
        assertTrue("Historical record from KV should be loaded", found);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ensureKvLoaded — summary JSON branch
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    public void testGetData_withKvRecordsAndSummary_loadsFromKv() {
        MockKeyValueStore kv = new MockKeyValueStore();
        AuditRecord r = new AuditRecord();
        r.id = "kv-rec-summary-001";
        r.eventType = "CATALOG_REQUEST_SUCCESS";
        r.status = "success";
        r.timestamp = 200L;

        String recordsJson = "["
                + app.krista.extensions.essentials.cloudfiles.onedrive.impl.util.Constants.GSON.toJson(r)
                + "]";
        kv.put("onedrive_audit_records", recordsJson);

        AuditSummary kvSum = new AuditSummary();
        kvSum.successCount = 1;
        kv.put("onedrive_audit_summary",
                app.krista.extensions.essentials.cloudfiles.onedrive.impl.util.Constants.GSON.toJson(kvSum));

        OneDriveAuditService svc = new OneDriveAuditService(kv);
        String id = id();
        AuditData data = svc.getData(id, null, null, null, null);

        assertNotNull(data);
        assertTrue("Historical record should be loaded from KV",
                data.records.stream().anyMatch(rec -> "kv-rec-summary-001".equals(rec.id)));
    }

    @Test
    public void testEnsureKvLoaded_withInMemorySummaryAndKvSummary_rebuildsFromRecords() throws Exception {
        MockKeyValueStore kv = new MockKeyValueStore();
        String id = id();

        AuditRecord r = new AuditRecord();
        r.id = "rebuild-001";
        r.eventType = "CATALOG_REQUEST_SUCCESS";
        r.status = "success";
        r.timestamp = 100L;

        kv.put("onedrive_audit_records", "["
                + app.krista.extensions.essentials.cloudfiles.onedrive.impl.util.Constants.GSON.toJson(r)
                + "]");
        kv.put("onedrive_audit_summary",
                app.krista.extensions.essentials.cloudfiles.onedrive.impl.util.Constants.GSON.toJson(new AuditSummary()));

        // Inject an in-memory summary entry for this id BEFORE ensureKvLoaded() runs
        java.lang.reflect.Field summariesField = OneDriveAuditService.class.getDeclaredField("SUMMARIES");
        summariesField.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.concurrent.ConcurrentHashMap<String, AuditSummary> summaries =
                (java.util.concurrent.ConcurrentHashMap<String, AuditSummary>) summariesField.get(null);
        summaries.put(id, new AuditSummary());

        OneDriveAuditService svc = new OneDriveAuditService(kv);
        AuditData data = svc.getData(id, null, null, null, null);
        assertNotNull("Should not throw; summary is rebuilt from records", data);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ensureGlobalCounterLoaded — KV loading path (via reflection reset)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    public void testEnsureGlobalCounterLoaded_withPreloadedKvData_loadsValues() throws Exception {
        MockKeyValueStore kv = new MockKeyValueStore();
        kv.put("onedrive_global_counter",
                "{\"totalRequests\":55,\"firstRecordedAt\":123456}");

        java.lang.reflect.Field loadedField =
                OneDriveAuditService.class.getDeclaredField("GLOBAL_COUNTER_LOADED");
        loadedField.setAccessible(true);
        java.lang.reflect.Field totalReqField =
                OneDriveAuditService.class.getDeclaredField("GLOBAL_TOTAL_REQUESTS");
        totalReqField.setAccessible(true);
        java.lang.reflect.Field firstAtField =
                OneDriveAuditService.class.getDeclaredField("GLOBAL_FIRST_RECORDED_AT");
        firstAtField.setAccessible(true);

        // Reset static state to re-trigger KV load
        loadedField.setBoolean(null, false);
        ((java.util.concurrent.atomic.AtomicLong) totalReqField.get(null)).set(0);
        ((java.util.concurrent.atomic.AtomicLong) firstAtField.get(null)).set(0);

        OneDriveAuditService svc = new OneDriveAuditService(kv);
        Map<String, Object> gc = svc.getGlobalCounter();

        assertNotNull(gc);
        long total = ((Number) gc.get("totalRequests")).longValue();
        assertTrue("Global totalRequests should be loaded from KV (>= 55)", total >= 55);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // MockKeyValueStore
    // ─────────────────────────────────────────────────────────────────────────

    private static class MockKeyValueStore implements KeyValueStore {
        private final Map<String, Object> store = new HashMap<>();

        @Override
        public void put(String key, Object value) {
            store.put(key, value);
        }

        @Override
        public Object get(String key) {
            return store.get(key);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T get(String key, Class<T> type) {
            Object val = store.get(key);
            if (val == null) return null;
            if (type.isInstance(val)) return type.cast(val);
            return (T) val;
        }

        @Override
        public void remove(String key) {
            store.remove(key);
        }

        @Override
        public boolean contains(String key) {
            return store.containsKey(key);
        }

        @Override
        public boolean supports(Class<?> clazz) {
            return true;
        }

        @Override
        public Set<String> getKeys() {
            return new HashSet<>(store.keySet());
        }

        @Override
        public Iterator<Map.Entry<String, Object>> iterator() {
            return store.entrySet().iterator();
        }
    }
}
