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

package app.krista.extensions.essentials.cloudfiles.onedrive.api;

import app.krista.extension.authorization.MustAuthorizeException;
import app.krista.extension.executor.Invoker;
import app.krista.extension.request.RoutingInfo;
import app.krista.extension.request.protos.http.HttpProtocol;
import app.krista.extensions.essentials.cloudfiles.onedrive.OnedriveAttributes;
import app.krista.extensions.essentials.cloudfiles.onedrive.audit.AuditData;
import app.krista.extensions.essentials.cloudfiles.onedrive.audit.AuditRecord;
import app.krista.extensions.essentials.cloudfiles.onedrive.audit.OneDriveAuditService;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.connectors.GraphServiceClientProviderFactory;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.stores.OnedriveAttributeStore;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.util.Constants;
import app.krista.extensions.util.KeyValueStore;
import com.microsoft.graph.models.Subscription;
import com.microsoft.graph.requests.SubscriptionCollectionPage;

import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * JAX-RS resource that serves the OneDrive Audit Dashboard.
 *
 * Routes:
 *   GET  /audit/            → serves the audit dashboard HTML (classpath: audit/index.html)
 *   GET  /audit/data        → JSON audit records + summary (supports from/to/type/status params)
 *   GET  /audit/health      → health/status info
 *   DELETE /audit/data      → clears records in the given time range
 */
@Path("/audit")
public class OneDriveAuditResource {

    private static final com.google.gson.Gson GSON_COMPACT = new com.google.gson.Gson();

    private final OneDriveAuditService auditService;
    private final OnedriveAttributeStore attributeStore;
    private final KeyValueStore keyValueStore;
    private final GraphServiceClientProviderFactory providerFactory;
    private final String invokerId;
    private final String baseRoutingUrl;

    @Inject
    public OneDriveAuditResource(OneDriveAuditService auditService,
                                 OnedriveAttributeStore attributeStore,
                                 KeyValueStore keyValueStore,
                                 GraphServiceClientProviderFactory providerFactory,
                                 Invoker invoker) {
        this.auditService      = auditService;
        this.attributeStore    = attributeStore;
        this.keyValueStore     = keyValueStore;
        this.providerFactory   = providerFactory;
        this.invokerId         = invoker.getInvokerId();
        this.baseRoutingUrl    = invoker.getRoutingInfo().getRoutingURL(
                HttpProtocol.PROTOCOL_NAME, RoutingInfo.Type.APPLIANCE);
    }

    /**
     * Serves the audit dashboard HTML or JSON data depending on the sub-path.
     */
    @GET
    @Path("{subPath:.*}")
    public Response auditDashboard(
            @PathParam("subPath") String subPath,
            @QueryParam("from")   Long   from,
            @QueryParam("to")     Long   to,
            @QueryParam("type")   String type,
            @QueryParam("status") String status) {

        if ("data".equals(subPath)) {
            AuditData data = auditService.getData(invokerId, from, to, type, status);
            return Response.ok(GSON_COMPACT.toJson(data), MediaType.APPLICATION_JSON).build();
        }

        if ("health".equals(subPath)) {
            Map<String, Object> health = buildHealthResponse();
            return Response.ok(GSON_COMPACT.toJson(health), MediaType.APPLICATION_JSON).build();
        }

        if ("globalCounter".equals(subPath)) {
            Map<String, Object> counter = auditService.getGlobalCounter();
            return Response.ok(GSON_COMPACT.toJson(counter), MediaType.APPLICATION_JSON).build();
        }

        // Serve the React SPA HTML from classpath
        InputStream html = getClass().getClassLoader().getResourceAsStream("audit/index.html");
        if (html == null) {
            return Response.status(404).entity("Audit dashboard not found. Build the UI first.").build();
        }
        return Response.ok(html, MediaType.TEXT_HTML).build();
    }

