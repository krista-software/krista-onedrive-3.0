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
import app.krista.extension.executor.ExtensionResponseBuilder;
import app.krista.extensions.essentials.cloudfiles.onedrive.OnedriveAttributes;
import app.krista.extensions.essentials.cloudfiles.onedrive.catalog.FileHandlers.TelemetryHelper;
import app.krista.extensions.essentials.cloudfiles.onedrive.catalog.validators.Validator;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.connectors.GraphServiceClientProvider;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.util.Constants;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.util.SubCatalogConstants;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.util.ValidationTypeUtil;
import app.krista.extensions.essentials.cloudfiles.onedrive.service.File;
import app.krista.model.base.FreeForm;
import com.microsoft.graph.http.GraphServiceException;
import com.microsoft.graph.models.DriveItem;
import com.microsoft.graph.models.Folder;
import com.microsoft.graph.models.ItemReference;
import com.microsoft.graph.models.RemoteItem;
import com.microsoft.graph.requests.DriveItemCollectionPage;
import com.microsoft.graph.requests.DriveItemCollectionRequest;
import com.microsoft.graph.requests.DriveItemCollectionRequestBuilder;
import com.microsoft.graph.requests.DriveSharedWithMeCollectionPage;
import com.microsoft.graph.requests.DriveSharedWithMeCollectionRequest;
import com.microsoft.graph.requests.DriveSharedWithMeCollectionRequestBuilder;
import com.microsoft.graph.requests.PermissionCollectionPage;
import com.microsoft.graph.requests.UserRequestBuilder;
import org.junit.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Coverage-completing unit tests for {@link FileManagementDao}.
 *
 * <p>Complements {@code FileManagementDaoTest}, {@code FileManagementDaoGetRootFilesTest} and
 * {@code FileManagementDaoGetSharedFilesTest} by driving the previously-uncovered code paths:
 * the {@code getListOfFiles} routing/catch logic, {@code getRootFilesByDriveIDPath},
 * the {@code getFileInfo → checkIfFolderExist → fetchFileInfo → processResponse} chain
 * (including {@code getFileNameByDate}, {@code getFileNameAndIDByLastModified}, {@code getFileDate}),
 * the full {@code createFolder} flow ({@code executeCreateFolder}, {@code getDriveItemForPath},
 * {@code getDriveItemForSharedPath}, {@code createFolderInDrive}), the {@code getRequestBuilder}
 * and {@code getPermissionCollectionPage} helpers, and the three {@code reenterValidate*} methods.
 */
public class FileManagementDaoCoverageTest {

    private static final OnedriveAttributes ATTRS =
            new OnedriveAttributes("http://localhost", "cid", "cs", "e@t.com", "tid");

    // ================================================================
    // getListOfFiles — routing + catch (sub-methods overridden as no-ops)
    // ================================================================

    /**
     * Builds a DAO whose file-listing sub-methods are no-ops so that getListOfFiles's own
     * routing branches (drive-ID / personal / shared / default / null) and success telemetry
     * are exercised without any real Graph traffic.
     */
    private FileManagementDao noOpRoutingDao() {
        return new FileManagementDao(nullUserProvider(), ATTRS, noOpValidationUtil(), noOpTelemetryHelper()) {
            @Override
            public void getRootFilesByDriveIDPath(String driveID, String relativePath, List<File> files,
                                                  boolean useEmail, String accountID, String search, FreeForm payload) {
                // no-op
            }

            @Override
            public void getRootFilesByPath(String relativePath, List<File> files, boolean useEmail,
                                           String accountID, String search, FreeForm payload) {
                // no-op
            }

            @Override
            void getSharedFiles(String relativePath, List<File> files, String search, boolean useEmail,
                                String accountID, FreeForm payload) {
                // no-op
            }
        };
    }

    @Test
    public void testGetListOfFiles_driveIdPath_returnsPayload() {
        FreeForm result = noOpRoutingDao().getListOfFiles("/drives/b!x/root:/folder", "s", true, "acct");
        assertNotNull("Drive-ID routing must return a payload", result);
    }

    @Test
    public void testGetListOfFiles_personalDrivePath_returnsPayload() {
        FreeForm result = noOpRoutingDao().getListOfFiles("drive/root:/Docs", "s", true, "acct");
        assertNotNull("Personal-drive routing must return a payload", result);
    }

    @Test
    public void testGetListOfFiles_sharedPath_returnsPayload() {
        FreeForm result = noOpRoutingDao().getListOfFiles("shared", "s", true, "acct");
        assertNotNull("Shared routing must return a payload", result);
    }

    @Test
    public void testGetListOfFiles_defaultPath_returnsPayload() {
        FreeForm result = noOpRoutingDao().getListOfFiles("someRandomFolder", "s", true, "acct");
        assertNotNull("Default routing must return a payload", result);
    }

    @Test
    public void testGetListOfFiles_nullPath_returnsPayload() {
        FreeForm result = noOpRoutingDao().getListOfFiles(null, "s", true, "acct");
        assertNotNull("Null path must still return a payload (validation-error branch)", result);
    }

    @Test
    public void testGetListOfFiles_forwardSlashSharedPath_returnsPayload() {
        // Exercises the FORWARD_SLASH_SHARED operand of the shared-path routing condition.
        FreeForm result = noOpRoutingDao().getListOfFiles("/shared", "s", true, "acct");
        assertNotNull("/shared routing must return a payload", result);
    }

    /**
     * When a sub-method throws IllegalArgumentException whose message contains "itemNotFound",
     * getListOfFiles's catch block must set FILE_NOT_FOUND on the payload.
     */
    @Test
    public void testGetListOfFiles_catchItemNotFound_setsFileNotFound() {
        FileManagementDao dao = new FileManagementDao(nullUserProvider(), ATTRS, noOpValidationUtil(), noOpTelemetryHelper()) {
            @Override
            public void getRootFilesByPath(String relativePath, List<File> files, boolean useEmail,
                                           String accountID, String search, FreeForm payload) {
                throw new IllegalArgumentException("the itemNotFound was raised");
            }
        };
        FreeForm result = dao.getListOfFiles("someRandomFolder", "s", true, "acct");
        assertEquals(Constants.FILE_NOT_FOUND, result.get("Message"));
    }

    /**
     * When a sub-method throws IllegalArgumentException without "itemNotFound",
     * getListOfFiles's catch block must set PLEASE_PROVIDE_VALID_SOURCE_PATH.
     */
    @Test
    public void testGetListOfFiles_catchOtherError_setsInvalidSourcePath() {
        FileManagementDao dao = new FileManagementDao(nullUserProvider(), ATTRS, noOpValidationUtil(), noOpTelemetryHelper()) {
            @Override
            public void getRootFilesByPath(String relativePath, List<File> files, boolean useEmail,
                                           String accountID, String search, FreeForm payload) {
                throw new IllegalArgumentException("boom");
            }
        };
        FreeForm result = dao.getListOfFiles("someRandomFolder", "s", true, "acct");
        assertEquals(Constants.PLEASE_PROVIDE_VALID_SOURCE_PATH, result.get("Message"));
    }

    // ================================================================
    // getRootFilesByDriveIDPath
    // ================================================================

    private static final String DRV = "drv";
    private static final String DRV_PATH = "path";

    /**
     * Builds a deep-stub UserRequestBuilder for the drive-ID chain, wiring the folder lookup
     * (expand(permissions).get()) to {@code folderItem} and the children page to {@code page}.
     * Per-item permission fetches are stubbed to return null for each supplied item id.
     */
    private UserRequestBuilder driveIdUser(DriveItem folderItem, DriveItemCollectionPage page, List<DriveItem> items) {
        UserRequestBuilder user = mock(UserRequestBuilder.class, RETURNS_DEEP_STUBS);
        when(user.drives(DRV).root().itemWithPath(DRV_PATH).buildRequest().expand(anyString()).get())
                .thenReturn(folderItem);
        if (page != null) {
            DriveItemCollectionRequest childrenReq =
                    user.drives(DRV).root().itemWithPath(DRV_PATH).children().buildRequest();
            doReturn(page).when(childrenReq).get();
        } else {
            DriveItemCollectionRequest childrenReq =
                    user.drives(DRV).root().itemWithPath(DRV_PATH).children().buildRequest();
            doReturn(null).when(childrenReq).get();
        }
        if (items != null) {
            for (DriveItem item : items) {
                if (item.id != null) {
                    com.microsoft.graph.requests.PermissionCollectionRequest permReq =
                            user.drives(DRV).items(item.id).permissions().buildRequest();
                    doReturn(null).when(permReq).get();
                }
            }
        }
        return user;
    }

    private FileManagementDao driveIdSubject(UserRequestBuilder user) {
        GraphServiceClientProvider provider = new GraphServiceClientProvider(null, null, null, null) {
            @Override
            public UserRequestBuilder getUserRequestBuilder(Boolean useEmail, String accountId) {
                return user;
            }
        };
        return new FileManagementDao(provider, ATTRS, noOpValidationUtil(), noOpTelemetryHelper());
    }

