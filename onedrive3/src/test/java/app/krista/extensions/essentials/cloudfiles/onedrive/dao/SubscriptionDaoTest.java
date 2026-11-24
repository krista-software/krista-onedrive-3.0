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

package app.krista.extensions.essentials.cloudfiles.onedrive.dao;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Unit tests for SubscriptionDao.
 * Tests factory methods, getters, and immutable "with" methods.
 */
public class SubscriptionDaoTest {

    private static final String TEST_SUBSCRIPTION_ID = "sub-12345";
    private static final String TEST_DRIVE_ID = "drive-67890";
    private static final String TEST_DELTA_TOKEN = "delta-token-abc";
    private static final long TEST_EXPIRATION = 1700000000000L;

    // ========================================
    // FACTORY METHOD TESTS
    // ========================================

    /**
     * Test 3-arg create factory method sets all fields correctly
     */
    @Test
    public void testCreate_ThreeArgs() {
        SubscriptionDao dao = SubscriptionDao.create(TEST_SUBSCRIPTION_ID, TEST_DRIVE_ID, TEST_DELTA_TOKEN);

        assertEquals("subscriptionId should match", TEST_SUBSCRIPTION_ID, dao.getSubscriptionId());
        assertEquals("driveId should match", TEST_DRIVE_ID, dao.getDriveId());
        assertEquals("deltaToken should match", TEST_DELTA_TOKEN, dao.getDeltaToken());
        assertEquals("expirationTimestamp should default to 0", 0, dao.getExpirationTimestamp());
    }

    /**
     * Test 4-arg create factory method sets all fields correctly
     */
    @Test
    public void testCreate_FourArgs() {
        SubscriptionDao dao = SubscriptionDao.create(TEST_SUBSCRIPTION_ID, TEST_DRIVE_ID, TEST_DELTA_TOKEN, TEST_EXPIRATION);

        assertEquals("subscriptionId should match", TEST_SUBSCRIPTION_ID, dao.getSubscriptionId());
        assertEquals("driveId should match", TEST_DRIVE_ID, dao.getDriveId());
        assertEquals("deltaToken should match", TEST_DELTA_TOKEN, dao.getDeltaToken());
        assertEquals("expirationTimestamp should match", TEST_EXPIRATION, dao.getExpirationTimestamp());
    }

    /**
     * Test create with null values - should not throw
     */
    @Test
    public void testCreate_NullValues() {
        SubscriptionDao dao = SubscriptionDao.create(null, null, null);

        assertNull("subscriptionId should be null", dao.getSubscriptionId());
        assertNull("driveId should be null", dao.getDriveId());
        assertNull("deltaToken should be null", dao.getDeltaToken());
        assertEquals("expirationTimestamp should default to 0", 0, dao.getExpirationTimestamp());
    }

    /**
     * Test create with null values and custom expiration
     */
    @Test
    public void testCreate_NullValuesWithExpiration() {
        SubscriptionDao dao = SubscriptionDao.create(null, null, null, TEST_EXPIRATION);

        assertNull("subscriptionId should be null", dao.getSubscriptionId());
        assertNull("driveId should be null", dao.getDriveId());
        assertNull("deltaToken should be null", dao.getDeltaToken());
        assertEquals("expirationTimestamp should match", TEST_EXPIRATION, dao.getExpirationTimestamp());
    }

    // ========================================
    // IMMUTABLE "WITH" METHOD TESTS
    // ========================================

    /**
     * Test withDeltaToken returns new instance with updated token
     */
    @Test
    public void testWithDeltaToken_ReturnsNewInstance() {
        SubscriptionDao original = SubscriptionDao.create(TEST_SUBSCRIPTION_ID, TEST_DRIVE_ID, TEST_DELTA_TOKEN, TEST_EXPIRATION);
        String newToken = "new-delta-token";

        SubscriptionDao updated = original.withDeltaToken(newToken);

        assertNotSame("Should return a new instance", original, updated);
        assertEquals("New deltaToken should be set", newToken, updated.getDeltaToken());
    }

    /**
     * Test withDeltaToken preserves other fields
     */
    @Test
    public void testWithDeltaToken_PreservesOtherFields() {
        SubscriptionDao original = SubscriptionDao.create(TEST_SUBSCRIPTION_ID, TEST_DRIVE_ID, TEST_DELTA_TOKEN, TEST_EXPIRATION);
        String newToken = "new-delta-token";

        SubscriptionDao updated = original.withDeltaToken(newToken);

        assertEquals("subscriptionId should be preserved", TEST_SUBSCRIPTION_ID, updated.getSubscriptionId());
        assertEquals("driveId should be preserved", TEST_DRIVE_ID, updated.getDriveId());
        assertEquals("expirationTimestamp should be preserved", TEST_EXPIRATION, updated.getExpirationTimestamp());
    }

