package com.liang.xz.server.controller;

import com.liang.xz.server.config.CryptoConfig;
import com.liang.xz.server.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * <p>加密服务控制器 —— 向前端提供 RSA 公钥用于密码加密传输</p>
 *
 * @author auth-platform
 * @since 2.0.0
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "加密服务", description = "提供公钥给前端用于密码加密传输")
public class CryptoController {

    private final CryptoConfig cryptoConfig;

    @GetMapping("/api/crypto/public-key")
    @Operation(summary = "获取RSA公钥", description = "返回 PEM 格式的 RSA 公钥，前端使用此公钥对密码进行 RSA-OAEP 加密后传输")
    public ApiResponse<String> getPublicKey() {
        String pem = cryptoConfig.getPublicKeyPem();
        log.debug("[Crypto] 已向前端下发RSA公钥");
        return ApiResponse.success(pem);
    }
}
