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


import app.krista.extension.authorization.RequestAuthenticator;
import app.krista.extension.executor.Invoker;
import app.krista.extension.impl.anno.*;
import app.krista.extension.request.RoutingInfo;
import app.krista.extension.request.protos.http.HttpProtocol;
import app.krista.extension.util.InvokerAttributes;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.connectors.GraphServiceClientProviderFactory;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.services.OnedriveSubscriptionCleanupService;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.stores.OnedriveAttributeStore;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.util.Constants;
import app.krista.extensions.util.KeyValueStore;
import app.krista.ksdk.context.AuthorizationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.Map;
@Field.Text(value = Constants.CLIENT_ID)
@Field.Text(value = Constants.CLIENT_SECRET, isSecured = true)
@Field.Text(value = Constants.TENANT_ID, isSecured = true)
@Field.Text(value = Constants.EMAIL)
@Extension(version = "4.0.5", name = "OneDrive Files", description = "Microsoft OneDrive Files integration via Microsoft Graph API for file", implementationModel = Extension.ImplementationModel.Krista_4_O)
@Containerize(baseImageVersion = "3.6.2-sp3")
@Java(version = Java.Version.JAVA_21)
@ChangeLog(file = "resources/docs/pages/release_notes.md")
@StaticResource(path = "docs", file = "docs")
public class OnedriveExtension {

    private static final Logger LOGGER = LoggerFactory.getLogger(OnedriveExtension.class);

    private final OnedriveSetUpService onedriveSetUpService;
    private final OnedriveRequestAuthenticator requestAuthenticator;
    private final OnedriveSubscriptionCleanupService cleanupService;
    private final Invoker invoker;
    private final OnedriveAttributeStore onedriveAttributeStore;
    private final KeyValueStore keyValueStore;

    @Inject
    public OnedriveExtension(OnedriveSetUpService onedriveSetUpService,
                              Invoker invoker, OnedriveAttributeStore attributeStore,
                              GraphServiceClientProviderFactory providerFactory,
                              AuthorizationContext authorizationContext,
                              OnedriveSubscriptionCleanupService cleanupService,
                              KeyValueStore keyValueStore) {
        this.onedriveSetUpService = onedriveSetUpService;
        this.cleanupService = cleanupService;
        this.invoker = invoker;
        this.onedriveAttributeStore = attributeStore;
        this.keyValueStore = keyValueStore;
        String routingUrl = invoker.getRoutingInfo().getRoutingURL(HttpProtocol.PROTOCOL_NAME, RoutingInfo.Type.APPLIANCE);
        this.requestAuthenticator = new OnedriveRequestAuthenticator(attributeStore, invoker.getInvokerId(), routingUrl);
    }

    @InvokerRequest(InvokerRequest.Type.AUTHENTICATOR)
    public RequestAuthenticator getRequestAuthenticator() {
        return requestAuthenticator;
    }

    @InvokerRequest(InvokerRequest.Type.CUSTOM_TABS)
    public Map<String, String> customTabs() {
        return Map.of("Authentication", "rest/onedrive/authentication/",
                "Documentation", "static/docs",
                "OneDrive Audit", "rest/onedrive/audit/","AI Assistant", "rest/onedrive/mcp-setup");
    }

    @InvokerRequest(InvokerRequest.Type.TEST_CONNECTION)
    public void testConnection() {
        throw new IllegalArgumentException("Please use Authentication tab for test connection.");
    }

    @InvokerRequest(InvokerRequest.Type.INVOKER_UPDATED)
    public void attributesUpdated(Map<String, Object> oldAttributes, Map<String, Object> newAttributes) {
        onedriveSetUpService.attributesUpdated(oldAttributes, newAttributes);
    }

    /**
     * Handles invoker removal by delegating to cleanup service.
     *
     * <p>The cleanup service will:
     * <ul>
     *   <li>Discover all subscriptions for this invoker</li>
     *   <li>Delete subscriptions from Microsoft Graph</li>
     *   <li>Clean up local storage (KeyValueStore + cache)</li>
     *   <li>Remove stored credentials</li>
     * </ul>
     * </p>
     */
    @InvokerRequest(InvokerRequest.Type.INVOKER_REMOVED)
    public void remove() {
        cleanupService.handleInvokerRemoval();
    }

    @InvokerRequest(InvokerRequest.Type.INVOKER_LOADED)
    public void load() {
        LOGGER.info("Migrating attributes from invoker to store:");
        this.saveAttributesInStore();
    }

    public void saveAttributesInStore() {
        String emailFromInvoker = InvokerAttributes.getStringOrNull(invoker.getAttributes(), Constants.EMAIL);
        OnedriveAttributes onedriveAttributes = onedriveAttributeStore.load(invoker.getInvokerId());
        if (onedriveAttributes == null && emailFromInvoker != null) {
            LOGGER.info("Started Migrating attributes from invoker to store for email: {}", emailFromInvoker);
            String routingUrl = invoker.getRoutingInfo().getRoutingURL(HttpProtocol.PROTOCOL_NAME, RoutingInfo.Type.APPLIANCE);
            // Old setup page stored user auth credentials (delegated OAuth with own app).
            // Set authType=Public + publicAuthMode=own so the new auth tab shows them
            // under "User Authentication" instead of "Server to Server".
            String clientId = InvokerAttributes.getStringOrNull(invoker.getAttributes(), Constants.CLIENT_ID);
            String clientSecret = InvokerAttributes.getStringOrNull(invoker.getAttributes(), Constants.CLIENT_SECRET);
            String tenantId = InvokerAttributes.getStringOrNull(invoker.getAttributes(), Constants.TENANT_ID);
            OnedriveAttributes attributes = new OnedriveAttributes(routingUrl, clientId, clientSecret,
                    emailFromInvoker, tenantId, Constants.PUBLIC);
            attributes.setPublicAuthMode("own");
            onedriveAttributeStore.save(attributes, invoker.getInvokerId());
            String key = emailFromInvoker + Constants.UNDER_SCORE + attributes.getClientId() + Constants.UNDER_SCORE + attributes.getAuthType();
            keyValueStore.put(key, keyValueStore.get(emailFromInvoker));
            LOGGER.info("Completed Migrating attributes from invoker to store for email: {}", emailFromInvoker);
        }
    }

}
