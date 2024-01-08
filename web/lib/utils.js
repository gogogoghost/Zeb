function IdGnerator () {
    let currentId = 0
    return {
        nextId () {
            currentId++
            if (currentId >= 2147483647) {
                currentId = 1
            }
            return currentId
        }
    }
}

export const promiseIdGen = new IdGnerator()
export const objIdGen = new IdGnerator()
export const callbackIdGen = new IdGnerator()

export function arrayBufferToHex (arrayBuffer) {
    const uint8Array = new Uint8Array(arrayBuffer);
    const hexArray = Array.from(uint8Array, byte => byte.toString(16).padStart(2, '0'));
    return hexArray.join('');
}