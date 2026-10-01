<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { useAppStore } from '../../stores/app'
import AppHeader from '../../components/AppHeader.vue'
import AppIcon from '../../components/AppIcon.vue'
import StatusBadge from '../../components/StatusBadge.vue'
import MatchCard from '../../components/MatchCard.vue'
import EmptyState from '../../components/EmptyState.vue'
import LoadingState from '../../components/LoadingState.vue'
import ErrorState from '../../components/ErrorState.vue'
import { competitionType, formatDate, go } from '../../utils/ui'

const store = useAppStore()
const id = ref('')
const failed = ref(false)
const detail = computed(() => store.selectedCompetition)
const myEntry = computed(() => store.entries.find((entry) => entry.user_id === store.me?.id))

async function load() {
  failed.value = false
  try {
    await store.bootstrap()
    await store.selectCompetition(id.value)
  } catch {
    failed.value = true
  }
}

onLoad((query) => {
  id.value = String(query?.id || '')
  load()
})

async function join() {
  if (!store.signedIn) return go('/pages/auth/login')
  try { await store.joinCompetition(id.value) } catch { /* Store renders the API error. */ }
}
</script>

<template>
  <view class="app-shell no-tabs">
    <AppHeader title="比赛详情" backable />
    <LoadingState v-if="store.loading && !detail" />
    <ErrorState v-else-if="failed" title="暂时无法加载比赛详情" @retry="load" />
    <template v-else-if="detail">
      <view class="detail-hero">
        <view class="label-line"><text :class="['type', { gold: (detail.competition_type || detail.type) === 'RATED' }]">{{ competitionType(detail.competition_type || detail.type) }}</text><StatusBadge :status="detail.status" /></view>
        <text class="detail-title">{{ detail.title }}</text>
        <text class="meta">{{ formatDate(detail.start_time) }}</text>
        <text class="meta">{{ detail.location || '地点待定' }}</text>
        <text class="meta">{{ detail.format === 'ROUND_ROBIN' ? '小组循环' : '单淘汰' }} · {{ detail.best_of || 3 }}局制</text>
        <view class="join-line">
          <text>{{ store.canManage ? `${store.entries.length} / ` : '上限 ' }}{{ detail.max_participants || '—' }} 人</text>
          <button v-if="detail.status === 'REGISTRATION'" :class="['btn btn-small', (myEntry || store.joinedCompetitionIds.includes(id)) ? 'btn-secondary' : 'btn-primary']" @click="(myEntry || store.joinedCompetitionIds.includes(id)) ? store.leaveCompetition(id) : join()">{{ (myEntry || store.joinedCompetitionIds.includes(id)) ? '退出报名' : '立即报名' }}</button>
        </view>
      </view>
      <view v-if="store.success" class="notice notice-success">{{ store.success }}</view>
      <view v-if="store.error" class="notice notice-error">{{ store.error }}</view>
      <view class="detail-section"><text class="section-title">比赛介绍</text><text class="body-copy">{{ detail.description || '赛事详情以现场组织安排为准。' }}</text></view>
      <view class="detail-section">
        <view class="section-row"><text class="section-title">参赛选手</text><text v-if="store.canManage" class="muted">{{ store.entries.length }} 人</text></view>
        <EmptyState v-if="!store.canManage" title="参赛名单由赛事组织方管理" />
        <EmptyState v-else-if="!store.entries.length" title="暂无参赛选手" />
        <view v-else class="participant-grid"><text v-for="item in store.entries.slice(0, 12)" :key="item.id">{{ item.nickname || item.real_name || `球友 ${item.user_id.slice(0, 4)}` }}</text></view>
      </view>
      <view class="detail-section">
        <view class="section-row"><text class="section-title">赛程</text><text class="link" @click="go(detail.format === 'ROUND_ROBIN' ? '/pages/competition/groups' : '/pages/competition/bracket', { id })">查看全部</text></view>
        <EmptyState v-if="!store.schedule.length" title="赛程尚未生成" />
        <MatchCard v-for="item in store.schedule.slice(0, 3)" :key="item.id" :item="item" @open="go('/pages/match/detail', { id: item.id, competitionId: id })" />
      </view>
      <view class="detail-section"><text class="section-title">赛事规则</text><text class="body-copy">V1 为单打项目。比赛结果、晋级与积分结算以后端最终确认结果为准。</text></view>
      <view v-if="store.canManage" class="manage-entry" @click="go('/pages/competition/manage', { id })"><text>赛事管理</text><AppIcon name="chevron-right" /></view>
    </template>
  </view>
</template>

<style scoped>
.detail-hero { padding: .75rem 0 1.25rem; border-bottom: 1px solid #e7eae8; }
.label-line, .join-line, .section-row { display: flex; align-items: center; justify-content: space-between; gap: .75rem; }
.type { color: #456059; font-size: .75rem; font-weight: 700; }
.type.gold { color: #957516; }
.detail-title { display: block; margin: 1rem 0; font-size: 1.5rem; font-weight: 800; line-height: 1.35; }
.meta { display: block; margin-top: .35rem; color: #66706b; font-size: .875rem; }
.join-line { margin-top: 1.25rem; font-size: .8125rem; font-weight: 600; }
.detail-section { padding: 1.5rem 0; border-bottom: 1px solid #e7eae8; }
.body-copy { display: block; margin-top: .75rem; }
.participant-grid { display: grid; margin-top: .75rem; grid-template-columns: repeat(2, 1fr); gap: .75rem; color: #444d49; font-size: .875rem; }
.manage-entry { display: flex; min-height: 3.75rem; align-items: center; justify-content: space-between; margin-top: 1.5rem; padding: 0 1rem; color: #173f35; background: #eef3f0; border-radius: .875rem; font-weight: 700; }
</style>
