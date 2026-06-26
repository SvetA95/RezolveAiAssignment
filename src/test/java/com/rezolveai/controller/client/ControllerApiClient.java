package com.rezolveai.controller.client;

import com.rezolveai.controller.config.ControllerConfig;
import io.restassured.authentication.PreemptiveBasicAuthScheme;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.specification.RequestSpecification;

import static io.restassured.RestAssured.given;

/**
 * Builds the base RestAssured request spec shared by every Controller endpoint
 * call: base URI plus Basic Auth credentials from {@link ControllerConfig}.
 * Falls back to no auth if credentials aren't set.
 */
public final class ControllerApiClient {

    private ControllerApiClient() {
    }

    public static RequestSpecification baseRequest() {
        RequestSpecBuilder builder = new RequestSpecBuilder()
                .setBaseUri(ControllerConfig.baseUri());

        String username = ControllerConfig.username();
        String password = ControllerConfig.password();

        if (username != null && password != null) {
            PreemptiveBasicAuthScheme basicAuth = new PreemptiveBasicAuthScheme();
            basicAuth.setUserName(username);
            basicAuth.setPassword(password);
            builder.setAuth(basicAuth);
        }

        return given().spec(builder.build());
    }
}
