package com.rabbiter.cm.domain;

import lombok.Data;

@Data
public class AliPay {
    private String traceNo;       // 商户订单号（唯一）
    private double totalAmount;   // 订单金额
    private String subject;       // 订单标题
    private String alipayTraceNo; // 支付宝交易号（回调后设置）
}

