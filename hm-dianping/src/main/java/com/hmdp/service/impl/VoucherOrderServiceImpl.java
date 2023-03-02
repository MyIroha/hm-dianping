package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.redisson.RedissonLock;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.Collections;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedissonClient redissonClient;
/*
    @Override
    public Result seckillVoucher(Long voucherId) {
        log.debug("VoucherOrderServiceImpl:seckillVoucher:voucherId:start");
        //查询优惠卷
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        //判断秒杀是否开始
        if(voucher.getBeginTime().isAfter(LocalDateTime.now())){
            //秒杀尚未开始
            return Result.fail("秒杀尚未开始");
        }
        //判断秒杀是否已经结束
        if(voucher.getEndTime().isBefore(LocalDateTime.now())){
            //秒杀结束
            return Result.fail("秒杀已结束");
        }
        //判断库存是否充足
        if(voucher.getStock() < 1){
            //库存不足
            return Result.fail("库存不足");
        }

        Long userId = UserHolder.getUser().getId();
        String s = userId.toString().intern();
        //获取锁
        SimpleRedisLock simpleRedisLock = new SimpleRedisLock(stringRedisTemplate , "order:"+userId);
        RLock rLock =  redissonClient.getLock("lock:order:"+userId);

//        boolean isLock = simpleRedisLock.tryLock(1200L);
        boolean isLock = rLock.tryLock();
        log.debug("islock:"+isLock);
        //判断锁是否成功
        if(!isLock){
            //获取锁失败，返回错误
            return Result.fail("已下过单");
        }
        //获取事务有关的代理对象
        try {
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            //返回订单id
            return proxy.createVoucherOrder(voucherId,userId);
        }  finally {
            //释放锁
//           simpleRedisLock.unLock();
            rLock.unlock();
        }

    }
*/

    private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024*1024);

    private static  final  ExecutorService SECKILL_ORDER_EXECUTER = Executors.newSingleThreadExecutor();

    @PostConstruct
    private void init(){
        SECKILL_ORDER_EXECUTER.submit(new VoucherOrderHandle());
    }

    private IVoucherOrderService proxy;

    private class VoucherOrderHandle implements Runnable{
        @Override
        public void run() {
            while (true) {
                //1.获取队列中的订单信息
                VoucherOrder voucherOrder;
                try {
                    voucherOrder = orderTasks.take();
                } catch (InterruptedException e) {
                    log.error("处理订单异常", e);
                    throw new RuntimeException(e);
                }
                //2.创建订单
                handleVoucherOrder(voucherOrder);
            }

        }
    }

    private void handleVoucherOrder(VoucherOrder voucherOrder){
        //获取用户
        Long userId = voucherOrder.getUserId();
        //创建锁对象
        RLock rLock = redissonClient.getLock("lock:order:"+userId);
        //判断是否获取锁成功
        boolean isLock = rLock.tryLock();
        log.debug("islock:"+isLock);
        //判断锁是否成功
        if(!isLock){
            //获取锁失败，返回错误
            log.debug("用户重复下单");
        }
        //获取事务有关的代理对象
        try {
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            //返回订单id
            proxy.createVoucherOrder(voucherOrder,userId);
        }  finally {
            //释放锁
//           simpleRedisLock.unLock();
            rLock.unlock();
        }
    }
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static{
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }
    @Override
    public Result seckillVoucher(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        //1.执行lua脚本
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(),userId.toString()
        );
        //2.判断结果是否为0
        log.debug("-----------------------"+result);
        int r =result.intValue();
        if( r != 0){
            //2.1. 如果不为0 ， 没有购买资格
            return Result.fail(r == 1 ? "库存不足":"不能重复下单");
        }


        //2.2. 为0，有购买资格，把下单信息保存在阻塞队列中
        VoucherOrder voucherOrder = new VoucherOrder();
        //订单id
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        //用户id
        voucherOrder.setUserId(userId);
        //代金卷id
        voucherOrder.setVoucherId(voucherId);


        //获取代理对象
        proxy = (IVoucherOrderService) AopContext.currentProxy();

        //TODO:保存阻塞队列
        orderTasks.add(voucherOrder);



        //3.返回订单id
        return Result.ok(orderId);

    }
    @Override
    @Transactional
    public Result createVoucherOrder(VoucherOrder voucherOrder, Long userId) {
        int count = query().eq("user_id",voucherOrder.getUserId()).eq("voucher_id", voucherOrder.getVoucherId()).count();
        if(count > 0){
            return Result.fail("每个账户只能下一次单");
        }
        //扣减库存
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1 ")
                .eq("voucher_id", voucherOrder.getVoucherId())
                .gt("stock",0)
                .update();
        if(!success){
            //扣减失败
            return Result.fail("库存不足");
        }



        save(voucherOrder);
        return Result.ok();
    }

    /**
     * TODO:下方为单线程利用 synchronized 来进行线程安全的方式 添加订单
     * @param voucherId
     * @return
     */
    /*
    @Override
    public Result seckillVoucher(Long voucherId) {
        //查询优惠卷
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        //判断秒杀是否开始
        if(voucher.getBeginTime().isAfter(LocalDateTime.now())){
            //秒杀尚未开始
            return Result.fail("秒杀尚未开始");
        }
        //判断秒杀是否已经结束
        if(voucher.getEndTime().isBefore(LocalDateTime.now())){
            //秒杀结束
            return Result.fail("秒杀已结束");
        }
        //判断库存是否充足
        if(voucher.getStock() < 1){
            //库存不足
            return Result.fail("库存不足");
        }

        Long userId = UserHolder.getUser().getId();
        String s = userId.toString().intern();
        //先提交事务，在释放锁
        synchronized (userId.toString().intern()) {
            //获取事务有关的代理对象
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            //返回订单id
            return proxy.createVoucherOrder(voucherId,userId);
        }
    }
    //TODO:一人一单 - 事务和互斥锁 （仅适合单服务模式）
    @Override
    @Transactional
    public  Result createVoucherOrder(Long voucherId, Long userId){

        int count = query().eq("user_id",userId).eq("voucher_id",voucherId).count();
        if(count > 0){
            return Result.fail("每个账户只能下一次单");
        }
        //扣减库存
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1 ")
                .eq("voucher_id",voucherId)
                .gt("stock",0)
                .update();
        if(!success){
            //扣减失败
            return Result.fail("库存不足");
        }

        //创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        //订单id
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        //用户id
//        Long userId = UserHolder.getUser().getId();
        voucherOrder.setUserId(userId);
        //代金卷id
        voucherOrder.setVoucherId(voucherId);

        save(voucherOrder);
        return Result.ok(orderId);


    }
     */
}
