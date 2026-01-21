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

import app.krista.extensions.essentials.cloudfiles.onedrive.OnedriveAttributes;
import app.krista.extensions.essentials.cloudfiles.onedrive.catalog.CatalogTypes;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.connectors.GraphServiceClientProvider;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.connectors.GraphServiceClientProviderFactory;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.util.Constants;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.util.DriveUtil;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.util.FileUtil;
import app.krista.extensions.essentials.cloudfiles.onedrive.service.File;
import com.microsoft.graph.http.GraphServiceException;
import com.microsoft.graph.models.DriveItem;
import com.microsoft.graph.requests.DriveItemCollectionPage;
import com.microsoft.graph.requests.DriveItemDeltaCollectionPage;
import com.microsoft.graph.requests.PermissionCollectionPage;
import com.microsoft.graph.requests.UserRequestBuilder;
import org.jvnet.hk2.annotations.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Implementation class for searching and retrieving file entities from OneDrive.
 * Provides methods to search files based on various criteria such as date, size, name,
 * creator, modifier, owner, and path.
 */
@Service
public class FileEntitySearchImpl {

    private static final Logger LOGGER = LoggerFactory.getLogger(FileEntitySearchImpl.class);
    private final GraphServiceClientProvider provider;
    private final OnedriveAttributes onedriveAttributes;

    /**
     * Constructs a FileEntitySearchImpl with factory-created provider.
     *
     * @param factory            Factory to create the GraphServiceClientProvider
     * @param onedriveAttributes Attributes for OneDrive configuration
     */
    @Inject
    public FileEntitySearchImpl(GraphServiceClientProviderFactory factory, OnedriveAttributes onedriveAttributes) {
        this(factory.create(), onedriveAttributes);
    }

    /**
     * Constructs a FileEntitySearchImpl with the specified provider and attributes.
     *
     * @param provider           The GraphServiceClientProvider for Microsoft Graph API calls
     * @param onedriveAttributes Attributes for OneDrive configuration
     */
    public FileEntitySearchImpl(GraphServiceClientProvider provider, OnedriveAttributes onedriveAttributes) {
        this.provider = provider;
        this.onedriveAttributes = onedriveAttributes;
    }

    /**
     * Retrieves files based on their creation or modification date.
     * Filters files using the specified date, operator, and date type.
     *
     * @param longDate The date to compare against (in milliseconds since epoch)
     * @param operator The comparison operator (e.g., "gt", "lt", "eq")
     * @param dateType If true, compares against modification date; if false, compares against creation date
     * @return List of files matching the date criteria
     */
    public List<app.krista.extensions.essentials.cloudfiles.onedrive.catalog.entities.File> getFilesByDate(long longDate, String operator, boolean dateType) {
        try {
            List<File> files = new ArrayList<>();
            DriveItemDeltaCollectionPage page = getUserRequestBuilder().drive().root().delta().buildRequest().get();

            while (page != null && !page.getCurrentPage().isEmpty()) {
                for (DriveItem driveInPage : page.getCurrentPage()) {
                    if (driveInPage.id != null && dateType
                            && driveInPage.lastModifiedBy != null) {
                        fetchFilesByOperator(driveInPage, longDate, true, operator, files);
                    }
                    if (driveInPage.id != null && !dateType
                            && driveInPage.createdDateTime != null) {
                        fetchFilesByOperator(driveInPage, longDate, false, operator, files);
                    }
                }
                if (page.getNextPage() != null) {
                    page = page.getNextPage().buildRequest().get();
                } else {
                    break;
                }
            }
            return files.stream()
                    .map(CatalogTypes::fromFile)
                    .collect(Collectors.toList());
        } catch (GraphServiceException | IllegalArgumentException cause) {
            LOGGER.info(cause.getMessage(), cause);
        }
        return List.of();
    }

    /**
     * Retrieves files based on their size.
     * Filters files using the specified size and operator.
     *
     * @param size     The size to compare against (in bytes)
     * @param operator The comparison operator (e.g., "gt", "lt", "eq")
     * @return List of files matching the size criteria
     */
    public List<app.krista.extensions.essentials.cloudfiles.onedrive.catalog.entities.File> getFilesBySize(double size, String operator) {
        try {
            List<File> files = new ArrayList<>();
            DriveItemDeltaCollectionPage page = getUserRequestBuilder().drive().root().delta().buildRequest().get();
            while (page != null && !page.getCurrentPage().isEmpty()) {
                for (DriveItem driveInPage : page.getCurrentPage()) {
                    if (driveInPage.id != null && driveInPage.size != null) {
                        fetchFilesByOperator(driveInPage, size, operator, files);
                    }
                }
                if (page.getNextPage() != null) {
                    page = page.getNextPage().buildRequest().get();
                } else {
                    break;
                }
            }
            return files.stream()
                    .map(CatalogTypes::fromFile)
                    .collect(Collectors.toList());
        } catch (GraphServiceException | IllegalArgumentException cause) {
            LOGGER.info(cause.getMessage(), cause);
        }
        return List.of();
    }

