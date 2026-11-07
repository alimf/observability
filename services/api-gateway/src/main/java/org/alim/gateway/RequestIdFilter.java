package org.alim.gateway;

import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

/**
 * Guarantees every request entering the system carries an {@code X-Request-Id}: a
 * well-formed client value is kept, anything else is replaced. Downstream services log
 * it (see RequestLoggingFilter in the common module) and the response echoes it, so one
 * ID ties together the client, gateway and service logs.
 */
@Component
public class RequestIdFilter implements GlobalFilter, Ordered {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";

    // Client-supplied IDs end up in every log line, so only accept short, plain values.
    private static final Pattern VALID_REQUEST_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {

        String requestId = exchange.getRequest().getHeaders().getFirst(REQUEST_ID_HEADER);

        if (requestId == null || !VALID_REQUEST_ID.matcher(requestId).matches()) {
            requestId = UUID.randomUUID().toString();
        }

        String resolved = requestId;

        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> headers.set(REQUEST_ID_HEADER, resolved))
                .build();

        exchange.getResponse().getHeaders().set(REQUEST_ID_HEADER, resolved);

        return chain.filter(exchange.mutate().request(request).build());
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
