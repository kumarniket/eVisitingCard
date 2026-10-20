import { startTransition, useEffect, useMemo, useState } from 'react'
import { BrowserRouter, Link, Route, Routes, useNavigate, useParams } from 'react-router-dom'
import { QRCodeSVG } from 'qrcode.react'
import {
  ArrowDownRight, ArrowLeft, ArrowRight, ArrowUpRight, BadgeCheck, Check,
  Clock3, ExternalLink, Mail, MapPin, Menu, Phone, Search,
  Sparkles, Store, Users, X,
} from 'lucide-react'
import { getRedirectResult, onAuthStateChanged, signInWithPopup, signInWithRedirect, signOut, type User } from '@firebase/auth'
import { auth, googleProvider } from './firebase'
import { getPublicProducts, getPublicShop, getPublicShops, type Product, type Shop } from './api'
import VendorPage from './VendorPage'
import AdminPage from './AdminPage'

const sampleShops: Shop[] = [
  { id: '1', name: 'Morrow Coffee', slug: 'morrow-coffee', category: 'Coffee & food', description: 'Slow mornings, good beans, and a little room to stay awhile.', city: 'Indiranagar', address: '12 100 Feet Road, Bengaluru', phone: '+918045551201', email: 'hello@morrow.example', website: 'https://example.com', coverUrl: 'https://images.unsplash.com/photo-1445116572660-236099ec97a0?auto=format&fit=crop&w=1000&q=85', published: true, approved: true, accent: 'citrus' },
  { id: '2', name: 'Form & Field', slug: 'form-and-field', category: 'Home & living', description: 'Everyday objects made slowly, thoughtfully, and close to home.', city: 'Jubilee Hills', address: '8 Road No. 36, Hyderabad', phone: '+914040551202', email: 'studio@formfield.example', website: 'https://example.com', coverUrl: 'https://images.unsplash.com/photo-1490312278390-ab64016e0aa9?auto=format&fit=crop&w=1000&q=85', published: true, approved: true, accent: 'sky' },
  { id: '3', name: 'Little Loom', slug: 'little-loom', category: 'Clothing', description: 'Small-batch textiles with a soft spot for bright colour.', city: 'Koregaon Park', address: '4 Lane 6, Pune', phone: '+912040551203', email: 'hi@littleloom.example', website: 'https://example.com', coverUrl: 'https://images.unsplash.com/photo-1441986300917-64674bd600d8?auto=format&fit=crop&w=1000&q=85', published: true, approved: true, accent: 'rose' },
  { id: '4', name: 'Sunday Objects', slug: 'sunday-objects', category: 'Art & gifts', description: 'Prints, paper things, and gifts for people who notice details.', city: 'Fort Kochi', address: '27 Princess Street, Kochi', phone: '+914840551204', email: 'shop@sundayobjects.example', website: 'https://example.com', coverUrl: 'https://images.unsplash.com/photo-1513519245088-0e12902e5a38?auto=format&fit=crop&w=1000&q=85', published: true, approved: true, accent: 'violet' },
  { id: '5', name: 'Common Ground', slug: 'common-ground', category: 'Coffee & food', description: 'A neighborhood kitchen for lunch that turns into dinner.', city: 'Koregaon Park', address: '18 North Main Road, Pune', phone: '+912040551205', email: 'table@commonground.example', website: 'https://example.com', coverUrl: 'https://images.unsplash.com/photo-1414235077428-338989a2e8c0?auto=format&fit=crop&w=1000&q=85', published: true, approved: true, accent: 'lime' },
  { id: '6', name: 'Studio Anokhi', slug: 'studio-anokhi', category: 'Wellness', description: 'A calm corner for movement, breath, and feeling like yourself.', city: 'Indiranagar', address: '3 12th Main, Bengaluru', phone: '+918045551206', email: 'namaste@anokhi.example', coverUrl: 'https://images.unsplash.com/photo-1544367567-0f2fcb009e0b?auto=format&fit=crop&w=1000&q=85', published: true, approved: true, accent: 'sky' },
]