    /**
     * Helper method to fetch files by date operator.
     * Delegates to the more general fetchFilesByOperator method.
     *
     * @param driveInPage The DriveItem to evaluate
     * @param date        The date to compare against
     * @param dateType    If true, compares against modification date; if false, compares against creation date
     * @param operator    The comparison operator
     * @param files       The list to add matching files to
     */
    private void fetchFilesByOperator(DriveItem driveInPage, Long date, boolean dateType, String operator, List<File> files) {
        fetchFilesByOperator(driveInPage, null, date, dateType, operator, files);
    }

    /**
     * Helper method to fetch files by size operator.
     * Delegates to the more general fetchFilesByOperator method.
     *
     * @param driveInPage The DriveItem to evaluate
     * @param size        The size to compare against
     * @param operator    The comparison operator
     * @param files       The list to add matching files to
     */
    private void fetchFilesByOperator(DriveItem driveInPage, Double size, String operator, List<File> files) {
        fetchFilesByOperator(driveInPage, size, null, false, operator, files);
    }

    /**
     * General method to fetch files based on either size or date criteria.
     * Evaluates the DriveItem against the specified criteria and adds matching files to the list.
     *
     * @param driveInPage The DriveItem to evaluate
     * @param size        The size to compare against (may be null if using date comparison)
     * @param date        The date to compare against (may be null if using size comparison)
     * @param dateType    If true, compares against modification date; if false, compares against creation date
     * @param operator    The comparison operator
     * @param files       The list to add matching files to
     */
    private void fetchFilesByOperator(DriveItem driveInPage, Double size, Long date, boolean dateType, String operator, List<File> files) {
        boolean shouldAddFile = false;

        switch (operator) {
            case Constants.GTE:
                shouldAddFile = date == null
                        ? driveInPage.size >= size
                        : dateType
                        ? getFileDate(driveInPage.lastModifiedDateTime) >= date
                        : getFileDate(driveInPage.createdDateTime) >= date;
                break;
            case Constants.LTE:
                shouldAddFile = date == null
                        ? driveInPage.size <= size
                        : dateType
                        ? getFileDate(driveInPage.lastModifiedDateTime) <= date
                        : getFileDate(driveInPage.createdDateTime) <= date;
                break;
            case Constants.GT:
                shouldAddFile = date == null
                        ? driveInPage.size > size
                        : dateType
                        ? getFileDate(driveInPage.lastModifiedDateTime) > date
                        : getFileDate(driveInPage.createdDateTime) > date;
                break;
            case Constants.LT:
                shouldAddFile = date == null
                        ? driveInPage.size < size
                        : dateType
                        ? getFileDate(driveInPage.lastModifiedDateTime) < date
                        : getFileDate(driveInPage.createdDateTime) < date;
                break;
            case Constants.EQUALS:
                shouldAddFile = date == null
                        ? driveInPage.size.equals(size.longValue())
                        : dateType
                        ? FileUtil.isEqualDate(getFileDate(driveInPage.lastModifiedDateTime), date)
                        : FileUtil.isEqualDate(getFileDate(driveInPage.createdDateTime), date);
                break;
            default:
                break;
        }
        if (shouldAddFile) {
            files.add(new FileImpl(driveInPage, null, null));
        }
    }

    /**
     * Retrieves a file by its primary key (ID or path).
     * If the primary key is a path, it is converted to a relative path before retrieval.
     *
     * @param primaryKey The ID or path of the file to retrieve
     * @return The file if found, null otherwise
     */
    public File getFileByKey(String primaryKey) {
        try {
            String relativePath = FileUtil.getRelativeFilePath(primaryKey);
            if (relativePath != null) {
                DriveItem driveItem = getUserRequestBuilder().drive().root().itemWithPath(relativePath).buildRequest().get();
                if (driveItem != null && driveItem.id != null) {
                    PermissionCollectionPage permissionPage = getUserRequestBuilder()
                            .drive().items(driveItem.id)
                            .permissions().buildRequest().get();

                    return new FileImpl(driveItem, permissionPage, onedriveAttributes);
                }
            }
            return null;
        } catch (GraphServiceException | IllegalArgumentException cause) {
            LOGGER.info(cause.getMessage(), cause);
        }
        return null;
    }

