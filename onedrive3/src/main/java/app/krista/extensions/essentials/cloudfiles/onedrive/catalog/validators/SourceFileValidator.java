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


package app.krista.extensions.essentials.cloudfiles.onedrive.catalog.validators;

import java.util.Map;

import static app.krista.extensions.essentials.cloudfiles.onedrive.catalog.extensionRes.FieldTypes.TEXT_FIELD;

public class SourceFileValidator implements Validator {

    @Override
    public String getFetchFieldName(String fieldName) {
        return fieldName;
    }

    @Override
    public String getFieldType() {
        return TEXT_FIELD;
    }

    @Override
    public String getFetchStepMessage(String stepMessage) {
        return stepMessage;
    }

    @Override
    public String getConfirmationStepMessage(String resourceId, Map<ValidationResource, String> context) {
        return String.format("We couldn't find the file or folder at the path you provided: '%s'\n\nPlease check:\n• The path is correct and complete\n• The file or folder exists in your OneDrive\n• You have permission to access it\n\nWould you like to enter a different path?", resourceId);
    }

    public String getErrMessage(String resourceId) {
        return String.format("We couldn't find the file or folder at '%s'. Please verify the path is correct and the item exists in your OneDrive.", resourceId);
    }
}