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
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.util.Constants;
import app.krista.extensions.util.KeyValueStore;
import app.krista.ksdk.accounts.Account;
import app.krista.ksdk.context.AuthorizationContext;
import app.krista.ksdk.context.RequestContext;
import app.krista.model.field.NamedValuedField;
import app.krista.sdk.msauth.MicrosoftAuthSdk;
import app.krista.sdk.msauth.exception.MsAuthException;
import app.krista.sdk.msauth.extension.audit.AuditRecorder;
import app.krista.sdk.msauth.extension.base.MsAuthAttributes;
import app.krista.sdk.msauth.extension.service.MsAuthGraphClientProvider;
import app.krista.sdk.msauth.token.ITokenStore;
import com.microsoft.aad.msal4j.*;
import com.microsoft.graph.requests.GraphServiceClient;
import com.microsoft.graph.requests.UserRequestBuilder;
import okhttp3.Request;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

import static app.krista.extensions.essentials.cloudfiles.onedrive.impl.util.Constants.*;

/**
 * Graph client provider for OneDrive that wraps the SDK's {@link MsAuthGraphClientProvider}.
 *
 * <p>All Graph client and token-refresh logic is delegated to the SDK. OneDrive-specific methods
 * ({@link #getGraphServiceClientForSystem()}, {@link #getRestClient})
 * are implemented here.</p>
 */
