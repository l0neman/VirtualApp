# VirtualApp 实现分析

[TOC]

## 工具相关

### 反射工具

使用映射的方式，将目标反射类型的成员变量和方法映射到本地的反射信息存储类中，方便调用。

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

VirtualApp 在虚拟 Server 进程实现了多个虚拟服务，负责管理沙盒内应用相关的组件和维护相关状态。

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
  if (VirtualCore.get().isMainProcess()) {
    return;
  }

  // 针对虚拟服务端进程。
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
    // hook ActivityThread$H 类，将影响系统对 startActivity 的处理。
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

```java
// VActivityManager.java

public int startActivity(Intent intent, int userId) {
  if (userId < 0) {
    return ActivityManagerCompat.START_NOT_CURRENT_USER_ACTIVITY;
  }

  ActivityInfo info = VirtualCore.get().resolveActivityInfo(intent, userId);

  if (info == null) {
    return ActivityManagerCompat.START_INTENT_NOT_RESOLVED;
  }

  return startActivity(intent, info, null, null, null, 0, userId);
}
```

