package com.shiwei.review.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class ReviewVO {

    private Long id;
    private Long shopId;
    private Long userId;
    private String userNickName;
    private String userIcon;

    private Integer scoreTotal;
    private Integer scoreTaste;
    private Integer scoreEnv;
    private Integer scoreService;

    private String content;
    private List<String> images = new ArrayList<>();
    private List<Tag> tags = new ArrayList<>();

    private Integer usefulCount;
    /** 当前登录用户是否点过「有用」 */
    private Boolean useful;

    private Reply reply;
    private LocalDateTime createTime;

    @Data
    public static class Tag {
        private Long id;
        private String name;
        private Integer polarity;
    }

    @Data
    public static class Reply {
        private String content;
        private LocalDateTime createTime;
    }
}
