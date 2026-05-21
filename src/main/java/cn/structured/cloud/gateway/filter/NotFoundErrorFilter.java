package cn.structured.cloud.gateway.filter;

import cn.structure.common.entity.ResResultVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * 404错误处理过滤器
 * <p>
 * 职责：捕获并统一处理404错误响应格式
 * <p>
 * 这个过滤器在路由之后执行，用于处理目标服务返回的404错误
 */
@Slf4j
@Component
public class NotFoundErrorFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return chain.filter(exchange)
                .onErrorResume(error -> {
                    ServerHttpResponse response = exchange.getResponse();

                    if (response.isCommitted()) {
                        return Mono.error(error);
                    }

                    response.getHeaders().add("Content-Type", "application/json;charset=UTF-8");

                    ResResultVO<Void> result;
                    HttpStatus status;
                    String code;
                    String message;

                    // 检查是否是404错误
                    String errorMsg = error.getMessage();
                    if (errorMsg != null && (errorMsg.contains("404") || errorMsg.toLowerCase().contains("not found"))) {
                        status = HttpStatus.NOT_FOUND;
                        code = "NOT_FOUND";
                        message = "Service not found";
                        log.warn("404 error detected: {}", errorMsg);
                    } else {
                        // 其他错误，传递给下一个错误处理器
                        return Mono.error(error);
                    }

                    result = ResResultVO.<Void>builder()
                            .code(code)
                            .message(message)
                            .success(false)
                            .data(null)
                            .timestamp(System.currentTimeMillis())
                            .build();

                    response.setStatusCode(status);
                    try {
                        String jsonStr = cn.hutool.json.JSONUtil.toJsonStr(result);
                        byte[] bytes = jsonStr.getBytes(StandardCharsets.UTF_8);
                        DataBuffer buffer = response.bufferFactory().wrap(bytes);
                        return response.writeWith(Mono.just(buffer));
                    } catch (Exception e) {
                        log.error("Failed to write error response", e);
                        return Mono.error(error);
                    }
                });
    }

    @Override
    public int getOrder() {
        // 在所有过滤器之后执行
        return Ordered.LOWEST_PRECEDENCE;
    }
}
