package com.zhang.shop.controller;

import com.zhang.shop.controller.viewObject.ItemVO;
import com.zhang.shop.error.BusinessException;
import com.zhang.shop.mq.MqProducer;
import com.zhang.shop.response.CommonReturnType;
import com.zhang.shop.service.CacheService;
import com.zhang.shop.service.ItemService;
import com.zhang.shop.service.PromoService;
import com.zhang.shop.service.model.ItemModel;
import org.joda.time.format.DateTimeFormat;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Controller("item")
@RequestMapping("/item")
//跨域请求中，不能做到session共享
@CrossOrigin(allowCredentials = "true", allowedHeaders = "*",originPatterns = "*")
//@CrossOrigin(origins = "http://localhost:63342", allowedHeaders = "*", methods = {}, allowCredentials = "true")
public class ItemController extends BaseController{

    @Autowired
    private ItemService itemService;

    @Autowired
    private RedisTemplate<String,Object> redisTemplate;

    @Autowired
    private CacheService cacheService;

    @Autowired
    private PromoService promoService;

    @Autowired
    private MqProducer mqProducer;

    //创建商品的controller
    @RequestMapping(value = "/create", method = {RequestMethod.POST}, consumes = {CONTENT_TYPE_FORMED})
    @ResponseBody
    public CommonReturnType createItem(@RequestParam(name = "title") String title,
                                       @RequestParam(name = "description") String description,
                                       @RequestParam(name = "price") BigDecimal price,
                                       @RequestParam(name = "stock") Integer stock,
                                       @RequestParam(name = "imgUrl") String imgUrl) throws BusinessException {
        //封装service请求用来创建商品
        ItemModel itemModel = new ItemModel();
        itemModel.setTitle(title);
        itemModel.setDescription(description);
        itemModel.setPrice(price);
        itemModel.setStock(stock);
        itemModel.setImgUrl(imgUrl);

        ItemModel itemModelForReturn = itemService.createItem(itemModel);
        ItemVO itemVO = convertVOFromModel(itemModelForReturn);
        // 最后需要给前端返回itemVO
        return CommonReturnType.create(itemVO);

    }

    @RequestMapping(value = "/publishpromo", method = {RequestMethod.GET})
    @ResponseBody
    public CommonReturnType publishPromo(@RequestParam(name = "id")Integer id){
        promoService.publishPromo(id);
        return CommonReturnType.create(null);
    }

    //获取商品的列表
    @RequestMapping(value = "/get", method = {RequestMethod.GET})
    @ResponseBody
    public CommonReturnType getItem(@RequestParam(name = "id")Integer id){
        // 简单的数据库查询方式
        //ItemModel itemModel = itemService.getItemById(id);

        ItemModel itemModel = null;
        // 2021.02.15 升级为本地热点缓存+redis缓存
        //第一级：先去本地缓存
        itemModel=(ItemModel)cacheService.getFromCommonCache("item_"+id);
        //如果不存在，就执行下游操作，到数据查询
        if (itemModel == null) {
            itemModel=(ItemModel)redisTemplate.opsForValue().get("item_"+id);
            if (itemModel == null) {
                itemModel = itemService.getItemById(id);
                //设置itemModel到redis服务器
                redisTemplate.opsForValue().set("item_" + id, itemModel);
                //设置失效时间 10min
                redisTemplate.expire("item_" + id, 10, TimeUnit.MINUTES);
            }
            //填充本地缓冲
            cacheService.setCommonCache("item_"+id,itemModel);
        }
        ItemVO itemVO=convertVOFromModel(itemModel);
        return CommonReturnType.create(itemVO);

    }

    private ItemVO convertVOFromModel(ItemModel itemModel) {
        if (itemModel == null) {
            return null;
        }
        ItemVO itemVO = new ItemVO();
        BeanUtils.copyProperties(itemModel, itemVO);
        // 如果存在秒杀相关的信息，我们需要在item中进行添加秒杀有关的参数
        if (itemModel.getPromoModel()!=null) {
            itemVO.setPromoStatus(itemModel.getPromoModel().getStatus());
            itemVO.setStartDate(itemModel.getPromoModel().getStartDate().toString(DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss")));
            itemVO.setPromoId(itemModel.getPromoModel().getId());
            itemVO.setPromoPrice(itemModel.getPromoModel().getPromoItemPrice());
        }else{
            itemVO.setPromoStatus(0);
        }
        return itemVO;
    }

    //商品列表页面浏览
    @RequestMapping(value = "/list", method = {RequestMethod.GET})
    @ResponseBody
    public CommonReturnType listItem() {
        List<ItemModel> itemModelList = itemService.listItem();
        // 使用Java8的stream API
        List<ItemVO> itemVOList = itemModelList.stream().map(itemModel -> {
            ItemVO itemVO = this.convertVOFromModel(itemModel);
            return itemVO;
        }).collect(Collectors.toList());

        return CommonReturnType.create(itemVOList);
    }

}

