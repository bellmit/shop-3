package com.zhang.shop.service.model;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.math.BigDecimal;

@Getter
@Setter
// 用户下单的交易模型
public class OrderModel implements Serializable {

    //交易单号，例如2019052100001212，使用string类型
    private String id;

    //购买的用户id
    private Integer userId;

    //购买的商品id
    private Integer itemId;

    //购买时商品的单价
    private BigDecimal itemPrice;

    //购买数量
    private Integer amount;

    //购买金额
    private BigDecimal orderPrice;

    // 若非空表示以秒杀状态下单
    private Integer promoId;
}
