package com.example.musicc.view.activity;

import android.content.ComponentName;
import android.graphics.Bitmap;
import android.media.MediaMetadata;
import android.media.browse.MediaBrowser;
import android.media.session.MediaController;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.SeekBar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.databinding.DataBindingUtil;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.musicc.R;
import com.example.musicc.databinding.SongListActivityBinding;
import com.example.musicc.service.MusicPlayerService;
import com.example.musicc.view.adapter.OnViewHolderClickListener;
import com.example.musicc.view.adapter.SongListAdapter;

import java.util.List;

public class SongListActivity extends AppCompatActivity {
    private static final String TAG = "MyTag";

    private SongListActivityBinding binding;
    private MediaBrowser mediaBrowser;
    private MediaControllerCallbacks mediaControllerCallbacks = new MediaControllerCallbacks();
    private Handler mHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        binding = DataBindingUtil.setContentView(this, R.layout.song_list_activity);

        binding.songList.setLayoutManager(new LinearLayoutManager(this));
        SongListAdapter songListAdapter = new SongListAdapter();
        binding.songList.setAdapter(songListAdapter);
        binding.songList.setHasFixedSize(true);
        songListAdapter.setOnViewHolderClickListener(new OnViewHolderClickListener<SongListAdapter.ViewHolder>() {
            @Override
            public void onViewHolderClick(SongListAdapter.ViewHolder viewHolder) {
                if(getMediaController() != null)
                    getMediaController().getTransportControls().playFromMediaId(viewHolder.getMediaId(), null);
            }
        });

