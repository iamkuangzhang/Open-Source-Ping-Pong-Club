<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useAppStore } from '../../stores/app'
import AppHeader from '../../components/AppHeader.vue'
import AppIcon from '../../components/AppIcon.vue'
import AppTabBar from '../../components/AppTabBar.vue'
import UserAvatar from '../../components/UserAvatar.vue'
import StatusBadge from '../../components/StatusBadge.vue'
import LoadingState from '../../components/LoadingState.vue'
import ErrorState from '../../components/ErrorState.vue'
import { go } from '../../utils/ui'

type MenuItem = { label: string; icon: string; path?: string; auth?: boolean; action?: 'about' | 'settings' }

const store = useAppStore()
onMounted(() => store.bootstrap())

const rank = computed(() => store.rating ? store.leaderboard.findIndex(item => item.user_id === store.rating?.user_id) + 1 : 0)
const memberNumber = computed(() => store.isMember ? '成员档案已认证' : '')

const activityMenus: MenuItem[] = [
  { label: '我的比赛', icon: 'competition', path: '/pages/competition/index', auth: true },
  { label: '我的约球', icon: 'play', path: '/pages/play/index', auth: true },
  { label: '我的发布', icon: 'community', path: '/pages/community/index', auth: true }
]
const serviceMenus: MenuItem[] = [
  { label: '通知中心', icon: 'notification', path: '/pages/community/notifications', auth: true },
  { label: '关于开源乓协', icon: 'info', action: 'about' },
  { label: '设置', icon: 'settings', action: 'settings' }
]
const adminMenus = computed(() => [
  { label: '成员审核', path: '/pages/admin/membership-review' },
  { label: '赛事管理', path: '/pages/admin/index', section: 'competitions' },
  { label: '公告管理', path: '/pages/admin/index', section: 'announcements' },
  ...(store.canManageRoles ? [{ label: '组织管理', path: '/pages/admin/organization' }] : [])
])

function openMenu(item: MenuItem) {
  if (item.auth && !store.signedIn) return go('/pages/auth/login')
  if (item.path) return go(item.path)
  if (item.action === 'about') {
    uni.showModal({
      title: '关于开源乓协',
      content: '开源乓协是个人学习与娱乐性质的独立开发项目，并非学校或相关学生组织官方产品。',
      showCancel: false,
      confirmText: '知道了'
    })
    return
  }
  uni.showModal({
    title: '设置',
    content: store.signedIn ? '当前版本的账号操作可在本页完成。' : '登录后可管理个人资料与账号状态。',
    showCancel: false,
    confirmText: '知道了'
  })
}
</script>

<template>
  <view class="app-shell profile-home">
    <AppHeader title="我的" />

    <view class="profile-card">
      <template v-if="!store.signedIn">
        <view class="profile-main">
          <UserAvatar placeholder size="lg" />
          <view class="profile-copy"><text class="profile-name">未登录</text><text class="profile-description">登录后可查看成员身份与个人记录</text></view>
        </view>
        <view class="auth-actions">
          <button class="btn btn-primary btn-block" @click="go('/pages/auth/login')">登录</button>
          <view class="register-link"><text>没有账号？</text><text class="link" @click="go('/pages/auth/register')">注册账号</text></view>
        </view>
      </template>

      <LoadingState v-else-if="store.profileStatus === 'loading'" text="正在加载个人资料" />
      <ErrorState v-else-if="store.profileStatus === 'error'" title="暂时无法加载个人资料" description="其他个人入口仍可使用" @retry="store.loadPrivate()" />

      <template v-else>
        <view class="profile-main">
          <UserAvatar :name="store.me?.nickname" :src="store.me?.avatarUrl" size="lg" />
          <view class="profile-copy">
            <text class="profile-name">{{ store.me?.nickname || '开源乓协用户' }}</text>
            <view class="identity"><StatusBadge :label="store.isMember ? '正式成员' : '普通用户'" :status="store.isMember ? 'ACTIVE' : 'PENDING'" /><text v-if="store.isMember" class="member-no">{{ memberNumber }}</text></view>
          </view>
          <button class="edit-button" @click="go('/pages/profile/edit')">编辑资料</button>
        </view>

        <view v-if="store.isMember" class="stat-strip">
          <view class="stat-item"><text class="stat-value">{{ store.rating?.current_rating }}</text><text class="stat-label">积分</text></view>
          <view class="stat-item"><text class="stat-value">{{ rank || '—' }}</text><text class="stat-label">排名</text></view>
          <view class="stat-item"><text class="stat-value">{{ store.winRate }}</text><text class="stat-label">胜率</text></view>
        </view>
        <view v-else class="membership-entry" @click="go('/pages/membership/apply')">
          <view class="membership-icon"><AppIcon name="member-qr" /></view>
          <view><text>加入开源乓协</text><text>审核通过后成为正式成员并获得初始积分</text></view>
          <AppIcon name="chevron-right" />
        </view>
      </template>
    </view>

    <view class="menu-list profile-menu">
      <view v-for="item in activityMenus" :key="item.label" class="profile-row" @click="openMenu(item)">
        <AppIcon :name="item.icon" muted />
        <text class="row-label">{{ item.label }}</text>
        <AppIcon name="chevron-right" />
      </view>
    </view>

    <view class="menu-list profile-menu">
      <view v-for="item in serviceMenus" :key="item.label" class="profile-row" @click="openMenu(item)">
        <AppIcon :name="item.icon" muted />
        <text class="row-label">{{ item.label }}</text>
        <AppIcon name="chevron-right" />
      </view>
    </view>

    <view v-if="store.canManage" class="section admin-section">
      <view class="admin-heading"><AppIcon name="admin" /><text class="section-title">管理中心</text></view>
      <view class="menu-list admin-menu">
        <view v-for="item in adminMenus" :key="item.label" class="profile-row" @click="go(item.path, { section: item.section })">
          <text class="row-label">{{ item.label }}</text><AppIcon name="chevron-right" />
        </view>
      </view>
    </view>

    <view v-if="store.signedIn" class="menu-list logout-list"><button class="logout-button" @click="store.logout()">退出登录</button></view>
    <AppTabBar active="profile" />
  </view>
