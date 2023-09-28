const suspendCallback = {}
const suspendObject = {}

export function suspend (isObject, fromToken, suspendToken) {
  const obj = isObject ? suspendObject : suspendCallback
  if (!obj[fromToken]) {
    obj[fromToken] = new Set()
  }
  obj[fromToken].add(suspendToken)
}

export function unsuspend (fromToken) {
  delete suspendObject[fromToken]
  delete suspendCallback[fromToken]
}

export function isSuspend (isObject, token) {
  const obj = isObject ? suspendObject : suspendCallback
  for (const s of Object.values(obj)) {
    if (s.has(token)) {
      return true
    }
  }
  return false
}