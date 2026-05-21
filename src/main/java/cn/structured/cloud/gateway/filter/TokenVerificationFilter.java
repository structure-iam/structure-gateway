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
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import org.springframework.util.AntPathMatcher;

import java.util.List;

/**
 * Token验证过滤器
 * <p>
 * 职责：验证请求头中的JWT Token是否存在且格式正确
 * 注意：仅验证Token是否存在和格式是否为JWT（3段式），不验证Token的真实性和有效性
 * <p>
 * 执行顺序：Ordered.HIGHEST_PRECEDENCE + 10
 */
@Component
public class TokenVerificationFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(TokenVerificationFilter.class);

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Autowired
    private StructureGatewayProperties gatewayProperties;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!gatewayProperties.getTokenCheck().isEnabled()) {
            return chain.filter(exchange);
        }

        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        if (isExcludedPath(path)) {
            return chain.filter(exchange);
        }

        String authHeader = request.getHeaders().getFirst(GatewayConstants.TOKEN_HEADER);

        if (StrUtil.isBlank(authHeader)) {
            log.warn("Token header is missing for request: {}", path);
            return unauthorizedResponse(exchange, "Authorization header is required");
        }

        if (!authHeader.startsWith(GatewayConstants.TOKEN_BEARER_PREFIX)) {
            log.warn("Invalid token format for request: {}", path);
            return unauthorizedResponse(exchange, "Authorization header must start with 'Bearer '");
        }

        String token = authHeader.substring(GatewayConstants.TOKEN_PREFIX.length()).trim();
        if (StrUtil.isBlank(token)) {
            log.warn("Token is empty for request: {}", path);
            return unauthorizedResponse(exchange, "Token is empty");
        }

        int minTokenLength = gatewayProperties.getTokenCheck().getMinTokenLength();
        if (token.length() < minTokenLength) {
            log.warn("Token is too short for request: {}", path);
            return unauthorizedResponse(exchange, "Invalid token");
        }

        if (!isValidJwtFormat(token)) {
            log.warn("Invalid JWT format for request: {}", path);
            return unauthorizedResponse(exchange, "Invalid JWT format");
        }

        return chain.filter(exchange);
    }

    /**
     * 验证JWT格式是否正确（3段式）
     */
    private boolean isValidJwtFormat(String token) {
        String[] parts = token.split("\\.");
        return parts.length == 3;
    }

    /**
     * 判断路径是否在排除列表中
     */
    private boolean isExcludedPath(String path) {
        List<String> excludedPaths = gatewayProperties.getExcludedPaths();
        if (excludedPaths != null) {
            for (String excludedPath : excludedPaths) {
                if (pathMatcher.match(excludedPath, path)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 返回未授权响应
     */
    private Mono<Void> unauthorizedResponse(ServerWebExchange exchange, String message) {
        return GatewayResponseUtil.unauthorized(exchange, message);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
