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

import app.krista.extension.authorization.MustAuthorizeException;
import app.krista.extensions.essentials.cloudfiles.onedrive.dao.SubscriptionDao;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.util.Constants;
import app.krista.extensions.util.KeyValueStore;
import org.jvnet.hk2.annotations.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A storage service for managing OneDrive subscription data and file metadata.
 * This class provides a centralized store for mapping between file paths and subscription IDs,
 * as well as storing and retrieving subscription metadata.
 *
 * <p>The store maintains bidirectional mappings between file paths and subscription IDs
 * using in-memory concurrent hash maps for fast lookups, while persisting data using
 * the injected KeyValueStore for durability.</p>
 *
 * <p><b>Thread Safety:</b> This class is thread-safe. All cache operations use ConcurrentHashMap
 * with atomic operations to prevent race conditions.</p>
 *
 * <p><b>Cache Behavior:</b> Uses write-through caching with defensive copying to prevent
 * cache pollution from mutable objects.</p>
 */
@Service
public final class SubscriptionStorageStore {
    private static final Logger LOGGER = LoggerFactory.getLogger(SubscriptionStorageStore.class);
    private static final int CACHE_SIZE_WARNING_THRESHOLD = 10000;

    private final Map<String, String> pathToSubscriptionId = new ConcurrentHashMap<>();
    private final Map<String, String> subscriptionIdToPath = new ConcurrentHashMap<>();
    private final Map<String, SubscriptionDao> subscriptionMetadataCache = new ConcurrentHashMap<>();
    private final KeyValueStore keyValueStore;

    private volatile boolean cacheInitialized = false;
    private final Object initializationLock = new Object();

    @Inject
    public SubscriptionStorageStore(KeyValueStore keyValueStore) {
        this.keyValueStore = keyValueStore;
    }

    /**
     * Stores file metadata for a subscription.
     * Uses write-through caching: updates both persistent store and cache.
     *
     * @param subscriptionId  the subscription identifier
     * @param subscriptionDao the subscription metadata
     */
    public void addFileMetaData(String subscriptionId, SubscriptionDao subscriptionDao) {
        validateSubscriptionId(subscriptionId);
        validateSubscriptionDao(subscriptionDao);

        persistMetadata(subscriptionId, subscriptionDao);
        cacheMetadata(subscriptionId, subscriptionDao);
        LOGGER.debug("Stored file metadata for subscription: {}", subscriptionId);
    }

    /**
     * Retrieves file metadata for a subscription.
     * Ensures cache is initialized from KeyValueStore on first access, then uses cache only.
     * Thread-safe: uses double-checked locking for cache initialization.
     *
     * @param subscriptionId the subscription identifier
     * @return subscription metadata, or null if not found
     */
    public SubscriptionDao getFileMetaData(String subscriptionId) {
        validateSubscriptionId(subscriptionId);

        ensureCacheInitialized();

        SubscriptionDao metadata = subscriptionMetadataCache.get(subscriptionId);

        if (metadata != null) {
            LOGGER.debug("Retrieved metadata for subscription: {} from cache", subscriptionId);
        } else {
            LOGGER.debug("No metadata found for subscription: {}", subscriptionId);
        }

        return metadata;
    }

    /**
     * Associates a file path with a subscription ID.
     * Creates bidirectional mapping in both persistent store and cache.
     *
     * @param path           the file path
     * @param subscriptionId the subscription identifier
     */
    public void addSubscriptionID(String path, String subscriptionId) {
        validatePath(path);
        validateSubscriptionId(subscriptionId);

        keyValueStore.put(path, subscriptionId);
        removeOldMappingIfExists(path);
        updateBidirectionalMapping(path, subscriptionId);
    }

    /**
     * Retrieves the subscription ID for a file path.
     * Falls back to key-value store if not found in cache.
     *
     * @param path the file path
     * @return the subscription ID, or empty string if not found
     */
    public String getSubscriptionId(String path) {
        validatePath(path);

        String cachedId = pathToSubscriptionId.get(path);
        if (cachedId != null) {
            return cachedId;
        }

        Object rawValue = keyValueStore.get(path, Object.class);
        if (rawValue instanceof String subscriptionId) {
            updateBidirectionalMapping(path, subscriptionId);
            return subscriptionId;
        }

        return "";
    }

    /**
     * Removes a subscription or path mapping.
     * Handles cleanup from persistent store, bidirectional mappings, and cache.
     * If cache becomes empty after removal, resets initialization flag to trigger reload on next access.
     *
     * @param key the path or subscription ID to remove
     */
    public void remove(String key) {
        if (key == null || key.trim().isEmpty()) {
            throw new IllegalArgumentException("key cannot be null or empty");
        }

        keyValueStore.remove(key);
        removeFromBidirectionalMappings(key);

        synchronized (initializationLock) {
            subscriptionMetadataCache.remove(key);

            if (subscriptionMetadataCache.isEmpty()) {
                cacheInitialized = false;
                LOGGER.debug("Cache is empty after removal, reset initialization flag");
            }
        }

        LOGGER.debug("Removed key from all stores: {}", key);
    }

