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

package app.krista.extensions.essentials.cloudfiles.onedrive.catalog;

import app.krista.extension.executor.ExtensionResponse;
import app.krista.extension.executor.ExtensionResponseBuilder;
import app.krista.extension.impl.anno.Attribute;
import app.krista.extension.impl.anno.CatalogRequest;
import app.krista.extension.impl.anno.Domain;
import app.krista.extension.impl.anno.Field;
import app.krista.extensions.essentials.cloudfiles.onedrive.OnedriveAttributes;
import app.krista.extensions.essentials.cloudfiles.onedrive.OnedriveSetUpService;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.stores.RefreshTokenStore;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.util.Constants;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.Map;

@Domain(id = "catEntryDomain_30c63e77-37f2-4a1d-afd8-f6bfda07ad57",
        name = "Cloud Files",
        ecosystemId = "catEntryEcosystem_84b53163-327b-4b1b-8c96-9334d292f9f5",
        ecosystemName = "Essentials",
        ecosystemVersion = "550ba0ed-7f26-4d47-8412-f1910a6ffccd")
public class SetupArea {
    private final OnedriveSetUpService onedriveSetUpService;
    private final OnedriveAttributes onedriveAttributes;
    private final RefreshTokenStore refreshTokenStore;

    @Inject
    public SetupArea(OnedriveSetUpService onedriveSetUpService, OnedriveAttributes onedriveAttributes, RefreshTokenStore refreshTokenStore) {
        this.onedriveSetUpService = onedriveSetUpService;
        this.onedriveAttributes = onedriveAttributes;
        this.refreshTokenStore = refreshTokenStore;
    }


    /**
     * Validates and saves OneDrive connection configuration.
     * This method validates the provided Azure AD credentials and establishes a connection to Microsoft OneDrive.
     * It performs OAuth 2.0 authentication flow and stores the configuration for subsequent operations.
     *
     * @param clientId Application (client) ID from Azure AD app registration
     * @param clientSecret Client secret value from Azure AD app registration
     * @param tenantId Directory (tenant) ID from Azure AD
     * @param email Email address of the OneDrive account to connect to
     * @return ExtensionResponse containing success status and message indicating whether configuration was saved successfully
     */
    @CatalogRequest(
            id = "localDomainRequest_54ef5f19-7695-4dfc-8613-451a1e79d313",
            name = "Save Configuration",
            description = "Validates and saves OneDrive connection configuration including Client ID, Client Secret, Tenant ID, and Email credentials for establishing secure connection to Microsoft OneDrive",
            area = "Setup",
            type = CatalogRequest.Type.CHANGE_SYSTEM)
    @Field.Text(name = "Message", required = false, attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'Status message indicating the result of the configuration save operation'")})
    @Field.Boolean(name = "Is Successful", required = false, attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'Indicates whether the configuration was successfully saved and validated'")})
    public ExtensionResponse saveConfiguration(
            @Field.Text(name = "Client Id", attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'Application (client) ID from your Azure AD app registration. This identifies your application to Microsoft\\'s identity platform'")}) String clientId,
            @Field.Text(name = "Client Secret", attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'Client secret value from your Azure AD app registration. This authenticates your application to Microsoft\\'s identity platform'")}) String clientSecret,
            @Field.Text(name = "Tenant Id", attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'Directory (tenant) ID from your Azure AD. This identifies the Azure AD tenant where your app is registered'")}) String tenantId,
            @Field(name = "Email", type = "Email", attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'Email address of the OneDrive account to connect to. This should be the same account used to register the application in Azure AD'")}) String email) {
        Map<String, Object> attributes = Map.of(Constants.CLIENT_ID, clientId, Constants.CLIENT_SECRET, clientSecret,
                Constants.TENANT_ID, tenantId, Constants.EMAIL, email);
        boolean isSaved;
        String message;
        isSaved = onedriveSetUpService.validateConnectionFromClientSide(attributes);
        refreshTokenStore.put(Constants.IS_FROM_CATALOG_REQUEST, "true");
        onedriveAttributes.update(attributes);
        if (isSaved)
            message = "User Authenticated Successfully.";
        else
            message = "User Authentication Failed. Please check the credentials.";

        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("Is Successful", isSaved);
        resultMap.put("Message", message);
        return new ExtensionResponseBuilder()
                .success(resultMap, null, null)
                .build();
    }

    /**
     * Tests the OneDrive connection to verify it is properly established.
     * This method attempts to connect to Microsoft OneDrive using the saved configuration
     * and verifies that the connection is working correctly.
     *
     * @return ExtensionResponse containing success status and message about the connection test result
     */

    @CatalogRequest(
            id = "localDomainRequest_833eb86f-3c64-4ad4-8042-d20ff1eedb73",
            name = "Test Connection",
            description = "Test the One Drive files connection is established.",
            area = "Setup",
            type = CatalogRequest.Type.CHANGE_SYSTEM)
    @Field.Boolean(name = "Is Successful", required = false, attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'Indicates whether the connection test was successful and OneDrive is accessible'")})
    @Field.Text(name = "Message", required = false, attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'Detailed message about the connection test result, including any error information if the test failed'")})
    public ExtensionResponse testConnection() {
        try {
            onedriveSetUpService.testConnection(false);
        } catch (Exception e) {
            return new ExtensionResponseBuilder()
                    .success(Map.of("Is Successful", false, "Message", "Failed to connect: " + e.getMessage()), null, null)
                    .build();
        }
        return new ExtensionResponseBuilder()
                .success(Map.of("Is Successful", true, "Message", "Connection Successful"), null, null)
                .build();
    }

    /**
     * Performs a health check on the OneDrive extension.
     * This method verifies the overall health status of the OneDrive integration by checking
     * connectivity, configuration validity, and system readiness.
     *
     * @return ExtensionResponse containing health status information including overall health indicator
     */

    @CatalogRequest(
            id = "localDomainRequest_cb132352-9202-4db6-ae68-e89af67de34b",
            name = "Health Check",
            description = "This 'Health Check' request verifies the health status of One Drive by calling the health check API.",
            area = "Setup",
            type = CatalogRequest.Type.CHANGE_SYSTEM)
    @Field.Desc(name = "Health Status", type = "Entity(Health Status)", required = false)
    @Field.Desc(name = "Extension Response Meta", type = "Entity(Extension Response Meta)", required = false)
    @Field.Boolean(name = "Is Healthy", required = false, attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'Overall health status indicator - true if system is operating normally, false if issues are detected'")})
    public ExtensionResponse healthCheck() {
        return onedriveSetUpService.checkHealth();
    }
}