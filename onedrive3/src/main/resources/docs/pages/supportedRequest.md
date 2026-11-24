# Supported Requests

## Catalog Requests

Krista OneDrive extension supports the following Catalog Requests:

> #### Notes :
> - In OneDrive, the term 'Files' denotes both individual files and folders.
> - The **Success** indicator will be marked as Yes if the response is successfully retrieved.
> - There exists no correlation between the legacy version of OneDrive and the updated iteration, OneDrive Files.
> - We can not upgrade from OneDrive extension to OneDrive Files extension.

### Setup Area

#### 1. Save Configuration

- **Description**: Validates and saves OneDrive connection configuration including Client ID, Client Secret, Tenant ID,
  and Email credentials for establishing secure connection to Microsoft OneDrive.
- **Input Parameters**:

| **Parameter Name** | **Parameter Type** | **Mandatory** | **Example**                   |
|--------------------|--------------------|---------------|-------------------------------|
| Client Id          | Text               | Yes           | 4uhu24givb35y39b399bv39       |
| Client Secret      | Text               | Yes           | your_client_secret            |
| Tenant Id          | Text               | Yes           | your_tenant_id                |
| Email              | Email              | Yes           | onedrive.testmail@contoso.com |

> #### Notes :
> - All parameters are mandatory for establishing connection.
> - Client ID and Client Secret must be obtained from Azure AD app registration.
> - Email should be the same account used to register the application in Azure AD.

- **Output**:

| **Parameter Name** | **Parameter Type** | **Example**                      |
|--------------------|--------------------|----------------------------------|
| Message            | Text               | User Authenticated Successfully. |
| Is Successful      | Yes/No             | Yes                              |

#### 2. Test Connection

- **Description**: Test the OneDrive files connection is established.
- **Input Parameters**: None

- **Output**:

| **Parameter Name** | **Parameter Type** | **Example**           |
|--------------------|--------------------|-----------------------|
| Is Successful      | Yes/No             | Yes                   |
| Message            | Text               | Connection Successful |

#### 3. Health Check

- **Description**: This 'Health Check' request verifies the health status of OneDrive by calling the health check API.
- **Input Parameters**: None

- **Output**:

| **Parameter Name**      | **Parameter Type** | **Example** |
|-------------------------|--------------------|-------------|
| Health Status           | Entity             | Entity      |
| Extension Response Meta | Entity             | Entity      |
| Is Healthy              | Yes/No             | Yes         |

### Management Area

#### 1. Copy

- **Description**: The Copy request copies the provided file/folder to the target location.
  If the target file already exists, it can be replaced (based on the Replace If Exists flag).
  If the target folder does not exist, you can control whether it should be auto-created using the Create Folder flag.
- **Input Parameters**:

| **Parameter Name** | **Parameter Type** | **Mandatory** | **Example**                   |
|--------------------|--------------------|---------------|-------------------------------|
| Source             | Text               | Yes           | drive/root:/ABC/fileName      |
| Target             | Text               | Yes           | drive/root:/Test/TargetFolder |
| Replace If Exists  | Boolean            | No            | True/False                    |
| Create Folder      | Boolean            | No            | True/False                    |

> #### Notes :
> - The Copy request supports only for Personal drive folders and file.
> - For reference, path of Personal drive starts with drive/root:/ and files in Shared drives starts with drives/b!
    absd69bfb33bvt5wev67b/root:/.
> - Limitation : From Microsoft Graph service, In many cases the copy action is performed asynchronously.
> - The response from the Catalog might indicate message like **Please, Provide Valid Source Path** but the copy
    operation is completed.
> - Replace If Exists is not supported to copy folders.
> - **Re-enter Support**: If source path is invalid, system will prompt to re-enter the source path. If target path is
    invalid, system will prompt to re-enter the target path.

- **Output**:

