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
import app.krista.extension.util.InvokerAttributes;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.util.Constants;
import app.krista.sdk.msauth.extension.base.MsAuthAttributes;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import org.jvnet.hk2.annotations.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

@Service
public class OnedriveAttributes implements MsAuthAttributes {

    private static final Logger LOGGER = LoggerFactory.getLogger(OnedriveAttributes.class);

    private final String routingUrl;
    @SerializedName(Constants.CLIENT_ID)
    private String clientId;
    @SerializedName(Constants.CLIENT_SECRET)
    private String clientSecret;
    @SerializedName(Constants.EMAIL)
    private String email;
    @SerializedName(Constants.TENANT_ID)
    private String tenantId;
    @SerializedName(Constants.AUTH_TYPE)
    private String authType;
    @SerializedName(Constants.CERTIFICATE_CONTENT)
    private String certificateContent;
    @SerializedName(Constants.CERTIFICATE_PASSWORD)
    private String certificatePassword;
    private String publicClientId;
    private String publicClientSecret;
    private String publicAuthMode;

    @Inject
    public OnedriveAttributes(Invoker invoker) {
        this(invoker.getRoutingInfo().getRoutingURL(HttpProtocol.PROTOCOL_NAME, RoutingInfo.Type.APPLIANCE),
                invoker.getAttributes());
    }

    public OnedriveAttributes(String routingUrl, Map<String, Object> attributes) {
        this(routingUrl, InvokerAttributes.getStringOrNull(attributes, Constants.CLIENT_ID),
                InvokerAttributes.getStringOrNull(attributes, Constants.CLIENT_SECRET),
                InvokerAttributes.getStringOrNull(attributes, Constants.EMAIL),
                InvokerAttributes.getStringOrNull(attributes, Constants.TENANT_ID));
    }

    public OnedriveAttributes(String routingUrl, String clientId, String clientSecret, String email, String tenantId) {
        this(routingUrl, clientId, clientSecret, email, tenantId, null);
    }

    public OnedriveAttributes(String routingUrl, String clientId, String clientSecret, String email,
                              String tenantId, String authType) {
        loadPublicConfig();
        this.routingUrl = routingUrl.contains(Constants.LOCAL_EXTN_URL)
                ? routingUrl.replace(Constants.LOCAL_EXTN_URL, Constants.LOCAL_EXTN_REPLACE_URL)
                : routingUrl;
        this.clientId = clientId == null ? publicClientId : clientId;
        this.clientSecret = clientSecret == null ? publicClientSecret : clientSecret;
        this.email = email;
        this.tenantId = tenantId;
        this.authType = authType;
    }

    public OnedriveAttributes(String routingUrl, String clientId, String clientSecret, String email,
                              String tenantId, String authType, String certificateContent, String certificatePassword) {
        this(routingUrl, clientId, clientSecret, email, tenantId, authType);
        this.certificateContent = certificateContent;
        this.certificatePassword = certificatePassword;
    }

    public static OnedriveAttributes create(String routingUrl, Map<String, Object> attributes) {
        return new OnedriveAttributes(routingUrl, attributes);
    }

    public static OnedriveAttributes create(JsonObject authPayload, String baseUrl) {
        String authType = authPayload.get(Constants.AUTH_TYPE).getAsString();
        if (Constants.PUBLIC.equals(authType)) {
            String publicAuthMode = authPayload.has("publicAuthMode") ? authPayload.get("publicAuthMode").getAsString() : "krista";
            if ("own".equals(publicAuthMode)) {
                OnedriveAttributes attrs = new OnedriveAttributes(
                        baseUrl,
                        authPayload.get(Constants.CLIENT_ID).getAsString(),
                        authPayload.get(Constants.CLIENT_SECRET).getAsString(),
                        authPayload.get(Constants.EMAIL).getAsString(),
                        authPayload.has(Constants.TENANT_ID) && !authPayload.get(Constants.TENANT_ID).getAsString().isBlank()
                                ? authPayload.get(Constants.TENANT_ID).getAsString() : null,
                        authType);
                attrs.publicAuthMode = publicAuthMode;
                return attrs;
            }
            OnedriveAttributes attrs = new OnedriveAttributes(baseUrl, null, null,
                    authPayload.get(Constants.EMAIL).getAsString(), null, authType);
            attrs.publicAuthMode = publicAuthMode;
            return attrs;
        } else if (Constants.PRIVATE.equals(authType)) {
            return new OnedriveAttributes(
                    baseUrl,
                    authPayload.get(Constants.CLIENT_ID).getAsString(),
                    authPayload.get(Constants.CLIENT_SECRET).getAsString(),
                    authPayload.get(Constants.EMAIL).getAsString(),
                    authPayload.get(Constants.TENANT_ID).getAsString(),
                    authType);
        } else if (Constants.CLIENT_CREDENTIALS_CERT.equals(authType)) {
            String certPassword = authPayload.has(Constants.CERTIFICATE_PASSWORD)
                    && !authPayload.get(Constants.CERTIFICATE_PASSWORD).getAsString().isBlank()
                    ? authPayload.get(Constants.CERTIFICATE_PASSWORD).getAsString() : null;
            return new OnedriveAttributes(
                    baseUrl,
                    authPayload.get(Constants.CLIENT_ID).getAsString(),
                    "",  // no client secret for certificate auth
                    authPayload.get(Constants.EMAIL).getAsString(),
                    authPayload.get(Constants.TENANT_ID).getAsString(),
                    authType,
                    authPayload.get(Constants.CERTIFICATE_CONTENT).getAsString(),
                    certPassword);
        } else {
            throw new IllegalArgumentException(Constants.UNSUPPORTED_AUTH);
        }
    }

