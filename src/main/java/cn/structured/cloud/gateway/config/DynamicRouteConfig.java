package cn.structured.cloud.gateway.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 动态路由配置类
 * <p>
 * 职责：
 * 1. 启用动态路由配置属性
 * 2. 配置默认路由（当Nacos配置未就绪时使用）
 */
@Configuration
@EnableConfigurationProperties(DynamicRouteProperties.class)
@RequiredArgsConstructor
public class DynamicRouteConfig {

    private final DynamicRouteProperties dynamicRouteProperties;
}