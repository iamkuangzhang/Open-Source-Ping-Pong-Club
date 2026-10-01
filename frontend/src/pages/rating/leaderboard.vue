<script setup lang="ts">
import { onMounted } from 'vue'
import AppHeader from '../../components/AppHeader.vue'
import RatingRow from '../../components/RatingRow.vue'
import LoadingState from '../../components/LoadingState.vue'
import EmptyState from '../../components/EmptyState.vue'
import ErrorState from '../../components/ErrorState.vue'
import { useAppStore } from '../../stores/app'
const store = useAppStore()
onMounted(() => store.bootstrap())
</script>

<template>
  <view class="app-shell no-tabs">
    <AppHeader title="积分榜" subtitle="正式成员积分排名" backable />
    <text class="rank-note">积分赛最终确认后更新，同分时按有效比赛场次排序。</text>
    <LoadingState v-if="store.leaderboardStatus === 'loading'" variant="ranking" text="正在加载积分榜" />
    <ErrorState v-else-if="store.leaderboardStatus === 'error'" title="暂时无法加载积分榜" @retry="store.loadLeaderboard()" />
    <EmptyState v-else-if="!store.leaderboard.length" title="暂无积分排名" />
    <view v-else class="rating-list"><RatingRow v-for="(item, index) in store.leaderboard" :key="item.user_id" :item="item" :rank="index + 1" /></view>
  </view>
</template>

<style scoped>
.rank-note { display: block; padding: .75rem 0 1rem; color: #66706b; font-size: .75rem; line-height: 1.5; }
.rating-list { padding: 0 1rem; background: #fff; border-top: 1px solid #e2e7e3; border-bottom: 1px solid #e2e7e3; }
</style>
