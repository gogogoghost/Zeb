import {api} from './bridge'


const res=api.TestService.testByte(new Uint8Array([10,20,30,40,50,60]))
console.log(res)
// const res=api.TestService.test(
//     10,
//     1.1,
//     "abcdefg"
//     ,
//     true
//     ,
//     [10,20]
//     ,function(){
//         console.log("callback!!!!!!!!!!!",arguments)
//     },{
//         success(obj){
//             console.log("success!!!!!!!",obj)
//         }
//     }
// )
// console.log(res)



// api.TestService.manyWork().then((res)=>{
//     console.log("resolve",res)
// }).catch((err)=>{
//     console.log("reject",err)
// })

// console.log(api.TestService.jsonTest({
//     "age":99
// }))