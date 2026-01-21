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
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.stores.RefreshTokenStore;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.util.Constants;
import app.krista.ksdk.context.AuthorizationContext;
import app.krista.ksdk.context.RequestContext;
import app.krista.model.field.NamedValuedField;
import com.google.gson.JsonObject;
import com.microsoft.aad.msal4j.*;
import com.microsoft.graph.authentication.IAuthenticationProvider;
import com.microsoft.graph.core.ClientException;
import com.microsoft.graph.requests.GraphServiceClient;
import com.microsoft.graph.requests.UserRequestBuilder;
import okhttp3.Request;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

public class GraphServiceClientProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(GraphServiceClientProvider.class);
    private final RefreshTokenStore refreshTokenStore;
    private final OnedriveAttributes onedriveAttributes;
    private final RequestContext requestContext;
    private final AuthorizationContext authorizationContext;
    private final String authContextId;
    public static final String AUTHORIZATION_PROMPT = "Authorize yourself and click Validate Attributes before saving changes to proceed further";

    @Inject
    public GraphServiceClientProvider(RefreshTokenStore refreshTokenStore, OnedriveAttributes onedriveAttributes,
                                      RequestContext requestContext, AuthorizationContext authorizationContext) {
        this(refreshTokenStore, onedriveAttributes, requestContext, authorizationContext, null);
    }

    public GraphServiceClientProvider(RefreshTokenStore refreshTokenStore, OnedriveAttributes onedriveAttributes,
                                      RequestContext requestContext, AuthorizationContext authorizationContext, String authContextId) {
        this.refreshTokenStore = refreshTokenStore;
        this.onedriveAttributes = onedriveAttributes;
        this.requestContext = requestContext;
        this.authorizationContext = authorizationContext;
        this.authContextId = authContextId;
    }

    public OnedriveAttributes getOnedriveAttributes() {
        return onedriveAttributes;
    }

    /**
     * Creates a REST client for OneDrive operations.
     *
     * @param driveId The drive identifier
     * @return RestClient configured for the specified drive
     */

    public RestClient getRestClient(String driveId) {
        return getRestClient(driveId, !requestContext.invokeAsUser());
    }

    /**
     * Creates a Graph Service Client for user-level operations.
     * This method determines whether to use admin credentials or user-specific credentials
     * based on the useEmail parameter.
     *
     * @param useEmail Whether to use email-based authentication (admin) or user-specific auth
     * @param accountID The account ID for user-specific authentication (null for admin)
     * @return GraphServiceClient configured with appropriate authentication
     * @throws IOException if authentication fails or network issues occur
     */

    public GraphServiceClient<Request> getGraphServiceClientForUser(Boolean useEmail, String accountID) throws IOException {
        if (useEmail != null) {
            return getGraphServiceClient(useEmail, useEmail ? null : accountID);
        } else {
            return getGraphServiceClient(!requestContext.invokeAsUser(), null);
        }
    }


    /**
     * Creates a Graph Service Client for admin-level operations.
     * Uses the configured admin email credentials for authentication.
     *
     * @return GraphServiceClient configured with admin authentication
     */
    public GraphServiceClient<Request> getGraphServiceClientForAdmin() {
        return getGraphServiceClient(true, null);
    }

    private GraphServiceClient<Request> getGraphServiceClient(boolean useEmail, String accountID) {
        String userId = getUserId(useEmail, accountID);
        String refreshToken = refreshTokenStore.get(userId);
        LOGGER.info("getGraphServiceClient: userId {}", userId);
        if (refreshToken == null) {
            LOGGER.info("refreshToken is null");
            throw createMustAuthorizationException(userId, false);
        }
        return getGraphServiceClient(userId, refreshToken);
    }

    private GraphServiceClient<Request> getGraphServiceClient(String userId, String refreshToken) {
        try {
            String[] scopes = Constants.REQUIRED_SCOPE.split(Constants.SCOPE_SEPARATOR);
            Set<String> scopeSet = Arrays.stream(scopes).collect(Collectors.toSet());

            RefreshTokenParameters refreshTokenParameters = RefreshTokenParameters.builder(scopeSet, refreshToken).build();
            IClientCredential clientCredential = ClientCredentialFactory.createFromSecret(onedriveAttributes.getClientSecret());
            String authority = Constants.AUTHORITY + onedriveAttributes.getTenantId();

            ConfidentialClientApplication confidentialClientApplication = ConfidentialClientApplication.builder
                    (onedriveAttributes.getClientId(), clientCredential).authority(authority).build();
            IAuthenticationResult authenticationResult = confidentialClientApplication.acquireToken(refreshTokenParameters).get();

            final String cachedTokenContent = confidentialClientApplication.tokenCache().serialize();
            updateRefreshToken(userId, Constants.GSON.fromJson(cachedTokenContent, JsonObject.class));

            return GraphServiceClient.builder()
                    .authenticationProvider(new GraphServiceClientAuthenticationProvider(authenticationResult.accessToken()))
                    .buildClient();
        } catch (ClientException | ExecutionException | MalformedURLException cause) {
            throw createMustAuthorizationException(userId, true);
        } catch (InterruptedException cause) {
            Thread.currentThread().interrupt();
            throw createMustAuthorizationException(userId, true);
        }
    }

    private RestClient getRestClient(String driveId, boolean useEmail) {
        try {
            String userId = getUserId(useEmail, null);
            String refreshToken = refreshTokenStore.get(userId);
            if (refreshToken == null) {
                throw createMustAuthorizationException(userId, false);
            }
            return getRestClient(driveId, userId, refreshToken);
        } catch (RuntimeException cause) {
            LOGGER.error("Failed to get graph client {}", cause.getClass().getName());
            LOGGER.info(cause.getMessage());
            throw cause;
        }
    }

    private RestClient getRestClient(String driveId, String userId, String refreshToken) {
        try {
            String[] scopes = Constants.REQUIRED_SCOPE.split(Constants.SCOPE_SEPARATOR);
            Set<String> scopeSet = Arrays.stream(scopes).collect(Collectors.toSet());
            RefreshTokenParameters refreshTokenParameters = RefreshTokenParameters.builder(scopeSet, refreshToken).build();
            IClientCredential clientCredential = ClientCredentialFactory.createFromSecret(onedriveAttributes.getClientSecret());
            String authority = Constants.AUTHORITY + onedriveAttributes.getTenantId();
            ConfidentialClientApplication confidentialClientApplication = ConfidentialClientApplication.builder
                    (onedriveAttributes.getClientId(), clientCredential).authority(authority).build();
            IAuthenticationResult authenticationResult = confidentialClientApplication.acquireToken(refreshTokenParameters).get();
            return new RestClient(authenticationResult.accessToken(), driveId);
        } catch (IOException | ExecutionException | InterruptedException cause) {
            Thread.currentThread().interrupt();
            throw createMustAuthorizationException(userId, true);
        }
    }

    private void updateRefreshToken(String userId, JsonObject jsonObject) {
        JsonObject refreshToken = jsonObject.get("RefreshToken").getAsJsonObject();
        String firstKey = refreshToken.keySet().iterator().next();
        JsonObject tokenData = refreshToken.getAsJsonObject(firstKey);
        String secret = tokenData.get("secret").getAsString();
        refreshTokenStore.put(userId, secret);
        LOGGER.info("Updated Refresh Token.");
    }

    /**
     * This request returns user request builder
     *
     * @return userRequestBuilder
     */

    public UserRequestBuilder getUserRequestBuilder(Boolean useEmail, String accountID) {
        try {
            if (useEmail != null) {
                return !useEmail
                        ? getGraphServiceClientForUser(useEmail, null).me()
                        : getGraphServiceClientForUser(useEmail, accountID).users(onedriveAttributes.getMailId());
            }
            return requestContext.invokeAsUser()
                    ? getGraphServiceClientForUser(null, null).me()
                    : getGraphServiceClientForUser(null, null).users(onedriveAttributes.getMailId());
        } catch (IOException cause) {
            throw new IllegalStateException(cause);
        }
    }

    private MustAuthorizeException createMustAuthorizationException(String userId, boolean reAuthentication) {
        LOGGER.debug(reAuthentication
                ? Constants.GOT_ERROR_FOR_AUTHENTICATION_SO_SENDING_FOR_RE_AUTHENTICATION
                : Constants.GOT_ERROR_FOR_AUTHENTICATION_SO_SENDING_FOR_AUTHENTICATION);
        List<NamedValuedField> details = new ArrayList<>();
        NamedValuedField userIdField = new NamedValuedField(Constants.USER_ID, Constants.TEXT, userId, new HashMap<>(), new HashMap<>());
        details.add(userIdField);
        if (authContextId != null) {
            NamedValuedField contextIdField = new NamedValuedField(Constants.AUTH_CONTEXT_ID, Constants.TEXT, authContextId, new HashMap<>(), new HashMap<>());
            details.add(contextIdField);
        }
        return new MustAuthorizeException(reAuthentication
                ? Constants.REFRESH_TOKEN_IS_NO_LONGER_VALID_PLEASE_REAUTHORIZE_YOURSELF
                : AUTHORIZATION_PROMPT, details);
    }

    private String getUserId(boolean calledFromValidateAttributes, String accountID) {
        String userId;
        if (calledFromValidateAttributes) {
            boolean isFromCatalogRequest = Boolean.parseBoolean(refreshTokenStore.get(Constants.IS_FROM_CATALOG_REQUEST));
            userId = isFromCatalogRequest ? authorizationContext.getAuthorizedAccount().getAccountId() : onedriveAttributes.getMailId();
        } else {
            userId = (accountID != null) ? accountID : authorizationContext.getAuthorizedAccount().getAccountId();
        }
        LOGGER.info("User ID: {}", userId);
        if (userId == null) {
            throw new IllegalStateException(Constants.FAILED_TO_GET_ACCOUNT);
        }
        return userId;
    }

    public static class GraphServiceClientAuthenticationProvider implements IAuthenticationProvider {

        private final String accessToken;

        public GraphServiceClientAuthenticationProvider(String accessToken) {
            if (accessToken == null || accessToken.isBlank()) {
                throw new IllegalArgumentException("Empty access token provided " + accessToken);
            }
            this.accessToken = accessToken;
        }

        @NotNull
        @Override
        public CompletableFuture<String> getAuthorizationTokenAsync(@NotNull URL requestUrl) {
            return CompletableFuture.completedFuture(accessToken);
        }
    }
}
