package cn.structured.cloud.gateway.util;

import cn.structure.common.entity.ResResultVO;
import com.alibaba.fastjson.JSON;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * 网关响应工具类
 * <p>
 * 职责：统一网关错误响应格式，确保响应体与业务系统一致
 * 使用 {@link ResResultVO} 作为统一响应格式，包含以下字段：
 * - code: 业务状态码
 * - message: 返回消息内容
 * - success: 业务是否成功
 * - data: 返回数据
 * - timestamp: 系统响应时间戳
 * <p>
 * 支持的HTTP状态码：
 * - 400 Bad Request
 * - 401 Unauthorized
 * - 403 Forbidden
 * - 429 Too Many Requests
 */
public class GatewayResponseUtil {

    /**
     * 构建通用错误响应
     *
     * @param exchange ServerWebExchange 对象
     * @param status   HTTP状态码
     * @param code     业务状态码
     * @param message  错误消息
     * @return Mono<Void> 响应流
     */
    public static Mono<Void> buildErrorResponse(ServerWebExchange exchange, HttpStatus status, String code, String message) {
        ServerHttpResponse response = exchange.getResponse();
        if (response.isCommitted()) {
            return Mono.empty();
        }

        response.setStatusCode(status);
        response.getHeaders().add("Content-Type", "application/json;charset=UTF-8");

        ResResultVO<Void> result = ResResultVO.<Void>builder()
                .code(code)
                .message(message)
                .success(false)
                .data(null)
                .timestamp(System.currentTimeMillis())
                .build();

        String body = JSON.toJSONString(result);
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    /**
     * 构建未授权响应 (HTTP 401)
     *
     * @param exchange ServerWebExchange 对象
     * @param message  错误消息
     * @return Mono<Void> 响应流
     */
    public static Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        return buildErrorResponse(exchange, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", message);
    }

    /**
     * 构建错误请求响应 (HTTP 400)
     *
     * @param exchange ServerWebExchange 对象
     * @param message  错误消息
     * @return Mono<Void> 响应流
     */
    public static Mono<Void> badRequest(ServerWebExchange exchange, String message) {
        return buildErrorResponse(exchange, HttpStatus.BAD_REQUEST, "BAD_REQUEST", message);
    }

    /**
     * 构建禁止访问响应 (HTTP 403)
     *
     * @param exchange ServerWebExchange 对象
     * @param message  错误消息
     * @return Mono<Void> 响应流
     */
    public static Mono<Void> forbidden(ServerWebExchange exchange, String message) {
        return buildErrorResponse(exchange, HttpStatus.FORBIDDEN, "FORBIDDEN", message);
    }

    /**
     * 构建请求过多响应 (HTTP 429)
     * 额外添加 X-Rate-Limit-Retry-After-Seconds 响应头
     *
     * @param exchange ServerWebExchange 对象
     * @param message  错误消息
     * @return Mono<Void> 响应流
     */
    public static Mono<Void> tooManyRequests(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        if (response.isCommitted()) {
            return Mono.empty();
        }

        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().add("Content-Type", "application/json;charset=UTF-8");
        response.getHeaders().add("X-Rate-Limit-Retry-After-Seconds", "1");

        ResResultVO<Void> result = ResResultVO.<Void>builder()
                .code("TOO_MANY_REQUESTS")
                .message(message)
                .success(false)
                .data(null)
                .timestamp(System.currentTimeMillis())
                .build();

        String body = JSON.toJSONString(result);
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }
}