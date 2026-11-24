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

import app.krista.extension.authorization.MustAuthorizeException;
import app.krista.extension.executor.ExtensionResponse;
import app.krista.extension.executor.ExtensionResponseBuilder;
import app.krista.extension.executor.Invoker;
import app.krista.extension.impl.anno.Attribute;
import app.krista.extension.impl.anno.CatalogRequest;
import app.krista.extension.impl.anno.Domain;
import app.krista.extension.impl.anno.Field;
import app.krista.extensions.essentials.cloudfiles.onedrive.OnedriveSetUpService;
import app.krista.extensions.essentials.cloudfiles.onedrive.audit.AuditEventType;
import app.krista.extensions.essentials.cloudfiles.onedrive.audit.OneDriveAuditService;

import javax.inject.Inject;
import java.util.Map;

@Domain(id = "catEntryDomain_30c63e77-37f2-4a1d-afd8-f6bfda07ad57",
        name = "Cloud Files",
        ecosystemId = "catEntryEcosystem_84b53163-327b-4b1b-8c96-9334d292f9f5",
        ecosystemName = "Essentials",
        ecosystemVersion = "550ba0ed-7f26-4d47-8412-f1910a6ffccd")
public class SetupArea {
    private final OnedriveSetUpService onedriveSetUpService;
    private final OneDriveAuditService auditService;
    private final String invokerId;

    @Inject
    public SetupArea(OnedriveSetUpService onedriveSetUpService,
                     OneDriveAuditService auditService,
                     Invoker invoker) {
        this.onedriveSetUpService = onedriveSetUpService;
        this.auditService = auditService;
        this.invokerId = invoker.getInvokerId();
    }

    @CatalogRequest(
            id = "localDomainRequest_833eb86f-3c64-4ad4-8042-d20ff1eedb73",
            name = "Test Connection",
            description = "Test the One Drive files connection is established.",
            area = "Setup",
            type = CatalogRequest.Type.CHANGE_SYSTEM)
    @Field.Boolean(name = "Is Successful", required = false, attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'Indicates whether the connection test was successful and OneDrive is accessible'")})
    @Field.Text(name = "Message", required = false, attributes = {@Attribute(name = "visualWidth", value = "L"), @Attribute(name = "toolTip", value = "'Detailed message about the connection test result, including any error information if the test failed'")})
    public ExtensionResponse testConnection() {
        auditService.startRequest(invokerId, "Test Connection");
        try {
            onedriveSetUpService.testConnection(false);
            auditService.record(invokerId, AuditEventType.CATALOG_REQUEST_SUCCESS, "success", "Test Connection", null, null, null);
        } catch (MustAuthorizeException cause) {
            auditService.record(invokerId, AuditEventType.CATALOG_REQUEST_FAILURE, "failure", "Test Connection",
                    cause.getMessage(), cause.getClass().getSimpleName(), null);
            throw cause;
        } catch (Exception e) {
            auditService.record(invokerId, AuditEventType.CATALOG_REQUEST_FAILURE, "failure", "Test Connection",
                    e.getMessage(), e.getClass().getSimpleName(), null);
            return new ExtensionResponseBuilder()
                    .success(Map.of("Is Successful", false, "Message", "Failed to connect: " + e.getMessage()), null, null)
                    .build();
        }
        return new ExtensionResponseBuilder()
                .success(Map.of("Is Successful", true, "Message", "Connection Successful"), null, null)
                .build();
    }

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
        auditService.startRequest(invokerId, "Health Check");
        try {
            ExtensionResponse response = onedriveSetUpService.checkHealth();
            auditService.record(invokerId, AuditEventType.CATALOG_REQUEST_SUCCESS, "success", "Health Check", null, null, null);
            return response;
        } catch (MustAuthorizeException cause) {
            auditService.record(invokerId, AuditEventType.CATALOG_REQUEST_FAILURE, "failure", "Health Check",
                    cause.getMessage(), cause.getClass().getSimpleName(), null);
            throw cause;
        } catch (Exception cause) {
            auditService.record(invokerId, AuditEventType.CATALOG_REQUEST_FAILURE, "failure", "Health Check",
                    cause.getMessage(), cause.getClass().getSimpleName(), null);
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            throw new RuntimeException(cause);
        }
    }
}
