package com.rezolveai.controller.client;

import io.restassured.response.Response;

/**
 * Request builders for the read-only discovery endpoints (customers, services,
 * deployments).
 */
public final class DiscoveryRequests {

    private DiscoveryRequests() {
    }

    public static Response listCustomers() {
        return ControllerApiClient.baseRequest()
                .when()
                .get("/customers");
    }

    public static Response listServices(String customer) {
        return ControllerApiClient.baseRequest()
                .when()
                .get("/customers/{customer}/services", customer);
    }

    public static Response listDeployments(String customer, String service) {
        return ControllerApiClient.baseRequest()
                .when()
                .get("/customers/{customer}/services/{service}/deployments", customer, service);
    }
}
