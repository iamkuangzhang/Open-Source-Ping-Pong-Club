<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import AppHeader from '../../components/AppHeader.vue'
import LoadingState from '../../components/LoadingState.vue'
import EmptyState from '../../components/EmptyState.vue'
import ErrorState from '../../components/ErrorState.vue'
import { useForumStore } from '../../stores/forum'
import { useAppStore } from '../../stores/app'
import { back, formatDate, go } from '../../utils/ui'

const forum = useForumStore()
const store = useAppStore()
const id = ref('')
const draft = ref('')
const reporting = ref(false)
const reason = ref('')
const visited = ref(false)

const detail = computed(() => forum.detail)
const isAuthor = computed(() => Boolean(store.me?.id && detail.value?.author_user_id === store.me?.id))
const canModerateComment = (authorId: string) => store.me?.id === authorId || store.canManage
const authorOf = (value?: string) => value ? `球友 ${value.slice(0, 5)}` : '匿名球友'
async function load() {
  forum.clearFeedback()
  await forum.loadPost(id.value).catch(() => undefined)
  if (forum.detailStatus === 'success') await forum.loadComments(id.value, 1, false).catch(() => undefined)
}

onLoad((query) => {
  id.value = String(query?.id || '')
  load()
})

onShow(() => {
  if (!visited.value) { visited.value = true; return }
  if (id.value) load()
})

async function submitComment() {
  if (!store.signedIn) {
    go('/pages/auth/login')
    return
  }
  if (!draft.value.trim()) {
    uni.showToast({ title: '请先写下评论内容', icon: 'none' })
    return
  }
  try {
    await forum.createComment(id.value, draft.value)
    draft.value = ''
  } catch { /* 错误提示由 store 与页面错误块共同呈现 */ }
}

function removePost() {
  uni.showModal({
    title: '删除帖子',
    content: '删除后帖子将不再公开展示，确定继续吗？',
    success: async (result) => {
      if (!result.confirm) return
      try {
        await forum.deletePost(id.value)
        back()
      } catch { /* 保留在当前页展示失败原因 */ }
    }
  })
}

function removeComment(commentId: string) {
  uni.showModal({
    title: '删除评论',
    content: '确定删除这条评论吗？',
    success: async (result) => {
      if (!result.confirm) return
      try {
        await forum.deleteComment(id.value, commentId)
      } catch { /* 错误提示由 store 呈现 */ }
    }
  })
}

function startReport() {
  if (!store.signedIn) {
    go('/pages/auth/login')
    return
  }
  reporting.value = !reporting.value
  forum.clearFeedback()
}

async function submitReport() {
  if (reason.value.trim().length < 2) {
    uni.showToast({ title: '请写明举报原因', icon: 'none' })
    return
  }
  try {
    await forum.reportPost(id.value, reason.value)
    reason.value = ''
    reporting.value = false
  } catch { /* 错误提示由 store 呈现 */ }
}
</script>

