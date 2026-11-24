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

package app.krista.extensions.essentials.cloudfiles.onedrive.api;

import app.krista.extensions.essentials.cloudfiles.onedrive.api.OnedriveApplication;
import app.krista.extensions.essentials.cloudfiles.onedrive.api.AuthenticationResource;
import app.krista.extensions.essentials.cloudfiles.onedrive.api.ListenerResource;
import app.krista.extensions.essentials.cloudfiles.onedrive.api.OneDriveAuditResource;
import app.krista.extensions.essentials.cloudfiles.onedrive.mcp.OnedriveMcpSetupResource;
import org.junit.Before;
import org.junit.Test;

import javax.ws.rs.ApplicationPath;
import org.jvnet.hk2.annotations.Service;

import java.util.Set;

import static org.junit.Assert.*;

/**
 * Unit tests for OnedriveApplication.
 * Validates JAX-RS resource registration and class-level annotations.
 */
public class OnedriveApplicationTest {

    private OnedriveApplication application;

    @Before
    public void setUp() {
        application = new OnedriveApplication();
    }

    // ========================================
    // GET CLASSES TESTS
    // ========================================

    @Test
    public void testGetClasses_returnsExactlyFourClasses() {
        Set<Class<?>> classes = application.getClasses();
        assertEquals("getClasses should return exactly 4 classes", 4, classes.size());
    }

    @Test
    public void testGetClasses_containsAuthenticationResource() {
        Set<Class<?>> classes = application.getClasses();
        assertTrue("Should contain AuthenticationResource",
                classes.contains(AuthenticationResource.class));
    }

    @Test
    public void testGetClasses_containsListenerResource() {
        Set<Class<?>> classes = application.getClasses();
        assertTrue("Should contain ListenerResource",
                classes.contains(ListenerResource.class));
    }

    @Test
    public void testGetClasses_containsOneDriveAuditResource() {
        Set<Class<?>> classes = application.getClasses();
        assertTrue("Should contain OneDriveAuditResource",
                classes.contains(OneDriveAuditResource.class));
    }

    @Test
    public void testGetClasses_containsOnedriveMcpSetupResource() {
        Set<Class<?>> classes = application.getClasses();
        assertTrue("Should contain OnedriveMcpSetupResource",
                classes.contains(OnedriveMcpSetupResource.class));
    }

    // ========================================
    // ANNOTATION TESTS
    // ========================================

    @Test
    public void testApplicationPath_annotationPresent() {
        ApplicationPath annotation = OnedriveApplication.class.getAnnotation(ApplicationPath.class);
        assertNotNull("@ApplicationPath annotation should be present", annotation);
    }

    @Test
    public void testApplicationPath_valueIsOnedrive() {
        ApplicationPath annotation = OnedriveApplication.class.getAnnotation(ApplicationPath.class);
        assertEquals("onedrive", annotation.value());
    }

    @Test
    public void testService_annotationPresent() {
        Service annotation = OnedriveApplication.class.getAnnotation(Service.class);
        assertNotNull("@Service annotation should be present", annotation);
    }
}