| **Parameter Name** | **Parameter Type** | **Example**                                                         |
|--------------------|--------------------|---------------------------------------------------------------------|
| File               | Entity             | {name : "ABC", extension:".txt" , absolutePath : "drive/root:/ABC"} |
| Message            | Text               | File has been copied successfully                                   |
| Success            | Yes/No             | Yes                                                                 |

#### 2. Copy With Name

- **Description**: Atomically copies a file into the target folder with a specified name, avoiding renames and preventing
  collisions during parallel execution.
- **Input Parameters**:

| **Parameter Name** | **Parameter Type** | **Mandatory** | **Example**                   |
|--------------------|--------------------|---------------|-------------------------------|
| Source             | Text               | Yes           | drive/root:/ABC/fileName.xlsx |
| Target             | Text               | Yes           | drive/root:/Test/TargetFolder |
| Target File Name   | Text               | Yes           | 2026-04-16_Report.xlsx        |
| Replace            | Boolean            | No            | True/False                    |
| Create Folder      | Boolean            | No            | True/False                    |

> #### Notes :
> - The Copy With Name request supports only for Personal drive files.
> - For reference, path of Personal drive starts with drive/root:/ and files in Shared drives starts with drives/b!
    absd69bfb33bvt5wev67b/root:/.
> - The copy and rename is performed atomically — no intermediate file name is visible to other operations.
> - This request is useful when multiple workflows may copy to the same folder in parallel and need unique file names.

- **Output**:

| **Parameter Name** | **Parameter Type** | **Example**                                                                    |
|--------------------|--------------------|--------------------------------------------------------------------------------|
| File               | Entity             | {name : "2026-04-16_Report.xlsx", extension:".xlsx" , absolutePath : "drive/root:/Test/TargetFolder/2026-04-16_Report.xlsx"} |
| Message            | Text               | File has been copied successfully                                              |
| Success            | Yes/No             | Yes                                                                            |

#### 3. Delete

- **Description**: Delete the file in Target path from the cloud storage provider.
- **Input Parameters**:

| **Parameter Name** | **Parameter Type** | **Mandatory** | **Example**                  |
|--------------------|--------------------|---------------|------------------------------|
| Target             | Text               | Yes           | drive/root:/ABC/fileName.txt |

> #### Notes :
> - The Delete request supports only for Personal drive folders and file.
> - For reference, path of Personal drive starts with drive/root:/ and files in Shared drives starts with drives/b!
    absd69bfb33bvt5wev67b/root:/.
> - **Re-enter Support**: If target path is invalid or file not found, system will prompt to re-enter the target path.

- **Output**:

| **Parameter Name** | **Parameter Type** | **Example**               |
|--------------------|--------------------|---------------------------|
| Message            | Text               | File deleted successfully |
| Success            | Yes/No             | Yes                       |

#### 4. Download as Link

- **Description**: Retrieves the file from the cloud storage and provides a reference as link.
- **Input Parameters**:

| **Parameter Name** | **Parameter Type** | **Mandatory** | **Example**                                   |
|--------------------|--------------------|---------------|-----------------------------------------------|
| Source             | Text               | Yes           | drives/b!absdbfbbvwevb/root:/ABC/fileName.txt |

> #### Notes :
> - The download link is valid for only 1 hour.
> - The Download as Link request supports Shared drives.
> - The Download as Link request does not support downloading Folders.
> - For reference, path of Personal drive starts with drive/root:/ and files in Shared drives starts with drives/b!
    absd69bfb33bvt5wev67b/root:/.
> - **Re-enter Support**: If source path is invalid or file not found, system will prompt to re-enter the source path.

- **Output**:

| **Parameter Name** | **Parameter Type** | **Example**                        |
|--------------------|--------------------|------------------------------------|
| Download Link      | Rich Text          | /Documents/john/filename           |
| Message            | Text               | To Download file, click on the URL |
| Success            | Yes/No             | Yes                                |

#### 5. Download as File

- **Description**: Retrieves the file from the cloud storage and provides a Krista File.
- **Input Parameters**:

