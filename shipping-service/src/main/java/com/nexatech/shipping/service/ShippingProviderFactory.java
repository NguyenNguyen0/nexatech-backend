package com.nexatech.shipping.service;

import com.nexatech.shipping.config.ShippingProperties;
import com.nexatech.shipping.enums.ShippingProvider;
import com.nexatech.shipping.provider.ShippingProviderClient;
import com.nexatech.shipping.provider.ghn.GhnShippingClient;
import com.nexatech.shipping.provider.mock.MockShippingClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ShippingProviderFactory {

    private final ShippingProperties shippingProperties;
    private final MockShippingClient mockShippingClient;
    private final GhnShippingClient ghnShippingClient;

    public ShippingProviderClient getCurrentProviderClient() {
        return getProviderClient(shippingProperties.getProvider());
    }

    public ShippingProviderClient getProviderClient(ShippingProvider provider) {
        if (provider == ShippingProvider.GHN) {
            // Fail fast with a clear config error before calling the sandbox API.
            ghnShippingClient.validateConfiguration();
            return ghnShippingClient;
        }
        return mockShippingClient;
    }
}
