# VirtualApp 实现分析

[TOC]

## 工具相关

### 反射工具

VirtualApp 内的反射工具，使用映射的方式，将目标反射类型的成员变量和方法映射到本地的反射信息存储类中，方便调用。

同时由于本地映射类类名和结构以及成员名变量可与目标对象对应，所以使反射具有了面向对象的特性。

示例：

```java
public class ContextImpl {
    public static Class<?> TYPE = RefClass.load(ContextImpl.class, "android.app.ContextImpl");
    @MethodParams({Context.class})
    public static RefObject<String> mBasePackageName;
    public static RefObject<Object> mPackageInfo;
    public static RefObject<PackageManager> mPackageManager;

    public static RefMethod<Context> getReceiverRestrictedContext;
}
```

```java
// user mirror class.

ContextImpl.mPackageManager.set(context, null);
Context receiverContext = ContextImpl.getReceiverRestrictedContext.call(context);
```

## 虚拟沙盒服务

VirtualApp 在它的虚拟 Server 进程实现了多个虚拟服务，负责管理沙盒内应用相关的组件和维护相关状态。

它使用一个处于虚拟 Server 进程的 `ContentProvider` 来维护这些服务的 Binder，客户端进程即可通过 `ContentProvider` 的 `call` 方法返回的 Bundle 数据包携带的匿名 Binder 获取客户端 Binder 的引用，从而达到访问服务端进程的目的。

