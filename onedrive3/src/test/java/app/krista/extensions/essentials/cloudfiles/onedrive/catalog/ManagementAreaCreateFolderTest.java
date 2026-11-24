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
import app.krista.extensions.essentials.cloudfiles.onedrive.audit.AuditEventType;
import app.krista.extensions.essentials.cloudfiles.onedrive.audit.OneDriveAuditService;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.ManagementDaoImpl;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Unit tests for {@link ManagementArea#createFolder(String, String, Boolean)}.
 *
 * Covers:
 * - Success path: DAO response returned unchanged, CATALOG_REQUEST_SUCCESS recorded
 * - MustAuthorizeException re-thrown unchanged, CATALOG_REQUEST_FAILURE recorded
 * - Generic Exception → failure response ("Failed to create folder: ..."), CATALOG_REQUEST_FAILURE recorded
 * - Exception with null message → well-formed failure response
 * - folderName / path / allowRetry forwarded verbatim to the DAO
 * - auditService.startRequest invoked with the "Create Folder" catalog name
 */
public class ManagementAreaCreateFolderTest {

    private static final String TEST_INVOKER_ID = "INV_test-create-folder";

    // ================================================================
    // SUCCESS PATH
    // ================================================================

    @Test
    public void testCreateFolder_success_returnsDaoResponse() {
        ExtensionResponse expected = buildSuccessResponse();
        ManagementDaoImpl dao = new ManagementDaoImpl(null, null, null, null, null, null) {
            @Override
            public ExtensionResponse createFolder(String folderName, String path, Boolean allowRetry) {
                return expected;
            }
        };
        RecordingAuditService audit = new RecordingAuditService();

        ExtensionResponse actual = buildArea(dao, audit).createFolder("NewFolder", "drive/root:/P", true);

        assertSame("createFolder must return the DAO response unchanged", expected, actual);
        assertEquals("Create Folder", audit.startedCatalog);
        assertEquals(1, audit.events.size());
        assertEquals(AuditEventType.CATALOG_REQUEST_SUCCESS, audit.events.get(0).eventType);
        assertEquals("success", audit.events.get(0).status);
    }

    // ================================================================
    // MustAuthorizeException IS RE-THROWN
    // ================================================================

    @Test(expected = MustAuthorizeException.class)
    public void testCreateFolder_mustAuthorizeException_isRethrown() {
        ManagementDaoImpl dao = new ManagementDaoImpl(null, null, null, null, null, null) {
            @Override
            public ExtensionResponse createFolder(String folderName, String path, Boolean allowRetry) {
                throw new MustAuthorizeException("Authorization required");
            }
        };
        buildArea(dao, new RecordingAuditService()).createFolder("F", "P", false);
    }

    @Test
    public void testCreateFolder_mustAuthorizeException_messagePreservedAndFailureRecorded() {
        String errorMsg = "Please authenticate first";
        ManagementDaoImpl dao = new ManagementDaoImpl(null, null, null, null, null, null) {
            @Override
            public ExtensionResponse createFolder(String folderName, String path, Boolean allowRetry) {
                throw new MustAuthorizeException(errorMsg);
            }
        };
        RecordingAuditService audit = new RecordingAuditService();

        try {
            buildArea(dao, audit).createFolder("F", "P", true);
            fail("Expected MustAuthorizeException");
        } catch (MustAuthorizeException e) {
            assertEquals("Exception message must be preserved on re-throw", errorMsg, e.getMessage());
        }
        assertEquals(1, audit.events.size());
        assertEquals(AuditEventType.CATALOG_REQUEST_FAILURE, audit.events.get(0).eventType);
        assertEquals("failure", audit.events.get(0).status);
        assertEquals(errorMsg, audit.events.get(0).reason);
        assertEquals("MustAuthorizeException", audit.events.get(0).exceptionClass);
    }

    // ================================================================
    // GENERIC EXCEPTION → FAILURE RESPONSE
    // ================================================================

    @Test
    public void testCreateFolder_runtimeException_returnsFailureResponse() {
        String errorMsg = "Something went wrong";
        ManagementDaoImpl dao = new ManagementDaoImpl(null, null, null, null, null, null) {
            @Override
            public ExtensionResponse createFolder(String folderName, String path, Boolean allowRetry) {
                throw new RuntimeException(errorMsg);
            }
        };
        RecordingAuditService audit = new RecordingAuditService();

        ExtensionResponse response = buildArea(dao, audit).createFolder("F", "P", true);

        Map<String, Object> payload = response.getResponseValue();
        assertNotNull("Payload must not be null on exception", payload);
        assertEquals("Success must be false on exception", false, payload.get("Success"));
        assertEquals("Message must carry the error text",
                "Failed to create folder: " + errorMsg, payload.get("Message"));
        assertEquals(1, audit.events.size());
        assertEquals(AuditEventType.CATALOG_REQUEST_FAILURE, audit.events.get(0).eventType);
        assertEquals("RuntimeException", audit.events.get(0).exceptionClass);
    }

    @Test
    public void testCreateFolder_failureResponse_containsSuccessAndMessageKeys() {
        ManagementDaoImpl dao = new ManagementDaoImpl(null, null, null, null, null, null) {
            @Override
            public ExtensionResponse createFolder(String folderName, String path, Boolean allowRetry) {
                throw new RuntimeException("boom");
            }
        };

        Map<String, Object> payload = buildArea(dao, new RecordingAuditService())
                .createFolder("F", "P", true).getResponseValue();

        assertTrue("Payload must contain 'Success' key", payload.containsKey("Success"));
        assertTrue("Payload must contain 'Message' key", payload.containsKey("Message"));
    }

    @Test
    public void testCreateFolder_exceptionWithNullMessage_responseWellFormed() {
        ManagementDaoImpl dao = new ManagementDaoImpl(null, null, null, null, null, null) {
            @Override
            public ExtensionResponse createFolder(String folderName, String path, Boolean allowRetry) {
                throw new RuntimeException((String) null);
            }
        };

        Map<String, Object> payload = buildArea(dao, new RecordingAuditService())
                .createFolder("F", "P", true).getResponseValue();

        assertEquals(false, payload.get("Success"));
        assertEquals("Failed to create folder: null", payload.get("Message"));
    }

    // ================================================================
    // PARAMETER FORWARDING
    // ================================================================

    @Test
    public void testCreateFolder_parametersForwardedToDao() {
        String[] captured = new String[2];
        Boolean[] capturedRetry = {null};
        ManagementDaoImpl dao = new ManagementDaoImpl(null, null, null, null, null, null) {
            @Override
            public ExtensionResponse createFolder(String folderName, String path, Boolean allowRetry) {
                captured[0] = folderName;
                captured[1] = path;
                capturedRetry[0] = allowRetry;
                return buildSuccessResponse();
            }
        };

        buildArea(dao, new RecordingAuditService()).createFolder("MyFolder", "drive/root:/Docs", false);

        assertEquals("Folder name must be forwarded verbatim", "MyFolder", captured[0]);
        assertEquals("Path must be forwarded verbatim", "drive/root:/Docs", captured[1]);
        assertEquals("allowRetry must be forwarded verbatim", Boolean.FALSE, capturedRetry[0]);
    }

    @Test
    public void testCreateFolder_nullAllowRetry_forwardedAsNull() {
        Boolean[] capturedRetry = {Boolean.TRUE};
        ManagementDaoImpl dao = new ManagementDaoImpl(null, null, null, null, null, null) {
            @Override
            public ExtensionResponse createFolder(String folderName, String path, Boolean allowRetry) {
                capturedRetry[0] = allowRetry;
                return buildSuccessResponse();
            }
        };

        buildArea(dao, new RecordingAuditService()).createFolder("F", "P", null);

        assertNull("Null allowRetry must be forwarded as null", capturedRetry[0]);
    }

    // ================================================================
    // HELPERS
    // ================================================================

    /**
     * Builds a ManagementArea wired with the given DAO and audit stub. createFolder touches only
     * managementDao, auditService and invokerId, so the remaining dependencies are null.
     */
    private ManagementArea buildArea(ManagementDaoImpl dao, OneDriveAuditService audit) {
        return new ManagementArea(
                dao,
                null,   // EventHandler — unused by createFolder
                null,   // RequestContext — unused by createFolder
                null,   // AuthorizationContext — unused by createFolder
                audit,
                createMockInvoker(),
                null    // OnedriveSetUpService — unused by createFolder
        );
    }

    private static ExtensionResponse buildSuccessResponse() {
        return new ExtensionResponseBuilder()
                .success(Map.of("Success", true, "Message", "Folder created successfully"), null, null)
                .build();
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

    /** Captures startRequest and record invocations for assertions. */
    private static class RecordingAuditService extends OneDriveAuditService {
        String startedCatalog;
        final List<Event> events = new ArrayList<>();

        RecordingAuditService() {
            super(null);
        }

        @Override
        public void startRequest(String invokerId, String catalogName) {
            this.startedCatalog = catalogName;
        }

        @Override
        public void record(String invokerId, AuditEventType eventType, String status,
                           String catalogName, String reason, String exceptionClass,
                           Map<String, String> metadata) {
            events.add(new Event(eventType, status, reason, exceptionClass));
        }
    }

    private static class Event {
        final AuditEventType eventType;
        final String status;
        final String reason;
        final String exceptionClass;

        Event(AuditEventType eventType, String status, String reason, String exceptionClass) {
            this.eventType = eventType;
            this.status = status;
            this.reason = reason;
            this.exceptionClass = exceptionClass;
        }
    }
}
