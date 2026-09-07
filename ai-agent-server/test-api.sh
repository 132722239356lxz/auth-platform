#!/bin/bash
# ============================================
# AI智能体服务 - API测试脚本 (curl版)
# 使用方式: bash test-api.sh
# 前置: ai-agent-server 启动在 9003 端口
# ============================================

BASE="http://localhost:9003"

echo "=========================================="
echo "  AI智能体服务 API 测试"
echo "  Base URL: $BASE"
echo "=========================================="

echo ""
echo "--- 1. 健康检查 ---"
curl -s "$BASE/api/ai/health/status" | head -c 200
echo ""

echo ""
echo "--- 2. 添加知识库文档 ---"
curl -s -X POST "$BASE/api/ai/knowledge/doc" \
  -H "Content-Type: application/json" \
  -d '{"kbName":"test-kb","title":"使用手册","content":"AI智能体使用说明","contentType":"TEXT"}' | head -c 200
echo ""

echo ""
echo "--- 3. 获取知识库列表 ---"
curl -s "$BASE/api/ai/knowledge/kb" | head -c 300
echo ""

echo ""
echo "--- 4. 本地搜索 ---"
curl -s "$BASE/api/ai/search/local?q=AI&n=5" | head -c 300
echo ""

echo ""
echo "--- 5. 获取业务指标 ---"
curl -s "$BASE/api/ai/analysis/metrics" | head -c 500
echo ""

echo ""
echo "--- 6. 触发分析预警 ---"
curl -s -X POST "$BASE/api/ai/analysis/trigger" | head -c 300
echo ""

echo ""
echo "--- 7. 获取预警汇总 ---"
curl -s "$BASE/api/ai/analysis/alerts/summary" | head -c 300
echo ""

echo ""
echo "--- 8. AI对话 (快速问答) ---"
curl -s "$BASE/api/ai/chat/quick?q=查看待审批任务数" | head -c 500
echo ""

echo ""
echo "--- 9. AI对话 (带记忆) ---"
curl -s -X POST "$BASE/api/ai/chat/ask" \
  -H "Content-Type: application/json" \
  -d '{"sessionId":"curl-test","question":"获取当前业务指标","useMemory":true}' | head -c 500
echo ""

echo ""
echo "--- 10. 对话服务状态 ---"
curl -s "$BASE/api/ai/chat/status" | head -c 200
echo ""

echo ""
echo "--- 11. 清除会话 ---"
curl -s -X DELETE "$BASE/api/ai/chat/session/curl-test" | head -c 100
echo ""

echo ""
echo "--- 12. 清理测试数据 ---"
curl -s -X DELETE "$BASE/api/ai/knowledge/kb/test-kb" | head -c 100
echo ""

echo ""
echo "=========================================="
echo "  测试完成"
echo "=========================================="
