# Health Check

## Overview

Verifies the health status of the OneDrive Files extension by performing comprehensive checks on configuration,
authentication, connectivity, and system resources.

## Request Details

| Property          | Value         |
|-------------------|---------------|
| **Area**          | Setup         |
| **Type**          | CHANGE_SYSTEM |
| **Retry Support** | Yes           |

## Input Parameters

This catalog request does not require any input parameters.

## Output Parameters

| Parameter Name          | Type   | Description                                                                                          |
|-------------------------|--------|------------------------------------------------------------------------------------------------------|
| Health Status           | Entity | Detailed health status information including component-level health metrics                          |
| Extension Response Meta | Entity | Metadata about the extension response including timestamps and version information                   |
| Is Healthy              | Yes/No | Overall health status indicator - true if system is operating normally, false if issues are detected |

### Health Status Entity Fields

The Health Status entity may include:

| Field                      | Type      | Description                                                         |
|----------------------------|-----------|---------------------------------------------------------------------|
| Configuration Status       | Text      | Status of extension configuration (Configured/Not Configured)       |
| Authentication Status      | Text      | Status of authentication tokens (Valid/Expired/Invalid)             |
| Connectivity Status        | Text      | Status of connection to Microsoft OneDrive (Connected/Disconnected) |
| Token Expiry               | Date/Time | When the current access token expires                               |
| Last Successful Connection | Date/Time | Timestamp of last successful API call                               |

### Extension Response Meta Entity Fields

| Field             | Type      | Description                                        |
|-------------------|-----------|----------------------------------------------------|
| Extension Version | Text      | Current version of the OneDrive Files extension    |
| Timestamp         | Date/Time | When the health check was performed                |
| Response Time     | Number    | Time taken to complete health check (milliseconds) |

## Validation Rules

| Component      | Validation Rule                                | Health Impact                            |
|----------------|------------------------------------------------|------------------------------------------|
| Configuration  | All required credentials must be present       | Critical - Unhealthy if missing          |
| Authentication | Valid access token or refresh token must exist | Critical - Unhealthy if invalid          |
| Connectivity   | Microsoft Graph API must be reachable          | Critical - Unhealthy if unreachable      |
| Token Status   | Access token should not be expired             | Warning - Healthy if refresh token valid |

## Error Handling

### INPUT_ERROR

**When it occurs**: Invalid request parameters (rare, as no input required)

**Common scenarios**:

- Malformed request

**Response**:

```json
{
  "Is Healthy": false,
  "Health Status": {
    "Error": "Invalid request"
  }
}
```

### LOGIC_ERROR

**When it occurs**: Health check logic detects configuration or authentication issues

**Common scenarios**:

- Extension not configured
- Authentication tokens invalid
- Configuration corrupted

**Response**:

```json
{
  "Is Healthy": false,
  "Health Status": {
    "Configuration Status": "Not Configured",
    "Authentication Status": "Invalid"
  }
}
```

### SYSTEM_ERROR

**When it occurs**: System-level failures during health check

**Common scenarios**:

- Network connectivity issues
- Microsoft OneDrive API unavailable
- Internal extension error
- Resource constraints

**Response**:

```json
{
  "Is Healthy": false,
  "Health Status": {
    "Error": "System error: [details]"
  }
}
```

### Authorization Errors

**When it occurs**: Permission or access issues detected

**Common scenarios**:

- Required permissions not granted
- User account disabled
- Conditional access blocking

**Response**:

```json
{
  "Is Healthy": false,
  "Health Status": {
    "Authentication Status": "Authorization Failed",
    "Error": "Insufficient permissions"
  }
}
```

## Usage Examples

### Example 1: Healthy System

**Input**: None

**Output**:

```
Is Healthy: Yes
Health Status: {
  "Configuration Status": "Configured",
  "Authentication Status": "Valid",
  "Connectivity Status": "Connected",
  "Token Expiry": "2024-10-14T18:30:00Z",
  "Last Successful Connection": "2024-10-14T17:25:00Z"
}
Extension Response Meta: {
  "Extension Version": "3.0.4",
  "Timestamp": "2024-10-14T17:30:00Z",
  "Response Time": 245
}
```

**Interpretation**: All systems operational, no action required

### Example 2: Authentication Issue

**Input**: None

**Output**:

```
Is Healthy: No
Health Status: {
  "Configuration Status": "Configured",
  "Authentication Status": "Expired",
  "Connectivity Status": "Disconnected",
  "Error": "Refresh token expired"
}
Extension Response Meta: {
  "Extension Version": "3.0.4",
  "Timestamp": "2024-10-14T17:30:00Z"
}
```

**Interpretation**: Re-authentication required via [Save Configuration](pages/SaveConfiguration.md)

### Example 3: Not Configured

**Input**: None

**Output**:

```
Is Healthy: No
Health Status: {
  "Configuration Status": "Not Configured",
  "Error": "Extension not configured"
}
Extension Response Meta: {
  "Extension Version": "3.0.4",
  "Timestamp": "2024-10-14T17:30:00Z"
}
```

**Interpretation**: Configuration required via [Save Configuration](pages/SaveConfiguration.md)

## Business Rules

1. **Comprehensive Check**: Validates all critical components (configuration, authentication, connectivity)
2. **Non-Destructive**: Does not modify any data or configuration
3. **Quick Execution**: Designed to complete within seconds
4. **Detailed Reporting**: Provides component-level health information
5. **Overall Status**: Returns single boolean indicating overall health
6. **Metadata Included**: Provides context about extension version and check timing

## Limitations

