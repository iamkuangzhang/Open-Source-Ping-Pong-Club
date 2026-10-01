<script setup lang="ts">
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import AppHeader from '../../components/AppHeader.vue'
import StatusBadge from '../../components/StatusBadge.vue'
import LoadingState from '../../components/LoadingState.vue'
import type { Match } from '../../stores/app'
import { api } from '../../services/api'

const item = ref<Match | null>(null)
onLoad(async (query) => { item.value = await api.get(`/matches/${query?.id}`) })
const playerName = (id?: string) => id ? `球友 ${id.slice(0, 5)}` : 'BYE'
const resultText = (value?: string) => ({ NORMAL: '正常完赛', WALKOVER: '弃权胜', BYE: '轮空晋级' } as Record<string, string>)[value || ''] || '等待比赛'
</script>

<template>
  <view class="app-shell no-tabs">
    <AppHeader title="场次详情" backable />
    <LoadingState v-if="!item" />
    <template v-else>
      <view class="match-title"><text>第 {{ item.round_number }} 轮 · 第 {{ item.match_number }} 场</text><StatusBadge :status="item.status" /></view>
      <view class="versus">
        <view :class="{ winner: item.winner_user_id === item.player_a_user_id }"><text class="name">{{ playerName(item.player_a_user_id) }}</text><text class="score">{{ item.player_a_games ?? '—' }}</text></view>
        <text class="versus-mark">:</text>
        <view :class="{ winner: item.winner_user_id === item.player_b_user_id }"><text class="name">{{ playerName(item.player_b_user_id) }}</text><text class="score">{{ item.player_b_games ?? '—' }}</text></view>
      </view>
      <view class="detail-list"><view class="list-row"><text>赛制</text><text>{{ item.best_of }} 局制</text></view><view class="list-row"><text>比赛结果</text><text>{{ resultText(item.result_type) }}</text></view></view>
    </template>
  </view>
</template>

<style scoped>
.match-title { display: flex; align-items: center; justify-content: space-between; padding: 1rem 0; font-size: .875rem; font-weight: 700; }
.versus { display: grid; align-items: center; padding: 2rem 0 2.25rem; background: #fff; border-top: 1px solid #e2e7e3; border-bottom: 1px solid #e2e7e3; grid-template-columns: 1fr 2rem 1fr; text-align: center; }
.versus view { display: flex; min-width: 0; flex-direction: column; gap: 1rem; }
.name { overflow: hidden; font-size: 1rem; font-weight: 700; text-overflow: ellipsis; white-space: nowrap; }
.score { font-size: 2rem; font-weight: 800; }
.winner .name, .winner .score { color: #173f35; }
.versus-mark { color: #9aa19e; }
.detail-list { margin-top: 1.5rem; }
</style>
