package com.zhang.shop.service;

import com.zhang.shop.error.BusinessException;
import com.zhang.shop.service.model.ItemModel;

import java.util.List;

public interface ItemService {

    //创建商品
    ItemModel createItem(ItemModel itemModel) throws BusinessException;

    //商品列表浏览
    List<ItemModel> listItem();


    //商品详情浏览
    ItemModel getItemById(Integer id);

    //库存扣减
    boolean decreaseStock(Integer itemId, Integer amount) throws BusinessException;

    // 商品销量增加
    void increaseSales(Integer itemId, Integer amount) throws BusinessException;

    // 验证item及promo model是否有效
    ItemModel getItemByIdInCache(Integer id);

    // 异步更新库存
    boolean asyncDecreaseStock(Integer itemId, Integer amount);

    //库存回补
    boolean increaseStock(Integer itemId, Integer amount) throws BusinessException;

    // 库存流水
    String initStockLog(Integer itemId, Integer amount);
}
