package com.lody.virtual.server.am;

import android.content.ComponentName;
import android.content.Intent;

import com.lody.virtual.remote.AppTaskInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Lody
 */

class TaskRecord {
  /** 返回栈所有 activity 记录 */
  public final List<ActivityRecord> activities = Collections.synchronizedList(new ArrayList<ActivityRecord>());
  /** 返回栈 id */
  public int taskId;
  /** 用户 id */
  public int userId;
  /** 亲和关系组 */
  public String affinity;
  /** 返回栈根元素 */
  public Intent taskRoot;

  TaskRecord(int taskId, int userId, String affinity, Intent intent) {
    this.taskId = taskId;
    this.userId = userId;
    this.affinity = affinity;
    this.taskRoot = intent;
  }

  AppTaskInfo getAppTaskInfo() {
    int len = activities.size();
    if (len <= 0) {
      return null;
    }

    ComponentName top = activities.get(len - 1).component;
    return new AppTaskInfo(taskId, taskRoot, taskRoot.getComponent(), top);
  }

  public boolean isFinishing() {
    boolean allFinish = true;
    for (ActivityRecord r : activities) {
      if (!r.marked)
        allFinish = false;
    }
    return allFinish;
  }
}
