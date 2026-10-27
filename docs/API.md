# API 设计 V1.0

统一前缀：`/api/v1`

## 1. 返回格式

成功：
```json
{
  "success": true,
  "code": "OK",
  "message": "success",
  "data": {}
}
```

失败：
```json
{
  "success": false,
  "code": "MATCH_VERSION_CONFLICT",
  "message": "比赛数据已被其他管理人员修改",
  "data": null
}
```

## 2. HTTP Status

- 400 参数格式错误
- 401 未登录或 Access Token 无效
- 403 已登录但无权限
- 404 资源不存在
- 409 状态或版本冲突
- 422 业务规则不满足
- 500 未处理服务端异常

## 3. Authentication

- POST `/auth/register`
- POST `/auth/login`
- POST `/auth/refresh`
- POST `/auth/logout`

Access Token：15 分钟  
Refresh Token：30 天

## 4. 用户

- GET `/users/me`
- PUT `/users/me`
- GET `/users/{userId}/public`

公开资料不得返回手机号、微信号、QQ、完整学号等敏感信息。

## 5. 成员

- POST `/membership/applications`
- GET `/membership/applications/me`
- POST `/membership/me/qr`
- GET `/public/member-verifications/{token}`

管理：
- GET `/admin/membership/applications`
- GET `/admin/membership/applications/{id}`
- POST `/admin/membership/applications/{id}/approve`
- POST `/admin/membership/applications/{id}/reject`

## 6. 组织职务

- GET `/organization/roles`
- GET `/organization/me/roles`
- GET `/members/{userId}/roles`
- GET `/admin/members`
- PUT `/admin/members/{userId}/roles`，Body：`{ "roleCode": "ADMIN", "active": true }`

组织管理接口仅 PRESIDENT / ADVISOR。角色启用或撤销写入审计日志。
首次部署时，部署运维须通过受控流程为可信账号初始化 PRESIDENT 或 ADVISOR；之后由这两类职务任命管理员，业务 API 不提供匿名自助升权入口。

## 6.1 运动类型

- GET `/sports`：列出有效运动类型，公开可读
- POST `/admin/sports`：添加运动类型，Body：`{ "code": "VOLLEYBALL", "name": "排球" }`
- PUT `/admin/sports/{id}`：编辑运动类型名称与排序，Body：`{ "name": "排球", "sortOrder": 5 }`
- PUT `/admin/sports/{id}/status`：启用或停用运动类型，Body：`{ "active": false }`

新增类型编码必须以英文字母开头，仅包含英文字母、数字和下划线，最多 40 字符；名称最多 40 字符。创建需要管理权限并写入审计日志。编辑与停用/启用需要管理权限，均刷新 `updated_at` 并写入审计日志；目标不存在返回 `SPORT_NOT_FOUND`。停用后不再出现在有效运动类型列表中，历史赛事不受影响。

## 7. 比赛

公开：
- GET `/competitions`
- GET `/competitions/{id}`
- GET `/competitions/{id}/schedule`
- GET `/competitions/{id}/bracket`
- GET `/competitions/{id}/groups`

管理：
- POST `/admin/competitions`
- PUT `/admin/competitions/{id}`
- POST `/admin/competitions/{id}/publish`
- POST `/admin/competitions/{id}/close-registration`
- POST `/admin/competitions/{id}/arrange`
- POST `/admin/competitions/{id}/start`
- POST `/admin/competitions/{id}/enter-result-confirming`
- POST `/admin/competitions/{id}/finalize`
- POST `/admin/competitions/{id}/cancel`

创建/编辑赛事可传入 `sportCode`，缺省时兼容为 `PING_PONG`。只有乒乓球 `RATED` 赛事可结算共享积分；其他球类暂不支持积分赛事与专属计分/赛制规则。

## 8. 比赛报名

