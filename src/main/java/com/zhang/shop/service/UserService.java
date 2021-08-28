package com.zhang.shop.service;

import com.zhang.shop.error.BusinessException;
import com.zhang.shop.service.model.UserModel;

public interface UserService {

    // 通过用户ID获取用户对象的方法
    UserModel getUserById(Integer id);
    void register(UserModel userModel) throws BusinessException;
    /*
    * telephone:用户注册的手机
    * password：用户加密以后的密码
    */
    UserModel validateLogin(String telephone, String encryptPassword) throws BusinessException;

    // 从缓存里面提取用户信息
    UserModel getUserByIdInCache(Integer id);
}
