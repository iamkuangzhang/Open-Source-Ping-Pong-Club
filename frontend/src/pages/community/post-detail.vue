<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad, onReachBottom } from '@dcloudio/uni-app'
import AppHeader from '../../components/AppHeader.vue'
import EmptyState from '../../components/EmptyState.vue'
import ErrorState from '../../components/ErrorState.vue'
import LoadingState from '../../components/LoadingState.vue'
import StatusBadge from '../../components/StatusBadge.vue'
import { useAppStore } from '../../stores/app'
import { formatDate, go } from '../../utils/ui'

const store = useAppStore()
const postId = ref('')
const draft = ref('')

const commentPlaceholder = computed(() => store.signedIn ? '友善交流，说说你的看法' : '登录后可以发表评论')
const hasMoreComments = computed(() => store.forumCommentsPage < store.forumCommentsPages)
const reportLabel = computed(() => {
  const status = store.forumReport?.status
  if (status === 'PENDING') return '待处理'
  if (status === 'HIDDEN') return '已处理，内容已隐藏'
  if (status === 'DISMISSED') return '已处理，举报未通过'
  return ''
})

onLoad(async (query) => {
  store.initApi()
  store.success = ''
  store.error = ''
  postId.value = String(query?.id || '')
  if (!postId.value) return
  await store.loadForumPost(postId.value).catch(() => undefined)
  if (store.forumPostStatus === 'success') {
    await store.loadForumComments(postId.value).catch(() => undefined)
  }
})

onReachBottom(() => loadMore())

async function loadMore() {
  if (!hasMoreComments.value || store.forumCommentsStatus === 'loading') return
  await store.loadForumComments(postId.value, store.forumCommentsPage + 1, true).catch(() => undefined)
}

async function retryComments() {
  await store.loadForumComments(postId.value).catch(() => undefined)
}

async function submit() {
  const content = draft.value.trim()
  if (!content) {
    uni.showToast({ title: '请输入评论内容', icon: 'none' })
    return
  }
  if (!store.signedIn) {
    uni.showToast({ title: '请先登录后再评论', icon: 'none' })
    go('/pages/auth/login')
    return
  }
  try {
    await store.publishForumComment(postId.value, content)
    draft.value = ''
    uni.showToast({ title: '评论已发布', icon: 'none' })
  } catch {
    // 失败反馈由 store.error 在页面内渲染
  }
}

async function removeComment(id: string) {
  const confirmed = await new Promise<boolean>((resolve) => uni.showModal({
    title: '删除这条评论？',
    content: '删除后其他球友将无法再看到这条评论。',
    confirmText: '删除',
    confirmColor: '#c64d47',
    success: (result) => resolve(result.confirm)
  }))
  if (!confirmed) return
  try {
    await store.removeForumComment(postId.value, id)
    uni.showToast({ title: '评论已删除', icon: 'none' })
  } catch {
    // 失败反馈由 store.error 在页面内渲染
  }
}

async function report() {
  if (!store.signedIn) {
    uni.showToast({ title: '请先登录后再举报', icon: 'none' })
    go('/pages/auth/login')
    return
  }
  const reasons = ['广告或垃圾信息', '人身攻击或辱骂', '不实信息', '违规或不适宜内容', '其他']
  const index = await new Promise<number>((resolve) => uni.showActionSheet({
    itemList: reasons,
    success: (result) => resolve(result.tapIndex),
    fail: () => resolve(-1)
  }))
  if (index < 0) return
  try {
    await store.reportForumPost(postId.value, reasons[index])
    uni.showToast({ title: '举报已提交', icon: 'none' })
  } catch {
    // 失败反馈由 store.error 在页面内渲染
  }
}
</script>

