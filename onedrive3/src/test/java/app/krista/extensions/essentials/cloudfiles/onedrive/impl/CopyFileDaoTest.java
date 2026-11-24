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
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.connectors.GraphServiceClientProvider;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.util.Constants;
import okhttp3.RequestBody;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static org.junit.Assert.*;

/**
 * Non-mocked unit tests for CopyFileDao helper methods.
 * <p>
 * This test class focuses on testing the public helper methods and logic that don't require
 * external dependencies. The main copyFile and moveFile methods require operationsManagementDao
 * and other dependencies, so we focus on testing:
 * - containsInvalidCharacters method
 * - createRequestBody method
 * - checkIfTargetFileExist method
 * - Input validation logic
 * - String handling and special characters
 * - JSON generation and formatting
 * - Boundary conditions for helper methods
 * - Dependency limitation demonstrations
 */
public class CopyFileDaoTest {

    private CopyFileDao copyFileDao;

    // Test data constants
    private static final String VALID_SOURCE_PATH = "/drives/b!testDriveId/root:/source/file.txt";
    private static final String VALID_TARGET_PATH = "/drives/b!testDriveId/root:/target/folder";
    private static final String UNICODE_PATH = "/drives/b!testDriveId/root:/ソース/ファイル.txt";

    @Before
    public void setUp() {
        // Create a real CopyFileDao instance with minimal telemetry dependency
        // This allows us to test the core logic without external dependencies
        GraphServiceClientProvider nullProvider = null;

        // Create a minimal telemetry helper that doesn't throw exceptions
        TelemetryHelper minimalTelemetryHelper = new TelemetryHelper(null) {
            @Override
            public void incrementCount(String operation) {
                // Do nothing - skip telemetry for testing
            }

            @Override
            public void recordSuccess(String operation, long startTime, java.util.Map<String, String> tags) {
                // Do nothing - skip telemetry for testing
            }

            @Override
            public void recordError(String operation, long startTime, Exception error, java.util.Map<String, String> tags) {
                // Do nothing - skip telemetry for testing
            }

            @Override
            public void recordValidationError(String operation, long startTime, String errorType, java.util.Map<String, String> tags) {
                // Do nothing - skip telemetry for testing
            }

            @Override
            public void recordRetryPrompted(String operation, long startTime, java.util.Map<String, String> tags) {
                // Do nothing - skip telemetry for testing
            }
        };

        copyFileDao = new CopyFileDao(
            nullProvider,           // GraphServiceClientProvider - not needed for logic tests
                null,      // OperationsManagementDao - not needed for logic tests
                null,         // OnedriveAttributes - not needed for logic tests
                null,     // ValidationTypeUtil - not needed for logic tests
            minimalTelemetryHelper  // Minimal telemetry helper to avoid NPE
        );
    }

    // ========================================
    // HELPER METHOD TESTS - NO DEPENDENCIES REQUIRED
    // ========================================

    /**
     * Test containsInvalidCharacters method with valid input
     */
    @Test
    public void testContainsInvalidCharacters_ValidInput() {
        // Test valid folder names
        String[] validInputs = {
            "ValidFolderName",
            "folder123",
            "folder_name",
            "folder-name",
            "folder.name",
            "folder name", // spaces are allowed
            "ソースフォルダ", // Unicode characters
            "folder123ABC",
            "a", // single character
            "" // empty string
        };

        for (String validInput : validInputs) {
            boolean result = copyFileDao.containsInvalidCharacters(validInput);
            assertFalse("Input '" + validInput + "' should be valid", result);
        }
    }

    /**
     * Test containsInvalidCharacters method with invalid characters
     */
    @Test
    public void testContainsInvalidCharacters_InvalidCharacters() {
        // Test each invalid character individually
        String[] invalidChars = {"*", ":", "<", ">", "?", "/", "\\", "|"};

        for (String invalidChar : invalidChars) {
            String invalidInput = "folder" + invalidChar + "name";
            boolean result = copyFileDao.containsInvalidCharacters(invalidInput);
            assertTrue("Input with '" + invalidChar + "' should be invalid", result);
        }
    }

    /**
     * Test containsInvalidCharacters method with multiple invalid characters
     */
    @Test
    public void testContainsInvalidCharacters_MultipleInvalidCharacters() {
        String[] invalidInputs = {
            "folder*name:test",
            "folder<>name",
            "folder?/name",
            "folder\\|name",
            "***invalid***",
            "::invalid::",
            "<<<invalid>>>",
                "?"
        };

        for (String invalidInput : invalidInputs) {
            boolean result = copyFileDao.containsInvalidCharacters(invalidInput);
            assertTrue("Input '" + invalidInput + "' should be invalid", result);
        }
    }

