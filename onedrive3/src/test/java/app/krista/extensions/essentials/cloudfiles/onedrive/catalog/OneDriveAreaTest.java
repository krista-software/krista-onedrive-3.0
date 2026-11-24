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

package app.krista.extensions.essentials.cloudfiles.onedrive.catalog;

import app.krista.extension.executor.ExtensionResponse;
import org.junit.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link OneDriveArea} class structure.
 *
 * Covers:
 * - Class exists and has expected public methods
 * - "searchByPermissions" method signature
 * - "writeWithCustomFileName" method signature
 * - Constructor requires expected parameter types
 */
public class OneDriveAreaTest {

    // ================================================================
    // CLASS EXISTENCE AND STRUCTURE TESTS
    // ================================================================

    @Test
    public void testOneDriveArea_classExists() {
        assertNotNull("OneDriveArea class should exist", OneDriveArea.class);
    }

    @Test
    public void testOneDriveArea_hasExpectedPublicMethods() {
        List<String> methodNames = Arrays.stream(OneDriveArea.class.getDeclaredMethods())
                .filter(m -> java.lang.reflect.Modifier.isPublic(m.getModifiers()))
                .map(Method::getName)
                .collect(Collectors.toList());

        assertTrue("OneDriveArea should have searchByPermissions method",
                methodNames.contains("searchByPermissions"));
        assertTrue("OneDriveArea should have writeWithCustomFileName method",
                methodNames.contains("writeWithCustomFileName"));
    }

    // ================================================================
    // "searchByPermissions" METHOD TESTS
    // ================================================================

    @Test
    public void testSearchByPermissions_methodExists() {
        Method method = findMethod("searchByPermissions");
        assertNotNull("searchByPermissions method should exist", method);
    }

    @Test
    public void testSearchByPermissions_returnsString() {
        Method method = findMethod("searchByPermissions");
        assertNotNull("searchByPermissions method should exist", method);
        assertEquals("searchByPermissions should return String",
                String.class, method.getReturnType());
    }

    @Test
    public void testSearchByPermissions_acceptsStringParameter() {
        Method method = findMethod("searchByPermissions");
        assertNotNull("searchByPermissions method should exist", method);
        Class<?>[] paramTypes = method.getParameterTypes();
        assertEquals("searchByPermissions should accept 1 parameter", 1, paramTypes.length);
        assertEquals("searchByPermissions parameter should be String",
                String.class, paramTypes[0]);
    }

    // ================================================================
    // "writeWithCustomFileName" METHOD TESTS
    // ================================================================

    @Test
    public void testWriteWithCustomFileName_methodExists() {
        Method method = findMethod("writeWithCustomFileName");
        assertNotNull("writeWithCustomFileName method should exist", method);
    }

    @Test
    public void testWriteWithCustomFileName_returnsExtensionResponse() {
        Method method = findMethod("writeWithCustomFileName");
        assertNotNull("writeWithCustomFileName method should exist", method);
        assertEquals("writeWithCustomFileName should return ExtensionResponse",
                ExtensionResponse.class, method.getReturnType());
    }

    @Test
    public void testWriteWithCustomFileName_acceptsThreeParameters() {
        Method method = findMethod("writeWithCustomFileName");
        assertNotNull("writeWithCustomFileName method should exist", method);
        Class<?>[] paramTypes = method.getParameterTypes();
        assertEquals("writeWithCustomFileName should accept 3 parameters", 3, paramTypes.length);
        assertEquals("First parameter should be String (targetPath)",
                String.class, paramTypes[0]);
        assertEquals("Second parameter should be String (customName)",
                String.class, paramTypes[1]);
    }

    // ================================================================
    // CONSTRUCTOR TESTS
    // ================================================================

    @Test
    public void testConstructor_exists() {
        assertTrue("OneDriveArea should have at least one constructor",
                OneDriveArea.class.getConstructors().length > 0);
    }

    @Test
    public void testConstructor_parameterCount() {
        // The @Inject constructor should have 8 parameters
        boolean hasExpectedConstructor = Arrays.stream(OneDriveArea.class.getConstructors())
                .anyMatch(c -> c.getParameterCount() == 8);
        assertTrue("OneDriveArea should have a constructor with 8 parameters", hasExpectedConstructor);
    }

    // ================================================================
    // MULTIPLE METHODS INDEPENDENCE TEST
    // ================================================================

    @Test
    public void testPublicMethods_atLeastTwo() {
        long publicMethodCount = Arrays.stream(OneDriveArea.class.getDeclaredMethods())
                .filter(m -> java.lang.reflect.Modifier.isPublic(m.getModifiers()))
                .count();
        assertTrue("OneDriveArea should have at least 2 public methods (catalog requests)",
                publicMethodCount >= 2);
    }

    // ================================================================
    // HELPERS
    // ================================================================

    /**
     * Finds a declared method on OneDriveArea by name (first match).
     */
    private Method findMethod(String methodName) {
        return Arrays.stream(OneDriveArea.class.getDeclaredMethods())
                .filter(m -> methodName.equals(m.getName()))
                .findFirst()
                .orElse(null);
    }
}
