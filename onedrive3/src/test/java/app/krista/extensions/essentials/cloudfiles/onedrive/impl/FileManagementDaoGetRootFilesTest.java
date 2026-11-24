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
import app.krista.extensions.essentials.cloudfiles.onedrive.catalog.validators.Validator;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.connectors.GraphServiceClientProvider;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.util.Constants;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.util.ValidationTypeUtil;
import app.krista.extensions.essentials.cloudfiles.onedrive.service.File;
import app.krista.model.base.FreeForm;
import com.microsoft.graph.http.GraphServiceException;
import com.microsoft.graph.models.DriveItem;
import com.microsoft.graph.models.Folder;
import com.microsoft.graph.requests.DriveItemCollectionPage;
import com.microsoft.graph.requests.DriveItemRequestBuilder;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link FileManagementDao#getRootFilesByPath}.
 *
 * <p>{@code getRequestBuilder} is package-private, allowing anonymous subclasses in this
 * same-package test to override it and inject a controlled {@link DriveItemRequestBuilder}.
 * Real {@link DriveItemCollectionPage} instances (not Mockito mocks) are used for the children
 * page to avoid deep-stub generic-type-erasure cast failures.
 *
 * Covers:
 * - Empty folder page → FOLDER_IS_EMPTY payload
 * - Null children page → no files added, no error
 * - Folder with items + null search → all items added
 * - Folder with items + blank search → all items added
 * - Folder with items + matching search → only matching items added
 * - Folder with items + no-match search → no items added
 * - Item with null id → silently skipped
 * - GraphServiceException with "itemNotFound" → FILE_NOT_FOUND payload
 * - GraphServiceException without "itemNotFound" → PLEASE_PROVIDE_VALID_SOURCE_PATH payload
 * - IllegalArgumentException with non-itemNotFound message → PLEASE_PROVIDE_VALID_SOURCE_PATH
 * - IllegalArgumentException with "itemNotFound" message → FILE_NOT_FOUND
 */
public class FileManagementDaoGetRootFilesTest {

    private static final OnedriveAttributes ATTRS =
            new OnedriveAttributes("http://localhost", "cid", "cs", "e@t.com", "tid");

    // ================================================================
    // EMPTY FOLDER
    // ================================================================

    /**
     * When the folder page is empty, emptyFolderResponse must be called with
     * success=true and FOLDER_IS_EMPTY as the message.
     */
    @Test
    public void testGetRootFilesByPath_emptyPage_setsEmptyFolderPayload() {
        DriveItem folderItem = folderItem("folderId");
        // Real DriveItemCollectionPage avoids Mockito deep-stub generic-type-erasure cast failures
        DriveItemCollectionPage emptyPage = new DriveItemCollectionPage(Collections.emptyList(), null);

        DriveItemRequestBuilder mockBuilder = builderFor(folderItem, emptyPage);

        FreeForm payload = new FreeForm();
        buildSubject(mockBuilder).getRootFilesByPath("", new ArrayList<>(), true, "acct", null, payload);

        assertTrue("emptyFolderResponse sets success=true", (Boolean) payload.get("Success"));
        assertEquals("FOLDER_IS_EMPTY message expected", Constants.FOLDER_IS_EMPTY, payload.get("Message"));
    }

    /**
     * When the children page is null, the while loop never executes.
     * No files are added and no error is written to the payload.
     */
    @Test
    public void testGetRootFilesByPath_nullPage_noFilesAddedNorErrorSet() {
        DriveItem folderItem = folderItem("folderId");
        DriveItemRequestBuilder mockBuilder = mock(DriveItemRequestBuilder.class, RETURNS_DEEP_STUBS);
        when(mockBuilder.buildRequest().expand(anyString()).get()).thenReturn(folderItem);
        // Extract intermediate mock OUTSIDE when() to avoid UnfinishedStubbingException;
        // doReturn avoids calling .get() directly (bypasses type-erasure ClassCastException)
        var childrenReqMock = mockBuilder.children().buildRequest();
        doReturn(null).when(childrenReqMock).get();

        FreeForm payload = new FreeForm();
        List<File> files = new ArrayList<>();
        buildSubject(mockBuilder).getRootFilesByPath("", files, true, "acct", null, payload);

        assertEquals("No files must be added when page is null", 0, files.size());
        assertNull("Payload must not have an error message set", payload.get("Message"));
    }