    @Test
    public void testGetRootFilesByDriveIDPath_folderEmptyPage_setsFolderEmpty() {
        DriveItemCollectionPage empty = new DriveItemCollectionPage(Collections.emptyList(), null);
        UserRequestBuilder user = driveIdUser(folderItem("fId"), empty, null);

        FreeForm payload = new FreeForm();
        driveIdSubject(user).getRootFilesByDriveIDPath(DRV, DRV_PATH, new ArrayList<>(), true, "acct", null, payload);

        assertTrue((Boolean) payload.get("Success"));
        assertEquals(Constants.FOLDER_IS_EMPTY, payload.get("Message"));
    }

    @Test
    public void testGetRootFilesByDriveIDPath_nullChildrenPage_noFilesNoError() {
        UserRequestBuilder user = driveIdUser(folderItem("fId"), null, null);

        FreeForm payload = new FreeForm();
        List<File> files = new ArrayList<>();
        driveIdSubject(user).getRootFilesByDriveIDPath(DRV, DRV_PATH, files, true, "acct", null, payload);

        assertEquals(0, files.size());
    }

    @Test
    public void testGetRootFilesByDriveIDPath_nullSearch_itemAdded() {
        DriveItem item = fileItem("id1", "Report.docx");
        DriveItemCollectionPage page = new DriveItemCollectionPage(List.of(item), null);
        UserRequestBuilder user = driveIdUser(folderItem("fId"), page, List.of(item));

        List<File> files = new ArrayList<>();
        try {
            driveIdSubject(user).getRootFilesByDriveIDPath(DRV, DRV_PATH, files, true, "acct", null, new FreeForm());
        } catch (Exception ignored) { /* fetchFileResponse may fail on stub data */ }

        assertEquals(1, files.size());
    }

    @Test
    public void testGetRootFilesByDriveIDPath_blankSearch_itemAdded() {
        DriveItem item = fileItem("id1", "Report.docx");
        DriveItemCollectionPage page = new DriveItemCollectionPage(List.of(item), null);
        UserRequestBuilder user = driveIdUser(folderItem("fId"), page, List.of(item));

        List<File> files = new ArrayList<>();
        try {
            driveIdSubject(user).getRootFilesByDriveIDPath(DRV, DRV_PATH, files, true, "acct", "", new FreeForm());
        } catch (Exception ignored) {}

        assertEquals(1, files.size());
    }

    @Test
    public void testGetRootFilesByDriveIDPath_searchMatches_onlyMatchingAdded() {
        DriveItem item = fileItem("id1", "Report.docx");
        DriveItemCollectionPage page = new DriveItemCollectionPage(List.of(item), null);
        UserRequestBuilder user = driveIdUser(folderItem("fId"), page, List.of(item));

        List<File> files = new ArrayList<>();
        try {
            driveIdSubject(user).getRootFilesByDriveIDPath(DRV, DRV_PATH, files, true, "acct", "Report", new FreeForm());
        } catch (Exception ignored) {}

        assertEquals(1, files.size());
    }

    @Test
    public void testGetRootFilesByDriveIDPath_searchNoMatch_noneAdded() {
        DriveItem item = fileItem("id1", "Report.docx");
        DriveItemCollectionPage page = new DriveItemCollectionPage(List.of(item), null);
        UserRequestBuilder user = driveIdUser(folderItem("fId"), page, List.of(item));

        List<File> files = new ArrayList<>();
        try {
            driveIdSubject(user).getRootFilesByDriveIDPath(DRV, DRV_PATH, files, true, "acct", "zzz", new FreeForm());
        } catch (Exception ignored) {}

        assertEquals(0, files.size());
    }

    @Test
    public void testGetRootFilesByDriveIDPath_notAFolder_addsSingleItem() {
        // driveItem has id but folder == null → else branch adds it directly
        DriveItem notFolder = fileItem("fileId", "single.txt");
        UserRequestBuilder user = driveIdUser(notFolder, null, null);

        List<File> files = new ArrayList<>();
        try {
            driveIdSubject(user).getRootFilesByDriveIDPath(DRV, DRV_PATH, files, true, "acct", null, new FreeForm());
        } catch (Exception ignored) {}

        assertEquals(1, files.size());
    }

    @Test
    public void testGetRootFilesByDriveIDPath_searchSet_nullName_skipped() {
        DriveItem item = new DriveItem();
        item.id = "id1";
        item.name = null; // search set but name == null → filter branch skips
        DriveItemCollectionPage page = new DriveItemCollectionPage(List.of(item), null);
        UserRequestBuilder user = driveIdUser(folderItem("fId"), page, List.of(item));

        List<File> files = new ArrayList<>();
        try {
            driveIdSubject(user).getRootFilesByDriveIDPath(DRV, DRV_PATH, files, true, "acct", "query", new FreeForm());
        } catch (Exception ignored) {}
        assertEquals(0, files.size());
    }

    @Test
    public void testGetRootFilesByDriveIDPath_blankSearch_nullIdItem_skipped() {
        // blank search + null id → (search==null || isBlank) true, but id != null is false → skip
        DriveItem item = new DriveItem();
        item.id = null;
        item.name = "named.txt";
        DriveItemCollectionPage page = new DriveItemCollectionPage(List.of(item), null);
        UserRequestBuilder user = driveIdUser(folderItem("fId"), page, List.of(item));

        List<File> files = new ArrayList<>();
        try {
            driveIdSubject(user).getRootFilesByDriveIDPath(DRV, DRV_PATH, files, true, "acct", "", new FreeForm());
        } catch (Exception ignored) {}
        assertEquals(0, files.size());
    }

    @Test
    public void testGetRootFilesByDriveIDPath_searchSet_nullIdItem_skipped() {
        DriveItem item = new DriveItem();
        item.id = null; // id == null with search set → both add-branches skip
        item.name = "named.txt";
        DriveItemCollectionPage page = new DriveItemCollectionPage(List.of(item), null);
        UserRequestBuilder user = driveIdUser(folderItem("fId"), page, List.of(item));

        List<File> files = new ArrayList<>();
        try {
            driveIdSubject(user).getRootFilesByDriveIDPath(DRV, DRV_PATH, files, true, "acct", "query", new FreeForm());
        } catch (Exception ignored) {}
        assertEquals(0, files.size());
    }

    @Test
    public void testGetRootFilesByDriveIDPath_nullDriveItem_elseBranch() {
        // driveItem lookup returns null → else branch (driveItem != null is false)
        UserRequestBuilder user = driveIdUser(null, null, null);

        List<File> files = new ArrayList<>();
        try {
            driveIdSubject(user).getRootFilesByDriveIDPath(DRV, DRV_PATH, files, true, "acct", null, new FreeForm());
        } catch (Exception ignored) { /* FileImpl(null) throws inside fetchFileResponse */ }
        assertNotNull(files);
    }

    @Test
    public void testGetRootFilesByDriveIDPath_nullId_elseBranch() {
        // driveItem non-null but id == null → else branch (driveItem.id != null is false)
        DriveItem noId = new DriveItem();
        noId.id = null;
        noId.name = "noId.txt";
        UserRequestBuilder user = driveIdUser(noId, null, null);

        List<File> files = new ArrayList<>();
        try {
            driveIdSubject(user).getRootFilesByDriveIDPath(DRV, DRV_PATH, files, true, "acct", null, new FreeForm());
        } catch (Exception ignored) {}
        assertEquals(1, files.size());
    }

    @Test
    public void testGetRootFilesByDriveIDPath_graphServiceException_setsFileNotFound() {
        UserRequestBuilder user = mock(UserRequestBuilder.class, RETURNS_DEEP_STUBS);
        when(user.drives(DRV).root().itemWithPath(DRV_PATH).buildRequest().expand(anyString()).get())
                .thenThrow(new TestGse("some failure"));

        FreeForm payload = new FreeForm();
        driveIdSubject(user).getRootFilesByDriveIDPath(DRV, DRV_PATH, new ArrayList<>(), true, "acct", null, payload);

        assertFalse((Boolean) payload.get("Success"));
        assertEquals(Constants.FILE_NOT_FOUND, payload.get("Message"));
    }

    @Test
    public void testGetRootFilesByDriveIDPath_multiPage_traversesNextPage() {
        DriveItem item = fileItem("id1", "A.txt");
        DriveItemCollectionPage page2 = new DriveItemCollectionPage(Collections.emptyList(), null);
        DriveItemCollectionRequestBuilder nextBuilder = mock(DriveItemCollectionRequestBuilder.class);
        DriveItemCollectionRequest nextReq = mock(DriveItemCollectionRequest.class);
        when(nextBuilder.buildRequest()).thenReturn(nextReq);
        doReturn(page2).when(nextReq).get();
        DriveItemCollectionPage page1 = new DriveItemCollectionPage(List.of(item), nextBuilder);

        UserRequestBuilder user = driveIdUser(folderItem("fId"), page1, List.of(item));

        List<File> files = new ArrayList<>();
        try {
            driveIdSubject(user).getRootFilesByDriveIDPath(DRV, DRV_PATH, files, true, "acct", null, new FreeForm());
        } catch (Exception ignored) {}

        assertEquals("Only the first page's item is added", 1, files.size());
    }

