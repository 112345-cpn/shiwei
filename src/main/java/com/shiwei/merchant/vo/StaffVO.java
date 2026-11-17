package com.shiwei.merchant.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StaffVO {

    private Long id;
    private Long userId;
    private String nickName;
    private String phone;
    /** 0 法人 1 运营 2 核销员 */
    private Integer staffRole;
    private Long shopId;
}
