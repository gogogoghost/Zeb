import { api } from './lib/zeb'

function $ () {
    return document.querySelector(...arguments)
}

$('#getAge').onclick = () => {
    console.log(api.TestService.age)
}
$('#addAge').onclick = () => {
    console.log(api.TestService.ageAdd())
}
$('#callObj').onclick = () => {
    const obj = api.TestService.getInnerObject()
    console.log(obj.innerFunction())
}

async function complexCall () {
    const res = await api.TestService.testType(
        0xff,
        0x5555555555,
        -10.24,
        "Hello world",
        false,
        new Uint8Array([0x5f, 0x68]),
        null,
        { name: 'Jack', age: 18 },
        [
            "Don't worry",
            { price: 100 },
            [-100, 200, -300],
            "Be Happy"
        ],
        function () {
            console.log(arguments)
            return "Yes Please"
        },
        {
            done: async (txt) => {
                console.log(txt)
                return 999
            }
        }
    )
    console.log(res)
}

$('#testType').onclick = async () => {
    const start = new Date().getTime()
    await complexCall()
    console.log("time:", new Date().getTime() - start)
}
$('#start').onclick = () => {
    api.TestService.startThread(10, 3000, {
        exec () {
            console.log('exec')
        },
        timeout () {
            console.warn('timeout')
        }
    })
}
$('#stop').onclick = async () => {
    console.log('stop start')
    await api.TestService.stopThread()
    console.log('stop end')
}