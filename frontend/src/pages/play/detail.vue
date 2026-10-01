<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import AppHeader from '../../components/AppHeader.vue'
import BottomAction from '../../components/BottomAction.vue'
import StatusBadge from '../../components/StatusBadge.vue'
import LoadingState from '../../components/LoadingState.vue'
import { api } from '../../services/api'
import { useAppStore, type Post } from '../../stores/app'
import { formatDate, go } from '../../utils/ui'

const store = useAppStore()
const item = ref<Post | null>(null)
const full = computed(() => Boolean(item.value?.max_participants && Number(item.value?.participant_count || 0) >= item.value.max_participants))
const closed = computed(() => ['CANCELLED', 'ENDED', 'CLOSED'].includes(item.value?.status || ''))
onLoad(async (query) => { store.initApi(); item.value = await api.get(`/play-posts/${query?.id}`) })

async function toggleJoin() {
  if (!store.signedIn) return go('/pages/auth/login')
  if (!item.value) return
  try {
    if (item.value.joined) await store.leavePlayPost(item.value.id)
    else await store.joinPlayPost(item.value.id)
    item.value = await api.get(`/play-posts/${item.value.id}`)
  } catch { /* Store renders feedback. */ }
}
</script>

<template>
  <view class="app-shell no-tabs detail-page">
    <AppHeader title="约球详情" backable />
    <LoadingState v-if="!item" />
    <template v-else>
      <view class="title-line"><text class="page-title">{{ item.title }}</text><StatusBadge v-if="item.joined || full || closed" :status="item.joined ? 'ACTIVE' : full ? 'SOLD' : item.status" :label="item.joined ? '已加入' : full ? '已满' : undefined" /></view>
      <view class="info-list"><view><text>时间</text><b>{{ formatDate(item.event_time) }}</b></view><view><text>地点</text><b>{{ item.location || '地点待定' }}</b></view><view><text>人数</text><b>{{ item.participant_count || 0 }} / {{ item.max_participants || '—' }} 人</b></view><view><text>联系方式</text><b>{{ item.contact || '加入后联系发起人' }}</b></view></view>
      <view class="section"><text class="section-title">约球说明</text><text class="body-copy">{{ item.content }}</text></view>
      <view v-if="store.error" class="notice notice-error">{{ store.error }}</view>
      <BottomAction v-if="!closed"><button v-if="!full || item.joined" :class="['btn btn-block', item.joined ? 'btn-secondary' : 'btn-primary']" @click="toggleJoin">{{ item.joined ? '退出约球' : '加入约球' }}</button></BottomAction>
    </template>
  </view>
</template>

<style scoped>
.detail-page { padding-bottom: 6.5rem; }
.title-line { display: flex; align-items: flex-start; justify-content: space-between; gap: 1rem; padding: 1rem 0 1.5rem; }
.info-list { padding: 0 1rem; background: #fff; border-top: 1px solid #e2e7e3; border-bottom: 1px solid #e2e7e3; }
.info-list view { display: flex; min-height: 3.5rem; align-items: center; justify-content: space-between; gap: 1rem; border-bottom: 1px solid #e7eae8; font-size: .8125rem; }
.info-list view:last-child { border-bottom: 0; }
.info-list text { color: #66706b; }
.info-list b { max-width: 68%; text-align: right; font-weight: 600; }
.body-copy { display: block; margin-top: .75rem; }
</style>
