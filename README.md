# Interview AI

一套前后端分离的多模态智能模拟面试与结构化评测平台，覆盖候选人、面试官、管理员三类角色。候选人可以通过文本、语音或视频完成模拟面试并查看逐题反馈；面试官负责复核与人工评分；管理员维护用户、场景、题库和系统数据。

## 业务问题

通用题单难以反映岗位差异，人工模拟面试成本高，单次模型调用又存在延迟、失败、输出不可控和结果难追踪的问题。本项目将岗位上下文、结构化题库、多模态面试、异步模型评测和运行观测组合成一条可交付工作流：候选人提供目标岗位与 JD，系统从缓存题库中选择更相关的问题，面试结束后生成有证据的能力画像，面试官可查看服务成功率、P95 延迟和重试情况。

设计原则是“AI 负责提供辅助判断，系统负责数据边界和可靠性”：模型不得直接给出录用决定，结论必须绑定本次回答证据，任何不合规输出都不会直接落库。

## 核心能力

- **完整面试闭环**：场景选择 → 按 3:5:2 难度比例抽题 → 限时作答 → 异步评测 → 结构化报告。
- **岗位定制抽题**：创建面试时保存目标岗位、JD 与候选人背景快照；从 Redis 缓存候选集按关键词相关性排序，并在各难度配额内优先选择岗位相关题目，无上下文时自动回退随机策略。
- **多模态面试厅**：文本、语音、视频三种面试模式；候场厅完成网络、麦克风、摄像头检测，面试厅提供本地镜像预览、音量反馈、静音/关镜头、题目语音播报和语音转写。
- **媒体安全归档**：浏览器通过 `MediaRecorder` 采集 WebM/Ogg，结束时上传 MinIO；面试记录只绑定通过用户归属与业务模块校验的文件 ID，并保存媒体时长。
- **题库缓存优化**：基于 Redis Hash 实现热点题库读取、写后失效、TTL 更新与启动/定时预热；随机抽题从缓存候选集完成，避免高频执行 `ORDER BY RAND()`。
- **答案安全**：候选人 VO 与管理端 VO 分离，公开接口不包含 `expectedAnswer`、`keywords`；抽题快照由服务端构建并以 JSON 落库，提交答案会按快照题号重新校验和组装。
- **真实 AI 评测链路**：支持 OpenAI-compatible Chat Completions 模型；输出总分、六项能力维度、候选人画像、岗位匹配、风险信号、逐题证据和行动建议。API Key 仅从服务端环境变量读取。
- **AI 评测异步化**：`@Async` + 独立线程池将模型评测从提交接口解耦；支持任务提交、状态查询、结果回调和最多 3 次定时重试。
- **任务不丢失**：任务先持久化再投递，事务提交后启动工作线程；线程池使用 `CallerRunsPolicy`，避免队列饱和时静默丢弃；同一任务原地重试，保留完整状态轨迹。
- **AI 可观测性**：聚合最近 1000 个评测任务，提供成功率、平均/P95 延迟、重试率、在途任务和真实模型/离线引擎分布，并在管理仪表盘展示。
- **评测基准**：固定黄金样本验证评分区间，模拟兼容模型服务验证 HTTP 调用和 JSON 契约；基准说明不会用未经验证的数据包装准确率。
- **认证与权限**：Spring Security + JWT + Redis 会话 + RBAC，并对候选人的面试记录实施行级权限校验。
- **文件能力**：MinIO 保存简历、头像、面试录音录像与附件，包含类型、大小、文件名及模块路径校验。

## 技术栈

| 端 | 技术 |
| --- | --- |
| Web | Vue 3、TypeScript、Vite、Element Plus、Pinia、ECharts |
| API | Java 17、Spring Boot 3.5、Spring Security、MyBatis-Plus |
| 数据 | MySQL 8、Redis 7、MinIO |
| 工程 | Maven、Docker Compose、JMeter 5.6、OpenAPI / Knife4j |

## 项目结构

```text
.
├─ backend/                 Spring Boot 多模块后端
│  ├─ interview-bootstrap/ 启动与环境配置
│  ├─ interview-common/    安全、Redis、异步线程池、统一响应
│  └─ interview-modules/   system / question / interview / file / job
├─ frontend/                Vue 3 单页应用
├─ performance/             题库缓存与异步提交 JMeter 测试计划
├─ compose.yaml             MySQL、Redis、MinIO、前后端编排
└─ .env.example             容器环境变量模板
```

## 关键设计

### Redis Hash 题库缓存

Key 遵循“业务域:模块:维度”规范：

```text
interview:question-bank:scenario:{scenarioId}
└─ Hash fields
   ├─ difficulty:all
   ├─ difficulty:1
   ├─ difficulty:2
   └─ difficulty:3
```

每个场景的全部难度维度共享一个过期边界。新增、修改、删除题目后同时失效场景 Key 与全局 Key；应用启动后预热前 20 个热点场景，并在默认 30 分钟 TTL 到期前每 20 分钟主动更新。Redis 异常时自动回源 MySQL，不影响主业务可用性。

管理员可调用：

```http
GET  /api/questions/cache/stats
POST /api/questions/cache/warmup?limit=20
```

### 异步 AI 评测

```text
提交答案
   │  同一事务写 interview_record + task_log(pending)
   ▼
事务提交后投递 ──► aiEvaluationExecutor ──► 评测内核/外部模型
                           │                         │
                           │ 失败                    │ 成功/回调
                           ▼                         ▼
                    failed + nextRetryAt       score + JSON report
                           │
                           └── 每 5 分钟扫描，最多重试 3 次
```

