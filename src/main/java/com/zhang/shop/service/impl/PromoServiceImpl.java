package com.zhang.shop.service.impl;

import com.zhang.shop.dao.PromoDOMapper;
import com.zhang.shop.dataObject.PromoDO;
import com.zhang.shop.service.ItemService;
import com.zhang.shop.service.PromoService;
import com.zhang.shop.service.UserService;
import com.zhang.shop.service.model.ItemModel;
import com.zhang.shop.service.model.PromoModel;
import com.zhang.shop.service.model.UserModel;
import org.joda.time.DateTime;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class PromoServiceImpl implements PromoService {

    @Autowired
    private PromoDOMapper promoDOMapper;

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private ItemService itemService;

    @Autowired
    private UserService userService;

    @Override
    public PromoModel getPromByGetId(Integer id) {
        // 获取对应商品的秒杀信息
        PromoDO promoDO = promoDOMapper.selectByItemId(id);
        // dataObject -> model
        PromoModel promoModel = convertFromDataObject(promoDO);
        if(promoDO == null) {
            return null;
        }

        //判断当前时间是否秒杀活动即将开始或正在进行
        //DateTime now = new DateTime();
        if (promoModel.getStartDate().isAfterNow()) {
            promoModel.setStatus(1);
        } else if (promoModel.getEndDate().isBeforeNow()) {
            promoModel.setStatus(3);
        } else {
            promoModel.setStatus(2);
        }

        return promoModel;
    }

    @Override
    public void publishPromo(Integer promoId) {
        //通过活动id获取活动
        PromoDO promoDO=promoDOMapper.selectByPrimaryKey(promoId);
        if(promoDO.getItemId()==null || promoDO.getItemId().intValue()==0) {
            return;
        }
        ItemModel itemModel=itemService.getItemById(promoDO.getItemId());
        //库存同步到Redis
        redisTemplate.opsForValue().set("promo_item_stock_"+itemModel.getId(),itemModel.getStock());
        //大闸限制数量设置到redis内
        redisTemplate.opsForValue().set("promo_door_count_" + promoId, itemModel.getStock().intValue() * 5);
    }

    // 生成秒杀令牌 进行活动商品的检查
    @Override
    public String generateSecondKillToken(Integer promoId,Integer itemId,Integer userId)  {
        //判断库存是否售罄，若Key存在，则直接返回下单失败
        if(redisTemplate.hasKey("promo_item_stock_invalid_"+itemId)) {
            return null;
        }
        PromoDO promoDO = promoDOMapper.selectByPrimaryKey(promoId);
        PromoModel promoModel = convertFromDataObject(promoDO);
        if(promoModel == null) {
            return null;
        }
        if(promoModel.getStartDate().isAfterNow()) {
            promoModel.setStatus(1);
        }else if(promoModel.getEndDate().isBeforeNow()){
            promoModel.setStatus(3);
        }else{
            promoModel.setStatus(2);
        }

        //判断活动是否正在进行
        if(promoModel.getStatus()!=2) {
            return null;
        }
        //判断item信息是否存在
        ItemModel itemModel=itemService.getItemByIdInCache(itemId);
        if(itemModel==null) {
            return null;
        }
        //判断用户是否存在
        UserModel userModel=userService.getUserByIdInCache(userId);
        if(userModel==null) {
            return null;
        }

        //获取大闸数量 人为的控制
        long result = redisTemplate.opsForValue().
                increment("promo_door_count_" + promoId, -1);
        if (result < 0) {
            return null;
        }
        //令牌生成

        //生成Token，并且存入redis内，5分钟时限 令牌是对于每个用户的每个商品的。
        String token= UUID.randomUUID().toString().replace("-","");
        redisTemplate.opsForValue().set("promo_token_"+promoId+"_userid_"+userId+"_itemid_"+itemId,token);
        redisTemplate.expire("promo_token_"+promoId+"_userid_"+userId+"_itemid_"+itemId, 5, TimeUnit.MINUTES);
        return token;
    }

    private PromoModel convertFromDataObject(PromoDO promoDO){
        if (promoDO == null){
            return null;
        }
        PromoModel promoModel = new PromoModel();
        BeanUtils.copyProperties(promoDO, promoModel);
        promoModel.setPromoItemPrice(new BigDecimal(promoDO.getPromoItemPrice()));
        promoModel.setStartDate(new DateTime(promoDO.getStartData()));
        promoModel.setEndDate(new DateTime(promoDO.getEndData()));
        return promoModel;
    }
}
