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
import com.hmdp.utils.UserHolder;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

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
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {
    @Resource
    private IUserService userService;
    private final StringRedisTemplate stringRedisTemplate;

    public FollowServiceImpl(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public Result follow(Long followUserId, Boolean isFollow) {
        //1.获取登录用户
        Long id = UserHolder.getUser().getId();
        //2.判断当前用户是否已经关注
        if(isFollow){
            Follow follow = new Follow();
            follow.setFollowUserId(followUserId);
            follow.setUserId(id);
            boolean isSucess=save( follow);
            if(isSucess){
                stringRedisTemplate.opsForSet().add("follow:"+id,followUserId.toString());
            }
        }else{
           boolean isSucess= remove(new QueryWrapper<Follow>().eq("user_id",id).eq("follow_user_id",followUserId));
           if (isSucess){
               stringRedisTemplate.opsForSet().remove("follow:"+id,followUserId.toString());
           }
        }
        return Result.ok();
    }

    @Override
    public Result isFollow(Long followUserId) {
        Long id = UserHolder.getUser().getId();
        Integer count = Math.toIntExact(query().eq("user_id", id).eq("follow_user_id", followUserId).count());
        return Result.ok(count>0);
    }

    @Override
    public Result followCommons(Long id) {
        Long userId = UserHolder.getUser().getId();
        String key1="follow:"+userId;
        String key2="follow:"+id;
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(key1, key2);
        if(intersect==null||intersect.isEmpty()){
            return Result.ok(Collections.emptyList());
        }
        List<Long> ids = intersect.stream().map(Long::valueOf).collect(Collectors.toList());
        List<UserDTO> users = userService.listByIds(ids).stream().map(user -> BeanUtil.copyProperties(user, UserDTO.class)).collect(Collectors.toList());
        return Result.ok(users);
    }
}
