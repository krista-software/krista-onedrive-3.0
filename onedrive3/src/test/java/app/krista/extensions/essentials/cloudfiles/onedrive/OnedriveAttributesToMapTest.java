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

import app.krista.extensions.essentials.cloudfiles.onedrive.impl.util.Constants;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.*;

/**
 * Tests for {@link OnedriveAttributes#toMap()}.
 */
public class OnedriveAttributesToMapTest {

    private static final String BASE_URL = "http://localhost:8765";

    // ── All fields populated, non-cert auth ─────────────────────────────────

    @Test
    public void testToMap_allFieldsPopulated_nonCert() {
        OnedriveAttributes attrs = new OnedriveAttributes(
                BASE_URL, "cid", "csecret", "user@test.com", "tid", "Public");
        attrs.setPublicAuthMode("own");

        Map<String, Object> map = attrs.toMap();

        assertEquals("cid", map.get(Constants.CLIENT_ID));
        assertEquals("csecret", map.get(Constants.CLIENT_SECRET));
        assertEquals("tid", map.get(Constants.TENANT_ID));
        assertEquals("user@test.com", map.get(Constants.EMAIL));
        assertEquals("Public", map.get(Constants.AUTH_TYPE));
        assertEquals("own", map.get("publicAuthMode"));
        assertFalse("Non-cert auth should not include certificateContent",
                map.containsKey(Constants.CERTIFICATE_CONTENT));
        assertFalse("Non-cert auth should not include certificatePassword",
                map.containsKey(Constants.CERTIFICATE_PASSWORD));
    }

    // ── Null fields are omitted ─────────────────────────────────────────────

    @Test
    public void testToMap_nullClientId_omitted() {
        // Public auth without publicAuthMode="own" is Krista Managed (Quick Setup).
        // Credentials must never appear in the map for Quick Setup, even if
        // the constructor fills clientId from config.properties.
        OnedriveAttributes attrs = new OnedriveAttributes(
                BASE_URL, null, "csecret", "user@test.com", "tid", "Public");

        Map<String, Object> map = attrs.toMap();

        assertFalse("Krista Managed credentials should not appear in toMap()",
                map.containsKey(Constants.CLIENT_ID));
    }

    @Test
    public void testToMap_nullEmail_omitted() {
        OnedriveAttributes attrs = new OnedriveAttributes(
                BASE_URL, "cid", "csecret", null, "tid", "Public");

        Map<String, Object> map = attrs.toMap();

        assertFalse("Null email should be omitted", map.containsKey(Constants.EMAIL));
    }

    @Test
    public void testToMap_nullTenantId_omitted() {
        OnedriveAttributes attrs = new OnedriveAttributes(
                BASE_URL, "cid", "csecret", "user@test.com", null, "Public");

        Map<String, Object> map = attrs.toMap();

        assertFalse("Null tenantId should be omitted", map.containsKey(Constants.TENANT_ID));
    }

    @Test
    public void testToMap_nullAuthType_omitted() {
        OnedriveAttributes attrs = new OnedriveAttributes(
                BASE_URL, "cid", "csecret", "user@test.com", "tid", null);

        Map<String, Object> map = attrs.toMap();

        assertFalse("Null authType should be omitted", map.containsKey(Constants.AUTH_TYPE));
    }

    // ── publicAuthMode null vs non-null ─────────────────────────────────────

    @Test
    public void testToMap_publicAuthModeNull_omitted() {
        OnedriveAttributes attrs = new OnedriveAttributes(
                BASE_URL, "cid", "csecret", "user@test.com", "tid", "Public");
        // publicAuthMode is null by default

        Map<String, Object> map = attrs.toMap();

        assertFalse("Null publicAuthMode should be omitted", map.containsKey("publicAuthMode"));
    }

    @Test
    public void testToMap_publicAuthModeKrista_included() {
        OnedriveAttributes attrs = new OnedriveAttributes(
                BASE_URL, "cid", "csecret", "user@test.com", "tid", "Public");
        attrs.setPublicAuthMode("krista");

        Map<String, Object> map = attrs.toMap();

        assertEquals("krista", map.get("publicAuthMode"));
    }

