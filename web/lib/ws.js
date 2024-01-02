import { arrayBufferToHex } from "./utils"

let socketInstance = null

export function send (data) {
    socketInstance.send(data)
}

export function onMessage (callback) {
    socketInstance.onmessage = (evt) => {
        callback(evt.data)
    }
}


export function connectZeb (auth, port) {
    return new Promise((resolve) => {
        const socket = new WebSocket(`ws://127.0.0.1:${port}/zebChannel?auth=${auth}`)
        socket.binaryType = "arraybuffer";
        socket.addEventListener('open', () => {
            socketInstance = socket
            resolve()
        });
    })
}