    /**
     * Converts an OffsetDateTime to a Unix timestamp (milliseconds since epoch).
     *
     * @param dateTime The OffsetDateTime to convert
     * @return The Unix timestamp, or 0 if dateTime is null
     */
    private long getFileDate(OffsetDateTime dateTime) {
        return dateTime != null ? Timestamp.valueOf(dateTime.toLocalDateTime()).getTime() : 0L;
    }

    /**
     * Retrieves files by their absolute path.
     * If the path points to a folder, retrieves all files within that folder.
     *
     * @param absolutePath The absolute path to search for
     * @return List of files at the specified path
     */
    public List<File> getFileByPath(String absolutePath) {
        try {
            List<File> files = new ArrayList<>();
            if (absolutePath != null) {
                String relativePath = absolutePath.contains(Constants.DRIVE_ROOT) ? absolutePath.replace(Constants.DRIVE_ROOT, "/") : absolutePath;
                DriveItem driveItem = getUserRequestBuilder().drive().root().itemWithPath(relativePath).buildRequest().get();
                if (driveItem != null && driveItem.id != null && driveItem.folder != null) {
                    files.add(new FileImpl(driveItem, null, null));
                    getDriveItemCollectionPageByPath(relativePath, files);
                    return files;
                }
                return List.of(new FileImpl(driveItem, null, null));
            }
            return List.of();
        } catch (GraphServiceException | IllegalArgumentException cause) {
            LOGGER.info(cause.getMessage(), cause);
        }
        return List.of();
    }

    /**
     * Retrieves files within a parent folder.
     * If the parent folder is the root, special handling is applied.
     *
     * @param parentFolder The path of the parent folder
     * @return List of files within the parent folder
     */
    public List<File> getFilesByParentFolder(String parentFolder) {
        try {
            if (parentFolder != null) {
                List<File> files = new ArrayList<>();
                String relativePath = parentFolder.contains(Constants.DRIVE_ROOT) ? parentFolder.replace(Constants.DRIVE_ROOT, "/") : parentFolder;
                boolean isRoot = isOnlyRootParentPath(parentFolder);
                DriveItem driveItem = isRoot
                        ? FileUtil.getRootUserRequestBuilder(provider).buildRequest().get()
                        : FileUtil.getRootUserRequestBuilder(provider).itemWithPath(relativePath).buildRequest().get();
                if (driveItem != null && driveItem.folder != null) {
                    getDriveItemCollectionPageByPath(isRoot ? "/" : relativePath, files);
                    return files;
                }
            }
            return List.of();
        } catch (GraphServiceException | IllegalArgumentException cause) {
            LOGGER.info(cause.getMessage(), cause);
        }
        return List.of();
    }

    /**
     * Checks if the provided path refers to the root folder.
     *
     * @param relativePath The path to check
     * @return true if the path refers to the root folder, false otherwise
     */
    private static boolean isOnlyRootParentPath(String relativePath) {
        return relativePath.equals(Constants.DRIVE_ROOT) || relativePath.equals("/" + Constants.DRIVE_ROOT) || relativePath.equals("/drive/root:");
    }

    /**
     * Retrieves all files in the user's drive.
     * Excludes the root folder itself from the results.
     *
     * @return List of all files in the drive
     */
    public List<File> getFiles() {
        try {
            List<File> files = new ArrayList<>();
            DriveItemDeltaCollectionPage page = getUserRequestBuilder().drive().root().delta().buildRequest().get();
            while (page != null && !page.getCurrentPage().isEmpty()) {
                for (DriveItem driveInPage : page.getCurrentPage()) {
                    assert driveInPage.name != null;
                    if (!driveInPage.name.equalsIgnoreCase("root")) {
                        files.add(new FileImpl(driveInPage, null, null));
                    }
                }
                if (page.getNextPage() != null) {
                    page = page.getNextPage().buildRequest().get();
                } else {
                    break;
                }
            }
            return files;
        } catch (GraphServiceException | IllegalArgumentException cause) {
            LOGGER.info(cause.getMessage(), cause);
        }
        return List.of();
    }

