package com.shiwei.review.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("tb_review")
public class Review {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;
    private Long shopId;
    private Long userId;

    private Integer scoreTotal;
    private Integer scoreTaste;
    private Integer scoreEnv;
    private Integer scoreService;

    private String content;
    private String images;
    private String tags;
    private Integer usefulCount;
    private Integer anonymous;

    /** 0 待审 1 通过 2 拒绝 3 隐藏 */
    private Integer status;
    private String auditReason;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    @JsonIgnore
    @TableLogic
    private Integer deleted;
}
