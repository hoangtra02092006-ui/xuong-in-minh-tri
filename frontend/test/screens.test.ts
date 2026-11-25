import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import { createMockApi } from '@/api/mock'
import HomeView from '@/features/home/HomeView.vue'
import SettingsView from '@/features/nesting/SettingsView.vue'
import UploadView from '@/features/nesting/UploadView.vue'
import { buildRoutes } from '@/router'
import DecimalInput from '@/shared/ui/DecimalInput.vue'
import { useToasts } from '@/shared/toast'
import { setApi } from '@/stores/api-provider'
import { useOrderStore } from '@/stores/order'
import { file } from './helpers'

function makeRouter() {
  const order = useOrderStore()
  return createRouter({ history: createMemoryHistory(), routes: buildRoutes(() => order.items.length > 0) })
}

beforeEach(() => {
  localStorage.clear()
  useToasts().items.splice(0)
  setActivePinia(createPinia())
  setApi(
    createMockApi({
      storage: null,
      delay: () => Promise.resolve(),
      readImage: async () => ({ width: 96, height: 96 }),
      objectUrl: () => 'blob:x',
    }),
  )
})

describe('DecimalInput', () => {
  it('keeps the raw text while typing and accepts "2,5" and "2.5"', async () => {
    const w = mount(DecimalInput, { props: { value: 13, label: 'Rộng' } })
    const input = w.find('input')
    expect(input.attributes('type')).toBe('text')
    expect(input.attributes('inputmode')).toBe('decimal')
    await input.trigger('focus')
    await input.setValue('2,')
    expect(w.emitted('input')!.at(-1)).toEqual([2])
    await input.setValue('2,5')
    expect(w.emitted('input')!.at(-1)).toEqual([2.5])
    await input.setValue('2.5')
    expect(w.emitted('input')!.at(-1)).toEqual([2.5])
    await w.setProps({ value: 25 })
    expect(input.element.value).toBe('2.5') // not overwritten while focused
    await input.trigger('blur')
    expect(w.emitted('commit')!.at(-1)).toEqual(['2.5'])
    expect(input.element.value).toBe('25')
    await input.setValue('')
    expect(w.emitted('input')!.at(-1)).toEqual([null])
  })
})

describe('route guard with the order store', () => {
  it('settings without designs goes back to step 1; with designs it opens', async () => {
    const router = makeRouter()
    await router.push('/nest/settings')
    expect(router.currentRoute.value.name).toBe('upload')
    useOrderStore().addFiles([file('a', 130, 40)])
    await router.push('/nest/settings')
    expect(router.currentRoute.value.name).toBe('settings')
  })
})

describe('home', () => {
  it('has 1 active tile and 4 locked "Sắp có"; starting clears the old order', async () => {
    const order = useOrderStore()
    order.addFiles([file('old', 10, 10)])
    const router = makeRouter()
    await router.push('/')
    const w = mount(HomeView, { global: { plugins: [router] } })
    expect(w.findAll('[aria-disabled="true"]')).toHaveLength(4)
    expect(w.text().match(/Sắp có/g)).toHaveLength(4)
    await w.find('button.tool--active').trigger('click')
    await flushPromises()
    expect(order.items).toEqual([])
    expect(router.currentRoute.value.name).toBe('upload')
  })
})

describe('step 1 upload', () => {
  it('shows the empty state, uploads files one by one, fills the table and the totals', async () => {
    vi.useFakeTimers()
    const router = makeRouter()
    await router.push('/nest/upload')
    const w = mount(UploadView, { global: { plugins: [router] }, attachTo: document.body })
    expect(w.text()).toContain('Chưa có file nào')
    const continueBtn = () => w.findAll('button').find((b) => b.text().includes('Tiếp tục'))!
    expect(continueBtn().attributes('disabled')).toBeDefined()

    const input = w.find('input[type=file]')
    Object.defineProperty(input.element, 'files', {
      value: [
        new File(['%PDF'], 'nhan-13x4.pdf'),
        new File(['x'], 'sai.ai'),
        new File(['%PDF'], 'nhan-5x7.pdf'),
      ],
      configurable: true,
    })
    await input.trigger('change')
    for (let i = 0; i < 10; i++) await flushPromises()

    expect(w.findAll('tbody tr')).toHaveLength(2)
    const toasts = useToasts().items.map((t) => t.text)
    expect(toasts.some((t) => t.startsWith('sai.ai: '))).toBe(true)
    expect(toasts).toContain('Đã nạp 2 file.')
    await vi.advanceTimersByTimeAsync(1300) // progress rows clear after ~1,2 s
    expect(w.find('.queue').exists()).toBe(false)
    expect(w.text()).toContain('Tổng: 2 loại hình · 2 bản · diện tích hình 87 cm²')
    expect(continueBtn().attributes('disabled')).toBeUndefined()

    // Edit width: height follows the file ratio, undo appears, then restores.
    const width = w.find('input[aria-label="Rộng (cm) của nhan-13x4.pdf"]')
    await width.trigger('focus')
    await width.setValue('6,5')
    expect(useOrderStore().items[0]).toMatchObject({ widthMm: 65, heightMm: 20 })
    expect(w.text()).toContain('gốc 13 x 4 cm')
    await w.find('button[aria-label="Hoàn tác kích thước nhan-13x4.pdf"]').trigger('click')
    expect(useOrderStore().items[0]).toMatchObject({ widthMm: 130, heightMm: 40 })

    await w.find('button[aria-label="Xoá nhan-5x7.pdf"]').trigger('click')
    expect(w.findAll('tbody tr')).toHaveLength(1)
    w.unmount()
    vi.useRealTimers()
  })
})

describe('step 2 settings', () => {
  it('shows errors under the field, the estimate, and blocks "Ghép file" on errors', async () => {
    const order = useOrderStore()
    order.addFiles([file('a', 130, 40), file('img', 50, 50, 'IMAGE')])
    const router = makeRouter()
    await router.push('/nest/settings')
    const w = mount(SettingsView, { global: { plugins: [router] } })
    expect(w.text()).toContain('Ước lượng tối thiểu')
    expect(w.text()).toContain('0,01')
    const submit = () => w.findAll('button').find((b) => b.text().includes('Ghép file'))!
    expect(submit().attributes('disabled')).toBeUndefined()

    await w.find('input[name="sheetWidth"]').setValue('0')
    expect(w.text()).toContain('Khổ ngang phải lớn hơn 0.')
    expect(submit().attributes('disabled')).toBeDefined()
    await w.find('input[name="sheetWidth"]').setValue('57')

    await w.find('input[value="TRUE_SHAPE"]').setValue(true)
    expect(w.text()).toContain('1 mẫu sẽ được xếp như khối chữ nhật')
    expect(w.text()).toContain('Tắt sẽ chặn TẤT CẢ các góc xoay')
  })

  it('submitting a valid order goes to the result page', async () => {
    const api = createMockApi({ storage: null, delay: () => Promise.resolve() })
    setApi(api)
    const order = useOrderStore()
    order.addFiles([await api.uploadFile(new File(['%PDF'], 'nhan-13x4.pdf'))])
    const router = makeRouter()
    await router.push('/nest/settings')
    const w = mount(SettingsView, { global: { plugins: [router] } })
    await w.find('form').trigger('submit')
    await vi.waitFor(() => expect(router.currentRoute.value.name).toBe('result'))
  })
})
