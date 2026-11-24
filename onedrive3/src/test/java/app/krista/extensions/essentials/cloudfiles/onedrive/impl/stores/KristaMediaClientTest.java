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

package app.krista.extensions.essentials.cloudfiles.onedrive.impl.stores;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link KristaMediaClient}.
 * <p>
 * Focuses on the static {@link KristaMediaClient#compressFile} utility (which has no
 * external dependencies) and the instantiation of the class.  The {@code toKristaFile}
 * and {@code toJavaFile} methods require an injected {@code FileRepository} (Krista SDK)
 * and are exercised at the integration level via {@link app.krista.extensions.essentials.cloudfiles.onedrive.impl.OperationsManagementDaoTest}.
 * </p>
 */
public class KristaMediaClientTest {

    // ========================================
    // compressFile — public static utility
    // ========================================

    @Test
    public void testCompressFile_ValidFile_CreatesZipWithEntry() throws IOException {
        // Arrange: create a real temporary source file
        File sourceFile = File.createTempFile("krista-compress-src-", ".txt");
        File zipFile = new File(System.getProperty("java.io.tmpdir"), "krista-compress-out-" + System.nanoTime() + ".zip");
        try {
            Files.writeString(sourceFile.toPath(), "Hello from KristaMediaClientTest");

            // Act
            KristaMediaClient.compressFile(zipFile.getAbsolutePath(), sourceFile.getAbsolutePath());

            // Assert: zip file is created and contains the source file entry
            assertTrue("Zip file must be created", zipFile.exists());
            assertTrue("Zip file must not be empty", zipFile.length() > 0);

            try (ZipFile zip = new ZipFile(zipFile)) {
                ZipEntry entry = zip.getEntry(sourceFile.getName());
                assertNotNull("Zip must contain an entry named after the source file", entry);
            }
        } finally {
            sourceFile.delete();
            zipFile.delete();
        }
    }

    @Test
    public void testCompressFile_NonExistentSource_ThrowsIOException() {
        String missingSource = System.getProperty("java.io.tmpdir") + "/nonexistent-source-file-abc.txt";
        String outputZip = System.getProperty("java.io.tmpdir") + "/krista-compress-fail-" + System.nanoTime() + ".zip";

        assertThrows("compressFile must throw IOException when source does not exist",
                IOException.class,
                () -> KristaMediaClient.compressFile(outputZip, missingSource));
    }

    @Test
    public void testCompressFile_SourceIsDirectory_ThrowsIOException() throws IOException {
        // A directory cannot be read as a FileInputStream — compressFile should throw
        File tempDir = Files.createTempDirectory("krista-compress-dir-test").toFile();
        String outputZip = System.getProperty("java.io.tmpdir") + "/krista-compress-dir-fail-" + System.nanoTime() + ".zip";
        try {
            assertThrows("compressFile must throw IOException when source is a directory",
                    IOException.class,
                    () -> KristaMediaClient.compressFile(outputZip, tempDir.getAbsolutePath()));
        } finally {
            tempDir.delete();
            new File(outputZip).delete();
        }
    }

    // ========================================
    // Instantiation
    // ========================================

    @Test
    public void testKristaMediaClient_CanBeInstantiated() {
        // HK2 will inject dependencies at runtime; verify the class can be constructed
        // without the injected FileRepository (used by mocks in OperationsManagementDaoTest)
        KristaMediaClient client = new KristaMediaClient();
        assertNotNull("KristaMediaClient instance must not be null", client);
    }

    // ========================================
    // isUnsupportedFileFormat fallback — indirect coverage via subclassing
    // ========================================

    /**
     * Verifies that when {@code fileRepository.getBlackListedFileExtensions()} throws
     * (simulated via the local fallback list), the method still returns the correct result
     * for a known unsupported extension.
     * <p>
     * The fallback list in {@link KristaMediaClient} contains:
     * "html", "php5", "pht", "phtml", "shtml", "asa", "cer", "asax", "swf", "xap", "jsp", "exe", "js"
     * </p>
     */
    @Test
    public void testIsUnsupportedFileFormat_FallbackList_HtmlIsUnsupported() throws Exception {
        // Create a subclass where fileRepository throws so the local fallback list is used
        KristaMediaClient clientWithBrokenRepo = new KristaMediaClient() {
            // fileRepository is null (not injected in test) → getBlackListedFileExtensions()
            // will throw NullPointerException, triggering the catch-block fallback
        };

        // Invoke isUnsupportedFileFormat indirectly via reflection
        java.lang.reflect.Method method = KristaMediaClient.class
                .getDeclaredMethod("isUnsupportedFileFormat", String.class);
        method.setAccessible(true);

        // html is in the local fallback list
        Object resultHtml = method.invoke(clientWithBrokenRepo, "page.html");
        assertTrue("html must be identified as unsupported via fallback list", (Boolean) resultHtml);

        // txt is NOT in the fallback list
        Object resultTxt = method.invoke(clientWithBrokenRepo, "document.txt");
        assertFalse("txt must NOT be identified as unsupported via fallback list", (Boolean) resultTxt);
    }

    @Test
    public void testIsUnsupportedFileFormat_FallbackList_ExeIsUnsupported() throws Exception {
        KristaMediaClient client = new KristaMediaClient();

        java.lang.reflect.Method method = KristaMediaClient.class
                .getDeclaredMethod("isUnsupportedFileFormat", String.class);
        method.setAccessible(true);

        Object resultExe = method.invoke(client, "program.exe");
        assertTrue("exe must be identified as unsupported via fallback list", (Boolean) resultExe);
    }

    @Test
    public void testIsUnsupportedFileFormat_FallbackList_JsIsUnsupported() throws Exception {
        KristaMediaClient client = new KristaMediaClient();

        java.lang.reflect.Method method = KristaMediaClient.class
                .getDeclaredMethod("isUnsupportedFileFormat", String.class);
        method.setAccessible(true);

        Object result = method.invoke(client, "script.js");
        assertTrue("js must be identified as unsupported via fallback list", (Boolean) result);
    }

    @Test
    public void testIsUnsupportedFileFormat_FallbackList_PdfIsSafe() throws Exception {
        KristaMediaClient client = new KristaMediaClient();

        java.lang.reflect.Method method = KristaMediaClient.class
                .getDeclaredMethod("isUnsupportedFileFormat", String.class);
        method.setAccessible(true);

        Object result = method.invoke(client, "report.pdf");
        assertFalse("pdf must NOT be identified as unsupported", (Boolean) result);
    }

    @Test
    public void testGetFileExtension_NoExtension_ThrowsIllegalArgument() throws Exception {
        KristaMediaClient client = new KristaMediaClient();

        java.lang.reflect.Method method = KristaMediaClient.class
                .getDeclaredMethod("getFileExtension", String.class);
        method.setAccessible(true);

        try {
            method.invoke(client, "filewithoutextension");
            fail("Expected IllegalArgumentException for file without extension");
        } catch (java.lang.reflect.InvocationTargetException e) {
            assertSame("Cause must be IllegalArgumentException",
                    IllegalArgumentException.class, e.getCause().getClass());
        }
    }
}
