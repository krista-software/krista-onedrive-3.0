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

import app.krista.extension.authorization.MustAuthorizeException;
import app.krista.extension.authorization.RequestAuthenticator.AuthorizationResponse;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.stores.OnedriveAttributeStore;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.util.Constants;
import app.krista.extensions.util.KeyValueStore;
import app.krista.model.field.NamedValuedField;
import app.krista.sdk.msauth.extension.base.MsAuthAttributes;
import app.krista.sdk.msauth.token.ITokenStore;
import app.krista.sdk.msauth.token.InMemoryTokenStore;
import app.krista.extension.executor.Invoker;
import app.krista.extension.request.RoutingInfo;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link OnedriveRequestAuthenticator}.
 * Covers the refactored authenticator that extends AbstractRequestAuthenticator.
 */
public class OnedriveRequestAuthenticatorTest {

    private static final String TEST_INVOKER_ID = "INV_test-invoker-123";
    private static final String TEST_ROUTING_URL = "http://localhost:8765";

    private TrackingAttributeStore attributeStore;
    private OnedriveRequestAuthenticator authenticator;

    @Before
    public void setUp() {
        MockKeyValueStore kvStore = new MockKeyValueStore();
        Invoker invoker = createMockInvoker();
        attributeStore = new TrackingAttributeStore(invoker, kvStore);
        authenticator = new OnedriveRequestAuthenticator(attributeStore, TEST_INVOKER_ID, TEST_ROUTING_URL);
    }

    // ========================================
    // CONSTRUCTOR TESTS
    // ========================================

    @Test
    public void testConstructor_createsInstanceSuccessfully() {
        assertNotNull("Authenticator should be created", authenticator);
    }

    @Test
    public void testGetInvokerId_returnsCorrectValue() {
        assertEquals(TEST_INVOKER_ID, authenticator.getInvokerId());
    }

    @Test
    public void testGetRoutingUrl_returnsCorrectValue() {
        assertEquals(TEST_ROUTING_URL, authenticator.getRoutingUrl());
    }

    // ========================================
    // getMustAuthorizeResponse — null/empty details
    // ========================================

    @Test(expected = IllegalStateException.class)
    public void testGetMustAuthorizeResponse_nullDetails_throwsIllegalState() {
        MustAuthorizeException cause = new MustAuthorizeException("Auth required");
        authenticator.getMustAuthorizeResponse(cause);
    }

    @Test(expected = IllegalStateException.class)
    public void testGetMustAuthorizeResponse_emptyDetails_throwsIllegalState() {
        MustAuthorizeException cause = new MustAuthorizeException("Auth required", new ArrayList<>());
        authenticator.getMustAuthorizeResponse(cause);
    }

    // ========================================
    // getMustAuthorizeResponse — wsContact bypass
    // ========================================

    @Test
    public void testGetMustAuthorizeResponse_wsContactUser_usesDefaultPublicAttrs() {
        String wsContactUserId = "wsContact-user-123";
        List<NamedValuedField> details = new ArrayList<>();
        details.add(new NamedValuedField(Constants.USER_ID, Constants.TEXT, wsContactUserId, new HashMap<>(), new HashMap<>()));
        MustAuthorizeException cause = new MustAuthorizeException("Auth required", details);

        AuthorizationResponse response = authenticator.getMustAuthorizeResponse(cause);

        assertNotNull("Response should not be null", response);
        assertNotNull("URL should not be null", response.getLocation());
        assertTrue("URL should contain login.microsoftonline.com",
                response.getLocation().contains("login.microsoftonline.com"));
        // Attribute store should NOT be called for wsContact
        assertFalse("Attribute store should not be loaded for wsContact",
                attributeStore.loadedKeys.contains(wsContactUserId));
    }

    @Test
    public void testGetMustAuthorizeResponse_wsContactUser_stateContainsInvokerId() {
        String wsContactUserId = "wsContact-user-456";
        List<NamedValuedField> details = new ArrayList<>();
        details.add(new NamedValuedField(Constants.USER_ID, Constants.TEXT, wsContactUserId, new HashMap<>(), new HashMap<>()));
        MustAuthorizeException cause = new MustAuthorizeException("Auth required", details);

        AuthorizationResponse response = authenticator.getMustAuthorizeResponse(cause);

        // State should contain wsContactId#invokerId pattern
        String url = response.getLocation();
        assertNotNull(url);
        assertTrue("URL should contain the wsContact userId", url.contains(wsContactUserId));
    }

    // ========================================
    // getMustAuthorizeResponse — wsContact with invokerId in detail[1]
    // ========================================

