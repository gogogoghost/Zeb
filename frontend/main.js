import zv from './bridge'


if(zv==null){
    throw "Get service error"
}

console.log(zv.api)
// zv.api.TestService.test(
//     10,"abcdefg",true,[10,20],()=>{
//         console.log("callback!!!!!!!!!!!")
//     },{
//         success(obj){
//             console.log("success!!!!!!!",obj)
//         }
//     }
// )
function main(){
    for(let i=0;i<20;i++){
        let worker=zv.api.TestService.getWorker()
        console.log(worker.work())
        worker=null
    }
}
// main()

// const sleep = (ms) => new Promise(r => setTimeout(r, ms));
// (async()=>{
//     while(true){
//         for(let i=0;i<100;i++){
//             const x=new Array(100)
//         }
//         await sleep(10)
//     }
// })()



// zv.api.TestService.manyWork().then((res)=>{
//     console.log("resolve",res)
// }).catch((err)=>{
//     console.log("reject",err)
// })