    private void loadPublicConfig() {
        Properties properties = new Properties();
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream("config.properties")) {
            if (inputStream != null) {
                properties.load(inputStream);
                this.publicClientId = properties.getProperty("public.clientId");
                this.publicClientSecret = properties.getProperty("public.clientSecret");
            }
        } catch (IOException cause) {
            throw new RuntimeException("Failed to load public configuration file.", cause);
        }
    }

    public void update(Map<String, Object> newAttributes) {
        clientId = InvokerAttributes.getStringOrNull(newAttributes, Constants.CLIENT_ID);
        clientSecret = InvokerAttributes.getStringOrNull(newAttributes, Constants.CLIENT_SECRET);
        email = InvokerAttributes.getStringOrNull(newAttributes, Constants.EMAIL);
        tenantId = InvokerAttributes.getStringOrNull(newAttributes, Constants.TENANT_ID);
    }

    public String getClientId() {
        return clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getEmail() {
        return email;
    }

    /**
     * @deprecated Use {@link #getEmail()} instead
     */
    @Deprecated
    public String getMailId() {
        return email;
    }

    public String getAuthType() {
        return authType;
    }

    public String getPublicAuthMode() {
        return publicAuthMode;
    }

    public void setPublicAuthMode(String publicAuthMode) {
        this.publicAuthMode = publicAuthMode;
    }

    public String getCertificateContent() {
        return certificateContent;
    }

    public String getCertificatePassword() {
        return certificatePassword;
    }

    public byte[] getCertificateBytes() {
        if (certificateContent == null || certificateContent.isBlank()) return null;
        return Base64.getDecoder().decode(certificateContent.trim());
    }

    /**
     * Returns true if this is an app-only (non-delegated) authentication flow.
     * Both Client Credentials Secret (Private) and Certificate flows are app-only.
     */
    public boolean isAppOnlyAuth() {
        return Constants.PRIVATE.equals(authType) || Constants.CLIENT_CREDENTIALS_CERT.equals(authType);
    }

    @Override
    public String getScopeString() {
        return Constants.REQUIRED_SCOPE;
    }

    public String getCallbackEndPoint() {
        if (Constants.PUBLIC.equals(authType)) {
            LOGGER.debug("getCallbackEndPoint: Using PUBLIC routing URL: {}", routingUrl);
            // Explicit "own" check first; if publicAuthMode was not persisted (old records),
            // fall back to clientId inference: if the stored clientId differs from the shared
            // publicClientId, the user supplied their own app credentials ("own" / User Auth).
            boolean isOwn = "own".equals(publicAuthMode)
                    || (publicAuthMode == null && clientId != null && publicClientId != null && !clientId.equals(publicClientId));
            if (isOwn) {
                return routingUrl + Constants.EXTENSION_FORWARD_PATH;
            }
            return routingUrl.replaceAll("/extension/api/.*", Constants.DEFAULT_CALLBACK_PATH);
        } else if (Constants.PRIVATE.equals(authType)) {
            return routingUrl + "/rest/onedrive/callback";
        } else {
            return getCallbackUrl();
        }
    }

    public String getForwardPath() {
        if (Constants.PUBLIC.equals(authType)) {
            return routingUrl + Constants.EXTENSION_FORWARD_PATH;
        } else {
            return Constants.EMPTY_STRING;
        }
    }

    public String getCallbackUrl() {
        return routingUrl + Constants.REST_OUTLOOK_CALLBACK;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> attributesMap = new HashMap<>();
        addIfNotNull(attributesMap, Constants.EMAIL, getEmail());
        addIfNotNull(attributesMap, Constants.AUTH_TYPE, getAuthType());
        addIfNotNull(attributesMap, "publicAuthMode", publicAuthMode);
        // Include credentials only for flows where the user supplied their own Azure AD app.
        // Quick Setup (Krista Managed) credentials must never appear in the map.
        boolean includeCredentials = "own".equals(publicAuthMode)
                || Constants.PRIVATE.equals(authType)
                || Constants.CLIENT_CREDENTIALS_CERT.equals(authType);
        if (includeCredentials) {
            addIfNotNull(attributesMap, Constants.CLIENT_ID, getClientId());
            addIfNotNull(attributesMap, Constants.CLIENT_SECRET, getClientSecret());
            addIfNotNull(attributesMap, Constants.TENANT_ID, getTenantId());
        }
        if (Constants.CLIENT_CREDENTIALS_CERT.equals(authType)) {
            addIfNotNull(attributesMap, Constants.CERTIFICATE_CONTENT, getCertificateContent());
            addIfNotNull(attributesMap, Constants.CERTIFICATE_PASSWORD, getCertificatePassword());
        }
        return attributesMap;
    }

    private static void addIfNotNull(Map<String, Object> map, String key, Object value) {
        if (value != null) {
            map.put(key, value);
        }
    }

}
