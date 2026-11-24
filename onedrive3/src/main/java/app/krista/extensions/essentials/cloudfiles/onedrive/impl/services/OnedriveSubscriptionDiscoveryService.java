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

import app.krista.extension.authorization.MustAuthorizeException;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.stores.SubscriptionStorageStore;
import app.krista.extensions.util.KeyValueStore;
import org.jvnet.hk2.annotations.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Service responsible for discovering all OneDrive subscriptions associated with an invoker.
 *
 * <p>This service uses a hybrid approach to ensure complete subscription discovery:
 * <ul>
 *   <li>First checks in-memory cache for fast access</li>
 *   <li>Then scans KeyValueStore to catch any subscriptions not in cache</li>
 *   <li>Validates subscription IDs using UUID pattern matching</li>
 * </ul>
 * </p>
 *
 * <p><b>Thread Safety:</b> This service is thread-safe as it uses thread-safe underlying stores.</p>
 */
@Service
public class OnedriveSubscriptionDiscoveryService {

    private static final Logger LOGGER = LoggerFactory.getLogger(OnedriveSubscriptionDiscoveryService.class);

    private final SubscriptionStorageStore subscriptionStorageStore;
    private final KeyValueStore keyValueStore;

    @Inject
    public OnedriveSubscriptionDiscoveryService(SubscriptionStorageStore subscriptionStorageStore,
                                                 KeyValueStore keyValueStore) {
        this.subscriptionStorageStore = subscriptionStorageStore;
        this.keyValueStore = keyValueStore;
    }

    /**
     * Discovers all subscription IDs for the current invoker using a hybrid approach.
     *
     * <p>This method combines two discovery strategies:
     * <ol>
     *   <li>Fast cache lookup - retrieves subscriptions from in-memory cache</li>
     *   <li>Complete KeyValueStore scan - finds any subscriptions not in cache</li>
     * </ol>
     * </p>
     *
     * @return Set of all subscription IDs found in cache and persistent storage (never null)
     */
    public Set<String> discoverAllSubscriptionIds() {
        Set<String> subscriptionIds = new HashSet<>();

        // Phase 1: Get from in-memory cache (fast)
        discoverFromCache(subscriptionIds);

        // Phase 2: Scan KeyValueStore for any missed subscriptions (complete)
        discoverFromKeyValueStore(subscriptionIds);

        LOGGER.info("Subscription discovery complete - Total found: {}", subscriptionIds.size());
        return subscriptionIds;
    }

    private int discoverFromCache(Set<String> subscriptionIds) {
        try {
            Set<String> cacheKeys = subscriptionStorageStore.getAllSubscriptionIds();
            int initialSize = subscriptionIds.size();
            subscriptionIds.addAll(cacheKeys);
            return subscriptionIds.size() - initialSize;
        } catch (MustAuthorizeException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.warn("Error discovering subscriptions from cache", e);
            return 0;
        }
    }

    private int discoverFromKeyValueStore(Set<String> subscriptionIds) {
        try {
            Set<String> allKeys = keyValueStore.getKeys();
            int initialSize = subscriptionIds.size();

            for (String key : allKeys) {
                if (isSubscriptionId(key)) {
                    subscriptionIds.add(key);
                }
            }

            return subscriptionIds.size() - initialSize;
        } catch (MustAuthorizeException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.warn("Error discovering subscriptions from KeyValueStore", e);
            return 0;
        }
    }

    /**
     * Validates if a key is a subscription ID using UUID pattern matching.
     * Microsoft Graph subscription IDs follow the UUID format.
     *
     * @param key The key to validate
     * @return true if the key is a valid subscription ID format
     */
    private boolean isSubscriptionId(String key) {
        if (key == null || key.trim().isEmpty()) {
            return false;
        }

        try {
            UUID.fromString(key);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
