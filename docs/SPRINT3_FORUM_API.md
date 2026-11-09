# Sprint 3 论坛接口

所有路径使用 `/api/v1` 前缀和项目统一响应。分页从 1 开始，默认每页 20 条，最大 100 条。分页数据包含 `items`、`page`、`size`、`total`、`pages`。返回的数据库字段使用下划线格式，与现有社区接口一致。

| 方法和路径 | 权限与参数 |
| --- | --- |
| GET `/forum/posts` | 公开；支持 `category`、`page`、`size`。分类按去除两端空白后的名称精确匹配。 |
| GET `/forum/posts/{postId}` | 公开；隐藏和删除的帖子返回 404。 |
| POST `/forum/posts` | 登录；请求体为 `category`、`title`、`content`，返回新帖 `id`。 |
| PUT `/forum/posts/{postId}` | 作者；请求体同发帖。 |
| DELETE `/forum/posts/{postId}` | 作者；帖子软删除。 |
| GET `/forum/posts/{postId}/comments` | 公开；支持 `page`、`size`，按发布时间正序返回。 |
| POST `/forum/posts/{postId}/comments` | 登录；请求体为 `content`，返回评论 `id`。 |
| DELETE `/forum/posts/{postId}/comments/{commentId}` | 评论作者或有论坛管理权限的用户。 |
| POST `/forum/posts/{postId}/reports` | 登录；请求体为 `reason`，返回举报 `id`。同一用户对同一帖子再次举报返回 409 和 `FORUM_REPORT_DUPLICATE`。 |
| GET `/forum/reports/{reportId}` | 举报人；查看本人提交的举报和处理状态。 |
| GET `/admin/forum/reports` | 论坛管理权限；支持 `page`、`size` 和 `status`。状态为 `PENDING`（默认）、`DISMISSED`、`HIDDEN`。 |
| PUT `/admin/forum/reports/{reportId}` | 论坛管理权限；请求体为 `action`（`HIDE` 或 `DISMISS`）和 `note`。仅待处理的举报可处理。 |

隐藏帖子同时关闭公开详情和评论访问。处理动作会保存处理人、时间、说明，并写入 `audit_logs`。论坛管理沿用现有组织管理角色范围。
