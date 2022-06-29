package com.example.musicc.iteration.view.adapter;

import android.media.browse.MediaBrowser;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.databinding.DataBindingUtil;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.example.musicc.R;
import com.example.musicc.databinding.SongListItemBinding;
import com.example.musicc.iteration.service.MusicPlayerService;

import java.util.List;

public class SongListAdapter extends RecyclerView.Adapter<SongListAdapter.ViewHolder> {

    private List<MediaBrowser.MediaItem> mediaItemList;
    private OnViewHolderClickListener<SongListAdapter.ViewHolder> holderClickListener;

    public SongListAdapter(List<MediaBrowser.MediaItem> mediaItemList){
        this.mediaItemList = mediaItemList;
    }

    public void setMediaItemList(List<MediaBrowser.MediaItem> mediaItemList){
        if(this.mediaItemList == null || mediaItemList == null) {
            this.mediaItemList = mediaItemList;
            notifyDataSetChanged();
        } else {
            DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new DiffUtilCallback(this.mediaItemList, mediaItemList));
            this.mediaItemList = mediaItemList;
            diffResult.dispatchUpdatesTo(this);
        }
    }

    public void setOnViewHolderClickListener(@NonNull OnViewHolderClickListener<SongListAdapter.ViewHolder> holderClickListener){
        this.holderClickListener = holderClickListener;
    }

    public class ViewHolder extends RecyclerView.ViewHolder{
        private SongListItemBinding binding;
        private MediaBrowser.MediaItem mediaItem;

        private ViewHolder(@NonNull SongListItemBinding binding){
            super(binding.getRoot());
            this.binding = binding;

            binding.getRoot().setOnClickListener(new View.OnClickListener(){
                @Override
                public void onClick(View v) {
                    if(holderClickListener != null) holderClickListener.onHolderClick(SongListAdapter.ViewHolder.this);
                }
            });
        }

        public void setMediaItem(@NonNull MediaBrowser.MediaItem mediaItem){
            this.mediaItem = mediaItem;
            binding.songTitle.setText(mediaItem.getDescription().getTitle());
            Bundle extras = mediaItem.getDescription().getExtras();
            binding.songArtist.setText(extras != null ? extras.getString(MusicPlayerService.METADATA_ARTIST, this.itemView.getResources().getString(R.string.UNKNOWN_ARTIST)) : this.itemView.getResources().getString(R.string.UNKNOWN_ARTIST));
        }

        public MediaBrowser.MediaItem getMediaItem(){
            return this.mediaItem;
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new SongListAdapter.ViewHolder((SongListItemBinding)DataBindingUtil.inflate(LayoutInflater.from(parent.getContext()), R.layout.song_list_item, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.setMediaItem(mediaItemList.get(position));
    }

    @Override
    public int getItemCount() {
        return mediaItemList != null ? mediaItemList.size() : 0;
    }

    private static class DiffUtilCallback extends DiffUtil.Callback{
        private List<MediaBrowser.MediaItem> oldList, newList;

        private DiffUtilCallback(@NonNull List<MediaBrowser.MediaItem> oldList, @NonNull List<MediaBrowser.MediaItem> newList){
            this.oldList = oldList;
            this.newList = newList;
        }

        @Override
        public int getOldListSize() {
            return oldList.size();
        }

        @Override
        public int getNewListSize() {
            return newList.size();
        }

        @Override
        public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
            MediaBrowser.MediaItem oldItem = oldList.get(oldItemPosition), newItem = newList.get(newItemPosition);
            return oldItem.getMediaId() != null && oldItem.getMediaId().equals(newItem.getMediaId());
        }

        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            MediaBrowser.MediaItem oldItem = oldList.get(oldItemPosition), newItem = newList.get(newItemPosition);

            if(oldItem.getDescription().getTitle() == null || !oldItem.getDescription().getTitle().equals(newItem.getDescription().getTitle()))
                return false;
            else if(oldItem.getDescription().getExtras() == null || newItem.getDescription().getExtras() == null || !oldItem.getDescription().getExtras().getString(MusicPlayerService.METADATA_ARTIST, "").equals(newItem.getDescription().getExtras().getString(MusicPlayerService.METADATA_ARTIST, "")))
                return false;
            else
                return (oldItem.getDescription().getExtras() != null && newItem.getDescription().getExtras() != null && oldItem.getDescription().getExtras().getString(MusicPlayerService.METADATA_ALBUM, "").equals(newItem.getDescription().getExtras().getString(MusicPlayerService.METADATA_ALBUM, "")));

        }
    }
}
