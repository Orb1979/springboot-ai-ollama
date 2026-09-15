import { useState, type FormEvent, type KeyboardEvent } from 'react'
import { sendChat } from '../api/client'

export function ChatPanel() {
  const [system, setSystem] = useState('')
  const [question, setQuestion] = useState('')
  const [answer, setAnswer] = useState('')
  const [error, setError] = useState('')
  const [isLoading, setIsLoading] = useState(false)

  async function submitQuestion() {
    const trimmedQuestion = question.trim()
    if (!trimmedQuestion || isLoading) {
      return
    }

    setIsLoading(true)
    setError('')
    setAnswer('')

    try {
      const response = await sendChat({
        system: system.trim(),
        question: trimmedQuestion,
      })
      setAnswer(response.message)
    } catch (requestError) {
      setError(
        requestError instanceof Error
          ? requestError.message
          : 'Unable to send your question.',
      )
    } finally {
      setIsLoading(false)
    }
  }

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    void submitQuestion()
  }

  function handleQuestionKeyDown(event: KeyboardEvent<HTMLTextAreaElement>) {
    if (
      event.key === 'Enter' &&
      !event.shiftKey &&
      !event.nativeEvent.isComposing
    ) {
      event.preventDefault()
      void submitQuestion()
    }
  }

  return (
    <section className="tool-panel" aria-labelledby="chat-heading">
      <div className="panel-heading">
        <p className="eyebrow">Conversation</p>
        <h2 id="chat-heading">Ask the AI</h2>
        <p>
          Define the assistant&apos;s role, then ask a focused question.
        </p>
      </div>

      <form className="chat-form" onSubmit={handleSubmit}>
        <div className="field">
          <label htmlFor="system-prompt">System prompt</label>
          <textarea
            id="system-prompt"
            value={system}
            onChange={(event) => setSystem(event.target.value)}
            placeholder="You are a helpful assistant who explains invoices clearly."
            rows={5}
          />
          <small>Describe who the AI is and how it should respond.</small>
        </div>

        <div className="field">
          <label htmlFor="question">Question</label>
          <textarea
            id="question"
            value={question}
            onChange={(event) => setQuestion(event.target.value)}
            onKeyDown={handleQuestionKeyDown}
            placeholder="Ask your question…"
            rows={5}
          />
          <small>
            Press Enter to send. Use Shift+Enter for a new line.
          </small>
        </div>

        <div className="form-actions">
          <button
            className="primary-button"
            type="submit"
            disabled={!question.trim() || isLoading}
          >
            {isLoading ? 'Sending…' : 'Send question'}
          </button>
        </div>
      </form>

      {error && (
        <p className="status-message error" role="alert" aria-label={error}>
          {error}
        </p>
      )}

      {answer && (
        <section className="response-card" aria-live="polite">
          <p className="eyebrow">AI response</p>
          <p className="response-copy">{answer}</p>
        </section>
      )}
    </section>
  )
}
