package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {


    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IUserService userService;
    @Override
    public Result follow(Long id, Boolean isFollow) {
        //获取登录的用户Id
        Long userId = UserHolder.getUser().getId();
        //判断到底是关注还是取关
        String key = RedisConstants.FOLLOW_KEY+id;
        if(isFollow){
            //关注，新增数据
            Follow follow = new Follow();
            follow.setUserId(id);
            follow.setFollowUserId(userId);
            boolean isSuccess = save(follow);
            if(isSuccess){
                //关注目标用户id，放入redis的set集合 sadd userId followUserId

                stringRedisTemplate.opsForSet().add(key,userId.toString());
            }
        }else{
            //取关，删除
            boolean isSuccess = remove(new QueryWrapper<Follow>().eq("user_id",userId).eq("follow_user_id",id));

            if(isSuccess){
                stringRedisTemplate.opsForSet().add(key);
            }
        }
        return Result.ok();
    }

    @Override
    public Result isFollow(Long id) {
        Long userId = UserHolder.getUser().getId();
        //查询是否关注 select * from where user_id = ? and follow_user_id = ?
        Integer count = query().eq("user_id", userId).eq("follow_user_id", id).count();
        return Result.ok(count>0);
    }

    @Override
    public Result followCommons(Long id) {
        //获取当前用户id和key
        Long meId = UserHolder.getUser().getId();
        String meKey = RedisConstants.FOLLOW_KEY+meId;
        //获取当前登录用户
        String followUserIdKey = RedisConstants.FOLLOW_KEY+id;
        //获取交集
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(meKey, followUserIdKey);
        if(intersect == null || intersect.isEmpty()){
            return Result.ok();
        }
        //解析id
        List<Long> ids = intersect.stream().map(Long::valueOf).collect(Collectors.toList());
        //查询用户
        List<UserDTO> userDTOs = userService.listByIds(ids).stream().map(user -> BeanUtil.copyProperties(user, UserDTO.class)).collect(Collectors.toList());

        return Result.ok(userDTOs);
    }
}
