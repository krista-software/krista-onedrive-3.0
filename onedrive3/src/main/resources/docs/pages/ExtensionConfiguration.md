# Extension Configuration

## Overview

The OneDrive Files extension requires configuration with Microsoft Azure AD credentials to establish a secure connection
to your OneDrive account. This page describes the configuration parameters and setup process.

## Configuration Parameters

The following parameters are required to configure the OneDrive Files extension:

| Parameter Name | Type  | Required | Secured | Description                                                               |
|----------------|-------|----------|---------|---------------------------------------------------------------------------|
| Client Id      | Text  | Yes      | No      | The Application (client) ID from your Azure AD app registration           |
| Client Secret  | Text  | Yes      | Yes     | The client secret value generated in your Azure AD app                    |
| Tenant Id      | Text  | Yes      | Yes     | The Directory (tenant) ID from your Azure AD app registration             |
| Email          | Email | Yes      | No      | The Microsoft 365 email account used to register the Azure AD application |

### Parameter Details

#### Client Id

- **Purpose**: Identifies your Azure AD application
- **Format**: GUID (e.g., `a1b2c3d4-e5f6-7890-abcd-ef1234567890`)
- **Where to Find**: Azure Portal → App registrations → Your app → Overview → Application (client) ID
- **Security**: Not sensitive, can be stored in plain text

#### Client Secret

- **Purpose**: Authenticates your application to Azure AD
- **Format**: String value (e.g., `abc123~DEF456.ghi789`)
- **Where to Find**: Azure Portal → App registrations → Your app → Certificates & secrets → Client secrets
- **Security**: Highly sensitive, stored encrypted in Krista
- **Important**: Copy the secret value immediately after creation - it cannot be retrieved later

#### Tenant Id

- **Purpose**: Identifies your Azure AD organization
- **Format**: GUID (e.g., `9876fedc-ba09-8765-4321-0fedcba98765`)
- **Where to Find**: Azure Portal → App registrations → Your app → Overview → Directory (tenant) ID
- **Security**: Sensitive, stored encrypted in Krista

#### Email

- **Purpose**: The Microsoft 365 account that will be used for OneDrive operations
- **Format**: Valid email address (e.g., `user@contoso.com`)
- **Requirements**:
    - Must have OneDrive access
    - Should be the same account used to register the Azure AD application
    - Must have appropriate permissions for the operations you plan to perform

## Configuration Steps

### Step 1: Register Azure AD Application

Before configuring the extension, you must register an application in Azure AD. Follow the detailed guide:

👉 [Creating Microsoft App](pages/CreatingMicrosoftApp.md)

### Step 2: Gather Configuration Values

Collect the following values from your Azure AD app registration:

1. Navigate to [Azure Portal](https://portal.azure.com)
2. Go to **Azure Active Directory** → **App registrations**
3. Select your application
4. Copy the following values:
    - **Application (client) ID** → Use as `Client Id`
    - **Directory (tenant) ID** → Use as `Tenant Id`
5. Go to **Certificates & secrets** → **Client secrets**
6. Create a new client secret (if not already created)
7. Copy the **Value** → Use as `Client Secret`

### Step 3: Configure Extension in Krista

1. Open Krista Designer
2. Navigate to the OneDrive Files extension
3. Click **Configure** or **Settings**
4. Enter the configuration parameters:
    - **Client Id**: Paste the Application (client) ID
    - **Client Secret**: Paste the client secret value
    - **Tenant Id**: Paste the Directory (tenant) ID
    - **Email**: Enter your Microsoft 365 email address
5. Click **Save** or **Apply**

### Step 4: Validate Configuration

After saving the configuration, validate the connection:

1. Use the [Save Configuration](pages/SaveConfiguration.md) catalog request to validate and save credentials
2. Use the [Test Connection](pages/TestConnection.md) catalog request to verify connectivity
3. Use the [Health Check](pages/HealthCheck.md) catalog request to check overall health

## Configuration Validation

The extension performs the following validations when you save the configuration:

### Client Id Validation

- Must not be empty
- Must be a valid GUID format
- Must correspond to an existing Azure AD application

### Client Secret Validation

- Must not be empty
- Must be a valid secret value
- Must not be expired

### Tenant Id Validation

- Must not be empty
- Must be a valid GUID format
- Must correspond to an existing Azure AD tenant

### Email Validation

- Must not be empty
- Must be a valid email format
- Must have access to OneDrive

## Common Configuration Issues

### Issue: "Invalid Client Id"

**Cause**: The Client Id does not match any registered application in Azure AD

**Solution**:

1. Verify you copied the correct Application (client) ID from Azure Portal
2. Ensure you're using the correct Azure AD tenant
3. Check that the application is not deleted or disabled

### Issue: "Invalid Client Secret"

**Cause**: The Client Secret is incorrect, expired, or has been deleted

**Solution**:

1. Generate a new client secret in Azure Portal
2. Copy the new secret value immediately
3. Update the configuration with the new secret
4. Ensure the secret has not expired (check expiration date)

### Issue: "Unauthorized Tenant"

**Cause**: The Tenant Id does not match the tenant where the application is registered

**Solution**:

1. Verify you copied the correct Directory (tenant) ID
2. Ensure the application is registered in the correct tenant
3. Check that you have access to the tenant

### Issue: "Authentication Failed"

**Cause**: The email account does not have proper permissions or access

**Solution**:

1. Verify the email account has OneDrive access
2. Ensure the account is not disabled or locked
3. Check that the account has consented to the application permissions
4. Verify the account is in the same tenant as the application

## Security Best Practices

### Protect Your Credentials

- **Never share** your Client Secret with unauthorized users
- **Rotate secrets** regularly (recommended: every 90 days)
- **Use separate** Azure AD applications for development and production
- **Limit permissions** to only what is required for your use case

### Monitor Access

- **Review** Azure AD sign-in logs regularly
- **Enable** conditional access policies if available
- **Monitor** for unusual activity or unauthorized access
- **Audit** configuration changes in Krista

### Secret Management

- **Set expiration dates** for client secrets (recommended: 6-12 months)
- **Document** secret rotation schedule
- **Test** new secrets before removing old ones
- **Store** backup credentials securely

## Required Azure AD Permissions

The Azure AD application must have the following Microsoft Graph API permissions:

| Permission          | Type      | Purpose                                             |
|---------------------|-----------|-----------------------------------------------------|
| Files.ReadWrite.All | Delegated | Read and write all files that user can access       |
| Sites.ReadWrite.All | Delegated | Read and write items in all site collections        |
| offline_access      | Delegated | Maintain access to data you have given it access to |

These permissions must be granted by an Azure AD administrator.

## Next Steps

After configuring the extension:

1. **Test the connection** using [Test Connection](pages/TestConnection.md)
2. **Review authentication** details in [Authentication](pages/Authentication.md)
3. **Start using** catalog requests for file operations
4. **Monitor health** using [Health Check](pages/HealthCheck.md)

## See Also

- [Creating Microsoft App](pages/CreatingMicrosoftApp.md) - Step-by-step Azure AD app registration
- [Authentication](pages/Authentication.md) - OAuth 2.0 authentication flow
- [Save Configuration](pages/SaveConfiguration.md) - Validate and save credentials
- [Test Connection](pages/TestConnection.md) - Verify connectivity