    // ── ClientCredentialsCert auth type — cert fields included ──────────────

    @Test
    public void testToMap_certAuth_certFieldsIncluded() {
        OnedriveAttributes attrs = new OnedriveAttributes(
                BASE_URL, "cid", "", "user@test.com", "tid",
                Constants.CLIENT_CREDENTIALS_CERT, "dGVzdA==", "certPass");

        Map<String, Object> map = attrs.toMap();

        assertEquals(Constants.CLIENT_CREDENTIALS_CERT, map.get(Constants.AUTH_TYPE));
        assertEquals("dGVzdA==", map.get(Constants.CERTIFICATE_CONTENT));
        assertEquals("certPass", map.get(Constants.CERTIFICATE_PASSWORD));
    }

    @Test
    public void testToMap_certAuth_nullCertContent_omitted() {
        OnedriveAttributes attrs = new OnedriveAttributes(
                BASE_URL, "cid", "", "user@test.com", "tid",
                Constants.CLIENT_CREDENTIALS_CERT, null, "certPass");

        Map<String, Object> map = attrs.toMap();

        assertFalse("Null certificateContent should be omitted",
                map.containsKey(Constants.CERTIFICATE_CONTENT));
        assertEquals("certPass", map.get(Constants.CERTIFICATE_PASSWORD));
    }

    @Test
    public void testToMap_certAuth_nullCertPassword_omitted() {
        OnedriveAttributes attrs = new OnedriveAttributes(
                BASE_URL, "cid", "", "user@test.com", "tid",
                Constants.CLIENT_CREDENTIALS_CERT, "dGVzdA==", null);

        Map<String, Object> map = attrs.toMap();

        assertEquals("dGVzdA==", map.get(Constants.CERTIFICATE_CONTENT));
        assertFalse("Null certificatePassword should be omitted",
                map.containsKey(Constants.CERTIFICATE_PASSWORD));
    }

    @Test
    public void testToMap_certAuth_bothCertFieldsNull_omitted() {
        OnedriveAttributes attrs = new OnedriveAttributes(
                BASE_URL, "cid", "", "user@test.com", "tid",
                Constants.CLIENT_CREDENTIALS_CERT, null, null);

        Map<String, Object> map = attrs.toMap();

        assertFalse(map.containsKey(Constants.CERTIFICATE_CONTENT));
        assertFalse(map.containsKey(Constants.CERTIFICATE_PASSWORD));
    }

    // ── Non-cert auth types do NOT include cert fields ──────────────────────

    @Test
    public void testToMap_privateAuth_noCertFields() {
        OnedriveAttributes attrs = new OnedriveAttributes(
                BASE_URL, "cid", "csecret", "user@test.com", "tid", "Private");

        Map<String, Object> map = attrs.toMap();

        assertEquals("Private", map.get(Constants.AUTH_TYPE));
        assertFalse(map.containsKey(Constants.CERTIFICATE_CONTENT));
        assertFalse(map.containsKey(Constants.CERTIFICATE_PASSWORD));
    }

    @Test
    public void testToMap_publicAuth_noCertFields() {
        OnedriveAttributes attrs = new OnedriveAttributes(
                BASE_URL, "cid", "csecret", "user@test.com", "tid", "Public");

        Map<String, Object> map = attrs.toMap();

        assertFalse(map.containsKey(Constants.CERTIFICATE_CONTENT));
        assertFalse(map.containsKey(Constants.CERTIFICATE_PASSWORD));
    }

    // ── Return type verification ────────────────────────────────────────────

    @Test
    public void testToMap_returnsNewMapInstance() {
        OnedriveAttributes attrs = new OnedriveAttributes(
                BASE_URL, "cid", "csecret", "user@test.com", "tid", "Public");

        Map<String, Object> map1 = attrs.toMap();
        Map<String, Object> map2 = attrs.toMap();

        assertNotSame("Each call should return a new map", map1, map2);
        assertEquals("Maps should be equal", map1, map2);
    }

