package com.zhang.shop.service.impl;

import com.zhang.shop.dao.ItemDOMapper;
import com.zhang.shop.dao.ItemStockDOMapper;
import com.zhang.shop.dao.StockLogDOMapper;
import com.zhang.shop.dataObject.ItemDO;
import com.zhang.shop.dataObject.ItemStockDO;
import com.zhang.shop.dataObject.StockLogDO;
import com.zhang.shop.error.BusinessException;
import com.zhang.shop.error.EmBusinessError;
import com.zhang.shop.mq.MqProducer;
import com.zhang.shop.service.ItemService;
import com.zhang.shop.service.PromoService;
import com.zhang.shop.service.model.ItemModel;
import com.zhang.shop.service.model.PromoModel;
import com.zhang.shop.validator.ValidationResult;
import com.zhang.shop.validator.ValidatorImpl;
import org.hibernate.validator.constraints.NotBlank;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class ItemServiceImpl implements ItemService {

    @Autowired
    private ValidatorImpl validator;

    @Autowired
    private ItemDOMapper itemDOMapper;

    @Autowired
    private ItemStockDOMapper itemStockDOMapper;

    @Autowired
    private PromoService promoService;

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private MqProducer mqProducer;

    @Autowired
    private StockLogDOMapper stockLogDOMapper;

    @Override
    @Transactional
    public ItemModel createItem(ItemModel itemModel) throws BusinessException {

        //校验入参
        ValidationResult result = validator.validate(itemModel);
        if (result.isHasErrors()) {
            throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR, result.getErrMsg());
        }
        //转化itemmodel->dataobject
        ItemDO itemDO = this.convertItemDOFromItemModel(itemModel);

        //写入数据库
        itemDOMapper.insertSelective(itemDO);
        itemModel.setId(itemDO.getId());

        ItemStockDO itemStockDO = this.convertItemStockDOFromItemModel(itemModel);
        itemStockDOMapper.insertSelective(itemStockDO);

        //返回创建完成的对象
        return this.getItemById(itemModel.getId());

    }

    private ItemDO convertItemDOFromItemModel(ItemModel itemModel) {
        if (itemModel == null) {
            return null;
        }
        ItemDO itemDO = new ItemDO();
        BeanUtils.copyProperties(itemModel, itemDO);
        // 这里需要额外的转系，前端时候double有问题
        itemDO.setPrice(itemModel.getPrice().doubleValue());
        return itemDO;
    }

    private ItemStockDO convertItemStockDOFromItemModel(ItemModel itemModel) {
        if (itemModel == null) {
            return null;
        }
        ItemStockDO itemStockDO = new ItemStockDO();
        itemStockDO.setItemId(itemModel.getId());
        itemStockDO.setStock(itemModel.getStock());

        return itemStockDO;
    }

    @Override
    public List<ItemModel> listItem() {
        // 得到一个列表
        List<ItemDO> itemDOList = itemDOMapper.listItem();

        //使用Java8的stream API
//        List<ItemModel> itemModelList = itemDOList.stream().map(itemDO -> {
//            ItemStockDO itemStockDO = itemStockDOMapper.selectByItemId(itemDO.getId());
//            ItemModel itemModel = this.convertModelFromDataObject(itemDO, itemStockDO);
//            return itemModel;
//        }).collect(Collectors.toList());

        List<ItemModel> itemModelList = new ArrayList<>();

        for(ItemDO itemDO:itemDOList){
            ItemStockDO itemStockDO = itemStockDOMapper.selectByItemId(itemDO.getId());
            ItemModel itemModel = this.convertModelFromDataObject(itemDO, itemStockDO);
            itemModelList.add(itemModel);
        }

        return itemModelList;
    }

    @Override
    public ItemModel getItemById(Integer id) {
        ItemDO itemDO = itemDOMapper.selectByPrimaryKey(id);
        if (itemDO == null) {
            return null;
        }
        //操作获得库存数量
        ItemStockDO itemStockDO = itemStockDOMapper.selectByItemId(itemDO.getId());

        //将itemObject-> Model
        ItemModel itemModel = convertModelFromDataObject(itemDO, itemStockDO);

        // 获取活动商品信息
        PromoModel promoModel = promoService.getPromByGetId(itemModel.getId());
        // 需要把商品和促销信息关联起来
        // 如果存在秒杀，并且秒杀还没进行完成的话，在商品属性上添加上促销的值。
        if(promoModel != null && promoModel.getStatus() != 3){
            itemModel.setPromoModel(promoModel);
        }
        return itemModel;
    }

    @Override
    @Transactional(rollbackFor=Exception.class)
    public boolean decreaseStock(Integer itemId, Integer amount) throws BusinessException {
        // 缓存扣减
        long affectedRow = redisTemplate.opsForValue().
                increment("promo_item_stock_" + itemId, amount.intValue() * -1);
        //>0，表示Redis扣减成功
        if (affectedRow > 0) {
            return true;
        } else if (affectedRow == 0) {
            //打上售罄标识
            redisTemplate.opsForValue().set("promo_item_stock_invalid_" + itemId, "true");
            return true;
        } else {
            increaseStock(itemId, amount);
            return false;
        }
    }

    // 这个方法也可以使用自己创建一个sql语句,这也是一个危险的操作
    @Override
    @Transactional(rollbackFor = Exception.class)
    synchronized public void increaseSales(Integer itemId, Integer amount) throws BusinessException {
        ItemDO itemDO = itemDOMapper.selectByPrimaryKey(itemId);
        itemDO.setSales(itemDO.getSales()+amount);
        itemDOMapper.updateByPrimaryKeySelective(itemDO);
    }

    // 把商品模型放到了缓存里面
    @Override
    public ItemModel getItemByIdInCache(Integer id) {
        ItemModel itemModel=(ItemModel)redisTemplate.opsForValue().get("item_validate_"+id);
        if(itemModel==null){
            itemModel=this.getItemById(id);
            redisTemplate.opsForValue().set("item_validate_"+id,itemModel);
            redisTemplate.expire("item_validate_"+id,10, TimeUnit.MINUTES);
        }
        return itemModel;
    }

    // 异步的扣减库存
    @Deprecated
    @Override
    public boolean asyncDecreaseStock(Integer itemId, Integer amount) {
        boolean mqResult = mqProducer.asyncReduceStock(itemId, amount);
        return mqResult;
    }

    // 补回redis 的扣减
    @Override
    public boolean increaseStock(Integer itemId, Integer amount) throws BusinessException {
        redisTemplate.opsForValue().increment("promo_item_stock_" + itemId, amount.intValue());
        return true;
    }

    @Override
    @Transactional
    // 初始化库存流水
    public String initStockLog(Integer itemId, Integer amount) {
        StockLogDO stockLogDO = new StockLogDO();
        stockLogDO.setItemId(itemId);
        stockLogDO.setAmount(amount);
        stockLogDO.setStockLogId(UUID.randomUUID().toString().replace("-", ""));
        //1表示初始状态，2表示下单扣减库存成功，3表示下单回滚
        stockLogDO.setStatus(1);
        stockLogDOMapper.insertSelective(stockLogDO);
        return stockLogDO.getStockLogId();
    }

    private ItemModel convertModelFromDataObject(ItemDO itemDO, ItemStockDO itemStockDO) {
        ItemModel itemModel = new ItemModel();
        BeanUtils.copyProperties(itemDO, itemModel);
        // 这里同样需要转换
        itemModel.setPrice(new BigDecimal(itemDO.getPrice()));
        itemModel.setStock(itemStockDO.getStock());
        return itemModel;
    }
}
