
//number转成bytes
export function num2arr(num,size){
    const res=new Uint8Array(size)
    let i=size-1
    res[i]=num & 255
    while (num >= 256&&i>0) {
        i--
        num = num >>> 8
        res[i]=num & 255
    }
    return res
}

//bytes转成number
export function arr2num(bytes){
    let num=0
    for(let i=0;i<bytes.length;i++){
        num+=bytes[i]<<((bytes.length-1-i)*8)
    }
    return num
}

//bytes转float
export function arr2float(bytes){
    if(bytes.length==4){
        //float
        return new DataView(bytes.buffer,bytes.byteOffset,bytes.length).getFloat32()
    }else if(bytes.length==8){
        //double
        return new DataView(bytes.buffer,bytes.byteOffset,bytes.length).getFloat64()
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