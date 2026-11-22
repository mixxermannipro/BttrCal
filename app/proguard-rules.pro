# Keep domain models, entrypoints, ViewModels & widget/worker/receiver classes
-keep class com.bttrcal.app.BttrCalApplication { *; }
-keep class com.bttrcal.app.MainActivity { *; }
-keep class com.bttrcal.app.ui.MainViewModel { *; }
-keep class com.bttrcal.app.domain.model.** { *; }
-keep class com.bttrcal.app.widget.** { *; }
-keep class com.bttrcal.app.data.sync.CalendarSyncWorker { *; }
-keep class com.bttrcal.app.notifications.** { *; }
-keep class com.bttrcal.app.system.** { *; }

# Keep RemoteViews & WorkManager reflection constructors
-keepclassmembers class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keepclassmembers class * extends android.appwidget.AppWidgetProvider {
    public <init>();
}

# OkHttp & Kotlin Coroutines
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
