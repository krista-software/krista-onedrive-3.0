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
import app.krista.extensions.util.EventHandler;
import app.krista.ksdk.accounts.Account;
import app.krista.ksdk.context.AuthorizationContext;
import app.krista.ksdk.context.RequestContext;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link ManagementArea#getDrives(String)}.
 *
 * Covers:
 * - Success path: DAO response is returned directly
 * - MustAuthorizeException is re-thrown unchanged
 * - Any other Exception produces a failure response (Success=false, Message prefix)
 * - invokeAsUser=true  → useEmail=false forwarded to DAO
 * - invokeAsUser=false → useEmail=true  forwarded to DAO
 * - accountID from AuthorizationContext forwarded to DAO
 * - null search → empty string forwarded to DAO (FileUtil.validateTextInput converts null to "")
 * - non-null search → forwarded as-is to DAO
 * - Failure response contains expected keys (Success, Message)
 * - Exception with null message still produces a well-formed response
 */
public class ManagementAreaGetDrivesTest {

    private static final String TEST_INVOKER_ID = "INV_test-get-drives";
    private static final String TEST_ACCOUNT_ID = "testAccount123";

    // ================================================================
    // SUCCESS PATH
    // ================================================================

    @Test
    public void testGetDrives_success_returnsDaoResponse() {
        ExtensionResponse expected = buildSuccessResponse();

        ManagementDaoImpl dao = new ManagementDaoImpl(null, null, null, null, null, null) {
            @Override
            public ExtensionResponse getDrives(String search, boolean useEmail, String accountID) {
                return expected;
            }
        };

        ManagementArea area = buildArea(dao, false, TEST_ACCOUNT_ID);
        ExtensionResponse actual = area.getDrives(null);

        assertSame("getDrives must return the response from DAO unchanged", expected, actual);
    }

    // ================================================================
    // MustAuthorizeException IS RE-THROWN
    // ================================================================

    @Test(expected = MustAuthorizeException.class)
    public void testGetDrives_mustAuthorizeException_isRethrown() {
        ManagementDaoImpl dao = new ManagementDaoImpl(null, null, null, null, null, null) {
            @Override
            public ExtensionResponse getDrives(String search, boolean useEmail, String accountID) {
                throw new MustAuthorizeException("Authorization required");
            }
        };

        ManagementArea area = buildArea(dao, false, TEST_ACCOUNT_ID);
        area.getDrives("search");
    }

    @Test
    public void testGetDrives_mustAuthorizeException_messagePreserved() {
        String errorMsg = "Please authenticate first";
        ManagementDaoImpl dao = new ManagementDaoImpl(null, null, null, null, null, null) {
            @Override
            public ExtensionResponse getDrives(String search, boolean useEmail, String accountID) {
                throw new MustAuthorizeException(errorMsg);
            }
        };

        ManagementArea area = buildArea(dao, false, TEST_ACCOUNT_ID);
        try {
            area.getDrives(null);
            fail("Expected MustAuthorizeException");
        } catch (MustAuthorizeException e) {
            assertEquals("Exception message must be preserved on re-throw", errorMsg, e.getMessage());
        }
    }

    // ================================================================
    // GENERIC EXCEPTION → FAILURE RESPONSE
    // ================================================================

    @Test
    public void testGetDrives_runtimeException_returnsFailureResponse() {
        String errorMsg = "Something went wrong";
        ManagementDaoImpl dao = new ManagementDaoImpl(null, null, null, null, null, null) {
            @Override
            public ExtensionResponse getDrives(String search, boolean useEmail, String accountID) {
                throw new RuntimeException(errorMsg);
            }
        };

        ManagementArea area = buildArea(dao, false, TEST_ACCOUNT_ID);
        ExtensionResponse response = area.getDrives(null);

        Map<String, Object> payload = response.getResponseValue();
        assertNotNull("Payload must not be null on exception", payload);
        assertEquals("Success must be false on exception", false, payload.get("Success"));
        assertEquals("Message must carry the error text",
                "Failed to retrieve drives: " + errorMsg, payload.get("Message"));
    }

