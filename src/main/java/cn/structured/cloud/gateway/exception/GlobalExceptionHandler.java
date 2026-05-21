package cn.structured.cloud.gateway.exception;

import cn.structure.common.entity.ResResultVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.cloud.gateway.support.NotFoundException;
import org.springframework.cloud.gateway.support.ServiceUnavailableException;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.annotation.PostConstruct;

/**
 * 全局异常处理器
 * <p>
 * 职责：统一网关错误响应格式，确保所有异常都返回统一的JSON格式
 * 使用 {@link ResResultVO} 作为统一响应格式
 * <p>
 * 优先级设置为最高，确保优先处理所有异常
 */
@Slf4j
@Component
@Order(-2)
public class GlobalExceptionHandler implements ErrorWebExceptionHandler {

    @PostConstruct
    public void init() {
        log.info("✅ GlobalExceptionHandler initialized and registered");
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        ServerHttpResponse response = exchange.getResponse();

        if (response.isCommitted()) {
            log.debug("Response already committed");
            return Mono.empty();
        }

        log.info("🔧 GlobalExceptionHandler handling exception: {} - {}", ex.getClass().getSimpleName(), ex.getMessage());

        response.getHeaders().add("Content-Type", "application/json;charset=UTF-8");

        ResResultVO<Void> result;
        HttpStatus status;
        String code;
        String message;

        // 路由未找到或服务不可用
        if (isNotFoundOrUnavailable(ex)) {
            status = HttpStatus.NOT_FOUND;
            code = "NOT_FOUND";
            message = extractMessage(ex);
            log.warn("Service not found: {}", ex.getMessage());
        }
        // ResponseStatusException - 这是Spring Boot默认404的来源
        else if (ex instanceof ResponseStatusException) {
            ResponseStatusException rse = (ResponseStatusException) ex;
            int statusCode = rse.getStatus().value();
            status = HttpStatus.valueOf(statusCode);
            code = getCodeByStatus(status);
            // 如果是404但没有reason，尝试从异常类名推断
            message = rse.getReason();
            if (message == null || message.isEmpty()) {
                message = inferMessageFromException(ex, statusCode);
            }
            log.warn("Response status exception: {} - {}", status, message);
        }
        // 检查ResponseStatus注解
        else if (hasResponseStatus(ex)) {
            status = extractStatus(ex);
            code = getCodeByStatus(status);
            message = extractMessage(ex);
            log.warn("Response status: {} - {}", status, message);
        }
        // 参数异常
        else if (ex instanceof IllegalArgumentException) {
            status = HttpStatus.BAD_REQUEST;
            code = "BAD_REQUEST";
            message = ex.getMessage();
            log.warn("Bad request: {}", message);
        }
        // 认证相关错误
        else if (isAuthenticationError(ex)) {
            status = HttpStatus.UNAUTHORIZED;
            code = "INVALID_AUTHENTICATION";
            message = extractAuthenticationMessage(ex);
            log.warn("Authentication error: {}", ex.getMessage());
        }
        // 503 服务不可用
        else if (ex.getMessage() != null && ex.getMessage().contains("503")) {
            status = HttpStatus.SERVICE_UNAVAILABLE;
            code = "SERVICE_UNAVAILABLE";
            message = "Service temporarily unavailable";
            log.error("Service unavailable", ex);
        }
        // 网关超时错误
        else if (isTimeoutError(ex)) {
            status = HttpStatus.GATEWAY_TIMEOUT;
            code = "GATEWAY_TIMEOUT";
            message = "Gateway timeout";
            log.error("Gateway timeout", ex);
        }
        // 检查消息中是否包含404
        else if (is404Error(ex)) {
            status = HttpStatus.NOT_FOUND;
            code = "NOT_FOUND";
            message = extractMessage(ex);
            log.warn("404 error detected: {}", ex.getMessage());
        }
        // 其他异常
        else {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
            code = "INTERNAL_ERROR";
            message = "Internal server error";
            log.error("Unexpected error: {} - {}", ex.getClass().getSimpleName(), ex.getMessage(), ex);
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
            log.info("📤 Writing error response: {}", jsonStr);
            byte[] bytes = jsonStr.getBytes();
            DataBuffer buffer = response.bufferFactory().wrap(bytes);
            return response.writeWith(Mono.just(buffer));
        } catch (Exception e) {
            log.error("Failed to write error response", e);
            return Mono.empty();
        }
    }

