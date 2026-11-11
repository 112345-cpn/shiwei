package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.conditions.update.UpdateChainWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import io.netty.util.internal.StringUtil;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {
    @Resource
    private IUserService userService;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private FollowServiceImpl followServiceImpl;
    @Resource
    private IFollowService followService;

    @Override
    public Result queryBlogById(Long id) {
        //1,查询博客数据
        Blog blog = getById(id);
        if (blog == null) {
            return Result.fail("数据不存在");
        }
        //2.查询相关用户
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
        //查询是否被点赞
        this.isBlogliked(blog);
        return Result.ok(blog);
    }

    public void isBlogliked(Blog blog) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return;
        }
        Long userId = UserHolder.getUser().getId();
        Long id = blog.getId();
        //2.判断当前用户是否已经点赞
        String key = "blog:liked:" + id;
        java.lang.Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        blog.setIsLike(score != null);
    }


    @Override
    public Result likeBlog(Long id) {
//1.获取登录用户
        Long userId = UserHolder.getUser().getId();
        //2.判断当前用户是否已经点赞
        String key = "blog:liked:" + id;
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        if (score == null) {
            //3.如果未点赞，可以点赞，并装到redis里
            boolean isSucess = update().setSql("liked=liked + 1").eq("id", id).update();
            {
                if (isSucess) {
                    stringRedisTemplate.opsForZSet().add(key, userId.toString(), System.currentTimeMillis());
                }
            }
        } else {
            //4.如果已经点赞，则从redis移除
            boolean isSucess = update().setSql("liked=liked - 1").eq("id", id).update();
            {
                if (isSucess) {
                    stringRedisTemplate.opsForSet().remove(key, userId.toString());
                }
            }
        }
        return Result.ok();
    }

    @Override
    public Result queryBlogLikes(Long id) {
        //1.查询top5
        String key = "blog:liked:" + id;
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        if (top5 == null || top5.isEmpty()) {
            return Result.ok();
        }
        List<Long> ids = top5.stream().map(Long::valueOf).collect(Collectors.toList());
        //3.查出用户
        String idstr = StrUtil.join(", ", ids);
        List<UserDTO> userDTOs = userService.query().in("id", ids).last("order by field(id," + idstr + ")").list()
                .stream().map(user -> BeanUtil.copyProperties(user, UserDTO.class)).collect(Collectors.toList());
//4.返回dto
        return Result.ok(userDTOs);
    }

    @Override
    public Result saveBlog(Blog blog) {
        // 1. 获取当前登录用户的 ID（即发布笔记的博主 ID）
// [提示：从 UserHolder 中获取]
        UserDTO user = UserHolder.getUser();
// 2. 保存笔记到数据库表 tb_blog 中
        blog.setUserId(user.getId());
        boolean isSucess = this.save(blog);
        if (!isSucess) {
            return Result.fail("笔记发布失败");
        }
// 3. 查询当前博主的所有粉丝列表
        List<Follow> followUserId = followService.query().eq("follow_user_id", user.getId()).list();
// [提示：去 tb_follow 表里查，条件是 follow_user_id = 当前博主ID]
// [对应你最后那张图：List<Follow> follows = followService.query()...]

// 4. 开启循环，遍历所有的粉丝信息
// [提示：循环每一个 Follow 对象]
        for (Follow follow : followUserId) {
            // 4.1 获取每一个粉丝的具体用户 ID
            Long id = follow.getUserId();
            // 4.2 构造 Redis 的 Key（即每个粉丝专属的“收件箱”）
            // [提示：Key 格式通常为 "feed:" + 粉丝ID]
            String key = "feed:" + id;
            // 4.3 将笔记的 ID 推送到该粉丝的 Redis ZSet 集合中
            // [提示：使用 stringRedisTemplate.opsForZSet().add(key, blogId, 时间戳)]
            // [重要：Score 必须是当前系统时间戳，这样才能实现按时间排序的“滚动分页”]
            stringRedisTemplate.opsForZSet().add(key, blog.getId().toString(), System.currentTimeMillis());
        }
// 5. 返回笔记的 ID 给前端
        // 获取登录用户
        return Result.ok(blog.getId());
    }

    @Override
    public Result queryBlogOfFollow(Long max, Integer offset) {
        if(max==null){
            max=System.currentTimeMillis();
        }
        //1.获取当前用户
        Long id = UserHolder.getUser().getId();
        //2.查询收件箱,查询注意空对象
        String key = "feed:" + id;
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet().reverseRangeByScoreWithScores(key, 0, max, offset, 2);
        if (typedTuples == null || typedTuples.isEmpty()) {
            return Result.ok();
        }
        //3.解析数据
        ArrayList<Long> ids = new ArrayList<>(typedTuples.size());
        long minTime = 0;
        int os = 1;//偏移量
        //4.查询出blog
        for (ZSetOperations.TypedTuple<String> tuple : typedTuples) {
            ids.add(Long.valueOf(tuple.getValue()));
            // 2. 拿到当前这条数据的时间戳
            long time = tuple.getScore().longValue();
            if (time == minTime) {
                os++;
            } else {
                minTime = time;
                os = 1;
            }
        }
        String idstr = StrUtil.join(",", ids);
        List<Blog> blogs = query().in("id", ids).last("order by field(id,"+idstr+")").list();
        for (Blog blog : blogs) {
            //1.查询相关用户
            Long userId = blog.getUserId(); // 拿到作者ID
            User user = userService.getById(userId); // 去用户表查
            if (user != null) {
                blog.setName(user.getNickName());
                blog.setIcon(user.getIcon());
            }

            // 2. 补全点赞状态（查询当前登录人有没有给这篇笔记点过赞）
            isBlogliked(blog);
            //2/查询是否备点赞
        }
        //5.返回数据
        ScrollResult r = new ScrollResult();
        r.setList( blogs);
        r.setOffset(os);
        r.setMinTime(minTime);
        return Result.ok(r);
    }
}
