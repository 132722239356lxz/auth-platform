package com.liang.xz.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * <p>302 Location 头重写过滤器</p>
 *
 * <p>问题背景:</p>
 * <p>Gateway 使用 StripPrefix=1 剥离 /auth-server 前缀后转发给 auth-server，
 * 但 auth-server 的 302 重定向（如 /login → /oauth2/authorize → redirect_uri）生成的
 * Location 头是 auth-server 内部路径（如 /login, /oauth2/authorize），不包含 Gateway 前缀。
 * 浏览器收到后会用当前 origin 拼接，导致无法正确路由回 Gateway。</p>
 *
 * <p>本过滤器对 auth-server 返回的 302 响应，自动给 Location 头加上 /auth-server 前缀，
 * 确保浏览器能通过 Gateway 正确访问后续端点。</p>
 *
 * <p>例外: 如果 Location 已经是完整 URL（如 redirect_uri=http://localhost:5173/login/callback?code=xxx）
 * 或者是外部地址，则不处理。</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Slf4j
@Component
public class RewriteLocationFilter implements GlobalFilter, Ordered {

    /** 需要重写 Location 头的路由前缀 */
    private static final String AUTH_SERVER_PREFIX = "/auth-server";

    /** auth-server 内部路径白名单：这些路径出现在 Location 头时，需要加上 /auth-server 前缀 */
    private static final java.util.Set<String> REWRITE_PATHS = java.util.Set.of(
            "/login",
            "/oauth2/authorize",
            "/login-success",
            "/register",
            "/logout",
            "/connect/register"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpResponse originalResponse = exchange.getResponse();

        // 使用装饰器拦截响应，在写入前修改 Location 头
        ServerHttpResponseDecorator decoratedResponse = new ServerHttpResponseDecorator(originalResponse) {
            @Override
            public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
                HttpStatus statusCode = HttpStatus.resolve(getStatusCode() != null
                        ? getStatusCode().value() : 200);

                if (statusCode != null && statusCode.is3xxRedirection()) {
                    HttpHeaders headers = getHeaders();
                    String location = headers.getFirst(HttpHeaders.LOCATION);
                    if (location != null) {
                        String rewritten = rewriteLocation(location);
                        if (!rewritten.equals(location)) {
                            headers.set(HttpHeaders.LOCATION, rewritten);
                            log.debug("[RewriteLocation] {} → {}", location, rewritten);
                        }
                    }
                }

                return super.writeWith(body);
            }

            @Override
            public Mono<Void> writeAndFlushWith(
                    Publisher<? extends Publisher<? extends DataBuffer>> body) {
                return writeWith(Flux.from(body).flatMapSequential(p -> p));
            }
        };

        return chain.filter(exchange.mutate().response(decoratedResponse).build());
    }

    /**
     * 重写 Location 头
     */
    private String rewriteLocation(String location) {
        // 如果是完整 URL（http:// 或 https://），不处理
        if (location.startsWith("http://") || location.startsWith("https://")) {
            return location;
        }

        // 如果已经有 /auth-server 前缀，不重复添加
        if (location.startsWith(AUTH_SERVER_PREFIX)) {
            return location;
        }

        // 对于 auth-server 的内部路径，加上 /auth-server 前缀
        for (String path : REWRITE_PATHS) {
            if (location.equals(path) || location.startsWith(path + "?") || location.startsWith(path + "#")) {
                return AUTH_SERVER_PREFIX + location;
            }
        }

        return location;
    }

    @Override
    public int getOrder() {
        // 必须在 NettyWriteResponseFilter（-1）之前执行，否则响应体已写入，装饰器不会生效
        // -10 确保在所有其他全局过滤器之后、Netty 写入响应之前执行
        return -10;
    }
}
