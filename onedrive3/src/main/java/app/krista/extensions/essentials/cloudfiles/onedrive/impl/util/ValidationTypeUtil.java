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

import app.krista.extension.executor.ExtensionResponse;
import app.krista.extensions.essentials.cloudfiles.onedrive.catalog.FileHandlers.ErrorHandlingStateManager;
import app.krista.extensions.essentials.cloudfiles.onedrive.catalog.FileHandlers.ExtensionResponseGenerator;
import app.krista.extensions.essentials.cloudfiles.onedrive.catalog.extensionRes.OneDriveFieldsConstants;
import app.krista.extensions.essentials.cloudfiles.onedrive.catalog.validators.ValidationOrchestrator;
import app.krista.extensions.essentials.cloudfiles.onedrive.catalog.validators.Validator;
import app.krista.model.base.File;
import org.jvnet.hk2.annotations.Service;

import javax.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Utility service for validating various resources in SharePoint operations.
 * This class provides methods to check validations for different types of resources
 * such as files, paths, emails, sizes, and date ranges. It handles validation failures
 * by generating appropriate extension responses.
 *
 * @author Vaibhav Choudhary
 */
@Service
public class ValidationTypeUtil {

    private final ValidationOrchestrator validationOrchestrator;
    private final ExtensionResponseGenerator responseGenerator;
    private final ErrorHandlingStateManager internalStateManager;

    @Inject
    public ValidationTypeUtil(ValidationOrchestrator validationOrchestrator, ExtensionResponseGenerator responseGenerator, ErrorHandlingStateManager internalStateManager) {
        this.validationOrchestrator = validationOrchestrator;
        this.responseGenerator = responseGenerator;
        this.internalStateManager = internalStateManager;
    }


    public ExtensionResponse checkValidationsWithErrorMessage(Validator.ValidationResource validationType, String subCatalogConstant, String absolutePath, String errorMessage) {
        List<ValidationOrchestrator.ValidationResult> validationResults =
                validationOrchestrator.validate(Map.of(validationType, absolutePath), errorMessage);
        return handleValidationFailure(validationResults, subCatalogConstant, absolutePath);
    }

    public ExtensionResponse checkValidationsReadText(Validator.ValidationResource validationType, String subCatalogConstant, String absolutePath, String errorMessage) {
        List<ValidationOrchestrator.ValidationResult> validationResults = validationOrchestrator.validate(Map.of(validationType, absolutePath), errorMessage);
        return handleValidationFailure(validationResults, subCatalogConstant, absolutePath);
    }

    public ExtensionResponse checkValidations(Validator.ValidationResource validationType, String subCatalogConstant, String absolutePath, List<Map<String, Object>> share) {
        List<ValidationOrchestrator.ValidationResult> validationResults = validationOrchestrator.validate(Map.of(validationType, absolutePath), "");
        return handleValidationFailure(validationResults, subCatalogConstant, absolutePath, share);
    }

    public ExtensionResponse checkValidationsCopyFile(Validator.ValidationResource validationType, String subCatalogConstant,
                                                      String reenterField, String existingField, Boolean replace,
                                                      Boolean createFolder, Boolean isTarget, String errorMessage) {
        List<ValidationOrchestrator.ValidationResult> validationResults = validationOrchestrator.validate(Map.of(validationType, reenterField), errorMessage);
        return handleValidationFailure(validationResults, subCatalogConstant, reenterField, existingField, replace, createFolder, isTarget);
    }

    public ExtensionResponse checkValidationsMoveFile(String subCatalogConstant, String source, String target, Boolean replace, Boolean createFolder, Boolean isTarget, String errorMessage) {

        List<ValidationOrchestrator.ValidationResult> validationResults = validationOrchestrator.validate(Map.of(
                Validator.ValidationResource.SOURCE_FILE, source, Validator.ValidationResource.TARGET_FILE, target), errorMessage);
        return handleValidationFailureMoveFile(validationResults, subCatalogConstant, source, target, replace, createFolder, isTarget);
    }

    public ExtensionResponse checkValidationsWriteAsText(Validator.ValidationResource validationType, String subCatalogConstant, String targetPath, String fileContent) {
        List<ValidationOrchestrator.ValidationResult> validationResults = validationOrchestrator.validate(Map.of(validationType, targetPath), "");
        return handleValidationFailure(validationResults, subCatalogConstant, targetPath, fileContent);
    }

