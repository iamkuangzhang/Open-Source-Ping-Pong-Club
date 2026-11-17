import { defineStore } from 'pinia'
import { api, type ApiError } from '../services/api'

export type ForumPost = {
  id: string
  author_user_id: string
  category: string
  title: string
  content: string
  status?: string
  created_at?: string
  updated_at?: string
}

export type ForumComment = {
  id: string
  post_id: string
  author_user_id: string
  content: string
  created_at?: string
}

export type ForumPage<T> = {
  items: T[]
  page: number
  size: number
  total: number
  pages: number
}

export type ForumStatus = 'idle' | 'loading' | 'success' | 'empty' | 'error'

/** 论坛板块。后端按分类名称去空格后精确匹配，因此这里与发帖时的取值保持一致。 */
export const FORUM_CATEGORIES = ['赛事交流', '技术讨论', '约球组队', '器材交换', '公告建议']

const DEFAULT_SIZE = 10
const MAX_TITLE = 160
const MAX_CONTENT = 5000
const MAX_COMMENT = 2000

function forumMessageOf(error: unknown) {
  const apiError = error as ApiError
  if (apiError.code === 'FORUM_REPORT_DUPLICATE') return '你已经举报过这个帖子，请等待处理结果'
  if (apiError.code === 'FORUM_POST_NOT_FOUND') return '帖子已被删除或隐藏'
  if (apiError.code === 'FORUM_REPORT_NOT_FOUND') return '举报记录不存在'
  if (apiError.code === 'FORUM_REPORT_ALREADY_REVIEWED') return '该举报已处理'
  if (apiError.statusCode === 401) return '登录状态已过期，请重新登录'
  if (apiError.statusCode === 403) return '只有作者本人或论坛管理员可以执行该操作'
  if (apiError.statusCode === 404) return '内容不存在或已不可见'
  if (apiError.statusCode === 409) return '当前状态已发生变化，请刷新后重试'
  if (apiError.statusCode === 422) return '提交内容不符合要求，请检查分类、标题和正文'
  if (apiError.statusCode && apiError.statusCode >= 500) return '服务暂时不可用，请稍后重试'
  return '暂时无法完成操作，请检查网络后重试'
}

function emptyPage<T>(size = DEFAULT_SIZE): ForumPage<T> {
  return { items: [], page: 1, size, total: 0, pages: 0 }
}

