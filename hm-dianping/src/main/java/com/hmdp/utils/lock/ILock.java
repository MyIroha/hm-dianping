package com.hmdp.utils.lock;

public interface ILock {

    /**
     * 尝试获取锁
     * @param timeoutSec 锁持有的超市事件，过期自动释放
     * @return true 代表获取锁成功,false代表获取锁失败
     */
    boolean tryLock(Long timeoutSec);

    /**
     * 释放锁
     */
    void unLock();
}
