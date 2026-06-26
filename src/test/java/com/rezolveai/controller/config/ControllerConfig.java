package com.rezolveai.controller.config;

/**
 * Central place for environment-specific values.
 */
public final class ControllerConfig {

    private static final String DEFAULT_BASE_URI =
            "https://task-11-ctl.eu-west-1.fdp-qa.fredhopper.com/control";
    private static final String DEFAULT_CUSTOMER = "jdplc_09_etl2_ctl8-61dbe4da-20260610123310";
    private static final String DEFAULT_SERVICE = "fas:live1";
    private static final String DEFAULT_SECONDARY_CUSTOMER = "new_look_test_etl2_ctl8-6aa33dc7-20260611085344";

    private ControllerConfig() {
    }

    public static String baseUri() {
        return resolve("controller.baseUri", "CONTROLLER_BASE_URI", DEFAULT_BASE_URI);
    }

    public static String customer() {
        return resolve("controller.customer", "CONTROLLER_CUSTOMER", DEFAULT_CUSTOMER);
    }

    public static String service() {
        return resolve("controller.service", "CONTROLLER_SERVICE", DEFAULT_SERVICE);
    }

    public static String secondaryCustomer() {
        return resolve("controller.secondaryCustomer", "CONTROLLER_SECONDARY_CUSTOMER", DEFAULT_SECONDARY_CUSTOMER);
    }

     //Basic-auth username. 
    public static String username() {
        return resolve("controller.username", "CONTROLLER_USERNAME", null);
    }

     //Basic-auth password. 
    public static String password() {
        return resolve("controller.password", "CONTROLLER_PASSWORD", null);
    }

    private static String resolve(String systemProperty, String envVar, String fallback) {
        String value = System.getProperty(systemProperty);
        if (value != null && !value.isBlank()) {
            return value;
        }
        value = System.getenv(envVar);
        if (value != null && !value.isBlank()) {
            return value;
        }
        return fallback;
    }
}
