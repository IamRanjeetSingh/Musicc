package com.example.musicc;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;


public class MusiccApp extends Application {
    public static final String PLAYBACK_CHANNEL_ID = "PlaybackChannelId";
    public static final String SHARED_PREFERENCE = "MusiccSharedPreference";

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    private void createNotificationChannel(){
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
            NotificationChannel playbackChannel = new NotificationChannel(PLAYBACK_CHANNEL_ID, getResources().getString(R.string.PlaybackChannel), NotificationManager.IMPORTANCE_LOW);
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(playbackChannel);
        }
    }
}
