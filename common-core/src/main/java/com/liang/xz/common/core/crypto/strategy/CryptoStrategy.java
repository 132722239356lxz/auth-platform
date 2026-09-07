package com.liang.xz.common.core.crypto.strategy;

import com.liang.xz.common.core.crypto.CryptoAlgorithm;

/**
 * 加解密策略接口
 */
public interface CryptoStrategy {

    String encrypt(String plainText, byte[] key);

    String decrypt(String cipherText, byte[] key);

    CryptoAlgorithm supportedAlgorithm();
}
