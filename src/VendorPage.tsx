import { startTransition, useCallback, useEffect, useState, type FormEvent } from 'react'
import { ArrowRight, ArrowUpRight, Check, Clipboard, ExternalLink, PackagePlus, Save, Store } from 'lucide-react'
import { ApiError, apiRequest, type PageResponse, type Product, type Shop } from './api'
import type { User } from '@firebase/auth'

type Props = { user: User | null; authReady: boolean; onSignIn: () => void }
const categories = ['Coffee & food', 'Home & living', 'Clothing', 'Art & gifts', 'Wellness']
const fields = ['name', 'category', 'description', 'address', 'city', 'phone', 'email', 'website'] as const

export default function VendorPage({ user, authReady, onSignIn }: Props) {
  const [shop, setShop] = useState<Shop | null>(null)
  const [ready, setReady] = useState(false)
  const [message, setMessage] = useState('')
  const [name, setName] = useState('')
  const [category, setCategory] = useState(categories[0])
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    if (!user) return
    let active = true
    apiRequest('/api/v1/users/register', { method: 'POST' })
      .then(() => apiRequest<Shop>('/api/v1/shops/me').catch((error: unknown) => {
        if (error instanceof ApiError && error.status === 404) return null
        throw error
      }))
      .then((result) => {
        if (active) setShop(result)
      })
      .catch((error: Error) => { if (active) setMessage(error.message) })
      .finally(() => { if (active) setReady(true) })
    return () => { active = false }
  }, [user])

  async function createShop(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSaving(true)
    try {
      const result = await apiRequest<Shop>('/api/v1/shops', { method: 'POST', body: JSON.stringify({ name, category }) })
      setShop(result)
      setMessage('Shop created. Add your details and submit it for approval when you are ready.')
    } catch (error) {
      setMessage(error instanceof Error ? error.message : 'Could not create the shop profile.')
    } finally {
      setSaving(false)
    }
  }

  if (!authReady) return <Loading />
  return <main className="dashboard-page">
    <div className="dashboard-title"><div><div className="eyebrow muted-eyebrow">The shopkeeper's corner</div><h1>Your shop,<br className="mobile-break" /> out in the world<span className="orange-dot">.</span></h1><p>A free digital visiting card, made for the people who make a neighborhood.</p></div><div className="dashboard-illustration"><Store size={41} /><span>YOUR<br />LOCAL<br />FAVOURITE</span></div></div>
    {!user ? <div className="sign-in-panel"><div className="panel-icon"><Store size={22} /></div><div><h2>Start with your Google account.</h2><p>Every shop is reviewed before it appears in the public directory.</p></div><button className="button button-dark" onClick={onSignIn}>Continue with Google <ArrowUpRight size={16} /></button></div> : !ready ? <Loading /> : shop ? <ShopWorkspace shop={shop} onShopChange={setShop} message={message} setMessage={setMessage} /> : <section className="vendor-main setup-panel"><div className="dashboard-section-heading"><span className="eyebrow">01 / Your shop</span><span className="status-pill status-pending"><span /> Not set up</span></div><h2>Let's get your shop on the map.</h2><p className="form-intro">Give your place a name and a category. You can add the rest of your details next.</p><form className="shop-form" onSubmit={createShop}><label>Shop name<input required minLength={2} maxLength={100} value={name} onChange={(event) => setName(event.target.value)} placeholder="e.g. The Corner Store" /></label><label>What kind of place?<select value={category} onChange={(event) => setCategory(event.target.value)}>{categories.map((item) => <option key={item}>{item}</option>)}</select></label><button disabled={saving} className="button button-orange">{saving ? 'Creating...' : 'Create my shop profile'} <ArrowRight size={16} /></button>{message && <p className="form-message" role="status">{message}</p>}</form><div className="free-note"><Check size={16} />Free for every local business</div></section>}
  </main>
}

