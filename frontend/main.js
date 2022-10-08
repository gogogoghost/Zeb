import zv from './bridge'


if(zv==null){
    throw "Get service error"
}

console.log(zv.api)
const res=zv.api.TestService.test(
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



zv.api.TestService.manyWork().then((res)=>{
    console.log("resolve",res)
}).catch((err)=>{
    console.log("reject",err)
})