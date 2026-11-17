package com.shiwei.review.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("tb_review_tag")
public class ReviewTag {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long typeId;
    private String name;
    /** 1 正面 -1 负面 0 中性 */
    private Integer polarity;
    private Integer sort;
}
