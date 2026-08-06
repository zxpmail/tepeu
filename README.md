# Tepeu

个人 Agent 工作台：对话、工作区文件、记忆、终端，以及 Hook / 多 Agent / MCP / 成本 / 自主调度 / 市场 / 技能脚本等 Agentic OS 能力。

## 技术栈

- 后端：Java 21 · Spring Boot 4.0.7 · Spring AI 2.0 · SQLite · GraalJS 24.2.1
- 前端：React 18 · Vite 6 · Tailwind CSS 4
- 部署：Docker 多阶段单 JAR（端口 `30141`）

当前应用版本：**1.0.0**。详见 [RELEASE_NOTES-v1.0.0.md](./RELEASE_NOTES-v1.0.0.md)。

## 本地运行

```bash
# 后端
cd backend
mvn spring-boot:run

# 前端（开发代理到后端）
cd frontend
npm install
npm run dev
```

浏览器打开 Vite 提示的本地地址；生产构建会把前端打进后端静态资源。

## Docker

```bash
docker build -t tepeu:v1.0.0 .
docker compose up -d
# http://localhost:30141
```

数据卷：`tepeu-data` / `tepeu-keys`（见 `docker-compose.yml`）。  
本机 `docker build`：**暂缓**（当前环境无 Docker CLI；定义已就绪，见发布说明）。

## 测试

```bash
cd backend && mvn test
cd frontend && npm run typecheck
# 可选 E2E（需后端）：cd frontend && npm run test:e2e
```

## 文档入口

| 文件 | 用途 |
|------|------|
| [Product-Spec.md](./Product-Spec.md) | 产品规格 |
| [DEV-PLAN.md](./DEV-PLAN.md) | 交付切片 |
| [CONTEXT.md](./CONTEXT.md) | 当前进度快照 |
| [RELEASE_NOTES-v1.0.0.md](./RELEASE_NOTES-v1.0.0.md) | v1.0.0 说明 |
| [RELEASE_NOTES-v0.2.0.md](./RELEASE_NOTES-v0.2.0.md) | v0.2.0 说明 |
| [memory/handoff.md](./memory/handoff.md) | 会话交接 |

## 已完成（摘要）

- ✅ v0.1.0 个人工作台 · ✅ v0.2.0 Harness（Hook / 多 Agent / MCP / 成本）
- ✅ v1.0.0 Agentic OS（Phase 10–18：自主调度 → 技能脚本沙箱 → 发布收口）
- ⏳ Docker 镜像本机实测、git tag / GitHub Release（可选，须批准）
