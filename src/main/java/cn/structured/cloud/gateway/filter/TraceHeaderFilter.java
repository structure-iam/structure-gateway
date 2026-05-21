package cn.structured.cloud.gateway.filter;

import cn.hutool.core.util.StrUtil;
import cn.structured.cloud.gateway.config.StructureGatewayProperties;
import cn.structured.cloud.gateway.constant.GatewayConstants;
import cn.structured.cloud.gateway.util.GatewayResponseUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

/**
 * 链路追踪过滤器
 * <p>
 * 职责：验证和处理链路追踪相关的请求头
 * <p>
 * 验证项（可配置）：
 * - X-Request-Id：请求唯一标识
 * <p>
 * 执行顺序：Ordered.HIGHEST_PRECEDENCE + 50
 */
@Component
public class TraceHeaderFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(TraceHeaderFilter.class);

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Autowired
    private StructureGatewayProperties gatewayProperties;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!gatewayProperties.getTraceConfig().isEnabled()) {
            return chain.filter(exchange);
        }

        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        if (isSecurityExcludedPath(path)) {
            return chain.filter(exchange);
        }

        StructureGatewayProperties.TraceConfig traceConfig = gatewayProperties.getTraceConfig();
        List<String> validationErrors = new ArrayList<>();

        if (traceConfig.isRequireRequestId()) {
            String requestId = request.getHeaders().getFirst(GatewayConstants.REQUEST_ID_HEADER);
            if (StrUtil.isBlank(requestId)) {
                validationErrors.add("X-Request-Id is required");
            }
        }

        if (!validationErrors.isEmpty()) {
            log.warn("Trace header validation failed for request: {}, errors: {}", path, validationErrors);
            return GatewayResponseUtil.badRequest(exchange, String.join("; ", validationErrors));
        }

        return chain.filter(exchange);
    }



    /**
     * 判断路径是否在安全检查排除列表中
     */
    private boolean isSecurityExcludedPath(String path) {
        List<String> securityExcludedPaths = gatewayProperties.getSecurityExcludedPaths();
        if (securityExcludedPaths != null) {
            for (String excludedPath : securityExcludedPaths) {
                if (pathMatcher.match(excludedPath, path)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }
}
