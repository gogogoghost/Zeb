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
const worker=zv.api.TestService.getWorker()
console.log(worker.work())
// zv.api.TestService.manyWork().then((res)=>{
//     console.log("resolve",res)
// }).catch((err)=>{
//     console.log("reject",err)
// })