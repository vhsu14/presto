namespace java com.facebook.presto.common.experimental
namespace cpp facebook.presto.protocol

struct ThriftObject {
  1: string type
  2: binary serializedObject
}