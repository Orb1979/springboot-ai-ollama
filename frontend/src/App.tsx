import { useRef, useState, type KeyboardEvent } from 'react'
import { ChatPanel } from './components/ChatPanel'
import { InvoiceAnalyzer } from './components/InvoiceAnalyzer'

function App() {
  const [activeTool, setActiveTool] = useState<'chat' | 'invoice'>('chat')
  const chatTab = useRef<HTMLButtonElement>(null)
  const invoiceTab = useRef<HTMLButtonElement>(null)

  function selectTool(tool: 'chat' | 'invoice') {
    setActiveTool(tool)
    const target = tool === 'chat' ? chatTab.current : invoiceTab.current
    target?.focus()
  }

  function handleTabKeyDown(event: KeyboardEvent<HTMLButtonElement>) {
    let nextTool: 'chat' | 'invoice' | null = null

    if (event.key === 'ArrowRight' || event.key === 'ArrowDown') {
      nextTool = activeTool === 'chat' ? 'invoice' : 'chat'
    } else if (event.key === 'ArrowLeft' || event.key === 'ArrowUp') {
      nextTool = activeTool === 'chat' ? 'invoice' : 'chat'
    } else if (event.key === 'Home') {
      nextTool = 'chat'
    } else if (event.key === 'End') {
      nextTool = 'invoice'
    }

    if (nextTool) {
      event.preventDefault()
      selectTool(nextTool)
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
              ref={chatTab}
              id="chat-tab"
              type="button"
              role="tab"
              aria-selected={activeTool === 'chat'}
              aria-controls="chat-panel"
              tabIndex={activeTool === 'chat' ? 0 : -1}
              onClick={() => setActiveTool('chat')}
              onKeyDown={handleTabKeyDown}
            >
              Chat
            </button>
            <button
              ref={invoiceTab}
              id="invoice-tab"
              type="button"
              role="tab"
              aria-selected={activeTool === 'invoice'}
              aria-controls="invoice-panel"
              tabIndex={activeTool === 'invoice' ? 0 : -1}
              onClick={() => setActiveTool('invoice')}
              onKeyDown={handleTabKeyDown}
            >
              Invoice analyzer
            </button>
          </div>

          <div
            id="chat-panel"
            role="tabpanel"
            aria-labelledby="chat-tab"
            hidden={activeTool !== 'chat'}
          >
            <ChatPanel />
          </div>
          <div
            id="invoice-panel"
            role="tabpanel"
            aria-labelledby="invoice-tab"
            hidden={activeTool !== 'invoice'}
          >
            <InvoiceAnalyzer />
          </div>
        </section>
      </main>
    </div>
  )
}

export default App
