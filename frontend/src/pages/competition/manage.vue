<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import AppHeader from '../../components/AppHeader.vue'
import AppIcon from '../../components/AppIcon.vue'
import StatusBadge from '../../components/StatusBadge.vue'
import LoadingState from '../../components/LoadingState.vue'
import { useAppStore } from '../../stores/app'
import { go } from '../../utils/ui'

const store = useAppStore()
const id = ref('')
const detail = computed(() => store.selectedCompetition)

onLoad(async (query) => {
  store.success = ''
  store.error = ''
  id.value = String(query?.id || '')
  await store.bootstrap()
  await store.selectCompetition(id.value)
})

const actions = computed(() => {
  const status = detail.value?.status
  return [
    {
      key: 'entries',
      title: '参赛名单',
      desc: `${store.approvedEntryCount} 人已通过 · ${store.pendingEntryCount} 人待审核`,
      enabled: ['REGISTRATION', 'ARRANGING', 'READY', 'RUNNING', 'RESULT_CONFIRMING', 'FINISHED', 'COMPLETED'].includes(status || '')
    },
    {
      key: 'arrange',
      title: '赛事编排',
      desc: status === 'ARRANGING' ? '先看预览，再确认生成正式赛程' : '仅关闭报名后可编排',
      enabled: status === 'ARRANGING'
    },
    {
      key: 'control',
      title: '比赛控制',
      desc: status === 'DRAFT' ? '发布赛事' : status === 'REGISTRATION' ? '关闭报名' : status === 'READY' ? '开始比赛' : '查看赛事状态',
      enabled: ['DRAFT', 'REGISTRATION', 'READY'].includes(status || '')
    },
    {
      key: 'score',
      title: '比分管理',
      desc: `${store.completedMatches}/${store.schedule.length} 场已完成`,
      enabled: status === 'RUNNING'
    },
    {
      key: 'result',
      title: '结果确认',
      desc: '检查结果并完成积分结算',
      enabled: ['RUNNING', 'RESULT_CONFIRMING'].includes(status || '')
    },
    {
      key: 'settings',
      title: '赛事设置',
      desc: '编辑基础资料',
      enabled: ['DRAFT', 'REGISTRATION'].includes(status || '')
    }
  ]
})

async function act(key: string) {
  const status = detail.value?.status
  if (key === 'entries') return go('/pages/competition/entries', { id: id.value })
  if (key === 'arrange') return go('/pages/competition/arrange', { id: id.value })
  if (key === 'score') return go(detail.value?.format === 'ROUND_ROBIN' ? '/pages/competition/groups' : '/pages/competition/bracket', { id: id.value, manage: 1 })
  if (key === 'settings') return go('/pages/competition/create', { id: id.value })
  if (key === 'control') return command(status === 'DRAFT' ? 'publish' : status === 'REGISTRATION' ? 'close-registration' : 'start')
  if (key === 'result') return command(status === 'RUNNING' ? 'enter-result-confirming' : 'finalize')
}

async function command(action: string) {
  if (action === 'finalize') {
    const confirmed = await new Promise<boolean>((resolve) => uni.showModal({
      title: '确认完成赛事？',
      content: '最终确认后将锁定比赛结果，并统一结算积分。',
      confirmText: '确认结算',
      success: (result) => resolve(result.confirm)
    }))
    if (!confirmed) return
  }
  if (action === 'close-registration' && store.approvedEntryCount < 2) {
    uni.showToast({ title: '已通过人数不足 2 人，无法关闭报名', icon: 'none' })
    return
  }
  try {
    await store.competitionCommand(action)
  } catch {
    // Store renders the API error in context.
  }
}
</script>

<template>
  <view class="app-shell no-tabs">
    <AppHeader title="赛事管理" backable />
    <LoadingState v-if="store.loading && !detail" />
    <template v-else-if="detail">
      <view class="manage-head"><view><text class="card-title">{{ detail.title }}</text><text class="muted">管理操作受赛事状态与账号权限限制</text></view><StatusBadge :status="detail.status" /></view>
      <view v-if="store.success" class="notice notice-success">{{ store.success }}</view>
      <view v-if="store.error" class="notice notice-error">{{ store.error }}</view>
      <view class="manage-list">
        <view v-for="item in actions" :key="item.key" :class="['manage-row', { disabled: !item.enabled }]" @click="item.enabled && act(item.key)">
          <view><text class="row-title">{{ item.title }}</text><text class="row-meta">{{ item.desc }}</text></view>
          <AppIcon v-if="item.enabled" name="chevron-right" />
          <text v-else class="unavailable">—</text>
        </view>
      </view>
      <view class="section">
        <text class="section-title">赛程概览</text>
        <view class="stat-strip">
          <view class="stat-item"><text class="stat-value">{{ store.approvedEntryCount }}</text><text class="stat-label">参赛人数</text></view>
          <view class="stat-item"><text class="stat-value">{{ store.schedule.length }}</text><text class="stat-label">总场次</text></view>
          <view class="stat-item"><text class="stat-value">{{ store.completedMatches }}</text><text class="stat-label">已完成</text></view>
        </view>
      </view>
    </template>
  </view>
</template>

<style scoped>
.manage-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 1rem; padding: 1rem 0 1.25rem; }
.manage-list { padding: 0 1rem; background: #fff; border: 1px solid #e2e7e3; border-radius: .875rem; }
.manage-row { display: flex; min-height: 4.5rem; align-items: center; justify-content: space-between; gap: .75rem; border-bottom: 1px solid #e7eae8; }
.manage-row:last-child { border-bottom: 0; }
.manage-row.disabled { opacity: .45; }
.unavailable { color: #9fa6a2; font-size: 1.25rem; }
</style>
