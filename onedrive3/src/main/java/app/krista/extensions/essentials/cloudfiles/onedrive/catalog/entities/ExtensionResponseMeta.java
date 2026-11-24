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

@Domain(id = "catEntryDomain_30c63e77-37f2-4a1d-afd8-f6bfda07ad57",
        name = "Cloud Files",
        ecosystemId = "catEntryEcosystem_84b53163-327b-4b1b-8c96-9334d292f9f5",
        ecosystemName = "Essentials",
        ecosystemVersion ="01cd47d2-43d7-4dec-97de-3a757028dd3a")
@Entity(name = "Extension Response Meta", id = "localDomainEntity_96b1e509-7c0c-46e5-b12f-33d2560c0fcb", primaryKey = "Message", supportStore = false)
public class ExtensionResponseMeta {

    @Field.Text(name = "Message", required = false, attributes = {@Attribute(name = "visualWidth", value = "L")})
    public String message;

    @Field(name = "Technical Detailed Error Report", type = "RichText", required = false, attributes = {@Attribute(name = "visualWidth", value = "L")})
    public String technicalDetailedErrorReport;

    @Field.PickOne(name = "Response Type", required = false, attributes = {@Attribute(name = "visualWidth", value = "S")})
    public String responseType;

    @Field(name = "Time Taken In Seconds", type = "Number", required = false, attributes = {@Attribute(name = "visualWidth", value = "S")})
    public Double timeTakenInSeconds;
}
