package com.liang.xz.system.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * <p>授权审计与Token吊销接口测试 —— 覆盖授权记录查询、Token强制吊销、吊销日志追溯、门户概览统计</p>
 *
 * <p>运行方式:</p>
 * <pre>
 *   # 1. 先启动 system-server
 *   mvn spring-boot:run -pl system-server
 *
 *   # 2. 在 IDE 中右键运行本类
 *   # 或命令行: mvn test -pl system-server -Dtest=AuditControllerTest
 * </pre>
 *
 * <p>前置条件:</p>
 * <ul>
 *   <li>system-server 已启动且有数据库连接</li>
 *   <li>存在已注册的客户端和已授权的用户记录(否则部分测试用例会跳过)</li>
 *   <li>授权记录查询依赖实际的OAuth2授权流程，无数据时返回空列表(仍会通过)</li>
 * </ul>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuditControllerTest {

    @LocalServerPort
    private int port;

    private static final RestTemplate restTemplate = new RestTemplate();
    private static final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    private String baseUrl;

    private static final String TEST_CLIENT_ID = "test-client";
    private static final String TEST_USER_NAME = "admin";

    @BeforeEach
    void setUp() {
        baseUrl = "http://127.0.0.1:" + port;
    }

    // ======================== 1. 门户中台概览 ========================

    @Test
    @Order(1)
    @DisplayName("TC-A01: 门户中台概览统计")
    void testDashboard() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                baseUrl + "/api/audit/dashboard", String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        JsonNode node = parseJson(response.getBody());
        assertEquals(200, node.get("code").asInt());
        assertNotNull(node.get("data"));

        JsonNode data = node.get("data");
        System.out.println("[TC-A01 PASS] 概览统计: " + data.toString());
        assertTrue(data.has("totalClients") || data.has("clientCount"),
                "应包含客户端统计字段");
    }

    @Test
    @Order(2)
    @DisplayName("TC-A02: 客户端活跃Token统计")
    void testClientDashboard() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                baseUrl + "/api/audit/dashboard/client/" + TEST_CLIENT_ID, String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        JsonNode node = parseJson(response.getBody());
        assertEquals(200, node.get("code").asInt());
        assertNotNull(node.get("data"));

        System.out.println("[TC-A02 PASS] 客户端 [" + TEST_CLIENT_ID + "] 统计: "
                + node.get("data").toString());
    }

    // ======================== 2. 授权记录查询 ========================

    @Test
    @Order(3)
    @DisplayName("TC-A03: 按客户端查询授权记录")
    void testFindRecordsByClient() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                baseUrl + "/api/audit/authorizations/client/" + TEST_CLIENT_ID, String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        JsonNode node = parseJson(response.getBody());
        assertEquals(200, node.get("code").asInt());
        assertTrue(node.get("data").isArray());

        int count = node.get("data").size();
        System.out.println("[TC-A03 PASS] 客户端 [" + TEST_CLIENT_ID + "] 授权记录数: " + count);
        if (count > 0) {
            JsonNode first = node.get("data").get(0);
            System.out.println("  示例记录: " + first.toString());
        }
    }

    @Test
    @Order(4)
    @DisplayName("TC-A04: 按用户查询授权记录")
    void testFindRecordsByUser() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                baseUrl + "/api/audit/authorizations/user/" + TEST_USER_NAME, String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        JsonNode node = parseJson(response.getBody());
        assertEquals(200, node.get("code").asInt());
        assertTrue(node.get("data").isArray());

        int count = node.get("data").size();
        System.out.println("[TC-A04 PASS] 用户 [" + TEST_USER_NAME + "] 授权记录数: " + count);
    }

    @Test
    @Order(5)
    @DisplayName("TC-A05: 按用户+客户端精确查询授权记录")
    void testFindRecordsByUserAndClient() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                baseUrl + "/api/audit/authorizations/user/" + TEST_USER_NAME
                        + "/client/" + TEST_CLIENT_ID, String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        JsonNode node = parseJson(response.getBody());
        assertEquals(200, node.get("code").asInt());
        assertTrue(node.get("data").isArray());

        int count = node.get("data").size();
        System.out.println("[TC-A05 PASS] 用户 [" + TEST_USER_NAME + "] 在客户端 ["
                + TEST_CLIENT_ID + "] 的授权记录数: " + count);
    }

    @Test
    @Order(6)
    @DisplayName("TC-A06: 查询不存在的客户端授权记录")
    void testFindRecordsByNonExistentClient() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                baseUrl + "/api/audit/authorizations/client/non-existent-client-xyz", String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        JsonNode node = parseJson(response.getBody());
        assertEquals(200, node.get("code").asInt());
        assertTrue(node.get("data").isArray());
        assertEquals(0, node.get("data").size(),
                "不存在的客户端应返回空列表");
        System.out.println("[TC-A06 PASS] 不存在客户端返回空列表");
    }

    // ======================== 3. Token强制吊销 ========================

    @Test
    @Order(7)
    @DisplayName("TC-A07: 强制吊销Token")
    void testForceRevoke() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("userId", TEST_USER_NAME);
        request.put("clientId", TEST_CLIENT_ID);
        request.put("tokenType", "ALL");
        request.put("revokeType", 2);
        request.put("remark", "JUnit集成测试-安全审计强制吊销");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(
                baseUrl + "/api/audit/revoke", entity, String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        JsonNode node = parseJson(response.getBody());
        assertEquals(200, node.get("code").asInt());

        JsonNode data = node.get("data");
        System.out.println("[TC-A07 PASS] Token吊销完成: " + data.toString());
    }

    @Test
    @Order(8)
    @DisplayName("TC-A08: 强制吊销Token-参数校验失败(缺少userId)")
    void testForceRevokeValidation() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("clientId", TEST_CLIENT_ID);
        request.put("tokenType", "ALL");
        request.put("revokeType", 2);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(
                baseUrl + "/api/audit/revoke", entity, String.class);

        boolean isValidationError = response.getStatusCode().is4xxClientError()
                || (response.getStatusCode() == HttpStatus.OK
                    && parseJson(response.getBody()).get("code").asInt() != 200);
        assertTrue(isValidationError, "缺少必填参数应返回校验错误");

        if (response.getStatusCode() == HttpStatus.OK) {
            System.out.println("[TC-A08 PASS] 参数校验失败: code="
                    + parseJson(response.getBody()).get("code").asInt());
        } else {
            System.out.println("[TC-A08 PASS] HTTP 4xx: " + response.getStatusCode());
        }
    }

    @Test
    @Order(9)
    @DisplayName("TC-A09: 强制吊销-吊销类型边界值测试")
    void testForceRevokeWithInvalidType() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("userId", TEST_USER_NAME);
        request.put("clientId", TEST_CLIENT_ID);
        request.put("revokeType", 99);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(
                baseUrl + "/api/audit/revoke", entity, String.class);

        boolean isValidationError = response.getStatusCode().is4xxClientError()
                || (response.getStatusCode() == HttpStatus.OK
                    && parseJson(response.getBody()).get("code").asInt() != 200);
        assertTrue(isValidationError, "非法吊销类型应被拦截");

        if (response.getStatusCode() == HttpStatus.OK) {
            System.out.println("[TC-A09 PASS] 非法吊销类型被拦截: code="
                    + parseJson(response.getBody()).get("code").asInt());
        } else {
            System.out.println("[TC-A09 PASS] HTTP 4xx: " + response.getStatusCode());
        }
    }

    // ======================== 4. 吊销日志查询 ========================

    @Test
    @Order(10)
    @DisplayName("TC-A10: 查询吊销日志(分页)")
    void testFindRevokeLogs() {
        String url = baseUrl + "/api/audit/revoke-logs?limit=10&offset=0";
        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        JsonNode node = parseJson(response.getBody());
        assertEquals(200, node.get("code").asInt());
        assertTrue(node.get("data").isArray());

        int count = node.get("data").size();
        System.out.println("[TC-A10 PASS] 吊销日志总数(前10条): " + count);
    }

    @Test
    @Order(11)
    @DisplayName("TC-A11: 按用户查询吊销日志")
    void testFindRevokeLogsByUser() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                baseUrl + "/api/audit/revoke-logs/user/" + TEST_USER_NAME, String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        JsonNode node = parseJson(response.getBody());
        assertEquals(200, node.get("code").asInt());
        assertTrue(node.get("data").isArray());

        int count = node.get("data").size();
        System.out.println("[TC-A11 PASS] 用户 [" + TEST_USER_NAME + "] 吊销日志数: " + count);
    }

    @Test
    @Order(12)
    @DisplayName("TC-A12: 按客户端查询吊销日志")
    void testFindRevokeLogsByClient() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                baseUrl + "/api/audit/revoke-logs/client/" + TEST_CLIENT_ID, String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        JsonNode node = parseJson(response.getBody());
        assertEquals(200, node.get("code").asInt());
        assertTrue(node.get("data").isArray());

        int count = node.get("data").size();
        System.out.println("[TC-A12 PASS] 客户端 [" + TEST_CLIENT_ID + "] 吊销日志数: " + count);
    }

    @Test
    @Order(13)
    @DisplayName("TC-A13: 按吊销类型查询(1=用户主动登出)")
    void testFindRevokeLogsByType1() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                baseUrl + "/api/audit/revoke-logs/type/1", String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        JsonNode node = parseJson(response.getBody());
        assertEquals(200, node.get("code").asInt());
        assertTrue(node.get("data").isArray());

        System.out.println("[TC-A13 PASS] 吊销类型1(用户主动登出)日志数: "
                + node.get("data").size());
    }

    @Test
    @Order(14)
    @DisplayName("TC-A14: 按吊销类型查询(2=后台强制下线)")
    void testFindRevokeLogsByType2() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                baseUrl + "/api/audit/revoke-logs/type/2", String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        JsonNode node = parseJson(response.getBody());
        assertEquals(200, node.get("code").asInt());
        assertTrue(node.get("data").isArray());

        System.out.println("[TC-A14 PASS] 吊销类型2(后台强制下线)日志数: "
                + node.get("data").size());
    }

    @Test
    @Order(15)
    @DisplayName("TC-A15: 按吊销类型查询(3=IAM凭证失效)")
    void testFindRevokeLogsByType3() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                baseUrl + "/api/audit/revoke-logs/type/3", String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        JsonNode node = parseJson(response.getBody());
        assertEquals(200, node.get("code").asInt());
        assertTrue(node.get("data").isArray());

        System.out.println("[TC-A15 PASS] 吊销类型3(IAM凭证失效)日志数: "
                + node.get("data").size());
    }

    // ======================== 5. 全流程端到端测试 ========================

    @Test
    @Order(30)
    @DisplayName("TC-A30: 端到端流程: 概览→授权查询→吊销→日志追溯")
    void testEndToEndFlow() {
        System.out.println("\n========== 端到端审计流程 ==========");

        ResponseEntity<String> r1 = restTemplate.getForEntity(
                baseUrl + "/api/audit/dashboard", String.class);
        assertEquals(200, parseJson(r1.getBody()).get("code").asInt());
        System.out.println("Step1 概览统计: OK");

        ResponseEntity<String> r2 = restTemplate.getForEntity(
                baseUrl + "/api/audit/authorizations/user/" + TEST_USER_NAME, String.class);
        assertEquals(200, parseJson(r2.getBody()).get("code").asInt());
        int authCount = parseJson(r2.getBody()).get("data").size();
        System.out.println("Step2 授权记录查询: " + authCount + " 条");

        Map<String, Object> revokeReq = new LinkedHashMap<>();
        revokeReq.put("userId", TEST_USER_NAME);
        revokeReq.put("clientId", TEST_CLIENT_ID);
        revokeReq.put("tokenType", "ALL");
        revokeReq.put("revokeType", 2);
        revokeReq.put("remark", "E2E端到端测试-自动吊销");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(revokeReq, headers);

        ResponseEntity<String> r3 = restTemplate.postForEntity(
                baseUrl + "/api/audit/revoke", entity, String.class);
        assertEquals(200, parseJson(r3.getBody()).get("code").asInt());
        System.out.println("Step3 Token吊销: OK");

        ResponseEntity<String> r4 = restTemplate.getForEntity(
                baseUrl + "/api/audit/revoke-logs?limit=5&offset=0", String.class);
        assertEquals(200, parseJson(r4.getBody()).get("code").asInt());
        int logCount = parseJson(r4.getBody()).get("data").size();
        System.out.println("Step4 吊销日志追溯: " + logCount + " 条");

        System.out.println("========== 端到端流程完成 ==========\n");
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
