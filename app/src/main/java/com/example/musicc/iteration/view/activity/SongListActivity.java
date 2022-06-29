package com.example.musicc.iteration.view.activity;

import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadata;
import android.media.browse.MediaBrowser;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.widget.SeekBar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.content.ContextCompat;
import androidx.databinding.DataBindingUtil;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.musicc.R;
import com.example.musicc.databinding.SongListActivityBinding;
import com.example.musicc.iteration.service.MusicPlayerService;
import com.example.musicc.iteration.view.adapter.OnViewHolderClickListener;
import com.example.musicc.iteration.view.adapter.SongListAdapter;
import com.example.musicc.iteration.viewmodel.SongListViewModel;
import com.google.android.material.bottomsheet.BottomSheetBehavior;

import java.util.List;

public class SongListActivity extends AppCompatActivity{
    private static final String TAG = "MyTag";

    private SongListActivityBinding binding;
    private SongListViewModel viewModel;
    private BottomSheetBehavior<CoordinatorLayout> miniPlayerBottomSheet;

    private Handler mHandler;
    private MutableLiveData<Boolean> isTrackingDuration = new MutableLiveData<>(false);
    long currentDuration;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = DataBindingUtil.setContentView(this, R.layout.song_list_activity);
        viewModel = new ViewModelProvider(this, new ViewModelProvider.AndroidViewModelFactory(getApplication())).get(SongListViewModel.class);
        miniPlayerBottomSheet = BottomSheetBehavior.from(binding.playerBottomSheet);
        mHandler = new Handler(Looper.getMainLooper());
        viewModel.addEventObserver(SongListActivity.this, viewModelEventsObserver);