具体原理可见：[https://github.com/cmzy/DroidService](https://github.com/cmzy/DroidService)

VirtualApp 中虚拟服务的注册：

```java
// BinderProvider.java

@Override
public boolean onCreate() {
  Context context = getContext();
  // 守护服务进程。
  DaemonService.startup(context);

  if (!VirtualCore.get().isStartup()) {
    return true;
  }

  // 1. 包管理服务。
  VPackageManagerService.systemReady();
  IPCBus.register(IPackageManager.class, VPackageManagerService.get());

  // 2. 活动管理服务。
  VActivityManagerService.systemReady(context);
  IPCBus.register(IActivityManager.class, VActivityManagerService.get());

  // 3. 用户管理服务。
  IPCBus.register(IUserManager.class, VUserManagerService.get());

  // 4. 应用管理服务。
  VAppManagerService.systemReady();
  IPCBus.register(IAppManager.class, VAppManagerService.get());

  // 5. 广播管理。
  BroadcastSystem.attach(VActivityManagerService.get(), VAppManagerService.get());

  // 6. 工作调度服务。
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
    IPCBus.register(IJobService.class, VJobSchedulerService.get());
  }

  // 7. 通知管理服务。
  VNotificationManagerService.systemReady(context);
  IPCBus.register(INotificationManager.class, VNotificationManagerService.get());

  VAppManagerService.get().scanApps();

  // 8. 账户管理服务。
  VAccountManagerService.systemReady();
  IPCBus.register(IAccountManager.class, VAccountManagerService.get());

  // 9. 虚拟存储服务。
  IPCBus.register(IVirtualStorageService.class, VirtualStorageService.get());

  // 10. 设备信息服务。
  IPCBus.register(IDeviceInfoManager.class, VDeviceManagerService.get());

  // 11. 虚拟定位服务。
  IPCBus.register(IVirtualLocationManager.class, VirtualLocationService.get());

  return true;
}
```

## 系统服务注入

VirtualApp 作为一个沙盒，必须对系统相关的服务做 hook 处理，所以 VirtualApp 在大量服务中注入了 hook 方法，对于 Server 进程和 Client 进程分别处理。

```java
// InvocationStubManager.java

private void injectInternal() throws Throwable {
  // 宿主进程不进行 hook。
  if (VirtualCore.get().isMainProcess()) {
    return;
  }

  // 针对虚拟服务端进程，VAMS 中需要启动 activty，VPMS 中沙盒内查询应用。
  if (VirtualCore.get().isServerProcess()) {
    // hook ActivityManager，将对 Context.ACTIVITY_SERVICE 和 startActivity 造成影响。
    addInjector(new ActivityManagerStub());
    // hook PackageManager，将对 context.getPackageManager() 造成影响。
    addInjector(new PackageManagerStub());
    return;
  }

  // 针对沙盒应用的客户端进程。
  if (VirtualCore.get().isVAppProcess()) {
    // hook Os，将对 Os.chmod Os.fchown 等造成影响。
    addInjector(new LibCoreStub());
    addInjector(new ActivityManagerStub());
    addInjector(new PackageManagerStub());
    // hook ActivityThread$H 类，将影响系统对 activity 等组件的处理。
    addInjector(HCallbackStub.getDefault());
    // hook SmsManager，影响短信发送功能。
    addInjector(new ISmsStub());
    // hook SubscriptionManager，影响 sim 卡信息。
    addInjector(new ISubStub());
    // hook DropBoxManager，影响日志内容获取。
    addInjector(new DropBoxManagerStub());
    // hook NotificationManager，影响通知的发送。
    addInjector(new NotificationManagerStub());
    // hook LocationManager，影响位置信息获取。
    addInjector(new LocationManagerStub());
    // hook WindowManager.
    addInjector(new WindowManagerStub());
    // hook ClipboardManager。影响剪贴板信息。
    addInjector(new ClipBoardStub());
    // hook StorageManager，影响 Obb 数据存储。
    addInjector(new MountServiceStub());
    // hook BackupManager，影响备份功能。
    addInjector(new BackupManagerStub());
    // hook TelephonyManager，影响电话相关功能。
    addInjector(new TelephonyStub());
    // hook TelephonyRegistry。
    addInjector(new TelephonyRegistryStub());
    // hook PhoneSubInfoController。
    addInjector(new PhoneSubInfoStub());
    // hook PowerManager，影响电源相关功能。
    addInjector(new PowerManagerStub());
    // hook AppWidgetManager，影响小部件相关信息。
    addInjector(new AppWidgetManagerStub());
    // hook AccountManager，影响账号相关信息。
    addInjector(new AccountManagerStub());
    // hook AudioManager，将影响音量震动控制功能。
    addInjector(new AudioManagerStub());
    // hook SearchManager，影响搜索功能。
    addInjector(new SearchManagerStub());
    // hook ContentService，影响数据更新相关。
    addInjector(new ContentServiceStub());
    // hook ConnectivityManager，影响网络状态状态。
    addInjector(new ConnectivityStub());

    if (Build.VERSION.SDK_INT >= JELLY_BEAN_MR2) {
      // hook Vibrator，影响震动控制。
      addInjector(new VibratorStub());
      // hook WifiManager，影响 wifi 链接服务。
      addInjector(new WifiManagerStub());
      // hook BluetoothManager，影响蓝牙服务。
      addInjector(new BluetoothStub());
      // hook ContextHubManager。
      addInjector(new ContextHubServiceStub());
    }

    // hook UserManager，影响多用户功能。
    if (Build.VERSION.SDK_INT >= JELLY_BEAN_MR1) {
      addInjector(new UserManagerStub());
    }

    // hook DisplayManagerGlobal。
    if (Build.VERSION.SDK_INT >= JELLY_BEAN_MR1) {
      addInjector(new DisplayStub());
    }

    if (Build.VERSION.SDK_INT >= LOLLIPOP) {
      // hook PersistentDataBlockService，持久数据。
      addInjector(new PersistentDataBlockServiceStub());
      // hook InputMethodManager，影响输入法。
      addInjector(new InputMethodManagerStub());
      // hook IMms。
      addInjector(new MmsStub());
      // hook MediaSessionManager，音频相关。
      addInjector(new SessionManagerStub());
      // hook JobScheduler，工作调度。
      addInjector(new JobServiceStub());
      // hook RestrictionsManager。
      addInjector(new RestrictionStub());
    }

    if (Build.VERSION.SDK_INT >= KITKAT) {
      // hook AlarmManager，闹钟服务。
      addInjector(new AlarmManagerStub());
      // hook AppOpsManager，动态权限相关。
      addInjector(new AppOpsManagerStub());
      // hook MediaRouter，路由器选择。
      addInjector(new MediaRouterServiceStub());
    }

    if (Build.VERSION.SDK_INT >= LOLLIPOP_MR1) {
      // hook GraphicsStatsService。
      addInjector(new GraphicsStatsStub());
      // hook UsageStatsManager，影响最近使用应用信息。
      addInjector(new UsageStatsManagerStub());
    }

    if (Build.VERSION.SDK_INT >= M) {
      // hook FingerprintService，指纹功能。
      addInjector(new FingerprintManagerStub());
      // hook NetworkManagementService。
      addInjector(new NetworkManagementStub());
    }

    if (Build.VERSION.SDK_INT >= N) {
      addInjector(new WifiScannerStub());
      // hook LauncherApps，影响快捷方式创建。
      addInjector(new ShortcutServiceStub());
      // hook DevicePolicyManager。
      addInjector(new DevicePolicyManagerStub());
    }

    if (Build.VERSION.SDK_INT >= 26) {
      addInjector(new AutoFillManagerStub());
    }
  }
}
```

## Activity 组件管理

从启动 activity 的场景开始分析 VirtualApp 对 Activity 组件的管理。

启动 activity 的入口在 `VActivityManager` 中。

### VActivityManager

```java
// VActivityManager.java

public int startActivity(Intent intent, int userId) {
  if (userId < 0) {
    return ActivityManagerCompat.START_NOT_CURRENT_USER_ACTIVITY;
  }

  // 根据 intent 信息解析出目标 activity。
  ActivityInfo info = VirtualCore.get().resolveActivityInfo(intent, userId);

  if (info == null) {
    return ActivityManagerCompat.START_INTENT_NOT_RESOLVED;
  }

  return startActivity(intent, info, null, null, null, 0, userId);
}
```

上面首先通过 intent 查询目标 activity 信息，这里的处理类似于 android 系统的处理，首先从 PMS（VirtualApp 中实现了自己的 VPackageManagerService）中查询 intent 中的 component 对应的 activity，如果没有明确的 component，那么尝试根据 intent 提供的信息匹配出 component 会弹出选择启动的提示框，选择应用打开。

这里就不再分析了，直接去下一步，调用了重载的方法。

```java
// VActivityManager.java

public int startActivity(Intent intent, ActivityInfo info, IBinder resultTo, Bundle options,
                           String resultWho, int requestCode, int userId) {
    try {
      return getService().startActivity(intent, info, resultTo, options, resultWho, requestCode, userId);
    } catch (RemoteException e) {
      return VirtualRuntime.crash(e);
    }
  }
```

上面直接调用了 `getService` 的 `stratActivity` 方法，`getService` 返回的是 `VActivityManagerService` 的客户端 Binder 对象，`startActivity` 方法是 aidl 方法，将通过进程间通信调用到 `VActivityManagerService` 中。

### VActivityManagerService

```java
// VActivityManagerService.java

@Override
public int startActivity(Intent intent, ActivityInfo info, IBinder resultTo, Bundle options,
                         String resultWho, int requestCode, int userId) {
  synchronized (this) {
    return mMainStack.startActivityLocked(userId, intent, info, resultTo, options, resultWho,
        requestCode);
  }
}
```

这里直接调用了 `mMainStack` 的 `startActivityLocked` 方法。`mMainStack` 是 `ActivityStack` 类型，负责处理 activity 栈相关的逻辑，类似 android 系统中的 `ActivityStackSupervisor` 精简版。

### ActivityStack

```java
// ActivityStack.java

int startActivityLocked(int userId, Intent intent, ActivityInfo info, IBinder resultTo, Bundle options,
                        String resultWho, int requestCode) {
  optimizeTasksLocked();

  Intent destIntent;
  // 查询调用者 activity 记录。
  ActivityRecord sourceRecord = findActivityByToken(userId, resultTo);
  // 调用者所在返回栈。
  TaskRecord sourceTask = sourceRecord != null ? sourceRecord.task : null;

  // 栈复用规则。
  ReuseTarget reuseTarget = ReuseTarget.CURRENT;
  // 栈清理规则。
  ClearTarget clearTarget = ClearTarget.NOTHING;

  boolean clearTop = containFlags(intent, Intent.FLAG_ACTIVITY_CLEAR_TOP);
  boolean clearTask = containFlags(intent, Intent.FLAG_ACTIVITY_CLEAR_TASK);

  if (intent.getComponent() == null) {
    intent.setComponent(new ComponentName(info.packageName, info.name));
  }

  if (sourceRecord != null && sourceRecord.launchMode == LAUNCH_SINGLE_INSTANCE) {
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
  }

  if (clearTop) {
    removeFlags(intent, Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
    clearTarget = ClearTarget.TOP;
  }

  // clearTask 必须和 NEW_TASK 组合使用。
  if (clearTask) {
    if (containFlags(intent, Intent.FLAG_ACTIVITY_NEW_TASK)) {
      clearTarget = ClearTarget.TASK;
    } else {
      removeFlags(intent, Intent.FLAG_ACTIVITY_CLEAR_TASK);
    }
  }

  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
    switch (info.documentLaunchMode) {
      case ActivityInfo.DOCUMENT_LAUNCH_INTO_EXISTING:
        clearTarget = ClearTarget.TASK;
        reuseTarget = ReuseTarget.DOCUMENT;
        break;

      case ActivityInfo.DOCUMENT_LAUNCH_ALWAYS:
        reuseTarget = ReuseTarget.MULTIPLE;
        break;
    }
  }

  boolean singleTop = false;

  switch (info.launchMode) {
    case LAUNCH_SINGLE_TOP: {
      singleTop = true;
      if (containFlags(intent, Intent.FLAG_ACTIVITY_NEW_TASK)) {
        reuseTarget = containFlags(intent, Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
            ? ReuseTarget.MULTIPLE
            : ReuseTarget.AFFINITY;
      }
    }
    break;

    case LAUNCH_SINGLE_TASK: {
      clearTop = false;
      clearTarget = ClearTarget.TOP;
      reuseTarget = containFlags(intent, Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
          ? ReuseTarget.MULTIPLE
          : ReuseTarget.AFFINITY;
    }
    break;

    case LAUNCH_SINGLE_INSTANCE: {
      clearTop = false;
      clearTarget = ClearTarget.TOP;
      reuseTarget = ReuseTarget.AFFINITY;
    }
    break;

    default: {
      if (containFlags(intent, Intent.FLAG_ACTIVITY_SINGLE_TOP)) {
        singleTop = true;
      }
    }
    break;
  }

  if (clearTarget == ClearTarget.NOTHING) {
    if (containFlags(intent, Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)) {
      clearTarget = ClearTarget.SPEC_ACTIVITY;
    }
  }

  if (sourceTask == null && reuseTarget == ReuseTarget.CURRENT) {
    reuseTarget = ReuseTarget.AFFINITY;
  }

  String affinity = ComponentUtils.getTaskAffinity(info);
  // 复用栈。
  TaskRecord reuseTask = null;

  switch (reuseTarget) {
    case AFFINITY:
      // 通过 userId 和 affinity 匹配历史栈中是否存在目标栈。
      reuseTask = findTaskByAffinityLocked(userId, affinity);
      break;
    case DOCUMENT:
      // 通过 userId 和 intent 匹配历史栈中是否存在目标栈（intent 为根元素）。
      reuseTask = findTaskByIntentLocked(userId, intent);
      break;
    case CURRENT:
      reuseTask = sourceTask;
      break;
    default:
      break;
  }

  if (reuseTask == null) {
    // 在新栈中启动 activity。
    startActivityInNewTaskLocked(userId, intent, info, options);
    return 0;
  }

  boolean taskMarked = false;
  boolean delivered = false;

  // 移动目标栈至前台。
  mAM.moveTaskToFront(reuseTask.taskId, 0);

  boolean startTaskToFront = !clearTask && !clearTop &&
      ComponentUtils.isSameIntent(intent, reuseTask.taskRoot);

  if (clearTarget.deliverIntent || singleTop) {
    // 根据清理规则标记栈中需要被清除的 activity。
    taskMarked = markTaskByClearTarget(reuseTask, clearTarget, intent.getComponent());

    ActivityRecord topRecord = topActivityInTask(reuseTask);

    // 如果 activity 未指定 singleTop，则会重建此 activity。
    if (clearTop && !singleTop && topRecord != null && taskMarked) {
      topRecord.marked = true;
    }

    // 顶部是目标 activity。
    if (topRecord != null && !topRecord.marked &&
        topRecord.component.equals(intent.getComponent())) {

      // 回调目标 activity 的 onNewIntent 方法。
      deliverNewIntentLocked(sourceRecord, topRecord, intent);
      delivered = true;
    }
  }

  if (taskMarked) {
    synchronized (mHistory) {
      // 结束标记的 activity。
      scheduleFinishMarkedActivityLocked();
    }
  }

  if (!startTaskToFront) {
    // 没有复用栈。
    if (!delivered) {
      // 启动 activity 进程。
      destIntent = startActivityProcess(userId, sourceRecord, intent, info);

      if (destIntent != null) {
        // 直接调用 ActivityManager 的 startActivity 方法。
        startActivityFromSourceTask(reuseTask, destIntent, info, resultWho, requestCode, options);
      }
    }
  }

  return 0;
}
```

上面根据要启动的 activity 信息，首先从 VirtualApp 自己的 ActivityRecord 记录中查询启动者 activity，然后拿到其栈信息，接下来就是根据相关启动模式得到复用栈和清理相关的 activity。上面定义了两个规则，栈复用规则和栈清理规则。

```java
// ActivityStack.java

/* 栈清理规则 **/
private enum ClearTarget {
  NOTHING,        // 什么都不做。
  SPEC_ACTIVITY,  // 清理指定 activity。
  TASK(true),     // 清理整个 task， true 为复用（将调用目标 activity onNewIntent 方法）。
  TOP(true);      // 清理指定 activity 其上的所有 activity。
}
```

```java
// ActivityStack.java

/** 栈复用规则 */
private enum ReuseTarget {
  CURRENT,  // 当前栈。
  AFFINITY, // 亲和栈。
  DOCUMENT, // 文档模式（activity 独立栈，而且退出后栈将被清理）。
  MULTIPLE  // 启动多个栈。
}
```

根据上面的逻辑，这里列出来一个表格，将对应启动标记和情况与相关规则对应起来，方便理解。

- 栈复用规则

| rule | current | affinity             | multiple                          | document       |
| ---- | ------- | -------------------- | --------------------------------- | -------------- |
| -    | default | singleTop + NEW_TASK | document_always                   | document_exist |
|      |         | singleTask           | singleTop + NEW_TASK + MULTI_TASK |                |
|      |         | singleInstance       | singleTask + MULTI_TASK           |                |

- 栈清理规则

| nothing | spec_activity   | task                 | top            |
| ------- | --------------- | -------------------- | -------------- |
| -       | record_to_front | clear_task + newTask | clearTop       |
|         |                 |                      | singleTask     |
|         |                 |                      | singleInstance |

继续下面的分析，这里选择逻辑比较清晰的 `startActivityInNewTaskLocked` 进行分析：

```java
// ActivityStack.java

/* 在新栈中启动 activity */
private void startActivityInNewTaskLocked(int userId, Intent intent,
                                          ActivityInfo info, Bundle options) {
  // 确认 activity 进程。
  Intent destIntent = startActivityProcess(userId, null, intent, info);

  if (destIntent != null) {
    destIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    destIntent.addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
    destIntent.addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
      // noinspection deprecation
      destIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
    } else {
      destIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT);
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
      VirtualCore.get().getContext().startActivity(destIntent, options);
    } else {
      VirtualCore.get().getContext().startActivity(destIntent);
    }
  }
}
```

首先看 `startActivityProcess`，看 VirtualApp 是怎样启动 activity 进程的。

```java
// ActivityStack.java

private Intent startActivityProcess(int userId, ActivityRecord sourceRecord,
                                    Intent intent, ActivityInfo info) {
  intent = new Intent(intent);

  // 如果需要（没有查询到进程存在）则启动目标进程。
  ProcessRecord targetApp = mService.startProcessIfNeedLocked(info.processName, userId,
      info.packageName);

  if (targetApp == null) {
    return null;
  }

  // 构造占位 activity intent。
  Intent targetIntent = new Intent();
  targetIntent.setClassName(VirtualCore.get().getHostPkg(), fetchStubActivity(targetApp.vpid, info));

  ComponentName component = intent.getComponent();
  if (component == null) {
    component = ComponentUtils.toComponentName(info);
  }

  // 记录原始 component 字符串.
  targetIntent.setType(component.flattenToString());

  // 保存原始信息至占位 intent。
  StubActivityRecord saveInstance = new StubActivityRecord(intent, info,
      sourceRecord != null ? sourceRecord.component : null, userId);
  saveInstance.saveToIntent(targetIntent);

  return targetIntent;
}
```

可以看到，首先获得目标 activity 所在进程的记录对象，然后使用占位的 activity 替换原始 intent。

这里主要关注进程的启动，后面需要关注占位 activity 的处理。

启动新进程使用了 `VActivityManagerService` 的 `startProcessIfNeedLocked` 方法：

### VActivityManagerService

```java
// VActivityManagerService.java

ProcessRecord startProcessIfNeedLocked(String processName, int userId, String packageName) {
  if (VActivityManagerService.get().getFreeStubCount() < 3) {
    // 达到限制，清理进程。
    killAllApps();
  }

  // 获取包信息。
  PackageSetting ps = PackageCacheManager.getSetting(packageName);
  ApplicationInfo info = VPackageManagerService.get().getApplicationInfo(
      packageName, 0, userId);

  if (ps == null || info == null) {
    return null;
  }

  if (!ps.isLaunched(userId)) {
    // 发送首次启动广播。
    sendFirstLaunchBroadcast(ps, userId);
    // 更新状态。
    ps.setLaunched(userId, true);
    // 持久化 packageSetting。
    VAppManagerService.get().savePersistenceData();
  }

  int uid = VUserHandle.getUid(userId, ps.appId);
  ProcessRecord app = mProcessNames.get(processName, uid);

  // 已存在目标进程。
  if (app != null && app.client.asBinder().isBinderAlive()) {
    return app;
  }

  // 查询空闲的预先注册的占位进程。
  int vpid = queryFreeStubProcessLocked();

  if (vpid == -1) {
    return null;
  }

  // 执行启动进程。
  app = performStartProcessLocked(uid, vpid, info, processName);

  // 更新进程记录中的包名。
  if (app != null) {
    app.pkgList.add(info.packageName);
  }

  return app;
}
```

上面主要首先查询是否已启动了目标进程（VAMS 中有其记录），如果启动了则直接返回，否则分配空余占位进程号，启动新的进程，那么看一下怎样使用占位进程和启动新进程的。

```java
// VActivityManagerService.java

/* 查询未分配的 VA pid  */
private int queryFreeStubProcessLocked() {
  for (int vpid = 0; vpid < VASettings.STUB_COUNT; vpid++) {
    int N = mPidsSelfLocked.size();
    boolean using = false;

    while (N-- > 0) {
      // mPidsSelfLocked 是虚拟进程号和进程记录的 map。
      ProcessRecord r = mPidsSelfLocked.valueAt(N);

      if (r.vpid == vpid) {
        using = true;
        break;
      }
    }

    if (using) {
      continue;
    }

    return vpid;
  }

  return -1;
}
```

占位原来就是从定义的 `STUB_COUNT` 占位数量里查询没有记录在 `mPidsSelfLocaed` 里面的 id 号。

```java
// VactivityManagerService.java

private ProcessRecord performStartProcessLocked(int vuid, int vpid, ApplicationInfo info, String processName) {
  ProcessRecord app = new ProcessRecord(info, processName, vuid, vpid);

  Bundle extras = new Bundle();
  // 传递相关信息。
  BundleCompat.putBinder(extras, "_VA_|_binder_", app);
  extras.putInt("_VA_|_vuid_", vuid);
  extras.putString("_VA_|_process_", processName);
  extras.putString("_VA_|_pkg_", info.packageName);

  // 打电话给对应的占位 Provider。
  Bundle res = ProviderCall.call(VASettings.getStubAuthority(vpid), "_VA_|_init_process_", null, extras);

  if (res == null) {
    return null;
  }

  int pid = res.getInt("_VA_|_pid_");
  IBinder clientBinder = BundleCompat.getBinder(res, "_VA_|_client_");
  attachClient(pid, clientBinder);

  return app;
}
```

原来启动进程就是利用了 ContentProvider，VirtualApp 提前在清单文件中注册了数量和 `STUB_COUNT` 一致的占位 provider，在需要启动进程的时候直接呼叫对应 provider，系统就会启动对应 provider 进程了。

```xml
// AndroidManifest.xml

<provider
    android:name="com.lody.virtual.client.stub.StubContentProvider$C0"
    android:authorities="${applicationId}.virtual_stub_0"
    android:exported="false"
    android:process=":p0" />

<provider
    android:name="com.lody.virtual.client.stub.StubContentProvider$C1"
    android:authorities="${applicationId}.virtual_stub_1"
    android:exported="false"
    android:process=":p1" />

<provider
    android:name="com.lody.virtual.client.stub.StubContentProvider$C2"
    android:authorities="${applicationId}.virtual_stub_2"
    android:exported="false"
    android:process=":p2" />
...
```

那追溯对应 provider 看它做了什么操作。

### StubContenetProvider

```java
@Override
public Bundle call(String method, String arg, Bundle extras) {
	if ("_VA_|_init_process_".equals(method)) {
		return initProcess(extras);
	}

	return null;
}

private Bundle initProcess(Bundle extras) {
	ConditionVariable lock = VirtualCore.get().getInitLock();

	if (lock != null) {
		lock.block();
	}

	IBinder token = BundleCompat.getBinder(extras,"_VA_|_binder_");
	int vuid = extras.getInt("_VA_|_vuid_");

	VClientImpl client = VClientImpl.get();
	// 保存 ApplicationRecord 的 binder 和虚拟进程 id 至 VClientImpl。
    client.initProcess(token, vuid);

	Bundle res = new Bundle();
	BundleCompat.putBinder(res, "_VA_|_client_", client.asBinder());
	res.putInt("_VA_|_pid_", Process.myPid());

	return res;
}
```

这里保存了一些信息，就返回了，那么回到前面。

在 `ActivityStack` 的 `startActivityInNewTaskLocked` 方法中，启动进程后，就调用 context 的 `startActivity` 方法启动 activity。

```java
// ActivityStack.java

/* 在新栈中启动 activity */
private void startActivityInNewTaskLocked(int userId, Intent intent,
                                          ActivityInfo info, Bundle options) {

  Intent destIntent = startActivityProcess(userId, null, intent, info);

  if (destIntent != null) {
    destIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    destIntent.addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
    destIntent.addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
      // noinspection deprecation
      destIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
    } else {
      destIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT);
    }

    // 调用 context 的 startActivity 方法。
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
      VirtualCore.get().getContext().startActivity(destIntent, options);
    } else {
      VirtualCore.get().getContext().startActivity(destIntent);
    }
  }
}
```

到这里并没有结束，前面将原始 intent 替换成了占位 activity，是不能将沙盒内应用的真实 activity 启动起来的。

VirtualApp 对系统的 ActivityManager 做了 hook，而 context 的 `startActivity` 方法最终将回调到 `ActivityManager` 的 `startActivity` 方法，那么看一下 hook 方法做了什么。

### MethodProxies

```java
// com/lody/virtual/clien/thook/proxies/am/MethodProxies.java

static class StartActivity extends MethodProxy {

private static final String SCHEME_FILE = "file";
private static final String SCHEME_PACKAGE = "package";
private static final String SCHEME_CONTENT = "content";

@Override
public String getMethodName() {
  return "startActivity";
}

@Override
public Object call(Object who, Method method, Object... args) throws Throwable {

  Log.d("Q_M", "---->StartActivity 类");

  int intentIndex = ArrayUtils.indexOfObject(args, Intent.class, 1);
  if (intentIndex < 0) {
    return ActivityManagerCompat.START_INTENT_NOT_RESOLVED;
  }

  int resultToIndex = ArrayUtils.indexOfObject(args, IBinder.class, 2);

  Intent intent = (Intent) args[intentIndex];
  String resolvedType = (String) args[intentIndex + 1];
  IBinder resultTo = resultToIndex >= 0 ? (IBinder) args[resultToIndex] : null;

  intent.setDataAndType(intent.getData(), resolvedType);

  int userId = VUserHandle.myUserId();

  // 如果是占位 activity，直接走系统方法返回。
  if (ComponentUtils.isStubComponent(intent)) {
    return method.invoke(who, args);
  }

  // process other cases.

  // 请求卸载安装应用程序。
  if (Intent.ACTION_INSTALL_PACKAGE.equals(intent.getAction())
      || (Intent.ACTION_VIEW.equals(intent.getAction())
      && "application/vnd.android.package-archive".equals(intent.getType()))) {

    if (handleInstallRequest(intent)) {
      return 0;
    }
  } else if ((Intent.ACTION_UNINSTALL_PACKAGE.equals(intent.getAction())
      || Intent.ACTION_DELETE.equals(intent.getAction()))
      && "package".equals(intent.getScheme())) {

    if (handleUninstallRequest(intent)) {
      return 0;
    }
  }

  String resultWho = null;
  int requestCode = 0;
  Bundle options = ArrayUtils.getFirst(args, Bundle.class);

  if (resultTo != null) {
    resultWho = (String) args[resultToIndex + 1];
    requestCode = (int) args[resultToIndex + 2];
  }

  // 选择器界面。
  if (ChooserActivity.check(intent)) {
    intent.setComponent(new ComponentName(getHostContext(), ChooserActivity.class));

    intent.putExtra(Constants.EXTRA_USER_HANDLE, userId);
    intent.putExtra(ChooserActivity.EXTRA_DATA, options);
    intent.putExtra(ChooserActivity.EXTRA_WHO, resultWho);
    intent.putExtra(ChooserActivity.EXTRA_REQUEST_CODE, requestCode);

    return method.invoke(who, args);
  }

  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
    args[intentIndex - 1] = getHostPkg();
  }

  if (intent.getScheme() != null && intent.getScheme().equals(SCHEME_PACKAGE) &&
      intent.getData() != null) {

    if (intent.getAction() != null && intent.getAction().startsWith("android.settings.")) {
      intent.setData(Uri.parse("package:" + getHostPkg()));
    }
  }

  // 其他情况，先查询，再启动。
  ActivityInfo activityInfo = VirtualCore.get().resolveActivityInfo(intent, userId);
  if (activityInfo == null) {
    VLog.e("VActivityManager", "Unable to resolve activityInfo : " + intent);

    Log.d("Q_M", "---->StartActivity who=" + who);
    Log.d("Q_M", "---->StartActivity intent=" + intent);
    Log.d("Q_M", "---->StartActivity resultTo=" + resultTo);

    if (intent.getPackage() != null && isAppPkg(intent.getPackage())) {
      return ActivityManagerCompat.START_INTENT_NOT_RESOLVED;
    }

    if (INTERCEPT_BACK_HOME && Intent.ACTION_MAIN.equals(intent.getAction())
        && intent.getCategories().contains("android.intent.category.HOME")
        && resultTo != null) {
      VActivityManager.get().finishActivity(resultTo);
      return 0;
    }

    return method.invoke(who, args);
  }

  int res = VActivityManager.get().startActivity(intent, activityInfo, resultTo, options,
      resultWho, requestCode, VUserHandle.myUserId());

  if (res != 0 && resultTo != null && requestCode > 0) {
    VActivityManager.get().sendActivityResult(resultTo, resultWho, requestCode);
  }

  if (resultTo != null) {
    ActivityClientRecord r = VActivityManager.get().getActivityRecord(resultTo);

    if (r != null && r.activity != null) {
      try {
        TypedValue out = new TypedValue();
        Resources.Theme theme = r.activity.getResources().newTheme();
        theme.applyStyle(activityInfo.getThemeResource(), true);
        if (theme.resolveAttribute(android.R.attr.windowAnimationStyle, out, true)) {

          TypedArray array = theme.obtainStyledAttributes(out.data,
              new int[]{
                  android.R.attr.activityOpenEnterAnimation,
                  android.R.attr.activityOpenExitAnimation
              });

          r.activity.overridePendingTransition(array.getResourceId(0, 0), array.getResourceId(1, 0));
          array.recycle();
        }
      } catch (Throwable e) {
        // Ignore
      }
    }
  }

  return res;
}
```

上面判断如果是 Stub 组件，则不做操作，执行系统方法，那么此时占位 activity 将会传入系统被 AMS 记录下来，并执行启动，AMS 执行启动时将调用客户端进程的 `ActivityThread` 中 `ApplicaationThread` 的 `scheduleLaunchActivity` 方法执行启动 actvity 的操作，`ApplicationThread` 将调用 `ActivityThread` 的内部类 `H` 并向它发送 `LAUNCH_ACTIVITY` 命令，请求启动，VirtualApp 在此处在进行了 hook，看一下。

### HCallbackStub

```java
// HCallbackStub.java

@Override
public boolean handleMessage(Message msg) {
  if (!mCalling) {
    mCalling = true;
    try {
      if (LAUNCH_ACTIVITY == msg.what) {
        if (!handleLaunchActivity(msg)) {
          return true;
        }
      }
      ...
    } finally {
      mCalling = false;
    }
  }
  return false;
}
```

```java
private boolean handleLaunchActivity(Message msg) {
  Object r = msg.obj; // ActivityClientRecord.
  Intent stubIntent = ActivityThread.ActivityClientRecord.intent.get(r);
  // 读取 intnet 中的原始 activity 启动信息。
  StubActivityRecord saveInstance = new StubActivityRecord(stubIntent);

  if (saveInstance.intent == null) {
    return true;
  }

  // 取出原始 intent 信息。
  Intent intent = saveInstance.intent;
  ComponentName caller = saveInstance.caller;
  IBinder token = ActivityThread.ActivityClientRecord.token.get(r);
  ActivityInfo info = saveInstance.info;

  if (VClientImpl.get().getToken() == null) {
	// 进程启动失败，重新启动。
    InstalledAppInfo installedAppInfo = VirtualCore.get().getInstalledAppInfo(
        info.packageName, 0);

    if (installedAppInfo == null) {
      return true;
    }

    VActivityManager.get().processRestarted(info.packageName, info.processName,
        saveInstance.userId);

    getH().sendMessageAtFrontOfQueue(Message.obtain(msg));
    return false;
  }

  if (!VClientImpl.get().isBound()) {
    // 未绑定则新先绑定。
    VClientImpl.get().bindApplication(info.packageName, info.processName);
    // 再执行一次，调用系统原始处理方法启动 activity。
    getH().sendMessageAtFrontOfQueue(Message.obtain(msg));
    return false;
  }

  int taskId = IActivityManager.getTaskForActivity.call(
      ActivityManagerNative.getDefault.call(),
      token,
      false
  );

  // 通知 VAMS activity 创建成功，保存 activity 记录。
  VActivityManager.get().onActivityCreate(ComponentUtils.toComponentName(info), caller, token, info, intent, ComponentUtils.getTaskAffinity(info), taskId, info.launchMode, info.flags);

  ClassLoader appClassLoader = VClientImpl.get().getClassLoader(info.applicationInfo);

  // 替换成原始 intent 信息。
  intent.setExtrasClassLoader(appClassLoader);
  ActivityThread.ActivityClientRecord.intent.set(r, intent);
  ActivityThread.ActivityClientRecord.activityInfo.set(r, info);

  return true;
}
```

这里主要是把占位的 intent 信息替换回原始 intent 了，那么此时应用进程的 activity 就可以被 `Instrumentation` 类给调用起来了。

注意其中有个步骤是 `bindApplication`，它的作用很重要，看一下做了什么。

### VClientImpl

```java
// VClientImpl.java

public void bindApplication(final String packageName, final String processName) {
  if (Looper.getMainLooper() == Looper.myLooper()) {
    bindApplicationNoCheck(packageName, processName, new ConditionVariable());
  } else {
    final ConditionVariable lock = new ConditionVariable();
    VirtualRuntime.getUIHandler().post(new Runnable() {
      @Override
      public void run() {
        bindApplicationNoCheck(packageName, processName, lock);
        lock.open();
      }
    });
    lock.block();
  }
}
```

```java
// VClientImpl.java

// bind application and fix related to application info.
private void bindApplicationNoCheck(String packageName, String processName, ConditionVariable lock) {
  VDeviceInfo deviceInfo = getDeviceInfo();

  if (processName == null) {
    processName = packageName;
  }

  mTempLock = lock;

  try {
    setupUncaughtHandler();
  } catch (Throwable e) {
    e.printStackTrace();
  }

  try {
    fixInstalledProviders();
  } catch (Throwable e) {
    e.printStackTrace();
  }

  mirror.android.os.Build.SERIAL.set(deviceInfo.serial);
  mirror.android.os.Build.DEVICE.set(Build.DEVICE.replace(" ", "_"));
  ActivityThread.mInitialApplication.set(
      VirtualCore.mainThread(),
      null
  );

  AppBindData data = new AppBindData();
  InstalledAppInfo info = VirtualCore.get().getInstalledAppInfo(packageName, 0);

  if (info == null) {
    new Exception("App not exist!").printStackTrace();
    Process.killProcess(0);
    System.exit(0);
  }

  data.appInfo = VPackageManager.get().getApplicationInfo(packageName, 0, getUserId(vuid));
  data.processName = processName;
  data.providers = VPackageManager.get().queryContentProviders(processName, getVUid(), PackageManager.GET_META_DATA);

  Log.i(TAG, "Binding application " + data.appInfo.packageName + " (" + data.processName + ")");

  mBoundApplication = data;

  // fix process runtime info.
  VirtualRuntime.setupRuntime(data.processName, data.appInfo);

  int targetSdkVersion = data.appInfo.targetSdkVersion;

  // fix related to target sdk version info.
  if (targetSdkVersion < Build.VERSION_CODES.GINGERBREAD) {
    StrictMode.ThreadPolicy newPolicy = new StrictMode.ThreadPolicy.Builder(
        StrictMode.getThreadPolicy()).permitNetwork().build();
    StrictMode.setThreadPolicy(newPolicy);
  }

  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP &&
      targetSdkVersion < Build.VERSION_CODES.LOLLIPOP) {
    mirror.android.os.Message.updateCheckRecycle.call(targetSdkVersion);
  }

  if (VASettings.ENABLE_IO_REDIRECT) {
    startIOUniformer();
  }

  NativeEngine.launchEngine();
  Object mainThread = VirtualCore.mainThread();
  NativeEngine.startDexOverride();
  Context context = createPackageContext(data.appInfo.packageName);
  System.setProperty("java.io.tmpdir", context.getCacheDir().getAbsolutePath());
  File codeCacheDir;

  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
    codeCacheDir = context.getCodeCacheDir();
  } else {
    codeCacheDir = context.getCacheDir();
  }

  if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
    if (HardwareRenderer.setupDiskCache != null) {
      HardwareRenderer.setupDiskCache.call(codeCacheDir);
    }
  } else {
    if (ThreadedRenderer.setupDiskCache != null) {
      ThreadedRenderer.setupDiskCache.call(codeCacheDir);
    }
  }

  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
    if (RenderScriptCacheDir.setupDiskCache != null) {
      RenderScriptCacheDir.setupDiskCache.call(codeCacheDir);
    }
  } else
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
      if (RenderScript.setupDiskCache != null) {
        RenderScript.setupDiskCache.call(codeCacheDir);
      }
    }

  Object boundApp = fixBoundApp(mBoundApplication);
  mBoundApplication.info = ContextImpl.mPackageInfo.get(context);
  mirror.android.app.ActivityThread.AppBindData.info.set(boundApp, data.info);
  VMRuntime.setTargetSdkVersion.call(VMRuntime.getRuntime.call(), data.appInfo.targetSdkVersion);

  Configuration configuration = context.getResources().getConfiguration();
  Object compatInfo = CompatibilityInfo.ctor.newInstance(data.appInfo, configuration.screenLayout, configuration.smallestScreenWidthDp, false);

  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
      DisplayAdjustments.setCompatibilityInfo.call(ContextImplKitkat.mDisplayAdjustments.get(context), compatInfo);
    }

    DisplayAdjustments.setCompatibilityInfo.call(LoadedApkKitkat.mDisplayAdjustments.get(mBoundApplication.info), compatInfo);
  } else {
    CompatibilityInfoHolder.set.call(LoadedApkICS.mCompatibilityInfo.get(mBoundApplication.info), compatInfo);
  }

  boolean conflict = SpecialComponentList.isConflictingInstrumentation(packageName);

  if (!conflict) {
    InvocationStubManager.getInstance().checkEnv(AppInstrumentation.class);
  }

  mInitialApplication = LoadedApk.makeApplication.call(data.info, false, null);
  mirror.android.app.ActivityThread.mInitialApplication.set(mainThread, mInitialApplication);
  ContextFixer.fixContext(mInitialApplication);

  if (Build.VERSION.SDK_INT >= 24 && "com.tencent.mm:recovery".equals(processName)) {
    fixWeChatRecovery(mInitialApplication);
  }

  if (data.providers != null) {
    installContentProviders(mInitialApplication, data.providers);
  }

  if (lock != null) {
    lock.open();
    mTempLock = null;
  }

  VirtualCore.get().getComponentDelegate().beforeApplicationCreate(mInitialApplication);

  try {
    mInstrumentation.callApplicationOnCreate(mInitialApplication);
    InvocationStubManager.getInstance().checkEnv(HCallbackStub.class);
    if (conflict) {
      InvocationStubManager.getInstance().checkEnv(AppInstrumentation.class);
    }

    Application createdApp = ActivityThread.mInitialApplication.get(mainThread);

    if (createdApp != null) {
      mInitialApplication = createdApp;
    }
  } catch (Exception e) {
    if (!mInstrumentation.onException(mInitialApplication, e)) {
      throw new RuntimeException(
          "Unable to create application " + mInitialApplication.getClass().getName()
              + ": " + e.toString(), e);
    }
  }

  VActivityManager.get().appDoneExecuting();
  VirtualCore.get().getComponentDelegate().afterApplicationCreate(mInitialApplication);
}
```

看起来比较长，里面主要做了如下工作：

1. 保存相关变量，`mBoundApplication`，`mInitialApplication` 等。
2. 修复系统 `ActivityThread` 内部初始化相关类的变量为沙盒内应用的包名等特征。
3. 通过 `mInstrumentation`，回调 activity 的 `onCreate` 等生命周期方法。

这里就分析完了 VirutalApp 对 activity 组件的处理，不过如果想成功加载一个应用有这些还不够，还需要对大量服务进行 hook，以及对 IO 重定向的工作，目的是为了沙盒内应用营造一个类似系统的真实环境，防止宿主数据被访问到，或访问到错误数据导致崩溃。

