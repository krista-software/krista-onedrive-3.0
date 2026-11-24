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

import static org.junit.Assert.*;

public class AuditEventTypeTest {

    @Test
    public void testCatalogRequestEventsExist() {
        assertNotNull(AuditEventType.CATALOG_REQUEST_TRIGGERED);
        assertNotNull(AuditEventType.CATALOG_REQUEST_SUCCESS);
        assertNotNull(AuditEventType.CATALOG_REQUEST_FAILURE);
    }

    @Test
    public void testAuthEventsExist() {
        assertNotNull(AuditEventType.PASSWORD_CHANGE_DETECTED);
        assertNotNull(AuditEventType.TOKEN_REFRESHED);
        assertNotNull(AuditEventType.TOKEN_EXPIRY_DETECTED);
        assertNotNull(AuditEventType.INVALID_TOKEN_DETECTED);
        assertNotNull(AuditEventType.CLIENT_SECRET_EXPIRY_DETECTED);
        assertNotNull(AuditEventType.INVALID_CLIENT_SECRET_DETECTED);
        assertNotNull(AuditEventType.AUTH_CONFIG_FAILURE);
    }

    @Test
    public void testFileEventsExist() {
        assertNotNull(AuditEventType.FILE_UPLOADED);
        assertNotNull(AuditEventType.FILE_DOWNLOADED);
        assertNotNull(AuditEventType.FILE_DELETED);
        assertNotNull(AuditEventType.FILE_COPIED);
        assertNotNull(AuditEventType.FILE_MOVED);
        assertNotNull(AuditEventType.FOLDER_CREATED);
    }

    @Test
    public void testNotificationEventsExist() {
        assertNotNull(AuditEventType.FILE_NOTIFICATION_RECEIVED);
        assertNotNull(AuditEventType.FILE_NOTIFICATION_TRIGGERED);
        assertNotNull(AuditEventType.FILE_NOTIFICATION_DROPPED_DUPLICATE);
        assertNotNull(AuditEventType.FILE_NOTIFICATION_ERROR);
    }

    @Test
    public void testTotalValueCount_is20() {
        assertEquals(20, AuditEventType.values().length);
    }

    @Test
    public void testValueOf_catalogRequestTriggered() {
        assertEquals(AuditEventType.CATALOG_REQUEST_TRIGGERED,
                AuditEventType.valueOf("CATALOG_REQUEST_TRIGGERED"));
    }

    @Test
    public void testValueOf_fileUploaded() {
        assertEquals(AuditEventType.FILE_UPLOADED, AuditEventType.valueOf("FILE_UPLOADED"));
    }

    @Test
    public void testValueOf_fileNotificationError() {
        assertEquals(AuditEventType.FILE_NOTIFICATION_ERROR,
                AuditEventType.valueOf("FILE_NOTIFICATION_ERROR"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testValueOf_unknownValue_throwsIllegalArgument() {
        AuditEventType.valueOf("NOT_A_REAL_EVENT_TYPE");
    }

    @Test
    public void testName_matchesDeclaration() {
        assertEquals("CATALOG_REQUEST_TRIGGERED", AuditEventType.CATALOG_REQUEST_TRIGGERED.name());
        assertEquals("FILE_UPLOADED", AuditEventType.FILE_UPLOADED.name());
        assertEquals("TOKEN_EXPIRY_DETECTED", AuditEventType.TOKEN_EXPIRY_DETECTED.name());
        assertEquals("FILE_NOTIFICATION_ERROR", AuditEventType.FILE_NOTIFICATION_ERROR.name());
    }

    @Test
    public void testOrdinals_areDistinct() {
        AuditEventType[] values = AuditEventType.values();
        for (int i = 0; i < values.length; i++) {
            assertEquals(i, values[i].ordinal());
        }
    }

    @Test
    public void testEnumCanBeUsedInSwitch() {
        AuditEventType type = AuditEventType.FILE_UPLOADED;
        String result;
        switch (type) {
            case FILE_UPLOADED -> result = "upload";
            case FILE_DOWNLOADED -> result = "download";
            default -> result = "other";
        }
        assertEquals("upload", result);
    }
}