const categories = ['Everything', 'Coffee & food', 'Home & living', 'Clothing', 'Art & gifts', 'Wellness']

function getSignInErrorCode(error: unknown) {
  return typeof error === 'object' && error !== null && 'code' in error ? String(error.code) : ''
}

function getSignInErrorMessage(error: unknown) {
  const code = getSignInErrorCode(error)
  if (code === 'auth/unauthorized-domain') {
    return 'This site is not authorized in Firebase Authentication. Add localhost under Authentication settings.'
  }
  if (code === 'auth/operation-not-allowed') {
    return 'Google sign-in is disabled for this Firebase project. Enable Google under Authentication sign-in providers.'
  }
  if (code === 'auth/operation-not-supported-in-this-environment' || code === 'auth/web-storage-unsupported') {
    return 'This browser cannot complete Google sign-in. Open http://localhost:5173 in Chrome and sign in there.'
  }
  if (code === 'auth/popup-blocked' || code === 'auth/popup-closed-by-user') {
    return 'The sign-in window could not be completed. Try opening the site in Chrome.'
  }
  return code ? `Google Sign-In failed (${code}).` : 'Google Sign-In could not be completed. Please try again.'
}

function App() {
  const [user, setUser] = useState<User | null>(null)
  const [authReady, setAuthReady] = useState(!auth)
  const [menuOpen, setMenuOpen] = useState(false)
  const [notice, setNotice] = useState('')

  useEffect(() => {
    if (!auth) return
    return onAuthStateChanged(auth, (nextUser) => {
      setUser(nextUser)
      setAuthReady(true)
    })
  }, [])

  useEffect(() => {
    if (!auth) return
    getRedirectResult(auth).catch((error: unknown) => {
      setNotice(getSignInErrorMessage(error))
    })
  }, [])

  async function signIn() {
    if (!auth || !googleProvider) {
      setNotice('Firebase web config is missing from this build. For Docker, use --env-file .env.local and rebuild.')
      return
    }
    try {
      await signInWithPopup(auth, googleProvider)
    } catch (error) {
      const code = getSignInErrorCode(error)
      if (code === 'auth/popup-blocked' || code === 'auth/popup-closed-by-user') {
        try {
          await signInWithRedirect(auth, googleProvider)
          return
        } catch (redirectError) {
          setNotice(getSignInErrorMessage(redirectError))
          return
        }
      }
      setNotice(getSignInErrorMessage(error))
    }
  }

  async function signOutUser() {
    if (auth) await signOut(auth)
    setUser(null)
  }

  return (
    <BrowserRouter>
      <div className="site-shell">
        <header className="topbar">
          <Link className="wordmark" to="/" aria-label="e-CardMitra home"><span className="wordmark-mark"><img src="/ecardmitra-card.svg" width="21" height="21" alt="" /></span><span>e-CardMitra</span></Link>
          <button className="mobile-menu icon-button" aria-label={menuOpen ? 'Close menu' : 'Open menu'} onClick={() => setMenuOpen(!menuOpen)}>{menuOpen ? <X size={19} /> : <Menu size={19} />}</button>
          <nav className={`main-nav ${menuOpen ? 'is-open' : ''}`}><Link to="/" onClick={() => setMenuOpen(false)}>Explore</Link><Link to="/vendor" onClick={() => setMenuOpen(false)}>For shop owners</Link><Link to="/admin" onClick={() => setMenuOpen(false)}>Admin</Link></nav>
          <div className="topbar-actions">{user ? <button className="account-button" onClick={signOutUser}><span className="avatar">{user.displayName?.[0] ?? 'Y'}</span><span className="account-name">{user.displayName?.split(' ')[0] ?? 'Account'}</span><span className="sign-out-label">Sign out</span></button> : <button className="button button-dark button-small" onClick={signIn}>Sign in <ArrowUpRight size={15} /></button>}</div>
        </header>
        {notice && <div className="notice" role="status">{notice}<button aria-label="Dismiss notification" onClick={() => setNotice('')}><X size={15} /></button></div>}
        <Routes>
          <Route path="/" element={<DirectoryPage />} />
          <Route path="/shops/:slug" element={<ShopPage />} />
          <Route path="/vendor" element={<VendorPage user={user} authReady={authReady} onSignIn={signIn} />} />
          <Route path="/admin" element={<AdminPage user={user} authReady={authReady} onSignIn={signIn} />} />
          <Route path="*" element={<NotFound />} />
        </Routes>
        <footer className="footer"><Link className="wordmark footer-mark" to="/"><span className="wordmark-mark"><img src="/ecardmitra-card.svg" width="19" height="19" alt="" /></span><span>e-CardMitra</span></Link><small className="footer-copyright" style={{ fontSize: 'inherit' }}>© 2026 e-CardMitra. All Rights Reserved.</small><div><Link to="/vendor">List your shop <ArrowUpRight size={14} /></Link><span className="footer-free">Free, always</span></div></footer>
      </div>
    </BrowserRouter>
  )
}