    // ================================================================
    // FOLDER WITH ITEMS — search filtering
    // ================================================================

    /**
     * When search is null, all page items (with non-null id) must be added to the list.
     */
    @Test
    public void testGetRootFilesByPath_nullSearch_allItemsAdded() {
        DriveItem item1 = fileItem("id1", "Report.docx");
        DriveItem item2 = fileItem("id2", "Invoice.pdf");

        List<File> files = new ArrayList<>();
        try {
            buildSubject(builderForItems(List.of(item1, item2)))
                    .getRootFilesByPath("", files, true, "acct", null, new FreeForm());
        } catch (Exception ignored) { /* fetchFileResponse may throw with stub DriveItem data */ }

        assertEquals("Both items must be added when search is null", 2, files.size());
    }

    /**
     * When search is an empty string (blank), all items must be added — same as null search.
     */
    @Test
    public void testGetRootFilesByPath_blankSearch_allItemsAdded() {
        DriveItem item1 = fileItem("id1", "A.txt");
        DriveItem item2 = fileItem("id2", "B.txt");

        List<File> files = new ArrayList<>();
        try {
            buildSubject(builderForItems(List.of(item1, item2)))
                    .getRootFilesByPath("", files, true, "acct", "", new FreeForm());
        } catch (Exception ignored) {}

        assertEquals("Both items must be added when search is blank", 2, files.size());
    }

    /**
     * When search is set and matches some item names, only the matching items are added.
     */
    @Test
    public void testGetRootFilesByPath_searchMatches_onlyMatchingItemsAdded() {
        DriveItem item1 = fileItem("id1", "Report.docx");  // matches "Report"
        DriveItem item2 = fileItem("id2", "Invoice.pdf");  // no match

        List<File> files = new ArrayList<>();
        try {
            buildSubject(builderForItems(List.of(item1, item2)))
                    .getRootFilesByPath("", files, true, "acct", "Report", new FreeForm());
        } catch (Exception ignored) {}

        assertEquals("Only the matching item must be added", 1, files.size());
    }

    /**
     * When search is set but no item names contain it, no items are added.
     */
    @Test
    public void testGetRootFilesByPath_searchNoMatch_noItemsAdded() {
        DriveItem item1 = fileItem("id1", "Report.docx");

        List<File> files = new ArrayList<>();
        try {
            buildSubject(builderForItems(List.of(item1)))
                    .getRootFilesByPath("", files, true, "acct", "NOTFOUND", new FreeForm());
        } catch (Exception ignored) {}

        assertEquals("No items must be added when search does not match", 0, files.size());
    }

    /**
     * Items with a null id must be silently skipped regardless of search value.
     */
    @Test
    public void testGetRootFilesByPath_itemWithNullId_skipped() {
        DriveItem nullIdItem = new DriveItem();
        nullIdItem.id = null;
        nullIdItem.name = "ghost.txt";

        List<File> files = new ArrayList<>();
        try {
            buildSubject(builderForItems(List.of(nullIdItem)))
                    .getRootFilesByPath("", files, true, "acct", null, new FreeForm());
        } catch (Exception ignored) {}

        assertEquals("Item with null id must be skipped", 0, files.size());
    }

    // ================================================================
    // EXCEPTION PATHS
    // ================================================================

    /**
     * GraphServiceException whose message contains "itemNotFound" →
     * payload success=false, message=FILE_NOT_FOUND.
     */
    @Test
    public void testGetRootFilesByPath_graphServiceException_itemNotFound_setsFileNotFoundPayload() {
        DriveItemRequestBuilder throwingBuilder = mock(DriveItemRequestBuilder.class, RETURNS_DEEP_STUBS);
        when(throwingBuilder.buildRequest().expand(anyString()).get())
                .thenThrow(new TestGseException("itemNotFound error"));

        FreeForm payload = new FreeForm();
        buildSubject(throwingBuilder).getRootFilesByPath("", new ArrayList<>(), true, "acct", null, payload);

        assertFalse("Success must be false on GraphServiceException", (Boolean) payload.get("Success"));
        assertEquals("FILE_NOT_FOUND expected when message contains 'itemNotFound'",
                Constants.FILE_NOT_FOUND, payload.get("Message"));
    }