- POST `/competitions/{id}/entries`
- DELETE `/competitions/{id}/entries/me`
- GET `/admin/competitions/{id}/entries`
- POST `/admin/competition-entries/{entryId}/approve`
- POST `/admin/competition-entries/{entryId}/reject`

## 9. 单场比赛

- GET `/matches/{id}`
- POST `/admin/matches/{id}/start`
- PUT `/admin/matches/{id}/score`
- POST `/admin/matches/{id}/walkover`
- POST `/admin/matches/{id}/retirement`

比分更新必须提交 `version`。

版本冲突：
- HTTP 409
- `MATCH_VERSION_CONFLICT`

## 10. 积分

- GET `/ratings/leaderboard`
- GET `/ratings/me`
- GET `/ratings/me/history`
- GET `/ratings/users/{userId}`
- GET `/ratings/users/{userId}/history`

## 11. 公告

- GET `/announcements`
- GET `/announcements/{id}`
- POST `/admin/announcements`
- PUT `/admin/announcements/{id}`
- DELETE `/admin/announcements/{id}`

## 12. 约球

- GET `/play-posts`
- GET `/play-posts/{id}`
- POST `/play-posts`
- PUT `/play-posts/{id}`
- DELETE `/play-posts/{id}`
- POST `/play-posts/{id}/participants`
- DELETE `/play-posts/{id}/participants/me`

## 13. 二手信息

- GET `/market-posts`
- GET `/market-posts/{id}`
- POST `/market-posts`
- PUT `/market-posts/{id}`
- DELETE `/market-posts/{id}`
- POST `/market-posts/{id}/mark-sold`

V1 不存在支付、订单、退款 API。

## 14. 通知

- GET `/notifications`
- POST `/notifications/{id}/read`
- POST `/notifications/read-all`

## 15. 图片

- POST `/files/images`

限制：
- jpeg / png / webp
- 单图建议不超过 10MB
- 服务端验证 MIME、文件魔数和大小

## 16. 分页

请求：
- page
- size

约束：
`1 <= size <= 100`

返回：
```json
{
  "items": [],
  "page": 1,
  "size": 20,
  "total": 123,
  "pages": 7
}
```

## 17. 错误码

至少：
- AUTH_INVALID_CREDENTIALS
- AUTH_TOKEN_EXPIRED
- AUTH_ACCOUNT_DISABLED
- USER_NOT_FOUND
- MEMBERSHIP_ALREADY_ACTIVE
- MEMBERSHIP_APPLICATION_EXISTS
- MEMBERSHIP_APPLICATION_NOT_FOUND
- MEMBERSHIP_APPLICATION_ALREADY_REVIEWED
- PERMISSION_DENIED
- SPORT_ALREADY_EXISTS
- SPORT_NOT_FOUND
- SPORT_RATING_UNSUPPORTED
- MEMBER_NOT_FOUND
- ROLE_NOT_FOUND
- COMPETITION_NOT_FOUND
- COMPETITION_INVALID_STATE
- COMPETITION_REGISTRATION_CLOSED
- COMPETITION_FULL
- COMPETITION_ALREADY_REGISTERED
- COMPETITION_INSUFFICIENT_PARTICIPANTS
- COMPETITION_ALREADY_FINALIZED
- ENTRY_NOT_FOUND
- MATCH_NOT_FOUND
- MATCH_INVALID_STATE
- MATCH_INVALID_SCORE
- MATCH_VERSION_CONFLICT
- RATING_ACCOUNT_NOT_FOUND
- RATING_ALREADY_SETTLED
- PLAY_POST_FULL
- PLAY_POST_NOT_OPEN
- PLAY_ALREADY_JOINED
- MARKET_POST_NOT_FOUND
- MARKET_POST_NOT_OWNER
- QR_TOKEN_INVALID
- QR_TOKEN_EXPIRED
- VALIDATION_ERROR
- INTERNAL_ERROR

前端业务逻辑判断 `code`，不得依赖中文 message。
