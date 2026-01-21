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

import app.krista.extension.executor.ExtensionResponse;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.OnedriveSubscription;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.connectors.GraphServiceClientProvider;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.connectors.GraphServiceClientProviderFactory;
import app.krista.ksdk.telemetry.TelemetryMetrics;
import com.microsoft.graph.requests.UserRequestBuilder;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Non-mocked unit tests for OnedriveSetUpService.
 * <p>
 * This test class focuses on testing the public methods and logic that don't require
 * external dependencies. The main methods require provider and other dependencies,
 * so we focus on testing:
 * - Constructor validation
 * - Input validation logic
 * - Helper method behavior
 * - Error handling scenarios
 * - Edge cases and boundary conditions
 * - Health check functionality
 */
public class OnedriveSetUpServiceTest {

    private OnedriveSetUpService onedriveSetUpService;

    // Test data constants
    private static final String TEST_EMAIL = "test@example.com";
    private static final String TEST_ROUTING_URL = "http://localhost:8765";
    private static final String TEST_ACCOUNT_ID = "test-account-123";

    @Before
    public void setUp() {
        ContextService mockContextService = createMockContextService();
        OnedriveAttributes mockAttributes = createMockAttributes();
        GraphServiceClientProviderFactory mockClientProviderFactory = createMockClientProviderFactory();
        OnedriveRequestAuthenticator mockRequestAuthenticator = createMockRequestAuthenticator();
        OnedriveSubscription mockOnedriveSubscription = createMockOnedriveSubscription();
        TelemetryMetrics mockTelemetryMetrics = createMockTelemetryMetrics();

        onedriveSetUpService = new OnedriveSetUpService(
                mockContextService,           // ContextService - mock for routing URL and account ID
                mockAttributes,               // OnedriveAttributes - mock for configuration
                mockClientProviderFactory,    // GraphServiceClientProviderFactory - mock for client creation
                mockRequestAuthenticator,     // OnedriveRequestAuthenticator - mock for authentication
                mockOnedriveSubscription,     // OnedriveSubscription - mock for subscription management
                mockTelemetryMetrics          // TelemetryMetrics - mock for telemetry
        );
    }

    private ContextService createMockContextService() {
        return new ContextService(null, null) {
            @Override
            public String getRoutingUrl() {
                return TEST_ROUTING_URL;
            }

            @Override
            public String getAccountId() {
                return TEST_ACCOUNT_ID;
            }
        };
    }

    private OnedriveAttributes createMockAttributes() {
        return new OnedriveAttributes(
                TEST_ROUTING_URL,       // routingUrl
                "test-client-id",       // clientId
                "test-client-secret",   // clientSecret
                TEST_EMAIL,             // mailId
                "test-tenant-id"        // tenantId
        );
    }

    private GraphServiceClientProvider createMockProvider() {
        return new GraphServiceClientProvider(null, null, null, null) {
            @Override
            public UserRequestBuilder getUserRequestBuilder(Boolean useEmail, String accountId) {
                return null; // Return null for testing - methods that use this will handle gracefully
            }
        };
    }

    private GraphServiceClientProviderFactory createMockClientProviderFactory() {
        return new GraphServiceClientProviderFactory(null, null, null, null, createMockProvider()) {
            @Override
            public GraphServiceClientProvider create(OnedriveAttributes onedriveAttributes) {
                return createMockProvider();
            }

            @Override
            public GraphServiceClientProvider create() {
                return createMockProvider();
            }
        };
    }

    private OnedriveRequestAuthenticator createMockRequestAuthenticator() {
        return new OnedriveRequestAuthenticator(null, null) {
            // Mock implementation - all methods return default values
        };
    }

    private OnedriveSubscription createMockOnedriveSubscription() {
        return new OnedriveSubscription(null) {
            @Override
            public void createSubscriptionFromClientSide(String routingUrl, GraphServiceClientProvider provider) {
                // Mock implementation - do nothing for testing
            }
        };
    }

    private TelemetryMetrics createMockTelemetryMetrics() {
        return new TelemetryMetrics() {
            @Override
            public void incrementCounter(String metric) {
                // Do nothing - skip telemetry for testing
            }

            @Override
            public void incrementCounter(String s, long l) {
                
            }

            @Override
            public void incrementCounter(String s, long l, Map<String, String> map) {

            }
            

            @Override
            public void recordDuration(String metric, long duration) {
                // Do nothing - skip telemetry for testing
            }

            @Override
            public void recordDuration(String metric, long duration, Map<String, String> tags) {
                // Do nothing - skip telemetry for testing
            }

            @Override
            public void observeGauge(String s, double v) {

            }

            @Override
            public void observeGauge(String s, double v, Map<String, String> map) {

            }

            @Override
            public boolean sendAlertEmail(Exception e, String s, Map<String, Object> map, Map<String, Object> map1) {
                return false;
            }
        };
    }

    /**
     * Test constructor - should not throw exception
     */
    @Test
    public void testConstructor() {
        assertNotNull("OnedriveSetUpService should be created successfully", onedriveSetUpService);
    }


    /**
     * Test validateConnection with empty connection attributes
     */
    @Test
    public void testValidateConnectionWithEmptyAttributes() {
        Map<String, Object> emptyAttributes = new HashMap<>();
        // This should not throw an exception
        onedriveSetUpService.validateConnection(emptyAttributes);
    }


    /**
     * Test validateConnectionFromClientSide with empty attributes
     */
    @Test
    public void testValidateConnectionFromClientSideWithEmptyAttributes() {
        Map<String, Object> emptyAttributes = new HashMap<>();
        boolean result = onedriveSetUpService.validateConnectionFromClientSide(emptyAttributes);
        assertFalse("Should return false for empty attributes", result);
    }

