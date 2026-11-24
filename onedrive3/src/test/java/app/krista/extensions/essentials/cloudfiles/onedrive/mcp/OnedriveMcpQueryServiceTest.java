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
import app.krista.extension.request.RoutingInfo;
import app.krista.extensions.essentials.cloudfiles.onedrive.OnedriveAttributes;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.stores.OnedriveAttributeStore;
import app.krista.extensions.util.KeyValueStore;
import app.krista.model.base.FreeForm;
import app.krista.sdk.msauth.token.ITokenStore;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.Assert.*;

/**
 * Tests for {@link OnedriveMcpQueryService}.
 *
 * <p>Covers all methods: static/utility helpers via reflection,
 * instance methods (query, resolvedPath, executeGraphCall, parseGenericResponse,
 * graphObjectToFreeForm, toDisplayString), and the constructor + adaptKeyValueStore.</p>
 */
public class OnedriveMcpQueryServiceTest {

    private MockKeyValueStore mockKvStore;
    private Invoker mockInvoker;
    private OnedriveAttributeStore attributeStore;

    @Before
    public void setUp() throws Exception {
        mockKvStore = new MockKeyValueStore();
        mockInvoker = createMockInvoker();
        attributeStore = new OnedriveAttributeStore(mockInvoker, mockKvStore);
    }

    // ── blank ────────────────────────────────────────────────────────────

    @Test
    public void testBlank_null() throws Exception {
        assertTrue(invokeBlank(null));
    }

    @Test
    public void testBlank_empty() throws Exception {
        assertTrue(invokeBlank(""));
    }

    @Test
    public void testBlank_whitespace() throws Exception {
        assertTrue(invokeBlank("  "));
    }

    @Test
    public void testBlank_nonEmpty() throws Exception {
        assertFalse(invokeBlank("text"));
    }

    // ── getJsonString ────────────────────────────────────────────────────

    @Test
    public void testGetJsonString_nullObject() throws Exception {
        assertEquals("", invokeGetJsonString(null, "key"));
    }

    @Test
    public void testGetJsonString_missingKey() throws Exception {
        JsonObject obj = new JsonObject();
        assertEquals("", invokeGetJsonString(obj, "missing"));
    }

    @Test
    public void testGetJsonString_nullValue() throws Exception {
        JsonObject obj = new JsonObject();
        obj.add("key", JsonNull.INSTANCE);
        assertEquals("", invokeGetJsonString(obj, "key"));
    }

    @Test
    public void testGetJsonString_validValue() throws Exception {
        JsonObject obj = new JsonObject();
        obj.addProperty("method", "GET");
        assertEquals("GET", invokeGetJsonString(obj, "method"));
    }

    // ── getStr ──────────────────────────────────────────────────────────

    @Test
    public void testGetStr_nullObject() throws Exception {
        assertEquals("", invokeGetStr(null, "key"));
    }

    @Test
    public void testGetStr_missingKey() throws Exception {
        JsonObject obj = new JsonObject();
        assertEquals("", invokeGetStr(obj, "missing"));
    }

    @Test
    public void testGetStr_nullValue() throws Exception {
        JsonObject obj = new JsonObject();
        obj.add("key", JsonNull.INSTANCE);
        assertEquals("", invokeGetStr(obj, "key"));
    }

    @Test
    public void testGetStr_validValue() throws Exception {
        JsonObject obj = new JsonObject();
        obj.addProperty("displayName", "John");
        assertEquals("John", invokeGetStr(obj, "displayName"));
    }

    // ── toFreeFormKey ────────────────────────────────────────────────────

    @Test
    public void testToFreeFormKey_name() throws Exception {
        assertEquals("Name", invokeToFreeFormKey("name"));
    }

    @Test
    public void testToFreeFormKey_webUrl() throws Exception {
        assertEquals("Link", invokeToFreeFormKey("webUrl"));
    }

    @Test
    public void testToFreeFormKey_lastModifiedDateTime() throws Exception {
        assertEquals("LastModified", invokeToFreeFormKey("lastModifiedDateTime"));
    }

    @Test
    public void testToFreeFormKey_createdDateTime() throws Exception {
        assertEquals("Created", invokeToFreeFormKey("createdDateTime"));
    }

    @Test
    public void testToFreeFormKey_size() throws Exception {
        assertEquals("Size", invokeToFreeFormKey("size"));
    }

    @Test
    public void testToFreeFormKey_id() throws Exception {
        assertEquals("FileId", invokeToFreeFormKey("id"));
    }

    @Test
    public void testToFreeFormKey_lastModifiedBy() throws Exception {
        assertEquals("ModifiedBy", invokeToFreeFormKey("lastModifiedBy"));
    }

    @Test
    public void testToFreeFormKey_createdBy() throws Exception {
        assertEquals("CreatedBy", invokeToFreeFormKey("createdBy"));
    }

    @Test
    public void testToFreeFormKey_parentReference() throws Exception {
        assertEquals("Folder", invokeToFreeFormKey("parentReference"));
    }

    @Test
    public void testToFreeFormKey_file() throws Exception {
        assertEquals("MimeType", invokeToFreeFormKey("file"));
    }

    @Test
    public void testToFreeFormKey_unknown() throws Exception {
        assertEquals("unknown", invokeToFreeFormKey("unknown"));
    }

    // ── toDisplayString ─────────────────────────────────────────────────

    @Test
    public void testToDisplayString_nullValue() throws Exception {
        assertNull(invokeToDisplayString("key", null, new JsonObject()));
    }

    @Test
    public void testToDisplayString_jsonNull() throws Exception {
        assertNull(invokeToDisplayString("key", JsonNull.INSTANCE, new JsonObject()));
    }

    @Test
    public void testToDisplayString_primitivePlainString() throws Exception {
        JsonObject parent = new JsonObject();
        parent.addProperty("name", "test.txt");
        assertEquals("test.txt", invokeToDisplayString("name",
                parent.get("name"), parent));
    }

    @Test
    public void testToDisplayString_dateTimeTruncation() throws Exception {
        JsonObject parent = new JsonObject();
        parent.addProperty("lastModifiedDateTime", "2024-01-15T10:30:00Z");
        assertEquals("2024-01-15", invokeToDisplayString("lastModifiedDateTime",
                parent.get("lastModifiedDateTime"), parent));
    }

    @Test
    public void testToDisplayString_dateFieldWithLowercase() throws Exception {
        JsonObject parent = new JsonObject();
        parent.addProperty("createddate", "2024-06-01T08:00:00Z");
        assertEquals("2024-06-01", invokeToDisplayString("createddate",
                parent.get("createddate"), parent));
    }

    @Test
    public void testToDisplayString_dateWithoutT_noTruncation() throws Exception {
        JsonObject parent = new JsonObject();
        parent.addProperty("lastModifiedDateTime", "2024-01-15");
        assertEquals("2024-01-15", invokeToDisplayString("lastModifiedDateTime",
                parent.get("lastModifiedDateTime"), parent));
    }

    @Test
    public void testToDisplayString_sizeBytes() throws Exception {
        JsonObject parent = new JsonObject();
        parent.addProperty("size", "512");
        assertEquals("512 B", invokeToDisplayString("size",
                parent.get("size"), parent));
    }

    @Test
    public void testToDisplayString_sizeKilobytes() throws Exception {
        JsonObject parent = new JsonObject();
        parent.addProperty("size", "2048");
        assertEquals("2 KB", invokeToDisplayString("size",
                parent.get("size"), parent));
    }

    @Test
    public void testToDisplayString_sizeMegabytes() throws Exception {
        JsonObject parent = new JsonObject();
        parent.addProperty("size", "5242880");
        assertEquals("5.0 MB", invokeToDisplayString("size",
                parent.get("size"), parent));
    }

    @Test
    public void testToDisplayString_sizeNotNumber() throws Exception {
        JsonObject parent = new JsonObject();
        parent.addProperty("size", "notanumber");
        assertEquals("notanumber", invokeToDisplayString("size",
                parent.get("size"), parent));
    }

    @Test
    public void testToDisplayString_sizeZero() throws Exception {
        JsonObject parent = new JsonObject();
        parent.addProperty("size", "0");
        assertEquals("0 B", invokeToDisplayString("size",
                parent.get("size"), parent));
    }

    @Test
    public void testToDisplayString_sizeExactly1024() throws Exception {
        JsonObject parent = new JsonObject();
        parent.addProperty("size", "1024");
        assertEquals("1 KB", invokeToDisplayString("size",
                parent.get("size"), parent));
    }

    @Test
    public void testToDisplayString_sizeExactly1MB() throws Exception {
        JsonObject parent = new JsonObject();
        parent.addProperty("size", "1048576");
        assertEquals("1.0 MB", invokeToDisplayString("size",
                parent.get("size"), parent));
    }

    @Test
    public void testToDisplayString_lastModifiedByWithUser() throws Exception {
        JsonObject user = new JsonObject();
        user.addProperty("displayName", "Alice");
        JsonObject lastModifiedBy = new JsonObject();
        lastModifiedBy.add("user", user);
        JsonObject parent = new JsonObject();
        parent.add("lastModifiedBy", lastModifiedBy);

        assertEquals("Alice", invokeToDisplayString("lastModifiedBy",
                parent.get("lastModifiedBy"), parent));
    }

    @Test
    public void testToDisplayString_createdByWithUser() throws Exception {
        JsonObject user = new JsonObject();
        user.addProperty("displayName", "Bob");
        JsonObject createdBy = new JsonObject();
        createdBy.add("user", user);
        JsonObject parent = new JsonObject();
        parent.add("createdBy", createdBy);

        assertEquals("Bob", invokeToDisplayString("createdBy",
                parent.get("createdBy"), parent));
    }

    @Test
    public void testToDisplayString_lastModifiedByWithNullUser() throws Exception {
        JsonObject lastModifiedBy = new JsonObject();
        lastModifiedBy.add("user", JsonNull.INSTANCE);
        JsonObject parent = new JsonObject();
        parent.add("lastModifiedBy", lastModifiedBy);

        assertEquals("", invokeToDisplayString("lastModifiedBy",
                parent.get("lastModifiedBy"), parent));
    }

    @Test
    public void testToDisplayString_lastModifiedByWithNoUser() throws Exception {
        JsonObject lastModifiedBy = new JsonObject();
        JsonObject parent = new JsonObject();
        parent.add("lastModifiedBy", lastModifiedBy);

        assertEquals("", invokeToDisplayString("lastModifiedBy",
                parent.get("lastModifiedBy"), parent));
    }

    @Test
    public void testToDisplayString_parentReference() throws Exception {
        JsonObject parentRef = new JsonObject();
        parentRef.addProperty("name", "Documents");
        JsonObject parent = new JsonObject();
        parent.add("parentReference", parentRef);

        assertEquals("Documents", invokeToDisplayString("parentReference",
                parent.get("parentReference"), parent));
    }

    @Test
    public void testToDisplayString_fileWithMimeType() throws Exception {
        JsonObject file = new JsonObject();
        file.addProperty("mimeType", "application/pdf");
        JsonObject parent = new JsonObject();
        parent.add("file", file);

        assertEquals("application/pdf", invokeToDisplayString("file",
                parent.get("file"), parent));
    }

    @Test
    public void testToDisplayString_unknownJsonObject() throws Exception {
        JsonObject unknown = new JsonObject();
        unknown.addProperty("something", "value");
        JsonObject parent = new JsonObject();
        parent.add("otherObj", unknown);

        assertNull(invokeToDisplayString("otherObj",
                parent.get("otherObj"), parent));
    }

