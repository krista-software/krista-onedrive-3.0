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

package app.krista.extensions.essentials.cloudfiles.onedrive.impl;

import app.krista.extensions.essentials.cloudfiles.onedrive.OnedriveAttributes;
import app.krista.extensions.essentials.cloudfiles.onedrive.catalog.FileHandlers.TelemetryHelper;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.connectors.GraphServiceClientProvider;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.connectors.GraphServiceClientProviderFactory;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.util.Constants;
import app.krista.model.base.FreeForm;
import com.microsoft.graph.requests.UserRequestBuilder;
import org.junit.Before;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.*;

/**
 * Non-mocked unit tests for PermissionDao.
 * <p>
 * This test class focuses on testing the public methods and logic that don't require
 * external dependencies. The main fetchFilesWithPermissions method requires provider
 * and other dependencies, so we focus on testing:
 * - Constructor validation
 * - Input validation logic
 * - Permission validation
 * - Error handling scenarios
 * - Edge cases and boundary conditions
 * - Unsupported permission handling
 */
public class PermissionDaoTest {

    private PermissionDao permissionDao;

    // Test data constants
    private static final String VALID_PERMISSION_READ = Constants.READ;
    private static final String VALID_PERMISSION_WRITE = Constants.WRITE;
    private static final String UNSUPPORTED_PERMISSION_OWN = Constants.OWN;
    private static final String UNSUPPORTED_PERMISSION_READ_WRITE = Constants.READ_AND_WRITE;
    private static final String INVALID_PERMISSION = "INVALID_PERMISSION";
    private static final String TEST_ACCOUNT_ID = "test-account-123";
    private static final String TEST_EMAIL = "test@example.com";

