package org.alim.gateway;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.alim.gateway.RequestIdFilter.REQUEST_ID_HEADER;

class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    private String forwardedRequestId(MockServerWebExchange exchange) {

        AtomicReference<String> forwarded = new AtomicReference<>();

        filter.filter(exchange, next -> {
            forwarded.set(next.getRequest().getHeaders().getFirst(REQUEST_ID_HEADER));
            return Mono.empty();
        }).block();

        return forwarded.get();
    }

    @Test
    void keepsWellFormedClientRequestId() {

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/orders").header(REQUEST_ID_HEADER, "abc-123"));

        assertThat(forwardedRequestId(exchange)).isEqualTo("abc-123");
        assertThat(exchange.getResponse().getHeaders().getFirst(REQUEST_ID_HEADER)).isEqualTo("abc-123");
    }

    @Test
    void generatesIdWhenMissingOrUnsafe() {

        MockServerWebExchange missing = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/orders"));
        MockServerWebExchange unsafe = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/orders").header(REQUEST_ID_HEADER, "bad value {\"forged\":true}"));

        assertThat(forwardedRequestId(missing)).matches("[0-9a-f-]{36}");
        assertThat(forwardedRequestId(unsafe)).matches("[0-9a-f-]{36}");
    }
}
