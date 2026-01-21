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

package app.krista.extensions.essentials.cloudfiles.onedrive.catalog.entities;

import app.krista.extension.impl.anno.*;

import java.util.List;
import java.util.Map;

@Domain(id = "catEntryDomain_30c63e77-37f2-4a1d-afd8-f6bfda07ad57",
        name = "Cloud Files",
        ecosystemId = "catEntryEcosystem_84b53163-327b-4b1b-8c96-9334d292f9f5",
        ecosystemName = "Essentials",
        ecosystemVersion = "a8626fe2-4d91-4667-b6ae-4f60bb914ccf")
@Entity(name = "File", id = "localDomainEntity_292c2a1d-0d6c-4307-a4d1-04176dd5a315", primaryKey = "Absolute Path", supportStore = false, options = {})
public class File {

    @Searchable
    @ToString
    @Field.Text(name = "Name", required = false, attributes = {@Attribute(name = "visualWidth", value = "S")}, options = {})
    public String name;

    @Searchable
    @Field.Text(name = "Base Name", required = false, attributes = {@Attribute(name = "visualWidth", value = "S")}, options = {})
    public String baseName;

    @Searchable
    @Field.Text(name = "Extension", required = false, attributes = {@Attribute(name = "visualWidth", value = "S")}, options = {})
    public String extension;

    @Searchable
    @ToString
    @Field.Text(name = "Parent Folder", required = false, attributes = {@Attribute(name = "visualWidth", value = "S")}, options = {})
    public String parentFolder;

    @Searchable
    @Field.Text(name = "Absolute Path", required = false, attributes = {@Attribute(name = "visualWidth", value = "S")}, options = {})
    public String absolutePath;

    @Searchable
    @Field.Text(name = "Owner", required = false, attributes = {@Attribute(name = "visualWidth", value = "S")}, options = {})
    public String owner;

    @Searchable
    @ToString
    @Field.Date(name = "Created", required = false, includeTimeOfDay = true, showHowManyDaysInViewer = 60, allowPast = true, allowToday = true, allowFuture = true, defaultTimeSpan = 1, attributes = {@Attribute(name = "visualWidth", value = "S")}, options = {})
    public Long created;

    @Searchable
    @ToString
    @Field.Date(name = "Modified", required = false, includeTimeOfDay = true, showHowManyDaysInViewer = 60, allowPast = true, allowToday = true, allowFuture = true, defaultTimeSpan = 1, attributes = {@Attribute(name = "visualWidth", value = "S")}, options = {})
    public Long modified;

    @Searchable
    @ToString
    @Field(name = "Size", type = "Number", required = false, attributes = {@Attribute(name = "visualWidth", value = "S")}, options = {})
    public Double size;

    @Searchable
    @Field.PickOne(name = "Permissions", values = {"Read", "Write", "Read and Write", "Own"}, required = false, attributes = {@Attribute(name = "visualWidth", value = "S")}, options = {})
    public String permissions;

    @Searchable
    @ToString
    @Field.Boolean(name = "Is Folder", required = false, attributes = {@Attribute(name = "visualWidth", value = "S")}, options = {})
    public Boolean isFolder;

    @Field.Desc(name = "Tags", type = "[ Text ]", required = false)
    public List<String> tags;

    @Field.Desc(name = "Shared With", type = "[ { User Email: Email, Permission: PickOne(Read|Write|Read and Write|Own) } ]", required = false)
    public List<Map<String, Object>> sharedWith;

    @Searchable
    @Field.Text(name = "Created By", required = false, attributes = {@Attribute(name = "visualWidth", value = "S")}, options = {})
    public String createdBy;

    @Searchable
    @Field.Text(name = "Modified By", required = false, attributes = {@Attribute(name = "visualWidth", value = "S")}, options = {})
    public String modifiedBy;

}