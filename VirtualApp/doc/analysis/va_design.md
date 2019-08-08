# VirtualApp 架构设计分析

[TOC]

## 核心技术点

1. 插件化技术，通过宿主可将一个插件 APK 加载启动，并可正常加载其包含的四大组件，而不必在宿主的清单文件中提前配置。以此技术为基础，将完整应用作为插件加载至宿主内部执行，为实现沙盒打下基础。
2. 系统服务 Hook 技术，通过切入本地进程和系统服务之间的进程间通讯过程，改变原始系统服务的行为，欺骗插件应用以达到提供虚拟沙盒环境的目的。
3. IO 重定向 Hook 技术，通过重定向插件应用进程对 IO 的原始路径访问，为沙盒应用数据隔离提供支持。

## 进程模型

### Server

Server 端进程放置虚拟沙盒服务，为沙盒环境提供支持。

### Main

Main 端进程为宿主进程，宿主负责沙盒的用户界面。

### VClient

VClient 端进程为沙盒内部运行的应用进程，每一个 VClient 端进程都对应一个沙盒子应用。

### Other

Client 端或 Server 端产生的其他进程。

## 用户管理

用户 id 体系遵循 Android 系统原生的用户体系。

沙盒内的用户 id 从 0 开始递增，每个用户内部不能同时拥有多个同一个包名的多开应用，即一个应用需要多开几个，那么就需要几个用户。

每个用户内部安装的应用可以相互通信，像是在同一个系统中运行（待确认）。

uid（用户相关应用 id）= userId（用户 id） * 100000 + appId（应用 id，不能超过 100000 个）

判断应用是否在同一个用户下，通过 uid / 100000 即可得到相同的用户 id。

##  组件管理

### Activity

#### 启动管理

通过 Hook 客户端进程与系统服务 `ActivityManagerService` 的进程间沟通过程，使沙盒内应用向服务端请求 `startActivity` 时的行为改变，将沙盒内应用的 activity 替换为提前注册的占桩 activity，欺骗 `ActivityManagerService` 从而启动未在清单文件中注册的 activity，然后在服务端返回结果请求客户端启动 activity 时还原为原始沙盒内应用的 activity，完成 activity 的启动。

#### 栈管理

沙盒虚拟服务 `ActivityManagerService` 将对沙盒内应用的启动模式进行处理，并在沙盒服务进程中记录每个启动的 activity 以及对他们所在的栈进行调整，

### Service

#### 启动管理

和启动 activity 方法一致。

#### 运行管理

由于沙盒内应用的服务不必与手机系统内的应用发生沟通，那么对于 service 组件，没有必要再让它经过系统的 `ActivityManagerService` 服务了，那么直接调用 framework 中的本地服务初始化方法即可，并在沙盒虚拟 `ActivityManagerService` 服务中维护 service 的记录。

### Provider

与 service 原理一致，唯一不同的是，provider 需要在应用启动（application）之前安装。

### Receiver

对于沙盒内应用注册的系统广播，可将静态注册的广播动态化注册，对于非系统事件的广播，可选择不干涉，直接通过系统广播注册接收，也可在沙盒虚拟 `ActivityManagerService` 服务中实现广播注册和接收服务。

## 应用信息提供

通过 Hook 相关系统服务，修改其中提供信息的相关方法，或重定向带有相关运行时信息的文件，使沙盒内子应用访问到的系统服务不同，访问到的系统文件改变，导致应用获取到的信息不同。

## 核心服务类

```java
// 1. 包管理服务。    line numbers: 1231 - 1073
IPCBus.register(IPackageManager.class, VPackageManagerService.get());

// 2. 活动管理服务。  line numbers: 1237 - 1157
IPCBus.register(IActivityManager.class, VActivityManagerService.get());

// 3. 用户管理服务。   line numbers: 864 - 892
IPCBus.register(IUserManager.class, VUserManagerService.get());

// 4. 应用管理服务。   line numbers: 788 - 630
IPCBus.register(IAppManager.class, VAppManagerService.get());

// 5. 广播管理。       line numbers: 288
BroadcastSystem.attach(VActivityManagerService.get(), VAppManagerService.get());

// 6. 工作调度服务。   line numbers: 395 - 395
IPCBus.register(IJobService.class, VJobSchedulerService.get());

// 7. 通知管理服务。   line numbers: 158 - 158
IPCBus.register(INotificationManager.class, VNotificationManagerService.get());

// 8. 账户管理服务。  line numbers: 1649 - 1421
IPCBus.register(IAccountManager.class, VAccountManagerService.get());

// 9. 虚拟存储服务。  line numbers: 94 - 94
IPCBus.register(IVirtualStorageService.class, VirtualStorageService.get());

// 10. 设备信息服务。 line numbers: 73 - 179
IPCBus.register(IDeviceInfoManager.class, VDeviceManagerService.get());

// 11. 虚拟定位服务。 line numbers: 266 - 270
IPCBus.register(IVirtualLocationManager.class, VirtualLocationService.get());
```