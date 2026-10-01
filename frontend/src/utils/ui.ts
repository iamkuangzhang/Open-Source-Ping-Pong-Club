export function go(path: string, params?: Record<string, string | number | undefined>) {
  const query = params
    ? Object.entries(params).filter(([, value]) => value !== undefined).map(([key, value]) => `${key}=${encodeURIComponent(String(value))}`).join('&')
    : ''
  uni.navigateTo({ url: query ? `${path}?${query}` : path })
}

export function switchRoot(path: string) {
  uni.reLaunch({ url: path })
}

export function back() {
  const pages = getCurrentPages()
  if (pages.length > 1) uni.navigateBack()
  else switchRoot('/pages/competition/index')
}

export function formatDate(value?: string, includeTime = true) {
  if (!value) return '时间待定'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '时间待定'
  const month = date.getMonth() + 1
  const day = date.getDate()
  const time = `${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
  return includeTime ? `${month}月${day}日 ${time}` : `${month}月${day}日`
}

export function competitionType(value?: string) {
  return ({ RATED: '积分赛', DAILY: '日常赛', EXAM: '考核赛' } as Record<string, string>)[value || ''] || '赛事'
}

export function competitionStatus(value?: string) {
  return ({ DRAFT: '草稿', REGISTRATION: '报名中', ARRANGING: '编排中', READY: '待开赛', RUNNING: '进行中', PLAYING: '比赛中', RESULT_CONFIRMING: '结果确认', FINISHED: '已结束', COMPLETED: '已完成', CANCELLED: '已取消', ACTIVE: '有效', APPROVED: '已通过', REJECTED: '已拒绝', PENDING: '待处理', PUBLISHED: '已发布', ON_SALE: '在售', SOLD: '已售' } as Record<string, string>)[value || ''] || value || '未知'
}

export function userName(item?: Record<string, unknown> | null) {
  return String(item?.nickname || item?.real_name || item?.realName || '球友')
}
