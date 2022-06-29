package com.example.musicc.iteration.view.adapter;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

public interface OnViewHolderClickListener<T extends RecyclerView.ViewHolder> {
    void onHolderClick(@NonNull T viewHolder);
}
