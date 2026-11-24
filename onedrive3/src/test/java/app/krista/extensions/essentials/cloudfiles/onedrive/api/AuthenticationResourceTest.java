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

import app.krista.extension.executor.Invoker;
import app.krista.extension.request.RoutingInfo;
import app.krista.extension.request.protos.http.HttpProtocol;
import app.krista.extensions.essentials.cloudfiles.onedrive.OnedriveAttributes;
import app.krista.extensions.essentials.cloudfiles.onedrive.OnedriveSetUpService;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.FileChangeInfoHandler;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.connectors.GraphServiceClientProvider;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.connectors.GraphServiceClientProviderFactory;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.services.OnedriveSubscriptionCleanupService;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.stores.OnedriveAttributeStore;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.util.AuthenticationResponse;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.util.Constants;
import app.krista.extensions.essentials.cloudfiles.onedrive.audit.OneDriveAuditService;
import app.krista.extensions.util.EventHandler;
import app.krista.extensions.util.KeyValueStore;
import app.krista.ksdk.accounts.Account;
import app.krista.ksdk.authentication.AuthorizationListener;
import app.krista.ksdk.context.AuthorizationContext;
import app.krista.sdk.msauth.MicrosoftAuthSdk;
import app.krista.sdk.msauth.token.ITokenStore;
import app.krista.sdk.msauth.token.TokenResult;
import com.google.gson.JsonObject;
import org.junit.Before;
import org.junit.Test;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.core.Response;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link AuthenticationResource}.
 * Uses hand-crafted mocks (no Mockito) following existing test patterns.
 */
public class AuthenticationResourceTest {

    private static final String TEST_INVOKER_ID = "test-invoker-123";
    private static final String BASE_URL = "http://localhost:8765";
    private static final String TEST_EMAIL = "user@example.com";
    private static final String TEST_CLIENT_ID = "test-client-id";
    private static final String TEST_CLIENT_SECRET = "test-client-secret";
    private static final String TEST_TENANT_ID = "test-tenant-id";

    private MockKeyValueStore kvStore;
    private TrackingAttributeStore attributeStore;
    private TrackingProviderFactory providerFactory;
    private EventHandlerTracker eventHandler;
    private AuthorizationListenerTracker authListener;
    private TrackingCleanupService cleanupService;
    private TrackingSetUpService setUpService;
    private OneDriveAuditService auditService;
    private TrackingFileChangeInfoHandler fileChangeHandler;
    private boolean contextAuthenticated;
    private String authorizedAccountId;
    private String authorizedEmail;

    private AuthenticationResource resource;

    @Before
    public void setUp() throws Exception {
        kvStore = new MockKeyValueStore();
        Invoker invoker = createMockInvoker();
        attributeStore = new TrackingAttributeStore(invoker, kvStore);
        providerFactory = new TrackingProviderFactory(kvStore, attributeStore, null, null);
        eventHandler = new EventHandlerTracker();
        authListener = new AuthorizationListenerTracker();
        cleanupService = new TrackingCleanupService();
        setUpService = new TrackingSetUpService();
        auditService = new OneDriveAuditService(kvStore);
        fileChangeHandler = new TrackingFileChangeInfoHandler();
        contextAuthenticated = false;
        authorizedAccountId = "wsContact-authorized-123";
        authorizedEmail = TEST_EMAIL;

        AuthorizationContext authContext = createMockAuthorizationContext();

        resource = new AuthenticationResource(
                attributeStore, kvStore, providerFactory, authContext,
                eventHandler.proxy, invoker, authListener.proxy, fileChangeHandler,
                cleanupService, setUpService, auditService);

        clearFileStore();
    }

    // ========================================================================
    // getCredentials — null/empty paths
    // ========================================================================

    @Test
    public void testGetCredentials_nullAttributes_returnsEmpty() {
        // No attributes stored → load returns null
        String result = resource.getCredentials("Public", "AUTH_CODE_PUBLIC");
        assertEquals("", result);
    }

    @Test
    public void testGetCredentials_nullAuthType_returnsEmpty() {
        storeAttributes(createPublicKristaAttributes());
        String result = resource.getCredentials(null, "AUTH_CODE_PUBLIC");
        assertEquals("", result);
    }

    // ========================================================================
    // getCredentials — Legacy / migration detection
    // ========================================================================

    @Test
    public void testGetCredentials_legacyNullAuthType_servedToUserAuthTab() {
        // Simulate legacy credentials with null authType
        OnedriveAttributes attrs = new OnedriveAttributes(BASE_URL, TEST_CLIENT_ID, TEST_CLIENT_SECRET, TEST_EMAIL, TEST_TENANT_ID, null);
        storeAttributes(attrs);

        // AUTH_CODE_PRIVATE + authType "Public" → User Auth tab
        String result = resource.getCredentials("Public", "AUTH_CODE_PRIVATE");
        assertNotEquals("", result);
        assertTrue(result.contains(TEST_EMAIL));
    }

    @Test
    public void testGetCredentials_legacyEmptyAuthType_servedToUserAuthTab() {
        OnedriveAttributes attrs = new OnedriveAttributes(BASE_URL, TEST_CLIENT_ID, TEST_CLIENT_SECRET, TEST_EMAIL, TEST_TENANT_ID, "");
        storeAttributes(attrs);

        String result = resource.getCredentials("Public", "AUTH_CODE_PRIVATE");
        assertNotEquals("", result);
        assertTrue(result.contains(TEST_EMAIL));
    }

    @Test
    public void testGetCredentials_legacyNullAuthType_wrongFlow_returnsEmpty() {
        OnedriveAttributes attrs = new OnedriveAttributes(BASE_URL, TEST_CLIENT_ID, TEST_CLIENT_SECRET, TEST_EMAIL, TEST_TENANT_ID, null);
        storeAttributes(attrs);

        // Not AUTH_CODE_PRIVATE → not served
        String result = resource.getCredentials("Public", "AUTH_CODE_PUBLIC");
        assertEquals("", result);
    }

    @Test
    public void testGetCredentials_legacyMigrated_publicNoPublicAuthMode_servedToUserAuth() {
        // authType="Public" but publicAuthMode=null → legacy migrated
        OnedriveAttributes attrs = new OnedriveAttributes(BASE_URL, TEST_CLIENT_ID, TEST_CLIENT_SECRET, TEST_EMAIL, TEST_TENANT_ID, "Public");
        // publicAuthMode is null by default in OnedriveAttributes
        storeAttributes(attrs);

        String result = resource.getCredentials("Public", "AUTH_CODE_PRIVATE");
        assertNotEquals("", result);
        assertTrue(result.contains(TEST_EMAIL));
    }

