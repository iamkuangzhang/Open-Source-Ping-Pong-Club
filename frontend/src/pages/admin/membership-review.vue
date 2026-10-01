<script setup lang="ts">
import { onMounted, ref } from 'vue'
import AppHeader from '../../components/AppHeader.vue'
import StatusBadge from '../../components/StatusBadge.vue'
import EmptyState from '../../components/EmptyState.vue'
import LoadingState from '../../components/LoadingState.vue'
import { useAppStore, type MembershipApplication } from '../../stores/app'

const store = useAppStore()
const selected = ref<MembershipApplication | null>(null)
onMounted(() => store.bootstrap())
async function review(approve: boolean) { if (!selected.value) return; try { await store.reviewApplication(selected.value.id, approve); selected.value = null } catch { /* Store renders feedback. */ } }
</script>

<template>
  <view class="app-shell no-tabs">
    <AppHeader title="成员审核" subtitle="核对申请资料后完成审核" backable />
    <LoadingState v-if="store.loading && !store.adminApplications.length" text="正在加载申请" />
    <EmptyState v-else-if="!store.adminApplications.length" title="暂无待审核申请" description="新的申请会显示在这里" />
    <view v-else class="review-list"><view v-for="item in store.adminApplications" :key="item.id" class="application" @click="selected = item"><view><text class="row-title">{{ item.realName || item.real_name || item.nickname || '申请人' }}</text><text class="row-meta">申请加入开源乓协</text></view><StatusBadge :status="item.status" /></view></view>
    <view v-if="selected" class="sheet-mask" @click.self="selected = null"><view class="sheet"><view class="sheet-handle" /><text class="page-title">审核申请</text><view class="applicant"><text class="row-title">{{ selected.realName || selected.real_name || selected.nickname }}</text><text class="row-meta">当前状态：{{ selected.status }}</text></view><text class="review-note">请确认申请信息。审核通过后，申请人将成为正式成员并获得初始积分。</text><view class="sheet-actions"><button class="btn btn-secondary" @click="review(false)">拒绝</button><button class="btn btn-primary" @click="review(true)">通过</button></view></view></view>
  </view>
</template>

<style scoped>
.review-list { border-top: 1px solid #e2e7e3; }
.application { display: flex; min-height: 4.5rem; align-items: center; justify-content: space-between; gap: 1rem; padding: .7rem 0; border-bottom: 1px solid #e2e7e3; }
.sheet-mask { position: fixed; z-index: 60; inset: 0; display: flex; align-items: flex-end; background: rgba(18, 25, 22, .35); }
.sheet { width: 100%; max-width: 30rem; margin: 0 auto; padding: 1rem 1rem calc(1rem + env(safe-area-inset-bottom)); background: #fff; border-radius: 1rem 1rem 0 0; }
.sheet-handle { width: 2.5rem; height: .25rem; margin: 0 auto 1.25rem; border-radius: 999px; background: #d9ddda; }
.applicant { margin-top: 1.25rem; padding: 1rem; background: #f5f6f4; border-radius: .75rem; }
.review-note { display: block; margin: 1rem 0; color: #66706b; font-size: .8125rem; line-height: 1.6; }
.sheet-actions { display: flex; gap: .75rem; }
.sheet-actions .btn { flex: 1; }
</style>
