package com.zhang.shop.service.model;

import lombok.Getter;
import lombok.Setter;
import org.hibernate.validator.constraints.NotBlank;
//JSR 提供的校验注解
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.math.BigDecimal;
@Getter
@Setter
public class ItemModel implements Serializable {
    private Integer id;

    //商品名称
    @NotBlank(message = "商品名称不能为空")
    private String title;

    //商品价格
    @NotNull(message = "商品价格不能为空")
    @Min(value = 0,message = "商品价格必须大于0")
    private BigDecimal price;

    //商品的库存
    @NotNull(message = "库存不能不填")
    private Integer stock;

    //商品的描述
    @NotBlank(message = "商品描述信息不能为空")
    private String description;

    //商品的销量
    private Integer sales;

    @NotBlank(message = "商品图片信息不能为空")
    //商品描述图片的url
    private String imgUrl;

    // 因为我们是在数据库中单独存放了促销信息和商品，因此我们需要在商品模型中增加一个促销相关的属性
    // 使用聚合模型，如果promoModel不为空，表示其还有还未结束得秒杀
    private PromoModel promoModel;
}