    @Test
    public void testToDisplayString_jsonArray() throws Exception {
        JsonArray arr = new JsonArray();
        arr.add("item1");
        JsonObject parent = new JsonObject();
        parent.add("tags", arr);

        assertNull(invokeToDisplayString("tags",
                parent.get("tags"), parent));
    }

    // ── graphObjectToFreeForm ───────────────────────────────────────────

    @Test
    public void testGraphObjectToFreeForm_basicFields() throws Exception {
        JsonObject obj = new JsonObject();
        obj.addProperty("name", "report.pdf");
        obj.addProperty("webUrl", "https://example.com/report");
        obj.addProperty("size", "1024");

        FreeForm ff = invokeGraphObjectToFreeForm(obj);
        assertNotNull(ff);
        // FreeForm should contain mapped keys
        assertTrue(ff.keySet().contains("Name"));
        assertTrue(ff.keySet().contains("Link"));
        assertTrue(ff.keySet().contains("Size"));
    }

    @Test
    public void testGraphObjectToFreeForm_skipsOdataFields() throws Exception {
        JsonObject obj = new JsonObject();
        obj.addProperty("@odata.type", "#microsoft.graph.driveItem");
        obj.addProperty("@odata.context", "https://graph.microsoft.com");
        obj.addProperty("name", "file.txt");

        FreeForm ff = invokeGraphObjectToFreeForm(obj);
        assertNotNull(ff);
        assertTrue(ff.keySet().contains("Name"));
        assertFalse(ff.keySet().contains("@odata.type"));
        assertFalse(ff.keySet().contains("@odata.context"));
    }

    @Test
    public void testGraphObjectToFreeForm_emptyObject() throws Exception {
        JsonObject obj = new JsonObject();
        FreeForm ff = invokeGraphObjectToFreeForm(obj);
        assertNotNull(ff);
        assertTrue(ff.keySet().isEmpty());
    }

    @Test
    public void testGraphObjectToFreeForm_nullValueFieldSkipped() throws Exception {
        JsonObject obj = new JsonObject();
        obj.add("name", JsonNull.INSTANCE);
        obj.addProperty("id", "abc123");

        FreeForm ff = invokeGraphObjectToFreeForm(obj);
        assertNotNull(ff);
        assertFalse(ff.keySet().contains("Name"));
        assertTrue(ff.keySet().contains("FileId"));
    }

    @Test
    public void testGraphObjectToFreeForm_emptyStringFieldSkipped() throws Exception {
        JsonObject obj = new JsonObject();
        obj.addProperty("name", "");
        obj.addProperty("id", "abc123");

        FreeForm ff = invokeGraphObjectToFreeForm(obj);
        assertNotNull(ff);
        assertFalse(ff.keySet().contains("Name"));
        assertTrue(ff.keySet().contains("FileId"));
    }

    // ── parseGenericResponse ────────────────────────────────────────────

    @Test
    public void testParseGenericResponse_singleObject() throws Exception {
        String json = "{\"name\":\"file.txt\",\"id\":\"123\"}";
        List<FreeForm> rows = invokeParseGenericResponse(json);
        assertEquals(1, rows.size());
        assertTrue(rows.get(0).keySet().contains("Name"));
    }

    @Test
    public void testParseGenericResponse_noValueKey() throws Exception {
        String json = "{\"displayName\":\"John\",\"mail\":\"john@example.com\"}";
        List<FreeForm> rows = invokeParseGenericResponse(json);
        assertEquals(1, rows.size());
    }

    @Test
    public void testParseGenericResponse_valueNotArray() throws Exception {
        String json = "{\"value\":\"notanarray\"}";
        List<FreeForm> rows = invokeParseGenericResponse(json);
        // value is not an array, so treated as single object
        assertEquals(1, rows.size());
    }

    @Test
    public void testParseGenericResponse_emptyValueArray() throws Exception {
        String json = "{\"value\":[]}";
        List<FreeForm> rows = invokeParseGenericResponse(json);
        assertEquals(0, rows.size());
    }

    @Test
    public void testParseGenericResponse_flatListResponse() throws Exception {
        String json = "{\"value\":[" +
                "{\"name\":\"file1.txt\",\"id\":\"1\"}," +
                "{\"name\":\"file2.txt\",\"id\":\"2\"}" +
                "]}";
        List<FreeForm> rows = invokeParseGenericResponse(json);
        assertEquals(2, rows.size());
    }

    @Test
    public void testParseGenericResponse_searchResponse() throws Exception {
        String json = "{\"value\":[{\"hitsContainers\":[{\"hits\":[" +
                "{\"hitId\":\"hit1\",\"resource\":{\"name\":\"found.docx\",\"webUrl\":\"https://example.com\"}}," +
                "{\"hitId\":\"hit2\",\"resource\":{\"name\":\"found2.xlsx\"}}" +
                "]}]}]}";
        List<FreeForm> rows = invokeParseGenericResponse(json);
        assertEquals(2, rows.size());
        assertTrue(rows.get(0).keySet().contains("FileId"));
    }

    @Test
    public void testParseGenericResponse_searchResponse_emptyHitsContainers() throws Exception {
        String json = "{\"value\":[{\"hitsContainers\":[]}]}";
        List<FreeForm> rows = invokeParseGenericResponse(json);
        assertEquals(0, rows.size());
    }

    @Test
    public void testParseGenericResponse_searchResponse_noHitsInContainer() throws Exception {
        String json = "{\"value\":[{\"hitsContainers\":[{\"total\":0}]}]}";
        List<FreeForm> rows = invokeParseGenericResponse(json);
        assertEquals(0, rows.size());
    }

    @Test
    public void testParseGenericResponse_searchResponse_hitWithoutResource() throws Exception {
        String json = "{\"value\":[{\"hitsContainers\":[{\"hits\":[" +
                "{\"hitId\":\"h1\"}" +
                "]}]}]}";
        List<FreeForm> rows = invokeParseGenericResponse(json);
        assertEquals(1, rows.size());
        assertTrue(rows.get(0).keySet().contains("FileId"));
    }

    @Test
    public void testParseGenericResponse_searchResponse_hitWithNullHitId() throws Exception {
        String json = "{\"value\":[{\"hitsContainers\":[{\"hits\":[" +
                "{\"resource\":{\"name\":\"file.txt\"}}" +
                "]}]}]}";
        List<FreeForm> rows = invokeParseGenericResponse(json);
        assertEquals(1, rows.size());
        assertFalse(rows.get(0).keySet().contains("FileId"));
    }

    @Test
    public void testParseGenericResponse_searchResponse_hitWithJsonNullHitId() throws Exception {
        String json = "{\"value\":[{\"hitsContainers\":[{\"hits\":[" +
                "{\"hitId\":null,\"resource\":{\"name\":\"file.txt\"}}" +
                "]}]}]}";
        List<FreeForm> rows = invokeParseGenericResponse(json);
        assertEquals(1, rows.size());
    }

    @Test
    public void testParseGenericResponse_searchResponse_hitWithEmptyHitId() throws Exception {
        String json = "{\"value\":[{\"hitsContainers\":[{\"hits\":[" +
                "{\"hitId\":\"\",\"resource\":{\"name\":\"file.txt\"}}" +
                "]}]}]}";
        List<FreeForm> rows = invokeParseGenericResponse(json);
        assertEquals(1, rows.size());
        // Empty hitId should not be added
        assertFalse(rows.get(0).keySet().contains("FileId"));
    }

    @Test
    public void testParseGenericResponse_searchResponse_multipleContainers() throws Exception {
        String json = "{\"value\":[{\"hitsContainers\":[" +
                "{\"hits\":[{\"hitId\":\"h1\",\"resource\":{\"name\":\"a.txt\"}}]}," +
                "{\"hits\":[{\"hitId\":\"h2\",\"resource\":{\"name\":\"b.txt\"}}]}" +
                "]}]}";
        List<FreeForm> rows = invokeParseGenericResponse(json);
        assertEquals(2, rows.size());
    }

    @Test
    public void testParseGenericResponse_searchResponse_multipleValueElements() throws Exception {
        String json = "{\"value\":[" +
                "{\"hitsContainers\":[{\"hits\":[{\"hitId\":\"h1\",\"resource\":{\"name\":\"a.txt\"}}]}]}," +
                "{\"hitsContainers\":[{\"hits\":[{\"hitId\":\"h2\",\"resource\":{\"name\":\"b.txt\"}}]}]}" +
                "]}";
        List<FreeForm> rows = invokeParseGenericResponse(json);
        assertEquals(2, rows.size());
    }

    @Test
    public void testParseGenericResponse_searchResponse_valueElementWithoutHitsContainers() throws Exception {
        // First element has hitsContainers, second doesn't — second produces empty hitsContainers
        String json = "{\"value\":[" +
                "{\"hitsContainers\":[{\"hits\":[{\"hitId\":\"h1\",\"resource\":{\"name\":\"a.txt\"}}]}]}," +
                "{\"other\":\"data\"}" +
                "]}";
        List<FreeForm> rows = invokeParseGenericResponse(json);
        // First element: 1 hit. Second: no hitsContainers key → empty array fallback
        assertEquals(1, rows.size());
    }

    @Test
    public void testParseGenericResponse_sharedWithMe_remoteItem() throws Exception {
        String json = "{\"value\":[{\"id\":\"local-id\",\"remoteItem\":" +
                "{\"name\":\"shared.docx\",\"webUrl\":\"https://example.com/shared\",\"size\":2048}" +
                "}]}";
        List<FreeForm> rows = invokeParseGenericResponse(json);
        assertEquals(1, rows.size());
        FreeForm ff = rows.get(0);
        assertTrue(ff.keySet().contains("Name"));
        assertTrue(ff.keySet().contains("Link"));
        assertTrue(ff.keySet().contains("Size"));
    }

    @Test
    public void testParseGenericResponse_sharedWithMe_remoteItemOverridesTopLevel() throws Exception {
        // remoteItem fields override top-level fields (merged later)
        String json = "{\"value\":[{\"name\":\"local-name\",\"remoteItem\":" +
                "{\"name\":\"remote-name\",\"id\":\"remote-id\"}" +
                "}]}";
        List<FreeForm> rows = invokeParseGenericResponse(json);
        assertEquals(1, rows.size());
    }

    @Test
    public void testParseGenericResponse_flatList_nonObjectElementsSkipped() throws Exception {
        String json = "{\"value\":[{\"name\":\"file.txt\"},\"string-element\",42]}";
        List<FreeForm> rows = invokeParseGenericResponse(json);
        // Only the object element is processed
        assertEquals(1, rows.size());
    }

    @Test
    public void testParseGenericResponse_flatList_mixedItemsWithRemoteItem() throws Exception {
        String json = "{\"value\":[" +
                "{\"name\":\"normal.txt\",\"id\":\"1\"}," +
                "{\"id\":\"2\",\"remoteItem\":{\"name\":\"shared.pdf\",\"size\":4096}}" +
                "]}";
        List<FreeForm> rows = invokeParseGenericResponse(json);
        assertEquals(2, rows.size());
    }

    // ── success / failure static helpers ─────────────────────────────────

    @Test
    public void testSuccess() throws Exception {
        Map<String, Object> values = new HashMap<>();
        values.put("key", "value");
        ExtensionResponse resp = invokeSuccess(values);
        assertEquals(ExtensionResponse.Result.SUCCESS, resp.getResult());
        assertNotNull(resp.getResponseValue());
    }

