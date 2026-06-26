package com.rezolveai.controller.tests;

import com.rezolveai.controller.client.DiscoveryRequests;
import com.rezolveai.controller.config.ControllerConfig;
import com.rezolveai.controller.model.PlainTextResponseParser;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sanity checks that the configured customer/service/deployments actually
 * exist before the property tests assume them as valid test data.
 */
class DiscoverySmokeTest {

    @Test
    @DisplayName("Configured customer is present in /customers")
    void configuredCustomerExists() {
        Response response = DiscoveryRequests.listCustomers();

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(PlainTextResponseParser.linesOf(response.body().asString()))
                .contains(ControllerConfig.customer());
    }

    @Test
    @DisplayName("Configured service is present for the configured customer")
    void configuredServiceExists() {
        Response response = DiscoveryRequests.listServices(ControllerConfig.customer());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(PlainTextResponseParser.linesOf(response.body().asString()))
                .contains(ControllerConfig.service());
    }

    @Test
    @DisplayName("Configured service has at least one deployed server")
    void configuredServiceHasDeployments() {
        Response response = DiscoveryRequests.listDeployments(ControllerConfig.customer(), ControllerConfig.service());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(PlainTextResponseParser.linesOf(response.body().asString())).isNotEmpty();
    }
}
