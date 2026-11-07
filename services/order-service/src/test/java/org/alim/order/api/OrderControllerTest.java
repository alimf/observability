package org.alim.order.api;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import org.alim.order.service.PaymentClient;
import org.alim.order.service.PaymentException;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class OrderControllerTest {

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // The real client declines 10% of calls at random; tests must be deterministic.
    @MockitoBean
    private PaymentClient paymentClient;

    private static RequestPostProcessor user(UUID id) {
        return jwt().jwt(token -> token.subject(id.toString()).claim("roles", List.of("USER")));
    }

    private String createOrderAs(UUID userId) throws Exception {

        String body = mockMvc.perform(post("/api/v1/orders")
                        .with(user(userId))
                        .contentType(JSON)
                        .content("{\"amount\": 42.50}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(body).get("id").asText();
    }

    @Test
    void rejectsRequestsWithoutToken() throws Exception {

        mockMvc.perform(post("/api/v1/orders").contentType(JSON).content("{\"amount\": 10}"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void createsOrderForAuthenticatedUserAndIgnoresClientSuppliedCustomer() throws Exception {

        UUID userId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/orders")
                        .with(user(userId))
                        .contentType(JSON)
                        .content("{\"amount\": 42.50, \"customerId\": \"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.customerId").value(userId.toString()))
                .andExpect(jsonPath("$.status").value("PAID"));
    }

    @Test
    void validatesAmount() throws Exception {

        mockMvc.perform(post("/api/v1/orders")
                        .with(user(UUID.randomUUID()))
                        .contentType(JSON)
                        .content("{\"amount\": -1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("amount"));
    }

    @Test
    void paymentFailureIsBadGateway() throws Exception {

        doThrow(new PaymentException("declined")).when(paymentClient).authorize(any(), any());

        mockMvc.perform(post("/api/v1/orders")
                        .with(user(UUID.randomUUID()))
                        .contentType(JSON)
                        .content("{\"amount\": 10}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("PAYMENT_FAILED"));
    }

    @Test
    void usersCannotReadOrUpdateEachOthersOrders() throws Exception {

        UUID owner = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        String orderId = createOrderAs(owner);

        mockMvc.perform(get("/api/v1/orders/" + orderId).with(user(owner)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/orders/" + orderId).with(user(other)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));

        mockMvc.perform(patch("/api/v1/orders/" + orderId)
                        .with(user(other))
                        .contentType(JSON)
                        .content("{\"status\": \"CANCELLED\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void listReturnsOnlyCallersOrdersPaginated() throws Exception {

        UUID owner = UUID.randomUUID();
        createOrderAs(owner);
        createOrderAs(owner);
        createOrderAs(UUID.randomUUID());

        mockMvc.perform(get("/api/v1/orders").param("size", "1").with(user(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2));

        mockMvc.perform(get("/api/v1/orders").param("size", "1000").with(user(owner)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ownerCanCancelOnceThenConflicts() throws Exception {

        UUID owner = UUID.randomUUID();
        String orderId = createOrderAs(owner);

        mockMvc.perform(patch("/api/v1/orders/" + orderId)
                        .with(user(owner))
                        .contentType(JSON)
                        .content("{\"status\": \"CANCELLED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        mockMvc.perform(patch("/api/v1/orders/" + orderId)
                        .with(user(owner))
                        .contentType(JSON)
                        .content("{\"status\": \"CANCELLED\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_STATE_CONFLICT"));
    }

    @Test
    void rejectsUnsupportedStatusUpdate() throws Exception {

        UUID owner = UUID.randomUUID();
        String orderId = createOrderAs(owner);

        mockMvc.perform(patch("/api/v1/orders/" + orderId)
                        .with(user(owner))
                        .contentType(JSON)
                        .content("{\"status\": \"PAID\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void malformedTokenSubjectIsUnauthorized() throws Exception {

        mockMvc.perform(get("/api/v1/orders").with(jwt().jwt(token -> token.subject("not-a-uuid"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }
}