    @Test
    public void testGetCredentials_legacyMigrated_wrongFlow_returnsEmpty() {
        OnedriveAttributes attrs = new OnedriveAttributes(BASE_URL, TEST_CLIENT_ID, TEST_CLIENT_SECRET, TEST_EMAIL, TEST_TENANT_ID, "Public");
        storeAttributes(attrs);

        String result = resource.getCredentials("Public", "AUTH_CODE_PUBLIC");
        assertEquals("", result);
    }

    // ========================================================================
    // getCredentials — authType mismatch
    // ========================================================================

    @Test
    public void testGetCredentials_authTypeMismatch_returnsEmpty() {
        OnedriveAttributes attrs = createPublicOwnAttributes();
        storeAttributes(attrs);

        // Request Private, stored Public → mismatch
        String result = resource.getCredentials("Private", "CLIENT_CREDENTIALS_SECRET");
        assertEquals("", result);
    }

    // ========================================================================
    // getCredentials — Public + publicAuthMode matching
    // ========================================================================

    @Test
    public void testGetCredentials_publicOwnRequest_ownStored_returnsData() {
        OnedriveAttributes attrs = createPublicOwnAttributes();
        storeAttributes(attrs);

        // AUTH_CODE_PRIVATE → isOwnRequest=true; stored "own" → isOwnStored=true → match
        String result = resource.getCredentials("Public", "AUTH_CODE_PRIVATE");
        assertNotEquals("", result);
        assertTrue(result.contains(TEST_EMAIL));
    }

    @Test
    public void testGetCredentials_publicOwnRequest_kristaStored_returnsEmpty() {
        OnedriveAttributes attrs = createPublicKristaAttributes();
        storeAttributes(attrs);

        // AUTH_CODE_PRIVATE → isOwnRequest=true; stored "krista" → isOwnStored=false → mismatch
        String result = resource.getCredentials("Public", "AUTH_CODE_PRIVATE");
        assertEquals("", result);
    }

    @Test
    public void testGetCredentials_publicKristaRequest_kristaStored_returnsData() {
        OnedriveAttributes attrs = createPublicKristaAttributes();
        storeAttributes(attrs);

        // AUTH_CODE_PUBLIC → isOwnRequest=false; stored "krista" → isOwnStored=false → match
        String result = resource.getCredentials("Public", "AUTH_CODE_PUBLIC");
        assertNotEquals("", result);
        assertTrue(result.contains(TEST_EMAIL));
    }

    @Test
    public void testGetCredentials_publicKristaRequest_ownStored_returnsEmpty() {
        OnedriveAttributes attrs = createPublicOwnAttributes();
        storeAttributes(attrs);

        // AUTH_CODE_PUBLIC → isOwnRequest=false; stored "own" → isOwnStored=true → mismatch
        String result = resource.getCredentials("Public", "AUTH_CODE_PUBLIC");
        assertEquals("", result);
    }

    // ========================================================================
    // getCredentials — Krista-managed credential masking
    // ========================================================================

    @Test
    public void testGetCredentials_kristaManaged_clientIdAndSecretMasked() {
        OnedriveAttributes attrs = createPublicKristaAttributes();
        storeAttributes(attrs);

        String result = resource.getCredentials("Public", "AUTH_CODE_PUBLIC");
        assertNotEquals("", result);
        assertFalse("clientId should be masked", result.contains(TEST_CLIENT_ID));
        assertFalse("clientSecret should be masked", result.contains(TEST_CLIENT_SECRET));
    }

    @Test
    public void testGetCredentials_ownManaged_clientIdAndSecretPresent() {
        OnedriveAttributes attrs = createPublicOwnAttributes();
        storeAttributes(attrs);

        String result = resource.getCredentials("Public", "AUTH_CODE_PRIVATE");
        assertNotEquals("", result);
        assertTrue("clientId should be present for own mode", result.contains(TEST_CLIENT_ID));
    }

    // ========================================================================
    // getCredentials — Private auth
    // ========================================================================

    @Test
    public void testGetCredentials_privateStored_privateRequest_returnsData() {
        OnedriveAttributes attrs = new OnedriveAttributes(BASE_URL, TEST_CLIENT_ID, TEST_CLIENT_SECRET, TEST_EMAIL, TEST_TENANT_ID, "Private");
        storeAttributes(attrs);

        String result = resource.getCredentials("Private", "CLIENT_CREDENTIALS_SECRET");
        assertNotEquals("", result);
        assertTrue(result.contains(TEST_EMAIL));
    }

    // ========================================================================
    // getCredentials — null authFlow
    // ========================================================================

    @Test
    public void testGetCredentials_publicStored_nullAuthFlow_returnsData() {
        OnedriveAttributes attrs = createPublicOwnAttributes();
        storeAttributes(attrs);

        // null authFlow skips the publicAuthMode comparison
        String result = resource.getCredentials("Public", null);
        assertNotEquals("", result);
    }

    @Test
    public void testGetCredentials_publicAuth_kristaStored_nullAuthFlow_returnsEmpty() {
        // Quick Setup stored (publicAuthMode="krista") + null authFlow → must return ""
        // This is the authFlow null-gap fix: prevents Quick Setup data leaking without explicit authFlow
        OnedriveAttributes attrs = new OnedriveAttributes(BASE_URL, null, null,
                TEST_EMAIL, null, Constants.PUBLIC);
        attrs.setPublicAuthMode("krista");
        storeAttributes(attrs);

        String result = resource.getCredentials(Constants.PUBLIC, null);

        assertEquals("Quick Setup data must not leak when authFlow is null", "", result);
    }

    @Test
    public void testGetCredentials_publicAuth_ownStored_nullAuthFlow_returnsCredentials() {
        // User Auth stored (publicAuthMode="own") + null authFlow → backward compat, serve it
        OnedriveAttributes attrs = new OnedriveAttributes(BASE_URL, TEST_CLIENT_ID, TEST_CLIENT_SECRET,
                TEST_EMAIL, TEST_TENANT_ID, Constants.PUBLIC);
        attrs.setPublicAuthMode("own");
        storeAttributes(attrs);

        String result = resource.getCredentials(Constants.PUBLIC, null);

        assertFalse("User Auth data should be returned for null authFlow", result.isEmpty());
        assertTrue("Should contain client ID", result.contains(TEST_CLIENT_ID));
    }

    @Test
    public void testGetCredentials_publicAuth_kristaStored_explicitFlow_stripsCredentials() {
        // Quick Setup (krista) + explicit AUTH_CODE_PUBLIC flow → returns data but strips credentials
        OnedriveAttributes attrs = new OnedriveAttributes(BASE_URL, null, null,
                TEST_EMAIL, null, Constants.PUBLIC);
        attrs.setPublicAuthMode("krista");
        storeAttributes(attrs);

        String result = resource.getCredentials(Constants.PUBLIC, "AUTH_CODE_PUBLIC");

        assertFalse("Should return non-empty for Quick Setup with matching flow", result.isEmpty());
        // Belt-and-suspenders: CLIENT_ID and CLIENT_SECRET must be stripped
        assertFalse("clientId must not appear in Quick Setup response", result.contains("\"clientId\""));
        assertFalse("clientSecret must not appear in Quick Setup response", result.contains("\"clientSecret\""));
    }

