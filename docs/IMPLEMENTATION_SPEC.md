# 开源乓协 V1.0 Codex 开发实施规格

## 0. 总则

本文件是 Codex 的工程实施基线。

Codex 必须：
1. 优先遵循本目录文档，不自行改变业务规则。
2. 一次只实现一个 Phase。
3. 每个 Phase 结束必须执行测试并对照 Gate。
4. Gate 未 PASS 不得进入下一阶段。
5. 数据库变更必须由 Flyway 管理。
6. 已执行 Migration 不得直接改写，应新增 Migration。
7. 比赛、积分、权限、审核属于关键数据，必须有自动化测试。
8. 后端承担最终权限与业务校验。
9. 核心逻辑不得用 Mock 或 TODO 假装完成。
10. 不得引入微服务、MQ、工作流引擎等超出 V1 的复杂架构。

## 1. 技术基线

前端：
- HBuilderX
- uni-app
- Vue 3
- TypeScript
- Pinia
- SCSS

目标：
- 微信小程序
- Android App

后端：
- Java 21
- Spring Boot 3.5.x
- Maven 3.9+
- Spring Web
- Spring Security
- Spring Validation
- Spring Data JPA
- Flyway
- PostgreSQL 17
- Redis 7.x
- BCrypt

测试：
- JUnit 5
- Spring Boot Test
- Testcontainers
- Mockito
- k6

## 2. 推荐仓库

```text
opensource-pingpong/
├── backend/
├── frontend/
├── deploy/
├── docs/
├── .env.example
├── .gitignore
├── README.md
└── AGENTS.md
```

后端按业务域：
- auth
- user
- membership
- organization
- competition
- rating
- announcement
- play
- market
- notification
- audit
- storage
- common

比赛算法放：
`competition/algorithm`

积分纯函数放：
`rating/domain`

## 3. API 规则

统一前缀：
`/api/v1`

统一返回：
```json
{
  "success": true,
  "code": "OK",
  "message": "success",
  "data": {}
}
```

前端依据 `code` 做逻辑，不依据中文 message。

## 4. 时间

数据库：TIMESTAMPTZ  
Java：Instant 或 OffsetDateTime  
API：ISO 8601

核心业务不得依赖服务器默认时区。

## 5. 安全

- 密码 BCrypt
- Access Token + Refresh Token
- Access Token 建议 15 分钟
- Refresh Token 建议 30 天
- `/api/v1/admin/**` 必须授权
- Entity 不直接作为 Controller Request/Response
- 使用 DTO / VO
- 文件上传验证 MIME、魔数、大小
- 日志禁止打印密码、Token、二维码原 Token

## 6. 二维码

生成：
`POST /api/v1/membership/me/qr`

使用 256-bit 安全随机 Token。

Redis Key：
`member:qr:{SHA256(token)}`

TTL：60 秒

验证：
`GET /api/v1/public/member-verifications/{token}`

Token 正文不得直接包含 userId、成员编号、学号。

## 7. 关键业务

### 入会
审核通过必须在一个事务中：
- 锁定申请
- 验证 PENDING
- APPROVED
- 创建 membership
- 创建 rating_account=1400
- 创建 INITIAL rating_ledger
- 授予 MEMBER
- 创建通知
- 写 audit_log

### 比赛
严格使用状态机：
DRAFT → REGISTRATION → ARRANGING → READY → RUNNING → RESULT_CONFIRMING → FINISHED

### Match
使用 `@Version` 乐观锁。

### 积分
赛事最终确认时统一结算。
- 锁 Competition
- 校验 RESULT_CONFIRMING
- 校验 settlement 不存在
- 加载有效场次
- 按 user_id 固定顺序锁 rating_accounts
- 按比赛顺序逐场计算
- 写 rating_ledger
- 更新 rating_accounts
- Competition → FINISHED
- audit_log
- 事务提交

任一步失败全部回滚。

## 8. Phase 0：工程骨架

只实现：
- repository structure
- backend boot
- frontend boot
- PostgreSQL connection
- Redis connection
- Flyway
- 统一 API response
- 统一 exception handler
- Spring Security 骨架
- `/actuator/health`

Gate：
- backend tests PASS
- frontend build PASS
- Flyway 可在空 PostgreSQL 执行
- backend 可启动

禁止提前开发 Phase 1。

## 9. Phase 1：账号

实现：
- 注册
- 登录
- Refresh
- Logout
- `/users/me`
- 编辑资料

Gate：
- 注册成功
- 重复账号失败
- 密码 BCrypt
- 登录成功
- 错误密码失败
- Refresh 正常
- Logout 后 Refresh 失效
- 权限测试通过

## 10. Phase 2：成员与权限

实现：
- 申请加入
- 审核
- membership
- 1400 初始积分
- MEMBER 角色
- 组织角色
- 成员列表

Gate：
注册用户 → 申请 → 审核 → ACTIVE → 1400 → MEMBER
完整自动化测试 PASS。

