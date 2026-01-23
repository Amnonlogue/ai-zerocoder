package com.it.aizerocoder.model.dto.user;

import lombok.Data;

import java.io.Serializable;

/**
 * 用户登录接受请求类
 */

@Data
public class UserLoginRequest implements Serializable {

    private static final long serialVersionUID = 3191241716373120793L;

    /**
     * 账号
     */
    private String userAccount;

    /**
     * 密码
     */
    private String userPassword;
}