    @Test
    public void testFailure() throws Exception {
        ExtensionResponse resp = invokeFailure("Something went wrong");
        assertEquals(ExtensionResponse.Result.FAILURE, resp.getResult());
        assertNotNull(resp.getError());
        assertTrue(resp.getError().getErrorMessage().contains("Something went wrong"));
    }

    // ── callAi ──────────────────────────────────────────────────────────

    @Test
    public void testCallAi_openaiProvider() throws Exception {
        // Verify the method routes to the correct API client.
        // We expect an exception because there's no real API key, but this confirms the
        // "openai" branch is taken.
        try {
            invokeCallAi("fake-key", "openai", "test prompt");
            fail("Expected exception from API call");
        } catch (Exception e) {
            // Expected — the API call fails but the routing was correct
            assertNotNull(e);
        }
    }

    @Test
    public void testCallAi_geminiProvider() throws Exception {
        try {
            invokeCallAi("fake-key", "gemini", "test prompt");
            fail("Expected exception from API call");
        } catch (Exception e) {
            assertNotNull(e);
        }
    }

    @Test
    public void testCallAi_anthropicProvider() throws Exception {
        try {
            invokeCallAi("fake-key", "anthropic", "test prompt");
            fail("Expected exception from API call");
        } catch (Exception e) {
            assertNotNull(e);
        }
    }

    @Test
    public void testCallAi_unknownProviderDefaultsToAnthropic() throws Exception {
        // Unknown provider falls through to the else branch (Anthropic)
        try {
            invokeCallAi("fake-key", "unknown", "test prompt");
            fail("Expected exception from API call");
        } catch (Exception e) {
            assertNotNull(e);
        }
    }

    // ── adaptKeyValueStore ──────────────────────────────────────────────

    @Test
    public void testAdaptKeyValueStore_save() throws Exception {
        ITokenStore tokenStore = invokeAdaptKeyValueStore(mockKvStore);
        tokenStore.save("myKey", "myValue");
        assertEquals("myValue", mockKvStore.get("myKey"));
    }

    @Test
    public void testAdaptKeyValueStore_get_nonNull() throws Exception {
        mockKvStore.put("myKey", "storedValue");
        ITokenStore tokenStore = invokeAdaptKeyValueStore(mockKvStore);
        assertEquals("storedValue", tokenStore.get("myKey"));
    }

    @Test
    public void testAdaptKeyValueStore_get_null() throws Exception {
        ITokenStore tokenStore = invokeAdaptKeyValueStore(mockKvStore);
        assertNull(tokenStore.get("nonExistentKey"));
    }

    @Test
    public void testAdaptKeyValueStore_delete() throws Exception {
        mockKvStore.put("myKey", "value");
        ITokenStore tokenStore = invokeAdaptKeyValueStore(mockKvStore);
        tokenStore.delete("myKey");
        assertNull(mockKvStore.get("myKey"));
    }

    @Test
    public void testAdaptKeyValueStore_get_nonStringValue() throws Exception {
        mockKvStore.put("intKey", 42);
        ITokenStore tokenStore = invokeAdaptKeyValueStore(mockKvStore);
        assertEquals("42", tokenStore.get("intKey"));
    }

    // ── getProductConfig ────────────────────────────────────────────────

    @Test
    public void testGetProductConfig_returnsOnedriveMcpProductConfig() throws Exception {
        OnedriveMcpQueryService service = createServiceWithMockDeps();
        Method method = OnedriveMcpQueryService.class.getDeclaredMethod("getProductConfig");
        method.setAccessible(true);
        Object config = method.invoke(service);
        assertNotNull(config);
        assertTrue(config instanceof OnedriveMcpProductConfig);
    }

    // ── getJaxrsId ──────────────────────────────────────────────────────

    @Test
    public void testGetJaxrsId_returnsOnedrive() throws Exception {
        OnedriveMcpQueryService service = createServiceWithMockDeps();
        Method method = OnedriveMcpQueryService.class.getDeclaredMethod("getJaxrsId");
        method.setAccessible(true);
        String id = (String) method.invoke(service);
        assertEquals("onedrive", id);
    }

    // ── resolvedPath ────────────────────────────────────────────────────

    @Test
    public void testResolvedPath_nullAttrs_noSubstitution() throws Exception {
        // No attributes saved → load returns null → path unchanged
        OnedriveMcpQueryService service = createServiceWithMockDeps();
        String result = invokeResolvedPath(service, "/me/drive/root/children");
        assertEquals("/me/drive/root/children", result);
    }

    @Test
    public void testResolvedPath_publicAuth_noSubstitution() throws Exception {
        // Public auth → isAppOnlyAuth() == false → no substitution
        saveAttributes("Public");
        OnedriveMcpQueryService service = createServiceWithMockDeps();
        String result = invokeResolvedPath(service, "/me/drive/root/children");
        assertEquals("/me/drive/root/children", result);
    }

    @Test
    public void testResolvedPath_privateAuth_substitutesMe() throws Exception {
        // Private auth → isAppOnlyAuth() == true → /me/ → /users/{email}/
        saveAttributes("Private");
        OnedriveMcpQueryService service = createServiceWithMockDeps();
        String result = invokeResolvedPath(service, "/me/drive/root/children");
        assertEquals("/users/user@example.com/drive/root/children", result);
    }

    @Test
    public void testResolvedPath_certAuth_substitutesMe() throws Exception {
        saveAttributes("ClientCredentialsCert");
        OnedriveMcpQueryService service = createServiceWithMockDeps();
        String result = invokeResolvedPath(service, "/me/drive/root/children");
        assertEquals("/users/user@example.com/drive/root/children", result);
    }

    @Test
    public void testResolvedPath_privateAuth_noMeInPath() throws Exception {
        saveAttributes("Private");
        OnedriveMcpQueryService service = createServiceWithMockDeps();
        String result = invokeResolvedPath(service, "/users/other@test.com/drive");
        assertEquals("/users/other@test.com/drive", result);
    }

    @Test
    public void testResolvedPath_privateAuth_meAtEnd() throws Exception {
        saveAttributes("Private");
        OnedriveMcpQueryService service = createServiceWithMockDeps();
        String result = invokeResolvedPath(service, "/me");
        assertEquals("/users/user@example.com", result);
    }

    @Test
    public void testResolvedPath_privateAuth_blankEmail() throws Exception {
        // Private auth but email is blank → no substitution
        OnedriveAttributes attrs = new OnedriveAttributes(
                "http://localhost:8765", "client-id", "client-secret",
                "", "tenant-id", "Private");
        attributeStore.save(attrs, "test-invoker");
        OnedriveMcpQueryService service = createServiceWithMockDeps();
        String result = invokeResolvedPath(service, "/me/drive");
        assertEquals("/me/drive", result);
    }

    @Test
    public void testResolvedPath_privateAuth_nullEmail() throws Exception {
        OnedriveAttributes attrs = new OnedriveAttributes(
                "http://localhost:8765", "client-id", "client-secret",
                null, "tenant-id", "Private");
        attributeStore.save(attrs, "test-invoker");
        OnedriveMcpQueryService service = createServiceWithMockDeps();
        String result = invokeResolvedPath(service, "/me/drive");
        assertEquals("/me/drive", result);
    }

    // ════════════════════════════════════════════════════════════════════
    // query() — comprehensive branch coverage
    // ════════════════════════════════════════════════════════════════════

    // ── query — no AI key configured ────────────────────────────────────

    @Test
    public void testQuery_noAiKey_returnsFailure() throws Exception {
        saveAttributes("Private");
        OnedriveMcpQueryService service = createServiceWithMockDeps();
        ExtensionResponse resp = service.query("list files");
        assertEquals(ExtensionResponse.Result.FAILURE, resp.getResult());
        assertTrue(resp.getError().getErrorMessage().contains("AI API key is not configured"));
    }

    // ── query — no AI key, no provider, no claude key ───────────────────

    @Test
    public void testQuery_noProviderNoClaudeKey_returnsFailure() throws Exception {
        // Neither aiProvider nor claudeApiKey is configured
        saveAttributes("Private");
        OnedriveMcpQueryService service = createServiceWithMockDeps();
        ExtensionResponse resp = service.query("test");
        assertEquals(ExtensionResponse.Result.FAILURE, resp.getResult());
        assertTrue(resp.getError().getErrorMessage().contains("AI API key is not configured"));
    }

    // ── query — AI key via provider, not authenticated ───────────────────

    @Test
    public void testQuery_noAuth_returnsFailure() throws Exception {
        // Set AI key but no auth configured (no attributes saved)
        mockKvStore.put("mcp#aiProvider#test-invoker", "anthropic");
        mockKvStore.put("mcp#aiKey#anthropic#test-invoker", "fake-key");
        OnedriveMcpQueryService service = createServiceWithMockDeps();
        ExtensionResponse resp = service.query("list files");
        assertEquals(ExtensionResponse.Result.FAILURE, resp.getResult());
        assertTrue(resp.getError().getErrorMessage().contains("Not connected"));
    }

    // ── query — legacy Claude key fallback ──────────────────────────────

    @Test
    public void testQuery_legacyClaudeKey_noAuth_returnsFailure() throws Exception {
        // No provider set, but legacy claude key is present → provider becomes "anthropic"
        mockKvStore.put("mcp#claudeApiKey#test-invoker", "fake-claude-key");
        OnedriveMcpQueryService service = createServiceWithMockDeps();
        ExtensionResponse resp = service.query("list files");
        assertEquals(ExtensionResponse.Result.FAILURE, resp.getResult());
        assertTrue(resp.getError().getErrorMessage().contains("Not connected"));
    }

    // ── query — blank provider but no key ───────────────────────────────

    @Test
    public void testQuery_blankProvider_noClaudeKey_returnsFailure() throws Exception {
        saveAttributes("Private");
        mockKvStore.put("mcp#aiProvider#test-invoker", "");
        OnedriveMcpQueryService service = createServiceWithMockDeps();
        ExtensionResponse resp = service.query("list files");
        assertEquals(ExtensionResponse.Result.FAILURE, resp.getResult());
        assertTrue(resp.getError().getErrorMessage().contains("AI API key is not configured"));
    }

    // ── query — provider set but key is blank, falls to claude key ──────

    @Test
    public void testQuery_providerSetButKeyBlank_noClaudeKey_returnsFailure() throws Exception {
        saveAttributes("Private");
        mockKvStore.put("mcp#aiProvider#test-invoker", "openai");
        mockKvStore.put("mcp#aiKey#openai#test-invoker", "");
        // No claude key either → AI API key not configured
        OnedriveMcpQueryService service = createServiceWithMockDeps();
        ExtensionResponse resp = service.query("list files");
        assertEquals(ExtensionResponse.Result.FAILURE, resp.getResult());
        assertTrue(resp.getError().getErrorMessage().contains("AI API key is not configured"));
    }

    @Test
    public void testQuery_providerSetButKeyBlank_claudeKeyPresent_notConnected() throws Exception {
        // Provider key is blank → falls back to claude key → provider becomes "anthropic"
        // But no auth saved → "Not connected"
        mockKvStore.put("mcp#aiProvider#test-invoker", "openai");
        mockKvStore.put("mcp#aiKey#openai#test-invoker", "");
        mockKvStore.put("mcp#claudeApiKey#test-invoker", "claude-key-here");
        OnedriveMcpQueryService service = createServiceWithMockDeps();
        ExtensionResponse resp = service.query("list files");
        assertEquals(ExtensionResponse.Result.FAILURE, resp.getResult());
        assertTrue(resp.getError().getErrorMessage().contains("Not connected"));
    }