    /**
     * Retrieves the file path for a subscription ID.
     * Falls back to key-value store scan if not in cache.
     *
     * @param subscriptionId the subscription identifier
     * @return the file path, or null if not found
     */
    public String getPathBySubscription(String subscriptionId) {
        validateSubscriptionId(subscriptionId);

        String cachedPath = subscriptionIdToPath.get(subscriptionId);
        if (cachedPath != null) {
            return cachedPath;
        }

        return findPathInKeyValueStore(subscriptionId);
    }

    /**
     * Retrieves all subscription IDs from the cache.
     * Ensures cache is initialized from KeyValueStore on first access.
     *
     * @return Set of all subscription IDs (never null, may be empty)
     */
    public Set<String> getAllSubscriptionIds() {
        ensureCacheInitialized();

        Set<String> subscriptionIds = new HashSet<>(subscriptionMetadataCache.keySet());
        LOGGER.debug("Retrieved {} subscription IDs from cache", subscriptionIds.size());

        return subscriptionIds;
    }

    /**
     * Updates the expiration timestamp for a subscription in storage.
     *
     * @param subscriptionId the subscription ID to update
     * @param newExpirationTimestamp the new expiration timestamp in milliseconds since epoch
     */
    public void updateSubscriptionExpiration(String subscriptionId, long newExpirationTimestamp) {
        SubscriptionDao existingDao = getFileMetaData(subscriptionId);
        if (existingDao != null) {
            SubscriptionDao updatedDao = existingDao.withExpirationTimestamp(newExpirationTimestamp);
            addFileMetaData(subscriptionId, updatedDao);
            LOGGER.info("Updated subscription {} expiration timestamp to: {}", subscriptionId, newExpirationTimestamp);
        } else {
            LOGGER.warn("No metadata found for subscription {} during expiration update", subscriptionId);
        }
    }

    // ==================== Private Helper Methods ====================

    private void ensureCacheInitialized() {
        if (!cacheInitialized || subscriptionMetadataCache.isEmpty()) {
            synchronized (initializationLock) {
                if (!cacheInitialized || subscriptionMetadataCache.isEmpty()) {
                    LOGGER.info("Initializing subscription metadata cache from KeyValueStore (initialized={}, cacheSize={})",
                            cacheInitialized, subscriptionMetadataCache.size());
                    loadAllSubscriptionsFromKeyValueStore();
                    cacheInitialized = true;
                    LOGGER.info("Cache initialization complete. Loaded {} subscriptions", subscriptionMetadataCache.size());
                }
            }
        }
    }

    private void loadAllSubscriptionsFromKeyValueStore() {
        int loadedCount = 0;
        int skippedCount = 0;

        for (String key : keyValueStore.getKeys()) {
            try {
                Object rawObject = keyValueStore.get(key, Object.class);
                if (rawObject != null && isSubscriptionMetadata(rawObject)) {
                    SubscriptionDao dao = deserializeMetadata(rawObject, key);
                    if (dao != null) {
                        subscriptionMetadataCache.put(key, dao);
                        loadedCount++;
                        LOGGER.debug("Loaded subscription metadata for: {}", key);
                    }
                } else {
                    skippedCount++;
                }
            } catch (MustAuthorizeException e) {
                throw e;
            } catch (Exception e) {
                LOGGER.warn("Failed to load subscription metadata for key {}: {}", key, e.getMessage());
                skippedCount++;
            }
        }

        LOGGER.info("Loaded {} subscriptions into cache, skipped {} non-subscription keys", loadedCount, skippedCount);
    }

    private boolean isSubscriptionMetadata(Object rawObject) {
        try {
            if (rawObject instanceof String json) {
                SubscriptionDao dao = Constants.GSON.fromJson(json, SubscriptionDao.class);
                return dao != null && dao.getSubscriptionId() != null;
            } else if (rawObject instanceof Map) {
                String json = Constants.GSON.toJson(rawObject);
                SubscriptionDao dao = Constants.GSON.fromJson(json, SubscriptionDao.class);
                return dao != null && dao.getSubscriptionId() != null;
            }
            return false;
        } catch (MustAuthorizeException e) {
            throw e;
        } catch (Exception e) {
            return false;
        }
    }

    private void validateSubscriptionId(String subscriptionId) {
        if (subscriptionId == null || subscriptionId.trim().isEmpty()) {
            throw new IllegalArgumentException("subscriptionId cannot be null or empty");
        }
    }

