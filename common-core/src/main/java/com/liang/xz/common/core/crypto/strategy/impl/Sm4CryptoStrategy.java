package com.liang.xz.common.core.crypto.strategy.impl;

import com.liang.xz.common.core.crypto.CryptoAlgorithm;
import com.liang.xz.common.core.crypto.CryptoException;
import com.liang.xz.common.core.crypto.strategy.CryptoStrategy;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.security.Security;
import java.util.Base64;

/**
 * SM4 国密加解密实现 (CBC/PKCS7Padding)
 */
@Component
public class Sm4CryptoStrategy implements CryptoStrategy {

    private static final String TRANSFORMATION = "SM4/CBC/PKCS7Padding";
    private static final byte[] IV = {0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08,
            0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F, 0x10};

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    @Override
    public String encrypt(String plainText, byte[] key) {
        try {
            SecretKeySpec keySpec = new SecretKeySpec(key, "SM4");
            Cipher cipher = Cipher.getInstance(TRANSFORMATION, "BC");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new javax.crypto.spec.IvParameterSpec(IV));
            byte[] encrypted = cipher.doFinal(plainText.getBytes("UTF-8"));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new CryptoException("SM4加密失败", e);
        }
    }

    @Override
    public String decrypt(String cipherText, byte[] key) {
        try {
            SecretKeySpec keySpec = new SecretKeySpec(key, "SM4");
            Cipher cipher = Cipher.getInstance(TRANSFORMATION, "BC");
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new javax.crypto.spec.IvParameterSpec(IV));
            byte[] decrypted = cipher.doFinal(Base64.getDecoder().decode(cipherText));
            return new String(decrypted, "UTF-8");
        } catch (Exception e) {
            throw new CryptoException("SM4解密失败", e);
        }
    }

    @Override
    public CryptoAlgorithm supportedAlgorithm() {
        return CryptoAlgorithm.SM4;
    }
}
