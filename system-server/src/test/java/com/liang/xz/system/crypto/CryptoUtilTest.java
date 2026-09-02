package com.liang.xz.system.crypto;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Scanner;

/**
 * <p>加密解密工具测试类 —— 快速生成/验证 ENC{...} 格式的密文</p>
 *
 * <p>用途:</p>
 * <ul>
 *   <li>加密明文 → 生成 Nacos 配置加密格式 ENC{AES:base64Cipher}</li>
 *   <li>解密 ENC{...} 密文 → 还原明文</li>
 *   <li>交互式命令行模式，方便快速操作</li>
 * </ul>
 *
 * <p>密钥来源: application.yml 中的 nacos.crypto.secret-key (Base64编码)</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
public class CryptoUtilTest {

    private static final String SECRET_KEY_BASE64 = "eFGXDMr03rYgW7SwlGyBZw==";

    private static final String AES_TRANSFORMATION = "AES/ECB/PKCS5Padding";

    private static final String ENC_PREFIX = "ENC{";
    private static final String ENC_SUFFIX = "}";

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("   Nacos 配置加密/解密工具");
        System.out.printf(encrypt("sk-d07c67d7404063c05eb79353e02b9891256375c9cf2d951d592dc468b018ed12"));
        System.out.println("========================================");
        System.out.println("密钥(Base64): " + SECRET_KEY_BASE64);
        System.out.println();

        if (args.length >= 2) {
            String action = args[0];
            String input = args[1];
            if ("-e".equals(action) || "--encrypt".equals(action)) {
                System.out.println("加密结果:");
                System.out.println(encrypt(input));
                return;
            }
            if ("-d".equals(action) || "--decrypt".equals(action)) {
                System.out.println("解密结果:");
                System.out.println(decrypt(input));
                return;
            }
        }

        try (Scanner scanner = new Scanner(System.in)) {
            while (true) {
                System.out.println("请选择操作:");
                System.out.println("  1. 加密 (生成 ENC{AES:...} 格式密文)");
                System.out.println("  2. 解密");
                System.out.println("  3. 批量加密常用配置");
                System.out.println("  0. 退出");
                System.out.print("输入选项: ");

                String choice = scanner.nextLine().trim();
                switch (choice) {
                    case "1" -> {
                        System.out.print("请输入明文: ");
                        String plain = scanner.nextLine().trim();
                        if (!plain.isEmpty()) {
                            System.out.println("\n加密结果 (可直接放入Nacos配置):");
                            System.out.println(encrypt(plain));
                        }
                    }
                    case "2" -> {
                        System.out.print("请输入密文 (支持 ENC{AES:...} 或纯Base64): ");
                        String cipher = scanner.nextLine().trim();
                        if (!cipher.isEmpty()) {
                            try {
                                System.out.println("\n解密结果:");
                                System.out.println(decrypt(cipher));
                            } catch (Exception e) {
                                System.out.println("解密失败: " + e.getMessage());
                            }
                        }
                    }
                    case "3" -> batchEncrypt(scanner);
                    case "0" -> {
                        System.out.println("再见!");
                        return;
                    }
                    default -> System.out.println("无效选项，请重新输入\n");
                }
                System.out.println();
            }
        }
    }

    public static String encrypt(String plainText) {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(SECRET_KEY_BASE64);
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");
            Cipher cipher = Cipher.getInstance(AES_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec);
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            String base64 = Base64.getEncoder().encodeToString(encrypted);
            return ENC_PREFIX + "AES:" + base64 + ENC_SUFFIX;
        } catch (Exception e) {
            throw new RuntimeException("AES加密失败: " + e.getMessage(), e);
        }
    }

    public static String decrypt(String cipherText) {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(SECRET_KEY_BASE64);
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");
            Cipher cipher = Cipher.getInstance(AES_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, keySpec);

            String base64;
            if (cipherText.startsWith(ENC_PREFIX) && cipherText.endsWith(ENC_SUFFIX)) {
                String content = cipherText.substring(ENC_PREFIX.length(),
                        cipherText.length() - ENC_SUFFIX.length());
                int colonIdx = content.indexOf(':');
                if (colonIdx > 0) {
                    base64 = content.substring(colonIdx + 1);
                } else {
                    base64 = content;
                }
            } else {
                base64 = cipherText;
            }

            byte[] decrypted = cipher.doFinal(Base64.getDecoder().decode(base64));
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("AES解密失败: " + e.getMessage(), e);
        }
    }

    private static void batchEncrypt(Scanner scanner) {
        System.out.println("\n=== 批量加密常用配置 ===");
        System.out.println("将依次输入以下配置项的明文值，生成对应的加密密文:");
        System.out.println("  1. 数据库用户名 (spring.datasource.username)");
        System.out.println("  2. 数据库密码 (spring.datasource.password)");
        System.out.println("  3. OAuth2 RSA私钥 (oauth2.config.rsa-private-key)");
        System.out.println("  4. OAuth2 HS256密钥 (oauth2.config.hs256-secret-key)");
        System.out.println("  5. 加密主密钥 (crypto.config.master-key)");
        System.out.println();

        String[] labels = {
                "数据库用户名", "数据库密码",
                "OAuth2 RSA私钥", "OAuth2 HS256密钥", "加密主密钥"
        };
        String[] keys = {
                "spring.datasource.username", "spring.datasource.password",
                "oauth2.config.rsa-private-key", "oauth2.config.hs256-secret-key",
                "crypto.config.master-key"
        };

        System.out.println("加密结果 (可直接放入 application.yml 或 Nacos 配置):");
        System.out.println("---");
        for (int i = 0; i < labels.length; i++) {
            System.out.print(labels[i] + " (回车跳过): ");
            String input = scanner.nextLine().trim();
            if (!input.isEmpty()) {
                String encrypted = encrypt(input);
                System.out.println(keys[i] + ": " + encrypted);
            }
        }
        System.out.println("---");
        System.out.println("批量加密完成!");
    }
}
