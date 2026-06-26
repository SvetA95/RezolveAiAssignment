package com.rezolveai.controller.data;

import java.util.stream.Stream;

/**
 * Test data for ms.reindexBatchSize
 */
public final class ReindexBatchSizeTestData {

    public static final String PROPERTY_NAME = "ms.reindexBatchSize";

    /** Documented default when the property has never been explicitly set. */
    public static final String DOCUMENTED_DEFAULT_VALUE = "1";

    private ReindexBatchSizeTestData() {
    }

    public static Stream<String> validValues() {
        return Stream.of("1", "2", "5", "10");
    }

    public static Stream<String> invalidValues() {
        return Stream.of(
                "abc",
                "",
                "1.5",
                "-1",
                "0",
                "1e3",
                " 5",
                "5 ",
                "99999999999999999999"
        );
    }
}
