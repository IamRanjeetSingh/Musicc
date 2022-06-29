package com.example.musicc.iteration.viewmodel;

import android.app.Application;
import android.content.ComponentName;
import android.content.Intent;
import android.media.MediaMetadata;
import android.media.browse.MediaBrowser;
import android.media.session.MediaController;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

import com.example.musicc.iteration.service.MusicPlayerService;
import com.example.musicc.iteration.view.activity.SongListActivity;

import java.util.List;

public class SongListViewModel extends AndroidViewModel {
    private static final String TAG = "SongListViewModel";

    private MediaBrowser mediaBrowser;
    private MediaController mediaController;
    private MutableLiveData<Events> events = new MutableLiveData<>(null);
    private MutableLiveData<Boolean> isLooping = new MutableLiveData<>(false);
    private MutableLiveData<Boolean> isShuffling = new MutableLiveData<>(false);

    private long totalSongDuration;

    public enum Events{
        PlaybackStateChanged,
        MediaMetaDataChanged
    }

    private MutableLiveData<List<MediaBrowser.MediaItem>> mediaItemList = new MutableLiveData<>(null);

    public SongListViewModel(@NonNull Application application) {
        super(application);
        mediaBrowser = new MediaBrowser(getApplication(), new ComponentName(getApplication(), MusicPlayerService.class), new ConnectionCallback(), null);
        mediaBrowser.connect();
    }

    public void onStart() {
        Log.d(TAG, "onStart() called");

    }

    public void onStop(){
        Log.d(TAG, "onStop() called");

    }

    @Override
    protected void onCleared() {
        super.onCleared();
        mediaBrowser.disconnect();
    }

    public MutableLiveData<List<MediaBrowser.MediaItem>> getMediaItemList(){
        return mediaItemList;
    }


    public void forceLoadMediaItemList(){
        if(mediaBrowser.isConnected()) {
            mediaBrowser.subscribe(mediaBrowser.getRoot(), new MediaBrowser.SubscriptionCallback() {
                @Override
                public void onChildrenLoaded(@NonNull String parentId, @NonNull List<MediaBrowser.MediaItem> children) {
                    mediaBrowser.unsubscribe(mediaBrowser.getRoot());
                    mediaItemList.postValue(children);
                }
            });
        } else
            mediaItemList.postValue(null);
    }


    public void playMediaItem(@NonNull  MediaBrowser.MediaItem mediaItem){
        if(mediaController == null) return;
        Log.d(TAG, "playMediaItem() called with: mediaItem = [" + mediaItem + "]");
        mediaController.getTransportControls().playFromMediaId(mediaItem.getMediaId(), null);
    }

    public void playPauseToggle(){
        if(mediaController == null) return;
        getApplication().startService(new Intent(getApplication(), MusicPlayerService.class));
        Log.d(TAG, "playPauseToggle() called");
        if(mediaController.getPlaybackState() != null && mediaController.getPlaybackState().getState() == PlaybackState.STATE_PLAYING)
            mediaController.getTransportControls().pause();
        else if(mediaController.getPlaybackState() != null && mediaController.getPlaybackState().getState() == PlaybackState.STATE_PAUSED)
            mediaController.getTransportControls().play();
    }

    public void skipToNext(){
        if(mediaController == null) return;
        mediaController.getTransportControls().skipToNext();
    }

    public void skipToPrevious(){
        if(mediaController == null) return;
        mediaController.getTransportControls().skipToPrevious();
    }

    public void seekTo(long pos){
        if(mediaController == null) return;
        mediaController.getTransportControls().seekTo(pos);
    }

    public void toggleLooping(){
        if(mediaController == null) return;
        mediaController.getTransportControls().sendCustomAction(MusicPlayerService.ACTION_TOGGLE_LOOP, null);
    }

    public MutableLiveData<Boolean> getIsLooping(){
        return isLooping;
    }

