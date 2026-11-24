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

import app.krista.model.base.FreeForm;
import app.krista.sdk.msauth.extension.mcp.McpProductConfig;
import app.krista.sdk.msauth.extension.mcp.McpSearchResult;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OneDrive-specific implementation of {@link McpProductConfig}.
 *
 * <p>Uses Microsoft Graph {@code /search/query} API with KQL
 * (Keyword Query Language) to search for files and folders
 * stored in OneDrive / SharePoint.</p>
 */
public class OnedriveMcpProductConfig implements McpProductConfig {

    private static final Gson GSON = new Gson();
    private static final String SEARCH_URL = "https://graph.microsoft.com/v1.0/search/query";

    @Override
    public String productName() {
        return "OneDrive";
    }

    @Override
    public String queryLanguage() {
        return "KQL";
    }

    @Override
    public String queryLanguageFullName() {
        return "KQL (Keyword Query Language)";
    }

    @Override
    public String searchUrl() {
        return SEARCH_URL;
    }

    @Override
    public String searchHttpMethod() {
        return "POST";
    }

    @Override
    public List<String> standardFields() {
        return List.of("name", "webUrl", "lastModifiedDateTime", "lastModifiedBy", "size");
    }

    @Override
    public List<ColumnDef> resultColumns() {
        // Return empty — columns are driven entirely by the runtime payload.
        return List.of();
    }

    @Override
    public List<String> nlExamples() {
        return List.of(
                "List files from /me/drive/root only",
                "List my recent files",
                "Show all Excel files"
        );
    }

    @Override
    public List<String> kqlExamples() {
        return List.of(
                "Title:\"report\"",
                "Author:\"John Smith\"",
                "LastModifiedTime>2024-01-01"
        );
    }

    /**
     * Builds a prompt that instructs the AI to determine the exact Microsoft
     * Graph API call needed to answer the user's question about OneDrive files.
     *
     * @param question the user's natural language question
     * @return the Graph API planning prompt
     */
    public String graphApiPlanningPrompt(String question) {
        return "You are a Microsoft Graph API expert for OneDrive files and folders.\n"
                + "Given the user's question, determine the exact Microsoft Graph API call needed.\n\n"
                + "Output ONLY a raw JSON object (no markdown, no backticks, no explanation):\n"
                + "  \"method\": \"GET\" or \"POST\"\n"
                + "  \"path\":   relative path starting with /me/ or /search/query "
                + "(e.g. \"/me/drive/root/children\")\n"
                + "  \"body\":   full JSON string for POST requests, or null for GET\n\n"
                + "Common Graph API patterns for OneDrive:\n"
                + "- All files (own + shared, BEST for 'all files'):  POST /search/query\n"
                + "    body: {\"requests\":[{\"entityTypes\":[\"driveItem\"],"
                + "\"query\":{\"queryString\":\"*\"},\"from\":0,\"size\":50}]}\n"
                + "- Recent files:         GET /me/drive/recent?$top=50\n"
                + "- Shared with me:       GET /me/drive/sharedWithMe?$top=50\n"
                + "- List root files:      GET /me/drive/root/children?$top=50\n"
                + "- Search by name/keyword: POST /search/query\n"
                + "    body: {\"requests\":[{\"entityTypes\":[\"driveItem\"],"
                + "\"query\":{\"queryString\":\"KEYWORD\"},\"from\":0,\"size\":50}]}\n"
                + "- Files in folder:      GET /me/drive/root:/{folderPath}:/children?$top=50\n"
                + "- Get file by ID:       GET /me/drive/items/{itemId}\n"
                + "- List all drives:      GET /me/drives\n\n"
                + "IMPORTANT:\n"
                + "- For 'all files', 'all items', 'list all' → use POST /search/query with queryString:\"*\". "
                + "This covers BOTH own files AND shared files.\n"
                + "- For 'shared files only' → use GET /me/drive/sharedWithMe?$top=50\n"
                + "- For 'recent files' → use GET /me/drive/recent?$top=50\n"
                + "- For 'path' field always start with /me/ or /search/query (not /v1.0/)\n\n"
                + "Question: " + question;
    }

    @Override
    public String buildSearchRequestBody(String query, int maxResults, boolean allFields) {
        JsonObject queryObj = new JsonObject();
        queryObj.addProperty("queryString", query.isEmpty() ? "*" : query);

        JsonArray entityTypes = new JsonArray();
        entityTypes.add("driveItem");

        JsonObject request = new JsonObject();
        request.add("entityTypes", entityTypes);
        request.add("query", queryObj);
        request.addProperty("from", 0);
        request.addProperty("size", maxResults);

        JsonArray requests = new JsonArray();
        requests.add(request);

        JsonObject body = new JsonObject();
        body.add("requests", requests);

        return body.toString();
    }

