package com.rezolveai.controller.base;

import com.rezolveai.controller.client.PropertyRequests;
import com.rezolveai.controller.config.ControllerConfig;
import io.restassured.response.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import static com.rezolveai.controller.data.ReindexBatchSizeTestData.PROPERTY_NAME;

/**
 * Any test that changes ms.reindexBatchSize captures the value beforehand and restores it
 * afterward, so the suite doesn't leave the environment in a different state than it found it.
 */
public abstract class BaseApiTest {

    protected static final String CUSTOMER = ControllerConfig.customer();
    protected static final String SERVICE = ControllerConfig.service();
    protected static final String OTHER_CUSTOMER = ControllerConfig.secondaryCustomer();

    private String originalReindexBatchSize;

    @BeforeEach
    void captureOriginalPropertyValue() {
        Response response = PropertyRequests.getProperty(CUSTOMER, SERVICE, PROPERTY_NAME);
        originalReindexBatchSize = response.statusCode() == 200 ? response.body().asString().trim() : null;
    }

    @AfterEach
    void restoreOriginalPropertyValue() {
        if (originalReindexBatchSize != null) {
            PropertyRequests.setProperty(CUSTOMER, SERVICE, PROPERTY_NAME, originalReindexBatchSize);
        }
    }
}