    /**
     * Test containsInvalidCharacters method with null input
     */
    @Test
    public void testContainsInvalidCharacters_NullInput() {
        try {
            copyFileDao.containsInvalidCharacters(null);
            fail("Should throw NullPointerException for null input");
        } catch (NullPointerException e) {
            // Expected behavior
            assertNotNull("Exception should be thrown for null input", e);
        }
    }

    /**
     * Test createRequestBody method with valid folder ID
     */
    @Test
    public void testCreateRequestBody_ValidFolderId() throws IOException {
        String[] validFolderIds = {
            "test-folder-id-123",
            "folder_id_with_underscores",
            "folder-id-with-dashes",
            "FolderIdWithMixedCase",
            "123456789",
            "a", // single character
            "very_long_folder_id_with_many_characters_to_test_length_handling"
        };

        for (String folderId : validFolderIds) {
            RequestBody requestBody = copyFileDao.createRequestBody(folderId);

            assertNotNull("Request body should not be null for folder ID: " + folderId, requestBody);
            assertNotNull("Content type should not be null", requestBody.contentType());
            assertTrue("Content type should be JSON",
                      Objects.requireNonNull(requestBody.contentType()).toString().startsWith("application/json"));
            assertTrue("Request body should have reasonable content length",
                      requestBody.contentLength() > 10);
        }
    }

    /**
     * Test createRequestBody method with edge case folder IDs
     */
    @Test
    public void testCreateRequestBody_EdgeCaseFolderIds() {
        String[] edgeCaseFolderIds = {
            "", // empty string
            null, // null value
            "folder-id_with@special#chars!",
            "folder id with spaces",
            "フォルダID", // Unicode characters
            "folder\nid\nwith\nnewlines",
            "folder\tid\twith\ttabs"
        };

        for (String folderId : edgeCaseFolderIds) {
            RequestBody requestBody = copyFileDao.createRequestBody(folderId);

            assertNotNull("Request body should not be null for folder ID: " + folderId, requestBody);
            assertNotNull("Content type should not be null", requestBody.contentType());
            assertTrue(Objects.requireNonNull(requestBody.contentType()).toString().startsWith("application/json"));
        }
    }

    /**
     * Test checkIfTargetFileExist method with various path formats
     */
    @Test
    public void testCheckIfTargetFileExist_VariousPathFormats() {
        String[] testPaths = {
            null,
            "",
            "/invalid/path",
            "/drives/root/invalid",
            VALID_TARGET_PATH,
            UNICODE_PATH,
            "/drives/b!testDriveId/root:/path with spaces",
            "/drives/b!testDriveId/root:/very/deep/nested/folder/structure"
        };

        for (String testPath : testPaths) {
            Map<String, Object> response = new HashMap<>();

            // This should not throw an exception
            copyFileDao.checkIfTargetFileExist(testPath, response);

            // Verify response structure
            assertNotNull("Response should not be null for path: " + testPath, response);
            assertTrue("Response should contain IS_FILE_EXIST key",
                      response.containsKey(Constants.IS_FILE_EXIST));

            // For null and invalid paths, should indicate file doesn't exist
            if (testPath == null || !testPath.contains("/drives/")) {
                assertEquals("Should indicate file does not exist for invalid path: " + testPath,
                            false, response.get(Constants.IS_FILE_EXIST));
                assertTrue("Should contain error message",
                          response.containsKey(Constants.MESSAGE));
            }
        }
    }

    // ========================================
    // STRING HANDLING AND SPECIAL CHARACTERS TESTS FOR HELPER METHODS
    // ========================================

    /**
     * Test containsInvalidCharacters method with boundary characters
     */
    @Test
    public void testContainsInvalidCharacters_BoundaryCharacters() {
        // Test characters around the invalid ones in ASCII table
        String[] boundaryValidChars = {
            "folder)name", // ) is ASCII 41, * is ASCII 42
            "folder+name", // + is ASCII 43
            "folder-name", // - is ASCII 45, / is ASCII 47
            "folder9name", // 9 is ASCII 57, : is ASCII 58
            "folder;name", // ; is ASCII 59
            "folder=name", // = is ASCII 61, > is ASCII 62
            "folder@name", // @ is ASCII 64
            "folder[name", // [ is ASCII 91, \ is ASCII 92
            "folder]name", // ] is ASCII 93
            "folder^name", // ^ is ASCII 94
            "folder`name", // ` is ASCII 96
            "folder{name", // { is ASCII 123, | is ASCII 124
            "folder}name", // } is ASCII 125
            "folder~name"  // ~ is ASCII 126
        };

        for (String validChar : boundaryValidChars) {
            boolean result = copyFileDao.containsInvalidCharacters(validChar);
            assertFalse("Character in '" + validChar + "' should be valid", result);
        }
    }