function DirectoryPage() {
  const [shops, setShops] = useState(sampleShops)
  const [category, setCategory] = useState('Everything')
  const [query, setQuery] = useState('')
  const [preview, setPreview] = useState(true)

  useEffect(() => {
    let active = true
    getPublicShops().then((response) => {
      if (active && response.items.length) {
        setShops(response.items)
        setPreview(false)
      }
    }).catch(() => undefined)
    return () => { active = false }
  }, [])

  const filtered = useMemo(() => shops.filter((shop) => {
    const matchesCategory = category === 'Everything' || shop.category === category
    const searchable = `${shop.name} ${shop.description} ${shop.category} ${shop.city}`.toLowerCase()
    return matchesCategory && searchable.includes(query.toLowerCase()) && shop.published
  }), [category, query, shops])

  return (
    <main>
      <section className="hero-band">
        <div className="hero-copy"><div className="eyebrow"><span className="live-dot" /> Your neighborhood, well found</div><h1>Good things<br />are <span>closer</span><br className="mobile-break" /> than you think.</h1><p>Meet the independent shops, studios, and tables that make a place feel like yours.</p><a className="button button-orange" href="#directory">Find your next favourite <ArrowDownRight size={17} /></a><div className="hero-footnote"><Users size={15} /><span>Made for local people, by local people</span></div></div>
        <div className="hero-visual"><img src="https://images.unsplash.com/photo-1554118811-1e0d58224f24?auto=format&fit=crop&w=1400&q=90" alt="Sunlit neighborhood coffee shop with tables and plants" /><div className="hero-stamp"><Sparkles size={17} /><span>Small shops.<br />Big character.</span></div><div className="hero-photo-caption"><span>01 / 06</span><span>Indiranagar, Bengaluru</span></div></div>
        <div className="hero-side-note">A better kind of local search <ArrowDownRight size={15} /></div>
      </section>
      <section className="directory-section" id="directory">
        <div className="section-heading"><div><div className="eyebrow muted-eyebrow">The neighborhood edit</div><h2>Find your kind of place<span className="orange-dot">.</span></h2></div><span className="listing-count">{filtered.length.toString().padStart(2, '0')} places <span className="count-divider">/</span> {shops.length.toString().padStart(2, '0')}</span></div>
        <div className="search-row"><label className="search-box"><Search size={18} /><input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="A name, a craving, a corner..." aria-label="Search shops" />{query && <button aria-label="Clear search" onClick={() => setQuery('')}><X size={15} /></button>}</label></div>
        <div className="category-row" aria-label="Filter by category">{categories.map((item) => <button key={item} className={`category-chip ${category === item ? 'active' : ''}`} onClick={() => setCategory(item)}>{item}</button>)}{preview && <span className="preview-label"><span /> Preview listings</span>}</div>
        <div className="shop-grid">{filtered.map((shop, index) => <ShopCard key={shop.id} shop={shop} index={index} />)}</div>
        {filtered.length === 0 && <div className="empty-state"><Search size={24} /><h3>No places found just yet.</h3><p>Try a different search or category.</p><button className="text-action" onClick={() => { setQuery(''); setCategory('Everything') }}>Clear filters <ArrowRight size={15} /></button></div>}
        <div className="directory-bottom"><span>End of the good list. For now.</span><span>Know a place we should know? <Link to="/vendor">Add it here <ArrowUpRight size={13} /></Link></span></div>
      </section>
      <section className="owner-strip"><div className="owner-icon"><Store size={21} /></div><div><span className="eyebrow">For the people behind the counter</span><h2>Your shop deserves<br />a little more <em>local love.</em></h2></div><p>Set up a free digital visiting card. Get found, get shared, and keep doing what you do best.</p><Link to="/vendor" className="button button-dark">Meet your shop page <ArrowUpRight size={16} /></Link><span className="owner-doodle">✳</span></section>
    </main>
  )
}

