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

import app.krista.extensions.essentials.cloudfiles.onedrive.impl.connectors.GraphServiceClientProvider;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.util.Constants;
import app.krista.ksdk.context.AuthorizationContext;
import com.microsoft.graph.core.ClientException;
import com.microsoft.graph.models.Subscription;
import com.microsoft.graph.requests.SubscriptionCollectionPage;
import com.microsoft.graph.requests.SubscriptionCollectionRequestBuilder;
import com.microsoft.graph.serializer.OffsetDateTimeSerializer;
import org.jetbrains.annotations.NotNull;
import org.jvnet.hk2.annotations.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Objects;


/**
 * This class is used for OneDrive File Alerts
 */
@Service
public class OnedriveSubscription {
    private final AuthorizationContext authorizationContext;
    private static final Logger LOGGER = LoggerFactory.getLogger(OnedriveSubscription.class);

    @Inject
    protected OnedriveSubscription(AuthorizationContext authorizationContext) {
        this.authorizationContext = authorizationContext;
    }

    /**
     * This method will create a mail alert subscription
     *
     * @param routingUrl routing url to get the mail alert
     * @param provider   {@link GraphServiceClientProvider} object to get the subscriptions from microsoft
     */
    public static void createSubscription(String routingUrl, GraphServiceClientProvider provider) {
        int maxRetries = 3;
        int retryDelaySeconds = 5;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                Subscription subscription = prepareSubscription(routingUrl);
                SubscriptionCollectionPage collectionPage = provider.getGraphServiceClientForAdmin().subscriptions().buildRequest().get();
                if (isSubscribed(collectionPage, subscription)) return;
                provider.getGraphServiceClientForAdmin().subscriptions().buildRequest().post(subscription);
                return;
            } catch (ClientException | ParseException e) {
                if (attempt == maxRetries) {
                    throw new RuntimeException("Failed to create subscription after " + maxRetries + " attempts", e);
                }
                try {
                    Thread.sleep(retryDelaySeconds * 1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted during retry delay", ie);
                }
            }
        }
    }

    public void createSubscriptionFromClientSide(String routingUrl, GraphServiceClientProvider provider) {
        int maxRetries = 3;
        int retryDelaySeconds = 5;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                Subscription subscription = prepareSubscription(routingUrl);
                SubscriptionCollectionPage collectionPage = provider.getGraphServiceClientForUser(false, authorizationContext.getAuthorizedAccount().getAccountId()).subscriptions().buildRequest().get();
                if (isSubscribed(collectionPage, subscription)) return;
                provider.getGraphServiceClientForUser(false, authorizationContext.getAuthorizedAccount().getAccountId()).subscriptions().buildRequest().post(subscription);
                return;
            } catch (ClientException | ParseException | IOException e) {
                if (attempt == maxRetries) {
                    throw new RuntimeException("Failed to create subscription after " + maxRetries + " attempts", e);
                }
                try {
                    Thread.sleep(retryDelaySeconds * 1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted during retry delay", ie);
                }
            }
        }
    }

    private static boolean isSubscribed(SubscriptionCollectionPage collectionPage, Subscription subscription) {
        while (collectionPage != null && !collectionPage.getCurrentPage().isEmpty()) {
            for (Subscription oldSubscription : collectionPage.getCurrentPage()) {
                if (Objects.requireNonNull(oldSubscription.notificationUrl).equals(subscription.notificationUrl)) {
                    LOGGER.info("Subscription already exists");
                    return true;
                }
            }
            SubscriptionCollectionRequestBuilder nextPage = collectionPage.getNextPage();
            if (nextPage != null) {
                collectionPage = nextPage.buildRequest().get();
            } else {
                break;
            }
        }
        return false;
    }

    /**
     * This method is used to set subscription parameters for creating a mail alert subscription
     *
     * @param routingUrl routing url to get the mail alert
     * @return {@link Subscription} object with subscription parameters
     */
    @NotNull
    private static Subscription prepareSubscription(String routingUrl) throws ParseException {
        Subscription subscription = new Subscription();
        subscription.changeType = Constants.CHANGE_TYPE;
        subscription.notificationUrl = routingUrl + Constants.REST_ONEDRIVE_MAIL_NOTIFICATION;
        subscription.lifecycleNotificationUrl = routingUrl + Constants.REST_ONEDRIVE_LIFECYCLE_NOTIFICATION;
        subscription.resource = Constants.ME_ONEDRIVE_FOLDERS_ALERT_MESSAGES;
        long daysInMillis = System.currentTimeMillis() + (86000L * 1000L * 28L);
        Date date = new Date(daysInMillis);
        DateFormat dateFormat = new SimpleDateFormat(Constants.YYYY_MM_DD_T_HH_MM_SS_SSSSSSS_Z);
        subscription.expirationDateTime = OffsetDateTimeSerializer.deserialize(dateFormat.format(date));
        return subscription;
    }

    /**
     * This method is used to set the expiry date for the existing subscription
     *
     * @param provider       {@link GraphServiceClientProvider} object to get the subscriptions list
     * @param subscriptionId id of the existing subscription
     */
    public static void renewSubscription(GraphServiceClientProvider provider, String subscriptionId) {
        try {
            Subscription subscription = new Subscription();
            long daysInMillis = System.currentTimeMillis() + (86000L * 1000L * 28L);
            Date date = new Date(daysInMillis);
            DateFormat dateFormat = new SimpleDateFormat(Constants.YYYY_MM_DD_T_HH_MM_SS_SSSSSSS_Z);
            subscription.expirationDateTime = OffsetDateTimeSerializer.deserialize(dateFormat.format(date));
            provider.getGraphServiceClientForAdmin().subscriptions(subscriptionId).buildRequest().patch(subscription);
        } catch (ClientException | ParseException cause) {
            throw new RuntimeException(cause);
        }
    }

}
