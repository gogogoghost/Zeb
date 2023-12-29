// 数据类型
export const DataType = {
  NULL: 1,
  //基本类型
  STRING: 2,
  LONG: 3,
  INT: 4,
  FLOAT: 5,
  BOOLEAN: 6,
  //特殊类型
  FUNCTION: 10,
  OBJECT: 11,
  BYTEARRAY: 12,
  ARRAY: 13,
  // PROMISE: 14,
  ERROR: 15,
  JSON: 16
}

// 消息处理
export const MsgType = {
  CALLBACK: 1,
  OBJECT_CALLBACK: 2,
  RELEASE_CALLBACK: 3,
  RELEASE_OBJECT: 4,
  PROMISE_FINISH: 5,
  CALL_OBJECT: 6,
  READ_OBJECT: 7
}
