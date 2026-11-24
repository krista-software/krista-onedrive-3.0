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
 * Unit tests for OnedriveSubscriptionDiscoveryService.
 * Tests hybrid discovery (cache + KVS scan) and UUID validation.
 */
public class OnedriveSubscriptionDiscoveryServiceTest {

    private OnedriveSubscriptionDiscoveryService discoveryService;
    private MockKeyValueStore mockKvs;
    private SubscriptionStorageStore storageStore;

    private static final String VALID_UUID_1 = "550e8400-e29b-41d4-a716-446655440000";
    private static final String VALID_UUID_2 = "6ba7b810-9dad-11d1-80b4-00c04fd430c8";
    private static final String NON_UUID_KEY = "routing-url-path";
    private static final String ANOTHER_NON_UUID = "IS_FROM_CATALOG_REQUEST";

    @Before
    public void setUp() {
        mockKvs = new MockKeyValueStore();
        storageStore = new SubscriptionStorageStore(mockKvs);
        discoveryService = new OnedriveSubscriptionDiscoveryService(storageStore, mockKvs);
    }

    // ========================================
    // EMPTY DISCOVERY TESTS
    // ========================================

    /**
     * Test discovery returns empty set when no subscriptions exist
     */
    @Test
    public void testDiscoverAllSubscriptionIds_Empty() {
        Set<String> result = discoveryService.discoverAllSubscriptionIds();

        assertNotNull("Result should never be null", result);
        assertTrue("Should be empty when no subscriptions exist", result.isEmpty());
    }

    // ========================================
    // CACHE-BASED DISCOVERY TESTS
    // ========================================

    /**
     * Test discovery finds subscriptions from cache
     */
    @Test
    public void testDiscoverAllSubscriptionIds_FromCache() {
        SubscriptionDao dao = SubscriptionDao.create(VALID_UUID_1, "drive-1", "delta-1");
        storageStore.addFileMetaData(VALID_UUID_1, dao);

        Set<String> result = discoveryService.discoverAllSubscriptionIds();

        assertTrue("Should find subscription from cache", result.contains(VALID_UUID_1));
    }

    /**
     * Test discovery finds multiple subscriptions from cache
     */
    @Test
    public void testDiscoverAllSubscriptionIds_MultipleCacheEntries() {
        storageStore.addFileMetaData(VALID_UUID_1, SubscriptionDao.create(VALID_UUID_1, "d1", "t1"));
        storageStore.addFileMetaData(VALID_UUID_2, SubscriptionDao.create(VALID_UUID_2, "d2", "t2"));

        Set<String> result = discoveryService.discoverAllSubscriptionIds();

        assertEquals("Should find 2 subscriptions", 2, result.size());
        assertTrue("Should contain UUID_1", result.contains(VALID_UUID_1));
        assertTrue("Should contain UUID_2", result.contains(VALID_UUID_2));
    }

    // ========================================
    // KVS SCAN DISCOVERY TESTS
    // ========================================

    /**
     * Test discovery finds UUID keys from KeyValueStore scan
     */
    @Test
    public void testDiscoverAllSubscriptionIds_FromKvsScan() {
        // Put a UUID-format key directly in KVS (not through SubscriptionStorageStore)
        mockKvs.put(VALID_UUID_1, "some-value");
        // Also put non-UUID keys that should be ignored
        mockKvs.put(NON_UUID_KEY, "some-path-value");
        mockKvs.put(ANOTHER_NON_UUID, "false");

        Set<String> result = discoveryService.discoverAllSubscriptionIds();

        assertTrue("Should find UUID key from KVS scan", result.contains(VALID_UUID_1));
        assertFalse("Should NOT include non-UUID key", result.contains(NON_UUID_KEY));
        assertFalse("Should NOT include non-UUID key", result.contains(ANOTHER_NON_UUID));
    }

    /**
     * Test discovery merges cache and KVS results without duplicates
     */
    @Test
    public void testDiscoverAllSubscriptionIds_MergesWithoutDuplicates() {
        // Add to cache via storageStore
        storageStore.addFileMetaData(VALID_UUID_1, SubscriptionDao.create(VALID_UUID_1, "d1", "t1"));
        // Also put directly in KVS (simulating it's in both)
        mockKvs.put(VALID_UUID_2, "some-json");

        Set<String> result = discoveryService.discoverAllSubscriptionIds();

        assertTrue("Should contain cache entry", result.contains(VALID_UUID_1));
        assertTrue("Should contain KVS entry", result.contains(VALID_UUID_2));
    }

    // ========================================
    // UUID VALIDATION TESTS
    // ========================================

    /**
     * Test that various non-UUID keys are excluded from discovery
     */
    @Test
    public void testDiscoverAllSubscriptionIds_ExcludesNonUUIDs() {
        String[] nonUuidKeys = {
                "routing-url",
                "IS_FROM_CATALOG_REQUEST",
                "user@email.com",
                "http://localhost/path",
                "not-a-uuid-format",
                "",
                "12345",
                "abc-def-ghi"
        };

        for (String key : nonUuidKeys) {
            if (key != null && !key.isEmpty()) {
                mockKvs.put(key, "value");
            }
        }

        Set<String> result = discoveryService.discoverAllSubscriptionIds();

        for (String key : nonUuidKeys) {
            assertFalse("Non-UUID key should not be in results: " + key, result.contains(key));
        }
    }

    /**
     * Test that valid UUID keys are included in discovery
     */
    @Test
    public void testDiscoverAllSubscriptionIds_IncludesValidUUIDs() {
        String[] validUuids = {
                "550e8400-e29b-41d4-a716-446655440000",
                "6ba7b810-9dad-11d1-80b4-00c04fd430c8",
                "f47ac10b-58cc-4372-a567-0e02b2c3d479"
        };

        for (String uuid : validUuids) {
            mockKvs.put(uuid, "value");
        }

        Set<String> result = discoveryService.discoverAllSubscriptionIds();

        assertEquals("Should find all valid UUIDs", validUuids.length, result.size());
        for (String uuid : validUuids) {
            assertTrue("Should contain UUID: " + uuid, result.contains(uuid));
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
