package com.nexatech.gateway.config;

import java.time.Duration;

import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.http.client.HttpComponentsClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.autoconfigure.ClientHttpRequestFactoryBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayHttpClientConfig {

    @Bean
    ClientHttpRequestFactoryBuilderCustomizer<HttpComponentsClientHttpRequestFactoryBuilder> gatewayHttpClientPoolCustomizer(
            @Value("${gateway.http-client.max-connections:200}") int maxConnections,
            @Value("${gateway.http-client.max-connections-per-route:100}") int maxConnectionsPerRoute,
            @Value("${gateway.http-client.connection-request-timeout:2s}") Duration connectionRequestTimeout,
            @Value("${gateway.http-client.response-timeout:45s}") Duration responseTimeout) {
        return builder -> builder
                .withConnectionManagerCustomizer(connectionManager -> connectionManager
                        .setMaxConnTotal(maxConnections)
                        .setMaxConnPerRoute(maxConnectionsPerRoute))
                .withDefaultRequestConfigCustomizer(requestConfig -> requestConfig
                        .setConnectionRequestTimeout(Timeout.of(connectionRequestTimeout))
                        .setResponseTimeout(Timeout.of(responseTimeout)))
                .withCustomizer(factory -> factory.setConnectionRequestTimeout(connectionRequestTimeout));
    }
}
