import getService from './bridge'

const api=getService()
console.log(api)
api.JavaService.doSomething(
    789,
    "string from js",
    false,
    [123456,true,"string in array of js"],
    function(argInt,argStr,argBool,argArr){
        console.log(argInt,argStr,argBool,argArr)
    },
    {
        success(str){
            console.log("success:"+str)
        },
        fail(){

        },
        func(){

        }
    }
)