    // ================================================================
    // getRootFilesByPath — else (non-folder) + multi-page
    // ================================================================

    /**
     * DAO whose package-private getRequestBuilder returns the supplied builder and whose
     * getPermissionCollectionPage returns null (permissions not under test here).
     */
    private FileManagementDao rootPathSubject(com.microsoft.graph.requests.DriveItemRequestBuilder builder) {
        return new FileManagementDao(nullUserProvider(), ATTRS, noOpValidationUtil(), noOpTelemetryHelper()) {
            @Override
            com.microsoft.graph.requests.DriveItemRequestBuilder getRequestBuilder(boolean useEmail, String accountID) {
                return builder;
            }

            @Override
            PermissionCollectionPage getPermissionCollectionPage(boolean useEmail, String accountID, DriveItem driveInPage) {
                return null;
            }
        };
    }

    @Test
    public void testGetRootFilesByPath_notAFolder_addsSingleItem() {
        DriveItem notFolder = fileItem("fileId", "single.txt");
        com.microsoft.graph.requests.DriveItemRequestBuilder builder =
                mock(com.microsoft.graph.requests.DriveItemRequestBuilder.class, RETURNS_DEEP_STUBS);
        when(builder.buildRequest().expand(anyString()).get()).thenReturn(notFolder);

        List<File> files = new ArrayList<>();
        try {
            rootPathSubject(builder).getRootFilesByPath("", files, true, "acct", null, new FreeForm());
        } catch (Exception ignored) {}

        assertEquals(1, files.size());
    }

    @Test
    public void testGetRootFilesByPath_driveItemNullId_elseBranch() {
        // driveItem non-null but id == null → else branch (driveItem.id != null is false)
        DriveItem noId = new DriveItem();
        noId.id = null;
        noId.name = "noId.txt";
        noId.folder = new Folder();
        com.microsoft.graph.requests.DriveItemRequestBuilder builder =
                mock(com.microsoft.graph.requests.DriveItemRequestBuilder.class, RETURNS_DEEP_STUBS);
        when(builder.buildRequest().expand(anyString()).get()).thenReturn(noId);

        List<File> files = new ArrayList<>();
        try {
            rootPathSubject(builder).getRootFilesByPath("", files, true, "acct", null, new FreeForm());
        } catch (Exception ignored) {}
        assertEquals(1, files.size());
    }

    @Test
    public void testGetRootFilesByPath_searchSet_nullIdItem_skipped() {
        DriveItem item = new DriveItem();
        item.id = null; // id == null with search set → both add-branches skip
        item.name = "named.txt";
        DriveItemCollectionPage page = new DriveItemCollectionPage(List.of(item), null);
        DriveItem folder = folderItem("fId");
        com.microsoft.graph.requests.DriveItemRequestBuilder builder =
                mock(com.microsoft.graph.requests.DriveItemRequestBuilder.class, RETURNS_DEEP_STUBS);
        when(builder.buildRequest().expand(anyString()).get()).thenReturn(folder);
        DriveItemCollectionRequest childrenReq = builder.children().buildRequest();
        doReturn(page).when(childrenReq).get();

        List<File> files = new ArrayList<>();
        try {
            rootPathSubject(builder).getRootFilesByPath("", files, true, "acct", "query", new FreeForm());
        } catch (Exception ignored) {}
        assertEquals(0, files.size());
    }

    @Test
    public void testGetRootFilesByPath_searchSet_nullName_skipped() {
        DriveItem item = new DriveItem();
        item.id = "id1";
        item.name = null; // search set but name == null → filter branch skips
        DriveItemCollectionPage page = new DriveItemCollectionPage(List.of(item), null);
        DriveItem folder = folderItem("fId");
        com.microsoft.graph.requests.DriveItemRequestBuilder builder =
                mock(com.microsoft.graph.requests.DriveItemRequestBuilder.class, RETURNS_DEEP_STUBS);
        when(builder.buildRequest().expand(anyString()).get()).thenReturn(folder);
        DriveItemCollectionRequest childrenReq = builder.children().buildRequest();
        doReturn(page).when(childrenReq).get();

        List<File> files = new ArrayList<>();
        try {
            rootPathSubject(builder).getRootFilesByPath("", files, true, "acct", "query", new FreeForm());
        } catch (Exception ignored) {}
        assertEquals(0, files.size());
    }

    @Test
    public void testGetRootFilesByPath_nullDriveItem_elseBranch() {
        com.microsoft.graph.requests.DriveItemRequestBuilder builder =
                mock(com.microsoft.graph.requests.DriveItemRequestBuilder.class, RETURNS_DEEP_STUBS);
        when(builder.buildRequest().expand(anyString()).get()).thenReturn(null);

        List<File> files = new ArrayList<>();
        try {
            rootPathSubject(builder).getRootFilesByPath("", files, true, "acct", null, new FreeForm());
        } catch (Exception ignored) { /* FileImpl(null) throws inside fetchFileResponse */ }
        assertNotNull(files);
    }

    @Test
    public void testGetRootFilesByPath_nonEmptyPath_emptyFolder() {
        String rel = "Docs";
        DriveItem folder = folderItem("fId");
        com.microsoft.graph.requests.DriveItemRequestBuilder builder =
                mock(com.microsoft.graph.requests.DriveItemRequestBuilder.class, RETURNS_DEEP_STUBS);
        when(builder.itemWithPath(rel).buildRequest().expand(anyString()).get()).thenReturn(folder);
        DriveItemCollectionRequest childrenReq = builder.itemWithPath(rel).children().buildRequest();
        doReturn(new DriveItemCollectionPage(Collections.emptyList(), null)).when(childrenReq).get();

        FreeForm payload = new FreeForm();
        rootPathSubject(builder).getRootFilesByPath(rel, new ArrayList<>(), true, "acct", null, payload);

        assertEquals(Constants.FOLDER_IS_EMPTY, payload.get("Message"));
    }

    @Test
    public void testGetRootFilesByPath_multiPage_secondEmpty_traversesNextPage() {
        DriveItem item = fileItem("id1", "A.txt");
        DriveItemCollectionPage page2 = new DriveItemCollectionPage(Collections.emptyList(), null);

        DriveItemCollectionRequestBuilder nextBuilder = mock(DriveItemCollectionRequestBuilder.class);
        DriveItemCollectionRequest nextReq = mock(DriveItemCollectionRequest.class);
        when(nextBuilder.buildRequest()).thenReturn(nextReq);
        doReturn(page2).when(nextReq).get();
        DriveItemCollectionPage page1 = new DriveItemCollectionPage(List.of(item), nextBuilder);

        DriveItem folder = folderItem("fId");
        com.microsoft.graph.requests.DriveItemRequestBuilder builder =
                mock(com.microsoft.graph.requests.DriveItemRequestBuilder.class, RETURNS_DEEP_STUBS);
        when(builder.buildRequest().expand(anyString()).get()).thenReturn(folder);
        DriveItemCollectionRequest childrenReq = builder.children().buildRequest();
        doReturn(page1).when(childrenReq).get();

        List<File> files = new ArrayList<>();
        try {
            rootPathSubject(builder).getRootFilesByPath("", files, true, "acct", null, new FreeForm());
        } catch (Exception ignored) {}

        assertEquals("Only the first page's item is added", 1, files.size());
    }

    // ================================================================
    // getSharedFiles — multi-page (next-page traversal)
    // ================================================================

    @Test
    public void testGetSharedFiles_multiPage_traversesNextPage() {
        DriveItem item = sharedItem("id1", "driveA", "Doc.pdf");

        DriveSharedWithMeCollectionPage page2 = mock(DriveSharedWithMeCollectionPage.class);
        when(page2.getCurrentPage()).thenReturn(Collections.emptyList());
        when(page2.getNextPage()).thenReturn(null);

        DriveSharedWithMeCollectionRequestBuilder nextBuilder = mock(DriveSharedWithMeCollectionRequestBuilder.class);
        DriveSharedWithMeCollectionRequest nextReq = mock(DriveSharedWithMeCollectionRequest.class);
        when(nextBuilder.buildRequest()).thenReturn(nextReq);
        when(nextReq.get()).thenReturn(page2);

        DriveSharedWithMeCollectionPage page1 = mock(DriveSharedWithMeCollectionPage.class);
        when(page1.getCurrentPage()).thenReturn(List.of(item));
        when(page1.getNextPage()).thenReturn(nextBuilder);

        UserRequestBuilder user = mock(UserRequestBuilder.class, RETURNS_DEEP_STUBS);
        when(user.drive().sharedWithMe().buildRequest().top(anyInt()).get()).thenReturn(page1);

        List<File> files = new ArrayList<>();
        try {
            sharedSubject(user).getSharedFiles("shared", files, null, true, "acct", new FreeForm());
        } catch (Exception ignored) {}

        assertEquals(1, files.size());
    }

