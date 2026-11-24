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

package app.krista.extensions.essentials.cloudfiles.onedrive.catalog.validators;

import app.krista.extensions.essentials.cloudfiles.onedrive.impl.util.Constants;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests for {@link ValidationOrchestrator}.
 * <p>
 * Regression coverage for the "nested error message" bug: the orchestrator used to pass
 * {@code errorMessage + " " + resourceValue} into the validator's confirmation-step template,
 * which itself wraps its argument in a "We couldn't find the file or folder at the path you
 * provided: '%s'" message. The result was one error message embedded inside another. The
 * confirmation message must contain the plain resource path only.
 */
public class ValidationOrchestratorTest {

    private static final String DRIVE_PATH =
            "/drives/b!testDriveId/root:/Krista/July 2026/Google Gemini/bitbucket_credentials.txt";

    private final ValidationOrchestrator orchestrator = new ValidationOrchestrator();

    @Test
    public void confirmationMessageContainsPathButNotNestedErrorMessage() {
        List<ValidationOrchestrator.ValidationResult> results = orchestrator.validate(
                Map.of(Validator.ValidationResource.SOURCE_FILE, DRIVE_PATH),
                Constants.FILE_SPECIFIC_FILE_NOT_FOUND);

        assertFalse("Orchestrator should return a result", results.isEmpty());
        String confirmMessage = results.get(0).confirmStepMessage();

        assertTrue("Confirmation message should reference the actual path",
                confirmMessage.contains(DRIVE_PATH));
        assertFalse("Confirmation message must not embed the underlying error message (double-wrapping)",
                confirmMessage.contains(Constants.FILE_SPECIFIC_FILE_NOT_FOUND));
    }

    @Test
    public void confirmationMessageIsCleanEvenWithBlankErrorMessage() {
        List<ValidationOrchestrator.ValidationResult> results = orchestrator.validate(
                Map.of(Validator.ValidationResource.SOURCE_FILE, DRIVE_PATH), "");

        String confirmMessage = results.get(0).confirmStepMessage();

        assertTrue(confirmMessage.contains(DRIVE_PATH));
        // No stray leading space that a "" + " " + path concatenation would have produced.
        assertFalse("Path should not be prefixed with a stray space",
                confirmMessage.contains("'" + " " + DRIVE_PATH));
    }
}
