/** Reads a Blob as text; falls back to FileReader where Blob.text() is missing (older browsers, jsdom). */
export function readBlobText(blob: Blob): Promise<string> {
  if (typeof blob.text === 'function') return blob.text()
  return new Promise((resolve) => {
    const reader = new FileReader()
    reader.onload = () => resolve(String(reader.result ?? ''))
    reader.onerror = () => resolve('')
    reader.readAsText(blob)
  })
}
