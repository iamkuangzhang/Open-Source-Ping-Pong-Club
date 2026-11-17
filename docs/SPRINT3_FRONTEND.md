# Sprint 3 移动端联调交付说明

本轮交付对应 Sprint 3 中周佳睿负责的四项任务：论坛列表、详情和发帖页接真实 API（主交付）、赛事列表与详情及报名入口联调、我的报名状态与操作反馈、淘汰树与小组赛程及场次页联调。所有页面均直接调用后端真实接口，不使用本地模拟数据。

## 一、论坛三页接真实 API

后端契约以 `docs/SPRINT3_FORUM_API.md` 为准，本轮只新增移动端页面与数据层，不改动既有接口语义。

| 页面 | 路径 | 对接接口 |
| --- | --- | --- |
| 帖子列表 | `pages/forum/index` | GET `/forum/posts`（`category`、`page`、`size`） |
| 帖子详情 | `pages/forum/detail` | GET `/forum/posts/{postId}`、GET/POST `/forum/posts/{postId}/comments`、DELETE 评论、POST 举报 |
| 发帖与编辑 | `pages/forum/create` | POST `/forum/posts`、PUT `/forum/posts/{postId}` |

数据层集中在 `frontend/src/stores/forum.ts`，页面只负责渲染与交互。列表状态划分为加载、成功、空、错误四种，错误态保留重试入口；详情页在帖子被隐藏或删除时按 404 提示“帖子可能已被删除或隐藏”，不展示任何残留内容。

板块取值由前端固定为赛事交流、技术讨论、约球组队、器材交换、公告建议五项，与后端“去除两端空白后精确匹配”的规则保持一致。分页从 1 开始，每页 10 条，列表底部提供“加载更多”，全部加载后显示“已经看到全部帖子”。

作者权限由后端判定，前端仅对作者本人展示编辑与删除入口；举报同一帖子重复提交时，后端返回 409 与 `FORUM_REPORT_DUPLICATE`，页面提示“你已经举报过这个帖子，请等待处理结果”。未登录用户点击发帖、举报或评论时统一跳转登录页。

## 二、报名状态以服务端为准

原有实现通过本地缓存 `joinedCompetitionIds` 判断是否已报名，换设备或清理缓存后状态会失真。本轮新增只读接口 GET `/competitions/{id}/entries/me`，返回当前登录用户的报名记录，未报名时 `data` 为 `null`，未登录访问同样返回 `null`，不泄露他人数据。

前端在 `loadCompetitionDetail` 中并行拉取赛事详情、赛程、管理端名单与本人报名记录，`competitive/detail` 页面据此展示“报名待审核”“报名已通过”“报名未通过”等状态，并在报名、退出后立即重新拉取，本地缓存仅作为列表卡片的渲染辅助，不再作为判断依据。

报名与退出的失败反馈按错误码区分：重复报名、人数已满、报名时间已过、报名记录不存在均给出具体文案，不再统一显示“当前状态已发生变化”。

## 三、赛程页面

淘汰树与小组赛页面补齐加载态、空态与错误重试，长赛程提示可左右滑动查看；管理入口携带 `manage=1` 时，若当前账号没有赛事管理权限，页面直接提示只读，避免进入录分页后才被拒绝。场次详情页在赛事重新编排导致场次失效时给出可重试的错误提示。

## 四、验证方式

后端单元测试覆盖新增接口：

```powershell
backend\mvnw.cmd -Dtest=CompetitionControllerTest test
```

前端静态检查与构建：

```powershell
cd frontend
npx tsc --noEmit -p tsconfig.json
npm run build:h5
```

需要说明的是，本仓库前端的 `build:h5` 会执行类型检查之外的完整编译，论坛页面的接口联调仍建议在本地启动后端后按 `docs/SPRINT3_FORUM_API.md` 逐条手工验证。
