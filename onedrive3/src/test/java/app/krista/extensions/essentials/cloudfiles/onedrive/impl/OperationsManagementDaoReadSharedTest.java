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
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.stores.KristaMediaClient;
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
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Regression test for the Read-from-shared-drive download bug.
 *
 * <p>A file under a SharePoint/shared drive is resolved via {@code drives/{driveId}/...}, but the
 * content download must ALSO target that same drive. The bug passed {@code null} as the driveId to
 * {@link app.krista.extensions.essentials.cloudfiles.onedrive.impl.util.FileUtil#getUserRequestBuilderWithItemId},
 * which routes to the operator's <em>personal</em> drive
 * ({@code /users/{email}/drive/items/{id}/content}) and 404s for items that live in a shared drive.
 *
 * <p>This test wires the resolution chain through the shared drive and makes the personal-drive
 * entry point ({@code UserRequestBuilder.drive()}) throw. So if the download ever falls back to the
 * personal drive again, the read fails and the assertions below break.
 */
public class OperationsManagementDaoReadSharedTest {

    private static final String DRIVE_ID = "b!testSharedDrive";
    private static final String SOURCE = "/drives/" + DRIVE_ID + "/root:/folder/file.txt";
    private static final String ITEM_ID = "ITEM1";
    private static final String FILE_CONTENT = "hello world";

    @Test
    public void readTxtFile_sharedDrive_downloadsFromSharedDriveNotPersonal() {
        // --- resolved file (a valid, small, readable .txt) ---
        DriveItem item = new DriveItem();
        item.name = "file.txt";
        item.id = ITEM_ID;
        item.size = (long) FILE_CONTENT.length();
        item.folder = null;

        // --- download request: drives(DRIVE_ID).items(ITEM_ID).content().buildRequest().get() ---
        DriveItemContentStreamRequest dlReq = mock(DriveItemContentStreamRequest.class);
        when(dlReq.get()).thenReturn(new ByteArrayInputStream(FILE_CONTENT.getBytes(StandardCharsets.UTF_8)));
        DriveItemContentStreamRequestBuilder csrb = mock(DriveItemContentStreamRequestBuilder.class);
        when(csrb.buildRequest()).thenReturn(dlReq);
        DriveItemRequestBuilder dlBuilder = mock(DriveItemRequestBuilder.class);
        when(dlBuilder.content()).thenReturn(csrb);

        // --- resolution request: drives(DRIVE_ID).root().itemWithPath(path).buildRequest().get() ---
        DriveItemRequest resolveReq = mock(DriveItemRequest.class);
        when(resolveReq.get()).thenReturn(item);
        DriveItemRequestBuilder itemBuilder = mock(DriveItemRequestBuilder.class);
        when(itemBuilder.buildRequest()).thenReturn(resolveReq);
        DriveItemRequestBuilder rootBuilder = mock(DriveItemRequestBuilder.class);
        when(rootBuilder.itemWithPath(anyString())).thenReturn(itemBuilder);

        // --- the shared drive builder serves BOTH root() (resolve) and items() (download) ---
        DriveRequestBuilder sharedDrive = mock(DriveRequestBuilder.class);
        when(sharedDrive.root()).thenReturn(rootBuilder);
        when(sharedDrive.items(ITEM_ID)).thenReturn(dlBuilder);

        UserRequestBuilder urb = mock(UserRequestBuilder.class);
        when(urb.drives(DRIVE_ID)).thenReturn(sharedDrive);
        // If the download ever falls back to the personal drive, this blows up the read.
        when(urb.drive()).thenThrow(new IllegalStateException(
                "BUG: shared-drive read must not download from the personal drive"));

        OperationsManagementDao dao = new OperationsManagementDao(
                providerReturning(urb), mock(KristaMediaClient.class),
                mock(ValidationTypeUtil.class), mock(TelemetryHelper.class));

        ExtensionResponse response = dao.readTxtFile(SOURCE, false);
        Map<String, Object> payload = response.getResponseValue();

        assertTrue("Read must succeed from the shared drive", (Boolean) payload.get("Success"));
        assertEquals("Content must come from the shared-drive item",
                FILE_CONTENT, ((String) payload.get("Content")).trim());
    }

    private GraphServiceClientProvider providerReturning(UserRequestBuilder urb) {
        return new GraphServiceClientProvider(null, null, null, null) {
            @Override
            public UserRequestBuilder getUserRequestBuilder(Boolean useEmail, String accountId) {
                return urb;
            }
        };
    }
}
