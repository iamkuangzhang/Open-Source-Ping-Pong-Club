<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useAppStore } from '../../stores/app'
import AppHeader from '../../components/AppHeader.vue'
import AppIcon from '../../components/AppIcon.vue'
import AppTabBar from '../../components/AppTabBar.vue'
import CompetitionCard from '../../components/CompetitionCard.vue'
import SectionHeader from '../../components/SectionHeader.vue'
import RatingRow from '../../components/RatingRow.vue'
import LoadingState from '../../components/LoadingState.vue'
import EmptyState from '../../components/EmptyState.vue'
import ErrorState from '../../components/ErrorState.vue'
import { formatDate, go } from '../../utils/ui'

const store = useAppStore()
const filter = ref('ALL')
const filters = [['ALL', '全部'], ['REGISTRATION', '报名中'], ['ACTIVE', '进行中'], ['FINISHED', '已结束']]
const activeStatuses = ['ARRANGING', 'READY', 'RUNNING', 'RESULT_CONFIRMING']

const sortedCompetitions = computed(() => [...store.competitions].sort((a, b) => new Date(b.start_time || b.created_at || 0).getTime() - new Date(a.start_time || a.created_at || 0).getTime()))
const finishedCompetitions = computed(() => sortedCompetitions.value.filter((item) => item.status === 'FINISHED'))
const visible = computed(() => {
  if (filter.value === 'FINISHED') return finishedCompetitions.value
  if (filter.value === 'REGISTRATION') return sortedCompetitions.value.filter((item) => item.status === 'REGISTRATION')
  if (filter.value === 'ACTIVE') return sortedCompetitions.value.filter((item) => activeStatuses.includes(item.status))
  const registration = sortedCompetitions.value.filter((item) => item.status === 'REGISTRATION').slice(0, 3)
  const active = sortedCompetitions.value.filter((item) => activeStatuses.includes(item.status)).slice(0, 1)
  const recentFinished = finishedCompetitions.value.slice(0, 1)
  return [...registration, ...active, ...recentFinished].slice(0, 5)
})
const hasMoreFinished = computed(() => filter.value === 'ALL' && finishedCompetitions.value.some((item) => !visible.value.includes(item)))
const rank = computed(() => store.rating ? store.leaderboard.findIndex((item) => item.user_id === store.rating?.user_id) + 1 : 0)
const latestAnnouncement = computed(() => store.announcements[0])

onMounted(() => store.bootstrap())

async function join(id: string) {
  if (!store.signedIn) return go('/pages/auth/login')
  try { await store.joinCompetition(id) } catch { /* Store renders feedback. */ }
}
</script>

