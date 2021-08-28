package com.zhang.shop.controller.viewObject;

import lombok.Getter;
import lombok.Setter;
import org.joda.time.DateTime;

import java.math.BigDecimal;
import java.text.Bidi;
import java.time.format.DateTimeFormatter;

@Setter
@Getter
public class ItemVO {
    private Integer id;

    //商品名称
    private String title;

    //商品价格
    private BigDecimal price;

    //商品的库存
    private Integer stock;

    //商品的描述
    private String description;

    //商品的销量
    private Integer sales;

    //商品描述图片的url
    private String imgUrl;

    // 秒杀相关的属性

    //秒杀活动状态：0表示没有秒杀活动，1表示还未开始，2表示正在进行，3表示已结束
    private Integer promoStatus;

    private BigDecimal promoPrice;

    private Integer promoId;

    private String startDate;


}