| **Parameter Name** | **Parameter Type** | **Mandatory** | **Example**                                   |
|--------------------|--------------------|---------------|-----------------------------------------------|
| Source Path        | Text               | Yes           | drives/b!absdbfbbvwevb/root:/ABC/fileName.txt |

> #### Notes :
> - The Download as Krista File request supports files up to 50 MB.
> - The Download as Krista File request supports Shared drives.
> - The Download as Krista File request does not support downloading Folders.
> - For reference, path of Personal drive starts with drive/root:/ and files in Shared drives starts with drives/b!
    absd69bfb33bvt5wev67b/root:/.
> - Please note that if you attempt to download any file with the following extensions: html, php5, pht, phtml, shtml,
    asa, cer, asax, swf, xap, jsp, exe, or js, the file will be downloaded in a zip format.
> - **Re-enter Support**: If source path is invalid or file not found, system will prompt to re-enter the source path.

- **Output**:

| **Parameter Name** | **Parameter Type** | **Example**               |
|--------------------|--------------------|---------------------------|
| Krista File        | File               | File                      |
| Message            | Rich Text          | File is ready to download |
| Success            | Yes/No             | Yes                       |

#### 6. Get Info

- **Description**: Accepts Source of File and return Task ID which will further used in Get Info Result catalog request
  to retrieve folder information.
- **Input Parameters**:

| **Parameter Name** | **Parameter Type** | **Mandatory** | **Example**                |
|--------------------|--------------------|---------------|----------------------------|
| Source Path        | Text               | Yes           | drive/root:/ABC/folderName |

> #### Notes :
> - The Get Info request is applicable for Folders only.
> - The Get Info request supports Shared drives.
> - For reference, path of Personal drive starts with drive/root:/ and files in Shared drives starts with drives/b!
    absd69bfb33bvt5wev67b/root:/.

- **Output**:

| **Parameter Name** | **Parameter Type** | **Example**              |
|--------------------|--------------------|--------------------------|
| Task ID            | Text               | bdcqwveb84yt4y43y84yt284 |

#### 7. Get Info Result

- **Description**: Accepts Task ID and retrieves key information around a single folder in the cloud storage.
- **Input Parameters**:

| **Parameter Name** | **Parameter Type** | **Mandatory** | **Example**              |
|--------------------|--------------------|---------------|--------------------------|
| Task ID            | Text               | Yes           | bdcqwveb84yt4y43y84yt284 |

> #### Notes :
> - The Get Info Result request supports **Task ID** of Get Info catalog request.
> - The Get Info Result request supports Shared drives.
> - The Get Info Result request is applicable for Folders only.
> - For reference, path of Personal drive starts with drive/root:/ and files in Shared drives starts with drives/b!
    absd69bfb33bvt5wev67b/root:/.

- **Output**:

| **Parameter Name**             | **Parameter Type** | **Example**                           |
|--------------------------------|--------------------|---------------------------------------|
| Files                          | Number             | 5                                     |
| Folders                        | Number             | 2                                     |
| Size                           | Number             | 21324                                 |
| Oldest File                    | Text               | Onedrive                              |
| Oldest File Create Date        | Date               | 03/04/2023                            |
| Newest File                    | Text               | Onedrive 3.0                          |
| Newest File Date               | Date               | 03/04/2023                            |
| Last Modified File Id          | Text               | bdcqwveb84yt4y43y84yt284              |
| Last Modified File Name        | Text               | Extension                             |
| Last Modified File Create Date | Date               | 03/04/2023                            |
| Message                        | Text               | Folder information fetch successfully |
| Success                        | Yes/No             | Yes                                   |

#### 8. Get Result

- **Description**:This method accepts a Task ID generated from the List of Files, List of Folders &
- Search By Permissions method and returns the corresponding list of files and folders.
- **Input Parameters**:

| **Parameter Name** | **Parameter Type** | **Mandatory** | **Example**              |
|--------------------|--------------------|---------------|--------------------------|
| Task ID            | Text               | Yes           | bdcqwveb84yt4y43y84yt284 |

