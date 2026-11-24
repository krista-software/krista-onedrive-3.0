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

import app.krista.extensions.essentials.cloudfiles.onedrive.dao.SubscriptionDao;
import app.krista.extensions.util.KeyValueStore;
import org.junit.Before;
import org.junit.Test;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.Assert.*;

/**
 * Unit tests for SubscriptionStorageStore.
 * Tests write-through caching, bidirectional mappings, validation, and cleanup.
 */
public class SubscriptionStorageStoreTest {

    private SubscriptionStorageStore store;
    private MockKeyValueStore mockKvs;

    private static final String SUB_ID_1 = "sub-001";
    private static final String SUB_ID_2 = "sub-002";
    private static final String PATH_1 = "http://localhost:8080/onedrive/notification";
    private static final String PATH_2 = "http://localhost:8080/onedrive/lifecycle";

    @Before
    public void setUp() {
        mockKvs = new MockKeyValueStore();
        store = new SubscriptionStorageStore(mockKvs);
    }

    // ========================================
    // ADD AND GET FILE METADATA TESTS
    // ========================================

    /**
     * Test addFileMetaData stores and getFileMetaData retrieves correctly
     */
    @Test
    public void testAddAndGetFileMetaData() {
        SubscriptionDao dao = SubscriptionDao.create(SUB_ID_1, "drive-1", "delta-1", 1700000000L);

        store.addFileMetaData(SUB_ID_1, dao);
        SubscriptionDao retrieved = store.getFileMetaData(SUB_ID_1);

        assertNotNull("Retrieved metadata should not be null", retrieved);
        assertEquals("subscriptionId should match", SUB_ID_1, retrieved.getSubscriptionId());
        assertEquals("driveId should match", "drive-1", retrieved.getDriveId());
        assertEquals("deltaToken should match", "delta-1", retrieved.getDeltaToken());
        assertEquals("expirationTimestamp should match", 1700000000L, retrieved.getExpirationTimestamp());
    }

    /**
     * Test getFileMetaData returns null for unknown subscription
     */
    @Test
    public void testGetFileMetaData_NotFound() {
        SubscriptionDao retrieved = store.getFileMetaData("unknown-sub");

        assertNull("Should return null for unknown subscription", retrieved);
    }

    /**
     * Test addFileMetaData persists to KeyValueStore
     */
    @Test
    public void testAddFileMetaData_PersistsToKvs() {
        SubscriptionDao dao = SubscriptionDao.create(SUB_ID_1, "drive-1", "delta-1");

        store.addFileMetaData(SUB_ID_1, dao);

        assertTrue("KeyValueStore should contain subscription", mockKvs.contains(SUB_ID_1));
    }

    // ========================================
    // SUBSCRIPTION ID MAPPING TESTS
    // ========================================

    /**
     * Test addSubscriptionID creates bidirectional mapping
     */
    @Test
    public void testAddSubscriptionID_CreatesMapping() {
        store.addSubscriptionID(PATH_1, SUB_ID_1);

        assertEquals("Should retrieve subscription ID by path", SUB_ID_1, store.getSubscriptionId(PATH_1));
        assertEquals("Should retrieve path by subscription ID", PATH_1, store.getPathBySubscription(SUB_ID_1));
    }

    /**
     * Test getSubscriptionId returns empty string for unknown path
     */
    @Test
    public void testGetSubscriptionId_NotFound() {
        String result = store.getSubscriptionId("unknown-path");

        assertEquals("Should return empty string for unknown path", "", result);
    }

    /**
     * Test addSubscriptionID replaces old mapping
     */
    @Test
    public void testAddSubscriptionID_ReplacesOldMapping() {
        store.addSubscriptionID(PATH_1, SUB_ID_1);
        store.addSubscriptionID(PATH_1, SUB_ID_2);

        assertEquals("Path should map to new subscription", SUB_ID_2, store.getSubscriptionId(PATH_1));
    }

    /**
     * Test multiple path-to-subscription mappings
     */
    @Test
    public void testMultiplePathMappings() {
        store.addSubscriptionID(PATH_1, SUB_ID_1);
        store.addSubscriptionID(PATH_2, SUB_ID_2);

        assertEquals("PATH_1 should map to SUB_ID_1", SUB_ID_1, store.getSubscriptionId(PATH_1));
        assertEquals("PATH_2 should map to SUB_ID_2", SUB_ID_2, store.getSubscriptionId(PATH_2));
        assertEquals("SUB_ID_1 should map to PATH_1", PATH_1, store.getPathBySubscription(SUB_ID_1));
        assertEquals("SUB_ID_2 should map to PATH_2", PATH_2, store.getPathBySubscription(SUB_ID_2));
    }

    // ========================================
    // REMOVE TESTS
    // ========================================

    /**
     * Test remove cleans up metadata from all layers
     */
    @Test
    public void testRemove_CleansAllLayers() {
        SubscriptionDao dao = SubscriptionDao.create(SUB_ID_1, "drive-1", "delta-1");
        store.addFileMetaData(SUB_ID_1, dao);
        store.addSubscriptionID(PATH_1, SUB_ID_1);

        store.remove(SUB_ID_1);

        assertNull("Metadata should be removed", store.getFileMetaData(SUB_ID_1));
    }

