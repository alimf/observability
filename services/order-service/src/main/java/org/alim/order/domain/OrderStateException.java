package org.alim.order.domain;

import java.util.UUID;

public class OrderStateException extends RuntimeException {

    public OrderStateException(UUID orderId, OrderStatus status, String action) {
        super("Order " + orderId + " cannot be " + action + " while " + status);
    }
}
