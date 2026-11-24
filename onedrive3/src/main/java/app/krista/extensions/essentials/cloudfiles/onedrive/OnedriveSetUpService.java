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

package app.krista.extensions.essentials.cloudfiles.onedrive;

import app.krista.extension.executor.ExtensionResponse;
import app.krista.extension.executor.ExtensionResponseBuilder;
import app.krista.extensions.essentials.cloudfiles.onedrive.catalog.entities.ExtensionResponseMeta;
import app.krista.extensions.essentials.cloudfiles.onedrive.catalog.entities.HealthStatus;
import app.krista.extensions.essentials.cloudfiles.onedrive.dao.SubscriptionDao;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.OnedriveSubscription;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.connectors.GraphServiceClientProvider;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.connectors.GraphServiceClientProviderFactory;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.stores.SubscriptionStorageStore;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.util.FileChangeResourceUtil;
import app.krista.ksdk.telemetry.TelemetryMetrics;
import com.microsoft.graph.models.Subscription;
import com.microsoft.graph.requests.DriveItemDeltaCollectionPage;
import org.jvnet.hk2.annotations.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.validation.constraints.NotNull;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.ThreadMXBean;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

import static app.krista.extensions.essentials.cloudfiles.onedrive.impl.util.Constants.SUCCESS;

/**
 * Service responsible for setting up and managing OneDrive connections and health monitoring.
 *
 * <p>This service provides functionality for:
 * <ul>
 *   <li>Validating OneDrive connections from both server and client side</li>
 *   <li>Testing connection integrity</li>
 *   <li>Managing connection attributes and authentication</li>
 *   <li>Performing comprehensive health checks with system metrics</li>
 * </ul>
 *
 * @author Krista Extensions Team
 * @since 1.0
 */
@Service
public class OnedriveSetUpService {

    private static final String ONEDRIVE_SYSTEM_STATUS = "One drive system status";
    private static final String HEALTH_CHECK_SUCCESS_COUNT = "HealthCheck successCount";
    private static final String HEALTH_CHECK_LATENCY_MS = "HealthCheck latencyMs";
    private static final String HEALTH_CHECK_ERROR_TYPE = "HealthCheck errorType";
    private static final String HEALTH_CHECK_FAILURE_COUNT = "HealthCheck failureCount";
    private static final String SYSTEM_ERROR = "System Error";
    private static final String HEALTH_CHECK_REQUEST_COUNT = "HealthCheck requestCount";
    private static final Logger LOGGER = LoggerFactory.getLogger(OnedriveSetUpService.class);

    private final ContextService contextService;
    private final OnedriveAttributes onedriveAttributes;
    private final GraphServiceClientProviderFactory clientProviderFactory;
    private final SubscriptionStorageStore subscriptionStorageStore;
    private final TelemetryMetrics telemetryMetrics;

    public static final Instant START_TIME = Instant.now();

    @Inject
    public OnedriveSetUpService(ContextService contextService, OnedriveAttributes onedriveAttributes,
                                GraphServiceClientProviderFactory clientProviderFactory,
                                SubscriptionStorageStore subscriptionStorageStore,
                                TelemetryMetrics telemetryMetrics) {
        this.contextService = contextService;
        this.onedriveAttributes = onedriveAttributes;
        this.clientProviderFactory = clientProviderFactory;
        this.subscriptionStorageStore = subscriptionStorageStore;
        this.telemetryMetrics = telemetryMetrics;
    }

    /**
     * Tests OneDrive connection by attempting to retrieve root drive children.
     * Uses admin or user credentials based on the source of the request.
     *
     * @param fromStudio true if request originates from Krista Studio, false for client requests
     * @throws IOException if connection test fails or network issues occur
     */
    public void testConnection(boolean fromStudio) throws IOException {
        if (onedriveAttributes.getMailId() != null && !onedriveAttributes.getMailId().isBlank()) {
            if (fromStudio) {
                clientProviderFactory.create(onedriveAttributes)
                        .getGraphServiceClientForAdmin()
                        .me().drive().root().children().buildRequest().get();
            } else {
                String accountId = contextService.getAccountId();
                clientProviderFactory.create(onedriveAttributes)
                        .getGraphServiceClientForUser(false, accountId)
                        .me().drive().root().children().buildRequest().get();
            }
        }
    }

    /**
     * Verifies that the current invoker has delegated OneDrive access before a user-scoped tool runs.
     * Always uses useEmail=false because this is called only in MCP/invokeAsUser context where
     * the accountId is a wsContact ID, not an email address.
     */
    public void verifyUserAuthorization() throws IOException {
        String accountId = contextService.getAccountId();
        clientProviderFactory.create(onedriveAttributes)
                .getGraphServiceClientForUser(false, accountId)
                .me().buildRequest().get();
    }

