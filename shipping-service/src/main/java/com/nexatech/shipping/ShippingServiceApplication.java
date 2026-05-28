package com.nexatech.shipping;

import com.nexatech.shipping.config.ShippingProperties;
import com.nexatech.shipping.enums.ShippingProvider;
import com.nexatech.shipping.provider.ghn.GhnProperties;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.util.StringUtils;

@EnableDiscoveryClient
@EnableScheduling
@EnableAsync
@SpringBootApplication
@EnableConfigurationProperties({ShippingProperties.class, GhnProperties.class})
public class ShippingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShippingServiceApplication.class, args);
    }

    @Bean
    ApplicationRunner validateShippingProviderConfig(
            ShippingProperties shippingProperties,
            GhnProperties ghnProperties) {
        return args -> {
            if (shippingProperties.getProvider() != ShippingProvider.GHN) {
                return;
            }
            if (!StringUtils.hasText(ghnProperties.getToken())
                    || !StringUtils.hasText(ghnProperties.getShopId())
                    || ghnProperties.getFromDistrictId() == null
                    || !StringUtils.hasText(ghnProperties.getFromWardCode())) {
                throw new IllegalStateException(
                        "SHIPPING_PROVIDER=ghn requires GHN_TOKEN, GHN_SHOP_ID, GHN_FROM_DISTRICT_ID and GHN_FROM_WARD_CODE");
            }
        };
    }
}
