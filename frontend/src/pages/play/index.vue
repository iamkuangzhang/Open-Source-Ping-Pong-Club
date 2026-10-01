<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useAppStore, type Post } from '../../stores/app'
import AppHeader from '../../components/AppHeader.vue'
import AppIcon from '../../components/AppIcon.vue'
import AppTabBar from '../../components/AppTabBar.vue'
import StatusBadge from '../../components/StatusBadge.vue'
import SectionHeader from '../../components/SectionHeader.vue'
import LoadingState from '../../components/LoadingState.vue'
import EmptyState from '../../components/EmptyState.vue'
import ErrorState from '../../components/ErrorState.vue'
import { formatDate, go } from '../../utils/ui'

const store = useAppStore()
const filter = ref<'ALL' | 'OPEN' | 'FULL'>('ALL')
const filters = [['ALL', '全部'], ['OPEN', '可加入'], ['FULL', '已满']] as const
const isFull = (item: Post) => Boolean(item.max_participants && Number(item.participant_count || 0) >= item.max_participants)
const isClosed = (item: Post) => ['CANCELLED', 'ENDED', 'CLOSED'].includes(item.status || '')
const visible = computed(() => store.playPosts.filter((item) => filter.value === 'ALL' || (filter.value === 'FULL' ? isFull(item) : !isFull(item) && !isClosed(item))))

onMounted(() => store.bootstrap())
async function join(id: string) {
  if (!store.signedIn) return go('/pages/auth/login')
  try { await store.joinPlayPost(id) } catch { /* Store renders feedback. */ }
}
</script>

<template>
  <view class="app-shell play-home">
    <AppHeader title="约球" subtitle="找人打一场球" />
    <view class="segment" role="tablist"><button v-for="item in filters" :key="item[0]" :class="['segment-item', { active: filter === item[0] }]" @click="filter = item[0]">{{ item[1] }}</button></view>

    <view class="section play-section">
      <SectionHeader title="近期约球" subtitle="按时间查看可参加的活动" />
      <view class="section-content play-list">
        <LoadingState v-if="store.playPostsStatus === 'loading'" variant="list" text="正在加载约球" :rows="3" />
        <ErrorState v-else-if="store.playPostsStatus === 'error'" title="暂时无法加载约球" description="请稍后重试，创建入口仍可使用" @retry="store.loadPlayPosts()" />
        <EmptyState v-else-if="!visible.length" title="暂无约球" description="点击右下角发起一场练球" />
        <template v-else>
          <view v-for="item in visible" :key="item.id" class="play-card" @click="go('/pages/play/detail', { id: item.id })">
            <view class="play-head"><text class="play-title">{{ item.title }}</text><StatusBadge v-if="item.joined || isFull(item) || isClosed(item)" :status="item.joined ? 'ACTIVE' : isFull(item) ? 'SOLD' : item.status" :label="item.joined ? '已加入' : isFull(item) ? '已满' : undefined" /></view>
            <view class="play-meta"><AppIcon name="calendar" size="sm" muted /><text>{{ formatDate(item.event_time) }}</text></view>
            <view class="play-meta"><AppIcon name="location" size="sm" muted /><text>{{ item.location || '地点待定' }}</text></view>
            <view class="play-bottom">
              <view class="play-info"><text class="level">不限水平</text><view class="count"><AppIcon name="people" size="sm" muted /><text>{{ item.participant_count || 0 }} / {{ item.max_participants || '—' }} 人</text></view></view>
              <button v-if="!isFull(item) && !isClosed(item)" class="btn btn-primary btn-small" @click.stop="join(item.id)">加入</button>
              <view v-else class="detail-link"><text>查看详情</text><AppIcon name="chevron-right" size="sm" /></view>
            </view>
          </view>
        </template>
      </view>
    </view>

    <button class="fab" aria-label="发起约球" @click="go(store.signedIn ? '/pages/play/create' : '/pages/auth/login')"><AppIcon name="add" /></button>
    <AppTabBar active="play" />
  </view>
</template>

<style scoped lang="scss">
.play-section { margin-top: 1.25rem; }
.section-content { margin-top: .625rem; }
.play-list { display: flex; flex-direction: column; gap: .7rem; }
.play-card { padding: .9375rem; background: #fff; border: 1px solid #e2e7e3; border-radius: .875rem; }
.play-head, .play-bottom { display: flex; align-items: center; justify-content: space-between; gap: .75rem; }
.play-title { min-width: 0; flex: 1; color: #18201d; font-size: 1rem; font-weight: 700; line-height: 1.4; overflow-wrap: anywhere; }
.play-meta { display: flex; align-items: center; gap: .4rem; margin-top: .5rem; color: #66706b; font-size: .8125rem; }
.play-bottom { margin-top: .8rem; padding-top: .72rem; border-top: 1px solid #edf0ee; }
.play-info { display: flex; align-items: center; gap: .75rem; }
.level { color: #456059; font-size: .75rem; font-weight: 700; }
.count, .detail-link { display: flex; align-items: center; gap: .25rem; color: #66706b; font-size: .8125rem; }
.detail-link { color: #173f35; font-weight: 700; }
</style>