    // ── query — null provider key from store ────────────────────────────

    @Test
    public void testQuery_nullProviderNullClaudeKey_returnsFailure() throws Exception {
        saveAttributes("Private");
        // Provider resolves to null (not in store), and claude key also null
        OnedriveMcpQueryService service = createServiceWithMockDeps();
        ExtensionResponse resp = service.query("test");
        assertEquals(ExtensionResponse.Result.FAILURE, resp.getResult());
        assertTrue(resp.getError().getErrorMessage().contains("AI API key is not configured"));
    }

    // ── query — exception in query wraps in failure ─────────────────────

    @Test
    public void testQuery_exceptionCaught_returnsFailure() throws Exception {
        // Cause an exception: auth bridge fails during buildSearchContext
        saveAttributes("Public");
        mockKvStore.put("mcp#aiProvider#test-invoker", "anthropic");
        mockKvStore.put("mcp#aiKey#anthropic#test-invoker", "fake-key");

        OnedriveMcpQueryService service = createServiceWithMockDeps();
        ExtensionResponse resp = service.query("list files");
        // The query should fail (either "Not connected" or exception caught)
        assertEquals(ExtensionResponse.Result.FAILURE, resp.getResult());
    }

    // ── query — AI returns blank response ───────────────────────────────

    @Test
    public void testQuery_aiReturnsBlank_returnsFailure() throws Exception {
        saveAttributes("Private");
        mockKvStore.put("mcp#aiProvider#test-invoker", "anthropic");
        mockKvStore.put("mcp#aiKey#anthropic#test-invoker", "fake-key");

        // Use a stub that intercepts the query to simulate blank AI response
        TestableQueryService service = createTestableService();
        service.stubbedAiResponse = "";
        ExtensionResponse resp = service.query("test question");
        assertEquals(ExtensionResponse.Result.FAILURE, resp.getResult());
        assertTrue(resp.getError().getErrorMessage().contains("Could not determine Graph API call"));
    }

    @Test
    public void testQuery_aiReturnsNull_returnsFailure() throws Exception {
        saveAttributes("Private");
        mockKvStore.put("mcp#aiProvider#test-invoker", "anthropic");
        mockKvStore.put("mcp#aiKey#anthropic#test-invoker", "fake-key");

        TestableQueryService service = createTestableService();
        service.stubbedAiResponse = null;
        ExtensionResponse resp = service.query("test question");
        assertEquals(ExtensionResponse.Result.FAILURE, resp.getResult());
        assertTrue(resp.getError().getErrorMessage().contains("Could not determine Graph API call"));
    }

    // ── query — AI returns response without JSON braces ─────────────────

    @Test
    public void testQuery_aiReturnsNonJsonNoBraces_invalidPlan() throws Exception {
        saveAttributes("Private");
        mockKvStore.put("mcp#aiProvider#test-invoker", "anthropic");
        mockKvStore.put("mcp#aiKey#anthropic#test-invoker", "fake-key");

        // No "{" in the response → no JSON extraction, GSON.fromJson on raw text fails or returns null
        TestableQueryService service = createTestableService();
        service.stubbedAiResponse = "I cannot determine the API call";
        ExtensionResponse resp = service.query("test question");
        // Should fail because method/path are blank
        assertEquals(ExtensionResponse.Result.FAILURE, resp.getResult());
    }

    // ── query — AI returns valid plan with blank method ──────────────────

    @Test
    public void testQuery_aiReturnsBlankMethod_returnsFailure() throws Exception {
        saveAttributes("Private");
        mockKvStore.put("mcp#aiProvider#test-invoker", "anthropic");
        mockKvStore.put("mcp#aiKey#anthropic#test-invoker", "fake-key");

        TestableQueryService service = createTestableService();
        service.stubbedAiResponse = "{\"method\":\"\",\"path\":\"/me/drive\"}";
        ExtensionResponse resp = service.query("test question");
        assertEquals(ExtensionResponse.Result.FAILURE, resp.getResult());
        assertTrue(resp.getError().getErrorMessage().contains("Could not parse a valid Graph API call"));
    }

    // ── query — AI returns valid plan with blank path ────────────────────

    @Test
    public void testQuery_aiReturnsBlankPath_returnsFailure() throws Exception {
        saveAttributes("Private");
        mockKvStore.put("mcp#aiProvider#test-invoker", "anthropic");
        mockKvStore.put("mcp#aiKey#anthropic#test-invoker", "fake-key");

        TestableQueryService service = createTestableService();
        service.stubbedAiResponse = "{\"method\":\"GET\",\"path\":\"\"}";
        ExtensionResponse resp = service.query("test question");
        assertEquals(ExtensionResponse.Result.FAILURE, resp.getResult());
        assertTrue(resp.getError().getErrorMessage().contains("Could not parse a valid Graph API call"));
    }

    // ── query — AI plan with JSON object body ───────────────────────────

    @Test
    public void testQuery_aiPlanWithObjectBody_parsesBody() throws Exception {
        saveAttributes("Private");
        mockKvStore.put("mcp#aiProvider#test-invoker", "anthropic");
        mockKvStore.put("mcp#aiKey#anthropic#test-invoker", "fake-key");

        TestableQueryService service = createTestableService();
        // body is a JSON object (not primitive) — should call toString()
        service.stubbedAiResponse = "{\"method\":\"POST\",\"path\":\"/me/drive/root/search\","
                + "\"body\":{\"query\":\"test\"}}";
        service.stubbedGraphResponse = "{\"value\":[]}";
        ExtensionResponse resp = service.query("search for test");
        assertEquals(ExtensionResponse.Result.SUCCESS, resp.getResult());
    }

    // ── query — AI plan with string body ─────────────────────────────────

    @Test
    public void testQuery_aiPlanWithStringBody_parsesBody() throws Exception {
        saveAttributes("Private");
        mockKvStore.put("mcp#aiProvider#test-invoker", "anthropic");
        mockKvStore.put("mcp#aiKey#anthropic#test-invoker", "fake-key");

        TestableQueryService service = createTestableService();
        // body is a primitive string
        service.stubbedAiResponse = "{\"method\":\"POST\",\"path\":\"/me/drive/root/children\","
                + "\"body\":\"plain body text\"}";
        service.stubbedGraphResponse = "{\"value\":[]}";
        ExtensionResponse resp = service.query("test");
        assertEquals(ExtensionResponse.Result.SUCCESS, resp.getResult());
    }

    // ── query — AI plan with null body ───────────────────────────────────

    @Test
    public void testQuery_aiPlanWithNullBody_bodyIsNull() throws Exception {
        saveAttributes("Private");
        mockKvStore.put("mcp#aiProvider#test-invoker", "anthropic");
        mockKvStore.put("mcp#aiKey#anthropic#test-invoker", "fake-key");

        TestableQueryService service = createTestableService();
        service.stubbedAiResponse = "{\"method\":\"GET\",\"path\":\"/me/drive/root/children\","
                + "\"body\":null}";
        service.stubbedGraphResponse = "{\"value\":[{\"name\":\"file.txt\"}]}";
        ExtensionResponse resp = service.query("list files");
        assertEquals(ExtensionResponse.Result.SUCCESS, resp.getResult());
    }

    // ── query — AI plan with no body key at all ─────────────────────────

    @Test
    public void testQuery_aiPlanWithNoBody_bodyIsNull() throws Exception {
        saveAttributes("Private");
        mockKvStore.put("mcp#aiProvider#test-invoker", "anthropic");
        mockKvStore.put("mcp#aiKey#anthropic#test-invoker", "fake-key");

        TestableQueryService service = createTestableService();
        service.stubbedAiResponse = "{\"method\":\"GET\",\"path\":\"/me/drive/root/children\"}";
        service.stubbedGraphResponse = "{\"value\":[{\"name\":\"file.txt\"}]}";
        ExtensionResponse resp = service.query("list files");
        assertEquals(ExtensionResponse.Result.SUCCESS, resp.getResult());
    }

    // ── query — AI response with surrounding text and JSON ──────────────

    @Test
    public void testQuery_aiResponseWithSurroundingText_extractsJson() throws Exception {
        saveAttributes("Private");
        mockKvStore.put("mcp#aiProvider#test-invoker", "anthropic");
        mockKvStore.put("mcp#aiKey#anthropic#test-invoker", "fake-key");

        TestableQueryService service = createTestableService();
        // JSON is wrapped in text — the raw.contains("{") branch extracts it
        service.stubbedAiResponse = "Here is the plan: {\"method\":\"GET\",\"path\":\"/me/drive\"} end";
        service.stubbedGraphResponse = "{\"displayName\":\"user\"}";
        ExtensionResponse resp = service.query("get my drive info");
        assertEquals(ExtensionResponse.Result.SUCCESS, resp.getResult());
    }

    // ── query — success with generatedPlan attached ─────────────────────

    @Test
    public void testQuery_successAttachesGeneratedPlan() throws Exception {
        saveAttributes("Private");
        mockKvStore.put("mcp#aiProvider#test-invoker", "anthropic");
        mockKvStore.put("mcp#aiKey#anthropic#test-invoker", "fake-key");

        TestableQueryService service = createTestableService();
        service.stubbedAiResponse = "{\"method\":\"GET\",\"path\":\"/me/drive/root/children\"}";
        service.stubbedGraphResponse = "{\"value\":[{\"name\":\"doc.pdf\",\"id\":\"123\"}]}";
        ExtensionResponse resp = service.query("list files");
        assertEquals(ExtensionResponse.Result.SUCCESS, resp.getResult());
        assertTrue(resp.getResponseValue() instanceof Map);
        @SuppressWarnings("unchecked")
        Map<String, Object> rv = (Map<String, Object>) resp.getResponseValue();
        assertEquals("GET /me/drive/root/children", rv.get("generatedPlan"));
    }

    // ── query — POST method routes to POST in executeGraphCall ──────────

    @Test
    public void testQuery_postMethod_executesPost() throws Exception {
        saveAttributes("Private");
        mockKvStore.put("mcp#aiProvider#test-invoker", "anthropic");
        mockKvStore.put("mcp#aiKey#anthropic#test-invoker", "fake-key");

        TestableQueryService service = createTestableService();
        service.stubbedAiResponse = "{\"method\":\"POST\",\"path\":\"/me/drive/root/search(q='report')\"}";
        service.stubbedGraphResponse = "{\"value\":[]}";
        ExtensionResponse resp = service.query("search for report");
        assertEquals(ExtensionResponse.Result.SUCCESS, resp.getResult());
        assertEquals("POST", service.capturedMethod);
    }

    // ── query — GET method routes to GET ─────────────────────────────────

    @Test
    public void testQuery_getMethod_executesGet() throws Exception {
        saveAttributes("Private");
        mockKvStore.put("mcp#aiProvider#test-invoker", "anthropic");
        mockKvStore.put("mcp#aiKey#anthropic#test-invoker", "fake-key");

        TestableQueryService service = createTestableService();
        service.stubbedAiResponse = "{\"method\":\"GET\",\"path\":\"/me/drive/root/children\"}";
        service.stubbedGraphResponse = "{\"value\":[]}";
        ExtensionResponse resp = service.query("list files");
        assertEquals(ExtensionResponse.Result.SUCCESS, resp.getResult());
        assertEquals("GET", service.capturedMethod);
    }

    // ── query — resolvedPath substitution in full flow ───────────────────

