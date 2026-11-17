package com.shiwei.review.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class ReviewSubmitForm {

    @NotNull(message = "订单 id 不能为空")
    private Long orderId;

    @NotNull(message = "请打分")
    @Min(value = 1, message = "评分最低 1 星")
    @Max(value = 5, message = "评分最高 5 星")
    private Integer scoreTotal;

    @Min(1)
    @Max(5)
    private Integer scoreTaste;

    @Min(1)
    @Max(5)
    private Integer scoreEnv;

    @Min(1)
    @Max(5)
    private Integer scoreService;

    @Size(max = 2000, message = "评价内容最长 2000 字")
    private String content;

    @Size(max = 9, message = "最多传 9 张图")
    private List<String> images;

    @Size(max = 6, message = "最多选 6 个标签")
    private List<Long> tagIds;

    private Boolean anonymous;
}
