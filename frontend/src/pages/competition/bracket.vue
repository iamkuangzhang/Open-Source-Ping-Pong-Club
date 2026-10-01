<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import AppHeader from '../../components/AppHeader.vue'
import MatchCard from '../../components/MatchCard.vue'
import EmptyState from '../../components/EmptyState.vue'
import StatusBadge from '../../components/StatusBadge.vue'
import { useAppStore } from '../../stores/app'
import { go } from '../../utils/ui'

const store = useAppStore()
const id = ref('')
const tab = ref<'bracket' | 'list'>('bracket')
const manage = ref(false)

onLoad(async (query) => {
  id.value = String(query?.id || '')
  manage.value = query?.manage === '1'
  await store.bootstrap()
  await store.selectCompetition(id.value)
})

const rounds = computed(() => {
  const grouped = new Map<number, typeof store.schedule>()
  store.schedule.filter((match) => !match.group_id).forEach((match) => {
    const matches = grouped.get(match.round_number) || []
    matches.push(match)
    grouped.set(match.round_number, matches)
  })
  return [...grouped.entries()].sort(([a], [b]) => a - b).map(([round, matches]) => ({ round, matches: matches.sort((a, b) => a.match_number - b.match_number) }))
})
const bracketHeight = computed(() => `${Math.max(1, rounds.value[0]?.matches.length || 1) * 6.75}rem`)

function label(round: number) {
  const max = Math.max(...rounds.value.map((item) => item.round), 1)
  const distance = max - round
  return distance === 0 ? '决赛' : distance === 1 ? '半决赛' : distance === 2 ? '四分之一决赛' : `第 ${round} 轮`
}
function playerName(userId?: string) { return userId ? `球友 ${userId.slice(0, 5)}` : 'BYE' }
function open(matchId: string) { go(manage.value ? '/pages/match/scoring' : '/pages/match/detail', { id: matchId, competitionId: id.value }) }
</script>

<template>
  <view class="app-shell no-tabs bracket-page">
    <AppHeader title="淘汰赛对阵" subtitle="左右滑动查看完整晋级路径" backable />
    <view class="segment"><button :class="['segment-item', { active: tab === 'bracket' }]" @click="tab = 'bracket'">对阵图</button><button :class="['segment-item', { active: tab === 'list' }]" @click="tab = 'list'">场次列表</button></view>
    <EmptyState v-if="!store.schedule.length" title="赛程尚未生成" />
    <scroll-view v-else-if="tab === 'bracket'" class="bracket-scroll" scroll-x enhanced show-scrollbar="false">
      <view class="bracket" :style="{ height: bracketHeight }">
        <view v-for="(roundItem, roundIndex) in rounds" :key="roundItem.round" class="round">
          <text class="round-title">{{ label(roundItem.round) }}</text>
          <view class="round-matches">
            <view v-for="item in roundItem.matches" :key="item.id" :class="['bracket-match', { playable: item.status === 'READY' || item.status === 'PLAYING' }]" @click="open(item.id)">
              <view class="match-head"><text>第 {{ item.match_number }} 场</text><StatusBadge :status="item.status" /></view>
              <view class="player" :class="{ winner: item.winner_user_id === item.player_a_user_id, bye: !item.player_a_user_id }"><text>{{ playerName(item.player_a_user_id) }}</text><b>{{ item.player_a_games ?? '—' }}</b></view>
              <view class="player" :class="{ winner: item.winner_user_id === item.player_b_user_id, bye: !item.player_b_user_id }"><text>{{ playerName(item.player_b_user_id) }}</text><b>{{ item.player_b_games ?? '—' }}</b></view>
              <view v-if="roundIndex < rounds.length - 1" class="connector" />
            </view>
          </view>
        </view>
      </view>
    </scroll-view>
    <view v-else class="section match-list"><MatchCard v-for="item in store.schedule" :key="item.id" :item="item" :label="label(item.round_number)" @open="open(item.id)" /></view>
  </view>
</template>

<style scoped lang="scss">
.bracket-page { overflow-x: visible; }
.bracket-scroll { width: calc(100% + 2rem); margin: 1rem -1rem 0; }
.bracket { display: flex; width: max-content; min-height: 30rem; gap: 2rem; padding: .25rem 1.25rem 1rem; }
.round { display: flex; width: 13.5rem; flex-direction: column; }
.round-title { display: block; height: 2.25rem; color: #52605a; font-size: .8125rem; font-weight: 700; }
.round-matches { display: flex; min-height: 0; flex: 1; flex-direction: column; justify-content: space-around; gap: .75rem; }
.bracket-match { position: relative; align-self: center; width: 100%; padding: .55rem .7rem; background: #fff; border: 1px solid #dfe5e1; border-radius: .75rem; }
.bracket-match.playable { border-color: #9bb6ac; box-shadow: 0 0 0 2px #eef3f0; }
.match-head { display: flex; min-height: 1.5rem; align-items: center; justify-content: space-between; color: #78817c; font-size: .6875rem; }
.player { display: flex; min-height: 2rem; align-items: center; justify-content: space-between; gap: .5rem; border-top: 1px solid #edf0ee; color: #48514d; font-size: .8125rem; }
.player text { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.player b { color: #29332f; font-size: .875rem; }
.player.winner { color: #173f35; font-weight: 800; }
.player.bye { color: #929995; font-style: italic; }
.connector { position: absolute; top: 50%; right: -2.05rem; width: 2rem; height: 1px; background: #bac4be; }
.match-list { display: flex; flex-direction: column; gap: .7rem; }
</style>
