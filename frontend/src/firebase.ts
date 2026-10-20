import { getApp, getApps, initializeApp } from '@firebase/app'
import {
  browserPopupRedirectResolver,
  browserSessionPersistence,
  GoogleAuthProvider,
  initializeAuth,
} from '@firebase/auth'

const config = {
  apiKey: import.meta.env.VITE_FIREBASE_API_KEY,
  authDomain: import.meta.env.VITE_FIREBASE_AUTH_DOMAIN,
  projectId: import.meta.env.VITE_FIREBASE_PROJECT_ID,
  appId: import.meta.env.VITE_FIREBASE_APP_ID,
}

const configured = Object.values(config).every(Boolean)
const app = configured ? (getApps().length ? getApp() : initializeApp(config)) : null

export const auth = app ? initializeAuth(app, {
  persistence: browserSessionPersistence,
  popupRedirectResolver: browserPopupRedirectResolver,
}) : null
export const googleProvider = auth ? new GoogleAuthProvider() : null
googleProvider?.setCustomParameters({ prompt: 'select_account' })
