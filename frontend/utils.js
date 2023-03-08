
//number转成bytes
export function num2arr(num,i){
    const buffer=new ArrayBuffer(i)
    const view=new DataView(buffer)
    if(i==1){
        view.setInt8(0,num)
    }else if(i==2){
        view.setInt16(0,num)
    }else if(i==4){
        view.setInt32(0,num)
    }else if(i==8){
        view.setBigInt64(0,BigInt(num))
    }else{
        throw new Error("Invalid number size:"+i)
    }
    return new Uint8Array(buffer)
}

//bytes转成number
export function arr2num(bytes){
    const len=bytes.length
    const view=new DataView(bytes.buffer,bytes.byteOffset,bytes.length)
    if(len==1){
        return view.getInt8()
    }else if(len==2){
        return view.getInt16()
    }else if(len==4){
        return view.getInt32()
    }else if(len==8){
        return parseInt(view.getBigInt64())
    }else{
        throw new Error("Invalid bytes size:"+len)
    }
}

//bytes转float
export function arr2float(bytes){
    const len=bytes.length
    const view=new DataView(bytes.buffer,bytes.byteOffset,bytes.length)
    if(len==4){
        //float
        return view.getFloat32()
    }else if(len==8){
        //double
        return view.getFloat64()
    }else{
        throw new Error("Not valid float byte length:"+bytes.length)
    }
}

//bytes concat
export function concatArr(...arr){
    let total=0
    for(const a of arr){
        total+=a.length
    }
    let res=new Uint8Array(total)
    let index=0
    for(const a of arr){
        res.set(a,index)
        index+=a.length
    }
    return res
}

//构造请求结构
export function makeBuffer(type,length){
    const buf=new Uint8Array(1+length)
    const view=new DataView(buf.buffer)
    view.setInt8(0,type)
    return{
        buf,
        view
    }
}

//生成随机字符串
export function randomString(e) {
    e = e || 32;
    let t = "ABCDEFGHJKMNPQRSTWXYZabcdefhijkmnprstwxyz2345678",
        a = t.length,
        n = "";
    for (let i = 0; i < e; i++) n += t.charAt(Math.floor(Math.random() * a));
    return n
}