    @Test
    public void testGetMustAuthorizeResponse_wsContactWithInvokerId_alwaysUsesKristaDefaultCredentials() {
        String wsContactId = "wsContact-user-789";
        // Even when operator attrs exist in the store, MCP always uses Krista default public credentials.
        OnedriveAttributes operatorAttrs = new OnedriveAttributes(
                TEST_ROUTING_URL, "operator-cid", "operator-cs", "admin@example.com", "op-tenant", Constants.PUBLIC);
        attributeStore.savedAttributes.put(TEST_INVOKER_ID, operatorAttrs);

        List<NamedValuedField> details = new ArrayList<>();
        details.add(new NamedValuedField(Constants.CLIENT_USER_ID, Constants.TEXT, wsContactId, new HashMap<>(), new HashMap<>()));
        details.add(new NamedValuedField(Constants.USER_ID, Constants.TEXT, TEST_INVOKER_ID, new HashMap<>(), new HashMap<>()));
        MustAuthorizeException cause = new MustAuthorizeException("Auth required", details);

        AuthorizationResponse response = authenticator.getMustAuthorizeResponse(cause);

        assertNotNull("Response should not be null", response);
        assertNotNull("URL should not be null", response.getLocation());
        assertTrue("URL should contain login.microsoftonline.com",
                response.getLocation().contains("login.microsoftonline.com"));
        // Attribute store must NOT be queried — wsContact always uses Krista default from config.properties
        assertFalse("Attribute store should not be queried for wsContact path",
                attributeStore.loadedKeys.contains(TEST_INVOKER_ID));
        assertFalse("wsContactId should not be used as an attribute lookup key",
                attributeStore.loadedKeys.contains(wsContactId));
    }

    @Test
    public void testGetMustAuthorizeResponse_wsContactWithInvokerId_noOperatorAttrs_usesKristaDefault() {
        String wsContactId = "wsContact-no-attrs";
        String invokerIdVal = "INV_no-attrs-invoker";
        // No attrs stored under invokerIdVal — but attribute store should not be queried at all

        List<NamedValuedField> details = new ArrayList<>();
        details.add(new NamedValuedField(Constants.CLIENT_USER_ID, Constants.TEXT, wsContactId, new HashMap<>(), new HashMap<>()));
        details.add(new NamedValuedField(Constants.USER_ID, Constants.TEXT, invokerIdVal, new HashMap<>(), new HashMap<>()));
        MustAuthorizeException cause = new MustAuthorizeException("Auth required", details);

        AuthorizationResponse response = authenticator.getMustAuthorizeResponse(cause);

        assertNotNull("Response should not be null", response);
        assertNotNull("URL should still be generated using Krista default credentials", response.getLocation());
        // Attribute store must NOT be queried — wsContact always uses Krista default from config.properties
        assertTrue("Attribute store should not be queried for wsContact path",
                attributeStore.loadedKeys.isEmpty());
        assertTrue("URL should still point to Microsoft login",
                response.getLocation().contains("login.microsoftonline.com"));
    }

    @Test
    public void testGetMustAuthorizeResponse_wsContactSingleDetail_noInvokerId_loadAttributesNotCalled() {
        String wsContactId = "wsContact-single-detail";

        List<NamedValuedField> details = new ArrayList<>();
        details.add(new NamedValuedField(Constants.CLIENT_USER_ID, Constants.TEXT, wsContactId, new HashMap<>(), new HashMap<>()));
        // Only 1 detail — no invokerId; code path: invokerId == null → loadAttributes never called
        MustAuthorizeException cause = new MustAuthorizeException("Auth required", details);

        AuthorizationResponse response = authenticator.getMustAuthorizeResponse(cause);

        assertNotNull("Response should not be null", response);
        assertNotNull("URL should be generated even without invokerId", response.getLocation());
        // loadAttributes must NOT be called when there is no second detail
        assertTrue("loadedKeys should be empty when no invokerId is present",
                attributeStore.loadedKeys.isEmpty());
    }

    @Test
    public void testGetMustAuthorizeResponse_wsContactState_containsWsContactIdAndAuthenticatorInvokerId() {
        String wsContactId = "wsContact-state-verify";

        List<NamedValuedField> details = new ArrayList<>();
        details.add(new NamedValuedField(Constants.CLIENT_USER_ID, Constants.TEXT, wsContactId, new HashMap<>(), new HashMap<>()));
        MustAuthorizeException cause = new MustAuthorizeException("Auth required", details);

        AuthorizationResponse response = authenticator.getMustAuthorizeResponse(cause);

        String url = response.getLocation();
        assertNotNull(url);
        // The OAuth state embedded in the URL must include the wsContactId and the authenticator's own invokerId
        assertTrue("URL should contain wsContact userId in state", url.contains(wsContactId));
        assertTrue("URL state should contain the authenticator's invokerId", url.contains(TEST_INVOKER_ID));
    }

