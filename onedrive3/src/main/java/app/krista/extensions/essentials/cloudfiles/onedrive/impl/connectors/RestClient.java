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

package app.krista.extensions.essentials.cloudfiles.onedrive.impl.connectors;

import app.krista.extensions.essentials.cloudfiles.onedrive.impl.util.Constants;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Objects;

public class RestClient {

    private static final String BEARER = "Bearer ";
    private static final String BAD_REQUEST = "Bad Request";
    private static final String INTERNAL_SERVER_ERROR = "Internal Server Error";

    private static final String REQUEST_IS_ACCEPTED = "Request is Accepted";

    private static final String REQUEST_IS_SUCCESS = "Request is Success";

    private static final String CONFLICT_OCCURS = "Conflict Occurs";

    private static final String ONE_OR_MORE_INPUT_PARAMETER_IS_INVALID = "One or more input parameter is invalid";
    private static final String ONE_OR_MORE_FIELD_PROVIDED_ARE_INVALID = "One or more field provided are invalid";
    private static final String GRAPH_API_BASE_URI = "https://graph.microsoft.com/v1.0/me";
    private static final String DRIVE_ITEMS = "/drive/items/";
    private static final String DRIVES = "/drives/";
    private static final String ITEMS = "/items/";

    String accessToken;
    String driveId;
    String instanceUrl;

    private static final String AUTHORIZATION = "Authorization";
    private static final String CONTENT_TYPE = "Content-Type";
    private static final String YOU_ARE_NOT_AUTHORIZED = "You are not authorized";
    private static final String APPLICATION_JSON = "application/json";
    private static final String STATUS_CODE_IS = "Status code is {} ";
    private static final Logger LOGGER = LoggerFactory.getLogger(RestClient.class);


    public RestClient(String accessToken, String driveId) {
        this.accessToken = accessToken;
        this.driveId = driveId;
        this.instanceUrl = getProperInstanceUrl(driveId);
    }

    private String getProperInstanceUrl(String driveId) {
        return driveId != null ? GRAPH_API_BASE_URI + DRIVES + driveId + ITEMS
                : GRAPH_API_BASE_URI + DRIVE_ITEMS;
    }

    public Response get(String endPoint) {
        try {
            OkHttpClient client = new OkHttpClient();
            Request request = new Request.Builder()
                    .url(endPoint)
                    .get()
                    .build();
            return handleResponse(client.newCall(request).execute());
        } catch (IOException e) {
            return null;
        }
    }

    public Response executeHttpMethods(String endpoint, String action, RequestBody payload) throws IOException {
        switch (action) {
            case Constants.POST_METHOD:
                return executeRequest(payload, Constants.POST_METHOD, endpoint);
            case Constants.GET_REQUEST:
                return executeRequest(null, Constants.GET_REQUEST, endpoint);
            case Constants.PATCH_METHOD:
                return executeRequest(payload, Constants.PATCH_METHOD, endpoint);
            default:
                break;
        }
        return null;
    }

    private static Request prepareRequest(String methodType, RequestBody requestBody, String endPointUrl,
                                          String accessToken) {

        Request.Builder requestBuilder = new Request.Builder()
                .url(endPointUrl)
                .method(methodType, requestBody);

        if (!Constants.GET_REQUEST.equalsIgnoreCase(methodType)) {
            requestBuilder
                    .addHeader(AUTHORIZATION, BEARER + accessToken)
                    .addHeader(CONTENT_TYPE, APPLICATION_JSON);
        }

        return requestBuilder.build();
    }

    private Response executeRequest(RequestBody payload, String methodType, String endPointUrl) throws
            IOException {
        Request request = prepareRequest(methodType, payload, instanceUrl + endPointUrl, accessToken);
        OkHttpClient client = new OkHttpClient().newBuilder().build();
        try (Response response = client.newCall(request).execute()) {
            return handleResponse(Objects.requireNonNull(response));
        }
    }

    public Response handleResponse(Response response) {
        int statusCode = response.code();

        if (statusCode == 200 || statusCode == 201 || statusCode == 204) {
            LOGGER.info(STATUS_CODE_IS, response.code() + REQUEST_IS_SUCCESS);
            return response;
        } else if (statusCode == 202) {
            LOGGER.info(STATUS_CODE_IS, response.code() + REQUEST_IS_ACCEPTED);
            return response;
        } else if (statusCode == 409) {
            LOGGER.info(STATUS_CODE_IS, response.code() + CONFLICT_OCCURS);
            return response;
        } else if (statusCode == 401) {
            LOGGER.info(STATUS_CODE_IS, response.code() + YOU_ARE_NOT_AUTHORIZED);
            throw new IllegalArgumentException(YOU_ARE_NOT_AUTHORIZED);
        } else if (statusCode == 400) {
            LOGGER.info(STATUS_CODE_IS, response.code() + BAD_REQUEST);
            throw new IllegalArgumentException(ONE_OR_MORE_INPUT_PARAMETER_IS_INVALID);
        } else if (statusCode == 500) {
            LOGGER.info(STATUS_CODE_IS, response.code() + INTERNAL_SERVER_ERROR);
            throw new IllegalArgumentException(ONE_OR_MORE_FIELD_PROVIDED_ARE_INVALID);
        }
        return null;
    }

}