public class GraphServiceClientProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(GraphServiceClientProvider.class);

    private final MsAuthGraphClientProvider delegate;
    private final MicrosoftAuthSdk sdk;
    private final OnedriveAttributes onedriveAttributes;
    private final KeyValueStore keyValueStore;
    private final RequestContext requestContext;
    private final AuthorizationContext authorizationContext;
    private final String authContextId;

    @Inject
    public GraphServiceClientProvider(KeyValueStore keyValueStore, OnedriveAttributes onedriveAttributes,
                                      RequestContext requestContext, AuthorizationContext authorizationContext) {
        this(keyValueStore, onedriveAttributes, requestContext, authorizationContext, null);
    }

    public GraphServiceClientProvider(KeyValueStore keyValueStore, OnedriveAttributes onedriveAttributes,
                                      RequestContext requestContext, AuthorizationContext authorizationContext,
                                      String authContextId) {
        this.keyValueStore = keyValueStore;
        this.onedriveAttributes = onedriveAttributes;
        this.requestContext = requestContext;
        this.authorizationContext = authorizationContext;
        this.authContextId = authContextId;

        LOGGER.info("[MCP-CRED] GraphServiceClientProvider created — authType={}, clientId={}, email={}, authContextId={}",
                onedriveAttributes != null ? onedriveAttributes.getAuthType() : "null",
                onedriveAttributes != null ? maskId(onedriveAttributes.getClientId()) : "null",
                onedriveAttributes != null ? onedriveAttributes.getEmail() : "null",
                authContextId);

        ITokenStore tokenStore = new ITokenStore() {
            @Override public void save(String key, String value) {
                if (keyValueStore != null) keyValueStore.put(key, value);
            }
            @Override public String get(String key) {
                if (keyValueStore == null) return null;
                Object raw = keyValueStore.get(key, Object.class);
                return raw != null ? raw.toString() : null;
            }
            @Override public void delete(String key) {
                if (keyValueStore != null) keyValueStore.remove(key);
            }
        };

        this.sdk = onedriveAttributes != null ? onedriveAttributes.toSdk(tokenStore) : null;
        boolean isMcpMode = requestContext != null && requestContext.invokeAsUser();
        OnedriveAttributes delegateAttrs;
        if (isMcpMode && onedriveAttributes != null) {
            delegateAttrs = new OnedriveAttributes("", null, null, "", null, PUBLIC);
            LOGGER.info("[MCP-CRED] GraphServiceClientProvider: MCP mode — delegate using PUBLIC credentials for wsContact token key alignment");
        } else {
            delegateAttrs = onedriveAttributes;
        }

        this.delegate = (keyValueStore != null && onedriveAttributes != null)
                ? new MsAuthGraphClientProvider(keyValueStore, delegateAttrs, requestContext,
                        authorizationContext, authContextId)
                : null;
    }

    public OnedriveAttributes getOnedriveAttributes() {
        return onedriveAttributes;
    }

    // ── Delegated methods ────────────────────────────────────────────────────

    public GraphServiceClient<Request> getGraphServiceClientForUser(Boolean useEmail, String accountID) throws IOException {
        checkUserToken(useEmail, accountID);
        return delegate.getGraphServiceClientForUser(useEmail, accountID);
    }

    /**
     * Checks whether the wsContact user has a stored token before delegating to the SDK.
     *
     * <p>Called by both {@link #getGraphServiceClientForUser} and {@link #getUserRequestBuilder}
     * so that every Graph API call path is guarded. Throws {@link MustAuthorizeException} when
     * the user has not yet authenticated, which causes the platform to show the sign-in prompt
     * via {@link app.krista.extensions.essentials.cloudfiles.onedrive.OnedriveRequestAuthenticator#getMustAuthorizeResponse}.</p>
     *
     * <p><strong>Priority for resolving the wsContactId</strong>: explicit {@code accountID}
     * parameter first (most reliable, set per-call by the DAO), then {@code authorizationContext}
     * (request-scoped proxy, always reflects the current user).</p>
     */
    private void checkUserToken(Boolean useEmail, String accountID) {
        String wsContactId = resolveWsContactId(useEmail, accountID);
        if (wsContactId == null) {
            LOGGER.info("[MCP-CRED] checkUserToken: Non-MCP path (useEmail={}, accountID={}) — skipping per-user token check", useEmail, accountID);
            return;
        }
        LOGGER.info("[MCP-CRED] checkUserToken: MCP per-user path — checking plain-key token for wsContactId={}", wsContactId);
        LOGGER.info("[MCP-CRED] checkUserToken: Credentials active for this request — authType={}, clientId={}, email={}",
                onedriveAttributes != null ? onedriveAttributes.getAuthType() : "null",
                onedriveAttributes != null ? maskId(onedriveAttributes.getClientId()) : "null",
                onedriveAttributes != null ? onedriveAttributes.getEmail() : "null");
        String token = keyValueStore.get(wsContactId, String.class);
        if (token == null) {
            LOGGER.warn("[MCP-CRED] checkUserToken: NO token found for wsContactId={} — will generate OAuth link using Krista default Public credentials", wsContactId);
            List<NamedValuedField> details = new ArrayList<>();
            details.add(new NamedValuedField(Constants.CLIENT_USER_ID, Constants.TEXT, wsContactId,
                    new HashMap<>(), new HashMap<>()));
            String invokerId = resolveInvokerId();
            if (invokerId != null) {
                details.add(new NamedValuedField(Constants.USER_ID, Constants.TEXT, invokerId,
                        new HashMap<>(), new HashMap<>()));
            }
            throw new MustAuthorizeException(
                    "No credentials configured. Please authenticate via the Authentication tab.", details);
        }
        LOGGER.info("[MCP-CRED] checkUserToken: Token found for wsContactId={} — proceeding with authenticated request", wsContactId);
    }

    /**
     * Resolves the wsContactId to use for the per-user token check.
     *
     * <p>Priority: explicit {@code accountID} parameter (most reliable, set per-call by the DAO)
     * → authorizationContext (fallback when accountID is null and useEmail is false).
     * Returns {@code null} when useEmail=true or no wsContact identity can be resolved.</p>
     */
    private String resolveWsContactId(Boolean useEmail, String accountID) {
        if (resolveUseEmail(useEmail)) {
            // useEmail=true means email-based (operator) auth — no per-user token check needed
            return null;
        }
        if (accountID != null && accountID.startsWith(WS_CONTACT)) {
            return accountID;
        }
        // useEmail=false and no explicit accountID — try authorizationContext
        try {
            String authId = getAuthorizedAccountId();
            if (authId != null && authId.startsWith(WS_CONTACT)) {
                return authId;
            }
        } catch (Exception e) {
            LOGGER.warn("resolveWsContactId: Could not resolve wsContactId from authorizationContext ({})", e.getMessage());
        }
        return null;
    }

    private String resolveInvokerId() {
        try {
            return requestContext != null ? requestContext.getInvokerId() : null;
        } catch (Exception e) {
            LOGGER.warn("resolveInvokerId: Could not resolve invokerId ({})", e.getMessage());
            return null;
        }
    }

    public GraphServiceClient<Request> getGraphServiceClientForAdmin() {
        return delegate.getGraphServiceClientForAdmin();
    }

    public UserRequestBuilder getUserRequestBuilder(Boolean useEmail, String accountID) {
        checkUserToken(useEmail, accountID);
        return delegate.getUserRequestBuilder(resolveUseEmail(useEmail), resolveAccountId(useEmail, accountID));
    }

    public void verifyAccess(String email) {
        delegate.verifyAccess(email);
    }

    public String getDeltaLink() {
        return delegate.getDeltaLink();
    }

    public void storeDeltaLink(String deltaLink) {
        delegate.storeDeltaLink(deltaLink);
    }

    public void setAuditService(AuditRecorder auditService) {
        delegate.setAuditService(auditService);
    }

    public void setDefaultAttributesFactory(Supplier<? extends MsAuthAttributes> factory) {
        delegate.setDefaultAttributesFactory(factory);
    }

    public static void evictCache(MsAuthAttributes attributes) {
        MsAuthGraphClientProvider.evictCache(attributes);
    }

    // ── OneDrive-specific methods ────────────────────────────────────────────

    /**
     * Returns a Graph client for system (app-only) operations.
     * Routes via the SDK's admin client which handles {@code isAppOnlyAuth()} automatically.
     */
    public GraphServiceClient<Request> getGraphServiceClientForSystem() {
        return delegate.getGraphServiceClientForAdmin();
    }

    /**
     * Creates a REST client for OneDrive operations.
     */
    public RestClient getRestClient(String driveId) {
        return getRestClient(driveId, resolveUseEmail(null));
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private RestClient getRestClient(String driveId, boolean useEmail) {
        try {
            String userId = getUserId(useEmail, null);
            String refreshTokenStoreKey = getRefTokenStoreKey(userId);
            LOGGER.info("[MCP-CRED] getRestClient: userId={}, tokenKey={}, useEmail={}", userId, refreshTokenStoreKey, useEmail);
            String refreshToken = keyValueStore.get(refreshTokenStoreKey, String.class);
            if (refreshToken == null && userId.startsWith(WS_CONTACT)) {
                LOGGER.info("[MCP-CRED] getRestClient: Compound-key miss for wsContact user — retrying with plain key userId={}", userId);
                refreshToken = keyValueStore.get(userId, String.class);
                if (refreshToken != null) {
                    refreshTokenStoreKey = userId;
                    LOGGER.info("[MCP-CRED] getRestClient: Token found via plain-key fallback for userId={}", userId);
                }
            }
            if (refreshToken == null) {
                LOGGER.warn("[MCP-CRED] getRestClient: No refresh token found for userId={} — authentication required", userId);
                throw createMustAuthorizationException(userId, false);
            }
            LOGGER.info("[MCP-CRED] getRestClient: Refresh token found for userId={} — acquiring access token", userId);
            return getRestClient(driveId, refreshTokenStoreKey, refreshToken);
        } catch (RuntimeException cause) {
            LOGGER.error("[MCP-CRED] getRestClient: Failed — {}: {}", cause.getClass().getSimpleName(), cause.getMessage());
            throw cause;
        }
    }

    private RestClient getRestClient(String driveId, String userId, String refreshToken) {
        try {
            String[] scopes = Constants.REQUIRED_SCOPE.split(Constants.SCOPE_SEPARATOR);
            Set<String> scopeSet = new HashSet<>(Arrays.asList(scopes));
            RefreshTokenParameters refreshTokenParameters = RefreshTokenParameters.builder(scopeSet, refreshToken).build();

            OnedriveAttributes effectiveAttrs = chooseEffectiveAttributes();
            IClientCredential clientCredential = ClientCredentialFactory.createFromSecret(effectiveAttrs.getClientSecret());

            String authority;
            if (PRIVATE.equals(effectiveAttrs.getAuthType())) {
                authority = AUTHORITY + effectiveAttrs.getTenantId();
            } else if (PUBLIC.equals(effectiveAttrs.getAuthType())) {
                authority = ORG_AUTHORITY;
            } else {
                authority = AUTHORITY + effectiveAttrs.getTenantId();
            }

            LOGGER.info("[MCP-CRED] getRestClient: Acquiring access token — clientId={}, authority={}, authType={}",
                    maskId(effectiveAttrs.getClientId()), authority, effectiveAttrs.getAuthType());
            ConfidentialClientApplication cca = ConfidentialClientApplication.builder(
                    effectiveAttrs.getClientId(), clientCredential).authority(authority).build();
            IAuthenticationResult authenticationResult = cca.acquireToken(refreshTokenParameters).get();
            LOGGER.info("[MCP-CRED] getRestClient: Access token acquired successfully for userId={}", userId);
            return new RestClient(authenticationResult.accessToken(), driveId);
        } catch (IOException | ExecutionException cause) {
            LOGGER.error("[MCP-CRED] getRestClient: Token acquisition failed for userId={} — {}", userId, cause.getMessage());
            throw createMustAuthorizationException(extractPlainUserId(userId), true);
        } catch (InterruptedException cause) {
            Thread.currentThread().interrupt();
            LOGGER.error("[MCP-CRED] getRestClient: Interrupted during token acquisition for userId={}", userId);
            throw createMustAuthorizationException(extractPlainUserId(userId), true);
        }
    }

    private String getRefTokenStoreKey(String userId) {
        OnedriveAttributes effectiveAttrs = chooseEffectiveAttributes();
        String tokenKey = MicrosoftAuthSdk.buildTokenKey(userId, effectiveAttrs.getClientId(), effectiveAttrs.getAuthType());
        LOGGER.info("[MCP-CRED] getRefTokenStoreKey: userId={}, clientId={}, authType={} → tokenKey={}",
                userId, maskId(effectiveAttrs.getClientId()), effectiveAttrs.getAuthType(), tokenKey);
        return tokenKey;
    }

    /** Masks all but the first 6 chars of a credential ID for safe logging. */
    private static String maskId(String id) {
        if (id == null) return "null";
        return id.length() <= 6 ? "***" : id.substring(0, 6) + "***";
    }

    private OnedriveAttributes chooseEffectiveAttributes() {
        if (isInvokeAsUser()) {
            LOGGER.info("[MCP-CRED] chooseEffectiveAttributes: MCP mode — using Krista default PUBLIC credentials (config.properties), NOT operator setup credentials");
            return new OnedriveAttributes("", null, null, "", null, PUBLIC);
        }
        LOGGER.info("[MCP-CRED] chooseEffectiveAttributes: Setup-tab mode — using operator credentials: authType={}, clientId={}, email={}",
                onedriveAttributes != null ? onedriveAttributes.getAuthType() : "null",
                onedriveAttributes != null ? maskId(onedriveAttributes.getClientId()) : "null",
                onedriveAttributes != null ? onedriveAttributes.getEmail() : "null");
        return onedriveAttributes;
    }

    private String extractPlainUserId(String compoundKey) {
        if (compoundKey == null) {
            return compoundKey;
        }
        OnedriveAttributes effectiveAttrs = chooseEffectiveAttributes();
        String suffix = UNDER_SCORE + effectiveAttrs.getClientId()
                + UNDER_SCORE + effectiveAttrs.getAuthType();
        if (compoundKey.endsWith(suffix)) {
            return compoundKey.substring(0, compoundKey.length() - suffix.length());
        }
        return compoundKey;
    }

    /**
     * Returns the user ID for the current request context.
     */
    public String getUserId(boolean calledFromValidateAttributes) {
        String userId = calledFromValidateAttributes || !isInvokeAsUser()
                ? onedriveAttributes.getEmail()

                : getAuthorizedAccountId();
        if (userId == null) {
            throw new IllegalStateException(Constants.FAILED_TO_GET_ACCOUNT);
        }
        return userId;
    }

    private String getUserId(boolean calledFromValidateAttributes, String accountID) {
        String userId;
        if (calledFromValidateAttributes) {
            boolean isFromCatalogRequest = Boolean.parseBoolean(
                    keyValueStore.get(Constants.IS_FROM_CATALOG_REQUEST, String.class));
            userId = isFromCatalogRequest
                    ? getAuthorizedAccountId()
                    : onedriveAttributes.getEmail();
        } else {
            userId = (accountID != null) ? accountID : getAuthorizedAccountId();
        }
        LOGGER.info("User ID: {}", userId);
        if (userId == null) {
            throw new IllegalStateException(Constants.FAILED_TO_GET_ACCOUNT);
        }
        return userId;
    }

    private MustAuthorizeException createMustAuthorizationException(String userId, boolean reAuthentication) {
        LOGGER.debug(reAuthentication
                ? Constants.GOT_ERROR_FOR_AUTHENTICATION_SO_SENDING_FOR_RE_AUTHENTICATION
                : Constants.GOT_ERROR_FOR_AUTHENTICATION_SO_SENDING_FOR_AUTHENTICATION);
        List<NamedValuedField> details = new ArrayList<>();
        NamedValuedField userIdField = new NamedValuedField(Constants.USER_ID, Constants.TEXT, userId,
                new HashMap<>(), new HashMap<>());
        details.add(userIdField);
        if (authContextId != null) {
            NamedValuedField contextIdField = new NamedValuedField(Constants.AUTH_CONTEXT_ID, Constants.TEXT,
                    authContextId, new HashMap<>(), new HashMap<>());
            details.add(contextIdField);
        }
        return new MustAuthorizeException(reAuthentication
                ? Constants.REFRESH_TOKEN_IS_NO_LONGER_VALID_PLEASE_REAUTHORIZE_YOURSELF
                : AUTHORIZATION_PROMPT, details);
    }

    boolean resolveUseEmail(Boolean useEmail) {
        return useEmail != null ? useEmail : !isInvokeAsUser();
    }

    String resolveAccountId(Boolean useEmail, String accountID) {
        boolean effectiveUseEmail = resolveUseEmail(useEmail);
        if (!effectiveUseEmail && accountID == null) {
            return getAuthorizedAccountId();
        }
        return accountID;
    }

    private boolean isInvokeAsUser() {
        return requestContext != null && requestContext.invokeAsUser();
    }

    private String getAuthorizedAccountId() {
        if (authorizationContext == null) {
            throw new IllegalStateException(Constants.FAILED_TO_GET_ACCOUNT);
        }
        Account account = authorizationContext.getAuthorizedAccount();
        if (account == null || account.getAccountId() == null) {
            throw new IllegalStateException(Constants.FAILED_TO_GET_ACCOUNT);
        }
        return account.getAccountId();
    }
}