</template>

<style scoped lang="scss">
.profile-card { padding: 1rem; background: #fff; border: 1px solid #e2e7e3; border-radius: .875rem; }
.profile-main { display: flex; align-items: center; gap: .875rem; }
.profile-copy { min-width: 0; flex: 1; }
.profile-name { display: block; color: #18201d; font-size: 1.125rem; font-weight: 800; line-height: 1.3; }
.profile-description { display: block; margin-top: .25rem; color: #66706b; font-size: .8125rem; line-height: 1.45; }
.identity { display: flex; flex-wrap: wrap; align-items: center; gap: .4rem; margin-top: .35rem; }
.member-no { color: #66706b; font-size: .75rem; }
.edit-button { min-width: 2.75rem; min-height: 2.75rem; padding: 0 .25rem; color: #173f35; background: transparent !important; font-size: .8125rem; font-weight: 700; }
.auth-actions { margin-top: 1rem; }
.register-link { display: flex; justify-content: center; gap: .35rem; margin-top: .8rem; color: #66706b; font-size: .8125rem; }
.stat-strip { margin-top: 1rem; padding: .875rem 0 0; border-top: 1px solid #e7eae8; }
.membership-entry { display: flex; min-height: 4.5rem; align-items: center; gap: .7rem; margin-top: 1rem; padding-top: .875rem; border-top: 1px solid #e7eae8; }
.membership-icon { display: flex; width: 2.25rem; height: 2.25rem; align-items: center; justify-content: center; border-radius: .65rem; background: #eef3f0; }
.membership-entry > view:nth-child(2) { min-width: 0; flex: 1; }
.membership-entry > view:nth-child(2) text:first-child { display: block; font-size: .875rem; font-weight: 700; }
.membership-entry > view:nth-child(2) text:last-child { display: block; margin-top: .2rem; color: #66706b; font-size: .75rem; line-height: 1.4; }
.profile-menu { margin-top: 1rem; }
.profile-row { display: flex; min-height: 3.75rem; align-items: center; gap: .75rem; border-bottom: 1px solid #e7eae8; }
.profile-row:last-child { border-bottom: 0; }
.row-label { min-width: 0; flex: 1; color: #252d29; font-size: .9375rem; font-weight: 600; }
.admin-heading { display: flex; align-items: center; gap: .5rem; }
.admin-menu { margin-top: .625rem; }
.logout-list { margin-top: 1rem; }
.logout-button { display: flex; width: 100%; min-height: 3.5rem; align-items: center; justify-content: flex-start; padding: 0; color: #b84641; background: transparent !important; border: 0; text-align: left; font-size: .9375rem; font-weight: 600; }
.logout-button::after { display: none; }
.legal { display: block; padding: 1.25rem .75rem 0; color: #7b837f; text-align: center; font-size: .75rem; line-height: 1.55; }
</style>
