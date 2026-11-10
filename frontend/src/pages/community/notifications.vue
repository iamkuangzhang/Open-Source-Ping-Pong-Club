<script setup lang="ts">
import { computed, onMounted } from 'vue'
import AppHeader from '../../components/AppHeader.vue'
import EmptyState from '../../components/EmptyState.vue'
import ErrorState from '../../components/ErrorState.vue'
import LoadingState from '../../components/LoadingState.vue'
import { useAppStore } from '../../stores/app'
import type { AppNotification } from '../../stores/app'
import { formatDate, go } from '../../utils/ui'

const store = useAppStore()
const unread = computed(() => store.unreadNotificationCount)
const total = computed(() => store.notifications.length)
const subtitle = computed(() => total.value
  ? `共 ${total.value} 条 · 未读 ${unread.value} 条`
  : '报名、审核和比赛通知会显示在这里')

onMounted(async () => {
  store.success = ''
  store.error = ''
  await store.bootstrap()
})

async function markRead(item: AppNotification) {
  if (item.read_at) return
  try {
    await store.readNotification(item.id)
  } catch {
    uni.showToast({ title: '标记已读失败，请稍后重试', icon: 'none' })
  }
}

async function markAll() {
  if (!unread.value) return
  try {
    await store.readAllNotifications()
    uni.showToast({ title: '已全部标记为已读', icon: 'none' })
  } catch {
    uni.showToast({ title: '操作失败，请稍后重试', icon: 'none' })
  }
}

async function retry() {
  await store.loadPrivate().catch(() => undefined)
}
</script>

<template>
  <view class="app-shell no-tabs">
    <AppHeader
      title="通知中心"
      :subtitle="subtitle"
      backable
      :action="unread ? '全部已读' : undefined"
      @action="markAll"
    />

    <EmptyState
      v-if="!store.signedIn"
      title="登录后查看通知"
      description="通知与账号绑定，仅本人可见"
      icon="notification"
      action="去登录"
      @action="go('/pages/auth/login')"
    />
    <template v-else>
      <view v-if="store.error" class="notice notice-error">{{ store.error }}</view>
      <view v-if="unread" class="unread-tip">你有 {{ unread }} 条未读通知，点击单条即可标记为已读</view>

      <LoadingState v-if="store.notificationsStatus === 'loading'" variant="list" :rows="3" text="正在加载通知" />
      <ErrorState
        v-else-if="store.notificationsStatus === 'error'"
        title="暂时无法加载通知"
        description="请稍后重试"
        @retry="retry"
      />
      <EmptyState
        v-else-if="!store.notifications.length"
        title="暂无通知"
        description="报名、审核和比赛通知会显示在这里"
      />
      <view v-else class="notification-list">
        <view
          v-for="item in store.notifications"
          :key="item.id"
          :class="['notification', { unread: !item.read_at }]"
          @click="markRead(item)"
        >
          <view class="dot" />
          <view class="notification-main">
            <view class="notification-head">
              <text class="row-title">{{ item.title }}</text>
              <text v-if="!item.read_at" class="unread-badge">未读</text>
            </view>
            <text class="content">{{ item.content }}</text>
            <text class="time">{{ formatDate(item.created_at) }}</text>
          </view>
        </view>
      </view>

      <text v-if="store.notifications.length" class="footnote">通知按账号隔离，仅显示当前登录账号的记录，已读状态仅影响本人。</text>
    </template>
  </view>
</template>

<style scoped>
.unread-tip { margin: .5rem 0 .25rem; padding: .6rem .75rem; color: #856816; background: #faf3d7; border-radius: .55rem; font-size: .8125rem; line-height: 1.5; }
.notification-list { margin-top: .5rem; border-top: 1px solid #e2e7e3; }
.notification { display: grid; padding: 1rem 0; border-bottom: 1px solid #e2e7e3; grid-template-columns: .75rem 1fr; }
.notification.unread .row-title { color: #173f35; }
.dot { width: .4rem; height: .4rem; margin-top: .45rem; border-radius: 50%; background: transparent; }
.notification.unread .dot { background: #c9a742; }
.notification-main { min-width: 0; }
.notification-head { display: flex; align-items: center; gap: .4rem; }
.unread-badge { flex: 0 0 auto; padding: 0 .4rem; color: #856816; background: #faf3d7; border-radius: 999px; font-size: .6875rem; font-weight: 700; line-height: 1.5; }
.content, .time { display: block; margin-top: .35rem; color: #66706b; font-size: .8125rem; line-height: 1.55; }
.time { color: #87908b; font-size: .75rem; }
.footnote { display: block; margin-top: 1.25rem; color: #87908b; font-size: .75rem; line-height: 1.5; }
</style>
