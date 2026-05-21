package cn.structured.cloud.gateway.config;

import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 错误处理器配置
 * <p>
 * 职责：启用错误处理配置
 */
@Configuration
@EnableConfigurationProperties(ServerProperties.class)
public class ErrorHandlerConfig {
    // ErrorWebExceptionHandler 会在 GlobalExceptionHandler 上通过 @Component 注解自动注册
}
