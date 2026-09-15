import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import App from './App'

describe('App', () => {
  it('switches between the chat and invoice tools', async () => {
    const user = userEvent.setup()
    render(<App />)

    expect(screen.getByRole('heading', { name: 'Ask the AI' })).toBeVisible()
    expect(screen.getByRole('tab', { name: 'Chat' })).toHaveAttribute(
      'aria-selected',
      'true',
    )

    await user.click(screen.getByRole('tab', { name: 'Invoice analyzer' }))

    expect(
      screen.getByRole('heading', { name: 'Analyze an invoice' }),
    ).toBeVisible()
    expect(
      screen.getByRole('tab', { name: 'Invoice analyzer' }),
    ).toHaveAttribute('aria-selected', 'true')
    expect(
      screen.queryByRole('heading', { name: 'Ask the AI' }),
    ).not.toBeInTheDocument()
  })

  it('supports arrow-key tab navigation with one tab stop', async () => {
    const user = userEvent.setup()
    render(<App />)
    const chatTab = screen.getByRole('tab', { name: 'Chat' })
    const invoiceTab = screen.getByRole('tab', { name: 'Invoice analyzer' })

    chatTab.focus()
    await user.keyboard('{ArrowRight}')

    expect(invoiceTab).toHaveFocus()
    expect(invoiceTab).toHaveAttribute('aria-selected', 'true')
    expect(chatTab).toHaveAttribute('tabindex', '-1')
    expect(invoiceTab).toHaveAttribute('tabindex', '0')
  })
})