    public ExtensionResponse checkValidationsRename(Validator.ValidationResource validationType, String subCatalogConstant, String source, String targetFileName, boolean isSourceReentered, String errorMessage) {
        List<ValidationOrchestrator.ValidationResult> validationResults = validationOrchestrator.validate(Map.of(validationType, source), errorMessage);
        return handleValidationFailure(validationResults, subCatalogConstant, source, targetFileName, isSourceReentered);
    }

    public ExtensionResponse checkValidationsTwoFields(Validator.ValidationResource validationType, String subCatalogConstant, String reenterField, String existingField, boolean isReenteredField) {
        List<ValidationOrchestrator.ValidationResult> validationResults = validationOrchestrator.validate(Map.of(validationType, reenterField), "");
        return handleValidationFailureSiteList(validationResults, subCatalogConstant, reenterField, existingField, isReenteredField);
    }

    public ExtensionResponse checkValidationsWrite(Validator.ValidationResource validationType, String subCatalogConstant, File kristaFile, String target, String customName, boolean isCustomNameReentered, String errorMessage) {
        List<ValidationOrchestrator.ValidationResult> validationResults = validationOrchestrator.validate(Map.of(validationType, isCustomNameReentered ? customName : target), errorMessage);
        return handleValidationFailure(validationResults, subCatalogConstant, kristaFile, target, customName, isCustomNameReentered);
    }

    public ExtensionResponse checkValidationsVersionHistory(Validator.ValidationResource validationType, String subCatalogConstant, String sourcePath, String search, Long since) {
        List<ValidationOrchestrator.ValidationResult> validationResults = validationOrchestrator.validate(Map.of(validationType, sourcePath), "");
        return handleValidationFailure(validationResults, subCatalogConstant, sourcePath, search, since);
    }

    public ExtensionResponse checkValidationsWithInfo(Validator.ValidationResource validationType, String subCatalogConstant, String sourcePath, boolean useEmail, String accountID) {
        List<ValidationOrchestrator.ValidationResult> validationResults = validationOrchestrator.validate(Map.of(validationType, sourcePath), "");
        return handleValidationFailure(validationResults, subCatalogConstant, sourcePath, useEmail, accountID);
    }

    public ExtensionResponse checkValidationsListFilesOrFolder(Validator.ValidationResource validationType, String subCatalogConstant, String sourcePath, String search, boolean useEmail, String accountID) {
        List<ValidationOrchestrator.ValidationResult> validationResults = validationOrchestrator.validate(Map.of(validationType, sourcePath), "");
        return handleValidationFailure(validationResults, subCatalogConstant, sourcePath, search, useEmail, accountID);
    }

    private ExtensionResponse handleValidationFailure(List<ValidationOrchestrator.ValidationResult> validationResult, String subCatalogConstant, String absolutePath) {
        String stateId = UUID.randomUUID().toString();

        internalStateManager.put(stateId, Constants.GSON.toJson(validationResult));
        return responseGenerator.generateConfirmationResponse(
                ExtensionResponse.Error.ExceptionType.INPUT_ERROR,
                validationResult,
                subCatalogConstant,
                Map.of(
                        OneDriveFieldsConstants.STATE_ID, stateId,
                        OneDriveFieldsConstants.TARGET, absolutePath != null ? absolutePath : "",
                        OneDriveFieldsConstants.SOURCE, absolutePath != null ? absolutePath : ""
                )
        );
    }

    private ExtensionResponse handleValidationFailure(List<ValidationOrchestrator.ValidationResult> validationResult, String subCatalogConstant, String reenterField, String existingField, Boolean replace, Boolean createFolder, Boolean isTarget) {
        String stateId = UUID.randomUUID().toString();

        internalStateManager.put(stateId, Constants.GSON.toJson(validationResult));
        return responseGenerator.generateConfirmationResponse(
                ExtensionResponse.Error.ExceptionType.INPUT_ERROR,
                validationResult,
                subCatalogConstant,
                Map.of(
                        OneDriveFieldsConstants.STATE_ID, stateId,
                        OneDriveFieldsConstants.TARGET, isTarget ? reenterField : existingField,
                        OneDriveFieldsConstants.SOURCE, isTarget ? existingField : reenterField,
                        OneDriveFieldsConstants.REPLACE, replace,
                        OneDriveFieldsConstants.CREATE_FOLDER, createFolder,
                        OneDriveFieldsConstants.IS_TARGET, isTarget
                )
        );
    }