function ShopWorkspace({ shop, onShopChange, message, setMessage }: { shop: Shop; onShopChange: (shop: Shop) => void; message: string; setMessage: (message: string) => void }) {
  const [products, setProducts] = useState<Product[]>([])
  const [loadingProducts, setLoadingProducts] = useState(true)
  const [saving, setSaving] = useState(false)
  const [productName, setProductName] = useState('')
  const [productDescription, setProductDescription] = useState('')
  const [productPrice, setProductPrice] = useState('')
  const [shareCopied, setShareCopied] = useState(false)
  const [uploadingLogo, setUploadingLogo] = useState(false)
  const [logoMessage, setLogoMessage] = useState('')
  const [logoUploadFailed, setLogoUploadFailed] = useState(false)

  const reloadProducts = useCallback(async () => {
    try {
      const result = await apiRequest<PageResponse<Product>>('/api/v1/shops/me/products?page=0&size=100')
      startTransition(() => setProducts(result.items))
    } catch (error) {
      setMessage(error instanceof Error ? error.message : 'Could not load products.')
    } finally {
      startTransition(() => setLoadingProducts(false))
    }
  }, [setMessage])

  useEffect(() => { void reloadProducts() }, [reloadProducts, shop.id])

  async function saveShop(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    const patch = Object.fromEntries(fields.map((field) => [field, form.get(field)?.toString().trim() || null]))
    setSaving(true)
    try {
      const updated = await apiRequest<Shop>('/api/v1/shops/me', { method: 'PATCH', body: JSON.stringify(patch) })
      onShopChange(updated)
      setMessage('Shop details saved.')
    } catch (error) {
      setMessage(error instanceof Error ? error.message : 'Could not save shop details.')
    } finally {
      setSaving(false)
    }
  }

  async function uploadLogo(file?: File) {
    if (!file) return
    const form = new FormData()
    form.append('file', file)
    setUploadingLogo(true)
    setLogoUploadFailed(false)
    setLogoMessage('Uploading logo...')
    try {
      const updated = await apiRequest<Shop>('/api/v1/shops/me/logo', { method: 'POST', body: form })
      onShopChange(updated)
      setLogoMessage('Shop logo uploaded successfully.')
    } catch (error) {
      setLogoUploadFailed(true)
      setLogoMessage(error instanceof Error ? error.message : 'Could not upload the shop logo.')
    } finally {
      setUploadingLogo(false)
    }
  }

  async function submitForReview() {
    try {
      const updated = await apiRequest<Shop>('/api/v1/shops/me/submit', { method: 'POST' })
      onShopChange(updated)
      setMessage('Your shop is in the review queue.')
    } catch (error) {
      setMessage(error instanceof Error ? error.message : 'Could not submit your shop.')
    }
  }

  async function addProduct(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    try {
      await apiRequest('/api/v1/shops/me/products', { method: 'POST', body: JSON.stringify({ name: productName, description: productDescription, price: productPrice ? Number(productPrice) : null }) })
      setProductName('')
      setProductDescription('')
      setProductPrice('')
      await reloadProducts()
      setMessage('Product added to your catalogue.')
    } catch (error) {
      setMessage(error instanceof Error ? error.message : 'Could not add product.')
    }
  }

  async function deleteProduct(id: string) {
    try {
      await apiRequest(`/api/v1/shops/me/products/${encodeURIComponent(id)}`, { method: 'DELETE' })
      setProducts((current) => current.filter((product) => product.id !== id))
      setMessage('Product removed.')
    } catch (error) {
      setMessage(error instanceof Error ? error.message : 'Could not remove product.')
    }
  }

  async function copyLink() {
    await navigator.clipboard.writeText(`${window.location.origin}/shops/${shop.slug}`)
    setShareCopied(true)
    window.setTimeout(() => setShareCopied(false), 1800)
  }

  return <div className="workspace-layout"><div className="workspace-main">
    <div className="workspace-heading"><div className="workspace-brand">{shop.logoUrl && <img src={shop.logoUrl} alt={`${shop.name} logo`} />}<div><div className="eyebrow">Your visiting card</div><h2>{shop.name}</h2><p>{shop.city || 'Add your city'} · /shops/{shop.slug}</p></div></div><span className={`status-pill ${shop.published ? 'status-approved' : 'status-pending'}`}><span />{shop.published ? 'Published' : shop.approvalStatus ?? 'Draft'}</span></div>
    <div className="workspace-actions"><button className="button button-outline" onClick={copyLink}>{shareCopied ? <Check size={15} /> : <Clipboard size={15} />}{shareCopied ? 'Copied' : 'Copy shop link'}</button><a className="button button-outline" href={`/shops/${shop.slug}`} target="_blank" rel="noreferrer">Preview card <ExternalLink size={14} /></a><button className="button button-orange" onClick={submitForReview}>Submit for review <ArrowUpRight size={15} /></button></div>
    <section className="shop-logo-upload"><div><span className="eyebrow">Shop logo</span><p>Upload a PNG, JPEG, or GIF. Your logo will be shown after the admin approves and publishes your shop.</p></div>{shop.logoUrl && shop.published && <img src={shop.logoUrl} alt={`${shop.name} logo`} />}<label className={`button button-outline ${uploadingLogo ? 'is-uploading' : ''}`}>{uploadingLogo ? 'Uploading logo...' : shop.logoUrl ? 'Replace logo' : 'Upload logo'}<input type="file" accept="image/png,image/jpeg,image/gif" disabled={uploadingLogo} onChange={(event) => { const file = event.currentTarget.files?.[0]; event.currentTarget.value = ''; void uploadLogo(file) }} /></label>{logoMessage && <p className={`logo-upload-message ${logoUploadFailed ? 'is-error' : ''}`} role={logoUploadFailed ? 'alert' : 'status'}>{logoMessage}</p>}</section>
    <form className="shop-form details-form" onSubmit={saveShop}><div className="dashboard-section-heading"><span className="eyebrow">Shop details</span><button className="text-action" disabled={saving}>{saving ? 'Saving...' : 'Save changes'} <Save size={14} /></button></div>
      <label>Shop name<input name="name" required minLength={2} maxLength={100} defaultValue={shop.name} /></label><label>Category<select name="category" defaultValue={shop.category}>{categories.map((item) => <option key={item}>{item}</option>)}</select></label><label>About your shop<textarea name="description" maxLength={1000} defaultValue={shop.description} placeholder="What makes your place special?" rows={3} /></label><div className="form-two-columns"><label>Address<input name="address" maxLength={240} defaultValue={shop.address} /></label><label>City / neighborhood<input name="city" maxLength={100} defaultValue={shop.city} /></label><label>Telephone<input name="phone" maxLength={30} defaultValue={shop.phone} /></label><label>Email<input name="email" type="email" maxLength={254} defaultValue={shop.email} /></label></div><label>Website<input name="website" type="url" maxLength={2048} defaultValue={shop.website} placeholder="https://" /></label>{message && <p className="form-message" role="status">{message}</p>}</form>
    <section className="catalogue-section"><div className="dashboard-section-heading"><span className="eyebrow">Product catalogue</span><span className="catalogue-count">{products.length} items</span></div><form className="product-form" onSubmit={addProduct}><label>Product name<input required maxLength={120} value={productName} onChange={(event) => setProductName(event.target.value)} placeholder="What are you offering?" /></label><label>Description<input maxLength={1000} value={productDescription} onChange={(event) => setProductDescription(event.target.value)} placeholder="A short description" /></label><label>Price<input type="number" min="0" step="0.01" value={productPrice} onChange={(event) => setProductPrice(event.target.value)} placeholder="Optional" /></label><button className="icon-submit" aria-label="Add product"><PackagePlus size={18} /></button></form>
      {loadingProducts ? <p className="catalogue-note">Loading catalogue...</p> : products.length ? <div className="product-list">{products.map((product) => <article key={product.id}><span className="product-index">{product.name.slice(0, 1).toUpperCase()}</span><div><strong>{product.name}</strong><p>{product.description || 'No description added'}{product.price != null ? ` · ${product.price.toFixed(2)}` : ''}</p></div><button className="remove-product" onClick={() => deleteProduct(product.id)} aria-label={`Remove ${product.name}`}>Remove</button></article>)}</div> : <p className="catalogue-note">Your catalogue is empty. Add the first item above.</p>}
    </section>
  </div><aside className="vendor-aside workspace-aside"><span className="aside-number">01</span><h3>Review status</h3><p className="aside-copy">{shop.approved ? 'Your business has been verified by the e-CardMitra team.' : 'Our team checks every shop before it appears in the public directory.'}</p><div className="step-row"><span>01</span><div><strong>Keep your details current</strong><small>Visitors use these to reach and find you.</small></div></div><div className="step-row"><span>02</span><div><strong>Build your catalogue</strong><small>Help your next customer know what to expect.</small></div></div><div className="step-row"><span>03</span><div><strong>{shop.published ? 'Your card is live' : 'Wait for approval'}</strong><small>{shop.published ? 'Your public page can be shared now.' : 'Publishing is managed by the platform admin.'}</small></div></div><div className="free-note"><Check size={16} />Always free. No plans or billing.</div></aside></div>
}

function Loading() { return <main className="loading-state"><span className="loading-spinner" /><p>One moment...</p></main> }
