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
})
