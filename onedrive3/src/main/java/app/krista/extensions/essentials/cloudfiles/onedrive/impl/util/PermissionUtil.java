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

import app.krista.extensions.essentials.cloudfiles.onedrive.OnedriveAttributes;
import com.microsoft.graph.http.GraphServiceException;
import com.microsoft.graph.models.IdentitySet;
import com.microsoft.graph.models.Permission;
import com.microsoft.graph.models.SharePointIdentitySet;
import com.microsoft.graph.requests.PermissionCollectionPage;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class PermissionUtil {

    public static final String DRIVE_ITEMS = "drive/items";

    private PermissionUtil() {
    }

    public static List<Map<String, Object>> getPermissions(PermissionCollectionPage permissionCollectionPage) {
        try {
            List<Map<String, Object>> mapList = new ArrayList<>();

            while (permissionCollectionPage != null && !permissionCollectionPage.getCurrentPage().isEmpty()) {
                if (permissionCollectionPage.additionalDataManager() != null
                        && Objects.requireNonNullElse(permissionCollectionPage.additionalDataManager().get(Constants.ODATA_CONTEXT), "")
                        .toString().contains(DRIVE_ITEMS)) {
                    iterateAddDataPermission(permissionCollectionPage, mapList);
                } else {
                    iteratePermissionsFromDriveItem(permissionCollectionPage, mapList);
                }
                if (permissionCollectionPage.getNextPage() != null) {
                    permissionCollectionPage = permissionCollectionPage.getNextPage().buildRequest().get();
                } else {
                    break;
                }
            }
            return mapList;
        } catch (GraphServiceException cause) {
            cause.printStackTrace();
            throw new IllegalArgumentException(cause.getMessage(), cause);
        }
    }

    private static void iterateAddDataPermission(PermissionCollectionPage permissionCollectionPage, List<Map<String, Object>> mapList) {
        for (Permission permission : permissionCollectionPage.getCurrentPage()) {
            if (permission.grantedToV2 != null) {
                Map<String, Object> sharedUser = new HashMap<>();
                String email = permission.grantedToV2.user != null
                        ? Objects.requireNonNullElse(permission.grantedToV2.user
                        .additionalDataManager().get(Constants.EMAIL), permission.grantedToV2.user.displayName).toString() : "";
                if (email.isBlank()) {
                    email = permission.grantedToV2.siteUser != null ? Objects.requireNonNullElse(permission.grantedToV2.siteUser
                            .additionalDataManager().get(Constants.EMAIL), permission.grantedToV2.siteUser.displayName).toString() : "";
                }
                assert email != null;
                sharedUser.put(Constants.USER_EMAIL, email.replace("\"", ""));
                sharedUser.put(Constants.PERMISSION, getRoleFromPermission(permission));
                mapList.add(sharedUser);
            } else if (permission.grantedToIdentitiesV2 != null) {
                for (SharePointIdentitySet set : permission.grantedToIdentitiesV2) {
                    Map<String, Object> sharedUser = new HashMap<>();
                    String email = set.user != null ? Objects.requireNonNullElse(set.user.additionalDataManager().get(Constants.EMAIL), set.user.displayName).toString() : "";
                    if (email.isBlank()) {
                        email = set.siteUser != null ? Objects.requireNonNullElse(set.siteUser.additionalDataManager().get(Constants.EMAIL), set.siteUser.displayName).toString() : "";
                    }
                    sharedUser.put(Constants.USER_EMAIL, email.replace("\"", ""));
                    sharedUser.put(Constants.PERMISSION, permission.roles != null ? toSentenceCase(permission.roles.get(0)) : "");
                    mapList.add(sharedUser);
                }
            }
        }
    }

    @NotNull
    private static String getRoleFromPermission(Permission permission) {
        return permission.roles != null && !permission.roles.isEmpty() && !permission.roles.get(0).equals("owner") ? toSentenceCase(permission.roles.get(0)) : Constants.OWN;
    }

    private static void iteratePermissionsFromDriveItem(PermissionCollectionPage permissionCollectionPage, List<Map<String, Object>> mapList) {
        for (Permission permission : permissionCollectionPage.getCurrentPage()) {
            if (permission.grantedTo != null) {
                Map<String, Object> sharedUser = new HashMap<>();
                String email = permission.grantedTo.user != null
                        ? Objects.requireNonNullElse(permission.grantedTo.user.additionalDataManager().get(Constants.EMAIL), permission.grantedTo.user.displayName).toString()
                        : "";
                sharedUser.put(Constants.USER_EMAIL, email.replace("\"", ""));
                sharedUser.put(Constants.PERMISSION, getRoleFromPermission(permission));
                mapList.add(sharedUser);
            } else if (permission.grantedToIdentities != null) {
                for (IdentitySet set : permission.grantedToIdentities) {
                    Map<String, Object> sharedUser = new HashMap<>();
                    String email = set.user != null ? Objects.requireNonNullElse(set.user.additionalDataManager().get(Constants.EMAIL), set.user.displayName).toString() : "";
                    sharedUser.put(Constants.USER_EMAIL, email.replace("\"", ""));
                    sharedUser.put(Constants.PERMISSION, permission.roles != null ? toSentenceCase(permission.roles.get(0)) : "");
                    mapList.add(sharedUser);
                }
            }
        }
    }

    public static String getOwnerPermissions(PermissionCollectionPage permissionCollectionPage, OnedriveAttributes onedriveAttributes) {
        try {
            while (permissionCollectionPage != null && !permissionCollectionPage.getCurrentPage().isEmpty()) {
                if (permissionCollectionPage.additionalDataManager() != null
                        && Objects.requireNonNullElse(permissionCollectionPage.additionalDataManager()
                        .get(Constants.ODATA_CONTEXT), "").toString().contains(DRIVE_ITEMS)) {
                    Permission permission = permissionCollectionPage.getCurrentPage().get(permissionCollectionPage.getCurrentPage().size() - 1);
                    if (permission != null && permission.grantedToV2 != null && permission.grantedToV2.user != null) {
                        String email = permission.grantedToV2.user.additionalDataManager().get(Constants.EMAIL) != null
                                ? permission.grantedToV2.user.additionalDataManager().get(Constants.EMAIL).getAsString() : "";

                        return email.equals(onedriveAttributes.getMailId()) ? Constants.OWN : Constants.WRITE;
                    }
                } else {
                    Permission permissionGranted = permissionCollectionPage.getCurrentPage().get(permissionCollectionPage.getCurrentPage().size() - 1);
                    if (permissionGranted != null && permissionGranted.grantedTo != null && permissionGranted.grantedTo.user != null) {
                        String email = permissionGranted.grantedTo.user.additionalDataManager().get(Constants.EMAIL) != null
                                ? permissionGranted.grantedTo.user.additionalDataManager().get(Constants.EMAIL).getAsString() : permissionGranted.grantedTo.user.displayName;

                        return email.equals(onedriveAttributes.getMailId()) ? Constants.OWN : Constants.WRITE;
                    }
                    for (Permission permission : permissionCollectionPage.getCurrentPage()) {
                        if (permission != null && permission.grantedToIdentities != null && permission.roles != null) {
                            for (IdentitySet identity : permission.grantedToIdentities) {
                                if (identity.user != null) {
                                    String email = identity.user.additionalDataManager().get(Constants.EMAIL) != null
                                            ? identity.user.additionalDataManager().get(Constants.EMAIL).getAsString() : "";
                                    return email.equals(onedriveAttributes.getMailId()) ? permission.roles.toString() : null;
                                }
                            }
                        }
                    }

                }
                if (permissionCollectionPage.getNextPage() != null) {
                    permissionCollectionPage = permissionCollectionPage.getNextPage().buildRequest().get();
                } else {
                    break;
                }
            }
        } catch (GraphServiceException cause) {
            cause.printStackTrace();
            throw new IllegalArgumentException(cause.getMessage(), cause);
        }
        return null;
    }

    private static String toSentenceCase(String role) {
        return role.substring(0, 1).toUpperCase() + role.substring(1).toLowerCase();
    }
}
