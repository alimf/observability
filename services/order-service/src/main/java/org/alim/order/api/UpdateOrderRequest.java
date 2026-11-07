package org.alim.order.api;

import jakarta.validation.constraints.NotNull;

/**
 * Partial update (PATCH). The only client-driven change today is cancellation.
 */
public record UpdateOrderRequest(

        @NotNull(message = "status is required")
        Status status
) {

    public enum Status {
        CANCELLED
    }
}
