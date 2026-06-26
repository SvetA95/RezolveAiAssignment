package com.rezolveai.controller.tests;

import com.rezolveai.controller.base.BaseApiTest;
import com.rezolveai.controller.base.Eventually;
import com.rezolveai.controller.client.PropertyRequests;
import com.rezolveai.controller.data.ReindexBatchSizeTestData;
import com.rezolveai.controller.model.PlainTextResponseParser;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Duration;
import java.util.Map;

import static com.rezolveai.controller.data.ReindexBatchSizeTestData.PROPERTY_NAME;
import static org.assertj.core.api.Assertions.assertThat;


class ReindexBatchSizePropertyTest extends BaseApiTest {

    private static final Duration POLL_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration POLL_INTERVAL = Duration.ofSeconds(1);

    @Test
    @DisplayName("Property appears in the full properties listing with a numeric value")
    void appearsInPropertiesList() {
        Response response = PropertyRequests.listProperties(CUSTOMER, SERVICE);

        assertThat(response.statusCode()).isEqualTo(200);
        Map<String, String> properties = PlainTextResponseParser.propertiesOf(response.body().asString());
        assertThat(properties).containsKey(PROPERTY_NAME);
        assertThat(properties.get(PROPERTY_NAME)).matches("\\d+");
    }

    @Test
    @DisplayName("Single-property GET returns 200 and matches the value from the list endpoint")
    void singlePropertyGetMatchesListValue() {
        Response listResponse = PropertyRequests.listProperties(CUSTOMER, SERVICE);
        String valueFromList = PlainTextResponseParser.propertiesOf(listResponse.body().asString()).get(PROPERTY_NAME);

        Response singleResponse = PropertyRequests.getProperty(CUSTOMER, SERVICE, PROPERTY_NAME);

        assertThat(singleResponse.statusCode()).isEqualTo(200);
        assertThat(singleResponse.body().asString().trim()).isEqualTo(valueFromList);
    }

    @ParameterizedTest(name = "setting a valid value \"{0}\" is accepted")
    @MethodSource("com.rezolveai.controller.data.ReindexBatchSizeTestData#validValues")
    @DisplayName("Task 1: valid values are accepted with 202 and eventually readable back")
    void settingValidValueIsAcceptedAndPersisted(String newValue) {
        Response setResponse = PropertyRequests.setProperty(CUSTOMER, SERVICE, PROPERTY_NAME, newValue);
        assertThat(setResponse.statusCode()).isEqualTo(202);

        boolean valueWasApplied = Eventually.pollUntilTrue(POLL_TIMEOUT, POLL_INTERVAL, () -> {
            Response getResponse = PropertyRequests.getProperty(CUSTOMER, SERVICE, PROPERTY_NAME);
            return getResponse.statusCode() == 200 && newValue.equals(getResponse.body().asString().trim());
        });

        assertThat(valueWasApplied)
                .as("ms.reindexBatchSize should read back as '%s' within %s", newValue, POLL_TIMEOUT)
                .isTrue();
    }

    @ParameterizedTest(name = "invalid value \"{0}\" is rejected")
    @MethodSource("com.rezolveai.controller.data.ReindexBatchSizeTestData#invalidValues")
    @DisplayName("Invalid input is rejected rather than silently accepted as a batch size")
    void settingInvalidValueIsRejected(String invalidValue) {
        Response setResponse = PropertyRequests.setProperty(CUSTOMER, SERVICE, PROPERTY_NAME, invalidValue);

        assertThat(setResponse.statusCode())
                .as("expected '%s' to be rejected (4xx), not accepted as a valid batch size", invalidValue)
                .isBetween(400, 499);
    }

    @Test
    @DisplayName("A leading '+' sign is normalized away rather than rejected or stored literally")
    void settingValueWithExplicitPlusSignIsNormalized() {
        Response setResponse = PropertyRequests.setProperty(CUSTOMER, SERVICE, PROPERTY_NAME, "+5");
        assertThat(setResponse.statusCode()).isEqualTo(202);

        boolean valueWasNormalized = Eventually.pollUntilTrue(POLL_TIMEOUT, POLL_INTERVAL, () -> {
            Response getResponse = PropertyRequests.getProperty(CUSTOMER, SERVICE, PROPERTY_NAME);
            return getResponse.statusCode() == 200 && "5".equals(getResponse.body().asString().trim());
        });

        assertThat(valueWasNormalized)
                .as("'+5' should be accepted and normalized to '5', not stored literally as '+5'")
                .isTrue();
    }

    @Test
    @DisplayName("Changing the property for one customer doesn't affect another customer's copy of it")
    void doesNotLeakAcrossCustomers() {
        String otherCustomersValueBefore = PropertyRequests.getProperty(OTHER_CUSTOMER, SERVICE, PROPERTY_NAME)
                .body().asString().trim();

        String newValue = "2".equals(otherCustomersValueBefore) ? "3" : "2";
        PropertyRequests.setProperty(CUSTOMER, SERVICE, PROPERTY_NAME, newValue);

        boolean ourValueChanged = Eventually.pollUntilTrue(POLL_TIMEOUT, POLL_INTERVAL, () -> {
            Response response = PropertyRequests.getProperty(CUSTOMER, SERVICE, PROPERTY_NAME);
            return response.statusCode() == 200 && newValue.equals(response.body().asString().trim());
        });
        assertThat(ourValueChanged).isTrue();

        String otherCustomersValueAfter = PropertyRequests.getProperty(OTHER_CUSTOMER, SERVICE, PROPERTY_NAME)
                .body().asString().trim();
        assertThat(otherCustomersValueAfter).isEqualTo(otherCustomersValueBefore);
    }

    @Test
    @DisplayName("GET for a non-existent property returns 404, not 200 with empty body")
    void gettingNonExistentPropertyReturns404() {
        Response response = PropertyRequests.getProperty(CUSTOMER, SERVICE, "ms.thisPropertyDoesNotExist");

        assertThat(response.statusCode()).isEqualTo(404);
    }

    @Test
    @DisplayName("GET against a non-existent customer returns 404")
    void gettingPropertyForNonExistentCustomerReturns404() {
        Response response = PropertyRequests.getProperty("no-such-customer", SERVICE, PROPERTY_NAME);

        assertThat(response.statusCode()).isEqualTo(404);
    }

    @Test
    @DisplayName("GET against a non-existent service returns 404")
    void gettingPropertyForNonExistentServiceReturns404() {
        Response response = PropertyRequests.getProperty(CUSTOMER, "no-such-service", PROPERTY_NAME);

        assertThat(response.statusCode()).isEqualTo(404);
    }

    @Test
    @DisplayName("Setting the documented default value (1) round-trips correctly")
    void settingDocumentedDefaultValueRoundTrips() {
        Response setResponse = PropertyRequests.setProperty(
                CUSTOMER, SERVICE, PROPERTY_NAME, ReindexBatchSizeTestData.DOCUMENTED_DEFAULT_VALUE);
        assertThat(setResponse.statusCode()).isEqualTo(202);

        boolean valueWasApplied = Eventually.pollUntilTrue(POLL_TIMEOUT, POLL_INTERVAL, () -> {
            Response getResponse = PropertyRequests.getProperty(CUSTOMER, SERVICE, PROPERTY_NAME);
            return getResponse.statusCode() == 200
                    && ReindexBatchSizeTestData.DOCUMENTED_DEFAULT_VALUE.equals(getResponse.body().asString().trim());
        });

        assertThat(valueWasApplied).isTrue();
    }
}
