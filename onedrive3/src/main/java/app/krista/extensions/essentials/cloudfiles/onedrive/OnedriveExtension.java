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
import app.krista.extension.impl.anno.*;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.stores.RefreshTokenStore;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.util.Constants;

import javax.inject.Inject;
import java.io.IOException;
import java.util.Map;

@Field.Text(value = Constants.CLIENT_ID)
@Field.Text(value = Constants.CLIENT_SECRET, isSecured = true)
@Field.Text(value = Constants.TENANT_ID, isSecured = true)
@Field.Text(value = Constants.EMAIL)
@Extension(version = "3.0.7")
@Java(version = Java.Version.JAVA_21)
@StaticResource(path = "docs", file = "docs")
public class OnedriveExtension {

    private final OnedriveSetUpService onedriveSetUpService;
    private final RefreshTokenStore refreshTokenStore;

    @Inject
    public OnedriveExtension(OnedriveSetUpService onedriveSetUpService, RefreshTokenStore refreshTokenStore) {
        this.onedriveSetUpService = onedriveSetUpService;
        this.refreshTokenStore = refreshTokenStore;
    }


    /**
     * Provides the OAuth 2.0 request authenticator for OneDrive.
     * This authenticator handles the OAuth 2.0 authorization code flow for user authentication.
     *
     * @return RequestAuthenticator configured for OneDrive OAuth 2.0 flow
     */

    @InvokerRequest(InvokerRequest.Type.AUTHENTICATOR)
    public RequestAuthenticator getRequestAuthenticator() {
        return onedriveSetUpService.getRequestAuthenticator();
    }


    /**
     * Defines custom tabs for the extension UI.
     * Adds a Documentation tab that displays the extension's static documentation.
     *
     * @return Map containing tab name and path (Documentation -> static/docs)
     */
    @InvokerRequest(InvokerRequest.Type.CUSTOM_TABS)
    public Map<String, String> customTabs() {
        return Map.of("Documentation", "static/docs");
    }


    /**
     * Validates the OneDrive connection configuration.
     * This method is called when the user clicks "Validate Attributes" in the extension configuration UI.
     * It verifies that the provided credentials are valid and can establish a connection to OneDrive.
     *
     * @param connectionAttributes Map containing clientId, clientSecret, tenantId, and email
     */
    @InvokerRequest(InvokerRequest.Type.VALIDATE_ATTRIBUTES)
    public void validateConnection(Map<String, Object> connectionAttributes) {
        onedriveSetUpService.validateConnection(connectionAttributes);
        refreshTokenStore.put(Constants.IS_FROM_CATALOG_REQUEST, "false");
    }


    /**
     * Tests the OneDrive connection.
     * This method is called when the user clicks "Test Connection" in the extension configuration UI.
     * It attempts to connect to OneDrive and retrieve basic information to verify connectivity.
     *
     * @throws IOException if the connection test fails or network issues occur
     */
    @InvokerRequest(InvokerRequest.Type.TEST_CONNECTION)
    public void testConnection() throws IOException {
        onedriveSetUpService.testConnection(true);
    }

    @InvokerRequest(InvokerRequest.Type.INVOKER_UPDATED)
    public void attributesUpdated(Map<String, Object> oldAttributes, Map<String, Object> newAttributes) {
        onedriveSetUpService.attributesUpdated(oldAttributes, newAttributes);
    }

}
