package org.alim.observability.observability;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.http.HttpServlet;

import static org.alim.observability.observability.RequestLoggingFilter.REQUEST_ID_HEADER;
import static org.alim.observability.observability.RequestLoggingFilter.REQUEST_ID_MDC_KEY;
import static org.assertj.core.api.Assertions.assertThat;

class RequestLoggingFilterTest {

    private final RequestLoggingFilter filter = new RequestLoggingFilter();

    @Test
    void propagatesValidIncomingRequestId() throws Exception {

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/orders/1");
        request.addHeader(REQUEST_ID_HEADER, "abc-123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> mdcDuringRequest = new AtomicReference<>();

        filter.doFilter(request, response, chainCapturingMdc(mdcDuringRequest));

        assertThat(response.getHeader(REQUEST_ID_HEADER)).isEqualTo("abc-123");
        assertThat(mdcDuringRequest.get()).isEqualTo("abc-123");
        assertThat(MDC.get(REQUEST_ID_MDC_KEY)).isNull();
    }

    @Test
    void replacesMissingOrUnsafeRequestId() throws Exception {

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/orders/1");
        request.addHeader(REQUEST_ID_HEADER, "bad value\n{\"forged\":true}");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> mdcDuringRequest = new AtomicReference<>();

        filter.doFilter(request, response, chainCapturingMdc(mdcDuringRequest));

        assertThat(response.getHeader(REQUEST_ID_HEADER))
                .matches("[0-9a-f-]{36}")
                .isEqualTo(mdcDuringRequest.get());
    }

    @Test
    void skipsActuatorEndpoints() throws Exception {

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/prometheus");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader(REQUEST_ID_HEADER)).isNull();
    }

    private static MockFilterChain chainCapturingMdc(AtomicReference<String> captured) {
        return new MockFilterChain(new HttpServlet() {
        }) {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest request, jakarta.servlet.ServletResponse response) {
                captured.set(MDC.get(REQUEST_ID_MDC_KEY));
            }
        };
    }
}
