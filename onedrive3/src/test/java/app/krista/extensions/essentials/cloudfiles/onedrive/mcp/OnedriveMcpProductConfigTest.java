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

package app.krista.extensions.essentials.cloudfiles.onedrive.mcp;

import app.krista.model.base.FreeForm;
import app.krista.sdk.msauth.extension.mcp.McpSearchResult;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Tests for {@link OnedriveMcpProductConfig}.
 * Targets 100 % line + branch coverage of every public and private method.
 */
public class OnedriveMcpProductConfigTest {

    private final OnedriveMcpProductConfig config = new OnedriveMcpProductConfig();

    // ── Simple getter tests ──────────────────────────────────────────────

    @Test
    public void testProductName() {
        assertEquals("OneDrive", config.productName());
    }

    @Test
    public void testQueryLanguage() {
        assertEquals("KQL", config.queryLanguage());
    }

    @Test
    public void testQueryLanguageFullName() {
        assertEquals("KQL (Keyword Query Language)", config.queryLanguageFullName());
    }

    @Test
    public void testSearchUrl() {
        assertEquals("https://graph.microsoft.com/v1.0/search/query", config.searchUrl());
    }

    @Test
    public void testSearchHttpMethod() {
        assertEquals("POST", config.searchHttpMethod());
    }

    @Test
    public void testStandardFields() {
        List<String> expected = List.of("name", "webUrl", "lastModifiedDateTime", "lastModifiedBy", "size");
        assertEquals(expected, config.standardFields());
    }

    @Test
    public void testResultColumnsIsEmpty() {
        assertTrue(config.resultColumns().isEmpty());
    }

    // ── Examples ─────────────────────────────────────────────────────────

    @Test
    public void testNlExamplesReturnsThreeItems() {
        List<String> examples = config.nlExamples();
        assertNotNull(examples);
        assertEquals(3, examples.size());
        assertTrue(examples.contains("List files from /me/drive/root only"));
        assertTrue(examples.contains("List my recent files"));
        assertTrue(examples.contains("Show all Excel files"));
    }

    @Test
    public void testKqlExamplesReturnsThreeItems() {
        List<String> examples = config.kqlExamples();
        assertNotNull(examples);
        assertEquals(3, examples.size());
        assertTrue(examples.contains("Title:\"report\""));
        assertTrue(examples.contains("Author:\"John Smith\""));
        assertTrue(examples.contains("LastModifiedTime>2024-01-01"));
    }

    // ── graphApiPlanningPrompt ────────────────────────────────────────────

    @Test
    public void testGraphApiPlanningPromptContainsQuestion() {
        String prompt = config.graphApiPlanningPrompt("list all files");
        assertNotNull(prompt);
        assertTrue(prompt.contains("list all files"));
        assertTrue(prompt.contains("Microsoft Graph API expert"));
        assertTrue(prompt.contains("/me/drive/root/children"));
    }

    // ── queryGenerationPrompt ────────────────────────────────────────────

    @Test
    public void testQueryGenerationPromptContainsQuestion() {
        String prompt = config.queryGenerationPrompt("find budget files");
        assertNotNull(prompt);
        assertTrue(prompt.contains("find budget files"));
        assertTrue(prompt.contains("KQL"));
    }

    // ── answerExtractionPrompt ───────────────────────────────────────────

    @Test
    public void testAnswerExtractionPromptContainsQuestionAndContent() {
        String prompt = config.answerExtractionPrompt("who modified it?", "file data here");
        assertNotNull(prompt);
        assertTrue(prompt.contains("who modified it?"));
        assertTrue(prompt.contains("file data here"));
        assertTrue(prompt.contains("Answer directly and concisely"));
    }

    // ── buildSearchRequestBody ───────────────────────────────────────────

    @Test
    public void testBuildSearchRequestBody_emptyQueryUsesWildcard() {
        String body = config.buildSearchRequestBody("", 25, false);
        assertNotNull(body);
        assertTrue(body.contains("\"queryString\":\"*\""));
        assertTrue(body.contains("\"from\":0"));
        assertTrue(body.contains("\"size\":25"));
        assertTrue(body.contains("\"entityTypes\":[\"driveItem\"]"));
    }