    /**
     * GraphServiceException whose message does NOT contain "itemNotFound" →
     * payload success=false, message=PLEASE_PROVIDE_VALID_SOURCE_PATH.
     */
    @Test
    public void testGetRootFilesByPath_graphServiceException_notItemNotFound_setsInvalidPathPayload() {
        DriveItemRequestBuilder throwingBuilder = mock(DriveItemRequestBuilder.class, RETURNS_DEEP_STUBS);
        when(throwingBuilder.buildRequest().expand(anyString()).get())
                .thenThrow(new TestGseException("Access denied"));

        FreeForm payload = new FreeForm();
        buildSubject(throwingBuilder).getRootFilesByPath("", new ArrayList<>(), true, "acct", null, payload);

        assertFalse("Success must be false on GraphServiceException", (Boolean) payload.get("Success"));
        assertEquals("PLEASE_PROVIDE_VALID_SOURCE_PATH expected for non-itemNotFound GSE",
                Constants.PLEASE_PROVIDE_VALID_SOURCE_PATH, payload.get("Message"));
    }

    /**
     * IllegalArgumentException with a non-itemNotFound message →
     * payload success=false, message=PLEASE_PROVIDE_VALID_SOURCE_PATH.
     */
    @Test
    public void testGetRootFilesByPath_illegalArgumentException_setsInvalidPathPayload() {
        DriveItemRequestBuilder throwingBuilder = mock(DriveItemRequestBuilder.class, RETURNS_DEEP_STUBS);
        when(throwingBuilder.buildRequest().expand(anyString()).get())
                .thenThrow(new IllegalArgumentException("bad argument"));

        FreeForm payload = new FreeForm();
        buildSubject(throwingBuilder).getRootFilesByPath("", new ArrayList<>(), true, "acct", null, payload);

        assertFalse("Success must be false on IllegalArgumentException", (Boolean) payload.get("Success"));
        assertEquals("PLEASE_PROVIDE_VALID_SOURCE_PATH expected for IAE without itemNotFound",
                Constants.PLEASE_PROVIDE_VALID_SOURCE_PATH, payload.get("Message"));
    }

    /**
     * IllegalArgumentException whose message contains "itemNotFound" →
     * the same condition applies as for GSE: payload message=FILE_NOT_FOUND.
     */
    @Test
    public void testGetRootFilesByPath_illegalArgumentException_itemNotFound_setsFileNotFoundPayload() {
        DriveItemRequestBuilder throwingBuilder = mock(DriveItemRequestBuilder.class, RETURNS_DEEP_STUBS);
        when(throwingBuilder.buildRequest().expand(anyString()).get())
                .thenThrow(new IllegalArgumentException("itemNotFound at path"));

        FreeForm payload = new FreeForm();
        buildSubject(throwingBuilder).getRootFilesByPath("", new ArrayList<>(), true, "acct", null, payload);

        assertFalse("Success must be false", (Boolean) payload.get("Success"));
        assertEquals("FILE_NOT_FOUND expected when IAE message contains 'itemNotFound'",
                Constants.FILE_NOT_FOUND, payload.get("Message"));
    }

    // ================================================================
    // HELPERS
    // ================================================================