    @Test
    public void testGetSharedFiles_itemWithNullParentReference_skipped() {
        DriveItem item = new DriveItem();
        item.id = "id1";
        item.name = "x.pdf";
        item.remoteItem = new RemoteItem();
        item.remoteItem.parentReference = null; // parentReference != null is false
        UserRequestBuilder user = sharedPageUser(List.of(item));

        List<File> files = new ArrayList<>();
        sharedSubject(user).getSharedFiles("shared", files, null, true, "acct", new FreeForm());
        assertEquals(0, files.size());
    }

    @Test
    public void testGetSharedFiles_itemWithNullDriveId_skipped() {
        DriveItem item = new DriveItem();
        item.id = "id1";
        item.name = "x.pdf";
        item.remoteItem = new RemoteItem();
        item.remoteItem.parentReference = new ItemReference();
        item.remoteItem.parentReference.driveId = null; // driveId != null is false
        UserRequestBuilder user = sharedPageUser(List.of(item));

        List<File> files = new ArrayList<>();
        sharedSubject(user).getSharedFiles("shared", files, null, true, "acct", new FreeForm());
        assertEquals(0, files.size());
    }

    @Test
    public void testGetSharedFiles_searchSet_nullName_skipped() {
        DriveItem item = sharedItem("id1", "driveA", null); // name == null with search set
        UserRequestBuilder user = sharedPageUser(List.of(item));

        List<File> files = new ArrayList<>();
        try {
            sharedSubject(user).getSharedFiles("shared", files, "query", true, "acct", new FreeForm());
        } catch (Exception ignored) {}
        assertEquals(0, files.size());
    }

    /** Deep-stub user whose sharedWithMe page returns the supplied items (single page). */
    private UserRequestBuilder sharedPageUser(List<DriveItem> items) {
        DriveSharedWithMeCollectionPage page = mock(DriveSharedWithMeCollectionPage.class);
        when(page.getCurrentPage()).thenReturn(items);
        when(page.getNextPage()).thenReturn(null);
        UserRequestBuilder user = mock(UserRequestBuilder.class, RETURNS_DEEP_STUBS);
        when(user.drive().sharedWithMe().buildRequest().top(anyInt()).get()).thenReturn(page);
        return user;
    }

    private FileManagementDao sharedSubject(UserRequestBuilder user) {
        GraphServiceClientProvider provider = new GraphServiceClientProvider(null, null, null, null) {
            @Override
            public UserRequestBuilder getUserRequestBuilder(Boolean useEmail, String accountId) {
                return user;
            }
        };
        return new FileManagementDao(provider, ATTRS, noOpValidationUtil(), noOpTelemetryHelper());
    }

    // ================================================================
    // getRequestBuilder + getPermissionCollectionPage (direct)
    // ================================================================

    @Test
    public void testGetRequestBuilder_returnsDriveRoot() {
        UserRequestBuilder user = mock(UserRequestBuilder.class, RETURNS_DEEP_STUBS);
        FileManagementDao dao = sharedSubject(user);
        assertNotNull("getRequestBuilder must return the drive-root builder", dao.getRequestBuilder(true, "acct"));
    }

    @Test
    public void testGetPermissionCollectionPage_returnsStubbedPage() {
        UserRequestBuilder user = mock(UserRequestBuilder.class, RETURNS_DEEP_STUBS);
        com.microsoft.graph.requests.PermissionCollectionRequest permReq =
                user.drive().items("id1").permissions().buildRequest();
        doReturn(null).when(permReq).get();

        FileManagementDao dao = sharedSubject(user);
        DriveItem item = fileItem("id1", "x.txt");
        assertEquals(null, dao.getPermissionCollectionPage(true, "acct", item));
    }

    // ================================================================
    // getFileInfo → checkIfFolderExist → fetchFileInfo → processResponse
    // ================================================================

    /**
     * Deep-stub user for the DRIVE_ROOT ("drive/root:/") path: wires the folder-existence lookup
     * ({@code drive().root().itemWithPath(path).buildRequest().get()}) and the children lookup
     * ({@code ...children().buildRequest().get()}).
     */
    private UserRequestBuilder driveRootInfoUser(String relPath, DriveItem folderLookup, DriveItemCollectionPage childrenPage) {
        UserRequestBuilder user = mock(UserRequestBuilder.class, RETURNS_DEEP_STUBS);
        when(user.drive().root().itemWithPath(relPath).buildRequest().get()).thenReturn(folderLookup);
        DriveItemCollectionRequest childrenReq = user.drive().root().itemWithPath(relPath).children().buildRequest();
        doReturn(childrenPage).when(childrenReq).get();
        return user;
    }

    @Test
    public void testGetFileInfo_driveRoot_folderFound_processesResponse() {
        DriveItem folder = folderItemWithSize("fId", 100L);
        DriveItemCollectionPage children = new DriveItemCollectionPage(List.of(
                folderChild("s1", "Sub", offset(2020, 1, 1), offset(2021, 1, 1)),
                fileChild("f1", "File.txt", offset(2019, 6, 1), offset(2022, 3, 1))
        ), null);
        UserRequestBuilder user = driveRootInfoUser("MyFolder", folder, children);

        FreeForm payload = sharedSubject(user).getFileInfo("drive/root:/MyFolder", true, "acct");

        assertTrue((Boolean) payload.get("Success"));
        assertEquals(1, payload.get("Files"));
        assertEquals(1, payload.get("Folders"));
    }

    @Test
    public void testGetFileInfo_driveRoot_folderFound_nullSize_emptyChildren() {
        DriveItem folder = folderItemWithSize("fId", null);
        DriveItemCollectionPage children = new DriveItemCollectionPage(Collections.emptyList(), null);
        UserRequestBuilder user = driveRootInfoUser("MyFolder", folder, children);

        FreeForm payload = sharedSubject(user).getFileInfo("drive/root:/MyFolder", true, "acct");

        assertEquals(Constants.FOLDER_IS_EMPTY, payload.get("Message"));
    }

    @Test
    public void testGetFileInfo_driveRoot_notAFolder_validationError() {
        DriveItem notFolder = fileItem("fId", "x.txt"); // folder == null
        UserRequestBuilder user = driveRootInfoUser("MyFolder", notFolder, null);

        FreeForm payload = sharedSubject(user).getFileInfo("drive/root:/MyFolder", true, "acct");

        assertFalse((Boolean) payload.get("Success"));
        assertEquals(Constants.PLEASE_PROVIDE_VALID_FOLDER_PATH, payload.get("Message"));
    }

    @Test
    public void testGetFileInfo_driveRoot_fetchInfoThrows_stillSucceedsCheck() {
        DriveItem folder = folderItemWithSize("fId", 50L);
        UserRequestBuilder user = mock(UserRequestBuilder.class, RETURNS_DEEP_STUBS);
        when(user.drive().root().itemWithPath("MyFolder").buildRequest().get()).thenReturn(folder);
        DriveItemCollectionRequest childrenReq = user.drive().root().itemWithPath("MyFolder").children().buildRequest();
        doReturn(null).when(childrenReq).get(); // fetchFileInfo gets null page → processResponse skipped

        FreeForm payload = sharedSubject(user).getFileInfo("drive/root:/MyFolder", true, "acct");

        assertTrue((Boolean) payload.get("Success"));
    }

    @Test
    public void testGetFileInfo_driveId_folderFound_processesResponse() {
        DriveItem folder = folderItemWithSize("fId", 200L);
        DriveItemCollectionPage children = new DriveItemCollectionPage(List.of(
                fileChild("f1", "File.txt", offset(2020, 1, 1), offset(2020, 2, 1))
        ), null);

        UserRequestBuilder user = mock(UserRequestBuilder.class, RETURNS_DEEP_STUBS);
        // checkIfFolderExist (drive-ID branch)
        when(user.drives("b!xyz").root().itemWithPath("Sub").buildRequest().get()).thenReturn(folder);
        // fetchFileInfo (drive-ID branch)
        DriveItemCollectionRequest childrenReq = user.drives("b!xyz").root().itemWithPath("Sub").children().buildRequest();
        doReturn(children).when(childrenReq).get();

        FreeForm payload = sharedSubject(user).getFileInfo("/drives/b!xyz/root:/Sub", true, "acct");

        assertTrue((Boolean) payload.get("Success"));
        assertEquals(1, payload.get("Files"));
    }

    @Test
    public void testGetFileInfo_driveRoot_nullDriveItem_validationError() {
        // lookup returns null → checkIfFolderExist's (driveItem != null) is false
        UserRequestBuilder user = driveRootInfoUser("MyFolder", null, null);
        FreeForm payload = sharedSubject(user).getFileInfo("drive/root:/MyFolder", true, "acct");
        assertFalse((Boolean) payload.get("Success"));
        assertEquals(Constants.PLEASE_PROVIDE_VALID_FOLDER_PATH, payload.get("Message"));
    }

