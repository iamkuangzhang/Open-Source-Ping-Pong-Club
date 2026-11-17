<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import AppHeader from '../../components/AppHeader.vue'
import EmptyState from '../../components/EmptyState.vue'
import ErrorState from '../../components/ErrorState.vue'
import LoadingState from '../../components/LoadingState.vue'
import StatusBadge from '../../components/StatusBadge.vue'
import { useAppStore } from '../../stores/app'
import type { ForumReportStatus } from '../../stores/app'
import { formatDate, go } from '../../utils/ui'

const store = useAppStore()
const filters: Array<{ key: ForumReportStatus; label: string }> = [
  { key: 'PENDING', label: '待处理' },
  { key: 'HIDDEN', label: '已隐藏' },
  { key: 'DISMISSED', label: '已驳回' }
]
const notes = reactive<Record<string, string>>({})
const expandedId = ref('')
const submittingId = ref('')

const denied = computed(() => store.forumModerateChecked && !store.forumModerateAccess)
const statusLabel = (status?: string) => ({ PENDING: '待处理', HIDDEN: '已隐藏', DISMISSED: '已驳回' } as Record<string, string>)[status || ''] || '未知'
const postStatusLabel = (status?: string) => ({ PUBLISHED: '公开中', HIDDEN: '已隐藏', DELETED: '已删除' } as Record<string, string>)[status || ''] || '未知'
const activeFilter = computed(() => store.forumReportsFilter)

onLoad(async () => {
  store.initApi()
  store.success = ''
  store.error = ''
  await store.loadForumReports('PENDING').catch(() => undefined)
})

async function switchFilter(status: ForumReportStatus) {
  expandedId.value = ''
  await store.loadForumReports(status, 1).catch(() => undefined)
}

async function turnPage(page: number) {
  if (page < 1 || page > store.forumReportsPages) return
  expandedId.value = ''
  await store.loadForumReports(store.forumReportsFilter, page).catch(() => undefined)
}

function toggle(reportId: string) {
  expandedId.value = expandedId.value === reportId ? '' : reportId
  if (!notes[reportId]) notes[reportId] = ''
}

async function review(reportId: string, action: 'HIDE' | 'DISMISS') {
  const note = (notes[reportId] || '').trim()
  if (!note) {
    uni.showToast({ title: '请填写处理说明', icon: 'none' })
    return
  }
  const confirmed = await new Promise<boolean>((resolve) => uni.showModal({
    title: action === 'HIDE' ? '隐藏该内容？' : '驳回该举报？',
    content: action === 'HIDE'
      ? '隐藏后帖子将从论坛公开列表和详情中立即消失，作者与评论都无法继续访问。'
      : '驳回后帖子保持公开，举报记录将标记为已驳回并留痕。',
    confirmText: action === 'HIDE' ? '确认隐藏' : '确认驳回',
    confirmColor: action === 'HIDE' ? '#c64d47' : '#173f35',
    success: (result) => resolve(result.confirm)
  }))
  if (!confirmed) return
  submittingId.value = reportId
  try {
    await store.reviewForumReport(reportId, action, note)
    notes[reportId] = ''
    expandedId.value = ''
    uni.showToast({ title: action === 'HIDE' ? '内容已隐藏' : '举报已驳回', icon: 'none' })
  } catch {
    // 失败反馈由 store.error 在页面内渲染
  } finally {
    submittingId.value = ''
  }
}
</script>

