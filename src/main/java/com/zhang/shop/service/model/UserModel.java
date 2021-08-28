package com.zhang.shop.service.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.NotBlank;
import org.springframework.stereotype.Component;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
//import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.io.Serializable;


@Data
@NoArgsConstructor
@AllArgsConstructor
@Component
public class UserModel implements Serializable {

    private Integer id;

    @NotBlank(message = "用户名不为空") // 注意这个NotBlank需要使用hibernate的
    private String name;

    @NotNull(message = "性别不为空")
    private Byte gender;

    @NotNull(message = "年龄不为空")
    @Max(value = 150, message = "你是神仙吗？")
    @Min(value = 0, message = "年龄不合法")
    private Integer age;

    @NotBlank(message = "手机号不为空")
    private String telephone;
    private String registerMode;
    private String thirdPartyId;

    @NotBlank(message = "密码不为空")
    private String encryptPassword;

}
