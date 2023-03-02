package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class RedisIdWorker {
    /*
        开始事件戳
     */
    private static final long BEGIN_TIMESTMAP = 1640995200L;
    private static final int COUNT_BITS = 32;
    private StringRedisTemplate stringRedisTemplate;

    public RedisIdWorker(StringRedisTemplate stringRedisTemplate){
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public long nextId(String keyPrefix){
        //1.生成事件戳
        LocalDateTime now = LocalDateTime.now();
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp = nowSecond - BEGIN_TIMESTMAP;
        //2.生成序列号
        //2.1.获取当前日期，精确到天
        //2.2.自增长
        String date = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        long count  = stringRedisTemplate.opsForValue().increment("icr："+keyPrefix+":"+date);
        //3.拼接并返回
        return timestamp << COUNT_BITS | count;
    }

    public static void main(String[] args) {
        LocalDateTime time = LocalDateTime.of(2022,1,1,0,0,0);
        System.out.println(time.toEpochSecond(ZoneOffset.UTC));
    }
}
