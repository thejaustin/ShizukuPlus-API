# ShizukuPlus-API

ShizukuPlus-API is an enhanced, developer-friendly version of the Shizuku API. It provides a modernized interface for interacting with [Shizuku+](https://github.com/thejaustin/ShizukuPlus), while maintaining full backward compatibility with standard Shizuku and Sui servers.

## ✨ Key Features (Plus Upgrades)

ShizukuPlus-API eliminates the boilerplate associated with standard Shizuku development:

*   **Synchronous Shell Execution**: No more managing `InputStream`, `ErrorStream`, and threads. Get a clean `CommandResult` in one line.
*   **High-Level Utilities**: Dedicated classes for managing **System Settings**, **Package Installation**, and **System Overlays (RRO)**.
*   **Dhizuku (Device Owner) Integration**: Directly access the `DevicePolicyManager` binder without requiring the user to perform a factory reset or complex ADB setup.
*   **Universal Compatibility**: Automatically detects if the server is Shizuku+ or standard Shizuku. It uses optimized paths for Shizuku+ and provides a transparent fallback (via `SafeShell`) for original Shizuku servers.

## 🚀 Plus API Features

ShizukuPlus-API includes exclusive interfaces for advanced system interaction:

### 1. AVF (Virtual Machine) Manager
Manage isolated Linux environments via the Android Virtualization Framework.
*   **Capabilities**: Create, start, and manage Microdroid or Debian-based VMs.
*   **Use Case**: Run hardware-accelerated Linux GUI apps or secure isolated services.

### 2. Privileged Storage Proxy
Bypass SAF (Storage Access Framework) limitations for verified power-user tools.
*   **Capabilities**: Obtain `FileDescriptors` for restricted paths like `/data/data/`.
*   **Security**: Requires explicit biometric/user confirmation via the ShizukuPlus manager.

### 3. Intelligence Bridge (AI Core Plus)
Access privileged system intelligence and hardware accelerators.
*   **Capabilities**: High-priority NPU scheduling and privileged screen context sampling (EyeDropper extension).
*   **Use Case**: Advanced automation and context-aware accessibility tools.

### 4. Window Manager Plus (Desktop Mode)
Take control of the desktop windowing experience.
*   **Capabilities**: Force free-form resizing, manage the system "Bubble Bar," and set "Always on Top" windows.

### 5. System Theming Bridge (Overlay Manager Plus)
Expose privileged overlay management.
*   **Capabilities**: Enable/disable system UI overlays without root.
*   **Use Case**: Rootless theming engines (like Hex Installer).

### 6. Network & DNS Governor
Manage network restrictions and routing.
*   **Capabilities**: Set system-wide Private DNS and manage iptables rules via routing/VPN.
*   **Use Case**: Rootless ad-blockers (AdAway) and firewalls (AFWall+).

### 7. Deep Process Control (Activity Manager Plus)
Advanced memory and process management.
*   **Capabilities**: Deeply kill background apps and manage standby buckets.
*   **Use Case**: Performance optimizers (Thanox, 3C Toolbox).

### 8. Continuity Bridge
Seamless multi-device state synchronization.
*   **Capabilities**: Sync app states and privileged task handoffs between devices running ShizukuPlus.

## 🚀 Getting Started

### Add dependency

Add the following to your `build.gradle`:

```gradle
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.thejaustin:ShizukuPlus-API:13.2.0-plus'
}
```

## 🛠️ Usage Examples

### 1. Unified Shell
Execute any command and get the output synchronously:
```java
CommandResult result = ShizukuPlusAPI.Shell.executeCommand("whoami");
if (result.isSuccess()) {
    Log.d("API", "Output: " + result.output);
}
```

### 2. System Settings
Easily read or modify `system`, `secure`, and `global` settings:
```java
ShizukuPlusAPI.Settings.putSecure("now_bar_enabled", "1");
String battery = ShizukuPlusAPI.Settings.getSystem("font_scale");
```

### 3. Advanced Window Control
Force an app into free-form mode even if restricted by its manifest:
```java
IWindowManagerPlus wm = ShizukuPlusAPI.getWindowManagerPlus();
wm.forceResizable("com.example.app", true);
```

### 4. Storage Access
Access a file in an app's private data directory (requires user confirmation):
```java
IStorageProxy storage = ShizukuPlusAPI.getStorageProxy();
ParcelFileDescriptor pfd = storage.openFile("/data/data/com.example.app/files/config.json", ParcelFileDescriptor.MODE_READ_ONLY);
```

## 🔄 Compatibility

ShizukuPlus-API is built on a **Translation Layer**. 

*   **On Shizuku+**: Uses optimized Binder transactions for maximum speed and access to exclusive Plus APIs.
*   **On standard Shizuku**: Automatically wraps commands into `Shizuku.newProcess` shell scripts behind the scenes. 

**Result**: Your app works everywhere, but runs better on Shizuku+.

## 📱 Documentation & Original API
For the core logic, `UserService` documentation, and AIDL definitions, please refer to the original [Shizuku-API](https://github.com/RikkaApps/Shizuku-API) repository. ShizukuPlus-API includes all original `rikka.shizuku.Shizuku` methods.

## 📃 License
[MIT License](LICENSE)
