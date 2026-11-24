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

import app.krista.extensions.essentials.cloudfiles.onedrive.impl.util.FileUtil;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class FileUtilTest {


    @Test
    public void testGetBaseNameAndExtension() {
        // Test file names
        String fileName1 = "abced.eede.txt";
        String fileName2 = "filewithoutextension";
        String fileName3 = ".hiddenfile";
        String fileName4 = "file.with.multiple.extensions.jpeg";
        String fileName5 = "abcddvw.txt";

        // Test file names and expected results
        String[][] testCases = {
                {fileName1, "abced.eede", "txt"},
                {fileName2, "filewithoutextension", ""},
                {fileName3, ".hiddenfile", ""},
                {fileName4, "file.with.multiple.extensions", "jpeg"},
                {fileName5, "abcddvw", "txt"}

        };

        // Perform tests
        for (String[] testCase : testCases) {
            List<String> result = FileUtil.getBaseName(testCase[0]);
            assertEquals(testCase[1], result.get(0)); // Base Name assertion
            assertEquals(testCase[2], result.get(1)); // Extension assertion
        }
    }

    @Test
    public void testGetRelativeFilePath() {
        String filePath1 = "/drives/b!bmOsU0Afr0OJ53x0qg9Bs-VQuYjd4E1NuMJ7xz2Iszy7s3T6khKjRo5Rh1OgPiHb/root:/Test Shared Folder/fileIfo/data";
        String filePath2 = "/invalid/path/without/root";

        // Test valid file path
        String relativePath1 = FileUtil.getRelativeFilePath(filePath1);
        assertEquals("Test Shared Folder/fileIfo/data", relativePath1);

        // Test invalid file path
        String relativePath2 = FileUtil.getRelativeFilePath(filePath2);
        assertNull(relativePath2);
    }

    @Test
    public void testGetParentPathWithShared() {
        // Test with a valid path containing "shared"
        String filePathWithShared = "shared/Teams/QA/03_Automation";
        String expectedPathWithShared = "/Teams/QA";
        assertEquals(expectedPathWithShared, FileUtil.getParentPath(filePathWithShared));
    }

    @Test
    public void testGetParentPathWithSlashShared() {
        // Test with a valid path containing "/shared"
        String filePathWithSlashShared = "shared/Teams/QA/03_Automation/another_folder";
        String expectedPathWithSlashShared = "/Teams/QA/03_Automation";
        assertEquals(expectedPathWithSlashShared, FileUtil.getParentPath(filePathWithSlashShared));
    }

    @Test
    public void testGetParentPathWithoutShared() {
        // Test with a path that doesn't contain "shared" or "/shared"
        String filePathWithoutShared = "/drives/b!jxUeN0DgA02sUFccW1Pb1lXRHihDmDNCmAiRj77wj4mIw359lmkzQYFiwAJ5WZk3/root:/Teams/QA/03_Automation";
        String expectedPathWithoutShared = "/drives/b!jxUeN0DgA02sUFccW1Pb1lXRHihDmDNCmAiRj77wj4mIw359lmkzQYFiwAJ5WZk3/root:/Teams/QA";
        assertEquals(expectedPathWithoutShared, FileUtil.getParentPath(filePathWithoutShared));
    }

    @Test
    public void testGetParentPathWithRootShared() {
        // Test with a valid root path
        String filePathWithRootShared = "/drives/b!jxUeN0DgA02sUFccW1Pb1lXRHihDmDNCmAiRj77wj4mIw359lmkzQYFiwAJ5WZk3/root:/Teams";
        String expectedPathWithRootShared = "/drives/b!jxUeN0DgA02sUFccW1Pb1lXRHihDmDNCmAiRj77wj4mIw359lmkzQYFiwAJ5WZk3/root:";
        assertEquals(expectedPathWithRootShared, FileUtil.getParentPath(filePathWithRootShared));
    }

    @Test
    public void testGetParentPathInvalidInput() {
        // Test with a path that doesn't contain a slash
        String pathWithoutSlash = "samplepath";
        assertNull(FileUtil.getParentPath(pathWithoutSlash));
    }

    @Test
    public void testGetParentPathEmptyInput() {
        // Test with an empty path
        String emptyPath = "";
        assertNull(FileUtil.getParentPath(emptyPath));
    }


    @Test
    public void testCheckDriveIDContains() {
        // Test with paths containing the specified format
        String validPath1 = "/drives/b!jxUeN0DgA02sUFccW1Pb1lXRHihDmDNCmAiRj77wj4mIw359lmkzQYFiwAJ5WZk3/root:/Teams/QA/03_Automation";
        assertTrue(FileUtil.checkDriveIDContains(validPath1));

        String validPath2 = "/some/other/path/drives/b!somerandomID/root:/another/folder";
        assertFalse(FileUtil.checkDriveIDContains(validPath2));

        // Test with paths not containing the specified format
        String invalidPath1 = "/drives/someotherID";
        assertFalse(FileUtil.checkDriveIDContains(invalidPath1));

        String invalidPath2 = "/some/other/path";
        assertFalse(FileUtil.checkDriveIDContains(invalidPath2));
    }

    @Test
    public void testGetDriveIDAndPath() {
        String filePath = "/drives/b!jxUeN0DgA02sUFccW1Pb1lXRHihDmDNCmAiRj77wj4mIw359lmkzQYFiwAJ5WZk3/root:/Teams/QA/03_Automation";
        List<String> expected = List.of("b!jxUeN0DgA02sUFccW1Pb1lXRHihDmDNCmAiRj77wj4mIw359lmkzQYFiwAJ5WZk3", "Teams/QA/03_Automation");
        List<String> result = FileUtil.getDriveIDAndPath(filePath);

        assertEquals(expected, result);
    }


    @Test
    public void testGetUsersWithPermission() {
        // Sample data
        List<Map<String, Object>> share = new ArrayList<>();
        Map<String, Object> user1 = new HashMap<>();
        user1.put("User", "user1@example.com");
        user1.put("Permissions", "Read");
        share.add(user1);

        Map<String, Object> user2 = new HashMap<>();
        user2.put("User", "user2@example.com");
        user2.put("Permissions", "Write");
        share.add(user2);

        Map<String, Object> user3 = new HashMap<>();
        user3.put("User", "user3@example.com");
        user3.put("Permissions", "Read and Write");
        share.add(user3);

        Map<String, Object> user4 = new HashMap<>();
        user3.put("User", "user4@example.com");
        user3.put("Permissions", "Own");
        share.add(user3);

        // Testing method
        List<String> usersWithReadPermission = FileUtil.getUsersWithPermission(share, "Read");
        List<String> usersWithWritePermission = FileUtil.getUsersWithPermission(share, "Write");
        usersWithWritePermission.addAll(FileUtil.getUsersWithPermission(share, "Read and Write"));

        // Assertions
        assertEquals(1, usersWithReadPermission.size());
        assertEquals("user1@example.com", usersWithReadPermission.get(0));

        assertEquals(1, usersWithWritePermission.size());
        assertEquals("user2@example.com", usersWithWritePermission.get(0));
    }


    @Test
    public void testReadFile() {
        // Test input
        String inputString = "This is a test string.\nWith multiple lines.\n";

        // Convert the inputString to InputStream
        InputStream inputStream = new ByteArrayInputStream(inputString.getBytes(StandardCharsets.UTF_8));

        // Test reading InputStream
        String result;
        try {
            result = FileUtil.readFile(inputStream);
        } catch (RuntimeException e) {
            throw new AssertionError("Unexpected exception: " + e.getMessage(), e);
        }

        // Assert the content read from InputStream matches the inputString
        assertEquals(inputString, result);
    }

    @Test
    public void testReadFileWithIOException() {
        // Test reading InputStream with IOException
        InputStream inputStream = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("Simulated IOException");
            }
        };

        // Assert that reading the InputStream with an IOException throws a RuntimeException
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            FileUtil.readFile(inputStream);
        });

        // Assert that the cause of the RuntimeException is an IOException
        assertEquals(IOException.class, exception.getCause().getClass());
    }

    @Test
    public void testCreateTxtFile() {
        String fileName = "testFile.txt";
        String fileContent = "This is a test file content.";

        File createdFile = null;
        try {
            createdFile = FileUtil.createTxtFile(fileName, fileContent);

            assertTrue(createdFile.exists());
            assertTrue(createdFile.isFile());

        } catch (IOException e) {
            fail("IOException occurred: " + e.getMessage());
        } finally {
            if (createdFile != null && createdFile.exists()) {
                assertTrue(createdFile.delete());
            }
        }
    }

    @Test
    public void testEqualDates() {
        long epochTime1 = 1708321460000L; // First epoch time
        long epochTime2 = 1708321440000L; // Second epoch time

        boolean result = FileUtil.isEqualDate(epochTime1, epochTime2);

        assertEquals(true, result);
    }

    @Test
    public void testDifferentDates() {
        long epochTime1 = 1708321460000L; // First epoch time
        long epochTime3 = 1708310000000L; // Third epoch time (different date)

        boolean result = FileUtil.isEqualDate(epochTime1, epochTime3);

        assertEquals(false, result);
    }

    @Test
    public void testNullInput() {
        assertEquals("", FileUtil.validateTextInput(null));
    }

    @Test
    public void testEmptyStringInput() {
        assertEquals("", FileUtil.validateTextInput(""));
    }

    @Test
    public void testNonEmptyStringInput() {
        assertEquals("FileName", FileUtil.validateTextInput("FileName"));
    }


    @Test
    public void testPathContainsInputWithPathStartingWithInput() {
        String path = "/drive/root:/CN Test2/abc.txt";
        String input = "/drive/root:/CN Test2";
        assertTrue(FileUtil.isPathContainsInput(path, input));
    }

    @Test
    public void testPathContainsInputWithPathStartingWithInputWithoutLeadingSlashes() {
        String path = "/drive/root:/CN Test2/abc.txt";
        String input = "drive/root:/CN Test2";
        assertTrue(FileUtil.isPathContainsInput(path, input));
    }

    @Test
    public void testPathDoesNotContainInput() {
        String path = "/drive/root:/CN Test2/abc.txt";
        String input = "/drive/root:/CN Test";
        assertFalse(FileUtil.isPathContainsInput(path, input));
    }

    @Test
    public void testPathDoesNotContainInputWithoutLeadingSlashes() {
        String path = "/drive/root:/CN Test2/abc.txt";
        String input = "drive/root:/CN Test";
        assertFalse(FileUtil.isPathContainsInput(path, input));
    }

    @Test
    public void testPathContainsExactInput() {
        String path = "/drive/root:/CN Test";
        String input = "/drive/root:/CN Test";
        assertTrue(FileUtil.isPathContainsInput(path, input));
    }

    @Test
    public void testPathDoesNotContainInputLongerInput() {
        String path = "/drive/root:/CN Test";
        String input = "/drive/root:/CN Test2";
        assertFalse(FileUtil.isPathContainsInput(path, input));
    }

    @Test
    public void testPathContainsInputWithAdditionalCharacters() {
        String path = "/drive/root:/CN Test2/subfolder";
        String input = "/drive/root:/CN Test2";
        assertTrue(FileUtil.isPathContainsInput(path, input));
    }

    @Test
    public void testPathDoesNotContainInputWithAdditionalCharacters() {
        String path = "/drive/root:/CN Test/subfolder";
        String input = "/drive/root:/CN Test2";
        assertFalse(FileUtil.isPathContainsInput(path, input));
    }

    // ========================================
    // getDriveIDAndPath — URL-decode fix
    // ========================================

    @Test
    public void testGetDriveIDAndPath_PlainPath_UnchangedAfterDecode() {
        // Normal path without encoding — should pass through unchanged
        String plainPath = "/drives/b!jxUeN0DgA02sUFccW1Pb1lXRHihDmDNCmAiRj77wj4mIw359lmkzQYFiwAJ5WZk3/root:/Teams/QA/03_Automation";
        List<String> result = FileUtil.getDriveIDAndPath(plainPath);
        assertEquals("Drive ID must be extracted correctly",
                "b!jxUeN0DgA02sUFccW1Pb1lXRHihDmDNCmAiRj77wj4mIw359lmkzQYFiwAJ5WZk3", result.get(0));
        assertEquals("Plain path must remain unchanged", "Teams/QA/03_Automation", result.get(1));
    }

    @Test
    public void testGetDriveIDAndPath_UrlEncodedSlashes_DecodedCorrectly() {
        // SubCatalog framework re-enters with %2F instead of /
        String encodedPath = "/drives/b!abc123XYZ/root:/folder%2Fsubfolder%2Ffile.txt";
        List<String> result = FileUtil.getDriveIDAndPath(encodedPath);
        assertEquals("Drive ID must be extracted correctly", "b!abc123XYZ", result.get(0));
        assertEquals("URL-encoded %2F must be decoded to /", "folder/subfolder/file.txt", result.get(1));
    }

    @Test
    public void testGetDriveIDAndPath_SingleEncodedSegment_DecodedCorrectly() {
        // Single %2F — one path separator re-entered via SubCatalog
        String encodedPath = "/drives/b!testDriveId/root:/parentFolder%2FchildFolder";
        List<String> result = FileUtil.getDriveIDAndPath(encodedPath);
        assertEquals("b!testDriveId", result.get(0));
        assertEquals("parentFolder/childFolder", result.get(1));
    }

    @Test
    public void testGetDriveIDAndPath_NoSubPath_EmptyPathReturned() {
        // Path ends at /root:/ with nothing after — relative path should be empty
        String rootOnlyPath = "/drives/b!abc123/root:/";
        List<String> result = FileUtil.getDriveIDAndPath(rootOnlyPath);
        assertEquals("b!abc123", result.get(0));
        assertEquals("", result.get(1));
    }

}