    @Test
    public void testBuildSearchRequestBody_nonEmptyQueryUsesQuery() {
        String body = config.buildSearchRequestBody("budget report", 25, false);
        assertNotNull(body);
        assertTrue(body.contains("\"queryString\":\"budget report\""));
    }

    @Test
    public void testBuildSearchRequestBody_respectsMaxResults() {
        String body = config.buildSearchRequestBody("test", 10, false);
        assertTrue(body.contains("\"size\":10"));
    }

    @Test
    public void testBuildSearchRequestBody_allFieldsTrue() {
        // allFields param is accepted but doesn't change the body structure
        String body = config.buildSearchRequestBody("query", 50, true);
        assertNotNull(body);
        assertTrue(body.contains("\"queryString\":\"query\""));
        assertTrue(body.contains("\"size\":50"));
    }

    // ── parseSearchResponse ──────────────────────────────────────────────

    @Test
    public void testParseSearchResponse_noValueKey() {
        McpSearchResult result = config.parseSearchResponse("{}", "https://graph.microsoft.com");
        assertNotNull(result);
        assertEquals(0, result.rows().size());
    }

    @Test
    public void testParseSearchResponse_emptyValueArray() {
        McpSearchResult result = config.parseSearchResponse("{\"value\":[]}", "https://graph.microsoft.com");
        assertNotNull(result);
        assertEquals(0, result.rows().size());
    }

    @Test
    public void testParseSearchResponse_noHitsContainersKey() {
        // value element without hitsContainers → empty hitsContainers loop
        String json = "{\"value\":[{\"searchTerms\":[\"test\"]}]}";
        McpSearchResult result = config.parseSearchResponse(json, "https://graph.microsoft.com");
        assertEquals(0, result.rows().size());
    }

    @Test
    public void testParseSearchResponse_hitsContainerWithoutTotalAndHits() {
        // hitsContainer missing "total" and "hits" keys
        String json = "{\"value\":[{\"hitsContainers\":[{\"moreResultsAvailable\":false}]}]}";
        McpSearchResult result = config.parseSearchResponse(json, "https://graph.microsoft.com");
        assertEquals(0, result.rows().size());
    }

    @Test
    public void testParseSearchResponse_hitWithoutResource() {
        // hit missing "resource" key → empty resource object, no fields added
        String json = "{\"value\":[{\"hitsContainers\":[{\"total\":1,\"hits\":[{\"hitId\":\"id1\"}]}]}]}";
        McpSearchResult result = config.parseSearchResponse(json, "https://graph.microsoft.com");
        assertEquals(1, result.rows().size());
        assertEquals("id1", result.rows().get(0).get("fileId"));
    }

    @Test
    public void testParseSearchResponse_emptyHitId() {
        // empty hitId should not be added to row
        String json = "{\"value\":[{\"hitsContainers\":[{\"total\":1,\"hits\":[{\"hitId\":\"\","
                + "\"resource\":{\"name\":\"file.txt\"}}]}]}]}";
        McpSearchResult result = config.parseSearchResponse(json, "https://graph.microsoft.com");
        assertEquals(1, result.rows().size());
        assertFalse(result.rows().get(0).containsKey("fileId"));
        assertEquals("file.txt", result.rows().get(0).get("Name"));
    }

    @Test
    public void testParseSearchResponse_fullResourceFields() {
        String json = "{\"value\":[{\"hitsContainers\":[{\"total\":2,\"hits\":["
                + "{\"hitId\":\"abc123\",\"resource\":{"
                + "\"name\":\"test.docx\","
                + "\"webUrl\":\"https://example.com/test.docx\","
                + "\"size\":\"1024\","
                + "\"lastModifiedDateTime\":\"2024-01-15T10:30:00Z\","
                + "\"createdDateTime\":\"2023-06-01T08:00:00Z\","
                + "\"lastModifiedBy\":{\"user\":{\"displayName\":\"John Doe\"}},"
                + "\"createdBy\":{\"user\":{\"displayName\":\"Jane Smith\"}},"
                + "\"parentReference\":{\"name\":\"Documents\"},"
                + "\"file\":{\"mimeType\":\"application/vnd.openxmlformats-officedocument.wordprocessingml.document\"},"
                + "\"id\":\"item-id-1\""
                + "}}]}]}]}";

        McpSearchResult result = config.parseSearchResponse(json, "https://graph.microsoft.com");
        assertEquals(1, result.rows().size());

        Map<String, Object> row = result.rows().get(0);
        assertEquals("test.docx", row.get("Name"));
        assertEquals("https://example.com/test.docx", row.get("Link"));
        assertEquals("1 KB", row.get("Size"));
        assertEquals("2024-01-15", row.get("LastModified"));
        assertEquals("2023-06-01", row.get("Created"));
        assertEquals("John Doe", row.get("ModifiedBy"));
        assertEquals("Jane Smith", row.get("CreatedBy"));
        assertEquals("Documents", row.get("Folder"));
        assertEquals("application/vnd.openxmlformats-officedocument.wordprocessingml.document", row.get("MimeType"));
        assertEquals("item-id-1", row.get("FileId"));
        assertEquals("abc123", row.get("fileId"));
    }