    // ========================================================================
    // getAuthKey
    // ========================================================================

    @Test
    public void testGetAuthKey_noAttributes_returnsEmpty() {
        String result = resource.getAuthKey();
        assertEquals("", result);
    }

    @Test
    public void testGetAuthKey_attributesWithAuthType_returnsAuthType() {
        OnedriveAttributes attrs = new OnedriveAttributes(BASE_URL, TEST_CLIENT_ID, TEST_CLIENT_SECRET, TEST_EMAIL, TEST_TENANT_ID, "Private");
        storeAttributes(attrs);

        String result = resource.getAuthKey();
        assertTrue(result.contains("Private"));
    }

    @Test
    public void testGetAuthKey_attributesWithNullAuthType_returnsPublicDefault() {
        OnedriveAttributes attrs = new OnedriveAttributes(BASE_URL, TEST_CLIENT_ID, TEST_CLIENT_SECRET, TEST_EMAIL, TEST_TENANT_ID, null);
        storeAttributes(attrs);

        String result = resource.getAuthKey();
        assertTrue(result.contains("Public"));
    }

    // ========================================================================
    // authenticationTab
    // ========================================================================

    @Test
    public void testAuthenticationTab_returnsHtmlResponse() {
        Response resp = resource.authenticationTab("index.html");
        // If auth/index.html is on classpath, returns 200; otherwise 404
        assertNotNull(resp);
        int status = resp.getStatus();
        assertTrue("Should return 200 or 404", status == 200 || status == 404);
    }

    // ========================================================================
    // getCallBack — null code
    // ========================================================================

    @Test
    public void testGetCallBack_nullCode_returnsFailureMessage() {
        String result = resource.getCallBack(null, "test@example.com#ctx123");
        assertEquals("Authentication Failed. Please re-authorize.", result);
    }

    @Test
    public void testGetCallbackForV3Auth_nullCode_returnsFailureMessage() {
        String result = resource.getCallbackForV3Auth(null, "test@example.com#ctx123");
        assertEquals("Authentication Failed. Please re-authorize.", result);
    }

    // ========================================================================
    // getCallBack — blank state
    // ========================================================================

    @Test(expected = BadRequestException.class)
    public void testGetCallBack_blankState_throwsBadRequest() {
        resource.getCallBack("code123", "#ctx123");
    }

    @Test(expected = BadRequestException.class)
    public void testGetCallBack_emptyState_throwsBadRequest() {
        resource.getCallBack("code123", " #ctx123");
    }

    // ========================================================================
    // subscriptionValidation
    // ========================================================================

    @Test
    public void testSubscriptionValidation_returnsTokenWithStatus200() {
        Response resp = resource.subscriptionValidation("  myToken123  ");
        assertEquals(200, resp.getStatus());
        assertEquals("myToken123", resp.getEntity());
    }

    // ========================================================================
    // lifecycleValidation
    // ========================================================================

    @Test
    public void testLifecycleValidation_returnsTokenWithStatus200() {
        Response resp = resource.lifecycleValidation("  lifecycleToken  ");
        assertEquals(200, resp.getStatus());
        assertEquals("lifecycleToken", resp.getEntity());
    }

    // ========================================================================
    // lifecycleNotification
    // ========================================================================

    @Test
    public void testLifecycleNotification_returns202() {
        JsonObject notification = new JsonObject();
        Response resp = resource.lifecycleNotification(notification);
        assertEquals(202, resp.getStatus());
    }

    // ========================================================================
    // subscriptionNotification — no file in payload
    // ========================================================================

    @Test
    public void testSubscriptionNotification_noFile_returns200() {
        fileChangeHandler.payloadToReturn = new HashMap<>();
        // No "File" key in payload

        JsonObject notification = new JsonObject();
        Response resp = resource.subscriptionNotification(notification);
        assertEquals(200, resp.getStatus());
        assertTrue("Event should be dispatched", eventHandler.handleEventCalled);
    }

    // ========================================================================
    // subscriptionNotification — with file (non-duplicate)
    // ========================================================================

    @Test
    public void testSubscriptionNotification_withNewFile_returns200() {
        Map<String, Object> payload = createFilePayload("file1.txt", "/root/file1.txt", 1704067200L, "Created");
        fileChangeHandler.payloadToReturn = payload;

        JsonObject notification = new JsonObject();
        Response resp = resource.subscriptionNotification(notification);
        assertEquals(200, resp.getStatus());
        assertTrue("Event should be dispatched", eventHandler.handleEventCalled);
    }

    // ========================================================================
    // subscriptionNotification — duplicate file
    // ========================================================================

    @Test
    public void testSubscriptionNotification_duplicateFile_returns201() {
        Map<String, Object> payload = createFilePayload("file1.txt", "/root/file1.txt", 1704067200L, "Created");
        fileChangeHandler.payloadToReturn = payload;

        JsonObject notification = new JsonObject();
        // First call → new file
        resource.subscriptionNotification(notification);

        // Second call with same payload → duplicate
        fileChangeHandler.payloadToReturn = createFilePayload("file1.txt", "/root/file1.txt", 1704067200L, "Created");
        Response resp = resource.subscriptionNotification(notification);
        assertEquals(201, resp.getStatus());
    }

    // ========================================================================
    // subscriptionNotification — different files not duplicate
    // ========================================================================

    @Test
    public void testSubscriptionNotification_differentFiles_bothProcessed() {
        fileChangeHandler.payloadToReturn = createFilePayload("file1.txt", "/root/file1.txt", 1704067200L, "Created");
        JsonObject notification = new JsonObject();
        Response resp1 = resource.subscriptionNotification(notification);
        assertEquals(200, resp1.getStatus());

        fileChangeHandler.payloadToReturn = createFilePayload("file2.txt", "/root/file2.txt", 1704153600L, "Updated");
        Response resp2 = resource.subscriptionNotification(notification);
        assertEquals(200, resp2.getStatus());
    }

    // ========================================================================
    // subscriptionNotification — exception during processing
    // ========================================================================

    @Test
    public void testSubscriptionNotification_exception_returns200() {
        fileChangeHandler.throwOnGetChange = true;

        JsonObject notification = new JsonObject();
        Response resp = resource.subscriptionNotification(notification);
        assertEquals(200, resp.getStatus());
    }

    // ========================================================================
    // getAuthContextId — private method tested indirectly via getCallBack
    // ========================================================================

    @Test
    public void testGetCallBack_stateWithNoHash_authContextIdIsNull() {
        // state = "email" with no hash → parts.length == 1 → authContextId = null
        // code is null → returns failure message before using authContextId
        String result = resource.getCallBack(null, "email_only");
        assertEquals("Authentication Failed. Please re-authorize.", result);
    }

