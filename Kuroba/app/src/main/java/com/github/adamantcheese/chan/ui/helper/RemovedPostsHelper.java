package com.github.adamantcheese.chan.ui.helper;

import android.annotation.SuppressLint;
import android.content.Context;

import androidx.annotation.Nullable;

import com.github.adamantcheese.chan.controller.Controller;
import com.github.adamantcheese.chan.core.database.DatabaseManager;
import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.orm.PostHide;
import com.github.adamantcheese.chan.core.presenter.ThreadPresenter;
import com.github.adamantcheese.chan.ui.controller.RemovedPostsController;
import com.github.adamantcheese.chan.utils.AndroidUtils;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RemovedPostsHelper {
    private final String TAG = "RemovedPostsHelper";

    private DatabaseManager databaseManager;
    private Context context;
    private ThreadPresenter presenter;
    private RemovedPostsCallbacks callbacks;
    private @Nullable
    RemovedPostsController controller;

    public RemovedPostsHelper(
            Context context,
            DatabaseManager databaseManager,
            ThreadPresenter presenter,
            RemovedPostsCallbacks callbacks
    ) {
        this.context = context;
        this.databaseManager = databaseManager;
        this.presenter = presenter;
        this.callbacks = callbacks;
    }

    public void showPosts(List<Post> threadPosts, int threadNo) {
        databaseManager.runTask(() -> {
            List<Post> removedPosts = getRemovedPosts(threadPosts, threadNo);

            if (removedPosts.isEmpty()) {
                callbacks.noRemovedPostsFoundForThisThread();
                return null;
            }

            Collections.sort(removedPosts, (o1, o2) -> Integer.compare(o1.no, o2.no));

            AndroidUtils.runOnUiThread(() -> {
                present();

                // controller should not be null here, thus no null check
                controller.showRemovePosts(removedPosts);
            });

            return null;
        });
    }

    private List<Post> getRemovedPosts(List<Post> threadPosts, int threadNo) throws SQLException {
        List<PostHide> hiddenPosts = databaseManager.getDatabaseHideManager()
                .getRemovedPostsWithThreadNo(threadNo);
        List<Post> removedPosts = new ArrayList<>();

        @SuppressLint("UseSparseArrays")
        Map<Integer, PostHide> fastLookupMap = new HashMap<>();

        for (PostHide postHide : hiddenPosts) {
            fastLookupMap.put(postHide.no, postHide);
        }

        for (Post post : threadPosts) {
            if (fastLookupMap.containsKey(post.no)) {
                removedPosts.add(post);
            }
        }

        return removedPosts;
    }

    public void pop() {
        dismiss();
    }

    private void present() {
        if (controller == null) {
            controller = new RemovedPostsController(context, this);
            callbacks.presentRemovedPostsController(controller);
        }
    }

    private void dismiss() {
        if (controller != null) {
            controller.stopPresenting();
            controller = null;
        }
    }

    public void onRestoreClicked(List<Integer> selectedPosts) {
        presenter.onRestoreRemovedPostsClicked(selectedPosts);

        dismiss();
    }

    public interface RemovedPostsCallbacks {
        void presentRemovedPostsController(Controller controller);

        void noRemovedPostsFoundForThisThread();
    }
}