- **Snapshot in Time**: Health status reflects conditions at time of check only
- **No Predictive Analysis**: Does not predict future failures
- **Basic Connectivity Test**: Does not test all API endpoints
- **No Performance Metrics**: Does not measure throughput or latency in detail
- **No Historical Data**: Does not maintain health history

## Best Practices

1. **Regular Monitoring**: Schedule periodic health checks (e.g., hourly or daily)
2. **Pre-Operation Checks**: Run health check before critical operations
3. **Alert on Failures**: Set up alerts when Is Healthy returns false
4. **Log Results**: Maintain health check logs for trend analysis
5. **Investigate Warnings**: Address issues even if overall status is healthy
6. **Combine with Test Connection**: Use both for comprehensive validation
7. **Monitor Token Expiry**: Track token expiration to plan re-authentication

## Common Use Cases

1. **Scheduled Monitoring**: Automated health checks on regular intervals
2. **Pre-Workflow Validation**: Verify health before executing critical workflows
3. **Troubleshooting**: Diagnose issues when operations fail
4. **Post-Maintenance Verification**: Confirm health after configuration changes
5. **Dashboard Integration**: Display health status in monitoring dashboards
6. **Alerting Systems**: Trigger alerts when health degrades
7. **Compliance Reporting**: Document system health for audit purposes

## Related Catalog Requests

- [Test Connection](pages/TestConnection.md) - Basic connectivity test
- [Save Configuration](pages/SaveConfiguration.md) - Configure extension
- [Extension Configuration](pages/ExtensionConfiguration.md) - Configuration guide
- [Authentication](pages/Authentication.md) - Authentication details

## Technical Implementation

### Helper Classes

- **OnedriveSetUpService**: Orchestrates health check logic
- **HealthCheckService**: Performs individual component checks
- **TokenManager**: Validates token status
- **ConnectivityChecker**: Tests API connectivity

### Telemetry Metrics

The following metrics are tracked:

- `onedrive.setup.health_check.healthy` - Successful health checks
- `onedrive.setup.health_check.unhealthy` - Failed health checks
- `onedrive.setup.health_check.duration` - Time taken to complete check
- `onedrive.setup.health_check.component_failures` - Specific component failures

### Health Check Flow

1. **Configuration Check**
    - Verify all required credentials are present
    - Validate credential formats

2. **Authentication Check**
    - Check access token validity
    - Check refresh token validity
    - Determine token expiration time

3. **Connectivity Check**
    - Attempt API call to Microsoft Graph
    - Measure response time
    - Validate API response

4. **Aggregate Results**
    - Combine component statuses
    - Determine overall health
    - Build response entity

5. **Return Results**
    - Populate Health Status entity
    - Populate Extension Response Meta
    - Set Is Healthy flag

## Troubleshooting

### Issue: Is Healthy = No, "Not Configured"

**Possible Causes**:

- Extension has not been configured
- Configuration was cleared

**Solutions**:

1. Execute [Save Configuration](pages/SaveConfiguration.md)
2. Provide all required credentials
3. Verify configuration saved successfully

### Issue: Is Healthy = No, "Authentication Expired"

**Possible Causes**:

- Refresh token expired (typically after 90 days)
- Credentials revoked in Azure AD
- User password changed

**Solutions**:

1. Re-execute [Save Configuration](pages/SaveConfiguration.md)
2. Complete authentication flow again
3. Verify credentials in Azure Portal
4. Check user account status

### Issue: Is Healthy = No, "Connectivity Failed"

**Possible Causes**:

- Network connectivity issues
- Firewall blocking Microsoft Graph API
- Microsoft services unavailable
- DNS resolution failure

**Solutions**:

1. Verify internet connectivity
2. Check firewall rules
3. Test access to `graph.microsoft.com`
4. Check Microsoft 365 service health
5. Review network logs

### Issue: Is Healthy = No, "Insufficient Permissions"

**Possible Causes**:

- Required Microsoft Graph permissions not granted
- Admin consent not provided
- User lost OneDrive access

**Solutions**:

1. Verify permissions in Azure AD
2. Ensure admin consent granted
3. Check user OneDrive license
4. Review [Creating Microsoft App](pages/CreatingMicrosoftApp.md)

### Issue: Health Check Times Out

**Possible Causes**:

- Slow network connection
- Microsoft API delays
- System resource constraints

**Solutions**:

1. Retry health check
2. Check system resources
3. Verify network performance
4. Check Microsoft service status

## Health Status Interpretation

### Healthy Status

**Indicators**:

- Is Healthy: Yes
- Configuration Status: Configured
- Authentication Status: Valid
- Connectivity Status: Connected

**Action**: No action required, system operating normally

### Warning Status

**Indicators**:

- Is Healthy: Yes (but warnings present)
- Access token expired but refresh token valid
- Slow response times

**Action**: Monitor closely, plan maintenance

### Unhealthy Status

**Indicators**:

- Is Healthy: No
- One or more critical components failing

**Action**: Immediate investigation and remediation required

## Monitoring Integration

### Recommended Monitoring Setup

1. **Frequency**: Check health every 15-60 minutes
2. **Alerting**: Alert when Is Healthy = No for 2+ consecutive checks
3. **Logging**: Log all health check results
4. **Trending**: Track health metrics over time
5. **Dashboard**: Display current health status prominently

### Sample Monitoring Query

```
IF Is Healthy = No THEN
  Send alert to operations team
  Log detailed Health Status
  Trigger automated remediation if configured
END IF
```

## See Also

- [Test Connection](pages/TestConnection.md) - Basic connectivity test
- [Save Configuration](pages/SaveConfiguration.md) - Configure extension
- [Authentication](pages/Authentication.md) - Authentication and token management
- [Extension Configuration](pages/ExtensionConfiguration.md) - Configuration guide

