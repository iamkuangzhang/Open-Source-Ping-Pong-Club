# 开源乓协 V1.0.0 Release Checklist

验收日期：2026-10-01

| Gate | 状态 | 实际结果 |
|---|---|---|
| 项目应用冷停止 | PASS | 仅停止当前仓库 Spring Boot/Maven/uni-app 进程，确认 8080/5176 无项目监听后重新启动 |
| PostgreSQL | PASS | PostgreSQL 18.0，`opensource_pingpong_dev` 使用项目账号实际连接成功 |
| Redis | PASS | `127.0.0.1:6379`，PING=PONG |
| Java | PASS | Eclipse Temurin Java 21.0.10 |
| Maven Wrapper | PASS | Maven 3.9.12 |
| Node/npm | PASS | Node.js 22.20.0 / npm 10.9.3 |
| Flyway | PASS | `flyway_schema_history` V1～V7 顺序正确且 success=true；启动校验 7 项成功 |
| Spring Boot 冷启动 | PASS | Tomcat 8080 启动成功 |
| `/actuator/health` | PASS | `UP` |
| 数据库时间 Round-trip | PASS | 约球、公告、二手发布时间、通知 read_at 均无时区/日期漂移；JSON 毫秒精度与数据库微秒精度误差小于 1ms |
| 后端自动化测试 | PASS | 45 tests，0 failures，0 errors，0 skipped |
| 后端 package | PASS | 未跳过测试，BUILD SUCCESS |
| 完整赛事 HTTP E2E | PASS | 注册、审核、ACTIVE、1400、二维码、8 人积分单淘汰、10 次 finalize、积分流水/榜单、DAILY 循环赛全部通过 |
| 约球 HTTP E2E | PASS | 创建、查询、加入、重复加入 409、满员 409、退出释放名额、再次加入全部通过 |
| 社区 API Smoke | PASS | 公告权限与读取；二手发布/查询/卖家修改/售出/非卖家 403；通知 read/read-all/越权隔离通过 |
| Security 路由 | PASS | 未登录赛事/约球/二手写操作 401；普通用户管理接口 403；管理角色操作成功；公共成员验证成功 |
| 异常响应安全 | PASS | 客户端 500 仅返回 `INTERNAL_ERROR`，不包含 SQL、堆栈、路径、Java 类或连接信息 |
| 日志安全 | PASS | 认证 DTO 密码、Access Token、Refresh Token 永久脱敏；最终启动日志敏感模式扫描 0 命中 |
| Secret 审计 | PASS | 无真实 secret 进入候选 Git 文件；`.env.local`、日志、构建目录均被忽略 |
| 测试数据隔离 | PASS | 正式前端无 `e2e_`/`ui_demo_`/乱码过滤；测试前缀仅位于 E2E/QA 脚本 |
| 生产残留扫描 | PASS | 生产路径无 TODO、FIXME、HACK、fakeData、mockData、UnsupportedOperationException、console.log、System.out.println、debugger |
| 产品名称/非官方声明 | PASS | 展示统一为“开源乓协”；登录、注册、关于、成员验证保留非官方说明 |
| H5 build | PASS | `npm run build:h5` |
| 微信小程序 build | PASS | `npm run build:mp-weixin` |
| App build | PASS | `npm run build:app` |
| H5 页面 Smoke | PASS | Edge headless 实际加载比赛、约球、社区、我的、登录、注册、通知、成员身份、积分、管理中心 |
| 微信真实发布 | BLOCKED（外部条件） | 缺少真实 AppID/开发者账号，不属于代码失败 |
| Android 正式签名 | BLOCKED（外部条件） | 缺少签名证书/HBuilderX 发布环境，不属于代码失败 |
| Release Notes | PASS | `RELEASE_NOTES_V1.0.0.md` |
| Git 初始化与忽略规则 | PASS | 已初始化；本机秘密、日志、构建产物和视觉 QA 临时文件均通过 `git check-ignore` |
| Git baseline commit | PASS | 已创建 `release: 开源乓协 v1.0.0` 稳定基线 |
| Git tag | PASS | 已创建本地 annotated tag `v1.0.0`；未推送远程 |

## 已知非阻塞警告

- Flyway 对 PostgreSQL 18.0 显示“高于当前已测试版本 17”的升级建议。
- Dart Sass legacy JS API deprecation warning。
- Node 依赖循环访问 warning。
- 测试中 Spring Boot `@MockBean` 弃用提醒和 Mockito 动态 agent 提醒。