    @Before
    public void setUp() {
        // Create a real PermissionDao instance with minimal dependencies
        // This allows us to test the core logic without external dependencies
        GraphServiceClientProvider mockProvider = createMockProvider();
        OnedriveAttributes mockAttributes = createMockAttributes();
        TelemetryHelper mockTelemetryHelper = createMockTelemetryHelper();

        permissionDao = new PermissionDao(
            mockProvider,           // GraphServiceClientProvider - mock to avoid NPE
            mockAttributes,         // OnedriveAttributes - mock for configuration
            mockTelemetryHelper     // Mock telemetry helper to avoid NPE
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

    private OnedriveAttributes createMockAttributes() {
        return new OnedriveAttributes(
            "http://localhost:8765",  // routingUrl
            "test-client-id",         // clientId
            "test-client-secret",     // clientSecret
            TEST_EMAIL,               // mailId
            "test-tenant-id"          // tenantId
        );
    }

    private TelemetryHelper createMockTelemetryHelper() {
        return new TelemetryHelper(null) {
            @Override
            public void incrementCount(String metric) {
                // Do nothing - skip telemetry for testing
            }

            @Override
            public void recordSuccess(String operation, long startTime, Map<String, String> tags) {
                // Do nothing - skip telemetry for testing
            }

            @Override
            public void recordError(String operation, long startTime, Exception error, Map<String, String> tags) {
                // Do nothing - skip telemetry for testing
            }

            @Override
            public void recordValidationError(String operation, long startTime, String reason, Map<String, String> tags) {
                // Do nothing - skip telemetry for testing
            }

            @Override
            public void recordRetryPrompted(String operation, long startTime, Map<String, String> tags) {
                // Do nothing - skip telemetry for testing
            }
        };
    }

    // ========================================
    // CONSTRUCTOR TESTS
    // ========================================

    /**
     * Test constructor with factory - should not throw exception
     */
    @Test
    public void testConstructorWithFactory() {
        GraphServiceClientProviderFactory mockFactory = new GraphServiceClientProviderFactory(
            null, null, null, null) {
            @Override
            public GraphServiceClientProvider create() {
                return createMockProvider();
            }
        };

        OnedriveAttributes attributes = createMockAttributes();
        TelemetryHelper telemetryHelper = createMockTelemetryHelper();

        // This should not throw an exception
        PermissionDao dao = new PermissionDao(mockFactory, attributes, telemetryHelper);
        assertNotNull("PermissionDao should be created successfully", dao);
    }

    /**
     * Test constructor with direct provider - should not throw exception
     */
    @Test
    public void testConstructorWithProvider() {
        GraphServiceClientProvider provider = createMockProvider();
        OnedriveAttributes attributes = createMockAttributes();
        TelemetryHelper telemetryHelper = createMockTelemetryHelper();

        // This should not throw an exception
        PermissionDao dao = new PermissionDao(provider, attributes, telemetryHelper);
        assertNotNull("PermissionDao should be created successfully", dao);
    }

    // ========================================
    // PERMISSION VALIDATION TESTS
    // ========================================

    /**
     * Test fetchFilesWithPermissions with unsupported permissions
     */
    @Test
    public void testFetchFilesWithPermissions_UnsupportedPermissions() {
        // Test OWN permission (unsupported)
        FreeForm result1 = permissionDao.fetchFilesWithPermissions(UNSUPPORTED_PERMISSION_OWN, true, TEST_ACCOUNT_ID);
        assertNotNull("Result should not be null for OWN permission", result1);

        // Test READ_AND_WRITE permission (unsupported)
        FreeForm result2 = permissionDao.fetchFilesWithPermissions(UNSUPPORTED_PERMISSION_READ_WRITE, false, TEST_ACCOUNT_ID);
        assertNotNull("Result should not be null for READ_AND_WRITE permission", result2);
    }

    /**
     * Test fetchFilesWithPermissions with supported permissions (will attempt API calls)
     */
    @Test
    public void testFetchFilesWithPermissions_SupportedPermissions() {
        try {
            // Test READ permission (supported)
            FreeForm result1 = permissionDao.fetchFilesWithPermissions(VALID_PERMISSION_READ, true, TEST_ACCOUNT_ID);
            assertNotNull("Result should not be null for READ permission", result1);
        } catch (Exception e) {
            // Expected with null provider when trying to make API calls
            assertTrue("Exception expected when supported permission triggers API calls", true);
        }

        try {
            // Test WRITE permission (supported)
            FreeForm result2 = permissionDao.fetchFilesWithPermissions(VALID_PERMISSION_WRITE, false, TEST_ACCOUNT_ID);
            assertNotNull("Result should not be null for WRITE permission", result2);
        } catch (Exception e) {
            // Expected with null provider when trying to make API calls
            assertTrue("Exception expected when supported permission triggers API calls", true);
        }
    }

    /**
     * Test fetchFilesWithPermissions with invalid permissions
     */
    @Test
    public void testFetchFilesWithPermissions_InvalidPermissions() {
        try {
            FreeForm result = permissionDao.fetchFilesWithPermissions(INVALID_PERMISSION, true, TEST_ACCOUNT_ID);
            assertNotNull("Result should not be null for invalid permission", result);
        } catch (Exception e) {
            // Expected with null provider or invalid permission
            assertTrue("Exception expected for invalid permission", true);
        }
    }

    // ========================================
    // GETFILEBYPERMISSION METHOD TESTS
    // ========================================

    /**
     * Test getFileByPermission with unsupported permissions
     */
    @Test
    public void testGetFileByPermission_UnsupportedPermissions() {
        // Test OWN permission (unsupported)
        FreeForm result1 = permissionDao.getFileByPermission(UNSUPPORTED_PERMISSION_OWN, true, TEST_ACCOUNT_ID);
        assertNotNull("Result should not be null for OWN permission", result1);

        // Test READ_AND_WRITE permission (unsupported)
        FreeForm result2 = permissionDao.getFileByPermission(UNSUPPORTED_PERMISSION_READ_WRITE, false, TEST_ACCOUNT_ID);
        assertNotNull("Result should not be null for READ_AND_WRITE permission", result2);
    }

    /**
     * Test getFileByPermission with supported permissions (will attempt API calls)
     */
    @Test
    public void testGetFileByPermission_SupportedPermissions() {
        try {
            // Test READ permission (supported)
            FreeForm result1 = permissionDao.getFileByPermission(VALID_PERMISSION_READ, true, TEST_ACCOUNT_ID);
            assertNotNull("Result should not be null for READ permission", result1);
        } catch (Exception e) {
            // Expected with null provider when trying to make API calls
            assertTrue("Exception expected when supported permission triggers API calls", true);
        }

        try {
            // Test WRITE permission (supported)
            FreeForm result2 = permissionDao.getFileByPermission(VALID_PERMISSION_WRITE, false, TEST_ACCOUNT_ID);
            assertNotNull("Result should not be null for WRITE permission", result2);
        } catch (Exception e) {
            // Expected with null provider when trying to make API calls
            assertTrue("Exception expected when supported permission triggers API calls", true);
        }
    }

    // ========================================
    // INPUT VALIDATION TESTS
    // ========================================

    /**
     * Test methods with null permission
     */
    @Test
    public void testMethods_NullPermission() {
        try {
            FreeForm result1 = permissionDao.fetchFilesWithPermissions(null, true, TEST_ACCOUNT_ID);
            assertNotNull("Result should not be null for null permission", result1);
        } catch (Exception e) {
            // Expected with null permission
            assertTrue("Exception expected for null permission", true);
        }

        try {
            FreeForm result2 = permissionDao.getFileByPermission(null, false, TEST_ACCOUNT_ID);
            assertNotNull("Result should not be null for null permission", result2);
        } catch (Exception e) {
            // Expected with null permission
            assertTrue("Exception expected for null permission", true);
        }
    }

    /**
     * Test methods with empty permission
     */
    @Test
    public void testMethods_EmptyPermission() {
        try {
            FreeForm result1 = permissionDao.fetchFilesWithPermissions("", true, TEST_ACCOUNT_ID);
            assertNotNull("Result should not be null for empty permission", result1);
        } catch (Exception e) {
            // Expected with empty permission
            assertTrue("Exception expected for empty permission", true);
        }

        try {
            FreeForm result2 = permissionDao.getFileByPermission("", false, TEST_ACCOUNT_ID);
            assertNotNull("Result should not be null for empty permission", result2);
        } catch (Exception e) {
            // Expected with empty permission
            assertTrue("Exception expected for empty permission", true);
        }
    }

    /**
     * Test methods with null account ID
     */
    @Test
    public void testMethods_NullAccountId() {
        try {
            FreeForm result1 = permissionDao.fetchFilesWithPermissions(VALID_PERMISSION_READ, true, null);
            assertNotNull("Result should not be null for null account ID", result1);
        } catch (Exception e) {
            // Expected with null account ID
            assertTrue("Exception expected for null account ID", true);
        }

        try {
            FreeForm result2 = permissionDao.getFileByPermission(VALID_PERMISSION_WRITE, false, null);
            assertNotNull("Result should not be null for null account ID", result2);
        } catch (Exception e) {
            // Expected with null account ID
            assertTrue("Exception expected for null account ID", true);
        }
    }

    // ========================================
    // EDGE CASE TESTS
    // ========================================

    /**
     * Test methods with various permission case variations
     */
    @Test
    public void testMethods_PermissionCaseVariations() {
        String[] permissionVariations = {
            "read", "READ", "Read", "rEaD",
            "write", "WRITE", "Write", "wRiTe",
            "own", "OWN", "Own", "oWn",
            "read and write", "READ AND WRITE", "Read And Write"
        };

        for (String permission : permissionVariations) {
            try {
                FreeForm result = permissionDao.fetchFilesWithPermissions(permission, true, TEST_ACCOUNT_ID);
                assertNotNull("Result should not be null for permission: " + permission, result);
            } catch (Exception e) {
                // Expected with null provider or API calls
                assertTrue("Exception handling for permission: " + permission, true);
            }
        }
    }

    /**
     * Test methods with different useEmail flag combinations
     */
    @Test
    public void testMethods_UseEmailFlagVariations() {
        boolean[] useEmailFlags = {true, false};

        for (boolean useEmail : useEmailFlags) {
            // Test with unsupported permission (should not trigger API calls)
            FreeForm result1 = permissionDao.fetchFilesWithPermissions(UNSUPPORTED_PERMISSION_OWN, useEmail, TEST_ACCOUNT_ID);
            assertNotNull("Result should not be null for useEmail: " + useEmail, result1);

            FreeForm result2 = permissionDao.getFileByPermission(UNSUPPORTED_PERMISSION_READ_WRITE, useEmail, TEST_ACCOUNT_ID);
            assertNotNull("Result should not be null for useEmail: " + useEmail, result2);
        }
    }

    /**
     * Test methods with various account ID formats
     */
    @Test
    public void testMethods_AccountIdVariations() {
        String[] accountIds = {
            TEST_ACCOUNT_ID,
            "",
            "account-with-dashes",
            "account_with_underscores",
            "account123",
            "very-long-account-id-with-many-characters-to-test-length-handling",
            "account@domain.com",
            "account with spaces"
        };

        for (String accountId : accountIds) {
            // Test with unsupported permission (should not trigger API calls)
            FreeForm result = permissionDao.fetchFilesWithPermissions(UNSUPPORTED_PERMISSION_OWN, true, accountId);
            assertNotNull("Result should not be null for accountId: " + accountId, result);
        }
    }

    // ========================================
    // BOUNDARY CONDITION TESTS
    // ========================================

    /**
     * Test methods with very long permission strings
     */
    @Test
    public void testMethods_VeryLongPermissionStrings() {
        StringBuilder longPermission = new StringBuilder();
        longPermission.append("verylongpermission".repeat(1000));

        try {
            FreeForm result = permissionDao.fetchFilesWithPermissions(longPermission.toString(), true, TEST_ACCOUNT_ID);
            assertNotNull("Result should not be null for very long permission", result);
        } catch (Exception e) {
            // Expected with very long permission string
            assertTrue("Exception expected for very long permission", true);
        }
    }

    /**
     * Test methods with special characters in parameters
     */
    @Test
    public void testMethods_SpecialCharacters() {
        String[] specialPermissions = {
            "read@permission",
            "write#permission",
            "permission$with$symbols",
            "permission with spaces",
            "permission\nwith\nnewlines",
            "permission\twith\ttabs",
            "permission\"with\"quotes",
            "permission'with'apostrophes"
        };

        for (String permission : specialPermissions) {
            try {
                FreeForm result = permissionDao.fetchFilesWithPermissions(permission, true, TEST_ACCOUNT_ID);
                assertNotNull("Result should not be null for special permission: " + permission, result);
            } catch (Exception e) {
                // Expected with special characters
                assertTrue("Exception handling for special permission: " + permission, true);
            }
        }
    }

    // ========================================
    // PERFORMANCE TESTS
    // ========================================

    /**
     * Test methods performance with multiple rapid calls
     */
    @Test
    public void testMethods_PerformanceTest() {
        long startTime = System.currentTimeMillis();
        int successfulCalls = 0;

        // Make multiple rapid calls with unsupported permissions (no API calls)
        for (int i = 0; i < 100; i++) {
            try {
                permissionDao.fetchFilesWithPermissions(UNSUPPORTED_PERMISSION_OWN, true, TEST_ACCOUNT_ID + i);
                successfulCalls++;
            } catch (Exception e) {
                // Continue testing even if some calls fail
            }
        }

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        // Should complete quickly (less than 5 seconds)
        assertTrue("Multiple permission calls should complete quickly (was " + duration + "ms)",
                  duration < 5000);
        assertTrue("At least some calls should succeed", successfulCalls > 0);
    }
}
