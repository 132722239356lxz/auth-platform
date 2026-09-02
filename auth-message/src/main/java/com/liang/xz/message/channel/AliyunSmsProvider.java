package com.liang.xz.message.channel;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 阿里云短信实现(示例骨架, 实际使用时引入 aliyun-sdk-dysmsapi 并完善)
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "sms.aliyun", name = "access-key-id")
public class AliyunSmsProvider implements SmsProvider {

    @Override
    public boolean send(List<String> phoneNumbers, String templateCode, List<String> templateParams) {
        // TODO: 接入阿里云Dysmsapi SDK
        // 1. DefaultProfile profile = DefaultProfile.getProfile("cn-hangzhou", accessKeyId, accessKeySecret);
        // 2. IAcsClient client = new DefaultAcsClient(profile);
        // 3. SendSmsRequest req = new SendSmsRequest()...
        // 4. client.getAcsResponse(req);
        log.info("[AliyunSMS] 模拟发送: phones={}, template={}, params={}", phoneNumbers, templateCode, templateParams);
        for (String phone : phoneNumbers) {
            log.info("[AliyunSMS] → {}: 验证码/通知已发送", phone);
        }
        return true;
    }

    @Override
    public String getProviderName() {
        return "Aliyun";
    }
}
