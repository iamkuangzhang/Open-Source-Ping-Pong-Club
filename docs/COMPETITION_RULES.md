# 比赛系统规则 V1.0

## 1. 比赛状态

DRAFT
→ REGISTRATION
→ ARRANGING
→ READY
→ RUNNING
→ RESULT_CONFIRMING
→ FINISHED

未结束状态可转 CANCELLED。

FINISHED 不允许返回其他状态。

## 2. 状态转换

### DRAFT → REGISTRATION
必须有：
- title
- competition_type
- format
- location
- registration_end
- start_time
- best_of
- max_participants

### REGISTRATION → ARRANGING
- 管理人员手动关闭，或报名截止时间触发
- 至少 2 名 APPROVED 参赛者

### ARRANGING → READY
- 正式赛程已确认保存

### READY → RUNNING
- 管理人员开始赛事

### RUNNING → RESULT_CONFIRMING
- 所有应完成 Match 已完成

### RESULT_CONFIRMING → FINISHED
- 管理人员在最终确认页二次确认
- RATED 赛事执行统一积分结算

## 3. 单场状态

- PENDING
- READY
- PLAYING
- COMPLETED

结果类型：
- NORMAL
- BYE
- WALKOVER
- RETIREMENT
- CANCELLED

## 4. 比分规则

支持 best_of 3 / 5 / 7。

需要胜局：
`best_of / 2 + 1`

默认每局 11 分且至少领先 2 分。

合法判断：
`max(a,b) >= 11 && abs(a-b) >= 2`

一方达到需要胜局数后立即结束，不允许继续录入无意义对局。

## 5. 单淘汰

支持 2–64 人，人数不要求为 2 的整数次幂。

`bracketSize`：
大于等于参赛人数的最小 `2^n`

`byeCount = bracketSize - participantCount`

例：
- 5 → 8，3 个 BYE
- 13 → 16，3 个 BYE
- 17 → 32，15 个 BYE

## 6. 标准种子位置

初始 `[1,2]`。

每次扩展到 newSize 时，对旧 seed 生成：
- `seed`
- `newSize + 1 - seed`

示例：
- 4：`[1,4,2,3]`
- 8：`[1,8,4,5,2,7,3,6]`

不得针对固定人数硬编码多套数组。

## 7. 随机抽签

流程：
1. APPROVED 用户按 user_id 排序
2. 使用可指定 seed 的确定性 PRNG 洗牌
3. 依次赋予 seed 1..N
4. N+1..bracketSize 为 BYE
5. 应用标准种子位置

保存 `arrangement_seed`。

相同参赛名单 + 相同 seed + 相同算法版本必须产生相同结果。

## 8. BYE

若一场：
- A = 用户
- B = NULL

自动：
- result_type = BYE
- winner = A
- status = COMPLETED
- rating_eligible = false

并自动把 A 写入 next_match 对应槽位。

BYE：
- 不计比赛场次
- 不计胜场
- 不产生积分

## 9. 晋级

每个淘汰赛 Match 保存：
- next_match_id
- winner_to_slot = A / B

比赛结束后事务内：
- 计算 winner
- 读取 next_match
- 按 winner_to_slot 写入 player_a 或 player_b

决赛 `next_match_id = NULL`。

## 10. 小组循环

使用 Circle Method。

若 n 为偶数：
- 轮数 n-1
- 每轮 n/2 场

若 n 为奇数：
- 加一个虚拟 BYE
- 涉及 BYE 的配对不生成 Match

总场数必须：
`n * (n - 1) / 2`

每两个真实选手恰好交手一次。

## 11. 小组分组

支持：
- RANDOM
- RATING_BALANCED

RANDOM：
- 确定性随机洗牌
- 轮流放入各组

RATING_BALANCED：
- 按积分排序
- 蛇形分组
- 无积分的普通用户排在有积分成员之后

## 12. 小组排名

先按总胜场 DESC。

并列时：
1. 并列人员之间胜场
2. 并列人员之间局胜率
3. 并列人员之间小分得失率
4. 若仍相同，标记 TIE_UNRESOLVED，由管理人员人工裁定

局胜率：
`赢局数 / 总局数`

小分得失率：
`总得分 / 总失分`

代码比较推荐使用交叉相乘，避免浮点精度问题。

## 13. WALKOVER 与 RETIREMENT

未开赛未到场：
- WALKOVER
- 不计积分

比赛开始后主动退出：
- RETIREMENT
- 默认按负场
- 默认可计积分

若特殊原因设置 `rating_eligible=false`：
必须填写 reason 并记录 audit_log。

## 14. 并发修改

Match 使用 `version` 乐观锁。

前端提交比分必须附带 version。

旧版本提交返回：
- HTTP 409
- `MATCH_VERSION_CONFLICT`
