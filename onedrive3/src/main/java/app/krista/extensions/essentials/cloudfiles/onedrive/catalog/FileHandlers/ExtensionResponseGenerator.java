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

package app.krista.extensions.essentials.cloudfiles.onedrive.catalog.FileHandlers;

import app.krista.extension.executor.*;
import app.krista.extensions.essentials.cloudfiles.onedrive.catalog.extensionRes.NamedFieldFactory;
import app.krista.extensions.essentials.cloudfiles.onedrive.catalog.extensionRes.RemediationActionFactory;
import app.krista.extensions.essentials.cloudfiles.onedrive.catalog.validators.ValidationOrchestrator;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.util.SubCatalogConstants;
import app.krista.model.field.NamedField;
import app.krista.model.field.NamedValuedField;
import org.jvnet.hk2.annotations.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Generates standardized extension responses for SharePoint operations, handling validation results and creating appropriate user feedback.
 */
@Service
public class ExtensionResponseGenerator {

    private static final String ERROR_MESSAGE = "ErrorMessage";
    private static final String STEP_MESSAGE = "StepMessage";
    private static final String FIELD = "Field";

    /**
     * Generates a confirmation response with reenter option for validation failures.
     */
    public ExtensionResponse generateConfirmationResponse(ExtensionResponse.Error.ExceptionType exceptionType,
                                                          List<ValidationOrchestrator.ValidationResult> validationResults,
                                                          String subCatalogRequestName,
                                                          Map<String, Object> state) {

        List<NamedField> fields = List.of(NamedFieldFactory.createSwitchField(SubCatalogConstants.REENTER));
        Map<String, Object> extensionResponseMap = generateResponse(validationResults, false);

        ExtensionResponse.Error error = new ErrorBuilder().exceptionType(exceptionType).build();

        return new ExtensionResponseBuilder().failure(error,
                new RemediationActions(List.of(RemediationActionFactory.createAskAction((String) extensionResponseMap.get(STEP_MESSAGE), fields)),
                        subCatalogRequestName), state).build();
    }

    /**
     * Creates a response map containing step messages, error messages, and fields based on validation results.
     */
    private Map<String, Object> generateResponse(List<ValidationOrchestrator.ValidationResult> validationResults,
                                                 boolean fetchResponse) {
        StringBuilder stepMessage = new StringBuilder();
        StringBuilder errMessage = new StringBuilder();
        List<NamedField> fields = new ArrayList<>();
        for (ValidationOrchestrator.ValidationResult validationResult : validationResults) {
            stepMessage.append(!fetchResponse ? validationResult.confirmStepMessage()
                    : validationResult.fetchStepMessage()).append("\n");
            errMessage.append(validationResult.errMessage()).append("\n");
            if (fetchResponse) {
                fields.add(NamedFieldFactory.createField(validationResult.fieldName(), validationResult.fieldType()));
            }
        }
        return Map.of(STEP_MESSAGE, stepMessage.toString(), ERROR_MESSAGE, errMessage.toString(), FIELD, fields);
    }

    /**
     * Generates a fetch response with input fields for correcting validation failures.
     */
    @SuppressWarnings("unchecked")
    public ExtensionResponse generateFetchResponse(ExtensionResponse.Error.ExceptionType exceptionType,
                                                   List<ValidationOrchestrator.ValidationResult> validationResults,
                                                   String subCatalogRequestName,
                                                   Map<String, Object> state) {

        Map<String, Object> stringStringMap = generateResponse(validationResults, true);
        ExtensionResponse.Error error = new ErrorBuilder().exceptionType(exceptionType).build();

        List<NamedField> fields = (List<NamedField>) stringStringMap.get(FIELD);

        return new ExtensionResponseBuilder()
                .failure(error, new RemediationActions(
                        List.of(RemediationActionFactory.createAskAction((String) stringStringMap.get(STEP_MESSAGE), fields)),
                        subCatalogRequestName), state)
                .build();
    }

    /**
     * Generates a deny response when users choose not to provide updated values for validation failures.
     */
    public ExtensionResponse generateFetchDenyResponse(
            ExtensionResponse.Error.ExceptionType exceptionType,
            List<ValidationOrchestrator.ValidationResult> validationResults,
            String subCatalogRequestName,
            Map<String, Object> state
    ) {
        StringJoiner stepMessage = new StringJoiner(", ", "We still need the following information: ", ". Please provide these values to continue.");
        for (ValidationOrchestrator.ValidationResult validationResult : validationResults) {
            stepMessage.add("'" + validationResult.fieldName() + "'");
        }
        ExtensionResponse.Error error = new ErrorBuilder().exceptionType(exceptionType).build();
        List<NamedValuedField> fields = List.of();

        return new ExtensionResponseBuilder()
                .failure(error, new RemediationActions(
                        List.of(RemediationActionFactory.createInformAction(stepMessage.toString(), RemediationAction.RecipientType.ALL_PARTICIPANTS, fields)),
                        subCatalogRequestName), state)
                .build();
    }

    /**
     * Generates a direct validation error response (WITHOUT retry).
     * Returns a simple error message without remediation actions.
     * Used when Allow Retry is disabled.
     *
     * @param validationResults List of validation failures
     * @return ExtensionResponse with direct error message
     */
    public ExtensionResponse generateValidationErrorResponse(
            List<ValidationOrchestrator.ValidationResult> validationResults) {

        // Build error message from validation results
        StringBuilder errMessage = new StringBuilder();
        for (ValidationOrchestrator.ValidationResult result : validationResults) {
            errMessage.append(result.errMessage()).append("\n");
        }

        // Return simple failure response without remediation
        return new ExtensionResponseBuilder()
                .failure(errMessage.toString().trim())
                .build();
    }
}