任务接口：

```http
POST /api/evaluations
GET  /api/evaluations/{taskId}
GET  /api/evaluations/records/{recordId}/latest
POST /api/evaluations/callback
GET  /api/evaluations/metrics
```

回调必须携带 `X-Evaluation-Callback-Token`。统一报告 JSON 包含 `score`、`summary`、`dimensions`、`candidateProfile`、`riskSignals`、`recommendations`、`items` 和 `modality`。模型回答会先经过 JSON 结构校验再落库；模型超时、服务异常或结构不合格都会进入现有重试流程。

默认不需要 API Key，系统使用标记为 `local-explainable` 的离线可解释评测器，仍会生成完整报告，便于本地部署和功能验收。启用真实模型时配置：

```env
AI_EVALUATION_ENABLED=true
AI_BASE_URL=https://api.openai.com/v1
AI_ENDPOINT=/chat/completions
AI_MODEL=your-model-name
AI_API_KEY=your-server-side-key
```

任何兼容 Chat Completions 协议的服务都可以通过 `AI_BASE_URL`、`AI_ENDPOINT` 和 `AI_MODEL` 接入。评测提示词明确要求模型仅依据本次回答中的可观察证据分析，不推断敏感属性，也不直接作出录用决定。录音录像通过文件 ID 纳入数据模型；当前通用兼容层评测转写文本，后续可在网关层扩展供应商专有的音视频输入格式。

## 快速启动

### Docker Compose

```bash
cp .env.example .env
# 修改 .env 中的密码、JWT 密钥与回调令牌
docker compose up --build
```

若本机已有服务占用默认端口，可通过 `LIGANG_MYSQL_PORT`、`LIGANG_REDIS_PORT`、`LIGANG_MINIO_PORT`、`LIGANG_MINIO_CONSOLE_PORT`、`LIGANG_BACKEND_PORT`、`LIGANG_FRONTEND_PORT` 覆盖宿主机端口；容器间通信端口无需修改。

- Web：<http://localhost:5173>
- API：<http://localhost:8080>
- API 文档：<http://localhost:8080/doc.html>
- MinIO 控制台：<http://localhost:9001>

首次启动会自动执行 `backend/init.sql` 和 `backend/rbac.sql`。已有数据库请先备份，再执行 `backend/migration_20260910.sql`。

语音/视频模式需要通过 `localhost` 或 HTTPS 访问，并在浏览器中授予麦克风/摄像头权限。Chrome/Edge 可使用实时语音转写；不支持 Web Speech API 的浏览器仍可正常录制，并允许手工补充回答要点。单场媒体上传上限为 150 MB。

### 本地开发

依赖 Java 17、Node.js 22+、MySQL 8、Redis 7；文件上传功能另需 MinIO。

```bash
# 后端
cd backend
./mvnw spring-boot:run -pl interview-bootstrap -am

# 前端（另一个终端）
cd frontend
npm ci
npm run dev
```

Windows 可将 `./mvnw` 替换为 `mvnw.cmd`。Vite 会把 `/api` 代理到 `http://localhost:8080`。

### 初始账号

初始化脚本提供三个分角色演示账号：

| 用户名 | 密码 | 角色 |
| --- | --- | --- |
| `admin` | `admin123` | 管理员 |
| `interviewer` | `interview123` | 面试官 |
| `test01` | `candidate123` | 候选人 |

公开部署前必须修改初始密码以及 `.env` 中的全部密钥。

## 验证与压测

```bash
# 后端单元测试
cd backend
./mvnw test

# 前端类型检查与生产构建
cd frontend
npm run build
```

`performance/` 提供两套非 GUI JMeter 测试计划及数据模板。题库混合读压测以数据库查询量下降约 50% 或更多为验收线；异步提交以平均响应时间提升约 40% 或更多、`task_log` 无任务缺口为验收线。具体命令、SQL/APM 计数口径和报告留存要求见 [performance/README.md](performance/README.md)。

性能百分比与硬件、题库分布、缓存命中率及模型耗时有关，应在目标部署环境重新执行测试并保存 JTL 与 HTML 报告。

AI 评分的基准数据、运行命令以及接入真实模型后的人工一致性评测方法见 [performance/AI_EVALUATION_BENCHMARK.md](performance/AI_EVALUATION_BENCHMARK.md)。

## 配置说明

| 配置 | 默认值 | 说明 |
| --- | --- | --- |
| `interview.cache.question.ttl` | `PT30M` | 题库 Hash TTL |
| `interview.cache.question.refresh-interval` | `PT20M` | 热点题库刷新周期 |
| `interview.cache.question.warmup-limit` | `20` | 自动预热场景数 |
| `interview.evaluation.max-retries` | `3` | 失败任务最大重试次数 |
| `interview.evaluation.retry-delay` | `PT1M` | 单次失败后的最小等待时间 |
| `interview.evaluation.ai.enabled` | `false` | 是否调用真实远程模型 |
| `interview.evaluation.ai.base-url` | OpenAI API 地址 | OpenAI-compatible 服务地址 |
| `interview.evaluation.ai.model` | 空 | 评测模型名称，启用远程评测时必填 |
| `AI_API_KEY` | 空 | 服务端模型密钥；禁止写入前端或提交仓库 |
| `AI_CALLBACK_TOKEN` | 无安全默认值 | 外部评测回调令牌，生产环境必须配置 |

生产环境通过 `application-prod.yml` 读取数据库、Redis、JWT 与 MinIO 环境变量。不要提交真实密码、访问密钥或压测 JWT。

## License

Copyright remains with the repository owner. Add an explicit license before commercial distribution.
