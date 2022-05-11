import { encode, decode } from 'js-base64';
/**
 * 基本类型 不管直接序列化
 * function 用_func#重命名然后传递字符串
 * 带有function的object 用_object#重命名后传递字符串
 */
const FUNCTION_PREFIX="_func#"
const OBJECT_PREFIX="_object#"
const BYTEARRAY_PREFIX="_bytes#"


//存储回调的map
const functionMap={}
//存储带有回调function的map
const objectMap={}

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
    console.log("log:invoke native:"+arguments)
    //将argument转数组
    const args=[]
    for(let i=0;i<rawArgs.length;i++){
        const obj=rawArgs[i]
        if(obj instanceof Function){
            //保存回调
            const name=`${FUNCTION_PREFIX}${randomString(16)}`
            functionMap[name]=obj
            args.push(name)
        }else if(obj instanceof Object){
            //保存object
            const name=`${OBJECT_PREFIX}${randomString(16)}`
            objectMap[name]=obj
            args.push(name)
        }else if(obj instanceof Uint8Array){
            //字节数组 转为字符串
            const data=encode(obj)
            args.push(`${BYTEARRAY_PREFIX}${data}`)
        }else{
            //普通数据
            args.push(obj)
        }
    }
    //window.Bridge.invoke 调用native方法
    return window.bridge.call(moduleName,funcName,JSON.stringify(args))
}

/**
 * 处理回调的参数
 */
function processArgs(argsString){
    const args=JSON.parse(argsString)
    for(let i=0;i<args.length;i++){
        const obj=args[i]
        if(obj&&obj.startsWith&&obj.startsWith(BYTEARRAY_PREFIX)){
            args[i]=decode(obj.substring(BYTEARRAY_PREFIX.length,obj.length))
        }
    }
    return args
}

/**
 * native回调js的方法
 */
window.invokeCallback=function(name,argsString){
    console.log("log:invoke callback:"+arguments)
    const func=functionMap[name]
    if(func){
        func(...processArgs(argsString))
    }
}

/**
 * native调用js对象中的方法
 */
window.invokeObjectCallback=function(name,funcName,argsString){
    console.log("log:invoke object callback:"+arguments)
    const func=(objectMap[name]||{})[funcName]
    if(func){
        func(...processArgs(argsString))
    }
}

/**
 * 释放一个回调
 */
window.releaseCallback=function(name){
    console.log("log:release function")
    delete functionMap[name]
}

/**
 * 释放一个对象
 */
window.releaseObject=function(name){
    console.log("log:release object")
    delete objectMap[name]
}

/**
 * 创建一个proxy代理的api
 */
function createApi(name,funcList=[]){
    console.log("log:createApi:"+arguments)
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
//初始化回调
let initCallback=undefined

/**
 * 初始化完成 native调用 触发完成
 */
window.initDone=function(){
    console.log("log:init done")
    if(initCallback){
        initCallback(api)
        initCallback=null
    }
}
const apiMap=JSON.parse(window.bridge.init())
for(const key in apiMap){
    api[key]=createApi(key,apiMap[key])
}

export default function (callback){
    callback(api)
}
