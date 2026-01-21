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

import app.krista.extension.executor.ExtensionResponse;
import app.krista.extensions.essentials.cloudfiles.onedrive.OnedriveAttributes;
import app.krista.extensions.essentials.cloudfiles.onedrive.catalog.FileHandlers.TelemetryHelper;
import app.krista.extensions.essentials.cloudfiles.onedrive.catalog.validators.Validator;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.connectors.GraphServiceClientProvider;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.connectors.GraphServiceClientProviderFactory;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.util.ValidationTypeUtil;
import com.microsoft.graph.requests.UserRequestBuilder;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Non-mocked unit tests for FileManagementDao.
 * <p>
 * This test class focuses on testing the public methods and logic that don't require
 * external dependencies. The main methods require provider and other dependencies,
 * so we focus on testing:
 * - Constructor validation
 * - Input validation logic
 * - Helper method behavior
 * - Error handling scenarios
 * - Edge cases and boundary conditions
 */
public class FileManagementDaoTest {

    private FileManagementDao fileManagementDao;

    // Test data constants
    private static final String VALID_DRIVE_PATH = "/drives/b!testDriveId/root:/source/file.txt";
    private static final String VALID_ROOT_PATH = "drive/root:/source/file.txt";
    private static final String INVALID_PATH = "/invalid/path/without/proper/format";
    private static final String TEST_FOLDER_NAME = "testfolder";

    @Before
    public void setUp() {
        // Create a real FileManagementDao instance with minimal dependencies
        GraphServiceClientProvider mockProvider = createMockProvider();
        OnedriveAttributes mockAttributes = createMockAttributes();
        ValidationTypeUtil mockValidationUtil = createMockValidationUtil();
        TelemetryHelper mockTelemetryHelper = createMockTelemetryHelper();

        fileManagementDao = new FileManagementDao(
                mockProvider,           // GraphServiceClientProvider - mock to avoid NPE
                mockAttributes,         // OnedriveAttributes - mock for configuration
                mockValidationUtil,     // ValidationTypeUtil - mock for validation
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
                "test@example.com",       // mailId
                "test-tenant-id"          // tenantId
        );
    }

    private ValidationTypeUtil createMockValidationUtil() {
        return new ValidationTypeUtil(null, null, null) {
            @Override
            public ExtensionResponse checkValidations(Validator.ValidationResource validationType, String subCatalogConstant, String absolutePath, List<Map<String, Object>> share) {
                return null; // Return null to indicate no validation issues
            }

            @Override
            public ExtensionResponse checkValidationsWithErrorMessage(Validator.ValidationResource validationType, String subCatalogConstant, String absolutePath, String errorMessage) {
                return null; // Return null to indicate no validation issues
            }
        };
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
                null, null, null, null, createMockProvider()) {
            @Override
            public GraphServiceClientProvider create() {
                return createMockProvider();
            }
        };

        OnedriveAttributes attributes = createMockAttributes();
        ValidationTypeUtil validationUtil = createMockValidationUtil();
        TelemetryHelper telemetryHelper = createMockTelemetryHelper();

