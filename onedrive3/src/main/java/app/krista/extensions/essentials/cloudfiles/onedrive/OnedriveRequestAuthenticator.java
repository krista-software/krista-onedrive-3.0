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

package app.krista.extensions.essentials.cloudfiles.onedrive;

import app.krista.extension.authorization.MustAuthorizeException;
import app.krista.extension.authorization.RequestAuthenticator.AuthorizationResponse;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.stores.OnedriveAttributeStore;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.util.Constants;
import app.krista.model.field.NamedValuedField;
import app.krista.sdk.msauth.MicrosoftAuthSdk;
import app.krista.sdk.msauth.extension.base.AbstractRequestAuthenticator;
import app.krista.sdk.msauth.extension.base.MsAuthAttributes;
import app.krista.sdk.msauth.token.ITokenStore;
import app.krista.sdk.msauth.token.InMemoryTokenStore;
import org.jvnet.hk2.annotations.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.Collections;
import java.util.List;

@Service
public class OnedriveRequestAuthenticator extends AbstractRequestAuthenticator {

    private static final Logger LOGGER = LoggerFactory.getLogger(OnedriveRequestAuthenticator.class);
    private final OnedriveAttributeStore onedriveAttributeStore;

    @Inject
    public OnedriveRequestAuthenticator(OnedriveAttributeStore onedriveAttributeStore, String invokerId,
                                         String routingUrl) {
        super(invokerId, routingUrl, "/onedrive");
        this.onedriveAttributeStore = onedriveAttributeStore;
    }

    /**
     * Overrides the SDK's default to handle the wsContact bypass:
     * when the userId starts with "wsContact", the attribute store is bypassed
     * entirely and a default PUBLIC configuration is used instead.
     */
    @Override
    public AuthorizationResponse getMustAuthorizeResponse(MustAuthorizeException cause) {
        List<NamedValuedField> details = cause.getDetails();
        if (details == null || details.isEmpty()) {
            LOGGER.error("getMustAuthorizeResponse: FAILURE — MustAuthorizeException has no details. Cannot build auth URL. cause={}",
                    cause.getMessage());
            throw new IllegalStateException("MustAuthorizeException has no details");
        }

        String fieldName = details.get(0).getName();
        String userId = (String) details.get(0).getValue();
        LOGGER.info("getMustAuthorizeResponse: ===== START =====");
        LOGGER.info("getMustAuthorizeResponse: Handling MAE — fieldName={}, userId={}, detailCount={}, causeMsg={}",
                fieldName, userId, details.size(), cause.getMessage());
        if (details.size() > 1) {
            for (int i = 1; i < details.size(); i++) {
                LOGGER.info("getMustAuthorizeResponse: detail[{}] name={}, value={}",
                        i, details.get(i).getName(), details.get(i).getValue());
            }
        }

        if (userId != null && userId.startsWith(Constants.WS_CONTACT)) {
            // MCP per-user OAuth link always uses the Krista default public credentials from
            // config.properties — NOT the operator's own Azure app. The Krista default app has
            // the correct redirect URIs and pre-configured consent for the MCP flow.
            String invokerId = details.size() > 1 ? (String) details.get(1).getValue() : null;
            LOGGER.info("getMustAuthorizeResponse: Per-user (wsContact) path — wsContactId={}, invokerId={}",
                    userId, invokerId);

            MsAuthAttributes attrs = createDefaultAttributes(getRoutingUrl());
            LOGGER.info("getMustAuthorizeResponse: Using Krista default PUBLIC credentials from config.properties — authType={}, clientId={}",
                    attrs.getAuthType(),
                    attrs.getClientId() != null
                            ? attrs.getClientId().substring(0, Math.min(6, attrs.getClientId().length())) + "***"
                            : "null (loaded from config.properties)");
            LOGGER.info("getMustAuthorizeResponse: Effective attrs — authType={}, callbackEndpoint={}",
                    attrs.getAuthType(), attrs.getCallbackEndPoint());
            MicrosoftAuthSdk sdk = attrs.toSdk(getTokenStore());
            // State format: wsContactId#invokerId#forwardPath
            // The callback parses parts[1] as the invokerId and saves enriched attrs under it.
            String state = userId + "#" + getInvokerId();
            if ("Public".equals(attrs.getAuthType())) {
                state += "#" + attrs.getForwardPath();
            }
            LOGGER.info("getMustAuthorizeResponse: State built — state={}", state);
            String url;
            try {
                url = sdk.buildAuthorizationUrl(state);
            } catch (Exception e) {
                LOGGER.error("getMustAuthorizeResponse: FAILURE — buildAuthorizationUrl threw exception for wsContactId={} — {}: {}",
                        userId, e.getClass().getSimpleName(), e.getMessage(), e);
                throw e;
            }
            if (url == null || url.isBlank()) {
                LOGGER.error("getMustAuthorizeResponse: FAILURE — buildAuthorizationUrl returned blank URL for wsContactId={}", userId);
            } else {
                LOGGER.info("getMustAuthorizeResponse: SUCCESS — OAuth URL generated for wsContactId={} (starts with: {})",
                        userId, url.substring(0, Math.min(80, url.length())));
            }
            return new AuthorizationResponse(url, Collections.emptyList());
        }

        LOGGER.info("getMustAuthorizeResponse: Non-wsContact path — userId={} — delegating to super.getMustAuthorizeResponse", userId);
        MsAuthAttributes loaded = null;
        try {
            loaded = loadAttributes(userId);
        } catch (Exception e) {
            LOGGER.error("getMustAuthorizeResponse: FAILURE — loadAttributes threw exception for userId={} — {}: {}",
                    userId, e.getClass().getSimpleName(), e.getMessage(), e);
        }
        LOGGER.info("getMustAuthorizeResponse: loadAttributes(userId={}) → {}",
                userId, loaded != null ? "FOUND authType=" + loaded.getAuthType() : "NULL (will use default PUBLIC attrs)");
        AuthorizationResponse response;
        try {
            response = super.getMustAuthorizeResponse(cause);
        } catch (Exception e) {
            LOGGER.error("getMustAuthorizeResponse: FAILURE — super.getMustAuthorizeResponse threw exception for userId={} — {}: {}",
                    userId, e.getClass().getSimpleName(), e.getMessage(), e);
            throw e;
        }
        if (response == null || response.getLocation() == null || response.getLocation().isBlank()) {
            LOGGER.error("getMustAuthorizeResponse: FAILURE — super returned null/blank URL for userId={}", userId);
        } else {
            LOGGER.info("getMustAuthorizeResponse: SUCCESS — super returned URL for userId={} (starts with: {})",
                    userId, response.getLocation().substring(0, Math.min(80, response.getLocation().length())));
        }
        return response;
    }

    @Override
    protected MsAuthAttributes loadAttributes(String key) {
        return onedriveAttributeStore.load(key);
    }

    @Override
    protected ITokenStore getTokenStore() {
        return new InMemoryTokenStore();
    }

    @Override
    protected MsAuthAttributes createDefaultAttributes(String routingUrl) {
        return new OnedriveAttributes(routingUrl, null, null, "", null, Constants.PUBLIC);
    }
}
