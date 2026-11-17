<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import AppHeader from '../../components/AppHeader.vue'
import EmptyState from '../../components/EmptyState.vue'
import ErrorState from '../../components/ErrorState.vue'
import LoadingState from '../../components/LoadingState.vue'
import StatusBadge from '../../components/StatusBadge.vue'
import { useAppStore } from '../../stores/app'
import type { ArrangePreviewMatch } from '../../stores/app'
import { competitionStatus, go } from '../../utils/ui'

const store = useAppStore()
const competitionId = ref('')
const confirming = ref(false)
const failed = ref(false)

const detail = computed(() => store.selectedCompetition)
const preview = computed(() => store.arrangePreview)
const arranging = computed(() => detail.value?.status === 'ARRANGING')
const formatLabel = computed(() => (preview.value?.format === 'ROUND_ROBIN' ? '小组循环赛' : '单淘汰赛'))
const nameOf = (userId?: string | null) => {
  if (!userId) return '待定'
  const entry = store.entries.find(item => item.user_id === userId)
  return entry?.nickname || entry?.real_name || store.userLabel(userId)
}

const roundGroups = computed(() => {
  const matches = preview.value?.matches || []
  const rounds = Array.from(new Set(matches.map(item => item.round))).sort((a, b) => a - b)
  return rounds.map(round => ({
    round,
    matches: matches.filter(item => item.round === round)
  }))
})

const firstRound = computed(() => roundGroups.value.find(item => item.round === 1)?.matches || [])

onLoad(async (query) => {
  store.initApi()
  store.success = ''
  store.error = ''
  store.arrangePreview = null
  store.arrangePreviewStatus = 'idle'
  store.arrangeResult = null
  competitionId.value = String(query?.id || '')
  await load()
})

async function load() {
  if (!competitionId.value) return
  failed.value = false
  try {
    await store.selectCompetition(competitionId.value)
  } catch {
    failed.value = true
    return
  }
  if (arranging.value) await store.loadArrangePreview(competitionId.value).catch(() => undefined)
}

async function regenerate() {
  await store.loadArrangePreview(competitionId.value, Date.now()).catch(() => undefined)
}

async function confirm() {
  const expected = preview.value?.matches.length || 0
  const confirmed = await new Promise<boolean>((resolve) => uni.showModal({
    title: '确认生成正式赛程？',
    content: `将按当前预览为 ${preview.value?.participantCount || 0} 名选手生成 ${expected} 场比赛，生成后赛事进入待开赛状态。`,
    confirmText: '确认编排',
    success: (result) => resolve(result.confirm)
  }))
  if (!confirmed) return
  confirming.value = true
  try {
    await store.confirmArrangement(competitionId.value)
    uni.showToast({ title: '正式赛程已生成', icon: 'none' })
  } catch {
    // 失败反馈由 store.error 在页面内渲染
  } finally {
    confirming.value = false
  }
}

function openSchedule() {
  go(detail.value?.format === 'ROUND_ROBIN' ? '/pages/competition/groups' : '/pages/competition/bracket', { id: competitionId.value, manage: 1 })
}
</script>

