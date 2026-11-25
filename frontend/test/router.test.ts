import { describe, expect, it } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import { buildRoutes } from '@/router'

function makeRouter(hasItems: boolean) {
  return createRouter({ history: createMemoryHistory(), routes: buildRoutes(() => hasItems) })
}

describe('routes', () => {
  it('settings is guarded: no items sends user back to upload', async () => {
    const router = makeRouter(false)
    await router.push('/nest/settings')
    expect(router.currentRoute.value.name).toBe('upload')
  })
  it('settings opens when there are items', async () => {
    const router = makeRouter(true)
    await router.push('/nest/settings')
    expect(router.currentRoute.value.name).toBe('settings')
  })
  it('result reads jobId from URL; unknown paths go to 404', async () => {
    const router = makeRouter(false)
    await router.push('/nest/result/abc')
    expect(router.currentRoute.value.params.jobId).toBe('abc')
    await router.push('/khong-co')
    expect(router.currentRoute.value.name).toBe('not-found')
  })
})