    /**
     * Test withDeltaToken does not modify original
     */
    @Test
    public void testWithDeltaToken_OriginalUnchanged() {
        SubscriptionDao original = SubscriptionDao.create(TEST_SUBSCRIPTION_ID, TEST_DRIVE_ID, TEST_DELTA_TOKEN, TEST_EXPIRATION);

        original.withDeltaToken("new-token");

        assertEquals("Original deltaToken should be unchanged", TEST_DELTA_TOKEN, original.getDeltaToken());
    }

    /**
     * Test withExpirationTimestamp returns new instance with updated timestamp
     */
    @Test
    public void testWithExpirationTimestamp_ReturnsNewInstance() {
        SubscriptionDao original = SubscriptionDao.create(TEST_SUBSCRIPTION_ID, TEST_DRIVE_ID, TEST_DELTA_TOKEN, TEST_EXPIRATION);
        long newExpiration = 1800000000000L;

        SubscriptionDao updated = original.withExpirationTimestamp(newExpiration);

        assertNotSame("Should return a new instance", original, updated);
        assertEquals("New expirationTimestamp should be set", newExpiration, updated.getExpirationTimestamp());
    }

    /**
     * Test withExpirationTimestamp preserves other fields
     */
    @Test
    public void testWithExpirationTimestamp_PreservesOtherFields() {
        SubscriptionDao original = SubscriptionDao.create(TEST_SUBSCRIPTION_ID, TEST_DRIVE_ID, TEST_DELTA_TOKEN, TEST_EXPIRATION);
        long newExpiration = 1800000000000L;

        SubscriptionDao updated = original.withExpirationTimestamp(newExpiration);

        assertEquals("subscriptionId should be preserved", TEST_SUBSCRIPTION_ID, updated.getSubscriptionId());
        assertEquals("driveId should be preserved", TEST_DRIVE_ID, updated.getDriveId());
        assertEquals("deltaToken should be preserved", TEST_DELTA_TOKEN, updated.getDeltaToken());
    }

    /**
     * Test withExpirationTimestamp does not modify original
     */
    @Test
    public void testWithExpirationTimestamp_OriginalUnchanged() {
        SubscriptionDao original = SubscriptionDao.create(TEST_SUBSCRIPTION_ID, TEST_DRIVE_ID, TEST_DELTA_TOKEN, TEST_EXPIRATION);

        original.withExpirationTimestamp(9999999L);

        assertEquals("Original expirationTimestamp should be unchanged", TEST_EXPIRATION, original.getExpirationTimestamp());
    }

    /**
     * Test withDeltaToken with null value
     */
    @Test
    public void testWithDeltaToken_NullValue() {
        SubscriptionDao original = SubscriptionDao.create(TEST_SUBSCRIPTION_ID, TEST_DRIVE_ID, TEST_DELTA_TOKEN, TEST_EXPIRATION);

        SubscriptionDao updated = original.withDeltaToken(null);

        assertNull("Updated deltaToken should be null", updated.getDeltaToken());
        assertEquals("subscriptionId should be preserved", TEST_SUBSCRIPTION_ID, updated.getSubscriptionId());
    }

    /**
     * Test withExpirationTimestamp with zero value
     */
    @Test
    public void testWithExpirationTimestamp_ZeroValue() {
        SubscriptionDao original = SubscriptionDao.create(TEST_SUBSCRIPTION_ID, TEST_DRIVE_ID, TEST_DELTA_TOKEN, TEST_EXPIRATION);

        SubscriptionDao updated = original.withExpirationTimestamp(0);

        assertEquals("Updated expirationTimestamp should be 0", 0, updated.getExpirationTimestamp());
    }

    // ========================================
    // EDGE CASE TESTS
    // ========================================

    /**
     * Test create with empty strings
     */
    @Test
    public void testCreate_EmptyStrings() {
        SubscriptionDao dao = SubscriptionDao.create("", "", "");

        assertEquals("subscriptionId should be empty", "", dao.getSubscriptionId());
        assertEquals("driveId should be empty", "", dao.getDriveId());
        assertEquals("deltaToken should be empty", "", dao.getDeltaToken());
    }

    /**
     * Test multiple chained with operations
     */
    @Test
    public void testChainedWithOperations() {
        SubscriptionDao original = SubscriptionDao.create(TEST_SUBSCRIPTION_ID, TEST_DRIVE_ID, TEST_DELTA_TOKEN, TEST_EXPIRATION);

        SubscriptionDao updated = original
                .withDeltaToken("new-token")
                .withExpirationTimestamp(9999L);

        assertEquals("subscriptionId should be preserved", TEST_SUBSCRIPTION_ID, updated.getSubscriptionId());
        assertEquals("driveId should be preserved", TEST_DRIVE_ID, updated.getDriveId());
        assertEquals("deltaToken should be updated", "new-token", updated.getDeltaToken());
        assertEquals("expirationTimestamp should be updated", 9999L, updated.getExpirationTimestamp());
    }
}
