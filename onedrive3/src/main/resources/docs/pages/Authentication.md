# Authentication

## Overview

The OneDrive Files extension uses OAuth 2.0 authentication with Microsoft Azure Active Directory (Azure AD) to securely
access OneDrive resources. This page explains the authentication flow, token management, and troubleshooting
authentication issues.

## OAuth 2.0 Authentication Flow

The extension implements the OAuth 2.0 Authorization Code Grant flow with the following steps:

### 1. Initial Authorization

When you first configure the extension or validate attributes:

1. **User initiates connection** by providing credentials in Krista
2. **Extension redirects** user to Microsoft login page
3. **User authenticates** with Microsoft credentials
4. **User consents** to requested permissions
5. **Microsoft redirects** back with authorization code
6. **Extension exchanges** authorization code for access token and refresh token

### 2. Token Storage

After successful authentication:

- **Access Token**: Short-lived token (typically 1 hour) used for API requests
- **Refresh Token**: Long-lived token used to obtain new access tokens
- Both tokens are stored securely in Krista's encrypted storage

### 3. Automatic Token Refresh

The extension automatically manages token lifecycle:

1. **Before each API call**, extension checks if access token is expired
2. **If expired**, extension uses refresh token to obtain new access token
3. **New access token** is stored and used for the API request
4. **Process is transparent** to the user - no re-authentication required

## Grant Types

### Authorization Code Grant

**Used for**: Initial user authentication and consent

**Flow**:

```
User → Microsoft Login → User Consent → Authorization Code → Access Token + Refresh Token
```

**When it occurs**:

- First time configuring the extension
- When validating attributes in Krista
- After refresh token expires or is revoked

**User action required**: Yes - user must log in and consent

### Refresh Token Grant

**Used for**: Obtaining new access tokens without user interaction

**Flow**:

```
Refresh Token → New Access Token
```

**When it occurs**:

- Automatically when access token expires (typically every hour)
- Before each API request if token is expired

**User action required**: No - fully automatic

## Token Expiration Scenarios

### Access Token Expiration

**Expiration Time**: Typically 1 hour

**What happens**:

1. Extension detects expired access token
2. Extension uses refresh token to get new access token
3. New access token is stored
4. API request proceeds normally

**User impact**: None - handled automatically

**Error messages**: None if refresh token is valid

### Refresh Token Expiration

**Expiration Time**: Typically 90 days (or when explicitly revoked)

**What happens**:

1. Extension attempts to use refresh token
2. Microsoft returns error indicating refresh token is invalid
3. Extension returns authentication error
4. User must re-authenticate

**User impact**: Must re-authenticate through Krista

**Error messages**:

- "Authentication failed. Please re-validate attributes."
- "Refresh token expired. Please reconnect."
- "Invalid refresh token. Re-authentication required."

**Resolution**:

1. Go to extension configuration in Krista
2. Click "Validate Attributes" or "Reconnect"
3. Complete the authentication flow again

### Token Revocation

**Causes**:

- User changes password
- Administrator revokes access
- User revokes consent in Microsoft account settings
- Security policy changes

**What happens**:

1. Extension attempts to use token
2. Microsoft returns authorization error
3. Extension returns authentication error

**User impact**: Must re-authenticate

**Resolution**: Same as refresh token expiration

## Required Permissions

The extension requests the following Microsoft Graph API permissions:

| Permission          | Type      | Purpose                                      | Admin Consent Required |
|---------------------|-----------|----------------------------------------------|------------------------|
| Files.ReadWrite.All | Delegated | Read and write all files user can access     | No                     |
| Sites.ReadWrite.All | Delegated | Read and write items in all site collections | Yes (in some tenants)  |
| offline_access      | Delegated | Maintain access to data                      | No                     |

### Permission Scopes Explained

#### Files.ReadWrite.All

- Allows reading file metadata and content
- Allows creating, updating, and deleting files
- Allows copying and moving files
- Limited to files the authenticated user can access

#### Sites.ReadWrite.All

- Allows access to SharePoint sites and shared drives
- Required for shared drive operations
- May require admin consent depending on tenant settings

#### offline_access

- Allows obtaining refresh tokens
- Enables automatic token refresh without user interaction
- Essential for long-running workflows

## Authentication States

### Authenticated

**Indicators**:

- Test Connection succeeds
- Health Check shows "Healthy" status
- Catalog requests execute successfully

**Token status**: Valid access token and refresh token

**User action**: None required

### Token Expired (Recoverable)

**Indicators**:

- Access token expired but refresh token valid
- Automatic refresh in progress

