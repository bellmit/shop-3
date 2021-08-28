package com.zhang.shop.controller;

import com.zhang.shop.controller.viewObject.UserVO;
import com.zhang.shop.error.BusinessException;
import com.zhang.shop.error.EmBusinessError;
import com.zhang.shop.response.CommonReturnType;
import com.zhang.shop.service.UserService;
import com.zhang.shop.service.model.UserModel;
import org.apache.commons.lang3.StringUtils;
import org.apache.tomcat.util.security.MD5Encoder;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Controller("user")
@RequestMapping("/user")
//跨域请求中，不能做到session共享
//@CrossOrigin(allowCredentials = "true",allowedHeaders = "*")
//@CrossOrigin(origins = {"*"}, allowedHeaders="*",allowCredentials = "true")
@CrossOrigin(allowCredentials = "true", allowedHeaders = "*",originPatterns = "*")
//@CrossOrigin(origins = "http://localhost:63342", allowedHeaders = "*", methods = {}, allowCredentials = "true")

public class UserController extends BaseController{
    @Autowired
    private UserService userService;

    @Autowired
    private HttpServletRequest httpServletRequest;

    @Autowired
    private RedisTemplate redisTemplate;



    //用户获取otp短信接口
    @RequestMapping(value = "/getotp", method = {RequestMethod.POST}, consumes = {CONTENT_TYPE_FORMED})
    @ResponseBody
    public CommonReturnType getOtp(@RequestParam(name = "telephone") String telephone) {
        //需要按照一定的规则生成OTP验证码
        Random random = new Random();
        int randomInt = random.nextInt(99999);
        randomInt += 10000;
        String otpCode = String.valueOf(randomInt);

        //将OTP验证码同对应用户的手机号关联，使用httpsession的方式绑定手机号与OTPCDOE
        // 首先想到的就是利用redis的kv对。将用户对应的phone和code放到redis。可以反复覆盖和时间锁。这个有待升级。
        httpServletRequest.getSession().setAttribute(telephone, otpCode);

        //将OTP验证码通过短信通道发送给用户，省略
        System.out.println("telephone=" + telephone + "&otpCode=" + otpCode);

        return CommonReturnType.create(null);
    }

    //用户注册接口
    @RequestMapping(value = "/register", method = {RequestMethod.POST}, consumes = {CONTENT_TYPE_FORMED})
    @ResponseBody
    public CommonReturnType register(@RequestParam(name = "telephone") String telephone,
                                     @RequestParam(name = "otpCode") String otpCode,
                                     @RequestParam(name = "name") String name,
                                     @RequestParam(name = "gender") String gender,
                                     @RequestParam(name = "age") String age,
                                     @RequestParam(name = "password") String password) throws BusinessException, UnsupportedEncodingException, NoSuchAlgorithmException {

        //验证手机号和对应的otpCode相符合
        String inSessionOtpCode = (String) this.httpServletRequest.getSession().getAttribute(telephone);

        if (!com.alibaba.druid.util.StringUtils.equals(otpCode, inSessionOtpCode)) {
            throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR, "短信验证码不符合");
        }
        //用户的注册流程
        UserModel userModel = new UserModel();
        userModel.setName(name);
        userModel.setAge(Integer.valueOf(age));
        userModel.setGender(Byte.valueOf(gender));
        userModel.setTelephone(telephone);
        userModel.setRegisterMode("byphone");

        //密码加密
        userModel.setEncryptPassword(this.EncodeByMd5(password));
        //userModel.setEncryptPassword("123");

        userService.register(userModel);
        return CommonReturnType.create(null);

    }


    // 标准流程，首先修改接口，添加相关的方法，然后再Impl里面实现方法。方法的实现中，可能需要修改或者添加sql语句。去Mapper中修改。
    //用户登录接口
    @RequestMapping(value = "/login", method = {RequestMethod.POST}, consumes = {CONTENT_TYPE_FORMED})
    @ResponseBody
    public CommonReturnType login(@RequestParam(name = "telephone") String telephone,
                                  @RequestParam(name = "password") String password) throws BusinessException, UnsupportedEncodingException, NoSuchAlgorithmException {
        //入参校验
        if (StringUtils.isEmpty(telephone) || StringUtils.isEmpty(password)) {
            throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR);
        }

        //用户登录服务，用来校验用户登录是否合法
        //用户加密后的密码
        UserModel userModel = userService.validateLogin(telephone, this.EncodeByMd5(password));

        // 生成登录凭证token， UUID
        String uuidToken=UUID.randomUUID().toString();
        uuidToken=uuidToken.replace("-","");
        //建立Token与用户登录态的联系
        redisTemplate.opsForValue().set(uuidToken,userModel);
        //设置超时时间
        redisTemplate.expire(uuidToken,1, TimeUnit.HOURS);
        return CommonReturnType.create(uuidToken);

        //将登陆凭证加入到用户登录成功的session内  // 这里用token会更好
//        this.httpServletRequest.getSession().setAttribute("IS_LOGIN", true);
//        this.httpServletRequest.getSession().setAttribute("LOGIN_USER", userModel);

        //return CommonReturnType.create(null);

    }


    @RequestMapping("/get")
    @ResponseBody
    public CommonReturnType getUser(@RequestParam(name = "id") Integer id) throws Exception{
        //调用service服务获取对应id的用户对象并返回给前端
        UserModel userModel = userService.getUserById(id);
        //若获取的对应用户信息不存在

        if (userModel == null) {
            throw new BusinessException(EmBusinessError.USER_NOT_EXIST); // 抛出之后需要解决
        }


        UserVO userVO = convertFromModel(userModel);
        // 返回通用
        return CommonReturnType.create(userVO);
    }

    // 使用UserVO，避免返回不必要的信息，如密码
    private UserVO convertFromModel(UserModel userModel) {
        if (userModel == null) {
            return null;
        }
        UserVO userVO = new UserVO();
        // 把model转换为vo版本
        BeanUtils.copyProperties(userModel, userVO);
        return userVO;

    }

    //密码加密
    public String EncodeByMd5(String str) throws NoSuchAlgorithmException, UnsupportedEncodingException {
        //确定计算方法
        MessageDigest md5 = MessageDigest.getInstance("MD5");
        Base64.Encoder base64en = Base64.getEncoder();
        //加密字符串
        String newstr = base64en.encodeToString(md5.digest(str.getBytes("utf-8")));
        return newstr;
    }

}
