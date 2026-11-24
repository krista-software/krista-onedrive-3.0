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
import app.krista.extension.executor.Invoker;
import app.krista.extension.request.RoutingInfo;
import app.krista.extensions.essentials.cloudfiles.onedrive.OnedriveAttributes;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.stores.OnedriveAttributeStore;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.util.Constants;
import app.krista.extensions.util.KeyValueStore;
import app.krista.ksdk.context.AuthorizationContext;
import app.krista.ksdk.context.RequestContext;
import app.krista.model.field.NamedValuedField;
import org.junit.Before;
import org.junit.Test;

import app.krista.ksdk.accounts.Account;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link GraphServiceClientProviderFactory}.
 * Focuses on the MustAuthorizeException details fix and RequestContext paths.
 */
public class GraphServiceClientProviderFactoryTest {

    private static final String TEST_INVOKER_ID = "INV_test-factory-invoker";
    private static final String TEST_ROUTING_URL = "http://localhost:8765";

    private MockKeyValueStore kvStore;
    private TrackingAttributeStore attributeStore;
    private GraphServiceClientProviderFactory factory;

    @Before
    public void setUp() {
        kvStore = new MockKeyValueStore();
        Invoker invoker = createMockInvoker();
        attributeStore = new TrackingAttributeStore(invoker, kvStore);
    }

    // ========================================
    // create(String authContextId) — attributes found
    // ========================================

    @Test
    public void testCreateWithAuthContextId_attributesFound_returnsProvider() {
        OnedriveAttributes attrs = new OnedriveAttributes(TEST_ROUTING_URL, "cid", "cs", "email", "tid", Constants.PUBLIC);
        attributeStore.savedAttributes.put("authCtx-1", attrs);

        factory = new GraphServiceClientProviderFactory(kvStore, attributeStore, null, null);
        GraphServiceClientProvider provider = factory.create("authCtx-1");

        assertNotNull("Provider should not be null when attributes exist", provider);
    }

    // ========================================
    // create(String authContextId) — no attributes → MustAuthorizeException with details
    // ========================================

    @Test
    public void testCreateWithAuthContextId_noAttributes_throwsMustAuthorize() {
        factory = new GraphServiceClientProviderFactory(kvStore, attributeStore, null, null);

        try {
            factory.create("missing-context");
            fail("Should throw MustAuthorizeException");
        } catch (MustAuthorizeException e) {
            assertNotNull("Exception details should not be null", e.getDetails());
            assertEquals("Should have 2 detail fields (userId + authContextId)", 2, e.getDetails().size());

            // Verify userId field
            NamedValuedField userIdField = e.getDetails().get(0);
            assertEquals(Constants.USER_ID, userIdField.getName());
            assertEquals("missing-context", userIdField.getValue());

            // Verify authContextId field
            NamedValuedField authCtxField = e.getDetails().get(1);
            assertEquals(Constants.AUTH_CONTEXT_ID, authCtxField.getName());
            assertEquals("missing-context", authCtxField.getValue());
        }
    }

    // ========================================
    // create(RequestContext) — invokeAsUser=true, NO stored attrs → MustAuthorizeException
    // ========================================

    /**
     * First MCP call with no stored attrs and no authorizationContext: must still throw
     * MustAuthorizeException via the fallback path (invokerId as userId).
     */
    @Test
    public void testCreateWithRequestContext_invokeAsUser_noAttrs_throwsMustAuthorizeException() {
        RequestContext rc = createMockRequestContext(TEST_INVOKER_ID, true);
        // authorizationContext null → NullPointerException caught → fallback MAE with invokerId
        factory = new GraphServiceClientProviderFactory(kvStore, attributeStore, rc, null);

        try {
            factory.create(rc);
            fail("create(RequestContext) with invokeAsUser=true and no attrs must throw MustAuthorizeException");
        } catch (MustAuthorizeException e) {
            assertNotNull("Exception details must not be null", e.getDetails());
            assertFalse("Exception details must not be empty", e.getDetails().isEmpty());
            assertTrue("Error message should mention Authentication tab",
                    e.getMessage().contains("Authentication tab"));
        }
    }

