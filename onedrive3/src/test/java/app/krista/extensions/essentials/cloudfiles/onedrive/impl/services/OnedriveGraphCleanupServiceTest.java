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

package app.krista.extensions.essentials.cloudfiles.onedrive.impl.services;

import app.krista.extensions.essentials.cloudfiles.onedrive.OnedriveAttributes;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.connectors.GraphServiceClientProviderFactory;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * Unit tests for OnedriveGraphCleanupService and its CleanupResult inner class.
 */
public class OnedriveGraphCleanupServiceTest {

    private OnedriveGraphCleanupService cleanupService;

    @Before
    public void setUp() {
        GraphServiceClientProviderFactory mockFactory = new GraphServiceClientProviderFactory(
                null, null, null, null);
        cleanupService = new OnedriveGraphCleanupService(mockFactory);
    }

    // ========================================
    // CLEANUP RESULT TESTS
    // ========================================

    /**
     * Test CleanupResult with all successes
     */
    @Test
    public void testCleanupResult_AllSuccess() {
        OnedriveGraphCleanupService.CleanupResult result =
                new OnedriveGraphCleanupService.CleanupResult(5, 0);

        assertEquals("successCount should be 5", 5, result.getSuccessCount());
        assertEquals("failureCount should be 0", 0, result.getFailureCount());
        assertEquals("totalProcessed should be 5", 5, result.getTotalProcessed());
        assertFalse("hasFailures should be false", result.hasFailures());
    }

    /**
     * Test CleanupResult with all failures
     */
    @Test
    public void testCleanupResult_AllFailures() {
        OnedriveGraphCleanupService.CleanupResult result =
                new OnedriveGraphCleanupService.CleanupResult(0, 3);

        assertEquals("successCount should be 0", 0, result.getSuccessCount());
        assertEquals("failureCount should be 3", 3, result.getFailureCount());
        assertEquals("totalProcessed should be 3", 3, result.getTotalProcessed());
        assertTrue("hasFailures should be true", result.hasFailures());
    }

    /**
     * Test CleanupResult with mixed results
     */
    @Test
    public void testCleanupResult_Mixed() {
        OnedriveGraphCleanupService.CleanupResult result =
                new OnedriveGraphCleanupService.CleanupResult(3, 2);

        assertEquals("successCount should be 3", 3, result.getSuccessCount());
        assertEquals("failureCount should be 2", 2, result.getFailureCount());
        assertEquals("totalProcessed should be 5", 5, result.getTotalProcessed());
        assertTrue("hasFailures should be true", result.hasFailures());
    }

    /**
     * Test CleanupResult with zero counts
     */
    @Test
    public void testCleanupResult_ZeroCounts() {
        OnedriveGraphCleanupService.CleanupResult result =
                new OnedriveGraphCleanupService.CleanupResult(0, 0);

        assertEquals("successCount should be 0", 0, result.getSuccessCount());
        assertEquals("failureCount should be 0", 0, result.getFailureCount());
        assertEquals("totalProcessed should be 0", 0, result.getTotalProcessed());
        assertFalse("hasFailures should be false", result.hasFailures());
    }

    /**
     * Test CleanupResult toString format
     */
    @Test
    public void testCleanupResult_ToString() {
        OnedriveGraphCleanupService.CleanupResult result =
                new OnedriveGraphCleanupService.CleanupResult(3, 2);

        String str = result.toString();
        assertNotNull("toString should not return null", str);
        assertTrue("toString should contain success count", str.contains("3"));
        assertTrue("toString should contain failure count", str.contains("2"));
        assertTrue("toString should contain total", str.contains("5"));
    }

    // ========================================
    // DELETE ALL SUBSCRIPTIONS TESTS
    // ========================================

    /**
     * Test deleteAllSubscriptions with empty set returns zero counts
     */
    @Test
    public void testDeleteAllSubscriptions_EmptySet() {
        Set<String> emptySet = Collections.emptySet();
        OnedriveAttributes attrs = createMockAttributes();

        OnedriveGraphCleanupService.CleanupResult result =
                cleanupService.deleteAllSubscriptions(emptySet, attrs);

        assertEquals("successCount should be 0", 0, result.getSuccessCount());
        assertEquals("failureCount should be 0", 0, result.getFailureCount());
        assertFalse("hasFailures should be false", result.hasFailures());
    }

    /**
     * Test deleteAllSubscriptions with null attributes returns all failures
     */
    @Test
    public void testDeleteAllSubscriptions_NullAttributes() {
        Set<String> ids = new HashSet<>();
        ids.add("sub-1");
        ids.add("sub-2");

        OnedriveGraphCleanupService.CleanupResult result =
                cleanupService.deleteAllSubscriptions(ids, null);

        assertEquals("successCount should be 0", 0, result.getSuccessCount());
        assertEquals("failureCount should match input size", 2, result.getFailureCount());
        assertTrue("hasFailures should be true", result.hasFailures());
    }

    // ========================================
    // HELPER METHODS
    // ========================================

    private OnedriveAttributes createMockAttributes() {
        return new OnedriveAttributes(
                "http://localhost:8765",
                "test-client-id",
                "test-client-secret",
                "test@example.com",
                "test-tenant-id");
    }
}
