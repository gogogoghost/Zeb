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

api.TestService.manyWork().then((res)=>{
    console.log("success promise",res)
    console.log(res['name'])
    console.log(res['age'])
}).catch((err)=>{
    console.log("error promise",err)
})
api.TestService.manyWork().then((res)=>{
    console.log("success promise",res)
    console.log(res['name'])
    console.log(res['age'])
}).catch((err)=>{
    
    console.log("error promise",err)
})