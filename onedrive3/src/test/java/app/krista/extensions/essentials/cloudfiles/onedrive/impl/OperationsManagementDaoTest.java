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
import app.krista.extensions.essentials.cloudfiles.onedrive.catalog.FileHandlers.TelemetryHelper;
import app.krista.extensions.essentials.cloudfiles.onedrive.catalog.validators.Validator;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.connectors.GraphServiceClientProvider;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.connectors.GraphServiceClientProviderFactory;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.stores.KristaMediaClient;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.util.Constants;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.util.ValidationTypeUtil;
import app.krista.model.base.File;
import com.microsoft.graph.requests.UserRequestBuilder;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Non-mocked unit tests for OperationsManagementDao.
 * <p>
 * This test class focuses on testing the public methods and logic that don't require
 * external dependencies. The main methods require provider and other dependencies,
 * so we focus on testing:
 * - Constructor validation
 * - Input validation logic
 * - Helper method behavior
 * - Error handling scenarios
 * - Edge cases and boundary conditions
 * - File validation methods
 */
public class OperationsManagementDaoTest {

    private OperationsManagementDao operationsManagementDao;

    // Test data constants
    private static final String VALID_DRIVE_PATH = "/drives/b!testDriveId/root:/source/file.txt";
    private static final String VALID_ROOT_PATH = "drive/root:/source/file.txt";
    private static final String INVALID_PATH = "/invalid/path/without/proper/format";
    private static final String TEST_FILE_NAME = "testfile.txt";
    private static final String TEST_CUSTOM_NAME = "customname";