> #### Notes :
> - The Get Result request supports **Task ID** of List Files, List Folder catalog requests and Search By Permissions
    catalog request from OneDrive Area.

- **Output**:

| **Parameter Name** | **Parameter Type** | **Example**                                                              | **Description**             |
|--------------------|--------------------|--------------------------------------------------------------------------|-----------------------------|
| Files              | List&lt;File>      | [{name : "ABC", extension:".txt" , "Absolute Path" : "drive/root:/ABC"}] | File represents File Entity |
| Message            | Text               | File information                                                         |                             |
| Success            | Yes/No             | Yes                                                                      |                             |

#### 9. List Files

- **Description**: Produces a non-recursive list of files that is present within the folder denoted by the source path.
- **Input Parameters**:

| **Parameter Name** | **Parameter Type** | **Mandatory** | **Example**              |
|--------------------|--------------------|---------------|--------------------------|
| Source Path        | Text               | Yes           | drive/root:/ABC/fileName |
| Search             | Text               | No            | Demo.json                |

> #### Notes :
>  - Files in Personal drive can be also searched using relative path For Example :  /ABC/fileName.txt .
>  - The List Files request response contains files as well as folders.
>  - To get all Shared files use path as shared or /shared , For Example : shared .
> - For reference, path of Personal drive starts with drive/root:/ and files in Shared drives starts with drives/b!
    absd69bfb33bvt5wev67b/root:/.
> - To collect list of files use **Task ID** in Get Result catalog request.

- **Output**:

| **Parameter Name** | **Parameter Type** | **Example**              |
|--------------------|--------------------|--------------------------|
| Task ID            | Text               | bdcqwveb84yt4y43y84yt284 |

#### 10. List Folders

- **Description**: Produces a non-recursive list of folders that is present within the folder denoted by the source
  path.
- **Input Parameters**:

| **Parameter Name** | **Parameter Type** | **Mandatory** | **Example**                |
|--------------------|--------------------|---------------|----------------------------|
| Source Path        | Text               | Yes           | drive/root:/ABC/folderName |
| Search             | Text               | No            | Demo                       |

> #### Notes :
>  - Folders in Personal drive can be also searched using relative path For Example : /ABC/folderName .
>  - To get all Shared folders use path as shared or /shared , For Example : shared .
> - For reference, path of Personal drive starts with drive/root:/ and files in Shared drives starts with drives/b!
    absd69bfb33bvt5wev67b/root:/.
> - To collect list of folders use **Task ID** in Get Result catalog request.

- **Output**:

| **Parameter Name** | **Parameter Type** | **Example**              |
|--------------------|--------------------|--------------------------|
| Task ID            | Text               | bdcqwveb84yt4y43y84yt284 |

#### 11. Move

- **Description**: Move a file on the drive from the source to the target, where both denote full file paths.
- **Input Parameters**:

| **Parameter Name** | **Parameter Type** | **Mandatory** | **Example**                   |
|--------------------|--------------------|---------------|-------------------------------|
| Source             | Text               | Yes           | drive/root:/ABC/fileName.txt  |
| Target             | Text               | Yes           | drive/root:/Test/TargetFolder |

> #### Notes :
> - The Move request supports only for Personal drive folders and file.
> - For reference, path of Personal drive starts with drive/root:/ and files in Shared drives starts with drives/b!
    absd69bfb33bvt5wev67b/root:/.
> - Limitation : From Microsoft Graph service, In many cases the move action is performed asynchronously.
> - The response from the Catalog might indicate message like **Please, Provide Valid Source Path** but the move
    operation
    is completed.
> - Replace If Exists is not supported to move folders.
> - **Re-enter Support**: If source path is invalid, system will prompt to re-enter the source path. If target path is
    invalid, system will prompt to re-enter the target path.

- **Output**:

