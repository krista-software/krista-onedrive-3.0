# Test Connection

## Overview

Tests the OneDrive Files connection to verify that the extension is properly configured and can successfully communicate
with Microsoft OneDrive services.

## Request Details

| Property          | Value         |
|-------------------|---------------|
| **Area**          | Setup         |
| **Type**          | CHANGE_SYSTEM |
| **Retry Support** | Yes           |

## Input Parameters

This catalog request does not require any input parameters.

## Output Parameters

| Parameter Name | Type   | Description                                                                                           |
|----------------|--------|-------------------------------------------------------------------------------------------------------|
| Is Successful  | Yes/No | Indicates whether the connection test was successful and OneDrive is accessible                       |
| Message        | Text   | Detailed message about the connection test result, including any error information if the test failed |

## Validation Rules

| Field          | Validation Rule                                | Error Message                                                |
|----------------|------------------------------------------------|--------------------------------------------------------------|
| Configuration  | Extension must be configured                   | "Extension not configured. Please save configuration first." |
| Authentication | Valid access token or refresh token must exist | "Authentication required. Please save configuration."        |
| Network        | Microsoft OneDrive API must be reachable       | "Failed to connect: Network error"                           |

## Error Handling

### INPUT_ERROR

**When it occurs**: Extension is not properly configured

**Common scenarios**:

- Extension configuration has not been saved
- Required credentials are missing

**Response**:

```json
{
  "Is Successful": false,
  "Message": "Extension not configured. Please save configuration first."
}
```

### LOGIC_ERROR

**When it occurs**: Configuration exists but authentication fails

**Common scenarios**:

- Access token expired and refresh token is invalid
- Credentials were revoked or changed in Azure AD
- User account is disabled or locked

**Response**:

```json
{
  "Is Successful": false,
  "Message": "Failed to connect: Authentication failed"
}
```

### SYSTEM_ERROR

**When it occurs**: System-level failures during connection test

**Common scenarios**:

- Network connectivity issues
- Microsoft OneDrive API is unavailable
- Timeout during API call
- Internal extension error

**Response**:

```json
{
  "Is Successful": false,
  "Message": "Failed to connect: [error details]"
}
```

### Authorization Errors

**When it occurs**: Permission or access issues

**Common scenarios**:

- Required Microsoft Graph API permissions not granted
- User does not have OneDrive license
- Conditional access policies blocking access
- Tenant restrictions preventing access

**Response**:

```json
{
  "Is Successful": false,
  "Message": "Failed to connect: Insufficient permissions"
}
```

## Usage Examples

### Example 1: Successful Connection Test

**Input**: None

**Output**:

```
Is Successful: Yes
Message: Connection Successful
```

**Interpretation**: Extension is properly configured and can access OneDrive

### Example 2: Authentication Failure

**Input**: None

**Output**:

```
Is Successful: No
Message: Failed to connect: Authentication failed
```

**Interpretation**: Credentials are invalid or expired; re-validate configuration

### Example 3: Network Error

**Input**: None

**Output**:

```
Is Successful: No
Message: Failed to connect: Network error - Unable to reach Microsoft services
```

**Interpretation**: Network connectivity issue; check internet connection

## Business Rules

1. **Configuration Prerequisite**: [Save Configuration](pages/SaveConfiguration.md) must be executed successfully before
   testing connection
2. **Token Validation**: Extension attempts to refresh access token if expired before testing
3. **API Call**: Makes a lightweight API call to Microsoft Graph to verify connectivity
4. **No Data Modification**: Test does not create, modify, or delete any OneDrive data
5. **Quick Validation**: Designed to complete within a few seconds under normal conditions

## Limitations

- **Requires Configuration**: Cannot be executed before saving configuration
- **Network Dependent**: Requires active internet connection
- **No Detailed Diagnostics**: Does not provide detailed information about specific permission issues
- **Single Test**: Tests only basic connectivity, not all extension features

## Best Practices

1. **Test After Configuration**: Always run Test Connection immediately
   after [Save Configuration](pages/SaveConfiguration.md)
2. **Regular Testing**: Test connection periodically to ensure ongoing connectivity
3. **Before Critical Operations**: Test connection before executing important file operations
4. **After Credential Changes**: Test after rotating Client Secret or changing credentials
5. **Troubleshooting First Step**: Use as first diagnostic step when experiencing issues
6. **Monitor Results**: Log test results for troubleshooting and auditing purposes

## Common Use Cases

