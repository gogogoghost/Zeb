
console.log("before")

import {api} from './bridge'

console.log("run")
// const res=api.TestService.testByte(new Uint8Array([10,20,30,40,50,60]))
// console.log(res)
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


console.log("start!")
api.TestService.manyWork().then((res)=>{
    console.log("resolve",res)
}).catch((err)=>{
    console.log("reject")
    console.error(err)
})

console.log(api.TestService.jsonTest({
    "age":99
}))

let hasTrow=false
api.TestService.testReturnFromCallback((name)=>{
    console.log("testReturnFromCallback",name)
    if(!hasTrow){
        hasTrow=true
        throw new Error("custom error",{cause:new Error("source error")})
    }
})