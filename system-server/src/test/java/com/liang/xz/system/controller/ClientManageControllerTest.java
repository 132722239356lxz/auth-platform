package com.liang.xz.system.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * <p>客户端管理接口测试 —— 覆盖第三方客户端的注册、查询、更新、上下线、密钥管理全流程</p>
 *
 * <p>运行方式:</p>
 * <pre>
 *   # 1. 先启动 system-server
 *   mvn spring-boot:run -pl system-server
 *
 *   # 2. 在 IDE 中右键运行本类
 *   # 或命令行: mvn test -pl system-server -Dtest=ClientManageControllerTest
 * </pre>
 *
 * <p>测试顺序: 按 @Order 顺序执行，模拟真实操作流程</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ClientManageControllerTest {

    @LocalServerPort
    private int port;

    private static final RestTemplate restTemplate = new RestTemplate();
    private static final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    private String baseUrl;
    private static String createdClientId;
    private static String createdClientPkId;

    @BeforeEach
    void setUp() {
        baseUrl = "http://127.0.0.1:" + port;
    }

    // ======================== 1. 查询加密配置(无需认证) ========================

    @Test
    @Order(1)
    @DisplayName("TC-01: 查询加密配置")
    void testGetCryptoInfo() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                baseUrl + "/api/clients/crypto-info", String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        JsonNode node = parseJson(response.getBody());
        assertEquals(200, node.get("code").asInt());
        assertNotNull(node.get("data").get("currentAlgorithm").asText());
        System.out.println("[TC-01 PASS] 加密配置: " + node.get("data").toString());
    }

    // ======================== 2. 注册客户端 ========================

    @Test
    @Order(2)
    @DisplayName("TC-02: 注册新客户端")
    void testCreateClient() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("clientId", "test-app-" + System.currentTimeMillis());
        request.put("clientSecret", "test-secret-12345678");
        request.put("clientName", "JUnit测试客户端");
        request.put("scopes", List.of("openid", "profile", "read"));
        request.put("grantTypes", List.of("authorization_code", "refresh_token", "client_credentials"));
        request.put("redirectUris", List.of(
                Map.of("uri", "http://localhost:8080/login/oauth2/code/test", "platform", "web", "label", "测试回调")));
        request.put("postLogoutRedirectUris", List.of(
                Map.of("uri", "http://localhost:8080/logout", "platform", "web", "label", "登出回调")));
        request.put("authMethods", List.of("client_secret_basic", "client_secret_post"));
        request.put("tokenTtl", 7200);
        request.put("refreshTtl", 86400);
        request.put("enabled", true);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(
                baseUrl + "/api/clients", entity, String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        JsonNode node = parseJson(response.getBody());
        assertEquals(200, node.get("code").asInt());
        assertNotNull(node.get("data"));

        JsonNode data = node.get("data");
        createdClientId = data.get("clientId").asText();
        createdClientPkId = data.get("id").asText();

        assertNotNull(createdClientId);
        assertNotNull(createdClientPkId);
        assertEquals("JUnit测试客户端", data.get("clientName").asText());
        assertEquals(7200L, data.get("tokenTtl").asLong());
        assertEquals(true, data.get("enabled").asBoolean());

        System.out.println("[TC-02 PASS] 客户端注册成功: clientId=" + createdClientId
                + ", pkId=" + createdClientPkId);
    }

    // ======================== 3. 查询所有客户端 ========================

    @Test
    @Order(3)
    @DisplayName("TC-03: 查询所有客户端列表")
    void testListAll() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                baseUrl + "/api/clients", String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        JsonNode node = parseJson(response.getBody());
        assertEquals(200, node.get("code").asInt());
        assertTrue(node.get("data").isArray());
        assertTrue(node.get("data").size() > 0);

        boolean found = false;
        for (JsonNode client : node.get("data")) {
            if (createdClientId.equals(client.get("clientId").asText())) {
                found = true;
                break;
            }
        }
        assertTrue(found, "列表中应包含刚创建的客户端");
        System.out.println("[TC-03 PASS] 客户端总数: " + node.get("data").size());
    }

    // ======================== 4. 按主键ID查询 ========================

    @Test
    @Order(4)
    @DisplayName("TC-04: 按主键ID查询客户端")
    void testGetById() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                baseUrl + "/api/clients/" + createdClientPkId, String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        JsonNode node = parseJson(response.getBody());
        assertEquals(200, node.get("code").asInt());
        assertEquals(createdClientId, node.get("data").get("clientId").asText());
        System.out.println("[TC-04 PASS] 查询到客户端: " + node.get("data").get("clientName").asText());
    }

    @Test
    @Order(5)
    @DisplayName("TC-05: 按不存在的ID查询")
    void testGetByIdNotFound() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                baseUrl + "/api/clients/non-existent-id", String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        JsonNode node = parseJson(response.getBody());
        assertEquals(404, node.get("code").asInt());
        System.out.println("[TC-05 PASS] 返回404: " + node.get("message").asText());
    }

    // ======================== 5. 更新客户端 ========================

    @Test
    @Order(6)
    @DisplayName("TC-06: 更新客户端配置")
    void testUpdateClient() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("clientId", createdClientId);
        request.put("clientSecret", "test-secret-12345678");
        request.put("clientName", "JUnit测试客户端-已更新");
        request.put("scopes", List.of("openid", "profile", "read", "write"));
        request.put("grantTypes", List.of("authorization_code", "refresh_token", "client_credentials"));
        request.put("redirectUris", List.of(
                Map.of("uri", "http://localhost:8080/login/oauth2/code/test", "platform", "web", "label", "测试回调"),
                Map.of("uri", "http://localhost:8080/updated", "platform", "mobile", "label", "移动端回调")));
        request.put("authMethods", List.of("client_secret_basic"));
        request.put("tokenTtl", 14400);
        request.put("refreshTtl", 172800);
        request.put("enabled", true);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl + "/api/clients/" + createdClientPkId,
                HttpMethod.PUT, entity, String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        JsonNode node = parseJson(response.getBody());
        assertEquals(200, node.get("code").asInt());
        assertEquals("JUnit测试客户端-已更新", node.get("data").get("clientName").asText());
        assertEquals(14400L, node.get("data").get("tokenTtl").asLong());
        System.out.println("[TC-06 PASS] 客户端已更新: " + node.get("data").get("clientName").asText());
    }

    // ======================== 6. 密钥管理 ========================

    @Test
    @Order(7)
    @DisplayName("TC-07: 重置客户端密钥")
    void testResetSecret() {
        Map<String, String> body = Map.of("secret", "new-secret-" + System.currentTimeMillis());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, String>> entity = new HttpEntity<>(body, headers);

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl + "/api/clients/" + createdClientPkId + "/secret",
                HttpMethod.PUT, entity, String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        JsonNode node = parseJson(response.getBody());
        assertEquals(200, node.get("code").asInt());
        assertEquals("密钥重置成功", node.get("message").asText());
        System.out.println("[TC-07 PASS] 密钥重置成功");
    }

    @Test
    @Order(8)
    @DisplayName("TC-08: 重置密钥-空密钥校验")
    void testResetSecretWithBlank() {
        Map<String, String> body = Map.of("secret", "   ");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, String>> entity = new HttpEntity<>(body, headers);

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl + "/api/clients/" + createdClientPkId + "/secret",
                HttpMethod.PUT, entity, String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        JsonNode node = parseJson(response.getBody());
        assertEquals(400, node.get("code").asInt());
        System.out.println("[TC-08 PASS] 空密钥校验通过: " + node.get("message").asText());
    }

    // ======================== 7. 上下线控制 ========================

    @Test
    @Order(9)
    @DisplayName("TC-09: 客户端下线(禁用)")
    void testDisable() {
        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl + "/api/clients/" + createdClientPkId + "/disable",
                HttpMethod.PUT, null, String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        JsonNode node = parseJson(response.getBody());
        assertEquals(200, node.get("code").asInt());
        assertEquals(false, node.get("data").get("enabled").asBoolean());
        System.out.println("[TC-09 PASS] 客户端已下线");
    }

    @Test
    @Order(10)
    @DisplayName("TC-10: 客户端上线(启用)")
    void testEnable() {
        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl + "/api/clients/" + createdClientPkId + "/enable",
                HttpMethod.PUT, null, String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        JsonNode node = parseJson(response.getBody());
        assertEquals(200, node.get("code").asInt());
        assertEquals(true, node.get("data").get("enabled").asBoolean());
        System.out.println("[TC-10 PASS] 客户端已上线");
    }

    // ======================== 8. 参数校验 ========================

    @Test
    @Order(11)
    @DisplayName("TC-11: 注册客户端-参数校验失败(clientId为空)")
    void testCreateValidation() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("clientId", "");
        request.put("clientSecret", "test-secret-12345678");
        request.put("clientName", "校验测试");
        request.put("scopes", List.of("openid"));
        request.put("grantTypes", List.of("authorization_code"));
        request.put("authMethods", List.of("client_secret_basic"));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(
                baseUrl + "/api/clients", entity, String.class);

        assertTrue(response.getStatusCode().is4xxClientError()
                || response.getStatusCode() == HttpStatus.OK);
        if (response.getStatusCode() == HttpStatus.OK) {
            JsonNode node = parseJson(response.getBody());
            assertNotEquals(200, node.get("code").asInt());
            System.out.println("[TC-11 PASS] 参数校验失败: code=" + node.get("code").asInt());
        } else {
            System.out.println("[TC-11 PASS] HTTP 4xx: " + response.getStatusCode());
        }
    }

    // ======================== 9. 删除客户端 ========================

    @Test
    @Order(20)
    @DisplayName("TC-20: 删除客户端(注销接入)")
    void testDeleteClient() {
        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl + "/api/clients/" + createdClientPkId,
                HttpMethod.DELETE, null, String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        JsonNode node = parseJson(response.getBody());
        assertEquals(200, node.get("code").asInt());
        System.out.println("[TC-20 PASS] 客户端已注销: " + createdClientId);
    }

    @Test
    @Order(21)
    @DisplayName("TC-21: 删除不存在的客户端")
    void testDeleteNonExistent() {
        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl + "/api/clients/non-existent-id",
                HttpMethod.DELETE, null, String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        JsonNode node = parseJson(response.getBody());
        assertEquals(404, node.get("code").asInt());
        System.out.println("[TC-21 PASS] 删除不存在客户端返回404");
    }

    // ======================== 工具方法 ========================

    private JsonNode parseJson(String body) {
        try {
            return objectMapper.readTree(body);
        } catch (Exception e) {
            fail("JSON解析失败: " + e.getMessage());
            return null;
        }
    }
}