    /**
     * Test createRequestBody method with JSON-breaking characters
     */
    @Test
    public void testCreateRequestBody_JsonBreakingCharacters() throws IOException {
        String[] jsonBreakingIds = {
            "folder\"id", // double quote
            "folder'id", // single quote
            "folder\\id", // backslash
            "folder\nid", // newline
            "folder\tid", // tab
            "folder\rid", // carriage return
            "folder{id}", // curly braces
            "folder[id]", // square brackets
            "folder,id", // comma
            "folder:id"  // colon
        };

        for (String folderId : jsonBreakingIds) {
            RequestBody requestBody = copyFileDao.createRequestBody(folderId);

            assertNotNull("Request body should not be null for JSON-breaking ID: " + folderId, requestBody);
            assertNotNull("Content type should not be null", requestBody.contentType());
            assertTrue(Objects.requireNonNull(requestBody.contentType()).toString().startsWith("application/json"));

            // Verify the request body has reasonable content length
            assertTrue("Request body should have reasonable content length for ID: " + folderId,
                      requestBody.contentLength() > 5);
        }
    }

    /**
     * Test checkIfTargetFileExist method with malformed drive paths
     */
    @Test
    public void testCheckIfTargetFileExist_MalformedDrivePaths() {
        String[] malformedPaths = {
            "/drives/",
            "/drives/b!/root:",
            "/drives/b!testDriveId/",
            "/drives/b!testDriveId/root",
            "/drives/b!testDriveId/root/",
            "/drives/b!testDriveId/root::",
            "/drives/b!testDriveId/root:/",
            "/drives/b!testDriveId/root:///",
            "/drives/b!/root:/path",
            "/drives/testDriveId/root:/path", // missing b!
            "drives/b!testDriveId/root:/path", // missing leading slash
            "/drive/b!testDriveId/root:/path", // singular drive
            "/drives/b!testDriveId/roots:/path" // roots instead of root
        };

        for (String malformedPath : malformedPaths) {
            Map<String, Object> response = new HashMap<>();

            // Should not throw an exception
            copyFileDao.checkIfTargetFileExist(malformedPath, response);

            assertNotNull("Response should not be null for malformed path: " + malformedPath, response);
            assertTrue("Response should contain IS_FILE_EXIST key",
                      response.containsKey(Constants.IS_FILE_EXIST));
        }
    }

    /**
     * Test createRequestBody method with extremely long folder IDs
     */
    @Test
    public void testCreateRequestBody_ExtremelyLongFolderId() {
        // Create very long folder ID
        StringBuilder longIdBuilder = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            longIdBuilder.append("very_long_folder_id_segment_").append(i).append("_");
        }
        String longFolderId = longIdBuilder.toString();

        RequestBody requestBody = copyFileDao.createRequestBody(longFolderId);

