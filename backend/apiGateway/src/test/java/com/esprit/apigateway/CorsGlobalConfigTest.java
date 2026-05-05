package com.esprit.apigateway;

import org.junit.jupiter.api.Test;
import org.springframework.web.cors.reactive.CorsWebFilter;

import static org.assertj.core.api.Assertions.assertThat;

class CorsGlobalConfigTest {

    private final CorsGlobalConfig config = new CorsGlobalConfig();

    @Test
    void corsWebFilter_notNull() {
        CorsWebFilter filter = config.corsWebFilter();
        assertThat(filter).isNotNull();
    }
}
