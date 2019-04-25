/*
 * Clover - 4chan browser https://github.com/Floens/Clover/
 * Copyright (C) 2014  Floens
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
package org.floens.chan.core.model.export;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

public class ExportedFilter {
    @SerializedName("enabled")
    private boolean enabled;
    @SerializedName("type")
    private int type;
    @SerializedName("pattern")
    @Nullable
    private String pattern;
    @SerializedName("all_boards")
    private boolean allBoards;
    @SerializedName("boards")
    @Nullable
    private String boards;
    @SerializedName("action")
    private int action;
    @SerializedName("color")
    private int color;
    @SerializedName("apply_to_replies")
    private boolean applyToReplies;

    public ExportedFilter(
            boolean enabled,
            int type,
            @NonNull String pattern,
            boolean allBoards,
            @NonNull String boards,
            int action,
            int color,
            boolean applyToReplies
    ) {
        this.enabled = enabled;
        this.type = type;
        this.pattern = pattern;
        this.allBoards = allBoards;
        this.boards = boards;
        this.action = action;
        this.color = color;
        this.applyToReplies = applyToReplies;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getType() {
        return type;
    }

    @Nullable
    public String getPattern() {
        return pattern;
    }

    public boolean isAllBoards() {
        return allBoards;
    }

    @Nullable
    public String getBoards() {
        return boards;
    }

    public int getAction() {
        return action;
    }

    public int getColor() {
        return color;
    }

    public boolean getApplyToReplies() {
        return applyToReplies;
    }
}
