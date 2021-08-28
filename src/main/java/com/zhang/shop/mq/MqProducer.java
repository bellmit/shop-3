package com.zhang.shop.mq;

import com.alibaba.fastjson.JSON;
import com.zhang.shop.dao.StockLogDOMapper;
import com.zhang.shop.dataObject.StockLogDO;
import com.zhang.shop.service.OrderService;
import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.*;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.remoting.exception.RemotingException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

@Component
public class MqProducer {

    private DefaultMQProducer producer;
    //即是IP:9867
    @Value("${mq.nameserver.addr}")
    private String nameAddr;

    //即是stock
    @Value("${mq.topicname}")
    private String topicName;

    //private String topicName = "stock";
    //private String nameAddr = "123.56.52.77:9876";

    private TransactionMQProducer transactionMQProducer;

    @Autowired
    private OrderService orderService;

    @Autowired
    private StockLogDOMapper stockLogDOMapper;

    @PostConstruct
    public void init() throws MQClientException {
        //Producer初始化，Group对于生产者没有意义，但是消费者有意义
        producer=new DefaultMQProducer("producer_group");
        // 初始化地址
        producer.setNamesrvAddr(nameAddr);
        producer.start();


        transactionMQProducer = new TransactionMQProducer("transaction_producer_group");
        transactionMQProducer.setNamesrvAddr(nameAddr);
        transactionMQProducer.start();

        transactionMQProducer.setTransactionListener(new TransactionListener() {
             @Override
             public LocalTransactionState executeLocalTransaction(Message message, Object args) {
                 //在事务型消息中去进行下单
                 Integer itemId = (Integer) ((Map) args).get("itemId");
                 Integer promoId = (Integer) ((Map) args).get("promoId");
                 Integer userId = (Integer) ((Map) args).get("userId");
                 Integer amount = (Integer) ((Map) args).get("amount");
                 String stockLogId = (String) ((Map) args).get("stockLogId");
                 try {
                     orderService.createOrder(userId, itemId, promoId, amount, stockLogId);
                 } catch (Exception e) {
                     e.printStackTrace();
                     //如果发生异常，createOrder已经回滚，此时要回滚事务型消息。
                     //设置stockLog为回滚状态
                     StockLogDO stockLogDO = stockLogDOMapper.selectByPrimaryKey(stockLogId);
                     stockLogDO.setStatus(3);
                     stockLogDOMapper.updateByPrimaryKeySelective(stockLogDO);
                     return LocalTransactionState.ROLLBACK_MESSAGE;
                 }
                 return LocalTransactionState.COMMIT_MESSAGE;
             }

             // 处理unknown状态
            @Override
            public LocalTransactionState checkLocalTransaction(MessageExt messageExt) {
                 // 根据是否扣减内存成功，判断状态
                String jsonString = new String(messageExt.getBody());
                Map<String, Object> map = JSON.parseObject(jsonString, Map.class);
                String stockLogId = (String) map.get("stockLogId");
                StockLogDO stockLogDO = stockLogDOMapper.selectByPrimaryKey(stockLogId);
                if (stockLogDO == null) {
                    return LocalTransactionState.UNKNOW;
                }
                //订单操作已经完成，等着异步扣减库存，那么就提交事务型消息
                if (stockLogDO.getStatus() == 2) {
                    return LocalTransactionState.COMMIT_MESSAGE;
                    //订单操作还未完成，需要执行下单操作，那么就维持为prepare状态
                } else if (stockLogDO.getStatus() == 1) {
                    return LocalTransactionState.UNKNOW;
                }
                //否则就回滚
                return LocalTransactionState.ROLLBACK_MESSAGE;
            }
        });

    }


    // 事务型消息同步库存扣减消息
    public boolean transactionAsyncReduceStock(Integer userId, Integer itemId, Integer promoId, Integer amount, String stockLogId) {
        Map<String, Object> bodyMap = new HashMap<>();
        bodyMap.put("itemId", itemId);
        bodyMap.put("amount", amount);
        bodyMap.put("stockLogId",stockLogId);

        //用于执行orderService.createOrder的传参
        Map<String, Object> argsMap = new HashMap<>();
        argsMap.put("itemId", itemId);
        argsMap.put("amount", amount);
        argsMap.put("userId", userId);
        argsMap.put("promoId", promoId);
        argsMap.put("stockLogId",stockLogId);

        Message message = new Message(topicName, "increase",
                JSON.toJSON(bodyMap).toString().getBytes(Charset.forName("UTF-8")));

        TransactionSendResult result = null;
        try {
            //注意，发送的是sendMessageInTransaction
            // 是一个prepare状态，不会被执行
            result = transactionMQProducer.sendMessageInTransaction(message, argsMap);
        } catch (MQClientException e) {
            e.printStackTrace();
            return false;
        }

        if (result.getLocalTransactionState() == LocalTransactionState.ROLLBACK_MESSAGE){
            return false;
        }else if (result.getLocalTransactionState() == LocalTransactionState.COMMIT_MESSAGE){
            return true;
        }else{
            return false;
        }
    }



    // 异步扣减内存
    public boolean asyncReduceStock(Integer itemId, Integer amount)  {
        Map<String,Object> bodyMap=new HashMap<>();
        bodyMap.put("itemId",itemId);
        bodyMap.put("amount",amount);
        //创建消息
        Message message=new Message(topicName,"increase",
                JSON.toJSON(bodyMap).toString().getBytes(Charset.forName("UTF-8")));
        //发送消息
        try {
            producer.send(message);
        } catch (MQClientException e) {
            e.printStackTrace();
            return false;
        } catch (InterruptedException e) {
            e.printStackTrace();
            return false;
        } catch (RemotingException e) {
            e.printStackTrace();
            return false;
        } catch (MQBrokerException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }
}
