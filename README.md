# 开源乓协 V1.0.0

开源乓协是一个面向乒乓球活动管理与交流的个人学习/娱乐项目。本软件为独立开发项目，并非学校相关学生社团官方专用软件。

## 环境要求

- Windows 11
- Java 21
- 项目自带 Maven Wrapper（本机验收使用 Maven 3.9.12）
- Node.js 20+ 与 npm（本机验收使用 Node.js 22.20.0、npm 10.9.3）
- PostgreSQL 17+（V1.0.0 已在 PostgreSQL 18.0 验证）
- Redis 或 Redis-compatible 服务（默认 `127.0.0.1:6379`）

Docker Compose 仅作为可选开发部署方式，不是本机启动的必要条件。

## 本机秘密配置

复制变量名后在系统环境变量中设置，或创建被 `.gitignore` 排除的 `backend/.env.local`：

```dotenv
SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:5432/opensource_pingpong_dev
SPRING_DATASOURCE_USERNAME=your-project-user
SPRING_DATASOURCE_PASSWORD=your-local-password
SPRING_DATA_REDIS_HOST=127.0.0.1
SPRING_DATA_REDIS_PORT=6379
APP_JWT_SECRET=your-random-secret-at-least-32-characters
APP_JWT_ACCESS_TTL_MINUTES=15
APP_JWT_REFRESH_TTL_DAYS=30
```

不要把真实密码、Token 或 JWT Secret 写入 `.env.example`、README 或 Git。

## 启动

使用本机配置启动后端：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\start-local.ps1
```

健康检查：`http://127.0.0.1:8080/actuator/health`。

启动 H5 开发服务器：

```powershell
cd frontend
npm install
npm run dev:h5 -- --host 127.0.0.1 --port 5176
```

## 测试与验收

```powershell
backend\mvnw.cmd test
backend\mvnw.cmd package
powershell -ExecutionPolicy Bypass -File scripts\e2e-v1-local.ps1
powershell -ExecutionPolicy Bypass -File scripts\verify-play-post-local.ps1
powershell -ExecutionPolicy Bypass -File scripts\verify-community-release-local.ps1
```

E2E 和 QA 数据使用明确测试前缀，脚本不会删除来源不明的数据。

## 前端构建

```powershell
cd frontend
npm run build:h5
npm run build:mp-weixin
npm run build:app
```

微信小程序真实发布仍需有效 AppID/开发者账号；Android 正式发布仍需签名证书和 HBuilderX 打包环境。

## 可选 Docker 环境

`compose.yaml` 可用于其他开发环境启动 PostgreSQL/Redis。应用连接始终通过环境变量切换，Docker 不是 Release Gate。
