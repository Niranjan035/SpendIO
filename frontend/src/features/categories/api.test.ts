import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { buildCategoryQuery, categoriesApi } from './api'

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

function call(fetchMock: { mock: { calls: unknown[][] } }, index = 0): [string, RequestInit | undefined] {
  const args = fetchMock.mock.calls[index] as [string, RequestInit | undefined]
  return args
}

function page() {
  return jsonResponse({
    content: [],
    page: 0,
    size: 50,
    totalElements: 0,
    totalPages: 0,
    first: true,
    last: true,
  })
}

describe('buildCategoryQuery', () => {
  it('includes sane defaults for the list endpoint', () => {
    expect(buildCategoryQuery({})).toEqual({
      type: '',
      status: '',
      q: '',
      page: 0,
      size: 50,
      sort: '',
    })
  })
})

describe('categoriesApi', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('lists categories with the serialised filter', async () => {
    const fetchMock = vi.fn().mockResolvedValue(page())
    vi.stubGlobal('fetch', fetchMock)

    const result = await categoriesApi.list({
      type: 'EXPENSE',
      status: 'ACTIVE',
      q: 'food',
      page: 1,
      size: 25,
    })

    expect(result.content).toEqual([])
    const [url] = call(fetchMock)
    expect(url).toBe(
      '/api/v1/categories?type=EXPENSE&status=ACTIVE&q=food&page=1&size=25',
    )
  })

  it('creates a category via POST /categories', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ id: 9, name: 'Food' }))
    vi.stubGlobal('fetch', fetchMock)

    await categoriesApi.create({
      name: 'Food & Groceries',
      type: 'EXPENSE',
      icon: 'utensils',
      color: '#16a34a',
      excludeFromBudgets: false,
    })

    const [url, init] = call(fetchMock)
    expect(url).toBe('/api/v1/categories')
    expect(init?.method).toBe('POST')
    expect(JSON.parse(String(init?.body))).toEqual({
      name: 'Food & Groceries',
      type: 'EXPENSE',
      icon: 'utensils',
      color: '#16a34a',
      excludeFromBudgets: false,
    })
  })

  it('updates a category through PATCH /categories/{id}', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ id: 9, name: 'Food' }))
    vi.stubGlobal('fetch', fetchMock)

    await categoriesApi.update(9, { name: 'Groceries', excludeFromBudgets: true })

    const [url, init] = call(fetchMock)
    expect(url).toBe('/api/v1/categories/9')
    expect(init?.method).toBe('PATCH')
    expect(JSON.parse(String(init?.body))).toEqual({ name: 'Groceries', excludeFromBudgets: true })
  })

  it('archives and restores through POST /categories/{id}/...', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse({ id: 9, status: 'ARCHIVED' }))
      .mockResolvedValueOnce(jsonResponse({ id: 9, status: 'ACTIVE' }))
    vi.stubGlobal('fetch', fetchMock)

    await categoriesApi.archive(9)
    await categoriesApi.restore(9)

    expect(call(fetchMock, 0)[0]).toBe('/api/v1/categories/9/archive')
    expect(call(fetchMock, 1)[0]).toBe('/api/v1/categories/9/restore')
    expect(call(fetchMock, 0)[1]?.method).toBe('POST')
  })

  it('deletes a category, optionally reassigning its transactions', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 204 }))
    vi.stubGlobal('fetch', fetchMock)

    await categoriesApi.remove(9)
    await categoriesApi.remove(9, 3)

    expect(call(fetchMock, 0)[0]).toBe('/api/v1/categories/9')
    expect(call(fetchMock, 0)[1]?.method).toBe('DELETE')
    expect(call(fetchMock, 1)[0]).toBe('/api/v1/categories/9?reassignTo=3')
  })

  it('reads a single category from GET /categories/{id}', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ id: 9, name: 'Food' }))
    vi.stubGlobal('fetch', fetchMock)

    const category = await categoriesApi.detail(9)
    expect(category.name).toBe('Food')
    const [url] = call(fetchMock)
    expect(url).toBe('/api/v1/categories/9')
  })
})