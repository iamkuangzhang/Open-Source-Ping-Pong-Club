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
type CommunityTab = 'ANNOUNCEMENT' | 'FORUM' | 'MARKET'
const tab = ref<CommunityTab>('ANNOUNCEMENT')
const brokenMarketImages = ref<Set<string>>(new Set())
const marketDescription = (content?: string) => (content || '器材详情以发布者说明为准').split(/[\n·]/)[0]
const marketPrice = (price?: string) => price ? (price.startsWith('¥') ? price : `¥${price}`) : '价格面议'
const sold = (status?: string) => status === 'SOLD'
const hasMarketImage = (item: { id: string; image_url?: string }) => Boolean(item.image_url && !brokenMarketImages.value.has(item.id))
const markMarketImageBroken = (id: string) => { brokenMarketImages.value = new Set([...brokenMarketImages.value, id]) }
const currentStatus = computed(() => tab.value === 'ANNOUNCEMENT' ? store.announcementsStatus : tab.value === 'FORUM' ? store.forumPostsStatus : store.marketPostsStatus)
const sectionTitle = computed(() => tab.value === 'ANNOUNCEMENT' ? '开源乓协公告' : tab.value === 'FORUM' ? '球友交流' : '二手器材')
const sectionSubtitle = computed(() => tab.value === 'ANNOUNCEMENT' ? '活动、赛事与成员通知' : tab.value === 'FORUM' ? '发帖交流、评论互动' : '球友发布的闲置器材信息')
const loadingText = computed(() => tab.value === 'ANNOUNCEMENT' ? '正在加载公告' : tab.value === 'FORUM' ? '正在加载帖子' : '正在加载二手信息')
const errorTitle = computed(() => tab.value === 'ANNOUNCEMENT' ? '暂时无法加载公告' : tab.value === 'FORUM' ? '暂时无法加载帖子' : '暂时无法加载二手信息')
onMounted(() => store.bootstrap())

function selectTab(next: CommunityTab) {
  tab.value = next
  if (next === 'FORUM' && store.forumPostsStatus === 'idle') store.loadForumPosts().catch(() => undefined)
}

function retryCurrent() {
  if (tab.value === 'ANNOUNCEMENT') return store.loadAnnouncements()
  if (tab.value === 'FORUM') return store.loadForumPosts()
  return store.loadMarketPosts()
}
</script>

<template>
  <view class="app-shell community-home">
    <AppHeader title="社区" subtitle="公告、论坛与校园二手" action-icon="notification" action-label="通知中心" @action="go(store.signedIn ? '/pages/community/notifications' : '/pages/auth/login')" />
    <view class="segment community-tabs" role="tablist">
      <button :class="['segment-item', { active: tab === 'ANNOUNCEMENT' }]" @click="selectTab('ANNOUNCEMENT')">公告</button>
      <button :class="['segment-item', { active: tab === 'FORUM' }]" @click="selectTab('FORUM')">论坛</button>
      <button :class="['segment-item', { active: tab === 'MARKET' }]" @click="selectTab('MARKET')">二手</button>
    </view>

    <view class="section community-section">
      <SectionHeader :title="sectionTitle" :subtitle="sectionSubtitle" />
      <LoadingState v-if="currentStatus === 'loading'" :variant="tab === 'MARKET' ? 'list' : 'inline'" :text="loadingText" />
      <ErrorState v-else-if="currentStatus === 'error'" :title="errorTitle" description="请稍后重试" @retry="retryCurrent" />

      <template v-else-if="tab === 'ANNOUNCEMENT'">
        <EmptyState v-if="!store.announcements.length" title="暂无公告" description="新的通知会显示在这里" icon="announcement" />
        <view v-else class="announcement-feed">
          <view v-for="item in store.announcements" :key="item.id" class="feed-row" @click="go('/pages/community/announcement-detail', { id: item.id })">
            <view class="feed-copy"><text class="feed-title">{{ item.title }}</text><text class="feed-summary">{{ item.content }}</text><text class="feed-date">{{ formatDate(item.created_at, false) }}</text></view><AppIcon name="chevron-right" size="sm" />
          </view>
        </view>
      </template>

      <template v-else-if="tab === 'FORUM'">
        <EmptyState v-if="!store.forumPosts.length" title="暂无帖子" description="球友发布的新帖会显示在这里" icon="community" />
        <view v-else class="announcement-feed">
          <view v-for="item in store.forumPosts" :key="item.id" class="feed-row" @click="go('/pages/community/post-detail', { id: item.id })">
            <view class="feed-copy">
              <text class="feed-title">{{ item.title }}</text>
              <text class="feed-summary">{{ item.content }}</text>
              <text class="feed-date">{{ item.category ? `${item.category} · ` : '' }}{{ formatDate(item.created_at, false) }}</text>
            </view>
            <AppIcon name="chevron-right" size="sm" />
          </view>
          <button v-if="store.forumPostsPage < store.forumPostsPages" class="btn btn-secondary btn-block load-more" @click="store.loadForumPosts(store.forumPostsPage + 1, true)">加载更多帖子</button>
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
.community-section { margin-top: 1.25rem; }
.announcement-feed { margin-top: .55rem; border-top: 1px solid #e2e7e3; }
.feed-row { display: flex; min-height: 5.6rem; align-items: center; gap: .75rem; padding: .85rem 0; border-bottom: 1px solid #e2e7e3; }
.feed-copy { min-width: 0; flex: 1; }
.feed-title { display: block; color: #18201d; font-size: .9375rem; font-weight: 700; line-height: 1.4; }
.feed-summary { display: -webkit-box; margin-top: .3rem; overflow: hidden; color: #66706b; font-size: .8125rem; line-height: 1.45; -webkit-box-orient: vertical; -webkit-line-clamp: 2; }
.feed-date { display: block; margin-top: .38rem; color: #87908b; font-size: .75rem; }
.market-grid { margin-top: .7rem; }
.load-more { margin-top: .85rem; }
.market-card { position: relative; }
.market-image { position: relative; background: #e8ede9; }
.market-photo { width: 100%; height: 100%; }
.sold-badge { position: absolute; top: .5rem; right: .5rem; }
.market-card.sold .market-image > image { opacity: .42; }
.market-title { display: -webkit-box; overflow: hidden; color: #18201d; font-size: .9375rem; font-weight: 700; line-height: 1.4; -webkit-box-orient: vertical; -webkit-line-clamp: 2; }
.condition { display: block; margin-top: .3rem; overflow: hidden; color: #66706b; font-size: .75rem; text-overflow: ellipsis; white-space: nowrap; }
</style>