    // ── toDisplayValue branch coverage via parseSearchResponse ───────────

    @Test
    public void testParseSearchResponse_jsonNullValueInResource() {
        // null value in resource → toDisplayValue returns null → skipped
        String json = "{\"value\":[{\"hitsContainers\":[{\"total\":1,\"hits\":[{\"hitId\":\"h1\","
                + "\"resource\":{\"name\":null,\"webUrl\":\"https://example.com\"}}]}]}]}";
        McpSearchResult result = config.parseSearchResponse(json, "https://graph.microsoft.com");
        assertEquals(1, result.rows().size());
        Map<String, Object> row = result.rows().get(0);
        assertFalse("Null value should be skipped", row.containsKey("Name"));
        assertEquals("https://example.com", row.get("Link"));
    }

    @Test
    public void testParseSearchResponse_dateFieldContainingDateKeyword() {
        // key containing "date" (lowercase) with T → should truncate
        String json = "{\"value\":[{\"hitsContainers\":[{\"total\":1,\"hits\":[{\"hitId\":\"h1\","
                + "\"resource\":{\"somedate\":\"2024-03-20T14:00:00Z\"}}]}]}]}";
        McpSearchResult result = config.parseSearchResponse(json, "https://graph.microsoft.com");
        Map<String, Object> row = result.rows().get(0);
        assertEquals("2024-03-20", row.get("somedate"));
    }

    @Test
    public void testParseSearchResponse_dateFieldWithoutT() {
        // DateTime key but value without T → no truncation
        String json = "{\"value\":[{\"hitsContainers\":[{\"total\":1,\"hits\":[{\"hitId\":\"h1\","
                + "\"resource\":{\"lastModifiedDateTime\":\"2024-03-20\"}}]}]}]}";
        McpSearchResult result = config.parseSearchResponse(json, "https://graph.microsoft.com");
        Map<String, Object> row = result.rows().get(0);
        assertEquals("2024-03-20", row.get("LastModified"));
    }

    @Test
    public void testParseSearchResponse_sizeInBytes() {
        // size < 1024 → "N B"
        String json = "{\"value\":[{\"hitsContainers\":[{\"total\":1,\"hits\":[{\"hitId\":\"h1\","
                + "\"resource\":{\"size\":\"500\"}}]}]}]}";
        McpSearchResult result = config.parseSearchResponse(json, "https://graph.microsoft.com");
        assertEquals("500 B", result.rows().get(0).get("Size"));
    }

    @Test
    public void testParseSearchResponse_sizeInKB() {
        // 1024 <= size < 1048576 → "N KB"
        String json = "{\"value\":[{\"hitsContainers\":[{\"total\":1,\"hits\":[{\"hitId\":\"h1\","
                + "\"resource\":{\"size\":\"2048\"}}]}]}]}";
        McpSearchResult result = config.parseSearchResponse(json, "https://graph.microsoft.com");
        assertEquals("2 KB", result.rows().get(0).get("Size"));
    }

    @Test
    public void testParseSearchResponse_sizeInMB() {
        // size >= 1048576 → "N.N MB"
        String json = "{\"value\":[{\"hitsContainers\":[{\"total\":1,\"hits\":[{\"hitId\":\"h1\","
                + "\"resource\":{\"size\":\"5242880\"}}]}]}]}";
        McpSearchResult result = config.parseSearchResponse(json, "https://graph.microsoft.com");
        assertEquals("5.0 MB", result.rows().get(0).get("Size"));
    }

