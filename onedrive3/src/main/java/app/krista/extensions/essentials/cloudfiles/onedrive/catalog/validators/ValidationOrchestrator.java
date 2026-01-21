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

import app.krista.extensions.essentials.cloudfiles.onedrive.catalog.extensionRes.OneDriveFieldsConstants;
import org.jvnet.hk2.annotations.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ValidationOrchestrator {

    public ValidationOrchestrator() {
        validators.put(Validator.ValidationResource.TARGET_FILE, new TargetFileValidator());
        validators.put(Validator.ValidationResource.SOURCE_FILE, new SourceFileValidator());
        validators.put(Validator.ValidationResource.TARGET_PATH, new TargetFileValidator());
        validators.put(Validator.ValidationResource.FOLDER_NAME, new SourceFileValidator());
        validators.put(Validator.ValidationResource.TARGET_FILE_NAME, new SourceFileValidator());
        validators.put(Validator.ValidationResource.SOURCE_PATH, new SourceFileValidator());
        validators.put(Validator.ValidationResource.CUSTOM_NAME, new SourceFileValidator());
    }

    public record ValidationResult(String confirmStepMessage, String fieldName, String fetchStepMessage,
                                   String errMessage, String fieldType) {
    }

    private final Map<Validator.ValidationResource, Validator> validators = new ConcurrentHashMap<>();

    public List<ValidationResult> validate(Map<Validator.ValidationResource, String> resources, String errorMessage) {
        List<ValidationResult> results = new ArrayList<>();

        for (Map.Entry<Validator.ValidationResource, String> entry : resources.entrySet()) {
            Validator.ValidationResource resourceKey = entry.getKey();
            String resourceValue = entry.getValue();

            Validator validator = validators.get(resourceKey);

            if (validator == null) {
                throw new IllegalArgumentException("No validator found for resource: " + resourceKey);
            }

            String confirmStepMessage = validator.getConfirmationStepMessage(errorMessage + " " + resourceValue, resources);
            String fetchStepMessage = "";
            String errMessage = validator.getErrMessage(resourceValue);
            String fieldType = validator.getFieldType();
            String fieldNme = "";
            if (resourceKey == Validator.ValidationResource.SOURCE_FILE) {
                fieldNme = validator.getFetchFieldName(OneDriveFieldsConstants.SOURCE);
                fetchStepMessage = validator.getFetchStepMessage("Enter Source Path correctly");
            } else if (resourceKey == Validator.ValidationResource.TARGET_FILE) {
                fieldNme = validator.getFetchFieldName(OneDriveFieldsConstants.TARGET);
                fetchStepMessage = validator.getFetchStepMessage("Enter Target Path correctly");

            } else if (resourceKey == Validator.ValidationResource.EMAIL) {
                fieldNme = validator.getFetchFieldName(OneDriveFieldsConstants.EMAIL);
                fetchStepMessage = validator.getFetchStepMessage("Enter Email correctly");

            } else if (resourceKey == Validator.ValidationResource.TARGET_PATH) {
                fieldNme = validator.getFetchFieldName(OneDriveFieldsConstants.TARGET_PATH);
                fetchStepMessage = validator.getFetchStepMessage("Enter Target Path correctly");

            } else if (resourceKey == Validator.ValidationResource.FOLDER_NAME) {
                fieldNme = validator.getFetchFieldName(OneDriveFieldsConstants.FOLDER_NAME);
                fetchStepMessage = validator.getFetchStepMessage("Enter folder name correctly");

            } else if (resourceKey == Validator.ValidationResource.SOURCE_PATH) {
                fieldNme = validator.getFetchFieldName(OneDriveFieldsConstants.SOURCE_PATH);
                fetchStepMessage = validator.getFetchStepMessage("Enter Source path correctly");

            } else if (resourceKey == Validator.ValidationResource.TARGET_FILE_NAME) {
                fieldNme = validator.getFetchFieldName(OneDriveFieldsConstants.TARGET_FILE_NAME);
                fetchStepMessage = validator.getFetchStepMessage("Please Enter Target File Name correctly");

            } else if (resourceKey == Validator.ValidationResource.CUSTOM_NAME) {
                fieldNme = validator.getFetchFieldName(OneDriveFieldsConstants.CUSTOM_NAME);
                fetchStepMessage = validator.getFetchStepMessage("Please Enter Custom Name correctly");

            }
            results.add(new ValidationResult(confirmStepMessage, fieldNme, fetchStepMessage, errMessage, fieldType));
        }
        return results;
    }
}
