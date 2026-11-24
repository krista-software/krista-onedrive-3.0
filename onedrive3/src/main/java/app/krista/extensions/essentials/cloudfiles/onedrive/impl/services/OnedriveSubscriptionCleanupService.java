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
import app.krista.extension.executor.Invoker;
import app.krista.extensions.essentials.cloudfiles.onedrive.OnedriveAttributes;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.stores.OnedriveAttributeStore;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.util.Constants;
import app.krista.extensions.util.KeyValueStore;
import org.jvnet.hk2.annotations.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.Set;

/**
 * Service responsible for orchestrating complete OneDrive subscription cleanup.
 *
 * <p>This service coordinates the cleanup process when:
 * <ul>
 *   <li>An invoker is removed (INVOKER_REMOVED)</li>
 *   <li>Credentials are changed (email change detection)</li>
 * </ul>
 * </p>
 *
 * <p><b>Cleanup Process:</b>
 * <ol>
 *   <li>Discover all subscriptions for the invoker</li>
 *   <li>Delete subscriptions from Microsoft Graph</li>
 *   <li>Clean up local storage (KeyValueStore + cache)</li>
 *   <li>Remove stored credentials</li>
 * </ol>
 * </p>
 *
 * <p><b>Error Handling:</b> This service will NOT throw exceptions or block invoker removal.
 * Partial cleanup is considered acceptable to ensure invoker removal completes successfully.</p>
 */
@Service
public class OnedriveSubscriptionCleanupService {

    private static final Logger LOGGER = LoggerFactory.getLogger(OnedriveSubscriptionCleanupService.class);

    private final OnedriveSubscriptionDiscoveryService discoveryService;
    private final OnedriveGraphCleanupService graphCleanupService;
    private final OnedriveLocalStorageCleanupService localStorageCleanupService;
    private final OnedriveAttributeStore attributeStore;
    private final KeyValueStore keyValueStore;
    private final Invoker invoker;

    @Inject
    public OnedriveSubscriptionCleanupService(
            OnedriveSubscriptionDiscoveryService discoveryService,
            OnedriveGraphCleanupService graphCleanupService,
            OnedriveLocalStorageCleanupService localStorageCleanupService,
            OnedriveAttributeStore attributeStore,
            KeyValueStore keyValueStore,
            @Named("self") Invoker invoker) {
        this.discoveryService = discoveryService;
        this.graphCleanupService = graphCleanupService;
        this.localStorageCleanupService = localStorageCleanupService;
        this.attributeStore = attributeStore;
        this.keyValueStore = keyValueStore;
        this.invoker = invoker;
    }

    /**
     * Handles subscription cleanup when credentials are updated.
     *
     * <p>This method:
     * <ul>
     *   <li>Takes snapshot of current subscriptions BEFORE any changes</li>
     *   <li>Checks if the email address has changed</li>
     *   <li>Deletes old subscriptions if email changed</li>
     * </ul>
     * </p>
     *
     * <p><b>Important:</b> This method takes a snapshot of subscriptions BEFORE credential changes
     * to avoid race conditions where new subscriptions might be created during the credential change process.</p>
     *
     * @param newAttributes The new credentials being saved
     */
    public void handleCredentialChange(OnedriveAttributes newAttributes) {
        LOGGER.info("========== PROCESSING CREDENTIAL CHANGE ==========");

        try {
            OnedriveAttributes oldAttributes = attributeStore.load(invoker.getInvokerId());

            LOGGER.info("New Email: {}, Old Email: {}",
                    newAttributes.getEmail(),
                    oldAttributes != null ? oldAttributes.getEmail() : "NONE (First time setup)");

            if (hasEmailChanged(oldAttributes, newAttributes)) {
                LOGGER.info("Email changed detected - taking snapshot of subscriptions before cleanup");

                Set<String> subscriptionsToDelete = discoveryService.discoverAllSubscriptionIds();
                LOGGER.info("Snapshot taken - {} subscriptions will be deleted", subscriptionsToDelete.size());

                deleteOldSubscriptions(oldAttributes, subscriptionsToDelete);
            }

            LOGGER.info("========== CREDENTIAL CHANGE PROCESSING COMPLETE ==========");

        } catch (Exception e) {
            LOGGER.error("Error during credential change cleanup - continuing with save", e);
        }
    }

