# interview-AI 多模态智能模拟面试评测平台

基于 AI 的面试模拟与评测系统,覆盖**管理员 / 面试官 / 候选人**三类角色。前后端分分支托管,`main` 仅作项目总览。

## 分支结构

| 分支 | 内容 | 技术栈 |
| --- | --- | --- |
| `main` | 项目总览(本文档) | — |
| `interview-AI/backend` | 后端 | Java 17 · Spring Boot 3.5 · MyBatis-Plus · MySQL · Redis · MinIO · Spring Security · JWT · knife4j/springdoc |
| `interview-AI/frontend` | 前端 | Vue 3 · Vite · TypeScript · Element Plus · Pinia · ECharts · axios |

## 功能概览

- **认证与权限**:JWT(sessionId)+ Redis 会话,登出即时失效;RBAC(admin/interviewer/candidate)+ 接口级权限注解 + 数据权限
- **题库模块**:题目增删改查、按 3:5:2 难度比例随机抽题,列表返回 VO 屏蔽 `expectedAnswer/keywords`
- **面试流程**:场景管理 → 创建面试(pending)→ start(ongoing)→ submit(异步评测评分 + AI 反馈)→ cancel;重复 start/submit 有状态机校验
- **报告生成**:`ReportGenerator` 解析作答数据启发式打分,幂等写 `score/aiFeedback`
- **用户中心**:改资料 / 改密码 / 我的记录;管理员分页、分配角色,改密/改角色后删会话
- **文件上传**:MinIO 存储 + 类型/大小校验
- **定时任务**:每日 8 点面试提醒(`interview-job`)
- **API 文档**:knife4j,启动后访问 `http://localhost:8080/doc.html`

## 后端运行(`interview-AI/backend`)

### 环境依赖
JDK 17 · Maven · MySQL 8 · Redis;文件上传另需 MinIO。

### 步骤
1. 建库 `interview_ai`,按顺序执行根目录 SQL:`init.sql` → `rbac.sql` → `increment_20260831.sql`
2. 启动本地 MySQL(3306)、Redis(6379);文件上传需 MinIO(9000,bucket `interviewai`)
3. 按需修改 `interview-bootstrap/src/main/resources/application-dev.yml`(数据源/Redis/MinIO/JWT)
4. 启动入口模块:
   ```bash
   cd interview-bootstrap
   ../mvnw spring-boot:run
   ```
5. 接口文档:http://localhost:8080/doc.html

### 种子账号
| 角色 | 账号 | 密码 |
| --- | --- | --- |
| 管理员 | `admin` | `admin123` |
| 面试官 | `interviewer` | `interview123` |
| 候选人 | `cand01` | `abc123` |

> ⚠️ `init.sql` 里的 `test01/123456` 是明文,启动时 `DataInitializer` 只收敛了 admin/interviewer,**test01 登录会 401**,请改用 `cand01`。

## 前端运行(`interview-AI/frontend`)

```bash
npm install
npm run dev   # http://localhost:5173
```

- 开发代理已配:`/api` → `http://localhost:8080`,无跨域问题
- 登录已接后端真实接口(JWT 存 localStorage,401 自动回登录页)

## 待办 / 已知缺口

- [ ] 前端 `src/stores/config.ts`、`src/stores/dashboard.ts` 仍走本地 mock(`src/mock/`),需对接真实接口
- [ ] 后端 `test01` 种子账号密码未收敛为 BCrypt(见上方种子账号警告)
- [ ] 前端根目录 `vite.config.js` / `vite.config.d.ts` 是 `tsc` 生成物,已加入 `.gitignore`;本机残留会遮蔽 `vite.config.ts`(Vite 优先加载 `.js`),建议删除
- [ ] `application-dev.yml` 含本地明文密码与占位 JWT secret,仅限开发;生产请用环境变量(`application-prod.yml` 已留注释模板)
- [ ] 前端未提供 `.env`,代理目标写死在 `vite.config.ts`(localhost:8080),部署时需调整

## 说明
课程阶段作业项目。
