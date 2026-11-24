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
import com.microsoft.graph.requests.DriveItemContentStreamRequest;
import com.microsoft.graph.requests.DriveItemContentStreamRequestBuilder;
import com.microsoft.graph.requests.DriveItemRequest;
import com.microsoft.graph.requests.DriveItemRequestBuilder;
import com.microsoft.graph.requests.DriveRequestBuilder;
import com.microsoft.graph.requests.UserRequestBuilder;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Full-branch coverage for {@link OperationsManagementDao#readTxtFile(String, boolean)}.
 *
 * <p>The Graph resolution/download chains are wired through a real
 * {@link GraphServiceClientProvider} that hands back a mocked {@link UserRequestBuilder}, so the
 * real {@code FileUtil}/{@code getFileInfo} logic runs while the network calls are stubbed. This
 * exercises every branch: personal vs shared drive paths, file-exists vs not-found, too-large,
 * valid .txt/.md vs invalid extension, the re-entry (not-reentered) vs direct-error (reentered)
 * arms, the non-drive path, and each arm of the catch block.
 */
public class OperationsManagementDaoReadTxtFileTest {

    private static final String PERSONAL_TXT = "drive/root:/folder/file.txt";
    private static final String SHARED_MD = "/drives/b!drv/root:/folder/file.md";
    private static final String SHARED_DRIVE_ID = "b!drv";
    private static final String ITEM_ID = "ITEM1";

    /** Distinct instance returned by the (mocked) validator so re-entry can be asserted by identity. */
    private static final ExtensionResponse REENTER_SENTINEL =
            new ExtensionResponseBuilder().failure("reenter-prompt").build();

    // ---- personal drive ----

    @Test
    public void personalDrive_validTxt_readsContent() {
        DriveRequestBuilder drive = wireDrive(file("file.txt", ITEM_ID, 11L), null,
                stream("hello world"), null, ITEM_ID);
        UserRequestBuilder urb = mock(UserRequestBuilder.class);
        when(urb.drive()).thenReturn(drive);

        Map<String, Object> payload = dao(urb).readTxtFile(PERSONAL_TXT, false).getResponseValue();

        assertTrue((Boolean) payload.get(Constants.SUCCESS));
        assertEquals(Constants.FILE_FOUND, payload.get(Constants.MESSAGE));
        assertEquals("hello world", ((String) payload.get("Content")).trim());
    }

    @Test
    public void sharedDrive_validMd_readsContentFromSharedDrive() {
        DriveRequestBuilder drive = wireDrive(file("file.md", ITEM_ID, 5L), null,
                stream("# heading"), null, ITEM_ID);
        UserRequestBuilder urb = mock(UserRequestBuilder.class);
        when(urb.drives(SHARED_DRIVE_ID)).thenReturn(drive);

        Map<String, Object> payload = dao(urb).readTxtFile(SHARED_MD, false).getResponseValue();

        assertTrue((Boolean) payload.get(Constants.SUCCESS));
        assertEquals("# heading", ((String) payload.get("Content")).trim());
    }

    @Test
    public void fileTooLarge_returnsError() {
        DriveRequestBuilder drive = wireDrive(file("big.txt", ITEM_ID, 40L * 1024 * 1024), null,
                null, null, null);
        UserRequestBuilder urb = mock(UserRequestBuilder.class);
        when(urb.drive()).thenReturn(drive);

        Map<String, Object> payload = dao(urb).readTxtFile(PERSONAL_TXT, false).getResponseValue();

        assertFalse((Boolean) payload.get(Constants.SUCCESS));
        assertEquals("File size is too large to read", payload.get(Constants.MESSAGE));
    }

    @Test
    public void invalidExtension_notReentered_promptsReenter() {
        DriveRequestBuilder drive = wireDrive(file("file.pdf", ITEM_ID, 5L), null, null, null, null);
        UserRequestBuilder urb = mock(UserRequestBuilder.class);
        when(urb.drive()).thenReturn(drive);

        ExtensionResponse response = dao(urb).readTxtFile(PERSONAL_TXT, false);

        assertSame(REENTER_SENTINEL, response);
    }

    @Test
    public void invalidExtension_reentered_returnsError() {
        DriveRequestBuilder drive = wireDrive(file("file.pdf", ITEM_ID, 5L), null, null, null, null);
        UserRequestBuilder urb = mock(UserRequestBuilder.class);
        when(urb.drive()).thenReturn(drive);

        Map<String, Object> payload = dao(urb).readTxtFile(PERSONAL_TXT, true).getResponseValue();

        assertFalse((Boolean) payload.get(Constants.SUCCESS));
        assertEquals(Constants.INVALID_FILE_NAME_OR_INVALID_FILE_EXTENSION, payload.get(Constants.MESSAGE));
    }

    @Test
    public void fileNotFound_notReentered_promptsReenter() {
        DriveRequestBuilder drive = wireDrive(null, new IllegalArgumentException("itemNotFound"),
                null, null, null);
        UserRequestBuilder urb = mock(UserRequestBuilder.class);
        when(urb.drive()).thenReturn(drive);

        ExtensionResponse response = dao(urb).readTxtFile(PERSONAL_TXT, false);

        assertSame(REENTER_SENTINEL, response);
    }

    @Test
    public void fileNotFound_reentered_returnsError() {
        DriveRequestBuilder drive = wireDrive(null, new IllegalArgumentException("itemNotFound"),
                null, null, null);
        UserRequestBuilder urb = mock(UserRequestBuilder.class);
        when(urb.drive()).thenReturn(drive);

        Map<String, Object> payload = dao(urb).readTxtFile(PERSONAL_TXT, true).getResponseValue();

        assertFalse((Boolean) payload.get(Constants.SUCCESS));
        assertEquals(Constants.FILE_SPECIFIC_FILE_NOT_FOUND, payload.get(Constants.MESSAGE));
    }

    // ---- non-drive path ----

    @Test
    public void notDrivePath_notReentered_promptsReenter() {
        ExtensionResponse response = dao(mock(UserRequestBuilder.class)).readTxtFile("not-a-drive-path", false);

        assertSame(REENTER_SENTINEL, response);
    }

    @Test
    public void notDrivePath_reentered_returnsError() {
        Map<String, Object> payload =
                dao(mock(UserRequestBuilder.class)).readTxtFile("not-a-drive-path", true).getResponseValue();

        assertFalse((Boolean) payload.get(Constants.SUCCESS));
        assertEquals(Constants.FILE_SPECIFIC_FILE_NOT_FOUND, payload.get(Constants.MESSAGE));
    }

    // ---- edge cases in the too-large / valid-file conditions ----

    @Test
    public void resolvedItemNull_reentered_returnsInvalidExtension() {
        // getFileInfo reports the item exists but resolves to a null DriveItem.
        DriveRequestBuilder drive = wireDrive(null, null, null, null, null);
        UserRequestBuilder urb = mock(UserRequestBuilder.class);
        when(urb.drive()).thenReturn(drive);

        Map<String, Object> payload = dao(urb).readTxtFile(PERSONAL_TXT, true).getResponseValue();

        assertFalse((Boolean) payload.get(Constants.SUCCESS));
        assertEquals(Constants.INVALID_FILE_NAME_OR_INVALID_FILE_EXTENSION, payload.get(Constants.MESSAGE));
    }

    @Test
    public void resolvedItemIsFolder_reentered_returnsInvalidExtension() {
        DriveItem folderItem = file("subfolder", ITEM_ID, 5L);
        folderItem.folder = new com.microsoft.graph.models.Folder();
        DriveRequestBuilder drive = wireDrive(folderItem, null, null, null, null);
        UserRequestBuilder urb = mock(UserRequestBuilder.class);
        when(urb.drive()).thenReturn(drive);

        Map<String, Object> payload = dao(urb).readTxtFile(PERSONAL_TXT, true).getResponseValue();

        assertFalse((Boolean) payload.get(Constants.SUCCESS));
        assertEquals(Constants.INVALID_FILE_NAME_OR_INVALID_FILE_EXTENSION, payload.get(Constants.MESSAGE));
    }

    @Test
    public void nullSize_validTxt_readsContent() {
        // Null size skips the too-large check and still reads a valid file.
        DriveRequestBuilder drive = wireDrive(file("file.txt", ITEM_ID, null), null,
                stream("body"), null, ITEM_ID);
        UserRequestBuilder urb = mock(UserRequestBuilder.class);
        when(urb.drive()).thenReturn(drive);

        Map<String, Object> payload = dao(urb).readTxtFile(PERSONAL_TXT, false).getResponseValue();

        assertTrue((Boolean) payload.get(Constants.SUCCESS));
        assertEquals("body", ((String) payload.get("Content")).trim());
    }

    @Test
    public void nullName_reentered_returnsInvalidExtension() {
        DriveRequestBuilder drive = wireDrive(file(null, ITEM_ID, 5L), null, null, null, null);
        UserRequestBuilder urb = mock(UserRequestBuilder.class);
        when(urb.drive()).thenReturn(drive);

        Map<String, Object> payload = dao(urb).readTxtFile(PERSONAL_TXT, true).getResponseValue();

        assertFalse((Boolean) payload.get(Constants.SUCCESS));
        assertEquals(Constants.INVALID_FILE_NAME_OR_INVALID_FILE_EXTENSION, payload.get(Constants.MESSAGE));
    }

    @Test
    public void nullId_reentered_returnsInvalidExtension() {
        DriveRequestBuilder drive = wireDrive(file("file.txt", null, 5L), null, null, null, null);
        UserRequestBuilder urb = mock(UserRequestBuilder.class);
        when(urb.drive()).thenReturn(drive);

        Map<String, Object> payload = dao(urb).readTxtFile(PERSONAL_TXT, true).getResponseValue();

        assertFalse((Boolean) payload.get(Constants.SUCCESS));
        assertEquals(Constants.INVALID_FILE_NAME_OR_INVALID_FILE_EXTENSION, payload.get(Constants.MESSAGE));
    }

    // ---- catch block ----

    @Test
    public void downloadThrowsItemNotFound_returnsFileNotFound() {
        DriveRequestBuilder drive = wireDrive(file("file.txt", ITEM_ID, 5L), null,
                null, new IllegalArgumentException("Error code: itemNotFound"), ITEM_ID);
        UserRequestBuilder urb = mock(UserRequestBuilder.class);
        when(urb.drive()).thenReturn(drive);

        Map<String, Object> payload = dao(urb).readTxtFile(PERSONAL_TXT, false).getResponseValue();

        assertFalse((Boolean) payload.get(Constants.SUCCESS));
        assertEquals(Constants.FILE_SPECIFIC_FILE_NOT_FOUND, payload.get(Constants.MESSAGE));
    }

    @Test
    public void downloadThrowsGenericError_returnsGenericMessage() {
        DriveRequestBuilder drive = wireDrive(file("file.txt", ITEM_ID, 5L), null,
                null, new IllegalArgumentException("connection reset"), ITEM_ID);
        UserRequestBuilder urb = mock(UserRequestBuilder.class);
        when(urb.drive()).thenReturn(drive);

        Map<String, Object> payload = dao(urb).readTxtFile(PERSONAL_TXT, false).getResponseValue();

        assertFalse((Boolean) payload.get(Constants.SUCCESS));
        assertEquals("Error occurred during reading attachment", payload.get(Constants.MESSAGE));
    }

    @Test
    public void downloadThrowsNullMessage_returnsGenericMessage() {
        DriveRequestBuilder drive = wireDrive(file("file.txt", ITEM_ID, 5L), null,
                null, new IllegalArgumentException(), ITEM_ID);
        UserRequestBuilder urb = mock(UserRequestBuilder.class);
        when(urb.drive()).thenReturn(drive);

        Map<String, Object> payload = dao(urb).readTxtFile(PERSONAL_TXT, false).getResponseValue();

        assertFalse((Boolean) payload.get(Constants.SUCCESS));
        assertEquals("Error occurred during reading attachment", payload.get(Constants.MESSAGE));
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
        when(validationTypeUtil.checkValidationsReadText(any(), any(), any(), any()))
                .thenReturn(REENTER_SENTINEL);
        return new OperationsManagementDao(provider, mock(KristaMediaClient.class),
                validationTypeUtil, mock(TelemetryHelper.class));
    }

    /**
     * Wires a DriveRequestBuilder that serves both the resolution chain
     * ({@code root().itemWithPath(path).buildRequest().get()}) and, when {@code itemId} is non-null,
     * the download chain ({@code items(itemId).content().buildRequest().get()}).
     */
    private DriveRequestBuilder wireDrive(DriveItem resolveItem, RuntimeException resolveThrow,
                                          InputStream downloadStream, RuntimeException downloadThrow,
                                          String itemId) {
        DriveRequestBuilder drive = mock(DriveRequestBuilder.class);

        DriveItemRequest resolveReq = mock(DriveItemRequest.class);
        if (resolveThrow != null) {
            when(resolveReq.get()).thenThrow(resolveThrow);
        } else {
            when(resolveReq.get()).thenReturn(resolveItem);
        }
        DriveItemRequestBuilder itemBuilder = mock(DriveItemRequestBuilder.class);
        when(itemBuilder.buildRequest()).thenReturn(resolveReq);
        DriveItemRequestBuilder rootBuilder = mock(DriveItemRequestBuilder.class);
        when(rootBuilder.itemWithPath(anyString())).thenReturn(itemBuilder);
        when(drive.root()).thenReturn(rootBuilder);

        if (itemId != null) {
            DriveItemContentStreamRequest dlReq = mock(DriveItemContentStreamRequest.class);
            if (downloadThrow != null) {
                when(dlReq.get()).thenThrow(downloadThrow);
            } else {
                when(dlReq.get()).thenReturn(downloadStream);
            }
            DriveItemContentStreamRequestBuilder csrb = mock(DriveItemContentStreamRequestBuilder.class);
            when(csrb.buildRequest()).thenReturn(dlReq);
            DriveItemRequestBuilder dlBuilder = mock(DriveItemRequestBuilder.class);
            when(dlBuilder.content()).thenReturn(csrb);
            when(drive.items(itemId)).thenReturn(dlBuilder);
        }
        return drive;
    }

    private DriveItem file(String name, String id, Long size) {
        DriveItem driveItem = new DriveItem();
        driveItem.name = name;
        driveItem.id = id;
        driveItem.size = size;
        driveItem.folder = null;
        return driveItem;
    }

    private InputStream stream(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }
}
