package com.segovia.tv.ui

import android.app.Activity
import android.graphics.Color
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.LazyHeaders

object ImageLoader {

    fun load(
        activity: Activity,
        url: String,
        image: ImageView,
        token: String? = null
    ) {
        if (activity.isFinishing || activity.isDestroyed) return

        if (url.isBlank()) {
            image.setImageDrawable(
                UiUtils.rounded(
                    Color.parseColor("#111827"),
                    8f
                )
            )
            return
        }

        val manager = Glide.with(activity)

        val request = if (
            url.contains("googleapis.com") &&
            !token.isNullOrEmpty()
        ) {
            manager.load(
                GlideUrl(
                    url,
                    LazyHeaders.Builder()
                        .addHeader(
                            "Authorization",
                            "Bearer $token"
                        )
                        .build()
                )
            )
        } else {
            manager.load(url)
        }

        request
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .placeholder(
                UiUtils.rounded(
                    Color.parseColor("#111827"),
                    8f
                )
            )
            .error(
                UiUtils.rounded(
                    Color.parseColor("#1F2937"),
                    8f
                )
            )
            .into(image)
    }
}