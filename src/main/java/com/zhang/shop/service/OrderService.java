package com.zhang.shop.service;

import com.zhang.shop.error.BusinessException;
import com.zhang.shop.service.model.OrderModel;

public interface OrderService {

    OrderModel createOrder(Integer userId, Integer itemId, Integer promoId, Integer amount, String stockLogId) throws BusinessException;

}
