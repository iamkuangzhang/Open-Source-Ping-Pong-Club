type ApiResponse<T> = {
  success: boolean
  code: string
  message: string
  data: T
}

export type ApiError = Error & { code?: string; statusCode?: number }

let defaultBaseUrl = 'http://127.0.0.1:8080/api/v1'
// #ifdef H5
defaultBaseUrl = '/api/v1'
// #endif

export const apiConfig = {
  baseUrl: (import.meta.env.VITE_API_BASE_URL as string | undefined) || defaultBaseUrl,
  accessToken: '',
  refreshToken: '',
  onTokenChange: undefined as undefined | ((accessToken: string, refreshToken: string) => void),
  onUnauthorized: undefined as undefined | (() => void)
}

function request<T>(method: UniApp.RequestOptions['method'], path: string, body?: unknown, retry = true): Promise<T> {
  return new Promise((resolve, reject) => {
    uni.request({
      url: `${apiConfig.baseUrl}${path}`,
      method,
      data: body as Record<string, unknown>,
      header: {
        'Content-Type': 'application/json',
        ...(apiConfig.accessToken ? { Authorization: `Bearer ${apiConfig.accessToken}` } : {})
      },
      success: async (response) => {
        const payload = response.data as ApiResponse<T>
        if (response.statusCode === 401 && retry && apiConfig.refreshToken) {
          try {
            const refreshed = await request<{ accessToken: string; refreshToken: string; expiresIn: number }>(
              'POST',
              '/auth/refresh',
              { refreshToken: apiConfig.refreshToken },
              false
            )
            apiConfig.accessToken = refreshed.accessToken
            apiConfig.refreshToken = refreshed.refreshToken
            apiConfig.onTokenChange?.(refreshed.accessToken, refreshed.refreshToken)
            resolve(await request<T>(method, path, body, false))
            return
          } catch {
            apiConfig.onUnauthorized?.()
          }
        }
        if (response.statusCode && response.statusCode >= 200 && response.statusCode < 300 && payload?.success) {
          resolve(payload.data)
          return
        }
        const error = new Error(payload?.message || `HTTP ${response.statusCode}`) as ApiError
        error.code = payload?.code
        error.statusCode = response.statusCode
        reject(error)
      },
      fail: (failure) => {
        const error = new Error(failure.errMsg || '网络请求失败') as ApiError
        error.code = 'NETWORK_ERROR'
        reject(error)
      }
    })
  })
}

export const api = {
  get: <T>(path: string) => request<T>('GET', path),
  post: <T>(path: string, body?: unknown) => request<T>('POST', path, body),
  put: <T>(path: string, body?: unknown) => request<T>('PUT', path, body),
  delete: <T>(path: string) => request<T>('DELETE', path)
}
