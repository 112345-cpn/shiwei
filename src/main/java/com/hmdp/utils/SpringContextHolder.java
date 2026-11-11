package com.hmdp.utils;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

/**
 * Spring 上下文 Holder（静态获取 Bean）
 *
 * 适用于：工具类、静态方法中需要使用 Spring 管理的 Bean
 *
 * @author HM-DianPing
 */
@Component
public class SpringContextHolder implements ApplicationContextAware {

    private static ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        SpringContextHolder.applicationContext = applicationContext;
    }

    public static <T> T getBean(Class<T> beanClass) {
        return applicationContext.getBean(beanClass);
    }

    public static <T> T getBean(String name, Class<T> beanClass) {
        return applicationContext.getBean(name, beanClass);
    }

    public static Object getBean(String name) {
        return applicationContext.getBean(name);
    }
}