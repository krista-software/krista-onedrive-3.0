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
import app.krista.extensions.essentials.cloudfiles.onedrive.OnedriveAttributes;
import app.krista.extensions.essentials.cloudfiles.onedrive.audit.OneDriveAuditService;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.connectors.GraphServiceClientProvider;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.connectors.GraphServiceClientProviderFactory;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.stores.OnedriveAttributeStore;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.util.Constants;
import app.krista.extensions.util.KeyValueStore;
import com.microsoft.graph.models.Subscription;
import com.microsoft.graph.requests.GraphServiceClient;
import com.microsoft.graph.requests.SubscriptionCollectionPage;
import com.microsoft.graph.requests.SubscriptionCollectionRequest;
import com.microsoft.graph.requests.SubscriptionCollectionRequestBuilder;
import okhttp3.Request;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the private {@code buildSubscriptionInfo()} method in
 * {@link OneDriveAuditResource}, exercised via reflection.
 *
 * <p>Requires {@code org.mockito:mockito-core} on the test classpath to mock the
 * Microsoft Graph SDK chain (GraphServiceClient, SubscriptionCollectionPage, etc.).
 * The Graph SDK chain stubs are isolated from network access through Mockito mocks;
 * all other dependencies use hand-written subclass stubs consistent with the rest
 * of the test suite.
 */
public class OneDriveAuditResourceBuildSubscriptionInfoTest {

    private static final String INVOKER_ID = "test-invoker-id";
    private static final String BASE_URL   = "http://test.appliance";
    /** URL that the method under test considers a "matching" subscription. */
    private static final String MATCH_URL  = BASE_URL + Constants.REST_ONEDRIVE_MAIL_NOTIFICATION;
    private static final String OTHER_URL  = "http://other.host/some-other-notification";

    private MockKeyValueStore kvs;
    private OneDriveAuditService auditService;

    @Before
    public void setUp() {
        kvs          = new MockKeyValueStore();
        auditService = new OneDriveAuditService(kvs);
    }

    // =========================================================================
    // Builder helpers
    // =========================================================================

    /**
     * Creates an {@link OneDriveAuditResource} wired to the given attribute store and
     * provider factory, using a Mockito-backed Invoker that returns INVOKER_ID / BASE_URL.
     */
    private OneDriveAuditResource resource(OnedriveAttributeStore store,
                                            GraphServiceClientProviderFactory factory) {
        Invoker invoker  = mock(Invoker.class);
        RoutingInfo info = mock(RoutingInfo.class);
        when(invoker.getInvokerId()).thenReturn(INVOKER_ID);
        when(invoker.getRoutingInfo()).thenReturn(info);
        when(info.getRoutingURL(anyString(), any())).thenReturn(BASE_URL);
        return new OneDriveAuditResource(auditService, store, kvs, factory, invoker);
    }

    /**
     * Reflectively invokes the private {@code buildSubscriptionInfo()} method and
     * unwraps any {@link InvocationTargetException} so that assertions see the
     * actual exception type thrown by the method.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> call(OneDriveAuditResource r) throws Exception {
        Method m = OneDriveAuditResource.class.getDeclaredMethod("buildSubscriptionInfo");
        m.setAccessible(true);
        try {
            return (Map<String, Object>) m.invoke(r);
        } catch (InvocationTargetException ite) {
            Throwable cause = ite.getCause();
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            if (cause instanceof Exception)        throw (Exception) cause;
            throw ite;
        }
    }

    // =========================================================================
    // Stub / mock factories
    // =========================================================================

    private static OnedriveAttributeStore stubStore(OnedriveAttributes attrs) {
        return new OnedriveAttributeStore(null, null) {
            @Override public OnedriveAttributes load(String id) { return attrs; }
        };
    }

    /** Factory stub that throws a RuntimeException from create() without touching the Graph SDK. */
    private static GraphServiceClientProviderFactory throwingFactory(RuntimeException ex) {
        return new GraphServiceClientProviderFactory(null, null, null, null) {
            @Override public GraphServiceClientProvider create(String id) { throw ex; }
        };
    }

    /**
     * Factory stub that drives create() to return a provider backed by a Mockito
     * GraphServiceClient whose subscriptions().buildRequest().get() returns firstPage.
     */
    @SuppressWarnings("unchecked")
    private GraphServiceClientProviderFactory factoryForPage(SubscriptionCollectionPage firstPage) {
        SubscriptionCollectionRequest       req    = mock(SubscriptionCollectionRequest.class);
        SubscriptionCollectionRequestBuilder bldr  = mock(SubscriptionCollectionRequestBuilder.class);
        GraphServiceClient<Request>          client = mock(GraphServiceClient.class);
        when(client.subscriptions()).thenReturn(bldr);
        when(bldr.buildRequest()).thenReturn(req);
        when(req.get()).thenReturn(firstPage);
        return new StubFactory(new StubProvider(client));
    }

