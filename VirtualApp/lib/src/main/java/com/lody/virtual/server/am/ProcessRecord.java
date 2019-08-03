package com.lody.virtual.server.am;

import android.content.pm.ApplicationInfo;
import android.os.Binder;
import android.os.ConditionVariable;
import android.os.IInterface;

import com.lody.virtual.client.IVClient;
import com.lody.virtual.os.VUserHandle;

import java.util.HashSet;
import java.util.Set;

final class ProcessRecord extends Binder implements Comparable<ProcessRecord> {

  final ConditionVariable lock = new ConditionVariable();
  public final ApplicationInfo info;           // all about the first app in the process
  final public String processName;             // name of the process
  final Set<String> pkgList = new HashSet<>(); // List of packages
  /** 客户端处理程序 */
  public IVClient client;
  /** 客户端 ApplicationThread */
  IInterface appThread;
  /** 真实进程 id */
  public int pid;
  /** VA 应用程序 id */
  public int vuid;
  /** VA 客户端进程号 */
  public int vpid;
  /** VA 用户 id */
  public int userId;
  /** 执行完毕 */
  boolean doneExecuting;
  /** 优先级 */
  int priority;

  public ProcessRecord(ApplicationInfo info, String processName, int vuid, int vpid) {
    this.info = info;
    this.vuid = vuid;
    this.vpid = vpid;
    this.userId = VUserHandle.getUserId(vuid);
    this.processName = processName;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;

    if (o == null || getClass() != o.getClass())
      return false;

    ProcessRecord record = (ProcessRecord) o;
    return processName != null ? processName.equals(record.processName) : record.processName == null;
  }

  @Override
  public int compareTo(ProcessRecord another) {
    return this.priority - another.priority;
  }
}