    @Test
    public void testGetDrives_failureResponse_containsSuccessAndMessageKeys() {
        ManagementDaoImpl dao = new ManagementDaoImpl(null, null, null, null, null, null) {
            @Override
            public ExtensionResponse getDrives(String search, boolean useEmail, String accountID) {
                throw new RuntimeException("drive error");
            }
        };

        ManagementArea area = buildArea(dao, false, TEST_ACCOUNT_ID);
        ExtensionResponse response = area.getDrives(null);

        Map<String, Object> payload = response.getResponseValue();
        assertTrue("Payload must contain 'Success' key", payload.containsKey("Success"));
        assertTrue("Payload must contain 'Message' key", payload.containsKey("Message"));
    }

    @Test
    public void testGetDrives_exceptionWithNullMessage_responseMessageNotNull() {
        ManagementDaoImpl dao = new ManagementDaoImpl(null, null, null, null, null, null) {
            @Override
            public ExtensionResponse getDrives(String search, boolean useEmail, String accountID) {
                throw new RuntimeException((String) null);
            }
        };

        ManagementArea area = buildArea(dao, false, TEST_ACCOUNT_ID);
        ExtensionResponse response = area.getDrives(null);

        Map<String, Object> payload = response.getResponseValue();
        assertNotNull("Payload must be present even when exception message is null", payload);
        assertEquals("Success must be false", false, payload.get("Success"));
        assertNotNull("Message must not be null", payload.get("Message"));
    }

    // ================================================================
    // invokeAsUser → useEmail DERIVATION
    // ================================================================

    @Test
    public void testGetDrives_invokeAsUser_true_useEmailFalse() {
        boolean[] capturedUseEmail = {true};

        ManagementDaoImpl dao = new ManagementDaoImpl(null, null, null, null, null, null) {
            @Override
            public ExtensionResponse getDrives(String search, boolean useEmail, String accountID) {
                capturedUseEmail[0] = useEmail;
                return buildSuccessResponse();
            }
        };

        buildArea(dao, true /* invokeAsUser */, TEST_ACCOUNT_ID).getDrives(null);

        assertFalse("useEmail must be false when invokeAsUser=true", capturedUseEmail[0]);
    }

    @Test
    public void testGetDrives_invokeAsUser_false_useEmailTrue() {
        boolean[] capturedUseEmail = {false};

        ManagementDaoImpl dao = new ManagementDaoImpl(null, null, null, null, null, null) {
            @Override
            public ExtensionResponse getDrives(String search, boolean useEmail, String accountID) {
                capturedUseEmail[0] = useEmail;
                return buildSuccessResponse();
            }
        };

        buildArea(dao, false /* invokeAsUser */, TEST_ACCOUNT_ID).getDrives(null);

        assertTrue("useEmail must be true when invokeAsUser=false", capturedUseEmail[0]);
    }

    // ================================================================
    // ACCOUNT ID FORWARDING
    // ================================================================

    @Test
    public void testGetDrives_accountIdForwardedToDao() {
        String expectedAccountId = "specific-account-id";
        String[] capturedAccountId = {null};

        ManagementDaoImpl dao = new ManagementDaoImpl(null, null, null, null, null, null) {
            @Override
            public ExtensionResponse getDrives(String search, boolean useEmail, String accountID) {
                capturedAccountId[0] = accountID;
                return buildSuccessResponse();
            }
        };

        buildArea(dao, false, expectedAccountId).getDrives(null);

        assertEquals("Account ID from AuthorizationContext must be forwarded to DAO",
                expectedAccountId, capturedAccountId[0]);
    }

    // ================================================================
    // SEARCH PARAM HANDLING (FileUtil.validateTextInput: null → "")
    // ================================================================

    @Test
    public void testGetDrives_nullSearch_forwardedAsEmptyString() {
        String[] capturedSearch = {"SENTINEL"};

        ManagementDaoImpl dao = new ManagementDaoImpl(null, null, null, null, null, null) {
            @Override
            public ExtensionResponse getDrives(String search, boolean useEmail, String accountID) {
                capturedSearch[0] = search;
                return buildSuccessResponse();
            }
        };

        buildArea(dao, false, TEST_ACCOUNT_ID).getDrives(null);

        // FileUtil.validateTextInput(null) returns ""
        assertEquals("Null search must be converted to empty string by validateTextInput",
                "", capturedSearch[0]);
    }

