package com.nexatech.shipping.provider.ghn;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "shipping.ghn")
public class GhnProperties {

    private String baseUrl = "https://dev-online-gateway.ghn.vn/shiip/public-api";
    private String token;
    private String shopId;
    private Integer fromDistrictId;
    private String fromWardCode;
    private String fromName = "NexaTech Warehouse";
    private String fromPhone = "0987654321";
    private String fromAddress = "Thu Duc, Ho Chi Minh City";
    private String fromProvinceName = "TP. Hồ Chí Minh";
    private String fromDistrictName = "Thành phố Thủ Đức";
    private String fromWardName = "Linh Trung";
}
