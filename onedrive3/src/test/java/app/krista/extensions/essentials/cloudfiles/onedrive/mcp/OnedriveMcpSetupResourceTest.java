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
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.junit.Before;
import org.junit.Test;

import javax.ws.rs.core.Response;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.Assert.*;

/**
 * Tests for {@link OnedriveMcpSetupResource}.
 *
 * <p>Covers all methods including constructor, interceptNlQuery,
 * convertToNlqResponse, extractFreeFormValue, adaptKeyValueStore,
 * getProductConfig, getJaxrsId, and static utility methods.</p>
 */
public class OnedriveMcpSetupResourceTest {

    private static final Gson GSON = new Gson();

    private MockKeyValueStore mockKvStore;
    private OnedriveMcpQueryService mockQueryService;
    private OnedriveMcpSetupResource resource;

    @Before
    public void setUp() throws Exception {
        mockKvStore = new MockKeyValueStore();
        Invoker mockInvoker = createMockInvoker();

        // Pre-populate store with attribute data so OnedriveAttributeStore.load() works
        OnedriveAttributeStore attributeStore = new OnedriveAttributeStore(mockInvoker, mockKvStore);
        OnedriveAttributes attrs = new OnedriveAttributes(
                "http://localhost:8765", "client-id", "client-secret",
                "user@example.com", "tenant-id", "Private");
        attributeStore.save(attrs, "test-invoker");

        // Create a mock query service using reflection to bypass its constructor dependencies
        mockQueryService = createMockQueryService();

        resource = new OnedriveMcpSetupResource(mockKvStore, mockInvoker, attributeStore, mockQueryService);
    }

    // ================================================================
    // getProductConfig
    // ================================================================

    @Test
    public void testGetProductConfig_returnsOnedriveMcpProductConfig() throws Exception {
        Method method = OnedriveMcpSetupResource.class.getDeclaredMethod("getProductConfig");
        method.setAccessible(true);
        Object config = method.invoke(resource);
        assertNotNull(config);
        assertTrue(config instanceof OnedriveMcpProductConfig);
    }

    // ================================================================
    // getJaxrsId
    // ================================================================

    @Test
    public void testGetJaxrsId_returnsOnedrive() throws Exception {
        Method method = OnedriveMcpSetupResource.class.getDeclaredMethod("getJaxrsId");
        method.setAccessible(true);
        String id = (String) method.invoke(resource);
        assertEquals("onedrive", id);
    }

    // ================================================================
    // interceptNlQuery — success path
    // ================================================================

    @Test
    public void testInterceptNlQuery_success_withMapResponse() throws Exception {
        // Build a successful ExtensionResponse with FreeForm rows
        FreeForm ff1 = new FreeForm();
        ff1.put("Name", "Text", "Document.docx");
        ff1.put("Link", "Text", "https://example.com/doc");

        FreeForm ff2 = new FreeForm();
        ff2.put("Name", "Text", "Sheet.xlsx");

        Map<String, Object> responseValue = new LinkedHashMap<>();
        responseValue.put("generatedPlan", "GET /me/drive/root/children");
        responseValue.put("Response", List.of(ff1, ff2));

        ExtensionResponse extResponse = new ExtensionResponse(
                ExtensionResponse.Result.SUCCESS, responseValue, null, null, null);

        setQueryServiceResponse(extResponse);

        Response response = resource.interceptNlQuery("list my files");

        assertEquals(200, response.getStatus());
        String json = (String) response.getEntity();
        Map<String, Object> result = GSON.fromJson(json, new TypeToken<Map<String, Object>>() {}.getType());
        assertEquals(2.0, result.get("total"));
        assertEquals(2.0, result.get("shown"));
        assertEquals("GET /me/drive/root/children", result.get("generatedQuery"));
        assertEquals(false, result.get("specific"));
        assertNotNull(result.get("columns"));
        assertNotNull(result.get("rows"));
    }

    @Test
    public void testInterceptNlQuery_success_emptyResponse() throws Exception {
        Map<String, Object> responseValue = new LinkedHashMap<>();
        responseValue.put("Response", List.of());

        ExtensionResponse extResponse = new ExtensionResponse(
                ExtensionResponse.Result.SUCCESS, responseValue, null, null, null);

        setQueryServiceResponse(extResponse);

        Response response = resource.interceptNlQuery("find nothing");

        assertEquals(200, response.getStatus());
        String json = (String) response.getEntity();
        Map<String, Object> result = GSON.fromJson(json, new TypeToken<Map<String, Object>>() {}.getType());
        assertEquals(0.0, result.get("total"));
        assertEquals(0.0, result.get("shown"));
    }

