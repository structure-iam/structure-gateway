package cn.structured.cloud.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;

import java.util.List;

/**
 * 动态路由配置属性类
 * 通过 @RefreshScope 注解实现 Nacos 配置热更新
 * <p>
 * 配置示例：
 * spring:
 *   cloud:
 *     gateway:
 *       routes:
 *         - id: auth-center
 *           uri: lb://auth-center
 *           predicates:
 *             - Path=/auth/**
 *         - id: user-center
 *           uri: lb://user-center
 *           predicates:
 *             - Path=/user/**
 */
@Data
@RefreshScope
@ConfigurationProperties(prefix = "spring.cloud.gateway")
public class DynamicRouteProperties {

    /**
     * 路由配置列表
     */
    private List<RouteDefinition> routes;

    /**
     * 路由定义
     */
    @Data
    public static class RouteDefinition {
        
        /**
         * 路由ID
         */
        private String id;

        /**
         * 目标URI（支持 lb:// 服务发现模式）
         */
        private String uri;

        /**
         * 断言列表
         */
        private List<PredicateDefinition> predicates;

        /**
         * 过滤器列表
         */
        private List<FilterDefinition> filters;

        /**
         * 路由顺序
         */
        private Integer order;
    }

    /**
     * 断言定义
     */
    @Data
    public static class PredicateDefinition {
        
        /**
         * 断言名称（如 Path、Header、Query 等）
         */
        private String name;

        /**
         * 断言参数
         */
        private String args;

        public PredicateDefinition() {
        }

        public PredicateDefinition(String text) {
            int idx = text.indexOf('=');
            if (idx > 0) {
                this.name = text.substring(0, idx);
                this.args = text.substring(idx + 1);
            } else {
                this.name = text;
            }
        }
    }

    /**
     * 过滤器定义
     */
    @Data
    public static class FilterDefinition {
        
        /**
         * 过滤器名称
         */
        private String name;

        /**
         * 过滤器参数
         */
        private String args;

        public FilterDefinition() {
        }

        public FilterDefinition(String text) {
            int idx = text.indexOf('=');
            if (idx > 0) {
                this.name = text.substring(0, idx);
                this.args = text.substring(idx + 1);
            } else {
                this.name = text;
            }
        }
    }
}