        SongListAdapter songListAdapter = new SongListAdapter(viewModel.getMediaItemList().getValue());
        binding.songList.setLayoutManager(new LinearLayoutManager(SongListActivity.this));
        binding.songList.setAdapter(songListAdapter);
        viewModel.getMediaItemList().observe(SongListActivity.this, new Observer<List<MediaBrowser.MediaItem>>() {
            @Override
            public void onChanged(List<MediaBrowser.MediaItem> mediaItemList) {
                if(binding.songList.getAdapter() != null) ((SongListAdapter)binding.songList.getAdapter()).setMediaItemList(mediaItemList);
                if(binding.songListRefresh.isRefreshing()) binding.songListRefresh.setRefreshing(false);
            }
        });
        binding.songListRefresh.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                viewModel.forceLoadMediaItemList();
            }
        });

        miniPlayerBottomSheet.setPeekHeight(0);

        songListAdapter.setOnViewHolderClickListener(new OnViewHolderClickListener<SongListAdapter.ViewHolder>() {
            @Override
            public void onHolderClick(@NonNull SongListAdapter.ViewHolder viewHolder) {
                if(miniPlayerBottomSheet.getState() != BottomSheetBehavior.STATE_EXPANDED)
                    viewModel.playMediaItem(viewHolder.getMediaItem());
            }
        });


        isTrackingDuration.observe(this, new Observer<Boolean>() {
            @Override
            public void onChanged(Boolean aBoolean) {if(aBoolean) updateCurrentDuration();}
        });


        binding.miniPlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {viewModel.playPauseToggle();}
        });
        binding.play.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {viewModel.playPauseToggle();}
        });

        binding.skipToPrevious.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {viewModel.skipToPrevious();}
        });

        binding.skipToNext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {viewModel.skipToNext();}
        });

        binding.progress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            long duration;
            boolean wasTrackingDuration;
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    duration = (long) (((float) binding.progress.getProgress() / (float) binding.progress.getMax()) * viewModel.getTotalSongDuration());
                    binding.currentDuration.setText(getResources().getString(R.string.duration_Placeholder, (duration / 1000) / 60, (duration / 1000) % 60));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                wasTrackingDuration = isTrackingDuration.getValue() != null ? isTrackingDuration.getValue() : false;
                isTrackingDuration.setValue(false);
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                viewModel.seekTo(duration);
                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        isTrackingDuration.setValue(wasTrackingDuration);
                    }
                }, 100);
            }
        });

        viewModel.getIsLooping().observe(this, new Observer<Boolean>() {
            @Override
            public void onChanged(Boolean aBoolean) {
                if(aBoolean) binding.repeat.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(SongListActivity.this, R.color.colorAccent)));
                else binding.repeat.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(SongListActivity.this, android.R.color.darker_gray)));
            }
        });
        viewModel.getIsShuffling().observe(this, new Observer<Boolean>() {
            @Override
            public void onChanged(Boolean aBoolean) {
                if(aBoolean) binding.shuffle.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(SongListActivity.this, R.color.colorAccent)));
                else binding.shuffle.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(SongListActivity.this, android.R.color.darker_gray)));
            }
        });

        binding.repeat.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {viewModel.toggleLooping();}
        });
        binding.shuffle.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {viewModel.toggleShuffling();}
        });

        binding.miniPlayerControls.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {miniPlayerBottomSheet.setState(BottomSheetBehavior.STATE_EXPANDED);}
        });
        miniPlayerBottomSheet.addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(@NonNull View bottomSheet, int newState) {
                if(newState == BottomSheetBehavior.STATE_EXPANDED)
                    binding.songList.setClickable(false);
                else if(newState == BottomSheetBehavior.STATE_COLLAPSED)
                    binding.songList.setClickable(true);
            }

            @Override
            public void onSlide(@NonNull View bottomSheet, float slideOffset) {}
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        viewModel.onStart();
    }

    @Override
    protected void onStop() {
        super.onStop();
        viewModel.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        viewModel.removeEventObserver(viewModelEventsObserver);
    }

    @Override
    public void onBackPressed() {
        if(miniPlayerBottomSheet.getState() == BottomSheetBehavior.STATE_EXPANDED)
            miniPlayerBottomSheet.setState(BottomSheetBehavior.STATE_COLLAPSED);
        else
            super.onBackPressed();
    }

    public void onPlaybackStateChanged(){
        Log.d(TAG, "onPlaybackStateChanged() called");
        PlaybackState playbackState = viewModel.getPlaybackState();
        if(playbackState == null) return;
        Log.d(TAG, "playbackState: "+playbackState.getState());
        
        if(playbackState.getState() == PlaybackState.STATE_PLAYING){
            isTrackingDuration.setValue(true);
            binding.miniPlay.setImageResource(R.drawable.ic_pause);
            binding.play.setImageResource(R.drawable.ic_pause);
        }
        else if(playbackState.getState() == PlaybackState.STATE_PAUSED){
            isTrackingDuration.setValue(false);
            binding.miniPlay.setImageResource(R.drawable.ic_play);
            binding.play.setImageResource(R.drawable.ic_play);
        }
    }

    public void onMediaMetaDataChanged(){
        Log.d(TAG, "onMediaMetaDataChanged() called");
        MediaMetadata metadata = viewModel.getMediaMetaData();
        if(metadata == null) return;
        Log.d(TAG, "metadata: "+metadata);

        String title = metadata.getString(MusicPlayerService.METADATA_TITLE);
        Bitmap thumbnail = metadata.getBitmap(MusicPlayerService.METADATA_COVER_IMAGE);
        thumbnail = thumbnail == null ? BitmapFactory.decodeResource(getResources(), R.drawable.ic_music_note_2) : thumbnail;
        binding.thumbnail.setImageBitmap(thumbnail);
        binding.miniThumbnail.setImageBitmap(thumbnail);
        binding.miniSongTitle.setText(title);
        binding.songTitle.setText(title);
        binding.miniSongArtist.setText(metadata.getString(MusicPlayerService.METADATA_ARTIST));
        binding.totalDuration.setText(getResources().getString(R.string.duration_Placeholder, (metadata.getLong(MusicPlayerService.METADATA_DURATION)/1000)/60, (metadata.getLong(MusicPlayerService.METADATA_DURATION)/1000)%60));
        miniPlayerBottomSheet.setPeekHeight(Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, getResources().getDimension(R.dimen.miniPlayerControlHeight), getResources().getDisplayMetrics())));
    }


    private void updateCurrentDuration() {
        if (isTrackingDuration.getValue() != null && isTrackingDuration.getValue()) {
            currentDuration = viewModel.getCurrentMediaPlayerPosition();
            binding.currentDuration.setText(getResources().getString(R.string.duration_Placeholder, (currentDuration / 1000) / 60, (currentDuration / 1000) % 60));
            float progress = currentDuration != 0 && viewModel.getTotalSongDuration() != 0 ? (float)currentDuration / (float)viewModel.getTotalSongDuration() : 0;
            binding.progress.setProgress((int)(progress * binding.progress.getMax()));
            binding.miniProgress.setProgress((int)(progress * binding.miniProgress.getMax()));
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    updateCurrentDuration();
                }
            }, 500);
        }
    }


    private Observer<SongListViewModel.Events> viewModelEventsObserver = new Observer<SongListViewModel.Events>(){
        @Override
        public void onChanged(@Nullable SongListViewModel.Events events) {
            if(events == null) return;

            if(events.equals(SongListViewModel.Events.PlaybackStateChanged)) onPlaybackStateChanged();
            else if(events.equals(SongListViewModel.Events.MediaMetaDataChanged)) onMediaMetaDataChanged();
        }
    };
}