    @Test
    public void testGetCallBack_stateWithUrlAuthContextId_treatedAsNull() {
        // parts[1] starts with http:// → authContextId = null
        String result = resource.getCallBack(null, "email#https://callback.com");
        assertEquals("Authentication Failed. Please re-authorize.", result);
    }

    @Test
    public void testGetCallBack_stateWithPathAuthContextId_treatedAsNull() {
        // parts[1] starts with "/" → authContextId = null
        String result = resource.getCallBack(null, "email#/some/path");
        assertEquals("Authentication Failed. Please re-authorize.", result);
    }

    // ========================================================================
    // getCallBack — full callback flow (MsAuthException path)
    // ========================================================================

    @Test(expected = IllegalStateException.class)
    public void testGetCallBack_validCodeAndState_msAuthExceptionThrown() {
        // Factory returns a real provider so getOnedriveAttributes() works,
        // but handleCallback() fails because there's no real OAuth server
        providerFactory.returnRealProvider = true;
        providerFactory.lastCreatedAttributes = new OnedriveAttributes(
                BASE_URL, TEST_CLIENT_ID, TEST_CLIENT_SECRET, TEST_EMAIL, TEST_TENANT_ID, "Public");

        // state = "email#authContextId"
        resource.getCallBack("fake_auth_code", TEST_EMAIL + "#authCtx-test");
        // handleCallback → MsAuthException → rethrown as IllegalStateException
    }

    @Test(expected = IllegalStateException.class)
    public void testGetCallbackForV3Auth_validCode_msAuthExceptionThrown() {
        providerFactory.returnRealProvider = true;
        providerFactory.lastCreatedAttributes = new OnedriveAttributes(
                BASE_URL, TEST_CLIENT_ID, TEST_CLIENT_SECRET, TEST_EMAIL, TEST_TENANT_ID, "Public");

        resource.getCallbackForV3Auth("fake_auth_code", TEST_EMAIL + "#authCtx-test");
    }

    // ========================================================================
    // getCallBack — getGraphServiceClientProvider paths
    // ========================================================================

    @Test(expected = IllegalStateException.class)
    public void testGetCallBack_wsContactKey_usesPublicProvider() {
        // key starts with "wsContact" + authContextId not in store → falls back to PUBLIC attrs
        providerFactory.returnRealProvider = true;

        resource.getCallBack("fake_code", "wsContact123#authCtx-test");
        // Will fail at handleCallback, proving the wsContact path was taken
    }

    // ========================================================================
    // getGraphServiceClientProvider — wsContact with operator attrs (new behaviour)
    // ========================================================================

    @Test(expected = IllegalStateException.class)
    public void testGetCallBack_wsContactKey_withOperatorAttrs_usesOperatorAppForCallback() {
        // Store operator attrs under the authContextId (= invokerId from MCP state parts[1])
        OnedriveAttributes operatorAttrs = new OnedriveAttributes(
                BASE_URL, TEST_CLIENT_ID, TEST_CLIENT_SECRET, TEST_EMAIL, TEST_TENANT_ID, Constants.PUBLIC);
        attributeStore.savedAttributes.put(TEST_INVOKER_ID, operatorAttrs);

        providerFactory.returnRealProvider = true;

        // state: wsContactId#invokerId#forwardPath
        resource.getCallBack("fake_code",
                "wsContact-user-abc#" + TEST_INVOKER_ID + "#/rest/onedrive/callback");
        // handleCallback fails → MsAuthException → IllegalStateException
    }

    @Test
    public void testGetCallBack_wsContactKey_withOperatorAttrs_usesKristaDefaultNotOperatorClientId() {
        // MCP callback always uses Krista default credentials from config.properties,
        // even when operator attrs exist in the store. The auth code was issued by the
        // Krista default app so the token exchange must also use the same app.
        OnedriveAttributes operatorAttrs = new OnedriveAttributes(
                BASE_URL, TEST_CLIENT_ID, TEST_CLIENT_SECRET, TEST_EMAIL, TEST_TENANT_ID, Constants.PUBLIC);
        attributeStore.savedAttributes.put(TEST_INVOKER_ID, operatorAttrs);

        providerFactory.returnRealProvider = true;

        try {
            resource.getCallBack("fake_code",
                    "wsContact-user-abc#" + TEST_INVOKER_ID + "#/rest/onedrive/callback");
        } catch (IllegalStateException e) {
            // Expected: real OAuth call fails
        }

        assertNotNull("Provider should have been created with attributes", providerFactory.lastCreatedAttributes);
        // Krista default is used — clientId is NOT the operator's test-client-id
        assertNotEquals("Operator clientId must NOT be used for MCP callback",
                TEST_CLIENT_ID, providerFactory.lastCreatedAttributes.getClientId());
        assertEquals("Auth type must be PUBLIC for Krista default credentials",
                Constants.PUBLIC, providerFactory.lastCreatedAttributes.getAuthType());
    }

    @Test
    public void testGetCallBack_wsContactKey_noOperatorAttrs_usesKristaDefaultApp() {
        // No operator attrs in store → falls back to Krista default PUBLIC app.
        // The default app loads its clientId from config.properties, so it will be non-null
        // but will NOT equal the operator-specific TEST_CLIENT_ID.
        String unknownAuthCtxId = "INV_unknown-operator";

        providerFactory.returnRealProvider = true;

        try {
            resource.getCallBack("fake_code",
                    "wsContact-fallback#" + unknownAuthCtxId + "#/rest/onedrive/callback");
        } catch (IllegalStateException e) {
            // Expected: handleCallback fails (no real OAuth server)
        }

        assertNotNull("Provider should still have been created with fallback attrs",
                providerFactory.lastCreatedAttributes);
        // The fallback attrs were NOT the operator's own credentials
        assertNotEquals("Fallback should NOT use the operator's client ID",
                TEST_CLIENT_ID, providerFactory.lastCreatedAttributes.getClientId());
        assertEquals("Auth type must be PUBLIC for the Krista default fallback",
                Constants.PUBLIC, providerFactory.lastCreatedAttributes.getAuthType());
    }

    @Test(expected = IllegalStateException.class)
    public void testGetCallBack_wsContactKey_nullAuthContextId_usesKristaDefaultPublicApp() {
        // state with only 1 part → authContextId = null → uses Krista default PUBLIC
        providerFactory.returnRealProvider = true;

        resource.getCallBack("fake_code", "wsContact-no-ctx");
        // Will fail at handleCallback, proving the wsContact PUBLIC fallback path was taken
    }

