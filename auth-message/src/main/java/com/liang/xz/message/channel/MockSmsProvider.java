package com.liang.xz.message.channel;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Mock 短信实现 —— 当没有配置真实短信厂商时使用(开发/测试环境)
 */
@Slf4j
@Component
@ConditionalOnMissingBean(AliyunSmsProvider.class)
public class MockSmsProvider implements SmsProvider {

    @Override
    public boolean send(List<String> phoneNumbers, String templateCode, List<String> templateParams) {
        log.info("[MockSMS] ====== 模拟短信发送 ======");
        for (String phone : phoneNumbers) {
            log.info("[MockSMS] To: {} | Template: {} | Params: {}", phone, templateCode, templateParams);
        }
        log.info("[MockSMS] ====== 发送完成({}条) ======", phoneNumbers.size());
        return true;
    }

    @Override
    public String getProviderName() {
        return "Mock";
    }
}