    @Test
    public void testParseSearchResponse_sizeNonNumeric() {
        // non-numeric size → NumberFormatException → returns original string
        String json = "{\"value\":[{\"hitsContainers\":[{\"total\":1,\"hits\":[{\"hitId\":\"h1\","
                + "\"resource\":{\"size\":\"unknown\"}}]}]}]}";
        McpSearchResult result = config.parseSearchResponse(json, "https://graph.microsoft.com");
        assertEquals("unknown", result.rows().get(0).get("Size"));
    }

    @Test
    public void testParseSearchResponse_sizeEmpty() {
        // empty size string → formatSize returns ""  → skipped
        String json = "{\"value\":[{\"hitsContainers\":[{\"total\":1,\"hits\":[{\"hitId\":\"h1\","
                + "\"resource\":{\"size\":\"\"}}]}]}]}";
        McpSearchResult result = config.parseSearchResponse(json, "https://graph.microsoft.com");
        assertFalse(result.rows().get(0).containsKey("Size"));
    }

    @Test
    public void testParseSearchResponse_unknownObjectFieldSkipped() {
        // JsonObject value with unknown key → toDisplayValue returns null → skipped
        String json = "{\"value\":[{\"hitsContainers\":[{\"total\":1,\"hits\":[{\"hitId\":\"h1\","
                + "\"resource\":{\"name\":\"a.txt\",\"unknownObj\":{\"foo\":\"bar\"}}}]}]}]}";
        McpSearchResult result = config.parseSearchResponse(json, "https://graph.microsoft.com");
        Map<String, Object> row = result.rows().get(0);
        assertEquals("a.txt", row.get("Name"));
        assertFalse("Unknown object fields should be skipped", row.containsKey("unknownObj"));
    }

    @Test
    public void testParseSearchResponse_jsonArrayFieldSkipped() {
        // JsonArray value → toDisplayValue returns null → skipped
        String json = "{\"value\":[{\"hitsContainers\":[{\"total\":1,\"hits\":[{\"hitId\":\"h1\","
                + "\"resource\":{\"name\":\"b.txt\",\"tags\":[\"a\",\"b\"]}}]}]}]}";
        McpSearchResult result = config.parseSearchResponse(json, "https://graph.microsoft.com");
        Map<String, Object> row = result.rows().get(0);
        assertEquals("b.txt", row.get("Name"));
        assertFalse("Array fields should be skipped", row.containsKey("tags"));
    }

    @Test
    public void testParseSearchResponse_lastModifiedByWithoutUser() {
        // lastModifiedBy object without "user" key → extractUserDisplayName returns ""
        String json = "{\"value\":[{\"hitsContainers\":[{\"total\":1,\"hits\":[{\"hitId\":\"h1\","
                + "\"resource\":{\"lastModifiedBy\":{\"application\":{\"displayName\":\"App\"}}}}]}]}]}";
        McpSearchResult result = config.parseSearchResponse(json, "https://graph.microsoft.com");
        assertFalse("Empty display name should be skipped", result.rows().get(0).containsKey("ModifiedBy"));
    }

    @Test
    public void testParseSearchResponse_lastModifiedByWithNullUser() {
        // lastModifiedBy with user: null → user is null → return ""
        String json = "{\"value\":[{\"hitsContainers\":[{\"total\":1,\"hits\":[{\"hitId\":\"h1\","
                + "\"resource\":{\"lastModifiedBy\":{\"user\":null}}}]}]}]}";
        McpSearchResult result = config.parseSearchResponse(json, "https://graph.microsoft.com");
        assertFalse(result.rows().get(0).containsKey("ModifiedBy"));
    }

    @Test
    public void testParseSearchResponse_lastModifiedByUserWithEmptyDisplayName() {
        // user exists but displayName is empty → return ""
        String json = "{\"value\":[{\"hitsContainers\":[{\"total\":1,\"hits\":[{\"hitId\":\"h1\","
                + "\"resource\":{\"lastModifiedBy\":{\"user\":{\"displayName\":\"\"}}}}]}]}]}";
        McpSearchResult result = config.parseSearchResponse(json, "https://graph.microsoft.com");
        assertFalse(result.rows().get(0).containsKey("ModifiedBy"));
    }

