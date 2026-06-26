package com.rezolveai.controller.client;

import io.restassured.response.Response;

/**
 * Request builders for the service-property endpoints (list / get / set).
 * Path segments are passed as RestAssured path params so values containing
 * characters like ':' (e.g. service name "fas:live1") are percent-encoded
 * consistently rather than hand-built into the URL string.
 */
public final class PropertyRequests {

    private PropertyRequests() {
    }

    public static Response listProperties(String customer, String service) {
        return ControllerApiClient.baseRequest()
                .when()
                .get("/customers/{customer}/services/{service}/properties", customer, service);
    }

    public static Response getProperty(String customer, String service, String propertyName) {
        return ControllerApiClient.baseRequest()
                .when()
                .get("/customers/{customer}/services/{service}/properties/{propertyName}",
                        customer, service, propertyName);
    }

    public static Response setProperty(String customer, String service, String propertyName, String rawValue) {
        return ControllerApiClient.baseRequest()
                .contentType("text/plain")
                .body(rawValue == null ? "" : rawValue)
                .when()
                .post("/customers/{customer}/services/{service}/properties/{propertyName}",
                        customer, service, propertyName);
    }
}
