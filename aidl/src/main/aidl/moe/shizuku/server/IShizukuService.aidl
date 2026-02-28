package moe.shizuku.server;

import moe.shizuku.server.IRemoteProcess;
import moe.shizuku.server.IShizukuApplication;
import moe.shizuku.server.IShizukuServiceConnection;
import moe.shizuku.server.IVirtualMachineManager;
import moe.shizuku.server.IStorageProxy;
import moe.shizuku.server.IAICorePlus;
import moe.shizuku.server.IWindowManagerPlus;

interface IShizukuService {

    int getVersion() = 2;

    int getUid() = 3;

    int checkPermission(String permission) = 4;

    IRemoteProcess newProcess(in String[] cmd, in String[] env, in String dir) = 7;

    String getSELinuxContext() = 8;

    String getSystemProperty(in String name, in String defaultValue) = 9;

    void setSystemProperty(in String name, in String value) = 10;

    int addUserService(in IShizukuServiceConnection conn, in Bundle args) = 11;

    int removeUserService(in IShizukuServiceConnection conn, in Bundle args) = 12;

    void requestPermission(int requestCode) = 14;

    boolean checkSelfPermission() = 15;

    boolean shouldShowRequestPermissionRationale() = 16;

    void attachApplication(in IShizukuApplication application,in Bundle args) = 17;

    void exit() = 100;

    void attachUserService(in IBinder binder, in Bundle options) = 101;

    oneway void dispatchPackageChanged(in Intent intent) = 102;

    boolean isHidden(int uid) = 103;

    oneway void dispatchPermissionConfirmationResult(int requestUid, int requestPid, int requestCode, in Bundle data) = 104;

    int getFlagsForUid(int uid, int mask) = 105;

    void updateFlagsForUid(int uid, int mask, int value) = 106;

    IVirtualMachineManager getVirtualMachineManager() = 107;

    IStorageProxy getStorageProxy() = 108;

    IAICorePlus getAICorePlus() = 109;

    IWindowManagerPlus getWindowManagerPlus() = 110;
 }
