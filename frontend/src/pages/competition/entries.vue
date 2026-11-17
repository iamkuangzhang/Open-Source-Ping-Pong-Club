<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import AppHeader from '../../components/AppHeader.vue'
import EmptyState from '../../components/EmptyState.vue'
import ErrorState from '../../components/ErrorState.vue'
import LoadingState from '../../components/LoadingState.vue'
import StatusBadge from '../../components/StatusBadge.vue'
import { useAppStore } from '../../stores/app'
import type { CompetitionEntry } from '../../stores/app'
import { competitionStatus, formatDate, go } from '../../utils/ui'

const store = useAppStore()
type EntryFilter = 'ALL' | 'PENDING' | 'APPROVED' | 'REJECTED'
const filters: Array<{ key: EntryFilter; label: string }> = [
  { key: 'ALL', label: '全部' },
  { key: 'PENDING', label: '待审核' },
  { key: 'APPROVED', label: '已通过' },
  { key: 'REJECTED', label: '已拒绝' }
]
const competitionId = ref('')
const filter = ref<EntryFilter>('ALL')
const reviewingId = ref('')
const failed = ref(false)

const detail = computed(() => store.selectedCompetition)
const capacity = computed(() => detail.value?.max_participants || 0)
const registrationOpen = computed(() => detail.value?.status === 'REGISTRATION')
const filteredEntries = computed<CompetitionEntry[]>(() => filter.value === 'ALL'
  ? store.entries
  : store.entries.filter(entry => entry.status === filter.value))
const approved = computed(() => store.approvedEntryCount)
const pending = computed(() => store.pendingEntryCount)
const rejected = computed(() => store.rejectedEntryCount)
const countOf = (key: EntryFilter) => key === 'ALL'
  ? store.entries.length
  : key === 'PENDING' ? pending.value : key === 'APPROVED' ? approved.value : rejected.value
const quotaWarning = computed(() => {
  if (!capacity.value) return ''
  if (approved.value > capacity.value) return `已通过人数超过赛事人数上限（${approved.value} / ${capacity.value}），请先拒绝部分报名`
  if (approved.value === capacity.value) return `已通过人数已达赛事人数上限（${approved.value} / ${capacity.value}），无法再通过新的报名`
  return ''
})
const closeBlockedHint = computed(() => approved.value >= 2
  ? ''
  : `已通过人数不足 2 人，关闭报名会被后端拒绝（COMPETITION_INSUFFICIENT_PARTICIPANTS）`)

onLoad(async (query) => {
  store.initApi()
  store.success = ''
  store.error = ''
  competitionId.value = String(query?.id || '')
  await retry()
})

function entryName(entry: CompetitionEntry) {
  return entry.nickname || entry.real_name || `球友 ${entry.user_id.slice(0, 5)}`
}

async function review(entry: CompetitionEntry, approve: boolean) {
  if (!registrationOpen.value) {
    uni.showToast({ title: '当前赛事状态不允许审核报名', icon: 'none' })
    return
  }
  const confirmed = await new Promise<boolean>((resolve) => uni.showModal({
    title: approve ? '通过这位球友的报名？' : '拒绝这位球友的报名？',
    content: approve
      ? `${entryName(entry)} 将进入正式参赛名单，通过人数会计入赛事编排。`
      : `${entryName(entry)} 的报名将被标记为已拒绝，不会进入参赛名单。`,
    confirmText: approve ? '通过' : '拒绝',
    confirmColor: approve ? '#173f35' : '#c64d47',
    success: (result) => resolve(result.confirm)
  }))
  if (!confirmed) return
  reviewingId.value = entry.id
  try {
    await store.reviewCompetitionEntry(entry.id, approve)
    uni.showToast({ title: approve ? '已通过报名' : '已拒绝报名', icon: 'none' })
  } catch {
    // 失败反馈由 store.error 在页面内渲染
  } finally {
    reviewingId.value = ''
  }
}

async function retry() {
  if (!competitionId.value) return
  failed.value = false
  try {
    await store.selectCompetition(competitionId.value)
  } catch {
    failed.value = true
  }
}
</script>

