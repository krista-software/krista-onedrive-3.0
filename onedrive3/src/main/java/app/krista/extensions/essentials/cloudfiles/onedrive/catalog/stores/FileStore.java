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

package app.krista.extensions.essentials.cloudfiles.onedrive.catalog.stores;

import app.krista.extension.executor.SearchCondition;
import app.krista.extension.executor.SearchQuery;
import app.krista.extension.util.EntityStore;
import app.krista.extensions.essentials.cloudfiles.onedrive.catalog.CatalogTypes;
import app.krista.extensions.essentials.cloudfiles.onedrive.catalog.entities.File;
import app.krista.extensions.essentials.cloudfiles.onedrive.catalog.stores.search.FileSearchStrategy;
import app.krista.extensions.essentials.cloudfiles.onedrive.catalog.stores.search.FileSearchStrategyFactory;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.FileEntitySearchImpl;
import org.jvnet.hk2.annotations.Service;

import javax.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class FileStore implements EntityStore<File> {
    private final FileEntitySearchImpl fileEntitySearch;

    @Inject
    public FileStore(FileEntitySearchImpl fileEntitySearch) {
        this.fileEntitySearch = fileEntitySearch;
    }

    @Override
    public File create(Map<String, Object> map) throws IOException {
        return null;
    }

    @Override
    public File get(String primaryKey) throws IOException {
        return CatalogTypes.fromFile(fileEntitySearch.getFileByKey(primaryKey));
    }

    @Override
    public File update(File file) throws IOException {
        return null;
    }

    @Override
    public void delete(String s) throws IOException {
        // Do Nothing
    }

    @Override
    public boolean contains(String s) throws IOException {
        return false;
    }

    /**
     * @deprecated
     */
    @Deprecated(since = "1.5")
    @Override
    public List<File> search(List<SearchCondition> conditions, long pageIndex, int pageSize) throws IOException {
        return Collections.emptyList();
    }

    @Override
    public List<File> search(SearchQuery searchQuery, long pageIndex, int pageSize) throws IOException {
        List<SearchCondition> conditions = searchQuery.getSearchConditions();
        List<File> fileDetails = new ArrayList<>();
        for (SearchCondition condition : conditions) {
            FileSearchStrategy searchStrategy = FileSearchStrategyFactory.getStrategy(condition);
            if (searchStrategy == null) {
                continue;
            }

            if (fileDetails.isEmpty()) {
                fileDetails.addAll(searchStrategy.search(fileEntitySearch, condition));
            } else {
                fileDetails = searchStrategy.fieldFilter(fileDetails, condition);
            }

            if (!condition.getFieldName().equals("logicalName") && fileDetails.isEmpty()) {
                break;
            }
        }

        if (conditions.size() == 1 && fileDetails.isEmpty()) {
            fileDetails = fileEntitySearch.getFiles().stream()
                    .map(CatalogTypes::fromFile)
                    .collect(Collectors.toList());
        }
        return fileDetails.stream().skip(Math.max((pageIndex - 1) * pageSize, 0))
                .limit(pageSize)
                .collect(Collectors.toList());
    }

    @Override
    public long count(List<SearchCondition> list) throws IOException {
        return 0;
    }

    @Override
    public List<String> lookup(Map<String, Object> map, List<String> list) throws IOException {
        return List.of();
    }
}
