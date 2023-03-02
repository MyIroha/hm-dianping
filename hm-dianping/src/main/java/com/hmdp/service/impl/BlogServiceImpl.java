package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
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
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Resource
    private IFollowService followService;

    @Override
    public Result saveBlog(Blog blog) {
        //获取登录用户
        Long userId = UserHolder.getUser().getId();
        blog.setUserId(userId);
        //2.保存探店笔记
        boolean isSuccess = save(blog);
        if(!isSuccess){
            return Result.fail("新增失败");
        }
        //3.查询笔记坐着的所有粉丝
        List<Follow> follows = followService.query().eq("follow_user_id", userId).list();
        //4.推送笔记id给所有粉丝
        for(Follow follow:follows){
            Long userId1 = follow.getUserId();
            String key = RedisConstants.FEED_KEY + userId1;
            stringRedisTemplate.opsForZSet().add(key,blog.getId().toString(),System.currentTimeMillis());

        }
        //返回id
        return Result.ok(blog.getId());
    }

    @Resource
    private IUserService userService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog -> {
            this.queryBlogUser(blog);
            this.isBlockLiked(blog);
        });
        return Result.ok(records);
    }

    @Override
    public Result queryBlockById(Long id) {
        //查询blog
        Blog blog = getById(id);
        if(blog == null){
            return Result.fail("笔记不存在");
        }
        //查询blog有关的用户
        queryBlogUser(blog);
        //查询blog是否被点赞
        isBlockLiked(blog);
        return Result.ok(blog);
    }

    private void isBlockLiked(Blog blog) {
        UserDTO user = UserHolder.getUser();
        if(user == null){
            //用户未登录无需查询
            return ;
        }
        Long id1 = user.getId();

        //判断当前用户是否点赞
        String key = RedisConstants.BLOG_LIKED_KEY + blog.getId();
        Double b = stringRedisTemplate.opsForZSet().score(key, id1.toString());
        blog.setIsLike(b!=null);
    }

    @Override
    public Result likeBlog(Long id) {
        //获取用户
        Long id1 = UserHolder.getUser().getId();
        //判断当前用户是否点赞
        String key = RedisConstants.BLOG_LIKED_KEY + id;
        Double b = stringRedisTemplate.opsForZSet().score(key, id1.toString());
        //如果未点赞，则可以点赞
        if(b==null){
            boolean success = update().setSql("liked = liked + 1").eq("id", id).update();
            if(success){
                //保存用户到redis的SortedSet
                stringRedisTemplate.opsForZSet().add(key,id1.toString(),System.currentTimeMillis());
            }
        }else{
            boolean success = update().setSql("liked = liked - 1").eq("id", id).update();
            if(success){
                stringRedisTemplate.opsForZSet().remove(key,id1.toString());
            }
        }
        //数据库点在+1
        //保存用户到redis集合
        //如果已经点在，取消点赞
        //点赞数减一
        //把用户从redis set集合中移除
        return Result.ok();
    }

    @Override
    public Result queryBlockLiks(Long id) {
        //查看top5的点赞用户 zrange key 0 4
        String key = RedisConstants.BLOG_LIKED_KEY + id;
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        if(top5 == null || top5.isEmpty()){
            return Result.ok(Collections.emptyList());
        }
        //解析初其中的用户id
        List<Long> ids = top5.stream().map(Long::valueOf).collect(Collectors.toList());
        String idStr = StrUtil.join(",",ids);
        //更具用户id查询用户
        List<UserDTO> users = userService
                            .query().in("id",ids).last("Order by field(id,"+idStr+")").list()
                            .stream()
                            .map(user -> BeanUtil.copyProperties(user,UserDTO.class))
                .collect(Collectors.toList());
        //返回
        return Result.ok(users);
    }

    public void queryBlogUser(Blog blog){
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }
}
