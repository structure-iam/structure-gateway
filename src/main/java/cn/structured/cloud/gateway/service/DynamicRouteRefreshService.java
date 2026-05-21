package cn.structured.cloud.gateway.service;

import cn.structured.cloud.gateway.config.DynamicRouteProperties;
import com.alibaba.fastjson.JSON;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.cloud.gateway.filter.FilterDefinition;
import org.springframework.cloud.gateway.handler.predicate.PredicateDefinition;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionWriter;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;

/**
 * 动态路由刷新服务
 * <p>
 * 职责：
 * 1. 监听 Nacos 配置变化
 * 2. 将配置属性转换为 Gateway RouteDefinition
 * 3. 更新路由定义并发布刷新事件
 * <p>
 * 使用 @RefreshScope 注解实现配置热更新
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DynamicRouteRefreshService {

    private final RouteDefinitionWriter routeDefinitionWriter;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final DynamicRouteProperties dynamicRouteProperties;

    /**
     * 初始化路由
     */
    @PostConstruct
    public void initRoutes() {
        log.info("Initializing dynamic routes from Nacos configuration");
        refreshRoutes();
    }

    /**
     * 刷新路由
     * 当 Nacos 配置变更时，@RefreshScope 会自动刷新 dynamicRouteProperties
     * 调用此方法即可更新网关路由
     */
    public void refreshRoutes() {
        try {
            List<DynamicRouteProperties.RouteDefinition> configRoutes = dynamicRouteProperties.getRoutes();
            
            if (configRoutes == null || configRoutes.isEmpty()) {
                log.warn("No routes configured in Nacos, using default routes");
                return;
            }

            log.info("Refreshing {} routes from Nacos configuration", configRoutes.size());
            
            // 先清除所有现有路由
            clearAllRoutes();
            
            // 添加新路由
            List<RouteDefinition> routeDefinitions = convertToRouteDefinitions(configRoutes);
            for (RouteDefinition routeDefinition : routeDefinitions) {
                addRoute(routeDefinition);
            }
            
            // 发布路由刷新事件
            publishRefreshEvent();
            
            log.info("Routes refreshed successfully");
        } catch (Exception e) {
            log.error("Failed to refresh routes", e);
            throw new RuntimeException("Failed to refresh routes", e);
        }
    }

    /**
     * 将配置属性转换为 Gateway RouteDefinition
     */
    private List<RouteDefinition> convertToRouteDefinitions(List<DynamicRouteProperties.RouteDefinition> configRoutes) {
        List<RouteDefinition> routeDefinitions = new ArrayList<>();
        
        for (DynamicRouteProperties.RouteDefinition configRoute : configRoutes) {
            RouteDefinition routeDefinition = new RouteDefinition();
            routeDefinition.setId(configRoute.getId());
            routeDefinition.setUri(java.net.URI.create(configRoute.getUri()));
            
            if (configRoute.getOrder() != null) {
                routeDefinition.setOrder(configRoute.getOrder());
            }
            
            // 设置断言
            if (configRoute.getPredicates() != null) {
                List<PredicateDefinition> predicates = new ArrayList<>();
                for (DynamicRouteProperties.PredicateDefinition configPredicate : configRoute.getPredicates()) {
                    PredicateDefinition predicate =
                        new PredicateDefinition();
                    predicate.setName(configPredicate.getName());
                    
                    if (StringUtils.hasText(configPredicate.getArgs())) {
                        predicate.setArgs(parseArgs(configPredicate.getArgs()));
                    }
                    predicates.add(predicate);
                }
                routeDefinition.setPredicates(predicates);
            }
            
            // 设置过滤器
            if (configRoute.getFilters() != null) {
                List<FilterDefinition> filters = new ArrayList<>();
                for (DynamicRouteProperties.FilterDefinition configFilter : configRoute.getFilters()) {
                    FilterDefinition filter =
                        new FilterDefinition();
                    filter.setName(configFilter.getName());
                    
                    if (StringUtils.hasText(configFilter.getArgs())) {
                        filter.setArgs(parseArgs(configFilter.getArgs()));
                    }
                    filters.add(filter);
                }
                routeDefinition.setFilters(filters);
            }
            
            routeDefinitions.add(routeDefinition);
            log.debug("Converted route: {}", JSON.toJSONString(routeDefinition));
        }
        
        return routeDefinitions;
    }

    /**
     * 解析参数字符串
     * 支持格式: key1=value1,key2=value2
     */
    private java.util.Map<String, String> parseArgs(String argsStr) {
        java.util.Map<String, String> args = new java.util.LinkedHashMap<>();
        if (!StringUtils.hasText(argsStr)) {
            return args;
        }
        
        String[] pairs = argsStr.split(",");
        for (String pair : pairs) {
            String[] keyValue = pair.split("=");
            if (keyValue.length == 2) {
                args.put(keyValue[0].trim(), keyValue[1].trim());
            }
        }
        return args;
    }

    /**
     * 添加路由
     */
    private void addRoute(RouteDefinition routeDefinition) {
        routeDefinitionWriter.save(Mono.just(routeDefinition)).subscribe(
            success -> log.debug("Route added: {}", routeDefinition.getId()),
            error -> log.error("Failed to add route: {}", routeDefinition.getId(), error)
        );
    }

    /**
     * 清除所有路由
     */
    private void clearAllRoutes() {
        try {
            routeDefinitionWriter.delete(Mono.empty()).subscribe(
                success -> log.debug("All routes cleared"),
                error -> log.warn("Failed to clear routes", error)
            );
        } catch (Exception e) {
            log.warn("Error clearing routes", e);
        }
    }

    /**
     * 发布路由刷新事件
     */
    private void publishRefreshEvent() {
        applicationEventPublisher.publishEvent(new RefreshRoutesEvent(this));
        log.debug("RefreshRoutesEvent published");
    }
}