    private ExtensionResponse handleValidationFailureMoveFile(List<ValidationOrchestrator.ValidationResult> validationResult, String subCatalogConstant, String source, String target, Boolean replace, Boolean createFolder, Boolean isTarget) {
        String stateId = UUID.randomUUID().toString();

        internalStateManager.put(stateId, Constants.GSON.toJson(validationResult));
        return responseGenerator.generateConfirmationResponse(
                ExtensionResponse.Error.ExceptionType.INPUT_ERROR,
                validationResult,
                subCatalogConstant,
                Map.of(
                        OneDriveFieldsConstants.STATE_ID, stateId,
                        OneDriveFieldsConstants.SOURCE, source,
                        OneDriveFieldsConstants.TARGET, target,
                        OneDriveFieldsConstants.REPLACE, replace,
                        OneDriveFieldsConstants.CREATE_FOLDER, createFolder,
                        OneDriveFieldsConstants.IS_TARGET, isTarget
                )
        );
    }

    private ExtensionResponse handleValidationFailure(List<ValidationOrchestrator.ValidationResult> validationResult, String subCatalogConstant, String targetPath, String fileContent) {
        String stateId = UUID.randomUUID().toString();
        internalStateManager.put(stateId, Constants.GSON.toJson(validationResult));
        return responseGenerator.generateConfirmationResponse(
                ExtensionResponse.Error.ExceptionType.INPUT_ERROR,
                validationResult,
                subCatalogConstant,
                Map.of(
                        OneDriveFieldsConstants.STATE_ID, stateId,
                        OneDriveFieldsConstants.TARGET_PATH, targetPath,
                        OneDriveFieldsConstants.FILE_CONTENT, fileContent
                )
        );
    }

    private ExtensionResponse handleValidationFailure(List<ValidationOrchestrator.ValidationResult> validationResult, String subCatalogConstant, String source, String targetFileName, boolean isSourceReentered) {
        String stateId = UUID.randomUUID().toString();
        internalStateManager.put(stateId, Constants.GSON.toJson(validationResult));
        return responseGenerator.generateConfirmationResponse(
                ExtensionResponse.Error.ExceptionType.INPUT_ERROR,
                validationResult,
                subCatalogConstant,
                Map.of(
                        OneDriveFieldsConstants.STATE_ID, stateId,
                        OneDriveFieldsConstants.SOURCE_PATH, isSourceReentered ? source : targetFileName,
                        OneDriveFieldsConstants.TARGET_FILE_NAME, isSourceReentered ? targetFileName : source,
                        OneDriveFieldsConstants.IS_SOURCE, isSourceReentered
                )
        );
    }

    private ExtensionResponse handleValidationFailureSiteList(List<ValidationOrchestrator.ValidationResult> validationResult, String subCatalogConstant, String reenterField, String existingField, Boolean isReenteredField) {
        String stateId = UUID.randomUUID().toString();
        internalStateManager.put(stateId, Constants.GSON.toJson(validationResult));

        return responseGenerator.generateConfirmationResponse(
                ExtensionResponse.Error.ExceptionType.INPUT_ERROR,
                validationResult,
                subCatalogConstant,
                Map.of(
                        OneDriveFieldsConstants.STATE_ID, stateId,
                        OneDriveFieldsConstants.SITE_NAME, validationResult.getFirst().fieldName().equals(OneDriveFieldsConstants.SITE_NAME) ? reenterField : existingField,
                        OneDriveFieldsConstants.LIST_NAME, validationResult.getFirst().fieldName().equals(OneDriveFieldsConstants.SITE_NAME) ? existingField : reenterField,
                        validationResult.getFirst().fieldName().equals(OneDriveFieldsConstants.GROUP_NAME) || validationResult.getFirst().fieldName().equals(OneDriveFieldsConstants.MAIL_NICK_NAME) ? OneDriveFieldsConstants.IS_GROUP_NAME :
                                validationResult.getFirst().fieldName().equals(OneDriveFieldsConstants.FOLDER_NAME) || validationResult.getFirst().fieldName().equals(OneDriveFieldsConstants.TARGET_PATH) ? OneDriveFieldsConstants.IS_FOLDER_NAME : OneDriveFieldsConstants.IS_SITE, isReenteredField,
                        OneDriveFieldsConstants.NEW_GROUP_NAME, existingField,
                        OneDriveFieldsConstants.DISPLAY_NAME, reenterField,
                        OneDriveFieldsConstants.FOLDER_NAME, validationResult.getFirst().fieldName().equals(OneDriveFieldsConstants.FOLDER_NAME) ? reenterField : existingField,
                        OneDriveFieldsConstants.TARGET_PATH, validationResult.getFirst().fieldName().equals(OneDriveFieldsConstants.FOLDER_NAME) ? existingField : reenterField,
                        OneDriveFieldsConstants.GROUP_NAME, validationResult.getFirst().fieldName().equals(OneDriveFieldsConstants.GROUP_NAME) ? reenterField : existingField,
                        OneDriveFieldsConstants.MAIL_NICK_NAME, validationResult.getFirst().fieldName().equals(OneDriveFieldsConstants.GROUP_NAME) ? existingField : reenterField
                )
        );
    }

