package com.nexatech.shipping.provider.ghn;

import io.netty.channel.ChannelOption;
import io.netty.resolver.DefaultAddressResolverGroup;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Configuration
public class GhnWebClientConfig {

    @Bean
    public WebClient ghnWebClient(GhnProperties properties) {
        // Dùng JDK DNS resolver thay vì Netty async DNS để tránh lỗi
        // "Failed to resolve ... after 3 queries" trên một số môi trường
        HttpClient httpClient = HttpClient.create()
                .resolver(DefaultAddressResolverGroup.INSTANCE)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5_000)
                .responseTimeout(Duration.ofSeconds(10));

        return WebClient.builder()
                .baseUrl(properties.getBaseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("Token", properties.getToken() == null ? "" : properties.getToken())
                .defaultHeader("ShopId", properties.getShopId() == null ? "" : properties.getShopId())
                .build();
    }
}
