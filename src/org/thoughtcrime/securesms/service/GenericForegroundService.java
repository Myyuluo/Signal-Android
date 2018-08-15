package org.thoughtcrime.securesms.service;


import android.app.PendingIntent;
import android.app.Service;
import android.arch.lifecycle.DefaultLifecycleObserver;
import android.arch.lifecycle.Lifecycle;
import android.arch.lifecycle.LifecycleObserver;
import android.arch.lifecycle.LifecycleOwner;
import android.arch.lifecycle.ProcessLifecycleOwner;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;

import org.thoughtcrime.securesms.ApplicationContext;
import org.thoughtcrime.securesms.ConversationListActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.notifications.NotificationChannels;
import org.thoughtcrime.securesms.util.NoCatchupLifecycleObserver;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class GenericForegroundService extends Service {

  private static final int    NOTIFICATION_ID  = 827353982;
  private static final String EXTRA_TITLE      = "extra_title";
  private static final String EXTRA_CHANNEL_ID = "extra_channel_id";

  private static final String ACTION_START = "start";
  private static final String ACTION_STOP  = "stop";

  private final AtomicInteger foregroundCount = new AtomicInteger(0);

  private final AtomicReference<NotificationInfo> notification = new AtomicReference<>();

  private final LifecycleObserver observer = new NoCatchupLifecycleObserver(lifecycle, new DefaultLifecycleObserver() {
      @Override
      public void onStart(@NonNull LifecycleOwner owner) {
        stop();
        lifecycle.removeObserver(this);
      }

      @Override
      public void onStop(@NonNull LifecycleOwner owner) {
        NotificationInfo info = notification.get();
        if (info != null) {
          show(info.getTitle(), info.getChannelId());
        }
      }
    })

  @Override
  public void onCreate() {
    Lifecycle lifecycle = ProcessLifecycleOwner.get().getLifecycle();
    lifecycle.addObserver();
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    if      (intent != null && ACTION_START.equals(intent.getAction())) handleStart(intent);
    else if (intent != null && ACTION_STOP.equals(intent.getAction()))  handleStop();

    return START_NOT_STICKY;
  }


  private void handleStart(@NonNull Intent intent) {
    if (foregroundCount.getAndIncrement() == 0 && ApplicationContext.getInstance(getApplicationContext()).isAppVisible()) {
      String title     = intent.getStringExtra(EXTRA_TITLE);
      String channelId = intent.getStringExtra(EXTRA_TITLE);

      notification.set(new NotificationInfo(title, channelId));
      show(title, channelId);
    }
  }

  private void handleStop() {
    if (foregroundCount.decrementAndGet() == 0) {
      stop();
    }
  }

  private void show(String title, String channelId) {
    startForeground(NOTIFICATION_ID, new NotificationCompat.Builder(this, channelId)
                                                           .setSmallIcon(R.drawable.ic_signal_grey_24dp)
                                                           .setContentTitle(title)
                                                           .setContentIntent(PendingIntent.getActivity(this, 0, new Intent(this, ConversationListActivity.class), 0))
                                                           .build());
  }

  private void stop() {
    stopForeground(true);
    stopSelf();
  }

  @Nullable
  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }

  public static void startForegroundTask(@NonNull Context context, @NonNull String task) {
    startForegroundTask(context, task, NotificationChannels.OTHER);
  }

  public static void startForegroundTask(@NonNull Context context, @NonNull String task, @NonNull String channelId) {
    Intent intent = new Intent(context, GenericForegroundService.class);
    intent.setAction(ACTION_START);
    intent.putExtra(EXTRA_TITLE, task);
    intent.putExtra(EXTRA_CHANNEL_ID, channelId);

    context.startService(intent);
  }

  public static void stopForegroundTask(@NonNull Context context) {
    Intent intent = new Intent(context, GenericForegroundService.class);
    intent.setAction(ACTION_STOP);

    context.startService(intent);
  }

  private static class NotificationInfo {

    private final String title;
    private final String channelId;

    private NotificationInfo(String title, String channelId) {
      this.title = title;
      this.channelId = channelId;
    }

    public String getTitle() {
      return title;
    }

    public String getChannelId() {
      return channelId;
    }
  }
}
