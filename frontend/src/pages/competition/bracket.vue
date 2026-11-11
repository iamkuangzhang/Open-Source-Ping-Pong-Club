<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import AppHeader from '../../components/AppHeader.vue'
import MatchCard from '../../components/MatchCard.vue'
import EmptyState from '../../components/EmptyState.vue'
import LoadingState from '../../components/LoadingState.vue'
import ErrorState from '../../components/ErrorState.vue'
import StatusBadge from '../../components/StatusBadge.vue'
import { useAppStore } from '../../stores/app'
import { go } from '../../utils/ui'

const store = useAppStore()
const id = ref('')
const tab = ref<'bracket' | 'list'>('bracket')
const manage = ref(false)
const status = ref<'loading' | 'success' | 'empty' | 'error'>('loading')

async function load() {
  status.value = 'loading'
  try {
    await store.bootstrap()
    if (!id.value) throw new Error('缺少赛事标识')
    await store.selectCompetition(id.value)
    status.value = store.schedule.length ? 'success' : 'empty'
  } catch {
    status.value = 'error'
  }
}

onLoad((query) => {
  id.value = String(query?.id || '')
  manage.value = query?.manage === '1'
  load()
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
const totalMatches = computed(() => rounds.value.reduce((sum, item) => sum + item.matches.length, 0))
const longSchedule = computed(() => (rounds.value[0]?.matches.length || 0) > 8)

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
    <LoadingState v-if="status === 'loading'" variant="list" text="正在加载赛程" :rows="3" />
    <ErrorState v-else-if="status === 'error'" title="暂时无法加载赛程" description="请稍后重试，赛事信息仍可查看" @retry="load" />
    <EmptyState v-else-if="status === 'empty'" title="赛程尚未生成" :description="manage ? '报名关闭后可生成对阵，生成后本页自动展示' : '赛事组织方完成编排后会在这里展示对阵'" />
    <template v-else>
    <view class="segment"><button :class="['segment-item', { active: tab === 'bracket' }]" @click="tab = 'bracket'">对阵图</button><button :class="['segment-item', { active: tab === 'list' }]" @click="tab = 'list'">场次列表</button></view>
    <text class="schedule-tip">共 {{ totalMatches }} 场{{ longSchedule ? '，长赛程可左右滑动并上下滚动查看' : '' }}</text>
    <view v-if="manage && !store.canManage" class="notice notice-error">当前账号没有赛事管理权限，仅可查看对阵，无法录入比分。</view>
    <scroll-view v-if="tab === 'bracket'" class="bracket-scroll" scroll-x enhanced show-scrollbar="false">
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
    </template>
  </view>
</template>

<style scoped lang="scss">
.bracket-page { overflow-x: visible; }
.schedule-tip { display: block; margin: .7rem 0 .1rem; color: #66706b; font-size: .75rem; line-height: 1.5; }
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