    /**
     * Test testConnection method with fromStudio = true - but with null email to avoid API calls
     */
    @Test
    public void testTestConnectionFromStudioWithNullEmail() throws IOException {
        // Create a service with attributes that have null email to avoid actual API calls
        OnedriveAttributes attributesWithNullEmail = new OnedriveAttributes(
                TEST_ROUTING_URL, "test-client-id", "test-client-secret", null, "test-tenant-id");

        OnedriveSetUpService serviceWithNullEmail = new OnedriveSetUpService(
                createMockContextService(), attributesWithNullEmail, createMockClientProviderFactory(),
                createMockRequestAuthenticator(), createMockOnedriveSubscription(), createMockTelemetryMetrics());

        // This should not throw an exception when email is null
        serviceWithNullEmail.testConnection(true);
    }

    /**
     * Test testConnection method with fromStudio = false - but with null email to avoid API calls
     */
    @Test
    public void testTestConnectionNotFromStudioWithNullEmail() throws IOException {
        // Create a service with attributes that have null email to avoid actual API calls
        OnedriveAttributes attributesWithNullEmail = new OnedriveAttributes(
                TEST_ROUTING_URL, "test-client-id", "test-client-secret", null, "test-tenant-id");

        OnedriveSetUpService serviceWithNullEmail = new OnedriveSetUpService(
                createMockContextService(), attributesWithNullEmail, createMockClientProviderFactory(),
                createMockRequestAuthenticator(), createMockOnedriveSubscription(), createMockTelemetryMetrics());

        // This should not throw an exception when email is null
        serviceWithNullEmail.testConnection(false);
    }

    /**
     * Test attributesUpdated method
     */
    @Test
    public void testAttributesUpdated() {
        Map<String, Object> oldAttributes = new HashMap<>();
        oldAttributes.put("clientId", "old-client-id");

        Map<String, Object> newAttributes = new HashMap<>();
        newAttributes.put("clientId", "new-client-id");

        // This should not throw an exception
        onedriveSetUpService.attributesUpdated(oldAttributes, newAttributes);
    }

    /**
     * Test getRequestAuthenticator method
     */
    @Test
    public void testGetRequestAuthenticator() {
        OnedriveRequestAuthenticator authenticator = onedriveSetUpService.getRequestAuthenticator();
        assertNotNull("Request authenticator should not be null", authenticator);
    }

    /**
     * Test getOnedriveAttributes method
     */
    @Test
    public void testGetOnedriveAttributes() {
        OnedriveAttributes attributes = onedriveSetUpService.getOnedriveAttributes();
        assertNotNull("Onedrive attributes should not be null", attributes);
        assertEquals("Mail ID should match", TEST_EMAIL, attributes.getMailId());
    }

    /**
     * Test checkHealth method - success scenario
     */
    @Test
    public void testCheckHealthSuccess() {
        ExtensionResponse response = onedriveSetUpService.checkHealth();
        assertNotNull("Health check response should not be null", response);
        assertTrue("Health check should be successful", response.getResult()== ExtensionResponse.Result.SUCCESS);
        assertNotNull("Response should not be null", response);
    }

    /**
     * Test showTime method indirectly through checkHealth
     */
    @Test
    public void testShowTimeIndirectly() {
        ExtensionResponse response = onedriveSetUpService.checkHealth();
        assertNotNull("Health check response should not be null", response);
        // The showTime method is called internally during health check
        // We can verify it doesn't cause any exceptions
    }

    /**
     * Test validateConnection with attributes containing blank email
     */
    @Test
    public void testValidateConnectionWithBlankEmail() {
        Map<String, Object> attributesWithBlankEmail = new HashMap<>();
        attributesWithBlankEmail.put("clientId", "test-client-id");
        attributesWithBlankEmail.put("clientSecret", "test-client-secret");
        attributesWithBlankEmail.put("email", "");  // Blank email
        attributesWithBlankEmail.put("tenantId", "test-tenant-id");

        // This should not throw an exception and should handle blank email gracefully
        onedriveSetUpService.validateConnection(attributesWithBlankEmail);
    }

    /**
     * Test validateConnectionFromClientSide with attributes containing blank email
     */
    @Test
    public void testValidateConnectionFromClientSideWithBlankEmail() {
        Map<String, Object> attributesWithBlankEmail = new HashMap<>();
        attributesWithBlankEmail.put("clientId", "test-client-id");
        attributesWithBlankEmail.put("clientSecret", "test-client-secret");
        attributesWithBlankEmail.put("email", "");  // Blank email
        attributesWithBlankEmail.put("tenantId", "test-tenant-id");

        boolean result = onedriveSetUpService.validateConnectionFromClientSide(attributesWithBlankEmail);
        assertFalse("Should return false for blank email", result);
    }

    /**
     * Test that START_TIME is properly initialized
     */
    @Test
    public void testStartTimeInitialization() {
        assertNotNull("START_TIME should be initialized", OnedriveSetUpService.START_TIME);
        assertTrue("START_TIME should be before current time",
                   OnedriveSetUpService.START_TIME.isBefore(java.time.Instant.now().plusSeconds(1)));
    }

    /**
     * Test health check response structure
     */
    @Test
    public void testHealthCheckResponseStructure() {
        ExtensionResponse response = onedriveSetUpService.checkHealth();
        assertNotNull("Health check response should not be null", response);
        assertTrue("Health check should be successful", response.getResult()== ExtensionResponse.Result.SUCCESS);

        // Just verify the response is not null and successful
        // The internal structure testing would require more complex mocking
        assertNotNull("Response should not be null", response);
    }
}
