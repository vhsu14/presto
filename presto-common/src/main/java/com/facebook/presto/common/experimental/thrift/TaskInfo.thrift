namespace java com.facebook.presto.common.experimental
namespace cpp facebook.presto.protocol

include "Task.thrift"
include "TaskStatus.thrift"

struct ThriftTaskInfo {
  1: Task.ThriftTaskId taskId;
  2: TaskStatus.ThriftTaskStatus taskStatus;
}