    /**
     * Single-page stub: getCurrentPage() returns subs, getNextPage() returns null (end of list).
     */
    private SubscriptionCollectionPage leafPage(List<Subscription> subs) {
        SubscriptionCollectionPage page = mock(SubscriptionCollectionPage.class);
        when(page.getCurrentPage()).thenReturn(subs);
        when(page.getNextPage()).thenReturn(null);
        return page;
    }

    /**
     * Multi-page stub: getCurrentPage() returns subs; getNextPage().buildRequest().get()
     * returns the supplied next page.
     */
    private SubscriptionCollectionPage pageWithNext(List<Subscription> subs,
                                                     SubscriptionCollectionPage next) {
        SubscriptionCollectionPage page = mock(SubscriptionCollectionPage.class);
        when(page.getCurrentPage()).thenReturn(subs);

        SubscriptionCollectionRequest        nextReq  = mock(SubscriptionCollectionRequest.class);
        SubscriptionCollectionRequestBuilder nextBldr = mock(SubscriptionCollectionRequestBuilder.class);
        when(nextBldr.buildRequest()).thenReturn(nextReq);
        when(nextReq.get()).thenReturn(next);
        when(page.getNextPage()).thenReturn(nextBldr);
        return page;
    }

    /** Minimal OnedriveAttributes with Private auth to avoid public config lookup side effects. */
    private static OnedriveAttributes attrs(String email) {
        return new OnedriveAttributes(BASE_URL, "test-cid", "test-secret", email, "test-tid", "Private");
    }

    private static Subscription subscription(String id, String notifyUrl,
                                             OffsetDateTime expiry, String resource) {
        Subscription s       = new Subscription();
        s.id                 = id;
        s.notificationUrl    = notifyUrl;
        s.expirationDateTime = expiry;
        s.resource           = resource;
        return s;
    }

    // =========================================================================
    // 1–2. Guard clauses: no Graph API call when attrs or email is null
    // =========================================================================

    @Test
    public void whenAttrsNull_returnsNoneStatusWithNoAlert() throws Exception {
        Map<String, Object> result = call(resource(
                stubStore(null),
                throwingFactory(new RuntimeException("must not be reached"))));

        assertEquals("None", result.get("status"));
        assertEquals(false,  result.get("allowMailAlert"));
        assertEquals(2,      result.size());    // only these two keys are present
    }

    @Test
    public void whenEmailNull_returnsNoneStatusWithNoAlert() throws Exception {
        Map<String, Object> result = call(resource(
                stubStore(attrs(null)),
                throwingFactory(new RuntimeException("must not be reached"))));

        assertEquals("None", result.get("status"));
        assertEquals(false,  result.get("allowMailAlert"));
    }

    // =========================================================================
    // 3–5. Exception handling
    // =========================================================================

    /** MustAuthorizeException must propagate to the caller, not be swallowed. */
    @Test(expected = MustAuthorizeException.class)
    public void whenMustAuthorizeExceptionThrown_isRethrown() throws Exception {
        call(resource(
                stubStore(attrs("user@test.com")),
                throwingFactory(new MustAuthorizeException("please re-auth"))));
    }

    @Test
    public void whenGenericExceptionThrown_returnsUnknownStatusWithErrorMessage() throws Exception {
        Map<String, Object> result = call(resource(
                stubStore(attrs("user@test.com")),
                throwingFactory(new RuntimeException("graph exploded"))));

        assertEquals("Unknown",        result.get("status"));
        assertEquals(false,            result.get("allowMailAlert"));
        assertEquals("graph exploded", result.get("error"));
    }

    /** Null exception message must result in an empty string, not "null". */
    @Test
    public void whenExceptionMessageIsNull_errorFieldIsEmptyString() throws Exception {
        Map<String, Object> result = call(resource(
                stubStore(attrs("user@test.com")),
                throwingFactory(new RuntimeException((String) null))));

        assertEquals("Unknown", result.get("status"));
        assertEquals(false,     result.get("allowMailAlert"));
        assertEquals("",        result.get("error"));
    }

    // =========================================================================
    // 6–7. No matching subscription
    // =========================================================================

    @Test
    public void whenEmptySubscriptionPage_returnsNotActive() throws Exception {
        Map<String, Object> result = call(resource(
                stubStore(attrs("user@test.com")),
                factoryForPage(leafPage(Collections.emptyList()))));

        assertEquals("Not Active", result.get("status"));
        assertEquals(false,        result.get("allowMailAlert"));
    }

    @Test
    public void whenSubscriptionUrlDoesNotMatch_returnsNotActive() throws Exception {
        Subscription s = subscription("s1", OTHER_URL, null, "me/drives");

        Map<String, Object> result = call(resource(
                stubStore(attrs("user@test.com")),
                factoryForPage(leafPage(List.of(s)))));

        assertEquals("Not Active", result.get("status"));
        assertEquals(false,        result.get("allowMailAlert"));
    }

    // =========================================================================
    // 8–9. Matching subscription found
    // =========================================================================