    /**
     * Builds a FileManagementDao whose {@code getRequestBuilder} always returns the given
     * {@code mockBuilder}. {@code getPermissionCollectionPage} is also overridden to return
     * null (avoiding provider chain calls inside the items loop).
     */
    private FileManagementDao buildSubject(DriveItemRequestBuilder mockBuilder) {
        return new FileManagementDao(
                /* provider — not used since getRequestBuilder is overridden */
                new GraphServiceClientProvider(null, null, null, null) {
                    @Override
                    public com.microsoft.graph.requests.UserRequestBuilder getUserRequestBuilder(
                            Boolean useEmail, String accountId) {
                        return null;
                    }
                },
                ATTRS, noOpValidationUtil(), noOpTelemetryHelper()) {

            @Override
            DriveItemRequestBuilder getRequestBuilder(boolean useEmail, String accountID) {
                return mockBuilder;
            }

            @Override
            com.microsoft.graph.requests.PermissionCollectionPage getPermissionCollectionPage(
                    boolean useEmail, String accountID, DriveItem driveInPage) {
                return null; // permissions not under test here
            }
        };
    }

    /**
     * Creates a RETURNS_DEEP_STUBS mock stubbed for:
     * - driveItem lookup → {@code driveItem}
     * - children page   → {@code page} (real DriveItemCollectionPage instance)
     *
     * <p>The children chain extracts the intermediate mock reference OUTSIDE any {@code when()}
     * wrapper to avoid {@code UnfinishedStubbingException}, and uses {@code doReturn} to avoid
     * calling {@code .get()} during stub registration (bypasses the Mockito deep-stub
     * generic type-erasure {@code ClassCastException}).
     */
    private static DriveItemRequestBuilder builderFor(DriveItem driveItem, DriveItemCollectionPage page) {
        DriveItemRequestBuilder mock = mock(DriveItemRequestBuilder.class, RETURNS_DEEP_STUBS);
        when(mock.buildRequest().expand(anyString()).get()).thenReturn(driveItem);
        // Extract intermediate mock before doReturn to avoid UnfinishedStubbingException
        var childrenReqMock = mock.children().buildRequest();
        doReturn(page).when(childrenReqMock).get();
        return mock;
    }

    /**
     * Convenience: creates a folder DriveItem and a single-page children list, then
     * wires both into a stubbed builder.
     */
    private static DriveItemRequestBuilder builderForItems(List<DriveItem> items) {
        DriveItem folder = folderItem("fId");
        // Real DriveItemCollectionPage avoids Mockito deep-stub generic-type-erasure cast failures
        DriveItemCollectionPage page = new DriveItemCollectionPage(items, null);
        return builderFor(folder, page);
    }

    /** Creates a DriveItem recognised as a folder (has id and folder set). */
    private static DriveItem folderItem(String id) {
        DriveItem item = new DriveItem();
        item.id = id;
        item.folder = new Folder();
        return item;
    }

    /** Creates a file DriveItem (no folder field). */
    private static DriveItem fileItem(String id, String name) {
        DriveItem item = new DriveItem();
        item.id = id;
        item.name = name;
        return item;
    }

    private static ValidationTypeUtil noOpValidationUtil() {
        return new ValidationTypeUtil(null, null, null) {
            @Override
            public app.krista.extension.executor.ExtensionResponse checkValidations(
                    Validator.ValidationResource vt, String sub, String path,
                    List<Map<String, Object>> share) { return null; }
            @Override
            public app.krista.extension.executor.ExtensionResponse checkValidationsWithErrorMessage(
                    Validator.ValidationResource vt, String sub, String path, String msg) { return null; }
        };
    }

    private static TelemetryHelper noOpTelemetryHelper() {
        return new TelemetryHelper(null) {
            @Override public void incrementCount(String m) {}
            @Override public void recordSuccess(String op, long t, Map<String, String> tags) {}
            @Override public void recordError(String op, long t, Exception e, Map<String, String> tags) {}
            @Override public void recordValidationError(String op, long t, String r, Map<String, String> tags) {}
            @Override public void recordRetryPrompted(String op, long t, Map<String, String> tags) {}
        };
    }

    /**
     * Concrete subclass of GraphServiceException (protected constructor) for test use.
     * getMessage() delegates to getResponseMessage() to avoid NPE when error body is null.
     */
    private static class TestGseException extends GraphServiceException {
        TestGseException(String message) {
            super("GET", "https://graph.microsoft.com/v1.0/me/drive/root",
                    Collections.emptyList(), message,
                    500, message, Collections.emptyList(), null, false);
        }

        @Override
        public String getMessage() {
            return getResponseMessage();
        }
    }
}
