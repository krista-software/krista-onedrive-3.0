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

package app.krista.extensions.essentials.cloudfiles.onedrive.impl.stores;

import app.krista.extension.executor.Invoker;
import app.krista.extension.request.RoutingInfo;
import app.krista.extension.request.protos.http.HttpProtocol;
import app.krista.extensions.essentials.cloudfiles.onedrive.OnedriveAttributes;
import app.krista.extensions.util.KeyValueStore;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link OnedriveAttributeStore}.
 *
 * Covers:
 * - save(OnedriveAttributes) returns a UUID authContextId
 * - save(OnedriveAttributes, invokerId) returns true on success
 * - load with no data returns null
 * - load after save returns correct attributes
 * - remove deletes the key from store
 * - load with blank string returns null
 * - save with invokerId then load returns matching attributes
 */
public class OnedriveAttributeStoreTest {

    private static final String TEST_ROUTING_URL = "http://localhost:8765";
    private static final String TEST_CLIENT_ID = "test-client-id";
    private static final String TEST_CLIENT_SECRET = "test-client-secret";
    private static final String TEST_EMAIL = "user@example.com";
    private static final String TEST_TENANT_ID = "test-tenant-id";
    private static final String TEST_AUTH_TYPE = "Private";

    private MockKeyValueStore mockKvs;
    private OnedriveAttributeStore store;

    @Before
    public void setUp() {
        mockKvs = new MockKeyValueStore();
        Invoker mockInvoker = createMockInvoker();
        store = new OnedriveAttributeStore(mockInvoker, mockKvs);
    }

    // ================================================================
    // save(OnedriveAttributes) TESTS
    // ================================================================

    @Test
    public void save_returnsAuthContextId() {
        OnedriveAttributes attrs = createTestAttributes();

        String authContextId = store.save(attrs);

        assertNotNull("save should return a non-null authContextId", authContextId);
        assertFalse("authContextId should not be empty", authContextId.isEmpty());
    }

    @Test
    public void save_returnsValidUuid() {
        OnedriveAttributes attrs = createTestAttributes();

        String authContextId = store.save(attrs);

        // UUID.fromString will throw if the string is not a valid UUID
        try {
            UUID.fromString(authContextId);
        } catch (IllegalArgumentException e) {
            fail("authContextId should be a valid UUID, but was: " + authContextId);
        }
    }

    @Test
    public void save_storesDataInKeyValueStore() {
        OnedriveAttributes attrs = createTestAttributes();

        String authContextId = store.save(attrs);

        assertTrue("KeyValueStore should contain the authContextId key", mockKvs.contains(authContextId));
    }

    // ================================================================
    // save(OnedriveAttributes, invokerId) TESTS
    // ================================================================

    @Test
    public void save_withInvokerId_returnsTrue() {
        OnedriveAttributes attrs = createTestAttributes();

        boolean result = store.save(attrs, "invoker-123");

        assertTrue("save with invokerId should return true on success", result);
    }

    @Test
    public void save_withInvokerId_storesDataUnderInvokerId() {
        OnedriveAttributes attrs = createTestAttributes();

        store.save(attrs, "invoker-123");

        assertTrue("KeyValueStore should contain the invokerId key", mockKvs.contains("invoker-123"));
    }

    // ================================================================
    // load TESTS
    // ================================================================

    @Test
    public void load_noData_returnsNull() {
        OnedriveAttributes result = store.load("nonexistent-key");

        assertNull("load should return null when no data exists for the key", result);
    }

    @Test
    public void load_afterSave_returnsCorrectAttributes() {
        OnedriveAttributes attrs = createTestAttributes();
        String authContextId = store.save(attrs);

        OnedriveAttributes loaded = store.load(authContextId);

        assertNotNull("load should return non-null after save", loaded);
        assertEquals("email should match", TEST_EMAIL, loaded.getEmail());
        assertEquals("clientId should match", TEST_CLIENT_ID, loaded.getClientId());
        assertEquals("clientSecret should match", TEST_CLIENT_SECRET, loaded.getClientSecret());
        assertEquals("tenantId should match", TEST_TENANT_ID, loaded.getTenantId());
        assertEquals("authType should match", TEST_AUTH_TYPE, loaded.getAuthType());
    }

    @Test
    public void load_blankString_returnsNull() {
        // Put a blank string directly in the store to simulate blank data
        mockKvs.put("blank-key", "");

        OnedriveAttributes result = store.load("blank-key");

        assertNull("load should return null for blank stored value", result);
    }

