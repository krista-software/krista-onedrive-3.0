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

import com.microsoft.graph.models.DriveItem;
import com.microsoft.graph.models.ItemReference;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;

public final class DriveUtil {

    private DriveUtil() {
    }

    public static File getFileFromInputStream(InputStream stream, String fileName) throws IOException {
        if (stream == null) {
            throw new IllegalArgumentException("Failed to get file from input stream. Input stream is empty.");
        }
        stringValidator(fileName, "Failed to get file from input stream. File name is empty.");
        File outputFile = new File(fileName);
        Files.copy(stream, outputFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        return outputFile;
    }

    public static void stringValidator(String value, String errorMessage) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(errorMessage);
        }
    }

    public static void listValidator(List<?> files, String errorMessage) {
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException(errorMessage);
        }
    }

    public static byte[] getByteArray(String fileName) throws IOException {
        stringValidator(fileName, "Failed to get byte array. File name is empty.");
        if (fileName.endsWith("xlsx")) {
            try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                workbook.createSheet("Sheet0");
                try (FileOutputStream out = new FileOutputStream(fileName)) {
                    workbook.write(out);
                }
                workbook.write(bos);
                return bos.toByteArray();
            }
        } else if (fileName.endsWith("docx")) {
            try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                try (FileOutputStream out = new FileOutputStream(fileName)) {
                    document.write(out);
                }
                document.write(bos);
                return bos.toByteArray();
            }
        } else {
            try (XMLSlideShow ppt = new XMLSlideShow(); FileOutputStream out = new FileOutputStream(fileName)) {
                ppt.write(out);
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                ppt.write(bos);
                return bos.toByteArray();
            }
        }
    }

    public static byte[] getByteArray(File file) throws IOException {
        try (FileInputStream fl = new FileInputStream(file)) {
            byte[] arr = new byte[(int) file.length()];
            if (fl.read(arr) > 0) {
                return arr;
            }
            return new byte[0];
        }
    }

    public static DriveItem getDriveItem(String folderId) {
        stringValidator(folderId, "Failed to get drive item. Folder id is empty.");
        DriveItem driveItem = new DriveItem();
        ItemReference parentReference = new ItemReference();
        parentReference.id = folderId;
        driveItem.parentReference = parentReference;
        return driveItem;
    }

    public static DriveItem getDriveItemForItem(String folderName) {
        stringValidator(folderName, "Failed to get drive item. Folder name is empty.");
        DriveItem driveItem = new DriveItem();
        driveItem.name = folderName;
        driveItem.folder = new com.microsoft.graph.models.Folder();
        return driveItem;
    }
}