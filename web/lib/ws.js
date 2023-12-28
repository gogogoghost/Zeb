export class WsClient{
    socket=null
    constructor(socket){
        this.socket=socket

    }
    send(data){
        this.socket.send(data)
    }
    onMessage(callback){
        this.socket.onmessage=(evt)=>{
            const fileReader=new FileReader()
            fileReader.onload=()=>{
                callback(fileReader.result)
            }
            fileReader.readAsArrayBuffer(evt.data)
        }
    }
}


export function connectZeb(auth,port){
    return new Promise((resolve)=>{
        const socket=new WebSocket(`ws://127.0.0.1:${port}/${auth}`)
        socket.addEventListener('open', (event) => {
            resolve(new WsClient(socket))
        });
    })
}