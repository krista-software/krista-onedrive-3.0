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
import org.jvnet.hk2.annotations.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.Set;

/**
 * Service responsible for cleaning up local storage for OneDrive subscriptions.
 *
 * <p>This service handles:
 * <ul>
 *   <li>Removal of subscription metadata from cache</li>
 *   <li>Removal of path-to-subscription mappings</li>
 *   <li>Removal of subscription-to-path mappings</li>
 *   <li>Removal of entries from KeyValueStore</li>
 * </ul>
 * </p>
 *
 * <p><b>Storage Layers:</b> OneDrive uses a three-layer storage architecture:
 * <ol>
 *   <li>KeyValueStore (persistent storage)</li>
 *   <li>In-memory cache (subscriptionMetadataCache)</li>
 *   <li>Bidirectional mappings (pathToSubscriptionId / subscriptionIdToPath)</li>
 * </ol>
 * This service ensures all three layers are cleaned up properly.</p>
 */
@Service
public class OnedriveLocalStorageCleanupService {

    private static final Logger LOGGER = LoggerFactory.getLogger(OnedriveLocalStorageCleanupService.class);

    private final SubscriptionStorageStore subscriptionStorageStore;

    @Inject
    public OnedriveLocalStorageCleanupService(SubscriptionStorageStore subscriptionStorageStore) {
        this.subscriptionStorageStore = subscriptionStorageStore;
    }

    /**
     * Cleans up all local storage for the given subscription IDs.
     *
     * <p>The method continues processing even if individual cleanups fail,
     * ensuring maximum cleanup is achieved.</p>
     *
     * @param subscriptionIds Set of subscription IDs to clean up
     * @return Number of subscriptions successfully cleaned up
     */
    public int cleanupAllSubscriptions(Set<String> subscriptionIds) {
        LOGGER.info("Starting local storage cleanup - Count: {}", subscriptionIds.size());

        if (subscriptionIds.isEmpty()) {
            return 0;
        }

        int successCount = 0;

        for (String subscriptionId : subscriptionIds) {
            try {
                cleanupSingleSubscription(subscriptionId);
                successCount++;
            } catch (MustAuthorizeException e) {
                throw e;
            } catch (Exception e) {
                LOGGER.error("Failed to clean up local storage for subscription: {}", subscriptionId, e);
            }
        }

        LOGGER.info("Local storage cleanup complete - Success: {}/{}", successCount, subscriptionIds.size());
        return successCount;
    }

    /**
     * Cleans up local storage for a single subscription.
     *
     * <p>This method:
     * <ol>
     *   <li>Retrieves the path associated with the subscription (if any)</li>
     *   <li>Removes subscription metadata from all storage layers</li>
     *   <li>Removes path mapping if it exists</li>
     * </ol>
     * </p>
     *
     * @param subscriptionId The subscription ID to clean up
     */
    private void cleanupSingleSubscription(String subscriptionId) {
        String path = subscriptionStorageStore.getPathBySubscription(subscriptionId);

        subscriptionStorageStore.remove(subscriptionId);

        if (path != null && !path.trim().isEmpty()) {
            subscriptionStorageStore.remove(path);
        }
    }
}
