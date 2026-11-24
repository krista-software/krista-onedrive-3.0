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

import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class AuditRecordTest {

    @Test
    public void testDefaultFields_areNullOrZero() {
        AuditRecord record = new AuditRecord();

        assertNull(record.id);
        assertEquals(0L, record.timestamp);
        assertNull(record.eventType);
        assertNull(record.status);
        assertNull(record.catalogName);
        assertNull(record.reason);
        assertNull(record.exceptionClass);
        assertNull(record.metadata);
        assertNull(record.executionLogs);
        assertNull(record.triggeredBy);
        assertEquals(0L, record.durationMs);
    }

    @Test
    public void testId_canBeSetAndRead() {
        AuditRecord record = new AuditRecord();
        record.id = "abc123def456";
        assertEquals("abc123def456", record.id);
    }

    @Test
    public void testTimestamp_canBeSetAndRead() {
        AuditRecord record = new AuditRecord();
        record.timestamp = 1718000000000L;
        assertEquals(1718000000000L, record.timestamp);
    }

    @Test
    public void testEventType_canBeSetAndRead() {
        AuditRecord record = new AuditRecord();
        record.eventType = "CATALOG_REQUEST_SUCCESS";
        assertEquals("CATALOG_REQUEST_SUCCESS", record.eventType);
    }

    @Test
    public void testStatus_canBeSetAndRead() {
        AuditRecord record = new AuditRecord();
        record.status = "success";
        assertEquals("success", record.status);
    }

    @Test
    public void testCatalogName_canBeSetAndRead() {
        AuditRecord record = new AuditRecord();
        record.catalogName = "Upload File";
        assertEquals("Upload File", record.catalogName);
    }

    @Test
    public void testReason_canBeSetAndRead() {
        AuditRecord record = new AuditRecord();
        record.reason = "File not found at the specified path";
        assertEquals("File not found at the specified path", record.reason);
    }

    @Test
    public void testExceptionClass_canBeSetAndRead() {
        AuditRecord record = new AuditRecord();
        record.exceptionClass = "java.io.IOException";
        assertEquals("java.io.IOException", record.exceptionClass);
    }

    @Test
    public void testMetadata_canBeSetAndRead() {
        AuditRecord record = new AuditRecord();
        Map<String, String> metadata = new HashMap<>();
        metadata.put("filePath", "/Documents/test.txt");
        metadata.put("fileSize", "1024");
        record.metadata = metadata;

        assertNotNull(record.metadata);
        assertEquals("1024", record.metadata.get("fileSize"));
        assertEquals("/Documents/test.txt", record.metadata.get("filePath"));
    }

    @Test
    public void testExecutionLogs_canBeSetAndRead() {
        AuditRecord record = new AuditRecord();
        List<String> logs = Arrays.asList("[INFO] Starting upload", "[DEBUG] Connecting to OneDrive");
        record.executionLogs = logs;

        assertNotNull(record.executionLogs);
        assertEquals(2, record.executionLogs.size());
        assertEquals("[INFO] Starting upload", record.executionLogs.get(0));
    }

    @Test
    public void testTriggeredBy_canBeSetAndRead() {
        AuditRecord record = new AuditRecord();
        record.triggeredBy = "user@example.com";
        assertEquals("user@example.com", record.triggeredBy);
    }

    @Test
    public void testDurationMs_canBeSetAndRead() {
        AuditRecord record = new AuditRecord();
        record.durationMs = 342L;
        assertEquals(342L, record.durationMs);
    }

    @Test
    public void testAllFields_canBeSetTogether() {
        AuditRecord record = new AuditRecord();
        record.id = "id001";
        record.timestamp = 1000L;
        record.eventType = "CATALOG_REQUEST_FAILURE";
        record.status = "failure";
        record.catalogName = "Delete File";
        record.reason = "Permission denied";
        record.exceptionClass = "SecurityException";
        record.triggeredBy = "admin@company.com";
        record.durationMs = 125L;

        Map<String, String> metadata = new HashMap<>();
        metadata.put("path", "/shared/doc.pdf");
        record.metadata = metadata;

        List<String> logs = Arrays.asList("[ERROR] Access denied");
        record.executionLogs = logs;

        assertEquals("id001", record.id);
        assertEquals(1000L, record.timestamp);
        assertEquals("CATALOG_REQUEST_FAILURE", record.eventType);
        assertEquals("failure", record.status);
        assertEquals("Delete File", record.catalogName);
        assertEquals("Permission denied", record.reason);
        assertEquals("SecurityException", record.exceptionClass);
        assertEquals("admin@company.com", record.triggeredBy);
        assertEquals(125L, record.durationMs);
        assertEquals("/shared/doc.pdf", record.metadata.get("path"));
        assertEquals(1, record.executionLogs.size());
    }

    @Test
    public void testFields_canBeOverwritten() {
        AuditRecord record = new AuditRecord();
        record.status = "triggered";
        record.status = "success";
        assertEquals("success", record.status);
    }

    @Test
    public void testTimestamp_zeroIsValid() {
        AuditRecord record = new AuditRecord();
        record.timestamp = 0L;
        assertEquals(0L, record.timestamp);
    }

    @Test
    public void testDurationMs_zeroIsValid() {
        AuditRecord record = new AuditRecord();
        record.durationMs = 0L;
        assertEquals(0L, record.durationMs);
    }
}
