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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.Map;
import java.util.UUID;

@Service
public class OnedriveAttributeStore {

    private static final Logger LOGGER = LoggerFactory.getLogger(OnedriveAttributeStore.class);
    private final Invoker invoker;
    private final KeyValueStore store;

    @Inject
    public OnedriveAttributeStore(Invoker invoker, KeyValueStore store) {
        this.invoker = invoker;
        this.store = store;
    }

    @SuppressWarnings("unchecked")
    public OnedriveAttributes load(String authContextId) {
        LOGGER.debug("load: Loading attributes for key: {}", authContextId);

        String loadedAttributes = null;
        if (store.get(authContextId) != null) {
            loadedAttributes = (String) store.get(authContextId);
        }

        if (loadedAttributes == null || loadedAttributes.isBlank()) {
            LOGGER.debug("load: No attributes found, returning null");
            return null;
        }

        try {
            Map<String, Object> attributes = Constants.GSON.fromJson(loadedAttributes, Map.class);
            String baseUrl = invoker.getRoutingInfo().getRoutingURL(HttpProtocol.PROTOCOL_NAME, RoutingInfo.Type.APPLIANCE);
            String authType = (String) attributes.get(Constants.AUTH_TYPE);

            OnedriveAttributes result = new OnedriveAttributes(
                    baseUrl,
                    (String) attributes.get(Constants.CLIENT_ID),
                    (String) attributes.get(Constants.CLIENT_SECRET),
                    (String) attributes.get(Constants.EMAIL),
                    (String) attributes.get(Constants.TENANT_ID),
                    authType,
                    (String) attributes.get(Constants.CERTIFICATE_CONTENT),
                    (String) attributes.get(Constants.CERTIFICATE_PASSWORD));
            String publicAuthMode = (String) attributes.get("publicAuthMode");
            if (publicAuthMode != null) {
                result.setPublicAuthMode(publicAuthMode);
            }
            return result;
        } catch (Exception e) {
            LOGGER.error("load: Error parsing JSON attributes: {}", e.getMessage(), e);
            return null;
        }
    }

    public String save(OnedriveAttributes onedriveAttributes) {
        String authContextId = UUID.randomUUID().toString();
        store.put(authContextId, Constants.GSON.toJson(buildAttributeMap(onedriveAttributes)));
        LOGGER.info("save: Attributes saved with authContextId: {}", authContextId);
        return authContextId;
    }

    public boolean save(OnedriveAttributes onedriveAttributes, String invokerId) {
        LOGGER.debug("save: Saving attributes with invokerId: {}", invokerId);
        try {
            store.put(invokerId, Constants.GSON.toJson(buildAttributeMap(onedriveAttributes)));
            LOGGER.info("save: Attributes saved for invokerId: {}", invokerId);
            return true;
        } catch (Exception e) {
            LOGGER.error("save: Failed to save attributes for invokerId: {}, error: {}", invokerId, e.getMessage(), e);
            return false;
        }
    }

    private Map<String, Object> buildAttributeMap(OnedriveAttributes attr) {
        Map<String, Object> map = new java.util.HashMap<>();
        map.put(Constants.CLIENT_ID, attr.getClientId() != null ? attr.getClientId() : "");
        map.put(Constants.CLIENT_SECRET, attr.getClientSecret() != null ? attr.getClientSecret() : "");
        map.put(Constants.TENANT_ID, attr.getTenantId() != null ? attr.getTenantId() : "");
        map.put(Constants.EMAIL, attr.getEmail() != null ? attr.getEmail() : "");
        map.put(Constants.AUTH_TYPE, attr.getAuthType() != null ? attr.getAuthType() : "");
        if (attr.getPublicAuthMode() != null) {
            map.put("publicAuthMode", attr.getPublicAuthMode());
        }
        if (attr.getCertificateContent() != null) {
            map.put(Constants.CERTIFICATE_CONTENT, attr.getCertificateContent());
        }
        if (attr.getCertificatePassword() != null) {
            map.put(Constants.CERTIFICATE_PASSWORD, attr.getCertificatePassword());
        }
        return map;
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
