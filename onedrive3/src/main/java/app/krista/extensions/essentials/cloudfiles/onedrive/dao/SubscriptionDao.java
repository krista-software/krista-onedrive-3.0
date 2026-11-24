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

/**
 * Data Access Object for managing OneDrive subscription information.
 * Stores subscription details including IDs and delta tokens.
 *
 * This class is effectively immutable from the outside - all fields are private
 * with public getters only. The no-arg constructor is package-private for GSON
 * deserialization compatibility.
 */
public class SubscriptionDao {
    private String subscriptionId;
    private String driveId;
    private String deltaToken;
    private long expirationTimestamp; // timestamp when subscription expires (milliseconds since epoch)

    /**
     * Creates a new SubscriptionDao instance with the specified parameters.
     *
     * @param subscriptionId The unique identifier for the subscription
     * @param driveId        The identifier for the OneDrive drive
     * @param deltaToken     Token used for tracking changes since last synchronization
     * @return A new SubscriptionDao instance populated with the provided values
     */
    public static SubscriptionDao create(String subscriptionId, String driveId, String deltaToken) {
        SubscriptionDao subscriptionDao = new SubscriptionDao();
        subscriptionDao.subscriptionId = subscriptionId;
        subscriptionDao.driveId = driveId;
        subscriptionDao.deltaToken = deltaToken;
        subscriptionDao.expirationTimestamp = 0;
        return subscriptionDao;
    }

    /**
     * Creates a new SubscriptionDao instance with expiration tracking.
     *
     * @param subscriptionId      The unique identifier for the subscription
     * @param driveId             The identifier for the OneDrive drive
     * @param deltaToken          Token used for tracking changes since last synchronization
     * @param expirationTimestamp Timestamp when subscription expires (milliseconds since epoch)
     * @return A new SubscriptionDao instance populated with the provided values
     */
    public static SubscriptionDao create(String subscriptionId, String driveId, String deltaToken, long expirationTimestamp) {
        SubscriptionDao subscriptionDao = new SubscriptionDao();
        subscriptionDao.subscriptionId = subscriptionId;
        subscriptionDao.driveId = driveId;
        subscriptionDao.deltaToken = deltaToken;
        subscriptionDao.expirationTimestamp = expirationTimestamp;
        return subscriptionDao;
    }

    public String getSubscriptionId() {
        return subscriptionId;
    }

    public String getDriveId() {
        return driveId;
    }

    public String getDeltaToken() {
        return deltaToken;
    }

    public long getExpirationTimestamp() {
        return expirationTimestamp;
    }

    /**
     * Returns a new SubscriptionDao with the updated delta token.
     * Preserves immutability by creating a new instance.
     *
     * @param newDeltaToken the new delta token value
     * @return a new SubscriptionDao instance with the updated delta token
     */
    public SubscriptionDao withDeltaToken(String newDeltaToken) {
        return create(this.subscriptionId, this.driveId, newDeltaToken, this.expirationTimestamp);
    }

    /**
     * Returns a new SubscriptionDao with the updated expiration timestamp.
     * Preserves immutability by creating a new instance.
     *
     * @param newExpirationTimestamp the new expiration timestamp value
     * @return a new SubscriptionDao instance with the updated expiration timestamp
     */
    public SubscriptionDao withExpirationTimestamp(long newExpirationTimestamp) {
        return create(this.subscriptionId, this.driveId, this.deltaToken, newExpirationTimestamp);
    }
}
