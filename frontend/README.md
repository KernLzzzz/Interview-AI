# Interview AI Web

Vue 3 + TypeScript 前端，包含候选人面试工作台、异步评测进度、结构化报告、题库/场景/用户管理等页面。

```bash
npm ci
npm run dev
```

生产构建：

```bash
npm run build
```

开发服务器将 `/api` 代理到 `http://localhost:8080`；容器部署时由 `nginx.conf` 转发到后端服务。