    /**
     * 判断是否为404相关的异常
     */
    private boolean isNotFoundOrUnavailable(Throwable ex) {
        if (ex instanceof NotFoundException) {
            return true;
        }
        if (ex instanceof ServiceUnavailableException) {
            return true;
        }
        return false;
    }

    /**
     * 判断是否为404错误
     */
    private boolean is404Error(Throwable ex) {
        if (ex.getMessage() == null) {
            return false;
        }
        String msg = ex.getMessage().toLowerCase();
        return msg.contains("not found") ||
                msg.contains("404") ||
                msg.contains("unable to find route") ||
                msg.contains("route not found");
    }

    /**
     * 检查是否有ResponseStatus注解
     */
    private boolean hasResponseStatus(Throwable ex) {
        if (ex.getClass().isAnnotationPresent(ResponseStatus.class)) {
            return true;
        }
        return false;
    }

    /**
     * 从异常中提取HTTP状态码
     */
    private HttpStatus extractStatus(Throwable ex) {
        ResponseStatus annotation = ex.getClass().getAnnotation(ResponseStatus.class);
        if (annotation != null) {
            return annotation.value();
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    /**
     * 从异常类名和消息推断友好的错误消息
     */
    private String inferMessageFromException(Throwable ex, int statusCode) {
        String className = ex.getClass().getSimpleName().toLowerCase();
        String message = ex.getMessage();

        // 根据异常类名推断消息
        if (className.contains("notfound") || className.contains("not_found")) {
            return "Service not found";
        }
        if (className.contains("404")) {
            return "Resource not found";
        }
        if (statusCode == 404) {
            return "Request failed";
        }

        // 如果有消息，清理后返回
        if (message != null && !message.isEmpty()) {
            return message;
        }

        return "Request failed";
    }

    /**
     * 判断是否为认证相关错误
     */
    private boolean isAuthenticationError(Throwable ex) {
        if (ex.getMessage() == null) {
            return false;
        }
        String msg = ex.getMessage().toLowerCase();
        return msg.contains("authentication") ||
                msg.contains("access token") ||
                msg.contains("invalid token") ||
                msg.contains("token") ||
                msg.contains("unauthorized");
    }

    /**
     * 判断是否为超时错误
     */
    private boolean isTimeoutError(Throwable ex) {
        if (ex.getMessage() == null) {
            return false;
        }
        String msg = ex.getMessage().toLowerCase();
        return msg.contains("timeout") ||
                msg.contains("504") ||
                msg.contains("connect timed out") ||
                msg.contains("connection refused");
    }

    /**
     * 提取认证错误消息
     */
    private String extractAuthenticationMessage(Throwable ex) {
        if (ex.getMessage() == null) {
            return "Authentication failed";
        }
        String message = ex.getMessage();
        if (message.contains(";")) {
            message = message.substring(0, message.indexOf(";"));
        }
        return message;
    }

    /**
     * 提取异常消息
     */
    private String extractMessage(Throwable ex) {
        if (ex.getMessage() == null) {
            return "Service not available";
        }
        String message = ex.getMessage();
        if (message.contains(";")) {
            message = message.substring(0, message.indexOf(";"));
        }
        if (message.contains("Unable to find Route")) {
            return "Route not found";
        }
        if (message.contains("status 404")) {
            return "Service not found";
        }
        return message;
    }

    /**
     * 根据HTTP状态码获取业务错误码
     */
    private String getCodeByStatus(HttpStatus status) {
        switch (status) {
            case NOT_FOUND:
                return "NOT_FOUND";
            case BAD_REQUEST:
                return "BAD_REQUEST";
            case UNAUTHORIZED:
                return "UNAUTHORIZED";
            case FORBIDDEN:
                return "FORBIDDEN";
            case TOO_MANY_REQUESTS:
                return "TOO_MANY_REQUESTS";
            case GATEWAY_TIMEOUT:
                return "GATEWAY_TIMEOUT";
            case SERVICE_UNAVAILABLE:
                return "SERVICE_UNAVAILABLE";
            default:
                return "ERROR";
        }
    }
}
