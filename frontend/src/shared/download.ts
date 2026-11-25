/** Saves a blob through a temporary link, so the browser shows its normal download. */
export function saveBlob(blob: Blob, fileName: string): void {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = fileName
  document.body.appendChild(a)
  a.click()
  a.remove()
  setTimeout(() => URL.revokeObjectURL(url), 10_000)
}

/** Opens a server download URL (zip, ready TIF) without leaving the page. */
export function openDownload(url: string): void {
  const a = document.createElement('a')
  a.href = url
  a.target = '_blank'
  a.rel = 'noopener'
  document.body.appendChild(a)
  a.click()
  a.remove()
}

/**
 * Starts a download without opening a tab, for code that runs outside a click (a TIF that
 * became READY while polling). A new tab there is a popup and Chrome blocks it; a link without
 * `target` is not. The server answers with `Content-Disposition: attachment` (spec 6.5), so the
 * page stays put even when `download` is ignored cross-origin. Returns false when it could not
 * even try (no DOM), so the caller can offer a button instead.
 */
export function startDownload(url: string): boolean {
  if (typeof document === 'undefined' || !document.body) return false
  const a = document.createElement('a')
  a.href = url
  a.download = ''
  a.style.display = 'none'
  document.body.appendChild(a)
  try {
    a.click()
    return true
  } catch {
    return false
  } finally {
    a.remove()
  }
}
