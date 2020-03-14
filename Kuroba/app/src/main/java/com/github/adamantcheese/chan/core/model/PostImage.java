/*
 * Kuroba - *chan browser https://github.com/Adamantcheese/Kuroba/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.github.adamantcheese.chan.core.model;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.utils.StringUtils;

import okhttp3.HttpUrl;

import static com.github.adamantcheese.chan.core.model.PostImage.Type.GIF;
import static com.github.adamantcheese.chan.core.model.PostImage.Type.MOVIE;
import static com.github.adamantcheese.chan.core.model.PostImage.Type.PDF;
import static com.github.adamantcheese.chan.core.model.PostImage.Type.STATIC;

public class PostImage {
    public enum Type {
        STATIC,
        GIF,
        MOVIE,
        PDF,
        SWF
    }

    public final String serverFilename;
    public final HttpUrl thumbnailUrl;
    public final HttpUrl spoilerThumbnailUrl;
    @Nullable
    public final HttpUrl imageUrl;
    public final String filename;
    public final String extension;
    public final int imageWidth;
    public final int imageHeight;
    public final boolean spoiler;
    public final boolean isInlined;
    @Nullable
    public final String fileHash;
    public final Type type;
    private long size;

    private PostImage(Builder builder) {
        this.serverFilename = builder.serverFilename;
        this.thumbnailUrl = builder.thumbnailUrl;
        this.spoilerThumbnailUrl = builder.spoilerThumbnailUrl;
        this.imageUrl = builder.imageUrl;
        this.filename = builder.filename;
        this.extension = builder.extension;
        this.imageWidth = builder.imageWidth;
        this.imageHeight = builder.imageHeight;
        this.spoiler = builder.spoiler;
        this.isInlined = builder.isInlined;
        this.size = builder.size;
        this.fileHash = builder.fileHash;

        switch (extension) {
            case "gif":
                type = GIF;
                break;
            case "webm":
            case "mp4":
            case "mp3":
            case "m4a":
            case "ogg":
            case "flac":
                type = MOVIE;
                break;
            case "pdf":
                type = PDF;
                break;
            case "swf":
                type = Type.SWF;
                break;
            default:
                type = STATIC;
                break;
        }
    }

    public boolean equalUrl(PostImage other) {
        if (imageUrl == null || other.imageUrl == null) {
            return serverFilename.equals(other.serverFilename);
        }

        return imageUrl.equals(other.imageUrl);
    }

    public HttpUrl getThumbnailUrl() {
        if (!spoiler) {
            return thumbnailUrl;
        } else {
            return spoilerThumbnailUrl;
        }
    }

    public synchronized long getSize() {
        return size;
    }

    public synchronized void setSize(long size) {
        this.size = size;
    }

    public static final class Builder {
        private String serverFilename;
        private HttpUrl thumbnailUrl;
        private HttpUrl spoilerThumbnailUrl;
        private HttpUrl imageUrl;
        private String filename;
        private String extension;
        private int imageWidth;
        private int imageHeight;
        private boolean spoiler;
        private boolean isInlined = false;
        private long size;
        @Nullable
        private String fileHash;

        public Builder() {
        }

        public Builder serverFilename(String serverFilename) {
            this.serverFilename = serverFilename;
            return this;
        }

        public Builder thumbnailUrl(HttpUrl thumbnailUrl) {
            this.thumbnailUrl = thumbnailUrl;
            return this;
        }

        public Builder spoilerThumbnailUrl(HttpUrl spoilerThumbnailUrl) {
            this.spoilerThumbnailUrl = spoilerThumbnailUrl;
            return this;
        }

        public Builder imageUrl(@NonNull HttpUrl imageUrl) {
            if (imageUrl == null) {
                throw new NullPointerException("imageUrl must not be null!");
            }

            this.imageUrl = HttpUrl.parse(imageUrl.toString().replace("http://", "https://"));
            return this;
        }

        public Builder filename(String filename) {
            this.filename = filename;
            return this;
        }

        public Builder extension(String extension) {
            this.extension = extension;
            return this;
        }

        public Builder imageWidth(int imageWidth) {
            this.imageWidth = imageWidth;
            return this;
        }

        public Builder imageHeight(int imageHeight) {
            this.imageHeight = imageHeight;
            return this;
        }

        public Builder spoiler(boolean spoiler) {
            this.spoiler = spoiler;
            return this;
        }

        public Builder isInlined(boolean inlined) {
            this.isInlined = inlined;
            return this;
        }

        public Builder size(long size) {
            this.size = size;
            return this;
        }

        public Builder fileHash(String fileHash) {
            if (!TextUtils.isEmpty(fileHash)) {
                this.fileHash = StringUtils.decodeBase64(fileHash);
            }

            return this;
        }

        public PostImage build() {
            if (ChanSettings.removeImageSpoilers.get()) {
                spoiler = false;
            }

            return new PostImage(this);
        }
    }
}