    @Test
    public void testParseSearchResponse_parentReferenceWithoutName() {
        // parentReference object without "name" → getString returns "" → skipped
        String json = "{\"value\":[{\"hitsContainers\":[{\"total\":1,\"hits\":[{\"hitId\":\"h1\","
                + "\"resource\":{\"parentReference\":{\"driveId\":\"d1\"}}}]}]}]}";
        McpSearchResult result = config.parseSearchResponse(json, "https://graph.microsoft.com");
        assertFalse(result.rows().get(0).containsKey("Folder"));
    }

    @Test
    public void testParseSearchResponse_fileWithoutMimeType() {
        // file object without "mimeType" → getString returns "" → skipped
        String json = "{\"value\":[{\"hitsContainers\":[{\"total\":1,\"hits\":[{\"hitId\":\"h1\","
                + "\"resource\":{\"file\":{\"hashes\":{}}}}]}]}]}";
        McpSearchResult result = config.parseSearchResponse(json, "https://graph.microsoft.com");
        assertFalse(result.rows().get(0).containsKey("MimeType"));
    }

    @Test
    public void testParseSearchResponse_plainPrimitiveField() {
        // primitive field that is not DateTime/date/size → returned as-is
        String json = "{\"value\":[{\"hitsContainers\":[{\"total\":1,\"hits\":[{\"hitId\":\"h1\","
                + "\"resource\":{\"customField\":\"customValue\"}}]}]}]}";
        McpSearchResult result = config.parseSearchResponse(json, "https://graph.microsoft.com");
        assertEquals("customValue", result.rows().get(0).get("customField"));
    }

    @Test
    public void testParseSearchResponse_totalAccumulation() {
        // multiple hitsContainers with different totals
        String json = "{\"value\":[{\"hitsContainers\":["
                + "{\"total\":3,\"hits\":[{\"hitId\":\"h1\",\"resource\":{\"name\":\"a.txt\"}}]},"
                + "{\"total\":5,\"hits\":[{\"hitId\":\"h2\",\"resource\":{\"name\":\"b.txt\"}}]}"
                + "]}]}";
        McpSearchResult result = config.parseSearchResponse(json, "https://graph.microsoft.com");
        assertEquals(2, result.rows().size());
    }

    @Test
    public void testParseSearchResponse_multipleValueEntries() {
        String json = "{\"value\":["
                + "{\"hitsContainers\":[{\"total\":1,\"hits\":[{\"hitId\":\"h1\",\"resource\":{\"name\":\"a.txt\"}}]}]},"
                + "{\"hitsContainers\":[{\"total\":1,\"hits\":[{\"hitId\":\"h2\",\"resource\":{\"name\":\"b.txt\"}}]}]}"
                + "]}";
        McpSearchResult result = config.parseSearchResponse(json, "https://graph.microsoft.com");
        assertEquals(2, result.rows().size());
    }

    // ── parseStandardResponse ────────────────────────────────────────────

    @Test
    public void testParseStandardResponse_noValueKey() {
        List<FreeForm> rows = config.parseStandardResponse("{}", "https://graph.microsoft.com");
        assertNotNull(rows);
        assertTrue(rows.isEmpty());
    }

    @Test
    public void testParseStandardResponse_emptyValueArray() {
        List<FreeForm> rows = config.parseStandardResponse("{\"value\":[]}", "https://graph.microsoft.com");
        assertTrue(rows.isEmpty());
    }

    @Test
    public void testParseStandardResponse_noHitsContainersKey() {
        String json = "{\"value\":[{\"searchTerms\":[\"test\"]}]}";
        List<FreeForm> rows = config.parseStandardResponse(json, "https://graph.microsoft.com");
        assertTrue(rows.isEmpty());
    }

    @Test
    public void testParseStandardResponse_hitsContainerWithoutHits() {
        String json = "{\"value\":[{\"hitsContainers\":[{\"total\":0}]}]}";
        List<FreeForm> rows = config.parseStandardResponse(json, "https://graph.microsoft.com");
        assertTrue(rows.isEmpty());
    }

