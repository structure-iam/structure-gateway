
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

import java.util.List;

/**
 * 设备识别过滤器
 * <p>
 * 职责：验证和处理设备标识相关的请求头
 * <p>
 * 功能：
 * - 验证 X-Device-Id 请求头（可选或必填）
 * - 记录设备ID到日志中（用于追踪和审计）
 * - 将设备ID添加到请求属性中，供后续过滤器使用
 * <p>
 * 设备ID用途：
 * - 浏览器端：设备指纹
 * - 移动端：设备UUID或AppId
 * - 服务端：clientId或appId
 * <p>
 * 执行顺序：Ordered.HIGHEST_PRECEDENCE + 3
 */
@Component
public class DeviceIdentificationFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(DeviceIdentificationFilter.class);

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Autowired
    private StructureGatewayProperties gatewayProperties;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!gatewayProperties.getDeviceIdentification().isEnabled()) {
            return chain.filter(exchange);
        }

        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        if (isSecurityExcludedPath(path)) {
            return chain.filter(exchange);
        }

        String deviceId = request.getHeaders().getFirst(GatewayConstants.DEVICE_ID_HEADER);
        StructureGatewayProperties.DeviceIdentification deviceConfig = gatewayProperties.getDeviceIdentification();

        if (deviceConfig.isRequireDeviceId() && StrUtil.isBlank(deviceId)) {
            log.warn("Device ID is missing for request: {}", path);
            return GatewayResponseUtil.badRequest(exchange, "X-Device-Id is required");
        }

        if (!StrUtil.isBlank(deviceId)) {
            if (deviceId.length() > deviceConfig.getMaxDeviceIdLength()) {
                log.warn("Device ID is too long: {} for request: {}", deviceId.length(), path);
                return GatewayResponseUtil.badRequest(exchange, 
                    "X-Device-Id must not exceed " + deviceConfig.getMaxDeviceIdLength() + " characters");
            }

            if (deviceConfig.isLogDeviceId()) {
                log.info("[DEVICE] Request from device: {} for path: {}", deviceId, path);
            }

            ServerHttpRequest modifiedRequest = request.mutate()
                    .header(GatewayConstants.DEVICE_ID_HEADER, deviceId)
                    .build();

            exchange = exchange.mutate()
                    .request(modifiedRequest)
                    .build();
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
        return Ordered.HIGHEST_PRECEDENCE + 3;
    }
}