    /**
     * Builds the credential health response used by the audit dashboard.
     * Returns configuration details, token health event counts, and auth stats.
     */
    private Map<String, Object> buildHealthResponse() {
        Map<String, Object> health = new HashMap<>();

        // ── Credential config ───────────────────────────────────────────────
        try {
            OnedriveAttributes attrs = attributeStore.load(invokerId);
            if (attrs != null) {
                health.put("authType", attrs.getAuthType());
                health.put("email",    attrs.getEmail());
                health.put("tenantId", mask(attrs.getTenantId()));
                health.put("clientId", mask(attrs.getClientId()));
                // Client credentials (Private) don't use refresh tokens
                if ("Private".equals(attrs.getAuthType())) {
                    health.put("hasRefreshToken", true);
                } else {
                    String email = attrs.getEmail();
                    String compositeKey = app.krista.sdk.msauth.MicrosoftAuthSdk.buildTokenKey(
                            email, attrs.getClientId(), attrs.getAuthType());
                    String refreshToken = keyValueStore.get(compositeKey, String.class);
                    if (refreshToken == null || refreshToken.isEmpty()) {
                        refreshToken = keyValueStore.get(email, String.class);
                    }
                    health.put("hasRefreshToken", refreshToken != null && !refreshToken.isEmpty());
                }
            } else {
                health.put("authType", "not configured");
                health.put("hasRefreshToken", false);
            }
        } catch (MustAuthorizeException e) {
            throw e;
        } catch (Exception e) {
            health.put("authType", "error");
            health.put("hasRefreshToken", false);
        }

        // ── Auth event counts from audit records ────────────────────────────
        AuditData data = auditService.getData(invokerId, null, null, null, null);
        List<AuditRecord> records = data.records;

        Set<String> authEventKeys = Set.of(
                "TOKEN_REFRESHED", "TOKEN_EXPIRY_DETECTED", "INVALID_TOKEN_DETECTED",
                "CLIENT_SECRET_EXPIRY_DETECTED", "INVALID_CLIENT_SECRET_DETECTED", "AUTH_CONFIG_FAILURE"
        );

        Map<String, Map<String, Object>> events = new HashMap<>();
        for (String key : authEventKeys) {
            Map<String, Object> ev = new HashMap<>();
            ev.put("count", 0);
            ev.put("lastSeen", null);
            events.put(key, ev);
        }
        for (AuditRecord r : records) {
            if (r.eventType != null && events.containsKey(r.eventType)) {
                Map<String, Object> ev = events.get(r.eventType);
                int cnt = (int) ev.get("count");
                ev.put("count", cnt + 1);
                Long prev = (Long) ev.get("lastSeen");
                if (prev == null || r.timestamp > prev) {
                    ev.put("lastSeen", r.timestamp);
                }
            }
        }
        health.put("events", events);

        // ── Subscription status ──────────────────────────────────────────────
        health.put("subscription", buildSubscriptionInfo());

        return health;
    }

    /**
     * Queries Microsoft Graph API for live subscription status, matching the
     * approach used by Outlook's audit resource.
     *
     * Returns a map with the fields the audit dashboard's renderSubscription()
     * function expects:
     *   status        — "Active" | "Not Active" | "None"
     *   allowMailAlert — true when a matching subscription is found
     *   expiresAt     — epoch-ms of the subscription expiration
     *   subscriptionId — the Graph subscription ID
     *   resource      — the Graph resource being monitored
     */
    private Map<String, Object> buildSubscriptionInfo() {
        try {
            OnedriveAttributes attrs = attributeStore.load(invokerId);
            if (attrs == null || attrs.getEmail() == null) {
                return Map.of("status", "None", "allowMailAlert", false);
            }

            String expectedUrl = baseRoutingUrl + Constants.REST_ONEDRIVE_MAIL_NOTIFICATION;
            SubscriptionCollectionPage page = providerFactory.create(invokerId)
                    .getGraphServiceClientForAdmin().subscriptions().buildRequest().get();

            Map<String, Object> sub = new HashMap<>();
            boolean found = false;
            while (page != null) {
                for (Subscription s : page.getCurrentPage()) {
                    if (expectedUrl.equals(s.notificationUrl)) {
                        found = true;
                        sub.put("status", "Active");
                        sub.put("subscriptionId", s.id);
                        sub.put("expiresAt", s.expirationDateTime != null
                                ? s.expirationDateTime.toEpochSecond() * 1000 : null);
                        sub.put("resource", s.resource);
                        break;
                    }
                }
                if (found || page.getNextPage() == null) break;
                page = page.getNextPage().buildRequest().get();
            }
            if (!found) {
                sub.put("status", "Not Active");
            }
            sub.put("allowMailAlert", found);
            return sub;
        } catch (MustAuthorizeException e) {
            throw e;
        } catch (Exception e) {
            Map<String, Object> sub = new HashMap<>();
            sub.put("status", "Unknown");
            sub.put("allowMailAlert", false);
            sub.put("error", e.getMessage() != null ? e.getMessage() : "");
            return sub;
        }
    }

    /** Masks a credential string: shows first 8 chars then *** */
    private static String mask(String value) {
        if (value == null || value.isEmpty()) return "\u2014";
        int show = Math.min(8, value.length());
        return value.substring(0, show) + "***";
    }

    /**
     * Clears audit records within the given time range.
     */
    @DELETE
    @Path("data")
    @Produces(MediaType.APPLICATION_JSON)
    public Response clearAuditData(@QueryParam("from") Long from, @QueryParam("to") Long to) {
        auditService.clearRecords(invokerId, from, to);
        return Response.ok("{\"cleared\":true}", MediaType.APPLICATION_JSON).build();
    }
}
