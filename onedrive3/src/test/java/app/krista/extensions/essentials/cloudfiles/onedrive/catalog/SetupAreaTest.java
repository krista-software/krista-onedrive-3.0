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

import app.krista.extension.authorization.MustAuthorizeException;
import app.krista.extension.executor.ExtensionResponse;
import app.krista.extension.executor.ExtensionResponseBuilder;
import app.krista.extension.executor.Invoker;
import app.krista.extensions.essentials.cloudfiles.onedrive.OnedriveSetUpService;
import app.krista.extensions.essentials.cloudfiles.onedrive.audit.AuditEventType;
import app.krista.extensions.essentials.cloudfiles.onedrive.audit.OneDriveAuditService;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link SetupArea}.
 *
 * Covers:
 * - testConnection success, failure with RuntimeException, and MustAuthorizeException propagation
 * - healthCheck success, MustAuthorizeException propagation, and RuntimeException propagation
 * - Class structure verification (public methods exist)
 * - Audit service interaction tracking
 */
public class SetupAreaTest {

    private static final String TEST_INVOKER_ID = "test-invoker";

    private MockOnedriveSetUpService mockSetUpService;
    private MockOneDriveAuditService mockAuditService;
    private SetupArea setupArea;

    @Before
    public void setUp() {
        mockSetUpService = new MockOnedriveSetUpService();
        mockAuditService = new MockOneDriveAuditService();
        Invoker mockInvoker = createMockInvoker();
        setupArea = new SetupArea(mockSetUpService, mockAuditService, mockInvoker);
    }

    // ================================================================
    // testConnection TESTS
    // ================================================================

    @Test
    public void testConnection_success() {
        ExtensionResponse response = setupArea.testConnection();

        assertNotNull("Response should not be null", response);
        Map<String, Object> payload = response.getResponseValue();
        assertNotNull("Response payload should not be null", payload);
        assertEquals("Is Successful should be true", true, payload.get("Is Successful"));
        assertEquals("Message should be 'Connection Successful'", "Connection Successful", payload.get("Message"));
        assertEquals("testConnection should have been called once", 1, mockSetUpService.testConnectionCallCount);
        assertTrue("auditService.startRequest should have been called", mockAuditService.startRequestCalled);
        assertTrue("auditService.record should have been called", mockAuditService.recordCallCount > 0);
    }

    @Test
    public void testConnection_failure_exception() {
        mockSetUpService.testConnectionException = new RuntimeException("Connection timeout");

        ExtensionResponse response = setupArea.testConnection();

        assertNotNull("Response should not be null", response);
        Map<String, Object> payload = response.getResponseValue();
        assertNotNull("Response payload should not be null", payload);
        assertEquals("Is Successful should be false", false, payload.get("Is Successful"));
        String message = (String) payload.get("Message");
        assertNotNull("Message should not be null", message);
        assertTrue("Message should contain 'Connection timeout', but was: " + message,
                message.contains("Connection timeout"));
    }

    @Test(expected = MustAuthorizeException.class)
    public void testConnection_mustAuthorize() {
        mockSetUpService.testConnectionException = new MustAuthorizeException("auth required");
        setupArea.testConnection();
    }

    // ================================================================
    // healthCheck TESTS
    // ================================================================

    @Test
    public void healthCheck_success() {
        ExtensionResponse expected = new ExtensionResponseBuilder()
                .success(Map.of("Is Healthy", true), null, null)
                .build();
        mockSetUpService.checkHealthResponse = expected;

        ExtensionResponse response = setupArea.healthCheck();

        assertSame("healthCheck must return the response from SetUpService unchanged", expected, response);
        assertTrue("auditService.startRequest should have been called", mockAuditService.startRequestCalled);
        assertTrue("auditService.record should have been called for success", mockAuditService.recordCallCount > 0);
    }

    @Test(expected = MustAuthorizeException.class)
    public void healthCheck_mustAuthorize() {
        mockSetUpService.checkHealthException = new MustAuthorizeException("auth required");
        setupArea.healthCheck();
    }

    @Test(expected = RuntimeException.class)
    public void healthCheck_runtimeException() {
        mockSetUpService.checkHealthException = new RuntimeException("health check failed");
        setupArea.healthCheck();
    }

    // ================================================================
    // CLASS STRUCTURE TESTS
    // ================================================================

    @Test
    public void testConnectionMethod_exists() throws NoSuchMethodException {
        Method method = SetupArea.class.getMethod("testConnection");
        assertNotNull("testConnection method should exist", method);
        assertEquals("testConnection should return ExtensionResponse",
                ExtensionResponse.class, method.getReturnType());
    }

    @Test
    public void healthCheckMethod_exists() throws NoSuchMethodException {
        Method method = SetupArea.class.getMethod("healthCheck");
        assertNotNull("healthCheck method should exist", method);
        assertEquals("healthCheck should return ExtensionResponse",
                ExtensionResponse.class, method.getReturnType());
    }

    // ================================================================
    // AUDIT SERVICE TRACKING TESTS
    // ================================================================