    @Override
    public McpSearchResult parseSearchResponse(String json, String baseUrl) {
        List<Map<String, Object>> rows = new ArrayList<>();
        int total = 0;

        JsonObject root = GSON.fromJson(json, JsonObject.class);
        JsonArray values = root.has("value") ? root.getAsJsonArray("value") : new JsonArray();

        for (JsonElement valEl : values) {
            JsonObject val = valEl.getAsJsonObject();
            JsonArray hitsContainers = val.has("hitsContainers")
                    ? val.getAsJsonArray("hitsContainers") : new JsonArray();

            for (JsonElement hcEl : hitsContainers) {
                JsonObject hc = hcEl.getAsJsonObject();
                total += hc.has("total") ? hc.get("total").getAsInt() : 0;
                JsonArray hits = hc.has("hits") ? hc.getAsJsonArray("hits") : new JsonArray();

                for (JsonElement hitEl : hits) {
                    JsonObject hit = hitEl.getAsJsonObject();
                    JsonObject resource = hit.has("resource")
                            ? hit.getAsJsonObject("resource") : new JsonObject();

                    Map<String, Object> row = new LinkedHashMap<>();
                    for (Map.Entry<String, JsonElement> entry : resource.entrySet()) {
                        String strVal = toDisplayValue(entry.getKey(), entry.getValue(), resource);
                        if (strVal != null && !strVal.isEmpty()) {
                            row.put(toFreeFormKey(entry.getKey()), strVal);
                        }
                    }
                    String hitId = getString(hit, "hitId");
                    if (!hitId.isEmpty()) row.put("fileId", hitId);
                    rows.add(row);
                }
            }
        }

        return new McpSearchResult("kql", total, rows.size(), rows);
    }

    @Override
    public List<FreeForm> parseStandardResponse(String json, String baseUrl) {
        List<FreeForm> rows = new ArrayList<>();
        JsonObject root = GSON.fromJson(json, JsonObject.class);
        JsonArray values = root.has("value") ? root.getAsJsonArray("value") : new JsonArray();

        for (JsonElement valEl : values) {
            JsonObject val = valEl.getAsJsonObject();
            JsonArray hitsContainers = val.has("hitsContainers")
                    ? val.getAsJsonArray("hitsContainers") : new JsonArray();

            for (JsonElement hcEl : hitsContainers) {
                JsonObject hc = hcEl.getAsJsonObject();
                JsonArray hits = hc.has("hits") ? hc.getAsJsonArray("hits") : new JsonArray();

                for (JsonElement hitEl : hits) {
                    JsonObject hit = hitEl.getAsJsonObject();
                    JsonObject resource = hit.has("resource")
                            ? hit.getAsJsonObject("resource") : new JsonObject();

                    FreeForm ff = new FreeForm();
                    for (Map.Entry<String, JsonElement> entry : resource.entrySet()) {
                        String strVal = toDisplayValue(entry.getKey(), entry.getValue(), resource);
                        if (strVal != null && !strVal.isEmpty()) {
                            ff.put(toFreeFormKey(entry.getKey()), " Text ", strVal);
                        }
                    }
                    String hitId = getString(hit, "hitId");
                    if (!hitId.isEmpty()) ff.put("FileId", " Text ", hitId);
                    rows.add(ff);
                }
            }
        }
        return rows;
    }

    @Override
    public List<Map<String, String>> parseAllFieldsResponse(String json, String baseUrl) {
        List<Map<String, String>> rows = new ArrayList<>();
        JsonObject root = GSON.fromJson(json, JsonObject.class);
        JsonArray values = root.has("value") ? root.getAsJsonArray("value") : new JsonArray();

        for (JsonElement valEl : values) {
            JsonObject val = valEl.getAsJsonObject();
            JsonArray hitsContainers = val.has("hitsContainers")
                    ? val.getAsJsonArray("hitsContainers") : new JsonArray();

            for (JsonElement hcEl : hitsContainers) {
                JsonObject hc = hcEl.getAsJsonObject();
                JsonArray hits = hc.has("hits") ? hc.getAsJsonArray("hits") : new JsonArray();

                for (JsonElement hitEl : hits) {
                    JsonObject hit = hitEl.getAsJsonObject();
                    JsonObject resource = hit.has("resource")
                            ? hit.getAsJsonObject("resource") : new JsonObject();

                    Map<String, String> row = new LinkedHashMap<>();
                    for (Map.Entry<String, JsonElement> entry : resource.entrySet()) {
                        String strVal = toDisplayValue(entry.getKey(), entry.getValue(), resource);
                        if (strVal != null && !strVal.isEmpty()) {
                            row.put(toFreeFormKey(entry.getKey()), strVal);
                        }
                    }
                    String hitId = getString(hit, "hitId");
                    if (!hitId.isEmpty()) row.put("fileId", hitId);
                    String summary = hit.has("summary") ? getString(hit, "summary") : "";
                    if (!summary.isEmpty()) row.put("summary", summary);
                    row.put("key", row.getOrDefault("name", ""));
                    row.put("title", row.getOrDefault("name", ""));
                    rows.add(row);
                }
            }
        }
        return rows;
    }

