package com.liang.xz.common.core.crypto;

/**
 * 加密算法枚举
 */
public enum CryptoAlgorithm {

    AES("AES", 16),
    SM4("SM4", 16),
    DES("DES", 8);

    private final String name;
    private final int minKeyLength;

    CryptoAlgorithm(String name, int minKeyLength) {
        this.name = name;
        this.minKeyLength = minKeyLength;
    }

    public String getName() {
        return name;
    }

    public int getMinKeyLength() {
        return minKeyLength;
    }

    public static CryptoAlgorithm fromName(String name) {
        for (CryptoAlgorithm algo : values()) {
            if (algo.name.equalsIgnoreCase(name)) {
                return algo;
            }
        }
        throw new IllegalArgumentException("不支持的加密算法: " + name);
    }
}
