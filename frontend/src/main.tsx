import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import '@/styles/globals.css'
import { App } from './app/App'
import { Providers } from './app/providers'

const container = document.getElementById('root')
if (!container) throw new Error('root container missing')

createRoot(container).render(
  <StrictMode>
    <Providers>
      <App />
    </Providers>
  </StrictMode>,
)
