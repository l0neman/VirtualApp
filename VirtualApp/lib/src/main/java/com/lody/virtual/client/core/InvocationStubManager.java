package com.lody.virtual.client.core;

import android.os.Build;

import com.lody.virtual.client.hook.base.MethodInvocationProxy;
import com.lody.virtual.client.hook.base.MethodInvocationStub;
import com.lody.virtual.client.hook.delegate.AppInstrumentation;
import com.lody.virtual.client.hook.proxies.account.AccountManagerStub;
import com.lody.virtual.client.hook.proxies.alarm.AlarmManagerStub;
import com.lody.virtual.client.hook.proxies.am.ActivityManagerStub;
import com.lody.virtual.client.hook.proxies.am.HCallbackStub;
import com.lody.virtual.client.hook.proxies.appops.AppOpsManagerStub;
import com.lody.virtual.client.hook.proxies.appwidget.AppWidgetManagerStub;
import com.lody.virtual.client.hook.proxies.audio.AudioManagerStub;
import com.lody.virtual.client.hook.proxies.backup.BackupManagerStub;
import com.lody.virtual.client.hook.proxies.bluetooth.BluetoothStub;
import com.lody.virtual.client.hook.proxies.clipboard.ClipBoardStub;
import com.lody.virtual.client.hook.proxies.connectivity.ConnectivityStub;
import com.lody.virtual.client.hook.proxies.content.ContentServiceStub;
import com.lody.virtual.client.hook.proxies.context_hub.ContextHubServiceStub;
import com.lody.virtual.client.hook.proxies.devicepolicy.DevicePolicyManagerStub;
import com.lody.virtual.client.hook.proxies.display.DisplayStub;
import com.lody.virtual.client.hook.proxies.dropbox.DropBoxManagerStub;
import com.lody.virtual.client.hook.proxies.fingerprint.FingerprintManagerStub;
import com.lody.virtual.client.hook.proxies.graphics.GraphicsStatsStub;
import com.lody.virtual.client.hook.proxies.imms.MmsStub;
import com.lody.virtual.client.hook.proxies.input.InputMethodManagerStub;
import com.lody.virtual.client.hook.proxies.isms.ISmsStub;
import com.lody.virtual.client.hook.proxies.isub.ISubStub;
import com.lody.virtual.client.hook.proxies.job.JobServiceStub;
import com.lody.virtual.client.hook.proxies.libcore.LibCoreStub;
import com.lody.virtual.client.hook.proxies.location.LocationManagerStub;
import com.lody.virtual.client.hook.proxies.media.router.MediaRouterServiceStub;
import com.lody.virtual.client.hook.proxies.media.session.SessionManagerStub;
import com.lody.virtual.client.hook.proxies.mount.MountServiceStub;
import com.lody.virtual.client.hook.proxies.network.NetworkManagementStub;
import com.lody.virtual.client.hook.proxies.notification.NotificationManagerStub;
import com.lody.virtual.client.hook.proxies.persistent_data_block.PersistentDataBlockServiceStub;
import com.lody.virtual.client.hook.proxies.phonesubinfo.PhoneSubInfoStub;
import com.lody.virtual.client.hook.proxies.pm.PackageManagerStub;
import com.lody.virtual.client.hook.proxies.power.PowerManagerStub;
import com.lody.virtual.client.hook.proxies.restriction.RestrictionStub;
import com.lody.virtual.client.hook.proxies.search.SearchManagerStub;
import com.lody.virtual.client.hook.proxies.shortcut.ShortcutServiceStub;
import com.lody.virtual.client.hook.proxies.telephony.TelephonyRegistryStub;
import com.lody.virtual.client.hook.proxies.telephony.TelephonyStub;
import com.lody.virtual.client.hook.proxies.usage.UsageStatsManagerStub;
import com.lody.virtual.client.hook.proxies.user.UserManagerStub;
import com.lody.virtual.client.hook.proxies.vibrator.VibratorStub;
import com.lody.virtual.client.hook.proxies.view.AutoFillManagerStub;
import com.lody.virtual.client.hook.proxies.wifi.WifiManagerStub;
import com.lody.virtual.client.hook.proxies.wifi_scanner.WifiScannerStub;
import com.lody.virtual.client.hook.proxies.window.WindowManagerStub;
import com.lody.virtual.client.interfaces.IInjector;

import java.util.HashMap;
import java.util.Map;

import static android.os.Build.VERSION_CODES.JELLY_BEAN_MR1;
import static android.os.Build.VERSION_CODES.JELLY_BEAN_MR2;
import static android.os.Build.VERSION_CODES.KITKAT;
import static android.os.Build.VERSION_CODES.LOLLIPOP;
import static android.os.Build.VERSION_CODES.LOLLIPOP_MR1;
import static android.os.Build.VERSION_CODES.M;
import static android.os.Build.VERSION_CODES.N;

/**
 * @author Lody
 *
 * 系统服务 hook 注入管理器。
 */
public final class InvocationStubManager {

  private static InvocationStubManager sInstance = new InvocationStubManager();
  private static boolean sInit;

  private Map<Class<?>, IInjector> mInjectors = new HashMap<>(13);

  private InvocationStubManager() {
  }

  public static InvocationStubManager getInstance() {
    return sInstance;
  }

  void injectAll() throws Throwable {
    for (IInjector injector : mInjectors.values()) {
      injector.inject();
    }

    // XXX: Lazy inject the Instrumentation,
    addInjector(AppInstrumentation.getDefault());
  }

  /**
   * @return if the InvocationStubManager has been initialized.
   */
  public boolean isInit() {
    return sInit;
  }


  public void init() throws Throwable {
    if (isInit()) {
      throw new IllegalStateException("InvocationStubManager Has been initialized.");
    }

    injectInternal();
    sInit = true;

  }

  private void injectInternal() throws Throwable {
    if (VirtualCore.get().isMainProcess()) {
      return;
    }

    if (VirtualCore.get().isServerProcess()) {

      // hook ActivityManager，将对 Context.ACTIVITY_SERVICE 和 startActivity 造成影响。
      addInjector(new ActivityManagerStub());
      // hook PackageManager，将对 context.getPackageManager() 造成影响。
      addInjector(new PackageManagerStub());
      return;
    }

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

  private void addInjector(IInjector IInjector) {
    mInjectors.put(IInjector.getClass(), IInjector);
  }

  public <T extends IInjector> T findInjector(Class<T> clazz) {
    // noinspection unchecked
    return (T) mInjectors.get(clazz);
  }

  public <T extends IInjector> void checkEnv(Class<T> clazz) {
    IInjector IInjector = findInjector(clazz);
    if (IInjector != null && IInjector.isEnvBad()) {
      try {
        IInjector.inject();
      } catch (Throwable e) {
        e.printStackTrace();
      }
    }
  }

  public <T extends IInjector, H extends MethodInvocationStub> H getInvocationStub(Class<T> injectorClass) {
    T injector = findInjector(injectorClass);
    if (injector != null && injector instanceof MethodInvocationProxy) {
      // noinspection unchecked
      return (H) ((MethodInvocationProxy) injector).getInvocationStub();
    }
    return null;
  }

}