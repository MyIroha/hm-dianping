package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.beans.Transient;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheClient cacheClient;

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    @Override
    @Transactional
    public Result update(Shop shop) {
        log.debug("ShopServiceImpl:update:start");
        Long id = shop.getId();
        if(id == null){
            return Result.fail("店铺id不存在");
        }
        String key = RedisConstants.CACHE_SHOP_KEY+id;
        //1.更新数据库
        updateById(shop);
        //2，删除缓存
        stringRedisTemplate.delete(key);
        return Result.ok();
    }

    @Override
    public Result queryById(Long id) {
        log.debug("ShopServiceImpl:queryById:Start");

        Shop shop = (Shop) cacheClient.queryWithPassThrough(
                RedisConstants.CACHE_SHOP_KEY,
                id,
                Shop.class,
                this::getById,
                RedisConstants.CACHE_SHOP_TTL,
                TimeUnit.MINUTES);

////        Shop shop = this.queryWithPassThrough(id);
////        Shop shop = queryWithMutex(id);
////        if(shop == null ){
////            log.debug("ShopServiceImpl:queryById:Result-Fail:Done");
////            return Result.fail("店铺不存在");
////        }
//        Shop shop = queryWithLogicalExpire(id);
//        log.debug("ShopServiceImpl:queryById:Result-True:Done");
        return Result.ok(shop);
    }

    //TODO:缓存击穿解决方案 - 互斥锁
//    public Shop queryWithMutex(Long id){
//        log.debug("ShopServiceImpl:queryWithMutex:start");
//        String key = RedisConstants.CACHE_SHOP_KEY+id;
//        //1.从Redis查看商铺缓存
//        String shopJson = stringRedisTemplate.opsForValue().get(key);
//        //2.判断是否存在
//        if(StrUtil.isNotBlank(shopJson)){
//            //3.存在直接返回
//            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
//            return shop;
//        }
//        //判断命中的是否是空值
//        if(shopJson != null){
//            return null;
//        }
//
//        //实现缓存重建
//        //获取互斥锁
//        String lockKey = null;
//        Shop shop = null;
//        try {
//            lockKey = RedisConstants.LOCK_SHOP_KEY + id;
//            log.debug("ShopServiceImpl:queryWithMutex:lockKey:${}",lockKey);
//            boolean bol = tryLock(lockKey);
//            //判断是否获取成功
//            if(!bol){
//                log.debug("ShopServiceImpl:queryWithMutex:restart");
//                //失败，则休眠并重试
//                Thread.sleep(50);
//                return queryWithMutex(id);
//            }
//            //4.成功，更具id查询数据库
//            shop = getById(id);
//            //模拟重建延迟
//            Thread.sleep(200);
//            //5.数据库不存在，返回错误
//            if(shop == null){
//                stringRedisTemplate.opsForValue().set(key,"",RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
//                return null;
//            }
//            //6.存在，先写入Redis目录中
//            stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
//        } catch (InterruptedException e) {
//            throw new RuntimeException(e);
//        } finally {
//            //7.释放互斥锁
//            log.debug("ShopServiceImpl:queryWithMutex:unLock:lockKey:${}",lockKey);
//            unLock(lockKey);
//        }
//
//        log.debug("ShopServiceImpl:queryWithMutex:done");
//        //返回对象
//        return shop;
//    }
//
//    //TODO:缓存击穿解决方案 - 逻辑过期
//    public Shop queryWithLogicalExpire(Long id){
//        log.debug("ShopServiceImpl:queryWithLogicalExpire:start");
//        String key = RedisConstants.CACHE_SHOP_KEY+id;
//        //1.从Redis查看商铺缓存
//        String shopJson = stringRedisTemplate.opsForValue().get(key);
//        //2.判断是否存在
//        if(StrUtil.isBlank(shopJson)){
//            //不存在直接返回null
//            return null;
//        }
//        //4.命中，需要先发json发序列化为对象
//        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
//        JSONObject data = (JSONObject) redisData.getData();
//        Shop shop = JSONUtil.toBean(data,Shop.class);
//        LocalDateTime expireTime = redisData.getExpireTime();
//        //5.判断是否过期
//        if(expireTime.isAfter(LocalDateTime.now())){
//            //5.1. 未过期，直接返回店铺信息
//            return shop;
//        }
//        //5.2. 已过期，需要缓存重建
//        //6. 缓存重建
//        //6.1 获取互斥锁
//        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
//        boolean isLock = tryLock(lockKey);
//        if(isLock){
//            //6.3 成功，开启独立线程实现缓存重建
//            CACHE_REBUILD_EXECUTOR.submit(()->{
//                //重建缓存
//                try {
//                    this.saveShop2Redis(id,20l);
//                } catch (Exception e) {
//                    throw new RuntimeException(e);
//                } finally {
//                    unLock(lockKey);
//                }
//                //释放锁
//
//            });
//        }
//
//
//        //6.4 返回过期的商铺信息
//
//        //7.返回
//        return shop;
//    }

    //存储逻辑过期时间
    public void saveShop2Redis(Long id,Long expireSeconds) throws InterruptedException {
        String key = RedisConstants.CACHE_SHOP_KEY+id;
        //1.查询店铺数据
        Shop shop = getById(id);
        Thread.sleep(200);
        //2.封装逻辑过期
        RedisData<Shop> redisData = new RedisData<>();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        //3.写入Redis
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(redisData));
    }

    //TODO:缓存穿透解决方案
//    public Shop queryWithPassThrough(Long id){
//        log.debug("ShopServiceImpl:queryById:start");
//        String key = RedisConstants.CACHE_SHOP_KEY+id;
//        //1.从Redis查看商铺缓存
//        String shopJson = stringRedisTemplate.opsForValue().get(key);
//        //2.判断是否存在
//        if(StrUtil.isNotBlank(shopJson)){
//            //3.存在直接返回
//            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
//            return shop;
//        }
//        //判断命中的是否是空值
//        if(shopJson != null){
//            return null;
//        }
//        //4.不存在，更具id查询数据库
//        Shop shop = getById(id);
//        //5.数据库不存在，返回错误
//        if(shop == null){
//            stringRedisTemplate.opsForValue().set(key,"",RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
//            return null;
//        }
//        //6.存在，先写入Redis目录中
//        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
//        //7.返回
//        return shop;
//    }

//    private boolean tryLock(String key){
//        log.debug("ShopServiceImpl:tryLock:start");
//        boolean b = stringRedisTemplate.opsForValue().setIfAbsent(key,"1",10,TimeUnit.SECONDS);
//        log.debug("ShopServiceImpl:tryLock:done");
//        return BooleanUtil.isTrue(b);
//    }
//
//    private void unLock(String key){
//        log.debug("ShopServiceImpl:unLock:start");
//        stringRedisTemplate.delete(key);
//        log.debug("ShopServiceImpl:unLock:done");
//    }
}
