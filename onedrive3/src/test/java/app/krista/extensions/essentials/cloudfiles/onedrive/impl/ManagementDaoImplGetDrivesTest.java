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
 * Unit tests for {@link ManagementDaoImpl#getDrives(String, boolean, String)}.
 *
 * Covers:
 * - Success: response from fileManagementDao.getListOfDrives is returned unchanged
 * - All three parameters (search, useEmail, accountID) are forwarded as-is
 * - IllegalArgumentException thrown by getListOfDrives → re-wrapped as IAE with same message
 * - GraphServiceException thrown by getListOfDrives → re-wrapped as IAE with same message
 * - Original exception is preserved as the cause in both re-wrap cases
 * - RuntimeException (not in catch clause) propagates upward uncaught
 */
public class ManagementDaoImplGetDrivesTest {

    // ================================================================
    // SUCCESS PATH
    // ================================================================

    @Test
    public void testGetDrives_success_returnsDaoResponse() {
        ExtensionResponse expected = buildSuccessResponse();

        ManagementDaoImpl subject = buildSubject(new FileManagementDaoStub() {
            @Override
            public ExtensionResponse getListOfDrives(String search, boolean useEmail, String accountID) {
                return expected;
            }
        });

        ExtensionResponse actual = subject.getDrives(null, true, "account");

        assertSame("getDrives must return the response from fileManagementDao unchanged", expected, actual);
    }

    // ================================================================
    // PARAMETER FORWARDING
    // ================================================================

    @Test
    public void testGetDrives_searchForwarded_null() {
        String[] captured = {"SENTINEL"};

        buildSubject(new FileManagementDaoStub() {
            @Override
            public ExtensionResponse getListOfDrives(String search, boolean useEmail, String accountID) {
                captured[0] = search;
                return buildSuccessResponse();
            }
        }).getDrives(null, true, "account");

        assertNull("null search must be forwarded as null", captured[0]);
    }

    @Test
    public void testGetDrives_searchForwarded_nonNull() {
        String[] captured = {null};

        buildSubject(new FileManagementDaoStub() {
            @Override
            public ExtensionResponse getListOfDrives(String search, boolean useEmail, String accountID) {
                captured[0] = search;
                return buildSuccessResponse();
            }
        }).getDrives("Sales", true, "account");

        assertEquals("Non-null search must be forwarded unchanged", "Sales", captured[0]);
    }

    @Test
    public void testGetDrives_useEmailTrue_forwarded() {
        boolean[] captured = {false};

        buildSubject(new FileManagementDaoStub() {
            @Override
            public ExtensionResponse getListOfDrives(String search, boolean useEmail, String accountID) {
                captured[0] = useEmail;
                return buildSuccessResponse();
            }
        }).getDrives(null, true, "account");

        assertTrue("useEmail=true must be forwarded as true", captured[0]);
    }

    @Test
    public void testGetDrives_useEmailFalse_forwarded() {
        boolean[] captured = {true};

        buildSubject(new FileManagementDaoStub() {
            @Override
            public ExtensionResponse getListOfDrives(String search, boolean useEmail, String accountID) {
                captured[0] = useEmail;
                return buildSuccessResponse();
            }
        }).getDrives(null, false, "account");

        assertFalse("useEmail=false must be forwarded as false", captured[0]);
    }

    @Test
    public void testGetDrives_accountIdForwarded() {
        String[] captured = {null};

        buildSubject(new FileManagementDaoStub() {
            @Override
            public ExtensionResponse getListOfDrives(String search, boolean useEmail, String accountID) {
                captured[0] = accountID;
                return buildSuccessResponse();
            }
        }).getDrives(null, true, "specific-account-id");

        assertEquals("accountID must be forwarded unchanged", "specific-account-id", captured[0]);
    }

    // ================================================================
    // IllegalArgumentException — caught, re-wrapped
    // ================================================================

    @Test(expected = IllegalArgumentException.class)
    public void testGetDrives_illegalArgumentException_isRethrown() {
        buildSubject(new FileManagementDaoStub() {
            @Override
            public ExtensionResponse getListOfDrives(String search, boolean useEmail, String accountID) {
                throw new IllegalArgumentException("bad arg");
            }
        }).getDrives(null, true, "account");
    }

    @Test
    public void testGetDrives_illegalArgumentException_messagePreserved() {
        String msg = "bad argument supplied";
        try {
            buildSubject(new FileManagementDaoStub() {
                @Override
                public ExtensionResponse getListOfDrives(String search, boolean useEmail, String accountID) {
                    throw new IllegalArgumentException(msg);
                }
            }).getDrives(null, true, "account");
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertEquals("Re-wrapped IAE must preserve the original message", msg, e.getMessage());
        }
    }

    @Test
    public void testGetDrives_illegalArgumentException_causeIsOriginal() {
        IllegalArgumentException original = new IllegalArgumentException("original");
        try {
            buildSubject(new FileManagementDaoStub() {
                @Override
                public ExtensionResponse getListOfDrives(String search, boolean useEmail, String accountID) {
                    throw original;
                }
            }).getDrives(null, true, "account");
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertSame("Cause of re-wrapped IAE must be the original exception", original, e.getCause());
        }
    }

    @Test
    public void testGetDrives_illegalArgumentException_nullMessage_reWrappedWithNullMessage() {
        try {
            buildSubject(new FileManagementDaoStub() {
                @Override
                public ExtensionResponse getListOfDrives(String search, boolean useEmail, String accountID) {
                    throw new IllegalArgumentException((String) null);
                }
            }).getDrives(null, true, "account");
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertNull("Re-wrapped IAE must have null message when original message was null", e.getMessage());
        }
    }

    // ================================================================
    // GraphServiceException — caught, re-wrapped as IAE
    // ================================================================

    @Test(expected = IllegalArgumentException.class)
    public void testGetDrives_graphServiceException_isRethrownAsIllegalArgumentException() {
        buildSubject(new FileManagementDaoStub() {
            @Override
            public ExtensionResponse getListOfDrives(String search, boolean useEmail, String accountID) {
                throw new TestGraphServiceException("Graph API error");
            }
        }).getDrives(null, true, "account");
    }

    @Test
    public void testGetDrives_graphServiceException_causeIsOriginal() {
        TestGraphServiceException original = new TestGraphServiceException("Graph API unavailable");
        try {
            buildSubject(new FileManagementDaoStub() {
                @Override
                public ExtensionResponse getListOfDrives(String search, boolean useEmail, String accountID) {
                    throw original;
                }
            }).getDrives(null, true, "account");
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertSame("Cause of re-wrapped IAE must be the original GraphServiceException",
                    original, e.getCause());
        }
    }

    // ================================================================
    // RuntimeException — NOT caught, propagates upward
    // ================================================================

    @Test(expected = RuntimeException.class)
    public void testGetDrives_unexpectedRuntimeException_propagatesUncaught() {
        buildSubject(new FileManagementDaoStub() {
            @Override
            public ExtensionResponse getListOfDrives(String search, boolean useEmail, String accountID) {
                throw new RuntimeException("unexpected");
            }
        }).getDrives(null, true, "account");
    }

    @Test
    public void testGetDrives_unexpectedRuntimeException_isNotWrappedAsIAE() {
        try {
            buildSubject(new FileManagementDaoStub() {
                @Override
                public ExtensionResponse getListOfDrives(String search, boolean useEmail, String accountID) {
                    throw new RuntimeException("unexpected");
                }
            }).getDrives(null, true, "account");
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
     * All other DAO dependencies are null since getDrives only uses fileManagementDao.
     */
    private ManagementDaoImpl buildSubject(FileManagementDao fileManagementDao) {
        return new ManagementDaoImpl(fileManagementDao, null, null, null, null, null);
    }

    private static ExtensionResponse buildSuccessResponse() {
        return new ExtensionResponseBuilder()
                .success(Map.of("Success", true, "Drives", Collections.emptyList(), "Message", "OK"), null, null)
                .build();
    }

    /**
     * Base stub for FileManagementDao — all methods throw UnsupportedOperationException.
     * Override getListOfDrives in each test as needed.
     */
    private static class FileManagementDaoStub extends FileManagementDao {
        FileManagementDaoStub() {
            super((GraphServiceClientProvider) null,
                    new OnedriveAttributes("http://localhost", "cid", "cs", "email@test.com", "tid"),
                    createNoOpValidationTypeUtil(),
                    createNoOpTelemetryHelper());
        }

        @Override
        public ExtensionResponse getListOfDrives(String search, boolean useEmail, String accountID) {
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
            super("GET", "http://graph.test", Collections.emptyList(), message,
                    500, message, Collections.emptyList(), null, false);
        }

        @Override
        public String getMessage() {
            return getResponseMessage();
        }
    }
}