    /**
     * First MCP call with authorizationContext available: throws MAE with CLIENT_USER_ID
     * (raw wsContactId) at details[0] so getMustAuthorizeResponse builds the correct state.
     */
    @Test
    public void testCreateWithRequestContext_invokeAsUser_noAttrs_withAuthContext_throwsMaeWithWsContactId() {
        final String wsContactId = "wsContact_abc123";
        RequestContext rc = createMockRequestContext(TEST_INVOKER_ID, true);
        AuthorizationContext authCtx = createMockAuthorizationContext(wsContactId);
        factory = new GraphServiceClientProviderFactory(kvStore, attributeStore, rc, authCtx);

        try {
            factory.create(rc);
            fail("create(RequestContext) invokeAsUser=true + no attrs must throw MustAuthorizeException");
        } catch (MustAuthorizeException e) {
            assertNotNull("Exception details must not be null", e.getDetails());
            assertEquals("Should have 2 details: CLIENT_USER_ID and USER_ID", 2, e.getDetails().size());

            NamedValuedField clientUserIdField = e.getDetails().get(0);
            assertEquals("details[0] name should be clientUserId",
                    Constants.CLIENT_USER_ID, clientUserIdField.getName());
            assertEquals("details[0] value should be wsContactId",
                    wsContactId, clientUserIdField.getValue());

            NamedValuedField userIdField = e.getDetails().get(1);
            assertEquals("details[1] name should be userId", Constants.USER_ID, userIdField.getName());
            assertEquals("details[1] value should be invokerId", TEST_INVOKER_ID, userIdField.getValue());
        }
    }

    // ========================================
    // create(RequestContext) — invokeAsUser=true, attrs FOUND → returns provider (post-auth)
    // ========================================

    /**
     * When authorizationContext is unavailable (fallback / internal call), the factory
     * falls back to the non-user path and returns a provider if Setup attrs exist.
     * This covers internal calls where authorizationContext is not injected.
     */
    @Test
    public void testCreateWithRequestContext_invokeAsUser_attrsFound_returnsProvider() {
        OnedriveAttributes attrs = new OnedriveAttributes(TEST_ROUTING_URL, null, null, "user@example.com", null, Constants.PUBLIC);
        attributeStore.savedAttributes.put(TEST_INVOKER_ID, attrs);

        RequestContext rc = createMockRequestContext(TEST_INVOKER_ID, true);
        // authorizationContext=null triggers fallback to non-user path
        factory = new GraphServiceClientProviderFactory(kvStore, attributeStore, rc, null);

        GraphServiceClientProvider provider = factory.create(rc);

        assertNotNull("Provider must be returned via fallback when authorizationContext is unavailable", provider);
        assertTrue("Must load attributes using the invokerId",
                attributeStore.loadedKeys.contains(TEST_INVOKER_ID));
    }

    /**
     * After the OAuth callback stores the wsContact user's refresh token (plain alias),
     * the next MCP call must find the token and return a working provider without
     * throwing MustAuthorizeException.
     */
    @Test
    public void testCreateWithRequestContext_invokeAsUser_withTokenAndAuthContext_returnsProvider() {
        final String wsContactId = "wsContact_abc123";
        // Simulate what the OAuth callback stores: plain wsContactId alias
        kvStore.put(wsContactId, "stored_refresh_token_abc");

        // Setup attrs saved under invokerId (by saveCredentials on the Authentication tab)
        OnedriveAttributes attrs = new OnedriveAttributes(TEST_ROUTING_URL, null, null, "admin@example.com", null, Constants.PUBLIC);
        attributeStore.savedAttributes.put(TEST_INVOKER_ID, attrs);

        RequestContext rc = createMockRequestContext(TEST_INVOKER_ID, true);
        AuthorizationContext authCtx = createMockAuthorizationContext(wsContactId);
        factory = new GraphServiceClientProviderFactory(kvStore, attributeStore, rc, authCtx);

        GraphServiceClientProvider provider = factory.create(rc);

        assertNotNull("Provider must be returned when wsContact user has a stored token", provider);
        assertTrue("Must load attributes using the invokerId",
                attributeStore.loadedKeys.contains(TEST_INVOKER_ID));
    }

