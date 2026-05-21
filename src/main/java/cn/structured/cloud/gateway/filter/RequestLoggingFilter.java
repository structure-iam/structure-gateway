package cn.structured.cloud.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 请求日志过滤器
 * <p>
 * 职责：记录所有经过网关的请求和响应信息，用于排查路由转发问题
 * <p>
 * 执行顺序：最低优先级（Ordered.LOWEST_PRECEDENCE），确保在其他过滤器之后执行
 */
@Slf4j
@Component
public class RequestLoggingFilter implements GlobalFilter, Ordered {

    private static final String START_TIME = "requestStartTime";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 记录请求开始时间
        exchange.getAttributes().put(START_TIME, System.currentTimeMillis());

        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        String method = request.getMethod().name();
        
        // 记录请求进入日志
        log.info("[GATEWAY-IN] {} {} - Headers: {}", method, path, request.getHeaders());
        
        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
            // 记录响应日志
            logResponse(exchange);
        }));
    }

    /**
     * 记录响应信息
     */
    private void logResponse(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest();
        ServerHttpResponse response = exchange.getResponse();
        String path = request.getURI().getPath();
        String method = request.getMethod().name();
        
        // 获取请求耗时
        Long startTime = exchange.getAttribute(START_TIME);
        long duration = 0;
        if (startTime != null) {
            duration = System.currentTimeMillis() - startTime;
        }
        
        // 获取路由信息
        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        String routeId = route != null ? route.getId() : "unknown";
        String targetUri = route != null ? route.getUri().toString() : "unknown";
        
        // 获取响应状态
        int statusCode = response.getStatusCode() != null ? response.getStatusCode().value() : -1;
        
        // 记录响应日志
        log.info("[GATEWAY-OUT] {} {} - Status: {} - Duration: {}ms - Route: {} -> {}", 
                method, path, statusCode, duration, routeId, targetUri);
        
        if (statusCode >= 400) {
            log.warn("[GATEWAY-ERROR] Request failed - {} {} - Status: {} - Route: {} -> {}", 
                    method, path, statusCode, routeId, targetUri);
        }
    }

    @Override
    public int getOrder() {
        // 设置为最低优先级，确保在所有其他过滤器之后执行
        return Ordered.LOWEST_PRECEDENCE;
    }
}

