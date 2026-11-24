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

package app.krista.extensions.essentials.cloudfiles.onedrive.impl.util;

import com.google.gson.Gson;
import org.junit.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link Constants}.
 * Verifies constant values and the private-constructor utility-class pattern.
 */
public class ConstantsTest {

    // ========================================
    // Private constructor verification
    // ========================================

    @Test
    public void testConstructorIsPrivate() throws NoSuchMethodException {
        Constructor<Constants> constructor = Constants.class.getDeclaredConstructor();
        assertTrue("Constants constructor should be private",
                Modifier.isPrivate(constructor.getModifiers()));
    }

    @Test
    public void testConstructorAccessibleViaReflection() throws Exception {
        Constructor<Constants> constructor = Constants.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        Constants instance = constructor.newInstance();
        assertNotNull("Should be able to create instance via reflection", instance);
    }

    // ========================================
    // Key constant values
    // ========================================

    @Test
    public void testClientId() {
        assertEquals("clientId", Constants.CLIENT_ID);
    }

    @Test
    public void testClientSecret() {
        assertEquals("clientSecret", Constants.CLIENT_SECRET);
    }

    @Test
    public void testTenantId() {
        assertEquals("tenantId", Constants.TENANT_ID);
    }

    @Test
    public void testEmail() {
        assertEquals("email", Constants.EMAIL);
    }

    @Test
    public void testAuthType() {
        assertEquals("authType", Constants.AUTH_TYPE);
    }

    @Test
    public void testPublic() {
        assertEquals("Public", Constants.PUBLIC);
    }

    @Test
    public void testPrivate() {
        assertEquals("Private", Constants.PRIVATE);
    }

    @Test
    public void testClientCredentialsCert() {
        assertEquals("ClientCredentialsCert", Constants.CLIENT_CREDENTIALS_CERT);
    }

    @Test
    public void testRequiredScope() {
        assertEquals("openid User.Read offline_access Files.ReadWrite.All", Constants.REQUIRED_SCOPE);
    }

    @Test
    public void testChangeType() {
        assertEquals("updated", Constants.CHANGE_TYPE);
    }

    @Test
    public void testComparisonOperators() {
        assertEquals("gte", Constants.GTE);
        assertEquals("lte", Constants.LTE);
        assertEquals("gt", Constants.GT);
        assertEquals("lt", Constants.LT);
        assertEquals("equals", Constants.EQUALS);
    }

    @Test
    public void testNoChange() {
        assertEquals("No Change", Constants.NO_CHANGE);
    }

    @Test
    public void testEvent() {
        assertEquals("Event", Constants.EVENT);
    }

    @Test
    public void testDriveRoot() {
        assertEquals("drive/root:/", Constants.DRIVE_ROOT);
    }

    @Test
    public void testLocalExtnUrl() {
        assertEquals("https://extension.local.eng.krista.app", Constants.LOCAL_EXTN_URL);
    }

    @Test
    public void testLocalExtnReplaceUrl() {
        assertEquals("http://localhost:8765", Constants.LOCAL_EXTN_REPLACE_URL);
    }

    @Test
    public void testDefaultCallbackPath() {
        assertEquals("/extension/api/rest/v3/oauth/callback", Constants.DEFAULT_CALLBACK_PATH);
    }

    @Test
    public void testRestOnedriveMailNotification() {
        assertEquals("/rest/onedrive/onedriveNotification", Constants.REST_ONEDRIVE_MAIL_NOTIFICATION);
    }

    @Test
    public void testRestOnedriveLifecycleNotification() {
        assertEquals("/rest/onedrive/lifecycleNotification", Constants.REST_ONEDRIVE_LIFECYCLE_NOTIFICATION);
    }

    // ========================================
    // GSON field
    // ========================================

    @Test
    public void testGsonIsNotNull() {
        assertNotNull("GSON field should not be null", Constants.GSON);
    }

    @Test
    public void testGsonIsGsonInstance() {
        assertTrue("GSON field should be a Gson instance", Constants.GSON instanceof Gson);
    }
}
