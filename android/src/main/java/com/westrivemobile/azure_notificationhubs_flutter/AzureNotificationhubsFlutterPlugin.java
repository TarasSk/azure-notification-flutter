package com.westrivemobile.azure_notificationhubs_flutter;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import androidx.annotation.NonNull;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.ProcessLifecycleOwner;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

import io.flutter.Log;
import io.flutter.app.FlutterActivity;
import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry.NewIntentListener;

public class AzureNotificationhubsFlutterPlugin extends BroadcastReceiver implements FlutterPlugin, MethodCallHandler, NewIntentListener, ActivityAware {

    private static Context applicationContext;
    private MethodChannel channel;
    private Activity activity;
    public static NotificationLifeCycle appLifeCycle = NotificationLifeCycle.AppKilled;

    //    This static function is optional and equivalent to onAttachedToEngine. It supports the old
    //    pre-Flutter-1.12 Android projects. You are encouraged to continue supporting
    //    plugin registration via this function while apps migrate to use the new Android APIs
    //    post-flutter-1.12 via https://flutter.dev/go/android-project-migration.
    //
    //    It is encouraged to share logic between onAttachedToEngine and registerWith to keep
    //    them functionally equivalent. Only one of onAttachedToEngine or registerWith will be called
    //    depending on the user's project. onAttachedToEngine or registerWith must both be defined
    //    in the same class.
    //    public static void registerWith(PluginRegistry.Registrar registrar) {
    //        AzureNotificationhubsFlutterPlugin instance = new AzureNotificationhubsFlutterPlugin();
    //        instance.onAttachedToEngine(registrar.context(), registrar.messenger());
    //    }

    @Override
    public void onAttachedToEngine(@NonNull FlutterPluginBinding binding) {
        onAttachedToEngine(binding.getApplicationContext(), binding.getFlutterEngine().getDartExecutor());
    }

    @Override
    public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
        LocalBroadcastManager.getInstance(binding.getApplicationContext()).unregisterReceiver(this);
    }

    private void onAttachedToEngine(Context context, BinaryMessenger binaryMessenger) {
        applicationContext = context;
        channel = new MethodChannel(binaryMessenger, "azure_notificationhubs_flutter");
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(NotificationService.ACTION_TOKEN);
        intentFilter.addAction(NotificationService.ACTION_REMOTE_MESSAGE);
        LocalBroadcastManager manager = LocalBroadcastManager.getInstance(applicationContext);
        manager.registerReceiver(this, intentFilter);
    }

    @Override
    public void onAttachedToActivity(@NonNull ActivityPluginBinding binding) {
        this.activity = binding.getActivity();
        channel.setMethodCallHandler(this);
    }

    @Override
    public void onDetachedFromActivityForConfigChanges() {

    }

    @Override
    public void onReattachedToActivityForConfigChanges(@NonNull ActivityPluginBinding binding) {

    }

    @Override
    public void onDetachedFromActivity() {
        Log.d("DART/NATIVE", "onDetachedFromEngine");
        this.channel.setMethodCallHandler(null);
    }

    @Override
    public void onMethodCall(@NonNull MethodCall call, @NonNull Result result) {
        if (call.method.equals("configure")) {
            String receiverId = call.argument("receiverId");
            if (receiverId == null) {
                result.error("-1","Receiver Id is missing", null);
            } else {
                registerWithNotificationHubs(receiverId);
                NotificationService.createChannelAndHandleNotifications(applicationContext);
            }
        } else {
            result.notImplemented();
        }
    }

    public void registerWithNotificationHubs(String receiverId) {
        Intent intent = new Intent(applicationContext, RegistrationIntentService.class);
        intent.putExtra("receiverId", receiverId);
        applicationContext.startService(intent);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        getApplicationLifeCycle();
        String action = intent.getAction();
        if (action == null) {
            return;
        }
        if (action.equals(NotificationService.ACTION_TOKEN)) {
            String token = intent.getStringExtra(NotificationService.EXTRA_TOKEN);
            channel.invokeMethod("onToken", token);
        } else if (action.equals(NotificationService.ACTION_REMOTE_MESSAGE)) {
            RemoteMessage message = intent.getParcelableExtra(NotificationService.EXTRA_REMOTE_MESSAGE);
            Map<String, Object> content = NotificationService.parseRemoteMessage(message);
            Log.d("DART/NATIVE", "appLifeCycle" + appLifeCycle.toString());
            channel.invokeMethod("onMessage", content);
        }
    }

    @Override
    public boolean onNewIntent(Intent intent) {
        Log.d("DART/NATIVE", "onNewIntent");
        RemoteMessage message = intent.getParcelableExtra(NotificationService.EXTRA_REMOTE_MESSAGE);
        Map<String, Object> content = NotificationService.parseRemoteMessage(message);

        channel.invokeMethod("onResume", content);
        if (this.activity != null) {
            this.activity.setIntent(intent);
        }
        return true;
    }

    public static NotificationLifeCycle getApplicationLifeCycle(){

        Lifecycle.State state = ProcessLifecycleOwner.get().getLifecycle().getCurrentState();
        //Log.d(TAG, "ProcessLifecycleOwner: " + state.toString());

        if(state == Lifecycle.State.RESUMED){
            appLifeCycle = NotificationLifeCycle.Foreground;
        } else
        if(state == Lifecycle.State.CREATED){
            appLifeCycle = NotificationLifeCycle.Background;
        } else {
            appLifeCycle = NotificationLifeCycle.AppKilled;
        }
        return appLifeCycle;
    }

}

enum NotificationLifeCycle {
    Foreground,
    Background,
    AppKilled
}