    public void toggleShuffling(){
        if(mediaController == null) return;
        mediaController.getTransportControls().sendCustomAction(MusicPlayerService.ACTION_TOGGLE_SHUFFLE, null);
    }

    public MutableLiveData<Boolean> getIsShuffling(){
        return isShuffling;
    }

    public void addEventObserver(@NonNull SongListActivity activity, @NonNull Observer<Events> eventsObserver){
        events.observe(activity, eventsObserver);
    }

    public void removeEventObserver(@NonNull Observer<Events> eventsObserver){
        events.removeObserver(eventsObserver);
    }

    public long getCurrentMediaPlayerPosition(){
        if(mediaController != null && mediaController.getPlaybackState() != null)
            return mediaController.getPlaybackState().getPosition();
        return 0L;
    }

    public long getTotalSongDuration(){
        return totalSongDuration;
    }

    @Nullable
    public PlaybackState getPlaybackState(){
        if(mediaController == null) return null;
        return mediaController.getPlaybackState();
    }

    @Nullable
    public MediaMetadata getMediaMetaData(){
        if(mediaController == null) return null;
        return mediaController.getMetadata();
    }


    private MediaController.Callback mediaControllerCallbacks = new MediaController.Callback() {
        int lastState = PlaybackState.STATE_NONE;
        @Override
        public void onPlaybackStateChanged(@Nullable PlaybackState state) {
            if(state == null || state.getState() != lastState)
                events.setValue(Events.PlaybackStateChanged);
            lastState = state != null ? state.getState() : PlaybackState.STATE_NONE;
        }

        @Override
        public void onMetadataChanged(@Nullable MediaMetadata metadata) {
            totalSongDuration = metadata != null ? metadata.getLong(MusicPlayerService.METADATA_DURATION) : 0L;
            events.setValue(Events.MediaMetaDataChanged);
        }

        @Override
        public void onSessionEvent(@NonNull String event, @Nullable Bundle extras) {
            if(event.equals(MusicPlayerService.EVENT_EXTRAS_CHANGED) && extras != null){
                Log.d(TAG, "onSessionEvent: isLooping: "+extras.getBoolean(MusicPlayerService.IS_LOOPING)+" isShuffling: "+extras.getBoolean(MusicPlayerService.IS_SHUFFLING));
                boolean value = extras.getBoolean(MusicPlayerService.IS_LOOPING, false);
                if(isLooping.getValue() != null && value != isLooping.getValue())
                    isLooping.setValue(value);
                value = extras.getBoolean(MusicPlayerService.IS_SHUFFLING, false);
                if(isShuffling.getValue() != null && value != isShuffling.getValue())
                    isShuffling.setValue(value);
            }
        }
    };


    private class ConnectionCallback extends MediaBrowser.ConnectionCallback{
        @Override
        public void onConnected() {
            Log.d(TAG, "onConnected: ");
            if(mediaItemList.getValue() == null) forceLoadMediaItemList();
            mediaController = new MediaController(getApplication(), mediaBrowser.getSessionToken());
            mediaController.registerCallback(mediaControllerCallbacks);
            if(mediaController.getExtras() != null){
                isLooping.setValue(mediaController.getExtras().getBoolean(MusicPlayerService.IS_LOOPING, false));
                isShuffling.setValue(mediaController.getExtras().getBoolean(MusicPlayerService.IS_SHUFFLING, false));
            }
            if(mediaController.getPlaybackState() != null){
                mediaControllerCallbacks.onMetadataChanged(mediaController.getMetadata());
                mediaControllerCallbacks.onPlaybackStateChanged(mediaController.getPlaybackState());
            }
        }

        @Override
        public void onConnectionSuspended() {
            Log.d(TAG, "onConnectionSuspended: ");
            mediaController.unregisterCallback(mediaControllerCallbacks);
            mediaController = null;
        }

        @Override
        public void onConnectionFailed() {
            Log.e(TAG, "onConnectionFailed: Connection to MusicPlayerService Failed", new RuntimeException());
        }
    }
}