        binding.miniPlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                playPauseToggle();
            }
        });

        binding.play.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                playPauseToggle();
            }
        });

        binding.skipToNext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                skipToNext();
            }
        });

        binding.skipToPrevious.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                skipToPrevious();
            }
        });

        binding.progress.setOnSeekBarChangeListener(seekListener);

        mediaBrowser = new MediaBrowser(this, new ComponentName(this, MusicPlayerService.class), connectionCallback, null);
    }

    private void playPauseToggle(){
        if(getMediaController() != null && getMediaController().getPlaybackState() != null && getMediaController().getPlaybackState().getState() == PlaybackState.STATE_PLAYING)
            getMediaController().getTransportControls().pause();
        else if(getMediaController() != null && getMediaController().getPlaybackState() != null && getMediaController().getPlaybackState().getState() == PlaybackState.STATE_PAUSED)
            getMediaController().getTransportControls().play();
    }

    private void skipToNext(){
        if(getMediaController() != null)
            getMediaController().getTransportControls().skipToNext();
    }

    private void skipToPrevious(){
        if(getMediaController() != null)
            getMediaController().getTransportControls().skipToPrevious();
    }

    private SeekBar.OnSeekBarChangeListener seekListener = new SeekBar.OnSeekBarChangeListener(){
        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            if(fromUser) {
                int currentDuration = (int)(((float)progress / 100.0f)*mediaControllerCallbacks.totalDuration);
                binding.currentDuration.setText(getResources().getString(R.string.duration_Placeholder, currentDuration/60, currentDuration%60));
            }
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
            Log.d(TAG, "onStartTrackingTouch: ");
            mediaControllerCallbacks.setKeepTrackingProgress(false);
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            Log.d(TAG, "onStopTrackingTouch: ");
            long pos = (long) (((float) binding.progress.getProgress() / (float) binding.progress.getMax()) * mediaControllerCallbacks.totalDuration);
            Log.d(TAG, "onStopTrackingTouch: position: "+pos+" progress: "+binding.progress.getProgress());
            pos*=1000;
            getMediaController().getTransportControls().seekTo(pos);
            mediaControllerCallbacks.setKeepTrackingProgress(true);
        }
    };

    @Override
    protected void onStart() {
        super.onStart();
        mediaBrowser.connect();
    }

    @Override
    protected void onStop() {
        super.onStop();
        mediaBrowser.disconnect();
    }


    private MediaBrowser.ConnectionCallback connectionCallback = new MediaBrowser.ConnectionCallback(){
        @Override
        public void onConnected() {
            super.onConnected();
            mediaBrowser.subscribe(mediaBrowser.getRoot(), subscriptionCallback);
            setMediaController(new MediaController(SongListActivity.this, mediaBrowser.getSessionToken()));
            getMediaController().registerCallback(mediaControllerCallbacks);

            if(getMediaController().getPlaybackState() != null && getMediaController().getPlaybackState().getState() == PlaybackState.STATE_PLAYING){
                mediaControllerCallbacks.onPlaybackStateChanged(getMediaController().getPlaybackState());
                mediaControllerCallbacks.onMetadataChanged(getMediaController().getMetadata());
            }
        }

        @Override
        public void onConnectionSuspended() {
            super.onConnectionSuspended();
            mediaBrowser.unsubscribe(mediaBrowser.getRoot());
            getMediaController().unregisterCallback(mediaControllerCallbacks);
        }
    };

    private MediaBrowser.SubscriptionCallback subscriptionCallback = new MediaBrowser.SubscriptionCallback() {
        @Override
        public void onChildrenLoaded(@NonNull String parentId, @NonNull List<MediaBrowser.MediaItem> children) {
            super.onChildrenLoaded(parentId, children);
            if(binding.songList.getAdapter() == null) {
                Log.e(TAG, "onChildrenLoaded: Song List RecyclerView has no adapter", new NullPointerException());
                return;
            }

            ((SongListAdapter)binding.songList.getAdapter()).setMediaItemList(children);
        }
    };


    private class MediaControllerCallbacks extends MediaController.Callback{
        private long totalDuration;
        private MutableLiveData<Boolean> keepTrackingProgress = new MutableLiveData<>(false);

        private MediaControllerCallbacks(){
            keepTrackingProgress.observe(SongListActivity.this, new Observer<Boolean>() {
                @Override
                public void onChanged(Boolean aBoolean) {
                    if(aBoolean)
                        updateUi();
                }
            });
        }

        @Override
        public void onPlaybackStateChanged(@Nullable PlaybackState state) {
            Log.d(TAG, "onPlaybackStateChanged() called with: state = [" + state + "]");
            if(state == null)
                return;
            int stateState = state.getState();

            if (stateState == PlaybackState.STATE_PLAYING) {
                keepTrackingProgress.postValue(true);
                binding.miniPlay.setImageResource(R.drawable.ic_pause);
                binding.play.setImageResource(R.drawable.ic_pause);
            } else if (stateState == PlaybackState.STATE_PAUSED) {
                keepTrackingProgress.postValue(false);
                binding.miniPlay.setImageResource(R.drawable.ic_play);
                binding.play.setImageResource(R.drawable.ic_play_accent);
            }
        }

        @Override
        public void onMetadataChanged(@Nullable MediaMetadata metadata) {
            Log.d(TAG, "onMetadataChanged() called with: metadata = [" + metadata + "]");
            if(metadata == null) return;

            String title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE);
            totalDuration= metadata.getLong(MediaMetadata.METADATA_KEY_DURATION)/1000;
            Bitmap albumArt = metadata.getBitmap(MediaMetadata.METADATA_KEY_ART);

            if(title != null){
                binding.songTitle.setText(title);
                binding.miniSongTitle.setText(title);
            } else{
                binding.songTitle.setText(getResources().getString(R.string.UNKNOWN_TITLE));
                binding.miniSongTitle.setText(getResources().getString(R.string.UNKNOWN_TITLE));
            }

            if(totalDuration != 0L){
                binding.totalDuration.setText(getResources().getString(R.string.duration_Placeholder, totalDuration/60, totalDuration%60));
            } else{
                binding.totalDuration.setText(getResources().getString(R.string.UNKNOWN_DURATION));
            }

            if(albumArt != null){
                binding.thumbnail.setImageBitmap(albumArt);
                binding.miniThumbnail.setImageBitmap(albumArt);
            } else{
                Log.d(TAG, "onMetadataChanged: album art is null");
                binding.thumbnail.setImageResource(R.drawable.ic_music_note);
                binding.miniThumbnail.setImageResource(R.drawable.ic_music_note);
            }
        }

        private void setKeepTrackingProgress(boolean keepTrackingProgress){
            this.keepTrackingProgress.postValue(keepTrackingProgress && getMediaController() != null && getMediaController().getPlaybackState() != null && getMediaController().getPlaybackState().getState() == PlaybackState.STATE_PLAYING);
        }

        private void updateUi(){
            if(getMediaController() != null && getMediaController().getPlaybackState() != null && keepTrackingProgress.getValue() != null && keepTrackingProgress.getValue()) {
                long currentDuration = getMediaController().getPlaybackState().getPosition()/1000;
                binding.currentDuration.setText(getResources().getString(R.string.duration_Placeholder, currentDuration/60,currentDuration%60));

                float progress = currentDuration != 0 && totalDuration != 0 ? (float)currentDuration / (float)totalDuration : 0;
                binding.progress.setProgress((int) (progress * binding.progress.getMax()));
                binding.miniProgress.setProgress((int) (progress * binding.miniProgress.getMax()));

                mHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            updateUi();
                        }
                    }, 500);
            }
        }
    }
}