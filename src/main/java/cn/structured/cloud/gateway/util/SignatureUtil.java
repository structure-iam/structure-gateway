
package cn.structured.cloud.gateway.util;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.SecureUtil;
import org.springframework.http.server.reactive.ServerHttpRequest;

public class SignatureUtil {

    private SignatureUtil() {
    }

    public static boolean verifySignature(ServerHttpRequest request, String timestamp, String nonce, String signature, String secretKey) {
        if (StrUtil.isBlank(signature) || StrUtil.isBlank(secretKey)) {
            return false;
        }

        String expectedSignature = generateSignature(request, timestamp, nonce, secretKey);
        return signature.equalsIgnoreCase(expectedSignature);
    }

    public static String generateSignature(ServerHttpRequest request, String timestamp, String nonce, String secretKey) {
        String method = request.getMethodValue().toUpperCase();
        String path = request.getURI().getPath();

        StringBuilder sb = new StringBuilder();
        sb.append(method).append(path).append(timestamp).append(nonce);

        return SecureUtil.hmacSha256(secretKey).digestHex(sb.toString());
    }
}