    // ================================================================
    // interceptNlQuery — failure path
    // ================================================================

    @Test
    public void testInterceptNlQuery_failure_withError() throws Exception {
        ExtensionResponse.Error error = new ExtensionResponse.Error(
                "Access denied", System.currentTimeMillis(),
                ExtensionResponse.Error.ExceptionType.LOGIC_ERROR, "");
        ExtensionResponse extResponse = new ExtensionResponse(
                ExtensionResponse.Result.FAILURE, null, error, null, null);

        setQueryServiceResponse(extResponse);

        Response response = resource.interceptNlQuery("bad query");

        assertEquals(400, response.getStatus());
        String json = (String) response.getEntity();
        assertTrue(json.contains("error"));
    }

    @Test
    public void testInterceptNlQuery_failure_withNullError() throws Exception {
        ExtensionResponse extResponse = new ExtensionResponse(
                ExtensionResponse.Result.FAILURE, null, null, null, null);

        setQueryServiceResponse(extResponse);

        Response response = resource.interceptNlQuery("bad query");

        assertEquals(400, response.getStatus());
        String json = (String) response.getEntity();
        assertTrue(json.contains("Query failed."));
    }

    // ================================================================
    // interceptNlQuery — exception path
    // ================================================================

    @Test
    public void testInterceptNlQuery_exception() throws Exception {
        setQueryServiceException(new RuntimeException("Connection timeout"));

        Response response = resource.interceptNlQuery("crash query");

        assertEquals(400, response.getStatus());
        String json = (String) response.getEntity();
        assertTrue(json.contains("Connection timeout"));
    }

    // ================================================================
    // convertToNlqResponse — branch coverage
    // ================================================================

    @Test
    public void testConvertToNlqResponse_nullResponseValue() throws Exception {
        // responseValue is null — should produce empty rows/columns
        ExtensionResponse extResponse = new ExtensionResponse(
                ExtensionResponse.Result.SUCCESS, null, null, null, null);

        setQueryServiceResponse(extResponse);

        Response response = resource.interceptNlQuery("test");

        assertEquals(200, response.getStatus());
        String json = (String) response.getEntity();
        Map<String, Object> result = GSON.fromJson(json, new TypeToken<Map<String, Object>>() {}.getType());
        assertEquals(0.0, result.get("total"));
        // generatedQuery defaults to the question when no plan in response
        assertEquals("test", result.get("generatedQuery"));
    }

    @Test
    public void testConvertToNlqResponse_responseListContainsNonFreeForm() throws Exception {
        // Response list has items that are not FreeForm — should be skipped
        Map<String, Object> responseValue = new LinkedHashMap<>();
        responseValue.put("Response", List.of("not-a-freeform", 42));

        ExtensionResponse extResponse = new ExtensionResponse(
                ExtensionResponse.Result.SUCCESS, responseValue, null, null, null);

        setQueryServiceResponse(extResponse);

        Response response = resource.interceptNlQuery("test");

        assertEquals(200, response.getStatus());
        String json = (String) response.getEntity();
        Map<String, Object> result = GSON.fromJson(json, new TypeToken<Map<String, Object>>() {}.getType());
        assertEquals(0.0, result.get("total"));
    }

    @Test
    public void testConvertToNlqResponse_responseKeyNotList() throws Exception {
        // "Response" value is not a List — should produce empty rows
        Map<String, Object> responseValue = new LinkedHashMap<>();
        responseValue.put("Response", "not-a-list");

        ExtensionResponse extResponse = new ExtensionResponse(
                ExtensionResponse.Result.SUCCESS, responseValue, null, null, null);

        setQueryServiceResponse(extResponse);

        Response response = resource.interceptNlQuery("test");

        assertEquals(200, response.getStatus());
        String json = (String) response.getEntity();
        Map<String, Object> result = GSON.fromJson(json, new TypeToken<Map<String, Object>>() {}.getType());
        assertEquals(0.0, result.get("total"));
    }

    @Test
    public void testConvertToNlqResponse_nullGeneratedPlan() throws Exception {
        // No "generatedPlan" key — defaults to original question
        Map<String, Object> responseValue = new LinkedHashMap<>();
        responseValue.put("Response", List.of());

        ExtensionResponse extResponse = new ExtensionResponse(
                ExtensionResponse.Result.SUCCESS, responseValue, null, null, null);

        setQueryServiceResponse(extResponse);

        Response response = resource.interceptNlQuery("my question");

        assertEquals(200, response.getStatus());
        String json = (String) response.getEntity();
        Map<String, Object> result = GSON.fromJson(json, new TypeToken<Map<String, Object>>() {}.getType());
        assertEquals("my question", result.get("generatedQuery"));
    }