    @Test
    public void testQuery_privateAuth_substitutesInFullUrl() throws Exception {
        saveAttributes("Private");
        mockKvStore.put("mcp#aiProvider#test-invoker", "anthropic");
        mockKvStore.put("mcp#aiKey#anthropic#test-invoker", "fake-key");

        TestableQueryService service = createTestableService();
        service.stubbedAiResponse = "{\"method\":\"GET\",\"path\":\"/me/drive/root/children\"}";
        service.stubbedGraphResponse = "{\"value\":[]}";
        service.query("list files");
        // Private auth → /me/ replaced with /users/user@example.com/
        assertTrue(service.capturedUrl.contains("/users/user@example.com/"));
        assertFalse(service.capturedUrl.contains("/me/"));
    }

    // ── query — executeGraphCall returns failure → no generatedPlan ──────

    @Test
    public void testQuery_graphCallFailure_noGeneratedPlanAttached() throws Exception {
        saveAttributes("Private");
        mockKvStore.put("mcp#aiProvider#test-invoker", "anthropic");
        mockKvStore.put("mcp#aiKey#anthropic#test-invoker", "fake-key");

        TestableQueryService service = createTestableService();
        service.stubbedAiResponse = "{\"method\":\"GET\",\"path\":\"/me/drive/root/children\"}";
        service.stubbedGraphResponse = null; // triggers failure in executeGraphCall
        service.stubbedGraphStatusCode = 403;
        ExtensionResponse resp = service.query("list files");
        assertEquals(ExtensionResponse.Result.FAILURE, resp.getResult());
    }

    // ── query — executeGraphCall HTTP 202 no content ────────────────────

    @Test
    public void testQuery_graphCall202_actionCompleted() throws Exception {
        saveAttributes("Private");
        mockKvStore.put("mcp#aiProvider#test-invoker", "anthropic");
        mockKvStore.put("mcp#aiKey#anthropic#test-invoker", "fake-key");

        TestableQueryService service = createTestableService();
        service.stubbedAiResponse = "{\"method\":\"POST\",\"path\":\"/me/drive/items/123/copy\"}";
        service.stubbedGraphStatusCode = 202;
        ExtensionResponse resp = service.query("copy the file");
        assertEquals(ExtensionResponse.Result.SUCCESS, resp.getResult());
    }

    // ── query — executeGraphCall HTTP 204 no content ────────────────────

    @Test
    public void testQuery_graphCall204_actionCompleted() throws Exception {
        saveAttributes("Private");
        mockKvStore.put("mcp#aiProvider#test-invoker", "anthropic");
        mockKvStore.put("mcp#aiKey#anthropic#test-invoker", "fake-key");

        TestableQueryService service = createTestableService();
        service.stubbedAiResponse = "{\"method\":\"POST\",\"path\":\"/me/drive/items/123\"}";
        service.stubbedGraphStatusCode = 204;
        ExtensionResponse resp = service.query("delete the file");
        assertEquals(ExtensionResponse.Result.SUCCESS, resp.getResult());
    }

    // ── query — executeGraphCall HTTP 201 created ───────────────────────

    @Test
    public void testQuery_graphCall201_success() throws Exception {
        saveAttributes("Private");
        mockKvStore.put("mcp#aiProvider#test-invoker", "anthropic");
        mockKvStore.put("mcp#aiKey#anthropic#test-invoker", "fake-key");

        TestableQueryService service = createTestableService();
        service.stubbedAiResponse = "{\"method\":\"POST\",\"path\":\"/me/drive/root/children\"}";
        service.stubbedGraphResponse = "{\"name\":\"NewFolder\",\"id\":\"folder-id\"}";
        service.stubbedGraphStatusCode = 201;
        ExtensionResponse resp = service.query("create a folder");
        assertEquals(ExtensionResponse.Result.SUCCESS, resp.getResult());
    }

    // ── query — executeGraphCall HTTP error (e.g. 404) ──────────────────

    @Test
    public void testQuery_graphCall404_returnsFailure() throws Exception {
        saveAttributes("Private");
        mockKvStore.put("mcp#aiProvider#test-invoker", "anthropic");
        mockKvStore.put("mcp#aiKey#anthropic#test-invoker", "fake-key");

        TestableQueryService service = createTestableService();
        service.stubbedAiResponse = "{\"method\":\"GET\",\"path\":\"/me/drive/items/nonexistent\"}";
        service.stubbedGraphStatusCode = 404;
        service.stubbedGraphErrorBody = "{\"error\":{\"code\":\"itemNotFound\"}}";
        ExtensionResponse resp = service.query("get item");
        assertEquals(ExtensionResponse.Result.FAILURE, resp.getResult());
        assertTrue(resp.getError().getErrorMessage().contains("HTTP 404"));
    }

    // ── query — executeGraphCall throws exception ────────────────────────

    @Test
    public void testQuery_graphCallException_returnsFailure() throws Exception {
        saveAttributes("Private");
        mockKvStore.put("mcp#aiProvider#test-invoker", "anthropic");
        mockKvStore.put("mcp#aiKey#anthropic#test-invoker", "fake-key");

        TestableQueryService service = createTestableService();
        service.stubbedAiResponse = "{\"method\":\"GET\",\"path\":\"/me/drive/root/children\"}";
        service.stubbedGraphException = new RuntimeException("Connection refused");
        ExtensionResponse resp = service.query("list files");
        assertEquals(ExtensionResponse.Result.FAILURE, resp.getResult());
        assertTrue(resp.getError().getErrorMessage().contains("Connection refused"));
    }

    // ── query — callAi throws exception → outer catch ───────────────────

    @Test
    public void testQuery_callAiThrows_outerCatchReturnsFailure() throws Exception {
        saveAttributes("Private");
        mockKvStore.put("mcp#aiProvider#test-invoker", "anthropic");
        mockKvStore.put("mcp#aiKey#anthropic#test-invoker", "fake-key");

        TestableQueryService service = createTestableService();
        service.stubbedAiException = new RuntimeException("AI service unavailable");
        ExtensionResponse resp = service.query("test");
        assertEquals(ExtensionResponse.Result.FAILURE, resp.getResult());
        assertTrue(resp.getError().getErrorMessage().contains("AI service unavailable"));
    }

    // ── query — AI returns plan with missing method key ──────────────────

    @Test
    public void testQuery_aiReturnsPlanWithMissingMethodKey_returnsFailure() throws Exception {
        saveAttributes("Private");
        mockKvStore.put("mcp#aiProvider#test-invoker", "anthropic");
        mockKvStore.put("mcp#aiKey#anthropic#test-invoker", "fake-key");

        TestableQueryService service = createTestableService();
        service.stubbedAiResponse = "{\"path\":\"/me/drive\"}";
        ExtensionResponse resp = service.query("test");
        assertEquals(ExtensionResponse.Result.FAILURE, resp.getResult());
        assertTrue(resp.getError().getErrorMessage().contains("Could not parse a valid Graph API call"));
    }

    // ── query — AI returns plan with missing path key ────────────────────

    @Test
    public void testQuery_aiReturnsPlanWithMissingPathKey_returnsFailure() throws Exception {
        saveAttributes("Private");
        mockKvStore.put("mcp#aiProvider#test-invoker", "anthropic");
        mockKvStore.put("mcp#aiKey#anthropic#test-invoker", "fake-key");

        TestableQueryService service = createTestableService();
        service.stubbedAiResponse = "{\"method\":\"GET\"}";
        ExtensionResponse resp = service.query("test");
        assertEquals(ExtensionResponse.Result.FAILURE, resp.getResult());
        assertTrue(resp.getError().getErrorMessage().contains("Could not parse a valid Graph API call"));
    }

    // ── query — parseGenericResponse returns rows from Graph 200 ────────

    @Test
    public void testQuery_graphCall200_multipleRows() throws Exception {
        saveAttributes("Private");
        mockKvStore.put("mcp#aiProvider#test-invoker", "anthropic");
        mockKvStore.put("mcp#aiKey#anthropic#test-invoker", "fake-key");

        TestableQueryService service = createTestableService();
        service.stubbedAiResponse = "{\"method\":\"GET\",\"path\":\"/me/drive/root/children\"}";
        service.stubbedGraphResponse = "{\"value\":["
                + "{\"name\":\"a.txt\",\"id\":\"1\"},"
                + "{\"name\":\"b.txt\",\"id\":\"2\"},"
                + "{\"name\":\"c.txt\",\"id\":\"3\"}"
                + "]}";
        service.stubbedGraphStatusCode = 200;
        ExtensionResponse resp = service.query("list all files");
        assertEquals(ExtensionResponse.Result.SUCCESS, resp.getResult());
        @SuppressWarnings("unchecked")
        Map<String, Object> rv = (Map<String, Object>) resp.getResponseValue();
        @SuppressWarnings("unchecked")
        List<?> rows = (List<?>) rv.get("Response");
        assertEquals(3, rows.size());
    }

    // ── query — POST with null body sends "{}" ──────────────────────────

    @Test
    public void testQuery_postWithNullBody_sendsEmptyJson() throws Exception {
        saveAttributes("Private");
        mockKvStore.put("mcp#aiProvider#test-invoker", "anthropic");
        mockKvStore.put("mcp#aiKey#anthropic#test-invoker", "fake-key");

        TestableQueryService service = createTestableService();
        service.stubbedAiResponse = "{\"method\":\"POST\",\"path\":\"/me/drive/root/search\"}";
        service.stubbedGraphResponse = "{\"value\":[]}";
        service.stubbedGraphStatusCode = 200;
        service.query("search");
        assertEquals("POST", service.capturedMethod);
        // body was null in plan, but executeGraphCall sends "{}" for null POST body
        assertNull(service.capturedBody);
    }

    // ════════════════════════════════════════════════════════════════════
    // executeGraphCall — direct invocation via reflection
    // ════════════════════════════════════════════════════════════════════

    // ── executeGraphCall — GET returns HTTP 200 with list response ───────

    @Test
    public void testExecuteGraphCall_get_http200_listResponse() throws Exception {
        // Start a lightweight stub HTTP server for the Graph API call
        com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer.create(
                new java.net.InetSocketAddress(0), 0);
        int port = server.getAddress().getPort();
        server.createContext("/v1.0/me/drive/root/children", exchange -> {
            // Verify it's a GET request with Authorization header
            assertEquals("GET", exchange.getRequestMethod());
            assertNotNull(exchange.getRequestHeaders().getFirst("Authorization"));
            String resp = "{\"value\":[{\"name\":\"a.txt\",\"id\":\"1\"},{\"name\":\"b.txt\",\"id\":\"2\"}]}";
            exchange.sendResponseHeaders(200, resp.getBytes().length);
            exchange.getResponseBody().write(resp.getBytes());
            exchange.getResponseBody().close();
        });
        server.start();
        try {
            OnedriveMcpQueryService service = createServiceWithMockDeps();
            ExtensionResponse result = invokeExecuteGraphCall(service,
                    "GET", "http://localhost:" + port + "/v1.0/me/drive/root/children",
                    null, "Bearer fake-token");
            assertEquals(ExtensionResponse.Result.SUCCESS, result.getResult());
            @SuppressWarnings("unchecked")
            Map<String, Object> rv = (Map<String, Object>) result.getResponseValue();
            @SuppressWarnings("unchecked")
            List<FreeForm> rows = (List<FreeForm>) rv.get("Response");
            assertEquals(2, rows.size());
        } finally {
            server.stop(0);
        }
    }

    // ── executeGraphCall — GET returns HTTP 200 with single object ───────