| **Parameter Name** | **Parameter Type** | **Example**                                                            |
|--------------------|--------------------|------------------------------------------------------------------------|
| File               | Entity             | {name : "ABC", extension:".txt" , "Absolute Path" : "drive/root:/ABC"} |
| Message            | Text               | File has been moved successfully                                       |
| Success            | Yes/No             | Yes                                                                    |

#### 12. Read

- **Description**: Read the content of the file as plain text. If the file is too big or cannot be accessed this should
  be reflected in the success and message variables produced. Content in that case should be empty.
- **Input Parameters**:

| **Parameter Name** | **Parameter Type** | **Mandatory** | **Example**              |
|--------------------|--------------------|---------------|--------------------------|
| Source             | Text               | Yes           | drive/root:/ABC/fileName |

> #### Notes :
> - The Read request supports only for Personal drive files and applicable for .txt files only.
> - For reference, path of Personal drive starts with drive/root:/ and files in Shared drives starts with drives/b!
    absd69bfb33bvt5wev67b/root:/.
> - **Re-enter Support**: If source path is invalid or file not found, system will prompt to re-enter the source path.

- **Output**:

| **Parameter Name** | **Parameter Type** | **Example**               |
|--------------------|--------------------|---------------------------|
| Content            | Paragraph          | This is demo file content |
| Message            | Text               | File found                |
| Success            | Yes/No             | Yes                       |

#### 13. Rename

- **Description**: Rename the file located at the source file path to the given name.
- **Input Parameters**:

| **Parameter Name** | **Parameter Type** | **Mandatory** | **Example**                  |
|--------------------|--------------------|---------------|------------------------------|
| Source             | Text               | Yes           | drive/root:/ABC/fileName.txt |
| Target File Name   | Text               | Yes           | New Folder or NewExcel.xlsx  |

> #### Notes :
> - **Re-enter Support**: If source path is invalid, system will prompt to re-enter the source path. If target file name
    is invalid or already exists, system will prompt to re-enter the target file name.

- **Output**:

| **Parameter Name** | **Parameter Type** | **Example**            |
|--------------------|--------------------|------------------------|
| Message            | Text               | Renaming is successful |
| Success            | Yes/No             | Yes                    |

> #### Notes :
> - The Rename request supports only for Personal drive folders and file.
> - For reference, path of Personal drive starts with drive/root:/ and files in Shared drives starts with drives/b!
    absd69bfb33bvt5wev67b/root:/.

#### 14. Share File

- **Description**: Share the file denoted by source with the given users identified by their email address applying the
  provided Permissions.
- **Input Parameters**:

