# Save Configuration

## Overview

Validates and saves OneDrive connection configuration including Client ID, Client Secret, Tenant ID, and Email
credentials for establishing secure connection to Microsoft OneDrive.

## Request Details

| Property          | Value         |
|-------------------|---------------|
| **Area**          | Setup         |
| **Type**          | CHANGE_SYSTEM |
| **Retry Support** | No            |

## Input Parameters

| Parameter Name | Type  | Required | Description                                                                                                                       |
|----------------|-------|----------|-----------------------------------------------------------------------------------------------------------------------------------|
| Client Id      | Text  | Yes      | Application (client) ID from your Azure AD app registration. This identifies your application to Microsoft's identity platform    |
| Client Secret  | Text  | Yes      | Client secret value from your Azure AD app registration. This authenticates your application to Microsoft's identity platform     |
| Tenant Id      | Text  | Yes      | Directory (tenant) ID from your Azure AD. This identifies the Azure AD tenant where your app is registered                        |
| Email          | Email | Yes      | Email address of the OneDrive account to connect to. This should be the same account used to register the application in Azure AD |

## Output Parameters

| Parameter Name | Type   | Description                                                              |
|----------------|--------|--------------------------------------------------------------------------|
| Message        | Text   | Status message indicating the result of the configuration save operation |
| Is Successful  | Yes/No | Indicates whether the configuration was successfully saved and validated |

## Validation Rules

| Field           | Validation Rule                | Error Message                                               |
|-----------------|--------------------------------|-------------------------------------------------------------|
| Client Id       | Must not be empty              | "Client Id is required"                                     |
| Client Id       | Must be valid GUID format      | "Invalid Client Id format"                                  |
| Client Secret   | Must not be empty              | "Client Secret is required"                                 |
| Tenant Id       | Must not be empty              | "Tenant Id is required"                                     |
| Tenant Id       | Must be valid GUID format      | "Invalid Tenant Id format"                                  |
| Email           | Must not be empty              | "Email is required"                                         |
| Email           | Must be valid email format     | "Invalid email format"                                      |
| All credentials | Must authenticate successfully | "User Authentication Failed. Please check the credentials." |

## Error Handling

### INPUT_ERROR

**When it occurs**: Invalid input parameter format or missing required fields

**Common scenarios**:

- Empty Client Id, Client Secret, Tenant Id, or Email
- Invalid GUID format for Client Id or Tenant Id
- Invalid email format

**Response**:

```json
{
  "Is Successful": false,
  "Message": "Invalid input: [specific error]"
}
```

### LOGIC_ERROR

**When it occurs**: Credentials are correctly formatted but authentication fails

**Common scenarios**:

- Incorrect Client Id or Client Secret
- Mismatched Tenant Id
- Email account does not have OneDrive access
- Application not properly configured in Azure AD

**Response**:

```json
{
  "Is Successful": false,
  "Message": "User Authentication Failed. Please check the credentials."
}
```

### SYSTEM_ERROR

**When it occurs**: System-level failures during authentication

**Common scenarios**:

- Network connectivity issues
- Microsoft authentication service unavailable
- Internal extension error

**Response**:

```json
{
  "Is Successful": false,
  "Message": "System error occurred: [error details]"
}
```

### Authorization Errors

**When it occurs**: Permission or access issues

**Common scenarios**:

- Application does not have required Microsoft Graph API permissions
- User account is disabled or locked
- Conditional access policies blocking access

**Response**:

```json
{
  "Is Successful": false,
  "Message": "Authorization failed: [specific reason]"
}
```

## Usage Examples

### Example 1: Successful Configuration

**Input**:

```
Client Id: a1b2c3d4-e5f6-7890-abcd-ef1234567890
Client Secret: abc123~DEF456.ghi789
Tenant Id: 9876fedc-ba09-8765-4321-0fedcba98765
Email: user@contoso.com
```

**Output**:

```
Message: User Authenticated Successfully.
Is Successful: Yes
```

### Example 2: Invalid Credentials

**Input**:

```
Client Id: invalid-client-id
Client Secret: wrong-secret
Tenant Id: 9876fedc-ba09-8765-4321-0fedcba98765
Email: user@contoso.com
```

**Output**:

```
Message: User Authentication Failed. Please check the credentials.
Is Successful: No
```

### Example 3: Missing Required Field

**Input**:

```
Client Id: a1b2c3d4-e5f6-7890-abcd-ef1234567890
Client Secret: 
Tenant Id: 9876fedc-ba09-8765-4321-0fedcba98765
Email: user@contoso.com
```