## 11. Phase 3：二维码

实现：
- 动态二维码
- Redis TTL
- 公共验证

Gate：
- ACTIVE 可生成
- 普通用户不可生成
- 60 秒后过期
- 成员失效后 Token 不再验证为有效

## 12. Phase 4：比赛基础

实现：
- 比赛 CRUD
- 发布
- 报名
- 报名审核
- 关闭报名
- 状态机

暂不做编排。

Gate：
状态机和报名测试全部 PASS。

## 13. Phase 5：比赛算法

先写算法与测试，再接数据库。

实现：
- SingleEliminationBracketGenerator
- RoundRobinScheduleGenerator
- RandomGroupAllocator
- RatingBalancedGroupAllocator

Gate：
执行 `TEST_PLAN.md` 对应算法测试全部 PASS。

## 14. Phase 6：正式赛程

将算法接入：
- stages
- groups
- matches

实现：
- 编排预览
- 确认落库

Gate：
- 13 人单淘汰正确
- 4 人循环生成 6 场正确

## 15. Phase 7：比分与晋级

实现：
- match start
- score edit
- score validation
- winner calculation
- auto advancement
- optimistic locking

Gate：
8 人淘汰赛可从首轮完整推进到唯一冠军。

## 16. Phase 8：积分

实现：
- RatingCalculator
- rating settlement
- ledger
- leaderboard
- history

Gate：
积分边界、幂等、并发、流水完整性全部 PASS。

## 17. Phase 9：前端核心

实现：
- 首页
- 登录注册
- 加入申请
- 成员卡
- 比赛列表/详情
- 报名
- 管理比赛
- 编排
- 赛程
- 比分录入
- 排行榜
- 积分历史

Gate：
微信小程序和 Android 构建 PASS，核心流程两端可执行。

## 18. Phase 10：社区

实现：
- 公告
- 约球
- 二手
- 通知

Gate：
CRUD、权限、状态测试 PASS。

## 19. Phase 11：最终验收

执行：
- 全量自动化测试
- k6
- 权限检查
- 安全检查
- 数据库备份恢复演练
- 微信小程序构建
- Android release build

## 20. Codex 禁止自行改变

- 初始积分 1400
- 最低积分 400
- 积分变化表
- 比赛性质
- 角色
- 权限
- 积分赛事条件
- 完赛锁定
- 二维码 TTL 60 秒
- BYE 不计积分
- 未开赛 WALKOVER 不计积分
- 比分不影响积分变化
- V1 不做支付
- V1 不做即时聊天
- V1 不做微服务

## 21. 遇到规格冲突

不得静默修改。

必须：
1. 指出冲突
2. 标明涉及文件
3. 给出建议修正
4. 暂停冲突部分
5. 不受影响部分可继续

## 22. 每阶段完成报告

必须输出：
- 完成内容
- 新增/修改文件
- Migration
- 新增 API
- 测试命令
- 测试结果
- 已知问题
- Gate PASS/FAIL

## 23. Definition of Done

一个功能同时满足才算完成：
- 代码
- Migration
- 后端校验
- 权限
- 错误码
- 必要日志/审计
- Unit Test
- Integration Test
- 前端错误处理
- 无核心 TODO/Mock

## 24. Phase 0 首次提示词

请完整阅读 `docs/` 下的全部 V1.0 文档，尤其是 `IMPLEMENTATION_SPEC.md`。

当前只执行 Phase 0，不得提前开发 Phase 1 或后续业务。

请先检查当前仓库状态，然后按照实施规格创建工程骨架、后端、前端、Flyway、PostgreSQL/Redis 配置、统一响应结构、统一异常处理、Spring Security 基础框架以及健康检查。

完成后必须：
1. 运行后端测试；
2. 验证 Flyway 可以在空 PostgreSQL 数据库完整执行；
3. 验证后端可以启动；
4. 验证前端可以构建；
5. 输出新增和修改文件清单；
6. 输出实际测试命令和结果；
7. 对照 Phase 0 Gate 逐项给出 PASS/FAIL；
8. 如果存在 FAIL，不要开始 Phase 1。

## 25. 后续提示词模板

完整阅读 `docs/` 下的 V1.0 文档和当前仓库已有实现。

现在只执行 Phase X。

首先检查上一 Phase 的 Gate 和当前测试状态，不要破坏已通过的功能。

严格按 Implementation Spec 实现本 Phase，禁止提前实现后续 Phase。

所有数据库变化必须使用新的 Flyway Migration，禁止修改已经执行过的历史 Migration。

所有核心业务逻辑必须补充自动化测试。

完成后：
- 运行相关 Unit Tests；
- 运行 Integration Tests；
- 运行必要构建；
- 输出修改文件；
- 输出数据库变更；
- 输出新增 API；
- 输出测试结果；
- 对照本 Phase Gate 给出 PASS/FAIL；
- 说明剩余问题。

Gate 未完全 PASS 时，不要进入下一 Phase。
