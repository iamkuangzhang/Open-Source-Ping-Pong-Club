<script setup lang="ts">
import { computed, onMounted } from 'vue'
import AppHeader from '../../components/AppHeader.vue'
import EmptyState from '../../components/EmptyState.vue'
import LoadingState from '../../components/LoadingState.vue'
import { useAppStore } from '../../stores/app'
import { formatDate } from '../../utils/ui'
const store = useAppStore()
onMounted(() => store.bootstrap())
const rank = computed(() => store.rating ? store.leaderboard.findIndex((item) => item.user_id === store.rating?.user_id) + 1 : 0)
const delta = (item: any) => item.rating_delta ?? item.delta ?? 0
const reason = (item: any) => item.reason || (item.source_type === 'INITIAL' ? '初始积分' : '比赛积分')
</script>

<template>
  <view class="app-shell no-tabs">
    <AppHeader title="我的积分" backable />
    <LoadingState v-if="store.ratingSummaryStatus === 'loading'" text="正在加载积分" />
    <view v-else-if="store.rating" class="rating-summary"><view><text class="summary-label">当前积分</text><text class="summary-score">{{ store.rating.current_rating }}</text></view><view class="summary-meta"><text>最高 {{ store.rating.highest_rating }}</text><text>排名 #{{ rank || '—' }}</text><text>{{ store.rating.rated_matches }} 场</text></view></view>
    <view class="section"><text class="section-title">积分记录</text><EmptyState v-if="!store.ratingHistory.length" title="暂无积分变化" /><view v-else class="ledger"><view v-for="item in store.ratingHistory" :key="item.id" class="list-row"><view><text class="row-title">{{ reason(item) }}</text><text class="row-meta">{{ formatDate(item.created_at) }}</text></view><view class="ledger-value"><text :class="delta(item) >= 0 ? 'up' : 'down'">{{ delta(item) >= 0 ? '+' : '' }}{{ delta(item) }}</text><text class="row-meta">积分 {{ (item as any).rating_after ?? item.after_rating }}</text></view></view></view></view>
  </view>
</template>

<style scoped>
.rating-summary { display: flex; align-items: flex-end; justify-content: space-between; gap: 1rem; padding: 1.2rem 0; border-bottom: 1px solid #e2e7e3; }
.summary-label { display: block; color: #66706b; font-size: .8125rem; }
.summary-score { display: block; margin-top: .15rem; color: #173f35; font-size: 2.25rem; font-weight: 800; }
.summary-meta { display: flex; flex-wrap: wrap; justify-content: flex-end; gap: .35rem .8rem; color: #66706b; font-size: .75rem; }
.ledger { margin-top: .75rem; }
.ledger-value { text-align: right; }
.up { color: #3e7c63; font-weight: 800; }
.down { color: #c64d47; font-weight: 800; }
</style>
