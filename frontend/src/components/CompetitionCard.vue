<script setup lang="ts">
import { computed } from 'vue'
import type { Competition } from '../stores/app'
import AppIcon from './AppIcon.vue'
import StatusBadge from './StatusBadge.vue'
import { competitionType, formatDate } from '../utils/ui'

const props = defineProps<{ item: Competition; count?: number; joined?: boolean; management?: boolean }>()
defineEmits<{ open: []; join: []; leave: [] }>()

const active = computed(() => ['ARRANGING', 'READY', 'RUNNING', 'RESULT_CONFIRMING'].includes(props.item.status))
const detailLabel = computed(() => props.item.status === 'FINISHED' ? '查看结果' : active.value ? '查看赛况' : '查看详情')
</script>

<template>
  <view :class="['competition-card', `status-${item.status.toLowerCase()}`]" @click="$emit('open')">
    <view class="card-top">
      <text class="type-label" :class="{ rated: (item.competition_type || item.type) === 'RATED' }">{{ competitionType(item.competition_type || item.type) }}</text>
      <StatusBadge :status="item.status" />
    </view>
    <text class="competition-title">{{ item.title }}</text>
    <view v-if="item.sport_name" class="meta-line sport-line"><text>{{ item.sport_name }}</text></view>
    <view class="meta-line"><AppIcon name="calendar" size="sm" muted /><text>{{ formatDate(item.start_time) }}</text></view>
    <view class="meta-line"><AppIcon name="location" size="sm" muted /><text>{{ item.location || '地点待定' }} · {{ item.best_of ? `${item.best_of}局制` : (item.format === 'ROUND_ROBIN' ? '小组循环' : '单淘汰') }}</text></view>
    <view class="card-bottom">
      <view class="participant-count"><AppIcon name="people" size="sm" muted /><text>{{ count === undefined ? `最多 ${item.max_participants || '—'} 人` : `${count} / ${item.max_participants || '—'} 人` }}</text></view>
      <view v-if="management" class="detail-link"><text>管理赛事</text><AppIcon name="chevron-right" size="sm" /></view>
      <button v-else-if="item.status === 'REGISTRATION'" :class="['btn btn-small', joined ? 'btn-secondary' : 'btn-primary']" @click.stop="$emit(joined ? 'leave' : 'join')">{{ joined ? '退出报名' : '报名' }}</button>
      <view v-else class="detail-link"><text>{{ detailLabel }}</text><AppIcon name="chevron-right" size="sm" /></view>
    </view>
  </view>
</template>

<style scoped lang="scss">
.competition-card { position: relative; padding: .9375rem; background: #fff; border: 1px solid #e2e7e3; border-radius: .875rem; }
.competition-card::before { content: ""; position: absolute; top: .9rem; bottom: .9rem; left: 0; width: 2px; border-radius: 0 2px 2px 0; background: transparent; }
.competition-card.status-registration::before { background: #c9a742; }
.competition-card.status-running::before, .competition-card.status-ready::before { background: #3e7c63; }
.card-top, .card-bottom { display: flex; align-items: center; justify-content: space-between; gap: .75rem; }
.type-label { color: #456059; font-size: .75rem; font-weight: 700; }
.type-label.rated { color: #8a6a12; }
.competition-title { display: block; margin: .6rem 0 .5rem; color: #18201d; font-size: 1rem; font-weight: 700; line-height: 1.4; overflow-wrap: anywhere; }
.meta-line { display: flex; min-width: 0; align-items: center; gap: .4rem; margin-top: .28rem; color: #66706b; font-size: .8125rem; line-height: 1.5; }
.meta-line text { min-width: 0; overflow-wrap: anywhere; }
.card-bottom { margin-top: .78rem; padding-top: .72rem; border-top: 1px solid #edf0ee; }
.participant-count, .detail-link { display: flex; align-items: center; gap: .3rem; color: #56615c; font-size: .8125rem; font-weight: 600; }
.detail-link { color: #173f35; font-weight: 700; }
</style>
