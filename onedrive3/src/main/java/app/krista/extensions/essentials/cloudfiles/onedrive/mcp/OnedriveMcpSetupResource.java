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

package app.krista.extensions.essentials.cloudfiles.onedrive.mcp;

import app.krista.extension.executor.ExtensionResponse;
import app.krista.extension.executor.Invoker;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.stores.OnedriveAttributeStore;
import app.krista.extensions.util.KeyValueStore;
import app.krista.model.base.FreeForm;
import app.krista.sdk.msauth.extension.mcp.AbstractMcpSetupResource;
import app.krista.sdk.msauth.extension.mcp.DefaultMsAuthBridge;
import app.krista.sdk.msauth.extension.mcp.McpProductConfig;
import app.krista.sdk.msauth.token.ITokenStore;
import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.ws.rs.Path;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Concrete MCP setup resource for the OneDrive extension.
 *
 * <p>Registers as a JAX-RS resource at {@code /onedrive/mcp-setup} and
 * inherits all endpoints from {@link AbstractMcpSetupResource}.</p>
 *
 * <p>Overrides {@link #interceptNlQuery(String)} to delegate NL queries to
 * {@link OnedriveMcpQueryService#query(String)} which asks the AI to plan
 * the appropriate Microsoft Graph API call and executes it generically —
 * no hardcoded intents.</p>
 */
@Path("/mcp-setup")
public class OnedriveMcpSetupResource extends AbstractMcpSetupResource {

    private static final Logger LOG = LoggerFactory.getLogger(OnedriveMcpSetupResource.class);
    private static final Gson GSON = new Gson();

    private final OnedriveMcpQueryService queryService;

    @Inject
    public OnedriveMcpSetupResource(KeyValueStore keyValueStore,
                                    Invoker invoker,
                                    OnedriveAttributeStore attributeStore,
                                    OnedriveMcpQueryService queryService) {
        super(adaptKeyValueStore(keyValueStore),
              invoker,
              new DefaultMsAuthBridge(
                      attributeStore.load(invoker.getInvokerId()),
                      adaptKeyValueStore(keyValueStore)));
        this.queryService = queryService;
    }

    @Override
    protected McpProductConfig getProductConfig() {
        return new OnedriveMcpProductConfig();
    }

    @Override
    protected String getJaxrsId() {
        return "onedrive";
    }

    /**
     * Intercepts NL queries and delegates to
     * {@link OnedriveMcpQueryService#query(String)}, which asks the AI
     * to plan the appropriate Microsoft Graph API call and executes it
     * generically — no hardcoded intents.
     */
    @Override
    protected Response interceptNlQuery(String question) {
        LOG.debug("[MCP-OD-NLQ] interceptNlQuery — question: '{}'", question);

        try {
            ExtensionResponse extResponse = queryService.query(question);
            LOG.debug("[MCP-OD-NLQ] queryService.query() returned — result: {}", extResponse.getResult());

            if (extResponse.getResult() == ExtensionResponse.Result.FAILURE) {
                String errorMsg = "Query failed.";
                if (extResponse.getError() != null) {
                    errorMsg = String.valueOf(extResponse.getError());
                }
                LOG.debug("[MCP-OD-NLQ] FAILURE — {}", errorMsg);
                return Response.status(400)
                        .entity(GSON.toJson(Map.of("error", errorMsg)))
                        .type(MediaType.APPLICATION_JSON).build();
            }

            Map<String, Object> result = convertToNlqResponse(extResponse, question);
            LOG.debug("[MCP-OD-NLQ] returning result — total: {}, shown: {}", result.get("total"), result.get("shown"));
            return Response.ok(GSON.toJson(result)).type(MediaType.APPLICATION_JSON).build();

        } catch (Exception e) {
            LOG.error("[MCP-OD-NLQ] interceptNlQuery FAILED — {}", e.getMessage(), e);
            return Response.status(400)
                    .entity(GSON.toJson(Map.of("error", "Query failed: " + e.getMessage())))
                    .type(MediaType.APPLICATION_JSON).build();
        }
    }

    /**
     * Converts an {@link ExtensionResponse} with FreeForm list into the
     * JSON structure the NQL tab UI expects.
     *
     * <p>Columns are derived dynamically from the keys present in the response
     * rows at runtime — whatever fields the Graph API returns will automatically
     * produce the correct columns.</p>
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> convertToNlqResponse(ExtensionResponse extResponse, String question) {
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, String>> rows = new ArrayList<>();
        Set<String> seenKeys = new LinkedHashSet<>(); // preserves insertion order
        String generatedPlan = question; // default: original question

        Object responseValue = extResponse.getResponseValue();
        if (responseValue instanceof Map) {
            Map<String, Object> payload = (Map<String, Object>) responseValue;
            // Use the AI-generated Graph plan string if available
            Object plan = payload.get("generatedPlan");
            if (plan != null) generatedPlan = String.valueOf(plan);
            Object responseList = payload.get("Response");
            if (responseList instanceof List) {
                List<?> freeFormRows = (List<?>) responseList;
                for (Object row : freeFormRows) {
                    if (row instanceof FreeForm) {
                        FreeForm ff = (FreeForm) row;
                        Map<String, String> item = new LinkedHashMap<>();
                        for (Object key : ff.keySet()) {
                            String value = extractFreeFormValue(ff, key);
                            if (value != null) {
                                String colKey = toColumnKey(String.valueOf(key));
                                item.put(colKey, value);
                                seenKeys.add(colKey);
                            }
                        }
                        rows.add(item);
                    }
                }
            }
        }

        List<Map<String, String>> columns = new ArrayList<>();
        for (String key : seenKeys) {
            Map<String, String> col = new LinkedHashMap<>();
            col.put("key", key);
            col.put("label", toColumnLabel(key));
            col.put("type", isLinkColumn(key) ? "link" : "text");
            columns.add(col);
        }

        LOG.debug("[MCP-OD-NLQ] convertToNlqResponse — rows: {}, columns: {}", rows.size(), seenKeys);
        result.put("total", rows.size());
        result.put("shown", rows.size());
        result.put("columns", columns);
        result.put("rows", rows);
        result.put("generatedQuery", generatedPlan);
        result.put("specific", false);
        return result;
    }

    /** Extracts the string value from a FreeForm entry (handles [type, value] lists). */
    private static String extractFreeFormValue(FreeForm ff, Object key) {
        Object val = ff.get(key);
        if (val instanceof List) {
            List<?> valList = (List<?>) val;
            if (valList.size() >= 2 && valList.get(1) != null) {
                String s = String.valueOf(valList.get(1));
                return s.isEmpty() ? null : s;
            }
        } else if (val != null) {
            String s = String.valueOf(val);
            return s.isEmpty() ? null : s;
        }
        return null;
    }

    /**
     * Normalises a FreeForm key (PascalCase) to a camelCase column key.
     * Examples: {@code "Name"} → {@code "name"},
     * {@code "LastModified"} → {@code "lastModified"}.
     */
    private static String toColumnKey(String freeFormKey) {
        if (freeFormKey == null || freeFormKey.isEmpty()) return freeFormKey;
        return Character.toLowerCase(freeFormKey.charAt(0)) + freeFormKey.substring(1);
    }

    /**
     * Converts a camelCase column key to a human-readable label.
     * Examples: {@code "lastModified"} → {@code "Last Modified"},
     * {@code "modifiedBy"} → {@code "Modified By"}.
     */
    private static String toColumnLabel(String columnKey) {
        if (columnKey == null || columnKey.isEmpty()) return columnKey;
        String spaced = columnKey.replaceAll("([a-z])([A-Z])", "$1 $2");
        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }

    /**
     * Returns {@code true} for column keys whose values are URLs and should
     * be rendered as clickable links in the UI.
     */
    private static boolean isLinkColumn(String key) {
        return "link".equals(key) || "url".equals(key);
    }

    /**
     * Adapts a {@link KeyValueStore} (put/get/remove) to the SDK's
     * {@link ITokenStore} interface (save/get/delete).
     *
     * <p>Both callers share the same {@code keyValueStore} singleton injected
     * by HK2, so session data (AI keys, providers) persists across calls.</p>
     */
    private static ITokenStore adaptKeyValueStore(KeyValueStore kvStore) {
        return new ITokenStore() {
            @Override
            public void save(String key, String value) {
                kvStore.put(key, value);
            }

            @Override
            public String get(String key) {
                Object val = kvStore.get(key);
                return val != null ? String.valueOf(val) : null;
            }

            @Override
            public void delete(String key) {
                kvStore.remove(key);
            }
        };
    }
}
