package com.waydee.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "waydee.security.jwt")
public record JwtProperties(
        String secret,
        String issuer,
        Duration accessTokenTtl,
        Duration refreshTokenTtl
) {
}
