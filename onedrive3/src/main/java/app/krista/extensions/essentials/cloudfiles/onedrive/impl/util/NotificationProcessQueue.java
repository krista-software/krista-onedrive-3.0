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

import app.krista.extension.executor.Invoker;
import app.krista.extension.request.RoutingInfo;
import app.krista.extension.request.protos.http.HttpProtocol;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.OnedriveSubscription;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.connectors.GraphServiceClientProviderFactory;
import com.google.gson.JsonArray;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NotificationProcessQueue {

    private final GraphServiceClientProviderFactory providerFactory;
    private final Invoker invoker;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    public NotificationProcessQueue(GraphServiceClientProviderFactory providerFactory, Invoker invoker) {
        this.providerFactory = providerFactory;
        this.invoker = invoker;
    }

    public void add(Notification notification) {
        executorService.submit(() -> this.offer(notification));
    }

    private void offer(Notification notification) {
        JsonArray array = notification.getNotificationObject().get(Constants.VALUE).getAsJsonArray();
        for (int i = 0; i < array.size(); i++) {
            String subscriptionId = array.get(i).getAsJsonObject().get(Constants.SUBSCRIPTION_ID).getAsString();
            String lifecycleEvent = array.get(i).getAsJsonObject().get(Constants.LIFECYCLE_EVENT).getAsString();
            if (lifecycleEvent.equalsIgnoreCase(Constants.SUBSCRIPTION_REMOVED)) {
                OnedriveSubscription.createSubscription(invoker.getRoutingInfo().getRoutingURL(HttpProtocol.PROTOCOL_NAME, RoutingInfo.Type.APPLIANCE), providerFactory.create());
            } else if (lifecycleEvent.equalsIgnoreCase(Constants.REAUTHORIZATION_REQUIRED)) {
                OnedriveSubscription.renewSubscription(providerFactory.create(), subscriptionId);
            }
        }
    }
}
