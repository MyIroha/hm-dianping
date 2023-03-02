package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@Component
@Slf4j
public class  CacheClient {
    private StringRedisTemplate stringRedisTemplates;

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);


    public CacheClient(StringRedisTemplate redisTemplates){
        this.stringRedisTemplates = redisTemplates;
    }

    public <T> void set(String key, T val, Long time, TimeUnit unit){
       stringRedisTemplates.opsForValue().set(key, JSONUtil.toJsonStr(val),time,unit);
    }

    public <T> void setWithLogicExpire(String key, T val,Long time,TimeUnit unit){
        Redis$Data<T> tRedisData = new Redis$Data<>();
        tRedisData.setData(val);
        tRedisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        stringRedisTemplates.opsForValue().set(key, JSONUtil.toJsonStr(tRedisData));
    }

    @Data
    class Redis$Data<T>{
        private LocalDateTime expireTime;
        private T data;
    }

    //TODO:缓存穿透工具类
    public <T,I> T queryWithPassThrough(String keyPrefix,
                                        I id,
                                        Class<T> type,
                                        Function<I,T> dbFallBack,
                                        Long time,
                                        TimeUnit unit){


        String key = keyPrefix+id;
        log.debug("queryWithPassThrough:shopJson is null"+key);
        //1.从Redis查看商铺缓存
        String json = stringRedisTemplates.opsForValue().get(key);
        //2.判断是否存在
        if(StrUtil.isNotBlank(json)){
            return JSONUtil.toBean(json, type);
        }
        //判断命中的是否是空值
        if(json != null){
            return null;
        }
        log.debug("queryWithPassThrough:shopJson is null"+json);
        //4.不存在，更具id查询数据库
        T t = dbFallBack.apply(id);
        //5.数据库不存在，返回错误
        if(t == null){
            stringRedisTemplates.opsForValue().set(key,"",RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        //6.存在，先写入Redis目录中
        this.set(key,t,time,unit);
        //7.返回
        return t;
    }

    //TODO:缓存击穿 - 逻辑过期工具类
    public  <T,I> T queryWithLogicalExpire(String keyPrefix,
                                           I id,
                                           Class<T> type,
                                           Function<I,T> dbFallBack,
                                           Long time,
                                           TimeUnit unit
                                           ){
        String key = RedisConstants.CACHE_SHOP_KEY+id;
        //1.从Redis查看商铺缓存
        String json = stringRedisTemplates.opsForValue().get(key);
        //2.判断是否存在
        if(StrUtil.isBlank(json)){
            //不存在直接返回null
            return null;
        }
        //4.命中，需要先发json发序列化为对象
        Redis$Data<T> redisData = JSONUtil.toBean(json, Redis$Data.class);
        JSONObject data = (JSONObject) redisData.getData();
        T t = JSONUtil.toBean(data,type);
        LocalDateTime expireTime = redisData.getExpireTime();
        //5.判断是否过期
        if(expireTime.isAfter(LocalDateTime.now())){
            //5.1. 未过期，直接返回店铺信息
            return t;
        }
        //5.2. 已过期，需要缓存重建
        //6. 缓存重建
        //6.1 获取互斥锁
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);
        if(isLock){
            //6.3 成功，开启独立线程实现缓存重建
            CACHE_REBUILD_EXECUTOR.submit(()->{
                //重建缓存
                try {
                    T t1 = dbFallBack.apply(id);
                    this.setWithLogicExpire(key,t1,time,unit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    unLock(lockKey);
                }
                //释放锁

            });
        }


        //7.返回
        return t;
    }

    private boolean tryLock(String key){
        log.debug("ShopServiceImpl:tryLock:start");
        boolean b = stringRedisTemplates.opsForValue().setIfAbsent(key,"1",10,TimeUnit.SECONDS);
        log.debug("ShopServiceImpl:tryLock:done");
        return BooleanUtil.isTrue(b);
    }

    private void unLock(String key){
        log.debug("ShopServiceImpl:unLock:start");
        stringRedisTemplates.delete(key);
        log.debug("ShopServiceImpl:unLock:done");
    }
}
