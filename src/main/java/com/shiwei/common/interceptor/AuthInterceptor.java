package com.shiwei.common.interceptor;

import com.shiwei.common.annotation.PublicApi;
import com.shiwei.common.annotation.RequireRole;
import com.shiwei.common.context.LoginUser;
import com.shiwei.common.context.UserContext;
import com.shiwei.common.enums.RoleType;
import com.shiwei.common.exception.BizException;
import com.shiwei.common.exception.ErrorCode;
import com.shiwei.common.security.TokenStore;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private final TokenStore tokenStore;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod hm)) {
            return true;
        }

        LoginUser user = tokenStore.get(request.getHeader("authorization"));

        boolean isPublic = AnnotationUtils.findAnnotation(hm.getMethod(), PublicApi.class) != null
                || AnnotationUtils.findAnnotation(hm.getBeanType(), PublicApi.class) != null;

        if (user == null) {
            if (isPublic) {
                return true;
            }
            throw BizException.of(ErrorCode.UNAUTHORIZED);
        }

        UserContext.set(user);
        checkRole(hm, user);
        return true;
    }

    private void checkRole(HandlerMethod hm, LoginUser user) {
        RequireRole require = AnnotationUtils.findAnnotation(hm.getMethod(), RequireRole.class);
        if (require == null) {
            return;
        }
        int current = user.getRole() == null ? 0 : user.getRole();
        if (current == RoleType.ADMIN.getCode()) {
            return;
        }
        for (RoleType allowed : require.value()) {
            if (allowed.getCode() == current) {
                return;
            }
        }
        throw BizException.of(ErrorCode.FORBIDDEN);
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }
}