<template>
  <view class="app-shell no-tabs forum-detail">
    <AppHeader title="帖子详情" backable :action="isAuthor ? '编辑' : '举报'" @action="isAuthor ? go('/pages/forum/create', { id }) : startReport()" />

    <LoadingState v-if="forum.detailStatus === 'loading'" variant="list" text="正在加载帖子" :rows="2" />
    <ErrorState v-else-if="forum.detailStatus === 'error'" title="暂时无法加载帖子" description="帖子可能已被删除或隐藏" @retry="load" />

    <template v-else-if="detail">
      <view class="post-head">
        <text class="category-chip">{{ detail.category }}</text>
        <text class="post-date">{{ formatDate(detail.created_at) }}</text>
      </view>
      <text class="post-title">{{ detail.title }}</text>
      <view class="post-meta"><text>{{ authorOf(detail.author_user_id) }}</text><text v-if="detail.updated_at && detail.updated_at !== detail.created_at">已编辑</text></view>
      <text class="post-body">{{ detail.content }}</text>

      <view v-if="isAuthor" class="author-bar">
        <button class="btn btn-secondary btn-small" @click="go('/pages/forum/create', { id })">编辑帖子</button>
        <button class="btn btn-text btn-small danger-text" :disabled="forum.submitting" @click="removePost">删除帖子</button>
      </view>

      <view v-if="reporting" class="report-panel">
        <text class="panel-title">举报这个帖子</text>
        <text class="panel-hint">同一帖子只能举报一次，处理进度会写入本人举报记录。</text>
        <textarea v-model="reason" class="textarea report-input" maxlength="500" placeholder="请说明违规类型与具体情况" />
        <view class="panel-actions">
          <button class="btn btn-secondary btn-small" @click="reporting = false">取消</button>
          <button class="btn btn-primary btn-small" :disabled="forum.submitting" @click="submitReport">提交举报</button>
        </view>
      </view>

      <view v-if="forum.success" class="notice notice-success">{{ forum.success }}</view>
      <view v-if="forum.error" class="notice notice-error">{{ forum.error }}</view>

      <view class="comment-section">
        <view class="comment-head"><text class="section-title">评论</text><text class="muted">{{ forum.commentsTotal }} 条</text></view>
        <LoadingState v-if="forum.commentsStatus === 'loading'" text="正在加载评论" />
        <ErrorState v-else-if="forum.commentsStatus === 'error'" title="暂时无法加载评论" @retry="forum.loadComments(id, 1, false)" />
        <EmptyState v-else-if="forum.commentsStatus === 'empty'" title="还没有评论" description="说点什么，让讨论继续" />
        <view v-else class="comment-list">
          <view v-for="item in forum.comments" :key="item.id" class="comment-row">
            <view class="comment-meta"><text class="comment-author">{{ authorOf(item.author_user_id) }}</text><text class="comment-date">{{ formatDate(item.created_at) }}</text></view>
            <text class="comment-body">{{ item.content }}</text>
            <button v-if="canModerateComment(item.author_user_id)" class="comment-delete" @click="removeComment(item.id)">删除</button>
          </view>
          <button v-if="forum.commentsPage < forum.commentsPages" class="btn btn-text btn-small" @click="forum.loadComments(id, forum.commentsPage + 1, true)">查看更多评论</button>
        </view>
      </view>

      <view class="comment-composer">
        <input v-if="store.signedIn" v-model="draft" class="input composer-input" maxlength="2000" placeholder="写下你的看法" confirm-type="send" @confirm="submitComment" />
        <button v-else class="btn btn-secondary btn-block" @click="go('/pages/auth/login')">登录后参与讨论</button>
        <button v-if="store.signedIn" class="btn btn-primary btn-small" :disabled="forum.submitting || !draft.trim()" @click="submitComment">发送</button>
      </view>
    </template>

    <EmptyState v-else title="帖子不存在" description="该帖子可能已被作者删除或被管理员隐藏" />
    <view class="safe-bottom" />
  </view>
</template>

<style scoped lang="scss">
.forum-detail { padding-bottom: calc(7rem + env(safe-area-inset-bottom)); }
.post-head { display: flex; align-items: center; justify-content: space-between; gap: .75rem; padding-top: .35rem; }
.category-chip { display: inline-flex; min-height: 1.4rem; align-items: center; padding: 0 .5rem; color: #365148; background: #eef3f0; border-radius: 999px; font-size: .6875rem; font-weight: 700; }
.post-date { color: #87908b; font-size: .75rem; }
.post-title { display: block; margin: .8rem 0 .45rem; color: #18201d; font-size: 1.3125rem; font-weight: 800; line-height: 1.35; }
.post-meta { display: flex; gap: .75rem; color: #66706b; font-size: .75rem; }
.post-body { display: block; margin-top: 1rem; color: #252d29; font-size: .9375rem; line-height: 1.75; white-space: pre-wrap; word-break: break-word; }
.author-bar { display: flex; gap: .75rem; margin-top: 1.25rem; }
.report-panel { margin-top: 1.25rem; padding: 1rem; background: #fff; border: 1px solid #e2e7e3; border-radius: .875rem; }
.panel-title { display: block; color: #18201d; font-size: .9375rem; font-weight: 700; }
.panel-hint { display: block; margin-top: .3rem; color: #66706b; font-size: .75rem; line-height: 1.5; }
.report-input { min-height: 6rem; margin-top: .75rem; }
.panel-actions { display: flex; justify-content: flex-end; gap: .75rem; margin-top: .75rem; }
.comment-section { margin-top: 1.5rem; }
.comment-head { display: flex; min-height: 2.75rem; align-items: center; justify-content: space-between; }
.comment-list { border-top: 1px solid #e2e7e3; }
.comment-row { position: relative; padding: .85rem 0; border-bottom: 1px solid #e2e7e3; }
.comment-meta { display: flex; align-items: center; justify-content: space-between; color: #66706b; font-size: .75rem; }
.comment-author { color: #3f4944; font-weight: 700; }
.comment-body { display: block; margin-top: .35rem; color: #252d29; font-size: .875rem; line-height: 1.65; white-space: pre-wrap; word-break: break-word; }
.comment-delete { position: absolute; top: .8rem; right: 0; min-height: 1.75rem; padding: 0 .25rem; color: #a2564f; background: transparent !important; font-size: .75rem; font-weight: 700; }
.comment-meta > text:last-child { margin-right: 3rem; }
.comment-composer { position: fixed; right: 0; bottom: 0; left: 0; z-index: 20; display: flex; align-items: center; gap: .625rem; max-width: 30rem; margin: 0 auto; padding: .65rem 1rem calc(.65rem + env(safe-area-inset-bottom)); background: #fff; border-top: 1px solid #e2e7e3; }
.composer-input { flex: 1; min-height: 2.6rem; }
</style>
