package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.Console;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
public class LoginInterceptor implements HandlerInterceptor {

//    private StringRedisTemplate redisTemplate;

//    public LoginInterceptor(StringRedisTemplate redisTemplate) {
//        this.redisTemplate = redisTemplate;
//    }
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //1.判断是否需要拦截，（Threadlocal中是否有用户）

        if(UserHolder.getUser() == null){

            //没有 需要拦截 设置状态码
            response.setStatus(401);
            return false;
        }
        log.debug(UserHolder.getUser()+"");

//        String token = request.getHeader("authorization");
//        System.out.println("--------------------------------------------------"+token);
//        if(StrUtil.isBlank(token)){
//            response.setStatus(401);
//            return false;
//        }
//        //给予token获取redis中的用户
//        Map<Object, Object> userMap = redisTemplate.opsForHash()
//                .entries(token);
//        log.debug("preHandle:"+userMap.toString());
//
//        if(userMap.isEmpty()){
//            response.setStatus(401);
//            return false;
//        }
//        //将查询到的hash数据转换为User对象
//        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
//        log.debug("preHandle:"+userDTO);
//        //存在的用户保存到TreadLocal中
//        UserHolder.saveUser(userDTO);
//        //刷新token的有效期
//        redisTemplate.expire(token,RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);
        //有用户放行
        return true;

    }

}