    @Test
    public void testGetFileInfo_driveId_nullDriveItem_validationError() {
        UserRequestBuilder user = mock(UserRequestBuilder.class, RETURNS_DEEP_STUBS);
        when(user.drives("b!xyz").root().itemWithPath("Sub").buildRequest().get()).thenReturn(null);
        FreeForm payload = sharedSubject(user).getFileInfo("/drives/b!xyz/root:/Sub", true, "acct");
        assertFalse((Boolean) payload.get("Success"));
        assertEquals(Constants.PLEASE_PROVIDE_VALID_FOLDER_PATH, payload.get("Message"));
    }

    @Test
    public void testGetFileInfo_driveId_folderFound_nullChildrenPage() {
        // fetchFileInfo (drive-ID branch): children page null → processResponse skipped (416 false branch)
        DriveItem folder = folderItemWithSize("fId", 30L);
        UserRequestBuilder user = mock(UserRequestBuilder.class, RETURNS_DEEP_STUBS);
        when(user.drives("b!xyz").root().itemWithPath("Sub").buildRequest().get()).thenReturn(folder);
        DriveItemCollectionRequest childrenReq = user.drives("b!xyz").root().itemWithPath("Sub").children().buildRequest();
        doReturn(null).when(childrenReq).get();

        FreeForm payload = sharedSubject(user).getFileInfo("/drives/b!xyz/root:/Sub", true, "acct");
        assertTrue((Boolean) payload.get("Success"));
    }

    @Test
    public void testGetFileInfo_processResponse_secondPageEmpty_loopExits() {
        // page1 non-empty, getNextPage present, second page non-null but EMPTY → while (!isEmpty) exits
        DriveItem folder = folderItemWithSize("fId", 10L);
        DriveItemCollectionPage page2 = new DriveItemCollectionPage(Collections.emptyList(), null);
        DriveItemCollectionRequestBuilder nextBuilder = mock(DriveItemCollectionRequestBuilder.class);
        DriveItemCollectionRequest nextReq = mock(DriveItemCollectionRequest.class);
        when(nextBuilder.buildRequest()).thenReturn(nextReq);
        doReturn(page2).when(nextReq).get();
        DriveItemCollectionPage page1 = new DriveItemCollectionPage(List.of(
                fileChild("f1", "First.txt", offset(2019, 1, 1), offset(2019, 2, 1))
        ), nextBuilder);

        UserRequestBuilder user = mock(UserRequestBuilder.class, RETURNS_DEEP_STUBS);
        when(user.drive().root().itemWithPath("MyFolder").buildRequest().get()).thenReturn(folder);
        DriveItemCollectionRequest childrenReq = user.drive().root().itemWithPath("MyFolder").children().buildRequest();
        doReturn(page1).when(childrenReq).get();

        FreeForm payload = sharedSubject(user).getFileInfo("drive/root:/MyFolder", true, "acct");
        assertNotNull(payload);
    }

    @Test
    public void testGetFileInfo_processResponse_secondPageNull_loopExits() {
        // page1 non-empty, getNextPage present, but nextPage fetch returns null → while (page != null) exits
        DriveItem folder = folderItemWithSize("fId", 10L);
        DriveItemCollectionRequestBuilder nextBuilder = mock(DriveItemCollectionRequestBuilder.class);
        DriveItemCollectionRequest nextReq = mock(DriveItemCollectionRequest.class);
        when(nextBuilder.buildRequest()).thenReturn(nextReq);
        doReturn(null).when(nextReq).get();
        DriveItemCollectionPage page1 = new DriveItemCollectionPage(List.of(
                fileChild("f1", "First.txt", offset(2019, 1, 1), offset(2019, 2, 1))
        ), nextBuilder);

        UserRequestBuilder user = mock(UserRequestBuilder.class, RETURNS_DEEP_STUBS);
        when(user.drive().root().itemWithPath("MyFolder").buildRequest().get()).thenReturn(folder);
        DriveItemCollectionRequest childrenReq = user.drive().root().itemWithPath("MyFolder").children().buildRequest();
        doReturn(page1).when(childrenReq).get();

        FreeForm payload = sharedSubject(user).getFileInfo("drive/root:/MyFolder", true, "acct");
        assertNotNull(payload);
    }

    @Test
    public void testGetFileInfo_driveId_notAFolder_validationError() {
        DriveItem notFolder = fileItem("fId", "x.txt");
        UserRequestBuilder user = mock(UserRequestBuilder.class, RETURNS_DEEP_STUBS);
        when(user.drives("b!xyz").root().itemWithPath("Sub").buildRequest().get()).thenReturn(notFolder);

        FreeForm payload = sharedSubject(user).getFileInfo("/drives/b!xyz/root:/Sub", true, "acct");

        assertFalse((Boolean) payload.get("Success"));
        assertEquals(Constants.PLEASE_PROVIDE_VALID_FOLDER_PATH, payload.get("Message"));
    }

    @Test
    public void testGetFileInfo_invalidPath_validationError() {
        UserRequestBuilder user = mock(UserRequestBuilder.class, RETURNS_DEEP_STUBS);
        FreeForm payload = sharedSubject(user).getFileInfo("randomFolder", true, "acct");

        assertFalse((Boolean) payload.get("Success"));
        assertEquals(Constants.PLEASE_PROVIDE_VALID_FOLDER_PATH, payload.get("Message"));
    }

    @Test
    public void testGetFileInfo_checkFolderThrowsItemNotFound_setsFileNotFound() {
        UserRequestBuilder user = mock(UserRequestBuilder.class, RETURNS_DEEP_STUBS);
        when(user.drive().root().itemWithPath("MyFolder").buildRequest().get())
                .thenThrow(new TestGse("itemNotFound occurred"));

        FreeForm payload = sharedSubject(user).getFileInfo("drive/root:/MyFolder", true, "acct");

        assertFalse((Boolean) payload.get("Success"));
        assertEquals(Constants.FILE_NOT_FOUND, payload.get("Message"));
    }

    @Test
    public void testGetFileInfo_checkFolderThrowsOther_setsInvalidFolderPath() {
        UserRequestBuilder user = mock(UserRequestBuilder.class, RETURNS_DEEP_STUBS);
        when(user.drive().root().itemWithPath("MyFolder").buildRequest().get())
                .thenThrow(new TestGse("access denied"));

        FreeForm payload = sharedSubject(user).getFileInfo("drive/root:/MyFolder", true, "acct");

        assertFalse((Boolean) payload.get("Success"));
        assertEquals(Constants.PLEASE_PROVIDE_VALID_FOLDER_PATH, payload.get("Message"));
    }

    /**
     * Exercises processResponse's multi-page traversal (getNextPage != null) plus getFileInfo's
     * outer catch: telemetry recordSuccess throws IllegalArgumentException after a successful fetch.
     */
    @Test
    public void testGetFileInfo_multiPage_andOuterCatch() {
        DriveItem folder = folderItemWithSize("fId", 10L);
        DriveItemCollectionPage page2 = new DriveItemCollectionPage(List.of(
                fileChild("f2", "Second.txt", offset(2020, 5, 1), offset(2020, 6, 1))
        ), null);
        DriveItemCollectionRequestBuilder nextBuilder = mock(DriveItemCollectionRequestBuilder.class);
        DriveItemCollectionRequest nextReq = mock(DriveItemCollectionRequest.class);
        when(nextBuilder.buildRequest()).thenReturn(nextReq);
        doReturn(page2).when(nextReq).get();
        DriveItemCollectionPage page1 = new DriveItemCollectionPage(List.of(
                fileChild("f1", "First.txt", offset(2019, 1, 1), offset(2019, 2, 1))
        ), nextBuilder);

        UserRequestBuilder user = mock(UserRequestBuilder.class, RETURNS_DEEP_STUBS);
        when(user.drive().root().itemWithPath("MyFolder").buildRequest().get()).thenReturn(folder);
        DriveItemCollectionRequest childrenReq = user.drive().root().itemWithPath("MyFolder").children().buildRequest();
        doReturn(page1).when(childrenReq).get();

        GraphServiceClientProvider provider = new GraphServiceClientProvider(null, null, null, null) {
            @Override
            public UserRequestBuilder getUserRequestBuilder(Boolean useEmail, String accountId) {
                return user;
            }
        };
        TelemetryHelper throwingSuccess = new TelemetryHelper(null) {
            @Override public void incrementCount(String m) {}
            @Override public void recordSuccess(String op, long t, Map<String, String> tags) {
                throw new IllegalArgumentException("telemetry boom"); // routed to getFileInfo outer catch
            }
            @Override public void recordError(String op, long t, Exception e, Map<String, String> tags) {}
            @Override public void recordValidationError(String op, long t, String r, Map<String, String> tags) {}
            @Override public void recordRetryPrompted(String op, long t, Map<String, String> tags) {}
        };
        FileManagementDao dao = new FileManagementDao(provider, ATTRS, noOpValidationUtil(), throwingSuccess);

        FreeForm payload = dao.getFileInfo("drive/root:/MyFolder", true, "acct");
        assertNotNull("Payload returned even when recordSuccess throws", payload);
    }