**Token status**: Expired access token, valid refresh token

**User action**: None required - automatic recovery

### Authentication Failed (Requires Re-authentication)

**Indicators**:

- Test Connection fails with authentication error
- Health Check shows "Unhealthy" status
- Catalog requests return authentication errors

**Token status**: Invalid or expired refresh token

**User action**: Re-validate attributes in Krista

## Troubleshooting Authentication Issues

### Issue: "Authentication Failed" on Initial Setup

**Possible Causes**:

- Incorrect Client Id, Client Secret, or Tenant Id
- Application not properly configured in Azure AD
- User does not have OneDrive access
- Required permissions not granted

**Solutions**:

1. Verify all configuration parameters are correct
2. Check Azure AD app registration settings
3. Ensure user has OneDrive license
4. Grant required permissions in Azure AD
5. Review [Extension Configuration](pages/ExtensionConfiguration.md)

### Issue: "Token Expired" or "Invalid Token"

**Possible Causes**:

- Refresh token expired (after 90 days)
- User password changed
- Admin revoked access
- User revoked consent

**Solutions**:

1. Re-validate attributes in Krista
2. Complete authentication flow again
3. Check Azure AD sign-in logs for details
4. Verify user account is active

### Issue: "Insufficient Permissions"

**Possible Causes**:

- Required permissions not granted to application
- User does not have necessary OneDrive permissions
- Conditional access policies blocking access

**Solutions**:

1. Verify all required permissions are granted in Azure AD
2. Ensure admin consent is provided if required
3. Check user's OneDrive access level
4. Review conditional access policies

### Issue: "Consent Required"

**Possible Causes**:

- User has not consented to application permissions
- Admin consent required but not provided
- Consent was revoked

**Solutions**:

1. Re-validate attributes to trigger consent flow
2. Request admin consent if required
3. Check Azure AD app permissions configuration

## Security Considerations

### Token Security

- **Tokens are encrypted** at rest in Krista storage
- **Tokens are transmitted** over HTTPS only
- **Tokens are not logged** in plain text
- **Tokens are scoped** to minimum required permissions

### Best Practices

1. **Regular Re-authentication**: Re-validate attributes every 60-90 days
2. **Monitor Access**: Review Azure AD sign-in logs regularly
3. **Rotate Secrets**: Update Client Secret periodically
4. **Limit Scope**: Only grant necessary permissions
5. **Use Conditional Access**: Implement Azure AD conditional access policies

### Compliance

The authentication implementation complies with:

- OAuth 2.0 RFC 6749
- Microsoft Identity Platform best practices
- GDPR data protection requirements
- SOC 2 security standards

## Token Lifecycle Diagram

```
[Initial Setup]
     ↓
[User Login & Consent] → [Authorization Code]
     ↓
[Exchange Code] → [Access Token + Refresh Token]
     ↓
[Store Tokens Securely]
     ↓
[Use Access Token for API Calls]
     ↓
[Access Token Expires?]
     ↓ Yes
[Use Refresh Token] → [New Access Token]
     ↓
[Continue API Calls]
     ↓
[Refresh Token Expires?]
     ↓ Yes
[Re-authentication Required] → [User Login & Consent]
```

## Monitoring Authentication Health

### Using Health Check

The [Health Check](pages/HealthCheck.md) catalog request provides authentication status:

**Output Fields**:

- **Is Healthy**: Overall health status
- **Token Status**: Current token validity
- **Health Status**: Detailed health information

**Recommended Frequency**: Check health before critical operations

### Using Test Connection

The [Test Connection](pages/TestConnection.md) catalog request verifies connectivity:

**Output Fields**:

- **Is Successful**: Connection status
- **Message**: Success or error details

**Recommended Use**: After configuration changes or authentication errors

## Next Steps

After understanding authentication:

1. **Configure the extension** using [Extension Configuration](pages/ExtensionConfiguration.md)
2. **Create Azure AD app** following [Creating Microsoft App](pages/CreatingMicrosoftApp.md)
3. **Test connection** using [Test Connection](pages/TestConnection.md)
4. **Monitor health** using [Health Check](pages/HealthCheck.md)

## See Also

- [Extension Configuration](pages/ExtensionConfiguration.md) - Configuration parameters
- [Creating Microsoft App](pages/CreatingMicrosoftApp.md) - Azure AD app setup
- [Save Configuration](pages/SaveConfiguration.md) - Validate and save credentials
- [Test Connection](pages/TestConnection.md) - Verify connectivity
- [Health Check](pages/HealthCheck.md) - Check authentication health

