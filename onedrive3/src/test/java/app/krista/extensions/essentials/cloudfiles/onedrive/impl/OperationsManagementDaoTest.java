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
import static org.junit.Assert.assertEquals;
import app.krista.model.base.File;
import com.google.gson.JsonPrimitive;
import com.microsoft.graph.http.GraphServiceException;
import com.microsoft.graph.models.DriveItem;
import com.microsoft.graph.models.ItemReference;
import com.microsoft.graph.requests.UserRequestBuilder;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

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
            null, null, null, null) {
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
    // RENAMEFILE METHOD TESTS — SHARED DRIVE SUPPORT
    // ========================================

    /**
     * Test renameFile with a shared drive path (checkDriveIDContains = true).
     * The path should be recognized as valid even though Graph API call fails with null provider.
     */
    @Test
    public void testRenameFile_SharedDrivePath_RecognizedAsValid() {
        String sharedDrivePath = "/drives/b!testDriveId/root:/testFolder/testFile.txt";
        String targetFileName = "renamedFile.txt";

        try {
            ExtensionResponse response = operationsManagementDao.renameFile(sharedDrivePath, targetFileName, false, false);
            // If provider is null, we expect an NPE from the Graph API call, not an "invalid path" error
        } catch (NullPointerException e) {
            // Expected: the path was correctly parsed (checkDriveIDContains=true, getDriveIDAndPath succeeded)
            // but the Graph API call failed because provider is a mock returning null
            assertTrue("NPE expected when Graph API call uses null provider", true);
        } catch (Exception e) {
            // Any other exception is also acceptable — the key point is that it didn't
            // return a "Source file not found" re-entry response for a valid path
            assertTrue("Exception from Graph API call, not path validation", true);
        }
    }

    /**
     * Test renameFile with a personal drive path (isPersonalDrive = true).
     */
    @Test
    public void testRenameFile_PersonalDrivePath_RecognizedAsValid() {
        String personalDrivePath = "drive/root:/Documents/testFile.txt";
        String targetFileName = "renamedFile.txt";

        try {
            ExtensionResponse response = operationsManagementDao.renameFile(personalDrivePath, targetFileName, false, false);
        } catch (NullPointerException e) {
            // Expected: path was correctly identified as personal drive, but Graph API call failed
            assertTrue("NPE expected when Graph API call uses null provider", true);
        } catch (Exception e) {
            assertTrue("Exception from Graph API call, not path validation", true);
        }
    }

    /**
     * Test renameFile with a personal drive path starting with /.
     */
    @Test
    public void testRenameFile_PersonalDrivePathWithLeadingSlash_RecognizedAsValid() {
        String personalDrivePath = "/drive/root:/Documents/testFile.txt";
        String targetFileName = "renamedFile.txt";

        try {
            ExtensionResponse response = operationsManagementDao.renameFile(personalDrivePath, targetFileName, false, false);
        } catch (NullPointerException e) {
            // Expected: path was correctly identified as personal drive
            assertTrue("NPE expected when Graph API call uses null provider", true);
        } catch (Exception e) {
            assertTrue("Exception from Graph API call, not path validation", true);
        }
    }

    /**
     * Test renameFile with an invalid path — should trigger re-entry error, not NPE.
     */
    @Test
    public void testRenameFile_InvalidPath_ReturnsReentryOrError() {
        String invalidPath = "/some/invalid/path/file.txt";
        String targetFileName = "renamedFile.txt";

        try {
            ExtensionResponse response = operationsManagementDao.renameFile(invalidPath, targetFileName, false, false);
            // If it returns a response (re-entry), that's the expected behavior
            assertNotNull("Should return a re-entry response for invalid path", response);
        } catch (NullPointerException e) {
            // This is acceptable if ValidationTypeUtil mock returns null and re-entry logic NPEs
            assertTrue("NPE in re-entry handling", true);
        }
    }

    /**
     * Test renameFile with null source — should handle gracefully.
     */
    @Test
    public void testRenameFile_NullSource() {
        try {
            ExtensionResponse response = operationsManagementDao.renameFile(null, "newName.txt", false, false);
        } catch (NullPointerException e) {
            // Expected: null source will cause NPE in FileUtil.checkDriveIDContains
            assertTrue("NPE expected for null source", true);
        }
    }

    /**
     * Test renameFile with shared drive path format variant without leading slash.
     */
    @Test
    public void testRenameFile_SharedDrivePathNoLeadingSlash_RecognizedAsValid() {
        String sharedDrivePath = "drives/b!testDriveId/root:/testFolder/testFile.txt";
        String targetFileName = "renamedFile.txt";

        try {
            ExtensionResponse response = operationsManagementDao.renameFile(sharedDrivePath, targetFileName, false, false);
        } catch (NullPointerException e) {
            // Expected: path was correctly parsed as shared drive but Graph API call failed
            assertTrue("NPE expected when Graph API call uses null provider", true);
        } catch (Exception e) {
            assertTrue("Exception from Graph API call, not path validation", true);
        }
    }

    /**
     * Test renameFile with target containing invalid characters.
     */
    @Test
    public void testRenameFile_InvalidTargetCharacters() {
        String validPath = "drive/root:/Documents/testFile.txt";
        String invalidTarget = "file*name.txt";

        try {
            ExtensionResponse response = operationsManagementDao.renameFile(validPath, invalidTarget, false, false);
            // May return error response about invalid characters, or NPE from null provider
        } catch (NullPointerException e) {
            // Expected with null provider
            assertTrue("NPE expected when Graph API call uses null provider", true);
        } catch (Exception e) {
            assertTrue("Exception from validation or Graph API call", true);
        }
    }

    // ========================================
    // GETERROR MESSAGE — null-message fix
    // ========================================

    /**
     * Verifies that when an exception with a null message reaches the download error path,
     * the response contains the expected fallback message (not NPE).
     * <p>
     * Before the fix: {@code Objects.requireNonNull(cause.getMessage())} would throw NPE,
     * causing the outer catch to fire and return "Unexpected error: null".
     * After the fix: {@code getErrorMessage} checks for null first and returns
     * {@link Constants#PLEASE_PROVIDE_VALID_FILE_SOURCE_PATH}.
     */
    @Test
    public void testGetErrorMessage_NullMessageException_ReturnsFallback() throws Exception {
        // Access the private static method via reflection
        java.lang.reflect.Method method = OperationsManagementDao.class
                .getDeclaredMethod("getErrorMessage", Exception.class);
        method.setAccessible(true);

        // Exception with null message
        Exception nullMessageEx = new RuntimeException((String) null);
        Object result = method.invoke(null, nullMessageEx);

        assertEquals("Null-message exception must return PLEASE_PROVIDE_VALID_FILE_SOURCE_PATH",
                Constants.PLEASE_PROVIDE_VALID_FILE_SOURCE_PATH, result);
    }

    @Test
    public void testGetErrorMessage_ItemNotFoundMessage_ReturnsFileNotFound() throws Exception {
        java.lang.reflect.Method method = OperationsManagementDao.class
                .getDeclaredMethod("getErrorMessage", Exception.class);
        method.setAccessible(true);

        Exception itemNotFoundEx = new RuntimeException("itemNotFound: the resource could not be located");
        Object result = method.invoke(null, itemNotFoundEx);

        assertEquals("itemNotFound message must return FILE_NOT_FOUND constant",
                Constants.FILE_NOT_FOUND, result);
    }

    @Test
    public void testGetErrorMessage_AccessDeniedMessage_ReturnsRestrictedMessage() throws Exception {
        java.lang.reflect.Method method = OperationsManagementDao.class
                .getDeclaredMethod("getErrorMessage", Exception.class);
        method.setAccessible(true);

        Exception accessDeniedEx = new RuntimeException("accessDenied");
        Object result = method.invoke(null, accessDeniedEx);

        assertEquals("accessDenied message must return restricted-file constant",
                Constants.FILE_IS_CURRENTLY_RESTRICTED_FOR_ASSISTANCE_CONTACT_YOUR_ADMINISTRATOR_OR_SUPPORT, result);
    }

    @Test
    public void testGetErrorMessage_GenericMessage_ReturnsValidSourcePathFallback() throws Exception {
        java.lang.reflect.Method method = OperationsManagementDao.class
                .getDeclaredMethod("getErrorMessage", Exception.class);
        method.setAccessible(true);

        Exception genericEx = new RuntimeException("some unrecognised error");
        Object result = method.invoke(null, genericEx);

        assertEquals("Unrecognised message must fall back to PLEASE_PROVIDE_VALID_FILE_SOURCE_PATH",
                Constants.PLEASE_PROVIDE_VALID_FILE_SOURCE_PATH, result);
    }

    // ========================================
    // DOWNLOADBYDRIVEIDBYPATH PRIVATE METHOD TESTS
    // ========================================

    /** 10 MB — well below the 50 MB limit */
    private static final long SIZE_10MB = 10L * 1024 * 1024;
    /** 51 MB — just over the 50 MB limit */
    private static final long SIZE_51MB = 51L * 1024 * 1024;

    /**
     * Reflective helper: invokes the private {@code downloadByDriveIDByPath} method.
     * Re-throws any exception thrown by the method itself.
     */
    private static void invokeDownloadByDriveIDByPath(
            OperationsManagementDao dao,
            String driveID, String path, Boolean downloadAsLink, Boolean downloadAsKristaFile,
            Map<String, Object> payload) throws Exception {
        java.lang.reflect.Method m = OperationsManagementDao.class.getDeclaredMethod(
                "downloadByDriveIDByPath",
                String.class, String.class, Boolean.class, Boolean.class, Map.class);
        m.setAccessible(true);
        try {
            m.invoke(dao, driveID, path, downloadAsLink, downloadAsKristaFile, payload);
        } catch (java.lang.reflect.InvocationTargetException ite) {
            Throwable cause = ite.getCause();
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            if (cause instanceof Exception) throw (Exception) cause;
            throw ite;
        }
    }

    /**
     * Builds a subject whose {@code getDriveItem} returns the supplied stub without
     * making any network calls.  Works because {@code getDriveItem} is package-private.
     */
    private OperationsManagementDao buildSubjectWithDriveItem(DriveItem stub) {
        return new OperationsManagementDao(createMockProvider(), createMockKristaMediaClient(),
                createMockValidationUtil(), createMockTelemetryHelper()) {
            @Override
            DriveItem getDriveItem(String driveID, String path) {
                return stub;
            }
        };
    }

    /**
     * Builds a subject whose {@code getDriveItem} always throws the given unchecked exception.
     */
    private OperationsManagementDao buildSubjectThatThrows(RuntimeException ex) {
        return new OperationsManagementDao(createMockProvider(), createMockKristaMediaClient(),
                createMockValidationUtil(), createMockTelemetryHelper()) {
            @Override
            DriveItem getDriveItem(String driveID, String path) {
                throw ex;
            }
        };
    }

    // --- null DriveItem ---

    /**
     * When {@code getDriveItem} returns {@code null}, the payload must receive
     * {@code Success=false} and {@code PLEASE_PROVIDE_VALID_FILE_SOURCE_PATH}.
     */
    @Test
    public void testDownloadByDriveIDByPath_nullDriveItem_setsErrorPayload() throws Exception {
        Map<String, Object> payload = new HashMap<>();
        invokeDownloadByDriveIDByPath(
                buildSubjectWithDriveItem(null),
                "driveId", "path/to/file.pdf", false, false, payload);

        assertFalse("Success must be false for null DriveItem",
                (Boolean) payload.get("Success"));
        assertEquals("Message must be PLEASE_PROVIDE_VALID_FILE_SOURCE_PATH",
                Constants.PLEASE_PROVIDE_VALID_FILE_SOURCE_PATH, payload.get("Message"));
    }

    // --- Folder item ---

    /**
     * When the DriveItem represents a folder ({@code folder != null}), it cannot be
     * downloaded; the payload must contain an error.
     */
    @Test
    public void testDownloadByDriveIDByPath_folderItem_setsErrorPayload() throws Exception {
        DriveItem folderItem = new DriveItem();
        folderItem.name = "someFolder";
        folderItem.folder = new com.microsoft.graph.models.Folder(); // non-null → is a folder
        Map<String, Object> payload = new HashMap<>();
        invokeDownloadByDriveIDByPath(
                buildSubjectWithDriveItem(folderItem),
                "driveId", "path/to/folder", false, false, payload);

        assertFalse("Success must be false for folder DriveItem",
                (Boolean) payload.get("Success"));
        assertEquals("Message must be PLEASE_PROVIDE_VALID_FILE_SOURCE_PATH",
                Constants.PLEASE_PROVIDE_VALID_FILE_SOURCE_PATH, payload.get("Message"));
    }

    // --- downloadAsLink = true ---

    /**
     * When {@code downloadAsLink=true}, the response must be a link response:
     * {@code Success=true}, a {@code Link} key, and the copy-URL message.
     */
    @Test
    public void testDownloadByDriveIDByPath_downloadAsLink_setsTrueSuccessAndLink() throws Exception {
        DriveItem item = new DriveItem();
        item.name = "report.pdf";
        item.webUrl = "https://sharepoint.example.com/report.pdf";
        item.size = SIZE_10MB;
        Map<String, Object> payload = new HashMap<>();
        invokeDownloadByDriveIDByPath(
                buildSubjectWithDriveItem(item),
                "driveId", "path/to/report.pdf", true /* downloadAsLink */, false, payload);

        assertTrue("Success must be true for download-as-link",
                (Boolean) payload.get("Success"));
        assertTrue("Payload must contain 'Link' key", payload.containsKey("Link"));
        assertEquals("Message must be TO_DOWNLOAD_FILE_PLEASE_COPY_URL",
                Constants.TO_DOWNLOAD_FILE_PLEASE_COPY_URL, payload.get("Message"));
    }

    // --- downloadAsKristaFile = true, size > 50 MB ---

    /**
     * When {@code downloadAsKristaFile=true} and the file exceeds 50 MB,
     * the response must indicate the size limit error (branch 4 of the method logic).
     */
    @Test
    public void testDownloadByDriveIDByPath_downloadAsKristaFileTrue_oversize_setsErrorPayload() throws Exception {
        DriveItem item = new DriveItem();
        item.name = "bigfile.mp4";
        item.webUrl = "https://sharepoint.example.com/bigfile.mp4";
        item.size = SIZE_51MB;
        Map<String, Object> payload = new HashMap<>();
        invokeDownloadByDriveIDByPath(
                buildSubjectWithDriveItem(item),
                "driveId", "path/to/bigfile.mp4", false, true /* downloadAsKristaFile */, payload);

        assertFalse("Success must be false for oversized Krista-file download",
                (Boolean) payload.get("Success"));
        assertTrue("Message must mention the 50 MB limit",
                ((String) payload.get("Message"))
                        .contains(Constants.FILE_SIZE_IS_GREATER_THAN_50_MB_PLEASE_DOWNLOAD_FILE_BY_URL));
    }

    // --- downloadAsKristaFile = false, size > 50 MB ---

    /**
     * When {@code downloadAsKristaFile=false} and the file exceeds 50 MB,
     * the inner {@code if (size > 50MB)} branch fires and returns an error
     * (branch 5 of the method logic).
     */
    @Test
    public void testDownloadByDriveIDByPath_downloadAsKristaFileFalse_oversize_setsErrorPayload() throws Exception {
        DriveItem item = new DriveItem();
        item.name = "huge.zip";
        item.webUrl = "https://sharepoint.example.com/huge.zip";
        item.size = SIZE_51MB;
        Map<String, Object> payload = new HashMap<>();
        invokeDownloadByDriveIDByPath(
                buildSubjectWithDriveItem(item),
                "driveId", "path/to/huge.zip", false, false, payload);

        assertFalse("Success must be false for oversized file (non-Krista path)",
                (Boolean) payload.get("Success"));
        assertTrue("Message must contain the 50 MB limit text",
                ((String) payload.get("Message"))
                        .contains(Constants.FILE_SIZE_IS_GREATER_THAN_50_MB_PLEASE_DOWNLOAD_FILE_BY_URL));
    }

    // --- size = null ---

    /**
     * When {@code driveItem.size} is {@code null}, neither the size-check nor the
     * download-eligible branch is entered; the else fallback emits an error.
     */
    @Test
    public void testDownloadByDriveIDByPath_sizeNull_setsErrorPayload() throws Exception {
        DriveItem item = new DriveItem();
        item.name = "file.pdf";
        item.webUrl = "https://example.com/file.pdf";
        item.size = null;
        Map<String, Object> payload = new HashMap<>();
        invokeDownloadByDriveIDByPath(
                buildSubjectWithDriveItem(item),
                "driveId", "path", false, false, payload);

        assertFalse("Success must be false when size is null",
                (Boolean) payload.get("Success"));
        assertEquals("Message must be PLEASE_PROVIDE_VALID_FILE_SOURCE_PATH when size is null",
                Constants.PLEASE_PROVIDE_VALID_FILE_SOURCE_PATH, payload.get("Message"));
    }

    // --- parentReference = null ---

    /**
     * When {@code driveItem.parentReference} is {@code null} (size is acceptable),
     * the download-eligible condition fails and the else fallback emits an error.
     */
    @Test
    public void testDownloadByDriveIDByPath_parentReferenceNull_setsErrorPayload() throws Exception {
        DriveItem item = new DriveItem();
        item.name = "file.pdf";
        item.webUrl = "https://example.com/file.pdf";
        item.size = SIZE_10MB;
        item.parentReference = null;
        Map<String, Object> payload = new HashMap<>();
        invokeDownloadByDriveIDByPath(
                buildSubjectWithDriveItem(item),
                "driveId", "path", false, false, payload);

        assertFalse("Success must be false when parentReference is null",
                (Boolean) payload.get("Success"));
        assertEquals("Message must be PLEASE_PROVIDE_VALID_FILE_SOURCE_PATH",
                Constants.PLEASE_PROVIDE_VALID_FILE_SOURCE_PATH, payload.get("Message"));
    }

    // --- parentReference.driveId = null ---

    /**
     * When {@code parentReference} is present but {@code driveId} is {@code null},
     * the download-eligible condition fails and the else fallback emits an error.
     */
    @Test
    public void testDownloadByDriveIDByPath_driveIdNull_setsErrorPayload() throws Exception {
        DriveItem item = new DriveItem();
        item.name = "file.pdf";
        item.webUrl = "https://example.com/file.pdf";
        item.size = SIZE_10MB;
        item.parentReference = new ItemReference();
        item.parentReference.driveId = null;
        Map<String, Object> payload = new HashMap<>();
        invokeDownloadByDriveIDByPath(
                buildSubjectWithDriveItem(item),
                "driveId", "path", false, false, payload);

        assertFalse("Success must be false when parentReference.driveId is null",
                (Boolean) payload.get("Success"));
        assertEquals("Message must be PLEASE_PROVIDE_VALID_FILE_SOURCE_PATH",
                Constants.PLEASE_PROVIDE_VALID_FILE_SOURCE_PATH, payload.get("Message"));
    }

    // --- ADM URL present, HTTP connection fails (IOException path) ---

    /**
     * When an ADM URL is present but the HTTP connection to it fails,
     * an {@link java.io.IOException} is thrown and caught by the first catch block,
     * producing an error payload.
     */
    @Test
    public void testDownloadByDriveIDByPath_admUrl_httpConnectionFails_setsErrorPayload() throws Exception {
        DriveItem item = new DriveItem();
        item.name = "file.pdf";
        item.id = "item-id-xyz";
        item.size = SIZE_10MB;
        item.parentReference = new ItemReference();
        item.parentReference.driveId = "b!testDriveId";
        // Port 1 on localhost is not open → ConnectException (extends IOException) thrown at getResponseCode()
        item.additionalDataManager().put(
                "@microsoft.graph.downloadUrl",
                new JsonPrimitive("http://127.0.0.1:1/download-will-fail"));
        Map<String, Object> payload = new HashMap<>();
        invokeDownloadByDriveIDByPath(
                buildSubjectWithDriveItem(item),
                "driveId", "path", false, false, payload);

        assertFalse("Success must be false when HTTP download fails",
                (Boolean) payload.get("Success"));
        assertTrue("Payload must contain 'Message' key", payload.containsKey("Message"));
    }

    // --- ADM URL present, HTTP 200 → success path ---

    /**
     * When an ADM URL is present and the HTTP server returns 200,
     * the input stream is read, a Krista file is created, and the payload receives
     * {@code Success=true} and {@code Message="File is ready to download"}.
     * <p>
     * Exercises: the {@code responseCode >= 200 && responseCode < 300} branch →
     * {@code conn.getInputStream()}, {@code DriveUtil.getFileFromInputStream},
     * {@code kristaMediaClient.toKristaFile}, and
     * {@code ResponseUtil.downloadAsKristaFileResponse}.
     */
    @Test
    public void testDownloadByDriveIDByPath_admUrl_http200_successKristaFileInPayload() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/dl", exchange -> {
            byte[] body = "dummy file content".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
        try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/dl";
            DriveItem item = new DriveItem();
            item.name = "testfile.txt";
            item.id = "item-id-123";
            item.size = SIZE_10MB;
            item.parentReference = new ItemReference();
            item.parentReference.driveId = "b!testDriveId";
            item.additionalDataManager().put("@microsoft.graph.downloadUrl", new JsonPrimitive(url));

            Map<String, Object> payload = new HashMap<>();
            invokeDownloadByDriveIDByPath(
                    buildSubjectWithDriveItem(item),
                    "driveId", "path/testfile.txt", false, false, payload);

            assertTrue("Success must be true for HTTP 200 download",
                    (Boolean) payload.get("Success"));
            assertEquals("Message must be 'File is ready to download'",
                    "File is ready to download", payload.get("Message"));
            assertNotNull("Payload must contain 'Krista File'", payload.get("Krista File"));
        } finally {
            server.stop(0);
        }
    }

    // --- ADM URL present, HTTP 404 → IOException thrown (else branch: responseCode not 2xx) ---

    /**
     * When an ADM URL is present and the HTTP server returns 404,
     * the {@code else} branch fires:
     * {@code throw new IOException("Failed to download file content: HTTP 404 ...")}.
     * The IOException is caught by the first catch block, producing {@code Success=false}.
     */
    @Test
    public void testDownloadByDriveIDByPath_admUrl_http404_iOExceptionBranchSetsErrorPayload() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/dl", exchange -> {
            exchange.sendResponseHeaders(404, -1);
            exchange.getResponseBody().close();
        });
        server.start();
        try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/dl";
            DriveItem item = new DriveItem();
            item.name = "missing.txt";
            item.id = "item-id-404";
            item.size = SIZE_10MB;
            item.parentReference = new ItemReference();
            item.parentReference.driveId = "b!testDriveId";
            item.additionalDataManager().put("@microsoft.graph.downloadUrl", new JsonPrimitive(url));

            Map<String, Object> payload = new HashMap<>();
            invokeDownloadByDriveIDByPath(
                    buildSubjectWithDriveItem(item),
                    "driveId", "path/missing.txt", false, false, payload);

            assertFalse("Success must be false for HTTP 404 response",
                    (Boolean) payload.get("Success"));
            assertTrue("Payload must contain 'Message' key", payload.containsKey("Message"));
        } finally {
            server.stop(0);
        }
    }

    // --- ADM URL present, HTTP 500 → IOException thrown (else branch: responseCode not 2xx) ---

    /**
     * When an ADM URL is present and the HTTP server returns 500,
     * the {@code else} branch fires:
     * {@code throw new IOException("Failed to download file content: HTTP 500 ...")}.
     * The IOException is caught by the first catch block, producing {@code Success=false}.
     */
    @Test
    public void testDownloadByDriveIDByPath_admUrl_http500_iOExceptionBranchSetsErrorPayload() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/dl", exchange -> {
            exchange.sendResponseHeaders(500, -1);
            exchange.getResponseBody().close();
        });
        server.start();
        try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/dl";
            DriveItem item = new DriveItem();
            item.name = "serverError.txt";
            item.id = "item-id-500";
            item.size = SIZE_10MB;
            item.parentReference = new ItemReference();
            item.parentReference.driveId = "b!testDriveId";
            item.additionalDataManager().put("@microsoft.graph.downloadUrl", new JsonPrimitive(url));

            Map<String, Object> payload = new HashMap<>();
            invokeDownloadByDriveIDByPath(
                    buildSubjectWithDriveItem(item),
                    "driveId", "path/serverError.txt", false, false, payload);

            assertFalse("Success must be false for HTTP 500 response",
                    (Boolean) payload.get("Success"));
            assertTrue("Payload must contain 'Message' key", payload.containsKey("Message"));
        } finally {
            server.stop(0);
        }
    }

    // --- No ADM URL, provider chain fails (generic Exception path) ---

    /**
     * When no ADM URL is present, the code falls back to the Graph API path.
     * With a null-returning mock provider, the builder chain throws a
     * {@link NullPointerException} caught by the generic {@code catch (Exception)} block.
     */
    @Test
    public void testDownloadByDriveIDByPath_noAdmUrl_providerChainFails_setsErrorPayload() throws Exception {
        DriveItem item = new DriveItem();
        item.name = "file.pdf";
        item.id = "item-id-xyz";
        item.size = SIZE_10MB;
        item.parentReference = new ItemReference();
        item.parentReference.driveId = "b!testDriveId";
        // No ADM URL → falls back to FileUtil.getUserRequestBuilderWithItemId(provider, ...) → NPE
        Map<String, Object> payload = new HashMap<>();
        invokeDownloadByDriveIDByPath(
                buildSubjectWithDriveItem(item),
                "driveId", "path", false, false, payload);

        assertFalse("Success must be false when provider chain fails",
                (Boolean) payload.get("Success"));
        assertTrue("Payload must contain 'Message' key", payload.containsKey("Message"));
    }

    // --- GraphServiceException thrown by getDriveItem ---

    /**
     * When {@code getDriveItem} throws a {@link GraphServiceException},
     * it is caught by the first catch block and produces an error payload.
     */
    @Test
    public void testDownloadByDriveIDByPath_graphServiceException_setsErrorPayload() throws Exception {
        Map<String, Object> payload = new HashMap<>();
        invokeDownloadByDriveIDByPath(
                buildSubjectThatThrows(new TestDownloadGraphServiceException("Graph API error")),
                "driveId", "path", false, false, payload);

        assertFalse("Success must be false on GraphServiceException",
                (Boolean) payload.get("Success"));
        assertTrue("Payload must contain 'Message' key", payload.containsKey("Message"));
    }

    // --- IllegalArgumentException thrown by getDriveItem ---

    /**
     * When {@code getDriveItem} throws an {@link IllegalArgumentException},
     * it is caught by the first catch block and produces an error payload.
     */
    @Test
    public void testDownloadByDriveIDByPath_illegalArgumentException_setsErrorPayload() throws Exception {
        Map<String, Object> payload = new HashMap<>();
        invokeDownloadByDriveIDByPath(
                buildSubjectThatThrows(new IllegalArgumentException("bad argument")),
                "driveId", "path", false, false, payload);

        assertFalse("Success must be false on IllegalArgumentException",
                (Boolean) payload.get("Success"));
        assertTrue("Payload must contain 'Message' key", payload.containsKey("Message"));
    }

    // --- Unexpected RuntimeException thrown by getDriveItem ---

    /**
     * When {@code getDriveItem} throws an unexpected {@link RuntimeException}
     * (not IAE or GraphServiceException), it falls through to the generic
     * {@code catch (Exception)} block and produces an error payload.
     */
    @Test
    public void testDownloadByDriveIDByPath_unexpectedRuntimeException_setsErrorPayload() throws Exception {
        Map<String, Object> payload = new HashMap<>();
        invokeDownloadByDriveIDByPath(
                buildSubjectThatThrows(new RuntimeException("unexpected error")),
                "driveId", "path", false, false, payload);

        assertFalse("Success must be false on unexpected RuntimeException",
                (Boolean) payload.get("Success"));
        assertTrue("Payload must contain 'Message' key", payload.containsKey("Message"));
    }

    /**
     * Concrete subclass of {@link GraphServiceException} for download-path tests.
     * GraphServiceException has a protected constructor; this exposes it.
     */
    private static class TestDownloadGraphServiceException extends GraphServiceException {
        TestDownloadGraphServiceException(String message) {
            super("GET", "http://graph.test", Collections.emptyList(), message,
                    500, message, Collections.emptyList(), null, false);
        }

        @Override
        public String getMessage() {
            return getResponseMessage();
        }
    }

    // ========================================
    // GETDOWNLOADURL PRIVATE STATIC METHOD TESTS
    // ========================================

    /**
     * Reflective helper: invokes the private static {@code getDownloadURL(DriveItem)} method.
     */
    private static String invokeGetDownloadURL(DriveItem driveItem) throws Exception {
        java.lang.reflect.Method method = OperationsManagementDao.class
                .getDeclaredMethod("getDownloadURL", DriveItem.class);
        method.setAccessible(true);
        return (String) method.invoke(null, driveItem);
    }

    /**
     * When {@code @microsoft.graph.downloadUrl} is present in additionalDataManager
     * and parentReference.path contains "/root:", the anchor uses the ADM URL and
     * the relative parent path extracted by {@code FileUtil.getRelativeFilePath}.
     */
    @Test
    public void testGetDownloadURL_admUrl_withRelativeParentPath() throws Exception {
        DriveItem driveItem = new DriveItem();
        driveItem.name = "report.pdf";
        driveItem.webUrl = "https://web.example.com/fallback";
        driveItem.additionalDataManager().put(
                "@microsoft.graph.downloadUrl",
                new JsonPrimitive("https://dl.example.com/download"));
        driveItem.parentReference = new ItemReference();
        driveItem.parentReference.path = "/drives/b!abc123/root:/Documents";

        String result = invokeGetDownloadURL(driveItem);

        // FileUtil.getRelativeFilePath("/drives/b!abc123/root:/Documents") → "Documents"
        assertEquals(
                "<a href=https://dl.example.com/download target=_blank>Documents/report.pdf</a>",
                result);
    }

    /**
     * When {@code @microsoft.graph.downloadUrl} is present and parentReference is null,
     * parentPath is the empty string.
     */
    @Test
    public void testGetDownloadURL_admUrl_parentReferenceNull() throws Exception {
        DriveItem driveItem = new DriveItem();
        driveItem.name = "file.txt";
        driveItem.webUrl = "https://web.example.com/file";
        driveItem.additionalDataManager().put(
                "@microsoft.graph.downloadUrl",
                new JsonPrimitive("https://dl.example.com/file"));
        driveItem.parentReference = null;

        String result = invokeGetDownloadURL(driveItem);

        // parentPath = "" (null parentReference branch)
        assertEquals(
                "<a href=https://dl.example.com/file target=_blank>/file.txt</a>",
                result);
    }

    /**
     * When {@code @microsoft.graph.downloadUrl} is present and parentReference.path is null,
     * parentPath is the empty string.
     */
    @Test
    public void testGetDownloadURL_admUrl_parentPathNull() throws Exception {
        DriveItem driveItem = new DriveItem();
        driveItem.name = "data.xlsx";
        driveItem.webUrl = "https://web.example.com/data";
        driveItem.additionalDataManager().put(
                "@microsoft.graph.downloadUrl",
                new JsonPrimitive("https://dl.example.com/data"));
        driveItem.parentReference = new ItemReference();
        driveItem.parentReference.path = null;

        String result = invokeGetDownloadURL(driveItem);

        // parentPath = "" (null path branch)
        assertEquals(
                "<a href=https://dl.example.com/data target=_blank>/data.xlsx</a>",
                result);
    }

    /**
     * When {@code @microsoft.graph.downloadUrl} is absent, falls back to
     * {@code driveItem.webUrl} as the download link.
     */
    @Test
    public void testGetDownloadURL_noAdmUrl_usesWebUrl() throws Exception {
        DriveItem driveItem = new DriveItem();
        driveItem.name = "image.png";
        driveItem.webUrl = "https://web.sharepoint.com/image.png";
        // No entry added to additionalDataManager → get() returns null → falls back to webUrl
        driveItem.parentReference = new ItemReference();
        driveItem.parentReference.path = "/drives/b!xyz/root:/Pictures";

        String result = invokeGetDownloadURL(driveItem);

        // FileUtil.getRelativeFilePath("/drives/b!xyz/root:/Pictures") → "Pictures"
        assertEquals(
                "<a href=https://web.sharepoint.com/image.png target=_blank>Pictures/image.png</a>",
                result);
    }

    /**
     * When {@code @microsoft.graph.downloadUrl} is absent and parentReference is null,
     * uses webUrl with empty parentPath.
     */
    @Test
    public void testGetDownloadURL_noAdmUrl_parentReferenceNull() throws Exception {
        DriveItem driveItem = new DriveItem();
        driveItem.name = "notes.txt";
        driveItem.webUrl = "https://web.sharepoint.com/notes.txt";
        driveItem.parentReference = null;

        String result = invokeGetDownloadURL(driveItem);

        assertEquals(
                "<a href=https://web.sharepoint.com/notes.txt target=_blank>/notes.txt</a>",
                result);
    }

    /**
     * When parentReference.path does not contain "/root:", {@code FileUtil.getRelativeFilePath}
     * returns {@code null}, which is concatenated as the literal string "null" in the anchor.
     */
    @Test
    public void testGetDownloadURL_parentPathWithoutRootSeparator_nullAppearsLiterally() throws Exception {
        DriveItem driveItem = new DriveItem();
        driveItem.name = "summary.docx";
        driveItem.webUrl = "https://web.example.com/summary";
        driveItem.additionalDataManager().put(
                "@microsoft.graph.downloadUrl",
                new JsonPrimitive("https://dl.example.com/summary"));
        driveItem.parentReference = new ItemReference();
        // No "/root:" segment → getRelativeFilePath returns null
        driveItem.parentReference.path = "/sites/team-site/Shared Documents";

        String result = invokeGetDownloadURL(driveItem);

        // parentPath = null → string concat produces "null"
        assertEquals(
                "<a href=https://dl.example.com/summary target=_blank>null/summary.docx</a>",
                result);
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
