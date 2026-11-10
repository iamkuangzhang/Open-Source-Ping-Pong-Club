# Sprint 3 赛事模块核查与修复（李佳奇）

本文档对应《Sprint 3 用户故事与任务清单》中李佳奇负责的 4 项任务。范围仅限
乒乓球单打（`SINGLES` / `SINGLE_ELIMINATION` / `ROUND_ROBIN`），不引入其他球类。

| 任务 | 优先级 | 验收标准 | 对应实现 / 证据 |
| --- | --- | --- | --- |
| 赛事创建和编辑权限自动化测试 | P0 | 非管理员写操作被拒绝 | `AuthorizationServiceTest`、`CompetitionPermissionWebTest` |
| 报名、退出与截止条件核查及修复 | P0 | 重复报名、满员、截止后报名返回业务错误 | `CompetitionControllerTest`、`CompetitionController.requireRegistrationOpen` |
| 参赛名单、审核与人数接口联调 | P0 | 管理员可审核；普通用户不可管理；批准人数与名额一致 | `CompetitionController.reviewEntry`、`CompetitionControllerTest` |
| 编排契约与状态机核查及修复 | P0 | 报名关闭后可编排；非法状态不能跳转 | `ScheduleService`、`CompetitionStateMachineTest`、`ScheduleServiceTest` |

## 1. 核查结论与修复

### 1.1 赛事创建与编辑权限（任务 16）

- 创建赛事使用 `COMPETITION_CREATE`，编辑、发布、关闭、编排、开始、取消使用
  `COMPETITION_MANAGE`，均由 `AuthorizationService` 在后端统一判断。
- 新增权限矩阵单元测试：普通成员无法创建/编辑赛事，部长、副会长、会长、指导老师
  可管理赛事；任命职务仅会长/指导老师。
- 新增 HTTP 层测试：普通成员 `POST /admin/competitions`、
  `PUT /admin/competitions/{id}`、审核名单均返回 `403 PERMISSION_DENIED`，
  且不会访问数据库；未登录请求返回 `401`。

### 1.2 报名、退出与截止条件（任务 17）

- 报名校验保持：赛事必须为 `REGISTRATION`；必须在 `registration_start` 与
  `registration_end` 之间；重复报名返回 `409 COMPETITION_ALREADY_REGISTERED`；
  满员返回 `409 COMPETITION_FULL`；截止后返回 `409 COMPETITION_REGISTRATION_CLOSED`。
- 修复：退出报名原先只检查赛事状态，未检查报名时间窗口。现与报名共用
  `requireRegistrationOpen`，报名截止后退出同样返回 `409 COMPETITION_REGISTRATION_CLOSED`。
- 报名使用 `select ... for update` 锁定赛事行后再统计和插入，避免并发下超卖。
- 新增测试覆盖截止后报名、重复报名、满员、截止后退出、截止前正常退出。

### 1.3 参赛名单、审核与人数（任务 20）

- 管理员审核接口继续要求 `COMPETITION_MANAGE`；仅 `PENDING` 且赛事处于
  `REGISTRATION` 状态时可审核，重复审核返回 `409 COMPETITION_INVALID_STATE`。
- 修复：审核时锁定赛事行并检查已批准人数，达到 `max_participants` 时拒绝批准，
  返回 `422 COMPETITION_FULL`，保证已批准人数与赛事名额一致。
- 修复：编辑赛事时禁止把 `max_participants` 调到低于当前
  `PENDING + APPROVED` 人数，返回 `422 COMPETITION_CAPACITY_BELOW_ENTRIES`。
- 审核更新的 `approved_at` 使用显式 JDBC 参数类型，驳回时写入 SQL NULL。

### 1.4 编排契约与状态机（任务 25）

- 编排仍要求赛事状态为 `ARRANGING`（报名关闭后），预览与落库使用同一套
  `APPROVED` 名单和同一种子。
- 修复：编排落库时通过 `CompetitionStateMachine.next(ARRANGING, "ready")`
  进入 `READY`，不再硬编码状态。
- 修复：若赛事已存在 `competition_stages`，再次编排返回
  `409 COMPETITION_INVALID_STATE`，防止重复生成赛程。
- 新增测试覆盖：非 `ARRANGING` 状态不可编排、已有赛程不可重复编排、
  未到 `READY` 不可开始、未完成全部场次不可进入结果确认，以及
  `DRAFT → FINISHED`、`REGISTRATION → RUNNING`、`FINISHED → CANCELLED`
  等非法状态跳转。

## 2. 测试与验收

```powershell
cd backend
.\mvnw.cmd test
```

结果：124 项测试，0 失败，5 项跳过（需 PostgreSQL 的既有集成测试）。

重点用例：

- `AuthorizationServiceTest`：角色-权限矩阵。
- `CompetitionPermissionWebTest`：普通成员写操作 403，未登录 401。
- `CompetitionControllerTest`：截止、重复、满员、退出、名额下调、名单审核。
- `CompetitionStateMachineTest`：合法与非法状态流转。
- `ScheduleServiceTest`：编排状态、重复编排、开始与结果确认前置条件。

## 3. 数据库与接口变更

- 无新增 Migration，无破坏性接口变更。
- 报名、退出、审核、编排接口路径与响应格式保持不变。
- 新增业务错误码：
  - `COMPETITION_CAPACITY_BELOW_ENTRIES`（422）
  - `COMPETITION_FULL` 也用于审核批准时名额已满（422）