<template>
  <view class="app-shell competition-home">
    <AppHeader branded title="比赛" subtitle="赛事、赛程与积分" action-icon="notification" action-label="通知中心" @action="go(store.signedIn ? '/pages/community/notifications' : '/pages/auth/login')" />

    <view class="segment" role="tablist"><button v-for="item in filters" :key="item[0]" :class="['segment-item', { active: filter === item[0] }]" @click="filter = item[0]">{{ item[1] }}</button></view>

    <view v-if="store.signedIn && store.ratingSummaryStatus !== 'empty'" class="section rating-section">
      <LoadingState v-if="store.ratingSummaryStatus === 'loading'" text="正在加载我的积分" />
      <ErrorState v-else-if="store.ratingSummaryStatus === 'error'" title="暂时无法加载我的积分" description="赛事与其他内容不受影响" @retry="store.loadPrivate()" />
      <view v-else-if="store.rating" class="rating-summary" @click="go('/pages/rating/mine')"><view><text class="summary-label">我的积分</text><text class="summary-number">{{ store.rating.current_rating }}</text></view><view class="summary-rank"><text>{{ rank > 0 ? `当前 #${rank}` : '查看积分记录' }}</text><AppIcon name="chevron-right" size="sm" /></view></view>
    </view>

    <view class="section">
      <SectionHeader title="近期赛事" :subtitle="filter === 'ALL' ? '优先展示可报名与进行中的赛事' : `当前查看：${filters.find((item) => item[0] === filter)?.[1]}`" />
      <view class="section-content competition-list">
        <LoadingState v-if="store.competitionsStatus === 'loading'" variant="list" text="正在加载赛事" :rows="2" />
        <ErrorState v-else-if="store.competitionsStatus === 'error'" title="暂时无法加载赛事" description="请稍后重试，其他内容仍可查看" @retry="store.loadCompetitions()" />
        <EmptyState v-else-if="!visible.length" title="暂无赛事" description="当前筛选下还没有赛事" />
        <CompetitionCard v-for="item in visible" v-else :key="item.id" :item="item" :joined="store.joinedCompetitionIds.includes(item.id)" @open="go('/pages/competition/detail', { id: item.id })" @join="join(item.id)" @leave="store.leaveCompetition(item.id)" />
        <button v-if="hasMoreFinished" class="history-link" @click="filter = 'FINISHED'"><text>查看全部已结束赛事</text><AppIcon name="chevron-right" size="sm" /></button>
      </view>
    </view>

    <view class="section">
      <SectionHeader title="积分榜" subtitle="正式成员积分排名" action="完整榜单" @action="go('/pages/rating/leaderboard')" />
      <view class="section-content leaderboard-panel">
        <LoadingState v-if="store.leaderboardStatus === 'loading'" variant="ranking" text="正在加载积分榜" :rows="3" />
        <ErrorState v-else-if="store.leaderboardStatus === 'error'" title="暂时无法加载积分榜" description="请稍后重试" @retry="store.loadLeaderboard()" />
        <EmptyState v-else-if="store.leaderboardStatus === 'empty'" title="暂无积分排名" description="完成积分赛事后会显示排名" />
        <view v-else class="ranking-list"><RatingRow v-for="(item, index) in store.leaderboard.slice(0, 3)" :key="item.user_id" :item="item" :rank="index + 1" /></view>
      </view>
    </view>

    <view class="section announcement-section">
      <SectionHeader title="最新公告" action="更多公告" @action="go('/pages/community/index')" />
      <view class="section-content">
        <LoadingState v-if="store.announcementsStatus === 'loading'" text="正在加载公告" />
        <ErrorState v-else-if="store.announcementsStatus === 'error'" title="暂时无法加载公告" description="请稍后重试" @retry="store.loadAnnouncements()" />
        <EmptyState v-else-if="store.announcementsStatus === 'empty'" title="暂无公告" description="新的通知会显示在这里" icon="announcement" />
        <view v-else-if="latestAnnouncement" class="announcement-row" @click="go('/pages/community/announcement-detail', { id: latestAnnouncement.id })"><view class="announcement-copy"><text class="announcement-title">{{ latestAnnouncement.title }}</text><text class="announcement-meta">{{ formatDate(latestAnnouncement.created_at, false) }}</text></view><AppIcon name="chevron-right" size="sm" /></view>
      </view>
    </view>

    <button v-if="store.canManage" class="fab" aria-label="创建赛事" @click="go('/pages/competition/create')"><AppIcon name="add" /></button>
    <AppTabBar active="competition" />
  </view>
</template>

<style scoped lang="scss">
.section-content { margin-top: .625rem; }
.competition-list { display: flex; flex-direction: column; gap: .7rem; }
.rating-section { margin-top: 1rem; }
.rating-summary { display: flex; min-height: 4.5rem; align-items: center; justify-content: space-between; gap: 1rem; padding: .8rem 1rem; background: #eef3f0; border: 1px solid #dce6e0; border-radius: .875rem; }
.summary-label { display: block; color: #52635d; font-size: .8125rem; font-weight: 600; }
.summary-number { display: block; margin-top: .08rem; color: #173f35; font-size: 1.5rem; font-weight: 800; }
.summary-rank { display: flex; align-items: center; gap: .15rem; color: #365148; font-size: .8125rem; font-weight: 700; }
.history-link { display: flex; min-height: 2.75rem; align-items: center; justify-content: center; gap: .2rem; color: #173f35; background: transparent !important; font-size: .8125rem; font-weight: 700; }
.ranking-list { padding: 0 1rem; background: #fff; border-top: 1px solid #e2e7e3; border-bottom: 1px solid #e2e7e3; }
.announcement-section { padding-bottom: .5rem; }
.announcement-row { display: flex; min-height: 3.75rem; align-items: center; gap: .75rem; padding: .65rem 0; border-top: 1px solid #e2e7e3; border-bottom: 1px solid #e2e7e3; }
.announcement-copy { min-width: 0; flex: 1; }
.announcement-title { display: block; overflow: hidden; color: #252d29; font-size: .9375rem; font-weight: 700; text-overflow: ellipsis; white-space: nowrap; }
.announcement-meta { display: block; margin-top: .2rem; color: #66706b; font-size: .75rem; }
</style>
