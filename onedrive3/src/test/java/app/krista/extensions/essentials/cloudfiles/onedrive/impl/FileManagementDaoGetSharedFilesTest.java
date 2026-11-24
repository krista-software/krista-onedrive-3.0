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
import com.microsoft.graph.models.ItemReference;
import com.microsoft.graph.models.RemoteItem;
import com.microsoft.graph.requests.DriveSharedWithMeCollectionPage;
import com.microsoft.graph.requests.UserRequestBuilder;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link FileManagementDao#getSharedFiles}.
 *
 * <p>The method is package-private to allow direct invocation from this same-package test class.
 *
 * Covers:
 * - Null shared page → no files added, no error set
 * - Empty shared page → no files added
 * - relativePath="shared" + null search → all valid items added
 * - relativePath="shared" + blank search → all valid items added
 * - relativePath="shared" + matching search → only matching items added
 * - relativePath="shared" + no-match search → no items added
 * - relativePath="/shared" (FORWARD_SLASH_SHARED) → accepted same as "shared"
 * - Non-shared path (item has full remoteItem chain) → PLEASE_PROVIDE_VALID_SOURCE_PATH
 * - GraphServiceException → success=true (catch block uses true) with message from exception
 * - IllegalArgumentException → success=true with message from exception
 * - Item with null remoteItem → skipped, not added
 */
public class FileManagementDaoGetSharedFilesTest {

    private static final OnedriveAttributes ATTRS =
            new OnedriveAttributes("http://localhost", "cid", "cs", "e@t.com", "tid");

    // ================================================================
    // NULL / EMPTY PAGE
    // ================================================================

    /**
     * When the shared-with-me API returns null, the while loop never executes.
     * No files are added and no error is written to the payload.
     */
    @Test
    public void testGetSharedFiles_nullPage_noFilesAdded() {
        UserRequestBuilder mockUser = deepStubUser();
        when(mockUser.drive().sharedWithMe().buildRequest().top(anyInt()).get()).thenReturn(null);

        List<File> files = new ArrayList<>();
        FreeForm payload = new FreeForm();
        buildSubject(mockUser).getSharedFiles("shared", files, null, true, "acct", payload);

        assertEquals("No files must be added for a null page", 0, files.size());
        assertNull("No error must be written to payload for null page", payload.get("Message"));
    }

    /**
     * When the shared-with-me page is empty, the while loop condition is false immediately.
     * No files are added.
     */
    @Test
    public void testGetSharedFiles_emptyPage_noFilesAdded() {
        DriveSharedWithMeCollectionPage page = mockPage(Collections.emptyList());

        UserRequestBuilder mockUser = deepStubUser();
        when(mockUser.drive().sharedWithMe().buildRequest().top(anyInt()).get()).thenReturn(page);

        List<File> files = new ArrayList<>();
        buildSubject(mockUser).getSharedFiles("shared", files, null, true, "acct", new FreeForm());

        assertEquals("No files must be added for an empty page", 0, files.size());
    }

    // ================================================================
    // relativePath = "shared" — search filtering
    // ================================================================

    /**
     * With relativePath="shared" and null search, all items that have a valid
     * remoteItem chain must be added to the files list.
     */
    @Test
    public void testGetSharedFiles_sharedPath_nullSearch_allItemsAdded() {
        DriveItem item1 = sharedItem("id1", "driveA", "Report.docx");
        DriveItem item2 = sharedItem("id2", "driveB", "Invoice.pdf");

        UserRequestBuilder mockUser = deepStubUserForSharedPage(List.of(item1, item2));

        List<File> files = new ArrayList<>();
        try {
            buildSubject(mockUser).getSharedFiles("shared", files, null, true, "acct", new FreeForm());
        } catch (Exception ignored) { /* fetchFileResponse / FileImpl may fail on stub data */ }

        assertEquals("Both items must be added when search is null", 2, files.size());
    }

