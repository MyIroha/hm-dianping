package com.hmdp.utils.lock;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock{
    private StringRedisTemplate stringRedisTemplate;
    private String name;

    public SimpleRedisLock(StringRedisTemplate stringRedisTemplate, String name) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.name = name;
    }
     private static final String KEY_PREFIX = "lock:";

     private static final String ID_PREFIX = UUID.randomUUID().toString() + "-";
     public String lockKey(){
         return KEY_PREFIX + name;
     }

     public String threadId(){
         return ID_PREFIX + Thread.currentThread().getId();
     }
    @Override
    public boolean tryLock(Long timeoutSec) {
         //获取线程标志
        String threadId = threadId();
        System.out.println("-----------------------------------"+threadId());
        //添加成功返回true ，添加失败返回false
        Boolean b = stringRedisTemplate.opsForValue()
                    .setIfAbsent(lockKey(),threadId,timeoutSec, TimeUnit.MINUTES);
         System.out.println(b);
        return Boolean.TRUE.equals(b);//判断包装类是否为空
    }

    @Override
    public void unLock() {
         //获取线程标志
         String threadId = threadId();
         //获取redis中的线程标识
         String id = stringRedisTemplate.opsForValue().get(lockKey());
         //判断是否一致
         if(id.equals(threadId)){
             //一致就释放锁
             stringRedisTemplate.delete(lockKey());
         }


    }
}
