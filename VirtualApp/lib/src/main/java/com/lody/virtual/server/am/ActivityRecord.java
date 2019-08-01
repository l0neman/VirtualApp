package com.lody.virtual.server.am;

import android.content.ComponentName;
import android.os.IBinder;

/**
 * @author Lody
 */

/* package */ class ActivityRecord {
	/** 所在返回栈 */
	public TaskRecord task;
	public ComponentName component;
	/** 调用者组件 */
	public ComponentName caller;
	/** 对应客户端进程 ActivityClientRecord 记录的 binder 引用 */
	public IBinder token;
	/** 所在应用用户 id */
	public int userId;
	/** 所在进程记录 */
	public ProcessRecord process;
	public int launchMode;
	public int flags;
	public boolean marked;
	public String affinity;

	public ActivityRecord(TaskRecord task, ComponentName component, ComponentName caller, IBinder token, int userId, ProcessRecord process, int launchMode, int flags, String affinity) {
		this.task = task;
		this.component = component;
		this.caller = caller;
		this.token = token;
		this.userId = userId;
		this.process = process;
		this.launchMode = launchMode;
		this.flags = flags;
		this.affinity = affinity;
	}

}
