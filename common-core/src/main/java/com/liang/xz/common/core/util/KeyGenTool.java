package com.liang.xz.common.core.util;

import java.security.KeyPair;

/**
 * <p>密钥生成工具 —— 一键生成 RS256 和 HS256 随机密钥</p>
 *
 * <p>运行方式:</p>
 * <pre>
 *   mvn exec:java -pl common-core \
 *     -Dexec.mainClass="com.liang.xz.common.core.util.KeyGenTool"
 *
 *   # 或直接在 IDE 中运行 main 方法
 * </pre>
 *
 * <p>输出格式:</p>
 * <ul>
 *   <li>RS256: 私钥 + 公钥 (Base64, PKCS#8 / X.509)</li>
 *   <li>HS256: 对称密钥 (Base64, 256-bit)</li>
 *   <li>可直接复制到 application.yml 配置</li>
 * </ul>
 *
 * @author auth-platform
 * @since 1.0.0
 */
public final class KeyGenTool {

    private KeyGenTool() {
        throw new UnsupportedOperationException("KeyGenTool is a utility class, do not instantiate");
    }

    public static void main(String[] args) {
        String keySize = args.length > 0 ? args[0] : "2048";

        printSeparator("RS256 (RSA-SHA256) 非对称密钥对");
        generateRs256(keySize);

        printSeparator("HS256 (HMAC-SHA256) 对称密钥");
        generateHs256();

        printSeparator("application.yml 配置参考");
        printConfigSnippet();

        printSeparator(null);
        System.out.println("温馨提示: 生产环境请妥善保管私钥，切勿提交到代码仓库!");
    }

    // ==================== RS256 生成 ====================

    private static void generateRs256(String keySizeStr) {
        try {
            int size = Integer.parseInt(keySizeStr);
            if (size < 2048) {
                System.out.println("[WARN] 密钥长度应 >= 2048，已自动调整为 2048");
                size = 2048;
            }

            KeyPair keyPair = RS256Util.generateKeyPair(size);
            String privateKey = RS256Util.exportPrivateKey(keyPair.getPrivate());
            String publicKey = RS256Util.exportPublicKey(keyPair.getPublic());

            System.out.println("算法:       RSA " + size + " bits");
            System.out.println("私钥格式:   PKCS#8 → Base64");
            System.out.println("公钥格式:   X.509  → Base64");
            System.out.println();
            System.out.println("--- 私钥 (妥善保管) ---");
            System.out.println(wrap(privateKey, 64));
            System.out.println();
            System.out.println("--- 公钥 (可公开分发) ---");
            System.out.println(wrap(publicKey, 64));
            System.out.println();

            String signature = RS256Util.sign("test-signature", privateKey);
            boolean verified = RS256Util.verify("test-signature", signature, publicKey);
            System.out.println("自检结果:   " + (verified ? "PASS ✓  签名/验签正常" : "FAIL ✗"));
        } catch (Exception e) {
            System.err.println("[ERROR] RS256 密钥生成失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ==================== HS256 生成 ====================

    private static void generateHs256() {
        try {
            String key = HS256Util.generateKey();

            System.out.println("算法:       HMAC-SHA256 (256 bits)");
            System.out.println("格式:       Base64");
            System.out.println();
            System.out.println("--- HS256 对称密钥 ---");
            System.out.println(wrap(key, 64));
            System.out.println();

            String signature = HS256Util.sign("test-signature", key);
            boolean verified = HS256Util.verify("test-signature", signature, key);
            System.out.println("自检结果:   " + (verified ? "PASS ✓  签名/验签正常" : "FAIL ✗"));
        } catch (Exception e) {
            System.err.println("[ERROR] HS256 密钥生成失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ==================== 配置片段输出 ====================

    private static void printConfigSnippet() {
        System.out.println("# ===== auth-server application.yml ===== ");
        System.out.println("# 生成 RS256 密钥后，将上面的 privateKey 和 publicKey 填入:");
        System.out.println("# oauth2:");
        System.out.println("#   config:");
        System.out.println("#     rsa-private-key: <私钥Base64>");
        System.out.println("#     rsa-public-key:  <公钥Base64>");
        System.out.println("#     hs256-secret:    <HS256密钥Base64>");
        System.out.println();
        System.out.println("# ===== Nacos 配置中心 ===== ");
        System.out.println("# 推荐将密钥存储在 Nacos 中，启动时由 ConfigCryptoUtil 加载");
        System.out.println("# 参考: nacos-config-import/auth-server.yml");
    }

    // ==================== 格式化辅助 ====================

    private static void printSeparator(String title) {
        System.out.println();
        System.out.println("════════════════════════════════════════════════════════════════");
        if (title != null) {
            System.out.println("  " + title);
            System.out.println("════════════════════════════════════════════════════════════════");
        }
    }

    /**
     * 按指定宽度换行输出长字符串
     */
    private static String wrap(String str, int width) {
        if (str == null || str.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < str.length(); i += width) {
            if (i > 0) {
                sb.append('\n');
            }
            int end = Math.min(i + width, str.length());
            sb.append(str, i, end);
        }
        return sb.toString();
    }
}
