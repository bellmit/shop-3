package com.zhang.shop.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhang.shop.dao.OrderDOMapper;
import com.zhang.shop.dao.SequenceDOMapper;
import com.zhang.shop.dao.StockLogDOMapper;
import com.zhang.shop.dataObject.OrderDO;
import com.zhang.shop.dataObject.SequenceDO;
import com.zhang.shop.dataObject.StockLogDO;
import com.zhang.shop.error.BusinessException;
import com.zhang.shop.error.EmBusinessError;
import com.zhang.shop.mq.MqProducer;
import com.zhang.shop.service.ItemService;
import com.zhang.shop.service.OrderService;
import com.zhang.shop.service.UserService;
import com.zhang.shop.service.model.ItemModel;
import com.zhang.shop.service.model.OrderModel;
import com.zhang.shop.service.model.UserModel;
import lombok.SneakyThrows;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationAdapter;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class OrderServiceImpl implements OrderService {
    @Autowired
    private ItemService itemService;

    @Autowired
    private UserService userService;

    @Autowired
    private OrderDOMapper orderDOMapper;

    @Autowired
    private SequenceDOMapper sequenceDOMapper;

    @Autowired
    private StockLogDOMapper stockLogDOMapper;


    @Override
    @Transactional
    public OrderModel createOrder(Integer userId, Integer itemId, Integer promoId, Integer amount, String stockLogId) throws BusinessException {
        //1.校验下单状态，下单的商品是否存在，用户是否合法（不需要再次验证了），购买数量是否正确
        ItemModel itemModel = itemService.getItemByIdInCache(itemId);
        if (itemModel == null) {
            throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR, "商品信息不存在");
        }

        if (amount <= 0 || amount > 99) {
            throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR, "数量信息不存在");
        }

        //2.落单减库存 在redis中减库存
        boolean result = itemService.decreaseStock(itemId, amount);
        if (!result) {
            throw new BusinessException(EmBusinessError.STOCK_NOT_ENOUGH);
        }

        //3.订单入库
        OrderModel orderModel = new OrderModel();
        orderModel.setUserId(userId);
        orderModel.setItemId(itemId);
        orderModel.setAmount(amount);

        if (promoId != null) {
            orderModel.setItemPrice(itemModel.getPromoModel().getPromoItemPrice());
        } else {
            orderModel.setItemPrice(itemModel.getPrice());
        }
        orderModel.setPromoId(promoId);
        orderModel.setOrderPrice(itemModel.getPrice().multiply(new BigDecimal(amount)));
        orderModel.setOrderPrice(orderModel.getItemPrice().multiply(BigDecimal.valueOf(amount)));


        //生成交易流水号
        orderModel.setId(generateOrderNo());
        OrderDO orderDO = this.convertFromOrderModel(orderModel);
        orderDOMapper.insertSelective(orderDO); // 数据库 这两个操作其实也是可以异步化的
        //加上商品的销量
        itemService.increaseSales(itemId, amount); // 数据库

        // 设置库存流水为成功
        StockLogDO stockLogDO = stockLogDOMapper.selectByPrimaryKey(stockLogId);
        if (stockLogDO == null) {
            throw new BusinessException(EmBusinessError.UNKNOWN_ERROR);
        }
        stockLogDO.setStatus(2);
        stockLogDOMapper.updateByPrimaryKeySelective(stockLogDO);

        //4.返回前端
        return orderModel;
    }

    // 因为这个事务会被别的事务调用，这里保证了别的事务的失败不会导致这个事务的归滚。一旦这个事务完成了，就直接提交。
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    // 这个函数不可以是private，否则会事务失效
    public String generateOrderNo(){
        StringBuffer sb = new StringBuffer();
        // 订单到16位
        // 前8位为时间信息，年月日
        LocalDateTime now = LocalDateTime.now(); // 默认是2021-01-27
        String nowDate = now.format(DateTimeFormatter.ISO_DATE).replace("-","");
        sb.append(nowDate);
        // 中间6位时自增序列
        // 获取当前sequence

        /* 这个方法会导致并发问题，因为是很多个单独的与数据的交互
        SequenceDO sequenceDO = sequenceDOMapper.getSequenceByName("order_info");
        Integer currentValue = sequenceDO.getCurrentValue();
        sequenceDO.setCurrentValue(currentValue+sequenceDO.getStep());
        sequenceDOMapper.updateByPrimaryKey(sequenceDO);
         */

        String sequenceStr;
        synchronized (this){
            SequenceDO sequenceDO = sequenceDOMapper.getSequenceByName("order_info");
            Integer currentValue = sequenceDO.getCurrentValue();
            sequenceDO.setCurrentValue(currentValue+sequenceDO.getStep());
            sequenceDOMapper.updateByPrimaryKey(sequenceDO);
            sequenceStr = String.valueOf(currentValue);
        }

        int len = 0;
        while(sequenceStr.length()+len<6){
            len++;
            sb.append("0");
        }
        sb.append(sequenceStr);
        // 最后两位时分库分表为
        sb.append("00");
        return sb.toString();
    }

    private OrderDO convertFromOrderModel(OrderModel orderModel) {
        if (orderModel == null) {
            return null;
        }
        OrderDO orderDO = new OrderDO();
        BeanUtils.copyProperties(orderModel, orderDO);
        orderDO.setItemPrice(orderModel.getItemPrice().doubleValue());
        //orderDO.setOrderPrice(orderModel.getOrderPrice().doubleValue());
        return orderDO;
    }

}
