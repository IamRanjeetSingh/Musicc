package com.example.musicc.service;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaDescription;
import android.media.MediaMetadata;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.media.PlaybackParams;
import android.media.browse.MediaBrowser;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.service.media.MediaBrowserService;
import android.support.v4.media.session.MediaSessionCompat;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.example.musicc.MusiccApp;
import com.example.musicc.R;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MusicPlayerService extends MediaBrowserService implements MediaPlayer.OnCompletionListener {
    private static final String TAG = "MusicPlayerService";

    public static final String BROWSER_ROOT_ID = "LocalSongBrowser";

    private MediaSession mediaSession;
    private PlaybackState.Builder playbackStateBuilder = new PlaybackState.Builder();
    private MediaMetadata.Builder mediaMetaDataBuilder = new MediaMetadata.Builder();
    private MediaMetadataRetriever metadataRetriever = new MediaMetadataRetriever();
    @Nullable private List<MediaBrowser.MediaItem> mediaItemList;
    private MediaPlayer mediaPlayer;
    private int currentMediaIndex = -1;

    @Override
    public void onCreate() {
        super.onCreate();
        mediaSession = new MediaSession(getApplicationContext(), "MusicPlayerServiceSession");
        initMediaSession(mediaSession);
        setSessionToken(mediaSession.getSessionToken());
        mediaPlayer = new MediaPlayer();
        mediaPlayer.setOnCompletionListener(this);
    }

    private void initMediaSession(@NonNull MediaSession mediaSession){
        playbackStateBuilder.setActions(PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PLAY_FROM_MEDIA_ID);
        if(Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
            mediaSession.setFlags(MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS | MediaSession.FLAG_HANDLES_MEDIA_BUTTONS);
        mediaSession.setPlaybackState(playbackStateBuilder.build());
        mediaSession.setCallback(mediaSessionCallbacks);
    }


    private MediaSession.Callback mediaSessionCallbacks = new MediaSession.Callback() {
        @Override
        public void onPlayFromMediaId(String mediaId, Bundle extras) {
            handleStopRequest();

            currentMediaIndex = findMediaItemIndex(mediaId);
            if(currentMediaIndex == -1)return;
            MediaBrowser.MediaItem mediaItem = mediaItemList.get(currentMediaIndex);

            handlePrepareRequest(mediaItem);

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
            if(mediaItemList != null) {
                currentMediaIndex++;
                if (currentMediaIndex >= mediaItemList.size())
                    currentMediaIndex = 0;

                MediaBrowser.MediaItem mediaItem = mediaItemList.get(currentMediaIndex);

                handlePrepareRequest(mediaItem);

                handlePlayRequest();
            }
        }

        @Override
        public void onSkipToPrevious() {
            if(mediaItemList != null) {
                currentMediaIndex--;
                if (currentMediaIndex < 0)
                    currentMediaIndex = mediaItemList.size()-1;

                MediaBrowser.MediaItem mediaItem = mediaItemList.get(currentMediaIndex);

                handlePrepareRequest(mediaItem);

                handlePlayRequest();
            }
        }

        @Override
        public void onSeekTo(long pos) {
            handleSeekRequest(pos);
        }
    };


    @Override
    public void onCompletion(MediaPlayer mp) {
        mediaSessionCallbacks.onStop();
        mediaSessionCallbacks.onSkipToNext();
    }

    private int findMediaItemIndex(@NonNull String mediaId){
        int index = -1;
        if(mediaItemList != null) {
            for (MediaBrowser.MediaItem mediaItem : mediaItemList) {
                index++;
                if (mediaItem.getMediaId() != null && mediaItem.getMediaId().equals(mediaId))
                    break;
            }
        }
        return index;
    }



    private Notification getNotification(){
        MediaMetadata metadata = mediaSession.getController().getMetadata();
        Bitmap icon = metadata != null ? metadata.getBitmap(MediaMetadata.METADATA_KEY_ART) : BitmapFactory.decodeResource(getResources(), R.drawable.ic_music_note_2);
        String title = metadata != null ? metadata.getString(MediaMetadata.METADATA_KEY_TITLE) : getResources().getString(R.string.UNKNOWN_TITLE);
        return new NotificationCompat.Builder(this, MusiccApp.PLAYBACK_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setLargeIcon(icon)
                .setContentTitle(title)
                .setStyle(new androidx.media.app.NotificationCompat.MediaStyle().setMediaSession(MediaSessionCompat.Token.fromToken(mediaSession.getSessionToken())))
                .build();
    }



    //Handle separate requests
    private void handlePlayRequest(){
        mediaPlayer.start();
        startService(new Intent(this, MusicPlayerService.class));
        startForeground(1, getNotification());

        playbackStateBuilder.setState(PlaybackState.STATE_PLAYING, mediaPlayer.getCurrentPosition(), 1.0f);
        playbackStateBuilder.setActions(PlaybackState.ACTION_PLAY_FROM_MEDIA_ID | PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_STOP | PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_SKIP_TO_PREVIOUS);

        mediaSession.setPlaybackState(playbackStateBuilder.build());
    }

    private void handlePauseRequest(){
        mediaPlayer.pause();
        stopForeground(false);

        playbackStateBuilder.setState(PlaybackState.STATE_PAUSED, mediaPlayer.getCurrentPosition(), 1.0f);
        playbackStateBuilder.setActions(PlaybackState.ACTION_PLAY_FROM_MEDIA_ID | PlaybackState.ACTION_PLAY | PlaybackState.ACTION_STOP | PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_SKIP_TO_PREVIOUS);

        mediaSession.setPlaybackState(playbackStateBuilder.build());
    }

    private void handleStopRequest(){
        mediaPlayer.stop();
        stopForeground(false);

        playbackStateBuilder.setState(PlaybackState.STATE_STOPPED, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1.0f);
        playbackStateBuilder.setActions(PlaybackState.ACTION_PLAY_FROM_MEDIA_ID);

        mediaSession.setPlaybackState(playbackStateBuilder.build());
    }

    private void handlePrepareRequest(@NonNull MediaBrowser.MediaItem mediaItem){
        if(mediaItem.getMediaId() == null) return;

        Uri mediaSource = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, Long.parseLong(mediaItem.getMediaId()));
        metadataRetriever.setDataSource(MusicPlayerService.this, mediaSource);

        mediaPlayer.reset();

        //update playback state
        playbackStateBuilder.setState(PlaybackState.STATE_CONNECTING, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1.0f);
        playbackStateBuilder.setActions(PlaybackState.ACTION_PLAY_FROM_MEDIA_ID);

        //update media metadata
        if(mediaItem.getDescription().getTitle() != null)
            mediaMetaDataBuilder.putString(MediaMetadata.METADATA_KEY_TITLE, mediaItem.getDescription().getTitle().toString());
        else
            mediaMetaDataBuilder.putString(MediaMetadata.METADATA_KEY_TITLE, getResources().getString(R.string.UNKNOWN_TITLE));

        if(metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION) != null)
            mediaMetaDataBuilder.putLong(MediaMetadata.METADATA_KEY_DURATION, Long.parseLong(metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)));
        else
            mediaMetaDataBuilder.putLong(MediaMetadata.METADATA_KEY_DURATION, 0L);


        byte[] embeddedPicture = metadataRetriever.getEmbeddedPicture();
        if(embeddedPicture != null)
            mediaMetaDataBuilder.putBitmap(MediaMetadata.METADATA_KEY_ART, BitmapFactory.decodeByteArray(embeddedPicture, 0, embeddedPicture.length));
        else
            mediaMetaDataBuilder.putBitmap(MediaMetadata.METADATA_KEY_ART, null);


        mediaSession.setPlaybackState(playbackStateBuilder.build());
        mediaSession.setMetadata(mediaMetaDataBuilder.build());


        try {
            mediaPlayer.setDataSource(MusicPlayerService.this, mediaSource);
            mediaPlayer.prepare();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handleSeekRequest(long pos){
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            mediaPlayer.seekTo(pos, MediaPlayer.SEEK_CLOSEST_SYNC);
        else
            mediaPlayer.seekTo((int)pos);

        PlaybackState state = mediaSession.getController().getPlaybackState();
        if(state != null)
            playbackStateBuilder.setState(state.getState(), mediaPlayer.getCurrentPosition(), 1.0f).setActions(state.getActions());
        else
            playbackStateBuilder.setState(PlaybackState.STATE_STOPPED, mediaPlayer.getCurrentPosition(), 1.0f).setActions(PlaybackState.ACTION_PLAY_FROM_MEDIA_ID | PlaybackState.ACTION_PLAY);

        mediaSession.setPlaybackState(playbackStateBuilder.build());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        metadataRetriever.release();
        mediaPlayer.release();
    }

    @Nullable
    @Override
    public BrowserRoot onGetRoot(@NonNull String clientPackageName, int clientUid, @Nullable Bundle rootHints) {
        return new BrowserRoot(BROWSER_ROOT_ID, null);
    }

    @Override
    public void onLoadChildren(@NonNull String parentId, @NonNull final Result<List<MediaBrowser.MediaItem>> result) {
        if(!parentId.equals(BROWSER_ROOT_ID))
            result.sendResult(null);
        Log.d(TAG, "onLoadChildren: starting to load songs...");
        SongLoader songLoader = new SongLoader(new SongLoader.TaskFinishListener() {
            @Override
            public void onTaskFinish(List<MediaBrowser.MediaItem> mediaItemList) {
                Log.d(TAG, "onTaskFinish: songs loaded!!!");
                MusicPlayerService.this.mediaItemList = mediaItemList;
                result.sendResult(mediaItemList);
            }
        });
        songLoader.execute(getApplicationContext());
        result.detach();
    }

    private static class SongLoader extends AsyncTask<Context, Void, List<MediaBrowser.MediaItem>> {
        private static final Uri CONTENT_URI = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        private static final String ID = MediaStore.Audio.Media._ID;
        private static final String TITLE = MediaStore.Audio.Media.TITLE;
        private static final String[] PROJECTION = {ID, TITLE};
        private static final String SELECTION = MediaStore.Audio.Media.IS_MUSIC+" != 0";

        private MediaMetadataRetriever metadataRetriever = new MediaMetadataRetriever();

        private interface TaskFinishListener{
            void onTaskFinish(List<MediaBrowser.MediaItem> mediaItemList);
        }

        @Nullable
        private TaskFinishListener taskFinishListener;

        private SongLoader(@Nullable TaskFinishListener taskFinishListener) {
            super();
            this.taskFinishListener = taskFinishListener;
        }

        @Override
        protected List<MediaBrowser.MediaItem> doInBackground(@NonNull Context... contexts) {
            Cursor songCursor = contexts[0].getContentResolver().query(CONTENT_URI, PROJECTION, SELECTION, null, MediaStore.Audio.Media.DEFAULT_SORT_ORDER);
            List<MediaBrowser.MediaItem> mediaItemList = new ArrayList<>();
            MediaDescription.Builder mediaDescriptionBuilder = new MediaDescription.Builder();
            if(songCursor == null)
                return mediaItemList;

            String mediaId, title;
            byte[] embeddedPicture;
            Bitmap thumbnail;
            BitmapFactory.Options bitmapOptions = new BitmapFactory.Options();

            songCursor.moveToFirst();
            while(!songCursor.isAfterLast()){
//                Log.d(TAG, "doInBackground: starting to set data source");
//                metadataRetriever.setDataSource(contexts[0], ContentUris.withAppendedId(CONTENT_URI, Long.parseLong(songCursor.getString(songCursor.getColumnIndex(ID)))));
//                Log.d(TAG, "doInBackground: data source set");
                
                mediaId = songCursor.getString(songCursor.getColumnIndex(ID));

                title = songCursor.getString(songCursor.getColumnIndex(TITLE));
                title = title != null ? title : contexts[0].getResources().getString(R.string.UNKNOWN_TITLE);

//                embeddedPicture = metadataRetriever.getEmbeddedPicture();
                thumbnail = null;
//
//                if(embeddedPicture != null) {
//                    bitmapOptions.inJustDecodeBounds = true;
//                    BitmapFactory.decodeByteArray(embeddedPicture, 0, embeddedPicture.length, bitmapOptions);
//                    bitmapOptions.inJustDecodeBounds = false;
//                    if (bitmapOptions.outHeight < 50 || bitmapOptions.outWidth > 50) {
//                        Log.d(TAG, "doInBackground: changing thumbnail size");
//                        bitmapOptions.inSampleSize = bitmapOptions.outHeight > bitmapOptions.outWidth ? bitmapOptions.outHeight / 50 : bitmapOptions.outWidth / 50;
//                        thumbnail = BitmapFactory.decodeByteArray(embeddedPicture, 0, embeddedPicture.length, bitmapOptions);
//                    } else {
//                        thumbnail = BitmapFactory.decodeByteArray(embeddedPicture, 0, embeddedPicture.length);
//                    }
//                }


                mediaDescriptionBuilder.setMediaId(mediaId)
                        .setTitle(title)
                        .setIconBitmap(thumbnail);

                mediaItemList.add(new MediaBrowser.MediaItem(mediaDescriptionBuilder.build(), MediaBrowser.MediaItem.FLAG_PLAYABLE));
                songCursor.moveToNext();
            }

            songCursor.close();
            return mediaItemList;
        }

        @Override
        protected void onPostExecute(List<MediaBrowser.MediaItem> mediaItemList) {
            super.onPostExecute(mediaItemList);
            metadataRetriever.release();
            if(taskFinishListener != null)
                taskFinishListener.onTaskFinish(mediaItemList);
        }
    }
}













































//
//    @Override
//    public void onPlayFromMediaId(String mediaId, Bundle extras) {
//        handleStopRequest();
//
//        currentMediaIndex = findMediaItemIndex(mediaId);
//        if(currentMediaIndex == -1)return;
//        MediaBrowser.MediaItem mediaItem = mediaItemList.get(currentMediaIndex);
//
//        Uri mediaSource = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, Long.parseLong(mediaId));
//
//        mediaPlayer.reset();
//        try {
//            mediaPlayer.setDataSource(MusicPlayerService.this, mediaSource);
//            mediaPlayer.prepare();
//
//            if(mediaItem.getDescription().getTitle() != null)
//                mediaMetaDataBuilder.putString(MediaMetadata.METADATA_KEY_TITLE, mediaItem.getDescription().getTitle().toString());
//
//            mediaSession.setMetadata(mediaMetaDataBuilder.build());
//
//            handlePlayRequest();
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//    }