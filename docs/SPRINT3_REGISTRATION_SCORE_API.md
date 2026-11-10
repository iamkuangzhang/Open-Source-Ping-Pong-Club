# Sprint 3 报名与比分接口（E6-B / E6-D）

本文档对应 Sprint 3 任务 `US-21-T1 ~ US-21-T5`（E6-B 报名）与
`US-25-T1 ~ US-25-T4`（E6-D 比分晋级）。赛事（event）在本仓库中沿用既有的
`competitions` 聚合，报名使用 `competition_entries` 扩展字段，从而不破坏赛程编排
（E6-C）对 `APPROVED` 名单的读取。

所有路径使用 `/api/v1` 前缀与项目统一响应格式；时间使用 ISO 8601；金额单位为分；
分页从 1 开始。写操作需要登录（Bearer JWT），管理操作需要对应权限。

## 1. 报名（E6-B）

| 方法和路径 | 权限 | 说明 |
| --- | --- | --- |
| POST `/events/{eventId}/registrations` | 登录 | 个人/双打报名；`eventId` 也可用 `/competitions/{eventId}/registrations` |
| POST `/registrations/{id}/pay` | 报名人 | 模拟支付，幂等，生成报名编号与二维码凭证 |
| DELETE `/registrations/{id}` | 报名人 | 开赛前 24 小时外退赛，释放名额并自动递补候补 |
| GET `/me/registrations` | 登录 | 我的赛事，返回待支付/审核中/已确认/候补/已退款全状态 |

报名请求体：

```json
{
  "type": "INDIVIDUAL",
  "partnerUserId": null,
  "groupId": null
}
```

- `type`：`INDIVIDUAL`（默认）或 `DOUBLE`。
- `partnerUserId`：双打必填，且不能是自己；搭档同样校验成员资格、信用分与重复报名。
- `groupId`：可选组别标识，当前保存到 `competition_entries.group_id`。

业务规则：

1. 仅 `REGISTRATION` 状态且在报名时间窗口内可报名。
2. `MEMBERS_ONLY` 赛事要求有效正式成员；信用分低于 60 返回 422。
3. 同一赛事同一用户只能有一条有效报名；双打搭档也不能重复报名。
4. 兼项上限为 2 项（跨未结束赛事统计）。
5. 名额通过 `update competitions set filled_slots=filled_slots+1 where filled_slots<max_participants`
   原子扣减，满额后进入候补并分配 `waitlist_no`。
6. 支付成功后生成唯一 `credential_no` 与 `qr_token`；重复支付返回原凭证（幂等）。
7. 退赛需距开赛超过 24 小时；释放名额后按 `waitlist_no` 顺序把首个候补转为
   `PENDING_PAYMENT`（需审核赛事为 `PENDING`），并写入站内信。
8. 已支付退赛的 `refund_status` 为 `REFUNDED`，模拟退款可在我的赛事中查询。

错误码：

| HTTP | code | 场景 |
| --- | --- | --- |
| 404 | `COMPETITION_NOT_FOUND` / `REG_NOT_FOUND` | 赛事或报名不存在 |
| 409 | `COMPETITION_REGISTRATION_CLOSED` | 未开放/超出报名时间 |
| 409 | `REG_DUPLICATE` | 重复报名或搭档已报名 |
| 409 | `REG_WAITLISTED` | 候补未转正时支付 |
| 409 | `REG_INVALID_STATE` | 当前状态不允许支付/退赛 |
| 422 | `REG_NOT_ELIGIBLE` | 账号状态、成员资格或双打搭档不合法 |
| 422 | `REG_CREDIT_TOO_LOW` | 信用分低于 60 |
| 422 | `REGISTRATION_LIMIT_EXCEEDED` | 兼项超过 2 项 |
| 422 | `REG_WITHDRAW_TOO_LATE` | 开赛前 24 小时内退赛 |
| 403 | `PERMISSION_DENIED` | 操作他人报名 |

## 2. 比分（E6-D）

