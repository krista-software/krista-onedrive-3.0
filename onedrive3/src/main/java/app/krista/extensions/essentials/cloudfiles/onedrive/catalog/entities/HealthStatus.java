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

package app.krista.extensions.essentials.cloudfiles.onedrive.catalog.entities;


import app.krista.extension.impl.anno.Attribute;
import app.krista.extension.impl.anno.Domain;
import app.krista.extension.impl.anno.Entity;
import app.krista.extension.impl.anno.Field;

@Domain(id = "catEntryDomain_30c63e77-37f2-4a1d-afd8-f6bfda07ad57",
        name = "Cloud Files",
        ecosystemId = "catEntryEcosystem_84b53163-327b-4b1b-8c96-9334d292f9f5",
        ecosystemName = "Essentials",
        ecosystemVersion = "6a59392b-ccda-470e-a2e7-931c29670634")
@Entity(name = "Health Status", id = "localDomainEntity_611ca624-bc78-442b-b73f-8281051a95a0", primaryKey = "Extension Name", supportStore = false)
public class HealthStatus {

    @Field.Text(name = "Extension Name", required = false, attributes = {@Attribute(name = "visualWidth", value = "S")})
    public String extensionName;

    @Field(name = "Current Memory Usage MB", type = "Number", required = false, attributes = {@Attribute(name = "visualWidth", value = "S")})
    public Double currentMemoryUsageMB;

    @Field(name = "Available Memory MB", type = "Number", required = false, attributes = {@Attribute(name = "visualWidth", value = "S")})
    public Double availableMemoryMB;

    @Field(name = "Total Memory MB", type = "Number", required = false, attributes = {@Attribute(name = "visualWidth", value = "S")})
    public Double totalMemoryMB;

    @Field(name = "CPU Usage Percentage", type = "Number", required = false, attributes = {@Attribute(name = "visualWidth", value = "S")})
    public Double cPUUsagePercentage;

    @Field(name = "Active Threads", type = "Number", required = false, attributes = {@Attribute(name = "visualWidth", value = "S")})
    public Double activeThreads;

    @Field(name = "Uptime Hours", type = "Number", required = false, attributes = {@Attribute(name = "visualWidth", value = "S")})
    public Double uptimeHours;

    @Field.Text(name = "System Status", required = false, attributes = {@Attribute(name = "visualWidth", value = "S")})
    public String systemStatus;

    @Field.Text(name = "Last Health Check Time", required = false, attributes = {@Attribute(name = "visualWidth", value = "S")})
    public String lastHealthCheckTime;

    /**
     * Builder method to populate HealthStatus with system metrics.
     * This method allows fluent configuration of all health status fields.
     *
     * @param extensionName   Name of the extension
     * @param usedMemory      Used memory in MB
     * @param availableMemory Available memory in MB
     * @param maxMemory       Total memory in MB
     * @param cpuUsage        CPU usage percentage
     * @param threadCount     Number of active threads
     * @param uptimeHours     Uptime in hours
     * @param systemStatus    Overall system status
     * @return This HealthStatus instance for method chaining
     */
    public HealthStatus buildWithMetrics(String extensionName, double usedMemory, double availableMemory,
                                         double maxMemory, double cpuUsage, double threadCount,
                                         double uptimeHours, String systemStatus, String lastHealthCheckTime) {
        this.extensionName = extensionName;
        this.currentMemoryUsageMB = usedMemory;
        this.availableMemoryMB = availableMemory;
        this.totalMemoryMB = maxMemory;
        this.cPUUsagePercentage = cpuUsage;
        this.activeThreads = threadCount;
        this.uptimeHours = uptimeHours;
        this.systemStatus = systemStatus;
        this.lastHealthCheckTime = lastHealthCheckTime;
        return this;
    }
}