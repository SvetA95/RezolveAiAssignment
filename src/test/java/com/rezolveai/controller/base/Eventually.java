package com.rezolveai.controller.base;

import java.time.Duration;
import java.util.function.BooleanSupplier;

/**
 * The property-change endpoint replies 202 Accepted, meaning the change is
 * applied asynchronously. A GET issued immediately afterward is therefore not
 * guaranteed to reflect the new value yet, so read-after-write assertions poll
 * for a bounded time instead of asserting on the very next response.
 */
public final class Eventually {

    private Eventually() {
    }

    public static boolean pollUntilTrue(Duration timeout, Duration interval, BooleanSupplier condition) {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return true;
            }
            sleep(interval);
        }
        return condition.getAsBoolean();
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while polling", e);
        }
    }
}
