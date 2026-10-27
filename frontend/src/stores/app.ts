import { defineStore } from 'pinia'
import { api, apiConfig, type ApiError } from '../services/api'

export type User = {
  id: string
  account: string
  status: string
  nickname: string
  realName?: string
  avatarUrl?: string
  college?: string
  major?: string
  grade?: string
  bio?: string
}

export type Competition = {
  id: string
  title: string
  description?: string
  sport_code?: string
  sport_name?: string
  competition_type?: 'DAILY' | 'EXAM' | 'RATED'
  type?: 'DAILY' | 'EXAM' | 'RATED'
  format?: 'SINGLE_ELIMINATION' | 'ROUND_ROBIN'
  status: string
  location?: string
  registration_end?: string
  registration_start?: string
  start_time?: string
  created_at?: string
  max_participants?: number
  best_of?: number
}

export type Sport = {
  id: string
  code: string
  name: string
}

export type RoleCode = 'MEMBER' | 'ADMIN' | 'VICE_MINISTER' | 'MINISTER' | 'VICE_PRESIDENT' | 'PRESIDENT' | 'ADVISOR'

export type OrganizationMember = {
  userId: string
  nickname?: string
  memberNumber: string
  roles: RoleCode[]
}

export type CompetitionEntry = {
  id: string
  user_id: string
  status: string
  nickname?: string
  real_name?: string
  seed_number?: number
}

export type Match = {
  id: string
  round_number: number
  match_number: number
  group_id?: string
  status: 'PENDING' | 'READY' | 'PLAYING' | 'COMPLETED'
  player_a_user_id?: string
  player_b_user_id?: string
  winner_user_id?: string
  result_type?: string
  player_a_games?: number
  player_b_games?: number
  best_of: number
  version: number
}

export type RatingAccount = {
  user_id: string
  current_rating: number
  highest_rating: number
  rated_matches: number
  wins: number
  losses: number
}

export type RatingLedger = {
  id: string
  reason: string
  before_rating?: number
  after_rating?: number
  delta?: number
  rating_before?: number
  rating_after?: number
  rating_delta?: number
  created_at: string
}

export type Post = {
  id: string
  title: string
  content: string
  status?: string
  visibility?: string
  participant_count?: number
  max_participants?: number
  created_at?: string
  event_time?: string
  location?: string
  contact?: string
  price?: string
  marketplace_condition?: string
  delivery_method?: string
  author_user_id?: string
  joined?: boolean
  image_url?: string
}

export type MembershipApplication = {
  id: string
  status: string
  realName?: string
  real_name?: string
  nickname?: string
  account?: string
}

export type SectionStatus = 'idle' | 'loading' | 'success' | 'empty' | 'error'

function messageOf(error: unknown) {
  const apiError = error as ApiError
  if (apiError.statusCode === 401) return '登录状态已过期，请重新登录'
  if (apiError.statusCode === 403) return '当前账号没有执行该操作的权限'
  if (apiError.statusCode === 409) return '当前状态已发生变化，请刷新后重试'
  if (apiError.statusCode === 422) return '提交内容不符合业务规则，请检查后重试'
  if (apiError.statusCode && apiError.statusCode >= 500) return '服务暂时不可用，请稍后重试'
  return '暂时无法完成操作，请稍后重试'
}

function loadToken(name: string) {
  try {
    return String(uni.getStorageSync(name) || '')
  } catch {
    return ''
  }
}

function loadStringList(name: string) {
  try {
    const value = uni.getStorageSync(name)
    return Array.isArray(value) ? value.map(String) : []
  } catch {
    return []
  }
}

function nowIso(offsetMs = 0) {
  return new Date(Date.now() + offsetMs).toISOString()
}

function compactUser(id?: string) {
  return id ? id.slice(0, 8) : '待定'
}

