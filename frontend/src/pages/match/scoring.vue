<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import AppHeader from '../../components/AppHeader.vue'
import BottomAction from '../../components/BottomAction.vue'
import LoadingState from '../../components/LoadingState.vue'
import { api } from '../../services/api'
import { useAppStore, type Match } from '../../stores/app'
import { back } from '../../utils/ui'

const store = useAppStore()
const item = ref<Match | null>(null)
const games = reactive(Array.from({ length: 7 }, () => ({ a: '', b: '' })))
onLoad(async (query) => { store.initApi(); item.value = await api.get(`/matches/${query?.id}`) })
const needed = computed(() => item.value ? Math.floor(item.value.best_of / 2) + 1 : 2)
const entered = computed(() => games.filter((game) => game.a !== '' && game.b !== '').map((game) => ({ a: Number(game.a), b: Number(game.b) })))
function winner(a: number, b: number) { return Math.max(a, b) >= 11 && Math.abs(a - b) >= 2 ? (a > b ? 0 : 1) : -1 }
const matchScore = computed(() => entered.value.reduce((score, game) => { const index = winner(game.a, game.b); if (index >= 0) score[index]++; return score }, [0, 0]))
const playerName = (userId: string | undefined, fallback: string) => userId ? `球友 ${userId.slice(0, 5)}` : fallback

async function submit() {
  if (!item.value) return
  if (entered.value.some((game) => winner(game.a, game.b) < 0) || Math.max(...matchScore.value) < needed.value) {
    uni.showToast({ title: '请录入合法且完整的比赛结果', icon: 'none' })
    return
  }
  try { await store.scoreMatch(item.value, entered.value); back() } catch { /* Store renders feedback. */ }
}
</script>

<template>
  <view class="app-shell no-tabs scoring">
    <AppHeader title="比分录入" subtitle="每局至少 11 分并领先 2 分" backable />
    <LoadingState v-if="!item" />
    <template v-else>
      <text class="round-label">第 {{ item.round_number }} 轮 · {{ item.best_of }} 局制</text>
      <view class="scoreboard">
        <view class="player-side"><text class="player-name">{{ playerName(item.player_a_user_id, '选手 A') }}</text><text class="player-code">选手 A</text></view>
        <view class="live-score"><b>{{ matchScore[0] }}</b><span>:</span><b>{{ matchScore[1] }}</b><text>当前大比分</text></view>
        <view class="player-side right"><text class="player-name">{{ playerName(item.player_b_user_id, '选手 B') }}</text><text class="player-code">选手 B</text></view>
      </view>
      <view class="games">
        <view v-for="(_, index) in games.slice(0, item.best_of)" :key="index" class="game-row"><text class="game-no">第 {{ index + 1 }} 局</text><input v-model="games[index].a" class="score-input" type="number" placeholder="—" /><text class="colon">:</text><input v-model="games[index].b" class="score-input" type="number" placeholder="—" /></view>
      </view>
      <view v-if="store.error" class="notice notice-error">{{ store.error }}</view>
      <BottomAction><button class="btn btn-primary btn-block" :disabled="store.loading" @click="submit">确认比赛结果</button></BottomAction>
    </template>
  </view>
</template>

<style scoped>
.scoring { padding-bottom: 6.5rem; }
.round-label { display: block; margin: .8rem 0; color: #66706b; text-align: center; font-size: .8125rem; }
.scoreboard { display: grid; align-items: center; padding: 1.2rem 0 1.4rem; grid-template-columns: 1fr 5.5rem 1fr; }
.player-side { min-width: 0; text-align: left; }
.player-side.right { text-align: right; }
.player-name { display: block; overflow: hidden; font-size: 1rem; font-weight: 800; text-overflow: ellipsis; white-space: nowrap; }
.player-code { display: block; margin-top: .3rem; color: #7b837f; font-size: .75rem; }
.live-score { display: grid; align-items: baseline; justify-content: center; grid-template-columns: 1fr 1rem 1fr; text-align: center; }
.live-score b { font-size: 1.75rem; }
.live-score span { color: #929995; }
.live-score text { grid-column: 1 / -1; margin-top: .2rem; color: #7b837f; font-size: .6875rem; }
.games { padding: 0 1rem; background: #fff; border: 1px solid #e2e7e3; border-radius: .875rem; }
.game-row { display: grid; min-height: 4rem; align-items: center; border-bottom: 1px solid #e7eae8; grid-template-columns: 1fr 3.5rem 1.5rem 3.5rem; text-align: center; }
.game-row:last-child { border-bottom: 0; }
.game-no { color: #66706b; text-align: left; font-size: .8125rem; }
.score-input { height: 2.75rem; background: #f2f4f2; border: 1px solid transparent; border-radius: .5rem; text-align: center; font-size: 1.125rem; font-weight: 700; }
.score-input:focus { background: #fff; border-color: #173f35; }
.colon { color: #8a928e; }
</style>
