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

package app.krista.extensions.essentials.cloudfiles.onedrive;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests for OneDriveAuthConfig.
 * Verifies the class can be instantiated and carries expected annotation metadata.
 */
public class OneDriveAuthConfigTest {

    @Test
    public void testCanInstantiate() {
        OneDriveAuthConfig config = new OneDriveAuthConfig();
        assertNotNull(config);
    }

    @Test
    public void testIsAnnotatedWithMsAuthExtension() {
        assertTrue(
            "OneDriveAuthConfig must be annotated with @MsAuthExtension",
            OneDriveAuthConfig.class.isAnnotationPresent(
                app.krista.sdk.msauth.extension.annotations.MsAuthExtension.class));
    }

    @Test
    public void testMsAuthExtension_extensionTypeIsOnedrive() {
        app.krista.sdk.msauth.extension.annotations.MsAuthExtension ann =
            OneDriveAuthConfig.class.getAnnotation(
                app.krista.sdk.msauth.extension.annotations.MsAuthExtension.class);
        assertNotNull(ann);
        assertEquals("onedrive", ann.extensionType());
    }

    @Test
    public void testMsAuthExtension_callbackPathPrefixIsOnedrive() {
        app.krista.sdk.msauth.extension.annotations.MsAuthExtension ann =
            OneDriveAuthConfig.class.getAnnotation(
                app.krista.sdk.msauth.extension.annotations.MsAuthExtension.class);
        assertNotNull(ann);
        assertEquals("/onedrive", ann.callbackPathPrefix());
    }

    @Test
    public void testMsAuthExtension_enableAuditDashboardIsTrue() {
        app.krista.sdk.msauth.extension.annotations.MsAuthExtension ann =
            OneDriveAuthConfig.class.getAnnotation(
                app.krista.sdk.msauth.extension.annotations.MsAuthExtension.class);
        assertNotNull(ann);
        assertTrue(ann.enableAuditDashboard());
    }

    @Test
    public void testMsAuthExtension_supportsOwnCredentialsIsTrue() {
        app.krista.sdk.msauth.extension.annotations.MsAuthExtension ann =
            OneDriveAuthConfig.class.getAnnotation(
                app.krista.sdk.msauth.extension.annotations.MsAuthExtension.class);
        assertNotNull(ann);
        assertTrue(ann.supportsOwnCredentials());
    }

    @Test
    public void testClass_isInCorrectPackage() {
        assertEquals(
            "app.krista.extensions.essentials.cloudfiles.onedrive",
            OneDriveAuthConfig.class.getPackageName());
    }
}