    /**
     * Updates OneDrive attributes when configuration changes occur.
     *
     * @param oldAttributes previous attribute values (not currently used)
     * @param newAttributes new attribute values to apply
     */
    public void attributesUpdated(Map<String, Object> oldAttributes, Map<String, Object> newAttributes) {
        onedriveAttributes.update(newAttributes);
    }

    /**
     * Gets the current OneDrive attributes configuration.
     *
     * @return the configured OnedriveAttributes instance
     */
    public OnedriveAttributes getOnedriveAttributes() {
        return onedriveAttributes;
    }

    /**
     * Creates a subscription and initializes file metadata using an already-authenticated provider.
     * Used when a valid token is already cached (e.g. after saveCredentials in the auth tab).
     *
     * @param provider authenticated GraphServiceClientProvider
     */
    public void initializeSubscriptionWithProvider(GraphServiceClientProvider provider) {
        String routingUrl = contextService.getRoutingUrl();
        DriveItemDeltaCollectionPage page = provider.getGraphServiceClientForAdmin().me()
                .drive().root().delta().buildRequest().get();
        Subscription createdSubscription = OnedriveSubscription.createSubscription(routingUrl, provider);
        if (createdSubscription != null) {
            LOGGER.info("Subscription created successfully during auth-tab save - subscriptionId: {}", createdSubscription.id);
        } else {
            LOGGER.info("Subscription already exists, skipping creation during auth-tab save");
        }
        storeSubscriptionMetadata(routingUrl, createdSubscription);
        FileChangeResourceUtil.addFileMetaData(routingUrl, page);
    }

    /**
     * Stores subscription metadata in the SubscriptionStorageStore for tracking and cleanup.
     *
     * @param routingUrl   the routing URL used as the path key
     * @param subscription the created subscription (may be null if already subscribed)
     */
    private void storeSubscriptionMetadata(String routingUrl, Subscription subscription) {
        if (subscription != null && subscription.id != null) {
            long expirationTimestamp = OnedriveSubscription.calculateExpirationTimestamp();
            SubscriptionDao subscriptionDao = SubscriptionDao.create(subscription.id, null, null, expirationTimestamp);
            subscriptionStorageStore.addFileMetaData(subscription.id, subscriptionDao);
            subscriptionStorageStore.addSubscriptionID(routingUrl, subscription.id);
            LOGGER.info("Stored subscription {} with expiration timestamp: {}", subscription.id, expirationTimestamp);
        }
    }

    /**
     * Performs a comprehensive health check of the MS Excel extension system.
     * This method follows the same pattern as the Krista Appliance HealthCheck service.
     *
     * @return ExtensionResponse containing health status and detailed system metrics
     */
    public ExtensionResponse checkHealth() {
        long startTime = System.currentTimeMillis();
        try {
            telemetryMetrics.incrementCounter(HEALTH_CHECK_REQUEST_COUNT);
            Map<String, Object> response = healthCheckData(startTime);
            return new ExtensionResponseBuilder().success(response).build();
        } catch (Exception e) {
            long endTime = System.currentTimeMillis();
            telemetryMetrics.incrementCounter(HEALTH_CHECK_FAILURE_COUNT);
            telemetryMetrics.incrementCounter(HEALTH_CHECK_ERROR_TYPE, 1,
                    Map.of("error_type", e.getClass().getSimpleName()));

            ExtensionResponseMeta extensionResponseEntity = new ExtensionResponseMeta();
            extensionResponseEntity.message = "Failed to perform health check: " + e.getMessage();
            extensionResponseEntity.technicalDetailedErrorReport = e.getMessage();
            extensionResponseEntity.responseType = SYSTEM_ERROR;
            extensionResponseEntity.timeTakenInSeconds = (endTime - startTime) / 1000.0;

            Map<String, Object> responseData = new HashMap<>();
            responseData.put("Is Healthy", false);
            responseData.put("Extension Response Meta", extensionResponseEntity);
            return new ExtensionResponseBuilder().success(responseData).build();
        }
    }