function ShopCard({ shop, index }: { shop: Shop; index: number }) {
  return (
    <article className={`shop-card accent-${shop.accent ?? ['citrus', 'sky', 'rose'][index % 3]}`} style={{ animationDelay: `${index * 70}ms` }}>
      <Link className="shop-photo" to={`/shops/${shop.slug}`}>{shop.coverUrl ? <img src={shop.coverUrl} alt={`${shop.name} storefront or products`} loading="lazy" /> : <div className="photo-placeholder"><Store size={38} /></div>}{shop.logoUrl && <span className="shop-logo-mark"><img src={shop.logoUrl} alt={`${shop.name} logo`} loading="lazy" /></span>}<span className="photo-category">{shop.category}</span><span className="photo-arrow"><ArrowUpRight size={17} /></span></Link>
      <div className="shop-card-body"><div className="shop-title-line"><Link to={`/shops/${shop.slug}`} className="shop-name">{shop.name}</Link>{shop.approved && <BadgeCheck size={16} className="verified" aria-label="Verified listing" />}</div><p className="shop-description">{shop.description}</p><div className="shop-card-foot"><span><MapPin size={14} />{shop.city}</span><Link to={`/shops/${shop.slug}`} aria-label={`View ${shop.name}`}><ArrowRight size={16} /></Link></div></div>
    </article>
  )
}

