namespace java com.facebook.presto.common.experimental
namespace cpp facebook.presto.protocol

include "TupleDomain.thrift"

struct ThriftSplitContext {
  1: bool cacheable;
}