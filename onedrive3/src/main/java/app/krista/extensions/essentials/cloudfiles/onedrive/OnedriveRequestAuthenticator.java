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

import app.krista.extension.authorization.MustAuthenticateException;
import app.krista.extension.authorization.MustAuthorizeException;
import app.krista.extension.authorization.RequestAuthenticator;
import app.krista.extension.request.ProtoRequest;
import app.krista.extension.request.ProtoResponse;
import app.krista.extension.request.protos.http.HttpRequest;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.stores.OnedriveAttributeStore;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.util.Constants;
import app.krista.model.field.NamedField;
import app.krista.model.field.NamedValuedField;
import com.github.scribejava.core.oauth.OAuth20Service;
import org.jvnet.hk2.annotations.Service;

import javax.inject.Inject;
import javax.ws.rs.core.MultivaluedMap;
import java.io.IOException;
import java.util.*;

@Service
public class OnedriveRequestAuthenticator implements RequestAuthenticator {

    private static final String ONEDRIVE_CALLBACK = "/onedrive/callback";
    private final OnedriveAttributeStore onedriveAttributeStore;
    private final OnedriveAttributes onedriveAttributes;

    @Inject
    public OnedriveRequestAuthenticator(OnedriveAttributeStore onedriveAttributeStore, OnedriveAttributes onedriveAttributes) {
        this.onedriveAttributeStore = onedriveAttributeStore;
        this.onedriveAttributes = onedriveAttributes;
    }

    @Override
    public String getScheme() {
        return null;
    }

    @Override
    public Set<String> getSupportedProtocols() {
        return Set.of();
    }

    @Override
    public String getAuthenticatedAccountId(ProtoRequest protoRequest) {
        try {
            HttpRequest httpRequest = (HttpRequest) protoRequest;
            if (ONEDRIVE_CALLBACK.equals(httpRequest.getUri().getPath())) {
                httpRequest.bufferBody();
                MultivaluedMap<String, String> queryParameters = httpRequest.getQueryParameters();
                String state = String.valueOf(queryParameters.get(Constants.STATE).getFirst());
                String[] parts = state.split(Constants.HASH);
                return parts[0];
            } else {
                return null;
            }
        } catch (RuntimeException | IOException cause) {
            return null;
        }
    }

    @Override
    public boolean setServiceAuthorization(String s) {
        return false;
    }

    @Override
    public Map<String, NamedField> getAttributeFields() {
        return Map.of();
    }

    @Override
    public ProtoResponse getMustAuthenticateResponse(MustAuthenticateException cause, ProtoRequest request) {
        return null;
    }

    @Override
    public AuthorizationResponse getMustAuthenticateResponse(MustAuthenticateException cause) {
        return null;
    }

    @Override
    public ProtoResponse getMustAuthorizeResponse(MustAuthorizeException cause, ProtoRequest request) {
        return null;
    }

    @Override
    public AuthorizationResponse getMustAuthorizeResponse(MustAuthorizeException cause) {
        String userId = (String) cause.getDetails().getFirst().getValue();
        Optional<NamedValuedField> authContextIdField = cause.getDetails().stream().filter(namedValuedField -> Objects.equals(namedValuedField.getName(), Constants.AUTH_CONTEXT_ID)).findFirst();

        OnedriveAttributes effectiveOnedriveAttributes;
        String state;
        if (authContextIdField.isPresent()) {
            String authContextId = (String) authContextIdField.get().getValue();
            effectiveOnedriveAttributes = onedriveAttributeStore.load(authContextId);
            state = userId + Constants.HASH + authContextId;
        } else {
            effectiveOnedriveAttributes = onedriveAttributes;
            state = userId;
        }
        OAuth20Service oAuth20Service = effectiveOnedriveAttributes.getOAuth20Service();
        String url = oAuth20Service.getAuthorizationUrl(state);
        return new AuthorizationResponse(url + Constants.AUTH_URL_QUERY_PARAMS, Collections.emptyList());
    }
}
