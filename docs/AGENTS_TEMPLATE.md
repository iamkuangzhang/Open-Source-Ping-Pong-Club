# AGENTS.md 模板

请将本文件内容复制到仓库根目录 `AGENTS.md`。

## 项目规则

1. 开始任何实现前先阅读 `docs/README.md` 和 `docs/IMPLEMENTATION_SPEC.md`。
2. 需要理解具体业务时继续阅读对应：
   - `PRODUCT_SPEC_V1.md`
   - `DATABASE.md`
   - `API.md`
   - `PERMISSIONS.md`
   - `COMPETITION_RULES.md`
   - `RATING_RULE.md`
   - `UI_PAGES.md`
   - `TEST_PLAN.md`
3. 一次只完成当前明确要求的 Phase。
4. 不得擅自修改既定业务规则。
5. 数据库使用 Flyway。
6. 已执行 Migration 不得重写，只能新增 Migration。
7. 核心逻辑不得使用 Mock/TODO 伪装完成。
8. 比赛、积分、权限、审核必须有自动化测试。
9. 完成 Phase 必须运行测试并对照 Gate。
10. Gate 未通过不得进入下一 Phase。
11. 遇到规格冲突必须明确报告，不得静默修改需求。
12. 所有产品展示统一使用“开源乓协”。
13. 系统不得暗示学校或相关学生组织官方授权。
