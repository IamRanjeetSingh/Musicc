package com.example.musicc.model;

import android.content.ContentUris;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.media.browse.MediaBrowser;
import android.net.Uri;
import android.provider.MediaStore;

import androidx.annotation.NonNull;

public class Song{
    private static final Uri CONTENT_URI = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
    private String id;
    private String title;
    private Bitmap thumbnail;

    public Song(@NonNull String id, @NonNull String title, @NonNull byte[] thumbnail){
        this.id = id;
        this.title = title;
        BitmapFactory.Options bitmapOptions = new BitmapFactory.Options();
        bitmapOptions.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(thumbnail, 0, thumbnail.length, bitmapOptions);
        bitmapOptions.inJustDecodeBounds = false;
        if(bitmapOptions.outHeight > 50 || bitmapOptions.outWidth > 50) {
            bitmapOptions.inSampleSize = bitmapOptions.outHeight > bitmapOptions.outWidth ? bitmapOptions.outHeight/50 : bitmapOptions.outWidth/50;
            this.thumbnail = BitmapFactory.decodeByteArray(thumbnail, 0, thumbnail.length, bitmapOptions);
        } else
            this.thumbnail = BitmapFactory.decodeByteArray(thumbnail, 0, thumbnail.length);
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public Bitmap getThumbnail() {
        return thumbnail;
    }

    public Bitmap getCoverImage(@NonNull Context context){
        MediaMetadataRetriever mdr = new MediaMetadataRetriever();
        mdr.setDataSource(context, ContentUris.withAppendedId(CONTENT_URI, Long.parseLong(id)));
        byte[] coverImage = mdr.getEmbeddedPicture();
        mdr.release();
        return BitmapFactory.decodeByteArray(coverImage, 0, coverImage.length);
    }

    public long getDuration(@NonNull Context context){
        MediaMetadataRetriever mdr = new MediaMetadataRetriever();
        mdr.setDataSource(context, ContentUris.withAppendedId(CONTENT_URI, Long.parseLong(id)));
        String duration = mdr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
        mdr.release();
        return duration != null ? Long.parseLong(duration) : 0L;
    }
}