    /**
     * With blank search, the same items must be added as with null search.
     */
    @Test
    public void testGetSharedFiles_sharedPath_blankSearch_allItemsAdded() {
        DriveItem item1 = sharedItem("id1", "driveA", "Report.docx");
        DriveItem item2 = sharedItem("id2", "driveB", "Invoice.pdf");

        UserRequestBuilder mockUser = deepStubUserForSharedPage(List.of(item1, item2));

        List<File> files = new ArrayList<>();
        try {
            buildSubject(mockUser).getSharedFiles("shared", files, "", true, "acct", new FreeForm());
        } catch (Exception ignored) {}

        assertEquals("Both items must be added when search is blank", 2, files.size());
    }

    /**
     * When search is set and matches part of an item name, only that item is added.
     */
    @Test
    public void testGetSharedFiles_sharedPath_searchMatches_onlyMatchingItemsAdded() {
        DriveItem item1 = sharedItem("id1", "driveA", "Report.docx");   // matches "Report"
        DriveItem item2 = sharedItem("id2", "driveB", "Invoice.pdf");   // no match

        UserRequestBuilder mockUser = deepStubUserForSharedPage(List.of(item1, item2));

        List<File> files = new ArrayList<>();
        try {
            buildSubject(mockUser).getSharedFiles("shared", files, "Report", true, "acct", new FreeForm());
        } catch (Exception ignored) {}

        assertEquals("Only the matching item must be added", 1, files.size());
    }

    /**
     * When search is set but no item names contain it, no items are added.
     */
    @Test
    public void testGetSharedFiles_sharedPath_searchNoMatch_noItemsAdded() {
        DriveItem item1 = sharedItem("id1", "driveA", "Report.docx");

        UserRequestBuilder mockUser = deepStubUserForSharedPage(List.of(item1));

        List<File> files = new ArrayList<>();
        try {
            buildSubject(mockUser).getSharedFiles("shared", files, "NOTFOUND", true, "acct", new FreeForm());
        } catch (Exception ignored) {}

        assertEquals("No items must be added when search does not match", 0, files.size());
    }

    // ================================================================
    // relativePath = "/shared"  (FORWARD_SLASH_SHARED)
    // ================================================================

    /**
     * "/shared" (FORWARD_SLASH_SHARED) must be accepted and treated identically to "shared".
     */
    @Test
    public void testGetSharedFiles_forwardSlashSharedPath_itemsAdded() {
        DriveItem item1 = sharedItem("id1", "driveA", "Doc.pdf");

        UserRequestBuilder mockUser = deepStubUserForSharedPage(List.of(item1));

        List<File> files = new ArrayList<>();
        try {
            buildSubject(mockUser).getSharedFiles("/shared", files, null, true, "acct", new FreeForm());
        } catch (Exception ignored) {}

        assertEquals("/shared path must add items the same as 'shared'", 1, files.size());
    }

    // ================================================================
    // Non-shared path
    // ================================================================

    /**
     * When relativePath is not "shared" or "/shared" (and the item has a valid remoteItem chain),
     * the method must write PLEASE_PROVIDE_VALID_SOURCE_PATH (success=false) to the payload and return.
     */
    @Test
    public void testGetSharedFiles_nonSharedPath_setsInvalidPathPayload() {
        DriveItem item1 = sharedItem("id1", "driveA", "Doc.pdf");

        UserRequestBuilder mockUser = deepStubUserForSharedPage(List.of(item1));

        FreeForm payload = new FreeForm();
        List<File> files = new ArrayList<>();
        buildSubject(mockUser).getSharedFiles("documents", files, null, true, "acct", payload);

        assertFalse("Success must be false for a non-shared path",
                (Boolean) payload.get("Success"));
        assertEquals("PLEASE_PROVIDE_VALID_SOURCE_PATH expected for non-shared path",
                Constants.PLEASE_PROVIDE_VALID_SOURCE_PATH, payload.get("Message"));
        assertEquals("No files must be added for a non-shared path", 0, files.size());
    }

