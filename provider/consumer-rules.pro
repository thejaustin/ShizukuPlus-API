# Cross-process Parcelable wrappers used in the server -> client binder handoff
# (ShizukuProvider.handleSendBinder / sendBinder). These are resolved BY NAME in the
# *client* app's classloader, so their fully qualified names AND CREATOR must survive
# R8/obfuscation unchanged in every consumer (including the Shizuku+ manager APK, which
# compiles this module in as a subproject).
#
# Keeping only af.shizuku.api.BinderContainer let R8 rename rikka.shizuku.BinderContainer
# and moe.shizuku.api.BinderContainer; the marshalled Parcelable then carried an obfuscated
# class name (e.g. "kd.a") that standard Shizuku/Sui clients could not resolve, throwing
# BadParcelableException and breaking binder delivery (pingBinder() == false).
-keep class af.shizuku.api.BinderContainer { *; }
-keep class rikka.shizuku.BinderContainer { *; }
-keep class moe.shizuku.api.BinderContainer { *; }