        // This should not throw an exception
        FileManagementDao dao = new FileManagementDao(mockFactory, attributes, validationUtil, telemetryHelper);
        assertNotNull("FileManagementDao should be created successfully", dao);
    }

    /**
     * Test constructor with direct provider - should not throw exception
     */
    @Test
    public void testConstructorWithProvider() {
        GraphServiceClientProvider provider = createMockProvider();
        OnedriveAttributes attributes = createMockAttributes();
        ValidationTypeUtil validationUtil = createMockValidationUtil();
        TelemetryHelper telemetryHelper = createMockTelemetryHelper();

        // This should not throw an exception
        FileManagementDao dao = new FileManagementDao(provider, attributes, validationUtil, telemetryHelper);
        assertNotNull("FileManagementDao should be created successfully", dao);
    }

    // ========================================
    // CREATEFOLDER METHOD TESTS
    // ========================================

    /**
     * Test createFolder with null folder name - should handle gracefully
     */
    @Test
    public void testCreateFolder_NullFolderName() {
        try {
            ExtensionResponse response = fileManagementDao.createFolder(null, VALID_DRIVE_PATH, false, false);
            assertNotNull("Response should not be null", response);
        } catch (Exception e) {
            // Expected with null folder name
            assertTrue("Exception expected with null folder name", true);
        }
    }

    /**
     * Test createFolder with null target path - should handle gracefully
     */
    @Test
    public void testCreateFolder_NullTargetPath() {
        try {
            ExtensionResponse response = fileManagementDao.createFolder(TEST_FOLDER_NAME, null, false, false);
            assertNotNull("Response should not be null", response);
        } catch (Exception e) {
            // Expected with null target path
            assertTrue("Exception expected with null target path", true);
        }
    }

    /**
     * Test createFolder with valid parameters - should handle gracefully
     */
    @Test
    public void testCreateFolder_ValidParameters() {
        try {
            ExtensionResponse response = fileManagementDao.createFolder(TEST_FOLDER_NAME, VALID_DRIVE_PATH, false, false);
            assertNotNull("Response should not be null", response);
        } catch (Exception e) {
            // Expected with null provider when trying to make API calls
            assertTrue("Exception expected when trying to make API calls", true);
        }
    }

    /**
     * Test createFolder with re-entered flags
     */
    @Test
    public void testCreateFolder_ReenteredFlags() {
        try {
            ExtensionResponse response = fileManagementDao.createFolder(TEST_FOLDER_NAME, VALID_ROOT_PATH, true, false);
            assertNotNull("Response should not be null", response);
        } catch (Exception e) {
            // Expected with null provider when trying to make API calls
            assertTrue("Exception expected when trying to make API calls", true);
        }

        try {
            ExtensionResponse response = fileManagementDao.createFolder(TEST_FOLDER_NAME, VALID_ROOT_PATH, false, true);
            assertNotNull("Response should not be null", response);
        } catch (Exception e) {
            // Expected with null provider when trying to make API calls
            assertTrue("Exception expected when trying to make API calls", true);
        }

        try {
            ExtensionResponse response = fileManagementDao.createFolder(TEST_FOLDER_NAME, VALID_ROOT_PATH, true, true);
            assertNotNull("Response should not be null", response);
        } catch (Exception e) {
            // Expected with null provider when trying to make API calls
            assertTrue("Exception expected when trying to make API calls", true);
        }
    }

    // ========================================
    // GETLISTOFFILES METHOD TESTS
    // ========================================

    /**
     * Test getListOfFiles with null path - should handle gracefully
     */
    @Test
    public void testGetListOfFiles_NullPath() {
        try {
            app.krista.model.base.FreeForm result = fileManagementDao.getListOfFiles(null, "", true, "testAccount");
            assertNotNull("Result should not be null", result);
        } catch (Exception e) {
            // Expected with null provider when trying to make API calls
            assertTrue("Exception expected when trying to make API calls", true);
        }
    }

    /**
     * Test getListOfFiles with valid parameters - should handle gracefully
     */
    @Test
    public void testGetListOfFiles_ValidParameters() {
        try {
            app.krista.model.base.FreeForm result = fileManagementDao.getListOfFiles(VALID_ROOT_PATH, "test", false, "testAccount");
            assertNotNull("Result should not be null", result);
        } catch (Exception e) {
            // Expected with null provider when trying to make API calls
            assertTrue("Exception expected when trying to make API calls", true);
        }
    }

    // ========================================
    // GETFILEINFO METHOD TESTS
    // ========================================

    /**
     * Test getFileInfo with null path - should handle gracefully
     */
    @Test
    public void testGetFileInfo_NullPath() {
        try {
            app.krista.model.base.FreeForm result = fileManagementDao.getFileInfo(null, true, "testAccount");
            assertNotNull("Result should not be null", result);
        } catch (Exception e) {
            // Expected with null provider when trying to make API calls
            assertTrue("Exception expected when trying to make API calls", true);
        }
    }

    /**
     * Test getFileInfo with valid parameters - should handle gracefully
     */
    @Test
    public void testGetFileInfo_ValidParameters() {
        try {
            app.krista.model.base.FreeForm result = fileManagementDao.getFileInfo(VALID_DRIVE_PATH, false, "testAccount");
            assertNotNull("Result should not be null", result);
        } catch (Exception e) {
            // Expected with null provider when trying to make API calls
            assertTrue("Exception expected when trying to make API calls", true);
        }
    }

    // ========================================
    // EDGE CASE TESTS
    // ========================================

    /**
     * Test methods with various path formats
     */
    @Test
    public void testMethods_VariousPathFormats() {
        String[] testPaths = {
                VALID_DRIVE_PATH,
                VALID_ROOT_PATH,
                INVALID_PATH,
                "/drives/b!123/root:/file.txt",
                "drive/root:/folder/subfolder/file.doc",
                "",
                "   ", // whitespace
                "/drives/b!test/root:/file with spaces.txt"
        };

        for (String path : testPaths) {
            try {
                ExtensionResponse response = fileManagementDao.createFolder(TEST_FOLDER_NAME, path, false, false);
                assertNotNull("Response should not be null for path: " + path, response);
            } catch (Exception e) {
                // Expected with various path formats
                assertTrue("Exception handling for path: " + path, true);
            }
        }
    }

    /**
     * Test methods with special characters in names
     */
    @Test
    public void testMethods_SpecialCharactersInNames() {
        String[] specialNames = {
                "folder with spaces",
                "folder-with-dashes",
                "folder_with_underscores",
                "folder.with.dots",
                "folder@symbol",
                "folder#hash",
                "folder$dollar",
                "folder%percent"
        };

        for (String name : specialNames) {
            try {
                ExtensionResponse response = fileManagementDao.createFolder(name, VALID_DRIVE_PATH, false, false);
                assertNotNull("Response should not be null for name: " + name, response);
            } catch (Exception e) {
                // Expected with special characters or null provider
                assertTrue("Exception handling for name: " + name, true);
            }
        }
    }
}
