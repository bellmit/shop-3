package com.zhang.shop.controller;

import com.zhang.shop.dao.UserDOMapper;
import com.zhang.shop.dataObject.UserDO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController //@Controller+@ResponseBody
public class HelloController {
    public HelloController() {
    }

    @RequestMapping("/")
    public String hello(){
        UserDO userDO = userDOMapper.selectByPrimaryKey(1);
        if (userDO == null){
            return "不存在用户";
        }else{
            return userDO.getName();
        }

    }

    @Autowired
    private UserDOMapper userDOMapper;


}
