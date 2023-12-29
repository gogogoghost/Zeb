let currentId = 0
//获取ID
export function nextId () {
    currentId++
    if (currentId >= Number.MAX_SAFE_INTEGER) {
        currentId = 1
    }
    return currentId
}

export function arrayBufferToHex (arrayBuffer) {
    const uint8Array = new Uint8Array(arrayBuffer);
    const hexArray = Array.from(uint8Array, byte => byte.toString(16).padStart(2, '0'));
    return hexArray.join('');
}