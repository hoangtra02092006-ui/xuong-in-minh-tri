import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import ToastHost from '@/shared/ui/ToastHost.vue'
import { toast, useToasts } from '@/shared/toast'

describe('ToastHost', () => {
  beforeEach(() => useToasts().items.splice(0))

  it('renders an action button that runs once and closes the toast', async () => {
    const run = vi.fn()
    toast.action('success', 'Tấm 2: TIF đã xong.', { label: 'Tải TIF tấm 2', run })
    toast.info('Không có nút')
    const w = mount(ToastHost)
    const buttons = w.findAll('.toast__action')
    expect(buttons).toHaveLength(1)
    expect(buttons[0].text()).toContain('Tải TIF tấm 2')
    await buttons[0].trigger('click')
    expect(run).toHaveBeenCalledTimes(1)
    expect(w.findAll('.toast__action')).toHaveLength(0)
    expect(useToasts().items).toHaveLength(1)
    await w.find('.toast__close').trigger('click')
    expect(useToasts().items).toHaveLength(0)
  })
})