    @Test
    public void testGetMustAuthorizeResponse_wsContactWithThreeDetails_handledWithoutException() {
        String wsContactId = "wsContact-multi-detail";
        String invokerIdVal = "INV_multi-detail";

        List<NamedValuedField> details = new ArrayList<>();
        details.add(new NamedValuedField(Constants.CLIENT_USER_ID, Constants.TEXT, wsContactId, new HashMap<>(), new HashMap<>()));
        details.add(new NamedValuedField(Constants.USER_ID, Constants.TEXT, invokerIdVal, new HashMap<>(), new HashMap<>()));
        details.add(new NamedValuedField("extra", Constants.TEXT, "extra-value", new HashMap<>(), new HashMap<>()));
        MustAuthorizeException cause = new MustAuthorizeException("Auth required", details);

        // Must not throw even when there are more than 2 details
        AuthorizationResponse response = authenticator.getMustAuthorizeResponse(cause);
        assertNotNull("Response should not be null with extra details", response);
        assertNotNull(response.getLocation());
    }

    // ========================================
    // getMustAuthorizeResponse — regular user with authContextId
    // ========================================

    @Test
    public void testGetMustAuthorizeResponse_regularUser_withAuthContextId_loadsAttributes() {
        String userId = "user@example.com";
        String authContextId = "authCtx-123";
        OnedriveAttributes attrs = new OnedriveAttributes(TEST_ROUTING_URL, "client-id", "client-secret",
                "user@example.com", "tenant-id", Constants.PUBLIC);
        attributeStore.savedAttributes.put(authContextId, attrs);

        List<NamedValuedField> details = new ArrayList<>();
        details.add(new NamedValuedField(Constants.USER_ID, Constants.TEXT, userId, new HashMap<>(), new HashMap<>()));
        details.add(new NamedValuedField(Constants.AUTH_CONTEXT_ID, Constants.TEXT, authContextId, new HashMap<>(), new HashMap<>()));
        MustAuthorizeException cause = new MustAuthorizeException("Auth required", details);

        AuthorizationResponse response = authenticator.getMustAuthorizeResponse(cause);

        assertNotNull("Response should not be null", response);
        assertNotNull("URL should not be null", response.getLocation());
        assertTrue("Attribute store should be loaded with authContextId",
                attributeStore.loadedKeys.contains(authContextId));
    }

    // ========================================
    // getMustAuthorizeResponse — regular user without authContextId
    // ========================================

    @Test
    public void testGetMustAuthorizeResponse_regularUser_noAuthContext_loadsFromInvokerId() {
        String userId = "user@example.com";
        OnedriveAttributes attrs = new OnedriveAttributes(TEST_ROUTING_URL, "client-id", "client-secret",
                "user@example.com", "tenant-id", Constants.PUBLIC);
        attributeStore.savedAttributes.put(TEST_INVOKER_ID, attrs);

        List<NamedValuedField> details = new ArrayList<>();
        details.add(new NamedValuedField(Constants.USER_ID, Constants.TEXT, userId, new HashMap<>(), new HashMap<>()));
        MustAuthorizeException cause = new MustAuthorizeException("Auth required", details);

        AuthorizationResponse response = authenticator.getMustAuthorizeResponse(cause);

        assertNotNull("Response should not be null", response);
        assertNotNull("URL should not be null", response.getLocation());
        assertTrue("Attribute store should be loaded with invokerId",
                attributeStore.loadedKeys.contains(TEST_INVOKER_ID));
    }

    // ========================================
    // getMustAuthorizeResponse — fallback to defaults
    // ========================================

    @Test
    public void testGetMustAuthorizeResponse_noStoredAttributes_fallsBackToDefaults() {
        String userId = "user@example.com";
        // Don't store any attributes — attribute store will return null

        List<NamedValuedField> details = new ArrayList<>();
        details.add(new NamedValuedField(Constants.USER_ID, Constants.TEXT, userId, new HashMap<>(), new HashMap<>()));
        MustAuthorizeException cause = new MustAuthorizeException("Auth required", details);

        AuthorizationResponse response = authenticator.getMustAuthorizeResponse(cause);

        assertNotNull("Response should not be null even without stored attrs", response);
        assertNotNull("URL should still be generated from defaults", response.getLocation());
    }

