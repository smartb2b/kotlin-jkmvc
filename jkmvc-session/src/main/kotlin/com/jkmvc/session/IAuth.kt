package com.jkmvc.session

import com.jkmvc.orm.Orm

/**
 * 认证用户
 *   1 登录
 *   2 注销
 *   3 密码加密
 *
 * @author shijianhang
 * @create 2017-09-19 下午11:35
 **/
interface IAuth {

    /**
     * 获得当前登录用户
     * @return
     */
    public fun getUser(): IAuthUserModel?

    /**
     * 登录验证
     *
     * @param username  用户名
     * @param password  密码
     * @return Orm?
     */
    public fun login(username:String, password:String): IAuthUserModel?

    /**
     * 注销登录
     */
    public fun logout()
}