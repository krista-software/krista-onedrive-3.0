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

import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link OnedriveSubscription#calculateExpirationTimestamp()}.
 */
public class OnedriveSubscriptionTest {

    @Test
    public void testCalculateExpirationTimestamp_returnsValueGreaterThanCurrentTime() {
        long now = System.currentTimeMillis();
        long expiration = OnedriveSubscription.calculateExpirationTimestamp();

        assertTrue("Expiration timestamp should be greater than current time",
                expiration > now);
    }

    @Test
    public void testCalculateExpirationTimestamp_isApproximately28DaysInFuture() {
        long now = System.currentTimeMillis();
        long expiration = OnedriveSubscription.calculateExpirationTimestamp();

        long expected28Days = TimeUnit.DAYS.toMillis(28);
        long actualDelta = expiration - now;

        // Allow 1-second tolerance for execution time
        long toleranceMillis = 1000L;
        assertTrue("Expiration should be approximately 28 days from now (within 1s tolerance)",
                Math.abs(actualDelta - expected28Days) < toleranceMillis);
    }

    @Test
    public void testCalculateExpirationTimestamp_twoSuccessiveCallsReturnCloseValues() {
        long first = OnedriveSubscription.calculateExpirationTimestamp();
        long second = OnedriveSubscription.calculateExpirationTimestamp();

        // Two successive calls should produce values within 100ms of each other
        long delta = Math.abs(second - first);
        assertTrue("Two successive calls should return values within 100ms of each other, but delta was " + delta,
                delta < 100L);
    }
}
