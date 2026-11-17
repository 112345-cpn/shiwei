package com.shiwei.review.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("tb_review_useful")
public class ReviewUseful {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long reviewId;
    private Long userId;
    private LocalDateTime createTime;
}
