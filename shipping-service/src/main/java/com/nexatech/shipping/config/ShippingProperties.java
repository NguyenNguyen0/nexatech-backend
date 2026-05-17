package com.nexatech.shipping.config;

import com.nexatech.shipping.enums.ShippingProvider;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "shipping")
public class ShippingProperties {

    private ShippingProvider provider = ShippingProvider.MOCK;
}
