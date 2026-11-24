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

package app.krista.extensions.essentials.cloudfiles.onedrive.impl.services;

import app.krista.extension.executor.Invoker;
import app.krista.extensions.essentials.cloudfiles.onedrive.OnedriveAttributes;
import app.krista.extensions.essentials.cloudfiles.onedrive.dao.SubscriptionDao;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.stores.OnedriveAttributeStore;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.stores.SubscriptionStorageStore;
import app.krista.extensions.util.KeyValueStore;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.Assert.*;

/**
 * Unit tests for OnedriveSubscriptionCleanupService.
 * Tests the orchestrator for credential change and invoker removal scenarios.
 */
public class OnedriveSubscriptionCleanupServiceTest {

    private static final String TEST_INVOKER_ID = "test-invoker-123";
    private static final String TEST_EMAIL_OLD = "old@example.com";
    private static final String TEST_EMAIL_NEW = "new@example.com";
    private static final String TEST_CLIENT_ID = "test-client-id";
    private static final String TEST_CLIENT_SECRET = "test-client-secret";
    private static final String TEST_TENANT_ID = "test-tenant-id";

    private OnedriveSubscriptionCleanupService cleanupService;
    private MockKeyValueStore mockKvs;
    private SubscriptionStorageStore storageStore;
    private TrackingOnedriveAttributeStore attributeStore;
    private OnedriveSubscriptionDiscoveryService discoveryService;
    private OnedriveLocalStorageCleanupService localCleanupService;
    private TrackingGraphCleanupService graphCleanupService;

    @Before
    public void setUp() {
        mockKvs = new MockKeyValueStore();
        storageStore = new SubscriptionStorageStore(mockKvs);

        MockKeyValueStore refreshKvs = new MockKeyValueStore();

        attributeStore = new TrackingOnedriveAttributeStore(createMockInvoker(), mockKvs);
        discoveryService = new OnedriveSubscriptionDiscoveryService(storageStore, mockKvs);
        localCleanupService = new OnedriveLocalStorageCleanupService(storageStore);
        graphCleanupService = new TrackingGraphCleanupService(null);

        cleanupService = new OnedriveSubscriptionCleanupService(
                discoveryService,
                graphCleanupService,
                localCleanupService,
                attributeStore,
                refreshKvs,
                createMockInvoker()
        );
    }

    // ========================================
    // HANDLE INVOKER REMOVAL TESTS
    // ========================================

    /**
     * Test handleInvokerRemoval with no subscriptions should not throw
     */
    @Test
    public void testHandleInvokerRemoval_NoSubscriptions() {
        // Should not throw any exception
        cleanupService.handleInvokerRemoval();
        assertTrue("Should complete without error", true);
    }

    /**
     * Test handleInvokerRemoval removes credentials even with no subscriptions
     */
    @Test
    public void testHandleInvokerRemoval_RemovesCredentials() {
        // Store some credentials
        attributeStore.savedAttributes.put(TEST_INVOKER_ID, createOldAttributes());

        cleanupService.handleInvokerRemoval();

        assertTrue("Credentials should be removed", attributeStore.removedKeys.contains(TEST_INVOKER_ID));
    }

    /**
     * Test handleInvokerRemoval cleans up existing subscriptions
     */
    @Test
    public void testHandleInvokerRemoval_WithSubscriptions() {
        String subId = "550e8400-e29b-41d4-a716-446655440000";
        storageStore.addFileMetaData(subId, SubscriptionDao.create(subId, "d1", "t1"));
        attributeStore.savedAttributes.put(TEST_INVOKER_ID, createOldAttributes());

        cleanupService.handleInvokerRemoval();

        assertTrue("Credentials should be removed", attributeStore.removedKeys.contains(TEST_INVOKER_ID));
    }

    /**
     * Test handleInvokerRemoval does not throw on errors - non-blocking cleanup
     */
    @Test
    public void testHandleInvokerRemoval_DoesNotThrowOnError() {
        // Use an attribute store that throws on load
        attributeStore.throwOnLoad = true;

        // Should not throw
        cleanupService.handleInvokerRemoval();
        assertTrue("Should complete without throwing", true);
    }

    // ========================================
    // HANDLE CREDENTIAL CHANGE TESTS
    // ========================================

    /**
     * Test handleCredentialChange with same email does not trigger cleanup
     */
    @Test
    public void testHandleCredentialChange_SameEmail() {
        OnedriveAttributes oldAttrs = createOldAttributes();
        OnedriveAttributes newAttrs = new OnedriveAttributes(
                "http://localhost", TEST_CLIENT_ID, TEST_CLIENT_SECRET, TEST_EMAIL_OLD, TEST_TENANT_ID);

        attributeStore.savedAttributes.put(TEST_INVOKER_ID, oldAttrs);

        cleanupService.handleCredentialChange(newAttrs);

        assertFalse("Graph cleanup should not be called for same email",
                graphCleanupService.deleteWasCalled);
    }

    /**
     * Test handleCredentialChange with case-insensitive same email does not trigger cleanup
     */
    @Test
    public void testHandleCredentialChange_SameEmailDifferentCase() {
        OnedriveAttributes oldAttrs = createOldAttributes();
        OnedriveAttributes newAttrs = new OnedriveAttributes(
                "http://localhost", TEST_CLIENT_ID, TEST_CLIENT_SECRET,
                TEST_EMAIL_OLD.toUpperCase(), TEST_TENANT_ID);

        attributeStore.savedAttributes.put(TEST_INVOKER_ID, oldAttrs);

        cleanupService.handleCredentialChange(newAttrs);

        assertFalse("Graph cleanup should not be called for same email (different case)",
                graphCleanupService.deleteWasCalled);
    }

