package com.example.musicc.iteration.service;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.media.MediaDescription;
import android.media.MediaMetadata;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.media.browse.MediaBrowser;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.MediaStore;
import android.service.media.MediaBrowserService;
import android.support.v4.media.session.MediaSessionCompat;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.media.AudioAttributesCompat;
import androidx.media.AudioFocusRequestCompat;
import androidx.media.AudioManagerCompat;

import com.example.musicc.MusiccApp;
import com.example.musicc.R;
import com.example.musicc.iteration.view.activity.SongListActivity;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class MusicPlayerService extends MediaBrowserService implements MediaPlayer.OnCompletionListener {
    private static final String TAG = "MusicPlayerService";
    private static final String DEFAULT_ROOT_ID = "DefaultRootId";
    private static final String MEDIA_SESSION_TAG = "MediaSessionTag";
    private static final int PLAYBACK_NOTIFICATION_ID = 100;
    private static final String ACTION_SKIP_TO_PREVIOUS = "ActionSkipToPrevious";
    private static final String ACTION_PLAY = "ActionPlay";
    private static final String ACTION_PAUSE = "ActionPause";
    private static final String ACTION_SKIP_TO_NEXT = "ActionSkipToNext";

    public static final String ACTION_TOGGLE_LOOP = "ToggleLoop";
    public static final String ACTION_TOGGLE_SHUFFLE = "ToggleShuffle";

    public static final String EVENT_EXTRAS_CHANGED = "SessionExtrasChanged";

    private static final String LAST_PLAYBACK_STATE = "LastPlaybackState";
    private static final String LAST_MEDIA_ITEM_ID = "LastMediaItemId";
    private static final String LAST_MEDIA_ITEM_POSITION = "LastMediaItemPosition";
    public static final String IS_LOOPING = "IsLooping";
    public static final String IS_SHUFFLING = "IsShuffling";

    public static final String METADATA_MEDIA_ID = MediaMetadata.METADATA_KEY_MEDIA_ID;
    public static final String METADATA_TITLE = MediaMetadata.METADATA_KEY_TITLE;
    public static final String METADATA_ARTIST = MediaMetadata.METADATA_KEY_ARTIST;
    public static final String METADATA_ALBUM = MediaMetadata.METADATA_KEY_ALBUM;
    public static final String METADATA_DURATION = MediaMetadata.METADATA_KEY_DURATION;
    public static final String METADATA_COVER_IMAGE = MediaMetadata.METADATA_KEY_ART;

    private MediaSession mediaSession;
    private MediaPlayer mediaPlayer;
    private AudioManager audioManager;
    private AudioNoisyReceiver audioNoisyReceiver;
    private PlaybackState.Builder playbackStateBuilder;
    private MediaMetadata.Builder metadataBuilder;

    private List<MediaBrowser.MediaItem> mediaItemList;
    private int currentMediaIndex = -1;
    private boolean isLooping = false;
    private boolean isShuffling = false;
    private MediaMetadataRetriever metadataRetriever = new MediaMetadataRetriever();
    private AudioAttributesCompat audioAttr = new AudioAttributesCompat.Builder()
                                                                    .setUsage(AudioAttributesCompat.USAGE_MEDIA)
                                                                    .setContentType(AudioAttributesCompat.CONTENT_TYPE_MUSIC)
                                                                    .build();
    private AudioFocusRequestCompat audioFocusReq = new AudioFocusRequestCompat.Builder(AudioManagerCompat.AUDIOFOCUS_GAIN)
                                                                            .setAudioAttributes(audioAttr)
                                                                            .setOnAudioFocusChangeListener(new FocusChangeListener())
                                                                            .build();



    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate() called");
        new SongLoader(new SongLoader.OnTaskCompleteListener() {
            @Override
            public void onTaskComplete(@Nullable List<MediaBrowser.MediaItem> mediaItemList) {MusicPlayerService.this.mediaItemList = mediaItemList;}
        });
        mediaSession = new MediaSession(MusicPlayerService.this, MEDIA_SESSION_TAG);
        mediaPlayer = new MediaPlayer();
        mediaPlayer.setOnCompletionListener(this);
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        audioNoisyReceiver = new AudioNoisyReceiver();
        registerReceiver(audioNoisyReceiver, new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY));
        playbackStateBuilder = new PlaybackState.Builder();
        playbackStateBuilder.addCustomAction(ACTION_TOGGLE_LOOP, ACTION_TOGGLE_LOOP, 0);
        playbackStateBuilder.addCustomAction(ACTION_TOGGLE_SHUFFLE, ACTION_TOGGLE_SHUFFLE, 0);
        metadataBuilder = new MediaMetadata.Builder();
        SharedPreferences sharedPreferences = getSharedPreferences(MusiccApp.SHARED_PREFERENCE, MODE_PRIVATE);

        mediaPlayer.setWakeMode(MusicPlayerService.this, PowerManager.PARTIAL_WAKE_LOCK);

        int state = sharedPreferences.getInt(LAST_PLAYBACK_STATE, PlaybackState.STATE_NONE);
        long position = sharedPreferences.getLong(LAST_MEDIA_ITEM_POSITION, PlaybackState.PLAYBACK_POSITION_UNKNOWN);
        long actions;

        if(state == PlaybackState.STATE_PAUSED)
            actions = PlaybackState.ACTION_PLAY_FROM_MEDIA_ID | PlaybackState.ACTION_PLAY | PlaybackState.ACTION_SKIP_TO_PREVIOUS | PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_SEEK_TO;
        else
            actions = PlaybackState.ACTION_PLAY_FROM_MEDIA_ID;

        playbackStateBuilder.setState(state, position, 1.0f);
        playbackStateBuilder.setActions(actions);

        mediaSession.setCallback(mediaSessionCallback);
        if(Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
            mediaSession.setFlags(MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS | MediaSession.FLAG_HANDLES_MEDIA_BUTTONS);
        setSessionToken(mediaSession.getSessionToken());

        mediaSession.setPlaybackState(playbackStateBuilder.build());
        mediaSession.setMetadata(null);
        mediaSession.setExtras(new Bundle());
        mediaSession.setActive(true);
    }

    @Override
    public int onStartCommand(@NonNull Intent intent, int flags, int startId) {
        if(intent.getAction() != null){
            Log.d(TAG, "onStartCommand: "+intent.getAction());
            switch (intent.getAction()){
                case ACTION_PLAY:
                    mediaSession.getController().getTransportControls().play();
                    break;

                case ACTION_PAUSE:
                    mediaSession.getController().getTransportControls().pause();
                    break;

                case ACTION_SKIP_TO_PREVIOUS:
                    mediaSession.getController().getTransportControls().skipToPrevious();
                    break;

                case ACTION_SKIP_TO_NEXT:
                    mediaSession.getController().getTransportControls().skipToNext();
                    break;

                case "delete":
                    stopSelf();
                    break;
            }
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy() called");

//        SharedPreferences.Editor editor = getSharedPreferences(MusiccApp.SHARED_PREFERENCE, MODE_PRIVATE).edit();
//        editor.putInt(LAST_PLAYBACK_STATE, mediaSession.getController().getPlaybackState() != null ? mediaSession.getController().getPlaybackState().getState() : PlaybackState.STATE_NONE);
//        editor.putString(LAST_MEDIA_ITEM_ID, mediaSession.getController().getMetadata() != null ? mediaSession.getController().getMetadata().getString(METADATA_MEDIA_ID) : null);
//        editor.putLong(LAST_MEDIA_ITEM_POSITION, mediaSession.getController().getPlaybackState() != null ? mediaSession.getController().getPlaybackState().getPosition() : PlaybackState.PLAYBACK_POSITION_UNKNOWN);
//        editor.putBoolean(IS_LOOPING, isLooping);
//        editor.putBoolean(IS_SHUFFLING, isShuffling);
//        editor.apply();

        mediaSession.release();
        mediaPlayer.release();
        unregisterReceiver(audioNoisyReceiver);
    }

    @Nullable
    @Override
    public BrowserRoot onGetRoot(@NonNull String clientPackageName, int clientUid, @Nullable Bundle rootHints) {
        Log.d(TAG, "onGetRoot() called");
        return new BrowserRoot(DEFAULT_ROOT_ID, null);
    }

    @Override
    public void onLoadChildren(@NonNull String parentId, @NonNull final Result<List<MediaBrowser.MediaItem>> result) {
        Log.d(TAG, "onLoadChildren() called");
        if(parentId.equals(DEFAULT_ROOT_ID)){
            result.detach();
            new SongLoader(new SongLoader.OnTaskCompleteListener() {
                @Override
                public void onTaskComplete(@Nullable List<MediaBrowser.MediaItem> mediaItemList) {
                    MusicPlayerService.this.mediaItemList = mediaItemList;
                    result.sendResult(mediaItemList);
                }
            }).execute(MusicPlayerService.this);
        }
        else {
            result.sendResult(null);
        }
    }



    private MediaSession.Callback mediaSessionCallback = new MediaSession.Callback() {

        //Transport Controls
        @Override
        public void onPlayFromMediaId(String mediaId, Bundle extras) {
            Log.d(TAG, "onPlayFromMediaId() called");
            currentMediaIndex = findMediaItemIndex(mediaId);
            if(currentMediaIndex == -1 || mediaItemList == null) return;

            handleStopRequest();
            handlePrepareRequest(mediaItemList.get(currentMediaIndex));
            handlePlayRequest();
        }

        @Override
        public void onPlay() {
            handlePlayRequest();
        }

        @Override
        public void onPause() {
            handlePauseRequest();
        }

        @Override
        public void onStop() {
            handleStopRequest();
        }

        @Override
        public void onSkipToNext() {
            if(mediaItemList == null) return;

            if(!isShuffling)
                currentMediaIndex = (currentMediaIndex+1)%mediaItemList.size();
            else
                currentMediaIndex = new Random().nextInt(mediaItemList.size())%mediaItemList.size();

            handlePrepareRequest(mediaItemList.get(currentMediaIndex));
            handlePlayRequest();
        }

        @Override
        public void onSkipToPrevious() {
            if(mediaItemList == null) return;

            if(!isShuffling)
                currentMediaIndex = Math.max(currentMediaIndex-1, 0);
            else
                currentMediaIndex = Math.max(new Random().nextInt(mediaItemList.size()), 0);

            handlePrepareRequest(mediaItemList.get(currentMediaIndex));
            handlePlayRequest();
        }

        @Override
        public void onSeekTo(long pos) {
            handleSeekRequest(pos);
        }

        @Override
        public void onCustomAction(@NonNull String action, @Nullable Bundle extras) {
            Bundle sessionExtras = mediaSession.getController().getExtras() != null ? mediaSession.getController().getExtras() : new Bundle();
            Log.d(TAG, "onCustomAction: "+action);
            switch (action) {
                case ACTION_TOGGLE_LOOP:
                    isLooping = !isLooping;
                    sessionExtras.putBoolean(IS_LOOPING, isLooping);
                    break;
                case ACTION_TOGGLE_SHUFFLE:
                    isShuffling = !isShuffling;
                    sessionExtras.putBoolean(IS_SHUFFLING, isShuffling);
                    break;
            }
            mediaSession.setExtras(sessionExtras);
            mediaSession.sendSessionEvent(EVENT_EXTRAS_CHANGED, mediaSession.getController().getExtras());
        }

        //find media item's index for the given mediaId from media item list
        private int findMediaItemIndex(@Nullable String mediaId){
            int index = -1;

            if(mediaId != null && mediaItemList != null){
                for(MediaBrowser.MediaItem item : mediaItemList){
                    index++;
                    if(item.getMediaId() != null && item.getMediaId().equals(mediaId)) {
                        break;
                    }
                }
                index = index >= mediaItemList.size() ? -1 : index;
            }

            return index;
        }
    };

    @Override
    public void onCompletion(MediaPlayer mp) {
        if(!isLooping)
            mediaSession.getController().getTransportControls().skipToNext();
        else {
            mediaSession.getController().getTransportControls().seekTo(0);
            mediaSession.getController().getTransportControls().play();
        }
    }

    //handle different requests
    private void handlePlayRequest(){
        Log.d(TAG, "handlePlayRequest() called");
        if(AudioManagerCompat.requestAudioFocus(audioManager, audioFocusReq) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            Log.d(TAG, "handlePlayRequest: Unable to acquire audio focus");
            return;
        }

        mediaPlayer.start();
        startService(new Intent(MusicPlayerService.this, MusicPlayerService.class));
        startForeground(PLAYBACK_NOTIFICATION_ID, getMediaNotification(ACTION_PAUSE));

        playbackStateBuilder.setState(PlaybackState.STATE_PLAYING, mediaPlayer.getCurrentPosition(), 1.0f);
        playbackStateBuilder.setActions(PlaybackState.ACTION_PLAY_FROM_MEDIA_ID | PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_STOP | PlaybackState.ACTION_SKIP_TO_PREVIOUS | PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_SEEK_TO);

        mediaSession.setPlaybackState(playbackStateBuilder.build());
    }

    private void handlePauseRequest(){
        mediaPlayer.pause();
        //stopSelf();
        stopForeground(false);
        NotificationManagerCompat.from(MusicPlayerService.this).notify(PLAYBACK_NOTIFICATION_ID, getMediaNotification(ACTION_PLAY));

        AudioManagerCompat.abandonAudioFocusRequest(audioManager, audioFocusReq);

        playbackStateBuilder.setState(PlaybackState.STATE_PAUSED, mediaPlayer.getCurrentPosition(), 1.0f);
        playbackStateBuilder.setActions(PlaybackState.ACTION_PLAY_FROM_MEDIA_ID | PlaybackState.ACTION_PLAY | PlaybackState.ACTION_STOP | PlaybackState.ACTION_SKIP_TO_PREVIOUS | PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_SEEK_TO);

        mediaSession.setPlaybackState(playbackStateBuilder.build());
    }

    private void handleStopRequest(){
        Log.d(TAG, "handleStopRequest() called");
        mediaPlayer.stop();
        stopSelf();
        stopForeground(false);
        NotificationManagerCompat.from(MusicPlayerService.this).notify(PLAYBACK_NOTIFICATION_ID, getMediaNotification(ACTION_PLAY));

        AudioManagerCompat.abandonAudioFocusRequest(audioManager, audioFocusReq);

        playbackStateBuilder.setState(PlaybackState.STATE_STOPPED, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1.0f)
                .setActions(PlaybackState.ACTION_PLAY_FROM_MEDIA_ID | PlaybackState.ACTION_SKIP_TO_PREVIOUS | PlaybackState.ACTION_SKIP_TO_NEXT);

        metadataBuilder.putString(METADATA_TITLE, null)
                .putString(METADATA_ARTIST, null)
                .putString(METADATA_ALBUM, null)
                .putLong(METADATA_DURATION, 0L)
                .putBitmap(METADATA_COVER_IMAGE, null);

        mediaSession.setPlaybackState(playbackStateBuilder.build());
        mediaSession.setMetadata(metadataBuilder.build());
    }

    private void handlePrepareRequest(@NonNull MediaBrowser.MediaItem mediaItem){
        Log.d(TAG, "handlePrepareRequest() called with: mediaItem.mediaId = [" + mediaItem.getMediaId() + "]");
        if(mediaItem.getMediaId() == null) return;

        //updating playback state
        playbackStateBuilder.setState(PlaybackState.STATE_CONNECTING, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1.0f)
                .setActions(PlaybackState.ACTION_PLAY_FROM_MEDIA_ID | PlaybackState.ACTION_SKIP_TO_PREVIOUS | PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_SEEK_TO);

        //updating metadata
        metadataBuilder.putString(METADATA_MEDIA_ID, null)
                .putString(METADATA_TITLE, null)
                .putString(METADATA_ARTIST, null)
                .putString(METADATA_ALBUM, null)
                .putLong(METADATA_DURATION, 0L)
                .putBitmap(METADATA_COVER_IMAGE, null);


        mediaSession.setPlaybackState(playbackStateBuilder.build());
        mediaSession.setMetadata(metadataBuilder.build());

        Uri mediaSource = ContentUris.withAppendedId(SongLoader.CONTENT_URI, Long.parseLong(mediaItem.getMediaId()));

        //setting data source for media player and metadata retriever
        metadataRetriever.setDataSource(MusicPlayerService.this, mediaSource);
        mediaPlayer.reset();
        try {
            mediaPlayer.setDataSource(MusicPlayerService.this, mediaSource);
            mediaPlayer.prepare();
        } catch (IOException e) {
            e.printStackTrace();
        }

        //updating playback state
        playbackStateBuilder.setState(PlaybackState.STATE_PAUSED, mediaPlayer.getCurrentPosition(), 1.0f)
                .setActions(PlaybackState.ACTION_PLAY_FROM_MEDIA_ID | PlaybackState.ACTION_PLAY | PlaybackState.ACTION_STOP | PlaybackState.ACTION_SKIP_TO_PREVIOUS | PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_SEEK_TO);

        //creating metadata
        String title = mediaItem.getDescription().getTitle() != null ? mediaItem.getDescription().getTitle().toString() : getResources().getString(R.string.UNKNOWN_TITLE);
        String artist = mediaItem.getDescription().getExtras() != null ? mediaItem.getDescription().getExtras().getString(METADATA_ARTIST) : getResources().getString(R.string.UNKNOWN_ARTIST);
        String album = mediaItem.getDescription().getExtras() != null ? mediaItem.getDescription().getExtras().getString(METADATA_ALBUM) : getResources().getString(R.string.UNKNOWN_ALBUM);

        String duration = metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
        if(duration == null) duration = "0";

        Bitmap coverImage = null;
        if(metadataRetriever.getEmbeddedPicture() != null) {
            BitmapFactory.Options bitmapFactorOpt = new BitmapFactory.Options();

            bitmapFactorOpt.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(metadataRetriever.getEmbeddedPicture(), 0, metadataRetriever.getEmbeddedPicture().length, bitmapFactorOpt);
            bitmapFactorOpt.inJustDecodeBounds = false;

            if(bitmapFactorOpt.outWidth > 500 || bitmapFactorOpt.outHeight > 500){
                bitmapFactorOpt.inSampleSize = bitmapFactorOpt.outWidth>bitmapFactorOpt.outHeight ? bitmapFactorOpt.outWidth/500 : bitmapFactorOpt.outHeight/500;
            }

            coverImage = BitmapFactory.decodeByteArray(metadataRetriever.getEmbeddedPicture(), 0, metadataRetriever.getEmbeddedPicture().length, bitmapFactorOpt);
        }

        //updating metadata
        metadataBuilder.putString(METADATA_MEDIA_ID, mediaItem.getMediaId())
                .putString(METADATA_TITLE, title)
                .putString(METADATA_ARTIST, artist)
                .putString(METADATA_ALBUM, album)
                .putLong(METADATA_DURATION, Long.parseLong(duration))
                .putBitmap(METADATA_COVER_IMAGE, coverImage);

        mediaSession.setPlaybackState(playbackStateBuilder.build());
        mediaSession.setMetadata(metadataBuilder.build());
    }

    private void handleSeekRequest(long pos){
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            mediaPlayer.seekTo(pos, MediaPlayer.SEEK_CLOSEST_SYNC);
        else
            mediaPlayer.seekTo((int)pos);

        PlaybackState playbackState = mediaSession.getController().getPlaybackState();
        if(playbackState == null){
            Log.e(TAG, "handleSeekRequest: Playback State is Null", new NullPointerException());
            return;
        }

        playbackStateBuilder.setState(playbackState.getState(), mediaPlayer.getCurrentPosition(), 1.0f);

        mediaSession.setPlaybackState(playbackStateBuilder.build());
    }

    //Action specified in the argument play/pause is going to be the action available from the notification
    private Notification getMediaNotification(@NonNull String playPauseAction){
        MediaMetadata metadata = mediaSession.getController().getMetadata();



        String title = metadata != null ? metadata.getString(METADATA_TITLE) : getResources().getString(R.string.UNKNOWN_TITLE);
        Bitmap icon = metadata != null ? metadata.getBitmap(METADATA_COVER_IMAGE) : drawableToBitmap(ContextCompat.getDrawable(this, R.drawable.ic_music_note_2));
        Intent prevIntent = new Intent(MusicPlayerService.this, MusicPlayerService.class), playPauseIntent = new Intent(MusicPlayerService.this, MusicPlayerService.class), nextIntent = new Intent(MusicPlayerService.this, MusicPlayerService.class);
        prevIntent.setAction(ACTION_SKIP_TO_PREVIOUS);
        playPauseIntent.setAction(playPauseAction.equals(ACTION_PLAY) || playPauseAction.equals(ACTION_PAUSE) ? playPauseAction : ACTION_PAUSE);
        nextIntent.setAction(ACTION_SKIP_TO_NEXT);

        Intent contentIntent = new Intent(MusicPlayerService.this, SongListActivity.class);

        Intent deleteIntent = new Intent(MusicPlayerService.this, MusicPlayerService.class);
        deleteIntent.setAction("delete");


        return new NotificationCompat.Builder(MusicPlayerService.this, MusiccApp.PLAYBACK_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setDeleteIntent(PendingIntent.getService(MusicPlayerService.this, 200, deleteIntent, PendingIntent.FLAG_UPDATE_CURRENT))
                .setLargeIcon(icon)
                .setContentIntent(PendingIntent.getActivity(MusicPlayerService.this, 100, contentIntent, PendingIntent.FLAG_UPDATE_CURRENT))
                .addAction(R.drawable.ic_previous_accent, "prev", PendingIntent.getService(MusicPlayerService.this, 101, prevIntent, PendingIntent.FLAG_UPDATE_CURRENT))
                .addAction(playPauseAction.equals(ACTION_PAUSE) ? R.drawable.ic_pause_accent : R.drawable.ic_play_accent, playPauseAction.equals(ACTION_PAUSE) ? "pause" : "play", PendingIntent.getService(MusicPlayerService.this, 102, playPauseIntent, PendingIntent.FLAG_UPDATE_CURRENT))
                .addAction(R.drawable.ic_next_accent, "next", PendingIntent.getService(MusicPlayerService.this, 103, nextIntent, PendingIntent.FLAG_UPDATE_CURRENT))
                .setStyle(new androidx.media.app.NotificationCompat.MediaStyle().setShowActionsInCompactView(0,1,2).setMediaSession(MediaSessionCompat.fromMediaSession(MusicPlayerService.this, mediaSession).getSessionToken()))
                .build();
    }



    private Bitmap drawableToBitmap (Drawable drawable) {
        Bitmap bitmap;

        if (drawable instanceof BitmapDrawable) {
            BitmapDrawable bitmapDrawable = (BitmapDrawable) drawable;
            if(bitmapDrawable.getBitmap() != null) {
                return bitmapDrawable.getBitmap();
            }
        }

        if(drawable.getIntrinsicWidth() <= 0 || drawable.getIntrinsicHeight() <= 0) {
            bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888); // Single color bitmap will be created of 1x1 pixel
        } else {
            bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        }

        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);
        return bitmap;
    }




    private class FocusChangeListener implements AudioManager.OnAudioFocusChangeListener{
        @Override
        public void onAudioFocusChange(int focusChange) {
            switch (focusChange){
                case AudioManager.AUDIOFOCUS_LOSS:
                    Log.d(TAG, "onAudioFocusChange: Audio focus lost type: LOSS");
                    mediaSession.getController().getTransportControls().pause();
                    break;

                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                    Log.d(TAG, "onAudioFocusChange: Audio focus lost type: TRANSIENT");
                    break;

                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                    Log.d(TAG, "onAudioFocusChange: Audio focus lost type: TRANSIENT_CAN_DUCK");
                    break;
            }
        }
    }



    private class AudioNoisyReceiver extends BroadcastReceiver{
        @Override
        public void onReceive(Context context, Intent intent) {
            mediaSession.getController().getTransportControls().pause();
        }
    }



    private static class SongLoader extends AsyncTask<Context, Void, List<MediaBrowser.MediaItem>> {
        private static final Uri CONTENT_URI = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        private static final String ID = MediaStore.Audio.Media._ID;
        private static final String TITLE = MediaStore.Audio.Media.TITLE;
        private static final String ARTIST = MediaStore.Audio.Media.ARTIST;
        private static final String ALBUM = MediaStore.Audio.Media.ALBUM;

        private OnTaskCompleteListener taskCompleteListener;

        private interface OnTaskCompleteListener{
            void onTaskComplete(@Nullable List<MediaBrowser.MediaItem> mediaItemList);
        }

        private SongLoader(OnTaskCompleteListener taskCompleteListener){
            this.taskCompleteListener = taskCompleteListener;
        }

        @Override
        protected List<MediaBrowser.MediaItem> doInBackground(@NonNull Context... contexts) {
            Cursor songCursor = contexts[0].getContentResolver().query(CONTENT_URI, new String[]{ID, TITLE, ARTIST, ALBUM}, MediaStore.Audio.Media.IS_MUSIC+" != 0", null, MediaStore.Audio.Media.DEFAULT_SORT_ORDER);
            List<MediaBrowser.MediaItem> mediaItemList = new ArrayList<>();

            if(songCursor == null) return mediaItemList;

            MediaDescription.Builder mediaDescriptionBuilder = new MediaDescription.Builder();
            Bundle extras;

            songCursor.moveToFirst();
            while(!songCursor.isAfterLast()){
                extras = new Bundle();
                extras.putString(METADATA_ARTIST, songCursor.getString(songCursor.getColumnIndex(ARTIST)));
                extras.putString(METADATA_ALBUM, songCursor.getString(songCursor.getColumnIndex(ALBUM)));

                mediaDescriptionBuilder.setMediaId(songCursor.getString(songCursor.getColumnIndex(ID)))
                        .setTitle(songCursor.getString(songCursor.getColumnIndex(TITLE)))
                        .setExtras(extras);

                mediaItemList.add(new MediaBrowser.MediaItem(mediaDescriptionBuilder.build(), MediaBrowser.MediaItem.FLAG_PLAYABLE));
                songCursor.moveToNext();
            }

            songCursor.close();


            return mediaItemList;
        }

        @Override
        protected void onPostExecute(List<MediaBrowser.MediaItem> mediaItemList) {
            taskCompleteListener.onTaskComplete(mediaItemList);
        }
    }
}