    /**
     * Retrieves folders in the root drive, optionally filtered by name.
     * If folderName is null, returns all folders.
     *
     * @param folderName The name of the folder to search for, or null for all folders
     * @return List of folders matching the criteria
     */
    public List<File> getFoldersInRootDrive(String folderName) {
        try {
            List<File> files = new ArrayList<>();
            DriveItemDeltaCollectionPage page = getUserRequestBuilder().drive().root().delta().buildRequest().get();
            while (page != null && !page.getCurrentPage().isEmpty()) {
                if (folderName != null) {
                    for (DriveItem driveInPage : page.getCurrentPage()) {
                        if (driveInPage.folder != null && driveInPage.name != null && driveInPage.name.equalsIgnoreCase(folderName)) {
                            files.add(new FileImpl(driveInPage, null, null));
                        }
                    }
                } else {
                    for (DriveItem driveInPage : page.getCurrentPage()) {
                        if (driveInPage.folder != null) {
                            files.add(new FileImpl(driveInPage, null, null));
                        }
                    }
                }
                if (page.getNextPage() != null) {
                    page = page.getNextPage().buildRequest().get();
                } else {
                    break;
                }
            }
            return files;
        } catch (GraphServiceException | IllegalArgumentException cause) {
            LOGGER.info(cause.getMessage(), cause);
        }
        return List.of();
    }

    /**
     * Retrieves files by their extension.
     * Delegates to getFilesByName for implementation.
     *
     * @param extensionName The extension to search for
     * @return List of files with the specified extension
     */
    public List<File> getFilesByExtension(String extensionName) {
        return getFilesByName(extensionName);
    }

    /**
     * Retrieves files by their base name.
     * Delegates to getFilesByName for implementation.
     *
     * @param baseName The base name to search for
     * @return List of files with the specified base name
     */
    public List<File> getFilesByBaseName(String baseName) {
        return getFilesByName(baseName);
    }

    /**
     * Retrieves files by their name.
     * Searches for files containing the specified name string.
     *
     * @param name The name to search for
     * @return List of files containing the specified name
     */
    public List<File> getFilesByName(String name) {
        try {
            List<File> files = new ArrayList<>();
            DriveUtil.stringValidator(name, Constants.FILE_NOT_FOUND);
            DriveItemDeltaCollectionPage page = getUserRequestBuilder().drive().root().delta().buildRequest().get();
            while (page != null && !page.getCurrentPage().isEmpty()) {
                for (DriveItem driveInPage : page.getCurrentPage()) {
                    if (driveInPage.name != null && driveInPage.name.contains(name)) {
                        files.add(new FileImpl(driveInPage, null, null));
                    }
                }
                if (page.getNextPage() != null) {
                    page = page.getNextPage().buildRequest().get();
                } else {
                    break;
                }
            }
            return files;
        } catch (GraphServiceException | IllegalArgumentException cause) {
            LOGGER.info(cause.getMessage(), cause);
        }
        return List.of();
    }

    /**
     * Helper method to retrieve children of a folder at the specified path.
     * Adds all children to the provided files list.
     *
     * @param relativePath The relative path of the folder
     * @param files        The list to add the children to
     */
    private void getDriveItemCollectionPageByPath(String relativePath, List<File> files) {
        try {
            DriveItemCollectionPage page = getUserRequestBuilder()
                    .drive().root()
                    .itemWithPath(relativePath)
                    .children().buildRequest().get();
            while (page != null && !page.getCurrentPage().isEmpty()) {
                for (DriveItem driveInPage : page.getCurrentPage()) {
                    files.add(new FileImpl(driveInPage, null, null));
                }
                if (page.getNextPage() != null) {
                    page = page.getNextPage().buildRequest().get();
                } else {
                    break;
                }
            }
        } catch (GraphServiceException | IllegalArgumentException cause) {
            LOGGER.info(cause.getMessage(), cause);
        }
    }

    /**
     * Gets a UserRequestBuilder for making API requests.
     * Uses the default user (no specific email or account ID).
     *
     * @return UserRequestBuilder for API requests
     */
    private UserRequestBuilder getUserRequestBuilder() {
        return provider.getUserRequestBuilder(null, null);
    }

    /**
     * Retrieves files created by a specific user.
     *
     * @param createdBy The email of the user who created the files
     * @return List of files created by the specified user
     */
    public List<File> getFilesCreatedBy(String createdBy) {
        return fetchFilesByUserMailID(createdBy, null);
    }