    /**
     * Test remove with path key
     */
    @Test
    public void testRemove_PathKey() {
        store.addSubscriptionID(PATH_1, SUB_ID_1);

        store.remove(PATH_1);

        assertEquals("Path should no longer map to subscription", "", store.getSubscriptionId(PATH_1));
    }

    // ========================================
    // GET ALL SUBSCRIPTION IDS TESTS
    // ========================================

    /**
     * Test getAllSubscriptionIds returns empty set when no subscriptions
     */
    @Test
    public void testGetAllSubscriptionIds_Empty() {
        Set<String> ids = store.getAllSubscriptionIds();

        assertNotNull("Should never return null", ids);
        assertTrue("Should be empty when no subscriptions added", ids.isEmpty());
    }

    /**
     * Test getAllSubscriptionIds returns all stored subscriptions
     */
    @Test
    public void testGetAllSubscriptionIds_ReturnsAll() {
        SubscriptionDao dao1 = SubscriptionDao.create(SUB_ID_1, "drive-1", "delta-1");
        SubscriptionDao dao2 = SubscriptionDao.create(SUB_ID_2, "drive-2", "delta-2");
        store.addFileMetaData(SUB_ID_1, dao1);
        store.addFileMetaData(SUB_ID_2, dao2);

        Set<String> ids = store.getAllSubscriptionIds();

        assertEquals("Should return 2 subscription IDs", 2, ids.size());
        assertTrue("Should contain SUB_ID_1", ids.contains(SUB_ID_1));
        assertTrue("Should contain SUB_ID_2", ids.contains(SUB_ID_2));
    }

    // ========================================
    // UPDATE SUBSCRIPTION EXPIRATION TESTS
    // ========================================

    /**
     * Test updateSubscriptionExpiration updates timestamp
     */
    @Test
    public void testUpdateSubscriptionExpiration() {
        SubscriptionDao dao = SubscriptionDao.create(SUB_ID_1, "drive-1", "delta-1", 1000L);
        store.addFileMetaData(SUB_ID_1, dao);

        store.updateSubscriptionExpiration(SUB_ID_1, 2000L);

        SubscriptionDao updated = store.getFileMetaData(SUB_ID_1);
        assertNotNull("Updated metadata should not be null", updated);
        assertEquals("Expiration should be updated", 2000L, updated.getExpirationTimestamp());
        assertEquals("subscriptionId should be preserved", SUB_ID_1, updated.getSubscriptionId());
        assertEquals("driveId should be preserved", "drive-1", updated.getDriveId());
        assertEquals("deltaToken should be preserved", "delta-1", updated.getDeltaToken());
    }

    /**
     * Test updateSubscriptionExpiration with unknown subscription does not throw
     */
    @Test
    public void testUpdateSubscriptionExpiration_UnknownSubscription() {
        // Should not throw
        store.updateSubscriptionExpiration("unknown-sub", 2000L);
    }

    // ========================================
    // VALIDATION TESTS
    // ========================================

    /**
     * Test addFileMetaData with null subscriptionId throws IllegalArgumentException
     */
    @Test(expected = IllegalArgumentException.class)
    public void testAddFileMetaData_NullSubscriptionId() {
        SubscriptionDao dao = SubscriptionDao.create(SUB_ID_1, "drive-1", "delta-1");
        store.addFileMetaData(null, dao);
    }

    /**
     * Test addFileMetaData with empty subscriptionId throws IllegalArgumentException
     */
    @Test(expected = IllegalArgumentException.class)
    public void testAddFileMetaData_EmptySubscriptionId() {
        SubscriptionDao dao = SubscriptionDao.create(SUB_ID_1, "drive-1", "delta-1");
        store.addFileMetaData("", dao);
    }

    /**
     * Test addFileMetaData with null dao throws IllegalArgumentException
     */
    @Test(expected = IllegalArgumentException.class)
    public void testAddFileMetaData_NullDao() {
        store.addFileMetaData(SUB_ID_1, null);
    }

    /**
     * Test getFileMetaData with null subscriptionId throws IllegalArgumentException
     */
    @Test(expected = IllegalArgumentException.class)
    public void testGetFileMetaData_NullSubscriptionId() {
        store.getFileMetaData(null);
    }

    /**
     * Test addSubscriptionID with null path throws IllegalArgumentException
     */
    @Test(expected = IllegalArgumentException.class)
    public void testAddSubscriptionID_NullPath() {
        store.addSubscriptionID(null, SUB_ID_1);
    }

    /**
     * Test addSubscriptionID with null subscriptionId throws IllegalArgumentException
     */
    @Test(expected = IllegalArgumentException.class)
    public void testAddSubscriptionID_NullSubscriptionId() {
        store.addSubscriptionID(PATH_1, null);
    }

    /**
     * Test remove with null key throws IllegalArgumentException
     */
    @Test(expected = IllegalArgumentException.class)
    public void testRemove_NullKey() {
        store.remove(null);
    }

    /**
     * Test remove with empty key throws IllegalArgumentException
     */
    @Test(expected = IllegalArgumentException.class)
    public void testRemove_EmptyKey() {
        store.remove("");
    }

    // ========================================
    // MOCK KEY VALUE STORE
    // ========================================

    /**
     * Simple in-memory KeyValueStore implementation for testing
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
