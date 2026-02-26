# ShizukuPlus-API

ShizukuPlus-API is an enhanced, developer-friendly version of the Shizuku API. It provides a modernized interface for interacting with [Shizuku+](https://github.com/thejaustin/ShizukuPlus), while maintaining full backward compatibility with standard Shizuku and Sui servers.

## ✨ Key Features (Plus Upgrades)

ShizukuPlus-API eliminates the boilerplate associated with standard Shizuku development:

*   **Synchronous Shell Execution**: No more managing `InputStream`, `ErrorStream`, and threads. Get a clean `CommandResult` in one line.
*   **High-Level Utilities**: Dedicated classes for managing **System Settings**, **Package Installation**, and **System Overlays (RRO)**.
*   **Dhizuku (Device Owner) Integration**: Directly access the `DevicePolicyManager` binder without requiring the user to perform a factory reset or complex ADB setup.
*   **Universal Compatibility**: Automatically detects if the server is Shizuku+ or standard Shizuku. It uses optimized paths for Shizuku+ and provides a transparent fallback (via `SafeShell`) for original Shizuku servers.

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

## 🛠️ Usage

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

### 3. Package Management
Perform silent installs and uninstalls:
```java
ShizukuPlusAPI.PackageManager.installPackage("/sdcard/app.apk");
ShizukuPlusAPI.PackageManager.clearPackageData("com.example.app");
```

### 4. Overlay (Theming) Management
Enable or disable system overlays (RRO):
```java
ShizukuPlusAPI.OverlayManager.enableOverlay("com.hexodus.theme.accent");
```

### 5. Dhizuku (Device Owner) Mode
Access enterprise-level management features via Shizuku+:
```java
if (ShizukuPlusAPI.Dhizuku.isAvailable()) {
    IBinder dpm = ShizukuPlusAPI.Dhizuku.getBinder();
    // Use the DPM binder for persistent freezing, proxy management, etc.
}
```

## 🔄 Compatibility

ShizukuPlus-API is built on a **Translation Layer**. 

*   **On Shizuku+**: Uses optimized Binder transactions for maximum speed.
*   **On standard Shizuku**: Automatically wraps commands into `Shizuku.newProcess` shell scripts behind the scenes. 

**Result**: Your app works everywhere, but runs better on Shizuku+.

## 📱 Documentation & Original API
For the core logic, `UserService` documentation, and AIDL definitions, please refer to the original [Shizuku-API](https://github.com/RikkaApps/Shizuku-API) repository. ShizukuPlus-API includes all original `rikka.shizuku.Shizuku` methods.

## 📃 License
[MIT License](LICENSE)
