package org.alim.common.security;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param publicPaths Ant-style paths reachable without a token, in addition to {@code /actuator/**}.
 *                    Each entry is {@code "<pattern>"} or {@code "<METHOD> <pattern>"},
 *                    e.g. {@code "POST /api/v1/users"}.
 */
@ConfigurationProperties("app.security")
public record AppSecurityProperties(List<String> publicPaths) {

    public AppSecurityProperties {
        publicPaths = publicPaths == null ? List.of() : List.copyOf(publicPaths);
    }
}
