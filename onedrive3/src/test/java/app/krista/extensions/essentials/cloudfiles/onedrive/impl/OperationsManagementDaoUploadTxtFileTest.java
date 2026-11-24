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
import app.krista.extensions.essentials.cloudfiles.onedrive.catalog.FileHandlers.TelemetryHelper;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.connectors.GraphServiceClientProvider;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.stores.KristaMediaClient;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.util.Constants;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.util.ValidationTypeUtil;
import com.microsoft.graph.models.DriveItem;
import com.microsoft.graph.models.Folder;
import com.microsoft.graph.requests.DriveItemContentStreamRequest;
import com.microsoft.graph.requests.DriveItemContentStreamRequestBuilder;
import com.microsoft.graph.requests.DriveItemRequest;
import com.microsoft.graph.requests.DriveItemRequestBuilder;
import com.microsoft.graph.requests.DriveRequestBuilder;
import com.microsoft.graph.requests.UserRequestBuilder;
import org.junit.After;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Full-branch coverage for both {@link OperationsManagementDao#uploadTxtFile} overloads.
 *
 * <p>Exercises: the 3-arg delegation; the non-personal-drive arm (retry / no-retry / reentered);
 * the personal-drive arm with the parent folder not found (retry / no-retry / reentered) and found;
 * and each arm of the catch block (itemNotFound / generic / null message).
 */
public class OperationsManagementDaoUploadTxtFileTest {

    private static final String PERSONAL_TARGET = "drive/root:/folder/notes.txt";
    private static final String NON_PERSONAL_TARGET = "not-a-personal-drive-path";
    private static final String UPLOAD_TARGET = "drive/root:/folder/cov_upload_test.txt";
    private static final String UPLOAD_FILE_NAME = "cov_upload_test.txt";
    private static final String PARENT_ID = "PARENT";

    private static final ExtensionResponse REENTER_SENTINEL =
            new ExtensionResponseBuilder().failure("reenter-prompt").build();

    @After
    public void cleanUpTempFile() {
        // uploadTextFileHelper writes a real temp file via FileUtil.createTxtFile before the upload call.
        new java.io.File(UPLOAD_FILE_NAME).delete();
    }

    // ---- 3-arg overload + non-personal drive ----

    @Test
    public void threeArgOverload_nonPersonal_notReentered_promptsReenter() {
        // 3-arg delegates with allowRetry = true.
        ExtensionResponse response =
                dao(mock(UserRequestBuilder.class)).uploadTxtFile("body", NON_PERSONAL_TARGET, false);

        assertSame(REENTER_SENTINEL, response);
    }

    @Test
    public void nonPersonal_notReentered_noRetry_returnsFailure() {
        ExtensionResponse response =
                dao(mock(UserRequestBuilder.class)).uploadTxtFile("body", NON_PERSONAL_TARGET, false, false);

        assertEquals(ExtensionResponse.Result.FAILURE, response.getResult());
        assertEquals("Please provide a valid personal drive path", response.getError().getErrorMessage());
    }

    @Test
    public void nonPersonal_reentered_returnsFolderPathError() {
        Map<String, Object> payload =
                dao(mock(UserRequestBuilder.class)).uploadTxtFile("body", NON_PERSONAL_TARGET, true, true)
                        .getResponseValue();

        assertFalse((Boolean) payload.get(Constants.SUCCESS));
        assertEquals(Constants.PLEASE_PROVIDE_VALID_FOLDER_PATH, payload.get(Constants.MESSAGE));
    }

    @Test
    public void nullFileContent_reentered_returnsFolderPathError() {
        // Null content exercises the fileContent != null ? ... : 0 branch in the entry log.
        Map<String, Object> payload =
                dao(mock(UserRequestBuilder.class)).uploadTxtFile(null, NON_PERSONAL_TARGET, true, true)
                        .getResponseValue();

        assertFalse((Boolean) payload.get(Constants.SUCCESS));
        assertEquals(Constants.PLEASE_PROVIDE_VALID_FOLDER_PATH, payload.get(Constants.MESSAGE));
    }

    // ---- personal drive, parent folder not found ----

    @Test
    public void parentNotFound_notReentered_allowRetry_promptsReenter() {
        UserRequestBuilder urb = wireParent(null, new IllegalArgumentException("itemNotFound"));

        ExtensionResponse response = dao(urb).uploadTxtFile("body", PERSONAL_TARGET, false, true);

        assertSame(REENTER_SENTINEL, response);
    }

    @Test
    public void parentNotFound_notReentered_noRetry_returnsFailure() {
        UserRequestBuilder urb = wireParent(null, new IllegalArgumentException("itemNotFound"));

        ExtensionResponse response = dao(urb).uploadTxtFile("body", PERSONAL_TARGET, false, false);

        assertEquals(ExtensionResponse.Result.FAILURE, response.getResult());
        assertEquals(Constants.FILE_NOT_FOUND, response.getError().getErrorMessage());
    }

    @Test
    public void parentNotFound_reentered_returnsError() {
        UserRequestBuilder urb = wireParent(null, new IllegalArgumentException("itemNotFound"));

        Map<String, Object> payload =
                dao(urb).uploadTxtFile("body", PERSONAL_TARGET, true, true).getResponseValue();

        assertFalse((Boolean) payload.get(Constants.SUCCESS));
        assertEquals(Constants.PLEASE_PROVIDE_VALID_FOLDER_PATH, payload.get(Constants.MESSAGE));
    }

    // ---- personal drive, parent found ----

    @Test
    public void parentFoundButNotFolder_recordsSuccess() {
        // Parent resolves to a non-folder item, so the upload helper is skipped and success is recorded.
        UserRequestBuilder urb = wireParent(file("folder", "X", 0L, false), null);

        ExtensionResponse response = dao(urb).uploadTxtFile("body", PERSONAL_TARGET, false, true);

        assertEquals(ExtensionResponse.Result.SUCCESS, response.getResult());
    }

    // ---- catch block (upload throws) ----

    @Test
    public void uploadThrowsItemNotFound_returnsFileNotFound() {
        UserRequestBuilder urb = wireUpload(new IllegalArgumentException("Error code: itemNotFound"));

        Map<String, Object> payload =
                dao(urb).uploadTxtFile("body", UPLOAD_TARGET, false, true).getResponseValue();

        assertFalse((Boolean) payload.get(Constants.SUCCESS));
        assertEquals(Constants.FILE_NOT_FOUND, payload.get(Constants.MESSAGE));
    }

    @Test
    public void uploadThrowsGenericError_returnsGenericMessage() {
        UserRequestBuilder urb = wireUpload(new IllegalArgumentException("connection reset"));

        Map<String, Object> payload =
                dao(urb).uploadTxtFile("body", UPLOAD_TARGET, false, true).getResponseValue();

        assertFalse((Boolean) payload.get(Constants.SUCCESS));
        assertEquals("Error occurred during uploading attachment", payload.get(Constants.MESSAGE));
    }

    @Test
    public void uploadThrowsNullMessage_returnsGenericMessage() {
        UserRequestBuilder urb = wireUpload(new IllegalArgumentException());

        Map<String, Object> payload =
                dao(urb).uploadTxtFile("body", UPLOAD_TARGET, false, true).getResponseValue();

        assertFalse((Boolean) payload.get(Constants.SUCCESS));
        assertEquals("Error occurred during uploading attachment", payload.get(Constants.MESSAGE));
    }

    // ---- helpers ----

    private OperationsManagementDao dao(UserRequestBuilder urb) {
        GraphServiceClientProvider provider = new GraphServiceClientProvider(null, null, null, null) {
            @Override
            public UserRequestBuilder getUserRequestBuilder(Boolean useEmail, String accountId) {
                return urb;
            }
        };
        ValidationTypeUtil validationTypeUtil = mock(ValidationTypeUtil.class);
        when(validationTypeUtil.checkValidationsWriteAsText(any(), any(), any(), any()))
                .thenReturn(REENTER_SENTINEL);
        return new OperationsManagementDao(provider, mock(KristaMediaClient.class),
                validationTypeUtil, mock(TelemetryHelper.class));
    }

    /** Wires only the parent-folder resolution ({@code drive().root().itemWithPath(...).get()}). */
    private UserRequestBuilder wireParent(DriveItem parentItem, RuntimeException parentThrow) {
        UserRequestBuilder urb = mock(UserRequestBuilder.class);
        DriveRequestBuilder drive = mock(DriveRequestBuilder.class);
        when(urb.drive()).thenReturn(drive);

        DriveItemRequest req = mock(DriveItemRequest.class);
        if (parentThrow != null) {
            when(req.get()).thenThrow(parentThrow);
        } else {
            when(req.get()).thenReturn(parentItem);
        }
        DriveItemRequestBuilder itemBuilder = mock(DriveItemRequestBuilder.class);
        when(itemBuilder.buildRequest()).thenReturn(req);
        DriveItemRequestBuilder rootBuilder = mock(DriveItemRequestBuilder.class);
        when(rootBuilder.itemWithPath(anyString())).thenReturn(itemBuilder);
        when(drive.root()).thenReturn(rootBuilder);
        return urb;
    }

    /**
     * Wires the full upload path: parent resolves to a folder, the target-exists check reports "not
     * found", and the content PUT throws {@code putThrow} (driving uploadTxtFile's catch block).
     */
    private UserRequestBuilder wireUpload(RuntimeException putThrow) {
        UserRequestBuilder urb = mock(UserRequestBuilder.class);
        DriveRequestBuilder drive = mock(DriveRequestBuilder.class);
        when(urb.drive()).thenReturn(drive);

        // parent folder resolution: root().itemWithPath("folder").buildRequest().get() -> folder
        DriveItem folderItem = file("folder", PARENT_ID, 0L, true);
        DriveItemRequest folderReq = mock(DriveItemRequest.class);
        when(folderReq.get()).thenReturn(folderItem);
        DriveItemRequestBuilder folderBuilder = mock(DriveItemRequestBuilder.class);
        when(folderBuilder.buildRequest()).thenReturn(folderReq);

        // target-exists check: root().itemWithPath("folder/cov_upload_test.txt").get() -> not found
        DriveItemRequest targetReq = mock(DriveItemRequest.class);
        when(targetReq.get()).thenThrow(new IllegalArgumentException("itemNotFound"));
        DriveItemRequestBuilder targetBuilder = mock(DriveItemRequestBuilder.class);
        when(targetBuilder.buildRequest()).thenReturn(targetReq);

        DriveItemRequestBuilder rootBuilder = mock(DriveItemRequestBuilder.class);
        when(rootBuilder.itemWithPath("folder")).thenReturn(folderBuilder);
        when(rootBuilder.itemWithPath("folder/" + UPLOAD_FILE_NAME)).thenReturn(targetBuilder);
        when(drive.root()).thenReturn(rootBuilder);

        // upload PUT: items(PARENT).itemWithPath(fileName).content().buildRequest().put(bytes) -> throws
        DriveItemContentStreamRequest putReq = mock(DriveItemContentStreamRequest.class);
        when(putReq.put(any(byte[].class))).thenThrow(putThrow);
        DriveItemContentStreamRequestBuilder csrb = mock(DriveItemContentStreamRequestBuilder.class);
        when(csrb.buildRequest()).thenReturn(putReq);
        DriveItemRequestBuilder uploadItemBuilder = mock(DriveItemRequestBuilder.class);
        when(uploadItemBuilder.content()).thenReturn(csrb);
        DriveItemRequestBuilder uploadParent = mock(DriveItemRequestBuilder.class);
        when(uploadParent.itemWithPath(UPLOAD_FILE_NAME)).thenReturn(uploadItemBuilder);
        when(drive.items(PARENT_ID)).thenReturn(uploadParent);
        return urb;
    }

    private DriveItem file(String name, String id, Long size, boolean isFolder) {
        DriveItem driveItem = new DriveItem();
        driveItem.name = name;
        driveItem.id = id;
        driveItem.size = size;
        driveItem.folder = isFolder ? new Folder() : null;
        return driveItem;
    }
}
