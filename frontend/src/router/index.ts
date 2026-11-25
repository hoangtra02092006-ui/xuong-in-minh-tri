import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'

export const APP_NAME = 'Xưởng in Minh Trí'

/** Route guard input, kept separate so it can be tested without the store. */
export type HasItems = () => boolean

export function buildRoutes(hasItems: HasItems): RouteRecordRaw[] {
  return [
    {
      path: '/',
      name: 'home',
      component: () => import('@/features/home/HomeView.vue'),
      meta: { title: 'Công cụ sản xuất' },
    },
    {
      path: '/nest/upload',
      name: 'upload',
      component: () => import('@/features/nesting/UploadView.vue'),
      meta: { title: 'Nạp file', step: 1 },
    },
    {
      path: '/nest/settings',
      name: 'settings',
      component: () => import('@/features/nesting/SettingsView.vue'),
      meta: { title: 'Tham số in', step: 2 },
      beforeEnter: () => (hasItems() ? true : { name: 'upload' }),
    },
    {
      path: '/nest/result/:jobId',
      name: 'result',
      component: () => import('@/features/nesting/ResultView.vue'),
      meta: { title: 'Kết quả', step: 3 },
      props: true,
    },
    {
      path: '/:pathMatch(.*)*',
      name: 'not-found',
      component: () => import('@/features/not-found/NotFoundView.vue'),
      meta: { title: 'Không tìm thấy trang' },
    },
  ]
}

export function createAppRouter(hasItems: HasItems) {
  const router = createRouter({
    history: createWebHistory(),
    routes: buildRoutes(hasItems),
    scrollBehavior: () => ({ top: 0 }),
  })
  router.afterEach((to) => {
    document.title = `${(to.meta.title as string) ?? ''} - ${APP_NAME}`
  })
  return router
}
