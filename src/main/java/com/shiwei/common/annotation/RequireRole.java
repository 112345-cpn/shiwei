package com.shiwei.common.annotation;

import com.shiwei.common.enums.RoleType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注允许访问该接口的角色，不写默认所有已登录用户可访问。
 * 与 @PublicApi 互斥，同时存在时以 @PublicApi 为准。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireRole {

    RoleType[] value();

    boolean any() default true;
}
