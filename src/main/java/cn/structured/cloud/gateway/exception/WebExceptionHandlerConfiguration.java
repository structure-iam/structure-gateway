package cn.structured.cloud.gateway.exception;

import cn.structure.common.entity.ResResultVO;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Web异常处理器配置
 * <p>
 * 职责：注册自定义的全局异常处理器
 */
@Configuration
public class WebExceptionHandlerConfiguration {

    @Bean
    public ErrorWebExceptionHandler customErrorWebExceptionHandler() {
        return new CustomErrorWebExceptionHandler();
    }

    /**
     * 自定义错误Web异常处理器
     */
    public static class CustomErrorWebExceptionHandler implements ErrorWebExceptionHandler {

        @Override
        public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
            ServerHttpResponse response = exchange.getResponse();

            if (response.isCommitted()) {
                return Mono.empty();
            }

            response.getHeaders().add("Content-Type", "application/json;charset=UTF-8");

            ResResultVO<Void> result;
            HttpStatus status;
            String code;
            String message;

            // 根据异常类型确定状态码和错误消息
            if (ex instanceof ResponseStatusException) {
                ResponseStatusException rse = (ResponseStatusException) ex;
                int statusCode = rse.getStatus().value();
                status = HttpStatus.valueOf(statusCode);
                code = getCodeByStatus(status);
                message = rse.getReason() != null ? rse.getReason() : "Request failed";
            } else if (ex.getMessage() != null && ex.getMessage().toLowerCase().contains("not found")) {
                status = HttpStatus.NOT_FOUND;
                code = "NOT_FOUND";
                message = "Service not found";
            } else {
                status = HttpStatus.INTERNAL_SERVER_ERROR;
                code = "INTERNAL_ERROR";
                message = "Internal server error";
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
                byte[] bytes = jsonStr.getBytes();
                DataBuffer buffer = response.bufferFactory().wrap(bytes);
                return response.writeWith(Mono.just(buffer));
            } catch (Exception e) {
                return Mono.empty();
            }
        }

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
                default:
                    return "ERROR";
            }
        }
    }
}
