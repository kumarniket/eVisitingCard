import { startTransition, useCallback, useEffect, useState } from 'react'
import { ArrowUpRight, Check, Clock3, ShieldCheck, Store, Users, X } from 'lucide-react'
import { apiRequest, type PageResponse } from './api'
import type { User } from '@firebase/auth'

type Vendor = { uid: string; displayName?: string; email?: string; approvalStatus: string }
type ShopSubmission = { id: string; name: string; category: string; city?: string; ownerUid: string; approvalStatus: string; approved: boolean; published: boolean; logoUrl?: string }
type Props = { user: User | null; authReady: boolean; onSignIn: () => void }
const adminEmail = 'niket2work@gmail.com'

export default function AdminPage({ user, authReady, onSignIn }: Props) {
  const [stats, setStats] = useState<{ vendors: number; pending: number; published: number } | null>(null)
  const [vendors, setVendors] = useState<Vendor[]>([])
  const [shops, setShops] = useState<ShopSubmission[]>([])
  const [publishReady, setPublishReady] = useState<ShopSubmission[]>([])
  const [error, setError] = useState('')
  const [logoMessage, setLogoMessage] = useState('')
  const [logoUploadFailed, setLogoUploadFailed] = useState(false)
  const [working, setWorking] = useState('')
  const [claimStatus, setClaimStatus] = useState<{ uid: string; status: 'allowed' | 'missing' | 'error'; message?: string } | null>(null)
  const isAllowedEmail = user?.email?.trim().toLowerCase() === adminEmail
  const currentClaimStatus = user && claimStatus?.uid === user.uid ? claimStatus.status : 'checking'

  const refresh = useCallback(async () => {
    const [dashboard, pendingVendors, submissions, readyToPublish] = await Promise.all([
      apiRequest<{ vendors: number; pending: number; published: number }>('/api/v1/admin/dashboard'),
      apiRequest<PageResponse<Vendor>>('/api/v1/admin/vendors?status=PENDING&page=0&size=100'),
      apiRequest<PageResponse<ShopSubmission>>('/api/v1/admin/shops?status=PENDING_REVIEW&page=0&size=100'),
      apiRequest<PageResponse<ShopSubmission>>('/api/v1/admin/shops?status=APPROVED&page=0&size=100'),
    ])
    startTransition(() => {
      setStats(dashboard)
      setVendors(pendingVendors.items)
      setShops(submissions.items)
      setPublishReady(readyToPublish.items.filter((shop) => !shop.published))
    })
  }, [])

  useEffect(() => {
    if (!user || !isAllowedEmail) return
    let active = true
    user.getIdTokenResult().then(({ claims }) => {
      if (!active) return
      if (claims.superAdmin !== true) {
        setClaimStatus({ uid: user.uid, status: 'missing' })
        return
      }
      setClaimStatus({ uid: user.uid, status: 'allowed' })
      refresh().catch((reason: Error) => setError(reason.message))
    }).catch((reason: Error) => {
      if (active) setClaimStatus({ uid: user.uid, status: 'error', message: reason.message })
    })
    return () => { active = false }
  }, [user, isAllowedEmail, refresh])

  async function action(key: string, path: string) {
    setError('')
    setWorking(key)
    try {
      await apiRequest(path, { method: 'POST' })
      await refresh()
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : 'The update failed.')
    } finally {
      setWorking('')
    }
  }

  async function uploadLogo(shopId: string, file?: File) {
    if (!file) return
    const form = new FormData()
    form.append('file', file)
    setWorking(shopId)
    setLogoUploadFailed(false)
    setLogoMessage('Uploading logo...')
    try {
      await apiRequest(`/api/v1/admin/shops/${encodeURIComponent(shopId)}/logo`, { method: 'POST', body: form })
      setLogoMessage('Logo uploaded successfully.')
      try {
        await refresh()
      } catch (reason) {
        setLogoUploadFailed(true)
        setLogoMessage(`Logo uploaded, but the review list could not refresh: ${reason instanceof Error ? reason.message : 'Please reload the page.'}`)
      }
    } catch (reason) {
      setLogoUploadFailed(true)
      setLogoMessage(reason instanceof Error ? reason.message : 'The logo could not be assigned.')
    } finally {
      setWorking('')
    }
  }

  async function removeLogo(shopId: string) {
    setWorking(shopId)
    try {
      await apiRequest(`/api/v1/admin/shops/${encodeURIComponent(shopId)}/logo`, { method: 'DELETE' })
      await refresh()
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : 'The logo assignment could not be removed.')
    } finally {
      setWorking('')
    }
  }

  if (!authReady) return <Loading />
  return (
    <main className="dashboard-page admin-page">
      <div className="dashboard-title">
        <div>
          <div className="eyebrow muted-eyebrow"><ShieldCheck size={15} /> Super administrator</div>
          <h1>Good morning,<br className="mobile-break" /> let's take a look<span className="orange-dot">.</span></h1>
          <p>A quick pulse on the businesses making your neighborhoods.</p>
        </div>
        <div className="dashboard-illustration admin-illustration"><Users size={37} /><span>THE<br />BACK<br />OFFICE</span></div>
      </div>
      {!user ? (
        <div className="sign-in-panel">
          <div className="panel-icon"><ShieldCheck size={22} /></div>
          <div><h2>Admin access is restricted.</h2><p>Sign in with an account that has the trusted super-admin claim.</p></div>
          <button className="button button-dark" onClick={onSignIn}>Sign in with Google <ArrowUpRight size={16} /></button>
        </div>
      ) : !isAllowedEmail ? (
        <div className="sign-in-panel">
          <div className="panel-icon"><ShieldCheck size={22} /></div>
          <div><h2>This Google account is not authorized.</h2><p>Only {adminEmail} can access the admin dashboard.</p></div>
        </div>
      ) : currentClaimStatus !== 'allowed' ? (
        <div className="sign-in-panel">
          <div className="panel-icon"><ShieldCheck size={22} /></div>
          <div><h2>Admin access is not enabled yet.</h2><p>{currentClaimStatus === 'missing' ? 'Sign out and back in after the project operator enables admin access for this account.' : currentClaimStatus === 'error' ? claimStatus?.message : 'Verifying admin access...'}</p></div>
        </div>
      ) : (
        <>
          <div className="admin-metrics">
            <Metric label="Total vendors" value={stats?.vendors} icon={<Users size={18} />} />
            <Metric label="Awaiting review" value={stats?.pending} icon={<Clock3 size={18} />} />
            <Metric label="Published shops" value={stats?.published} icon={<Store size={18} />} />
          </div>
          {error && <div className="admin-empty" role="alert"><ShieldCheck size={23} /><h2>Admin data isn't available.</h2><p>{error}</p></div>}
          {logoMessage && <p className={`logo-upload-message ${logoUploadFailed ? 'is-error' : ''}`} role={logoUploadFailed ? 'alert' : 'status'}>{logoMessage}</p>}
          <ReviewSection title="Vendor approval" count={`${vendors.length} pending`} empty="No vendor approvals are waiting.">
            {vendors.map((vendor) => (
              <article className="review-row" key={vendor.uid}>
                <div className="review-avatar">{vendor.displayName?.[0] ?? 'V'}</div>
                <div className="review-details"><strong>{vendor.displayName || 'New vendor'}</strong><p>{vendor.email || vendor.uid}</p></div>
                <button disabled={working === vendor.uid} className="review-action reject-action" onClick={() => action(vendor.uid, `/api/v1/admin/vendors/${encodeURIComponent(vendor.uid)}/reject`)} aria-label={`Reject ${vendor.displayName || 'vendor'}`}><X size={16} /></button>
                <button disabled={working === vendor.uid} className="review-action approve-action" onClick={() => action(vendor.uid, `/api/v1/admin/vendors/${encodeURIComponent(vendor.uid)}/approve`)} aria-label={`Approve ${vendor.displayName || 'vendor'}`}><Check size={16} /></button>
              </article>
            ))}
          </ReviewSection>
          <ReviewSection title="Shop submissions" count={`${shops.length} to review`} empty="No shop submissions are waiting.">
            {shops.map((shop) => (
              <article className="review-row" key={shop.id}>
                <div className="review-avatar shop-review-avatar"><Store size={17} /></div>
                <div className="review-details"><strong>{shop.name}</strong><p>{shop.category}{shop.city ? ` · ${shop.city}` : ''}</p></div>
                <label className="logo-upload-control">{working === shop.id ? 'Uploading...' : shop.logoUrl ? 'Replace logo' : 'Assign logo'}<input type="file" accept="image/png,image/jpeg,image/gif" disabled={working === shop.id} onChange={(event) => { const file = event.currentTarget.files?.[0]; event.currentTarget.value = ''; void uploadLogo(shop.id, file) }} /></label>
                {shop.logoUrl && <button disabled={working === shop.id} className="review-action reject-action" onClick={() => removeLogo(shop.id)} aria-label={`Remove ${shop.name} logo`}><X size={15} /></button>}
                <button disabled={working === shop.id} className="review-action approve-action" onClick={() => action(shop.id, `/api/v1/admin/shops/${encodeURIComponent(shop.id)}/approve`)} aria-label={`Approve ${shop.name}`}><Check size={16} /></button>
              </article>
            ))}
          </ReviewSection>
          <ReviewSection title="Ready to publish" count={`${publishReady.length} eligible`} empty="No approved shops are waiting to publish.">
            {publishReady.map((shop) => (
              <article className="review-row" key={shop.id}>
                <div className="review-avatar shop-review-avatar"><Store size={17} /></div>
                <div className="review-details"><strong>{shop.name}</strong><p>{shop.category}{shop.city ? ` · ${shop.city}` : ''}</p></div>
                <button disabled={working === shop.id} className="button button-orange review-publish" onClick={() => action(shop.id, `/api/v1/admin/shops/${encodeURIComponent(shop.id)}/publish`)}>Publish</button>
              </article>
            ))}
          </ReviewSection>
        </>
      )}
    </main>
  )
}

function ReviewSection({ title, count, empty, children }: { title: string; count: string; empty: string; children: React.ReactNode }) {
  const hasItems = Array.isArray(children) ? children.length > 0 : Boolean(children)
  return (
    <section className="admin-queue">
      <div className="dashboard-section-heading"><span className="eyebrow">{title}</span><span className="queue-count">{count}</span></div>
      {hasItems ? <div className="review-list">{children}</div> : <div className="review-empty"><Check size={18} /><span>{empty}</span></div>}
    </section>
  )
}

function Metric({ label, value, icon }: { label: string; value: number | undefined; icon: React.ReactNode }) {
  return <div className="metric"><span className="metric-icon">{icon}</span><span className="metric-value">{value ?? '—'}</span><span className="metric-label">{label}</span></div>
}

function Loading() { return <main className="loading-state"><span className="loading-spinner" /><p>One moment...</p></main> }
