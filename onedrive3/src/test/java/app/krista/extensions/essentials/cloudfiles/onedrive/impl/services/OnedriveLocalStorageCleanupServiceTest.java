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

import app.krista.extensions.essentials.cloudfiles.onedrive.dao.SubscriptionDao;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.stores.SubscriptionStorageStore;
import app.krista.extensions.util.KeyValueStore;
import org.junit.Before;
import org.junit.Test;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.Assert.*;

/**
 * Unit tests for OnedriveLocalStorageCleanupService.
 * Tests subscription cleanup from all three storage layers.
 */
public class OnedriveLocalStorageCleanupServiceTest {

    private OnedriveLocalStorageCleanupService cleanupService;
    private SubscriptionStorageStore storageStore;
    private MockKeyValueStore mockKvs;

    private static final String SUB_ID_1 = "550e8400-e29b-41d4-a716-446655440000";
    private static final String SUB_ID_2 = "6ba7b810-9dad-11d1-80b4-00c04fd430c8";
    private static final String PATH_1 = "http://localhost:8080/onedrive/notification";
    private static final String PATH_2 = "http://localhost:8080/onedrive/lifecycle";

    @Before
    public void setUp() {
        mockKvs = new MockKeyValueStore();
        storageStore = new SubscriptionStorageStore(mockKvs);
        cleanupService = new OnedriveLocalStorageCleanupService(storageStore);
    }

    // ========================================
    // EMPTY SET TESTS
    // ========================================

    /**
     * Test cleanupAllSubscriptions with empty set returns 0
     */
    @Test
    public void testCleanupAllSubscriptions_EmptySet() {
        int result = cleanupService.cleanupAllSubscriptions(Collections.emptySet());

        assertEquals("Should return 0 for empty set", 0, result);
    }

    // ========================================
    // SUCCESSFUL CLEANUP TESTS
    // ========================================

    /**
     * Test cleanupAllSubscriptions removes single subscription
     */
    @Test
    public void testCleanupAllSubscriptions_SingleSubscription() {
        SubscriptionDao dao = SubscriptionDao.create(SUB_ID_1, "drive-1", "delta-1");
        storageStore.addFileMetaData(SUB_ID_1, dao);
        storageStore.addSubscriptionID(PATH_1, SUB_ID_1);

        Set<String> ids = new HashSet<>();
        ids.add(SUB_ID_1);

        int result = cleanupService.cleanupAllSubscriptions(ids);

        assertEquals("Should successfully clean 1 subscription", 1, result);
        assertNull("Metadata should be removed", storageStore.getFileMetaData(SUB_ID_1));
    }

    /**
     * Test cleanupAllSubscriptions removes multiple subscriptions
     */
    @Test
    public void testCleanupAllSubscriptions_MultipleSubscriptions() {
        storageStore.addFileMetaData(SUB_ID_1, SubscriptionDao.create(SUB_ID_1, "d1", "t1"));
        storageStore.addFileMetaData(SUB_ID_2, SubscriptionDao.create(SUB_ID_2, "d2", "t2"));
        storageStore.addSubscriptionID(PATH_1, SUB_ID_1);
        storageStore.addSubscriptionID(PATH_2, SUB_ID_2);

        Set<String> ids = new HashSet<>();
        ids.add(SUB_ID_1);
        ids.add(SUB_ID_2);

        int result = cleanupService.cleanupAllSubscriptions(ids);

        assertEquals("Should successfully clean 2 subscriptions", 2, result);
        assertNull("SUB_ID_1 metadata should be removed", storageStore.getFileMetaData(SUB_ID_1));
        assertNull("SUB_ID_2 metadata should be removed", storageStore.getFileMetaData(SUB_ID_2));
    }

    /**
     * Test cleanupAllSubscriptions handles subscription without path mapping
     */
    @Test
    public void testCleanupAllSubscriptions_NoPathMapping() {
        storageStore.addFileMetaData(SUB_ID_1, SubscriptionDao.create(SUB_ID_1, "d1", "t1"));
        // No path mapping added

        Set<String> ids = new HashSet<>();
        ids.add(SUB_ID_1);

        int result = cleanupService.cleanupAllSubscriptions(ids);

        assertEquals("Should successfully clean subscription without path", 1, result);
    }

    /**
     * Test cleanupAllSubscriptions with subscription not in store
     */
    @Test
    public void testCleanupAllSubscriptions_SubscriptionNotInStore() {
        Set<String> ids = new HashSet<>();
        ids.add("non-existent-sub");

        int result = cleanupService.cleanupAllSubscriptions(ids);

        // Should succeed even if subscription doesn't exist (remove is idempotent)
        assertEquals("Should handle non-existent subscription", 1, result);
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
