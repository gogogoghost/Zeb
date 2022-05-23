import getService from './bridge'

const api=getService()
// console.log(api)
if(api==null){
    throw "Get service error"
}
// api.TestService.test(
//     789,
//     "string from js",
//     false,
//     [123456,true,"string in array of js"],
//     {
//       name:"Mike"
//     },
//     function(argInt,argStr,argBool,argArr){
//         console.log(argInt,argStr,argBool,argArr)
//     },
//     {
//         success(str){
//             console.log("success:"+str)
//         },
//         fail(){

//         },
//         func(){

//         }
//     }
// )

const promise=api.TestService.manyWork()
console.log(promise)
promise.then((res)=>{
    console.log(res)
})