package com.lody.virtual.server;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;

import com.lody.virtual.client.core.VirtualCore;
import com.lody.virtual.client.stub.DaemonService;
import com.lody.virtual.helper.compat.BundleCompat;
import com.lody.virtual.helper.ipcbus.IPCBus;
import com.lody.virtual.server.accounts.VAccountManagerService;
import com.lody.virtual.server.am.BroadcastSystem;
import com.lody.virtual.server.am.VActivityManagerService;
import com.lody.virtual.server.device.VDeviceManagerService;
import com.lody.virtual.server.interfaces.IAccountManager;
import com.lody.virtual.server.interfaces.IActivityManager;
import com.lody.virtual.server.interfaces.IAppManager;
import com.lody.virtual.server.interfaces.IDeviceInfoManager;
import com.lody.virtual.server.interfaces.IJobService;
import com.lody.virtual.server.interfaces.INotificationManager;
import com.lody.virtual.server.interfaces.IPackageManager;
import com.lody.virtual.server.interfaces.IServiceFetcher;
import com.lody.virtual.server.interfaces.IUserManager;
import com.lody.virtual.server.interfaces.IVirtualLocationManager;
import com.lody.virtual.server.interfaces.IVirtualStorageService;
import com.lody.virtual.server.job.VJobSchedulerService;
import com.lody.virtual.server.location.VirtualLocationService;
import com.lody.virtual.server.notification.VNotificationManagerService;
import com.lody.virtual.server.pm.VAppManagerService;
import com.lody.virtual.server.pm.VPackageManagerService;
import com.lody.virtual.server.pm.VUserManagerService;
import com.lody.virtual.server.vs.VirtualStorageService;

import mirror.android.app.job.IJobScheduler;

/**
 * @author Lody
 */
public final class BinderProvider extends ContentProvider {

  private final ServiceFetcher mServiceFetcher = new ServiceFetcher();

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

  @Override
  public Bundle call(String method, String arg, Bundle extras) {
    if ("@".equals(method)) {
      Bundle bundle = new Bundle();
      BundleCompat.putBinder(bundle, "_VA_|_binder_", mServiceFetcher);
      return bundle;
    }
    if ("register".equals(method)) {

    }
    return null;
  }

  @Override
  public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
    return null;
  }

  @Override
  public String getType(Uri uri) {
    return null;
  }

  @Override
  public Uri insert(Uri uri, ContentValues values) {
    return null;
  }

  @Override
  public int delete(Uri uri, String selection, String[] selectionArgs) {
    return 0;
  }

  @Override
  public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
    return 0;
  }

  private class ServiceFetcher extends IServiceFetcher.Stub {
    @Override
    public IBinder getService(String name) throws RemoteException {
      if (name != null) {
        return ServiceCache.getService(name);
      }
      return null;
    }

    @Override
    public void addService(String name, IBinder service) throws RemoteException {
      if (name != null && service != null) {
        ServiceCache.addService(name, service);
      }
    }

    @Override
    public void removeService(String name) throws RemoteException {
      if (name != null) {
        ServiceCache.removeService(name);
      }
    }
  }
}