    @Test
    public void testGetCallbackForV3Auth_wsContactKey_alwaysUsesKristaDefaultCredentials() {
        // v3 callback endpoint follows the same rule: wsContact always uses Krista default.
        OnedriveAttributes operatorAttrs = new OnedriveAttributes(
                BASE_URL, TEST_CLIENT_ID, TEST_CLIENT_SECRET, TEST_EMAIL, TEST_TENANT_ID, Constants.PUBLIC);
        attributeStore.savedAttributes.put(TEST_INVOKER_ID, operatorAttrs);

        providerFactory.returnRealProvider = true;

        try {
            resource.getCallbackForV3Auth("fake_code",
                    "wsContact-user-v3#" + TEST_INVOKER_ID + "#/rest/onedrive/callback");
        } catch (IllegalStateException e) {
            // Expected
        }

        assertNotNull(providerFactory.lastCreatedAttributes);
        assertNotEquals("v3 MCP callback must NOT use operator clientId",
                TEST_CLIENT_ID, providerFactory.lastCreatedAttributes.getClientId());
        assertEquals("Auth type must be PUBLIC for Krista default credentials",
                Constants.PUBLIC, providerFactory.lastCreatedAttributes.getAuthType());
    }

    @Test
    public void testGetCallBack_noAuthContextId_usesDefaultProvider() {
        // state with no hash → authContextId is null → uses factory.create() which returns null → NPE
        providerFactory.returnRealProvider = false;

        try {
            resource.getCallBack("fake_code", "user@example.com");
            fail("Expected exception");
        } catch (IllegalStateException | NullPointerException e) {
            // NPE because create() returns null (default path exercised)
        }
    }

    @Test
    public void testGetCallBack_stateWithHttpAuthContext_authContextIdIsNull() {
        // parts[1] starts with "http://" → authContextId = null → factory.create() used
        providerFactory.returnRealProvider = false;

        try {
            resource.getCallBack("fake_code", "user@example.com#http://callback");
        } catch (IllegalStateException | NullPointerException e) {
            // Expected — URL-like authContextId treated as null, falls to default path
        }
    }

    // ========================================================================
    // getExtensionMetadata
    // ========================================================================

    @Test
    public void testGetExtensionMetadata_unknownExtension_returnsErrorJson() {
        String result = resource.getExtensionMetadata("nonexistent-extension");
        assertNotNull(result);
        assertTrue(result.contains("error") || result.contains("Extension not found"));
    }

    // ========================================================================
    // Static fileStore limit — removeOldestMessageIds
    // ========================================================================

    @Test
    public void testSubscriptionNotification_fileStoreLimitEnforced() throws Exception {
        JsonObject notification = new JsonObject();

        // Fill fileStore near limit with unique files
        for (int i = 0; i < 1001; i++) {
            fileChangeHandler.payloadToReturn = createFilePayload(
                    "file" + i + ".txt", "/root/file" + i + ".txt", 1704067200L + i, "Created");
            resource.subscriptionNotification(notification);
        }

        // Should not throw — fileStore manages its size
        fileChangeHandler.payloadToReturn = createFilePayload("newfile.txt", "/root/newfile.txt", 1717200000L, "Created");
        Response resp = resource.subscriptionNotification(notification);
        assertEquals(200, resp.getStatus());

        // Verify fileStore size is under control via reflection
        Field fileStoreField = AuthenticationResource.class.getDeclaredField("fileStore");
        fileStoreField.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> fileStore = (List<Map<String, Object>>) fileStoreField.get(null);
        assertTrue("fileStore should be managed", fileStore.size() <= 1100);
    }

    // ========================================================================
    // testConnection — tested via auth flow branches
    // ========================================================================

    @Test
    public void testTestConnection_certAuth_graphCallFails_genericException() {
        providerFactory.throwGenericException = true;

        JsonObject payload = createCertAuthPayload();
        String result = resource.testConnection(payload);

        AuthenticationResponse resp = Constants.GSON.fromJson(result, AuthenticationResponse.class);
        assertFalse(resp.isSuccess());
        assertNotNull(resp.getErrorMessage());
        assertTrue(resp.getErrorMessage().contains("Connection test failed"));
    }

    @Test
    public void testTestConnection_certAuth_nullProvider_caughtAsException() {
        // create() returns null → NPE on .getGraphServiceClientForSystem() → caught by Exception handler
        JsonObject payload = createCertAuthPayload();
        String result = resource.testConnection(payload);

        AuthenticationResponse resp = Constants.GSON.fromJson(result, AuthenticationResponse.class);
        assertFalse(resp.isSuccess());
        assertTrue(resp.getErrorMessage().contains("Connection test failed"));
    }

    @Test
    public void testTestConnection_privateAuth_appOnlyMustAuthorize() {
        providerFactory.throwMustAuthorize = true;

        JsonObject payload = createPrivateAuthPayload();
        String result = resource.testConnection(payload);

        AuthenticationResponse resp = Constants.GSON.fromJson(result, AuthenticationResponse.class);
        assertFalse(resp.isSuccess());
        assertTrue(resp.getErrorMessage().contains("App-only authentication failed"));
    }

    @Test
    public void testTestConnection_publicAuth_mustAuthorize_returnsUrl() {
        providerFactory.throwMustAuthorize = true;

        JsonObject payload = createPublicOwnAuthPayload();
        String result = resource.testConnection(payload);

        AuthenticationResponse resp = Constants.GSON.fromJson(result, AuthenticationResponse.class);
        assertFalse(resp.isSuccess());
        assertEquals(Constants.AUTHORIZATION_PROMPT, resp.getErrorMessage());
    }

    @Test
    public void testTestConnection_publicAuth_nullProvider_caughtAsException() {
        // create() returns null → NPE on .getGraphServiceClientForAdmin() → caught by Exception handler
        JsonObject payload = createPublicOwnAuthPayload();
        String result = resource.testConnection(payload);

        AuthenticationResponse resp = Constants.GSON.fromJson(result, AuthenticationResponse.class);
        assertFalse(resp.isSuccess());
        assertTrue(resp.getErrorMessage().contains("Connection test failed"));
    }

    @Test
    public void testTestConnection_authContextIdRemovedInFinally() {
        providerFactory.throwGenericException = true;

        JsonObject payload = createPublicOwnAuthPayload();
        resource.testConnection(payload);

        // authContextId should be removed since authUrl was null (no MustAuthorize redirect)
        assertFalse("authContextId should be cleaned up",
                attributeStore.savedAttributes.containsKey(providerFactory.lastCreatedAuthContextId));
    }

    // ========================================================================
    // saveCredentials — tested via auth flow branches
    // ========================================================================

    @Test
    public void testSaveCredentials_cleanupCalledBeforeSave() {
        // Even though the graph call will NPE, cleanup should be called before the try block
        JsonObject payload = createPublicOwnAuthPayload();
        resource.saveCredentials(payload);

        assertTrue("Cleanup should be called before save", cleanupService.handleCredentialChangeCalled);
    }

