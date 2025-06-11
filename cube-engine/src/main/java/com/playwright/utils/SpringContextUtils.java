package com.playwright.utils;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

/**
 * @author 优立方
 * @version JDK 17
 * @date 2025年05月27日 10:55
 *
 * Spring应用上下文工具类，用于在非Spring管理对象中获取Bean实例
 */

@Component
public class SpringContextUtils implements ApplicationContextAware {

    // 静态保存Spring应用上下文，便于全局访问
    private static ApplicationContext context;

    /**
     * Spring容器自动调用此方法注入ApplicationContext
     * @param applicationContext Spring应用上下文对象
     * @throws BeansException 当注入失败时抛出
     */
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        // 将容器传入的上下文引用保存到静态变量
        SpringContextUtils.context = applicationContext;
    }

    /**
     * 根据类型获取Bean实例
     * @param clazz 要获取的Bean类型
     * @param <T> Bean的泛型类型
     * @return 匹配类型的Bean实例
     */
    public static <T> T getBean(Class<T> clazz) {
        return context.getBean(clazz);
    }

    /**
     * 根据Bean名称获取实例
     * @param beanName Spring配置中定义的Bean名称
     * @return 匹配名称的Bean实例
     */
    public static Object getBean(String beanName) {
        return context.getBean(beanName);
    }
}
