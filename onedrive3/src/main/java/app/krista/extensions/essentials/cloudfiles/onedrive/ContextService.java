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

import app.krista.extension.executor.Invoker;
import app.krista.extension.request.RoutingInfo;
import app.krista.extension.request.protos.http.HttpProtocol;
import app.krista.ksdk.context.AuthorizationContext;
import org.jvnet.hk2.annotations.Service;

import javax.inject.Inject;

/**
 * Service for accessing request context information including account details and routing URLs.
 *
 * <p>Provides access to:
 * <ul>
 *   <li>Current authorized account information</li>
 *   <li>Routing URLs with environment-specific handling</li>
 * </ul>
 *
 * @author Krista Extensions Team
 * @since 1.0
 */
@Service
public class ContextService {

    private final Invoker invoker;
    private final AuthorizationContext authorizationContext;

    @Inject
    public ContextService(Invoker invoker, AuthorizationContext authorizationContext) {
        this.invoker = invoker;
        this.authorizationContext = authorizationContext;
    }

    public String getAccountId(){
        return authorizationContext.getAuthorizedAccount().getAccountId();
    }


    public String getRoutingUrl() {
        return invoker.getRoutingInfo().getRoutingURL(HttpProtocol.PROTOCOL_NAME, RoutingInfo.Type.APPLIANCE)
                .contains("extension.local.eng.krista.app") ?
                invoker.getRoutingInfo().getRoutingURL(HttpProtocol.PROTOCOL_NAME, RoutingInfo.Type.APPLIANCE)
                        .replace("https://extension.local.eng.krista.app", "http://localhost:8765") :
                invoker.getRoutingInfo().getRoutingURL(HttpProtocol.PROTOCOL_NAME, RoutingInfo.Type.APPLIANCE);
    }
}