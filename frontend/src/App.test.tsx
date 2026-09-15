import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import App from './App'
import { listInvoices } from './api/client'

vi.mock('./api/client', async () => {
  const actual = await vi.importActual<typeof import('./api/client')>(
    './api/client',
  )
  return {
    ...actual,
    listInvoices: vi.fn(),
  }
})

const listInvoicesMock = vi.mocked(listInvoices)

describe('App', () => {
  beforeEach(() => {
    listInvoicesMock.mockReset()
    listInvoicesMock.mockResolvedValue([])
  })

  it('switches between chat, uploader, and invoices tabs', async () => {
    const user = userEvent.setup()
    render(<App />)

    expect(screen.getByRole('heading', { name: 'Ask the AI' })).toBeVisible()
    expect(screen.getByRole('tab', { name: 'Chat' })).toHaveAttribute(
      'aria-selected',
      'true',
    )

    await user.click(screen.getByRole('tab', { name: 'Invoice uploader' }))

    expect(
      screen.getByRole('heading', { name: 'Analyze an invoice' }),
    ).toBeVisible()
    expect(
      screen.getByRole('tab', { name: 'Invoice uploader' }),
    ).toHaveAttribute('aria-selected', 'true')

    await user.click(screen.getByRole('tab', { name: 'Invoices' }))

    expect(
      await screen.findByRole('heading', { name: 'Uploaded invoices' }),
    ).toBeVisible()
    expect(screen.getByRole('tab', { name: 'Invoices' })).toHaveAttribute(
      'aria-selected',
      'true',
    )
    expect(listInvoicesMock).toHaveBeenCalled()
  })

  it('supports arrow-key tab navigation with one tab stop', async () => {
    const user = userEvent.setup()
    render(<App />)
    const chatTab = screen.getByRole('tab', { name: 'Chat' })
    const uploaderTab = screen.getByRole('tab', { name: 'Invoice uploader' })

    chatTab.focus()
    await user.keyboard('{ArrowRight}')

    expect(uploaderTab).toHaveFocus()
    expect(uploaderTab).toHaveAttribute('aria-selected', 'true')
    expect(chatTab).toHaveAttribute('tabindex', '-1')
    expect(uploaderTab).toHaveAttribute('tabindex', '0')
  })
})
