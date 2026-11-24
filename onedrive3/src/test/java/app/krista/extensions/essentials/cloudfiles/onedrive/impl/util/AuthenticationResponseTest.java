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

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Unit tests for AuthenticationResponse.
 * Tests constructor, getters, and various state combinations.
 */
public class AuthenticationResponseTest {

    private static final String TEST_ERROR_MESSAGE = "Authentication failed";
    private static final String TEST_URL = "https://login.microsoftonline.com/authorize";

    // ========================================
    // SUCCESS CASE TESTS
    // ========================================

    /**
     * Test successful response with URL
     */
    @Test
    public void testSuccessResponse_WithUrl() {
        AuthenticationResponse response = new AuthenticationResponse(true, null, TEST_URL);

        assertTrue("isSuccess should be true", response.isSuccess());
        assertNull("errorMessage should be null", response.getErrorMessage());
        assertEquals("url should match", TEST_URL, response.getUrl());
    }

    /**
     * Test successful response without URL
     */
    @Test
    public void testSuccessResponse_WithoutUrl() {
        AuthenticationResponse response = new AuthenticationResponse(true, null, null);

        assertTrue("isSuccess should be true", response.isSuccess());
        assertNull("errorMessage should be null", response.getErrorMessage());
        assertNull("url should be null", response.getUrl());
    }

    // ========================================
    // FAILURE CASE TESTS
    // ========================================

    /**
     * Test failure response with error message
     */
    @Test
    public void testFailureResponse_WithError() {
        AuthenticationResponse response = new AuthenticationResponse(false, TEST_ERROR_MESSAGE, null);

        assertFalse("isSuccess should be false", response.isSuccess());
        assertEquals("errorMessage should match", TEST_ERROR_MESSAGE, response.getErrorMessage());
        assertNull("url should be null", response.getUrl());
    }

    /**
     * Test failure response with both error and URL
     */
    @Test
    public void testFailureResponse_WithErrorAndUrl() {
        AuthenticationResponse response = new AuthenticationResponse(false, TEST_ERROR_MESSAGE, TEST_URL);

        assertFalse("isSuccess should be false", response.isSuccess());
        assertEquals("errorMessage should match", TEST_ERROR_MESSAGE, response.getErrorMessage());
        assertEquals("url should match", TEST_URL, response.getUrl());
    }

    // ========================================
    // NULL AND EDGE CASE TESTS
    // ========================================

    /**
     * Test all-null response (except boolean)
     */
    @Test
    public void testResponse_AllNulls() {
        AuthenticationResponse response = new AuthenticationResponse(false, null, null);

        assertFalse("isSuccess should be false", response.isSuccess());
        assertNull("errorMessage should be null", response.getErrorMessage());
        assertNull("url should be null", response.getUrl());
    }

    /**
     * Test with empty strings
     */
    @Test
    public void testResponse_EmptyStrings() {
        AuthenticationResponse response = new AuthenticationResponse(true, "", "");

        assertTrue("isSuccess should be true", response.isSuccess());
        assertEquals("errorMessage should be empty", "", response.getErrorMessage());
        assertEquals("url should be empty", "", response.getUrl());
    }

    /**
     * Test with long error message
     */
    @Test
    public void testResponse_LongErrorMessage() {
        String longMessage = "Error: " + "x".repeat(1000);
        AuthenticationResponse response = new AuthenticationResponse(false, longMessage, null);

        assertEquals("Long error message should be preserved", longMessage, response.getErrorMessage());
    }
}
