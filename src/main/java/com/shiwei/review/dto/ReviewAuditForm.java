package com.shiwei.review.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ReviewAuditForm {

    @NotNull(message = "评价 id 不能为空")
    private Long reviewId;

    /** true 通过，false 拒绝 */
    @NotNull(message = "请选择审核结果")
    private Boolean pass;

    @Size(max = 255, message = "理由最长 255 字")
    private String reason;
}
