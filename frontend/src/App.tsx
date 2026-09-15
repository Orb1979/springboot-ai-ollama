import { useRef, useState, type KeyboardEvent } from 'react'
import { ChatPanel } from './components/ChatPanel'
import { InvoiceAnalyzer } from './components/InvoiceAnalyzer'
import { InvoiceList } from './components/InvoiceList'

type AppTab = 'chat' | 'uploader' | 'invoices'

const tabs: Array<{ id: AppTab; label: string; panelId: string }> = [
  { id: 'chat', label: 'Chat', panelId: 'chat-panel' },
  { id: 'uploader', label: 'Invoice uploader', panelId: 'uploader-panel' },
  { id: 'invoices', label: 'Invoices', panelId: 'invoices-panel' },
]

function App() {
  const [activeTab, setActiveTab] = useState<AppTab>('chat')
  const chatTab = useRef<HTMLButtonElement>(null)
  const uploaderTab = useRef<HTMLButtonElement>(null)
  const invoicesTab = useRef<HTMLButtonElement>(null)
  const tabRefs = {
    chat: chatTab,
    uploader: uploaderTab,
    invoices: invoicesTab,
  }

  function selectTab(tab: AppTab) {
    setActiveTab(tab)
    tabRefs[tab].current?.focus()
  }

  function handleTabKeyDown(event: KeyboardEvent<HTMLButtonElement>) {
    const order: AppTab[] = ['chat', 'uploader', 'invoices']
    const index = order.indexOf(activeTab)
    let nextTab: AppTab | null = null

    if (event.key === 'ArrowRight' || event.key === 'ArrowDown') {
      nextTab = order[(index + 1) % order.length]
    } else if (event.key === 'ArrowLeft' || event.key === 'ArrowUp') {
      nextTab = order[(index - 1 + order.length) % order.length]
    } else if (event.key === 'Home') {
      nextTab = 'chat'
    } else if (event.key === 'End') {
      nextTab = 'invoices'
    }

    if (nextTab) {
      event.preventDefault()
      selectTab(nextTab)
    }
  }

  return (
    <div className="app-shell">
      <header className="site-header">
        <a className="brand" href="/" aria-label="Ledger AI home">
          <span className="brand-mark" aria-hidden="true">
            L
          </span>
          <span>Ledger AI</span>
        </a>
        <span className="connection-label">
          <span aria-hidden="true" />
          Spring AI workspace
        </span>
      </header>

      <main>
        <section className="workspace" aria-label="AI tools">
          <div className="tabs" role="tablist" aria-label="Choose an AI tool">
            {tabs.map((tab) => (
              <button
                key={tab.id}
                ref={tabRefs[tab.id]}
                id={`${tab.id}-tab`}
                type="button"
                role="tab"
                aria-selected={activeTab === tab.id}
                aria-controls={tab.panelId}
                tabIndex={activeTab === tab.id ? 0 : -1}
                onClick={() => setActiveTab(tab.id)}
                onKeyDown={handleTabKeyDown}
              >
                {tab.label}
              </button>
            ))}
          </div>

          <div
            id="chat-panel"
            role="tabpanel"
            aria-labelledby="chat-tab"
            hidden={activeTab !== 'chat'}
          >
            <ChatPanel />
          </div>
          <div
            id="uploader-panel"
            role="tabpanel"
            aria-labelledby="uploader-tab"
            hidden={activeTab !== 'uploader'}
          >
            <InvoiceAnalyzer />
          </div>
          <div
            id="invoices-panel"
            role="tabpanel"
            aria-labelledby="invoices-tab"
            hidden={activeTab !== 'invoices'}
          >
            <InvoiceList active={activeTab === 'invoices'} />
          </div>
        </section>
      </main>
    </div>
  )
}

export default App
