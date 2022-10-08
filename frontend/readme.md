## ZebView

[![](https://img.shields.io/npm/v/zebview-bridge.svg?style=flat-square)](https://www.npmjs.com/package/zebview-bridge)

Bridge between javascript and java on Android WebView

### Get Start

There are two parts for getting start

#### Android Part

See [ZebView](https://github.com/gogogoghost/zebview) for more information

#### Frontend Part

Add dependency

```shell
npm install zebview-bridge
//or
yarn add zebview-bridge
```

Call service

```javascript
//Import
//If not in a ZebView's environmnet, api will be null
import {api} from 'zebview-bridge'
//Call native api
const res=api.TestService.test(789,"string",false)
//Support promise
api.TestService.manyWork().then(()=>{
    //promise resolve
}).catch(()=>{
    //promise reject
})
```
