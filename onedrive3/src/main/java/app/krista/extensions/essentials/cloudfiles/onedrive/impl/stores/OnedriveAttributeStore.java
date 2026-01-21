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

package app.krista.extensions.essentials.cloudfiles.onedrive.impl.stores;

import app.krista.extension.executor.Invoker;
import app.krista.extension.request.RoutingInfo;
import app.krista.extension.request.protos.http.HttpProtocol;
import app.krista.extensions.essentials.cloudfiles.onedrive.OnedriveAttributes;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.util.Constants;
import app.krista.extensions.util.KeyValueStore;
import org.jvnet.hk2.annotations.Service;

import javax.inject.Inject;
import java.util.Map;
import java.util.UUID;

@Service
public class OnedriveAttributeStore {

    private final Invoker invoker;
    private final KeyValueStore store;

    @Inject
    public OnedriveAttributeStore(Invoker invoker, KeyValueStore store) {
        this.invoker = invoker;
        this.store = store;
    }

    public OnedriveAttributes load(String authContextId) {
        var attributes = Constants.GSON.fromJson(String.valueOf(store.get(authContextId)), Map.class);
        return new OnedriveAttributes(invoker.getRoutingInfo().getRoutingURL(HttpProtocol.PROTOCOL_NAME, RoutingInfo.Type.APPLIANCE),
                (String) attributes.get(Constants.CLIENT_ID),
                (String) attributes.get(Constants.CLIENT_SECRET),
                (String) attributes.get(Constants.EMAIL),
                (String) attributes.get(Constants.TENANT_ID));
    }

    public String save(OnedriveAttributes onedriveAttributes) {
        String authContextId = UUID.randomUUID().toString();
        store.put(authContextId, Constants.GSON.toJson(Map.of(Constants.CLIENT_ID, onedriveAttributes.getClientId(),
                Constants.CLIENT_SECRET, onedriveAttributes.getClientSecret(),
                Constants.TENANT_ID, onedriveAttributes.getTenantId(),
                Constants.EMAIL, onedriveAttributes.getMailId())));
        return authContextId;
    }

    /**
     * Removes given authContext id from KeyValueStore
     *
     * @param authContextId authContext id
     */
    public void remove(String authContextId) {
        store.remove(authContextId);
    }

}