1. **Post-Configuration Validation**: Verify setup after initial configuration
2. **Periodic Health Checks**: Scheduled tests to ensure ongoing connectivity
3. **Pre-Operation Validation**: Verify connection before critical file operations
4. **Troubleshooting**: Diagnose connection issues when operations fail
5. **Credential Rotation Verification**: Confirm new credentials work after rotation
6. **Environment Migration**: Verify connectivity after moving to new environment

## Related Catalog Requests

- [Save Configuration](pages/SaveConfiguration.md) - Configure extension credentials
- [Health Check](pages/HealthCheck.md) - Comprehensive health status check
- [Extension Configuration](pages/ExtensionConfiguration.md) - Configuration guide

## Technical Implementation

### Helper Classes

- **OnedriveSetUpService**: Handles connection testing logic
- **Microsoft Graph API Client**: Makes API calls to verify connectivity
- **Token Manager**: Manages access token refresh before testing

### Telemetry Metrics

The following metrics are tracked:

- `onedrive.setup.test_connection.success` - Successful connection tests
- `onedrive.setup.test_connection.failure` - Failed connection tests
- `onedrive.setup.test_connection.duration` - Time taken to complete test

### Connection Test Flow

1. Verify extension configuration exists
2. Check if access token is valid
3. If access token expired, attempt to refresh using refresh token
4. Make lightweight API call to Microsoft Graph (e.g., GET /me/drive)
5. Evaluate API response
6. Return success if API call succeeds
7. Return failure with error details if API call fails

### API Endpoint Used

The test typically calls:

```
GET https://graph.microsoft.com/v1.0/me/drive
```

This endpoint:

- Requires minimal permissions
- Returns quickly
- Validates authentication
- Confirms OneDrive access

## Troubleshooting

### Issue: "Extension not configured"

**Possible Causes**:

- Configuration has not been saved
- Configuration was cleared or corrupted

**Solutions**:

1. Execute [Save Configuration](pages/SaveConfiguration.md) with valid credentials
2. Verify all required parameters are provided
3. Check extension logs for configuration errors

### Issue: "Failed to connect: Authentication failed"

**Possible Causes**:

- Access token and refresh token both expired or invalid
- Credentials were revoked in Azure AD
- User password changed
- Application deleted or disabled in Azure AD

**Solutions**:

1. Re-execute [Save Configuration](pages/SaveConfiguration.md) to re-authenticate
2. Verify credentials in Azure Portal
3. Check that Azure AD application is active
4. Ensure user account is not disabled or locked
5. Review [Authentication](pages/Authentication.md) for token management details

### Issue: "Failed to connect: Network error"

**Possible Causes**:

- No internet connectivity
- Firewall blocking Microsoft Graph API
- Proxy configuration issues
- DNS resolution failure

**Solutions**:

1. Verify internet connection is active
2. Check firewall rules allow access to `graph.microsoft.com`
3. Verify proxy settings if applicable
4. Test DNS resolution for `graph.microsoft.com`
5. Check network logs for connection attempts

### Issue: "Failed to connect: Insufficient permissions"

**Possible Causes**:

- Required Microsoft Graph API permissions not granted
- Admin consent not provided
- User does not have OneDrive license
- Conditional access policy blocking access

**Solutions**:

1. Verify all required permissions are granted in Azure AD
2. Ensure admin consent is provided if required
3. Check user has OneDrive license assigned
4. Review conditional access policies
5. See [Creating Microsoft App](pages/CreatingMicrosoftApp.md) for required permissions

### Issue: "Failed to connect: Timeout"

**Possible Causes**:

- Slow network connection
- Microsoft services experiencing delays
- Extension timeout configuration too short

**Solutions**:

1. Retry the test connection
2. Check Microsoft 365 service health status
3. Verify network latency to Microsoft services
4. Contact support if issue persists

## Comparison with Health Check

| Aspect          | Test Connection         | Health Check                  |
|-----------------|-------------------------|-------------------------------|
| **Purpose**     | Basic connectivity test | Comprehensive health status   |
| **Output**      | Success/failure message | Detailed health metrics       |
| **Speed**       | Fast (few seconds)      | Moderate (more comprehensive) |
| **Use Case**    | Quick validation        | Detailed diagnostics          |
| **Information** | Basic                   | Detailed                      |

**Recommendation**: Use Test Connection for quick checks, [Health Check](pages/HealthCheck.md) for detailed diagnostics.

## See Also

- [Save Configuration](pages/SaveConfiguration.md) - Configure extension credentials
- [Health Check](pages/HealthCheck.md) - Comprehensive health diagnostics
- [Authentication](pages/Authentication.md) - OAuth 2.0 authentication details
- [Extension Configuration](pages/ExtensionConfiguration.md) - Configuration guide
- [Creating Microsoft App](pages/CreatingMicrosoftApp.md) - Azure AD app setup