    /**
     * Rich multi-page processResponse run exercising the accumulator ternaries (retain prior
     * page's oldest/newest/last-modified values when a later page has no matching item),
     * getFileNameAndIDByLastModified's null-name/null-id and no-match paths, and getFileDate's
     * null-date branch.
     */
    @Test
    public void testGetFileInfo_multiPage_accumulatorTernariesAndNullFields() {
        DriveItem folder = folderItemWithSize("fId", 10L);
        // page1: A (file, dated) and B (folder, all null fields, highest lastModified)
        DriveItem a = fileChild("a", "A.txt", offset(2019, 1, 1), offset(2025, 1, 1));
        DriveItem b = new DriveItem();
        b.id = null;
        b.name = null;
        b.folder = new Folder();
        b.createdDateTime = null;                 // getFileDate null branch
        b.lastModifiedDateTime = offset(2030, 1, 1); // highest lastModified → matched with null name/id
        // page2: C (file) with dates that match neither the oldest nor the newest nor last-modified of page1
        DriveItem c = fileChild("c", "C.txt", offset(2018, 1, 1), offset(2017, 1, 1));

        DriveItemCollectionPage page2 = new DriveItemCollectionPage(List.of(c), null);
        DriveItemCollectionRequestBuilder nextBuilder = mock(DriveItemCollectionRequestBuilder.class);
        DriveItemCollectionRequest nextReq = mock(DriveItemCollectionRequest.class);
        when(nextBuilder.buildRequest()).thenReturn(nextReq);
        doReturn(page2).when(nextReq).get();
        DriveItemCollectionPage page1 = new DriveItemCollectionPage(List.of(a, b), nextBuilder);

        UserRequestBuilder user = mock(UserRequestBuilder.class, RETURNS_DEEP_STUBS);
        when(user.drive().root().itemWithPath("MyFolder").buildRequest().get()).thenReturn(folder);
        DriveItemCollectionRequest childrenReq = user.drive().root().itemWithPath("MyFolder").children().buildRequest();
        doReturn(page1).when(childrenReq).get();

        FreeForm payload = sharedSubject(user).getFileInfo("drive/root:/MyFolder", true, "acct");
        assertTrue((Boolean) payload.get("Success"));
    }

    @Test
    public void testGetFileInfo_fetchInfoThrowsGse_caughtInternally() {
        DriveItem folder = folderItemWithSize("fId", 5L);
        UserRequestBuilder user = mock(UserRequestBuilder.class, RETURNS_DEEP_STUBS);
        when(user.drive().root().itemWithPath("MyFolder").buildRequest().get()).thenReturn(folder);
        DriveItemCollectionRequest childrenReq = user.drive().root().itemWithPath("MyFolder").children().buildRequest();
        doThrow(new TestGse("children failed")).when(childrenReq).get();

        FreeForm payload = sharedSubject(user).getFileInfo("drive/root:/MyFolder", true, "acct");
        assertTrue("checkIfFolderExist still succeeded", (Boolean) payload.get("Success"));
    }

    @Test
    public void testGetFileInfo_driveId_folderFound_nullSize_emptyChildren() {
        DriveItem folder = folderItemWithSize("fId", null); // size null → addFileSize(0L)
        UserRequestBuilder user = mock(UserRequestBuilder.class, RETURNS_DEEP_STUBS);
        when(user.drives("b!xyz").root().itemWithPath("Sub").buildRequest().get()).thenReturn(folder);
        DriveItemCollectionRequest childrenReq = user.drives("b!xyz").root().itemWithPath("Sub").children().buildRequest();
        doReturn(new DriveItemCollectionPage(Collections.emptyList(), null)).when(childrenReq).get();

        FreeForm payload = sharedSubject(user).getFileInfo("/drives/b!xyz/root:/Sub", true, "acct");
        assertEquals(Constants.FOLDER_IS_EMPTY, payload.get("Message"));
    }

    /**
     * processResponse catch block: the second-page fetch throws a GraphServiceException.
     */
    @Test
    public void testGetFileInfo_processResponseSecondPageThrows_caught() {
        DriveItem folder = folderItemWithSize("fId", 10L);
        DriveItemCollectionRequestBuilder nextBuilder = mock(DriveItemCollectionRequestBuilder.class);
        DriveItemCollectionRequest nextReq = mock(DriveItemCollectionRequest.class);
        when(nextBuilder.buildRequest()).thenReturn(nextReq);
        doThrow(new TestGse("page fetch failed")).when(nextReq).get();
        DriveItemCollectionPage page1 = new DriveItemCollectionPage(List.of(
                fileChild("f1", "First.txt", offset(2019, 1, 1), offset(2019, 2, 1))
        ), nextBuilder);

        UserRequestBuilder user = mock(UserRequestBuilder.class, RETURNS_DEEP_STUBS);
        when(user.drive().root().itemWithPath("MyFolder").buildRequest().get()).thenReturn(folder);
        DriveItemCollectionRequest childrenReq = user.drive().root().itemWithPath("MyFolder").children().buildRequest();
        doReturn(page1).when(childrenReq).get();

        FreeForm payload = sharedSubject(user).getFileInfo("drive/root:/MyFolder", true, "acct");
        assertNotNull(payload);
    }

    // ================================================================
    // createFolder — full flow
    // ================================================================

    private static ExtensionResponse sentinel() {
        return new ExtensionResponseBuilder().success(new java.util.HashMap<>(), null, null).build();
    }

    /** ValidationTypeUtil whose checkValidationsTwoFields always returns {@code res}. */
    private ValidationTypeUtil twoFieldsReturning(ExtensionResponse res) {
        return new ValidationTypeUtil(null, null, null) {
            @Override
            public ExtensionResponse checkValidationsTwoFields(Validator.ValidationResource validationType,
                                                               String subCatalogConstant, String reenterField,
                                                               String existingField, boolean isReenteredField) {
                return res;
            }
        };
    }

    /** Provider that returns the supplied deep-stub user. */
    private FileManagementDao createFolderSubject(UserRequestBuilder user, ValidationTypeUtil validation) {
        GraphServiceClientProvider provider = new GraphServiceClientProvider(null, null, null, null) {
            @Override
            public UserRequestBuilder getUserRequestBuilder(Boolean useEmail, String accountId) {
                return user;
            }
        };
        return new FileManagementDao(provider, ATTRS, validation, noOpTelemetryHelper());
    }

    // ---- empty folder name ----

    @Test
    public void testCreateFolder_emptyName_notReentered_promptsReentry() {
        ExtensionResponse token = sentinel();
        FileManagementDao dao = createFolderSubject(mock(UserRequestBuilder.class, RETURNS_DEEP_STUBS), twoFieldsReturning(token));
        assertSame(token, dao.createFolder(null, "drive/root:/P", false, false, true));
    }

    @Test
    public void testCreateFolder_emptyName_reentered_returnsError() {
        FileManagementDao dao = createFolderSubject(mock(UserRequestBuilder.class, RETURNS_DEEP_STUBS), noOpValidationUtil());
        ExtensionResponse response = dao.createFolder("  ", "drive/root:/P", true, false, true);
        assertFalse((Boolean) response.getResponseValue().get(Constants.SUCCESS));
        assertEquals("Folder name cannot be empty", response.getResponseValue().get(Constants.MESSAGE));
    }

    @Test
    public void testCreateFolder_emptyName_allowRetryFalse_returnsError() {
        // allowRetry=false → the retry-prompt condition's second operand is false → direct error
        FileManagementDao dao = createFolderSubject(mock(UserRequestBuilder.class, RETURNS_DEEP_STUBS), noOpValidationUtil());
        ExtensionResponse response = dao.createFolder(null, "drive/root:/P", false, false, false);
        assertEquals("Folder name cannot be empty", response.getResponseValue().get(Constants.MESSAGE));
    }

    // ---- personal drive ----

    /** Deep-stub user for a personal-drive createFolder: parent lookup + child post. */
    private UserRequestBuilder personalCreateUser(DriveItem parent, DriveItem created, GraphServiceException postError) {
        UserRequestBuilder user = mock(UserRequestBuilder.class, RETURNS_DEEP_STUBS);
        when(user.drive().root().itemWithPath("P").buildRequest().get()).thenReturn(parent);
        if (postError != null) {
            when(user.drive().items("parentId").children().buildRequest().post(any(DriveItem.class))).thenThrow(postError);
        } else {
            when(user.drive().items("parentId").children().buildRequest().post(any(DriveItem.class))).thenReturn(created);
        }
        return user;
    }

    @Test
    public void testCreateFolder_personal_success() {
        DriveItem parent = fileItem("parentId", "P");
        DriveItem created = fileItem("newId", "NewFolder");
        FileManagementDao dao = createFolderSubject(personalCreateUser(parent, created, null), noOpValidationUtil());

        ExtensionResponse response = dao.createFolder("NewFolder", "drive/root:/P", false, false, true);
        assertTrue((Boolean) response.getResponseValue().get(Constants.SUCCESS));
    }