    // ================================================================
    // convertToNlqResponse — column type detection
    // ================================================================

    @Test
    public void testConvertToNlqResponse_linkColumnType() throws Exception {
        FreeForm ff = new FreeForm();
        ff.put("Link", "Text", "https://example.com");
        ff.put("Url", "Text", "https://example.com/file");
        ff.put("Name", "Text", "file.txt");

        Map<String, Object> responseValue = new LinkedHashMap<>();
        responseValue.put("Response", List.of(ff));

        ExtensionResponse extResponse = new ExtensionResponse(
                ExtensionResponse.Result.SUCCESS, responseValue, null, null, null);

        setQueryServiceResponse(extResponse);

        Response response = resource.interceptNlQuery("test");

        assertEquals(200, response.getStatus());
        String json = (String) response.getEntity();
        Map<String, Object> result = GSON.fromJson(json, new TypeToken<Map<String, Object>>() {}.getType());

        @SuppressWarnings("unchecked")
        List<Map<String, String>> columns = (List<Map<String, String>>) result.get("columns");
        // Verify link/url columns get "link" type, others get "text"
        for (Map<String, String> col : columns) {
            String key = col.get("key");
            if ("link".equals(key) || "url".equals(key)) {
                assertEquals("link", col.get("type"));
            } else {
                assertEquals("text", col.get("type"));
            }
        }
    }

    // ================================================================
    // extractFreeFormValue — all branches
    // ================================================================

    @Test
    public void testExtractFreeFormValue_listWithTwoElements() throws Exception {
        FreeForm ff = new FreeForm();
        ff.put("Key1", "Text", "hello");

        String result = invokeExtractFreeFormValue(ff, "Key1");
        assertEquals("hello", result);
    }

    @Test
    public void testExtractFreeFormValue_listWithNullSecondElement() throws Exception {
        FreeForm ff = new FreeForm();
        List<Object> valList = new ArrayList<>();
        valList.add("Text");
        valList.add(null);
        ff.put("Key1", "List", valList);

        String result = invokeExtractFreeFormValue(ff, "Key1");
        assertNull(result);
    }

    @Test
    public void testExtractFreeFormValue_listWithEmptySecondElement() throws Exception {
        FreeForm ff = new FreeForm();
        List<Object> valList = new ArrayList<>();
        valList.add("Text");
        valList.add("");
        ff.put("Key1", "List", valList);

        String result = invokeExtractFreeFormValue(ff, "Key1");
        assertNull(result);
    }

    @Test
    public void testExtractFreeFormValue_listWithOneElement() throws Exception {
        FreeForm ff = new FreeForm();
        List<Object> valList = new ArrayList<>();
        valList.add("OnlyOne");
        ff.put("Key1", "List", valList);

        String result = invokeExtractFreeFormValue(ff, "Key1");
        assertNull(result);
    }

    @Test
    public void testExtractFreeFormValue_nonListValue() throws Exception {
        FreeForm ff = new FreeForm();
        ff.put("Key1", "String", "directValue");

        String result = invokeExtractFreeFormValue(ff, "Key1");
        assertEquals("directValue", result);
    }

    @Test
    public void testExtractFreeFormValue_nonListEmptyString() throws Exception {
        FreeForm ff = new FreeForm();
        ff.put("Key1", "String", "");

        String result = invokeExtractFreeFormValue(ff, "Key1");
        assertNull(result);
    }

    @Test
    public void testExtractFreeFormValue_nullValue() throws Exception {
        FreeForm ff = new FreeForm();
        ff.put("Key1", "String", null);

        String result = invokeExtractFreeFormValue(ff, "Key1");
        assertNull(result);
    }

    @Test
    public void testExtractFreeFormValue_keyNotPresent() throws Exception {
        FreeForm ff = new FreeForm();
        String result = invokeExtractFreeFormValue(ff, "MissingKey");
        assertNull(result);
    }

    @Test
    public void testExtractFreeFormValue_listWithNonEmptySecondElement() throws Exception {
        // Explicitly tests the List path where get(1) is non-empty (returns value)
        FreeForm ff = new FreeForm();
        List<Object> valList = new ArrayList<>();
        valList.add("Text");
        valList.add("actualValue");
        ff.put("Key1", "List", valList);

        String result = invokeExtractFreeFormValue(ff, "Key1");
        assertEquals("actualValue", result);
    }