| 方法和路径 | 权限 | 说明 |
| --- | --- | --- |
| POST `/matches/{id}/scores` | `MATCH_SCORE_EDIT` | 按局录入比分并结算；支持 `Idempotency-Key` |
| PUT `/matches/{id}/scores` | `MATCH_SCORE_EDIT` | 30 分钟内修改；超时转为待审批申请 |
| POST `/admin/matches/{id}/walkover` | `MATCH_SCORE_EDIT` | 弃权/未到场，不计积分 |
| POST `/admin/matches/{id}/retirement` | `MATCH_SCORE_EDIT` | 开赛后退出，按负场计积分 |
| POST `/admin/matches/{id}/score-changes/{changeId}/approve` | `COMPETITION_MANAGE` | 审批超时修改 |
| POST `/admin/matches/{id}/score-changes/{changeId}/reject` | `COMPETITION_MANAGE` | 驳回超时修改 |
| GET `/events/{id}/bracket` | 公开 | 对阵图、比分、晋级状态与小组排名 |

请求体：

```json
{
  "version": 3,
  "games": [
    { "a": 11, "b": 8 },
    { "a": 11, "b": 9 }
  ],
  "reason": "可选，超时修改时必填"
}
```

计分模板：

| 运动 | 规则 |
| --- | --- |
| `TABLE_TENNIS` | 每局 11 分且净胜 2 分；`best_of` 支持 3/5/7 |
| `BADMINTON` | 每局 21 分且净胜 2 分，30 分封顶；3 局 2 胜 |
| `VOLLEYBALL` | 前四局 25 分且净胜 2 分，决胜局 15 分；5 局 3 胜 |
| `BASKETBALL` / `FOOTBALL` | 单场总分，高者胜，平分不合法 |

结算规则：

1. 合法比分保存到 `match_games`，写入 `match_score_logs` 与 `audit_logs`，胜者按
   `next_match_id` / `winner_to_slot` 自动晋级。
2. 已结算场次重复提交返回 409；携带相同 `Idempotency-Key` 的重复请求直接返回原结果。
3. 比分提交后 30 分钟内允许修改并保留前后快照。
4. 超过 30 分钟需填写原因并生成待审批申请；管理员审批通过后按最新版本应用，
   版本不一致返回 409。
5. 修改导致胜负变化时，若下一场已开始或席位已被其他选手占用，返回 409
   `MATCH_ADVANCEMENT_LOCKED`。
6. 弃权（`WALKOVER`）不计积分；退赛（`RETIREMENT`）默认按负场计积分。
7. `GET /events/{id}/bracket` 返回每个场次的 `games` 以及 `group_id` 非空场次的小组排名
   （胜场、负场、局分与净胜局）。

错误码：

| HTTP | code | 场景 |
| --- | --- | --- |
| 404 | `MATCH_NOT_FOUND` | 场次不存在 |
| 409 | `MATCH_INVALID_STATE` | 状态不允许录入/修改 |
| 409 | `MATCH_ALREADY_SETTLED` | 场次已结算 |
| 409 | `MATCH_VERSION_CONFLICT` | 乐观锁版本不一致 |
| 409 | `MATCH_SCORE_CHANGE_PENDING` | 已有待审批的修改 |
| 409 | `MATCH_ADVANCEMENT_LOCKED` | 下一场已锁定，无法改判 |
| 422 | `MATCH_INVALID_SCORE` | 比分不符合计分模板 |
| 422 | `MATCH_SCORE_REQUIRES_REASON` | 超时修改未填写原因 |

## 3. 数据库变更

- `V9__sprint3_registration.sql`
  - `competitions`：新增 `filled_slots`、`fee_cents`、`sport`，并回填已占名额。
  - `competition_entries`：新增报名类型、组别、搭档、候补号、凭证、支付/退款状态与版本。
  - 新增 `registration_members`、`registration_payments`、`credit_accounts`。
- `V10__sprint3_scoring.sql`
  - `matches`：新增 `score_submitted_at`、`score_edit_deadline`。
  - 新增 `match_score_logs`、`match_score_change_requests`。

## 4. 测试

- `ScoreTemplateTest`：乒乓球净胜 2 分、羽毛球 30 分封顶、排球决胜局、篮足总分与非法局数。
- `RegistrationServiceTest`：满额候补、信用分拦截、重复报名、24 小时退赛限制、支付幂等、候补递补。
- `ScoreSubmissionServiceTest`：非法比分、正常结算与晋级、版本冲突、超时修改转审批、弃权晋级。
- 既有 `MatchAdvancementIntegrationTest` 在配置 PostgreSQL 后可继续验证真实晋级链路。
