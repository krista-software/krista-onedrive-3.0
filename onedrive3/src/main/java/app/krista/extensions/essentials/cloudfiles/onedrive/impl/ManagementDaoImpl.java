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

package app.krista.extensions.essentials.cloudfiles.onedrive.impl;


import app.krista.extension.executor.ExtensionResponse;
import app.krista.model.base.FreeForm;
import com.microsoft.graph.http.GraphServiceException;
import org.jvnet.hk2.annotations.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.List;
import java.util.Map;

/**
 * Implementation of the management layer for OneDrive operations.
 * This class serves as a facade for various specialized DAOs, delegating operations to the appropriate
 * implementation while providing consistent error handling and logging.
 * It centralizes access to file, folder, and operation management functionality.
 */
@Service
public class ManagementDaoImpl {

    private static final Logger LOGGER = LoggerFactory.getLogger(ManagementDaoImpl.class);

    private final FileManagementDao fileManagementDao;
    private final FolderManagementDao folderManagementDao;
    private final OperationsManagementDao operationsManagementDao;
    private final SharedFileDao sharedFileDao;
    private final CopyFileDao copyFileDao;
    private final FileVersionOperations fileVersionOperations;

    /**
     * Constructs a ManagementDaoImpl with all required dependencies.
     *
     * @param fileManagementDao       DAO for file management operations
     * @param folderManagementDao     DAO for folder management operations
     * @param operationsManagementDao DAO for general file operations
     * @param sharedFileDao           DAO for file sharing operations
     * @param copyFileDao             DAO for file copy operations
     * @param fileVersionOperations   Service for file version operations
     */
    @Inject
    public ManagementDaoImpl(FileManagementDao fileManagementDao, FolderManagementDao folderManagementDao,
                             OperationsManagementDao operationsManagementDao, SharedFileDao sharedFileDao,
                             CopyFileDao copyFileDao, FileVersionOperations fileVersionOperations) {
        this.fileManagementDao = fileManagementDao;
        this.folderManagementDao = folderManagementDao;
        this.operationsManagementDao = operationsManagementDao;
        this.sharedFileDao = sharedFileDao;
        this.copyFileDao = copyFileDao;
        this.fileVersionOperations = fileVersionOperations;
    }


    /**
     * Retrieves a list of files from the specified path.
     *
     * @param relativePath The path to retrieve files from
     * @param search       Optional search term to filter files
     * @param useEmail     Whether to use email for authentication
     * @param accountID    The account ID for authentication
     * @return ExtensionResponse containing the list of files or an error
     * @throws IllegalArgumentException If an error occurs during the operation
     */
    public FreeForm getFileByPath(String relativePath, String search, boolean useEmail, String accountID) {
        try {
            return fileManagementDao.getListOfFiles(relativePath, search, useEmail, accountID);
        } catch (GraphServiceException | IllegalArgumentException cause) {
            throw new IllegalArgumentException(cause.getMessage(), cause);
        }
    }

    /**
     * Retrieves a list of folders from the specified path.
     *
     * @param relativePath The path to retrieve folders from
     * @param search       Optional search term to filter folders
     * @param useEmail     Whether to use email for authentication
     * @param accountID    The account ID for authentication
     * @return ExtensionResponse containing the list of folders or an error
     * @throws IllegalArgumentException If an error occurs during the operation
     */
    public FreeForm getFoldersByPath(String relativePath, String search, boolean useEmail, String accountID) {
        try {
            return folderManagementDao.getListOfFolders(relativePath, search, useEmail, accountID);
        } catch (GraphServiceException | IllegalArgumentException cause) {
            throw new IllegalArgumentException(cause.getMessage(), cause);
        }
    }

    /**
     * Downloads a file as a link (URL).
     *
     * @param source The path of the file to download
     * @return ExtensionResponse containing the download link or an error
     * @throws IllegalArgumentException If an error occurs during the operation
     */
    public ExtensionResponse downloadFileAsLink(String source) {
        try {
            return operationsManagementDao.downloadFiles(true, false, source, false);
        } catch (GraphServiceException | IllegalArgumentException cause) {
            throw new IllegalArgumentException(cause.getMessage(), cause);
        }
    }