    /**
     * When Setup attrs exist but the wsContact user has NO token, the factory must
     * still return a provider. The per-user token check is deferred to
     * GraphServiceClientProvider.getGraphServiceClientForUser (method execution time),
     * which throws the correctly-formatted MustAuthorizeException.
     *
     * <p>Throwing MAE from the factory here would fail HK2 singleton construction and
     * wrap the exception in a MultiException that the platform cannot handle as an
     * authorisation event.</p>
     */
    @Test
    public void testCreateWithRequestContext_invokeAsUser_setupAttrsExist_noToken_returnsProviderForDeferredAuth() {
        final String wsContactId = "wsContact_xyz789";
        // Setup attrs exist under invokerId — no token stored for wsContactId
        OnedriveAttributes setupAttrs = new OnedriveAttributes(TEST_ROUTING_URL, "cid", "cs", "admin@example.com", "tid", Constants.PUBLIC);
        attributeStore.savedAttributes.put(TEST_INVOKER_ID, setupAttrs);

        RequestContext rc = createMockRequestContext(TEST_INVOKER_ID, true);
        AuthorizationContext authCtx = createMockAuthorizationContext(wsContactId);
        factory = new GraphServiceClientProviderFactory(kvStore, attributeStore, rc, authCtx);

        // Factory must return a provider; MAE is thrown later from getGraphServiceClientForUser.
        GraphServiceClientProvider provider = factory.create(rc);
        assertNotNull("Factory must return provider when Setup attrs exist (auth deferred to method level)", provider);
        assertTrue("Must load attributes using the invokerId",
                attributeStore.loadedKeys.contains(TEST_INVOKER_ID));
    }

    // ========================================
    // create(RequestContext) — invokeAsUser=false, attributes found
    // ========================================

    @Test
    public void testCreateWithRequestContext_attributesFound_returnsProvider() {
        OnedriveAttributes attrs = new OnedriveAttributes(TEST_ROUTING_URL, "cid", "cs", "email", "tid", Constants.PUBLIC);
        attributeStore.savedAttributes.put(TEST_INVOKER_ID, attrs);

        RequestContext rc = createMockRequestContext(TEST_INVOKER_ID, false);
        factory = new GraphServiceClientProviderFactory(kvStore, attributeStore, rc, null);

        GraphServiceClientProvider provider = factory.create(rc);

        assertNotNull("Provider should not be null when attributes exist", provider);
        assertTrue("Should load attributes with invokerId",
                attributeStore.loadedKeys.contains(TEST_INVOKER_ID));
    }

    // ========================================
    // create(RequestContext) — invokeAsUser=false, no attributes → MustAuthorizeException WITH details
    // ========================================

    @Test
    public void testCreateWithRequestContext_noAttributes_throwsMustAuthorizeWithDetails() {
        RequestContext rc = createMockRequestContext(TEST_INVOKER_ID, false);
        factory = new GraphServiceClientProviderFactory(kvStore, attributeStore, rc, null);

        try {
            factory.create(rc);
            fail("Should throw MustAuthorizeException");
        } catch (MustAuthorizeException e) {
            assertNotNull("Exception details should not be null", e.getDetails());
            assertFalse("Exception details should not be empty", e.getDetails().isEmpty());
            assertEquals("Should have 1 detail field (userId)", 1, e.getDetails().size());

            NamedValuedField userIdField = e.getDetails().get(0);
            assertEquals("Field name should be userId", Constants.USER_ID, userIdField.getName());
            assertEquals("Field value should be the invokerId", TEST_INVOKER_ID, userIdField.getValue());
        }
    }

    @Test
    public void testCreateWithRequestContext_noAttributes_exceptionMessageCorrect() {
        RequestContext rc = createMockRequestContext(TEST_INVOKER_ID, false);
        factory = new GraphServiceClientProviderFactory(kvStore, attributeStore, rc, null);

        try {
            factory.create(rc);
            fail("Should throw MustAuthorizeException");
        } catch (MustAuthorizeException e) {
            assertTrue("Error message should mention authentication tab",
                    e.getMessage().contains("Authentication tab"));
        }
    }

    // ========================================
    // create() — delegates to create(RequestContext)
    // ========================================

