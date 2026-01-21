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

import app.krista.extension.executor.ExtensionResponse;
import app.krista.extension.impl.anno.Attribute;
import app.krista.extension.impl.anno.CatalogRequest;
import app.krista.extension.impl.anno.Field;
import app.krista.extension.impl.anno.SubCatalogRequest;
import app.krista.extensions.essentials.cloudfiles.onedrive.catalog.FileHandlers.ErrorHandlingStateManager;
import app.krista.extensions.essentials.cloudfiles.onedrive.catalog.FileHandlers.ExtensionResponseGenerator;
import app.krista.extensions.essentials.cloudfiles.onedrive.catalog.extensionRes.OneDriveFieldsConstants;
import app.krista.extensions.essentials.cloudfiles.onedrive.catalog.validators.ValidationOrchestrator;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.*;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.util.SubCatalogConstants;
import com.microsoft.graph.http.GraphServiceException;
import org.jetbrains.annotations.NotNull;
import org.jvnet.hk2.annotations.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ManagementAreaSubCatalogRequest {

    private static final Logger LOGGER = LoggerFactory.getLogger(ManagementAreaSubCatalogRequest.class);

    private final ExtensionResponseGenerator responseGenerator;
    private final ErrorHandlingStateManager internalStateManager;
    private final CopyFileDao copyFileDao;
    private final OperationsManagementDao operationsManagementDao;
    private final FileManagementDao fileManagementDao;
    private final SharedFileDao sharedFileDao;
    private final FileVersionOperations fileVersionOperations;

    @Inject
    public ManagementAreaSubCatalogRequest(ExtensionResponseGenerator responseGenerator, ErrorHandlingStateManager internalStateManager, CopyFileDao copyFileDao, OperationsManagementDao operationsManagementDao, FileManagementDao fileManagementDao, SharedFileDao sharedFileDao, FileVersionOperations fileVersionOperations) {
        this.responseGenerator = responseGenerator;
        this.internalStateManager = internalStateManager;
        this.copyFileDao = copyFileDao;
        this.operationsManagementDao = operationsManagementDao;
        this.fileManagementDao = fileManagementDao;
        this.sharedFileDao = sharedFileDao;
        this.fileVersionOperations = fileVersionOperations;
    }

    private ExtensionResponse getExtensionResponse(Map<String, Object> map, String validationType) {
        Boolean reenter = (Boolean) map.get(SubCatalogConstants.REENTER);

        String stateId = (String) map.get(OneDriveFieldsConstants.STATE_ID);
        List<ValidationOrchestrator.ValidationResult> validationResults = internalStateManager.get(stateId);
        if (Boolean.FALSE.equals(reenter)) {
            return responseGenerator.generateFetchDenyResponse(
                    ExtensionResponse.Error.ExceptionType.INPUT_ERROR,
                    validationResults,
                    null,
                    Map.of());
        }

        return responseGenerator.generateFetchResponse(
                ExtensionResponse.Error.ExceptionType.INPUT_ERROR,
                validationResults,
                validationType,
                addMetaDataToLinkedHashMap(map, stateId));
    }

    @NotNull
    private static Map<String, Object> addMetaDataToLinkedHashMap(Map<String, Object> map, String stateId) {
        Map<String, Object> metaData = new LinkedHashMap<>();
        metaData.put("stateId", stateId);
        metaData.putAll(map);
        return metaData;
    }

    @SubCatalogRequest(
            name = SubCatalogConstants.CONFIRM_REENTER_SOURCE_FILE_COPY,
            description = "Checks if user wants to re-enter Source Path",
            type = CatalogRequest.Type.QUERY_SYSTEM
    )
    public ExtensionResponse confirmReEnterSourceFileCopy(@Field.Desc(
            name = "inputMap",
            type = "{ Reenter: Boolean, stateId: Text,Source:Text,Target: Text,Replace:Boolean,Create Folder:Boolean,isTarget: Boolean}"
    ) Map<String, Object> map) {

        return getExtensionResponse(map, SubCatalogConstants.HANDLE_REENTER_SOURCE_FILE_COPY);
    }

    @SubCatalogRequest(
            name = SubCatalogConstants.HANDLE_REENTER_SOURCE_FILE_COPY,
            description = "Handle reenter source Path",
            type = CatalogRequest.Type.CHANGE_SYSTEM
    )

    @Field.Desc(name = "File", type = "Entity(File)", required = false)
    @Field.Text(name = "Message", required = false, attributes = {@Attribute(name = "visualWidth", value = "S")})
    @Field.Boolean(name = "Success", required = false, attributes = {@Attribute(name = "visualWidth", value = "S")})
    public ExtensionResponse handleReEnterSourceFileCopy(
            @Field.Desc(name = "inputMap", type = "{ Reenter: Boolean, stateId: Text,Source:Text,Target: Text,Replace:Boolean,Create Folder:Boolean,isTarget: Boolean}") Map<String, Object> map) {

        String sourcePath = (String) map.get(OneDriveFieldsConstants.SOURCE);

        if (sourcePath == null || sourcePath.isEmpty()) {
            LOGGER.info(" sourcePath is empty: {}", sourcePath);
        }
        return copyFile(map);
    }

    private ExtensionResponse copyFile(Map<String, Object> map) {
        String source = (String) map.get(OneDriveFieldsConstants.SOURCE);
        String target = (String) map.get(OneDriveFieldsConstants.TARGET);
        Boolean replace = (Boolean) map.get(OneDriveFieldsConstants.REPLACE);
        Boolean createFolder = (Boolean) map.get(OneDriveFieldsConstants.CREATE_FOLDER);
        Boolean isTarget = (Boolean) map.get(OneDriveFieldsConstants.IS_TARGET);
        try {
            if (isTarget)
                return copyFileDao.copyFile(source, target, replace, createFolder, true, false);
            else
                return copyFileDao.copyFile(source, target, replace, createFolder, false, true);

        } catch (GraphServiceException | IllegalArgumentException cause) {
            throw new IllegalArgumentException("Enter Source file correctly", cause);
        }
    }


    @SubCatalogRequest(
            name = SubCatalogConstants.CONFIRM_REENTER_TARGET_FILE_DELETION,
            description = "Checks if user wants to re-enter File name",
            type = CatalogRequest.Type.QUERY_SYSTEM
    )
    public ExtensionResponse confirmReEnterTargetFileDeletion(@Field.Desc(
            name = "inputMap",
            type = "{ Reenter: Boolean, stateId: Text,target: Text}"
    ) Map<String, Object> map) {

        return getExtensionResponse(map, SubCatalogConstants.HANDLE_REENTER_TARGET_FILE_DELETION);
    }

    @SubCatalogRequest(
            name = SubCatalogConstants.HANDLE_REENTER_TARGET_FILE_DELETION,
            description = "Handle reenter File name",
            type = CatalogRequest.Type.CHANGE_SYSTEM
    )

    @Field.Boolean(name = "Success", required = false, attributes = {@Attribute(name = "visualWidth", value = "S")})
    @Field.Text(name = "Message", required = false, attributes = {@Attribute(name = "visualWidth", value = "S")})
    public ExtensionResponse handleReenterFileName(
            @Field.Desc(name = "inputMap", type = "{ Reenter: Boolean, stateId: Text,Target: Text}") Map<String, Object> map) {

        String filename = (String) map.get(OneDriveFieldsConstants.TARGET);

        if (filename == null || filename.isEmpty()) {
            LOGGER.info(" File name is empty: {}", filename);
        }
        return deleteTargetFile(map);
    }

    private ExtensionResponse deleteTargetFile(Map<String, Object> map) {
        String target = (String) map.get(OneDriveFieldsConstants.TARGET);
        try {
            return operationsManagementDao.deleteFile(target, true);
        } catch (GraphServiceException | IllegalArgumentException cause) {
            throw new IllegalArgumentException("Enter target file correctly", cause);
        }
    }

    @SubCatalogRequest(
            name = SubCatalogConstants.CONFIRM_REENTER_SOURCE_FILE_DOWNLOAD,
            description = "Checks if user wants to re-enter File name",
            type = CatalogRequest.Type.QUERY_SYSTEM
    )
    public ExtensionResponse confirmReEnterTargetFileDownload(@Field.Desc(
            name = "inputMap",
            type = "{ Reenter: Boolean, stateId: Text,source: Text}"
    ) Map<String, Object> map) {

        return getExtensionResponse(map, SubCatalogConstants.HANDLE_REENTER_SOURCE_FILE_DOWNLOAD);
    }

    @SubCatalogRequest(
            name = SubCatalogConstants.HANDLE_REENTER_SOURCE_FILE_DOWNLOAD,
            description = "Handle reenter File name",
            type = CatalogRequest.Type.CHANGE_SYSTEM
    )
    @Field.File(name = "Krista File", required = false, attributes = {@Attribute(name = "visualWidth", value = "S")})
    @Field.Text(name = "Message", required = false, attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'Provides details about the download operation result, any errors encountered, or download links for files exceeding size limits'")})
    @Field.Boolean(name = "Success", required = false, attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'Indicates whether the file download was successful. Returns true even when large files are provided as links instead of direct downloads.'")})
    public ExtensionResponse handleReEnterTargetFileDownload(
            @Field.Desc(name = "inputMap", type = "{ Reenter: Boolean, stateId: Text,Source: Text}") Map<String, Object> map) {

        String filename = (String) map.get(OneDriveFieldsConstants.SOURCE);

        if (filename == null || filename.isEmpty()) {
            LOGGER.info(" File name is empty: {}", filename);
        }
        return downloadSourceFile(map, false);
    }

    @SubCatalogRequest(
            name = SubCatalogConstants.CONFIRM_REENTER_SOURCE_LINK_DOWNLOAD,
            description = "Checks if user wants to re-enter File name",
            type = CatalogRequest.Type.QUERY_SYSTEM
    )
    public ExtensionResponse confirmReEnterTargetLinkDownload(@Field.Desc(
            name = "inputMap",
            type = "{ Reenter: Boolean, stateId: Text,source: Text}"
    ) Map<String, Object> map) {

        return getExtensionResponse(map, SubCatalogConstants.HANDLE_REENTER_SOURCE_LINK_DOWNLOAD);
    }

    @SubCatalogRequest(
            name = SubCatalogConstants.HANDLE_REENTER_SOURCE_LINK_DOWNLOAD,
            description = "Handle reenter File name",
            type = CatalogRequest.Type.CHANGE_SYSTEM
    )

    @Field.Boolean(name = "Success", required = false, attributes = {@Attribute(name = "visualWidth", value = "S")})
    @Field.Text(name = "Message", required = false, attributes = {@Attribute(name = "visualWidth", value = "S")})
    @Field(name = "Link", type = "RichText", required = false, attributes = {@Attribute(name = "visualWidth", value = "L")})
    public ExtensionResponse handleReEnterTargetLinkDownload(
            @Field.Desc(name = "inputMap", type = "{ Reenter: Boolean, stateId: Text,Source: Text}") Map<String, Object> map) {

        String filename = (String) map.get(OneDriveFieldsConstants.SOURCE);

        if (filename == null || filename.isEmpty()) {
            LOGGER.info(" File name is empty: {}", filename);
        }
        return downloadSourceFile(map, true);
    }

    private ExtensionResponse downloadSourceFile(Map<String, Object> map, Boolean downloadAsLink) {
        String source = (String) map.get(OneDriveFieldsConstants.SOURCE);
        try {
            return operationsManagementDao.downloadFiles(downloadAsLink, !downloadAsLink, source, true);
        } catch (GraphServiceException | IllegalArgumentException cause) {
            throw new IllegalArgumentException("Enter Source file correctly", cause);
        }
    }

    @SubCatalogRequest(
            name = SubCatalogConstants.CONFIRM_REENTER_SOURCE_FILE_MOVE,
            description = "Checks if user wants to re-enter Source",
            type = CatalogRequest.Type.QUERY_SYSTEM
    )
    public ExtensionResponse confirmReEnterSourceFileMove(@Field.Desc(
            name = "inputMap",
            type = "{ Reenter: Boolean, stateId: Text,isTarget: Boolean, Source:Text,Target: Text}"
    ) Map<String, Object> map) {

        return getExtensionResponse(map, SubCatalogConstants.HANDLE_REENTER_SOURCE_FILE_MOVE);
    }

    @SubCatalogRequest(
            name = SubCatalogConstants.HANDLE_REENTER_SOURCE_FILE_MOVE,
            description = "Handle reenter Source",
            type = CatalogRequest.Type.CHANGE_SYSTEM
    )

    @Field.Desc(name = "File", type = "Entity(File)", required = false)
    @Field.Boolean(name = "Success", required = false, attributes = {@Attribute(name = "visualWidth", value = "S")})
    @Field.Text(name = "Message", required = false, attributes = {@Attribute(name = "visualWidth", value = "S")})
    public ExtensionResponse handleReEnterSourceFileMove(
            @Field.Desc(name = "inputMap", type = "{ Reenter: Boolean, stateId: Text,isTarget: Boolean, Source:Text,Target: Text}") Map<String, Object> map) {

        String sourcePath = (String) map.get(OneDriveFieldsConstants.SOURCE);

        if (sourcePath == null || sourcePath.isEmpty()) {
            LOGGER.info(" sourcePath is empty: {}", sourcePath);
        }
        return moveFile(map);
    }


    private ExtensionResponse moveFile(Map<String, Object> map) {
        String source = (String) map.get(OneDriveFieldsConstants.SOURCE);
        String target = (String) map.get(OneDriveFieldsConstants.TARGET);
        Boolean isTarget = (Boolean) map.get(OneDriveFieldsConstants.IS_TARGET);
        try {
            if (isTarget)
                return copyFileDao.moveFile(source, target, true, false);
            else
                return copyFileDao.moveFile(source, target, false, true);

        } catch (GraphServiceException | IllegalArgumentException cause) {
            throw new IllegalArgumentException("Enter Source file correctly", cause);
        }
    }

    @SubCatalogRequest(
            name = SubCatalogConstants.CONFIRM_REENTER_SOURCE_FILE_READ,
            description = "Checks if user wants to re-enter Source",
            type = CatalogRequest.Type.QUERY_SYSTEM
    )
    public ExtensionResponse confirmReEnterTargetFileRead(@Field.Desc(
            name = "inputMap",
            type = "{ Reenter: Boolean, stateId: Text,source: Text}"
    ) Map<String, Object> map) {

        return getExtensionResponse(map, SubCatalogConstants.HANDLE_REENTER_SOURCE_FILE_READ);
    }

    @SubCatalogRequest(
            name = SubCatalogConstants.HANDLE_REENTER_SOURCE_FILE_READ,
            description = "Handle reenter Source",
            type = CatalogRequest.Type.CHANGE_SYSTEM
    )
    @Field(name = "Content", type = "Paragraph", required = false, attributes = {@Attribute(name = "visualWidth", value = "L")})
    @Field.Boolean(name = "Success", required = false, attributes = {@Attribute(name = "visualWidth", value = "S")})
    @Field.Text(name = "Message", required = false, attributes = {@Attribute(name = "visualWidth", value = "S")})
    public ExtensionResponse handleReEnterTargetFileRead(
            @Field.Desc(name = "inputMap", type = "{ Reenter: Boolean, stateId: Text,Source: Text}") Map<String, Object> map) {

        String filename = (String) map.get(OneDriveFieldsConstants.SOURCE);

        if (filename == null || filename.isEmpty()) {
            LOGGER.info(" File name is empty: {}", filename);
        }
        return readTextFile(map);
    }

    private ExtensionResponse readTextFile(Map<String, Object> map) {
        String source = (String) map.get(OneDriveFieldsConstants.SOURCE);
        try {
            return operationsManagementDao.readTxtFile(source, true);
        } catch (GraphServiceException | IllegalArgumentException cause) {
            throw new IllegalArgumentException("Enter Source file correctly", cause);
        }
    }

    @SubCatalogRequest(
            name = SubCatalogConstants.CONFIRM_REENTER_SOURCE_FILE_RENAME,
            description = "Checks if user wants to re-enter File name",
            type = CatalogRequest.Type.QUERY_SYSTEM
    )
    public ExtensionResponse confirmReEnterTargetFileRename(@Field.Desc(
            name = "inputMap",
            type = "{ Reenter: Boolean, stateId: Text,Source Path: Text,Target File Name:Text,isSource:Boolean}"
    ) Map<String, Object> map) {

        return getExtensionResponse(map, SubCatalogConstants.HANDLE_REENTER_SOURCE_FILE_RENAME);
    }

    @SubCatalogRequest(
            name = SubCatalogConstants.HANDLE_REENTER_SOURCE_FILE_RENAME,
            description = "Handle reenter File name",
            type = CatalogRequest.Type.CHANGE_SYSTEM
    )

    @Field.Boolean(name = "Success", required = false, attributes = {@Attribute(name = "visualWidth", value = "S")})
    @Field.Text(name = "Message", required = false, attributes = {@Attribute(name = "visualWidth", value = "S")})
    public ExtensionResponse handleReEnterTargetFileRename(
            @Field.Desc(name = "inputMap", type = "{ Reenter: Boolean, stateId: Text,Source Path: Text,Target File Name:Text,isSource:Boolean}") Map<String, Object> map) {

        String filename = (String) map.get(OneDriveFieldsConstants.TARGET_FILE_NAME);

        if (filename == null || filename.isEmpty()) {
            LOGGER.info(" File name is empty: {}", filename);
        }
        return renameFile(map);
    }

    private ExtensionResponse renameFile(Map<String, Object> map) {
        String source = (String) map.get(OneDriveFieldsConstants.SOURCE_PATH);
        String targetFileName = (String) map.get(OneDriveFieldsConstants.TARGET_FILE_NAME);
        Boolean isSource = (Boolean) map.get(OneDriveFieldsConstants.IS_SOURCE);
        try {
            if (isSource)
                return operationsManagementDao.renameFile(source, targetFileName, true, false);
            else
                return operationsManagementDao.renameFile(source, targetFileName, false, true);
        } catch (GraphServiceException | IllegalArgumentException cause) {
            throw new IllegalArgumentException("Enter Source file correctly", cause);
        }
    }

    @SubCatalogRequest(
            name = SubCatalogConstants.CONFIRM_REENTER_SOURCE_FILE_SHARE,
            description = "Checks if user wants to re-enter File name",
            type = CatalogRequest.Type.QUERY_SYSTEM
    )
    public ExtensionResponse confirmReEnterTargetFileShare(@Field.Desc(
            name = "inputMap",
            type = "{ Reenter: Boolean, stateId: Text,source: Text,Share:[ { User: Email, Permissions: PickOne(Read|Write|Read and Write|Own) } ]}"
    ) Map<String, Object> map) {

        return getExtensionResponse(map, SubCatalogConstants.HANDLE_REENTER_SOURCE_FILE_SHARE);
    }

    @SubCatalogRequest(
            name = SubCatalogConstants.HANDLE_REENTER_SOURCE_FILE_SHARE,
            description = "Handle reenter File name",
            type = CatalogRequest.Type.CHANGE_SYSTEM
    )
    @Field.Desc(name = "File", type = "Entity(File)", required = false)
    @Field.Text(name = "Message", required = false, attributes = {@Attribute(name = "visualWidth", value = "S")})
    @Field.Boolean(name = "Success", required = false, attributes = {@Attribute(name = "visualWidth", value = "S")})
    public ExtensionResponse handleReEnterTargetFileShare(
            @Field.Desc(name = "inputMap", type = "{ Reenter: Boolean, stateId: Text,Source: Text,Share:[ { User: Email, Permissions: PickOne(Read|Write|Read and Write|Own) } ]}") Map<String, Object> map) {

        String filename = (String) map.get(OneDriveFieldsConstants.SOURCE);

        if (filename == null || filename.isEmpty()) {
            LOGGER.info(" File name is empty: {}", filename);
        }
        return shareFile(map);
    }

    private ExtensionResponse shareFile(Map<String, Object> map) {
        String source = (String) map.get(OneDriveFieldsConstants.SOURCE);
        List<Map<String, Object>> share = (List<Map<String, Object>>) map.get(OneDriveFieldsConstants.SHARE);

        try {
            return sharedFileDao.shareFile(source, share, true);
        } catch (GraphServiceException | IllegalArgumentException cause) {
            throw new IllegalArgumentException("Enter Source file correctly", cause);
        }
    }

    @SubCatalogRequest(
            name = SubCatalogConstants.CONFIRM_REENTER_TARGET_FILE_WRITE,
            description = "Checks if user wants to re-enter File name",
            type = CatalogRequest.Type.CHANGE_SYSTEM
    )
    public ExtensionResponse confirmReEnterTargetFileWrite(@Field.Desc(
            name = "inputMap",
            type = "{ Reenter: Boolean, stateId: Text,Target Path:Text, Custom Name: Text, Krista File: File, isTarget:boolean}"
    ) Map<String, Object> map) {

        return getExtensionResponse(map, SubCatalogConstants.HANDLE_REENTER_TARGET_FILE_WRITE);
    }

    @SubCatalogRequest(
            name = SubCatalogConstants.HANDLE_REENTER_TARGET_FILE_WRITE,
            description = "Handle reenter File name",
            type = CatalogRequest.Type.CHANGE_SYSTEM
    )
    @Field.Desc(name = "File", type = "Entity(File)", required = false)
    @Field.Boolean(name = "Success", required = false, attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'Indicates whether the file upload was successful'")})
    @Field.Text(name = "Message", required = false, attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'Provides details about the upload operation result or any errors encountered'")})
    public ExtensionResponse handleReEnterTargetFileWrite(
            @Field.Desc(name = "inputMap", type = "{ Reenter: Boolean, stateId: Text,Target Path:Text,Custom Name: Text, Krista File: File, isTarget:boolean}") Map<String, Object> map) {
        String customName = (String) map.get(OneDriveFieldsConstants.CUSTOM_NAME);
        String filename = (String) map.get(OneDriveFieldsConstants.TARGET_PATH);

        if (filename == null || filename.isEmpty()) {
            LOGGER.info(" File name is empty: {}", filename);
        }
        return write(map);
    }

    private ExtensionResponse write(Map<String, Object> map) {
        String target = (String) map.get(OneDriveFieldsConstants.TARGET_PATH);
        String customName = (String) map.get(OneDriveFieldsConstants.CUSTOM_NAME);
        Boolean isTarget = (Boolean) map.get(OneDriveFieldsConstants.IS_TARGET);
        List<app.krista.model.base.File> kristaFile = (List<app.krista.model.base.File>) map.get(OneDriveFieldsConstants.KRISTA_FILE);

        try {
            if (isTarget) {
                return operationsManagementDao.uploadFiles(kristaFile.getFirst(), target, customName, true, false);
            } else {
                return operationsManagementDao.uploadFiles(kristaFile.getFirst(), target, customName, false, true);
            }
        } catch (GraphServiceException | IllegalArgumentException cause) {
            throw new IllegalArgumentException("Enter Source file correctly", cause);
        }
    }

    @SubCatalogRequest(
            name = SubCatalogConstants.CONFIRM_REENTER_TARGET_FILE_UPLOAD,
            description = "Checks if user wants to re-enter File name",
            type = CatalogRequest.Type.QUERY_SYSTEM
    )
    public ExtensionResponse confirmReEnterTargetFileUpload(@Field.Desc(
            name = "inputMap",
            type = "{ Reenter: Boolean, stateId: Text,File Content: Text,Target Path:Text}"
    ) Map<String, Object> map) {

        return getExtensionResponse(map, SubCatalogConstants.HANDLE_REENTER_TARGET_FILE_UPLOAD);
    }

    @SubCatalogRequest(
            name = SubCatalogConstants.HANDLE_REENTER_TARGET_FILE_UPLOAD,
            description = "Handle reenter File name",
            type = CatalogRequest.Type.CHANGE_SYSTEM
    )
    @Field.Desc(name = "File", type = "Entity(File)", required = false)
    @Field.Boolean(name = "Success", required = false, attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'Indicates whether the text file was successfully created'")
    })
    @Field.Text(name = "Message", required = false, attributes = {
            @Attribute(name = "visualWidth", value = "S"),
            @Attribute(name = "toolTip", value = "'Provides details about the operation result or any errors encountered'")
    })
    public ExtensionResponse handleReEnterTargetFileUpload(
            @Field.Desc(name = "inputMap", type = "{ Reenter: Boolean, stateId: Text,File Content: Text,Target Path:Text}") Map<String, Object> map) {

        String filename = (String) map.get(OneDriveFieldsConstants.TARGET_PATH);

        if (filename == null || filename.isEmpty()) {
            LOGGER.info(" File name is empty: {}", filename);
        }
        return uploadTxt(map);
    }

    private ExtensionResponse uploadTxt(Map<String, Object> map) {
        String target = (String) map.get(OneDriveFieldsConstants.TARGET_PATH);
        String fileContent = (String) map.get(OneDriveFieldsConstants.FILE_CONTENT);

        try {
            return operationsManagementDao.uploadTxtFile(fileContent, target, true);
        } catch (GraphServiceException | IllegalArgumentException cause) {
            throw new IllegalArgumentException("Enter Source file correctly", cause);
        }
    }

    @SubCatalogRequest(
            name = SubCatalogConstants.CONFIRM_REENTER_SOURCE_FILE_HISTORY,
            description = "Checks if user wants to re-enter File name",
            type = CatalogRequest.Type.QUERY_SYSTEM
    )
    public ExtensionResponse confirmReEnterSourceFileHistory(@Field.Desc(
            name = "inputMap",
            type = "{ Reenter: Boolean, stateId: Text,Source Path: Text,Search:Text,Since:Date}"
    ) Map<String, Object> map) {

        return getExtensionResponse(map, SubCatalogConstants.HANDLE_REENTER_SOURCE_FILE_HISTORY);
    }

    @SubCatalogRequest(
            name = SubCatalogConstants.HANDLE_REENTER_SOURCE_FILE_HISTORY,
            description = "Handle reenter File name",
            type = CatalogRequest.Type.CHANGE_SYSTEM
    )

    @Field.Desc(name = "Result", type = "[ { Path: Text, Date: Date, Update: PickOne(Created|Updated|Deleted) } ]", required = false)
    @Field.Boolean(name = "Success", required = false, attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'Indicates whether the change history was successfully retrieved'")})
    @Field.Text(name = "Message", required = false, attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'Provides details about the operation result or any errors encountered'")})
    public ExtensionResponse handleReEnterSourceFileHistory(
            @Field.Desc(name = "inputMap", type = "{ Reenter: Boolean, stateId: Text,Source Path: Text,Search:Text,Since:Date}") Map<String, Object> map) {

        String filename = (String) map.get(OneDriveFieldsConstants.SOURCE_PATH);

        if (filename == null || filename.isEmpty()) {
            LOGGER.info(" File name is empty: {}", filename);
        }
        return versionHistory(map);
    }

    private ExtensionResponse versionHistory(Map<String, Object> map) {
        String source = (String) map.get(OneDriveFieldsConstants.SOURCE_PATH);
        String search = (String) map.get(OneDriveFieldsConstants.SEARCH);
        Long since = (Long) map.get(OneDriveFieldsConstants.SINCE);
        try {
            return fileVersionOperations.getVersionHistory(source, search, since, true);
        } catch (GraphServiceException | IllegalArgumentException cause) {
            throw new IllegalArgumentException("Enter Source file correctly", cause);
        }
    }

    @SubCatalogRequest(
            name = SubCatalogConstants.CONFIRM_REENTER_CREATE_FOLDER,
            description = "Checks if user wants to re-enter File name",
            type = CatalogRequest.Type.CHANGE_SYSTEM
    )
    public ExtensionResponse confirmReEnterCreateFolder(@Field.Desc(
            name = "inputMap",
            type = "{ Reenter: Boolean, stateId: Text,Target Path: Text,Folder Name: Text,isFolderName: Boolean}"
    ) Map<String, Object> map) {

        return getExtensionResponse(map, SubCatalogConstants.HANDLE_REENTER_CREATE_FOLDER);
    }

    @SubCatalogRequest(
            name = SubCatalogConstants.HANDLE_REENTER_CREATE_FOLDER,
            description = "Handle reenter File name",
            type = CatalogRequest.Type.CHANGE_SYSTEM
    )
    @Field.Text(name = "Message", required = false, attributes = {@Attribute(name = "visualWidth", value = "S")})
    @Field.Boolean(name = "Success", required = false, attributes = {@Attribute(name = "visualWidth", value = "S")})
    @Field.Desc(name = "File", type = "Entity(File)", required = false)
    public ExtensionResponse handleReEnterCreateFolder(
            @Field.Desc(name = "inputMap", type = "{ Reenter: Boolean, stateId: Text,Target Path: Text,Folder Name: Text,isFolderName: Boolean}") Map<String, Object> map) { //Sheet Name: Text, Format Type: Text, krista File: File

        String targetPath = (String) map.get(OneDriveFieldsConstants.TARGET_PATH);

        if (targetPath == null || targetPath.isEmpty()) {
            LOGGER.info(" File name is empty: {}", targetPath);
        }
        return createFolder(map);
    }

    private ExtensionResponse createFolder(Map<String, Object> map) {
        String folderName = (String) map.get(OneDriveFieldsConstants.FOLDER_NAME);
        String targetPath = (String) map.get(OneDriveFieldsConstants.TARGET_PATH);
        Boolean isFolderName = (Boolean) map.get(OneDriveFieldsConstants.IS_FOLDER_NAME);
        try {
            if (isFolderName) {
                return fileManagementDao.createFolder(folderName, targetPath, true, false);
            } else
                return fileManagementDao.createFolder(folderName, targetPath, false, true);

        } catch (GraphServiceException | IllegalArgumentException cause) {
            throw new IllegalArgumentException("Enter target file correctly", cause);
        }
    }

    @SubCatalogRequest(
            name = SubCatalogConstants.CONFIRM_REENTER_TARGET_FILE_WRITE_Custom_Name,
            description = "Checks if user wants to re-enter File name",
            type = CatalogRequest.Type.CHANGE_SYSTEM
    )
    public ExtensionResponse confirmReEnterTargetFileWrite_CustomName(@Field.Desc(
            name = "inputMap",
            type = "{ Reenter: Boolean, stateId: Text,Target Path:Text, Custom Name: Text, Krista File: File}"
    ) Map<String, Object> map) {

        return getExtensionResponse(map, SubCatalogConstants.HANDLE_REENTER_TARGET_FILE_WRITE_Custom_Name);
    }

    @SubCatalogRequest(
            name = SubCatalogConstants.HANDLE_REENTER_TARGET_FILE_WRITE_Custom_Name,
            description = "Handle reenter File name",
            type = CatalogRequest.Type.CHANGE_SYSTEM
    )

    @Field.Desc(name = "File", type = "Entity(File)", required = false)
    @Field.Boolean(name = "Success", required = false, attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'Indicates whether the file upload was successful'")})
    @Field.Text(name = "Message", required = false, attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'Provides details about the upload operation result or any errors encountered'")})
    public ExtensionResponse handleReEnterTargetFileWriteCustomName(
            @Field.Desc(name = "inputMap", type = "{ Reenter: Boolean, stateId: Text,Target Path:Text, Custom Name: Text,Krista File: File}") Map<String, Object> map) {

        String filename = (String) map.get(OneDriveFieldsConstants.TARGET_PATH);

        if (filename == null || filename.isEmpty()) {
            LOGGER.info(" File name is empty: {}", filename);
        }
        return write(map);
    }

}
