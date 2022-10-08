import zv from './bridge'

const api=zv.api

const res=api.TestService.test(
    10,
    1.1,
    "abcdefg"
    ,
    true
    ,
    [10,20]
    ,function(){
        console.log("callback!!!!!!!!!!!",arguments)
    },{
        success(obj){
            console.log("success!!!!!!!",obj)
        }
    }
)
console.log(res)



api.TestService.manyWork().then((res)=>{
    console.log("resolve",res)
}).catch((err)=>{
    console.log("reject",err)
})

console.log(api.TestService.jsonTest({
    "age":99
}))