    @Test
    public void testExtractFreeFormValue_listWithEmptyStringSecondElement() throws Exception {
        // Explicitly tests the List path where String.valueOf(get(1)).isEmpty() is true
        FreeForm ff = new FreeForm();
        List<String> valList = new ArrayList<>();
        valList.add("Text");
        valList.add("");
        ff.put("Key1", "List", valList);

        String result = invokeExtractFreeFormValue(ff, "Key1");
        assertNull(result);
    }

    // ================================================================
    // adaptKeyValueStore
    // ================================================================

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

    // ================================================================
    // toColumnKey — static utility
    // ================================================================

    @Test
    public void testToColumnKey_pascalToCamel() throws Exception {
        assertEquals("name", invokeToColumnKey("Name"));
    }

    @Test
    public void testToColumnKey_multiWord() throws Exception {
        assertEquals("lastModified", invokeToColumnKey("LastModified"));
    }

    @Test
    public void testToColumnKey_emptyString() throws Exception {
        assertEquals("", invokeToColumnKey(""));
    }

    @Test
    public void testToColumnKey_null() throws Exception {
        assertNull(invokeToColumnKey(null));
    }

    @Test
    public void testToColumnKey_alreadyLowercase() throws Exception {
        assertEquals("name", invokeToColumnKey("name"));
    }

    @Test
    public void testToColumnKey_singleChar() throws Exception {
        assertEquals("a", invokeToColumnKey("A"));
    }

    // ================================================================
    // toColumnLabel — static utility
    // ================================================================

    @Test
    public void testToColumnLabel_camelToLabel() throws Exception {
        assertEquals("Last Modified", invokeToColumnLabel("lastModified"));
    }

    @Test
    public void testToColumnLabel_multiWord() throws Exception {
        assertEquals("Modified By", invokeToColumnLabel("modifiedBy"));
    }

    @Test
    public void testToColumnLabel_emptyString() throws Exception {
        assertEquals("", invokeToColumnLabel(""));
    }

    @Test
    public void testToColumnLabel_null() throws Exception {
        assertNull(invokeToColumnLabel(null));
    }

    @Test
    public void testToColumnLabel_singleWord() throws Exception {
        assertEquals("Name", invokeToColumnLabel("name"));
    }

    @Test
    public void testToColumnLabel_singleChar() throws Exception {
        assertEquals("A", invokeToColumnLabel("a"));
    }

    // ================================================================
    // isLinkColumn — static utility
    // ================================================================

    @Test
    public void testIsLinkColumn_link() throws Exception {
        assertTrue(invokeIsLinkColumn("link"));
    }

    @Test
    public void testIsLinkColumn_url() throws Exception {
        assertTrue(invokeIsLinkColumn("url"));
    }

    @Test
    public void testIsLinkColumn_name() throws Exception {
        assertFalse(invokeIsLinkColumn("name"));
    }

    @Test
    public void testIsLinkColumn_other() throws Exception {
        assertFalse(invokeIsLinkColumn("other"));
    }

    @Test
    public void testIsLinkColumn_nullKey() throws Exception {
        assertFalse(invokeIsLinkColumn(null));
    }

    @Test
    public void testIsLinkColumn_empty() throws Exception {
        assertFalse(invokeIsLinkColumn(""));
    }

    // ================================================================
    // convertToNlqResponse — FreeForm value with null entries filtered
    // ================================================================

    @Test
    public void testConvertToNlqResponse_freeFormWithNullValuesSkipped() throws Exception {
        FreeForm ff = new FreeForm();
        ff.put("Name", "Text", "file.txt");
        // Add a key whose extracted value will be null (empty string value)
        List<Object> emptyVal = new ArrayList<>();
        emptyVal.add("Text");
        emptyVal.add("");
        ff.put("EmptyField", "List", emptyVal);

        Map<String, Object> responseValue = new LinkedHashMap<>();
        responseValue.put("Response", List.of(ff));

        ExtensionResponse extResponse = new ExtensionResponse(
                ExtensionResponse.Result.SUCCESS, responseValue, null, null, null);

        setQueryServiceResponse(extResponse);

        Response response = resource.interceptNlQuery("test");

        assertEquals(200, response.getStatus());
        String json = (String) response.getEntity();
        Map<String, Object> result = GSON.fromJson(json, new TypeToken<Map<String, Object>>() {}.getType());

        // Should have 1 row with only "name" column (emptyField filtered out)
        @SuppressWarnings("unchecked")
        List<Map<String, String>> columns = (List<Map<String, String>>) result.get("columns");
        boolean hasEmptyField = columns.stream().anyMatch(c -> "emptyField".equals(c.get("key")));
        assertFalse("Null-valued columns should not appear", hasEmptyField);
    }