    private ExtensionResponse handleValidationFailure(List<ValidationOrchestrator.ValidationResult> validationResult, String subCatalogConstant, String source, List<Map<String, Object>> share) {
        String stateId = UUID.randomUUID().toString();
        internalStateManager.put(stateId, Constants.GSON.toJson(validationResult));
        return responseGenerator.generateConfirmationResponse(
                ExtensionResponse.Error.ExceptionType.INPUT_ERROR,
                validationResult,
                subCatalogConstant,
                Map.of(
                        OneDriveFieldsConstants.STATE_ID, stateId,
                        OneDriveFieldsConstants.SOURCE, source,
                        OneDriveFieldsConstants.SHARE, share
                )
        );
    }

    private ExtensionResponse handleValidationFailure(List<ValidationOrchestrator.ValidationResult> validationResult, String subCatalogConstant, File kristaFile, String target, String customName, boolean isCustomReentered) {
        String stateId = UUID.randomUUID().toString();
        internalStateManager.put(stateId, Constants.GSON.toJson(validationResult));
        return responseGenerator.generateConfirmationResponse(
                ExtensionResponse.Error.ExceptionType.INPUT_ERROR,
                validationResult,
                subCatalogConstant,
                Map.of(
                        OneDriveFieldsConstants.STATE_ID, stateId,
                        OneDriveFieldsConstants.TARGET_PATH, target,
                        OneDriveFieldsConstants.KRISTA_FILE, List.of(kristaFile),
                        OneDriveFieldsConstants.CUSTOM_NAME, customName,
                        OneDriveFieldsConstants.IS_TARGET, !isCustomReentered
                )
        );
    }

    private ExtensionResponse handleValidationFailure(List<ValidationOrchestrator.ValidationResult> validationResult, String subCatalogConstant, String sourcePath, String search, Long since) {
        String stateId = UUID.randomUUID().toString();
        internalStateManager.put(stateId, Constants.GSON.toJson(validationResult));
        return responseGenerator.generateConfirmationResponse(
                ExtensionResponse.Error.ExceptionType.INPUT_ERROR,
                validationResult,
                subCatalogConstant,
                Map.of(
                        OneDriveFieldsConstants.STATE_ID, stateId,
                        OneDriveFieldsConstants.SOURCE_PATH, sourcePath,
                        OneDriveFieldsConstants.SEARCH, search,
                        OneDriveFieldsConstants.SINCE, since
                )
        );
    }

    private ExtensionResponse handleValidationFailure(List<ValidationOrchestrator.ValidationResult> validationResult, String subCatalogConstant, String sourcePath, String search, boolean useEmail, String accountID) {
        String stateId = UUID.randomUUID().toString();
        internalStateManager.put(stateId, Constants.GSON.toJson(validationResult));
        return responseGenerator.generateConfirmationResponse(
                ExtensionResponse.Error.ExceptionType.INPUT_ERROR,
                validationResult,
                subCatalogConstant,
                Map.of(
                        OneDriveFieldsConstants.STATE_ID, stateId,
                        OneDriveFieldsConstants.SOURCE, sourcePath,
                        OneDriveFieldsConstants.SEARCH, search,
                        OneDriveFieldsConstants.USE_MAIL, useEmail,
                        OneDriveFieldsConstants.ACCOUNT_ID, accountID
                )
        );
    }

    private ExtensionResponse handleValidationFailure(List<ValidationOrchestrator.ValidationResult> validationResult, String subCatalogConstant, String sourcePath, boolean useEmail, String accountID) {
        String stateId = UUID.randomUUID().toString();
        internalStateManager.put(stateId, Constants.GSON.toJson(validationResult));
        return responseGenerator.generateConfirmationResponse(
                ExtensionResponse.Error.ExceptionType.INPUT_ERROR,
                validationResult,
                subCatalogConstant,
                Map.of(
                        OneDriveFieldsConstants.STATE_ID, stateId,
                        OneDriveFieldsConstants.SOURCE, sourcePath != null ? sourcePath : "",
                        OneDriveFieldsConstants.USE_MAIL, useEmail,
                        OneDriveFieldsConstants.ACCOUNT_ID, accountID != null ? accountID : ""
                )
        );
    }
}
