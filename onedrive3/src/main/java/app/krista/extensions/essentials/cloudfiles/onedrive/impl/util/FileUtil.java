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

import app.krista.extensions.essentials.cloudfiles.onedrive.impl.connectors.GraphServiceClientProvider;
import com.microsoft.graph.models.DriveItem;
import com.microsoft.graph.requests.DriveItemRequestBuilder;

import java.io.*;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

public class FileUtil {

    private static final String DD_MM_YYYY_HH_MM_A = "dd/MM/yyyy, hh:mm a";

    private FileUtil() {
    }

    /**
     * Extracts the base name and extension from a file name.
     *
     * @param fileName The file name to parse (e.g., "document.txt")
     * @return List containing [basename, extension] (e.g., ["document", "txt"]), or empty list if fileName is null
     */

    public static List<String> getBaseName(String fileName) {
        if (fileName != null) {
            List<String> result = new ArrayList<>();
            int dotIndex = fileName.lastIndexOf('.');
            if (dotIndex > 0 && dotIndex < fileName.length() - 1) {
                result.add(fileName.substring(0, dotIndex));
                result.add(fileName.substring(dotIndex + 1));
            } else {
                result.add(fileName);
                result.add("");
            }
            return result;
        }
        return List.of();
    }

    /**
     * Constructs the full file path by combining a directory path with a drive item's name.
     *
     * @param filePath The directory path
     * @param driveItem The DriveItem containing the file name
     * @return The complete file path (e.g., "/folder/subfolder/file.txt")
     */
    public static String getFilePath(String filePath, DriveItem driveItem) {
        String fileName = driveItem.name;
        return filePath + "/" + fileName;
    }


    /**
     * Compares two epoch timestamps to determine if they represent the same date and time
     * (down to minute precision).
     *
     * @param entityFieldValue First epoch timestamp in milliseconds
     * @param fieldValue Second epoch timestamp in milliseconds
     * @return true if both timestamps represent the same date/time (formatted as "dd/MM/yyyy, hh:mm a")
     */
    public static boolean isEqualDate(Long entityFieldValue, Long fieldValue) {
        long epochTime1 = entityFieldValue; // First epoch time
        long epochTime2 = fieldValue; // Second epoch time

        // Convert epoch times to Date objects
        Date date1 = new Date(epochTime1);
        Date date2 = new Date(epochTime2);

        // Format dates to compare only date components (day, month, year)
        SimpleDateFormat dateFormat = new SimpleDateFormat(DD_MM_YYYY_HH_MM_A);

        String formattedDate1 = dateFormat.format(date1);
        String formattedDate2 = dateFormat.format(date2);
        return formattedDate1.equals(formattedDate2);
    }

    /**
     * Extracts the relative file path from an absolute OneDrive path.
     * Removes the "/root:" prefix and returns the path relative to the drive root.
     *
     * @param filePath The absolute OneDrive path (e.g., "/drive/root:/folder/file.txt")
     * @return The relative path (e.g., "folder/file.txt"), or null if "/root:" not found
     */

    public static String getRelativeFilePath(String filePath) {
        int rootIndex = filePath.indexOf("/root:");
        String pathAfterRoot = null;
        if (rootIndex != -1) {
            pathAfterRoot = filePath.substring(rootIndex + "/root:".length());

            while (pathAfterRoot.startsWith("/")) {
                pathAfterRoot = pathAfterRoot.substring(1);
            }
            return pathAfterRoot;
        } else {
            return pathAfterRoot;
        }
    }

    /**
     * Extracts the parent directory path from a relative file path.
     * Removes the "shared/" prefix if present.
     *
     * @param relativePath The relative file path (e.g., "folder/subfolder/file.txt")
     * @return The parent directory path (e.g., "folder/subfolder"), or null if no parent exists
     */

    public static String getParentPath(String relativePath) {
        int lastSlashIndex = relativePath.lastIndexOf('/');
        if (lastSlashIndex != -1) {
            String path = relativePath.substring(0, lastSlashIndex);
            path = path.contains("shared/") ? path.replace(Constants.SHARED, "") : path;
            return path;
        } else {
            return null;
        }
    }

    /**
     * Checks if a path contains a drive ID in the format "drives/[id]/root:".
     * This format is used for shared drives or specific drive references.
     *
     * @param relativePath The path to check
     * @return true if the path contains a drive ID pattern, false otherwise
     */

    public static boolean checkDriveIDContains(String relativePath) {
        try {
            String[] parts = relativePath.split("/");
            for (int i = 0; i < parts.length; i++) {
                if ((i == 0 || i == 1) && parts[i].startsWith("drives") && parts[i + 2].startsWith("root:")) {
                    return true;
                }
            }
        } catch (IndexOutOfBoundsException cause) {
            // Ignore
        }
        return false;
    }

