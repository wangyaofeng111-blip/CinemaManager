package com.rabbiter.cm.config;

import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AlipayConfig {

    @Value("${alipay.gatewayUrl}")
    private String gatewayUrl;

    @Value("${alipay.appId}")
    private String appId;

    @Value("${alipay.merchantPrivateKey}")
    private String merchantPrivateKey;

    @Value("${alipay.alipayPublicKey}")
    private String alipayPublicKey;

    @Value("${alipay.charset}")
    private String charset;

    @Value("${alipay.signType}")
    private String signType;

    @Bean
    public AlipayClient alipayClient() {
        // format = json
        return new DefaultAlipayClient(
                gatewayUrl,
                appId,
                merchantPrivateKey,
                "json",
                charset,
                alipayPublicKey,
                signType
        );
    }
}


