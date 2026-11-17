package com.shiwei.review.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("tb_review_reply")
public class ReviewReply {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long reviewId;
    private Long merchantId;
    private Long staffId;
    private String content;

    private LocalDateTime createTime;

    @JsonIgnore
    @TableLogic
    private Integer deleted;
}