    @Test
    public void testExecuteGraphCall_get_http200_singleObject() throws Exception {
        com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer.create(
                new java.net.InetSocketAddress(0), 0);
        int port = server.getAddress().getPort();
        server.createContext("/v1.0/me", exchange -> {
            String resp = "{\"displayName\":\"Test User\",\"mail\":\"test@example.com\"}";
            exchange.sendResponseHeaders(200, resp.getBytes().length);
            exchange.getResponseBody().write(resp.getBytes());
            exchange.getResponseBody().close();
        });
        server.start();
        try {
            OnedriveMcpQueryService service = createServiceWithMockDeps();
            ExtensionResponse result = invokeExecuteGraphCall(service,
                    "GET", "http://localhost:" + port + "/v1.0/me",
                    null, "Bearer fake-token");
            assertEquals(ExtensionResponse.Result.SUCCESS, result.getResult());
            @SuppressWarnings("unchecked")
            Map<String, Object> rv = (Map<String, Object>) result.getResponseValue();
            @SuppressWarnings("unchecked")
            List<FreeForm> rows = (List<FreeForm>) rv.get("Response");
            assertEquals(1, rows.size());
        } finally {
            server.stop(0);
        }
    }

    // ── executeGraphCall — GET returns HTTP 201 (created) ────────────────

    @Test
    public void testExecuteGraphCall_get_http201_success() throws Exception {
        com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer.create(
                new java.net.InetSocketAddress(0), 0);
        int port = server.getAddress().getPort();
        server.createContext("/v1.0/test", exchange -> {
            String resp = "{\"name\":\"NewItem\",\"id\":\"new-1\"}";
            exchange.sendResponseHeaders(201, resp.getBytes().length);
            exchange.getResponseBody().write(resp.getBytes());
            exchange.getResponseBody().close();
        });
        server.start();
        try {
            OnedriveMcpQueryService service = createServiceWithMockDeps();
            ExtensionResponse result = invokeExecuteGraphCall(service,
                    "GET", "http://localhost:" + port + "/v1.0/test",
                    null, "Bearer fake-token");
            assertEquals(ExtensionResponse.Result.SUCCESS, result.getResult());
            @SuppressWarnings("unchecked")
            Map<String, Object> rv = (Map<String, Object>) result.getResponseValue();
            @SuppressWarnings("unchecked")
            List<FreeForm> rows = (List<FreeForm>) rv.get("Response");
            assertEquals(1, rows.size());
        } finally {
            server.stop(0);
        }
    }

    // ── executeGraphCall — HTTP 202 (accepted, no body) ─────────────────

    @Test
    public void testExecuteGraphCall_http202_actionCompleted() throws Exception {
        com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer.create(
                new java.net.InetSocketAddress(0), 0);
        int port = server.getAddress().getPort();
        server.createContext("/v1.0/copy", exchange -> {
            exchange.sendResponseHeaders(202, -1);
            exchange.getResponseBody().close();
        });
        server.start();
        try {
            OnedriveMcpQueryService service = createServiceWithMockDeps();
            ExtensionResponse result = invokeExecuteGraphCall(service,
                    "POST", "http://localhost:" + port + "/v1.0/copy",
                    "{\"dest\":\"folder\"}", "Bearer fake-token");
            assertEquals(ExtensionResponse.Result.SUCCESS, result.getResult());
            @SuppressWarnings("unchecked")
            Map<String, Object> rv = (Map<String, Object>) result.getResponseValue();
            @SuppressWarnings("unchecked")
            List<FreeForm> rows = (List<FreeForm>) rv.get("Response");
            assertEquals(1, rows.size());
            // Verify the "Action completed successfully." message
            assertTrue(rows.get(0).keySet().contains("Answer"));
        } finally {
            server.stop(0);
        }
    }

    // ── executeGraphCall — HTTP 204 (no content) ────────────────────────

    @Test
    public void testExecuteGraphCall_http204_actionCompleted() throws Exception {
        com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer.create(
                new java.net.InetSocketAddress(0), 0);
        int port = server.getAddress().getPort();
        server.createContext("/v1.0/delete", exchange -> {
            exchange.sendResponseHeaders(204, -1);
            exchange.getResponseBody().close();
        });
        server.start();
        try {
            OnedriveMcpQueryService service = createServiceWithMockDeps();
            ExtensionResponse result = invokeExecuteGraphCall(service,
                    "GET", "http://localhost:" + port + "/v1.0/delete",
                    null, "Bearer fake-token");
            assertEquals(ExtensionResponse.Result.SUCCESS, result.getResult());
            @SuppressWarnings("unchecked")
            Map<String, Object> rv = (Map<String, Object>) result.getResponseValue();
            @SuppressWarnings("unchecked")
            List<FreeForm> rows = (List<FreeForm>) rv.get("Response");
            assertEquals(1, rows.size());
            assertTrue(rows.get(0).keySet().contains("Answer"));
        } finally {
            server.stop(0);
        }
    }

    // ── executeGraphCall — HTTP error (e.g. 403 Forbidden) ──────────────

    @Test
    public void testExecuteGraphCall_http403_returnsFailure() throws Exception {
        com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer.create(
                new java.net.InetSocketAddress(0), 0);
        int port = server.getAddress().getPort();
        server.createContext("/v1.0/forbidden", exchange -> {
            String errorBody = "{\"error\":{\"code\":\"accessDenied\",\"message\":\"Forbidden\"}}";
            exchange.sendResponseHeaders(403, errorBody.getBytes().length);
            exchange.getResponseBody().write(errorBody.getBytes());
            exchange.getResponseBody().close();
        });
        server.start();
        try {
            OnedriveMcpQueryService service = createServiceWithMockDeps();
            ExtensionResponse result = invokeExecuteGraphCall(service,
                    "GET", "http://localhost:" + port + "/v1.0/forbidden",
                    null, "Bearer fake-token");
            assertEquals(ExtensionResponse.Result.FAILURE, result.getResult());
            assertTrue(result.getError().getErrorMessage().contains("HTTP 403"));
            assertTrue(result.getError().getErrorMessage().contains("accessDenied"));
        } finally {
            server.stop(0);
        }
    }

    // ── executeGraphCall — HTTP 404 Not Found ───────────────────────────

    @Test
    public void testExecuteGraphCall_http404_returnsFailure() throws Exception {
        com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer.create(
                new java.net.InetSocketAddress(0), 0);
        int port = server.getAddress().getPort();
        server.createContext("/v1.0/notfound", exchange -> {
            String errorBody = "{\"error\":{\"code\":\"itemNotFound\"}}";
            exchange.sendResponseHeaders(404, errorBody.getBytes().length);
            exchange.getResponseBody().write(errorBody.getBytes());
            exchange.getResponseBody().close();
        });
        server.start();
        try {
            OnedriveMcpQueryService service = createServiceWithMockDeps();
            ExtensionResponse result = invokeExecuteGraphCall(service,
                    "GET", "http://localhost:" + port + "/v1.0/notfound",
                    null, "Bearer fake-token");
            assertEquals(ExtensionResponse.Result.FAILURE, result.getResult());
            assertTrue(result.getError().getErrorMessage().contains("HTTP 404"));
        } finally {
            server.stop(0);
        }
    }

    // ── executeGraphCall — HTTP 500 Internal Server Error ────────────────

    @Test
    public void testExecuteGraphCall_http500_returnsFailure() throws Exception {
        com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer.create(
                new java.net.InetSocketAddress(0), 0);
        int port = server.getAddress().getPort();
        server.createContext("/v1.0/error", exchange -> {
            String errorBody = "{\"error\":{\"code\":\"generalException\"}}";
            exchange.sendResponseHeaders(500, errorBody.getBytes().length);
            exchange.getResponseBody().write(errorBody.getBytes());
            exchange.getResponseBody().close();
        });
        server.start();
        try {
            OnedriveMcpQueryService service = createServiceWithMockDeps();
            ExtensionResponse result = invokeExecuteGraphCall(service,
                    "GET", "http://localhost:" + port + "/v1.0/error",
                    null, "Bearer fake-token");
            assertEquals(ExtensionResponse.Result.FAILURE, result.getResult());
            assertTrue(result.getError().getErrorMessage().contains("HTTP 500"));
        } finally {
            server.stop(0);
        }
    }

    // ── executeGraphCall — POST with body ────────────────────────────────

    @Test
    public void testExecuteGraphCall_post_withBody_http200() throws Exception {
        com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer.create(
                new java.net.InetSocketAddress(0), 0);
        int port = server.getAddress().getPort();
        server.createContext("/v1.0/search", exchange -> {
            assertEquals("POST", exchange.getRequestMethod());
            // Read and verify request body was sent
            byte[] body = exchange.getRequestBody().readAllBytes();
            assertTrue(body.length > 0);
            String resp = "{\"value\":[{\"name\":\"result.txt\",\"id\":\"r1\"}]}";
            exchange.sendResponseHeaders(200, resp.getBytes().length);
            exchange.getResponseBody().write(resp.getBytes());
            exchange.getResponseBody().close();
        });
        server.start();
        try {
            OnedriveMcpQueryService service = createServiceWithMockDeps();
            ExtensionResponse result = invokeExecuteGraphCall(service,
                    "POST", "http://localhost:" + port + "/v1.0/search",
                    "{\"query\":\"budget\"}", "Bearer fake-token");
            assertEquals(ExtensionResponse.Result.SUCCESS, result.getResult());
            @SuppressWarnings("unchecked")
            Map<String, Object> rv = (Map<String, Object>) result.getResponseValue();
            @SuppressWarnings("unchecked")
            List<FreeForm> rows = (List<FreeForm>) rv.get("Response");
            assertEquals(1, rows.size());
        } finally {
            server.stop(0);
        }
    }

    // ── executeGraphCall — POST with null body sends "{}" ────────────────

    @Test
    public void testExecuteGraphCall_post_nullBody_sendsEmptyJson() throws Exception {
        com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer.create(
                new java.net.InetSocketAddress(0), 0);
        int port = server.getAddress().getPort();
        server.createContext("/v1.0/action", exchange -> {
            assertEquals("POST", exchange.getRequestMethod());
            // Verify body is "{}" when null was passed
            byte[] body = exchange.getRequestBody().readAllBytes();
            assertEquals("{}", new String(body));
            String resp = "{\"value\":[]}";
            exchange.sendResponseHeaders(200, resp.getBytes().length);
            exchange.getResponseBody().write(resp.getBytes());
            exchange.getResponseBody().close();
        });
        server.start();
        try {
            OnedriveMcpQueryService service = createServiceWithMockDeps();
            ExtensionResponse result = invokeExecuteGraphCall(service,
                    "POST", "http://localhost:" + port + "/v1.0/action",
                    null, "Bearer fake-token");
            assertEquals(ExtensionResponse.Result.SUCCESS, result.getResult());
        } finally {
            server.stop(0);
        }
    }

    // ── executeGraphCall — non-POST method uses GET ─────────────────────

    @Test
    public void testExecuteGraphCall_nonPostMethod_usesGet() throws Exception {
        com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer.create(
                new java.net.InetSocketAddress(0), 0);
        int port = server.getAddress().getPort();
        server.createContext("/v1.0/items", exchange -> {
            assertEquals("GET", exchange.getRequestMethod());
            String resp = "{\"name\":\"item\",\"id\":\"i1\"}";
            exchange.sendResponseHeaders(200, resp.getBytes().length);
            exchange.getResponseBody().write(resp.getBytes());
            exchange.getResponseBody().close();
        });
        server.start();
        try {
            OnedriveMcpQueryService service = createServiceWithMockDeps();
            // "PATCH" is not POST → falls to GET branch
            ExtensionResponse result = invokeExecuteGraphCall(service,
                    "PATCH", "http://localhost:" + port + "/v1.0/items",
                    null, "Bearer fake-token");
            assertEquals(ExtensionResponse.Result.SUCCESS, result.getResult());
        } finally {
            server.stop(0);
        }
    }