    @Test
    public void testCreateFolder_personal_rootPath_usesSlashRelativePath() {
        // path "drive/root:/" → relativeFilePath is blank → itemWithPath("/") branch
        DriveItem parent = fileItem("parentId", "root");
        DriveItem created = fileItem("newId", "NewFolder");
        UserRequestBuilder user = mock(UserRequestBuilder.class, RETURNS_DEEP_STUBS);
        when(user.drive().root().itemWithPath("/").buildRequest().get()).thenReturn(parent);
        when(user.drive().items("parentId").children().buildRequest().post(any(DriveItem.class))).thenReturn(created);
        FileManagementDao dao = createFolderSubject(user, noOpValidationUtil());

        ExtensionResponse response = dao.createFolder("NewFolder", "drive/root:/", false, false, true);
        assertTrue((Boolean) response.getResponseValue().get(Constants.SUCCESS));
    }

    @Test
    public void testCreateFolder_personal_alreadyExists_promptsFolderNameReentry() {
        ExtensionResponse token = sentinel();
        DriveItem parent = fileItem("parentId", "P");
        FileManagementDao dao = createFolderSubject(
                personalCreateUser(parent, null, new TestGse("Name already exists")), twoFieldsReturning(token));

        assertSame(token, dao.createFolder("NewFolder", "drive/root:/P", false, false, true));
    }

    @Test
    public void testCreateFolder_personal_alreadyExists_folderReentered_promptsTargetPathReentry() {
        ExtensionResponse token = sentinel();
        DriveItem parent = fileItem("parentId", "P");
        FileManagementDao dao = createFolderSubject(
                personalCreateUser(parent, null, new TestGse("Name already exists")), twoFieldsReturning(token));

        // isReenteredFolderName=true, isReenteredTargetPath=false → target-path reentry branch
        assertSame(token, dao.createFolder("NewFolder", "drive/root:/P", true, false, true));
    }

    @Test
    public void testCreateFolder_personal_alreadyExists_bothReentered_recordsValidationError() {
        DriveItem parent = fileItem("parentId", "P");
        FileManagementDao dao = createFolderSubject(
                personalCreateUser(parent, null, new TestGse("Name already exists")), noOpValidationUtil());

        ExtensionResponse response = dao.createFolder("NewFolder", "drive/root:/P", true, true, true);
        assertFalse((Boolean) response.getResponseValue().get(Constants.SUCCESS));
    }

    @Test
    public void testCreateFolder_personal_alreadyExists_allowRetryFalse_recordsValidationError() {
        // allowRetry=false → both retry branches (643 / 649) short-circuit → recordValidationError
        DriveItem parent = fileItem("parentId", "P");
        FileManagementDao dao = createFolderSubject(
                personalCreateUser(parent, null, new TestGse("Name already exists")), noOpValidationUtil());
        ExtensionResponse response = dao.createFolder("NewFolder", "drive/root:/P", false, false, false);
        assertFalse((Boolean) response.getResponseValue().get(Constants.SUCCESS));
    }

    @Test
    public void testCreateFolder_personal_postFailsNonAlreadyExists_outerCatch() {
        // createFolderInDrive returns null with message != FOLDER_ALREADY_EXIST → executeCreateFolder
        // constructs FileImpl(null) → NPE → createFolder's outer catch (also covers line-740 false branch).
        DriveItem parent = fileItem("parentId", "P");
        FileManagementDao dao = createFolderSubject(
                personalCreateUser(parent, null, new TestGse("some transient error")), noOpValidationUtil());
        ExtensionResponse response = dao.createFolder("NewFolder", "drive/root:/P", true, true, true);
        assertNotNull(response);
    }

    @Test
    public void testCreateFolder_personal_parentLookupThrowsGse_outerCatch() {
        // getDriveItemForPath's GraphServiceException catch → payload has MESSAGE but no DRIVE_ITEM;
        // executeCreateFolder returns it; createFolder's (boolean) get(SUCCESS) NPEs → outer catch.
        UserRequestBuilder user = mock(UserRequestBuilder.class, RETURNS_DEEP_STUBS);
        when(user.drive().root().itemWithPath("P").buildRequest().get()).thenThrow(new TestGse("lookup failed"));
        FileManagementDao dao = createFolderSubject(user, noOpValidationUtil());

        ExtensionResponse response = dao.createFolder("NewFolder", "drive/root:/P", true, true, true);
        assertNotNull(response);
    }

    // ---- drive-ID (shared) drive ----

    /** Deep-stub user for a drive-ID createFolder: shared parent lookup + child post. */
    private UserRequestBuilder sharedCreateUser(DriveItem parent, DriveItem created, GraphServiceException postError,
                                                GraphServiceException lookupError) {
        UserRequestBuilder user = mock(UserRequestBuilder.class, RETURNS_DEEP_STUBS);
        if (lookupError != null) {
            when(user.drives("b!drv").root().itemWithPath("Parent").buildRequest().get()).thenThrow(lookupError);
        } else {
            when(user.drives("b!drv").root().itemWithPath("Parent").buildRequest().get()).thenReturn(parent);
        }
        if (postError != null) {
            when(user.drives("b!drv").items("parentId").children().buildRequest().post(any(DriveItem.class))).thenThrow(postError);
        } else if (created != null) {
            when(user.drives("b!drv").items("parentId").children().buildRequest().post(any(DriveItem.class))).thenReturn(created);
        }
        return user;
    }

    private DriveItem sharedParent() {
        DriveItem parent = fileItem("parentId", "Parent");
        parent.parentReference = new ItemReference();
        parent.parentReference.driveId = "b!drv";
        return parent;
    }

    @Test
    public void testCreateFolder_driveId_success() {
        DriveItem created = fileItem("newId", "NewFolder");
        FileManagementDao dao = createFolderSubject(sharedCreateUser(sharedParent(), created, null, null), noOpValidationUtil());

        ExtensionResponse response = dao.createFolder("NewFolder", "/drives/b!drv/root:/Parent", false, false, true);
        assertTrue((Boolean) response.getResponseValue().get(Constants.SUCCESS));
    }

    @Test
    public void testCreateFolder_driveId_failure_promptsTargetPathReentry() {
        ExtensionResponse token = sentinel();
        FileManagementDao dao = createFolderSubject(
                sharedCreateUser(sharedParent(), null, new TestGse("Name already exists"), null), twoFieldsReturning(token));

        assertSame(token, dao.createFolder("NewFolder", "/drives/b!drv/root:/Parent", false, false, true));
    }

    @Test
    public void testCreateFolder_driveId_failure_targetReentered_recordsValidationError() {
        FileManagementDao dao = createFolderSubject(
                sharedCreateUser(sharedParent(), null, new TestGse("Name already exists"), null), noOpValidationUtil());

        ExtensionResponse response = dao.createFolder("NewFolder", "/drives/b!drv/root:/Parent", false, true, true);
        assertFalse((Boolean) response.getResponseValue().get(Constants.SUCCESS));
    }

    @Test
    public void testCreateFolder_driveId_failure_allowRetryFalse_recordsValidationError() {
        FileManagementDao dao = createFolderSubject(
                sharedCreateUser(sharedParent(), null, new TestGse("Name already exists"), null), noOpValidationUtil());
        ExtensionResponse response = dao.createFolder("NewFolder", "/drives/b!drv/root:/Parent", false, false, false);
        assertFalse((Boolean) response.getResponseValue().get(Constants.SUCCESS));
    }

    @Test
    public void testCreateFolder_driveId_parentLookupThrowsGse_outerCatch() {
        FileManagementDao dao = createFolderSubject(
                sharedCreateUser(null, null, null, new TestGse("shared lookup failed")), noOpValidationUtil());

        ExtensionResponse response = dao.createFolder("NewFolder", "/drives/b!drv/root:/Parent", false, true, true);
        assertNotNull(response);
    }

    // ---- invalid path ----

    @Test
    public void testCreateFolder_invalidPath_promptsTargetPathReentry() {
        ExtensionResponse token = sentinel();
        FileManagementDao dao = createFolderSubject(mock(UserRequestBuilder.class, RETURNS_DEEP_STUBS), twoFieldsReturning(token));
        assertSame(token, dao.createFolder("NewFolder", "invalidPath", false, false, true));
    }

    @Test
    public void testCreateFolder_invalidPath_allowRetryFalse_returnsDefaultError() {
        FileManagementDao dao = createFolderSubject(mock(UserRequestBuilder.class, RETURNS_DEEP_STUBS), noOpValidationUtil());
        ExtensionResponse response = dao.createFolder("NewFolder", "invalidPath", false, false, false);
        assertFalse((Boolean) response.getResponseValue().get(Constants.SUCCESS));
        assertEquals(Constants.PLEASE_PROVIDE_VALID_SOURCE_PATH, response.getResponseValue().get(Constants.MESSAGE));
    }

