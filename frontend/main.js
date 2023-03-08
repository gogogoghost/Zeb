import {api} from './bridge'

function $(){
    return document.querySelector(...arguments)
}

$('#getAge').onclick=()=>{
    console.log(api.TestService.age)
}
$('#addAge').onclick=()=>{
    console.log(api.TestService.ageAdd())
}
$('#getPromise').onclick=()=>{
    const d=$('#promiseOut')
    console.log('Waiting for resolve')
    api.TestService.getPromise().then((res)=>{
        console.log('resolve:'+res)
    })
}
$('#callObj').onclick=()=>{
    const obj=api.TestService.getInnerObject()
    console.log(obj.innerFunction())
}
$('#testType').onclick=()=>{
    const res=api.TestService.testType(
        0xff,
        0x5555555555,
        -10.24,
        "Hello world",
        false,
        new Uint8Array([0x5f,0x68]),
        null,
        {name:'Jack',age:18},
        [
            "Don't worry",
            {price:100},
            [-100,200,-300],
            "Be Happy"
        ],
        function(){
            console.log(arguments)
            return "Yes Please"
        },
        {
            done(txt){
                console.log(txt)
                return 999
            }
        }
    )
    console.log(res)
}