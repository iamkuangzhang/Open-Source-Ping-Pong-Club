# 积分规则 V1.0

## 1. 基础规则

- 新正式成员初始积分：1400
- 最低积分：400
- 不设置长期不比赛衰减
- 不设置新人保护、双倍积分或定级赛
- 比分差不影响积分幅度
- 积分逐场按赛前即时积分计算
- 胜者增加 N，败者减少 N
- 若败者因 400 下限无法扣满，则双方都只变化实际可扣分数

## 2. 有效积分场次

必须同时满足：
1. 赛事类型为 RATED
2. 双方均为 ACTIVE 正式成员
3. 该场结果已完成
4. `rating_eligible = true`
5. result_type 不是 BYE
6. 未开赛弃权不计积分

## 3. 积分变化表

| 双方赛前积分差 | 高分者获胜 | 低分者爆冷获胜 |
|---:|---:|---:|
| 0–12 | 8 | 8 |
| 13–37 | 7 | 10 |
| 38–62 | 6 | 13 |
| 63–87 | 5 | 16 |
| 88–112 | 4 | 20 |
| 113–137 | 3 | 25 |
| 138–162 | 2 | 30 |
| 163–187 | 2 | 35 |
| 188–212 | 1 | 40 |
| 213–237 | 1 | 45 |
| ≥238 | 0 | 50 |

## 4. 示例

1400 vs 1400：
- A 胜：A 1408，B 1392

1600 vs 1400：
- 高分者胜：1601 / 1399
- 低分者胜：1560 / 1440

## 5. 纯函数

建议实现：

```java
RatingChange calculate(int ratingA, int ratingB, Winner winner)
```

要求：
- 不访问数据库
- 不访问 Redis
- 不读取当前时间
- 输入相同必须得到相同输出

## 6. 最低分

例如：
- 败者当前 404
- 理论应 -8
- 实际只能 -4
- 胜者 +4
- 败者 -4

## 7. 结算时机

V1 在赛事最终确认时统一结算，而不是每录入一场比分立即修改账户。

结算顺序固定：
1. stage_order ASC
2. round_number ASC
3. match_number ASC

后一场使用前一场结算后的新积分。

## 8. 流水

`rating_ledger.source_type`：
- INITIAL
- MATCH
- CORRECTION

INITIAL 示例：
- rating_before = NULL
- rating_delta = 1400
- rating_after = 1400

MATCH 必须记录：
- competition_id
- match_id
- opponent_user_id
- rating_before
- rating_delta
- rating_after
- WIN / LOSS
- rule_version
- settlement_id

## 9. 幂等

必须保证 `(match_id, user_id)` 对 MATCH 类型流水唯一。

一个赛事最多一个有效 `rating_settlement`。

重复 finalize 不得重复加减积分。

## 10. 并发

结算前锁定所有涉及的 `rating_accounts`。

使用 PostgreSQL 行锁或 JPA PESSIMISTIC_WRITE。

多用户锁定顺序统一按 user_id 排序，减少死锁。