    @Test
    public void load_blankStringSpaces_returnsNull() {
        mockKvs.put("blank-spaces-key", "   ");

        OnedriveAttributes result = store.load("blank-spaces-key");

        assertNull("load should return null for whitespace-only stored value", result);
    }

    // ================================================================
    // save with invokerId then load TESTS
    // ================================================================

    @Test
    public void save_withInvokerId_thenLoad_returnsMatchingAttributes() {
        OnedriveAttributes attrs = createTestAttributes();
        String invokerId = "my-invoker-id";

        store.save(attrs, invokerId);
        OnedriveAttributes loaded = store.load(invokerId);

        assertNotNull("load should return non-null after save with invokerId", loaded);
        assertEquals("email should match", TEST_EMAIL, loaded.getEmail());
        assertEquals("clientId should match", TEST_CLIENT_ID, loaded.getClientId());
        assertEquals("tenantId should match", TEST_TENANT_ID, loaded.getTenantId());
        assertEquals("authType should match", TEST_AUTH_TYPE, loaded.getAuthType());
    }

    // ================================================================
    // remove TESTS
    // ================================================================

    @Test
    public void remove_deletesKeyFromStore() {
        OnedriveAttributes attrs = createTestAttributes();
        String authContextId = store.save(attrs);

        assertTrue("Key should exist before remove", mockKvs.contains(authContextId));

        store.remove(authContextId);

        assertFalse("Key should not exist after remove", mockKvs.contains(authContextId));
    }

    @Test
    public void remove_thenLoad_returnsNull() {
        OnedriveAttributes attrs = createTestAttributes();
        String authContextId = store.save(attrs);

        store.remove(authContextId);
        OnedriveAttributes loaded = store.load(authContextId);

        assertNull("load after remove should return null", loaded);
    }

    // ================================================================
    // MULTIPLE SAVES INDEPENDENCE TESTS
    // ================================================================

    @Test
    public void multipleSaves_generateDifferentAuthContextIds() {
        OnedriveAttributes attrs = createTestAttributes();

        String id1 = store.save(attrs);
        String id2 = store.save(attrs);

        assertNotEquals("Each save should generate a unique authContextId", id1, id2);
    }

    // ================================================================
    // HELPERS
    // ================================================================

    private OnedriveAttributes createTestAttributes() {
        return new OnedriveAttributes(
                TEST_ROUTING_URL,
                TEST_CLIENT_ID,
                TEST_CLIENT_SECRET,
                TEST_EMAIL,
                TEST_TENANT_ID,
                TEST_AUTH_TYPE
        );
    }

    private Invoker createMockInvoker() {
        RoutingInfo routingInfo = (RoutingInfo) Proxy.newProxyInstance(
                RoutingInfo.class.getClassLoader(),
                new Class<?>[]{RoutingInfo.class},
                (proxy, method, args) -> {
                    if ("getRoutingURL".equals(method.getName())) return TEST_ROUTING_URL;
                    if (method.getReturnType() == boolean.class) return false;
                    return null;
                });

        return (Invoker) Proxy.newProxyInstance(
                Invoker.class.getClassLoader(),
                new Class<?>[]{Invoker.class},
                (proxy, method, args) -> {
                    if ("getInvokerId".equals(method.getName())) return "test-invoker";
                    if ("getRoutingInfo".equals(method.getName())) return routingInfo;
                    if (method.getReturnType() == boolean.class) return false;
                    if (method.getReturnType() == Map.class) return Collections.emptyMap();
                    if (method.getReturnType() == List.class) return Collections.emptyList();
                    return null;
                });
    }

    // ================================================================
    // MOCK KEY VALUE STORE
    // ================================================================

    /**
     * Simple in-memory KeyValueStore implementation for testing.
     */
    private static class MockKeyValueStore implements KeyValueStore {
        private final Map<String, Object> data = new ConcurrentHashMap<>();

        @Override
        public Object get(String key) {
            return data.get(key);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T get(String key, Class<T> type) {
            Object value = data.get(key);
            if (value == null) return null;
            if (type.isInstance(value)) return type.cast(value);
            return (T) value;
        }

        @Override
        public void remove(String key) {
            data.remove(key);
        }

        @Override
        public boolean contains(String key) {
            return data.containsKey(key);
        }

        @Override
        public void put(String key, Object value) {
            data.put(key, value);
        }

        @Override
        public boolean supports(Class<?> clazz) {
            return true;
        }

        @Override
        public Set<String> getKeys() {
            return new HashSet<>(data.keySet());
        }

        @Override
        public Iterator<Map.Entry<String, Object>> iterator() {
            return data.entrySet().iterator();
        }
    }
}
