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

package app.krista.extensions.essentials.cloudfiles.onedrive.impl;

import org.junit.Test;
import org.jvnet.hk2.annotations.Service;

import java.lang.reflect.Method;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link FileEntitySearchImpl}.
 * Tests the private static helper method {@code isOnlyRootParentPath} via reflection,
 * and verifies annotation presence.
 */
public class FileEntitySearchImplTest {

    /**
     * Invokes the private static method isOnlyRootParentPath via reflection.
     */
    private boolean invokeIsOnlyRootParentPath(String path) throws Exception {
        Method method = FileEntitySearchImpl.class.getDeclaredMethod("isOnlyRootParentPath", String.class);
        method.setAccessible(true);
        return (boolean) method.invoke(null, path);
    }

    // ========================================
    // isOnlyRootParentPath tests
    // ========================================

    @Test
    public void testIsOnlyRootParentPath_driveRootSlash_returnsTrue() throws Exception {
        assertTrue("drive/root:/ should be recognized as root path",
                invokeIsOnlyRootParentPath("drive/root:/"));
    }

    @Test
    public void testIsOnlyRootParentPath_slashDriveRootSlash_returnsTrue() throws Exception {
        assertTrue("/drive/root:/ should be recognized as root path",
                invokeIsOnlyRootParentPath("/drive/root:/"));
    }

    @Test
    public void testIsOnlyRootParentPath_slashDriveRoot_returnsTrue() throws Exception {
        assertTrue("/drive/root: should be recognized as root path",
                invokeIsOnlyRootParentPath("/drive/root:"));
    }

    @Test
    public void testIsOnlyRootParentPath_someOtherPath_returnsFalse() throws Exception {
        assertFalse("/some/other/path should not be recognized as root path",
                invokeIsOnlyRootParentPath("/some/other/path"));
    }

    @Test
    public void testIsOnlyRootParentPath_documents_returnsFalse() throws Exception {
        assertFalse("/Documents should not be recognized as root path",
                invokeIsOnlyRootParentPath("/Documents"));
    }

    // ========================================
    // Annotation presence
    // ========================================

    @Test
    public void testClassIsAnnotatedWithService() {
        assertTrue("FileEntitySearchImpl should be annotated with @Service",
                FileEntitySearchImpl.class.isAnnotationPresent(Service.class));
    }
}