<template>
  <view class="app-shell no-tabs post-detail">
    <AppHeader title="帖子详情" backable :action="store.forumPostStatus === 'success' ? '举报' : undefined" @action="report" />

    <LoadingState v-if="store.forumPostStatus === 'loading'" text="正在加载帖子" />
    <EmptyState
      v-else-if="store.forumPostStatus === 'empty'"
      title="帖子不可见"
      description="该内容可能已被作者删除，或因违规被管理员隐藏"
      icon="info"
      action="返回社区"
      @action="go('/pages/community/index')"
    />
    <ErrorState
      v-else-if="store.forumPostStatus === 'error'"
      title="暂时无法加载帖子"
      description="请检查网络后重试"
      @retry="store.loadForumPost(postId)"
    />

    <template v-else-if="store.forumPost">
      <view class="post-head">
        <view class="post-meta">
          <StatusBadge v-if="store.forumPost.category" :status="store.forumPost.category" :label="store.forumPost.category" />
          <text class="post-author">{{ store.userLabel(store.forumPost.author_user_id) }}</text>
          <text class="post-time">{{ formatDate(store.forumPost.created_at) }}</text>
        </view>
        <text class="page-title">{{ store.forumPost.title }}</text>
        <text class="body-copy post-content">{{ store.forumPost.content }}</text>
      </view>

      <view v-if="store.success" class="notice notice-success">{{ store.success }}</view>
      <view v-if="store.error" class="notice notice-error">{{ store.error }}</view>
      <view v-if="store.forumReport" class="report-state">
        <text class="report-label">举报处理状态</text>
        <StatusBadge :status="store.forumReport.status" :label="reportLabel" />
      </view>

      <view class="section comment-section">
        <view class="comment-head">
          <text class="section-title">评论</text>
          <text class="comment-count">{{ store.forumCommentsTotal }} 条</text>
        </view>

        <LoadingState v-if="store.forumCommentsStatus === 'loading'" variant="list" :rows="2" text="正在加载评论" />
        <ErrorState
          v-else-if="store.forumCommentsStatus === 'error'"
          title="暂时无法加载评论"
          description="评论加载失败，请重试"
          @retry="retryComments"
        />
        <EmptyState
          v-else-if="store.forumCommentsStatus === 'empty'"
          title="还没有评论"
          description="来写下第一条评论吧"
        />
        <view v-else class="comment-list">
          <view v-for="comment in store.forumComments" :key="comment.id" class="comment-row">
            <view class="comment-main">
              <view class="comment-meta">
                <text class="comment-author">{{ store.userLabel(comment.author_user_id) }}</text>
                <text class="comment-time">{{ formatDate(comment.created_at) }}</text>
              </view>
              <text class="comment-body">{{ comment.content }}</text>
            </view>
            <button
              v-if="store.canDeleteForumComment(comment)"
              class="comment-delete"
              aria-label="删除评论"
              @click="removeComment(comment.id)"
            >删除</button>
          </view>
          <button v-if="hasMoreComments" class="btn btn-secondary btn-block more" @click="loadMore">加载更多评论</button>
        </view>
      </view>
    </template>

    <view v-if="store.forumPostStatus === 'success'" class="comment-composer">
      <textarea
        v-model="draft"
        class="composer-input"
        :placeholder="commentPlaceholder"
        :maxlength="2000"
        :disabled="!store.signedIn"
        auto-height
      />
      <button
        class="btn btn-primary btn-small"
        :disabled="store.forumCommentSubmitting || !store.signedIn || !draft.trim()"
        @click="submit"
      >{{ store.forumCommentSubmitting ? '发布中' : store.signedIn ? '发布' : '登录' }}</button>
    </view>
  </view>
</template>

<style scoped>
.post-detail { padding-bottom: 7.5rem; }
.post-head { padding-bottom: 1.25rem; border-bottom: 1px solid #e7eae8; }
.post-meta { display: flex; align-items: center; gap: .5rem; margin-bottom: .6rem; }
.post-author { color: #3f4944; font-size: .8125rem; font-weight: 700; }
.post-time { color: #87908b; font-size: .75rem; }
.post-content { display: block; margin-top: .75rem; white-space: pre-wrap; }
.comment-section { margin-top: 1.25rem; }
.comment-head { display: flex; align-items: baseline; justify-content: space-between; gap: .75rem; }
.comment-count { color: #66706b; font-size: .8125rem; }
.comment-list { margin-top: .6rem; }
.comment-row { display: flex; align-items: flex-start; gap: .75rem; padding: .85rem 0; border-bottom: 1px solid #eef1ef; }
.comment-main { min-width: 0; flex: 1; }
.comment-meta { display: flex; align-items: baseline; gap: .5rem; }
.comment-author { color: #3f4944; font-size: .8125rem; font-weight: 700; }
.comment-time { color: #87908b; font-size: .75rem; }
.comment-body { display: block; margin-top: .3rem; color: #18201d; font-size: .875rem; line-height: 1.6; white-space: pre-wrap; word-break: break-word; }
.comment-delete { min-height: 2rem; flex: 0 0 auto; margin: 0; padding: 0 .4rem; color: #923a36; background: transparent !important; font-size: .75rem; font-weight: 700; }
.more { margin-top: .85rem; }
.report-state { display: flex; align-items: center; justify-content: space-between; gap: .75rem; margin-top: 1rem; padding: .7rem .875rem; background: #f5f7f5; border-radius: .6875rem; }
.report-label { color: #4c5853; font-size: .8125rem; font-weight: 700; }
.comment-composer {
  position: fixed;
  right: 0;
  bottom: 0;
  left: 0;
  z-index: 25;
  display: flex;
  align-items: flex-end;
  gap: .6rem;
  padding: .7rem 1rem calc(.7rem + env(safe-area-inset-bottom));
  background: rgba(255, 255, 255, .97);
  border-top: 1px solid #e7e9e7;
}
.composer-input { box-sizing: border-box; min-height: 2.75rem; max-height: 6rem; flex: 1; padding: .7rem .8rem; color: #18201d; background: #f5f7f5; border: 1px solid #dfe3e0; border-radius: .6875rem; font-size: .875rem; line-height: 1.5; }
</style>
