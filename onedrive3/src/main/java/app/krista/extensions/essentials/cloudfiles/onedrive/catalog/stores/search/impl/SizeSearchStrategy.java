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

package app.krista.extensions.essentials.cloudfiles.onedrive.catalog.stores.search.impl;

import app.krista.extension.executor.SearchCondition;
import app.krista.extensions.essentials.cloudfiles.onedrive.catalog.entities.File;
import app.krista.extensions.essentials.cloudfiles.onedrive.catalog.stores.search.FileEntityCondition;
import app.krista.extensions.essentials.cloudfiles.onedrive.catalog.stores.search.FileSearchStrategy;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.FileEntitySearchImpl;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

public class SizeSearchStrategy implements FileSearchStrategy {
    @Override
    public List<File> search(FileEntitySearchImpl fileEntitySearch, SearchCondition condition) throws IOException {
        double size = FileEntityCondition.getSizeInDouble(condition);
        String operator = FileEntityCondition.getOperator(condition);
        return fileEntitySearch.getFilesBySize(size, operator);
    }

    @Override
    public List<File> fieldFilter(List<File> fileEntityList, SearchCondition condition) {
        double size = FileEntityCondition.getSizeInDouble(condition);
        String operator = FileEntityCondition.getOperator(condition);
        return fileEntityList.stream().filter(file -> FileEntityCondition.matchNumberFieldCondition(file.size, operator, size)).collect(Collectors.toList());
    }
}
