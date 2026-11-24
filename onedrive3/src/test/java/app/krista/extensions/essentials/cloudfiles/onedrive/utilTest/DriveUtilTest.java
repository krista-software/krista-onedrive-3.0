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

package app.krista.extensions.essentials.cloudfiles.onedrive.utilTest;

import app.krista.extensions.essentials.cloudfiles.onedrive.impl.util.DriveUtil;
import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class DriveUtilTest {

    @Test
    public void testGetFileFromInputStream() {
        IllegalArgumentException exception = Assert.assertThrows(IllegalArgumentException.class, () -> DriveUtil.getFileFromInputStream(null, null));
        Assert.assertTrue(exception.getMessage().contains("Failed to get file from input stream. Input stream is empty."));
        InputStream empty = new ByteArrayInputStream(new byte[0]);
        exception = Assert.assertThrows(IllegalArgumentException.class, () -> DriveUtil.getFileFromInputStream(empty, null));
        Assert.assertTrue(exception.getMessage().contains("Failed to get file from input stream. File name is empty."));
        exception = Assert.assertThrows(IllegalArgumentException.class, () -> DriveUtil.getFileFromInputStream(empty, ""));
        Assert.assertTrue(exception.getMessage().contains("Failed to get file from input stream. File name is empty."));
        exception = Assert.assertThrows(IllegalArgumentException.class, () -> DriveUtil.getFileFromInputStream(empty, "  "));
        Assert.assertTrue(exception.getMessage().contains("Failed to get file from input stream. File name is empty."));
    }

    @Test
    public void testStringValidator() {
        IllegalArgumentException exception = Assert.assertThrows(IllegalArgumentException.class, () -> DriveUtil.stringValidator(null, "Test string validator"));
        Assert.assertTrue(exception.getMessage().contains("Test string validator"));
        exception = Assert.assertThrows(IllegalArgumentException.class, () -> DriveUtil.stringValidator("", "Test string validator"));
        Assert.assertTrue(exception.getMessage().contains("Test string validator"));
        exception = Assert.assertThrows(IllegalArgumentException.class, () -> DriveUtil.stringValidator("  ", "Test string validator"));
        Assert.assertTrue(exception.getMessage().contains("Test string validator"));
    }

    @Test
    public void testListValidator() {
        IllegalArgumentException exception = Assert.assertThrows(IllegalArgumentException.class, () -> DriveUtil.listValidator(null, "Test list validator"));
        Assert.assertTrue(exception.getMessage().contains("Test list validator"));
        exception = Assert.assertThrows(IllegalArgumentException.class, () -> DriveUtil.listValidator(List.of(), "Test list validator"));
        Assert.assertTrue(exception.getMessage().contains("Test list validator"));
    }

    @Test
    public void testGetByteArray() {
        String str = null;
        IllegalArgumentException exception = Assert.assertThrows(IllegalArgumentException.class, () -> DriveUtil.getByteArray(str));
        Assert.assertTrue(exception.getMessage().contains("Failed to get byte array. File name is empty."));
        exception = Assert.assertThrows(IllegalArgumentException.class, () -> DriveUtil.getByteArray(""));
        Assert.assertTrue(exception.getMessage().contains("Failed to get byte array. File name is empty."));
        exception = Assert.assertThrows(IllegalArgumentException.class, () -> DriveUtil.getByteArray("  "));
        Assert.assertTrue(exception.getMessage().contains("Failed to get byte array. File name is empty."));
    }

    @Test
    public void testGetDriveItem() {
        IllegalArgumentException exception = Assert.assertThrows(IllegalArgumentException.class, () -> DriveUtil.getDriveItem(null));
        Assert.assertTrue(exception.getMessage().contains("Failed to get drive item. Folder id is empty."));
        exception = Assert.assertThrows(IllegalArgumentException.class, () -> DriveUtil.getDriveItem(""));
        Assert.assertTrue(exception.getMessage().contains("Failed to get drive item. Folder id is empty."));
        exception = Assert.assertThrows(IllegalArgumentException.class, () -> DriveUtil.getDriveItem("  "));
        Assert.assertTrue(exception.getMessage().contains("Failed to get drive item. Folder id is empty."));
    }

    @Test
    public void testGetDriveItemForItem() {
        IllegalArgumentException exception = Assert.assertThrows(IllegalArgumentException.class, () -> DriveUtil.getDriveItemForItem(null));
        Assert.assertTrue(exception.getMessage().contains("Failed to get drive item. Folder name is empty."));
        exception = Assert.assertThrows(IllegalArgumentException.class, () -> DriveUtil.getDriveItemForItem(""));
        Assert.assertTrue(exception.getMessage().contains("Failed to get drive item. Folder name is empty."));
        exception = Assert.assertThrows(IllegalArgumentException.class, () -> DriveUtil.getDriveItemForItem("  "));
        Assert.assertTrue(exception.getMessage().contains("Failed to get drive item. Folder name is empty."));
    }

    // ========================================
    // getFileFromInputStream — temp-dir fix
    // ========================================

    @Test
    public void testGetFileFromInputStream_ValidStream_WritesToTempDir() throws IOException {
        String fileName = "driveutil-tempdir-test.txt";
        byte[] content = "test content for temp dir check".getBytes(StandardCharsets.UTF_8);
        InputStream stream = new ByteArrayInputStream(content);

        File result = DriveUtil.getFileFromInputStream(stream, fileName);

        try {
            Assert.assertNotNull("Result file must not be null", result);
            Assert.assertEquals("File name must match the requested name", fileName, result.getName());
            Assert.assertTrue("File must exist on disk", result.exists());
            // Verify the file lives in the system temp directory, not the working directory
            String expectedTempDir = new File(System.getProperty("java.io.tmpdir")).getCanonicalPath();
            String actualParentDir = result.getParentFile().getCanonicalPath();
            Assert.assertEquals("File must be written to system temp dir, not CWD", expectedTempDir, actualParentDir);
        } finally {
            if (result != null && result.exists()) {
                result.delete();
            }
        }
    }

    @Test
    public void testGetFileFromInputStream_EmptyStream_CreatesEmptyFileInTempDir() throws IOException {
        String fileName = "driveutil-empty-stream-test.bin";
        InputStream emptyStream = new ByteArrayInputStream(new byte[0]);

        File result = DriveUtil.getFileFromInputStream(emptyStream, fileName);

        try {
            Assert.assertNotNull("Result file must not be null for empty stream", result);
            Assert.assertEquals("File name must match", fileName, result.getName());
            String expectedTempDir = new File(System.getProperty("java.io.tmpdir")).getCanonicalPath();
            String actualParentDir = result.getParentFile().getCanonicalPath();
            Assert.assertEquals("Empty-stream file must also be in temp dir", expectedTempDir, actualParentDir);
        } finally {
            if (result != null && result.exists()) {
                result.delete();
            }
        }
    }
}