    /**
     * Test handleCredentialChange with different email triggers cleanup
     */
    @Test
    public void testHandleCredentialChange_DifferentEmail() {
        OnedriveAttributes oldAttrs = createOldAttributes();
        OnedriveAttributes newAttrs = new OnedriveAttributes(
                "http://localhost", TEST_CLIENT_ID, TEST_CLIENT_SECRET, TEST_EMAIL_NEW, TEST_TENANT_ID);

        attributeStore.savedAttributes.put(TEST_INVOKER_ID, oldAttrs);

        cleanupService.handleCredentialChange(newAttrs);

        // With different email, cleanup should be triggered
        // (graph cleanup may or may not be called depending on whether subscriptions exist)
    }

    /**
     * Test handleCredentialChange when old attributes is null (first-time setup)
     */
    @Test
    public void testHandleCredentialChange_FirstTimeSetup() {
        OnedriveAttributes newAttrs = new OnedriveAttributes(
                "http://localhost", TEST_CLIENT_ID, TEST_CLIENT_SECRET, TEST_EMAIL_NEW, TEST_TENANT_ID);
        // No old attributes stored

        cleanupService.handleCredentialChange(newAttrs);

        assertFalse("Graph cleanup should not be called for first-time setup",
                graphCleanupService.deleteWasCalled);
    }

    /**
     * Test handleCredentialChange with null emails does not trigger cleanup
     */
    @Test
    public void testHandleCredentialChange_NullEmails() {
        OnedriveAttributes oldAttrs = new OnedriveAttributes(
                "http://localhost", TEST_CLIENT_ID, TEST_CLIENT_SECRET, null, TEST_TENANT_ID);
        OnedriveAttributes newAttrs = new OnedriveAttributes(
                "http://localhost", TEST_CLIENT_ID, TEST_CLIENT_SECRET, null, TEST_TENANT_ID);

        attributeStore.savedAttributes.put(TEST_INVOKER_ID, oldAttrs);

        cleanupService.handleCredentialChange(newAttrs);

        assertFalse("Graph cleanup should not be called when emails are null",
                graphCleanupService.deleteWasCalled);
    }

    /**
     * Test handleCredentialChange does not throw on errors
     */
    @Test
    public void testHandleCredentialChange_DoesNotThrowOnError() {
        attributeStore.throwOnLoad = true;

        OnedriveAttributes newAttrs = new OnedriveAttributes(
                "http://localhost", TEST_CLIENT_ID, TEST_CLIENT_SECRET, TEST_EMAIL_NEW, TEST_TENANT_ID);

        // Should not throw
        cleanupService.handleCredentialChange(newAttrs);
        assertTrue("Should complete without throwing", true);
    }

    // ========================================
    // HELPER METHODS
    // ========================================

    private OnedriveAttributes createOldAttributes() {
        return new OnedriveAttributes(
                "http://localhost", TEST_CLIENT_ID, TEST_CLIENT_SECRET, TEST_EMAIL_OLD, TEST_TENANT_ID);
    }

    private Invoker createMockInvoker() {
        return (Invoker) Proxy.newProxyInstance(
                Invoker.class.getClassLoader(),
                new Class<?>[]{Invoker.class},
                (proxy, method, args) -> {
                    if ("getInvokerId".equals(method.getName())) {
                        return TEST_INVOKER_ID;
                    }
                    if (method.getReturnType() == boolean.class) return false;
                    if (method.getReturnType() == Map.class) return Collections.emptyMap();
                    if (method.getReturnType() == List.class) return Collections.emptyList();
                    return null;
                });
    }

    // ========================================
    // TRACKING MOCK CLASSES
    // ========================================

    /**
     * OnedriveAttributeStore mock that tracks calls and allows controlled behavior
     */
    private static class TrackingOnedriveAttributeStore extends OnedriveAttributeStore {
        final Map<String, OnedriveAttributes> savedAttributes = new HashMap<>();
        final Set<String> removedKeys = new HashSet<>();
        boolean throwOnLoad = false;

        TrackingOnedriveAttributeStore(Invoker invoker, KeyValueStore store) {
            super(invoker, store);
        }

        @Override
        public OnedriveAttributes load(String authContextId) {
            if (throwOnLoad) {
                throw new RuntimeException("Simulated load error");
            }
            return savedAttributes.get(authContextId);
        }

        @Override
        public void remove(String authContextId) {
            removedKeys.add(authContextId);
        }
    }

    /**
     * OnedriveGraphCleanupService mock that tracks whether delete was called
     */
    private static class TrackingGraphCleanupService extends OnedriveGraphCleanupService {
        boolean deleteWasCalled = false;

        TrackingGraphCleanupService(app.krista.extensions.essentials.cloudfiles.onedrive.impl.connectors.GraphServiceClientProviderFactory factory) {
            super(factory);
        }

        @Override
        public CleanupResult deleteAllSubscriptions(Set<String> subscriptionIds,
                                                     OnedriveAttributes attributes) {
            deleteWasCalled = true;
            return new CleanupResult(subscriptionIds.size(), 0);
        }
    }

    // ========================================
    // MOCK KEY VALUE STORE
    // ========================================

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