**Output**:

```
Message: Client Secret is required
Is Successful: No
```

## Business Rules

1. **First-Time Setup**: This request must be executed before any other OneDrive operations
2. **Credential Validation**: All credentials are validated against Microsoft Azure AD before saving
3. **Secure Storage**: Client Secret and Tenant Id are stored encrypted in Krista
4. **OAuth Flow**: Successful execution triggers OAuth 2.0 authentication flow
5. **Token Generation**: Access token and refresh token are obtained and stored upon success
6. **Configuration Update**: Existing configuration is overwritten with new values

## Limitations

- **No Partial Updates**: All four parameters must be provided; partial updates are not supported
- **Single Account**: Only one OneDrive account can be configured per extension instance
- **No Validation Retry**: Failed authentication requires re-entering all credentials
- **Network Dependency**: Requires active internet connection to Microsoft authentication services

## Best Practices

1. **Verify Credentials First**: Ensure all credentials are correct before executing the request
2. **Copy Carefully**: Copy Client Secret immediately after generation in Azure AD
3. **Test After Save**: Execute [Test Connection](pages/TestConnection.md) immediately after successful save
4. **Secure Handling**: Never log or display Client Secret in plain text
5. **Document Expiration**: Note the Client Secret expiration date for future rotation
6. **Use Separate Apps**: Create different Azure AD apps for development and production environments

## Common Use Cases

1. **Initial Extension Setup**: Configure the extension for first-time use
2. **Credential Rotation**: Update expired Client Secret
3. **Account Change**: Switch to a different OneDrive account
4. **Environment Migration**: Reconfigure extension when moving between environments
5. **Troubleshooting**: Re-validate credentials after authentication failures

## Related Catalog Requests

- [Test Connection](pages/TestConnection.md) - Verify the saved configuration
- [Health Check](pages/HealthCheck.md) - Check overall extension health
- [Creating Microsoft App](pages/CreatingMicrosoftApp.md) - How to obtain credentials

## Technical Implementation

### Helper Classes

- **OnedriveSetUpService**: Handles authentication and configuration validation
- **OnedriveAttributes**: Manages extension attribute storage
- **RefreshTokenStore**: Stores and manages OAuth tokens

### Telemetry Metrics

The following metrics are tracked:

- `onedrive.setup.save_configuration.success` - Successful configuration saves
- `onedrive.setup.save_configuration.failure` - Failed configuration attempts
- `onedrive.setup.save_configuration.duration` - Time taken to validate and save

### Authentication Flow

1. Validate input parameter formats
2. Create authentication request with provided credentials
3. Initiate OAuth 2.0 authorization code flow
4. Redirect user to Microsoft login page
5. User authenticates and consents to permissions
6. Receive authorization code from Microsoft
7. Exchange authorization code for access token and refresh token
8. Store tokens securely
9. Update extension configuration
10. Return success or failure response

## Troubleshooting

### Issue: "User Authentication Failed"

**Possible Causes**:

- Incorrect Client Id or Client Secret
- Wrong Tenant Id
- Email account does not match Azure AD app registration
- Application not configured properly in Azure AD

**Solutions**:

1. Verify all credentials in Azure Portal
2. Ensure Client Secret has not expired
3. Check that email account has OneDrive access
4. Review [Creating Microsoft App](pages/CreatingMicrosoftApp.md) for proper setup
5. Verify required permissions are granted in Azure AD

### Issue: "Invalid Client Id format"

**Possible Causes**:

- Client Id is not a valid GUID
- Extra spaces or characters in Client Id
- Copied wrong value from Azure Portal

**Solutions**:

1. Verify Client Id is in GUID format (e.g., `a1b2c3d4-e5f6-7890-abcd-ef1234567890`)
2. Remove any extra spaces or characters
3. Copy Application (client) ID directly from Azure Portal Overview page

### Issue: "Invalid email format"

**Possible Causes**:

- Email address contains typos
- Email is not in valid format
- Extra spaces in email field

**Solutions**:

1. Verify email address is correctly formatted
2. Remove any leading or trailing spaces
3. Ensure email domain is correct

## See Also

- [Extension Configuration](pages/ExtensionConfiguration.md) - Detailed configuration guide
- [Authentication](pages/Authentication.md) - OAuth 2.0 authentication flow
- [Creating Microsoft App](pages/CreatingMicrosoftApp.md) - Azure AD app registration
- [Test Connection](pages/TestConnection.md) - Verify connectivity
- [Health Check](pages/HealthCheck.md) - Check extension health

