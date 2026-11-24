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

package app.krista.extensions.essentials.cloudfiles.onedrive.impl.util;

import com.microsoft.graph.requests.DriveItemDeltaCollectionPage;

import java.util.HashMap;
import java.util.Map;

public class FileChangeResourceUtil {

    private FileChangeResourceUtil() {
    }

    private static Map<String, DriveItemDeltaCollectionPage> FILE_CHANGE_RESOURCE = new HashMap<>();

    public static void addFileMetaData(String routingURL, DriveItemDeltaCollectionPage page) {
        FILE_CHANGE_RESOURCE.put(routingURL, page);
    }

    public static DriveItemDeltaCollectionPage getFileChangeResource(String routingURL) {
        return FILE_CHANGE_RESOURCE.get(routingURL);
    }
}
