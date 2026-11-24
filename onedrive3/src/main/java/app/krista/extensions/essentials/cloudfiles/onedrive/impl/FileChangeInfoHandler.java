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

package app.krista.extensions.essentials.cloudfiles.onedrive.impl;


import app.krista.extension.executor.Invoker;
import app.krista.extension.request.RoutingInfo;
import app.krista.extension.request.protos.http.HttpProtocol;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.connectors.GraphServiceClientProvider;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.connectors.GraphServiceClientProviderFactory;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.util.Constants;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.util.FileChangeResourceUtil;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.util.FileUtil;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.util.ResponseUtil;
import com.microsoft.graph.http.GraphServiceException;
import com.microsoft.graph.models.DriveItem;
import com.microsoft.graph.models.DriveItemDeltaParameterSet;
import com.microsoft.graph.requests.DriveItemDeltaCollectionPage;
import org.jvnet.hk2.annotations.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Handles file change events in OneDrive by tracking and processing delta changes.
 * This service is responsible for detecting file creations, updates, and deletions
 * by comparing current state with previously stored state.
 */
@Service
public class FileChangeInfoHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(FileChangeInfoHandler.class);
    private final GraphServiceClientProviderFactory factory;
    private volatile GraphServiceClientProvider provider;
    private final String routingUrl;

    /**
     * Constructs a FileChangeInfoHandler with factory-created provider.
     * The GraphServiceClientProvider is created lazily on first use to avoid
     * MustAuthorizeException during DI construction when no credentials are configured.
     *
     * @param factory Factory to create the GraphServiceClientProvider
     * @param invoker Invoker for routing information
     */
    @Inject
    public FileChangeInfoHandler(GraphServiceClientProviderFactory factory, Invoker invoker) {
        this.factory = factory;
        this.provider = null;
        String rawUrl = invoker.getRoutingInfo().getRoutingURL(HttpProtocol.PROTOCOL_NAME, RoutingInfo.Type.APPLIANCE);
        this.routingUrl = rawUrl.contains("https://extension.local.eng.krista.app")
                ? rawUrl.replace("https://extension.local.eng.krista.app", "http://localhost:8765")
                : rawUrl;
    }

    /**
     * Constructs a FileChangeInfoHandler with the specified provider and invoker.
     * Initializes the routing URL based on the environment.
     *
     * @param provider The GraphServiceClientProvider for Microsoft Graph API calls
     * @param invoker  Invoker containing routing information
     */
    public FileChangeInfoHandler(GraphServiceClientProvider provider, Invoker invoker) {
        this.factory = null;
        this.provider = provider;
        String rawUrl = invoker.getRoutingInfo().getRoutingURL(HttpProtocol.PROTOCOL_NAME, RoutingInfo.Type.APPLIANCE);
        this.routingUrl = rawUrl.contains("https://extension.local.eng.krista.app")
                ? rawUrl.replace("https://extension.local.eng.krista.app", "http://localhost:8765")
                : rawUrl;
    }

    private GraphServiceClientProvider getProvider() {
        if (provider == null) {
            synchronized (this) {
                if (provider == null) {
                    provider = factory.create();
                }
            }
        }
        return provider;
    }

    /**
     * Retrieves file change events by comparing the current state with the previously stored state.
     * Detects created, updated, or deleted files and populates the payload with the relevant information.
     *
     * @return A map containing file change event details, including the affected file and event type
     */
    public Map<String, Object> getChangeFileEvent() {
        Map<String, Object> payload = new HashMap<>();
        try {
            DriveItemDeltaCollectionPage oldDeltaCollectionPage = FileChangeResourceUtil.getFileChangeResource(routingUrl);
            processDeltaLinkItems(oldDeltaCollectionPage, payload);
            FileChangeResourceUtil.addFileMetaData(routingUrl, createFileChangeResource());
            return payload;

        } catch (GraphServiceException | IllegalArgumentException cause) {
            LOGGER.info(cause.getMessage(), cause);
            ResponseUtil.errorResponse(false, Constants.FILE_NOT_FOUND, payload);
        }
        return payload;
    }

    /**
     * Creates a new file change resource by retrieving the current delta state from OneDrive.
     * This represents the current state of files in the drive.
     *
     * @return The current delta collection page from OneDrive
     * @throws RuntimeException If an error occurs during the subscription process
     */
    private DriveItemDeltaCollectionPage createFileChangeResource() {
        try {
            return FileUtil.getRootUserRequestBuilder(getProvider()).delta().buildRequest().get();
        } catch (GraphServiceException | IllegalArgumentException cause) {
            LOGGER.info(cause.getMessage(), cause);
            throw new RuntimeException("Error Occurred During Subscription");
        }
    }

    /**
     * Processes delta link items to detect changes between the old state and current state.
     * Updates the payload with information about any detected changes.
     *
     * @param oldDeltaCollectionPage The previously stored delta collection page
     * @param payload                The map to be populated with change information
     */
    private void processDeltaLinkItems(DriveItemDeltaCollectionPage oldDeltaCollectionPage, Map<String, Object> payload) {
        try {
            payload.put(Constants.EVENT, Constants.NO_CHANGE);
            String token = getDeltaLinkToken(oldDeltaCollectionPage);
            DriveItemDeltaCollectionPage trackChanges = getDeltaCollectionWithToken(token);
            compareWithChanges(trackChanges, payload);
        } catch (GraphServiceException | IllegalArgumentException cause) {
            LOGGER.info(cause.getMessage(), cause);
            payload.put(Constants.EVENT, Constants.NO_CHANGE);
        }
    }

    /**
     * Compares the current state with the previous state to detect file changes.
     * Identifies created or updated files based on timestamps and updates the payload accordingly.
     *
     * @param trackChange The delta collection page containing changes to process
     * @param payload     The map to be populated with change information
     */
    private void compareWithChanges(DriveItemDeltaCollectionPage trackChange, Map<String, Object> payload) {
        while (trackChange != null && !trackChange.getCurrentPage().isEmpty()) {
            DriveItemDeltaCollectionPage nextDelta = getDeltaCollectionWithToken(getDeltaLinkToken(trackChange));
            if (nextDelta == null) {
                DriveItem driveItem = trackChange.getCurrentPage().get(trackChange.getCurrentPage().size() - 1);
                if (driveItem.lastModifiedDateTime != null && driveItem.createdDateTime != null
                        && driveItem.lastModifiedDateTime.equals(driveItem.createdDateTime)) {
                    ResponseUtil.addCreatedFile(driveItem, payload);
                } else {
                    ResponseUtil.addUpdatedFile(driveItem, payload);
                }
            }
            if (nextDelta != null) {
                trackChange = nextDelta;
            } else {
                break;
            }
        }
    }

    /**
     * Retrieves a delta collection page using the specified token.
     * The token is used to get changes that occurred after a specific state.
     *
     * @param token The delta token from a previous request
     * @return The delta collection page containing changes since the token, or null if an error occurs
     */
    private DriveItemDeltaCollectionPage getDeltaCollectionWithToken(String token) {
        try {
            return FileUtil.getRootUserRequestBuilder(getProvider())
                    .delta(DriveItemDeltaParameterSet
                            .newBuilder()
                            .withToken(token)
                            .build())
                    .buildRequest()
                    .get();
        } catch (GraphServiceException | IllegalArgumentException cause) {
            LOGGER.info(cause.getMessage(), cause);
        }
        return null;
    }

    /**
     * Extracts the delta link token from a delta collection page.
     * The token is used to track changes since a specific state.
     *
     * @param deltaCollectionPage The delta collection page containing the delta link
     * @return The extracted token, or an empty string if no delta link is found
     */
    private String getDeltaLinkToken(DriveItemDeltaCollectionPage deltaCollectionPage) {
        while (deltaCollectionPage != null && !deltaCollectionPage.getCurrentPage().isEmpty()) {
            if (deltaCollectionPage.deltaLink() != null) {
                String deltaLink = Objects.requireNonNull(deltaCollectionPage.deltaLink());
                int tokenStart = deltaLink.indexOf("token='") + 7;
                int tokenEnd = deltaLink.lastIndexOf("'");
                if (tokenStart > 6 && tokenEnd > tokenStart) {
                    return deltaLink.substring(tokenStart, tokenEnd);
                }
            }
            if (deltaCollectionPage.getNextPage() != null) {
                deltaCollectionPage = deltaCollectionPage.getNextPage().buildRequest().get();
            } else {
                break;
            }
        }
        return "";
    }
}