<template>
  <view class="app-shell no-tabs">
    <AppHeader title="内容治理" subtitle="举报处理与内容隐藏" backable />

    <LoadingState v-if="!store.forumModerateChecked && store.forumReportsStatus !== 'error'" text="正在校验管理权限" />
    <EmptyState
      v-else-if="denied"
      title="无权访问该页面"
      description="内容治理仅对具备论坛管理权限的账号开放，普通账号不会看到举报队列"
      icon="info"
      action="返回社区"
      @action="go('/pages/community/index')"
    />
    <template v-else>
      <view class="segment report-filters" role="tablist">
        <button
          v-for="item in filters"
          :key="item.key"
          :class="['segment-item', { active: activeFilter === item.key }]"
          @click="switchFilter(item.key)"
        >{{ item.label }}{{ activeFilter === item.key ? ` ${store.forumReportsTotal}` : '' }}</button>
      </view>

      <view v-if="store.success" class="notice notice-success">{{ store.success }}</view>
      <view v-if="store.error" class="notice notice-error">{{ store.error }}</view>

      <LoadingState v-if="store.forumReportsStatus === 'loading'" variant="list" :rows="2" text="正在加载举报队列" />
      <ErrorState
        v-else-if="store.forumReportsStatus === 'error'"
        title="暂时无法加载举报队列"
        description="请稍后重试，或确认账号权限是否已被回收"
        @retry="store.loadForumReports(store.forumReportsFilter, store.forumReportsPage)"
      />
      <EmptyState
        v-else-if="store.forumReportsStatus === 'empty'"
        :title="activeFilter === 'PENDING' ? '没有待处理的举报' : '该状态下暂无记录'"
        :description="activeFilter === 'PENDING' ? '球友提交的新举报会进入这里' : '切换到其他状态可查看历史处理记录'"
      />

      <view v-else class="report-list">
        <view v-for="report in store.forumReports" :key="report.id" class="report-card">
          <view class="report-top">
            <StatusBadge :status="report.status" :label="statusLabel(report.status)" />
            <text class="report-time">{{ formatDate(report.created_at) }}</text>
          </view>
          <text class="card-title">{{ report.post_title || '内容已不存在' }}</text>
          <text class="report-content">{{ report.post_content || '原内容不可用' }}</text>
          <view class="report-reason"><text class="reason-label">举报原因</text><text class="reason-text">{{ report.reason }}</text></view>
          <view class="report-foot">
            <text class="caption">帖子状态：{{ postStatusLabel(report.post_status) }} · 举报人 {{ store.userLabel(report.reporter_user_id) }}</text>
            <button
              v-if="report.post_status === 'PUBLISHED'"
              class="btn btn-text btn-small"
              @click="go('/pages/community/post-detail', { id: report.post_id })"
            >查看</button>
          </view>

          <template v-if="report.status === 'PENDING'">
            <button v-if="expandedId !== report.id" class="btn btn-secondary btn-block handle" @click="toggle(report.id)">处理这条举报</button>
            <view v-else class="handle-panel">
              <textarea
                v-model="notes[report.id]"
                class="textarea handle-note"
                placeholder="填写处理说明，例如：已核实为广告内容，予以隐藏"
                :maxlength="500"
                auto-height
              />
              <text class="form-hint">处理说明必填，会随处理动作写入审计日志。</text>
              <view class="handle-actions">
                <button
                  class="btn btn-danger btn-small"
                  :disabled="submittingId === report.id || !(notes[report.id] || '').trim()"
                  @click="review(report.id, 'HIDE')"
                >隐藏内容</button>
                <button
                  class="btn btn-secondary btn-small"
                  :disabled="submittingId === report.id || !(notes[report.id] || '').trim()"
                  @click="review(report.id, 'DISMISS')"
                >驳回举报</button>
                <button class="btn btn-text btn-small" @click="expandedId = ''">取消</button>
              </view>
            </view>
          </template>
          <view v-else class="reviewed">
            <text class="caption">处理时间：{{ formatDate(report.reviewed_at) }} · 处理人 {{ store.userLabel(report.reviewed_by) }}</text>
            <text v-if="report.review_note" class="reviewed-note">{{ report.review_note }}</text>
          </view>
        </view>

        <view v-if="store.forumReportsPages > 1" class="pager">
          <button class="btn btn-secondary btn-small" :disabled="store.forumReportsPage <= 1" @click="turnPage(store.forumReportsPage - 1)">上一页</button>
          <text class="pager-info">{{ store.forumReportsPage }} / {{ store.forumReportsPages }}</text>
          <button class="btn btn-secondary btn-small" :disabled="store.forumReportsPage >= store.forumReportsPages" @click="turnPage(store.forumReportsPage + 1)">下一页</button>
        </view>
      </view>
    </template>
  </view>
</template>

<style scoped>
.report-filters { margin-top: .5rem; }
.report-list { margin-top: .85rem; }
.report-card { margin-bottom: .85rem; padding: 1rem; background: #fff; border: 1px solid #e2e7e3; border-radius: .875rem; }
.report-top { display: flex; align-items: center; justify-content: space-between; gap: .75rem; margin-bottom: .6rem; }
.report-time { color: #87908b; font-size: .75rem; }
.report-content { display: -webkit-box; margin-top: .4rem; overflow: hidden; color: #66706b; font-size: .8125rem; line-height: 1.55; -webkit-box-orient: vertical; -webkit-line-clamp: 3; }
.report-reason { display: flex; gap: .5rem; margin-top: .75rem; padding: .6rem .7rem; background: #f7f5ef; border-radius: .55rem; }
.reason-label { flex: 0 0 auto; color: #856816; font-size: .75rem; font-weight: 700; }
.reason-text { color: #4c5853; font-size: .8125rem; line-height: 1.5; word-break: break-word; }
.report-foot { display: flex; align-items: center; justify-content: space-between; gap: .75rem; margin-top: .7rem; }
.handle { margin-top: .85rem; }
.handle-panel { margin-top: .85rem; padding-top: .85rem; border-top: 1px solid #eef1ef; }
.handle-note { min-height: 4.5rem; }
.handle-actions { display: flex; gap: .5rem; margin-top: .7rem; }
.reviewed { margin-top: .85rem; padding-top: .75rem; border-top: 1px solid #eef1ef; }
.reviewed-note { display: block; margin-top: .35rem; color: #4c5853; font-size: .8125rem; line-height: 1.55; }
.pager { display: flex; align-items: center; justify-content: space-between; gap: .75rem; margin-top: 1rem; }
.pager-info { color: #66706b; font-size: .8125rem; }
</style>
