<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import AppHeader from '../../components/AppHeader.vue'
import MatchCard from '../../components/MatchCard.vue'
import EmptyState from '../../components/EmptyState.vue'
import LoadingState from '../../components/LoadingState.vue'
import ErrorState from '../../components/ErrorState.vue'
import { api } from '../../services/api'
import { useAppStore } from '../../stores/app'
import { go } from '../../utils/ui'

type GroupRow = { id: string; name: string; group_order: number; user_id?: string; position?: number }
const store = useAppStore()
const id = ref('')
const active = ref('')
const rows = ref<GroupRow[]>([])
const manage = ref(false)
const status = ref<'loading' | 'success' | 'empty' | 'error'>('loading')

async function load() {
  status.value = 'loading'
  try {
    await store.bootstrap()
    if (!id.value) throw new Error('缺少赛事标识')
    await store.selectCompetition(id.value)
    const groupRows = await api.get<GroupRow[]>(`/competitions/${id.value}/groups`)
    rows.value = groupRows || []
    active.value = rows.value[0]?.id || ''
    status.value = rows.value.length ? 'success' : 'empty'
  } catch {
    rows.value = []
    status.value = 'error'
  }
}

onLoad((query) => {
  id.value = String(query?.id || '')
  manage.value = query?.manage === '1'
  load()
})

const groups = computed(() => Array.from(new Map(rows.value.map((row) => [row.id, { id: row.id, name: row.name }])).values()))
const members = computed(() => rows.value.filter((row) => row.id === active.value))
const matches = computed(() => store.schedule.filter((match) => match.group_id === active.value))
function stats(userId?: string) {
  const mine = matches.value.filter((match) => match.player_a_user_id === userId || match.player_b_user_id === userId)
  const completed = mine.filter((match) => match.status === 'COMPLETED')
  const wins = completed.filter((match) => match.winner_user_id === userId).length
  return { wins, losses: completed.length - wins, played: completed.length }
}
</script>

<template>
  <view class="app-shell no-tabs">
    <AppHeader title="小组赛" subtitle="排名与本组赛程" backable />
    <LoadingState v-if="status === 'loading'" variant="list" text="正在加载小组赛程" :rows="3" />
    <ErrorState v-else-if="status === 'error'" title="暂时无法加载小组赛程" description="请稍后重试，赛事信息仍可查看" @retry="load" />
    <EmptyState v-else-if="status === 'empty'" title="小组赛程尚未生成" :description="manage ? '报名关闭后可生成分组，生成后本页自动展示' : '赛事组织方完成分组后会在这里展示名单与对阵'" />
    <template v-else>
      <view v-if="manage && !store.canManage" class="notice notice-error">当前账号没有赛事管理权限，仅可查看名单与赛程。</view>
      <scroll-view v-if="groups.length" class="group-tabs" scroll-x><view class="group-tab-inner"><button v-for="group in groups" :key="group.id" :class="['group-tab', { active: active === group.id }]" @click="active = group.id">{{ group.name }}</button></view></scroll-view>
      <EmptyState v-else title="本组暂无名单" description="分组已生成，但该组还没有选手记录" />
      <template v-if="groups.length">
        <scroll-view class="ranking-scroll" scroll-x><view class="ranking"><view class="ranking-head"><text>排名</text><text>球员</text><text>赛</text><text>胜</text><text>负</text></view><view v-for="(member, index) in members" :key="member.user_id" class="ranking-row"><text>{{ index + 1 }}</text><text>球友 {{ member.user_id?.slice(0, 5) }}</text><text>{{ stats(member.user_id).played }}</text><text>{{ stats(member.user_id).wins }}</text><text>{{ stats(member.user_id).losses }}</text></view></view></scroll-view>
        <text class="ranking-tip">排名按已完赛场次的胜场数排列，长名单可左右滑动查看。</text>
        <view class="section"><text class="section-title">本组比赛</text><view v-if="!matches.length" class="empty-hint">本组赛程尚未生成</view><view v-else class="match-list"><MatchCard v-for="item in matches" :key="item.id" :item="item" @open="go(manage ? '/pages/match/scoring' : '/pages/match/detail', { id: item.id, competitionId: id })" /></view></view>
      </template>
    </template>
  </view>
</template>

<style scoped>
.group-tabs { margin-top: .5rem; white-space: nowrap; }
.group-tab-inner { display: flex; gap: 1.2rem; border-bottom: 1px solid #e2e7e3; }
.group-tab { min-width: 3rem; min-height: 2.75rem; padding: 0; color: #66706b; background: transparent; border-bottom: 2px solid transparent; font-size: .875rem; font-weight: 600; }
.group-tab.active { color: #173f35; border-bottom-color: #173f35; font-weight: 700; }
.ranking-scroll { width: 100%; margin-top: 1rem; }
.ranking { min-width: 24rem; overflow: hidden; background: #fff; border-top: 1px solid #e2e7e3; border-bottom: 1px solid #e2e7e3; }
.ranking-head, .ranking-row { display: grid; min-height: 3rem; align-items: center; padding: 0 .75rem; grid-template-columns: 3rem 1fr 3rem 3rem 3rem; font-size: .8125rem; }
.ranking-head { color: #66706b; background: #f1f4f2; font-size: .75rem; }
.ranking-row { border-top: 1px solid #e7eae8; }
.ranking-tip { display: block; margin-top: .5rem; color: #66706b; font-size: .75rem; line-height: 1.5; }
.empty-hint { display: block; margin-top: .6rem; color: #66706b; font-size: .8125rem; }
.match-list { display: flex; flex-direction: column; gap: .7rem; margin-top: .75rem; }
</style>
