package com.liang.xz.server.config;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.server.authorization.web.authentication.OAuth2AuthorizationCodeAuthenticationConverter;
import org.springframework.security.oauth2.server.authorization.web.authentication.OAuth2ClientCredentialsAuthenticationConverter;
import org.springframework.security.oauth2.server.authorization.web.authentication.OAuth2DeviceCodeAuthenticationConverter;
import org.springframework.security.oauth2.server.authorization.web.authentication.OAuth2RefreshTokenAuthenticationConverter;
import org.springframework.security.web.authentication.AuthenticationConverter;
import org.springframework.util.StringUtils;

import java.util.*;

/**
 * <p>自定义Token请求转换器 —— 支持扩展的OAuth2授权参数</p>
 *
 * <p>扩展参数:</p>
 * <ul>
 *   <li><b>tenant_id:</b> 租户ID，标识请求所属租户</li>
 *   <li><b>user_type:</b> 用户类型</li>
 *   <li><b>client_type:</b> 客户端类型</li>
 * </ul>
 *
 * <p>这些参数会被 CustomTokenEnhancer 写入JWT的Claims中</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Slf4j
public class CustomTokenRequestConverter implements AuthenticationConverter {

    private final Map<String, AuthenticationConverter> converters;

    public CustomTokenRequestConverter() {
        this.converters = new LinkedHashMap<>();

        // 授权码模式
        this.converters.put(OAuth2ParameterNames.GRANT_TYPE + "=" + AuthorizationGrantType.AUTHORIZATION_CODE.getValue(),
                new OAuth2AuthorizationCodeAuthenticationConverter());

        // 客户端凭证模式
        this.converters.put(OAuth2ParameterNames.GRANT_TYPE + "=" + AuthorizationGrantType.CLIENT_CREDENTIALS.getValue(),
                new OAuth2ClientCredentialsAuthenticationConverter());

        // 刷新Token模式
        this.converters.put(OAuth2ParameterNames.GRANT_TYPE + "=" + AuthorizationGrantType.REFRESH_TOKEN.getValue(),
                new OAuth2RefreshTokenAuthenticationConverter());

        // 设备码模式
        this.converters.put(OAuth2ParameterNames.GRANT_TYPE + "=" + AuthorizationGrantType.DEVICE_CODE.getValue(),
                new OAuth2DeviceCodeAuthenticationConverter());
    }

    @Override
    public Authentication convert(HttpServletRequest request) {
        // 1. 提取自定义参数到request attributes
        extractCustomParams(request);

        // 2. 根据grant_type选择对应的转换器
        String grantType = request.getParameter(OAuth2ParameterNames.GRANT_TYPE);
        if (!StringUtils.hasText(grantType)) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("invalid_request", "缺少 grant_type 参数", null));
        }

        AuthenticationConverter converter = this.converters.get(
                OAuth2ParameterNames.GRANT_TYPE + "=" + grantType);
        if (converter == null) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("unsupported_grant_type",
                            "不支持的授权模式: " + grantType, null));
        }

        Authentication authentication = converter.convert(request);

        // 3. 将自定义参数注入到认证Token的附加参数中
        if (authentication != null) {
            injectCustomParams(authentication, request);
        }

        return authentication;
    }

    /**
     * 从请求中提取自定义参数
     */
    private void extractCustomParams(HttpServletRequest request) {
        // 从请求中提取 tenant_id / user_type / client_type
        extractAndSetAttribute(request, "tenant_id");
        extractAndSetAttribute(request, "user_type");
        extractAndSetAttribute(request, "client_type");
    }

    private void extractAndSetAttribute(HttpServletRequest request, String paramName) {
        String value = request.getParameter(paramName);
        if (StringUtils.hasText(value)) {
            request.setAttribute("custom_" + paramName, value.trim());
            log.debug("[TokenRequest] 提取自定义参数: {}={}", paramName, value.trim());
        }
    }

    /**
     * 将自定义参数注入到认证Token中
     */
    private void injectCustomParams(Authentication authentication, HttpServletRequest request) {
        Map<String, Object> additionalParams = new LinkedHashMap<>();

        // 收集自定义参数
        for (String param : List.of("tenant_id", "user_type", "client_type")) {
            Object value = request.getAttribute("custom_" + param);
            if (value != null) {
                additionalParams.put(param, value);
            }
        }

        if (!additionalParams.isEmpty()) {
            // 将自定义参数存储到SecurityContext中
            // 后续 CustomTokenEnhancer 会从授权记录中读取
            request.setAttribute("oauth2.custom.params", additionalParams);
            log.debug("[TokenRequest] 注入自定义参数: {}", additionalParams.keySet());
        }
    }
}
