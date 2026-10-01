<script setup lang="ts">
import { computed, onMounted } from 'vue'
import AppHeader from '../../components/AppHeader.vue'
import EmptyState from '../../components/EmptyState.vue'
import LoadingState from '../../components/LoadingState.vue'
import { useAppStore } from '../../stores/app'
import { formatDate } from '../../utils/ui'
const store = useAppStore()
onMounted(() => store.bootstrap())
const hasUnread = computed(() => store.notifications.some((item) => !item.read_at))
</script>

<template>
  <view class="app-shell no-tabs">
    <AppHeader title="通知中心" backable :action="hasUnread ? '全部已读' : undefined" @action="store.readAllNotifications()" />
    <LoadingState v-if="store.profileStatus === 'loading'" text="正在加载通知" />
    <EmptyState v-else-if="!store.notifications.length" title="暂无通知" description="报名、审核和比赛通知会显示在这里" />
    <view v-else class="notification-list"><view v-for="item in store.notifications" :key="item.id" :class="['notification', { unread: !item.read_at }]" @click="!item.read_at && store.readNotification(item.id)"><view class="dot" /><view><text class="row-title">{{ item.title }}</text><text class="content">{{ item.content }}</text><text class="time">{{ formatDate((item as any).created_at) }}</text></view></view></view>
  </view>
</template>

<style scoped>
.notification-list { margin-top: .5rem; border-top: 1px solid #e2e7e3; }
.notification { display: grid; padding: 1rem 0; border-bottom: 1px solid #e2e7e3; grid-template-columns: .75rem 1fr; }
.notification.unread .row-title { color: #173f35; }
.dot { width: .4rem; height: .4rem; margin-top: .45rem; border-radius: 50%; background: transparent; }
.notification.unread .dot { background: #c9a742; }
.content, .time { display: block; margin-top: .35rem; color: #66706b; font-size: .8125rem; line-height: 1.55; }
.time { color: #87908b; font-size: .75rem; }
</style>
