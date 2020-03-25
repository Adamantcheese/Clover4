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
package com.github.adamantcheese.chan.ui.cell;

import android.content.Context;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.cardview.widget.CardView;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.PostImage;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.core.site.sites.chan4.Chan4PagesRequest;
import com.github.adamantcheese.chan.ui.layout.FixedRatioLinearLayout;
import com.github.adamantcheese.chan.ui.text.FastTextView;
import com.github.adamantcheese.chan.ui.theme.Theme;
import com.github.adamantcheese.chan.ui.theme.ThemeHelper;
import com.github.adamantcheese.chan.ui.view.FloatingMenu;
import com.github.adamantcheese.chan.ui.view.FloatingMenuItem;
import com.github.adamantcheese.chan.ui.view.PostImageThumbnailView;
import com.github.adamantcheese.chan.ui.view.ThumbnailView;

import java.util.ArrayList;
import java.util.List;

import static com.github.adamantcheese.chan.ui.adapter.PostsFilter.Order.isNotBumpOrder;
import static com.github.adamantcheese.chan.utils.AndroidUtils.dp;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getString;
import static com.github.adamantcheese.chan.utils.AndroidUtils.setRoundItemBackground;

public class CardPostCell
        extends CardView
        implements PostCellInterface, View.OnClickListener {
    private static final int COMMENT_MAX_LENGTH = 200;

    private boolean bound;
    private Post post;
    private Loadable loadable;
    private PostCellInterface.PostCellCallback callback;
    private boolean compact = false;

    private PostImageThumbnailView thumbView;
    private TextView title;
    private FastTextView comment;
    private TextView replies;
    private ImageView options;
    private View filterMatchColor;

    public CardPostCell(Context context) {
        super(context);
    }

    public CardPostCell(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public CardPostCell(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        FixedRatioLinearLayout content = findViewById(R.id.card_content);
        content.setRatio(9f / 18f);
        thumbView = findViewById(R.id.thumbnail);
        thumbView.setRatio(16f / 13f);
        thumbView.setOnClickListener(this);
        title = findViewById(R.id.title);
        comment = findViewById(R.id.comment);
        replies = findViewById(R.id.replies);
        options = findViewById(R.id.options);
        setRoundItemBackground(options);
        filterMatchColor = findViewById(R.id.filter_match_color);

        setOnClickListener(this);

        setCompact(compact);

        options.setOnClickListener(v -> {
            List<FloatingMenuItem> items = new ArrayList<>();
            List<List<FloatingMenuItem>> extraItems = new ArrayList<>();
            extraItems.add(new ArrayList<>());
            extraItems.add(new ArrayList<>());
            List<Object> extraOptions = callback.onPopulatePostOptions(post, items, extraItems);
            showOptions(v, items, extraItems, extraOptions);
        });
    }

    private void showOptions(
            View anchor, List<FloatingMenuItem> items, List<List<FloatingMenuItem>> extraItems, List<Object> extraOptions
    ) {
        FloatingMenu menu = new FloatingMenu(getContext(), anchor, items);
        menu.setCallback(new FloatingMenu.FloatingMenuCallback() {
            @Override
            public void onFloatingMenuItemClicked(FloatingMenu menu, FloatingMenuItem item) {
                if (extraItems != null && extraOptions != null) {
                    if (item.getId() == extraOptions.get(0)) {
                        showOptions(anchor, extraItems.get(0), null, null);
                    } else if (item.getId() == extraOptions.get(1)) {
                        showOptions(anchor, extraItems.get(1), null, null);
                    }
                }
                callback.onPostOptionClicked(post, item.getId(), false);
            }

            @Override
            public void onFloatingMenuDismissed(FloatingMenu menu) {
            }
        });
        menu.show();
    }

    @Override
    public void onClick(View v) {
        if (v == thumbView) {
            callback.onThumbnailClicked(post.image(), thumbView);
        } else if (v == this) {
            callback.onPostClicked(post);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        if (post != null && bound) {
            thumbView.setPostImage(loadable, null, false, 0, 0);
            bound = false;
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        if (post != null && !bound) {
            bindPost(post);
        }
    }

    public void setPost(
            Loadable loadable,
            final Post post,
            PostCellInterface.PostCellCallback callback,
            boolean inPopup,
            boolean highlighted,
            boolean selected,
            int markedNo,
            boolean showDivider,
            ChanSettings.PostViewMode postViewMode,
            boolean compact,
            Theme theme
    ) {
        if (this.post == post) {
            return;
        }

        if (this.post != null && bound) {
            bound = false;
            this.post = null;
        }

        this.loadable = loadable;
        this.post = post;
        this.callback = callback;

        bindPost(post);

        if (this.compact != compact) {
            this.compact = compact;
            setCompact(compact);
        }
    }

    public Post getPost() {
        return post;
    }

    public ThumbnailView getThumbnailView(PostImage postImage) {
        return thumbView;
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    private void bindPost(Post post) {
        bound = true;

        if (post.image() != null && !ChanSettings.textOnly.get()) {
            thumbView.setVisibility(VISIBLE);
            thumbView.setPostImage(
                    loadable,
                    post.image(),
                    true,
                    ChanSettings.autoLoadThreadImages.get()
                            ? Math.max(500, thumbView.getWidth())
                            : thumbView.getWidth(),
                    ChanSettings.autoLoadThreadImages.get()
                            ? Math.max(500, thumbView.getHeight())
                            : thumbView.getHeight()
            );
        } else {
            thumbView.setVisibility(GONE);
            thumbView.setPostImage(loadable, null, false, 0, 0);
        }

        if (post.filterHighlightedColor != 0) {
            filterMatchColor.setVisibility(VISIBLE);
            filterMatchColor.setBackgroundColor(post.filterHighlightedColor);
        } else {
            filterMatchColor.setVisibility(GONE);
        }

        if (!TextUtils.isEmpty(post.subjectSpan)) {
            title.setVisibility(VISIBLE);
            title.setText(post.subjectSpan);
        } else {
            title.setVisibility(GONE);
            title.setText(null);
        }

        CharSequence commentText;
        if (post.comment.length() > COMMENT_MAX_LENGTH) {
            commentText = post.comment.subSequence(0, COMMENT_MAX_LENGTH);
        } else {
            commentText = post.comment;
        }

        comment.setText(commentText);
        comment.setTextColor(ThemeHelper.getTheme().textPrimary);

        String status = getString(R.string.card_stats, post.getReplies(), post.getImagesCount());
        if (!ChanSettings.neverShowPages.get()) {
            Chan4PagesRequest.Page p = callback.getPage(post);
            if (p != null && isNotBumpOrder(ChanSettings.boardOrder.get())) {
                status += " Pg " + p.page;
            }
        }

        replies.setText(status);
    }

    private void setCompact(boolean compact) {
        int textReduction = compact ? -2 : 0;
        int textSizeSp = Integer.parseInt(ChanSettings.fontSize.get()) + textReduction;
        title.setTextSize(textSizeSp);
        comment.setTextSize(textSizeSp);
        replies.setTextSize(textSizeSp);

        int p = compact ? dp(3) : dp(8);

        // Same as the layout.
        title.setPadding(p, p, p, 0);
        comment.setPadding(p, p, p, 0);
        replies.setPadding(p, p / 2, p, p);

        int optionsPadding = compact ? 0 : dp(5);
        options.setPadding(0, optionsPadding, optionsPadding, 0);
    }
}