    @Test
    public void testConnection_success_auditsCorrectly() {
        setupArea.testConnection();

        assertTrue("startRequest should have been called", mockAuditService.startRequestCalled);
        assertEquals("startRequest catalogName should be 'Test Connection'",
                "Test Connection", mockAuditService.lastStartRequestCatalogName);
        assertFalse("Recorded events should not be empty", mockAuditService.recordedEvents.isEmpty());
        // The success path records a SUCCESS event
        boolean hasSuccess = mockAuditService.recordedEvents.stream()
                .anyMatch(e -> e.eventType == AuditEventType.CATALOG_REQUEST_SUCCESS);
        assertTrue("A CATALOG_REQUEST_SUCCESS event should have been recorded", hasSuccess);
    }

    @Test
    public void testConnection_failure_auditsFailure() {
        mockSetUpService.testConnectionException = new RuntimeException("timeout");

        setupArea.testConnection();

        boolean hasFailure = mockAuditService.recordedEvents.stream()
                .anyMatch(e -> e.eventType == AuditEventType.CATALOG_REQUEST_FAILURE);
        assertTrue("A CATALOG_REQUEST_FAILURE event should have been recorded", hasFailure);
    }

    @Test
    public void healthCheck_success_auditsCorrectly() {
        ExtensionResponse expected = new ExtensionResponseBuilder()
                .success(Map.of("Is Healthy", true), null, null)
                .build();
        mockSetUpService.checkHealthResponse = expected;

        setupArea.healthCheck();

        assertTrue("startRequest should have been called", mockAuditService.startRequestCalled);
        assertEquals("startRequest catalogName should be 'Health Check'",
                "Health Check", mockAuditService.lastStartRequestCatalogName);
        boolean hasSuccess = mockAuditService.recordedEvents.stream()
                .anyMatch(e -> e.eventType == AuditEventType.CATALOG_REQUEST_SUCCESS);
        assertTrue("A CATALOG_REQUEST_SUCCESS event should have been recorded", hasSuccess);
    }

    @Test
    public void healthCheck_mustAuthorize_auditsFailure() {
        mockSetUpService.checkHealthException = new MustAuthorizeException("auth required");

        try {
            setupArea.healthCheck();
            fail("Expected MustAuthorizeException");
        } catch (MustAuthorizeException e) {
            // expected
        }

        boolean hasFailure = mockAuditService.recordedEvents.stream()
                .anyMatch(e -> e.eventType == AuditEventType.CATALOG_REQUEST_FAILURE);
        assertTrue("A CATALOG_REQUEST_FAILURE event should have been recorded for MustAuthorizeException", hasFailure);
    }

    // ================================================================
    // MOCK CLASSES
    // ================================================================

    /**
     * Minimal subclass of OnedriveSetUpService that overrides the methods used by SetupArea.
     * Avoids needing the complex constructor dependencies.
     */
    private static class MockOnedriveSetUpService extends OnedriveSetUpService {
        int testConnectionCallCount = 0;
        RuntimeException testConnectionException;
        ExtensionResponse checkHealthResponse;
        RuntimeException checkHealthException;

        MockOnedriveSetUpService() {
            super(null, null, null, null, null);
        }

        @Override
        public void testConnection(boolean fromStudio) {
            testConnectionCallCount++;
            if (testConnectionException != null) {
                throw testConnectionException;
            }
        }

        @Override
        public ExtensionResponse checkHealth() {
            if (checkHealthException != null) {
                throw checkHealthException;
            }
            if (checkHealthResponse != null) {
                return checkHealthResponse;
            }
            return new ExtensionResponseBuilder()
                    .success(Map.of("Is Healthy", true), null, null)
                    .build();
        }
    }

    /**
     * No-op audit service that tracks calls for verification.
     */
    private static class MockOneDriveAuditService extends OneDriveAuditService {
        boolean startRequestCalled = false;
        String lastStartRequestCatalogName;
        int recordCallCount = 0;
        final List<RecordedEvent> recordedEvents = new ArrayList<>();

        MockOneDriveAuditService() {
            super(null);
        }

        @Override
        public void startRequest(String invokerId, String catalogName) {
            startRequestCalled = true;
            lastStartRequestCatalogName = catalogName;
        }

        @Override
        public void record(String invokerId, AuditEventType eventType, String status,
                           String catalogName, String reason, String exceptionClass,
                           Map<String, String> metadata) {
            recordCallCount++;
            recordedEvents.add(new RecordedEvent(eventType, status, catalogName, reason));
        }

        static class RecordedEvent {
            final AuditEventType eventType;
            final String status;
            final String catalogName;
            final String reason;

            RecordedEvent(AuditEventType eventType, String status, String catalogName, String reason) {
                this.eventType = eventType;
                this.status = status;
                this.catalogName = catalogName;
                this.reason = reason;
            }
        }
    }

    private Invoker createMockInvoker() {
        return (Invoker) Proxy.newProxyInstance(
                Invoker.class.getClassLoader(),
                new Class<?>[]{Invoker.class},
                (proxy, method, args) -> {
                    if ("getInvokerId".equals(method.getName())) return TEST_INVOKER_ID;
                    if (method.getReturnType() == boolean.class) return false;
                    if (method.getReturnType() == Map.class) return Collections.emptyMap();
                    if (method.getReturnType() == List.class) return Collections.emptyList();
                    return null;
                });
    }
}