    @Test
    public void testGetDrives_nonNullSearch_forwardedToDao() {
        String[] capturedSearch = {null};

        ManagementDaoImpl dao = new ManagementDaoImpl(null, null, null, null, null, null) {
            @Override
            public ExtensionResponse getDrives(String search, boolean useEmail, String accountID) {
                capturedSearch[0] = search;
                return buildSuccessResponse();
            }
        };

        buildArea(dao, false, TEST_ACCOUNT_ID).getDrives("Marketing");

        assertEquals("Non-null search must be forwarded as-is to DAO", "Marketing", capturedSearch[0]);
    }

    @Test
    public void testGetDrives_blankSearch_forwardedAsIs() {
        String[] capturedSearch = {null};

        ManagementDaoImpl dao = new ManagementDaoImpl(null, null, null, null, null, null) {
            @Override
            public ExtensionResponse getDrives(String search, boolean useEmail, String accountID) {
                capturedSearch[0] = search;
                return buildSuccessResponse();
            }
        };

        buildArea(dao, false, TEST_ACCOUNT_ID).getDrives("   ");

        // FileUtil.validateTextInput returns input as-is for non-null strings
        assertEquals("Blank search must be forwarded as-is to DAO", "   ", capturedSearch[0]);
    }

    // ================================================================
    // HELPERS
    // ================================================================

    /**
     * Builds a ManagementArea wired with the given DAO stub plus minimal no-op/proxy stubs
     * for every other constructor dependency.
     */
    private ManagementArea buildArea(ManagementDaoImpl dao, boolean invokeAsUser, String accountId) {
        return new ManagementArea(
                dao,
                createMockEventHandler(),
                createMockRequestContext(invokeAsUser),
                createMockAuthorizationContext(accountId),
                createNoOpAuditService(),
                createMockInvoker(),
                null  // OnedriveSetUpService — not called by getDrives
        );
    }

    private static ExtensionResponse buildSuccessResponse() {
        return new ExtensionResponseBuilder()
                .success(Map.of("Success", true, "Drives", Collections.emptyList(), "Message", "OK"), null, null)
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

    private RequestContext createMockRequestContext(boolean invokeAsUser) {
        return (RequestContext) Proxy.newProxyInstance(
                RequestContext.class.getClassLoader(),
                new Class<?>[]{RequestContext.class},
                (proxy, method, args) -> {
                    if ("invokeAsUser".equals(method.getName())) return invokeAsUser;
                    if (method.getReturnType() == boolean.class) return false;
                    if (method.getReturnType() == String.class) return "";
                    return null;
                });
    }

    private AuthorizationContext createMockAuthorizationContext(String accountId) {
        Account account = (Account) Proxy.newProxyInstance(
                Account.class.getClassLoader(),
                new Class<?>[]{Account.class},
                (proxy, method, args) -> {
                    if ("getAccountId".equals(method.getName())) return accountId;
                    if (method.getReturnType() == String.class) return "";
                    return null;
                });
        return (AuthorizationContext) Proxy.newProxyInstance(
                AuthorizationContext.class.getClassLoader(),
                new Class<?>[]{AuthorizationContext.class},
                (proxy, method, args) -> {
                    if ("getAuthorizedAccount".equals(method.getName())) return account;
                    if (method.getReturnType() == boolean.class) return false;
                    return null;
                });
    }

    private OneDriveAuditService createNoOpAuditService() {
        return new OneDriveAuditService(null) {
            @Override
            public void startRequest(String invokerId, String catalogName) { /* no-op */ }

            @Override
            public void record(String invokerId, AuditEventType eventType, String status,
                               String catalogName, String reason, String exceptionClass,
                               Map<String, String> metadata) { /* no-op */ }
        };
    }

    private EventHandler createMockEventHandler() {
        return (EventHandler) Proxy.newProxyInstance(
                EventHandler.class.getClassLoader(),
                new Class<?>[]{EventHandler.class},
                (proxy, method, args) -> null);
    }
}
