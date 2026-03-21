package com.nexatech.identity.config.common;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import cn.hutool.extra.spring.SpringUtil;

@Configuration
public class SpringContextConfig {

    @Bean
    public static SpringUtil springUtil() {
        return new SpringUtil();
    }
}

