/** localStorage / sessionStorage access that never throws (private mode, quota, blocked). */
export function readJson<T>(storage: () => Storage, key: string): T | null {
  try {
    const raw = storage().getItem(key)
    return raw ? (JSON.parse(raw) as T) : null
  } catch {
    return null
  }
}

export function writeJson(storage: () => Storage, key: string, value: unknown): void {
  try {
    storage().setItem(key, JSON.stringify(value))
  } catch {
    // Quota or blocked storage: the app keeps working, only the draft is not saved.
  }
}

export function removeKey(storage: () => Storage, key: string): void {
  try {
    storage().removeItem(key)
  } catch {
    // Ignore, same reason as above.
  }
}

export const local = () => localStorage
export const session = () => sessionStorage
