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
import app.krista.extension.executor.ExtensionResponseBuilder;
import app.krista.extensions.essentials.cloudfiles.onedrive.OnedriveAttributes;
import app.krista.extensions.essentials.cloudfiles.onedrive.catalog.FileHandlers.TelemetryHelper;
import app.krista.extensions.essentials.cloudfiles.onedrive.catalog.validators.Validator;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.connectors.GraphServiceClientProvider;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.util.ValidationTypeUtil;
import com.microsoft.graph.http.GraphServiceException;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link ManagementDaoImpl#createFolder(String, String)} and
 * {@link ManagementDaoImpl#createFolder(String, String, Boolean)}.
 *
 * Targets 100% coverage of both overloads:
 * - 2-arg overload delegates to the 3-arg overload with allowRetry = true
 * - 3-arg overload forwards (folderName, path, false, false, allowRetry) to
 *   fileManagementDao.createFolder and returns its response unchanged
 * - IllegalArgumentException from the DAO → re-wrapped as IAE (message + cause preserved,
 *   including the null-message case)
 * - GraphServiceException from the DAO → re-wrapped as IAE (cause preserved)
 * - RuntimeException (outside the catch clause) propagates uncaught, unwrapped
 */
public class ManagementDaoImplCreateFolderTest {

    // ================================================================
    // 2-ARG OVERLOAD — delegates with allowRetry = true
    // ================================================================

    @Test
    public void testCreateFolder_twoArg_delegatesWithAllowRetryTrue() {
        Boolean[] capturedAllowRetry = {null};
        ExtensionResponse expected = buildSuccessResponse();

        ExtensionResponse actual = buildSubject(new FileManagementDaoStub() {
            @Override
            public ExtensionResponse createFolder(String folderName, String path,
                                                  boolean isReenteredFolderName, boolean isReenteredTargetPath,
                                                  Boolean allowRetry) {
                capturedAllowRetry[0] = allowRetry;
                return expected;
            }
        }).createFolder("NewFolder", "drive/root:/");

        assertEquals("2-arg overload must default allowRetry to true", Boolean.TRUE, capturedAllowRetry[0]);
        assertSame("2-arg overload must return the DAO response unchanged", expected, actual);
    }

    // ================================================================
    // 3-ARG OVERLOAD — SUCCESS PATH & FORWARDING
    // ================================================================

    @Test
    public void testCreateFolder_success_returnsDaoResponse() {
        ExtensionResponse expected = buildSuccessResponse();

        ExtensionResponse actual = buildSubject(new FileManagementDaoStub() {
            @Override
            public ExtensionResponse createFolder(String folderName, String path,
                                                  boolean isReenteredFolderName, boolean isReenteredTargetPath,
                                                  Boolean allowRetry) {
                return expected;
            }
        }).createFolder("NewFolder", "drive/root:/", true);

        assertSame("createFolder must return the DAO response unchanged", expected, actual);
    }

    @Test
    public void testCreateFolder_forwardsFolderNameAndPath() {
        String[] capturedName = {null};
        String[] capturedPath = {null};

        buildSubject(new FileManagementDaoStub() {
            @Override
            public ExtensionResponse createFolder(String folderName, String path,
                                                  boolean isReenteredFolderName, boolean isReenteredTargetPath,
                                                  Boolean allowRetry) {
                capturedName[0] = folderName;
                capturedPath[0] = path;
                return buildSuccessResponse();
            }
        }).createFolder("Reports", "drive/root:/Documents", true);

        assertEquals("folderName must be forwarded unchanged", "Reports", capturedName[0]);
        assertEquals("path must be forwarded unchanged", "drive/root:/Documents", capturedPath[0]);
    }

    @Test
    public void testCreateFolder_forwardsReenterFlagsAsFalse() {
        boolean[] capturedFolderNameFlag = {true};
        boolean[] capturedTargetPathFlag = {true};

        buildSubject(new FileManagementDaoStub() {
            @Override
            public ExtensionResponse createFolder(String folderName, String path,
                                                  boolean isReenteredFolderName, boolean isReenteredTargetPath,
                                                  Boolean allowRetry) {
                capturedFolderNameFlag[0] = isReenteredFolderName;
                capturedTargetPathFlag[0] = isReenteredTargetPath;
                return buildSuccessResponse();
            }
        }).createFolder("NewFolder", "drive/root:/", true);

        assertFalse("isReenteredFolderName must be forwarded as false", capturedFolderNameFlag[0]);
        assertFalse("isReenteredTargetPath must be forwarded as false", capturedTargetPathFlag[0]);
    }

    @Test
    public void testCreateFolder_allowRetryTrue_forwarded() {
        Boolean[] captured = {null};

        buildSubject(new FileManagementDaoStub() {
            @Override
            public ExtensionResponse createFolder(String folderName, String path,
                                                  boolean isReenteredFolderName, boolean isReenteredTargetPath,
                                                  Boolean allowRetry) {
                captured[0] = allowRetry;
                return buildSuccessResponse();
            }
        }).createFolder("NewFolder", "drive/root:/", true);

        assertEquals("allowRetry=true must be forwarded unchanged", Boolean.TRUE, captured[0]);
    }

    @Test
    public void testCreateFolder_allowRetryFalse_forwarded() {
        Boolean[] captured = {null};

        buildSubject(new FileManagementDaoStub() {
            @Override
            public ExtensionResponse createFolder(String folderName, String path,
                                                  boolean isReenteredFolderName, boolean isReenteredTargetPath,
                                                  Boolean allowRetry) {
                captured[0] = allowRetry;
                return buildSuccessResponse();
            }
        }).createFolder("NewFolder", "drive/root:/", false);

        assertEquals("allowRetry=false must be forwarded unchanged", Boolean.FALSE, captured[0]);
    }

    // ================================================================
    // IllegalArgumentException — caught, re-wrapped as IAE
    // ================================================================

    @Test(expected = IllegalArgumentException.class)
    public void testCreateFolder_illegalArgumentException_isRethrown() {
        buildSubject(new FileManagementDaoStub() {
            @Override
            public ExtensionResponse createFolder(String folderName, String path,
                                                  boolean isReenteredFolderName, boolean isReenteredTargetPath,
                                                  Boolean allowRetry) {
                throw new IllegalArgumentException("bad arg");
            }
        }).createFolder("NewFolder", "drive/root:/", true);
    }

    @Test
    public void testCreateFolder_illegalArgumentException_messagePreserved() {
        String msg = "invalid folder path";
        try {
            buildSubject(new FileManagementDaoStub() {
                @Override
                public ExtensionResponse createFolder(String folderName, String path,
                                                      boolean isReenteredFolderName, boolean isReenteredTargetPath,
                                                      Boolean allowRetry) {
                    throw new IllegalArgumentException(msg);
                }
            }).createFolder("NewFolder", "drive/root:/", true);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertEquals("Re-wrapped IAE must preserve the original message", msg, e.getMessage());
        }
    }

    @Test
    public void testCreateFolder_illegalArgumentException_causeIsOriginal() {
        IllegalArgumentException original = new IllegalArgumentException("original");
        try {
            buildSubject(new FileManagementDaoStub() {
                @Override
                public ExtensionResponse createFolder(String folderName, String path,
                                                      boolean isReenteredFolderName, boolean isReenteredTargetPath,
                                                      Boolean allowRetry) {
                    throw original;
                }
            }).createFolder("NewFolder", "drive/root:/", true);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertSame("Cause of re-wrapped IAE must be the original exception", original, e.getCause());
        }
    }

    @Test
    public void testCreateFolder_illegalArgumentException_nullMessage_reWrappedWithNullMessage() {
        try {
            buildSubject(new FileManagementDaoStub() {
                @Override
                public ExtensionResponse createFolder(String folderName, String path,
                                                      boolean isReenteredFolderName, boolean isReenteredTargetPath,
                                                      Boolean allowRetry) {
                    throw new IllegalArgumentException((String) null);
                }
            }).createFolder("NewFolder", "drive/root:/", true);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertNull("Re-wrapped IAE must have null message when original message was null", e.getMessage());
        }
    }

    // ================================================================
    // GraphServiceException — caught, re-wrapped as IAE
    // ================================================================

    @Test(expected = IllegalArgumentException.class)
    public void testCreateFolder_graphServiceException_isRethrownAsIllegalArgumentException() {
        buildSubject(new FileManagementDaoStub() {
            @Override
            public ExtensionResponse createFolder(String folderName, String path,
                                                  boolean isReenteredFolderName, boolean isReenteredTargetPath,
                                                  Boolean allowRetry) {
                throw new TestGraphServiceException("Graph API error");
            }
        }).createFolder("NewFolder", "drive/root:/", true);
    }

    @Test
    public void testCreateFolder_graphServiceException_causeIsOriginal() {
        TestGraphServiceException original = new TestGraphServiceException("Graph API unavailable");
        try {
            buildSubject(new FileManagementDaoStub() {
                @Override
                public ExtensionResponse createFolder(String folderName, String path,
                                                      boolean isReenteredFolderName, boolean isReenteredTargetPath,
                                                      Boolean allowRetry) {
                    throw original;
                }
            }).createFolder("NewFolder", "drive/root:/", true);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertSame("Cause of re-wrapped IAE must be the original GraphServiceException",
                    original, e.getCause());
        }
    }

    // ================================================================
    // RuntimeException — NOT caught, propagates upward unwrapped
    // ================================================================

    @Test(expected = RuntimeException.class)
    public void testCreateFolder_unexpectedRuntimeException_propagatesUncaught() {
        buildSubject(new FileManagementDaoStub() {
            @Override
            public ExtensionResponse createFolder(String folderName, String path,
                                                  boolean isReenteredFolderName, boolean isReenteredTargetPath,
                                                  Boolean allowRetry) {
                throw new RuntimeException("unexpected");
            }
        }).createFolder("NewFolder", "drive/root:/", true);
    }

    @Test
    public void testCreateFolder_unexpectedRuntimeException_isNotWrappedAsIAE() {
        try {
            buildSubject(new FileManagementDaoStub() {
                @Override
                public ExtensionResponse createFolder(String folderName, String path,
                                                      boolean isReenteredFolderName, boolean isReenteredTargetPath,
                                                      Boolean allowRetry) {
                    throw new RuntimeException("unexpected");
                }
            }).createFolder("NewFolder", "drive/root:/", true);
            fail("Expected RuntimeException");
        } catch (RuntimeException e) {
            assertFalse("Plain RuntimeException must NOT be re-wrapped as IllegalArgumentException",
                    e instanceof IllegalArgumentException);
            assertEquals("unexpected", e.getMessage());
        }
    }

    // ================================================================
    // HELPERS
    // ================================================================

    /**
     * Builds a ManagementDaoImpl wired with the given FileManagementDao stub.
     * All other DAO dependencies are null since createFolder only uses fileManagementDao.
     */
    private ManagementDaoImpl buildSubject(FileManagementDao fileManagementDao) {
        return new ManagementDaoImpl(fileManagementDao, null, null, null, null, null);
    }

    private static ExtensionResponse buildSuccessResponse() {
        return new ExtensionResponseBuilder()
                .success(Map.of("Success", true, "Message", "Folder created successfully"), null, null)
                .build();
    }

    /**
     * Base stub for FileManagementDao — the 5-arg createFolder throws UnsupportedOperationException.
     * Override createFolder in each test as needed.
     */
    private static class FileManagementDaoStub extends FileManagementDao {
        FileManagementDaoStub() {
            super((GraphServiceClientProvider) null,
                    new OnedriveAttributes("http://localhost", "cid", "cs", "email@test.com", "tid"),
                    createNoOpValidationTypeUtil(),
                    createNoOpTelemetryHelper());
        }

        @Override
        public ExtensionResponse createFolder(String folderName, String path,
                                              boolean isReenteredFolderName, boolean isReenteredTargetPath,
                                              Boolean allowRetry) {
            throw new UnsupportedOperationException("Override this method in the test");
        }

        private static ValidationTypeUtil createNoOpValidationTypeUtil() {
            return new ValidationTypeUtil(null, null, null) {
                @Override
                public ExtensionResponse checkValidations(Validator.ValidationResource vt, String sub, String path, List<Map<String, Object>> share) { return null; }
                @Override
                public ExtensionResponse checkValidationsWithErrorMessage(Validator.ValidationResource vt, String sub, String path, String msg) { return null; }
            };
        }

        private static TelemetryHelper createNoOpTelemetryHelper() {
            return new TelemetryHelper(null) {
                @Override public void incrementCount(String m) {}
                @Override public void recordSuccess(String op, long t, Map<String, String> tags) {}
                @Override public void recordError(String op, long t, Exception e, Map<String, String> tags) {}
                @Override public void recordValidationError(String op, long t, String reason, Map<String, String> tags) {}
                @Override public void recordRetryPrompted(String op, long t, Map<String, String> tags) {}
            };
        }
    }

    /**
     * Concrete subclass of GraphServiceException (which has a protected constructor)
     * that allows easy instantiation in tests.
     */
    private static class TestGraphServiceException extends GraphServiceException {
        TestGraphServiceException(String message) {
            super("POST", "http://graph.test", Collections.emptyList(), message,
                    500, message, Collections.emptyList(), null, false);
        }

        @Override
        public String getMessage() {
            return getResponseMessage();
        }
    }
}
