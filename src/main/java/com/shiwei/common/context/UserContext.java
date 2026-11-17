package com.shiwei.common.context;

import com.shiwei.common.exception.BizException;
import com.shiwei.common.exception.ErrorCode;

public final class UserContext {

    private static final ThreadLocal<LoginUser> HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(LoginUser user) {
        HOLDER.set(user);
    }

    public static LoginUser get() {
        return HOLDER.get();
    }

    public static Long userId() {
        LoginUser u = HOLDER.get();
        return u == null ? null : u.getId();
    }

    public static Long requireUserId() {
        Long id = userId();
        if (id == null) {
            throw BizException.of(ErrorCode.UNAUTHORIZED);
        }
        return id;
    }

    public static Integer role() {
        LoginUser u = HOLDER.get();
        return u == null ? null : u.getRole();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