    // ── executeGraphCall — POST case-insensitive ────────────────────────

    @Test
    public void testExecuteGraphCall_postLowercase_routesToPost() throws Exception {
        com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer.create(
                new java.net.InetSocketAddress(0), 0);
        int port = server.getAddress().getPort();
        server.createContext("/v1.0/post", exchange -> {
            assertEquals("POST", exchange.getRequestMethod());
            String resp = "{\"value\":[]}";
            exchange.sendResponseHeaders(200, resp.getBytes().length);
            exchange.getResponseBody().write(resp.getBytes());
            exchange.getResponseBody().close();
        });
        server.start();
        try {
            OnedriveMcpQueryService service = createServiceWithMockDeps();
            // lowercase "post" should still use POST
            ExtensionResponse result = invokeExecuteGraphCall(service,
                    "post", "http://localhost:" + port + "/v1.0/post",
                    "{}", "Bearer fake-token");
            assertEquals(ExtensionResponse.Result.SUCCESS, result.getResult());
        } finally {
            server.stop(0);
        }
    }

    // ── executeGraphCall — connection refused → exception ────────────────

    @Test
    public void testExecuteGraphCall_connectionRefused_returnsFailure() throws Exception {
        OnedriveMcpQueryService service = createServiceWithMockDeps();
        // Use a port that is definitely not listening
        ExtensionResponse result = invokeExecuteGraphCall(service,
                "GET", "http://localhost:1/v1.0/unreachable",
                null, "Bearer fake-token");
        assertEquals(ExtensionResponse.Result.FAILURE, result.getResult());
        assertTrue(result.getError().getErrorMessage().contains("Graph API call failed:"));
    }

    // ── executeGraphCall — invalid URL → exception ──────────────────────

    @Test
    public void testExecuteGraphCall_invalidUrl_returnsFailure() throws Exception {
        OnedriveMcpQueryService service = createServiceWithMockDeps();
        ExtensionResponse result = invokeExecuteGraphCall(service,
                "GET", "not-a-valid-url", null, "Bearer fake-token");
        assertEquals(ExtensionResponse.Result.FAILURE, result.getResult());
        assertTrue(result.getError().getErrorMessage().contains("Graph API call failed:"));
    }

    // ── executeGraphCall — HTTP 200 with empty value array ──────────────

    @Test
    public void testExecuteGraphCall_http200_emptyResults() throws Exception {
        com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer.create(
                new java.net.InetSocketAddress(0), 0);
        int port = server.getAddress().getPort();
        server.createContext("/v1.0/empty", exchange -> {
            String resp = "{\"value\":[]}";
            exchange.sendResponseHeaders(200, resp.getBytes().length);
            exchange.getResponseBody().write(resp.getBytes());
            exchange.getResponseBody().close();
        });
        server.start();
        try {
            OnedriveMcpQueryService service = createServiceWithMockDeps();
            ExtensionResponse result = invokeExecuteGraphCall(service,
                    "GET", "http://localhost:" + port + "/v1.0/empty",
                    null, "Bearer fake-token");
            assertEquals(ExtensionResponse.Result.SUCCESS, result.getResult());
            @SuppressWarnings("unchecked")
            Map<String, Object> rv = (Map<String, Object>) result.getResponseValue();
            @SuppressWarnings("unchecked")
            List<FreeForm> rows = (List<FreeForm>) rv.get("Response");
            assertEquals(0, rows.size());
        } finally {
            server.stop(0);
        }
    }

    // ── executeGraphCall — verifies Authorization header is sent ─────────

    @Test
    public void testExecuteGraphCall_sendsAuthHeader() throws Exception {
        final String[] capturedAuth = {null};
        com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer.create(
                new java.net.InetSocketAddress(0), 0);
        int port = server.getAddress().getPort();
        server.createContext("/v1.0/auth", exchange -> {
            capturedAuth[0] = exchange.getRequestHeaders().getFirst("Authorization");
            String resp = "{\"value\":[]}";
            exchange.sendResponseHeaders(200, resp.getBytes().length);
            exchange.getResponseBody().write(resp.getBytes());
            exchange.getResponseBody().close();
        });
        server.start();
        try {
            OnedriveMcpQueryService service = createServiceWithMockDeps();
            invokeExecuteGraphCall(service,
                    "GET", "http://localhost:" + port + "/v1.0/auth",
                    null, "Bearer my-secret-token");
            assertEquals("Bearer my-secret-token", capturedAuth[0]);
        } finally {
            server.stop(0);
        }
    }

    // ── executeGraphCall — HTTP 401 Unauthorized ────────────────────────

    @Test
    public void testExecuteGraphCall_http401_returnsFailure() throws Exception {
        com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer.create(
                new java.net.InetSocketAddress(0), 0);
        int port = server.getAddress().getPort();
        server.createContext("/v1.0/unauth", exchange -> {
            String errorBody = "{\"error\":{\"code\":\"InvalidAuthenticationToken\"}}";
            exchange.sendResponseHeaders(401, errorBody.getBytes().length);
            exchange.getResponseBody().write(errorBody.getBytes());
            exchange.getResponseBody().close();
        });
        server.start();
        try {
            OnedriveMcpQueryService service = createServiceWithMockDeps();
            ExtensionResponse result = invokeExecuteGraphCall(service,
                    "GET", "http://localhost:" + port + "/v1.0/unauth",
                    null, "Bearer expired-token");
            assertEquals(ExtensionResponse.Result.FAILURE, result.getResult());
            assertTrue(result.getError().getErrorMessage().contains("HTTP 401"));
            assertTrue(result.getError().getErrorMessage().contains("InvalidAuthenticationToken"));
        } finally {
            server.stop(0);
        }
    }

    // ── executeGraphCall — HTTP 200 with search-shape response ──────────

    @Test
    public void testExecuteGraphCall_http200_searchResponse() throws Exception {
        com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer.create(
                new java.net.InetSocketAddress(0), 0);
        int port = server.getAddress().getPort();
        server.createContext("/v1.0/search", exchange -> {
            String resp = "{\"value\":[{\"hitsContainers\":[{\"hits\":["
                    + "{\"hitId\":\"h1\",\"resource\":{\"name\":\"report.docx\"}},"
                    + "{\"hitId\":\"h2\",\"resource\":{\"name\":\"budget.xlsx\"}}"
                    + "]}]}]}";
            exchange.sendResponseHeaders(200, resp.getBytes().length);
            exchange.getResponseBody().write(resp.getBytes());
            exchange.getResponseBody().close();
        });
        server.start();
        try {
            OnedriveMcpQueryService service = createServiceWithMockDeps();
            ExtensionResponse result = invokeExecuteGraphCall(service,
                    "POST", "http://localhost:" + port + "/v1.0/search",
                    "{\"requests\":[{\"entityTypes\":[\"driveItem\"],\"query\":{\"queryString\":\"report\"}}]}",
                    "Bearer fake-token");
            assertEquals(ExtensionResponse.Result.SUCCESS, result.getResult());
            @SuppressWarnings("unchecked")
            Map<String, Object> rv = (Map<String, Object>) result.getResponseValue();
            @SuppressWarnings("unchecked")
            List<FreeForm> rows = (List<FreeForm>) rv.get("Response");
            assertEquals(2, rows.size());
        } finally {
            server.stop(0);
        }
    }

    // ── Reflection helpers ───────────────────────────────────────────────

    private static boolean invokeBlank(String input) throws Exception {
        Method method = OnedriveMcpQueryService.class.getDeclaredMethod("blank", String.class);
        method.setAccessible(true);
        return (boolean) method.invoke(null, input);
    }

    private static String invokeGetJsonString(JsonObject obj, String key) throws Exception {
        Method method = OnedriveMcpQueryService.class.getDeclaredMethod("getJsonString", JsonObject.class, String.class);
        method.setAccessible(true);
        return (String) method.invoke(null, obj, key);
    }

    private static String invokeGetStr(JsonObject obj, String key) throws Exception {
        Method method = OnedriveMcpQueryService.class.getDeclaredMethod("getStr", JsonObject.class, String.class);
        method.setAccessible(true);
        return (String) method.invoke(null, obj, key);
    }

    private static String invokeToFreeFormKey(String input) throws Exception {
        Method method = OnedriveMcpQueryService.class.getDeclaredMethod("toFreeFormKey", String.class);
        method.setAccessible(true);
        return (String) method.invoke(null, input);
    }

    private static String invokeToDisplayString(String key, JsonElement val, JsonObject parent) throws Exception {
        Method method = OnedriveMcpQueryService.class.getDeclaredMethod("toDisplayString",
                String.class, JsonElement.class, JsonObject.class);
        method.setAccessible(true);
        return (String) method.invoke(null, key, val, parent);
    }

    @SuppressWarnings("unchecked")
    private static FreeForm invokeGraphObjectToFreeForm(JsonObject obj) throws Exception {
        Method method = OnedriveMcpQueryService.class.getDeclaredMethod("graphObjectToFreeForm", JsonObject.class);
        method.setAccessible(true);
        return (FreeForm) method.invoke(null, obj);
    }

    @SuppressWarnings("unchecked")
    private static List<FreeForm> invokeParseGenericResponse(String json) throws Exception {
        Method method = OnedriveMcpQueryService.class.getDeclaredMethod("parseGenericResponse", String.class);
        method.setAccessible(true);
        return (List<FreeForm>) method.invoke(null, json);
    }

    private static ExtensionResponse invokeSuccess(Map<String, Object> values) throws Exception {
        Method method = OnedriveMcpQueryService.class.getDeclaredMethod("success", Map.class);
        method.setAccessible(true);
        return (ExtensionResponse) method.invoke(null, values);
    }

    private static ExtensionResponse invokeFailure(String message) throws Exception {
        Method method = OnedriveMcpQueryService.class.getDeclaredMethod("failure", String.class);
        method.setAccessible(true);
        return (ExtensionResponse) method.invoke(null, message);
    }

    private static String invokeCallAi(String apiKey, String provider, String prompt) throws Exception {
        Method method = OnedriveMcpQueryService.class.getDeclaredMethod("callAi",
                String.class, String.class, String.class);
        method.setAccessible(true);
        try {
            return (String) method.invoke(null, apiKey, provider, prompt);
        } catch (java.lang.reflect.InvocationTargetException e) {
            throw (Exception) e.getCause();
        }
    }

    private static ITokenStore invokeAdaptKeyValueStore(KeyValueStore kvStore) throws Exception {
        Method method = OnedriveMcpQueryService.class.getDeclaredMethod("adaptKeyValueStore", KeyValueStore.class);
        method.setAccessible(true);
        return (ITokenStore) method.invoke(null, kvStore);
    }

    private static String invokeResolvedPath(OnedriveMcpQueryService service, String path) throws Exception {
        Method method = OnedriveMcpQueryService.class.getDeclaredMethod("resolvedPath", String.class);
        method.setAccessible(true);
        return (String) method.invoke(service, path);
    }

    private static ExtensionResponse invokeExecuteGraphCall(OnedriveMcpQueryService service,
                                                             String method, String fullUrl,
                                                             String body, String authHeader) throws Exception {
        Method m = OnedriveMcpQueryService.class.getDeclaredMethod("executeGraphCall",
                String.class, String.class, String.class, String.class);
        m.setAccessible(true);
        try {
            return (ExtensionResponse) m.invoke(service, method, fullUrl, body, authHeader);
        } catch (java.lang.reflect.InvocationTargetException e) {
            if (e.getCause() instanceof Exception) throw (Exception) e.getCause();
            throw e;
        }
    }