function ShopPage() {
  const { slug = '' } = useParams()
  const navigate = useNavigate()
  const previewShop = sampleShops.find((item) => item.slug === slug)
  const [shop, setShop] = useState<Shop | null | undefined>(previewShop)
  const [catalogue, setCatalogue] = useState<{ slug: string; items: Product[]; error?: string } | null>(null)
  const [copied, setCopied] = useState(false)
  const productsLoading = catalogue?.slug !== slug
  const products = catalogue?.slug === slug ? catalogue.items : []
  const productsError = catalogue?.slug === slug ? catalogue.error : undefined

  useEffect(() => {
    let active = true
    getPublicShop(slug).then((result) => { if (active) setShop(result) }).catch(() => {
      if (active) setShop(previewShop ?? null)
    })
    return () => { active = false }
  }, [slug, previewShop])

  useEffect(() => {
    let active = true
    getPublicProducts(slug).then((result) => {
      if (active) startTransition(() => setCatalogue({ slug, items: result.items }))
    }).catch((error: unknown) => {
      if (active) setCatalogue({
        slug,
        items: [],
        error: error instanceof Error ? error.message : 'Could not load this shop catalogue.',
      })
    })
    return () => { active = false }
  }, [slug])

  if (shop === undefined || (shop && shop.slug !== slug)) return <Loading />
  if (!shop) return <NotFound />
  const shopUrl = window.location.href
  const shareTitle = shop.name
  const shareDescription = shop.description

  async function share() {
    if (navigator.share) {
      try { await navigator.share({ title: shareTitle, text: shareDescription, url: shopUrl }) } catch { return }
    } else {
      await navigator.clipboard.writeText(shopUrl)
      setCopied(true)
      window.setTimeout(() => setCopied(false), 1800)
    }
  }

  return (
    <main className="shop-page">
      <button className="back-link" onClick={() => navigate(-1)}><ArrowLeft size={16} /> Back to the neighborhood</button>
      <div className="shop-detail-hero">{shop.coverUrl ? <img src={shop.coverUrl} alt={`${shop.name} shop`} /> : <div className="photo-placeholder"><Store size={42} /></div>}{shop.logoUrl && <span className="shop-detail-logo"><img src={shop.logoUrl} alt={`${shop.name} logo`} /></span>}<span className="detail-category">{shop.category}</span></div>
      <div className="shop-detail-grid">
        <section className="shop-detail-main"><div className="eyebrow muted-eyebrow"><BadgeCheck size={15} className="verified" /> Independent local business</div><h1>{shop.name}<span className="orange-dot">.</span></h1>{shop.description && <p className="detail-description">{shop.description}</p>}<div className="detail-tags">{(shop.address || shop.city) && <span><MapPin size={15} />{[shop.address, shop.city].filter(Boolean).join(', ')}</span>}<span><Clock3 size={15} />Hours vary by day</span></div>
          <div className="contact-actions">{shop.phone && <><a className="button button-orange" href={`tel:${shop.phone}`}><Phone size={16} /> Call the shop</a><a className="button button-outline" href={`https://wa.me/${shop.phone.replace(/\D/g, '')}`} target="_blank" rel="noreferrer">WhatsApp <ArrowUpRight size={15} /></a></>}{shop.email && <a className="button button-outline" href={`mailto:${shop.email}`}><Mail size={15} /> Email</a>}{(shop.address || shop.city) && <a className="button button-outline" href={`https://maps.google.com/?q=${encodeURIComponent([shop.address, shop.city].filter(Boolean).join(', '))}`} target="_blank" rel="noreferrer"><MapPin size={15} /> Directions</a>}</div>
          {shop.website && <a className="website-link" href={shop.website} target="_blank" rel="noreferrer">Visit their website <ExternalLink size={14} /></a>}
          <a className="text-action save-contact" href={`data:text/vcard;charset=utf-8,${encodeURIComponent(`BEGIN:VCARD\nVERSION:3.0\nFN:${shop.name}\nTEL:${shop.phone ?? ''}\nEMAIL:${shop.email ?? ''}\nADR:;;${shop.address ?? ''};${shop.city ?? ''};;;;\nEND:VCARD`)}`} download={`${shop.slug}.vcf`}><ArrowDownRight size={15} /> Save contact card</a>
          {(productsLoading || productsError || products.length > 0) && <section className="public-catalogue"><div className="eyebrow muted-eyebrow">From the catalogue</div>{productsLoading ? <p className="catalogue-note">Loading catalogue...</p> : productsError ? <p className="catalogue-note" role="alert">{productsError}</p> : <div className="public-product-list">{products.map((product) => <article key={product.id}>{product.imageUrl && <img src={product.imageUrl} alt="" loading="lazy" />}<div><strong>{product.name}</strong>{product.description && <p>{product.description}</p>}</div>{product.price != null && <span>{new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 2 }).format(product.price)}</span>}</article>)}</div>}</section>}
        </section>
        <aside className="share-panel"><div className="share-panel-head"><span className="eyebrow">Take us with you</span><button onClick={share} className="share-button" aria-label="Share visiting card">{copied ? <Check size={17} /> : <ArrowUpRight size={17} />}</button></div><div className="qr-wrap"><QRCodeSVG value={shopUrl} size={150} level="M" includeMargin fgColor="#17251f" /></div><p>Scan to open this shop's<br />digital visiting card.</p><button onClick={share} className="text-action">{copied ? 'Link copied' : 'Share this place'} <ArrowUpRight size={15} /></button></aside>
      </div>
    </main>
  )
}

function Loading() { return <main className="loading-state"><span className="loading-spinner" /><p>One moment...</p></main> }

function NotFound() { return <main className="not-found"><span className="eyebrow">Well, this is awkward</span><h1>We can't find<br />that place<span className="orange-dot">.</span></h1><Link className="button button-dark" to="/">Back to exploring <ArrowLeft size={16} /></Link></main> }

export default App