    @Test
    public void testCreate_delegatesToCreateWithRequestContext() {
        OnedriveAttributes attrs = new OnedriveAttributes(TEST_ROUTING_URL, "cid", "cs", "email", "tid", Constants.PUBLIC);
        attributeStore.savedAttributes.put(TEST_INVOKER_ID, attrs);

        RequestContext rc = createMockRequestContext(TEST_INVOKER_ID, false);
        factory = new GraphServiceClientProviderFactory(kvStore, attributeStore, rc, null);

        GraphServiceClientProvider provider = factory.create();

        assertNotNull("create() should delegate to create(RequestContext)", provider);
    }

    // ========================================
    // create(OnedriveAttributes) — creates provider from supplied attributes
    // ========================================

    @Test
    public void testCreateWithAttributes_returnsProvider() {
        factory = new GraphServiceClientProviderFactory(kvStore, attributeStore, null, null);
        OnedriveAttributes attrs = new OnedriveAttributes(TEST_ROUTING_URL, "cid", "cs", "email", "tid", Constants.PUBLIC);

        GraphServiceClientProvider provider = factory.create(attrs);

        assertNotNull("Provider should be created from supplied attributes", provider);
    }

    // ========================================
    // createAttributes — saves and returns ID
    // ========================================

    @Test
    public void testCreateAttributes_savesAndReturnsId() {
        factory = new GraphServiceClientProviderFactory(kvStore, attributeStore, null, null);
        OnedriveAttributes attrs = new OnedriveAttributes(TEST_ROUTING_URL, "cid", "cs", "email", "tid", Constants.PUBLIC);

        String id = factory.createAttributes(attrs);

        assertNotNull("Returned ID should not be null", id);
        assertTrue("Attributes should be saved", attributeStore.savedAttributes.containsValue(attrs));
    }

    // ========================================
    // MOCK: TrackingAttributeStore
    // ========================================

    private static class TrackingAttributeStore extends OnedriveAttributeStore {
        final Map<String, OnedriveAttributes> savedAttributes = new HashMap<>();
        final Set<String> loadedKeys = new HashSet<>();

        TrackingAttributeStore(Invoker invoker, KeyValueStore store) {
            super(invoker, store);
        }

        @Override
        public OnedriveAttributes load(String authContextId) {
            loadedKeys.add(authContextId);
            return savedAttributes.get(authContextId);
        }

        @Override
        public String save(OnedriveAttributes onedriveAttributes) {
            String id = "saved-" + UUID.randomUUID();
            savedAttributes.put(id, onedriveAttributes);
            return id;
        }

        @Override
        public boolean save(OnedriveAttributes onedriveAttributes, String invokerId) {
            savedAttributes.put(invokerId, onedriveAttributes);
            return true;
        }

        @Override
        public void remove(String authContextId) {
            savedAttributes.remove(authContextId);
        }
    }

    // ========================================
    // MOCK: RequestContext
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
    // MOCK: KeyValueStore
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
        @Override public Iterator<Map.Entry<String, Object>> iterator() { return data.entrySet().iterator(); }
    }

    // ========================================
    // MOCK: AuthorizationContext
    // ========================================

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

    // ========================================
    // MOCK: Invoker
    // ========================================

    private Invoker createMockInvoker() {
        RoutingInfo routingInfo = (RoutingInfo) Proxy.newProxyInstance(
                RoutingInfo.class.getClassLoader(),
                new Class<?>[]{RoutingInfo.class},
                (proxy, method, args) -> {
                    if ("getRoutingURL".equals(method.getName())) return TEST_ROUTING_URL;
                    return null;
                });
        return (Invoker) Proxy.newProxyInstance(
                Invoker.class.getClassLoader(),
                new Class<?>[]{Invoker.class},
                (proxy, method, args) -> {
                    if ("getInvokerId".equals(method.getName())) return TEST_INVOKER_ID;
                    if ("getRoutingInfo".equals(method.getName())) return routingInfo;
                    if ("getAttributes".equals(method.getName())) return Collections.emptyMap();
                    if (method.getReturnType() == boolean.class) return false;
                    if (method.getReturnType() == Map.class) return Collections.emptyMap();
                    if (method.getReturnType() == List.class) return Collections.emptyList();
                    return null;
                });
    }
}
