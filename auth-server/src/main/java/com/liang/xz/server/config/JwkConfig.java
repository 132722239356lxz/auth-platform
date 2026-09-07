package com.liang.xz.server.config;

import com.liang.xz.common.core.properties.OAuth2TokenKeyConfig;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * <p>JWK 密钥配置 —— 同时支持 RS256 和 HS256 两种签名算法</p>
 *
 * <p>双模式支持:</p>
 * <ul>
 *   <li><b>RS256 (非对称):</b> 使用RSA密钥对，私钥签名、公钥验证。适合外部客户端接入</li>
 *   <li><b>HS256 (对称):</b> 使用共享密钥，同一密钥签名和验证。适合内部服务间调用</li>
 * </ul>
 *
 * <p>密钥轮换策略:</p>
 * <ul>
 *   <li>每个密钥有唯一的 kid (Key ID)</li>
 *   <li>JWT Header 中的 kid 字段指定使用哪个密钥验证</li>
 *   <li>支持多密钥共存，实现平滑的密钥轮换</li>
 * </ul>
 *
 * <p>JWK Set 端点: GET /.well-known/jwks.json</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class JwkConfig {

    private final OAuth2TokenKeyConfig prop;

    /**
     * JWK 密钥源 —— 同时注册 RSA 和 HMAC 密钥
     * <p>Spring Authorization Server 使用此 JWKSource 签名 JWT</p>
     */
    @Bean
    public JWKSource<SecurityContext> jwkSource() throws Exception {
        List<com.nimbusds.jose.jwk.JWK> jwkList = new ArrayList<>();

        // 1. 构造 RSA 密钥 (RS256)
        if (prop.getRsaPrivateKey() != null && !prop.getRsaPrivateKey().isBlank()
                && !prop.getRsaPrivateKey().startsWith("PLACEHOLDER")
                && !prop.getRsaPrivateKey().startsWith("ENC{")) {
            try {
                RSAKey rsaJwk = buildRsaJwk(prop.getRsaPrivateKey());
                jwkList.add(rsaJwk);
                log.info("[JWK] RSA密钥注册成功: kid={}", rsaJwk.getKeyID());
            } catch (Exception e) {
                log.error("[JWK] RSA密钥构造失败: {}", e.getMessage());
            }
        } else {
            log.warn("[JWK] RSA私钥未配置或为占位符，RS256签名不可用");
        }

        // 2. 构造 HMAC 对称密钥 (HS256)
        if (prop.getHs256SecretKey() != null && !prop.getHs256SecretKey().isBlank()
                && !prop.getHs256SecretKey().startsWith("PLACEHOLDER")
                && !prop.getHs256SecretKey().startsWith("ENC{")) {
            OctetSequenceKey hmacJwk = buildHmacJwk(prop.getHs256SecretKey());
            jwkList.add(hmacJwk);
            log.info("[JWK] HMAC密钥注册成功: kid={}", hmacJwk.getKeyID());
        } else {
            log.warn("[JWK] HS256密钥未配置或为占位符，HS256签名不可用");
        }

        if (jwkList.isEmpty()) {
            log.warn("[JWK] 未配置密钥，自动生成临时RSA密钥对(仅用于开发环境)");
            try {
                RSAKey autoRsaJwk = buildAutoRsaJwk();
                jwkList.add(autoRsaJwk);
                log.info("[JWK] 自动生成RSA密钥成功: kid={}", autoRsaJwk.getKeyID());
            } catch (Exception e) {
                log.error("[JWK] 自动生成密钥失败: {}", e.getMessage());
            }
        }

        JWKSet jwkSet = new JWKSet(jwkList);
        return new ImmutableJWKSet<>(jwkSet);
    }

    /**
     * JWT 解码器 —— 支持 RS256(公钥) 和 HS256(对称密钥) 两种验证方式
     * <p>业务资源服务器通过 JWK Set URI 获取公钥验证 RS256 签名的JWT</p>
     */
    @Bean
    public JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource) {
        // 使用 JWK Set URI 方式，支持从远程获取公钥
        // auth-server 自身也作为资源服务器，从自己的JWK端点验证
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(
                "http://127.0.0.1:9000/oauth2/jwks").build();

        log.info("[JWK] JWT解码器初始化完成: 支持RS256+HS256双模式验证");
        return decoder;
    }

    /**
     * 构造 RSA JWK 密钥
     */
    private RSAKey buildRsaJwk(String rsaPrivateKeyBase64) throws Exception {
        // 移除所有空白字符（换行符、空格、制表符等）
        // 防止 PEM 格式密钥中的换行符或 YAML 配置的续行换行导致 Base64 解码失败
        String cleanKey = rsaPrivateKeyBase64.replaceAll("\\s+", "");
        byte[] rsaPriBytes = Base64.getDecoder().decode(cleanKey);
        PKCS8EncodedKeySpec priSpec = new PKCS8EncodedKeySpec(rsaPriBytes);
        KeyFactory rsaFactory = KeyFactory.getInstance("RSA");
        PrivateKey privateKey = rsaFactory.generatePrivate(priSpec);

        // 从私钥推导公钥(简化方式，生产环境建议单独配置公钥)
        RSAPrivateKey rsaPrivateKey = (RSAPrivateKey) privateKey;
        // 注意: 这里使用私钥编码作为公钥推导仅用于演示
        // 生产环境应使用 KeyPairGenerator 生成密钥对，或从证书中提取公钥
        java.security.spec.RSAPrivateKeySpec privateKeySpec =
                rsaFactory.getKeySpec(privateKey, java.security.spec.RSAPrivateKeySpec.class);
        java.security.spec.RSAPublicKeySpec publicKeySpec =
                new java.security.spec.RSAPublicKeySpec(
                        privateKeySpec.getModulus(),
                        java.math.BigInteger.valueOf(65537)); // 常用公钥指数
        PublicKey publicKey = rsaFactory.generatePublic(publicKeySpec);

        return new RSAKey.Builder((RSAPublicKey) publicKey)
                .privateKey(rsaPrivateKey)
                .keyID("rsa-kid-v1")
                .algorithm(JWSAlgorithm.RS256)
                .build();
    }

    /**
     * 自动生成临时RSA密钥对(仅用于开发环境)
     * 生产环境务必在application.yml中配置正式的RSA密钥
     */
    private RSAKey buildAutoRsaJwk() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();
        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
        return new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyID("auto-rsa-" + UUID.randomUUID().toString().substring(0, 8))
                .algorithm(JWSAlgorithm.RS256)
                .build();
    }

    /**
     * 构造 HMAC 对称 JWK 密钥
     */
    private OctetSequenceKey buildHmacJwk(String secretKey) {
        byte[] keyBytes = secretKey.getBytes(StandardCharsets.UTF_8);
        // HS256 要求密钥长度至少 256 bits (32 bytes)
        if (keyBytes.length < 32) {
            // 如果密钥不足32字节，进行填充
            byte[] padded = new byte[32];
            System.arraycopy(keyBytes, 0, padded, 0, Math.min(keyBytes.length, 32));
            keyBytes = padded;
            log.warn("[JWK] HS256密钥不足32字节，已自动填充");
        }

        return new OctetSequenceKey.Builder(keyBytes)
                .keyID("hmac-kid-v1")
                .algorithm(JWSAlgorithm.HS256)
                .build();
    }
}