    /**
     * Checks if a path refers to a personal drive (user's own OneDrive).
     * Personal drive paths start with "drive/root" or "/drive/root".
     *
     * @param target The path to check
     * @return true if the path is a personal drive path, false otherwise
     */

    public static boolean isPersonalDrive(String target) {
        return target.startsWith(Constants.DRIVE_ROOT) || target.startsWith("/" + Constants.DRIVE_ROOT);
    }

    public static String validateTextInput(String input) {
        return input == null ? "" : input;
    }

    public static List<String> getDriveIDAndPath(String relativePath) {

        String[] stream = relativePath.trim().split("/");

        String path = Arrays.stream(stream, 4, stream.length)
                .collect(Collectors.joining("/"));

        // URL-decode the path so that re-entered paths (where the SubCatalog framework
        // encodes '/' as '%2F') resolve correctly against the Graph API.
        path = URLDecoder.decode(path, StandardCharsets.UTF_8);

        return List.of(stream[2], path);
    }

    public static List<String> getUsersWithPermission(List<Map<String, Object>> share, String targetPermission) {
        List<String> usersWithPermission = new ArrayList<>();
        for (Map<String, Object> user : share) {
            String permission = (String) user.get("Permissions");
            if (permission.equalsIgnoreCase(Constants.OWN) || permission.equalsIgnoreCase(Constants.READ_AND_WRITE)) {
                continue;
            }
            if (permission.equalsIgnoreCase(targetPermission)) {
                usersWithPermission.add((String) user.get("User"));
            }
        }
        return usersWithPermission;
    }


    public static String readFile(InputStream fileContent) {
        StringBuilder stringBuilder = new StringBuilder();
        try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(fileContent))) {
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                stringBuilder.append(line).append("\n");
            }
            return stringBuilder.toString();
        } catch (IOException cause) {
            throw new RuntimeException(cause);
        }
    }

    public static File createTxtFile(String fileName, String fileContent) throws IOException {
        File file = new File(fileName);
        try (FileWriter fileWriter = new FileWriter(file)) {
            fileWriter.write(fileContent);
        }
        return file;
    }

    public static DriveItemRequestBuilder getRootUserRequestBuilder(GraphServiceClientProvider provider) {
        return provider.getUserRequestBuilder(null, null).drive().root();
    }

    public static DriveItemRequestBuilder getSharedUserRequestBuilder(GraphServiceClientProvider provider, String driveId) {
        return provider.getUserRequestBuilder(null, null).drives(driveId).root();
    }

    public static DriveItemRequestBuilder getUserRequestBuilderWithItemId(GraphServiceClientProvider provider, String driveId, String itemId) {
        return driveId != null
                ? provider.getUserRequestBuilder(null, null).drives(driveId).items(itemId)
                : provider.getUserRequestBuilder(null, null).drive().items(itemId);
    }

    public static String validateRootFilePath(String path) {
        if (path.startsWith(Constants.DRIVE_ROOT)) {
            return "/dr";
        }
        return path;
    }


    public static boolean isPathContainsInput(String path, String input) {
        // Split path and input into tokens
        String[] pathTokens = path.split("/");
        String[] inputTokens = input.split("/");

        // Remove leading and trailing empty tokens
        pathTokens = removeEmptyTokens(pathTokens);
        inputTokens = removeEmptyTokens(inputTokens);

        // Check if the number of tokens in path is at least equal to the number of tokens in input
        if (pathTokens.length < inputTokens.length) {
            return false;
        }

        // Compare tokens
        for (int i = 0; i < inputTokens.length; i++) {
            if (!pathTokens[i].equals(inputTokens[i])) {
                return false;
            }
        }

        return true;
    }

    // Helper method to remove leading and trailing empty tokens
    private static String[] removeEmptyTokens(String[] tokens) {
        return Arrays.stream(tokens)
                .filter(token -> !token.isEmpty())
                .toArray(String[]::new);
    }

    public static String handleFolderExistenceMessage(String message) {
        if (message.contains("Name already exists")) {
            return Constants.FOLDER_ALREADY_EXIST;
        }
        return message;
    }

    /**
     * Checks if a path has a file extension.
     *
     * @param path The path to check
     * @return true if the path has a file extension, false otherwise
     */
    public static boolean hasFileExtension(String path) {
        Path filePath = Paths.get(path);
        return filePath.getFileName() != null && filePath.getFileName().toString().contains(".");
    }
}