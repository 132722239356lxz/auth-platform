# auth-platform
自研统一授权中台（Spring Authorization Server + 工作流 + RAG 智能体 + 分布式消息广播 + SpringCloudAlibaba）



┌─────────────────────────────────────────────────────────────
客户端层：App、Web前端、各业务子系统、第三方接入应用
├─────────────────────────────────────────────────────────────
接入网关层：Spring Cloud Gateway
路由转发、鉴权拦截、Token校验、流量限流、请求溯源
├─────────────────────────────────────────────────────────────
中台核心服务（微服务拆分，后续接入SpringCloudAlibaba）
1. 统一授权服务（OAuth2 令牌发放、刷新、吊销、登作废）
2. 自定义工作流审批服务（授权申请、权限开通驳回、流转节点）
3. 消息广播服务（MQ广播、子系统上下行通信、事件推送）
4. AI智能应用服务（RAG知识库、本地离线检索、联网搜索、业务数据分析预警智能体）
├─────────────────────────────────────────────────────────────
基础设施层
MySQL、Redis、RocketMQ/Kafka、MinIO文件、向量库(FAISS/Milvus)、Nacos、Sentinel
└─────────────────────────────────────────────────────────────

## 工程项目情况

auth-parent
├─ auth-server            # OAuth2授权服务核心
├─ auth-flow              # 自定义工作流审批模块
├─ auth-message           # MQ广播消息封装
├─ ai-agent-server        # RAG、智能体、知识库服务
├─ gateway                # SpringCloud Gateway网关
├─ common-core            # 公共实体、常量、工具、统一返回
└─ subsystem-sdk          # 子系统接入SDK（监听广播、上报消息）