    @Test
    public void whenMatchingSubscriptionFound_returnsActiveWithAllFields() throws Exception {
        OffsetDateTime expiry = OffsetDateTime.parse("2025-09-01T10:00:00Z");
        Subscription s = subscription("sub-abc", MATCH_URL, expiry, "me/mailboxSettings");

        Map<String, Object> result = call(resource(
                stubStore(attrs("user@test.com")),
                factoryForPage(leafPage(List.of(s)))));

        assertEquals("Active",                                    result.get("status"));
        assertEquals(true,                                        result.get("allowMailAlert"));
        assertEquals("sub-abc",                                   result.get("subscriptionId"));
        assertEquals(Long.valueOf(expiry.toEpochSecond() * 1000), result.get("expiresAt"));
        assertEquals("me/mailboxSettings",                        result.get("resource"));
    }

    /** Null expirationDateTime must map to a null expiresAt, not an NPE. */
    @Test
    public void whenMatchingSubscriptionHasNullExpiry_expiresAtIsNull() throws Exception {
        Subscription s = subscription("sub-xyz", MATCH_URL, null, "me/mailboxSettings");

        Map<String, Object> result = call(resource(
                stubStore(attrs("user@test.com")),
                factoryForPage(leafPage(List.of(s)))));

        assertEquals("Active", result.get("status"));
        assertEquals(true,     result.get("allowMailAlert"));
        assertNull(            result.get("expiresAt"));
    }

    // =========================================================================
    // 10–11. Pagination
    // =========================================================================

    /** Verify the while-loop advances to a subsequent page and matches there. */
    @Test
    public void whenMatchOnSecondPage_returnsActiveWithMatchDetails() throws Exception {
        Subscription nonMatch = subscription("s1", OTHER_URL, null, "me/drives");
        Subscription match    = subscription("s2", MATCH_URL, null, "me/mailboxSettings");

        SubscriptionCollectionPage second = leafPage(List.of(match));
        SubscriptionCollectionPage first  = pageWithNext(List.of(nonMatch), second);

        Map<String, Object> result = call(resource(
                stubStore(attrs("user@test.com")),
                factoryForPage(first)));

        assertEquals("Active", result.get("status"));
        assertEquals(true,     result.get("allowMailAlert"));
        assertEquals("s2",     result.get("subscriptionId"));
    }

    /** Exhausting all pages without a match should still return Not Active. */
    @Test
    public void whenMultiplePagesWithNoMatch_returnsNotActive() throws Exception {
        Subscription noMatch1 = subscription("s1", OTHER_URL,              null, "me/drives");
        Subscription noMatch2 = subscription("s2", "http://yet/another",   null, "me/drives");

        SubscriptionCollectionPage second = leafPage(List.of(noMatch2));
        SubscriptionCollectionPage first  = pageWithNext(List.of(noMatch1), second);

        Map<String, Object> result = call(resource(
                stubStore(attrs("user@test.com")),
                factoryForPage(first)));

        assertEquals("Not Active", result.get("status"));
        assertEquals(false,        result.get("allowMailAlert"));
    }

    // =========================================================================
    // Inner stubs
    // =========================================================================

    /**
     * GraphServiceClientProvider subclass that returns a caller-supplied client instead
     * of delegating to the real MsAuthGraphClientProvider (which requires live credentials).
     */
    private static class StubProvider extends GraphServiceClientProvider {
        private final GraphServiceClient<Request> client;

        StubProvider(GraphServiceClient<Request> client) {
            super(null, null, null, null);
            this.client = client;
        }

        @Override
        public GraphServiceClient<Request> getGraphServiceClientForAdmin() {
            return client;
        }
    }

    /**
     * GraphServiceClientProviderFactory subclass whose create(String) returns the
     * caller-supplied provider, bypassing the real attribute-loading logic.
     */
    private static class StubFactory extends GraphServiceClientProviderFactory {
        private final GraphServiceClientProvider provider;

        StubFactory(GraphServiceClientProvider provider) {
            super(null, null, null, null);
            this.provider = provider;
        }

        @Override
        public GraphServiceClientProvider create(String authContextId) {
            return provider;
        }
    }

    /** Minimal in-memory KeyValueStore, consistent with other test classes in this project. */
    private static class MockKeyValueStore implements KeyValueStore {
        private final Map<String, Object> data = new ConcurrentHashMap<>();

        @Override
        public Object get(String key) { return data.get(key); }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T get(String key, Class<T> type) {
            Object v = data.get(key);
            return (v != null && type.isInstance(v)) ? type.cast(v) : null;
        }

        @Override public void    put(String key, Object value) { data.put(key, value); }
        @Override public void    remove(String key)            { data.remove(key); }
        @Override public boolean contains(String key)          { return data.containsKey(key); }
        @Override public boolean supports(Class<?> clazz)      { return true; }
        @Override public Set<String> getKeys()                 { return new HashSet<>(data.keySet()); }

        @Override
        public Iterator<Map.Entry<String, Object>> iterator() {
            return data.entrySet().iterator();
        }
    }
}
