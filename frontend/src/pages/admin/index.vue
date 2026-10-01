<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import AppHeader from '../../components/AppHeader.vue'
import AppIcon from '../../components/AppIcon.vue'
import CompetitionCard from '../../components/CompetitionCard.vue'
import EmptyState from '../../components/EmptyState.vue'
import { useAppStore } from '../../stores/app'
import { go } from '../../utils/ui'

const store = useAppStore()
const showAnnouncement = ref(false)
const announcement = reactive({ title: '', content: '', visibility: 'PUBLIC' })
onMounted(() => store.bootstrap())

async function publish() {
  if (!announcement.title || !announcement.content) {
    uni.showToast({ title: '请填写公告内容', icon: 'none' })
    return
  }
  try {
    await store.publishAnnouncement(announcement)
    showAnnouncement.value = false
    announcement.title = ''
    announcement.content = ''
  } catch {
    // Store renders the API error in context.
  }
}
</script>

<template>
  <view class="app-shell no-tabs">
    <AppHeader title="管理中心" backable />
    <view class="admin-menu">
      <view class="admin-row" @click="go('/pages/admin/membership-review')">
        <view><text class="row-title">成员审核</text><text class="row-meta">{{ store.adminApplications.length }} 项待处理</text></view>
        <AppIcon name="chevron-right" />
      </view>
      <view class="admin-row" @click="go('/pages/competition/create')">
        <view><text class="row-title">创建赛事</text><text class="row-meta">创建单淘汰或小组循环比赛</text></view>
        <AppIcon name="chevron-right" />
      </view>
      <view class="admin-row" @click="showAnnouncement = !showAnnouncement">
        <view><text class="row-title">公告管理</text><text class="row-meta">发布公开或成员公告</text></view>
        <text class="toggle-label">{{ showAnnouncement ? '收起' : '展开' }}</text>
      </view>
    </view>

    <view v-if="showAnnouncement" class="announcement-form">
      <view class="field"><text class="field-label">公告标题</text><input v-model="announcement.title" class="input" /></view>
      <view class="field"><text class="field-label">正文</text><textarea v-model="announcement.content" class="textarea" /></view>
      <view class="segment">
        <button :class="['segment-item', { active: announcement.visibility === 'PUBLIC' }]" @click="announcement.visibility = 'PUBLIC'">公开</button>
        <button :class="['segment-item', { active: announcement.visibility === 'MEMBERS_ONLY' }]" @click="announcement.visibility = 'MEMBERS_ONLY'">仅成员</button>
      </view>
      <button class="btn btn-primary btn-block publish" @click="publish">发布公告</button>
    </view>

    <view class="section">
      <text class="section-title">赛事管理</text>
      <EmptyState v-if="!store.competitions.length" title="暂无赛事" />
      <CompetitionCard v-for="item in store.competitions" :key="item.id" :item="item" management @open="go('/pages/competition/manage', { id: item.id })" />
    </view>
  </view>
</template>

<style scoped>
.admin-menu { padding: 0 1rem; background: #fff; border: 1px solid #e2e7e3; border-radius: .875rem; }
.admin-row { display: flex; min-height: 4.5rem; align-items: center; justify-content: space-between; gap: .75rem; border-bottom: 1px solid #e7eae8; }
.admin-row:last-child { border-bottom: 0; }
.toggle-label { color: #173f35; font-size: .8125rem; font-weight: 700; }
.announcement-form { margin-top: 1rem; padding: 1rem; background: #fff; border: 1px solid #e2e7e3; border-radius: .875rem; }
.publish { margin-top: 1rem; }
</style>
