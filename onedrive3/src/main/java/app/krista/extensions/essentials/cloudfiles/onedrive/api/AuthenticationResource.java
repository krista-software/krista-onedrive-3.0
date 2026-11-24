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

package app.krista.extensions.essentials.cloudfiles.onedrive.api;


import app.krista.extension.authorization.MustAuthorizeException;
import app.krista.extension.executor.Invoker;
import app.krista.extension.request.RoutingInfo;
import app.krista.extension.request.protos.http.HttpProtocol;
import app.krista.extensions.essentials.cloudfiles.onedrive.OnedriveAttributes;
import app.krista.extensions.essentials.cloudfiles.onedrive.OnedriveSetUpService;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.FileChangeInfoHandler;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.connectors.GraphServiceClientProvider;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.connectors.GraphServiceClientProviderFactory;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.services.OnedriveSubscriptionCleanupService;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.stores.OnedriveAttributeStore;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.util.AuthenticationResponse;
import app.krista.extensions.util.KeyValueStore;
import app.krista.sdk.msauth.MicrosoftAuthSdk;
import app.krista.sdk.msauth.exception.MsAuthException;
import app.krista.sdk.msauth.token.ITokenStore;
import app.krista.sdk.msauth.token.InMemoryTokenStore;
import app.krista.extensions.essentials.cloudfiles.onedrive.audit.AuditEventType;
import app.krista.extensions.essentials.cloudfiles.onedrive.audit.OneDriveAuditService;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.util.Constants;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.util.Notification;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.util.NotificationProcessQueue;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.util.ResponseUtil;
import app.krista.extensions.util.EventHandler;
import app.krista.ksdk.accounts.Account;
import app.krista.ksdk.authentication.AuthorizationListener;
import app.krista.ksdk.context.AuthorizationContext;
import app.krista.model.base.FreeForm;
import com.google.gson.JsonObject;
import com.microsoft.graph.http.GraphServiceException;
import app.krista.sdk.msauth.token.TokenResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Path("/")
public final class AuthenticationResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthenticationResource.class);
    private final OnedriveAttributeStore onedriveAttributeStore;
    private final KeyValueStore keyValueStore;
    private final NotificationProcessQueue notificationProcessQueue;
    private final GraphServiceClientProviderFactory providerFactory;
    private final OnedriveSubscriptionCleanupService cleanupService;
    private final OnedriveSetUpService onedriveSetUpService;
    private final AuthorizationContext context;
    private final AuthorizationListener authorizationListener;
    private final EventHandler eventHandler;
    private final FileChangeInfoHandler fileChangeInfoHandler;
    private final OneDriveAuditService auditService;
    private final String baseRoutingUrl;
    private final String invokerId;
    private static final List<Map<String, Object>> fileStore = new ArrayList<>(1000);
    private static final int FILE_STORE_LIMIT = 1000;

    @Inject
    public AuthenticationResource(OnedriveAttributeStore onedriveAttributeStore, KeyValueStore keyValueStore,
                                  GraphServiceClientProviderFactory providerFactory, AuthorizationContext context,
                                  EventHandler eventHandler,
                                  Invoker invoker,
                                  AuthorizationListener authorizationListener, FileChangeInfoHandler fileChangeInfoHandler,
                                  OnedriveSubscriptionCleanupService cleanupService,
                                  OnedriveSetUpService onedriveSetUpService,
                                  OneDriveAuditService auditService) {
        this.onedriveAttributeStore = onedriveAttributeStore;
        this.keyValueStore = keyValueStore;
        this.eventHandler = eventHandler;
        this.providerFactory = providerFactory;
        this.cleanupService = cleanupService;
        this.onedriveSetUpService = onedriveSetUpService;
        this.context = context;
        this.authorizationListener = authorizationListener;
        this.fileChangeInfoHandler = fileChangeInfoHandler;
        this.auditService = auditService;
        this.notificationProcessQueue = new NotificationProcessQueue(providerFactory, invoker);
        this.baseRoutingUrl = invoker.getRoutingInfo().getRoutingURL(HttpProtocol.PROTOCOL_NAME, RoutingInfo.Type.APPLIANCE);
        this.invokerId = invoker.getInvokerId();
    }

    @GET
    @Path("/callback")
    public String getCallBack(@QueryParam(Constants.CODE) String code, @QueryParam(Constants.STATE) String state) {
        return getAuthenticationResponseMessage(code, state);
    }

    @GET
    @Path("v3/oauth/callback")
    public String getCallbackForV3Auth(@QueryParam(Constants.CODE) String code, @QueryParam(Constants.STATE) String state) {
        return getAuthenticationResponseMessage(code, state);
    }

    private String getAuthenticationResponseMessage(String code, String state) {
        LOGGER.info("getAuthenticationResponseMessage: ===== CALLBACK START =====");
        LOGGER.info("getAuthenticationResponseMessage: OAuth callback received — code present: {}, state: {}",
                code != null, state);

        if (code == null) {
            LOGGER.error("getAuthenticationResponseMessage: FAILURE — No authorization code in callback. " +
                    "User may have denied consent or Microsoft returned an error. state={}", state);
            return "Authentication Failed. Please re-authorize.";
        }

        String[] parts = state.split(Constants.HASH);
        if (parts[0].isBlank()) {
            LOGGER.error("getAuthenticationResponseMessage: FAILURE — State parameter is invalid or empty. state={}", state);
            throw new BadRequestException(Constants.INVALID_STATE_PARAMETERS);
        }

        String key = parts[0];
        String authContextId = getAuthContextId(state);
        LOGGER.info("getAuthenticationResponseMessage: Parsed state — key={}, parts.length={}, authContextId={}",
                key, parts.length, authContextId);

        // For MCP state (wsContact#INV_xxx#forwardPath), parts[1] is the invokerId.
        // After successful auth we save enriched attrs with the user's email under the
        // invokerId so that subsequent per-user calls can find the credentials and
        // avoid prompting the user to authenticate again ("auth loop prevention").
        String clientKey = null;
        if (parts.length >= 3 && key.startsWith(Constants.WS_CONTACT)) {
            clientKey = parts[1];
            LOGGER.info("getAuthenticationResponseMessage: MCP callback detected — wsContactId={}, invokerId={}",
                    key, clientKey);
        } else {
            LOGGER.info("getAuthenticationResponseMessage: Standard (non-MCP) callback — key={}", key);
        }

        try {
            LOGGER.info("getAuthenticationResponseMessage: Loading provider — authContextId={}, key={}", authContextId, key);
            GraphServiceClientProvider clientProvider = getGraphServiceClientProvider(authContextId, key);
            OnedriveAttributes attributes = clientProvider.getOnedriveAttributes();
            LOGGER.info("getAuthenticationResponseMessage: Provider loaded — authType={}, clientId={}",
                    attributes != null ? attributes.getAuthType() : "null",
                    attributes != null && attributes.getClientId() != null
                            ? attributes.getClientId().substring(0, Math.min(6, attributes.getClientId().length())) + "***"
                            : "null");

            ITokenStore tokenStore = new ITokenStore() {
                @Override public void save(String k, String v) { keyValueStore.put(k, v); }
                @Override public String get(String k) {
                    Object raw = keyValueStore.get(k, Object.class);
                    return raw != null ? raw.toString() : null;
                }
                @Override public void delete(String k) { keyValueStore.remove(k); }
            };
            String tokenKey = buildCallbackTokenKey(key, attributes);
            LOGGER.info("getAuthenticationResponseMessage: Exchanging auth code for tokens — tokenKey={}", tokenKey);
            TokenResult tokenResult;
            try {
                tokenResult = attributes.toSdk(tokenStore).handleCallback(code, tokenKey);
                LOGGER.info("getAuthenticationResponseMessage: Token exchange SUCCESS — accessToken present: {}, refreshToken present: {}",
                        tokenResult != null && tokenResult.getAccessToken() != null,
                        tokenResult != null && tokenResult.getRefreshToken() != null);
            } catch (Exception e) {
                LOGGER.error("getAuthenticationResponseMessage: FAILURE — handleCallback threw exception — {}: {}",
                        e.getClass().getSimpleName(), e.getMessage(), e);
                throw e;
            }
            String refreshToken = resolveRefreshToken(tokenStore, tokenKey, tokenResult);
            if (refreshToken == null) {
                LOGGER.error("getAuthenticationResponseMessage: FAILURE — No refresh token received or stored for tokenKey={}. " +
                        "Check that the OAuth app has 'offline_access' scope granted.", tokenKey);
                return Constants.REFRESH_TOKEN_NOT_RECEIVED;
            }
            LOGGER.info("getAuthenticationResponseMessage: Refresh token resolved for tokenKey={}", tokenKey);

            if (!key.startsWith(Constants.WS_CONTACT)) {
                LOGGER.info("getAuthenticationResponseMessage: Non-MCP path — verifying user drive access for key={}", key);
                boolean hasAccess = hasUserAccess(clientProvider);
                LOGGER.info("getAuthenticationResponseMessage: hasUserAccess={} for key={}", hasAccess, key);
                if (!hasAccess) {
                    tokenStore.delete(tokenKey);
                    LOGGER.warn("getAuthenticationResponseMessage: FAILURE — User access denied. " +
                            "Email '{}' does not match authenticated user. Token deleted.", key);
                    return Constants.UNAUTHORISED_USER + " User email '" + key + "' configured in the setup does not match with authenticated user.";
                }
            }

            LOGGER.info("getAuthenticationResponseMessage: Storing refresh token aliases for key={}", key);
            storeRefreshTokenAliases(tokenStore, key, attributes, refreshToken);
            LOGGER.info("getAuthenticationResponseMessage: Token aliases stored — plain key={} (wsContact plain alias also saved if applicable)", key);

            // MCP path: persist attrs with the authenticated email under the invokerId so the
            // factory can load them on subsequent per-user calls. Carry over the clientId/secret
            // from the attrs used for this token exchange so that future refreshes use the same
            // Azure app (same scopes, no Outlook-scope bleed from the shared Krista public app).
            if (clientKey != null && clientKey.startsWith(Constants.INVOKER_PREFIX)) {
                String userEmail = extractEmailFromAccessToken(tokenResult != null ? tokenResult.getAccessToken() : null);
                LOGGER.info("getAuthenticationResponseMessage: MCP path — extracted userEmail={} from access token", userEmail);
                if (userEmail == null) {
                    LOGGER.warn("getAuthenticationResponseMessage: WARNING — Could not extract email from access token. " +
                            "Enriched attrs saved with null email under invokerId={}.", clientKey);
                }
                String effectiveAuthType = attributes.getAuthType() != null ? attributes.getAuthType() : Constants.PUBLIC;
                LOGGER.info("getAuthenticationResponseMessage: Saving enriched attrs — invokerId={}, email={}, authType={}, clientId={}",
                        clientKey, userEmail, effectiveAuthType,
                        attributes.getClientId() != null
                                ? attributes.getClientId().substring(0, Math.min(6, attributes.getClientId().length())) + "***"
                                : "null (will use config.properties)");
                OnedriveAttributes enrichedAttrs = new OnedriveAttributes(
                        baseRoutingUrl,
                        attributes.getClientId(),
                        attributes.getClientSecret(),
                        userEmail,
                        attributes.getTenantId(),
                        effectiveAuthType);
                onedriveAttributeStore.save(enrichedAttrs, clientKey);
                LOGGER.info("getAuthenticationResponseMessage: MCP attrs saved — wsContactId={} is now authenticated. " +
                        "Next request will find token and proceed without prompting.", key);
            } else if (clientKey != null) {
                LOGGER.warn("getAuthenticationResponseMessage: clientKey={} does not start with INVOKER_PREFIX='{}'. " +
                        "MCP enriched attrs will NOT be saved — subsequent requests may re-prompt for auth.",
                        clientKey, Constants.INVOKER_PREFIX);
            }

            boolean isAuthenticated = context.isAuthenticated();
            LOGGER.info("getAuthenticationResponseMessage: context.isAuthenticated()={}", isAuthenticated);
            if (isAuthenticated) {
                authorizationListener.authorized();
                LOGGER.info("getAuthenticationResponseMessage: SUCCESS — User authenticated, authorizationListener.authorized() called.");
                return Constants.USER_AUTHENTICATED_SUCCESSFULLY_PLEASE_PROCEED_WITH_REQUEST;
            }
            LOGGER.info("getAuthenticationResponseMessage: SUCCESS — User authenticated. UI should prompt to save changes.");
            return Constants.USER_AUTHENTICATED_SUCCESSFULLY_SAVE_THE_CHANGES;
        } catch (MsAuthException cause) {
            LOGGER.error("getAuthenticationResponseMessage: FAILURE — MsAuthException during token exchange — {}: {}",
                    cause.getClass().getSimpleName(), cause.getMessage(), cause);
            throw new IllegalStateException(Constants.ERROR_OCCURRED_DURING_AUTHORIZATION, cause);
        }
    }

    /**
     * Extracts the user's email / UPN from a raw JWT access token without signature verification.
     * Returns {@code null} if the token cannot be decoded or no email claim is present.
     */
    private String extractEmailFromAccessToken(String token) {
        if (token == null || token.isBlank()) return null;
        try {
            String[] jwtParts = token.split("\\.");
            if (jwtParts.length >= 2) {
                int mod = jwtParts[1].length() % 4;
                String padded = mod > 0 ? jwtParts[1] + "=".repeat(4 - mod) : jwtParts[1];
                byte[] decoded = java.util.Base64.getUrlDecoder().decode(padded);
                java.util.Map<?, ?> claims = Constants.GSON.fromJson(new String(decoded), java.util.Map.class);
                if (claims.containsKey("preferred_username")) return (String) claims.get("preferred_username");
                if (claims.containsKey("upn"))               return (String) claims.get("upn");
                if (claims.containsKey("email"))             return (String) claims.get("email");
            }
        } catch (Exception e) {
            LOGGER.warn("extractEmailFromAccessToken: could not decode JWT payload — {}", e.getMessage());
        }
        return null;
    }

    String buildCallbackTokenKey(String key, OnedriveAttributes attributes) {
        return MicrosoftAuthSdk.buildTokenKey(key, attributes.getClientId(), attributes.getAuthType());
    }

    String resolveRefreshToken(ITokenStore tokenStore, String tokenKey, TokenResult tokenResult) {
        if (tokenResult != null && tokenResult.getRefreshToken() != null && !tokenResult.getRefreshToken().isBlank()) {
            return tokenResult.getRefreshToken();
        }
        String storedToken = tokenStore.get(tokenKey);
        return storedToken == null || storedToken.isBlank() ? null : storedToken;
    }

    void storeRefreshTokenAliases(ITokenStore tokenStore, String key, OnedriveAttributes attributes, String refreshToken) {
        storeRefreshTokenAlias(tokenStore, key, attributes, refreshToken);

        Account account = getAuthorizedAccount();
        if (account == null) {
            return;
        }

        storeRefreshTokenAlias(tokenStore, account.getAccountId(), attributes, refreshToken);
        storeRefreshTokenAlias(tokenStore, account.getPrimaryEmailAddress(), attributes, refreshToken);
    }

    private void storeRefreshTokenAlias(ITokenStore tokenStore, String key, OnedriveAttributes attributes, String refreshToken) {
        if (key == null || key.isBlank()) {
            return;
        }
        tokenStore.save(MicrosoftAuthSdk.buildTokenKey(key, attributes.getClientId(), attributes.getAuthType()), refreshToken);
        if (key.startsWith(Constants.WS_CONTACT)) {
            tokenStore.save(key, refreshToken);
        }
    }

    private Account getAuthorizedAccount() {
        try {
            if (context != null && context.isAuthenticated()) {
                return context.getAuthorizedAccount();
            }
        } catch (Exception cause) {
            LOGGER.debug("Unable to resolve authorized account while storing token aliases", cause);
        }
        return null;
    }

    private String getAuthContextId(String state) {
        String[] parts = state.split(Constants.HASH);
        if (parts.length <= 1) {
            return null;
        }
        String candidate = parts[1];
        if (candidate.startsWith("http://") || candidate.startsWith("https://") || candidate.startsWith("/")) {
            return null;
        }
        return candidate;
    }

    private GraphServiceClientProvider getGraphServiceClientProvider(String authContextId, String key) {
        if (key != null && key.startsWith(Constants.WS_CONTACT)) {
            // MCP OAuth link is always built with Krista default public credentials (config.properties).
            // The token exchange here MUST use the same credentials — clientId/secret must match
            // the app that issued the auth code, otherwise Microsoft returns AADSTS7000215.
            // Operator's own Azure app credentials are intentionally NOT used for MCP auth.
            LOGGER.info("getGraphServiceClientProvider: MCP callback — using Krista default PUBLIC credentials (config.properties), authContextId={}",
                    authContextId);
            OnedriveAttributes attrs = new OnedriveAttributes(baseRoutingUrl, null, null, "", null, Constants.PUBLIC);
            return providerFactory.create(attrs);
        }
        if (authContextId != null) {
            return providerFactory.create(authContextId);
        }
        return providerFactory.create();
    }

    private boolean hasUserAccess(GraphServiceClientProvider clientProvider) {
        try {
            clientProvider.getUserRequestBuilder(null, null).drive().root().children().buildRequest().get();
            return true;
        } catch (GraphServiceException ignore) {
            LOGGER.debug(Constants.IGNORED_EXCEPTION);
            return false;
        }
    }

    // ── New SDK Auth Tab Endpoints ──

    /**
     * Tests connection with provided credentials.
     */
    @POST
    @Path("/testConnection")
    @Produces("text/plain")
    public String testConnection(JsonObject authPayload) {
        LOGGER.info("testConnection: ===== START =====");
        auditService.startRequest(invokerId, "Test Connection");

        OnedriveAttributes attributes = OnedriveAttributes.create(authPayload, baseRoutingUrl);
        LOGGER.info("testConnection: authType={}, email={}, isAppOnlyAuth={}, publicAuthMode={}",
                attributes.getAuthType(), attributes.getEmail(),
                attributes.isAppOnlyAuth(), attributes.getPublicAuthMode());
        java.util.Map<String, String> meta = new java.util.HashMap<>();
        if (attributes.getEmail() != null) meta.put("triggeredBy", attributes.getEmail());

        String authContextId = providerFactory.createAttributes(attributes);
        LOGGER.info("testConnection: Temp attrs saved — authContextId={}", authContextId);
        String authUrl = null;

        try {
            LOGGER.info("testConnection: Calling Graph API — authType={}, email={}", attributes.getAuthType(), attributes.getEmail());
            if (Constants.CLIENT_CREDENTIALS_CERT.equals(attributes.getAuthType())) {
                providerFactory.create(authContextId).getGraphServiceClientForSystem()
                        .users(attributes.getEmail())
                        .drive().root().children().buildRequest().get();
            } else {
                providerFactory.create(authContextId).getGraphServiceClientForAdmin()
                        .users(attributes.getEmail())
                        .drive().root().children().buildRequest().get();
            }

            LOGGER.info("testConnection: Graph API call SUCCESS — connection verified for email={}", attributes.getEmail());
            auditService.record(invokerId, AuditEventType.CATALOG_REQUEST_SUCCESS, "success",
                    "Test Connection", "Connection verified for " + attributes.getEmail(), null, meta);
            return Constants.GSON.toJson(new AuthenticationResponse(true, null, null));
        } catch (MustAuthorizeException cause) {
            LOGGER.info("testConnection: MustAuthorizeException caught — isAppOnlyAuth={}, msg={}",
                    attributes.isAppOnlyAuth(), cause.getMessage());
            if (attributes.isAppOnlyAuth()) {
                LOGGER.warn("testConnection: FAILURE — App-only auth failed, no auth URL will be generated. Returning error.");
                auditService.record(invokerId, AuditEventType.CATALOG_REQUEST_FAILURE, "failure",
                        "Test Connection", "App-only authentication failed", "MustAuthorizeException", meta);
                return Constants.GSON.toJson(new AuthenticationResponse(false,
                        "App-only authentication failed. Please verify your credentials "
                        + "and ensure the app has the required Application permissions with admin consent.", null));
            }
            LOGGER.info("testConnection: Public/delegated auth — building OAuth URL");
            LOGGER.info("testConnection: State components — email={}, authContextId={}, forwardPath={}",
                    attributes.getEmail(), authContextId, attributes.getForwardPath());
            auditService.record(invokerId, AuditEventType.CATALOG_REQUEST_SUCCESS, "success",
                    "Test Connection", "Authentication required — redirecting to sign-in", null, meta);
            String state = attributes.getEmail() + Constants.HASH + authContextId
                    + Constants.HASH + attributes.getForwardPath();
            LOGGER.info("testConnection: State built={}", state);
            try {
                authUrl = attributes.toSdk(new InMemoryTokenStore()).buildAuthorizationUrl(state);
            } catch (Exception urlEx) {
                LOGGER.error("testConnection: FAILURE — buildAuthorizationUrl threw exception — {}: {}",
                        urlEx.getClass().getSimpleName(), urlEx.getMessage(), urlEx);
                return Constants.GSON.toJson(new AuthenticationResponse(false,
                        "Failed to build authentication URL: " + urlEx.getMessage(), null));
            }
            if (authUrl == null || authUrl.isBlank()) {
                LOGGER.error("testConnection: FAILURE — buildAuthorizationUrl returned blank URL. email={}, state={}",
                        attributes.getEmail(), state);
            } else {
                LOGGER.info("testConnection: SUCCESS — OAuth URL generated (starts with: {})",
                        authUrl.substring(0, Math.min(80, authUrl.length())));
            }
            return Constants.GSON.toJson(new AuthenticationResponse(false, Constants.AUTHORIZATION_PROMPT, authUrl));
        } catch (Exception cause) {
            LOGGER.error("testConnection: FAILURE — Unexpected exception type={}, msg={}",
                    cause.getClass().getName(), cause.getMessage(), cause);
            LOGGER.error("testConnection: NOTE — If this is NOT a MustAuthorizeException, " +
                    "no auth URL will be returned. Check that the Graph call throws MAE, not a generic exception.");
            auditService.record(invokerId, AuditEventType.CATALOG_REQUEST_FAILURE, "failure",
                    "Test Connection", cause.getMessage(), cause.getClass().getSimpleName(), meta);
            return Constants.GSON.toJson(new AuthenticationResponse(false, "Connection test failed: " + cause.getMessage(), null));
        } finally {
            if (authUrl == null) {
                LOGGER.info("testConnection: finally — authUrl is null, removing temp authContextId={} from store", authContextId);
                onedriveAttributeStore.remove(authContextId);
            } else {
                LOGGER.info("testConnection: finally — authUrl present, keeping authContextId={} for callback", authContextId);
            }
        }
    }

    /**
     * Saves credentials after validation.
     */
    @POST
    @Path("/saveCredentials")
    @Produces("text/plain")
    public String saveCredentials(JsonObject authPayload) {
        LOGGER.info("saveCredentials: ===== START =====");
        auditService.startRequest(invokerId, "Save Credentials");

        OnedriveAttributes attributes = OnedriveAttributes.create(authPayload, baseRoutingUrl);
        LOGGER.info("saveCredentials: authType={}, email={}, isAppOnlyAuth={}, publicAuthMode={}",
                attributes.getAuthType(), attributes.getEmail(),
                attributes.isAppOnlyAuth(), attributes.getPublicAuthMode());
        java.util.Map<String, String> meta = new java.util.HashMap<>();
        if (attributes.getEmail() != null) meta.put("triggeredBy", attributes.getEmail());

        // Handle credential change cleanup BEFORE saving new credentials
        cleanupService.handleCredentialChange(attributes);

        String authContextId = providerFactory.createAttributes(attributes);
        LOGGER.info("saveCredentials: Temp attrs saved — authContextId={}", authContextId);
        String authUrl = null;

        try {
            LOGGER.info("saveCredentials: Calling Graph API — authType={}, email={}", attributes.getAuthType(), attributes.getEmail());
            if (Constants.CLIENT_CREDENTIALS_CERT.equals(attributes.getAuthType())) {
                providerFactory.create(authContextId).getGraphServiceClientForSystem()
                        .users(attributes.getEmail())
                        .drive().root().children().buildRequest().get();
            } else {
                providerFactory.create(authContextId).getGraphServiceClientForAdmin()
                        .users(attributes.getEmail())
                        .drive().root().children().buildRequest().get();
            }

            LOGGER.info("saveCredentials: Graph API call SUCCESS — connection verified for email={}", attributes.getEmail());
            boolean isSaved = onedriveAttributeStore.save(attributes, invokerId);
            LOGGER.info("saveCredentials: Credentials saved to store: {} under invokerId={}", isSaved, invokerId);
            if (!isSaved) {
                LOGGER.error("saveCredentials: FAILURE — onedriveAttributeStore.save() returned false for invokerId={}", invokerId);
            }

            if (isSaved) {
                try {
                    LOGGER.info("saveCredentials: Triggering subscription creation for invokerId: {}", invokerId);
                    onedriveSetUpService.initializeSubscriptionWithProvider(providerFactory.create(authContextId));
                } catch (Exception e) {
                    LOGGER.warn("saveCredentials: Subscription creation failed after save, will retry on next validation - reason: {}", e.getMessage());
                }
            }

            auditService.record(invokerId, isSaved ? AuditEventType.CATALOG_REQUEST_SUCCESS : AuditEventType.CATALOG_REQUEST_FAILURE,
                    isSaved ? "success" : "failure", "Save Credentials",
                    isSaved ? "Configuration saved successfully" : "Failed to save configuration", null, meta);

            return isSaved
                    ? Constants.GSON.toJson(new AuthenticationResponse(true, null, null))
                    : Constants.GSON.toJson(new AuthenticationResponse(false, "Failed to save attributes", null));
        } catch (MustAuthorizeException cause) {
            LOGGER.info("saveCredentials: MustAuthorizeException caught — isAppOnlyAuth={}, msg={}",
                    attributes.isAppOnlyAuth(), cause.getMessage());
            if (attributes.isAppOnlyAuth()) {
                LOGGER.warn("saveCredentials: FAILURE — App-only auth failed, no auth URL will be generated. Returning error.");
                auditService.record(invokerId, AuditEventType.CATALOG_REQUEST_FAILURE, "failure",
                        "Save Credentials", "App-only authentication failed", "MustAuthorizeException", meta);
                return Constants.GSON.toJson(new AuthenticationResponse(false,
                        "App-only authentication failed. Please verify your credentials "
                        + "and ensure the app has the required Application permissions with admin consent.", null));
            }
            LOGGER.info("saveCredentials: Public/delegated auth — saving attrs and building OAuth URL");
            onedriveAttributeStore.save(attributes, invokerId);
            LOGGER.info("saveCredentials: Attrs saved under invokerId={} before redirect", invokerId);
            auditService.record(invokerId, AuditEventType.CATALOG_REQUEST_SUCCESS, "success",
                    "Save Credentials", "Authentication required — redirecting to sign-in", null, meta);
            LOGGER.info("saveCredentials: State components — email={}, authContextId={}, forwardPath={}",
                    attributes.getEmail(), authContextId, attributes.getForwardPath());
            String state = attributes.getEmail() + Constants.HASH + authContextId
                    + Constants.HASH + attributes.getForwardPath();
            LOGGER.info("saveCredentials: State built={}", state);
            try {
                authUrl = attributes.toSdk(new InMemoryTokenStore()).buildAuthorizationUrl(state);
            } catch (Exception urlEx) {
                LOGGER.error("saveCredentials: FAILURE — buildAuthorizationUrl threw exception — {}: {}",
                        urlEx.getClass().getSimpleName(), urlEx.getMessage(), urlEx);
                return Constants.GSON.toJson(new AuthenticationResponse(false,
                        "Failed to build authentication URL: " + urlEx.getMessage(), null));
            }
            if (authUrl == null || authUrl.isBlank()) {
                LOGGER.error("saveCredentials: FAILURE — buildAuthorizationUrl returned blank URL. email={}, state={}",
                        attributes.getEmail(), state);
            } else {
                LOGGER.info("saveCredentials: SUCCESS — OAuth URL generated (starts with: {})",
                        authUrl.substring(0, Math.min(80, authUrl.length())));
            }
            return Constants.GSON.toJson(new AuthenticationResponse(false, Constants.AUTHORIZATION_PROMPT, authUrl));
        } catch (Exception cause) {
            LOGGER.error("saveCredentials: FAILURE — Unexpected exception type={}, msg={}",
                    cause.getClass().getName(), cause.getMessage(), cause);
            LOGGER.error("saveCredentials: NOTE — If this is NOT a MustAuthorizeException, " +
                    "no auth URL will be returned. Check that the Graph call throws MAE, not a generic exception.");
            auditService.record(invokerId, AuditEventType.CATALOG_REQUEST_FAILURE, "failure",
                    "Save Credentials", cause.getMessage(), cause.getClass().getSimpleName(), meta);
            return Constants.GSON.toJson(new AuthenticationResponse(false, "Failed to save attributes", null));
        } finally {
            if (authUrl == null) {
                LOGGER.info("saveCredentials: finally — authUrl is null, removing temp authContextId={} from store", authContextId);
                onedriveAttributeStore.remove(authContextId);
            } else {
                LOGGER.info("saveCredentials: finally — authUrl present, keeping authContextId={} for callback", authContextId);
            }
        }
    }

    /**
     * Retrieves stored credentials for a specific authentication type.
     *
     * <p>Both Quick Setup (AUTH_CODE_PUBLIC) and User Authentication (AUTH_CODE_PRIVATE) share
     * {@code authType="Public"}, so {@code authFlow} is used to distinguish them via the
     * {@code publicAuthMode} field stored in attributes ("krista" vs "own").</p>
     *
     * @param authType The authentication type (Public or Private)
     * @param authFlow The authentication flow (AUTH_CODE_PUBLIC, AUTH_CODE_PRIVATE, CLIENT_CREDENTIALS_SECRET)
     * @return JSON representation of stored credentials, or empty string if not found
     */
    @GET
    @Path("/getCredentials")
    @Produces("text/plain")
    public String getCredentials(@QueryParam("authType") String authType,
                                 @QueryParam("authFlow") String authFlow) {
        LOGGER.info("getCredentials: Loading attributes for auth-type: {}, auth-flow: {} for invoker {}", authType, authFlow, invokerId);

        final OnedriveAttributes loadedAttributes = onedriveAttributeStore.load(invokerId);
        if (loadedAttributes == null || authType == null) {
            return "";
        }

        // ── Legacy / migration detection ───────────────────────────────────────
        // Old releases stored credentials without authType (null/empty).
        // Migrated credentials may also have authType="Public" but no publicAuthMode
        // (set by saveAttributesInStore before publicAuthMode was added).
        // Both cases are User Auth (delegated OAuth with own app) → serve to User Auth tab.
        String storedType = loadedAttributes.getAuthType();
        boolean isLegacy = storedType == null || storedType.isEmpty();
        boolean isLegacyMigrated = Constants.PUBLIC.equals(storedType)
                && loadedAttributes.getPublicAuthMode() == null;

        if (isLegacy || isLegacyMigrated) {
            if ("AUTH_CODE_PRIVATE".equals(authFlow) && Constants.PUBLIC.equals(authType)) {
                LOGGER.info("getCredentials: Legacy/migrated credentials detected, serving to User Auth tab");
            } else {
                return "";
            }
        } else if (!authType.equals(storedType)) {
            return "";
        } else if (Constants.PUBLIC.equals(authType)) {
            // Both Quick Setup and User Authentication share authType="Public".
            // Match publicAuthMode to isolate Quick Setup (krista) from User Auth (own).
            String storedMode = loadedAttributes.getPublicAuthMode();
            if (authFlow != null) {
                boolean isOwnRequest = "AUTH_CODE_PRIVATE".equals(authFlow);
                boolean isOwnStored = "own".equals(storedMode);
                if (isOwnRequest != isOwnStored) {
                    return "";
                }
            } else if (!"own".equals(storedMode)) {
                // Null authFlow from older UI — only serve if stored mode is "own" (backward compat).
                // Quick Setup (krista) data must never leak without explicit authFlow matching.
                return "";
            }
        }

        // ── Build response with credential masking ─────────────────────────
        Map<String, Object> attributesMap = loadedAttributes.toMap();
        // Belt-and-suspenders: strip Krista-managed credentials from the response
        // so they never reach the browser, even if toMap() were to include them.
        // Use negative check (!"own") to safely handle null publicAuthMode from legacy data.
        boolean isKristaManaged = Constants.PUBLIC.equals(loadedAttributes.getAuthType())
                && !"own".equals(loadedAttributes.getPublicAuthMode());
        if (isKristaManaged) {
            // Krista-managed: don't expose internal credentials
            attributesMap.remove(Constants.CLIENT_ID);
            attributesMap.remove(Constants.CLIENT_SECRET);
            attributesMap.remove(Constants.TENANT_ID);
        }
        return Constants.GSON.toJson(attributesMap);
    }

    /**
     * Retrieves the stored authentication key/type.
     */
    @GET
    @Path("/getAuthKey")
    @Produces("text/plain")
    public String getAuthKey() {
        LOGGER.info("getAuthKey: Loading stored authentication key");

        final OnedriveAttributes attributes = onedriveAttributeStore.load(invokerId);
        if (attributes != null) {
            final String authType = attributes.getAuthType();
            return Constants.GSON.toJson(Objects.requireNonNullElse(authType, Constants.PUBLIC));
        } else {
            return "";
        }
    }

    /**
     * Returns extension metadata JSON for the SDK auth/audit HTML pages.
     */
    @GET
    @Path("/msauth/metadata/{extensionId}")
    @javax.ws.rs.Produces(javax.ws.rs.core.MediaType.APPLICATION_JSON)
    public String getExtensionMetadata(@PathParam("extensionId") String extensionId) {
        app.krista.sdk.msauth.extension.registry.ExtensionRegistry.initialize();
        return app.krista.sdk.msauth.extension.registry.ExtensionRegistry.get(extensionId)
                .map(m -> m.toUiJson())
                .orElse("{\"error\":\"Extension not found: " + extensionId + "\"}");
    }

    /**
     * Serves SDK auth tab HTML.
     */
    @GET
    @Path("/authentication/{subPath:.*}")
    @Produces(MediaType.TEXT_HTML)
    public Response authenticationTab(@PathParam("subPath") String subPath) {
        InputStream stream = getClass().getClassLoader().getResourceAsStream("auth/index.html");
        if (stream == null) {
            return Response.status(Response.Status.NOT_FOUND).entity("auth/index.html not found in resources.").build();
        }
        return Response.ok(stream, MediaType.TEXT_HTML).build();
    }

    // ── Existing Notification Endpoints ──

    /**
     * This method finds duplicate file entity from recent 1000 file entity received and add new file entity to set and Remove the oldest 100 file entities to make space for new ones.
     *
     * @param payload This is file entity sent by FileChangeInfoHandler
     * @return true if duplicate is found
     */
    private boolean isDuplicateFile(Map<String, Object> payload) {
        if (!fileStore.isEmpty()) {
            if (isFileExist(payload)) {
                return true;
            }
            if (fileStore.size() >= FILE_STORE_LIMIT) {
                removeOldestMessageIds((FILE_STORE_LIMIT + 100) - fileStore.size());
            }
            fileStore.add(payload);
            return false;
        } else {
            fileStore.add(payload);
            return false;
        }
    }

    private boolean isFileExist(Map<String, Object> payload) {
        for (Map<String, Object> file : fileStore) {
            app.krista.extensions.essentials.cloudfiles.onedrive.catalog.entities.File fileStoreEntity = (app.krista.extensions.essentials.cloudfiles.onedrive.catalog.entities.File) file.get(Constants.FILE);
            String fileStoreEvent = file.get(Constants.EVENT).toString();
            app.krista.extensions.essentials.cloudfiles.onedrive.catalog.entities.File fileEntity = (app.krista.extensions.essentials.cloudfiles.onedrive.catalog.entities.File) payload.get(Constants.FILE);
            String fileEvent = payload.get(Constants.EVENT).toString();
            if (fileEntity.absolutePath.equals(fileStoreEntity.absolutePath) && fileEvent.equals(fileStoreEvent) && fileEntity.modified.equals(fileStoreEntity.modified)) {
                return true;
            }
        }
        return false;
    }

    private void removeOldestMessageIds(int count) {
        fileStore.subList(0, count).clear();
    }

    @POST
    @Path("/onedriveNotification")
    @Consumes(MediaType.TEXT_PLAIN)
    public Response subscriptionValidation(@QueryParam(Constants.VALIDATION_TOKEN) String validationToken) {
        return Response.status(200).type(MediaType.TEXT_PLAIN).entity(validationToken.trim()).build();
    }

    @POST
    @Path("/onedriveNotification")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response subscriptionNotification(JsonObject notification) {
        LOGGER.info("subscriptionNotification: Received notification payload: {}", notification);
        auditService.record(invokerId, AuditEventType.FILE_NOTIFICATION_RECEIVED, "info", "File Change Notification", null, null, null);
        try {
            Map<String, Object> payload = fileChangeInfoHandler.getChangeFileEvent();
            if (payload.get(Constants.FILE) != null) {
                LOGGER.info("subscriptionNotification: File change event detected in payload");
                if (isDuplicateFile(payload)) {
                    LOGGER.info("subscriptionNotification: Duplicate file event detected, skipping processing");
                    auditService.record(invokerId, AuditEventType.FILE_NOTIFICATION_DROPPED_DUPLICATE, "info", "File Change Notification", null, null, null);
                    return Response.status(201).build();
                }
                LOGGER.info("subscriptionNotification: Processing new file change event");
                FreeForm freeForm = new FreeForm();
                ResponseUtil.addEventToFreeForm(payload, freeForm);
                freeForm.put(Constants.ONEDRIVE_FILE_ID, Constants.SWITCH, true);
                eventHandler.handleEvent(Constants.FILE_RECEIVED, freeForm);
                auditService.record(invokerId, AuditEventType.FILE_NOTIFICATION_TRIGGERED, "success", "File Change Notification", null, null, null);
                LOGGER.info("subscriptionNotification: File change event dispatched successfully");
            } else {
                LOGGER.info("subscriptionNotification: No file found in payload, dispatching empty event");
                FreeForm freeForm = new FreeForm();
                freeForm.put(Constants.ONEDRIVE_FILE_ID, Constants.SWITCH, false);
                eventHandler.handleEvent(Constants.FILE_RECEIVED, freeForm);
            }
        } catch (Exception e) {
            LOGGER.error("subscriptionNotification: Error processing notification", e);
            auditService.record(invokerId, AuditEventType.FILE_NOTIFICATION_ERROR, "failure", "File Change Notification", e.getMessage(), e.getClass().getSimpleName(), null);
        }
        return Response.status(200).build();
    }

    @POST
    @Path("/lifecycleNotification")
    @Consumes(MediaType.TEXT_PLAIN)
    public Response lifecycleValidation(@QueryParam(Constants.VALIDATION_TOKEN) String validationToken) {
        return Response.status(200).type(MediaType.TEXT_PLAIN).entity(validationToken.trim()).build();
    }

    @POST
    @Path("/lifecycleNotification")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response lifecycleNotification(JsonObject notification) {
        this.notificationProcessQueue.add(new Notification(Notification.NotificationType.LIFECYCLE, notification));
        return Response.status(202).build();
    }

}
