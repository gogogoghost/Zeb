import { encode, decode } from 'js-base64';
/**
 * 基本类型 不管直接序列化
 * function 用_func#重命名然后传递字符串
 * 带有function的object 用_object#重命名后传递字符串
 */
const FUNCTION_PREFIX="_func#"
const OBJECT_PREFIX="_object#"
const BYTEARRAY_PREFIX="_bytes#"
const PROMISE_PREFIX="_promise#"


//存储回调的map
const functionMap={}
//存储带有回调function的map
const objectMap={}
//存储待pending的promise
const promiseMap={}

//生成随机字符串
function randomString(e) {
    e = e || 32;
    let t = "ABCDEFGHJKMNPQRSTWXYZabcdefhijkmnprstwxyz2345678",
        a = t.length,
        n = "";
    for (let i = 0; i < e; i++) n += t.charAt(Math.floor(Math.random() * a));
    return n
}

/**
 * 调用Native指定模块指定代码
 */
function invokeNative(moduleName,funcName,rawArgs){
    // console.log("log:invoke native:"+arguments)
    //将argument转数组
    const args=[]
    for(let i=0;i<rawArgs.length;i++){
        const obj=rawArgs[i]
        const constructor=obj.constructor
        if(constructor === Function){
            //保存回调
            const name=`${FUNCTION_PREFIX}${randomString(16)}`
            functionMap[name]=obj
            args.push(name)
        }else if(constructor === Object){
            //判断object是否包含function
            if(Object.values(obj).find((v)=>v.constructor===Function)){
                //回调对象 保存object
                const name=`${OBJECT_PREFIX}${randomString(16)}`
                objectMap[name]=obj
                args.push(name)
            }else{
                //普通对象 传递数据
                args.push(obj)
            }
        }else if(constructor === Uint8Array){
            //字节数组 转为字符串
            const data=encode(obj)
            args.push(`${BYTEARRAY_PREFIX}${data}`)
        }else{
            //普通数据
            args.push(obj)
        }
    }
    //window.Bridge.invoke 调用native方法
    const res=window.zebview.callService(moduleName,funcName,JSON.stringify(args))
    return processArgs(res)[0]
}

/**
 * 处理回调的参数 对象
 */
function processArg(obj){
    if(obj&&obj.constructor===String){
        //对象不为null 且为字符串形式
        if(obj.startsWith(BYTEARRAY_PREFIX)){
            //字节数组
            return decode(obj.substring(BYTEARRAY_PREFIX.length,obj.length))
        }else if(obj.startsWith(PROMISE_PREFIX)){
            //promise
            const id=obj.substring(PROMISE_PREFIX.length,obj.length)
            return new Promise((resolve,reject)=>{
                promiseMap[id]={
                    resolve,
                    reject
                }
            })
        }
    }
    return obj
}

/**
 * 处理回调的参数 数组
 */
function processArgs(argsString){
    const args=JSON.parse(argsString)
    for(let i=0;i<args.length;i++){
        args[i]=processArg(args[i])
    }
    return args
}

/**
 * native回调js的方法
 */
window.invokeCallback=function(name,argsString){
    // console.log("log:invoke callback:"+arguments)
    const func=functionMap[name]
    if(func){
        func(...processArgs(argsString))
    }
}

/**
 * native调用js对象中的方法
 */
window.invokeObjectCallback=function(name,funcName,argsString){
    // console.log("log:invoke object callback:"+arguments)
    const func=(objectMap[name]||{})[funcName]
    if(func){
        func(...processArgs(argsString))
    }
}

/**
 * native回调js中的promise
 */
window.finalizePromise=function(id,isSuccess,argsString){
    // console.log("log:invoke promise:"+argsString)
    const obj=promiseMap[id]
    if(obj){
        const args=processArgs(argsString)
        if(isSuccess){
            obj.resolve(args[0])
        }else{
            obj.reject(args[0])
        }
        delete promiseMap[id]
    }
}

/**
 * 释放一个回调
 */
window.releaseCallback=function(name){
    // console.log("log:release function")
    delete functionMap[name]
}

/**
 * 释放一个对象
 */
window.releaseObject=function(name){
    // console.log("log:release object")
    delete objectMap[name]
}

/**
 * 创建一个proxy代理的api
 */
function createApi(name,funcList=[]){
    // console.log("log:createApi:"+arguments)
    return new Proxy({},{
        get(target,key){
            for(const func of funcList){
                if(func===key){
                    return function(){
                        //调用该方法
                        return invokeNative(name,func,arguments)
                    }
                }
            }
            return undefined
        }
    })
}

//服务存储
let api={}
let hasInit=false

/**
 * 返回一个方法 当bridge初始化完成后接收回调
 */
export default function (){
    if(!hasInit){
        if(!window.zebview){
            return null
        }
        const apiMap=JSON.parse(window.zebview.getServices())
        for(const key in apiMap){
            api[key]=createApi(key,apiMap[key])
        }
        hasInit=true
    }
    return api
}
