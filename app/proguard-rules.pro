# Keep domain models & widget/worker classes
-keep class com.bttrcal.app.domain.model.** { *; }
-keep class com.bttrcal.app.widget.** { *; }
-keep class com.bttrcal.app.data.sync.CalendarSyncWorker { *; }
-keep class com.bttrcal.app.notifications.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
