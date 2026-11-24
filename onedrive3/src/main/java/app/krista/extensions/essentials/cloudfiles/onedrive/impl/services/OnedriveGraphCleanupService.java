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
import app.krista.extensions.essentials.cloudfiles.onedrive.OnedriveAttributes;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.OnedriveSubscription;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.connectors.GraphServiceClientProviderFactory;
import org.jvnet.hk2.annotations.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.Set;

/**
 * Service responsible for deleting OneDrive subscriptions from Microsoft Graph.
 *
 * <p>This service handles:
 * <ul>
 *   <li>Bulk deletion of multiple subscriptions</li>
 *   <li>Graceful error handling (continues on failures)</li>
 *   <li>Statistics tracking (success/failure counts)</li>
 * </ul>
 * </p>
 *
 * <p><b>Error Handling:</b> This service continues processing even if individual
 * deletions fail, ensuring maximum cleanup is achieved.</p>
 */
@Service
public class OnedriveGraphCleanupService {

    private static final Logger LOGGER = LoggerFactory.getLogger(OnedriveGraphCleanupService.class);

    private final GraphServiceClientProviderFactory clientProviderFactory;

    @Inject
    public OnedriveGraphCleanupService(GraphServiceClientProviderFactory clientProviderFactory) {
        this.clientProviderFactory = clientProviderFactory;
    }

    /**
     * Deletes all subscriptions from Microsoft Graph.
     *
     * @param subscriptionIds Set of subscription IDs to delete
     * @param attributes OneDrive credentials for Graph API authentication
     * @return CleanupResult containing success/failure statistics
     */
    public CleanupResult deleteAllSubscriptions(Set<String> subscriptionIds,
                                                OnedriveAttributes attributes) {
        LOGGER.info("Starting Microsoft Graph subscription deletion - Count: {}", subscriptionIds.size());

        if (subscriptionIds.isEmpty()) {
            return new CleanupResult(0, 0);
        }

        if (attributes == null) {
            LOGGER.warn("No credentials available - skipping Microsoft Graph deletion");
            return new CleanupResult(0, subscriptionIds.size());
        }

        var provider = clientProviderFactory.create(attributes);

        int successCount = 0;
        int failureCount = 0;

        for (String subscriptionId : subscriptionIds) {
            try {
                OnedriveSubscription.deleteSubscription(provider, subscriptionId);
                successCount++;
            } catch (MustAuthorizeException e) {
                throw e;
            } catch (Exception e) {
                failureCount++;
                LOGGER.error("Failed to delete subscription from Graph: {}", subscriptionId, e);
            }
        }

        LOGGER.info("Microsoft Graph deletion complete - Success: {}, Failed: {}",
                    successCount, failureCount);

        return new CleanupResult(successCount, failureCount);
    }

    /**
     * Result object containing cleanup statistics.
     */
    public static class CleanupResult {
        private final int successCount;
        private final int failureCount;

        public CleanupResult(int successCount, int failureCount) {
            this.successCount = successCount;
            this.failureCount = failureCount;
        }

        public int getSuccessCount() {
            return successCount;
        }

        public int getFailureCount() {
            return failureCount;
        }

        public int getTotalProcessed() {
            return successCount + failureCount;
        }

        public boolean hasFailures() {
            return failureCount > 0;
        }

        @Override
        public String toString() {
            return String.format("CleanupResult{success=%d, failed=%d, total=%d}",
                               successCount, failureCount, getTotalProcessed());
        }
    }
}
