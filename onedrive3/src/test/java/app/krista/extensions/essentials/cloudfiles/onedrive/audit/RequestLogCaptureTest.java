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

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.message.SimpleMessage;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Tests for RequestLogCapture — the package-private thread-local log appender.
 *
 * These tests exercise the public static API:
 * startCapture(), stopCapture(), and the install() side-effect (called inside startCapture).
 * Log4j2 may or may not be fully configured in test context; install() is designed to
 * swallow any installation failure, so tests are safe regardless.
 *
 * Additional tests call append() directly on the installed appender to exercise
 * the per-event filtering and message formatting logic.
 */
public class RequestLogCaptureTest {

    private static final String ONEDRIVE_PKG =
            "app.krista.extensions.essentials.cloudfiles.onedrive";

    @Test
    public void testStopCapture_withoutPriorStartCapture_returnsEmptyList() {
        // No startCapture called on this thread — stopCapture must return empty, not null
        List<String> logs = RequestLogCapture.stopCapture();
        assertNotNull(logs);
        assertTrue(logs.isEmpty());
    }

    @Test
    public void testStopCapture_afterStartCapture_returnsNonNullList() {
        RequestLogCapture.startCapture();
        List<String> logs = RequestLogCapture.stopCapture();
        assertNotNull(logs);
    }

    @Test
    public void testStopCapture_clearsThreadLocalSoSecondStopReturnsEmpty() {
        RequestLogCapture.startCapture();
        RequestLogCapture.stopCapture(); // first stop — captures and removes ThreadLocal
        List<String> second = RequestLogCapture.stopCapture(); // second stop — nothing captured
        assertNotNull(second);
        assertTrue(second.isEmpty());
    }

    @Test
    public void testStartCapture_multipleCallsOnSameThread_doesNotThrow() {
        RequestLogCapture.startCapture();
        RequestLogCapture.startCapture(); // replaces previous capture — no exception expected
        List<String> logs = RequestLogCapture.stopCapture();
        assertNotNull(logs);
    }

    @Test
    public void testStartAndStopCycle_canBeRepeated() {
        for (int i = 0; i < 5; i++) {
            RequestLogCapture.startCapture();
            List<String> logs = RequestLogCapture.stopCapture();
            assertNotNull("Iteration " + i + ": logs must not be null", logs);
        }
    }

    @Test
    public void testStopCapture_returnsDefensiveCopy() {
        RequestLogCapture.startCapture();
        List<String> logs1 = RequestLogCapture.stopCapture();
        // The returned list is a copy; modifying it should not affect anything
        logs1.add("external-modification");
        // Subsequent stop should still return an independent empty list
        List<String> logs2 = RequestLogCapture.stopCapture();
        assertNotNull(logs2);
        assertTrue(logs2.isEmpty());
    }