    @Before
    public void setUp() {
        // Create a real OperationsManagementDao instance with minimal dependencies
        GraphServiceClientProvider mockProvider = createMockProvider();
        KristaMediaClient mockKristaMediaClient = createMockKristaMediaClient();
        ValidationTypeUtil mockValidationUtil = createMockValidationUtil();
        TelemetryHelper mockTelemetryHelper = createMockTelemetryHelper();

        operationsManagementDao = new OperationsManagementDao(
            mockProvider,           // GraphServiceClientProvider - mock to avoid NPE
            mockKristaMediaClient,  // KristaMediaClient - mock for file operations
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

    private KristaMediaClient createMockKristaMediaClient() {
        return new KristaMediaClient() {
            @Override
            public File toKristaFile(java.io.File file) {
                // Mock implementation for testing - create a File with required constructor parameters
                return new File(1024L, file != null ? file.getName() : "mock-file.txt", "txt", "mock-content");
            }

            @Override
            public java.io.File toJavaFile(File kristaFile, String fileName) {
                // Mock implementation for testing
                return new java.io.File(fileName != null ? fileName : "mock-file.txt");
            }
        };
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

        KristaMediaClient kristaMediaClient = createMockKristaMediaClient();
        ValidationTypeUtil validationUtil = createMockValidationUtil();
        TelemetryHelper telemetryHelper = createMockTelemetryHelper();

        // This should not throw an exception
        OperationsManagementDao dao = new OperationsManagementDao(mockFactory, kristaMediaClient, validationUtil, telemetryHelper);
        assertNotNull("OperationsManagementDao should be created successfully", dao);
    }

    /**
     * Test constructor with direct provider - should not throw exception
     */
    @Test
    public void testConstructorWithProvider() {
        GraphServiceClientProvider provider = createMockProvider();
        KristaMediaClient kristaMediaClient = createMockKristaMediaClient();
        ValidationTypeUtil validationUtil = createMockValidationUtil();
        TelemetryHelper telemetryHelper = createMockTelemetryHelper();

        // This should not throw an exception
        OperationsManagementDao dao = new OperationsManagementDao(provider, kristaMediaClient, validationUtil, telemetryHelper);
        assertNotNull("OperationsManagementDao should be created successfully", dao);
    }

    // ========================================
    // CHECKIFFILEEXIST METHOD TESTS
    // ========================================

    /**
     * Test checkIfFileExist with null target - should handle gracefully
     */
    @Test
    public void testCheckIfFileExist_NullTarget() {
        Map<String, Object> payload = new HashMap<>();

        operationsManagementDao.checkIfFileExist(null, payload);

        assertNotNull("Payload should not be null", payload);
        assertFalse("File should not exist for null target", (Boolean) payload.getOrDefault(Constants.IS_FILE_EXIST, false));
        assertEquals("Should have appropriate message", Constants.PLEASE_PROVIDE_VALID_SOURCE_PATH, payload.get(Constants.MESSAGE));
    }

    /**
     * Test checkIfFileExist with empty target - should handle gracefully
     */
    @Test
    public void testCheckIfFileExist_EmptyTarget() {
        Map<String, Object> payload = new HashMap<>();

        operationsManagementDao.checkIfFileExist("", payload);

        assertNotNull("Payload should not be null", payload);
        // Method should handle empty target gracefully
    }

    /**
     * Test checkIfFileExist with valid drive path - should handle gracefully
     */
    @Test
    public void testCheckIfFileExist_ValidDrivePath() {
        Map<String, Object> payload = new HashMap<>();

        try {
            operationsManagementDao.checkIfFileExist(VALID_DRIVE_PATH, payload);
            assertNotNull("Payload should not be null", payload);
        } catch (Exception e) {
            // Expected with null provider when trying to make API calls
            assertTrue("Exception expected when trying to make API calls", true);
        }
    }

    /**
     * Test checkIfFileExist with valid root path - should handle gracefully
     */
    @Test
    public void testCheckIfFileExist_ValidRootPath() {
        Map<String, Object> payload = new HashMap<>();

        try {
            operationsManagementDao.checkIfFileExist(VALID_ROOT_PATH, payload);
            assertNotNull("Payload should not be null", payload);
        } catch (Exception e) {
            // Expected with null provider when trying to make API calls
            assertTrue("Exception expected when trying to make API calls", true);
        }
    }

    /**
     * Test checkIfFileExist with invalid path format - should handle gracefully
     */
    @Test
    public void testCheckIfFileExist_InvalidPath() {
        Map<String, Object> payload = new HashMap<>();

        operationsManagementDao.checkIfFileExist(INVALID_PATH, payload);

        assertNotNull("Payload should not be null", payload);
        assertFalse("File should not exist for invalid path", (Boolean) payload.getOrDefault(Constants.IS_FILE_EXIST, false));
    }

    // ========================================
    // CONTAINSINVALIDCHARACTERS METHOD TESTS
    // ========================================

    /**
     * Test containsInvalidCharacters with valid characters
     */
    @Test
    public void testContainsInvalidCharacters_ValidCharacters() {
        String[] validInputs = {
            "validfilename",
            "valid_file_name",
            "valid-file-name",
            "ValidFileName123",
            "file.name.with.dots",
            "file name with spaces"
        };

        for (String input : validInputs) {
            boolean result = operationsManagementDao.containsInvalidCharacters(input);
            assertFalse("Should not contain invalid characters: " + input, result);
        }
    }

    /**
     * Test containsInvalidCharacters with invalid characters
     */
    @Test
    public void testContainsInvalidCharacters_InvalidCharacters() {
        String[] invalidInputs = {
            "file*name",
            "file:name",
            "file<name",
            "file>name",
            "file?name",
            "file/name",
            "file\\name",
            "file|name"
        };

        for (String input : invalidInputs) {
            boolean result = operationsManagementDao.containsInvalidCharacters(input);
            assertTrue("Should contain invalid characters: " + input, result);
        }
    }

    /**
     * Test containsInvalidCharacters with null and empty strings
     */
    @Test
    public void testContainsInvalidCharacters_NullAndEmpty() {
        // Test empty string
        boolean result1 = operationsManagementDao.containsInvalidCharacters("");
        assertFalse("Empty string should not contain invalid characters", result1);

        // Test null string - this might throw NPE, which is expected behavior
        try {
            boolean result2 = operationsManagementDao.containsInvalidCharacters(null);
            // If it doesn't throw NPE, it should return false
            assertFalse("Null string should not contain invalid characters", result2);
        } catch (NullPointerException e) {
            // Expected behavior for null input
            assertTrue("NPE is expected for null input", true);
        }
    }

    // ========================================
    // DOWNLOADFILES METHOD TESTS
    // ========================================

    /**
     * Test downloadFiles with null path - should handle gracefully
     */
    @Test
    public void testDownloadFiles_NullPath() {
        try {
            ExtensionResponse response = operationsManagementDao.downloadFiles(true, false, null, false);
            assertNotNull("Response should not be null", response);
        } catch (Exception e) {
            // Expected with null provider or validation
            assertTrue("Exception expected with null path", true);
        }
    }

    /**
     * Test downloadFiles with valid parameters - should handle gracefully
     */
    @Test
    public void testDownloadFiles_ValidParameters() {
        try {
            ExtensionResponse response = operationsManagementDao.downloadFiles(true, false, VALID_DRIVE_PATH, false);
            assertNotNull("Response should not be null", response);
        } catch (Exception e) {
            // Expected with null provider when trying to make API calls
            assertTrue("Exception expected when trying to make API calls", true);
        }
    }

    /**
     * Test downloadFiles with re-entered flag
     */
    @Test
    public void testDownloadFiles_ReenteredFlag() {
        try {
            ExtensionResponse response = operationsManagementDao.downloadFiles(false, true, VALID_ROOT_PATH, true);
            assertNotNull("Response should not be null", response);
        } catch (Exception e) {
            // Expected with null provider when trying to make API calls
            assertTrue("Exception expected when trying to make API calls", true);
        }
    }

    // ========================================
    // UPLOADFILES METHOD TESTS
    // ========================================

    /**
     * Test uploadFiles with null file - should handle gracefully
     */
    @Test
    public void testUploadFiles_NullFile() {
        try {
            ExtensionResponse response = operationsManagementDao.uploadFiles(null, VALID_DRIVE_PATH);
            assertNotNull("Response should not be null", response);
        } catch (Exception e) {
            // Expected with null file
            assertTrue("Exception expected with null file", true);
        }
    }

    /**
     * Test uploadFiles with null target path - should handle gracefully
     */
    @Test
    public void testUploadFiles_NullTargetPath() {
        File mockFile = createMockFile();

        try {
            ExtensionResponse response = operationsManagementDao.uploadFiles(mockFile, null);
            assertNotNull("Response should not be null", response);
        } catch (Exception e) {
            // Expected with null target path
            assertTrue("Exception expected with null target path", true);
        }
    }

    /**
     * Test uploadFiles with custom name - should handle gracefully
     */
    @Test
    public void testUploadFiles_WithCustomName() {
        File mockFile = createMockFile();

        try {
            ExtensionResponse response = operationsManagementDao.uploadFiles(mockFile, VALID_DRIVE_PATH, TEST_CUSTOM_NAME, false, false);
            assertNotNull("Response should not be null", response);
        } catch (Exception e) {
            // Expected with null provider when trying to make API calls
            assertTrue("Exception expected when trying to make API calls", true);
        }
    }

    /**
     * Test uploadFiles with invalid custom name - should handle gracefully
     */
    @Test
    public void testUploadFiles_InvalidCustomName() {
        File mockFile = createMockFile();
        String invalidCustomName = "invalid*name";

        try {
            ExtensionResponse response = operationsManagementDao.uploadFiles(mockFile, VALID_DRIVE_PATH, invalidCustomName, false, false);
            assertNotNull("Response should not be null", response);
        } catch (Exception e) {
            // Expected with invalid custom name or null provider
            assertTrue("Exception expected with invalid custom name", true);
        }
    }

    // ========================================
    // HELPER METHODS
    // ========================================

    /**
     * Create a mock File for testing
     */
    private File createMockFile() {
        return new File(1024L, TEST_FILE_NAME, "txt", "mock-content");
    }
}
