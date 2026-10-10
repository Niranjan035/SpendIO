import { describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { CommandPalette } from './CommandPalette'

function renderPalette() {
  return render(
    <MemoryRouter initialEntries={['/dashboard']}>
      <Routes>
        <Route path="/dashboard" element={<CommandPalette />} />
        <Route path="/budgets" element={<p>Budgets page</p>} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('CommandPalette', () => {
  it('stays hidden until Ctrl/Cmd+K', async () => {
    renderPalette()
    const user = userEvent.setup()

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    await user.keyboard('{Control>}k{/Control}')
    expect(screen.getByRole('dialog', { name: 'Command palette' })).toBeInTheDocument()
  })

  it('filters and navigates to the chosen destination', async () => {
    renderPalette()
    const user = userEvent.setup()

    await user.keyboard('{Control>}k{/Control}')
    await user.type(screen.getByRole('combobox'), 'budget')
    await user.click(screen.getByRole('option', { name: /Budgets/ }))

    expect(await screen.findByText('Budgets page')).toBeInTheDocument()
  })
})
