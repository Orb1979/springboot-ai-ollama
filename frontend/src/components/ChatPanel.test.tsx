import { fireEvent, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { sendChat } from '../api/client'
import { ChatPanel } from './ChatPanel'

vi.mock('../api/client', () => ({
  sendChat: vi.fn(),
}))

const sendChatMock = vi.mocked(sendChat)

describe('ChatPanel', () => {
  beforeEach(() => {
    sendChatMock.mockReset()
  })

  it('submits the system prompt and question when Enter is pressed', async () => {
    sendChatMock.mockResolvedValue({ message: 'I am an invoice assistant.' })
    const user = userEvent.setup()
    render(<ChatPanel />)

    await user.type(
      screen.getByLabelText(/System prompt/),
      'You help with invoices.',
    )
    await user.type(screen.getByLabelText('Question'), 'Who are you?')
    fireEvent.keyDown(screen.getByLabelText('Question'), {
      key: 'Enter',
      shiftKey: false,
    })

    expect(sendChatMock).toHaveBeenCalledWith({
      system: 'You help with invoices.',
      question: 'Who are you?',
    })
    expect(
      await screen.findByText('I am an invoice assistant.'),
    ).toBeInTheDocument()
  })

  it('submits an empty string when the optional system prompt is blank', async () => {
    sendChatMock.mockResolvedValue({ message: 'Hello!' })
    const user = userEvent.setup()
    render(<ChatPanel />)

    expect(
      screen.getByLabelText('System prompt (optional)'),
    ).toHaveAccessibleDescription(
      'Optional. Leave blank to use the model’s default behavior.',
    )
    await user.type(screen.getByLabelText('Question'), 'Hello')
    await user.click(screen.getByRole('button', { name: 'Send question' }))

    expect(sendChatMock).toHaveBeenCalledWith({
      system: '',
      question: 'Hello',
    })
  })

  it('inserts a newline instead of submitting when Shift+Enter is pressed', async () => {
    const user = userEvent.setup()
    render(<ChatPanel />)
    const question = screen.getByLabelText('Question')

    await user.type(question, 'First line')
    await user.keyboard('{Shift>}{Enter}{/Shift}Second line')

    expect(question).toHaveValue('First line\nSecond line')
    expect(sendChatMock).not.toHaveBeenCalled()
  })

  it('does not submit a blank question', () => {
    render(<ChatPanel />)

    const question = screen.getByLabelText('Question')
    expect(question).toHaveAccessibleDescription(
      'Press Enter to send. Use Shift+Enter for a new line.',
    )
    fireEvent.keyDown(question, {
      key: 'Enter',
    })

    expect(sendChatMock).not.toHaveBeenCalled()
  })

  it('shows a request error and restores the send control', async () => {
    sendChatMock.mockRejectedValue(new Error('Chat service is unavailable'))
    const user = userEvent.setup()
    render(<ChatPanel />)

    await user.type(screen.getByLabelText('Question'), 'Hello')
    await user.click(screen.getByRole('button', { name: 'Send question' }))

    expect(
      await screen.findByRole('alert', {
        name: 'Chat service is unavailable',
      }),
    ).toBeInTheDocument()
    expect(
      screen.getByRole('button', { name: 'Send question' }),
    ).toBeEnabled()
  })
})