export const useForumStore = defineStore('forum', {
  state: () => ({
    category: '',
    posts: [] as ForumPost[],
    page: 1,
    size: DEFAULT_SIZE,
    total: 0,
    pages: 0,
    listStatus: 'idle' as ForumStatus,
    loadingMore: false,
    detail: null as ForumPost | null,
    detailStatus: 'idle' as ForumStatus,
    comments: [] as ForumComment[],
    commentsPage: 1,
    commentsPages: 0,
    commentsTotal: 0,
    commentsStatus: 'idle' as ForumStatus,
    submitting: false,
    error: '',
    success: ''
  }),
  getters: {
    hasMore: (state) => state.page < state.pages,
    emptyHint: (state) => state.category ? `“${state.category}”板块还没有帖子` : '论坛还没有帖子',
    titleLengthLimit: () => MAX_TITLE,
    contentLengthLimit: () => MAX_CONTENT,
    commentLengthLimit: () => MAX_COMMENT
  },
  actions: {
    clearFeedback() {
      this.error = ''
      this.success = ''
    },
    async loadPosts(category?: string, reset = true) {
      const selected = category === undefined ? this.category : category
      if (reset) {
        this.category = selected
        this.listStatus = 'loading'
        this.posts = []
        this.page = 1
        this.pages = 0
        this.total = 0
      } else {
        this.loadingMore = true
      }
      const target = reset ? 1 : this.page + 1
      const query = `?page=${target}&size=${this.size}${selected ? `&category=${encodeURIComponent(selected)}` : ''}`
      try {
        const result = await api.get<ForumPage<ForumPost>>(`/forum/posts${query}`)
        const page = result || emptyPage<ForumPost>(this.size)
        this.posts = reset ? page.items || [] : [...this.posts, ...(page.items || [])]
        this.page = page.page || target
        this.pages = page.pages || 0
        this.total = page.total || 0
        this.size = page.size || this.size
        if (reset) this.listStatus = this.posts.length ? 'success' : 'empty'
      } catch (error) {
        if (reset) this.listStatus = 'error'
        this.error = forumMessageOf(error)
        throw error
      } finally {
        this.loadingMore = false
      }
    },
    async loadMore() {
      if (this.loadingMore || this.listStatus !== 'success') return
      if (this.page >= this.pages) return
      await this.loadPosts(this.category, false).catch(() => undefined)
    },
    async loadPost(id: string) {
      this.detailStatus = 'loading'
      this.detail = null
      this.clearFeedback()
      try {
        this.detail = await api.get<ForumPost>(`/forum/posts/${id}`)
        this.detailStatus = 'success'
        return this.detail
      } catch (error) {
        this.detailStatus = 'error'
        this.error = forumMessageOf(error)
        throw error
      }
    },
    async loadComments(postId: string, page = 1, append = false) {
      this.commentsStatus = append ? this.commentsStatus : 'loading'
      try {
        const result = await api.get<ForumPage<ForumComment>>(`/forum/posts/${postId}/comments?page=${page}&size=20`)
        const data = result || emptyPage<ForumComment>(20)
        this.comments = append ? [...this.comments, ...(data.items || [])] : data.items || []
        this.commentsPage = data.page || page
        this.commentsPages = data.pages || 0
        this.commentsTotal = data.total || 0
        this.commentsStatus = this.comments.length ? 'success' : 'empty'
      } catch (error) {
        this.commentsStatus = 'error'
        this.error = forumMessageOf(error)
        throw error
      }
    },
    async createPost(payload: { category: string; title: string; content: string }) {
      this.submitting = true
      this.clearFeedback()
      try {
        const created = await api.post<{ id: string }>('/forum/posts', {
          category: payload.category,
          title: payload.title.trim(),
          content: payload.content.trim()
        })
        this.success = '帖子已发布'
        return created?.id || ''
      } catch (error) {
        this.error = forumMessageOf(error)
        throw error
      } finally {
        this.submitting = false
      }
    },
    async updatePost(id: string, payload: { category: string; title: string; content: string }) {
      this.submitting = true
      this.clearFeedback()
      try {
        await api.put(`/forum/posts/${id}`, {
          category: payload.category,
          title: payload.title.trim(),
          content: payload.content.trim()
        })
        this.success = '帖子已更新'
        if (this.detail?.id === id) await this.loadPost(id).catch(() => undefined)
      } catch (error) {
        this.error = forumMessageOf(error)
        throw error
      } finally {
        this.submitting = false
      }
    },
    async deletePost(id: string) {
      this.submitting = true
      this.clearFeedback()
      try {
        await api.delete(`/forum/posts/${id}`)
        this.posts = this.posts.filter((item) => item.id !== id)
        if (this.total > 0) this.total -= 1
        this.success = '帖子已删除'
      } catch (error) {
        this.error = forumMessageOf(error)
        throw error
      } finally {
        this.submitting = false
      }
    },
    async createComment(postId: string, content: string) {
      this.submitting = true
      this.clearFeedback()
      try {
        await api.post(`/forum/posts/${postId}/comments`, { content: content.trim() })
        await this.loadComments(postId, 1, false).catch(() => undefined)
        this.success = '评论已发布'
      } catch (error) {
        this.error = forumMessageOf(error)
        throw error
      } finally {
        this.submitting = false
      }
    },
    async deleteComment(postId: string, commentId: string) {
      this.submitting = true
      this.clearFeedback()
      try {
        await api.delete(`/forum/posts/${postId}/comments/${commentId}`)
        this.comments = this.comments.filter((item) => item.id !== commentId)
        this.commentsTotal = Math.max(0, this.commentsTotal - 1)
        if (!this.comments.length) this.commentsStatus = 'empty'
        this.success = '评论已删除'
      } catch (error) {
        this.error = forumMessageOf(error)
        throw error
      } finally {
        this.submitting = false
      }
    },
    async reportPost(postId: string, reason: string) {
      this.submitting = true
      this.clearFeedback()
      try {
        const created = await api.post<{ id: string }>(`/forum/posts/${postId}/reports`, { reason: reason.trim() })
        this.success = '举报已提交，处理结果可在通知中查看'
        return created?.id || ''
      } catch (error) {
        this.error = forumMessageOf(error)
        throw error
      } finally {
        this.submitting = false
      }
    }
  }
})