    @Override
    public String queryGenerationPrompt(String question) {
        return "You are a Microsoft OneDrive file search expert. Convert the following natural language question "
                + "into a KQL (Keyword Query Language) query for Microsoft Graph Search API.\n\n"
                + "KQL syntax rules:\n"
                + "- Use property:value for field-specific searches (no spaces around the colon)\n"
                + "- Common properties: FileType, Title, Author, Path, LastModifiedTime, Created, Size, Filename\n"
                + "- Use quotes for multi-word values: Title:\"Project Report\"\n"
                + "- Boolean operators: AND, OR, NOT (must be uppercase)\n"
                + "- Wildcards: * for prefix matching (e.g. budget*)\n"
                + "- Date ranges: LastModifiedTime>2024-01-01\n\n"
                + "Examples:\n"
                + "- \"Project documents\" → project\n"
                + "- \"Files modified this week\" → LastModifiedTime>today-7\n"
                + "- \"Files by John\" → Author:\"John\"\n"
                + "- \"Reports from 2024\" → Title:\"report\" AND LastModifiedTime>2024-01-01\n"
                + "- \"Files in the finance folder\" → Path:\"finance\"\n\n"
                + "Output a JSON object with exactly these fields:\n"
                + "  \"query\": the KQL query string\n"
                + "  \"specific\": true if the question asks about a specific attribute of a named file, "
                + "false if it asks for a list of files\n"
                + "  \"field\": (only when specific=true) the field name being asked about "
                + "(e.g. \"modifiedBy\", \"lastModified\", \"size\")\n\n"
                + "Output ONLY raw JSON. No markdown, no backticks, no explanation.\n\n"
                + "Question: " + question;
    }

    @Override
    public String answerExtractionPrompt(String question, String contentData) {
        return "Given the following OneDrive file search results, answer the user's question concisely.\n\n"
                + "Question: " + question + "\n\n"
                + "Search results:\n" + contentData + "\n"
                + "Instructions:\n"
                + "- Answer directly and concisely\n"
                + "- If the data doesn't contain the answer, say so\n"
                + "- Do not invent or hallucinate data\n"
                + "- Reference specific file names, authors, or dates when relevant";
    }

    // ── Dynamic field helpers ─────────────────────────────────────────────

    /**
     * Converts any Graph driveItem field to a display string.
     * Handles primitives, date truncation, and nested identity objects.
     */
    private static String toDisplayValue(String key, JsonElement val, JsonObject resource) {
        if (val == null || val.isJsonNull()) return null;
        if (val.isJsonPrimitive()) {
            String s = val.getAsString();
            if ((key.contains("DateTime") || key.contains("date")) && s.contains("T")) {
                return s.substring(0, s.indexOf('T'));
            }
            if ("size".equals(key)) return formatSize(s);
            return s;
        }
        if (val.isJsonObject()) {
            if ("lastModifiedBy".equals(key) || "createdBy".equals(key)) {
                return extractUserDisplayName(val.getAsJsonObject());
            }
            if ("parentReference".equals(key)) {
                return getString(val.getAsJsonObject(), "name");
            }
            if ("file".equals(key)) {
                return getString(val.getAsJsonObject(), "mimeType");
            }
        }
        return null; // skip arrays and other complex objects
    }

    /**
     * Maps a Graph driveItem field name to a PascalCase FreeForm key.
     * Unknown fields pass through unchanged.
     */
    private static String toFreeFormKey(String graphKey) {
        switch (graphKey) {
            case "name":                    return "Name";
            case "webUrl":                  return "Link";
            case "lastModifiedDateTime":    return "LastModified";
            case "createdDateTime":         return "Created";
            case "size":                    return "Size";
            case "id":                      return "FileId";
            case "lastModifiedBy":          return "ModifiedBy";
            case "createdBy":               return "CreatedBy";
            case "parentReference":         return "Folder";
            case "file":                    return "MimeType";
            default:                        return graphKey;
        }
    }

    private static String extractUserDisplayName(JsonObject identitySet) {
        if (identitySet == null) return "";
        JsonObject user = identitySet.has("user") && !identitySet.get("user").isJsonNull()
                ? identitySet.getAsJsonObject("user") : null;
        if (user != null) {
            String name = getString(user, "displayName");
            if (!name.isEmpty()) return name;
        }
        return "";
    }

    private static String getString(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) return "";
        return obj.get(key).getAsString();
    }

    private static String formatSize(String sizeStr) {
        if (sizeStr == null || sizeStr.isEmpty()) return "";
        try {
            long bytes = Long.parseLong(sizeStr);
            if (bytes < 1024) return bytes + " B";
            if (bytes < 1024 * 1024) return (bytes / 1024) + " KB";
            return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        } catch (NumberFormatException e) {
            return sizeStr;
        }
    }
}
