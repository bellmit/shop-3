package com.zhang.shop.service;

import com.zhang.shop.service.model.PromoModel;
import org.springframework.stereotype.Service;


public interface PromoService {
    // 获得即将开始的。
    PromoModel getPromByGetId(Integer id);

    // 活动发布
    void publishPromo(Integer promoId);

    // 生成秒杀令牌
    String generateSecondKillToken(Integer promoId,Integer itemId,Integer userId) ;

}
