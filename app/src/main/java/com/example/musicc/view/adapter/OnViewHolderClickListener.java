package com.example.musicc.view.adapter;

import androidx.recyclerview.widget.RecyclerView;

public interface OnViewHolderClickListener<T extends RecyclerView.ViewHolder> {
    void onViewHolderClick(T viewHolder);
}