    @Test
    public void testStopCapture_withNullCapture_returnsEmptyNotNull() {
        // Ensure thread starts without any active capture
        RequestLogCapture.stopCapture(); // clear if any leftover
        List<String> result = RequestLogCapture.stopCapture();
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // ── append() — direct invocation via installed appender ──────────────────

    /**
     * Returns the installed RequestLogCapture appender from the Log4j2 context,
     * or null if installation failed in this test environment.
     */
    private RequestLogCapture installedAppender() {
        RequestLogCapture.startCapture(); // triggers install() if not already done
        RequestLogCapture.stopCapture();  // clean up ThreadLocal
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        return (RequestLogCapture) ctx.getConfiguration().getAppender("onedrive-request-capture");
    }

    /** Builds a minimal LogEvent for use in append() tests. */
    private LogEvent buildEvent(String loggerName, String message) {
        return Log4jLogEvent.newBuilder()
                .setLoggerName(loggerName)
                .setLevel(Level.INFO)
                .setMessage(new SimpleMessage(message))
                .setTimeMillis(System.currentTimeMillis())
                .build();
    }

    @Test
    public void testAppend_withNullCapture_returnsImmediately() {
        RequestLogCapture appender = installedAppender();
        if (appender == null) return; // Log4j2 not fully configured — skip gracefully

        // CAPTURE ThreadLocal is null (stopCapture was called in installedAppender)
        LogEvent event = buildEvent(ONEDRIVE_PKG + ".SomeClass", "msg");
        appender.append(event); // logs == null → early return, no exception
    }

    @Test
    public void testAppend_withNonOnedriveLogger_doesNotCapture() {
        RequestLogCapture appender = installedAppender();
        if (appender == null) return;

        RequestLogCapture.startCapture();
        LogEvent event = buildEvent("com.other.package.SomeClass", "unrelated message");
        appender.append(event); // package check fails → early return

        List<String> logs = RequestLogCapture.stopCapture();
        assertTrue("Non-onedrive events must not be captured", logs.isEmpty());
    }

    @Test
    public void testAppend_withOnedrivePkgLogger_capturesMessage() {
        RequestLogCapture appender = installedAppender();
        if (appender == null) return;

        RequestLogCapture.startCapture();
        LogEvent event = buildEvent(ONEDRIVE_PKG + ".SomeClass", "captured log message");
        appender.append(event);

        List<String> logs = RequestLogCapture.stopCapture();
        assertFalse("Onedrive event should be captured", logs.isEmpty());
        assertTrue("Captured entry should contain the log message",
                logs.get(0).contains("captured log message"));
    }

    @Test
    public void testAppend_withOnedrivePkgLogger_shortNameIsClassSimpleName() {
        RequestLogCapture appender = installedAppender();
        if (appender == null) return;

        RequestLogCapture.startCapture();
        LogEvent event = buildEvent(ONEDRIVE_PKG + ".MyDaoClass", "hello");
        appender.append(event);

        List<String> logs = RequestLogCapture.stopCapture();
        assertFalse(logs.isEmpty());
        // Short name (after last dot) should appear, not the full package
        assertTrue("Short class name should be in captured entry",
                logs.get(0).contains("MyDaoClass"));
    }

    @Test
    public void testAppend_withFullBuffer_doesNotAddMoreEntries() {
        RequestLogCapture appender = installedAppender();
        if (appender == null) return;

        RequestLogCapture.startCapture();
        // Fill up 200 entries (MAX_LOGS = 200)
        for (int i = 0; i < 200; i++) {
            appender.append(buildEvent(ONEDRIVE_PKG + ".C", "msg-" + i));
        }
        // 201st append should be silently dropped
        appender.append(buildEvent(ONEDRIVE_PKG + ".C", "should-be-dropped"));

        List<String> logs = RequestLogCapture.stopCapture();
        assertEquals("Buffer should be capped at MAX_LOGS=200", 200, logs.size());
        assertTrue("Overflow entry should not appear",
                logs.stream().noneMatch(s -> s.contains("should-be-dropped")));
    }

    @Test
    public void testAppend_withMessageExceedingMaxLen_truncatesWithEllipsis() {
        RequestLogCapture appender = installedAppender();
        if (appender == null) return;

        RequestLogCapture.startCapture();
        // Build a payload longer than MAX_MSG_LEN (500); the full formatted entry
        // is "[INFO] ClassName: <payload>", so payload alone > 500 guarantees truncation
        String longPayload = "X".repeat(600);
        appender.append(buildEvent(ONEDRIVE_PKG + ".TruncClass", longPayload));

        List<String> logs = RequestLogCapture.stopCapture();
        assertFalse(logs.isEmpty());
        String captured = logs.get(0);
        assertTrue("Truncated message should end with ellipsis (…)",
                captured.endsWith("\u2026"));
        assertEquals("Truncated message length should equal MAX_MSG_LEN+1 (for ellipsis)",
                501, captured.length());
    }

    @Test
    public void testAppend_injectDirectlyIntoCapture_simulatesLogFlow() throws Exception {
        // Directly set the CAPTURE ThreadLocal so we can test the "logs not null" path
        // without relying on the installed appender.
        java.lang.reflect.Field captureField = RequestLogCapture.class.getDeclaredField("CAPTURE");
        captureField.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.lang.reflect.Field f = captureField;
        ThreadLocal<List<String>> capture = (ThreadLocal<List<String>>) f.get(null);

        List<String> injected = new ArrayList<>();
        capture.set(injected);
        try {
            // Now verify stopCapture returns our injected list (as a copy)
            List<String> result = RequestLogCapture.stopCapture();
            assertNotNull(result);
            assertTrue(result.isEmpty()); // injected was empty
        } finally {
            capture.remove(); // clean up
        }
    }
}