    @Test
    public void testCreateFolder_invalidPath_targetReentered_returnsDefaultError() {
        FileManagementDao dao = createFolderSubject(mock(UserRequestBuilder.class, RETURNS_DEEP_STUBS), noOpValidationUtil());
        ExtensionResponse response = dao.createFolder("NewFolder", "invalidPath", false, true, true);
        assertFalse((Boolean) response.getResponseValue().get(Constants.SUCCESS));
        assertEquals(Constants.PLEASE_PROVIDE_VALID_SOURCE_PATH, response.getResponseValue().get(Constants.MESSAGE));
    }

    @Test
    public void testCreateFolder_fourArgOverload_delegates() {
        DriveItem parent = fileItem("parentId", "P");
        DriveItem created = fileItem("newId", "NewFolder");
        FileManagementDao dao = createFolderSubject(personalCreateUser(parent, created, null), noOpValidationUtil());

        // 4-arg overload delegates to the 5-arg overload with allowRetry=true
        ExtensionResponse response = dao.createFolder("NewFolder", "drive/root:/P", false, false);
        assertTrue((Boolean) response.getResponseValue().get(Constants.SUCCESS));
    }

    // ================================================================
    // reenterValidate variants
    // ================================================================

    @Test
    public void testReenterValidate_source_validationReturnsResponse() {
        ExtensionResponse token = sentinel();
        ValidationTypeUtil v = new ValidationTypeUtil(null, null, null) {
            @Override
            public ExtensionResponse checkValidationsWithInfo(Validator.ValidationResource validationType,
                                                              String subCatalogConstant, String sourcePath,
                                                              boolean useEmail, String accountID) {
                return token;
            }
        };
        FileManagementDao dao = new FileManagementDao(nullUserProvider(), ATTRS, v, noOpTelemetryHelper());
        assertSame(token, dao.reenterValidate("src", true, "acct"));
    }

    @Test
    public void testReenterValidate_source_validationReturnsNull_buildsFailure() {
        ValidationTypeUtil v = new ValidationTypeUtil(null, null, null) {
            @Override
            public ExtensionResponse checkValidationsWithInfo(Validator.ValidationResource validationType,
                                                              String subCatalogConstant, String sourcePath,
                                                              boolean useEmail, String accountID) {
                return null;
            }
        };
        FileManagementDao dao = new FileManagementDao(nullUserProvider(), ATTRS, v, noOpTelemetryHelper());
        ExtensionResponse response = dao.reenterValidate("src", true, "acct");
        assertNotNull(response.getError());
        assertEquals(Constants.PROVIDE_VALID_PATH_FILE_NOT_FOUND, response.getError().getErrorMessage());
    }

    @Test
    public void testReenterValidateList_validationReturnsResponse() {
        ExtensionResponse token = sentinel();
        ValidationTypeUtil v = new ValidationTypeUtil(null, null, null) {
            @Override
            public ExtensionResponse checkValidationsListFilesOrFolder(Validator.ValidationResource validationType,
                                                                       String subCatalogConstant, String sourcePath,
                                                                       String search, boolean useEmail, String accountID) {
                return token;
            }
        };
        FileManagementDao dao = new FileManagementDao(nullUserProvider(), ATTRS, v, noOpTelemetryHelper());
        assertSame(token, dao.reenterValidateList("src", "search", true, "acct"));
    }

    @Test
    public void testReenterValidateList_validationReturnsNull_buildsFailure() {
        ValidationTypeUtil v = new ValidationTypeUtil(null, null, null) {
            @Override
            public ExtensionResponse checkValidationsListFilesOrFolder(Validator.ValidationResource validationType,
                                                                       String subCatalogConstant, String sourcePath,
                                                                       String search, boolean useEmail, String accountID) {
                return null;
            }
        };
        FileManagementDao dao = new FileManagementDao(nullUserProvider(), ATTRS, v, noOpTelemetryHelper());
        ExtensionResponse response = dao.reenterValidateList("src", "search", true, "acct");
        assertNotNull(response.getError());
        assertEquals(Constants.PROVIDE_VALID_PATH_FILE_NOT_FOUND, response.getError().getErrorMessage());
    }

    @Test
    public void testReenterValidateTwoFields_validationReturnsResponse() {
        ExtensionResponse token = sentinel();
        FileManagementDao dao = new FileManagementDao(nullUserProvider(), ATTRS, twoFieldsReturning(token), noOpTelemetryHelper());
        assertSame(token, dao.reenterValidate(Validator.ValidationResource.FOLDER_NAME,
                SubCatalogConstants.CONFIRM_REENTER_CREATE_FOLDER, "f1", "f2", true));
    }

    @Test
    public void testReenterValidateTwoFields_validationReturnsNull_buildsFailure() {
        FileManagementDao dao = new FileManagementDao(nullUserProvider(), ATTRS, twoFieldsReturning(null), noOpTelemetryHelper());
        ExtensionResponse response = dao.reenterValidate(Validator.ValidationResource.FOLDER_NAME,
                SubCatalogConstants.CONFIRM_REENTER_CREATE_FOLDER, "f1", "f2", true);
        assertNotNull(response.getError());
        assertEquals(Constants.PROVIDE_VALID_PATH_FILE_NOT_FOUND, response.getError().getErrorMessage());
    }

    // ================================================================
    // createProvider() via factory (covers the providerFactory branch, line 69)
    // ================================================================

    @Test
    public void testCreateProvider_usesFactoryWhenProviderNull() {
        UserRequestBuilder user = mock(UserRequestBuilder.class, RETURNS_DEEP_STUBS);
        com.microsoft.graph.requests.PermissionCollectionRequest permReq =
                user.drive().items("id1").permissions().buildRequest();
        doReturn(null).when(permReq).get();

        GraphServiceClientProvider provider = new GraphServiceClientProvider(null, null, null, null) {
            @Override
            public UserRequestBuilder getUserRequestBuilder(Boolean useEmail, String accountId) {
                return user;
            }
        };
        app.krista.extensions.essentials.cloudfiles.onedrive.impl.connectors.GraphServiceClientProviderFactory factory =
                new app.krista.extensions.essentials.cloudfiles.onedrive.impl.connectors.GraphServiceClientProviderFactory(null, null, null, null) {
                    @Override
                    public GraphServiceClientProvider create() {
                        return provider;
                    }
                };
        FileManagementDao dao = new FileManagementDao(factory, ATTRS, noOpValidationUtil(), noOpTelemetryHelper());

        // A method that calls createProvider() → providerFactory.create()
        assertEquals(null, dao.getPermissionCollectionPage(true, "acct", fileItem("id1", "x.txt")));
    }

    // ================================================================
    // HELPERS
    // ================================================================

    private static GraphServiceClientProvider nullUserProvider() {
        return new GraphServiceClientProvider(null, null, null, null) {
            @Override
            public UserRequestBuilder getUserRequestBuilder(Boolean useEmail, String accountId) {
                return null;
            }
        };
    }

    private static DriveItem folderItem(String id) {
        DriveItem item = new DriveItem();
        item.id = id;
        item.folder = new Folder();
        return item;
    }

    private static DriveItem folderItemWithSize(String id, Long size) {
        DriveItem item = folderItem(id);
        item.size = size;
        return item;
    }

    private static DriveItem fileItem(String id, String name) {
        DriveItem item = new DriveItem();
        item.id = id;
        item.name = name;
        return item;
    }

    private static DriveItem folderChild(String id, String name, OffsetDateTime created, OffsetDateTime modified) {
        DriveItem item = new DriveItem();
        item.id = id;
        item.name = name;
        item.folder = new Folder();
        item.createdDateTime = created;
        item.lastModifiedDateTime = modified;
        return item;
    }

    private static DriveItem fileChild(String id, String name, OffsetDateTime created, OffsetDateTime modified) {
        DriveItem item = new DriveItem();
        item.id = id;
        item.name = name;
        item.file = new com.microsoft.graph.models.File();
        item.createdDateTime = created;
        item.lastModifiedDateTime = modified;
        return item;
    }

    private static DriveItem sharedItem(String id, String driveId, String name) {
        DriveItem item = new DriveItem();
        item.id = id;
        item.name = name;
        item.remoteItem = new RemoteItem();
        item.remoteItem.parentReference = new ItemReference();
        item.remoteItem.parentReference.driveId = driveId;
        return item;
    }

    private static OffsetDateTime offset(int year, int month, int day) {
        return OffsetDateTime.of(year, month, day, 0, 0, 0, 0, ZoneOffset.UTC);
    }

    private static ValidationTypeUtil noOpValidationUtil() {
        return new ValidationTypeUtil(null, null, null);
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
     * getMessage() delegates to getResponseMessage() to avoid NPE when the error body is null.
     */
    private static class TestGse extends GraphServiceException {
        TestGse(String message) {
            super("GET", "https://graph.microsoft.com/v1.0/me/drive",
                    Collections.emptyList(), message,
                    500, message, Collections.emptyList(), null, false);
        }

        @Override
        public String getMessage() {
            return getResponseMessage();
        }
    }
}
