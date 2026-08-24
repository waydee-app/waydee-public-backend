package com.waydee.territory.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "waydee.territory")
public record TerritoryProperties(
        int minRadiusM,
        int maxRadiusM
) {
}
