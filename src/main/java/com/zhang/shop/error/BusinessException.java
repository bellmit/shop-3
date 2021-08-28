package com.zhang.shop.error;

import org.springframework.stereotype.Component;


public class BusinessException extends Exception implements CommonError{

    private CommonError commonError;

    // 对应的构造函数
    //直接接受EmBusinessError的传参用于构造业务异常
    public BusinessException(CommonError commonError) {
        super();//调用方法，可能会有自己的构造操作。
        this.commonError = commonError;
    }

    //接收自定义errMsg的方式构造业务异常
    public BusinessException(CommonError commonError, String errMsg) {
        super();
        this.commonError = commonError;
        this.commonError.setErrMsg(errMsg);
    }
    @Override
    public int getErrCode() {
        return this.commonError.getErrCode();
    }

    @Override
    public String getErrMsg() {
        return this.commonError.getErrMsg();
    }

    @Override
    public CommonError setErrMsg(String errMs) {
        this.commonError.setErrMsg(errMs);
        return this;
    }
}
