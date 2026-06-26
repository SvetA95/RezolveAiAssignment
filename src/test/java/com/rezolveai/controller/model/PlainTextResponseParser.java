package com.rezolveai.controller.model;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class PlainTextResponseParser {

    private PlainTextResponseParser() {
    }

    public static List<String> linesOf(String body) {
        if (body == null || body.isBlank()) {
            return List.of();
        }
        return Arrays.stream(body.split("\\R"))
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .collect(Collectors.toList());
    }

    /** Parses "key=value" lines (as returned by the properties-list endpoint) into a map. */
    public static Map<String, String> propertiesOf(String body) {
        Map<String, String> properties = new LinkedHashMap<>();
        for (String line : linesOf(body)) {
            int separatorIndex = line.indexOf('=');
            if (separatorIndex > 0) {
                properties.put(
                        line.substring(0, separatorIndex).trim(),
                        line.substring(separatorIndex + 1).trim());
            }
        }
        return properties;
    }
}
