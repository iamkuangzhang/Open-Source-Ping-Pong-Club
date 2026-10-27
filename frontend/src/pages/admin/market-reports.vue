<script setup lang="ts">
import { onMounted, ref } from 'vue'
import AppHeader from '../../components/AppHeader.vue'
import EmptyState from '../../components/EmptyState.vue'
import LoadingState from '../../components/LoadingState.vue'
import { api } from '../../services/api'

type MarketReport = {
  report_id: string
  post_id: string
  reason: string
  details?: string
  reported_at: string
  title: string
  content: string
  price?: string
  marketplace_condition?: string
  delivery_method?: string
  post_status: string
}

type HiddenPost = {
  id: string
  title: string
  content: string
  price?: string
  marketplace_condition?: string
  delivery_method?: string
}

const reports = ref<MarketReport[]>([])
const hiddenPosts = ref<HiddenPost[]>([])
const loading = ref(true)
const busyId = ref('')
const error = ref('')
const labels: Record<string, string> = {
  SUSPECTED_FRAUD: '疑似诈骗',
  PROHIBITED_GOODS: '违禁或不适宜物品',
  MISLEADING: '虚假或误导信息',
  HARASSMENT: '骚扰或不当内容',
  PRIVACY: '泄露个人隐私',
  OTHER: '其他违规问题'
}

onMounted(load)

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [pending, hidden] = await Promise.all([
      api.get<MarketReport[]>('/admin/market-reports'),
      api.get<HiddenPost[]>('/admin/market-posts/hidden')
    ])
    reports.value = pending || []
    hiddenPosts.value = hidden || []
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '加载二手审核信息失败'
  } finally {
    loading.value = false
  }
}

async function resolve(report: MarketReport, action: 'HIDE' | 'DISMISS') {
  busyId.value = report.report_id
  try {
    await api.put(`/admin/market-reports/${report.report_id}`, { action })
    uni.showToast({ title: action === 'HIDE' ? '信息已下架并记录处理结果' : '举报已驳回', icon: 'none' })
    await load()
  } catch (reason) {
    uni.showToast({ title: reason instanceof Error ? reason.message : '处理举报失败', icon: 'none' })
  } finally {
    busyId.value = ''
  }
}

async function restore(post: HiddenPost) {
  busyId.value = post.id
  try {
    await api.post(`/admin/market-posts/${post.id}/restore`)
    uni.showToast({ title: '信息已恢复展示', icon: 'none' })
    await load()
  } catch (reason) {
    uni.showToast({ title: reason instanceof Error ? reason.message : '恢复信息失败', icon: 'none' })
  } finally {
    busyId.value = ''
  }
}
</script>

<template>
  <view class="app-shell no-tabs">
    <AppHeader title="二手信息治理" subtitle="举报处理和临时下架均会记录审计" backable />
    <view class="scope-notice">举报不是违规结论。请核对举报理由和商品描述后再处理；平台仅管理信息展示，不介入线下付款、退款或纠纷赔付。</view>
    <LoadingState v-if="loading && !reports.length && !hiddenPosts.length" text="正在加载二手治理记录" />
    <view v-else>
      <view class="section">
        <text class="section-title">待处理举报（{{ reports.length }}）</text>
        <EmptyState v-if="!reports.length" title="暂无待处理举报" />
        <view v-for="report in reports" :key="report.report_id" class="moderation-card">
          <text class="card-title">{{ report.title }}</text>
          <text class="card-meta">{{ labels[report.reason] || report.reason }} · {{ report.reported_at }}</text>
          <text v-if="report.details" class="card-detail">举报说明：{{ report.details }}</text>
          <text class="card-detail">商品说明：{{ report.content }}</text>
          <view class="actions">
            <button class="btn btn-secondary" :disabled="busyId === report.report_id" @click="resolve(report, 'DISMISS')">驳回举报</button>
            <button class="btn btn-danger" :disabled="busyId === report.report_id" @click="resolve(report, 'HIDE')">下架信息</button>
          </view>
        </view>
      </view>
      <view class="section">
        <text class="section-title">暂时隐藏的信息（{{ hiddenPosts.length }}）</text>
        <EmptyState v-if="!hiddenPosts.length" title="暂无隐藏信息" />
        <view v-for="post in hiddenPosts" :key="post.id" class="moderation-card">
          <text class="card-title">{{ post.title }}</text>
          <text class="card-detail">{{ post.content }}</text>
          <button class="btn btn-primary restore" :disabled="busyId === post.id" @click="restore(post)">核查后恢复展示</button>
        </view>
      </view>
      <view v-if="error" class="notice notice-error">{{ error }}</view>
      <button class="btn btn-secondary btn-block reload" @click="load">重新加载</button>
    </view>
  </view>
</template>

<style scoped>
.scope-notice { margin-top: 1rem; padding: .8rem; color: #674b1d; background: #fff3d9; border-radius: .7rem; font-size: .78rem; line-height: 1.55; }
.section { margin-top: 1.5rem; }
.moderation-card { margin-top: .75rem; padding: 1rem; background: #fff; border: 1px solid #e2e7e3; border-radius: .75rem; }
.card-title, .card-meta, .card-detail { display: block; }
.card-title { font-weight: 700; }
.card-meta { margin-top: .35rem; color: #6a746f; font-size: .75rem; }
.card-detail { margin-top: .55rem; color: #4c5853; font-size: .8125rem; line-height: 1.5; white-space: pre-wrap; }
.actions { display: flex; gap: .6rem; margin-top: .85rem; }
.actions .btn { flex: 1; }
.restore, .reload { margin-top: .85rem; }
</style>
