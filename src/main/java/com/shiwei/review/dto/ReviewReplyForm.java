package com.shiwei.review.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ReviewReplyForm {

    @NotNull(message = "评价 id 不能为空")
    private Long reviewId;

    @NotBlank(message = "回复内容不能为空")
    @Size(max = 1000, message = "回复最长 1000 字")
    private String content;
}