        assertNotNull("Request body should not be null for extremely long folder ID", requestBody);
        assertNotNull("Content type should not be null", requestBody.contentType());
        assertTrue(Objects.requireNonNull(requestBody.contentType()).toString().startsWith("application/json"));
    }

    /**
     * Test createRequestBody method performance with multiple rapid calls
     */
    @Test
    public void testCreateRequestBody_PerformanceTest() {
        long startTime = System.currentTimeMillis();

        // Make multiple rapid calls
        for (int i = 0; i < 1000; i++) {
            String folderId = "test-folder-id-" + i;
            RequestBody requestBody = copyFileDao.createRequestBody(folderId);

            assertNotNull("Request body " + i + " should not be null", requestBody);
        }

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        // Should complete quickly (less than 5 seconds)
        assertTrue("Multiple createRequestBody calls should complete quickly (was " + duration + "ms)",
                  duration < 5000);
    }

    // ========================================
    // COMPREHENSIVE VALIDATION TESTS
    // ========================================

    /**
     * Test containsInvalidCharacters method with comprehensive character set
     */
    @Test
    public void testContainsInvalidCharacters_ComprehensiveCharacterSet() {
        // Test all printable ASCII characters to ensure only the specified ones are invalid
        for (int i = 32; i <= 126; i++) { // Printable ASCII range
            char c = (char) i;
            String testInput = "folder" + c + "name";
            boolean result = copyFileDao.containsInvalidCharacters(testInput);

            // Check if this character should be invalid
            boolean shouldBeInvalid = "*:<>?/\\|".indexOf(c) != -1;

            if (shouldBeInvalid) {
                assertTrue("Character '" + c + "' (ASCII " + i + ") should be invalid", result);
            } else {
                assertFalse("Character '" + c + "' (ASCII " + i + ") should be valid", result);
            }
        }
    }

    /**
     * Test containsInvalidCharacters method with edge case strings
     */
    @Test
    public void testContainsInvalidCharacters_EdgeCaseStrings() {
        String[] edgeCases = {
            "*", ":", "<", ">", "?", "/", "\\", "|", // Single invalid characters
            "***", ":::", "<<<", ">>>", "???", "///", "\\\\\\", "|||", // Multiple same invalid chars
            "valid", "VALID", "Valid123", "valid_name", "valid-name", "valid.name", // Valid strings
            "123", "abc", "ABC", "_", "-", ".", " ", // More valid strings
            "folder name with spaces", // Spaces should be valid
            "folder\tname", "folder\nname", // Tab and newline should be valid
            "very_long_folder_name_with_many_characters_to_test_length_handling_and_performance"
        };

        for (String testCase : edgeCases) {
            boolean result = copyFileDao.containsInvalidCharacters(testCase);
            boolean shouldBeInvalid = false;

            // Check if any invalid character is present
            for (char c : "*:<>?/\\|".toCharArray()) {
                if (testCase.indexOf(c) != -1) {
                    shouldBeInvalid = true;
                    break;
                }
            }

            assertEquals("Result for '" + testCase + "' should match expected", shouldBeInvalid, result);
        }
    }

    /**
     * Test createRequestBody method with comprehensive JSON structure validation
     */
    @Test
    public void testCreateRequestBody_JsonStructureValidation() throws IOException {
        String[] testFolderIds = {
            "simple-id",
            "id_with_underscores",
            "id-with-dashes",
            "IdWithMixedCase",
            "123456789",
            "id with spaces",
            "id\"with\"quotes",
            "id'with'single'quotes",
            "id\\with\\backslashes",
            "id\nwith\nnewlines",
            "id\twith\ttabs",
            "id{with}braces",
            "id[with]brackets",
            "id,with,commas",
            "id:with:colons",
            "フォルダID", // Unicode
            "", // Empty string
            null // Null value
        };

        for (String folderId : testFolderIds) {
            RequestBody requestBody = copyFileDao.createRequestBody(folderId);

            // Basic validations
            assertNotNull("Request body should not be null for folder ID: " + folderId, requestBody);
            assertNotNull("Content type should not be null", requestBody.contentType());
            assertTrue(Objects.requireNonNull(requestBody.contentType()).toString().startsWith("application/json"));

            // Content length should be reasonable
            long contentLength = requestBody.contentLength();
            assertTrue("Content length should be positive for folder ID: " + folderId,
                      contentLength > 0);
            assertTrue("Content length should be reasonable (less than 10KB) for folder ID: " + folderId,
                      contentLength < 10240);
        }
    }

    /**
     * Test checkIfTargetFileExist method with comprehensive path validation
     */
    @Test
    public void testCheckIfTargetFileExist_ComprehensivePathValidation() {
        String[] testPaths = {
            // Null and empty cases
            null,
            "",
            "   ", // Whitespace only

            // Invalid path formats
            "/invalid/path",
            "invalid/path",
            "/drives/",
            "/drives/invalid",
            "/drive/b!testDriveId/root:/path", // Missing 's' in drives

            // Valid path formats (structure-wise, may not exist)
            "/drives/b!testDriveId/root:",
            "/drives/b!testDriveId/root:/",
            "/drives/b!testDriveId/root:/folder",
            "/drives/b!testDriveId/root:/folder/subfolder",
            "/drives/b!testDriveId/root:/folder/file.txt",

            // Paths with special characters
            "/drives/b!testDriveId/root:/folder with spaces",
            "/drives/b!testDriveId/root:/folder%20with%20encoded%20spaces",
            "/drives/b!testDriveId/root:/ソース/ファイル.txt",
            "/drives/b!testDriveId/root:/مجلد/ملف.txt",
            "/drives/b!testDriveId/root:/папка/файл.txt",

            // Very long paths
            "/drives/b!testDriveId/root:" + "/very_long_folder_name".repeat(20),

            // Paths with various file extensions
            "/drives/b!testDriveId/root:/file.txt",
            "/drives/b!testDriveId/root:/file.pdf",
            "/drives/b!testDriveId/root:/file.docx",
            "/drives/b!testDriveId/root:/file.xlsx",
            "/drives/b!testDriveId/root:/file.pptx",
            "/drives/b!testDriveId/root:/file.jpg",
            "/drives/b!testDriveId/root:/file.png",
            "/drives/b!testDriveId/root:/file.mp4",
            "/drives/b!testDriveId/root:/file.zip"
        };

        for (String testPath : testPaths) {
            Map<String, Object> response = new HashMap<>();

            // Should not throw an exception
            try {
                copyFileDao.checkIfTargetFileExist(testPath, response);

                // Verify response structure
                assertNotNull("Response should not be null for path: " + testPath, response);
                assertTrue("Response should contain IS_FILE_EXIST key for path: " + testPath,
                          response.containsKey(Constants.IS_FILE_EXIST));

                // Verify IS_FILE_EXIST is a boolean
                Object isFileExist = response.get(Constants.IS_FILE_EXIST);
                assertTrue("IS_FILE_EXIST should be a boolean for path: " + testPath,
                          isFileExist instanceof Boolean);

                // For invalid paths, should indicate file doesn't exist
                if (testPath == null || testPath.trim().isEmpty() || !testPath.contains("/drives/")) {
                    assertEquals("Should indicate file does not exist for invalid path: " + testPath,
                                false, response.get(Constants.IS_FILE_EXIST));
                    assertTrue("Should contain error message for invalid path: " + testPath,
                              response.containsKey(Constants.MESSAGE));
                }

            } catch (Exception e) {
                fail("Should not throw exception for path: " + testPath + ". Exception: " + e.getMessage());
            }
        }
    }

    // ========================================
    // MOVE FILE METHOD TESTS
    // ========================================

    /**
     * Test moveFile method with various boolean flag combinations - all should fail due to dependencies
     */
    @Test
    public void testMoveFile_BooleanFlagCombinations() {
        boolean[] booleanValues = {true, false};

        for (boolean isSourceReentered : booleanValues) {
            for (boolean isTargetReentered : booleanValues) {
                try {
                    copyFileDao.moveFile(VALID_SOURCE_PATH, VALID_TARGET_PATH,
                                       isSourceReentered, isTargetReentered);
                    fail("Should throw NullPointerException because operationsManagementDao is null");
                } catch (NullPointerException e) {
                    assertTrue("Exception should mention operationsManagementDao for flags: isSourceReentered=" +
                             isSourceReentered + ", isTargetReentered=" + isTargetReentered,
                             e.getMessage().contains("operationsManagementDao"));
                }
            }
        }
    }

    /**
     * Test moveFile method with special characters in paths - all should fail due to dependencies
     */
    @Test
    public void testMoveFile_SpecialCharactersInPaths() {
        String[] specialCharPaths = {
            "/drives/b!testDriveId/root:/source folder/file name.txt",
            "/drives/b!testDriveId/root:/ソース/ファイル.txt",
            "/drives/b!testDriveId/root:/مجلد المصدر/ملف.txt",
            "/drives/b!testDriveId/root:/папка/файл.txt",
            "/drives/b!testDriveId/root:/folder-with-dashes/file_with_underscores.txt"
        };

        for (String sourcePath : specialCharPaths) {
            for (String targetPath : specialCharPaths) {
                try {
                    copyFileDao.moveFile(sourcePath, targetPath, false, false);
                    fail("Should throw NullPointerException because operationsManagementDao is null");
                } catch (NullPointerException e) {
                    assertTrue("Exception should mention operationsManagementDao",
                              e.getMessage().contains("operationsManagementDao"));
                }
            }
        }
    }

    /**
     * Test moveFile method with extremely long paths - all should fail due to dependencies
     */
    @Test
    public void testMoveFile_ExtremelyLongPaths() {
        // Create very long path
        StringBuilder longPathBuilder = new StringBuilder("/drives/b!testDriveId/root:");
        for (int i = 0; i < 50; i++) {
            longPathBuilder.append("/very_long_folder_name_").append(i);
        }
        longPathBuilder.append("/very_long_file_name.txt");
        String longPath = longPathBuilder.toString();

        try {
            copyFileDao.moveFile(longPath, VALID_TARGET_PATH, false, false);
            fail("Should throw NullPointerException because operationsManagementDao is null");
        } catch (NullPointerException e) {
            assertTrue("Exception should mention operationsManagementDao",
                      e.getMessage().contains("operationsManagementDao"));
        }

        try {
            copyFileDao.moveFile(VALID_SOURCE_PATH, longPath, false, false);
            fail("Should throw NullPointerException because operationsManagementDao is null");
        } catch (NullPointerException e) {
            assertTrue("Exception should mention operationsManagementDao",
                      e.getMessage().contains("operationsManagementDao"));
        }
    }

    /**
     * Test moveFile method with same source and target paths - should fail due to dependencies
     */
    @Test
    public void testMoveFile_SameSourceAndTarget() {
        String samePath = VALID_SOURCE_PATH;

        try {
            copyFileDao.moveFile(samePath, samePath, false, false);
            fail("Should throw NullPointerException because operationsManagementDao is null");
        } catch (NullPointerException e) {
            assertTrue("Exception should mention operationsManagementDao",
                      e.getMessage().contains("operationsManagementDao"));
        }
    }

    /**
     * Test moveFile method with whitespace-only paths - should fail due to dependencies
     */
    @Test
    public void testMoveFile_WhitespaceOnlyPaths() {
        String[] whitespacePaths = {
            " ",
            "   ",
            "\t",
            "\n",
            "\r",
            " \t \n \r "
        };

        for (String whitespacePath : whitespacePaths) {
            try {
                copyFileDao.moveFile(whitespacePath, VALID_TARGET_PATH, false, false);
                fail("Should throw NullPointerException because operationsManagementDao is null");
            } catch (NullPointerException e) {
                assertTrue("Exception should mention operationsManagementDao",
                          e.getMessage().contains("operationsManagementDao"));
            }

            try {
                copyFileDao.moveFile(VALID_SOURCE_PATH, whitespacePath, false, false);
                fail("Should throw NullPointerException because operationsManagementDao is null");
            } catch (NullPointerException e) {
                assertTrue("Exception should mention operationsManagementDao",
                          e.getMessage().contains("operationsManagementDao"));
            }
        }
    }

    /**
     * Test moveFile method with malformed drive paths - should fail due to dependencies
     */
    @Test
    public void testMoveFile_MalformedDrivePaths() {
        String[] malformedPaths = {
            "/drives/",
            "/drives/b!/root:",
            "/drives/b!testDriveId/",
            "/drives/b!testDriveId/root",
            "/drives/b!testDriveId/root/",
            "/drives/b!testDriveId/root::",
            "/drives/b!testDriveId/root:/",
            "drives/b!testDriveId/root:/path", // missing leading slash
            "/drive/b!testDriveId/root:/path", // singular drive
            "/drives/testDriveId/root:/path" // missing b!
        };

        for (String malformedPath : malformedPaths) {
            try {
                copyFileDao.moveFile(malformedPath, VALID_TARGET_PATH, false, false);
                fail("Should throw NullPointerException because operationsManagementDao is null");
            } catch (NullPointerException e) {
                assertTrue("Exception should mention operationsManagementDao",
                          e.getMessage().contains("operationsManagementDao"));
            }
        }
    }

    /**
     * Test moveFile method with control characters in paths - should fail due to dependencies
     */
    @Test
    public void testMoveFile_ControlCharactersInPaths() {
        String[] controlCharPaths = {
            "/drives/b!testDriveId/root:/folder\u0000name/file.txt", // null character
            "/drives/b!testDriveId/root:/folder\u0001name/file.txt", // start of heading
            "/drives/b!testDriveId/root:/folder\u0007name/file.txt", // bell character
            "/drives/b!testDriveId/root:/folder\u001Fname/file.txt", // unit separator
            "/drives/b!testDriveId/root:/folder\u007Fname/file.txt"  // delete character
        };

        for (String controlCharPath : controlCharPaths) {
            try {
                copyFileDao.moveFile(controlCharPath, VALID_TARGET_PATH, false, false);
                fail("Should throw NullPointerException because operationsManagementDao is null");
            } catch (NullPointerException e) {
                assertTrue("Exception should mention operationsManagementDao",
                          e.getMessage().contains("operationsManagementDao"));
            }
        }
    }

    /**
     * Test moveFile method with minimal valid paths - should fail due to dependencies
     */
    @Test
    public void testMoveFile_MinimalValidPaths() {
        String[] minimalPaths = {
            "/drives/b!a/root:/a",
            "/drives/b!1/root:/1",
            "/drives/b!x/root:/x.txt"
        };

        for (String path : minimalPaths) {
            try {
                copyFileDao.moveFile(path, path, false, false);
                fail("Should throw NullPointerException because operationsManagementDao is null");
            } catch (NullPointerException e) {
                assertTrue("Exception should mention operationsManagementDao",
                          e.getMessage().contains("operationsManagementDao"));
            }
        }
    }

    /**
     * Test moveFile method with encoded characters in paths - should fail due to dependencies
     */
    @Test
    public void testMoveFile_EncodedCharactersInPaths() {
        String[] encodedPaths = {
            "/drives/b!testDriveId/root:/source%20folder/file%20name.txt",
            "/drives/b!testDriveId/root:/folder%2Bwith%2Bplus/file.txt",
            "/drives/b!testDriveId/root:/folder%26with%26ampersand/file.txt",
            "/drives/b!testDriveId/root:/folder%3Dwith%3Dequals/file.txt",
            "/drives/b!testDriveId/root:/folder%40with%40at/file.txt"
        };

        for (String encodedPath : encodedPaths) {
            try {
                copyFileDao.moveFile(encodedPath, VALID_TARGET_PATH, false, false);
                fail("Should throw NullPointerException because operationsManagementDao is null");
            } catch (NullPointerException e) {
                assertTrue("Exception should mention operationsManagementDao",
                          e.getMessage().contains("operationsManagementDao"));
            }
        }
    }

    /**
     * Test moveFile method performance with multiple rapid calls - should fail due to dependencies
     */
    @Test
    public void testMoveFile_MultipleRapidCalls() {
        // Test that multiple rapid calls all fail consistently due to null operationsManagementDao
        for (int i = 0; i < 10; i++) { // Reduced to 10 for faster test execution
            try {
                copyFileDao.moveFile(VALID_SOURCE_PATH + i, VALID_TARGET_PATH + i,
                                   i % 2 == 0, i % 3 == 0);
                fail("Call " + i + " should throw NullPointerException because operationsManagementDao is null");
            } catch (NullPointerException e) {
                assertTrue("Exception " + i + " should mention operationsManagementDao",
                          e.getMessage().contains("operationsManagementDao"));
            }
        }
    }

    // ========================================
    // COPY WITH NAME TESTS - createRequestBody(folderId, targetFileName)
    // ========================================

    /**
     * Test createRequestBody with targetFileName produces JSON containing "name" field
     */
    @Test
    public void testCreateRequestBody_WithTargetFileName() throws IOException {
        String folderId = "test-folder-id";
        String targetFileName = "2026-04-16_ACN-SLOB-Worksheet.xlsx";

        RequestBody requestBody = copyFileDao.createRequestBody(folderId, targetFileName);

        assertNotNull("Request body should not be null", requestBody);
        assertNotNull("Content type should not be null", requestBody.contentType());
        assertTrue("Content type should be JSON",
                Objects.requireNonNull(requestBody.contentType()).toString().startsWith("application/json"));

        // Read body content to verify JSON structure
        okio.Buffer buffer = new okio.Buffer();
        requestBody.writeTo(buffer);
        String json = buffer.readUtf8();

        assertTrue("JSON should contain parentReference", json.contains("parentReference"));
        assertTrue("JSON should contain folder ID", json.contains(folderId));
        assertTrue("JSON should contain name field", json.contains("\"name\""));
        assertTrue("JSON should contain target file name", json.contains(targetFileName));
    }

    /**
     * Test createRequestBody with null targetFileName does NOT include "name" field (backward compat)
     */
    @Test
    public void testCreateRequestBody_WithNullTargetFileName() throws IOException {
        String folderId = "test-folder-id";

        RequestBody requestBody = copyFileDao.createRequestBody(folderId, null);

        okio.Buffer buffer = new okio.Buffer();
        requestBody.writeTo(buffer);
        String json = buffer.readUtf8();

        assertTrue("JSON should contain parentReference", json.contains("parentReference"));
        assertTrue("JSON should contain folder ID", json.contains(folderId));
        assertFalse("JSON should NOT contain name field when targetFileName is null", json.contains("\"name\""));
    }

    /**
     * Test createRequestBody with blank targetFileName does NOT include "name" field
     */
    @Test
    public void testCreateRequestBody_WithBlankTargetFileName() throws IOException {
        String folderId = "test-folder-id";

        RequestBody requestBody = copyFileDao.createRequestBody(folderId, "   ");

        okio.Buffer buffer = new okio.Buffer();
        requestBody.writeTo(buffer);
        String json = buffer.readUtf8();

        assertFalse("JSON should NOT contain name field when targetFileName is blank", json.contains("\"name\""));
    }

    /**
     * Test createRequestBody with empty targetFileName does NOT include "name" field
     */
    @Test
    public void testCreateRequestBody_WithEmptyTargetFileName() throws IOException {
        String folderId = "test-folder-id";

        RequestBody requestBody = copyFileDao.createRequestBody(folderId, "");

        okio.Buffer buffer = new okio.Buffer();
        requestBody.writeTo(buffer);
        String json = buffer.readUtf8();

        assertFalse("JSON should NOT contain name field when targetFileName is empty", json.contains("\"name\""));
    }

    /**
     * Test createRequestBody with various realistic target file names
     */
    @Test
    public void testCreateRequestBody_WithVariousTargetFileNames() throws IOException {
        String folderId = "target-folder-123";
        String[] fileNames = {
                "report.pdf",
                "2026-04-16_ACN-SLOB-Worksheet.xlsx",
                "INV-2026-0042.pdf",
                "backup_2026_04_20.tar.gz",
                "file with spaces.docx",
                "file_name_with_underscores.txt",
                "UPPERCASE.CSV",
                "mixed.Case.Name.json"
        };

        for (String fileName : fileNames) {
            RequestBody requestBody = copyFileDao.createRequestBody(folderId, fileName);

            okio.Buffer buffer = new okio.Buffer();
            requestBody.writeTo(buffer);
            String json = buffer.readUtf8();

            assertTrue("JSON should contain name field for: " + fileName, json.contains("\"name\""));
            assertTrue("JSON should contain file name: " + fileName, json.contains(fileName));
            assertTrue("JSON should contain parentReference", json.contains("parentReference"));
            assertTrue("JSON should contain folder ID", json.contains(folderId));
        }
    }

    /**
     * Test createRequestBody with special characters in target file name
     */
    @Test
    public void testCreateRequestBody_WithSpecialCharsInTargetFileName() throws IOException {
        String folderId = "target-folder-123";
        String[] specialNames = {
                "file (1).txt",
                "file [copy].pdf",
                "file {v2}.docx",
                "file'name.txt",
                "ファイル.xlsx",
                "файл.pdf",
                "ملف.docx"
        };

        for (String fileName : specialNames) {
            RequestBody requestBody = copyFileDao.createRequestBody(folderId, fileName);

            assertNotNull("Request body should not be null for: " + fileName, requestBody);
            assertTrue("Content type should be JSON",
                    Objects.requireNonNull(requestBody.contentType()).toString().startsWith("application/json"));

            okio.Buffer buffer = new okio.Buffer();
            requestBody.writeTo(buffer);
            String json = buffer.readUtf8();

            assertTrue("JSON should contain name field for: " + fileName, json.contains("\"name\""));
        }
    }

    /**
     * Test that single-arg createRequestBody delegates to two-arg with null targetFileName
     */
    @Test
    public void testCreateRequestBody_SingleArgMatchesTwoArgWithNull() throws IOException {
        String folderId = "test-folder-id";

        RequestBody singleArg = copyFileDao.createRequestBody(folderId);
        RequestBody twoArgNull = copyFileDao.createRequestBody(folderId, null);

        okio.Buffer buf1 = new okio.Buffer();
        singleArg.writeTo(buf1);
        String json1 = buf1.readUtf8();

        okio.Buffer buf2 = new okio.Buffer();
        twoArgNull.writeTo(buf2);
        String json2 = buf2.readUtf8();

        assertEquals("Single-arg and two-arg with null should produce same JSON", json1, json2);
    }

    /**
     * Test copyFile with targetFileName returns error response due to null operationsManagementDao.
     * Verifies the method signature accepts targetFileName parameter and handles the dependency
     * absence gracefully (copyFile catches all exceptions internally).
     */
    @Test
    public void testCopyFile_WithTargetFileName_ReturnsErrorDueToDependency() {
        ExtensionResponse response = copyFileDao.copyFile(
                VALID_SOURCE_PATH, VALID_TARGET_PATH,
                false, false,
                false, false,
                true, "custom-name.xlsx");
        assertNotNull("Should return a response even when dependencies are null", response);
    }

    /**
     * Test copyFile without targetFileName (null) returns error response due to null operationsManagementDao.
     * Ensures backward compatibility of the overloaded method.
     */
    @Test
    public void testCopyFile_WithNullTargetFileName_ReturnsErrorDueToDependency() {
        ExtensionResponse response = copyFileDao.copyFile(
                VALID_SOURCE_PATH, VALID_TARGET_PATH,
                false, false,
                false, false,
                true, null);
        assertNotNull("Should return a response even when dependencies are null", response);
    }

    /**
     * Test containsInvalidCharacters with typical target file names for Copy With Name
     */
    @Test
    public void testContainsInvalidCharacters_TypicalCopyWithNameFileNames() {
        // Valid file names that should be accepted
        String[] validNames = {
                "2026-04-16_ACN-SLOB-Worksheet.xlsx",
                "INV-2026-0042.pdf",
                "report_final (v2).docx",
                "backup.tar.gz",
                "file name with spaces.txt"
        };
        for (String name : validNames) {
            assertFalse("Should be valid: " + name, copyFileDao.containsInvalidCharacters(name));
        }

        // Invalid file names that should be rejected
        String[] invalidNames = {
                "file:name.txt",
                "file*name.xlsx",
                "file?name.pdf",
                "path/to/file.txt",
                "file<name>.docx",
                "file|name.csv"
        };
        for (String name : invalidNames) {
            assertTrue("Should be invalid: " + name, copyFileDao.containsInvalidCharacters(name));
        }
    }

    /**
     * Test moveFile method with various file extensions - should fail due to dependencies
     */
    @Test
    public void testMoveFile_VariousFileExtensions() {
        String[] fileExtensions = {
            ".txt", ".pdf", ".docx", ".xlsx", ".pptx",
            ".jpg", ".png", ".gif", ".mp4", ".avi",
            ".zip", ".rar", ".tar", ".gz", ".exe",
            ".html", ".css", ".js", ".json", ".xml"
        };

        for (String extension : fileExtensions) {
            String sourcePath = "/drives/b!testDriveId/root:/source/file" + extension;
            String targetPath = "/drives/b!testDriveId/root:/target/file" + extension;

            try {
                copyFileDao.moveFile(sourcePath, targetPath, false, false);
                fail("Should throw NullPointerException because operationsManagementDao is null");
            } catch (NullPointerException e) {
                assertTrue("Exception should mention operationsManagementDao for extension: " + extension,
                          e.getMessage().contains("operationsManagementDao"));
            }
        }
    }

}