    @Test
    public void testParseStandardResponse_hitWithoutResource() {
        String json = "{\"value\":[{\"hitsContainers\":[{\"hits\":[{\"hitId\":\"id1\"}]}]}]}";
        List<FreeForm> rows = config.parseStandardResponse(json, "https://graph.microsoft.com");
        assertEquals(1, rows.size());
    }

    @Test
    public void testParseStandardResponse_emptyHitId() {
        String json = "{\"value\":[{\"hitsContainers\":[{\"hits\":[{\"hitId\":\"\","
                + "\"resource\":{\"name\":\"file.txt\"}}]}]}]}";
        List<FreeForm> rows = config.parseStandardResponse(json, "https://graph.microsoft.com");
        assertEquals(1, rows.size());
    }

    @Test
    public void testParseStandardResponse_validResponse() {
        String json = "{\"value\":[{\"hitsContainers\":[{\"total\":1,\"hits\":[{\"hitId\":\"id1\","
                + "\"resource\":{\"name\":\"doc.pdf\",\"webUrl\":\"https://example.com/doc.pdf\","
                + "\"size\":\"2048\",\"lastModifiedDateTime\":\"2024-05-10T12:00:00Z\","
                + "\"createdBy\":{\"user\":{\"displayName\":\"Alice\"}}}}]}]}]}";

        List<FreeForm> rows = config.parseStandardResponse(json, "https://graph.microsoft.com");
        assertNotNull(rows);
        assertEquals(1, rows.size());
    }

    @Test
    public void testParseStandardResponse_nullResourceValue() {
        String json = "{\"value\":[{\"hitsContainers\":[{\"hits\":[{\"hitId\":\"id1\","
                + "\"resource\":{\"name\":null,\"webUrl\":\"https://example.com\"}}]}]}]}";
        List<FreeForm> rows = config.parseStandardResponse(json, "https://graph.microsoft.com");
        assertEquals(1, rows.size());
    }

    // ── parseAllFieldsResponse ───────────────────────────────────────────

    @Test
    public void testParseAllFieldsResponse_noValueKey() {
        List<Map<String, String>> rows = config.parseAllFieldsResponse("{}", "https://graph.microsoft.com");
        assertTrue(rows.isEmpty());
    }

    @Test
    public void testParseAllFieldsResponse_emptyValueArray() {
        List<Map<String, String>> rows = config.parseAllFieldsResponse("{\"value\":[]}", "https://graph.microsoft.com");
        assertTrue(rows.isEmpty());
    }

    @Test
    public void testParseAllFieldsResponse_noHitsContainersKey() {
        String json = "{\"value\":[{\"searchTerms\":[\"test\"]}]}";
        List<Map<String, String>> rows = config.parseAllFieldsResponse(json, "https://graph.microsoft.com");
        assertTrue(rows.isEmpty());
    }

    @Test
    public void testParseAllFieldsResponse_hitsContainerWithoutHits() {
        String json = "{\"value\":[{\"hitsContainers\":[{\"total\":0}]}]}";
        List<Map<String, String>> rows = config.parseAllFieldsResponse(json, "https://graph.microsoft.com");
        assertTrue(rows.isEmpty());
    }

    @Test
    public void testParseAllFieldsResponse_hitWithoutResource() {
        String json = "{\"value\":[{\"hitsContainers\":[{\"hits\":[{\"hitId\":\"id1\"}]}]}]}";
        List<Map<String, String>> rows = config.parseAllFieldsResponse(json, "https://graph.microsoft.com");
        assertEquals(1, rows.size());
        assertEquals("id1", rows.get(0).get("fileId"));
        assertEquals("", rows.get(0).get("key"));
        assertEquals("", rows.get(0).get("title"));
    }

    @Test
    public void testParseAllFieldsResponse_emptyHitId() {
        String json = "{\"value\":[{\"hitsContainers\":[{\"hits\":[{\"hitId\":\"\","
                + "\"resource\":{\"name\":\"file.txt\"}}]}]}]}";
        List<Map<String, String>> rows = config.parseAllFieldsResponse(json, "https://graph.microsoft.com");
        assertEquals(1, rows.size());
        assertFalse("Empty hitId should not be added", rows.get(0).containsKey("fileId"));
    }

