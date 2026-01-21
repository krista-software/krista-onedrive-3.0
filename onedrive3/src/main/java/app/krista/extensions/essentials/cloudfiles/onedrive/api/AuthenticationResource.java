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


import app.krista.extension.executor.Invoker;
import app.krista.extensions.essentials.cloudfiles.onedrive.OnedriveAttributes;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.FileChangeInfoHandler;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.connectors.GraphServiceClientProvider;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.connectors.GraphServiceClientProviderFactory;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.stores.OnedriveAttributeStore;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.stores.RefreshTokenStore;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.util.Constants;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.util.Notification;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.util.NotificationProcessQueue;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.util.ResponseUtil;
import app.krista.extensions.util.EventHandler;
import app.krista.ksdk.authentication.AuthorizationListener;
import app.krista.ksdk.context.AuthorizationContext;
import app.krista.model.base.FreeForm;
import com.github.scribejava.core.exceptions.OAuthException;
import com.github.scribejava.core.model.OAuth2AccessToken;
import com.google.gson.JsonObject;
import com.microsoft.graph.http.GraphServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;

@Path("/")
public final class AuthenticationResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthenticationResource.class);
    private final OnedriveAttributeStore onedriveAttributeStore;
    private final RefreshTokenStore refreshTokenStore;
    private final NotificationProcessQueue notificationProcessQueue;
    private final GraphServiceClientProviderFactory providerFactory;
    private final AuthorizationContext context;
    private final AuthorizationListener authorizationListener;
    private final EventHandler eventHandler;
    private final FileChangeInfoHandler fileChangeInfoHandler;
    private static final List<Map<String, Object>> fileStore = new ArrayList<>(1000);
    private static final int FILE_STORE_LIMIT = 1000;

    @Inject
    public AuthenticationResource(OnedriveAttributeStore onedriveAttributeStore, RefreshTokenStore refreshTokenStore,
                                  GraphServiceClientProviderFactory providerFactory, AuthorizationContext context,
                                  EventHandler eventHandler,
                                  Invoker invoker,
                                  AuthorizationListener authorizationListener, FileChangeInfoHandler fileChangeInfoHandler) {
        this.onedriveAttributeStore = onedriveAttributeStore;
        this.refreshTokenStore = refreshTokenStore;
        this.eventHandler = eventHandler;
        this.providerFactory = providerFactory;
        this.context = context;
        this.authorizationListener = authorizationListener;
        this.fileChangeInfoHandler = fileChangeInfoHandler;
        this.notificationProcessQueue = new NotificationProcessQueue(providerFactory, invoker);
    }

    @GET
    @Path("/callback")
    public String getCallBack(@QueryParam(Constants.CODE) String code, @QueryParam(Constants.STATE) String state) {
        Objects.requireNonNull(code);
        String[] parts = state.split(Constants.HASH);
        if (parts[0].isBlank() || parts.length > 2) {
            LOGGER.error("getCallBack - Invalid state parameters: {}", state);
            throw new BadRequestException(Constants.INVALID_STATE_PARAMETERS);
        }
        String key = parts[0];
        String authContextId = parts.length == 2 ? parts[1] : null;
        try {
            GraphServiceClientProvider clientProvider = getGraphServiceClientProvider(authContextId);
            OAuth2AccessToken accessToken = clientProvider.getOnedriveAttributes().getOAuth20Service().getAccessToken(code);
            refreshTokenStore.put(key, accessToken.getRefreshToken());
            if (!key.startsWith(Constants.WS_CONTACT) && !hasUserAccess(clientProvider)) {
                refreshTokenStore.remove(key);
                return Constants.UNAUTHORISED_USER + " User email '" + key + "' configured in the setup does not match with authenticated user.";
            }
            if (context.isAuthenticated()) {
                authorizationListener.authorized();
                LOGGER.info("User Authenticated Successfully.");
                return Constants.USER_AUTHENTICATED_SUCCESSFULLY_PLEASE_PROCEED_WITH_REQUEST;
            }
            LOGGER.info("User Authenticated Successfully. Save the changes.");
            return Constants.USER_AUTHENTICATED_SUCCESSFULLY_SAVE_THE_CHANGES;
        } catch (OAuthException cause) {
            throw new IllegalStateException(getErrorDescription(cause), cause.getCause());
        } catch (IOException | ExecutionException cause) {
            throw new IllegalStateException(Constants.ERROR_OCCURRED_DURING_AUTHORIZATION, cause.getCause());
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(Constants.ERROR_OCCURRED_DURING_AUTHORIZATION, interruptedException.getCause());
        } finally {
            if (authContextId != null) {
                onedriveAttributeStore.remove(authContextId);
            }
        }
    }

    private GraphServiceClientProvider getGraphServiceClientProvider(String authContextId) {
        GraphServiceClientProvider clientProvider;
        if (authContextId == null) {
            clientProvider = providerFactory.create();
        } else {
            OnedriveAttributes effectiveOutlookAttributes = onedriveAttributeStore.load(authContextId);
            clientProvider = providerFactory.create(effectiveOutlookAttributes);
        }
        return clientProvider;
    }

    private String getErrorDescription(OAuthException cause) {
        String errorDescription = Constants.ERROR_OCCURRED_DURING_AUTHORIZATION;
        if (cause.getMessage() != null && !cause.getMessage().isBlank()) {
            var exceptionMap = Constants.GSON.fromJson(cause.getMessage(), Map.class);
            if (exceptionMap.containsKey(Constants.ERROR_DESCRIPTION)) {
                errorDescription = errorDescription + exceptionMap.get(Constants.ERROR_DESCRIPTION);
            }
        }
        return errorDescription;
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
        Map<String, Object> payload = fileChangeInfoHandler.getChangeFileEvent();
        if (payload.get(Constants.FILE) != null) {
            if (isDuplicateFile(payload)) {
                return Response.status(201).build();
            }
            FreeForm freeForm = new FreeForm();
            ResponseUtil.addEventToFreeForm(payload, freeForm);
            freeForm.put(Constants.ONEDRIVE_FILE_ID, Constants.SWITCH, true);
            eventHandler.handleEvent(Constants.FILE_RECEIVED, freeForm);
        } else {
            FreeForm freeForm = new FreeForm();
            freeForm.put(Constants.ONEDRIVE_FILE_ID, Constants.SWITCH, false);
            eventHandler.handleEvent(Constants.FILE_RECEIVED, freeForm);
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