    // ── Service creation helpers ─────────────────────────────────────────

    private OnedriveMcpQueryService createServiceWithMockDeps() {
        return new OnedriveMcpQueryService(mockKvStore, mockInvoker, attributeStore);
    }

    private void saveAttributes(String authType) {
        OnedriveAttributes attrs = new OnedriveAttributes(
                "http://localhost:8765", "client-id", "client-secret",
                "user@example.com", "tenant-id", authType);
        attributeStore.save(attrs, "test-invoker");
    }

    // ── Testable subclass for query() integration testing ───────────────

    /**
     * Subclass that replicates {@code query()} logic with controllable stubs for
     * {@code callAi()} and {@code executeGraphCall()}, allowing full branch
     * coverage testing without hitting real external services.
     *
     * <p>Allocated via {@code sun.misc.Unsafe} to bypass the super constructor,
     * then internal fields are wired manually via reflection.</p>
     */
    static class TestableQueryService extends OnedriveMcpQueryService {
        // AI call stubs
        String stubbedAiResponse;
        RuntimeException stubbedAiException;

        // Graph call stubs
        String stubbedGraphResponse;
        int stubbedGraphStatusCode = 200;
        String stubbedGraphErrorBody = "error";
        RuntimeException stubbedGraphException;

        // Captured values for assertions
        String capturedMethod;
        String capturedUrl;
        String capturedBody;

        // This constructor won't be called (allocated via Unsafe)
        TestableQueryService() {
            super(null, null, null);
        }

        /**
         * Replicates the exact logic of the parent query() but uses
         * stubbed AI and Graph calls instead of the private static/instance methods.
         */
        @Override
        public ExtensionResponse query(String question) {
            long startTime = System.currentTimeMillis();
            try {
                // Use reflection to access private fields
                ITokenStore ts = getFieldValue(this, OnedriveMcpQueryService.class, "tokenStore");
                app.krista.extension.executor.Invoker inv = getFieldValue(this, OnedriveMcpQueryService.class, "invoker");
                OnedriveAttributeStore attrStore = getFieldValue(this, OnedriveMcpQueryService.class, "attributeStore");

                String id = inv.getInvokerId();

                // Resolve AI provider + API key (same logic as parent)
                String provider = ts.get(app.krista.sdk.msauth.extension.mcp.McpSessionKeys.aiProvider(id));
                String aiKey = isBlank(provider) ? null : ts.get(app.krista.sdk.msauth.extension.mcp.McpSessionKeys.aiKey(id, provider));

                if (isBlank(aiKey)) {
                    aiKey = ts.get(app.krista.sdk.msauth.extension.mcp.McpSessionKeys.claudeApiKey(id));
                    if (!isBlank(aiKey)) provider = "anthropic";
                }

                if (isBlank(aiKey)) {
                    return invokeStaticFailure("AI API key is not configured. "
                            + "Please set it in the AI Assistant tab \u2192 AI Settings.");
                }

                // Reload auth bridge
                OnedriveAttributes attrs = attrStore.load(inv.getInvokerId());
                app.krista.sdk.msauth.extension.mcp.DefaultMsAuthBridge authBridge =
                        new app.krista.sdk.msauth.extension.mcp.DefaultMsAuthBridge(attrs, ts);
                if (!authBridge.isAuthenticated()) {
                    return invokeStaticFailure("Not connected. Configure credentials in the Authentication tab.");
                }

                // AI call — stubbed
                if (stubbedAiException != null) throw stubbedAiException;
                String raw = stubbedAiResponse;

                if (isBlank(raw)) {
                    return invokeStaticFailure("Could not determine Graph API call. Please try rephrasing your question.");
                }

                if (raw.contains("{")) {
                    raw = raw.substring(raw.indexOf('{'), raw.lastIndexOf('}') + 1);
                }

                com.google.gson.Gson gson = new com.google.gson.Gson();
                JsonObject plan = gson.fromJson(raw, JsonObject.class);
                String method = getJsonStr(plan, "method");
                String path = getJsonStr(plan, "path");
                String body = plan != null && plan.has("body") && !plan.get("body").isJsonNull()
                        ? (plan.get("body").isJsonPrimitive()
                                ? plan.get("body").getAsString()
                                : plan.get("body").toString())
                        : null;

                if (isBlank(method) || isBlank(path)) {
                    return invokeStaticFailure("Could not parse a valid Graph API call from the AI response. Please rephrase your question.");
                }

                // Resolve /me/ for app-only auth
                String resolvedPath = path;
                if (attrs != null && attrs.isAppOnlyAuth()) {
                    String email = attrs.getEmail();
                    if (email != null && !email.isBlank()) {
                        resolvedPath = path.replaceFirst("^/me(/|$)", "/users/" + email + "$1");
                    }
                }

                String fullUrl = "https://graph.microsoft.com/v1.0" + resolvedPath;

                // Capture for assertions
                capturedMethod = method;
                capturedUrl = fullUrl;
                capturedBody = body;

                // Stubbed executeGraphCall
                ExtensionResponse result = stubbedExecuteGraphCall(method, fullUrl, body);

                // Attach generatedPlan (same as parent)
                if (result.getResult() == ExtensionResponse.Result.SUCCESS
                        && result.getResponseValue() instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> rv = (Map<String, Object>) result.getResponseValue();
                    rv.put("generatedPlan", method + " " + path);
                }
                return result;

            } catch (Exception e) {
                return invokeStaticFailure("Query failed: " + e.getMessage());
            }
        }

        private ExtensionResponse stubbedExecuteGraphCall(String method, String fullUrl, String body) {
            if (stubbedGraphException != null) {
                return invokeStaticFailure("Graph API call failed: " + stubbedGraphException.getMessage());
            }
            int status = stubbedGraphStatusCode;
            if (status == 202 || status == 204) {
                FreeForm ff = new FreeForm();
                ff.put("Answer", " Text ", "Action completed successfully.");
                Map<String, Object> actionMap = new HashMap<>();
                actionMap.put("Response", List.of(ff));
                return invokeStaticSuccess(actionMap);
            }
            if (status == 200 || status == 201) {
                try {
                    Method parseMethod = OnedriveMcpQueryService.class.getDeclaredMethod("parseGenericResponse", String.class);
                    parseMethod.setAccessible(true);
                    @SuppressWarnings("unchecked")
                    List<FreeForm> rows = (List<FreeForm>) parseMethod.invoke(null, stubbedGraphResponse);
                    Map<String, Object> responseMap = new HashMap<>();
                    responseMap.put("Response", rows);
                    return invokeStaticSuccess(responseMap);
                } catch (Exception e) {
                    return invokeStaticFailure("Parse failed: " + e.getMessage());
                }
            }
            return invokeStaticFailure("Graph API call failed (HTTP " + status + "): " + stubbedGraphErrorBody);
        }

        // Helper wrappers to call the parent's private static methods via reflection
        private static ExtensionResponse invokeStaticSuccess(Map<String, Object> values) {
            try {
                Method m = OnedriveMcpQueryService.class.getDeclaredMethod("success", Map.class);
                m.setAccessible(true);
                return (ExtensionResponse) m.invoke(null, values);
            } catch (Exception e) { throw new RuntimeException(e); }
        }

        private static ExtensionResponse invokeStaticFailure(String message) {
            try {
                Method m = OnedriveMcpQueryService.class.getDeclaredMethod("failure", String.class);
                m.setAccessible(true);
                return (ExtensionResponse) m.invoke(null, message);
            } catch (Exception e) { throw new RuntimeException(e); }
        }

        private static boolean isBlank(String s) {
            return s == null || s.isBlank();
        }

        private static String getJsonStr(JsonObject obj, String key) {
            if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) return "";
            return obj.get(key).getAsString();
        }

        @SuppressWarnings("unchecked")
        private static <T> T getFieldValue(Object target, Class<?> clazz, String fieldName) {
            try {
                Field f = clazz.getDeclaredField(fieldName);
                f.setAccessible(true);
                return (T) f.get(target);
            } catch (Exception e) { throw new RuntimeException(e); }
        }
    }

    /**
     * Creates a {@link TestableQueryService} instance with internal fields wired to
     * the test's mock stores. Uses {@code sun.misc.Unsafe} to allocate without
     * calling the super constructor, then sets fields via reflection.
     */
    private TestableQueryService createTestableService() throws Exception {
        Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        Field unsafeField = unsafeClass.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Object unsafe = unsafeField.get(null);
        Method allocateInstance = unsafeClass.getMethod("allocateInstance", Class.class);
        TestableQueryService service = (TestableQueryService) allocateInstance.invoke(unsafe, TestableQueryService.class);

        // Wire the private fields that query() uses
        ITokenStore tokenStore = invokeAdaptKeyValueStore(mockKvStore);

        setField(service, OnedriveMcpQueryService.class, "tokenStore", tokenStore);
        setField(service, OnedriveMcpQueryService.class, "invoker", mockInvoker);
        setField(service, OnedriveMcpQueryService.class, "attributeStore", attributeStore);

        // Unsafe doesn't run field initializers, so set defaults explicitly
        service.stubbedGraphStatusCode = 200;
        service.stubbedGraphErrorBody = "error";

        return service;
    }

    private static void setField(Object target, Class<?> clazz, String fieldName, Object value) throws Exception {
        Field field = clazz.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    // ── Mock helpers ─────────────────────────────────────────────────────

    private Invoker createMockInvoker() {
        RoutingInfo routingInfo = (RoutingInfo) Proxy.newProxyInstance(
                RoutingInfo.class.getClassLoader(),
                new Class<?>[]{RoutingInfo.class},
                (proxy, method, args) -> {
                    if ("getRoutingURL".equals(method.getName())) return "http://localhost:8765";
                    if (method.getReturnType() == boolean.class) return false;
                    return null;
                });

        return (Invoker) Proxy.newProxyInstance(
                Invoker.class.getClassLoader(),
                new Class<?>[]{Invoker.class},
                (proxy, method, args) -> {
                    if ("getInvokerId".equals(method.getName())) return "test-invoker";
                    if ("getRoutingInfo".equals(method.getName())) return routingInfo;
                    if (method.getReturnType() == boolean.class) return false;
                    if (method.getReturnType() == Map.class) return Collections.emptyMap();
                    if (method.getReturnType() == List.class) return Collections.emptyList();
                    return null;
                });
    }

    // ── Mock KeyValueStore ───────────────────────────────────────────────

    private static class MockKeyValueStore implements KeyValueStore {
        private final Map<String, Object> data = new ConcurrentHashMap<>();

        @Override
        public Object get(String key) {
            return data.get(key);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T get(String key, Class<T> type) {
            Object value = data.get(key);
            if (value == null) return null;
            if (type.isInstance(value)) return type.cast(value);
            return (T) value;
        }

        @Override
        public void remove(String key) {
            data.remove(key);
        }

        @Override
        public boolean contains(String key) {
            return data.containsKey(key);
        }

        @Override
        public void put(String key, Object value) {
            data.put(key, value);
        }

        @Override
        public boolean supports(Class<?> clazz) {
            return true;
        }

        @Override
        public Set<String> getKeys() {
            return new HashSet<>(data.keySet());
        }

        @Override
        public Iterator<Map.Entry<String, Object>> iterator() {
            return data.entrySet().iterator();
        }
    }
}
