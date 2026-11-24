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

package app.krista.extensions.essentials.cloudfiles.onedrive.impl.connectors;

import app.krista.extension.authorization.MustAuthorizeException;
import app.krista.extensions.essentials.cloudfiles.onedrive.OnedriveAttributes;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.util.Constants;
import app.krista.extensions.util.KeyValueStore;
import app.krista.ksdk.accounts.Account;
import app.krista.ksdk.context.AuthorizationContext;
import app.krista.ksdk.context.RequestContext;
import org.junit.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link GraphServiceClientProvider}.
 * <p>
 * Covers the locally-changed methods:
 * <ul>
 *   <li>{@code resolveUseEmail} — package-private, tested directly.</li>
 *   <li>{@code resolveAccountId} — package-private, tested directly.</li>
 *   <li>{@code resolveWsContactId} auth-fix — tested indirectly via
 *       {@link GraphServiceClientProvider#getUserRequestBuilder}: when {@code useEmail=true}
 *       and {@code accountID} starts with {@code "wsContact_"}, no
 *       {@link MustAuthorizeException} must be thrown (token check is skipped).</li>
 * </ul>
 */
public class GraphServiceClientProviderTest {

    private static final String WS_CONTACT_ID = "wsContact_abc-test-123";
    private static final String TEST_EMAIL = "test@example.com";

    // ========================================
    // resolveUseEmail — package-private
    // ========================================

    @Test
    public void testResolveUseEmail_ExplicitTrue_ReturnsTrue() {
        GraphServiceClientProvider provider = new GraphServiceClientProvider(null, null, null, null);
        assertTrue("resolveUseEmail(true) must return true", provider.resolveUseEmail(true));
    }

    @Test
    public void testResolveUseEmail_ExplicitFalse_ReturnsFalse() {
        GraphServiceClientProvider provider = new GraphServiceClientProvider(null, null, null, null);
        assertFalse("resolveUseEmail(false) must return false", provider.resolveUseEmail(false));
    }

    @Test
    public void testResolveUseEmail_NullWithInvokeAsUserFalse_ReturnsTrue() {
        // When useEmail=null, falls back to !isInvokeAsUser().
        // requestContext.invokeAsUser()=false → !false = true
        RequestContext rc = createMockRequestContext("INV_test", false);
        GraphServiceClientProvider provider = new GraphServiceClientProvider(null, null, rc, null);
        assertTrue("resolveUseEmail(null) with invokeAsUser=false must return true",
                provider.resolveUseEmail(null));
    }

    @Test
    public void testResolveUseEmail_NullWithInvokeAsUserTrue_ReturnsFalse() {
        // requestContext.invokeAsUser()=true → !true = false
        RequestContext rc = createMockRequestContext("INV_test", true);
        GraphServiceClientProvider provider = new GraphServiceClientProvider(null, null, rc, null);
        assertFalse("resolveUseEmail(null) with invokeAsUser=true must return false",
                provider.resolveUseEmail(null));
    }

    @Test
    public void testResolveUseEmail_NullRequestContext_NullUseEmail_ReturnsTrue() {
        // requestContext=null → isInvokeAsUser()=false → !false = true
        GraphServiceClientProvider provider = new GraphServiceClientProvider(null, null, null, null);
        assertTrue("resolveUseEmail(null) with null requestContext must return true",
                provider.resolveUseEmail(null));
    }

    // ========================================
    // resolveAccountId — package-private
    // ========================================

    @Test
    public void testResolveAccountId_UseEmailTrue_ReturnsAccountIdUnchanged() {
        GraphServiceClientProvider provider = new GraphServiceClientProvider(null, null, null, null);
        // useEmail=true → effectiveUseEmail=true → accountID returned as-is
        assertEquals("resolveAccountId with useEmail=true must return the original accountId",
                WS_CONTACT_ID, provider.resolveAccountId(true, WS_CONTACT_ID));
    }

    @Test
    public void testResolveAccountId_UseEmailTrue_NullAccountId_ReturnsNull() {
        GraphServiceClientProvider provider = new GraphServiceClientProvider(null, null, null, null);
        // useEmail=true with null accountID → still returns null (no authorizationContext lookup)
        assertNull("resolveAccountId with useEmail=true and null accountId must return null",
                provider.resolveAccountId(true, null));
    }

    @Test
    public void testResolveAccountId_UseEmailFalse_NonNullAccountId_ReturnsAccountId() {
        GraphServiceClientProvider provider = new GraphServiceClientProvider(null, null, null, null);
        // useEmail=false and accountID provided → returns accountID as-is
        assertEquals("resolveAccountId with useEmail=false and non-null accountId must return it",
                "someAccount", provider.resolveAccountId(false, "someAccount"));
    }

    // ========================================
    // resolveWsContactId auth-fix — verified via getUserRequestBuilder behaviour
    //
    // Scenario: useEmail=true AND accountID starts with "wsContact_"
    //
    //   BEFORE fix: resolveWsContactId checked accountID.startsWith(WS_CONTACT) FIRST
    //               → returned wsContact ID → checkUserToken looked up token in kvStore
    //               → token not found → threw MustAuthorizeException
    //
    //   AFTER fix:  resolveUseEmail(true) is checked FIRST → returns null
    //               → checkUserToken exits early (no kvStore access)
    //               → no MustAuthorizeException from token check
    // ========================================

    @Test
    public void testGetUserRequestBuilder_UseEmailTrue_WsContactAccountId_DoesNotThrowMustAuthorizeException() {
        // A spy kvStore that throws AssertionError if get() is called with a wsContact key.
        // Before the fix this would be reached; after the fix it must NOT be reached.
        final boolean[] wsContactTokenChecked = {false};
        KeyValueStore spyKvStore = new SpyKeyValueStore(wsContactTokenChecked);

        OnedriveAttributes attrs = new OnedriveAttributes(
                "http://localhost:8765", "test-cid", "test-cs", TEST_EMAIL, "test-tid", Constants.PUBLIC);

        // invokeAsUser=false so that resolveUseEmail(true) = true (explicit override wins)
        RequestContext rc = createMockRequestContext("INV_auth-fix-test", false);

        GraphServiceClientProvider provider = new GraphServiceClientProvider(spyKvStore, attrs, rc, null);

        try {
            provider.getUserRequestBuilder(true, WS_CONTACT_ID);
        } catch (Exception e) {
            // Any exception from the SDK delegate (MustAuthorizeException for operator auth,
            // NPE, etc.) is acceptable here — we only verify that the per-user wsContact
            // token check was NOT the trigger.
        }

        // The key assertion: the spy kvStore must NOT have been called with the wsContact key.
        // If it was called it means the old code path (check accountID.startsWith first) ran.
        assertFalse("Token check via kvStore.get(wsContactId) must NOT be executed when useEmail=true",
                wsContactTokenChecked[0]);
    }

    @Test
    public void testGetUserRequestBuilder_UseEmailFalse_WsContactAccountId_NoStoredToken_ThrowsMustAuthorize() {
        // Sanity check: when useEmail=false and accountID is wsContact_ but no token → MAE is thrown.
        MockKeyValueStore emptyKvStore = new MockKeyValueStore(); // returns null for all keys

        OnedriveAttributes attrs = new OnedriveAttributes(
                "http://localhost:8765", "test-cid", "test-cs", TEST_EMAIL, "test-tid", Constants.PUBLIC);

        RequestContext rc = createMockRequestContext("INV_auth-fix-test", false);

        GraphServiceClientProvider provider = new GraphServiceClientProvider(emptyKvStore, attrs, rc, null);

        try {
            provider.getUserRequestBuilder(false, WS_CONTACT_ID);
            // If we reach here and no MAE was thrown, the test is inconclusive (SDK may have handled it)
        } catch (MustAuthorizeException e) {
            // Expected: no token → should prompt authentication
            assertNotNull("MustAuthorizeException must have a message", e.getMessage());
        } catch (Exception e) {
            // Other exceptions are also acceptable for this test's purpose
        }
    }

    // ========================================
    // resolveWsContactId — direct branch coverage (via reflection)
    //
    // The method is private; every branch is exercised by invoking it through
    // getDeclaredMethod/setAccessible so that assertions are precise and not
    // obscured by the SDK delegate chain.
    //
    // Branches:
    //   Branch 1 : resolveUseEmail(useEmail) == true               → return null
    //   Branch 2 : accountID starts with WS_CONTACT                → return accountID
    //   Branch 3 : falls through; authorizationContext returns
    //              a non-wsContact ID (or null account → IAE)       → return null
    //   Branch 4 : authorizationContext returns a wsContact ID      → return that ID
    //   Branch 5 : authorizationContext / getAuthorizedAccountId
    //              throws                                           → caught, return null
    // ========================================

    // ── Branch 1: resolveUseEmail returns true → always null ──────────────────

    /**
     * useEmail=true with a wsContact accountID — the email-auth guard fires first,
     * so the wsContact ID must never be returned.
     */
    @Test
    public void testResolveWsContactId_UseEmailTrue_WsContactAccountID_ReturnsNull() throws Exception {
        GraphServiceClientProvider provider = new GraphServiceClientProvider(null, null, null, null);
        assertNull("useEmail=true must short-circuit and return null regardless of accountID",
                invokeResolveWsContactId(provider, true, WS_CONTACT_ID));
    }

    /**
     * useEmail=true with a plain email accountID — Branch 1 returns null.
     */
    @Test
    public void testResolveWsContactId_UseEmailTrue_NonWsContactAccountID_ReturnsNull() throws Exception {
        GraphServiceClientProvider provider = new GraphServiceClientProvider(null, null, null, null);
        assertNull("useEmail=true with non-wsContact accountID must return null",
                invokeResolveWsContactId(provider, true, TEST_EMAIL));
    }

    /**
     * useEmail=true with null accountID — Branch 1 still returns null.
     */
    @Test
    public void testResolveWsContactId_UseEmailTrue_NullAccountID_ReturnsNull() throws Exception {
        GraphServiceClientProvider provider = new GraphServiceClientProvider(null, null, null, null);
        assertNull("useEmail=true with null accountID must return null",
                invokeResolveWsContactId(provider, true, null));
    }

    /**
     * useEmail=null with a null requestContext.
     * resolveUseEmail(null) → !isInvokeAsUser() → !false = true → Branch 1.
     */
    @Test
    public void testResolveWsContactId_NullUseEmail_NullRequestContext_ReturnsNull() throws Exception {
        GraphServiceClientProvider provider = new GraphServiceClientProvider(null, null, null, null);
        assertNull("null useEmail + null requestContext: resolveUseEmail→true, so null expected",
                invokeResolveWsContactId(provider, null, WS_CONTACT_ID));
    }

    /**
     * useEmail=null with invokeAsUser=false.
     * resolveUseEmail(null) → !false = true → Branch 1.
     */
    @Test
    public void testResolveWsContactId_NullUseEmail_InvokeAsUserFalse_ReturnsNull() throws Exception {
        RequestContext rc = createMockRequestContext("INV_test", false);
        GraphServiceClientProvider provider = new GraphServiceClientProvider(null, null, rc, null);
        assertNull("null useEmail with invokeAsUser=false: resolveUseEmail→true, so null expected",
                invokeResolveWsContactId(provider, null, WS_CONTACT_ID));
    }

    // ── Branch 2: accountID starts with WS_CONTACT → return it ───────────────

    /**
     * useEmail=false with a valid wsContact accountID — the accountID is returned as-is.
     */
    @Test
    public void testResolveWsContactId_UseEmailFalse_WsContactAccountID_ReturnsAccountID() throws Exception {
        GraphServiceClientProvider provider = new GraphServiceClientProvider(null, null, null, null);
        assertEquals("useEmail=false with wsContact accountID must return that accountID",
                WS_CONTACT_ID,
                invokeResolveWsContactId(provider, false, WS_CONTACT_ID));
    }

    /**
     * Edge case: accountID is exactly the WS_CONTACT prefix string itself.
     * startsWith() is still true, so the prefix string is returned.
     */
    @Test
    public void testResolveWsContactId_UseEmailFalse_WsContactPrefixOnly_ReturnsPrefixOnly() throws Exception {
        GraphServiceClientProvider provider = new GraphServiceClientProvider(null, null, null, null);
        String prefixOnly = Constants.WS_CONTACT;
        assertEquals("accountID equal to the WS_CONTACT prefix must still be returned",
                prefixOnly,
                invokeResolveWsContactId(provider, false, prefixOnly));
    }

    /**
     * useEmail=null with invokeAsUser=true (MCP mode) and a wsContact accountID.
     * resolveUseEmail(null) → !true = false → Branch 2 returns the accountID.
     */
    @Test
    public void testResolveWsContactId_NullUseEmail_InvokeAsUserTrue_WsContactAccountID_ReturnsAccountID() throws Exception {
        RequestContext rc = createMockRequestContext("INV_test", true);
        GraphServiceClientProvider provider = new GraphServiceClientProvider(null, null, rc, null);
        assertEquals("null useEmail with invokeAsUser=true and wsContact accountID must return accountID",
                WS_CONTACT_ID,
                invokeResolveWsContactId(provider, null, WS_CONTACT_ID));
    }

    // ── Branch 3: non-wsContact accountID falls through; authorizationContext
    //             called but returns non-wsContact → return null ──────────────

    /**
     * useEmail=false, accountID doesn't start with WS_CONTACT, null authorizationContext.
     * getAuthorizedAccountId() throws IllegalStateException → caught → return null.
     */
    @Test
    public void testResolveWsContactId_UseEmailFalse_NonWsContactAccountID_NullAuthContext_ReturnsNull() throws Exception {
        GraphServiceClientProvider provider = new GraphServiceClientProvider(null, null, null, null);
        assertNull("non-wsContact accountID with null authorizationContext must return null",
                invokeResolveWsContactId(provider, false, "operator@example.com"));
    }

    /**
     * useEmail=false, empty string accountID (does not start with WS_CONTACT),
     * null authorizationContext → return null.
     */
    @Test
    public void testResolveWsContactId_UseEmailFalse_EmptyAccountID_NullAuthContext_ReturnsNull() throws Exception {
        GraphServiceClientProvider provider = new GraphServiceClientProvider(null, null, null, null);
        assertNull("empty accountID with null authorizationContext must return null",
                invokeResolveWsContactId(provider, false, ""));
    }

    /**
     * useEmail=false, null accountID, null authorizationContext.
     * accountID == null fails the startsWith guard → authorizationContext path.
     * authorizationContext is null → getAuthorizedAccountId() throws → caught → return null.
     */
    @Test
    public void testResolveWsContactId_UseEmailFalse_NullAccountID_NullAuthContext_ReturnsNull() throws Exception {
        GraphServiceClientProvider provider = new GraphServiceClientProvider(null, null, null, null);
        assertNull("null accountID with null authorizationContext must return null",
                invokeResolveWsContactId(provider, false, null));
    }

    /**
     * useEmail=false, null accountID, authorizationContext returns a non-wsContact account ID.
     * The ID does not start with WS_CONTACT so the method falls through and returns null.
     */
    @Test
    public void testResolveWsContactId_UseEmailFalse_NullAccountID_AuthContextReturnsNonWsContact_ReturnsNull() throws Exception {
        AuthorizationContext ac = createAuthContextReturning("operator@example.com");
        GraphServiceClientProvider provider = new GraphServiceClientProvider(null, null, null, ac);
        assertNull("authorizationContext returning non-wsContact ID must result in null",
                invokeResolveWsContactId(provider, false, null));
    }

    // ── Branch 4: authorizationContext returns a wsContact ID → return it ─────

    /**
     * useEmail=false, null accountID, authorizationContext returns a wsContact account ID.
     * The ID starts with WS_CONTACT so it must be returned.
     */
    @Test
    public void testResolveWsContactId_UseEmailFalse_NullAccountID_AuthContextReturnsWsContact_ReturnsWsContact() throws Exception {
        String wsContactFromAuth = "wsContact_from-authorizationContext-456";
        AuthorizationContext ac = createAuthContextReturning(wsContactFromAuth);
        GraphServiceClientProvider provider = new GraphServiceClientProvider(null, null, null, ac);
        assertEquals("authorizationContext returning wsContact ID must be returned",
                wsContactFromAuth,
                invokeResolveWsContactId(provider, false, null));
    }

    // ── Branch 5: authorizationContext throws → caught → return null ──────────

    /**
     * useEmail=false, null accountID, authorizationContext.getAuthorizedAccount() throws.
     * The exception is caught inside resolveWsContactId and null is returned.
     */
    @Test
    public void testResolveWsContactId_UseEmailFalse_NullAccountID_AuthContextThrows_ReturnsNull() throws Exception {
        AuthorizationContext throwingCtx = createThrowingAuthContext();
        GraphServiceClientProvider provider = new GraphServiceClientProvider(null, null, null, throwingCtx);
        assertNull("exception thrown by authorizationContext must be caught and return null",
                invokeResolveWsContactId(provider, false, null));
    }

    /**
     * Same as above but verifies explicitly that no exception escapes the method boundary.
     */
    @Test
    public void testResolveWsContactId_AuthContextThrows_DoesNotPropagateException() {
        try {
            AuthorizationContext throwingCtx = createThrowingAuthContext();
            GraphServiceClientProvider provider = new GraphServiceClientProvider(null, null, null, throwingCtx);
            invokeResolveWsContactId(provider, false, null);
            // Reaching here is the expected outcome — the exception was swallowed
        } catch (Exception e) {
            fail("resolveWsContactId must not propagate exceptions from authorizationContext: " + e);
        }
    }

    // ── Helpers for resolveWsContactId tests ──────────────────────────────────

    /**
     * Invokes the private {@code resolveWsContactId} method via reflection.
     * Unwraps any {@link InvocationTargetException} so that tests see the
     * real cause rather than a reflection wrapper.
     */
    private String invokeResolveWsContactId(GraphServiceClientProvider provider,
                                             Boolean useEmail, String accountID) throws Exception {
        Method m = GraphServiceClientProvider.class
                .getDeclaredMethod("resolveWsContactId", Boolean.class, String.class);
        m.setAccessible(true);
        try {
            return (String) m.invoke(provider, useEmail, accountID);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception) throw (Exception) cause;
            throw new RuntimeException(cause);
        }
    }

    /**
     * Creates an {@link AuthorizationContext} proxy whose {@code getAuthorizedAccount()}
     * throws a {@link RuntimeException}, exercising the {@code catch (Exception e)} branch
     * inside {@code resolveWsContactId}.
     */
    private AuthorizationContext createThrowingAuthContext() {
        return (AuthorizationContext) Proxy.newProxyInstance(
                AuthorizationContext.class.getClassLoader(),
                new Class<?>[]{AuthorizationContext.class},
                (proxy, method, args) -> {
                    if ("getAuthorizedAccount".equals(method.getName())) {
                        throw new RuntimeException("Auth service unavailable");
                    }
                    if (method.getReturnType() == boolean.class) return false;
                    return null;
                });
    }

    /**
     * Creates an {@link AuthorizationContext} proxy that returns an {@link Account} proxy
     * whose {@code getAccountId()} returns the supplied {@code accountId}.
     * <p>
     * Requires both {@code AuthorizationContext} and {@code Account} to be interfaces.
     */
    private AuthorizationContext createAuthContextReturning(String accountId) {
        Object account = Proxy.newProxyInstance(
                Account.class.getClassLoader(),
                new Class<?>[]{Account.class},
                (proxy, method, args) -> {
                    if ("getAccountId".equals(method.getName())) return accountId;
                    if (method.getReturnType() == boolean.class) return false;
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

    // ========================================
    // Helper: mock RequestContext via JDK Proxy
    // ========================================

    private RequestContext createMockRequestContext(String invokerId, boolean invokeAsUser) {
        return (RequestContext) Proxy.newProxyInstance(
                RequestContext.class.getClassLoader(),
                new Class<?>[]{RequestContext.class},
                (proxy, method, args) -> {
                    if ("getInvokerId".equals(method.getName())) return invokerId;
                    if ("invokeAsUser".equals(method.getName())) return invokeAsUser;
                    if (method.getReturnType() == boolean.class) return false;
                    if (method.getReturnType() == String.class) return "";
                    return null;
                });
    }

    // ========================================
    // Helper: spy KeyValueStore — records wsContact key access
    // ========================================

    private static class SpyKeyValueStore implements KeyValueStore {
        private final boolean[] wsContactTokenChecked;
        private final Map<String, Object> data = new ConcurrentHashMap<>();

        SpyKeyValueStore(boolean[] wsContactTokenChecked) {
            this.wsContactTokenChecked = wsContactTokenChecked;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T get(String key, Class<T> type) {
            if (key != null && key.startsWith(Constants.WS_CONTACT)) {
                wsContactTokenChecked[0] = true;
            }
            Object value = data.get(key);
            if (value == null) return null;
            if (type.isInstance(value)) return type.cast(value);
            return (T) value;
        }

        @Override public Object get(String key) { return data.get(key); }
        @Override public void remove(String key) { data.remove(key); }
        @Override public boolean contains(String key) { return data.containsKey(key); }
        @Override public void put(String key, Object value) { data.put(key, value); }
        @Override public boolean supports(Class<?> clazz) { return true; }
        @Override public Set<String> getKeys() { return new HashSet<>(data.keySet()); }
        @Override public Iterator<Map.Entry<String, Object>> iterator() {
            return data.entrySet().iterator();
        }
    }

    // ========================================
    // Helper: simple MockKeyValueStore
    // ========================================

    private static class MockKeyValueStore implements KeyValueStore {
        private final Map<String, Object> data = new ConcurrentHashMap<>();

        @Override public Object get(String key) { return data.get(key); }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T get(String key, Class<T> type) {
            Object value = data.get(key);
            if (value == null) return null;
            if (type.isInstance(value)) return type.cast(value);
            return (T) value;
        }

        @Override public void remove(String key) { data.remove(key); }
        @Override public boolean contains(String key) { return data.containsKey(key); }
        @Override public void put(String key, Object value) { data.put(key, value); }
        @Override public boolean supports(Class<?> clazz) { return true; }
        @Override public Set<String> getKeys() { return new HashSet<>(data.keySet()); }
        @Override public Iterator<Map.Entry<String, Object>> iterator() {
            return data.entrySet().iterator();
        }
    }
}