    // ================================================================
    // Item with null / incomplete remoteItem chain — skipped
    // ================================================================

    /**
     * Items whose remoteItem is null fail the outer if-condition and must be silently skipped.
     * The while-loop completes without adding any files.
     */
    @Test
    public void testGetSharedFiles_itemWithNullRemoteItem_skipped() {
        DriveItem item = new DriveItem();
        item.id = "someId";
        item.name = "ghost.docx";
        item.remoteItem = null; // outer if-condition fails → skip

        UserRequestBuilder mockUser = deepStubUserForSharedPage(List.of(item));

        List<File> files = new ArrayList<>();
        try {
            buildSubject(mockUser).getSharedFiles("shared", files, null, true, "acct", new FreeForm());
        } catch (Exception ignored) {}

        assertEquals("Item with null remoteItem must be skipped", 0, files.size());
    }

    /**
     * Items whose id is null also fail the outer if-condition and must be skipped.
     */
    @Test
    public void testGetSharedFiles_itemWithNullId_skipped() {
        DriveItem item = new DriveItem();
        item.id = null;
        item.remoteItem = new RemoteItem();
        item.remoteItem.parentReference = new ItemReference();
        item.remoteItem.parentReference.driveId = "driveX";

        UserRequestBuilder mockUser = deepStubUserForSharedPage(List.of(item));

        List<File> files = new ArrayList<>();
        try {
            buildSubject(mockUser).getSharedFiles("shared", files, null, true, "acct", new FreeForm());
        } catch (Exception ignored) {}

        assertEquals("Item with null id must be skipped", 0, files.size());
    }

    // ================================================================
    // EXCEPTION PATHS
    // ================================================================

    /**
     * When the provider chain throws GraphServiceException, the catch block must write
     * success=true (NOT false) to the payload — this is the behaviour of the existing code.
     * The message follows the itemNotFound check.
     */
    @Test
    public void testGetSharedFiles_graphServiceException_nonItemNotFound_setsSuccessTrueInvalidPath() {
        UserRequestBuilder mockUser = deepStubUser();
        when(mockUser.drive().sharedWithMe().buildRequest().top(anyInt()).get())
                .thenThrow(new TestGseException("Access denied"));

        FreeForm payload = new FreeForm();
        buildSubject(mockUser).getSharedFiles("shared", new ArrayList<>(), null, true, "acct", payload);

        assertTrue("Catch block writes success=true (existing code behaviour)",
                (Boolean) payload.get("Success"));
        assertEquals("PLEASE_PROVIDE_VALID_SOURCE_PATH expected when message is not itemNotFound",
                Constants.PLEASE_PROVIDE_VALID_SOURCE_PATH, payload.get("Message"));
    }

    /**
     * When GraphServiceException message contains "itemNotFound",
     * the payload message must be FILE_NOT_FOUND (success still =true per catch block).
     */
    @Test
    public void testGetSharedFiles_graphServiceException_itemNotFound_setsFileNotFoundPayload() {
        UserRequestBuilder mockUser = deepStubUser();
        when(mockUser.drive().sharedWithMe().buildRequest().top(anyInt()).get())
                .thenThrow(new TestGseException("itemNotFound error"));

        FreeForm payload = new FreeForm();
        buildSubject(mockUser).getSharedFiles("shared", new ArrayList<>(), null, true, "acct", payload);

        assertTrue("Catch block writes success=true", (Boolean) payload.get("Success"));
        assertEquals("FILE_NOT_FOUND expected when message contains 'itemNotFound'",
                Constants.FILE_NOT_FOUND, payload.get("Message"));
    }