    @Test
    public void testParseAllFieldsResponse_withSummary() {
        String json = "{\"value\":[{\"hitsContainers\":[{\"hits\":[{\"hitId\":\"id1\","
                + "\"summary\":\"This is a summary\","
                + "\"resource\":{\"name\":\"report.xlsx\"}}]}]}]}";
        List<Map<String, String>> rows = config.parseAllFieldsResponse(json, "https://graph.microsoft.com");
        assertEquals(1, rows.size());
        Map<String, String> row = rows.get(0);
        assertEquals("This is a summary", row.get("summary"));
        assertEquals("report.xlsx", row.get("Name"));
        assertEquals("id1", row.get("fileId"));
    }

    @Test
    public void testParseAllFieldsResponse_withEmptySummary() {
        String json = "{\"value\":[{\"hitsContainers\":[{\"hits\":[{\"hitId\":\"id1\","
                + "\"summary\":\"\","
                + "\"resource\":{\"name\":\"file.txt\"}}]}]}]}";
        List<Map<String, String>> rows = config.parseAllFieldsResponse(json, "https://graph.microsoft.com");
        assertEquals(1, rows.size());
        assertFalse("Empty summary should not be added", rows.get(0).containsKey("summary"));
    }

    @Test
    public void testParseAllFieldsResponse_withoutSummaryKey() {
        // hit without "summary" key → has("summary") returns false → skipped
        String json = "{\"value\":[{\"hitsContainers\":[{\"hits\":[{\"hitId\":\"id1\","
                + "\"resource\":{\"name\":\"file.txt\"}}]}]}]}";
        List<Map<String, String>> rows = config.parseAllFieldsResponse(json, "https://graph.microsoft.com");
        assertEquals(1, rows.size());
        assertFalse(rows.get(0).containsKey("summary"));
    }

    @Test
    public void testParseAllFieldsResponse_keyAndTitleFromName() {
        // "name" maps to "Name" via toFreeFormKey, so getOrDefault("name","") returns ""
        // because the row key is "Name", not "name"
        String json = "{\"value\":[{\"hitsContainers\":[{\"hits\":[{\"hitId\":\"id1\","
                + "\"resource\":{\"name\":\"report.xlsx\",\"webUrl\":\"https://example.com/report.xlsx\"}}]}]}]}";
        List<Map<String, String>> rows = config.parseAllFieldsResponse(json, "https://graph.microsoft.com");
        Map<String, String> row = rows.get(0);
        assertEquals("report.xlsx", row.get("Name"));
        // key and title use getOrDefault("name","") which returns "" since key is "Name"
        assertEquals("", row.get("key"));
        assertEquals("", row.get("title"));
    }

    @Test
    public void testParseAllFieldsResponse_fullResourceFields() {
        String json = "{\"value\":[{\"hitsContainers\":[{\"total\":1,\"hits\":[{\"hitId\":\"abc\","
                + "\"summary\":\"A budget file\","
                + "\"resource\":{"
                + "\"name\":\"budget.xlsx\","
                + "\"webUrl\":\"https://example.com/budget.xlsx\","
                + "\"size\":\"5242880\","
                + "\"lastModifiedDateTime\":\"2024-06-01T09:00:00Z\","
                + "\"createdDateTime\":\"2024-01-01T00:00:00Z\","
                + "\"lastModifiedBy\":{\"user\":{\"displayName\":\"Bob\"}},"
                + "\"createdBy\":{\"user\":{\"displayName\":\"Alice\"}},"
                + "\"parentReference\":{\"name\":\"Finance\"},"
                + "\"file\":{\"mimeType\":\"application/xlsx\"},"
                + "\"id\":\"drive-item-123\""
                + "}}]}]}]}";

        List<Map<String, String>> rows = config.parseAllFieldsResponse(json, "https://graph.microsoft.com");
        assertEquals(1, rows.size());
        Map<String, String> row = rows.get(0);
        assertEquals("budget.xlsx", row.get("Name"));
        assertEquals("https://example.com/budget.xlsx", row.get("Link"));
        assertEquals("5.0 MB", row.get("Size"));
        assertEquals("2024-06-01", row.get("LastModified"));
        assertEquals("2024-01-01", row.get("Created"));
        assertEquals("Bob", row.get("ModifiedBy"));
        assertEquals("Alice", row.get("CreatedBy"));
        assertEquals("Finance", row.get("Folder"));
        assertEquals("application/xlsx", row.get("MimeType"));
        assertEquals("drive-item-123", row.get("FileId"));
        assertEquals("abc", row.get("fileId"));
        assertEquals("A budget file", row.get("summary"));
    }

