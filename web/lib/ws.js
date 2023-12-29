import { arrayBufferToHex } from "./utils"

let socketInstance = null

export function send (data) {
    socketInstance.send(data)
}

export function onMessage (callback) {
    socketInstance.onmessage = (evt) => {
        const fileReader = new FileReader()
        fileReader.onload = () => {
            // console.log(arrayBufferToHex(fileReader.result))
            callback(fileReader.result)
        }
        fileReader.readAsArrayBuffer(evt.data)
    }
}


export function connectZeb (auth, port) {
    return new Promise((resolve) => {
        const socket = new WebSocket(`ws://127.0.0.1:${port}/${auth}`)
        socket.addEventListener('open', () => {
            socketInstance = socket
            resolve()
        });
    })
}