    /**
     * Collects comprehensive health data including system metrics and configuration validation.
     */
    private Map<String, Object> healthCheckData(long startTime) {
        Map<String, Object> healthData = new HashMap<>();

        Result result = getResult();

        double cpuUsage = getApproximateCpuUsage();
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        int threadCount = threadBean.getThreadCount();

        Duration uptime = Duration.between(START_TIME, Instant.now());
        double uptimeHours = uptime.toMillis() / (1000.0 * 60 * 60);
        uptimeHours = Math.round(uptimeHours * 100.0) / 100.0;

        String systemStatus = determineSystemStatus(cpuUsage, result.usedMemory(), result.maxMemory());

        telemetryMetrics.incrementCounter(ONEDRIVE_SYSTEM_STATUS, 1, Map.of("status", systemStatus));

        long endTime = System.currentTimeMillis();
        telemetryMetrics.incrementCounter(HEALTH_CHECK_SUCCESS_COUNT);
        telemetryMetrics.recordDuration(HEALTH_CHECK_LATENCY_MS, endTime - startTime);
        String lastHealthCheckTime = showTime();
        HealthStatus healthStatus = getHealthStatus((double) result.usedMemory(), (double) result.availableMemory(), (double) result.maxMemory(), cpuUsage, threadCount, uptimeHours, systemStatus, lastHealthCheckTime);
        // Build response
        boolean isHealthy = systemStatus.equals("HEALTHY");
        healthData.put("Health Status", healthStatus);
        healthData.put("Is Healthy", isHealthy);

        ExtensionResponseMeta extensionResponseEntity = new ExtensionResponseMeta();
        extensionResponseEntity.message = isHealthy ? "Health check completed successfully" : "Health check completed with issues";
        extensionResponseEntity.technicalDetailedErrorReport = "";
        extensionResponseEntity.responseType = SUCCESS;
        extensionResponseEntity.timeTakenInSeconds = (endTime - startTime) / 1000.0;
        healthData.put("Extension Response Meta", extensionResponseEntity);

        return healthData;
    }

    @NotNull
    private static Result getResult() {
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        long usedMemory = memoryBean.getHeapMemoryUsage().getUsed() / (1024 * 1024);
        long maxMemory = memoryBean.getHeapMemoryUsage().getMax() / (1024 * 1024);
        long availableMemory = maxMemory - usedMemory;
        return new Result(usedMemory, maxMemory, availableMemory);
    }

    private record Result(long usedMemory, long maxMemory, long availableMemory) {
    }

    private static HealthStatus getHealthStatus(double usedMemory, double availableMemory, double maxMemory, double cpuUsage, double threadCount, double uptimeHours, String systemStatus, String lastHealthCheckTime) {
        return new HealthStatus().buildWithMetrics(
                "One Drive Files",
                usedMemory,
                availableMemory,
                maxMemory,
                cpuUsage,
                threadCount,
                uptimeHours,
                systemStatus,
                lastHealthCheckTime
        );
    }

    /**
     * Returns the current date in yyyy-MM-dd format for health check timestamps.
     *
     * @return current date formatted as yyyy-MM-dd string
     */
    public String showTime() {
        long currentMillis = System.currentTimeMillis();

        return Instant.ofEpochMilli(currentMillis)
                .atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    }


    /**
     * Determines the overall system status based on various metrics.
     * This method follows the same logic as the Krista Appliance implementation.
     */
    private String determineSystemStatus(double cpuUsage, long usedMemory, long maxMemory) {
        double memoryUsagePercent = (double) usedMemory / maxMemory * 100;

        if (cpuUsage > 90 || memoryUsagePercent > 90) {
            return "UNHEALTHY";
        }
        if (cpuUsage > 75 || memoryUsagePercent > 75) {
            return "DEGRADED (SLOW RESPONSE)";
        }

        return "HEALTHY";
    }

    /**
     * Gets an approximate CPU usage percentage.
     * This method follows the same implementation as the Krista Appliance service.
     */
    private double getApproximateCpuUsage() {
        try {
            OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
            if (osBean instanceof com.sun.management.OperatingSystemMXBean sunOsBean) {

                double cpuUsage = sunOsBean.getProcessCpuLoad() * 100;

                if (cpuUsage >= 0 && cpuUsage <= 100) {
                    return Math.round(cpuUsage * 100.0) / 100.0;
                }

                // Try system CPU load as fallback
                double systemCpuUsage = sunOsBean.getCpuLoad() * 100;
                if (systemCpuUsage >= 0 && systemCpuUsage <= 100) {
                    return Math.round(systemCpuUsage * 100.0) / 100.0;
                }
            }

            // Fallback to load average approach
            double load = osBean.getSystemLoadAverage();
            int processors = Runtime.getRuntime().availableProcessors();

            if (load < 0) {
                throw new RuntimeException("System load average not available - CPU monitoring failed");
            }

            // Convert load average to percentage based on available processors
            double usage = (load / processors) * 100;
            return Math.min(Math.round(usage * 100.0) / 100.0, 100.0); // Cap at 100%

        } catch (Exception e) {
            LOGGER.error("CPU monitoring failed", e);
            throw new RuntimeException("CPU monitoring unavailable: " + e.getMessage(), e);
        }
    }

}