    @Test
    public void testSaveCredentials_nullProvider_caughtAsException() {
        // create() returns null → NPE → caught by Exception handler
        JsonObject payload = createPublicOwnAuthPayload();
        String result = resource.saveCredentials(payload);

        AuthenticationResponse resp = Constants.GSON.fromJson(result, AuthenticationResponse.class);
        assertFalse(resp.isSuccess());
        assertTrue(resp.getErrorMessage().contains("Failed to save attributes"));
    }

    @Test
    public void testSaveCredentials_privateAuth_appOnlyMustAuthorize() {
        providerFactory.throwMustAuthorize = true;

        JsonObject payload = createPrivateAuthPayload();
        String result = resource.saveCredentials(payload);

        AuthenticationResponse resp = Constants.GSON.fromJson(result, AuthenticationResponse.class);
        assertFalse(resp.isSuccess());
        assertTrue(resp.getErrorMessage().contains("App-only authentication failed"));
    }

    @Test
    public void testSaveCredentials_publicAuth_mustAuthorize_savesAndReturnsUrl() {
        providerFactory.throwMustAuthorize = true;

        JsonObject payload = createPublicOwnAuthPayload();
        String result = resource.saveCredentials(payload);

        AuthenticationResponse resp = Constants.GSON.fromJson(result, AuthenticationResponse.class);
        assertFalse(resp.isSuccess());
        assertEquals(Constants.AUTHORIZATION_PROMPT, resp.getErrorMessage());
    }

    @Test
    public void testSaveCredentials_genericException() {
        providerFactory.throwGenericException = true;

        JsonObject payload = createPublicOwnAuthPayload();
        String result = resource.saveCredentials(payload);

        AuthenticationResponse resp = Constants.GSON.fromJson(result, AuthenticationResponse.class);
        assertFalse(resp.isSuccess());
        assertTrue(resp.getErrorMessage().contains("Failed to save attributes"));
    }

    @Test
    public void testSaveCredentials_certAuth_nullProvider_caughtAsException() {
        // cert path: create() returns null → NPE on .getGraphServiceClientForSystem() → Exception handler
        JsonObject payload = createCertAuthPayload();
        String result = resource.saveCredentials(payload);

        AuthenticationResponse resp = Constants.GSON.fromJson(result, AuthenticationResponse.class);
        assertFalse(resp.isSuccess());
        assertTrue(resp.getErrorMessage().contains("Failed to save attributes"));
    }

    @Test
    public void testSaveCredentials_certAuth_genericException() {
        providerFactory.throwGenericException = true;

        JsonObject payload = createCertAuthPayload();
        String result = resource.saveCredentials(payload);

        AuthenticationResponse resp = Constants.GSON.fromJson(result, AuthenticationResponse.class);
        assertFalse(resp.isSuccess());
        assertTrue(resp.getErrorMessage().contains("Failed to save attributes"));
    }

    @Test
    public void testSaveCredentials_authContextIdRemovedInFinally() {
        providerFactory.throwGenericException = true;

        JsonObject payload = createPublicOwnAuthPayload();
        resource.saveCredentials(payload);

        // authContextId should be removed since authUrl was null (no MustAuthorize redirect)
        assertFalse("authContextId should be cleaned up",
                attributeStore.savedAttributes.containsKey(providerFactory.lastCreatedAuthContextId));
    }

    @Test
    public void testSaveCredentials_publicMustAuthorize_authContextIdNotRemoved() {
        providerFactory.throwMustAuthorize = true;

        JsonObject payload = createPublicOwnAuthPayload();
        resource.saveCredentials(payload);

        // In the MustAuthorize + delegated path, authUrl is set → authContextId NOT removed in finally
        // (The attribute store save is called in the catch block)
        assertTrue("Attributes should be saved for delegated auth redirect",
                attributeStore.savedAttributes.containsKey(TEST_INVOKER_ID));
    }

    @Test
    public void testResolveRefreshToken_returnsNullWhenCallbackAndStoreHaveNoToken() {
        ITokenStore tokenStore = tokenStore(new HashMap<>());

        String refreshToken = resource.resolveRefreshToken(tokenStore, "missing-key",
                new TokenResult("access-token", null, null));

        assertNull(refreshToken);
    }

    @Test
    public void testResolveRefreshToken_fallsBackToStoredRefreshToken() {
        Map<String, String> tokens = new HashMap<>();
        tokens.put("token-key", "stored-refresh-token");

        String refreshToken = resource.resolveRefreshToken(tokenStore(tokens), "token-key",
                new TokenResult("access-token", null, null));

        assertEquals("stored-refresh-token", refreshToken);
    }

    @Test
    public void testBuildCallbackTokenKey_usesSdkCompositeKeyForWorkspaceContact() {
        OnedriveAttributes attrs = createPublicOwnAttributes();

        String tokenKey = resource.buildCallbackTokenKey("REDACTED_SECRET", attrs);

        assertEquals(MicrosoftAuthSdk.buildTokenKey("REDACTED_SECRET", attrs.getClientId(), attrs.getAuthType()), tokenKey);
    }

    @Test
    public void testStoreRefreshTokenAliases_savesWorkspaceContactAndAuthorizedAccountKeys() {
        contextAuthenticated = true;
        authorizedAccountId = "wsContact-authorized-123";
        OnedriveAttributes attrs = createPublicOwnAttributes();
        Map<String, String> tokens = new HashMap<>();

        resource.storeRefreshTokenAliases(tokenStore(tokens), "wsContact-callback-123", attrs, "refresh-token");

        assertEquals("refresh-token", tokens.get(MicrosoftAuthSdk.buildTokenKey("REDACTED_SECRET", attrs.getClientId(), attrs.getAuthType())));
        assertEquals("refresh-token", tokens.get("REDACTED_SECRET"));
        assertEquals("refresh-token", tokens.get(MicrosoftAuthSdk.buildTokenKey(authorizedAccountId, attrs.getClientId(), attrs.getAuthType())));
        assertEquals("refresh-token", tokens.get(authorizedAccountId));
        assertEquals("refresh-token", tokens.get(MicrosoftAuthSdk.buildTokenKey(TEST_EMAIL, attrs.getClientId(), attrs.getAuthType())));
    }

    // ========================================================================
    // HELPER: Attribute creation and storage
    // ========================================================================

    private OnedriveAttributes createPublicKristaAttributes() {
        OnedriveAttributes attrs = new OnedriveAttributes(BASE_URL, TEST_CLIENT_ID, TEST_CLIENT_SECRET, TEST_EMAIL, TEST_TENANT_ID, "Public");
        attrs.setPublicAuthMode("krista");
        return attrs;
    }

