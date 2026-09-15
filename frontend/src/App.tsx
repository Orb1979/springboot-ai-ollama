import { useState } from 'react'
import { ChatPanel } from './components/ChatPanel'
import { InvoiceAnalyzer } from './components/InvoiceAnalyzer'

function App() {
  const [activeTool, setActiveTool] = useState<'chat' | 'invoice'>('chat')

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
        <section className="hero-copy" aria-labelledby="page-title">
          <p className="eyebrow">AI-powered operations</p>
          <h1 id="page-title">
            Clear answers.
            <br />
            Structured invoices.
          </h1>
          <p>
            Talk to your configured AI assistant or turn an invoice into
            useful, structured data.
          </p>
        </section>

        <section className="workspace" aria-label="AI tools">
          <div className="tabs" role="tablist" aria-label="Choose an AI tool">
            <button
              id="chat-tab"
              type="button"
              role="tab"
              aria-selected={activeTool === 'chat'}
              aria-controls="chat-panel"
              onClick={() => setActiveTool('chat')}
            >
              Chat
            </button>
            <button
              id="invoice-tab"
              type="button"
              role="tab"
              aria-selected={activeTool === 'invoice'}
              aria-controls="invoice-panel"
              onClick={() => setActiveTool('invoice')}
            >
              Invoice analyzer
            </button>
          </div>

          {activeTool === 'chat' ? (
            <div id="chat-panel" role="tabpanel" aria-labelledby="chat-tab">
              <ChatPanel />
            </div>
          ) : (
            <div
              id="invoice-panel"
              role="tabpanel"
              aria-labelledby="invoice-tab"
            >
              <InvoiceAnalyzer />
            </div>
          )}
        </section>
      </main>
    </div>
  )
}

export default App