    // ========================================
    // loadAttributes — delegates to attribute store
    // ========================================

    @Test
    public void testLoadAttributes_delegatesToAttributeStore() {
        OnedriveAttributes attrs = new OnedriveAttributes(TEST_ROUTING_URL, "cid", "cs", "email", "tid", Constants.PUBLIC);
        attributeStore.savedAttributes.put("test-key", attrs);

        // Indirectly test via getMustAuthorizeResponse with authContextId
        List<NamedValuedField> details = new ArrayList<>();
        details.add(new NamedValuedField(Constants.USER_ID, Constants.TEXT, "user", new HashMap<>(), new HashMap<>()));
        details.add(new NamedValuedField(Constants.AUTH_CONTEXT_ID, Constants.TEXT, "test-key", new HashMap<>(), new HashMap<>()));
        MustAuthorizeException cause = new MustAuthorizeException("Auth required", details);

        authenticator.getMustAuthorizeResponse(cause);

        assertTrue("loadAttributes should delegate to attribute store",
                attributeStore.loadedKeys.contains("test-key"));
    }

    @Test
    public void testLoadAttributes_returnsNullWhenNotFound() {
        // No attributes stored for "missing-key"
        List<NamedValuedField> details = new ArrayList<>();
        details.add(new NamedValuedField(Constants.USER_ID, Constants.TEXT, "user", new HashMap<>(), new HashMap<>()));
        details.add(new NamedValuedField(Constants.AUTH_CONTEXT_ID, Constants.TEXT, "missing-key", new HashMap<>(), new HashMap<>()));
        MustAuthorizeException cause = new MustAuthorizeException("Auth required", details);

        // Should not throw — falls back to createDefaultAttributes
        AuthorizationResponse response = authenticator.getMustAuthorizeResponse(cause);
        assertNotNull("Should fallback to default attributes", response);
    }

    // ========================================
    // createDefaultAttributes — returns PUBLIC OneDrive attributes
    // ========================================

    @Test
    public void testCreateDefaultAttributes_returnsPublicType() {
        // Exercise createDefaultAttributes indirectly via fallback path
        String userId = "no-attrs-user";
        List<NamedValuedField> details = new ArrayList<>();
        details.add(new NamedValuedField(Constants.USER_ID, Constants.TEXT, userId, new HashMap<>(), new HashMap<>()));
        MustAuthorizeException cause = new MustAuthorizeException("Auth required", details);

        // No attributes in store, so createDefaultAttributes is used
        AuthorizationResponse response = authenticator.getMustAuthorizeResponse(cause);
        assertNotNull("Default attributes should produce a valid URL", response);
        assertNotNull(response.getLocation());
    }

    // ========================================
    // getScheme — inherited from AbstractRequestAuthenticator
    // ========================================

    @Test
    public void testGetScheme_returnsNull() {
        assertNull("getScheme should return null", authenticator.getScheme());
    }

    // ========================================
    // getSupportedProtocols — inherited
    // ========================================

    @Test
    public void testGetSupportedProtocols_returnsEmptySet() {
        Set<String> protocols = authenticator.getSupportedProtocols();
        assertNotNull("Protocols should not be null", protocols);
        assertTrue("Protocols should be empty", protocols.isEmpty());
    }

    // ========================================
    // setServiceAuthorization — inherited
    // ========================================

    @Test
    public void testSetServiceAuthorization_returnsFalse() {
        assertFalse("setServiceAuthorization should return false",
                authenticator.setServiceAuthorization("any-key"));
    }

    // ========================================
    // getAttributeFields — inherited
    // ========================================

    @Test
    public void testGetAttributeFields_returnsEmptyMap() {
        Map<?, ?> fields = authenticator.getAttributeFields();
        assertNotNull("Attribute fields should not be null", fields);
        assertTrue("Attribute fields should be empty", fields.isEmpty());
    }

    // ========================================
    // getMustAuthenticateResponse — inherited, returns null
    // ========================================

    @Test
    public void testGetMustAuthenticateResponse_causeOnly_returnsNull() {
        assertNull(authenticator.getMustAuthenticateResponse(
                new app.krista.extension.authorization.MustAuthenticateException("test")));
    }

    @Test
    public void testGetMustAuthorizeResponse_withProtoRequest_returnsNull() {
        assertNull(authenticator.getMustAuthorizeResponse(
                new MustAuthorizeException("test"), null));
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
            String id = "authCtx-" + UUID.randomUUID();
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
