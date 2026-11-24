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

package app.krista.extensions.essentials.cloudfiles.onedrive.impl.connectors;


import app.krista.extension.authorization.MustAuthorizeException;
import app.krista.extensions.essentials.cloudfiles.onedrive.OnedriveAttributes;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.stores.OnedriveAttributeStore;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.util.Constants;
import app.krista.extensions.util.KeyValueStore;
import app.krista.ksdk.context.AuthorizationContext;
import app.krista.ksdk.context.RequestContext;
import app.krista.model.field.NamedValuedField;
import org.jetbrains.annotations.NotNull;
import org.jvnet.hk2.annotations.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@Service
public class GraphServiceClientProviderFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(GraphServiceClientProviderFactory.class);
    private final KeyValueStore keyValueStore;
    private final OnedriveAttributeStore onedriveAttributeStore;
    private final RequestContext requestContext;
    private final AuthorizationContext authorizationContext;

    @Inject
    public GraphServiceClientProviderFactory(KeyValueStore keyValueStore, OnedriveAttributeStore onedriveAttributeStore,
                                             RequestContext requestContext,
                                             AuthorizationContext authorizationContext) {
        this.keyValueStore = keyValueStore;
        this.onedriveAttributeStore = onedriveAttributeStore;
        this.requestContext = requestContext;
        this.authorizationContext = authorizationContext;
    }

    public String createAttributes(OnedriveAttributes attributes) {
        LOGGER.debug("createAttributes: Saving OnedriveAttributes");
        String result = onedriveAttributeStore.save(attributes);
        LOGGER.debug("createAttributes: Attributes saved with ID: {}", result);
        return result;
    }

    /**
     * Creates a GraphServiceClientProvider directly from a supplied attributes instance.
     */
    public GraphServiceClientProvider create(OnedriveAttributes onedriveAttributes) {
        LOGGER.info("[MCP-CRED] create(OnedriveAttributes): Using supplied attrs — authType={}, clientId={}, email={}",
                onedriveAttributes != null ? onedriveAttributes.getAuthType() : "null",
                onedriveAttributes != null ? maskId(onedriveAttributes.getClientId()) : "null",
                onedriveAttributes != null ? onedriveAttributes.getEmail() : "null");
        return getGraphServiceClientProvider(onedriveAttributes, null);
    }

    @NotNull
    private GraphServiceClientProvider getGraphServiceClientProvider(OnedriveAttributes attributes,
                                                                     String authContextId) {
        return new GraphServiceClientProvider(keyValueStore, attributes, requestContext, authorizationContext, authContextId);
    }

    public GraphServiceClientProvider create(String authContextId) {
        LOGGER.info("[MCP-CRED] create(authContextId): Loading attrs for authContextId={}", authContextId);
        final OnedriveAttributes attributes = onedriveAttributeStore.load(authContextId);
        if (attributes == null) {
            LOGGER.warn("[MCP-CRED] create(authContextId): No stored attributes found for authContextId={} — triggering auth prompt", authContextId);
            List<NamedValuedField> details = new ArrayList<>();
            details.add(new NamedValuedField(Constants.USER_ID, Constants.TEXT, authContextId, new HashMap<>(), new HashMap<>()));
            details.add(new NamedValuedField(Constants.AUTH_CONTEXT_ID, Constants.TEXT, authContextId, new HashMap<>(), new HashMap<>()));
            throw new MustAuthorizeException("No credentials configured. Please authenticate via the Authentication tab.", details);
        }
        LOGGER.info("[MCP-CRED] create(authContextId): Loaded attrs — authType={}, clientId={}, email={}",
                attributes.getAuthType(), maskId(attributes.getClientId()), attributes.getEmail());
        return getGraphServiceClientProvider(attributes, authContextId);
    }

    public GraphServiceClientProvider create() {
        return create(requestContext);
    }

    /**
     * Creates a provider for the given request context.
     *
     * <p>For per-user (MCP / "invoke as user") requests the logic is:
     * <ol>
     *   <li>Resolve the wsContactId from the AuthorizationContext.</li>
     *   <li>Check whether this specific wsContact user already has a stored refresh token
     *       (saved as a plain-key alias by the OAuth callback). Setup credentials saved under
     *       the invokerId are intentionally ignored here — they belong to the admin and must
     *       not be used as per-user tokens.</li>
     *   <li>If no token exists, throw {@link MustAuthorizeException} whose first detail
     *       contains the raw wsContactId. {@code getMustAuthorizeResponse} in
     *       {@link app.krista.extensions.essentials.cloudfiles.onedrive.OnedriveRequestAuthenticator}
     *       detects the {@code wsContact} prefix and builds the correct OAuth state
     *       ({@code wsContactId#invokerId#forwardPath}).</li>
     *   <li>If the token exists, the user is already authenticated — return a provider.</li>
     * </ol>
     *
     * <p>When {@code authorizationContext} is unavailable (e.g. internal non-MCP calls), the
     * method falls back to the Setup-attrs path so non-user operations are not broken.
     */
    public GraphServiceClientProvider create(RequestContext context) {
        String key = context.getInvokerId();
        boolean isMcp = context.invokeAsUser();
        LOGGER.info("[MCP-CRED] ===== create(RequestContext) START =====");
        LOGGER.info("[MCP-CRED] invokerId={}, invokeAsUser={} (mode={})",
                key, isMcp, isMcp ? "MCP/per-user" : "Setup-tab/operator");

        OnedriveAttributes attributes = onedriveAttributeStore.load(key);
        LOGGER.info("[MCP-CRED] Setup-tab credentials for invokerId={}: {}",
                key, attributes != null
                        ? "FOUND — authType=" + attributes.getAuthType()
                                + ", clientId=" + maskId(attributes.getClientId())
                                + ", email=" + attributes.getEmail()
                        : "NOT FOUND");

        if (isMcp) {
            if (attributes != null) {
                // Setup credentials exist. Return provider immediately.
                // The per-user token check is intentionally deferred to
                // GraphServiceClientProvider.getGraphServiceClientForUser, which is called at
                // request execution time (not from an HK2 singleton constructor). Throwing MAE
                // here would wrap it inside a MultiException that the platform cannot recognise
                // as an authorisation event.
                LOGGER.info("[MCP-CRED] MODE=MCP, Setup attrs present — returning provider with Setup credentials.");
                LOGGER.info("[MCP-CRED] Credentials in use: authType={}, clientId={}, email={}",
                        attributes.getAuthType(), maskId(attributes.getClientId()), attributes.getEmail());
                LOGGER.info("[MCP-CRED] Per-user token check deferred to getGraphServiceClientForUser()");
                return getGraphServiceClientProvider(attributes, null);
            }

            // No Setup attrs at all — try to surface auth prompt with correct wsContactId so
            // getMustAuthorizeResponse builds the right per-user OAuth state.
            LOGGER.info("[MCP-CRED] MODE=MCP, No Setup attrs — will use Krista default Public credentials (config.properties) for OAuth link");
            try {
                String wsContactId = authorizationContext.getAuthorizedAccount().getAccountId();
                LOGGER.info("[MCP-CRED] Resolved wsContactId={} — throwing MustAuthorizeException to trigger OAuth link", wsContactId);
                List<NamedValuedField> details = new ArrayList<>();
                details.add(new NamedValuedField(Constants.CLIENT_USER_ID, Constants.TEXT, wsContactId,
                        new HashMap<>(), new HashMap<>()));
                details.add(new NamedValuedField(Constants.USER_ID, Constants.TEXT, key,
                        new HashMap<>(), new HashMap<>()));
                throw new MustAuthorizeException(
                        "No credentials configured. Please authenticate via the Authentication tab.", details);
            } catch (MustAuthorizeException e) {
                throw e;
            } catch (Exception e) {
                LOGGER.warn("[MCP-CRED] Could not resolve wsContactId ({}); falling back to single-detail MAE",
                        e.getMessage());
            }
        }

        if (attributes != null) {
            LOGGER.info("[MCP-CRED] MODE=Setup-tab — returning provider with operator credentials.");
            LOGGER.info("[MCP-CRED] Credentials in use: authType={}, clientId={}, email={}",
                    attributes.getAuthType(), maskId(attributes.getClientId()), attributes.getEmail());
            return getGraphServiceClientProvider(attributes, null);
        }

        LOGGER.warn("[MCP-CRED] MODE=Setup-tab — No stored credentials for invokerId={} — triggering auth prompt", key);
        List<NamedValuedField> details = new ArrayList<>();
        details.add(new NamedValuedField(Constants.USER_ID, Constants.TEXT, key, new HashMap<>(), new HashMap<>()));
        throw new MustAuthorizeException(
                "No credentials configured. Please authenticate via the Authentication tab.", details);
    }

    /** Masks all but the first 6 chars of a credential ID for safe logging. */
    private static String maskId(String id) {
        if (id == null) return "null";
        return id.length() <= 6 ? "***" : id.substring(0, 6) + "***";
    }

}
