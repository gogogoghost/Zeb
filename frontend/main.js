import './style.css'
import bridge from './bridge'

document.querySelector('#app').innerHTML = `
  <h1></h1>
  <a href="https://vitejs.dev/guide/features.html" target="_blank">Documentation</a>
`

function uint8ArrayToString(fileData){
    var dataString = "";
    for (var i = 0; i < fileData.length; i++) {
        dataString += String.fromCharCode(fileData[i]);
    }
    return dataString
}

bridge((api)=>{

    console.log("exec")
    console.log(api)

    return

    api.barcode.init()

    api.barcode.registerCallback({
        onScan(bytes){
            console.log(bytes)
        },
        onTimeout(){
            console.log("timeout!!!!!")
        },
        onError(){

        }
    })

    document.querySelector('#start').onclick=()=>{
        api.barcode.startDecode(10000)
    }
    document.querySelector('#stop').onclick=()=>{
        api.barcode.stopDecode()
    }

    api.iccReader.init()
    api.iccReader.registerCallback({
        onRead(card){
            console.log("read:")
            console.log(card)
        },
        onTimeout(){
            console.log("timeout")
        },
        onError(){
            console.log("error")
        },
        onReadWithNoSupportCard(){
            console.log("no support")
        },
        onEject(){
            console.log("eject card")
        }
    })
    document.querySelector("#icc_start").onclick=()=>{
        api.iccReader.startReadCard(10000)
    }
    document.querySelector("#icc_eject").onclick=()=>{
        api.iccReader.ejectCard()
    }
})