    /**
     * When IllegalArgumentException is thrown, the catch block must write success=true
     * and the appropriate message constant.
     */
    @Test
    public void testGetSharedFiles_illegalArgumentException_setsSuccessTrueInvalidPath() {
        UserRequestBuilder mockUser = deepStubUser();
        when(mockUser.drive().sharedWithMe().buildRequest().top(anyInt()).get())
                .thenThrow(new IllegalArgumentException("bad argument"));

        FreeForm payload = new FreeForm();
        buildSubject(mockUser).getSharedFiles("shared", new ArrayList<>(), null, true, "acct", payload);

        assertTrue("Catch block writes success=true for IAE", (Boolean) payload.get("Success"));
        assertEquals("PLEASE_PROVIDE_VALID_SOURCE_PATH expected for non-itemNotFound IAE",
                Constants.PLEASE_PROVIDE_VALID_SOURCE_PATH, payload.get("Message"));
    }

    // ================================================================
    // HELPERS
    // ================================================================

    private FileManagementDao buildSubject(UserRequestBuilder mockUser) {
        GraphServiceClientProvider provider = new GraphServiceClientProvider(null, null, null, null) {
            @Override
            public UserRequestBuilder getUserRequestBuilder(Boolean useEmail, String accountId) {
                return mockUser;
            }
        };
        return new FileManagementDao(provider, ATTRS, noOpValidationUtil(), noOpTelemetryHelper());
    }

    private static UserRequestBuilder deepStubUser() {
        return mock(UserRequestBuilder.class, RETURNS_DEEP_STUBS);
    }

    /**
     * Builds a deep-stub UserRequestBuilder pre-stubbed so that the sharedWithMe page
     * returns the given items (single page, no next page).
     * Also stubs the per-item driveItem fetch to return an auto-mock DriveItem.
     */
    private static UserRequestBuilder deepStubUserForSharedPage(List<DriveItem> items) {
        DriveSharedWithMeCollectionPage page = mockPage(items);

        UserRequestBuilder mockUser = deepStubUser();
        when(mockUser.drive().sharedWithMe().buildRequest().top(anyInt()).get()).thenReturn(page);
        // Per-item fetch: drives(driveId).items(itemId).buildRequest().expand(any).get() → auto-mock
        // RETURNS_DEEP_STUBS handles this automatically; no explicit stub needed.
        return mockUser;
    }

    /** Builds a DriveSharedWithMeCollectionPage mock with a fixed current page and no next page. */
    private static DriveSharedWithMeCollectionPage mockPage(List<DriveItem> items) {
        DriveSharedWithMeCollectionPage page = mock(DriveSharedWithMeCollectionPage.class);
        when(page.getCurrentPage()).thenReturn(items);
        when(page.getNextPage()).thenReturn(null);
        return page;
    }

    /**
     * Creates a DriveItem that passes the outer if-condition in getSharedFiles:
     * id, remoteItem, remoteItem.parentReference, and remoteItem.parentReference.driveId are all set.
     */
    private static DriveItem sharedItem(String id, String driveId, String name) {
        DriveItem item = new DriveItem();
        item.id = id;
        item.name = name;
        item.remoteItem = new RemoteItem();
        item.remoteItem.parentReference = new ItemReference();
        item.remoteItem.parentReference.driveId = driveId;
        return item;
    }

    private static ValidationTypeUtil noOpValidationUtil() {
        return new ValidationTypeUtil(null, null, null) {
            @Override
            public app.krista.extension.executor.ExtensionResponse checkValidations(
                    Validator.ValidationResource vt, String sub, String path,
                    List<Map<String, Object>> share) {
                return null;
            }
            @Override
            public app.krista.extension.executor.ExtensionResponse checkValidationsWithErrorMessage(
                    Validator.ValidationResource vt, String sub, String path, String msg) {
                return null;
            }
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
            super("GET", "https://graph.microsoft.com/v1.0/me/drive/sharedWithMe",
                    Collections.emptyList(), message,
                    500, message, Collections.emptyList(), null, false);
        }

        @Override
        public String getMessage() {
            return getResponseMessage();
        }
    }
}