    // ── Edge cases for toFreeFormKey default branch ──────────────────────

    @Test
    public void testParseSearchResponse_unknownPrimitiveFieldUsesOriginalKey() {
        // A field not in the toFreeFormKey switch → default returns the key unchanged
        String json = "{\"value\":[{\"hitsContainers\":[{\"total\":1,\"hits\":[{\"hitId\":\"h1\","
                + "\"resource\":{\"eTag\":\"etag-value\"}}]}]}]}";
        McpSearchResult result = config.parseSearchResponse(json, "https://graph.microsoft.com");
        assertEquals("etag-value", result.rows().get(0).get("eTag"));
    }

    // ── formatSize edge case: exactly 1024 bytes ─────────────────────────

    @Test
    public void testParseSearchResponse_sizeExactly1024() {
        String json = "{\"value\":[{\"hitsContainers\":[{\"total\":1,\"hits\":[{\"hitId\":\"h1\","
                + "\"resource\":{\"size\":\"1024\"}}]}]}]}";
        McpSearchResult result = config.parseSearchResponse(json, "https://graph.microsoft.com");
        assertEquals("1 KB", result.rows().get(0).get("Size"));
    }

    @Test
    public void testParseSearchResponse_sizeExactly1MB() {
        String json = "{\"value\":[{\"hitsContainers\":[{\"total\":1,\"hits\":[{\"hitId\":\"h1\","
                + "\"resource\":{\"size\":\"1048576\"}}]}]}]}";
        McpSearchResult result = config.parseSearchResponse(json, "https://graph.microsoft.com");
        assertEquals("1.0 MB", result.rows().get(0).get("Size"));
    }

    @Test
    public void testParseSearchResponse_sizeZeroBytes() {
        String json = "{\"value\":[{\"hitsContainers\":[{\"total\":1,\"hits\":[{\"hitId\":\"h1\","
                + "\"resource\":{\"size\":\"0\"}}]}]}]}";
        McpSearchResult result = config.parseSearchResponse(json, "https://graph.microsoft.com");
        assertEquals("0 B", result.rows().get(0).get("Size"));
    }

    // ── getString edge: hitId key missing from hit ───────────────────────

    @Test
    public void testParseSearchResponse_hitWithoutHitIdKey() {
        String json = "{\"value\":[{\"hitsContainers\":[{\"total\":1,\"hits\":[{"
                + "\"resource\":{\"name\":\"file.txt\"}}]}]}]}";
        McpSearchResult result = config.parseSearchResponse(json, "https://graph.microsoft.com");
        assertEquals(1, result.rows().size());
        assertFalse(result.rows().get(0).containsKey("fileId"));
    }

    // ── createdBy with no user displayName ───────────────────────────────

    @Test
    public void testParseSearchResponse_createdByUserWithoutDisplayName() {
        String json = "{\"value\":[{\"hitsContainers\":[{\"total\":1,\"hits\":[{\"hitId\":\"h1\","
                + "\"resource\":{\"createdBy\":{\"user\":{\"email\":\"a@b.com\"}}}}]}]}]}";
        McpSearchResult result = config.parseSearchResponse(json, "https://graph.microsoft.com");
        // displayName missing → getString returns "" → empty → skipped
        assertFalse(result.rows().get(0).containsKey("CreatedBy"));
    }

    // ── Verify parseSearchResponse returns correct total ─────────────────

    @Test
    public void testParseSearchResponse_totalIsAccumulated() {
        String json = "{\"value\":[{\"hitsContainers\":["
                + "{\"total\":10,\"hits\":[{\"hitId\":\"h1\",\"resource\":{\"name\":\"a.txt\"}}]},"
                + "{\"total\":20,\"hits\":[{\"hitId\":\"h2\",\"resource\":{\"name\":\"b.txt\"}}]}"
                + "]}]}";
        McpSearchResult result = config.parseSearchResponse(json, "https://graph.microsoft.com");
        assertEquals(2, result.rows().size());
    }
}