export const useAppStore = defineStore('app', {
  state: () => ({
    accessToken: loadToken('accessToken'),
    refreshToken: loadToken('refreshToken'),
    me: null as User | null,
    rating: null as RatingAccount | null,
    ratingHistory: [] as RatingLedger[],
    application: null as MembershipApplication | null,
    adminApplications: [] as MembershipApplication[],
    myRoles: [] as RoleCode[],
    organizationMembers: [] as OrganizationMember[],
    sports: [] as Sport[],
    competitions: [] as Competition[],
    joinedCompetitionIds: loadStringList('joinedCompetitionIds'),
    selectedCompetitionId: '',
    selectedCompetition: null as Competition | null,
    entries: [] as CompetitionEntry[],
    schedule: [] as Match[],
    leaderboard: [] as RatingAccount[],
    announcements: [] as Post[],
    playPosts: [] as Post[],
    marketPosts: [] as Post[],
    notifications: [] as Array<{ id: string; title: string; content: string; read_at?: string }>,
    qr: null as { token: string; expiresIn: number } | null,
    adminAccess: false,
    sportsStatus: 'idle' as SectionStatus,
    organizationMembersStatus: 'idle' as SectionStatus,
    competitionsStatus: 'idle' as SectionStatus,
    leaderboardStatus: 'idle' as SectionStatus,
    announcementsStatus: 'idle' as SectionStatus,
    playPostsStatus: 'idle' as SectionStatus,
    marketPostsStatus: 'idle' as SectionStatus,
    profileStatus: 'idle' as SectionStatus,
    ratingSummaryStatus: 'idle' as SectionStatus,
    health: 'idle' as 'idle' | 'loading' | 'ready' | 'error',
    loading: false,
    error: '',
    success: ''
  }),
  getters: {
    signedIn: (state) => Boolean(state.accessToken),
    isMember: (state) => Boolean(state.rating),
    canManage: (state) => state.adminAccess,
    canManageRoles: (state) => state.myRoles.includes('PRESIDENT') || state.myRoles.includes('ADVISOR'),
    ratingText: (state) => state.rating ? String(state.rating.current_rating) : '—',
    winRate: (state) => {
      if (!state.rating || state.rating.rated_matches === 0) return '—'
      return `${Math.round((state.rating.wins / state.rating.rated_matches) * 100)}%`
    },
    selectedStatus: (state) => state.selectedCompetition?.status || '未选择',
    completedMatches: (state) => state.schedule.filter(item => item.status === 'COMPLETED').length,
    playableMatches: (state) => state.schedule.filter(item => item.status === 'READY' || item.status === 'PLAYING'),
    userLabel: () => compactUser
  },
  actions: {
    initApi() {
      apiConfig.accessToken = this.accessToken
      apiConfig.refreshToken = this.refreshToken
      apiConfig.onTokenChange = (access, refresh) => this.setTokens(access, refresh)
      apiConfig.onUnauthorized = () => this.logoutLocal()
    },
    setTokens(accessToken: string, refreshToken: string) {
      this.accessToken = accessToken
      this.refreshToken = refreshToken
      apiConfig.accessToken = accessToken
      apiConfig.refreshToken = refreshToken
      uni.setStorageSync('accessToken', accessToken)
      uni.setStorageSync('refreshToken', refreshToken)
    },
    logoutLocal() {
      this.accessToken = ''
      this.refreshToken = ''
      this.me = null
      this.rating = null
      this.ratingHistory = []
      this.application = null
      this.adminApplications = []
      this.myRoles = []
      this.organizationMembers = []
      this.adminAccess = false
      this.qr = null
      this.profileStatus = 'idle'
      this.ratingSummaryStatus = 'idle'
      apiConfig.accessToken = ''
      apiConfig.refreshToken = ''
      uni.removeStorageSync('accessToken')
      uni.removeStorageSync('refreshToken')
    },
    async run<T>(task: () => Promise<T>, success = '') {
      this.loading = true
      this.error = ''
      this.success = ''
      try {
        const result = await task()
        if (success) this.success = success
        return result
      } catch (error) {
        this.error = messageOf(error)
        throw error
      } finally {
        this.loading = false
      }
    },
    async bootstrap() {
      this.initApi()
      await Promise.allSettled([
        this.loadPublic(),
        this.accessToken ? this.loadPrivate() : Promise.resolve()
      ])
    },
    async loadPublic() {
      this.health = 'loading'
      const results = await Promise.allSettled([
        this.loadCompetitions(),
        this.loadLeaderboard(),
        this.loadAnnouncements(),
        this.loadPlayPosts(),
        this.loadMarketPosts()
      ])
      this.health = results.every(item => item.status === 'rejected') ? 'error' : 'ready'
    },
    async loadCompetitions() {
      this.competitionsStatus = 'loading'
      try {
        const competitions = await api.get<Competition[]>('/competitions')
        this.competitions = competitions || []
        if (this.competitions.length) {
          const details = await Promise.all(this.competitions.map(item => api.get<Competition>(`/competitions/${item.id}`).catch(() => item)))
          this.competitions = details.map((detail, index) => ({ ...this.competitions[index], ...detail }))
        }
        this.competitionsStatus = this.competitions.length ? 'success' : 'empty'
        if (!this.selectedCompetitionId && this.competitions[0]) this.selectedCompetitionId = this.competitions[0].id
      } catch (error) {
        this.competitionsStatus = 'error'
        console.error('[competitions] load failed', error)
        throw error
      }
    },
    async loadLeaderboard() {
      this.leaderboardStatus = 'loading'
      try {
        this.leaderboard = await api.get<Array<RatingAccount & { nickname?: string }>>('/ratings/leaderboard') || []
        this.leaderboardStatus = this.leaderboard.length ? 'success' : 'empty'
      } catch (error) {
        this.leaderboardStatus = 'error'
        console.error('[leaderboard] load failed', error)
        throw error
      }
    },
    async loadAnnouncements() {
      this.announcementsStatus = 'loading'
      try {
        this.announcements = await api.get<Post[]>('/announcements') || []
        this.announcementsStatus = this.announcements.length ? 'success' : 'empty'
      } catch (error) {
        this.announcementsStatus = 'error'
        console.error('[announcements] load failed', error)
        throw error
      }
    },
    async loadPlayPosts() {
      this.playPostsStatus = 'loading'
      try {
        this.playPosts = await api.get<Post[]>('/play-posts') || []
        this.playPostsStatus = this.playPosts.length ? 'success' : 'empty'
      } catch (error) {
        this.playPostsStatus = 'error'
        console.error('[play-posts] load failed', error)
        throw error
      }
    },
    async loadMarketPosts() {
      this.marketPostsStatus = 'loading'
      try {
        this.marketPosts = await api.get<Post[]>('/market-posts') || []
        this.marketPostsStatus = this.marketPosts.length ? 'success' : 'empty'
      } catch (error) {
        this.marketPostsStatus = 'error'
        console.error('[market-posts] load failed', error)
        throw error
      }
    },
    async loadPrivate() {
      this.profileStatus = 'loading'
      this.ratingSummaryStatus = 'loading'
      try {
        this.me = await api.get<User>('/users/me')
        this.profileStatus = 'success'
      } catch (error) {
        this.profileStatus = 'error'
        console.error('[profile] load failed', error)
        throw error
      }
      const [ratingResult, historyResult, notificationsResult, applicationResult] = await Promise.allSettled([
        api.get<RatingAccount | null>('/ratings/me'),
        api.get<RatingLedger[]>('/ratings/me/history'),
        api.get<Array<{ id: string; title: string; content: string; read_at?: string }>>('/notifications'),
        api.get<MembershipApplication>('/membership/applications/me')
      ])
      if (ratingResult.status === 'fulfilled') {
        this.rating = ratingResult.value
        this.ratingSummaryStatus = this.rating ? 'success' : 'empty'
      } else {
        const apiError = ratingResult.reason as ApiError
        this.rating = null
        this.ratingSummaryStatus = [403, 404].includes(apiError.statusCode || 0) ? 'empty' : 'error'
        if (this.ratingSummaryStatus === 'error') console.error('[rating-summary] load failed', ratingResult.reason)
      }
      this.ratingHistory = historyResult.status === 'fulfilled' ? historyResult.value || [] : []
      this.notifications = notificationsResult.status === 'fulfilled' ? notificationsResult.value || [] : []
      this.application = applicationResult.status === 'fulfilled' ? applicationResult.value : null
      try {
        this.myRoles = await api.get<RoleCode[]>('/organization/me/roles')
      } catch (error) {
        this.myRoles = []
        console.error('[organization-roles] load failed', error)
      }
      this.adminAccess = this.myRoles.some(role =>
        ['ADMIN', 'VICE_MINISTER', 'MINISTER', 'VICE_PRESIDENT', 'PRESIDENT', 'ADVISOR'].includes(role)
      )
      if (this.adminAccess) {
        try {
          this.adminApplications = await api.get<MembershipApplication[]>('/admin/membership/applications')
        } catch (error) {
          this.adminApplications = []
          console.error('[admin-applications] load failed', error)
        }
      } else {
        this.adminApplications = []
      }
    },
    async register(account: string, password: string, nickname: string) {
      await this.run(async () => {
        const tokens = await api.post<{ accessToken: string; refreshToken: string; expiresIn: number }>('/auth/register', { account, password, nickname })
        this.setTokens(tokens.accessToken, tokens.refreshToken)
        await this.loadPrivate()
      }, '注册成功，已登录')
    },
    async login(account: string, password: string) {
      await this.run(async () => {
        const tokens = await api.post<{ accessToken: string; refreshToken: string; expiresIn: number }>('/auth/login', { account, password })
        this.setTokens(tokens.accessToken, tokens.refreshToken)
        await this.loadPrivate()
      }, '登录成功')
    },
    async logout() {
      const refreshToken = this.refreshToken
      this.logoutLocal()
      if (refreshToken) await api.post('/auth/logout', { refreshToken }).catch(() => undefined)
      this.success = '已退出登录'
    },
    async updateProfile(payload: { nickname: string; realName?: string; avatarUrl?: string; college?: string; major?: string; grade?: string; bio?: string }) {
      await this.run(async () => {
        this.me = await api.put<User>('/users/me', payload)
      }, '资料已保存')
    },
    async applyMembership(payload?: { realName: string; studentNumber: string; college: string; major: string; grade: string; phone: string; reason?: string }) {
      await this.run(async () => {
        this.application = await api.post('/membership/applications', payload || {
          realName: this.me?.realName || this.me?.nickname || '开源乓协用户',
          studentNumber: `LOCAL${Date.now().toString().slice(-6)}`,
          college: '开源乓协',
          major: '乒乓球爱好者',
          grade: '2026',
          phone: '13000000000',
          reason: '希望加入开源乓协参与活动'
        })
        await this.loadPrivate()
      }, '已提交加入申请')
    },
    async reviewApplication(id: string, approve = true) {
      await this.run(async () => {
        await api.post(`/admin/membership/applications/${id}/${approve ? 'approve' : 'reject'}`, { comment: '移动端审核' })
        await this.loadPrivate()
      }, approve ? '已审核通过' : '已拒绝申请')
    },
    async generateQr() {
      await this.run(async () => {
        this.qr = await api.post('/membership/me/qr')
      }, '成员二维码已生成，60 秒内有效')
    },
    async createCompetition(format: 'SINGLE_ELIMINATION' | 'ROUND_ROBIN' = 'SINGLE_ELIMINATION', payload?: Record<string, unknown>) {
      await this.run(async () => {
        const created = await api.post<{ id: string }>('/admin/competitions', payload || {
          title: `开源乓协${format === 'ROUND_ROBIN' ? '循环赛' : '积分赛'} ${new Date().getMonth() + 1}/${new Date().getDate()}`,
          description: '移动端创建的真实赛事',
          type: format === 'ROUND_ROBIN' ? 'DAILY' : 'RATED',
          format,
          scope: 'PUBLIC',
          review: false,
          maxParticipants: format === 'ROUND_ROBIN' ? 6 : 8,
          bestOf: 3,
          location: '开源乓协球台',
          registrationStart: nowIso(-5 * 60_000),
          registrationEnd: nowIso(60 * 60_000),
          startTime: nowIso(2 * 60 * 60_000)
        })
        await this.loadCompetitions().catch(() => undefined)
        this.selectedCompetitionId = created.id
        await this.loadCompetitionDetail(created.id)
      }, '比赛已创建')
    },
    async updateCompetition(id: string, payload: Record<string, unknown>) {
      await this.run(async () => {
        await api.put(`/admin/competitions/${id}`, payload)
        await this.loadCompetitionDetail(id)
        await this.loadCompetitions().catch(() => undefined)
      }, '赛事资料已保存')
    },
    async loadSports() {
      this.sportsStatus = 'loading'
      try {
        this.sports = await api.get<Sport[]>('/sports') || []
        this.sportsStatus = this.sports.length ? 'success' : 'empty'
      } catch (error) {
        this.sportsStatus = 'error'
        console.error('[sports] load failed', error)
        throw error
      }
    },
    async createSport(payload: { code: string; name: string }) {
      await this.run(async () => {
        await api.post('/admin/sports', payload)
        await this.loadSports()
      }, '运动类型已添加')
    },
    async loadOrganizationMembers() {
      this.organizationMembersStatus = 'loading'
      try {
        this.organizationMembers = await api.get<OrganizationMember[]>('/admin/members') || []
        this.organizationMembersStatus = this.organizationMembers.length ? 'success' : 'empty'
      } catch (error) {
        this.organizationMembersStatus = 'error'
        console.error('[organization-members] load failed', error)
        throw error
      }
    },
    async setMemberRole(userId: string, roleCode: RoleCode, active: boolean) {
      await this.run(async () => {
        await api.put(`/admin/members/${userId}/roles`, { roleCode, active })
        await this.loadOrganizationMembers()
      }, active ? '职务已授予' : '职务已撤销')
    },
    async reviewCompetitionEntry(entryId: string, approve: boolean) {
      await this.run(async () => {
        await api.post(`/admin/competition-entries/${entryId}/${approve ? 'approve' : 'reject'}`)
        await this.loadCompetitionDetail()
      }, approve ? '报名已通过' : '报名已拒绝')
    },
    async selectCompetition(id: string) {
      this.selectedCompetitionId = id
      await this.loadCompetitionDetail(id)
    },
    async loadCompetitionDetail(id = this.selectedCompetitionId) {
      if (!id) return
      const [detail, schedule, entries] = await Promise.all([
        api.get<Competition>(`/competitions/${id}`),
        api.get<Match[]>(`/competitions/${id}/schedule`).catch(() => []),
        api.get<CompetitionEntry[]>(`/admin/competitions/${id}/entries`).catch(() => [])
      ])
      this.selectedCompetition = detail
      this.schedule = schedule || []
      this.entries = entries || []
      this.selectedCompetitionId = id
    },
    async competitionCommand(action: 'publish' | 'close-registration' | 'arrange' | 'start' | 'enter-result-confirming' | 'finalize') {
      const id = this.selectedCompetitionId
      if (!id) return
      await this.run(async () => {
        const body = action === 'arrange' ? { seed: 20261001 } : undefined
        await api.post(`/admin/competitions/${id}/${action}`, body)
        await this.loadCompetitionDetail(id)
        await this.loadCompetitions().catch(() => undefined)
        if (action === 'finalize') await this.loadPrivate()
      }, {
        publish: '已发布赛事',
        'close-registration': '报名已关闭',
        arrange: '赛程已生成',
        start: '赛事已开始',
        'enter-result-confirming': '已进入最终确认',
        finalize: '赛事已完成并结算'
      }[action])
    },
    async joinCompetition(id: string) {
      await this.run(async () => {
        await api.post(`/competitions/${id}/entries`)
        if (!this.joinedCompetitionIds.includes(id)) this.joinedCompetitionIds.push(id)
        uni.setStorageSync('joinedCompetitionIds', this.joinedCompetitionIds)
        await this.loadCompetitionDetail(id)
      }, '报名成功')
    },
    async leaveCompetition(id: string) {
      await this.run(async () => {
        await api.delete(`/competitions/${id}/entries/me`)
        this.joinedCompetitionIds = this.joinedCompetitionIds.filter(item => item !== id)
        uni.setStorageSync('joinedCompetitionIds', this.joinedCompetitionIds)
        await this.loadCompetitionDetail(id)
      }, '已退出报名')
    },
    async scoreMatch(match: Match, games?: Array<{ a: number; b: number }>) {
      await this.run(async () => {
        if (match.status === 'READY') {
          await api.post(`/admin/matches/${match.id}/start`)
          const fresh = await api.get<Match>(`/matches/${match.id}`)
          match = fresh
        }
        const winsNeeded = Math.floor(match.best_of / 2) + 1
        await api.put(`/admin/matches/${match.id}/score`, {
          version: match.version,
          games: games || Array.from({ length: winsNeeded }, () => ({ a: 11, b: 7 }))
        })
        await this.loadCompetitionDetail()
      }, '比分已录入并自动晋级')
    },
    async publishAnnouncement(payload?: { title: string; content: string; visibility: string }) {
      await this.run(async () => {
        await api.post('/admin/announcements', payload || {
          title: '开源乓协活动提醒',
          content: '本公告来自移动端真实 API。',
          visibility: 'PUBLIC'
        })
        await this.loadAnnouncements().catch(() => undefined)
      }, '公告已发布')
    },
    async createPlayPost(payload?: Record<string, unknown>) {
      await this.run(async () => {
        await api.post('/play-posts', payload || {
          title: '今晚约球，单打练习',
          content: '轻松练球，欢迎报名。',
          visibility: 'PUBLIC',
          maxParticipants: 4,
          location: '开源乓协球台',
          contact: '站内通知',
          eventTime: nowIso(4 * 60 * 60_000)
        })
        await this.loadPlayPosts().catch(() => undefined)
      }, '约球已发布')
    },
    async joinPlayPost(id: string) {
      await this.run(async () => {
        await api.post(`/play-posts/${id}/participants`)
        await this.loadPlayPosts().catch(() => undefined)
      }, '已加入约球')
    },
    async leavePlayPost(id: string) {
      await this.run(async () => {
        await api.delete(`/play-posts/${id}/participants/me`)
        await this.loadPlayPosts().catch(() => undefined)
      }, '已退出约球')
    },
    async createMarketPost(payload?: Record<string, unknown>) {
      await this.run(async () => {
        await api.post('/market-posts', payload || {
          title: '出一块闲置球拍',
          content: '仅信息展示，交易自行核实。',
          visibility: 'PUBLIC',
          price: '面议',
          contact: '站内通知',
          marketplaceCondition: '九成新',
          deliveryMethod: '当面交付',
          rulesAccepted: true
        })
        await this.loadMarketPosts().catch(() => undefined)
      }, '二手信息已发布')
    },
    async reportMarketPost(id: string, reason: string) {
      await this.run(async () => {
        await api.post(`/market-posts/${id}/reports`, { reason })
      }, '举报已提交，管理员将核查')
    },
    async markMarketSold(id: string) {
      await this.run(async () => {
        await api.post(`/market-posts/${id}/mark-sold`)
        await this.loadMarketPosts().catch(() => undefined)
      }, '已标记售出')
    },
    async readNotification(id: string) {
      await this.run(async () => {
        await api.post(`/notifications/${id}/read`)
        await this.loadPrivate()
      }, '通知已读')
    },
    async readAllNotifications() {
      await this.run(async () => {
        await api.post('/notifications/read-all')
        await this.loadPrivate()
      }, '通知已全部标记为已读')
    }
  }
})
