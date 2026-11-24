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
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public class AuditDataTest {

    @Test
    public void testConstructor_setsFieldsCorrectly() {
        AuditSummary summary = new AuditSummary();
        AuditRecord record = new AuditRecord();
        List<AuditRecord> records = Collections.singletonList(record);

        AuditData data = new AuditData(summary, records);

        assertSame(summary, data.summary);
        assertSame(records, data.records);
    }

    @Test
    public void testConstructor_withNullSummary() {
        List<AuditRecord> records = Collections.emptyList();
        AuditData data = new AuditData(null, records);

        assertNull(data.summary);
        assertNotNull(data.records);
    }

    @Test
    public void testConstructor_withNullRecords() {
        AuditSummary summary = new AuditSummary();
        AuditData data = new AuditData(summary, null);

        assertNotNull(data.summary);
        assertNull(data.records);
    }

    @Test
    public void testConstructor_withBothNull() {
        AuditData data = new AuditData(null, null);

        assertNull(data.summary);
        assertNull(data.records);
    }

    @Test
    public void testConstructor_withEmptyList() {
        AuditSummary summary = new AuditSummary();
        List<AuditRecord> records = Collections.emptyList();

        AuditData data = new AuditData(summary, records);

        assertNotNull(data.summary);
        assertNotNull(data.records);
        assertTrue(data.records.isEmpty());
    }

    @Test
    public void testConstructor_withMultipleRecords() {
        AuditSummary summary = new AuditSummary();
        AuditRecord r1 = new AuditRecord();
        r1.eventType = "CATALOG_REQUEST_SUCCESS";
        AuditRecord r2 = new AuditRecord();
        r2.eventType = "CATALOG_REQUEST_FAILURE";
        List<AuditRecord> records = Arrays.asList(r1, r2);

        AuditData data = new AuditData(summary, records);

        assertEquals(2, data.records.size());
        assertEquals("CATALOG_REQUEST_SUCCESS", data.records.get(0).eventType);
        assertEquals("CATALOG_REQUEST_FAILURE", data.records.get(1).eventType);
    }

    @Test
    public void testConstructor_summaryFieldsAreAccessible() {
        AuditSummary summary = new AuditSummary();
        summary.successCount = 5;
        summary.failureCount = 2;

        AuditData data = new AuditData(summary, Collections.emptyList());

        assertEquals(5, data.summary.successCount);
        assertEquals(2, data.summary.failureCount);
    }
}