    // ── Credential inclusion based on publicAuthMode / authType ──────────

    @Test
    public void testToMap_quickSetup_kristaMode_excludesCredentials() {
        OnedriveAttributes attrs = new OnedriveAttributes(
                BASE_URL, null, null, "user@test.com", null, "Public");
        attrs.setPublicAuthMode("krista");

        Map<String, Object> map = attrs.toMap();

        assertEquals("user@test.com", map.get(Constants.EMAIL));
        assertEquals("Public", map.get(Constants.AUTH_TYPE));
        assertEquals("krista", map.get("publicAuthMode"));
        // Krista-managed credentials must NEVER appear in the map
        assertFalse("Quick Setup toMap() must not include clientId", map.containsKey(Constants.CLIENT_ID));
        assertFalse("Quick Setup toMap() must not include clientSecret", map.containsKey(Constants.CLIENT_SECRET));
        assertFalse("Quick Setup toMap() must not include tenantId", map.containsKey(Constants.TENANT_ID));
    }

    @Test
    public void testToMap_publicAuth_nullPublicAuthMode_excludesCredentials() {
        // publicAuthMode was not set (legacy or default) — credentials must NOT leak
        OnedriveAttributes attrs = new OnedriveAttributes(
                BASE_URL, "cid", "csecret", "user@test.com", "tid", "Public");
        // publicAuthMode is null by default (not set via setPublicAuthMode)

        Map<String, Object> map = attrs.toMap();

        // Null publicAuthMode is not "own" and authType is not Private/Cert → no credentials
        assertFalse("Null publicAuthMode must not include clientId", map.containsKey(Constants.CLIENT_ID));
        assertFalse("Null publicAuthMode must not include clientSecret", map.containsKey(Constants.CLIENT_SECRET));
        assertFalse("Null publicAuthMode must not include tenantId", map.containsKey(Constants.TENANT_ID));
    }

    @Test
    public void testToMap_userAuth_ownMode_includesCredentials() {
        OnedriveAttributes attrs = new OnedriveAttributes(
                BASE_URL, "own-cid", "own-csecret", "user@test.com", "own-tid", "Public");
        attrs.setPublicAuthMode("own");

        Map<String, Object> map = attrs.toMap();

        assertEquals("own", map.get("publicAuthMode"));
        assertEquals("own-cid", map.get(Constants.CLIENT_ID));
        assertEquals("own-csecret", map.get(Constants.CLIENT_SECRET));
        assertEquals("own-tid", map.get(Constants.TENANT_ID));
    }

    @Test
    public void testToMap_privateAuth_includesCredentials() {
        OnedriveAttributes attrs = new OnedriveAttributes(
                BASE_URL, "priv-cid", "priv-cs", "admin@test.com", "priv-tid", "Private");

        Map<String, Object> map = attrs.toMap();

        assertEquals("Private", map.get(Constants.AUTH_TYPE));
        assertEquals("priv-cid", map.get(Constants.CLIENT_ID));
        assertEquals("priv-cs", map.get(Constants.CLIENT_SECRET));
        assertEquals("priv-tid", map.get(Constants.TENANT_ID));
        // No publicAuthMode for Private auth
        assertFalse(map.containsKey("publicAuthMode"));
    }

    @Test
    public void testToMap_certAuth_includesCredentialsAndCertFields() {
        OnedriveAttributes attrs = new OnedriveAttributes(
                BASE_URL, "cert-cid", "", "admin@test.com", "cert-tid",
                Constants.CLIENT_CREDENTIALS_CERT, "dGVzdA==", "certPass");

        Map<String, Object> map = attrs.toMap();

        assertEquals(Constants.CLIENT_CREDENTIALS_CERT, map.get(Constants.AUTH_TYPE));
        assertEquals("cert-cid", map.get(Constants.CLIENT_ID));
        assertEquals("dGVzdA==", map.get(Constants.CERTIFICATE_CONTENT));
        assertEquals("certPass", map.get(Constants.CERTIFICATE_PASSWORD));
    }
}
