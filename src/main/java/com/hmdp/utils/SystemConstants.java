package com.hmdp.utils;

public class SystemConstants {
    /**
     * @deprecated 图片目录已改为配置项 hmdp.upload.dir（见 UploadController）。
     *             原来写死的 E 盘绝对路径换个机器就跑不起来，别再用这个常量。
     */
    @Deprecated
    public static final String IMAGE_UPLOAD_DIR = "E:\\nginx-1.18.0\\html\\hmdp\\imgs";
    public static final String USER_NICK_NAME_PREFIX = "user_";
    /** 每页条数。店铺列表、笔记列表、评论都用它，改这里会同时影响三处。 */
    public static final int DEFAULT_PAGE_SIZE = 5;
    public static final int MAX_PAGE_SIZE = 10;
}