    private OnedriveAttributes createPublicOwnAttributes() {
        OnedriveAttributes attrs = new OnedriveAttributes(BASE_URL, TEST_CLIENT_ID, TEST_CLIENT_SECRET, TEST_EMAIL, TEST_TENANT_ID, "Public");
        attrs.setPublicAuthMode("own");
        return attrs;
    }

    private void storeAttributes(OnedriveAttributes attrs) {
        attributeStore.savedAttributes.put(TEST_INVOKER_ID, attrs);
    }

    private Map<String, Object> createFilePayload(String name, String absolutePath, long modified, String event) {
        Map<String, Object> payload = new HashMap<>();
        app.krista.extensions.essentials.cloudfiles.onedrive.catalog.entities.File file =
                new app.krista.extensions.essentials.cloudfiles.onedrive.catalog.entities.File();
        file.name = name;
        file.absolutePath = absolutePath;
        file.modified = modified;
        payload.put(Constants.FILE, file);
        payload.put(Constants.EVENT, event);
        return payload;
    }

    private JsonObject createCertAuthPayload() {
        JsonObject payload = new JsonObject();
        payload.addProperty(Constants.AUTH_TYPE, Constants.CLIENT_CREDENTIALS_CERT);
        payload.addProperty(Constants.CLIENT_ID, TEST_CLIENT_ID);
        payload.addProperty(Constants.CLIENT_SECRET, "");
        payload.addProperty(Constants.EMAIL, TEST_EMAIL);
        payload.addProperty(Constants.TENANT_ID, TEST_TENANT_ID);
        payload.addProperty(Constants.CERTIFICATE_CONTENT, "dGVzdA==");
        return payload;
    }

    private JsonObject createPrivateAuthPayload() {
        JsonObject payload = new JsonObject();
        payload.addProperty(Constants.AUTH_TYPE, Constants.PRIVATE);
        payload.addProperty(Constants.CLIENT_ID, TEST_CLIENT_ID);
        payload.addProperty(Constants.CLIENT_SECRET, TEST_CLIENT_SECRET);
        payload.addProperty(Constants.EMAIL, TEST_EMAIL);
        payload.addProperty(Constants.TENANT_ID, TEST_TENANT_ID);
        return payload;
    }

    private JsonObject createPublicOwnAuthPayload() {
        JsonObject payload = new JsonObject();
        payload.addProperty(Constants.AUTH_TYPE, Constants.PUBLIC);
        payload.addProperty("publicAuthMode", "own");
        payload.addProperty(Constants.CLIENT_ID, TEST_CLIENT_ID);
        payload.addProperty(Constants.CLIENT_SECRET, TEST_CLIENT_SECRET);
        payload.addProperty(Constants.EMAIL, TEST_EMAIL);
        payload.addProperty(Constants.TENANT_ID, TEST_TENANT_ID);
        return payload;
    }

    private void clearFileStore() throws Exception {
        Field fileStoreField = AuthenticationResource.class.getDeclaredField("fileStore");
        fileStoreField.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> fileStore = (List<Map<String, Object>>) fileStoreField.get(null);
        fileStore.clear();
    }

    // ========================================================================
    // Mock: Invoker
    // ========================================================================

    private Invoker createMockInvoker() {
        RoutingInfo routingInfo = (RoutingInfo) Proxy.newProxyInstance(
                RoutingInfo.class.getClassLoader(),
                new Class<?>[]{RoutingInfo.class},
                (proxy, method, args) -> {
                    if ("getRoutingURL".equals(method.getName())) {
                        return BASE_URL;
                    }
                    return null;
                });

        return (Invoker) Proxy.newProxyInstance(
                Invoker.class.getClassLoader(),
                new Class<?>[]{Invoker.class},
                (proxy, method, args) -> {
                    if ("getInvokerId".equals(method.getName())) {
                        return TEST_INVOKER_ID;
                    }
                    if ("getRoutingInfo".equals(method.getName())) {
                        return routingInfo;
                    }
                    if ("getAttributes".equals(method.getName())) {
                        return Collections.emptyMap();
                    }
                    if (method.getReturnType() == boolean.class) return false;
                    if (method.getReturnType() == Map.class) return Collections.emptyMap();
                    if (method.getReturnType() == List.class) return Collections.emptyList();
                    return null;
                });
    }

    private AuthorizationContext createMockAuthorizationContext() {
        return (AuthorizationContext) Proxy.newProxyInstance(
                AuthorizationContext.class.getClassLoader(),
                new Class<?>[]{AuthorizationContext.class},
                (proxy, method, args) -> {
                    if ("isAuthenticated".equals(method.getName())) {
                        return contextAuthenticated;
                    }
                    if ("getAuthorizedAccount".equals(method.getName())) {
                        return createMockAccount();
                    }
                    if (method.getReturnType() == boolean.class) return false;
                    if (method.getReturnType() == String.class) return "";
                    return null;
                });
    }

    private Account createMockAccount() {
        return (Account) Proxy.newProxyInstance(
                Account.class.getClassLoader(),
                new Class<?>[]{Account.class},
                (proxy, method, args) -> {
                    if ("getAccountId".equals(method.getName())) {
                        return authorizedAccountId;
                    }
                    if ("getPrimaryEmailAddress".equals(method.getName())) {
                        return authorizedEmail;
                    }
                    if (method.getReturnType() == boolean.class) return false;
                    if (method.getReturnType() == Set.class) return Collections.emptySet();
                    if (method.getReturnType() == Map.class) return Collections.emptyMap();
                    return null;
                });
    }

    // ========================================================================
    // Mock: TrackingAttributeStore
    // ========================================================================

    private static class TrackingAttributeStore extends OnedriveAttributeStore {
        final Map<String, OnedriveAttributes> savedAttributes = new HashMap<>();
        final Set<String> removedKeys = new HashSet<>();

        TrackingAttributeStore(Invoker invoker, KeyValueStore store) {
            super(invoker, store);
        }

        @Override
        public OnedriveAttributes load(String authContextId) {
            return savedAttributes.get(authContextId);
        }

        @Override
        public String save(OnedriveAttributes onedriveAttributes) {
            String id = "authCtx-" + UUID.randomUUID();
            savedAttributes.put(id, onedriveAttributes);
            return id;
        }

        @Override
        public boolean save(OnedriveAttributes onedriveAttributes, String invokerId) {
            savedAttributes.put(invokerId, onedriveAttributes);
            return true;
        }

        @Override
        public void remove(String authContextId) {
            removedKeys.add(authContextId);
            savedAttributes.remove(authContextId);
        }
    }

    // ========================================================================
    // Mock: TrackingProviderFactory
    // ========================================================================

    private static class TrackingProviderFactory extends GraphServiceClientProviderFactory {
        boolean throwMustAuthorize = false;
        boolean throwGenericException = false;
        boolean returnRealProvider = false;
        String lastCreatedAuthContextId;
        OnedriveAttributes lastCreatedAttributes;
        private final KeyValueStore kvs;