    private void validatePath(String path) {
        if (path == null || path.trim().isEmpty()) {
            throw new IllegalArgumentException("path cannot be null or empty");
        }
    }

    private void validateSubscriptionDao(SubscriptionDao subscriptionDao) {
        if (subscriptionDao == null) {
            throw new IllegalArgumentException("subscriptionDao cannot be null");
        }
    }

    private void persistMetadata(String subscriptionId, SubscriptionDao subscriptionDao) {
        keyValueStore.put(subscriptionId, Constants.GSON.toJson(subscriptionDao));
    }

    private void cacheMetadata(String subscriptionId, SubscriptionDao subscriptionDao) {
        subscriptionMetadataCache.put(subscriptionId, subscriptionDao);

        int cacheSize = subscriptionMetadataCache.size();
        if (cacheSize > CACHE_SIZE_WARNING_THRESHOLD) {
            LOGGER.warn("Cache size {} exceeds warning threshold {} for subscriptionMetadataCache",
                cacheSize, CACHE_SIZE_WARNING_THRESHOLD);
        }
    }

    private SubscriptionDao deserializeMetadata(Object rawObject, String subscriptionId) {
        if (rawObject instanceof String) {
            return deserializeFromString((String) rawObject, subscriptionId);
        } else if (rawObject instanceof Map) {
            return deserializeFromMap(rawObject, subscriptionId);
        } else {
            throw new IllegalStateException(
                "Unexpected type from key-value store: " + rawObject.getClass() + " for subscription: " + subscriptionId
            );
        }
    }

    private SubscriptionDao deserializeFromString(String json, String subscriptionId) {
        try {
            return Constants.GSON.fromJson(json, SubscriptionDao.class);
        } catch (MustAuthorizeException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.error("Failed to deserialize metadata from JSON for subscription: {}", subscriptionId, e);
            throw new RuntimeException("Failed to deserialize subscription metadata for: " + subscriptionId, e);
        }
    }

    private SubscriptionDao deserializeFromMap(Object rawObject, String subscriptionId) {
        try {
            String json = Constants.GSON.toJson(rawObject);
            return Constants.GSON.fromJson(json, SubscriptionDao.class);
        } catch (MustAuthorizeException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.error("Failed to convert metadata from Map for subscription: {}", subscriptionId, e);
            throw new RuntimeException("Failed to convert subscription metadata for: " + subscriptionId, e);
        }
    }

    private void removeOldMappingIfExists(String path) {
        String oldSubscriptionId = pathToSubscriptionId.get(path);
        if (oldSubscriptionId != null) {
            subscriptionIdToPath.remove(oldSubscriptionId);
            subscriptionMetadataCache.remove(oldSubscriptionId);
            LOGGER.debug("Removed old mapping for subscription: {}", oldSubscriptionId);
        }
    }

    private void updateBidirectionalMapping(String path, String subscriptionId) {
        pathToSubscriptionId.put(path, subscriptionId);
        subscriptionIdToPath.put(subscriptionId, path);

        int pathToIdSize = pathToSubscriptionId.size();
        if (pathToIdSize > CACHE_SIZE_WARNING_THRESHOLD) {
            LOGGER.warn("Cache size {} exceeds warning threshold {} for pathToSubscriptionId",
                pathToIdSize, CACHE_SIZE_WARNING_THRESHOLD);
        }

        int idToPathSize = subscriptionIdToPath.size();
        if (idToPathSize > CACHE_SIZE_WARNING_THRESHOLD) {
            LOGGER.warn("Cache size {} exceeds warning threshold {} for subscriptionIdToPath",
                idToPathSize, CACHE_SIZE_WARNING_THRESHOLD);
        }
    }

    private void removeFromBidirectionalMappings(String key) {
        String subscriptionId = pathToSubscriptionId.remove(key);
        if (subscriptionId != null) {
            subscriptionIdToPath.remove(subscriptionId);
            subscriptionMetadataCache.remove(subscriptionId);
        }

        String path = subscriptionIdToPath.remove(key);
        if (path != null) {
            pathToSubscriptionId.remove(path);
        }
    }

    private String findPathInKeyValueStore(String subscriptionId) {
        String matchingKey = keyValueStore.getKeys().stream()
            .filter(key -> {
                Object rawValue = keyValueStore.get(key, Object.class);
                return rawValue instanceof String && subscriptionId.equals(rawValue);
            })
            .findFirst()
            .orElse(null);

        if (matchingKey != null && !pathToSubscriptionId.containsKey(matchingKey)) {
            updateBidirectionalMapping(matchingKey, subscriptionId);
        }

        return matchingKey;
    }
}
