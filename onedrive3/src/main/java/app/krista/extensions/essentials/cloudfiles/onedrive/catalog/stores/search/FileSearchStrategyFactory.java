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

package app.krista.extensions.essentials.cloudfiles.onedrive.catalog.stores.search;

import app.krista.extension.executor.SearchCondition;
import app.krista.extensions.essentials.cloudfiles.onedrive.catalog.stores.search.impl.*;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.util.Constants;

public class FileSearchStrategyFactory {
    public static final String MODIFIED = "Modified";
    public static final String CREATED = "Created";
    public static final String NAME = "Name";
    public static final String EXTENSION = "Extension";
    public static final String BASE_NAME = "Base Name";
    public static final String CREATED_BY = "Created By";
    public static final String MODIFIED_BY = "Modified By";
    public static final String OWNER = "Owner";
    public static final String PARENT_FOLDER = "Parent Folder";
    public static final String ABSOLUTE_PATH = "Absolute Path";
    public static final String IS_FOLDER = "Is Folder";

    private FileSearchStrategyFactory() {
    }

    public static FileSearchStrategy getStrategy(SearchCondition condition) {
        String fieldName = condition.getFieldName();
        return switch (fieldName) {
            case Constants.SIZE -> new SizeSearchStrategy();
            case MODIFIED -> new DateSearchStrategy();
            case CREATED -> new DateSearchStrategy();
            case NAME -> new NameSearchStrategy();
            case BASE_NAME -> new BaseNameSearchStrategy();
            case EXTENSION -> new ExtensionSearchStrategy();
            case CREATED_BY -> new CreatedBySearchStrategy();
            case MODIFIED_BY -> new ModifiedBySearchStrategy();
            case IS_FOLDER -> new FolderSearchStrategy();
            case OWNER -> new OwnerSearchStrategy();
            case PARENT_FOLDER -> new ParentPathSearchStrategy();
            case ABSOLUTE_PATH -> new AbsolutePathSearchStrategy();
            default -> null;
        };
    }
}