    /**
     * Downloads a file as a file object.
     *
     * @param source The path of the file to download
     * @return ExtensionResponse containing the file or an error
     * @throws IllegalArgumentException If an error occurs during the operation
     */
    public ExtensionResponse downloadFileAsFile(String source) {
        try {
            ExtensionResponse response = operationsManagementDao.downloadFiles(false, true, source, false);
            LOGGER.info("ManagementDaoImpl.downloadFileAsFile - File download operation completed successfully");
            return response;
        } catch (GraphServiceException e) {
            LOGGER.error("ManagementDaoImpl.downloadFileAsFile - GraphServiceException occurred during download operation", e);
            throw new IllegalArgumentException("Graph service error: " + e.getMessage(), e);
        } catch (IllegalArgumentException e) {
            LOGGER.error("ManagementDaoImpl.downloadFileAsFile - IllegalArgumentException occurred during download operation", e);
            throw new IllegalArgumentException(e.getMessage(), e);
        } catch (Exception e) {
            LOGGER.error("ManagementDaoImpl.downloadFileAsFile - Unexpected exception occurred during download operation", e);
            throw new IllegalArgumentException("Unexpected error during file download: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves information about a file or folder.
     *
     * @param sourcePath The path to retrieve information for
     * @param useEmail   Whether to use email for authentication
     * @param accountID  The account ID for authentication
     * @return ExtensionResponse containing the file information or an error
     * @throws IllegalArgumentException If an error occurs during the operation
     */
    public FreeForm getInfo(String sourcePath, boolean useEmail, String accountID) {
        try {
            return fileManagementDao.getFileInfo(sourcePath, useEmail, accountID);
        } catch (GraphServiceException | IllegalArgumentException cause) {
            throw new IllegalArgumentException(cause.getMessage(), cause);
        }
    }

    /**
     * Uploads a file to the specified path.
     *
     * @param kristaFile The file to upload
     * @param targetPath The path to upload the file to
     * @param allowRetry Whether to allow retry on validation failure
     * @return ExtensionResponse containing the result of the operation
     * @throws IllegalArgumentException If an error occurs during the operation
     */
    public ExtensionResponse uploadFile(app.krista.model.base.File kristaFile, String targetPath, Boolean allowRetry) {
        try {
            return operationsManagementDao.uploadFiles(kristaFile, targetPath, allowRetry);
        } catch (GraphServiceException | IllegalArgumentException cause) {
            throw new IllegalArgumentException(cause.getMessage(), cause);
        }
    }

    /**
     * Deletes a file or folder.
     *
     * @param target The path of the file or folder to delete
     * @param allowRetry Whether to allow retry on validation failure
     * @return ExtensionResponse containing the result of the operation
     * @throws IllegalArgumentException If an error occurs during the operation
     */
    public ExtensionResponse deleteFile(String target, Boolean allowRetry) {
        LOGGER.info("ManagementDaoImpl.deleteFile - Starting file deletion for target: {}, allowRetry: {}", target, allowRetry);

        try {
            LOGGER.debug("ManagementDaoImpl.deleteFile - Delegating to operationsManagementDao");
            ExtensionResponse response = operationsManagementDao.deleteFile(target, false, allowRetry);
            LOGGER.info("ManagementDaoImpl.deleteFile - Operation completed successfully");
            return response;
        } catch (GraphServiceException gse) {
            LOGGER.error("ManagementDaoImpl.deleteFile - Graph service exception occurred", gse);
            throw new IllegalArgumentException("Graph service error: " + gse.getMessage(), gse);
        } catch (IllegalArgumentException iae) {
            LOGGER.error("ManagementDaoImpl.deleteFile - Validation error", iae);
            throw iae;
        } catch (Exception e) {
            LOGGER.error("ManagementDaoImpl.deleteFile - Unexpected error occurred", e);
            throw new IllegalArgumentException("Unexpected error: " + e.getMessage(), e);
        }
    }

    /**
     * Shares a file with specified users.
     *
     * @param source The path of the file to share
     * @param share  List of users to share the file with, including their permissions
     * @return ExtensionResponse containing the result of the operation
     * @throws IllegalArgumentException If an error occurs during the operation
     */
    public ExtensionResponse shareFile(String source, List<Map<String, Object>> share) {
        try {
            return sharedFileDao.shareFile(source, share, false);
        } catch (GraphServiceException | IllegalArgumentException cause) {
            throw new IllegalArgumentException(cause.getMessage(), cause);
        }
    }

    /**
     * Copies a file from the source path to the target path.
     *
     * @param source       The source file path
     * @param target       The target file path
     * @param replace      Whether to replace the file if it exists
     * @param createFolder Whether to create the target folder if it doesn't exist
     * @param allowRetry   Whether to allow retry on validation failure
     * @return An ExtensionResponse containing the result of the operation
     * @throws IllegalArgumentException If an error occurs during the operation
     */
    public ExtensionResponse copyFile(String source, String target, Boolean replace, Boolean createFolder, Boolean allowRetry) {
        return copyFile(source, target, replace, createFolder, allowRetry, null);
    }

    public ExtensionResponse copyFile(String source, String target, Boolean replace, Boolean createFolder, Boolean allowRetry, String targetFileName) {
        LOGGER.info("ManagementDaoImpl.copyFile - Starting file copy operation, allowRetry: {}, targetFileName: {}", allowRetry, targetFileName);
        try {
            ExtensionResponse response = copyFileDao.copyFile(source, target, replace, createFolder, false, false, allowRetry, targetFileName);
            LOGGER.info("ManagementDaoImpl.copyFile - File copy operation completed successfully");
            return response;
        } catch (GraphServiceException cause) {
            LOGGER.error("ManagementDaoImpl.copyFile - GraphServiceException occurred during copy operation", cause);
            throw new IllegalArgumentException("Graph service error: " + cause.getMessage(), cause);
        } catch (IllegalArgumentException cause) {
            LOGGER.error("ManagementDaoImpl.copyFile - IllegalArgumentException occurred during copy operation", cause);
            throw new IllegalArgumentException(cause.getMessage(), cause);
        } catch (Exception cause) {
            LOGGER.error("ManagementDaoImpl.copyFile - Unexpected exception occurred during copy operation", cause);
            throw new IllegalArgumentException("Unexpected error during file copy: " + cause.getMessage(), cause);
        }
    }

    /**
     * Moves a file from the source path to the target path.
     *
     * @param source The source file path
     * @param target The target file path
     * @param allowRetry Whether to allow retry on validation failure
     * @return ExtensionResponse containing the result of the operation
     * @throws IllegalArgumentException If an error occurs during the operation
     */
    public ExtensionResponse moveFile(String source, String target, Boolean allowRetry) {
        try {
            return copyFileDao.moveFile(source, target, false, false, allowRetry);
        } catch (GraphServiceException | IllegalArgumentException cause) {
            throw new IllegalArgumentException(cause.getMessage(), cause);
        }
    }

    /**
     * Renames a file.
     *
     * @param source         The path of the file to rename
     * @param targetFileName The new name for the file
     * @return ExtensionResponse containing the result of the operation
     * @throws IllegalArgumentException If an error occurs during the operation
     */
    public ExtensionResponse renameFile(String source, String targetFileName) {
        try {
            return operationsManagementDao.renameFile(source, targetFileName, false, false);
        } catch (GraphServiceException | IllegalArgumentException cause) {
            throw new IllegalArgumentException(cause.getMessage(), cause);
        }
    }

    /**
     * Reads a text file and returns its content.
     *
     * @param source The path of the text file to read
     * @return ExtensionResponse containing the file content or an error
     * @throws IllegalArgumentException If an error occurs during the operation
     */
    public ExtensionResponse readTxtFile(String source) {
        try {
            return operationsManagementDao.readTxtFile(source, false);
        } catch (GraphServiceException | IllegalArgumentException cause) {
            throw new IllegalArgumentException(cause.getMessage(), cause);
        }
    }

    /**
     * Uploads content as a text file.
     *
     * @param fileContent The content to upload
     * @param target      The path to upload the file to
     * @param allowRetry  Whether to allow retry on validation failure
     * @return ExtensionResponse containing the result of the operation
     * @throws IllegalArgumentException If an error occurs during the operation
     */
    public ExtensionResponse uploadFileAsTxt(String fileContent, String target, Boolean allowRetry) {
        try {
            return operationsManagementDao.uploadTxtFile(fileContent, target, false, allowRetry);
        } catch (GraphServiceException | IllegalArgumentException cause) {
            throw new IllegalArgumentException(cause.getMessage(), cause);
        }
    }

    /**
     * Retrieves changes to a file or folder since a specified time.
     *
     * @param sourcePath The path to check for changes
     * @param search     Optional search term to filter changes
     * @param since      The timestamp to check changes since (in milliseconds since epoch)
     * @return ExtensionResponse containing the changes or an error
     * @throws IllegalArgumentException If an error occurs during the operation
     */
    public ExtensionResponse getChangesSince(String sourcePath, String search, Long since) {
        try {
            return fileVersionOperations.getVersionHistory(sourcePath, search, since, false);
        } catch (GraphServiceException | IllegalArgumentException cause) {
            throw new IllegalArgumentException(cause.getMessage(), cause);
        }
    }

    /**
     * Creates a new folder.
     *
     * @param folderName The name of the folder to create
     * @param path       The path where the folder should be created
     * @return ExtensionResponse containing the result of the operation
     * @throws IllegalArgumentException If an error occurs during the operation
     */
    public ExtensionResponse createFolder(String folderName, String path) {
        // Backward-compatible overload: retry prompts enabled by default.
        return createFolder(folderName, path, true);
    }

    /**
     * Creates a new folder with control over re-entry (retry) behavior.
     *
     * @param folderName The name of the folder to create
     * @param path       The path where the folder should be created
     * @param allowRetry When {@code true}, validation failures prompt the user to
     *                   re-enter values; when {@code false}, a direct error is returned.
     * @return ExtensionResponse containing the result of the operation
     * @throws IllegalArgumentException If an error occurs during the operation
     */
    public ExtensionResponse createFolder(String folderName, String path, Boolean allowRetry) {
        try {
            return fileManagementDao.createFolder(folderName, path, false, false, allowRetry);
        } catch (GraphServiceException | IllegalArgumentException cause) {
            throw new IllegalArgumentException(cause.getMessage(), cause);
        }
    }

    /**
     * Returns a list of drives accessible to the authenticated user.
     *
     * @param search    Optional case-insensitive filter on the drive display name.
     * @param useEmail  {@code true} for Setup-tab/operator credentials; {@code false} for per-user MCP.
     * @param accountID The account identifier for authentication.
     * @return ExtensionResponse containing the drive list, Success flag, and Message.
     */
    public ExtensionResponse getDrives(String search, boolean useEmail, String accountID) {
        try {
            return fileManagementDao.getListOfDrives(search, useEmail, accountID);
        } catch (GraphServiceException | IllegalArgumentException cause) {
            throw new IllegalArgumentException(cause.getMessage(), cause);
        }
    }
}
