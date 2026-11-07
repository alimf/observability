package org.alim.gateway;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteLocator;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class GatewayApplicationTests {

    @Autowired
    private RouteLocator routeLocator;

    @Test
    void routesAreConfiguredPerService() {

        List<String> ids = routeLocator.getRoutes().map(route -> route.getId()).collectList().block();

        assertThat(ids).containsExactlyInAnyOrder("user-service", "order-service");
    }
}
