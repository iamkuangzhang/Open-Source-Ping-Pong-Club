<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useAppStore } from '../../stores/app'
import AppHeader from '../../components/AppHeader.vue'
import AppIcon from '../../components/AppIcon.vue'
import AppTabBar from '../../components/AppTabBar.vue'
import StatusBadge from '../../components/StatusBadge.vue'
import SectionHeader from '../../components/SectionHeader.vue'
import LoadingState from '../../components/LoadingState.vue'
import EmptyState from '../../components/EmptyState.vue'
import ErrorState from '../../components/ErrorState.vue'
import { formatDate, go } from '../../utils/ui'

const store = useAppStore()
const tab = ref<'ANNOUNCEMENT' | 'MARKET'>('ANNOUNCEMENT')
const brokenMarketImages = ref<Set<string>>(new Set())
const marketDescription = (content?: string) => (content || '器材详情以发布者说明为准').split(/[\n·]/)[0]
const marketPrice = (price?: string) => price ? (price.startsWith('¥') ? price : `¥${price}`) : '价格面议'
const sold = (status?: string) => status === 'SOLD'
const hasMarketImage = (item: { id: string; image_url?: string }) => Boolean(item.image_url && !brokenMarketImages.value.has(item.id))
const markMarketImageBroken = (id: string) => { brokenMarketImages.value = new Set([...brokenMarketImages.value, id]) }
const currentStatus = computed(() => tab.value === 'ANNOUNCEMENT' ? store.announcementsStatus : store.marketPostsStatus)
onMounted(() => store.bootstrap())
</script>

<template>
  <view class="app-shell community-home">
    <AppHeader title="社区" subtitle="公告与校园二手" action-icon="notification" action-label="通知中心" @action="go(store.signedIn ? '/pages/community/notifications' : '/pages/auth/login')" />
    <view class="forum-entry" @click="go('/pages/forum/index')"><view class="forum-entry-copy"><text class="forum-entry-title">球友论坛</text><text class="forum-entry-desc">发帖交流、约球组队与内容治理</text></view><AppIcon name="chevron-right" size="sm" /></view>
    <view class="segment community-tabs" role="tablist"><button :class="['segment-item', { active: tab === 'ANNOUNCEMENT' }]" @click="tab = 'ANNOUNCEMENT'">公告</button><button :class="['segment-item', { active: tab === 'MARKET' }]" @click="tab = 'MARKET'">二手</button></view>

    <view class="section community-section">
      <SectionHeader :title="tab === 'ANNOUNCEMENT' ? '开源乓协公告' : '二手器材'" :subtitle="tab === 'ANNOUNCEMENT' ? '活动、赛事与成员通知' : '球友发布的闲置器材信息'" />
      <LoadingState v-if="currentStatus === 'loading'" :variant="tab === 'MARKET' ? 'list' : 'inline'" :text="tab === 'MARKET' ? '正在加载二手信息' : '正在加载公告'" />
      <ErrorState v-else-if="currentStatus === 'error'" :title="tab === 'MARKET' ? '暂时无法加载二手信息' : '暂时无法加载公告'" description="请稍后重试" @retry="tab === 'MARKET' ? store.loadMarketPosts() : store.loadAnnouncements()" />

      <template v-else-if="tab === 'ANNOUNCEMENT'">
        <EmptyState v-if="!store.announcements.length" title="暂无公告" description="新的通知会显示在这里" icon="announcement" />
        <view v-else class="announcement-feed">
          <view v-for="item in store.announcements" :key="item.id" class="feed-row" @click="go('/pages/community/announcement-detail', { id: item.id })">
            <view class="feed-copy"><text class="feed-title">{{ item.title }}</text><text class="feed-summary">{{ item.content }}</text><text class="feed-date">{{ formatDate(item.created_at, false) }}</text></view><AppIcon name="chevron-right" size="sm" />
          </view>
        </view>
      </template>

      <template v-else>
        <EmptyState v-if="!store.marketPosts.length" title="暂无二手信息" description="有新的器材发布后会显示在这里" icon="market" />
        <view v-else class="market-grid">
          <view v-for="item in store.marketPosts" :key="item.id" :class="['market-card', { sold: sold(item.status) }]" @click="go('/pages/community/market-detail', { id: item.id })">
            <view class="market-image"><image v-if="hasMarketImage(item)" class="market-photo" :src="item.image_url" mode="aspectFill" @error="markMarketImageBroken(item.id)" /><AppIcon v-else name="market" size="lg" muted /><StatusBadge v-if="sold(item.status)" class="sold-badge" status="SOLD" label="已售" /></view>
            <view class="market-body"><text class="market-title">{{ item.title }}</text><text class="price">{{ marketPrice(item.price) }}</text><text class="condition">{{ marketDescription(item.content) }}</text></view>
          </view>
        </view>
      </template>
    </view>
    <AppTabBar active="community" />
  </view>
</template>

<style scoped lang="scss">
.forum-entry { display: flex; min-height: 3.75rem; align-items: center; justify-content: space-between; gap: .75rem; margin-top: .75rem; padding: 0 1rem; background: #eef3f0; border: 1px solid #dce6e0; border-radius: .875rem; }
.forum-entry-copy { display: flex; min-width: 0; flex-direction: column; }
.forum-entry-title { color: #173f35; font-size: .9375rem; font-weight: 700; }
.forum-entry-desc { margin-top: .15rem; color: #52635d; font-size: .75rem; }
.community-section { margin-top: 1.25rem; }
.announcement-feed { margin-top: .55rem; border-top: 1px solid #e2e7e3; }
.feed-row { display: flex; min-height: 5.6rem; align-items: center; gap: .75rem; padding: .85rem 0; border-bottom: 1px solid #e2e7e3; }
.feed-copy { min-width: 0; flex: 1; }
.feed-title { display: block; color: #18201d; font-size: .9375rem; font-weight: 700; line-height: 1.4; }
.feed-summary { display: -webkit-box; margin-top: .3rem; overflow: hidden; color: #66706b; font-size: .8125rem; line-height: 1.45; -webkit-box-orient: vertical; -webkit-line-clamp: 2; }
.feed-date { display: block; margin-top: .38rem; color: #87908b; font-size: .75rem; }
.market-grid { margin-top: .7rem; }
.market-card { position: relative; }
.market-image { position: relative; background: #e8ede9; }
.market-photo { width: 100%; height: 100%; }
.sold-badge { position: absolute; top: .5rem; right: .5rem; }
.market-card.sold .market-image > image { opacity: .42; }
.market-title { display: -webkit-box; overflow: hidden; color: #18201d; font-size: .9375rem; font-weight: 700; line-height: 1.4; -webkit-box-orient: vertical; -webkit-line-clamp: 2; }
.condition { display: block; margin-top: .3rem; overflow: hidden; color: #66706b; font-size: .75rem; text-overflow: ellipsis; white-space: nowrap; }
</style>
