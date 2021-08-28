package com.zhang.shop.response;

import lombok.*;

public class CommonReturnType {
    // 表明对应请求的返回处理结果“success“或”fail”
    @Getter
    @Setter
    private String status;
    //若status=success，则data内返回前端需要的json数据
    //若status=fail，则data内使用通用的错误码格式
    @Getter
    @Setter
    private Object data;

    //定义一个通用的创建方法
    public static CommonReturnType create(Object result) {
        return CommonReturnType.create(result, "success");
    }

    // 如果带status采用这个放啊
    public static CommonReturnType create(Object result,String status) {
        CommonReturnType type = new CommonReturnType();
        type.setStatus(status);
        type.setData(result);
        return type;
    }
}