<template>
  <view class="app-shell no-tabs">
    <AppHeader title="赛事编排" :subtitle="detail?.title || '正在加载赛事'" backable />

    <ErrorState
      v-if="failed"
      title="暂时无法加载赛事"
      description="请稍后重试，或确认账号是否具备赛事管理权限"
      @retry="load"
    />
    <LoadingState v-else-if="!detail" text="正在加载赛事信息" />
    <template v-else>
      <view class="head-row">
        <view>
          <text class="card-title">{{ detail.title }}</text>
          <text class="muted">{{ competitionStatus(detail.status) }} · 赛事人数上限 {{ detail.max_participants || '未设置' }} 人</text>
        </view>
        <StatusBadge :status="detail.status" />
      </view>

      <view v-if="store.success" class="notice notice-success">{{ store.success }}</view>
      <view v-if="store.error" class="notice notice-error">{{ store.error }}</view>
      <view v-if="store.arrangeResult" :class="['notice', store.arrangeResult.consistent ? 'notice-success' : 'notice-error']">
        预览 {{ store.arrangeResult.expected }} 场，正式赛程 {{ store.arrangeResult.actual }} 场，
        {{ store.arrangeResult.consistent ? '预览与确认结果一致' : '与预览不一致，请联系管理员核查' }}
      </view>

      <EmptyState
        v-if="!arranging && !store.arrangeResult"
        :title="detail.status === 'READY' || store.schedule.length ? '该赛事已完成编排' : '当前状态无法编排'"
        :description="detail.status === 'READY' || store.schedule.length
          ? '正式赛程已生成，可在赛程页查看'
          : '仅「编排中」状态可以生成赛程预览，请先在赛事管理中关闭报名'"
        action="返回赛事管理"
        @action="go('/pages/competition/manage', { id: competitionId })"
      />

      <template v-else-if="arranging">
        <LoadingState v-if="store.arrangePreviewStatus === 'loading'" variant="list" :rows="2" text="正在生成编排预览" />
        <ErrorState
          v-else-if="store.arrangePreviewStatus === 'error'"
          title="暂时无法生成编排预览"
          description="请确认已通过人数不少于 2 人后重试"
          @retry="regenerate"
        />

        <template v-else-if="preview">
          <view class="stat-strip preview-stats">
            <view class="stat-item"><text class="stat-value">{{ preview.participantCount }}</text><text class="stat-label">参赛人数</text></view>
            <view class="stat-item"><text class="stat-value">{{ store.arrangePreviewRounds }}</text><text class="stat-label">轮次</text></view>
            <view class="stat-item"><text class="stat-value">{{ preview.matches.length }}</text><text class="stat-label">总场次</text></view>
          </view>
          <text class="preview-meta">
            {{ formatLabel }} · 随机种子 {{ preview.seed }}{{ store.arrangePreviewByes ? ` · 首轮轮空 ${store.arrangePreviewByes} 场` : '' }}
          </text>

          <view class="section">
            <view class="section-head"><text class="section-title">首轮对阵</text><text class="caption">共 {{ firstRound.length }} 场</text></view>
            <view class="pair-list">
              <view v-for="match in firstRound" :key="`${match.round}-${match.match}`" class="pair-row">
                <text class="pair-index">第 {{ match.match }} 场</text>
                <text class="pair-text">
                  {{ nameOf(match.playerA) }} <text class="vs">vs</text> {{ nameOf(match.playerB) }}
                </text>
                <StatusBadge v-if="match.bye" status="PENDING" label="轮空" />
              </view>
            </view>
          </view>

          <view class="section">
            <text class="section-title">场次分布</text>
            <view class="round-list">
              <view v-for="group in roundGroups" :key="group.round" class="round-row">
                <text class="round-name">第 {{ group.round }} 轮</text>
                <text class="round-count">{{ group.matches.length }} 场</text>
              </view>
            </view>
          </view>

          <view class="actions">
            <button class="btn btn-secondary btn-block" :disabled="confirming" @click="regenerate">重新生成预览</button>
            <button class="btn btn-primary btn-block" :disabled="confirming || !preview.matches.length" @click="confirm">
              {{ confirming ? '正在生成赛程' : '确认编排并生成赛程' }}
            </button>
          </view>
        </template>
      </template>

      <view v-if="store.arrangeResult" class="actions">
        <button class="btn btn-primary btn-block" @click="openSchedule">查看正式赛程</button>
      </view>
    </template>
  </view>
</template>

<style scoped>
.head-row { display: flex; align-items: flex-start; justify-content: space-between; gap: 1rem; padding: 1rem 0 .85rem; }
.preview-stats { margin-top: .25rem; }
.preview-meta { display: block; margin-top: .6rem; color: #4c5853; font-size: .8125rem; line-height: 1.5; }
.section-head { display: flex; align-items: baseline; justify-content: space-between; gap: .75rem; }
.pair-list { margin-top: .6rem; padding: 0 .875rem; background: #fff; border: 1px solid #e2e7e3; border-radius: .875rem; }
.pair-row { display: flex; align-items: center; gap: .6rem; padding: .7rem 0; border-bottom: 1px solid #eef1ef; }
.pair-row:last-child { border-bottom: 0; }
.pair-index { flex: 0 0 4.2rem; color: #87908b; font-size: .75rem; }
.pair-text { min-width: 0; flex: 1; color: #18201d; font-size: .8125rem; font-weight: 600; }
.vs { color: #87908b; font-weight: 400; }
.round-list { margin-top: .6rem; padding: 0 .875rem; background: #fff; border: 1px solid #e2e7e3; border-radius: .875rem; }
.round-row { display: flex; align-items: center; justify-content: space-between; padding: .7rem 0; border-bottom: 1px solid #eef1ef; }
.round-row:last-child { border-bottom: 0; }
.round-name { color: #3f4944; font-size: .8125rem; font-weight: 700; }
.round-count { color: #66706b; font-size: .8125rem; }
.actions { display: flex; flex-direction: column; gap: .6rem; margin-top: 1.5rem; }
</style>