    /**
     * Handles complete cleanup when an invoker is removed.
     *
     * <p><b>Important:</b> This method will NOT throw exceptions or block invoker removal.
     * Partial cleanup is considered acceptable to ensure invoker removal completes.</p>
     */
    public void handleInvokerRemoval() {
        LOGGER.info("========== ONEDRIVE INVOKER REMOVAL STARTED ==========");
        LOGGER.info("Invoker ID: {}", invoker.getInvokerId());

        try {
            // Phase 1: Discover all subscriptions
            Set<String> subscriptionIds = discoveryService.discoverAllSubscriptionIds();
            LOGGER.info("Discovered {} subscriptions to clean up", subscriptionIds.size());

            if (subscriptionIds.isEmpty()) {
                removeCredentials();
                LOGGER.info("========== ONEDRIVE INVOKER REMOVAL COMPLETED ==========");
                return;
            }

            // Phase 2: Load credentials for Graph API operations
            OnedriveAttributes attributes = loadCredentials();

            // Phase 3: Delete from Microsoft Graph
            OnedriveGraphCleanupService.CleanupResult graphResult =
                graphCleanupService.deleteAllSubscriptions(subscriptionIds, attributes);

            logGraphCleanupResult(graphResult);

            // Phase 4: Clean up local storage
            localStorageCleanupService.cleanupAllSubscriptions(subscriptionIds);

            // Phase 5: Remove credentials
            removeCredentials();

            LOGGER.info("========== ONEDRIVE INVOKER REMOVAL COMPLETED ==========");

        } catch (MustAuthorizeException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.error("Error during OneDrive invoker removal - Partial cleanup may have occurred", e);
            LOGGER.info("========== ONEDRIVE INVOKER REMOVAL COMPLETED WITH ERRORS ==========");
        }
    }

    private OnedriveAttributes loadCredentials() {
        try {
            OnedriveAttributes attributes = attributeStore.load(invoker.getInvokerId());
            if (attributes == null) {
                LOGGER.warn("No credentials found for invoker: {}", invoker.getInvokerId());
            }
            return attributes;
        } catch (MustAuthorizeException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.error("Error loading credentials for invoker: {}", invoker.getInvokerId(), e);
            return null;
        }
    }

    private void removeCredentials() {
        try {
            attributeStore.remove(invoker.getInvokerId());
        } catch (MustAuthorizeException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.error("Error removing credentials for invoker: {}", invoker.getInvokerId(), e);
        }
    }

    private void logGraphCleanupResult(OnedriveGraphCleanupService.CleanupResult result) {
        if (result.hasFailures()) {
            LOGGER.warn("Microsoft Graph cleanup completed with failures - Success: {}, Failed: {}",
                       result.getSuccessCount(), result.getFailureCount());
        }
    }

    private boolean hasEmailChanged(OnedriveAttributes oldAttributes, OnedriveAttributes newAttributes) {
        if (oldAttributes == null) {
            return false;
        }

        String oldEmail = oldAttributes.getEmail();
        String newEmail = newAttributes.getEmail();

        if (oldEmail == null || newEmail == null) {
            return false;
        }

        return !oldEmail.equalsIgnoreCase(newEmail);
    }

    /**
     * Deletes all subscriptions for the old credentials using a pre-captured snapshot.
     *
     * <p>This method performs three cleanup operations:
     * <ol>
     *   <li>Removes refresh token for the old email address</li>
     *   <li>Deletes subscriptions from Microsoft Graph API</li>
     *   <li>Cleans up local storage (KeyValueStore + cache)</li>
     * </ol>
     * </p>
     *
     * @param oldAttributes Old credentials to clean up
     * @param subscriptionIds Pre-captured snapshot of subscription IDs to delete
     */
    private void deleteOldSubscriptions(OnedriveAttributes oldAttributes, Set<String> subscriptionIds) {
        try {
            // Phase 1: ALWAYS clean up refresh token when email changes
            cleanupRefreshToken(oldAttributes);

            if (subscriptionIds.isEmpty()) {
                LOGGER.info("No subscriptions to delete - refresh token cleanup completed");
                return;
            }

            LOGGER.info("Deleting {} subscriptions for old email: {}",
                       subscriptionIds.size(), oldAttributes.getEmail());

            // Phase 2: Delete from Microsoft Graph
            graphCleanupService.deleteAllSubscriptions(subscriptionIds, oldAttributes);

            // Phase 3: Clean up local storage
            localStorageCleanupService.cleanupAllSubscriptions(subscriptionIds);

        } catch (Exception e) {
            LOGGER.error("Error deleting old subscriptions - continuing with credential save", e);
        }
    }

    /**
     * Removes the refresh token associated with old credentials.
     *
     * <p>The refresh token key format must match the format used when storing:
     * <code>email_clientId_authType</code></p>
     *
     * @param oldAttributes Old credentials containing the email to clean up
     */
    private void cleanupRefreshToken(OnedriveAttributes oldAttributes) {
        try {
            if (oldAttributes == null || oldAttributes.getEmail() == null) {
                LOGGER.warn("Cannot cleanup refresh token - old attributes or email is null");
                return;
            }

            String oldEmail = oldAttributes.getEmail();
            String clientId = oldAttributes.getClientId();
            String authType = oldAttributes.getAuthType();

            String refreshTokenKey = oldEmail + Constants.UNDER_SCORE + clientId + Constants.UNDER_SCORE + authType;

            keyValueStore.remove(refreshTokenKey);
            LOGGER.info("Removed refresh token for old email: {} (key: {})", oldEmail, refreshTokenKey);

        } catch (Exception e) {
            LOGGER.error("Error removing refresh token for old email", e);
        }
    }
}
