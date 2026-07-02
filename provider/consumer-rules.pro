-keepnames class af.shizuku.api.BinderContainer
-keepclassmembers class af.shizuku.api.BinderContainer {
   public static final android.os.Parcelable$Creator CREATOR;
}
# Wire-compat containers for stock (moe) and rikka Shizuku clients. Their class
# names travel over the binder-delivery Bundle, so they must not be renamed by R8.
-keepnames class moe.shizuku.api.BinderContainer
-keepclassmembers class moe.shizuku.api.BinderContainer {
   public static final android.os.Parcelable$Creator CREATOR;
}
-keepnames class rikka.shizuku.BinderContainer
-keepclassmembers class rikka.shizuku.BinderContainer {
   public static final android.os.Parcelable$Creator CREATOR;
}