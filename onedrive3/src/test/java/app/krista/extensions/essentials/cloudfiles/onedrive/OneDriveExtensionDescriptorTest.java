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

import app.krista.sdk.msauth.extension.registry.MsAuthExtensionDescriptor;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests for OneDriveExtensionDescriptor.
 */
public class OneDriveExtensionDescriptorTest {

    @Test
    public void testCanInstantiate() {
        OneDriveExtensionDescriptor descriptor = new OneDriveExtensionDescriptor();
        assertNotNull(descriptor);
    }

    @Test
    public void testImplementsMsAuthExtensionDescriptor() {
        OneDriveExtensionDescriptor descriptor = new OneDriveExtensionDescriptor();
        assertTrue(descriptor instanceof MsAuthExtensionDescriptor);
    }

    @Test
    public void testGetConfigClass_returnsOneDriveAuthConfig() {
        OneDriveExtensionDescriptor descriptor = new OneDriveExtensionDescriptor();
        assertEquals(OneDriveAuthConfig.class, descriptor.getConfigClass());
    }

    @Test
    public void testGetConfigClass_isNotNull() {
        OneDriveExtensionDescriptor descriptor = new OneDriveExtensionDescriptor();
        assertNotNull(descriptor.getConfigClass());
    }

    @Test
    public void testGetConfigClass_isSameOnMultipleCalls() {
        OneDriveExtensionDescriptor descriptor = new OneDriveExtensionDescriptor();
        assertSame(descriptor.getConfigClass(), descriptor.getConfigClass());
    }

    @Test
    public void testClass_isInCorrectPackage() {
        assertEquals(
            "app.krista.extensions.essentials.cloudfiles.onedrive",
            OneDriveExtensionDescriptor.class.getPackageName());
    }
}
