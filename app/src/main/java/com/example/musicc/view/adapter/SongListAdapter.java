package com.example.musicc.view.adapter;

import android.media.browse.MediaBrowser;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.example.musicc.R;
import com.example.musicc.databinding.SongListItemBinding;

import java.util.List;

public class SongListAdapter extends RecyclerView.Adapter<SongListAdapter.ViewHolder> {

    private List<MediaBrowser.MediaItem> mediaItemList;
    private OnViewHolderClickListener<SongListAdapter.ViewHolder> holderClickListener;

    public class ViewHolder extends RecyclerView.ViewHolder{
        private SongListItemBinding binding;
        private String mediaId;

        private ViewHolder(@NonNull SongListItemBinding binding){
            super(binding.getRoot());
            this.binding = binding;

            binding.getRoot().setOnClickListener(new View.OnClickListener(){
                @Override
                public void onClick(View v) {
                    if(holderClickListener != null)
                        holderClickListener.onViewHolderClick(SongListAdapter.ViewHolder.this);
                }
            });
        }

        public String getMediaId(){
            return mediaId;
        }
    }

    public void setOnViewHolderClickListener(@NonNull OnViewHolderClickListener<SongListAdapter.ViewHolder> holderClickListener){
        this.holderClickListener = holderClickListener;
    }

    public void setMediaItemList(@NonNull List<MediaBrowser.MediaItem> mediaItemList){
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new DiffUtilCallback(this.mediaItemList, mediaItemList));
        this.mediaItemList = mediaItemList;
        diffResult.dispatchUpdatesTo(this);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new SongListAdapter.ViewHolder((SongListItemBinding) DataBindingUtil.inflate(LayoutInflater.from(parent.getContext()), R.layout.song_list_item, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.mediaId = mediaItemList.get(position).getMediaId();
        holder.binding.songTitle.setText(mediaItemList.get(position).getDescription().getTitle());
        if(mediaItemList.get(position).getDescription().getIconBitmap() != null)
            holder.binding.thumbnail.setImageBitmap(mediaItemList.get(position).getDescription().getIconBitmap());
    }

    @Override
    public int getItemCount() {
        return mediaItemList != null ? mediaItemList.size() : 0;
    }

    private static class DiffUtilCallback extends DiffUtil.Callback{
        @Nullable private List<MediaBrowser.MediaItem> oldMediaItemList;
        @Nullable private List<MediaBrowser.MediaItem> newMediaItemList;

        private DiffUtilCallback(@Nullable List<MediaBrowser.MediaItem> oldMediaItemList, @Nullable List<MediaBrowser.MediaItem> newMediaItemList){
            this.oldMediaItemList = oldMediaItemList;
            this.newMediaItemList = newMediaItemList;
        }

        @Override
        public int getOldListSize() {
            return oldMediaItemList != null ? oldMediaItemList.size() : 0;
        }

        @Override
        public int getNewListSize() {
            return newMediaItemList != null ? newMediaItemList.size() : 0;
        }

        @Override
        public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
            if(oldMediaItemList == null || newMediaItemList == null)
                return false;
            String oldMediaId = oldMediaItemList.get(oldItemPosition).getMediaId();
            String newMediaId = newMediaItemList.get(newItemPosition).getMediaId();

            if(oldMediaId == null || newMediaId == null)
                return false;
            return oldMediaId.equals(newMediaId);
        }

        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            if(oldMediaItemList == null || newMediaItemList == null)
                return false;
            MediaBrowser.MediaItem oldMediaItem = oldMediaItemList.get(oldItemPosition);
            MediaBrowser.MediaItem newMediaItem = newMediaItemList.get(newItemPosition);

            if(oldMediaItem.getDescription().getTitle() == null || !oldMediaItem.getDescription().getTitle().equals(newMediaItem.getDescription().getTitle()))
                return false;
            else
                return true;
        }
    }
}