    /**
     * Retrieves files modified by a specific user.
     *
     * @param modifiedBy The email of the user who modified the files
     * @return List of files modified by the specified user
     */
    public List<File> getFilesModifiedBy(String modifiedBy) {
        return fetchFilesByUserMailID(null, modifiedBy);
    }

    /**
     * Helper method to fetch files by creator or modifier email.
     * If both parameters are provided, createdBy takes precedence.
     *
     * @param createdBy  The email of the creator to search for, or null
     * @param modifiedBy The email of the modifier to search for, or null
     * @return List of files matching the criteria
     */
    private List<File> fetchFilesByUserMailID(String createdBy, String modifiedBy) {
        try {
            List<File> files = new ArrayList<>();
            DriveItemDeltaCollectionPage page = getUserRequestBuilder().drive().root().delta().buildRequest().get();
            while (page != null && !page.getCurrentPage().isEmpty()) {
                for (DriveItem driveInPage : page.getCurrentPage()) {
                    if (createdBy != null && isDriveInPageHasCreatedByUser(driveInPage, createdBy)) {
                        files.add(new FileImpl(driveInPage, null, null));
                    } else if (modifiedBy != null && isDriveInPageHasModifiedByUser(driveInPage, modifiedBy)) {
                        files.add(new FileImpl(driveInPage, null, null));
                    }
                }
                if (page.getNextPage() != null) {
                    page = page.getNextPage().buildRequest().get();
                } else {
                    break;
                }
            }
            return files;
        } catch (GraphServiceException | IllegalArgumentException cause) {
            LOGGER.info(cause.getMessage(), cause);
        }
        return List.of();
    }

    /**
     * Retrieves files owned by a specific user.
     *
     * @param owner The display name of the owner to search for
     * @return List of files owned by the specified user
     */
    public List<File> getFilesByOwner(String owner) {
        try {
            List<File> files = new ArrayList<>();
            DriveUtil.stringValidator(owner, Constants.FILE_NOT_FOUND);
            DriveItemDeltaCollectionPage page = getUserRequestBuilder().drive().root().delta().buildRequest().get();
            while (page != null && !page.getCurrentPage().isEmpty()) {
                for (DriveItem driveInPage : page.getCurrentPage()) {
                    if (isDriveItemHasOwner(owner, driveInPage)) {
                        files.add(new FileImpl(driveInPage, null, null));
                    }
                }
                if (page.getNextPage() != null) {
                    page = page.getNextPage().buildRequest().get();
                } else {
                    break;
                }
            }
            return files;
        } catch (GraphServiceException | IllegalArgumentException cause) {
            LOGGER.info(cause.getMessage(), cause);
        }
        return List.of();
    }

    /**
     * Checks if a DriveItem has the specified owner.
     *
     * @param owner       The display name of the owner to check for
     * @param driveInPage The DriveItem to check
     * @return true if the DriveItem has the specified owner, false otherwise
     */
    private boolean isDriveItemHasOwner(String owner, DriveItem driveInPage) {
        return driveInPage.createdBy != null && driveInPage.createdBy.user != null
                && driveInPage.createdBy.user.displayName != null
                && driveInPage.createdBy.user.displayName.equalsIgnoreCase(owner);
    }

    /**
     * Checks if a DriveItem was modified by the specified user.
     *
     * @param driveInPage The DriveItem to check
     * @param modifiedBy  The email of the modifier to check for
     * @return true if the DriveItem was modified by the specified user, false otherwise
     */
    private boolean isDriveInPageHasModifiedByUser(DriveItem driveInPage, String modifiedBy) {
        return driveInPage.lastModifiedBy != null && driveInPage.lastModifiedBy.user != null
                && driveInPage.lastModifiedBy.user.additionalDataManager().get(Constants.EMAIL) != null
                && driveInPage.lastModifiedBy.user.additionalDataManager().get(Constants.EMAIL).toString().contains(modifiedBy);
    }

    /**
     * Checks if a DriveItem was created by the specified user.
     *
     * @param driveInPage The DriveItem to check
     * @param createdBy   The email of the creator to check for
     * @return true if the DriveItem was created by the specified user, false otherwise
     */
    private boolean isDriveInPageHasCreatedByUser(DriveItem driveInPage, String createdBy) {
        return driveInPage.createdBy != null && driveInPage.createdBy.user != null
                && driveInPage.createdBy.user.additionalDataManager().get(Constants.EMAIL) != null
                && driveInPage.createdBy.user.additionalDataManager().get(Constants.EMAIL).toString().contains(createdBy);
    }
}