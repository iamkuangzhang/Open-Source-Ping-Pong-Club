<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import AppHeader from '../../components/AppHeader.vue'
import AppIcon from '../../components/AppIcon.vue'
import LoadingState from '../../components/LoadingState.vue'
import EmptyState from '../../components/EmptyState.vue'
import ErrorState from '../../components/ErrorState.vue'
import { FORUM_CATEGORIES, useForumStore } from '../../stores/forum'
import { useAppStore } from '../../stores/app'
import { formatDate, go } from '../../utils/ui'

const forum = useForumStore()
const store = useAppStore()
const tabs = computed(() => ['', ...FORUM_CATEGORIES])
const loadedOnce = ref(false)

const summaryOf = (content: string) => (content || '').replace(/\s+/g, ' ').slice(0, 80)
const authorOf = (id?: string) => id ? `球友 ${id.slice(0, 5)}` : '匿名球友'
const countText = computed(() => forum.total > 0 ? `共 ${forum.total} 个帖子` : '发帖交流，让球友看见你的想法')

async function load(category = forum.category) {
  try {
    await forum.loadPosts(category, true)
  } catch { /* 页面内的错误态已经呈现失败原因 */ }
}

onLoad(() => load())
onShow(() => {
  if (!loadedOnce.value) { loadedOnce.value = true; return }
  load().catch(() => undefined)
})

function compose() {
  if (!store.signedIn) {
    go('/pages/auth/login')
    return
  }
  go('/pages/forum/create')
}

function open(id: string) {
  go('/pages/forum/detail', { id })
}
</script>

<template>
  <view class="app-shell no-tabs forum-page">
    <AppHeader title="球友论坛" subtitle="发帖、回帖与内容治理" backable action="发帖" @action="compose" />

    <view class="segment" role="tablist">
      <button v-for="item in tabs" :key="item || 'ALL'" :class="['segment-item', { active: forum.category === item }]" @click="load(item)">{{ item || '全部' }}</button>
    </view>

    <text class="list-caption">{{ countText }}</text>

    <view class="forum-list">
      <LoadingState v-if="forum.listStatus === 'loading'" variant="list" text="正在加载帖子" :rows="3" />
      <ErrorState v-else-if="forum.listStatus === 'error'" title="暂时无法加载帖子" description="请核对网络后重试，其他板块内容不受影响" @retry="load()" />
      <EmptyState v-else-if="forum.listStatus === 'empty'" :title="forum.emptyHint" description="成为第一个在这里发声的球友" action="去发帖" @action="compose" />
      <template v-else>
        <view v-for="item in forum.posts" :key="item.id" class="post-row" @click="open(item.id)">
          <view class="post-head">
            <text class="category-chip">{{ item.category }}</text>
            <text class="post-date">{{ formatDate(item.created_at, false) }}</text>
          </view>
          <text class="post-title">{{ item.title }}</text>
          <text class="post-summary">{{ summaryOf(item.content) }}</text>
          <view class="post-foot"><text class="post-author">{{ authorOf(item.author_user_id) }}</text><AppIcon name="chevron-right" size="sm" /></view>
        </view>
        <view v-if="forum.posts.length" class="list-more">
          <button v-if="forum.hasMore" class="btn btn-secondary btn-small" :disabled="forum.loadingMore" @click="forum.loadMore()">{{ forum.loadingMore ? '正在加载' : '加载更多' }}</button>
          <text v-else class="list-end">已经看到全部帖子</text>
        </view>
      </template>
    </view>

    <view v-if="forum.error && forum.listStatus === 'success'" class="notice notice-error">{{ forum.error }}</view>
    <button class="fab" aria-label="发帖" @click="compose"><AppIcon name="add" /></button>
  </view>
</template>

<style scoped lang="scss">
.forum-page { padding-bottom: calc(7rem + env(safe-area-inset-bottom)); }
.list-caption { display: block; margin: .85rem 0 .1rem; color: #66706b; font-size: .75rem; line-height: 1.5; }
.forum-list { margin-top: .35rem; }
.post-row { display: flex; min-height: 6.5rem; flex-direction: column; gap: .3rem; padding: .9rem 0; border-bottom: 1px solid #e2e7e3; }
.post-head { display: flex; align-items: center; justify-content: space-between; gap: .75rem; }
.category-chip { display: inline-flex; min-height: 1.4rem; align-items: center; padding: 0 .5rem; color: #365148; background: #eef3f0; border-radius: 999px; font-size: .6875rem; font-weight: 700; }
.post-date { color: #87908b; font-size: .75rem; }
.post-title { display: block; overflow: hidden; color: #18201d; font-size: 1rem; font-weight: 700; line-height: 1.4; text-overflow: ellipsis; white-space: nowrap; }
.post-summary { display: -webkit-box; overflow: hidden; color: #66706b; font-size: .8125rem; line-height: 1.5; -webkit-box-orient: vertical; -webkit-line-clamp: 2; }
.post-foot { display: flex; align-items: center; justify-content: space-between; color: #525c57; font-size: .75rem; }
.post-author { color: #525c57; font-size: .75rem; }
.list-more { display: flex; min-height: 3.5rem; align-items: center; justify-content: center; }
.list-end { color: #87908b; font-size: .75rem; }
</style>