| **Parameter Name** | **Parameter Type**                    | **Mandatory** | **Example**                                          |
|--------------------|---------------------------------------|---------------|------------------------------------------------------|
| Source             | Text                                  | Yes           | drive/root:/ABC/fileName                             |
| Share              | List&lt;Multi Field<Email, Pick One>> | Yes           | [{Email : "testmail@mail.com, Permissions : "Read"}] |

> #### Notes :
> - In OneDrive Files Extension **Read and Write** and **Own** permissions are not supported.
> - Limitation: If a file is being shared with multiple users with only one permission, then in response, it may be
    possible to have only one user listed in the **Shared With** field.
> - The Share File request supports Shared drives.
> - For reference, path of Personal drive starts with drive/root:/ and files in Shared drives starts with drives/b!
    absd69bfb33bvt5wev67b/root:/.
> - **Re-enter Support**: If source path is invalid, system will prompt to re-enter the source path.

- **Output**:

| **Parameter Name** | **Parameter Type** | **Example**                                                            |
|--------------------|--------------------|------------------------------------------------------------------------|
| File               | Entity             | {name : "ABC", extension:".txt" , "Absolute Path" : "drive/root:/ABC"} |
| Message            | Text               | File Has been shared Successfully                                      |
| Success            | Yes/No             | Yes                                                                    |

#### 15. Write

- **Description**: Upload the content of the file from the media server to the cloud storage at target path. Produced
  File Entity represent the file in the cloud storage.
- **Input Parameters**:

| **Parameter Name** | **Parameter Type** | **Mandatory** | **Example**                |
|--------------------|--------------------|---------------|----------------------------|
| Krista File        | File               | Yes           | Test.pdf                   |
| Target Path        | Text               | Yes           | drive/root:/ABC/FolderName |

> #### Notes :
> - The Write request supports file size up to 50 MB.
> - The Write request does not offer support for uploading of multiple files.
> - The Write request supports Shared drives.
> - For reference, path of Personal drive starts with drive/root:/ and files in Shared drives starts with drives/b!
    absd69bfb33bvt5wev67b/root:/.
> - **Re-enter Support**: If target path is invalid, system will prompt to re-enter the target path.

- **Output**:

| **Parameter Name** | **Parameter Type** | **Example**                                                            |
|--------------------|--------------------|------------------------------------------------------------------------|
| File               | Entity             | {name : "ABC", extension:".txt" , "Absolute Path" : "drive/root:/ABC"} |
| Message            | Text               | File uploaded successfully                                             |
| Success            | Yes/No             | Yes                                                                    |

#### 16. Write Text

- **Description**: Uploads the file content to the cloud storage at the given target location as a plain text file.
- **Input Parameters**:

| **Parameter Name** | **Parameter Type** | **Mandatory** | **Example**                  |
|--------------------|--------------------|---------------|------------------------------|
| File Content       | Paragraph          | Yes           | Demo file content            |
| Target             | Text               | Yes           | drive/root:/ABC/fileName.txt |

> #### Notes :
> - The Write Text request supports only for Personal drive file and applicable for .txt files only.
> - For reference, path of Personal drive starts with drive/root:/ and files in Shared drives starts with drives/b!
    absd69bfb33bvt5wev67b/root:/.
> - The Write Text request supports file size up to 30 MB.
> - **Re-enter Support**: If target path is invalid, system will prompt to re-enter the target path.

- **Output**:

| **Parameter Name** | **Parameter Type** | **Example**                                                            |
|--------------------|--------------------|------------------------------------------------------------------------|
| File               | Entity             | {name : "ABC", extension:".txt" , "Absolute Path" : "drive/root:/ABC"} |
| Message            | Text               | File uploaded successfully                                             |
| Success            | Yes/No             | Yes                                                                    |

#### 17. Changes Since

- **Description**: This endpoint produces a list of recent updates within a specified folder since a given date. The
  updates include information about the path, file status (e.g., Created, Updated, Deleted), and can be filtered using
  an optional regex in the search variable.
- **Input Parameters**:

| **Parameter Name** | **Parameter Type** | **Mandatory** | **Example**                 |
|--------------------|--------------------|---------------|-----------------------------|
| Source Path        | Text               | Yes           | drive/root:/ABC/folderName  |
| Since              | Date               | Yes           | 2023-12-29 T03 \: 54 \: 26Z |
| Search             | Text               | No            | WeeklyDemo                  |

> #### Notes :
> - **Re-enter Support**: If source path is invalid, system will prompt to re-enter the source path.

**Output**:

| **Parameter Name** | **Parameter Type** | **Example**                                 |
|--------------------|--------------------|---------------------------------------------|
| Result             | Custom Field       | [{Path: Text, Date: Date, Update: Created}] |
| Message            | Text               | File content                                |
| Success            | Yes/No             | Yes                                         |

> #### Notes :
> - The Changes Since request supports only for Personal drive folders.
> - It supports only for files/folders having version history.
> - File **Deleted** event is not supported.
> - For reference, path of Personal drive starts with drive/root:/ and files in Shared drives starts with drives/b!
    absd69bfb33bvt5wev67b/root:/.

#### 18. When a File is Changed

- **Description**: Monitors the Cloud Storage for changes. When a change arrives and the affected files/folders match
  the source path and event type, the event will be triggered.
- **Input Parameters**:

| **Parameter Name** | **Parameter Type** | **Mandatory** | **Example**                              |
|--------------------|--------------------|---------------|------------------------------------------|
| Source             | Text               | Yes           | drive/root:/ABC/folderName               |
| Event              | Pick One           | No            | {"Created","Updated","Deleted","Shared"} |
| Search             | Text               | No            | WeeklyDemo                               |

- **Output**:

| **Parameter Name** | **Parameter Type** | **Example**                                                            |
|--------------------|--------------------|------------------------------------------------------------------------|
| File               | Entity             | {name : "ABC", extension:".txt" , "Absolute Path" : "drive/root:/ABC"} |
| Event              | Text               | Updated                                                                |

> #### Notes :
> - If extension is upgraded to new version of OneDrive Files Extension, It is necessary to **Validate Attributes**
    again to continue receiving Notification.
> - File entity in response does not contain **Permissions** and **Shared With**.
> - The When a File is Changed request supports only **Created** and **Updated** events, **Deleted** and **Shared**
    events are not supported.
> - The When a File is Changed request supports only for Personal drive folders and file.
> - The When a File is Changed request notifies events occurred in sub folders.
> - Average time of receiving event notification is 1 minute and maximum 5 minutes.
> - For reference, path of Personal drive starts with drive/root:/ and files in Shared drives starts with drives/b!
    absd69bfb33bvt5wev67b/root:/.

#### 19. Create Folder

- **Description** Accept Folder Name and Path as input and create folder at given path.
- **Input Parameters**:

| **Parameter Name** | **Parameter Type** | **Mandatory** | **Example**     |
|--------------------|--------------------|---------------|-----------------|
| Folder Name        | Text               | Yes           | New Folder      |
| Path               | Text               | Yes           | drive/root:/ABC |

**Output**:

| **Parameter Name** | **Parameter Type** | **Example**                                                                              |
|--------------------|--------------------|------------------------------------------------------------------------------------------|
| File               | Entity             | {name : "New Folder", extension:".txt" , "Absolute Path" : "drive/root:/ABC/New Folder"} |
| Message            | Text               | Folder created successfully                                                              |
| Success            | Yes/No             | Yes                                                                                      |

> #### Notes : Examples for providing path
> - Path for personal drive folder - drive/root:/ABC
> - Path for root - drive/root:/
> - Path for shared drive - /drives/b!jxUeN0DgA02sUFccW1Pb1lXRHihDmDNCmAiRj77wj4mIw359lmkzQYFiwAJ5WZk3/root:
    /TestAllFiles

### OneDrive Area

#### 1. Search By Permissions

- **Description**: Accepts Permissions and returns Task ID and This Task ID can be used in "Get Result" Catalog Request
  to fetch Files
- **Input Parameters**:

| **Parameter Name** | **Parameter Type** | **Mandatory** | **Example**                             |
|--------------------|--------------------|---------------|-----------------------------------------|
| Permission         | Pick One           | Yes           | {"Read","Write","Read and Write","Own"} |

> #### Notes :
> - The Search By Permissions request supports only for Personal drive folders and file.
> - Response of entity search does not support **Permissions** and **Shared With**.
> - The Search By Permissions request does not support **Read and Write**, **Own** permissions.
> - To collect list of files use **Task ID** in Get Result catalog request.

- **Output**:

| **Parameter Name** | **Parameter Type** | **Example**                 |
|--------------------|--------------------|-----------------------------|
| Task ID            | Text               | 36rttv2fgfgff3h334grf3ugbbv |

#### 2. Write with Custom File Name

- **Description**: Upload the content of the file from the media server to the cloud storage at target path with Custom
  File name.
  Produced File Entity represent the file in the cloud storage.
-
- **Input Parameters**:

| **Parameter Name** | **Parameter Type** | **Mandatory** | **Example**                |
|--------------------|--------------------|---------------|----------------------------|
| Krista File        | File               | Yes           | Test.pdf                   |
| Custom Name        | Text               | No            | Test Demo.pdf              |
| Target Path        | Text               | Yes           | drive/root:/ABC/FolderName |

> #### Notes :
> - The Write with Custom File Name request supports file size up to 50 MB.
> - The Write with Custom File Name request does not offer support for uploading of multiple files.
> - The Write with Custom File Name request supports Shared drives.
> - For reference, path of Personal drive starts with drive/root:/ and files in Shared drives starts with drives/b!
    absd69bfb33bvt5wev67b/root:/.

- **Output**:

| **Parameter Name** | **Parameter Type** | **Example**                                                            |
|--------------------|--------------------|------------------------------------------------------------------------|
| File               | Entity             | {name : "ABC", extension:".txt" , "Absolute Path" : "drive/root:/ABC"} |
| Message            | Text               | File uploaded successfully                                             |
| Success            | Yes/No             | Yes                                                                    |

## Entity Request

Krista OneDrive Extension supports the following Entity Request:

#### File

> #### Notes :
> - File Entity supports only for Personal drive folders and file.

* Search By File
    * Description: Returns list of File Details.
    * Input Parameters:

| **Parameter Name** | **Parameter Type**                           | **Mandatory** | **Example**                                                                |
|--------------------|----------------------------------------------|---------------|----------------------------------------------------------------------------|
| Name               | Text                                         | No            | Sample.xlsx                                                                |
| Base Name          | Text                                         | No            | Sample                                                                     |
| Extension          | Text                                         | No            | xlsx                                                                       |
| Parent Folder      | Text                                         | No            | drive/root:/ABC                                                            |
| Absolute Path      | Text                                         | No            | drive/root:/ABC/FolderName/Sample.xlsx                                     |
| Owner              | Text                                         | No            | John Doe                                                                   |
| Created            | Date                                         | No            | 2023-12-29 T03 \: 43 \: 26Z to 2024-1-20 T03 \: 43 \: 26Z                  |
| Modified           | Date                                         | No            | 2023-12-29 T03 \: 54 \: 26Z to 2024-1-20 T03 \: 43 \: 26Z                  |
| Size               | Number                                       | No            | 2000 bytes to 500000 bytes                                                 |
| Permissions        | PickOne                                      | No            | write                                                                      |
| Is Folder          | Yes/No                                       | No            | No                                                                         |
| Tags               | List&lt;Text>                                | No            | ""                                                                         |
| Shared With        | List&lt;Multi Field<User Email, Permission>> | No            | [{"john.doe@contoso.com" , "Write" },{"john.doe23@contoso.com" , "Read" }] |
| Created By         | Text                                         | No            | john.doe@contoso.com                                                       |
| Modified By        | Text                                         | No            | john.doe@contoso.com                                                       |

> #### Notes :
> - Files can also be search using relative path, For Example : /ABC/fileName/Sample.xlsx .
> - To search Parent Folder at root use path as : /drive/root:
> - Entity search using Permission is not supported.
> - Response of entity search does not support **Permissions** and **Shared With**.
> - **Tags** are not Supported in OneDrive Files extension.
> - **Size**, **Modified** and **Created** search is supported using Ask System Step.
> - For Example : By using Ask System filters Size >= 2000 and Size <= 5000.

* Output:

| **Parameter Name** | **Parameter Type** | **Description**             |
|--------------------|--------------------|-----------------------------|
| File               | List&lt;File>      | File represents File Entity |

## Re-entering Information

For most of the catalog requests in the OneDrive Files extension, if you provide incorrect or invalid input, the system
will prompt
you to re-enter the information. This feature helps ensure that operations are performed with valid data and reduces
errors

> #### Re-entry Process :
> - When invalid input is detected, you will receive a prompt asking if you want to re-enter the information.
> - Select "Yes" to provide corrected information.
> - The system will validate the new input before proceeding with the operation.
> - This process helps prevent errors and ensures successful completion of requests.

## Error Handling

The OneDrive Files extension includes comprehensive error handling to provide clear feedback when operations cannot be
completed:

> #### Common Error Scenarios :
> - **Invalid Path**: If a file or folder path doesn't exist or is inaccessible.