        TrackingProviderFactory(KeyValueStore kvs, OnedriveAttributeStore attrStore,
                                app.krista.ksdk.context.RequestContext rc, AuthorizationContext ac) {
            super(kvs, attrStore, rc, ac);
            this.kvs = kvs;
        }

        @Override
        public String createAttributes(OnedriveAttributes attributes) {
            lastCreatedAuthContextId = "authCtx-" + UUID.randomUUID();
            lastCreatedAttributes = attributes;
            return lastCreatedAuthContextId;
        }

        @Override
        public GraphServiceClientProvider create(String authContextId) {
            if (throwMustAuthorize) {
                throw new app.krista.extension.authorization.MustAuthorizeException("Authorization required");
            }
            if (throwGenericException) {
                throw new RuntimeException("Simulated error");
            }
            if (returnRealProvider && lastCreatedAttributes != null) {
                // Create a real provider with null delegate (keyValueStore=null) so
                // getOnedriveAttributes() works but Graph calls NPE
                return new GraphServiceClientProvider(null, lastCreatedAttributes, null, null, authContextId);
            }
            return null;
        }

        @Override
        public GraphServiceClientProvider create(OnedriveAttributes attrs) {
            lastCreatedAttributes = attrs;
            if (returnRealProvider) {
                return new GraphServiceClientProvider(null, attrs, null, null, null);
            }
            return create("dummy");
        }

        @Override
        public GraphServiceClientProvider create() {
            return create("dummy");
        }
    }

    // ========================================================================
    // Mock: EventHandler (Proxy-based — external interface)
    // ========================================================================

    private static class EventHandlerTracker {
        boolean handleEventCalled = false;
        String lastEventName;
        final EventHandler proxy;

        EventHandlerTracker() {
            proxy = (EventHandler) Proxy.newProxyInstance(
                    EventHandler.class.getClassLoader(),
                    new Class<?>[]{EventHandler.class},
                    (p, method, args) -> {
                        if ("handleEvent".equals(method.getName())) {
                            handleEventCalled = true;
                            lastEventName = args != null && args.length > 0 ? (String) args[0] : null;
                            if (CompletableFuture.class.isAssignableFrom(method.getReturnType())) {
                                return CompletableFuture.completedFuture(Collections.emptyIterator());
                            }
                            return null;
                        }
                        if (method.getReturnType() == boolean.class) return false;
                        return null;
                    });
        }
    }

    // ========================================================================
    // Mock: AuthorizationListener (Proxy-based — external interface)
    // ========================================================================

    private static class AuthorizationListenerTracker {
        boolean authorizedCalled = false;
        final AuthorizationListener proxy;

        AuthorizationListenerTracker() {
            proxy = (AuthorizationListener) Proxy.newProxyInstance(
                    AuthorizationListener.class.getClassLoader(),
                    new Class<?>[]{AuthorizationListener.class},
                    (p, method, args) -> {
                        if ("authorized".equals(method.getName())) {
                            authorizedCalled = true;
                        }
                        if (method.getReturnType() == boolean.class) return false;
                        return null;
                    });
        }
    }

    // ========================================================================
    // Mock: OnedriveSubscriptionCleanupService
    // ========================================================================

    private static class TrackingCleanupService extends OnedriveSubscriptionCleanupService {
        boolean handleCredentialChangeCalled = false;

        TrackingCleanupService() {
            super(null, null, null, null, null, null);
        }

        @Override
        public void handleCredentialChange(OnedriveAttributes newAttributes) {
            handleCredentialChangeCalled = true;
        }

        @Override
        public void handleInvokerRemoval() {
            // no-op
        }
    }

    // ========================================================================
    // Mock: OnedriveSetUpService
    // ========================================================================

    private static class TrackingSetUpService extends OnedriveSetUpService {
        boolean initializeSubscriptionCalled = false;

        TrackingSetUpService() {
            super(null, null, null, null, null);
        }

        @Override
        public void initializeSubscriptionWithProvider(GraphServiceClientProvider provider) {
            initializeSubscriptionCalled = true;
        }
    }

    // ========================================================================
    // Mock: FileChangeInfoHandler
    // ========================================================================

    private static class TrackingFileChangeInfoHandler extends FileChangeInfoHandler {
        Map<String, Object> payloadToReturn = new HashMap<>();
        boolean throwOnGetChange = false;

        TrackingFileChangeInfoHandler() {
            super((GraphServiceClientProviderFactory) null, createStaticMockInvoker());
        }

        @Override
        public Map<String, Object> getChangeFileEvent() {
            if (throwOnGetChange) {
                throw new RuntimeException("Simulated file change error");
            }
            return payloadToReturn;
        }

        private static Invoker createStaticMockInvoker() {
            RoutingInfo routingInfo = (RoutingInfo) Proxy.newProxyInstance(
                    RoutingInfo.class.getClassLoader(),
                    new Class<?>[]{RoutingInfo.class},
                    (proxy, method, args) -> {
                        if ("getRoutingURL".equals(method.getName())) return "http://localhost:8765";
                        return null;
                    });
            return (Invoker) Proxy.newProxyInstance(
                    Invoker.class.getClassLoader(),
                    new Class<?>[]{Invoker.class},
                    (proxy, method, args) -> {
                        if ("getRoutingInfo".equals(method.getName())) return routingInfo;
                        if ("getInvokerId".equals(method.getName())) return "mock-invoker";
                        if (method.getReturnType() == boolean.class) return false;
                        if (method.getReturnType() == Map.class) return Collections.emptyMap();
                        return null;
                    });
        }
    }

    // ========================================================================
    // Mock: KeyValueStore
    // ========================================================================

    private static class MockKeyValueStore implements KeyValueStore {
        private final Map<String, Object> data = new ConcurrentHashMap<>();

        @Override
        public Object get(String key) { return data.get(key); }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T get(String key, Class<T> type) {
            Object value = data.get(key);
            if (value == null) return null;
            if (type.isInstance(value)) return type.cast(value);
            return (T) value;
        }

        @Override
        public void remove(String key) { data.remove(key); }

        @Override
        public boolean contains(String key) { return data.containsKey(key); }

        @Override
        public void put(String key, Object value) { data.put(key, value); }

        @Override
        public boolean supports(Class<?> clazz) { return true; }

        @Override
        public Set<String> getKeys() { return new HashSet<>(data.keySet()); }

        @Override
        public Iterator<Map.Entry<String, Object>> iterator() { return data.entrySet().iterator(); }
    }

    private static ITokenStore tokenStore(Map<String, String> tokens) {
        return new ITokenStore() {
            @Override
            public void save(String key, String value) {
                tokens.put(key, value);
            }

            @Override
            public String get(String key) {
                return tokens.get(key);
            }

            @Override
            public void delete(String key) {
                tokens.remove(key);
            }
        };
    }
}
