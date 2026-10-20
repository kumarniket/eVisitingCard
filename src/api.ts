import { auth } from './firebase'

export type Shop = {
  id: string
  name: string
  slug: string
  category: string
  description?: string
  city?: string
  address?: string
  phone?: string
  email?: string
  website?: string
  coverUrl?: string
  logoUrl?: string
  logoCleanupWarning?: boolean
  published: boolean
  approved: boolean
  approvalStatus?: string
  accent?: string
}

export type Product = {
  id: string
  shopId: string
  name: string
  description?: string
  price?: number
  category?: string
  imageUrl?: string
  active: boolean
}

export type PageResponse<T> = {
  items: T[]
  page: number
  size: number
  totalItems: number
  totalPages: number
}

export class ApiError extends Error {
  readonly status: number
  readonly traceId?: string
  readonly reason?: string
  readonly path: string

  constructor(status: number, message: string, path: string, traceId?: string, reason?: string) {
    const diagnostic = reason ? ` (Firebase: ${reason})` : ''
    const reference = traceId ? ` (Reference: ${traceId})` : ''
    super(`${message} [${path}]${diagnostic}${reference}`)
    this.status = status
    this.path = path
    this.traceId = traceId
    this.reason = reason
  }
}

const apiBase = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

export async function apiRequest<T>(path: string, init: RequestInit = {}): Promise<T> {
  const user = auth?.currentUser
  const token = await user?.getIdToken()
  const headers = new Headers(init.headers)
  headers.set('Accept', 'application/json')
  if (init.body && !(init.body instanceof FormData) && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json')

  async function sendRequest(idToken?: string) {
    const requestHeaders = new Headers(headers)
    if (idToken) requestHeaders.set('Authorization', `Bearer ${idToken}`)
    try {
      return await fetch(`${apiBase}${path}`, { ...init, headers: requestHeaders })
    } catch {
      throw new Error('The service is unavailable. Check that the Spring Boot API is running.')
    }
  }

  let response = await sendRequest(token)
  if (response.status === 401 && user) {
    response = await sendRequest(await user.getIdToken(true))
  }

  if (!response.ok) {
    const responseText = await response.text()
    type ErrorBody = { message?: string; detail?: string; traceId?: string; reason?: string; verificationStage?: string }
    let body: ErrorBody | null
    try {
      body = JSON.parse(responseText) as ErrorBody
    } catch {
      body = null
    }
    const diagnostic = [body?.reason, body?.verificationStage].filter(Boolean).join(' / ')
    const fallback = responseText.trim().slice(0, 250)
    throw new ApiError(response.status, body?.message ?? (fallback || `Request failed (${response.status}).`),
      path, body?.traceId, diagnostic || undefined)
  }
  if (response.status === 204) return undefined as T
  return response.json() as Promise<T>
}

export function getPublicShops() {
  return apiRequest<PageResponse<Shop>>('/api/v1/public/shops?page=0&size=24')
}

export function getPublicShop(slug: string) {
  return apiRequest<Shop>(`/api/v1/public/shops/${encodeURIComponent(slug)}`)
}

export function getPublicProducts(slug: string) {
  return apiRequest<PageResponse<Product>>(`/api/v1/public/shops/${encodeURIComponent(slug)}/products?page=0&size=100`)
}