    // ================================================================
    // Constructor — verifies resource can be instantiated
    // ================================================================

    @Test
    public void testConstructor_setsQueryService() throws Exception {
        Field qsField = OnedriveMcpSetupResource.class.getDeclaredField("queryService");
        qsField.setAccessible(true);
        assertNotNull(qsField.get(resource));
        assertSame(mockQueryService, qsField.get(resource));
    }

    // ================================================================
    // Reflection helpers
    // ================================================================

    private static String invokeToColumnKey(String input) throws Exception {
        Method method = OnedriveMcpSetupResource.class.getDeclaredMethod("toColumnKey", String.class);
        method.setAccessible(true);
        return (String) method.invoke(null, input);
    }

    private static String invokeToColumnLabel(String input) throws Exception {
        Method method = OnedriveMcpSetupResource.class.getDeclaredMethod("toColumnLabel", String.class);
        method.setAccessible(true);
        return (String) method.invoke(null, input);
    }

    private static boolean invokeIsLinkColumn(String input) throws Exception {
        Method method = OnedriveMcpSetupResource.class.getDeclaredMethod("isLinkColumn", String.class);
        method.setAccessible(true);
        return (boolean) method.invoke(null, input);
    }

    private static String invokeExtractFreeFormValue(FreeForm ff, Object key) throws Exception {
        Method method = OnedriveMcpSetupResource.class.getDeclaredMethod("extractFreeFormValue", FreeForm.class, Object.class);
        method.setAccessible(true);
        return (String) method.invoke(null, ff, key);
    }

    private static ITokenStore invokeAdaptKeyValueStore(KeyValueStore kvStore) throws Exception {
        Method method = OnedriveMcpSetupResource.class.getDeclaredMethod("adaptKeyValueStore", KeyValueStore.class);
        method.setAccessible(true);
        return (ITokenStore) method.invoke(null, kvStore);
    }

    // ================================================================
    // Mock / stub helpers
    // ================================================================

    /**
     * Creates a mock OnedriveMcpQueryService that can have its query() response set.
     * Uses subclass mock maker (configured in mockito-extensions) via direct subclass.
     */
    private OnedriveMcpQueryService createMockQueryService() throws Exception {
        // Use Objenesis-style instantiation: allocate without calling constructor
        // Mockito's ByteBuddy maker is configured — use sun.misc.Unsafe to allocate
        try {
            Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
            Field unsafeField = unsafeClass.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            Object unsafe = unsafeField.get(null);
            Method allocateInstance = unsafeClass.getMethod("allocateInstance", Class.class);
            return (OnedriveMcpQueryService) allocateInstance.invoke(unsafe, StubQueryService.class);
        } catch (Exception e) {
            // Fallback: try to create via Mockito if available
            throw new RuntimeException("Cannot create mock OnedriveMcpQueryService", e);
        }
    }

    /**
     * Sets the response the mock query service will return from query().
     */
    private void setQueryServiceResponse(ExtensionResponse response) throws Exception {
        if (mockQueryService instanceof StubQueryService) {
            ((StubQueryService) mockQueryService).stubbedResponse = response;
            ((StubQueryService) mockQueryService).stubbedException = null;
        } else {
            Field f = mockQueryService.getClass().getDeclaredField("stubbedResponse");
            f.setAccessible(true);
            f.set(mockQueryService, response);
        }
    }

    /**
     * Sets the exception the mock query service will throw from query().
     */
    private void setQueryServiceException(RuntimeException exception) throws Exception {
        if (mockQueryService instanceof StubQueryService) {
            ((StubQueryService) mockQueryService).stubbedException = exception;
            ((StubQueryService) mockQueryService).stubbedResponse = null;
        } else {
            Field f = mockQueryService.getClass().getDeclaredField("stubbedException");
            f.setAccessible(true);
            f.set(mockQueryService, exception);
        }
    }

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

    // ================================================================
    // Stub query service (extends real class to override query())
    // ================================================================

    /**
     * A stub subclass of OnedriveMcpQueryService that overrides query()
     * to return a preconfigured response or throw an exception.
     */
    static class StubQueryService extends OnedriveMcpQueryService {
        ExtensionResponse stubbedResponse;
        RuntimeException stubbedException;

        // Constructor won't be called (allocated via Unsafe)
        StubQueryService() {
            super(null, null, null);
        }

        @Override
        public ExtensionResponse query(String question) {
            if (stubbedException != null) {
                throw stubbedException;
            }
            return stubbedResponse;
        }
    }

    // ================================================================
    // Mock KeyValueStore
    // ================================================================

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
