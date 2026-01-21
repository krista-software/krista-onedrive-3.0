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

import app.krista.extensions.essentials.cloudfiles.onedrive.catalog.extensionRes.FieldTypes;

import java.util.Map;

public class TargetFileValidator implements Validator {

    @Override
    public String getFetchFieldName(String fieldName) {
        return fieldName;
    }

    @Override
    public String getFieldType() {
        return FieldTypes.TEXT_FIELD;
    }

    @Override
    public String getFetchStepMessage(String stepMessage) {
        return stepMessage;
    }

    @Override
    public String getConfirmationStepMessage(String resourceId, Map<ValidationResource, String> context) {
        return String.format("The target path you provided isn't valid: '%s'\n\nPlease make sure:\n• The path format is correct (e.g., /Documents/MyFolder)\n• The parent folder exists\n• You have permission to write to this location\n\nWould you like to enter a different path?", resourceId);
    }

    public String getErrMessage(String resourceId) {
        return String.format("The target path '%s' is still invalid. Please check the path format and ensure the parent folder exists.", resourceId);
    }
}