import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { server } from '../test/server'
import { loginResponse, problem, signIn, userSummary } from '../test/fixtures'
import { api } from './apiClient'
import { authStore } from './authStore'
import { ApiError } from './errors'

const user = userSummary(['RECRUITER'])

describe('apiClient', () => {
  it('sends the bearer token and parses JSON', async () => {
    signIn(user, 'token-abc')
    let authorization: string | null = null
    server.use(
      http.get('*/api/v1/things', ({ request }) => {
        authorization = request.headers.get('Authorization')
        return HttpResponse.json({ ok: true })
      }),
    )

    await expect(api<{ ok: boolean }>('/api/v1/things')).resolves.toEqual({ ok: true })
    expect(authorization).toBe('Bearer token-abc')
  })

  it('serialises query parameters and skips empty ones', async () => {
    signIn(user)
    let search = ''
    server.use(
      http.get('*/api/v1/things', ({ request }) => {
        search = new URL(request.url).search
        return HttpResponse.json({})
      }),
    )

    await api('/api/v1/things', { query: { page: 2, q: '', role: undefined, enabled: false } })

    expect(search).toBe('?page=2&enabled=false')
  })

  it('refreshes once on 401 and replays the request with the new token', async () => {
    signIn(user, 'expired')
    const seen: (string | null)[] = []
    server.use(
      http.get('*/api/v1/things', ({ request }) => {
        seen.push(request.headers.get('Authorization'))
        return request.headers.get('Authorization') === 'Bearer fresh'
          ? HttpResponse.json({ ok: true })
          : problem(401, 'UNAUTHENTICATED', 'Authentication required.')
      }),
      http.post('*/api/v1/auth/refresh', ({ request }) => {
        expect(request.headers.get('X-Requested-With')).toBe('XMLHttpRequest')
        return HttpResponse.json(loginResponse(user, 'fresh'))
      }),
    )

    await expect(api('/api/v1/things')).resolves.toEqual({ ok: true })
    expect(seen).toEqual(['Bearer expired', 'Bearer fresh'])
    expect(authStore.getSnapshot().accessToken).toBe('fresh')
  })

  it('shares one refresh between concurrent requests', async () => {
    signIn(user, 'expired')
    let refreshCalls = 0
    server.use(
      http.get('*/api/v1/things', ({ request }) =>
        request.headers.get('Authorization') === 'Bearer fresh'
          ? HttpResponse.json({ ok: true })
          : problem(401, 'UNAUTHENTICATED', 'Authentication required.'),
      ),
      http.post('*/api/v1/auth/refresh', async () => {
        refreshCalls++
        return HttpResponse.json(loginResponse(user, 'fresh'))
      }),
    )

    await Promise.all([api('/api/v1/things'), api('/api/v1/things'), api('/api/v1/things')])

    expect(refreshCalls).toBe(1)
  })

  it('signs the user out when the refresh is rejected', async () => {
    signIn(user, 'expired')
    server.use(
      http.get('*/api/v1/things', () => problem(401, 'UNAUTHENTICATED', 'Authentication required.')),
      http.post('*/api/v1/auth/refresh', () => problem(401, 'UNAUTHENTICATED', 'Authentication required.')),
    )

    await expect(api('/api/v1/things')).rejects.toMatchObject({ status: 401 })
    expect(authStore.getSnapshot().status).toBe('anonymous')
  })

  it('does not try to refresh for unauthenticated endpoints such as login', async () => {
    let refreshCalls = 0
    server.use(
      http.post('*/api/v1/auth/login', () => problem(401, 'UNAUTHENTICATED', 'Invalid email or password.')),
      http.post('*/api/v1/auth/refresh', () => {
        refreshCalls++
        return problem(401, 'UNAUTHENTICATED', 'nope')
      }),
    )

    await expect(api('/api/v1/auth/login', { method: 'POST', body: {}, auth: false })).rejects.toBeInstanceOf(ApiError)
    expect(refreshCalls).toBe(0)
  })

  it('turns problem+json into an ApiError with field errors and correlation id', async () => {
    signIn(user)
    server.use(
      http.post('*/api/v1/things', () =>
        problem(400, 'VALIDATION_FAILED', 'One or more fields are invalid.', {
          errors: [{ field: 'email', message: 'must not be blank' }],
          correlationId: 'req-1',
        }),
      ),
    )

    const error = (await api('/api/v1/things', { method: 'POST', body: {} }).catch((e: unknown) => e)) as ApiError

    expect(error).toBeInstanceOf(ApiError)
    expect(error).toMatchObject({ status: 400, code: 'VALIDATION_FAILED', correlationId: 'req-1', message: 'One or more fields are invalid.' })
    expect(error.fieldErrors).toEqual([{ field: 'email', message: 'must not be blank' }])
  })

  it('maps a non-JSON error page to a generic ApiError', async () => {
    signIn(user)
    server.use(http.get('*/api/v1/things', () => new HttpResponse('<html>Bad gateway</html>', { status: 502 })))

    await expect(api('/api/v1/things')).rejects.toMatchObject({ status: 502, code: 'UNKNOWN_ERROR' })
  })

  it('reports an unreachable server as a network error', async () => {
    signIn(user)
    server.use(http.get('*/api/v1/things', () => HttpResponse.error()))

    await expect(api('/api/v1/things')).rejects.toMatchObject({ status: 0, code: 'NETWORK_ERROR' })
  })

  it.each([202, 204])('returns undefined for an empty %i response', async (status) => {
    signIn(user)
    server.use(http.post('*/api/v1/things', () => new HttpResponse(null, { status })))

    await expect(api('/api/v1/things', { method: 'POST' })).resolves.toBeUndefined()
  })
})
