package com.liang.xz.message.channel;

import java.util.List;

/**
 * 短信发送接口 —— 支持多厂商实现(阿里云/腾讯云/华为云)
 */
public interface SmsProvider {

    /**
     * 发送短信
     *
     * @param phoneNumbers 手机号列表
     * @param templateCode 短信模板编码
     * @param templateParams 模板参数
     * @return true=成功, false=失败
     */
    boolean send(List<String> phoneNumbers, String templateCode, List<String> templateParams);

    /**
     * 获取厂商名称
     */
    String getProviderName();
}