<template>
  <view class="app-shell no-tabs">
    <AppHeader title="参赛名单" :subtitle="detail?.title || '正在加载赛事'" backable />

    <ErrorState
      v-if="failed"
      title="暂时无法加载参赛名单"
      description="请稍后重试，或确认账号是否具备赛事管理权限"
      @retry="retry"
    />
    <LoadingState v-else-if="!detail" text="正在加载参赛名单" />
    <template v-else>
      <view class="head-row">
        <view>
          <text class="card-title">{{ detail.title }}</text>
          <text class="muted">{{ competitionStatus(detail.status) }} · 赛事人数上限 {{ capacity || '未设置' }} 人</text>
        </view>
        <StatusBadge :status="detail.status" />
      </view>

      <view class="stat-strip entry-stats">
        <view class="stat-item"><text class="stat-value">{{ approved }}</text><text class="stat-label">已通过</text></view>
        <view class="stat-item"><text class="stat-value">{{ pending }}</text><text class="stat-label">待审核</text></view>
        <view class="stat-item"><text class="stat-value">{{ capacity || '—' }}</text><text class="stat-label">人数上限</text></view>
      </view>
      <text class="consistency">{{ approved }} / {{ capacity || '—' }} 人已确认参赛，赛事编排将按已通过名单生成</text>

      <view v-if="store.success" class="notice notice-success">{{ store.success }}</view>
      <view v-if="store.error" class="notice notice-error">{{ store.error }}</view>
      <view v-if="quotaWarning" class="notice notice-error">{{ quotaWarning }}</view>
      <view v-if="closeBlockedHint" class="notice notice-hint">{{ closeBlockedHint }}</view>

      <view class="segment entry-filters" role="tablist">
        <button
          v-for="item in filters"
          :key="item.key"
          :class="['segment-item', { active: filter === item.key }]"
          @click="filter = item.key"
        >{{ item.label }} {{ countOf(item.key) }}</button>
      </view>

      <EmptyState
        v-if="!filteredEntries.length"
        :title="store.entries.length ? '该状态下没有报名记录' : '暂无报名记录'"
        :description="store.entries.length ? '切换到其他状态查看' : '球友报名后会出现在这里'"
      />
      <view v-else class="entry-list">
        <view v-for="entry in filteredEntries" :key="entry.id" class="entry-row">
          <view class="entry-main">
            <text class="row-title">{{ entryName(entry) }}</text>
            <text class="row-meta">报名时间 {{ formatDate(entry.registered_at) }}{{ entry.approved_at ? ` · 通过于 ${formatDate(entry.approved_at)}` : '' }}</text>
          </view>
          <view class="entry-side">
            <StatusBadge
              :status="entry.status"
              :label="entry.status === 'APPROVED' ? '已通过' : entry.status === 'REJECTED' ? '已拒绝' : '待审核'"
            />
            <view v-if="entry.status === 'PENDING' && registrationOpen" class="review-actions">
              <button
                class="btn btn-secondary btn-small"
                :disabled="reviewingId === entry.id"
                @click="review(entry, false)"
              >拒绝</button>
              <button
                class="btn btn-primary btn-small"
                :disabled="reviewingId === entry.id || (capacity > 0 && approved >= capacity)"
                @click="review(entry, true)"
              >通过</button>
            </view>
          </view>
        </view>
      </view>

      <view v-if="!registrationOpen" class="notice notice-hint">
        当前赛事状态为「{{ competitionStatus(detail.status) }}」，报名审核已关闭，仅可查看名单。
      </view>

      <view class="actions">
        <button class="btn btn-secondary btn-block" @click="go('/pages/competition/manage', { id: competitionId })">返回赛事管理</button>
      </view>
    </template>
  </view>
</template>

<style scoped>
.head-row { display: flex; align-items: flex-start; justify-content: space-between; gap: 1rem; padding: 1rem 0 .85rem; }
.entry-stats { margin-top: .25rem; }
.consistency { display: block; margin-top: .6rem; color: #4c5853; font-size: .8125rem; line-height: 1.5; }
.entry-filters { margin-top: 1rem; }
.entry-list { margin-top: .6rem; border-top: 1px solid #e2e7e3; }
.entry-row { display: flex; align-items: flex-start; justify-content: space-between; gap: .75rem; padding: .85rem 0; border-bottom: 1px solid #e2e7e3; }
.entry-main { min-width: 0; flex: 1; }
.entry-side { display: flex; flex-direction: column; align-items: flex-end; gap: .45rem; }
.review-actions { display: flex; gap: .4rem; }
.notice-hint { color: #4c5853; background: #eef3f0; }
.actions { margin-top: 1.5rem; }
</style>
