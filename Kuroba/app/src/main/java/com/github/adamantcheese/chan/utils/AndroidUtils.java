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
package com.github.adamantcheese.chan.utils;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Application;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.Signature;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.ScaleAnimation;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.browser.customtabs.CustomTabsIntent;

import com.github.adamantcheese.chan.BuildConfig;
import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.StartActivity;
import com.github.adamantcheese.chan.core.di.component.activity.StartActivityComponent;
import com.github.adamantcheese.chan.ui.theme.ThemeHelper;
import com.google.android.material.snackbar.Snackbar;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AndroidUtils {
    private static final String TAG = "AndroidUtils";

    @SuppressLint("StaticFieldLeak")
    private static Application application;

    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static void init(Application application) {
        if (AndroidUtils.application == null) {
            AndroidUtils.application = application;
        }
    }

    public static Resources getRes() {
        return application.getResources();
    }

    public static Context getAppContext() {
        return application;
    }

    public static String getString(int res) {
        return getRes().getString(res);
    }

    public static CharSequence getApplicationLabel() {
        return application.getPackageManager().getApplicationLabel(application.getApplicationInfo());
    }

    public static SharedPreferences getPreferences() {
        return PreferenceManager.getDefaultSharedPreferences(application);
    }

    public static boolean getIsOfficial() {
        try {
            @SuppressLint("PackageManagerGetSignatures")
            Signature sig = application.getPackageManager().getPackageInfo(application.getPackageName(), PackageManager.GET_SIGNATURES).signatures[0];
            return BuildConfig.SIGNATURE.equals(Integer.toHexString(sig.toCharsString().hashCode()));
        } catch (Exception ignored) {
            return false;
        }
    }

    /**
     * Tries to open an app that can open the specified URL.<br>
     * If this app will open the link then show a chooser to the user without this app.<br>
     * Else allow the default logic to run with startActivity.
     *
     * @param link url to open
     */
    public static void openLink(String link) {
        PackageManager pm = application.getPackageManager();

        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(link));

        ComponentName resolvedActivity = intent.resolveActivity(pm);
        if (resolvedActivity == null) {
            openIntentFailed();
        } else {
            boolean thisAppIsDefault = resolvedActivity.getPackageName().equals(application.getPackageName());
            if (!thisAppIsDefault) {
                openIntent(intent);
            } else {
                // Get all intents that match, and filter out this app
                List<ResolveInfo> resolveInfos = pm.queryIntentActivities(intent, 0);
                List<Intent> filteredIntents = new ArrayList<>(resolveInfos.size());
                for (ResolveInfo info : resolveInfos) {
                    if (!info.activityInfo.packageName.equals(application.getPackageName())) {
                        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(link));
                        i.setPackage(info.activityInfo.packageName);
                        filteredIntents.add(i);
                    }
                }

                if (filteredIntents.size() > 0) {
                    // Create a chooser for the last app in the list, and add the rest with EXTRA_INITIAL_INTENTS that get placed above
                    Intent chooser = Intent.createChooser(filteredIntents.remove(filteredIntents.size() - 1), null);
                    chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, filteredIntents.toArray(new Intent[0]));
                    openIntent(chooser);
                } else {
                    openIntentFailed();
                }
            }
        }
    }

    public static void openLinkInBrowser(Activity activity, ThemeHelper themeHelper, String link) {
        // Hack that's sort of the same as openLink
        // The link won't be opened in a custom tab if this app is the default handler for that link.
        // Manually check if this app opens it instead of a custom tab, and use the logic of
        // openLink to avoid that and show a chooser instead.
        boolean openWithCustomTabs = true;
        Intent urlIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(link));
        PackageManager pm = application.getPackageManager();
        ComponentName resolvedActivity = urlIntent.resolveActivity(pm);
        if (resolvedActivity != null) {
            openWithCustomTabs = !resolvedActivity.getPackageName().equals(application.getPackageName());
        }

        if (openWithCustomTabs) {
            CustomTabsIntent tabsIntent = new CustomTabsIntent.Builder()
                    .setToolbarColor(themeHelper.getTheme().primaryColor.color)
                    .build();
            try {
                tabsIntent.launchUrl(activity, Uri.parse(link));
            } catch (ActivityNotFoundException e) {
                // Can't check it beforehand so catch the exception
                openIntentFailed();
            }
        } else {
            openLink(link);
        }
    }

    public static void shareLink(String link) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, link);
        Intent chooser = Intent.createChooser(intent, getRes().getString(R.string.action_share));
        openIntent(chooser);
    }

    public static void openIntent(Intent intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (intent.resolveActivity(application.getPackageManager()) != null) {
            application.startActivity(intent);
        } else {
            openIntentFailed();
        }
    }

    private static void openIntentFailed() {
        Toast.makeText(application, R.string.open_link_failed, Toast.LENGTH_LONG).show();
    }

    public static int getAttrColor(Context context, int attr) {
        TypedArray typedArray = context.getTheme().obtainStyledAttributes(new int[]{attr});
        int color = typedArray.getColor(0, 0);
        typedArray.recycle();
        return color;
    }

    public static Drawable getAttrDrawable(Context context, int attr) {
        TypedArray typedArray = context.obtainStyledAttributes(new int[]{attr});
        Drawable drawable = typedArray.getDrawable(0);
        typedArray.recycle();
        return drawable;
    }

    public static boolean isTablet(Context context) {
        return context.getResources().getBoolean(R.bool.is_tablet);
    }

    public static int getDimen(Context context, int dimen) {
        return context.getResources().getDimensionPixelSize(dimen);
    }

    public static File getAppDir() {
        return application.getFilesDir().getParentFile();
    }

    public static int dp(float dp) {
        return (int) (dp * getRes().getDisplayMetrics().density);
    }

    public static int sp(float sp) {
        return (int) (sp * getRes().getDisplayMetrics().scaledDensity);
    }

    /**
     * Causes the runnable to be added to the message queue. The runnable will
     * be run on the ui thread.
     */
    public static void runOnUiThread(Runnable runnable) {
        mainHandler.post(runnable);
    }

    public static void runOnUiThread(Runnable runnable, long delay) {
        mainHandler.postDelayed(runnable, delay);
    }

    public static void requestKeyboardFocus(Dialog dialog, final View view) {
        view.requestFocus();
        dialog.setOnShowListener(dialog1 -> requestKeyboardFocus(view));
    }

    public static void requestKeyboardFocus(final View view) {
        InputMethodManager inputManager = (InputMethodManager) view.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        inputManager.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT);
    }

    public static void hideKeyboard(View view) {
        if (view != null) {
            InputMethodManager inputManager = (InputMethodManager) view.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            inputManager.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    public static void requestViewAndKeyboardFocus(View view) {
        view.setFocusable(false);
        view.setFocusableInTouchMode(true);
        if (view.requestFocus()) {
            InputMethodManager inputManager =
                    (InputMethodManager) view.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            inputManager.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    public static String getReadableFileSize(long bytes, boolean si) {
        long unit = si ? 1000 : 1024;
        if (bytes < unit)
            return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(unit));
        String pre = (si ? "kMGTPE" : "KMGTPE").charAt(exp - 1) + (si ? "" : "i");
        return String.format(Locale.US, "%.1f %sB", bytes / Math.pow(unit, exp), pre);
    }

    public interface OnMeasuredCallback {
        /**
         * Called when the layout is done.
         *
         * @param view same view as the argument.
         * @return true to continue with rendering, false to cancel and redo the layout.
         */
        boolean onMeasured(View view);
    }

    /**
     * Waits for a measure. Calls callback immediately if the view width and height are more than 0.
     * Otherwise it registers an onpredrawlistener.
     * <b>Warning: the view you give must be attached to the view root!</b>
     */
    public static void waitForMeasure(final View view, final OnMeasuredCallback callback) {
        if (view.getWindowToken() == null) {
            // If you call getViewTreeObserver on a view when it's not attached to a window will result in the creation of a temporarily viewtreeobserver.
            // This is almost always not what you want.
            throw new IllegalArgumentException("The view given to waitForMeasure is not attached to the window and does not have a ViewTreeObserver.");
        }

        waitForLayoutInternal(true, view.getViewTreeObserver(), view, callback);
    }

    /**
     * Always registers an onpredrawlistener.
     * <b>Warning: the view you give must be attached to the view root!</b>
     */
    public static void waitForLayout(final View view, final OnMeasuredCallback callback) {
        if (view.getWindowToken() == null) {
            // See comment above
            throw new IllegalArgumentException("The view given to waitForLayout is not attached to the window and does not have a ViewTreeObserver.");
        }

        waitForLayoutInternal(false, view.getViewTreeObserver(), view, callback);
    }

    /**
     * Always registers an onpredrawlistener. The given ViewTreeObserver will be used.
     */
    public static void waitForLayout(final ViewTreeObserver viewTreeObserver, final View view, final OnMeasuredCallback callback) {
        waitForLayoutInternal(false, viewTreeObserver, view, callback);
    }

    private static void waitForLayoutInternal(boolean returnIfNotZero, final ViewTreeObserver viewTreeObserver, final View view, final OnMeasuredCallback callback) {
        int width = view.getWidth();
        int height = view.getHeight();

        if (returnIfNotZero && width > 0 && height > 0) {
            callback.onMeasured(view);
        } else {
            viewTreeObserver.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                @Override
                public boolean onPreDraw() {
                    ViewTreeObserver usingViewTreeObserver = viewTreeObserver;
                    if (viewTreeObserver != view.getViewTreeObserver()) {
                        Logger.e(TAG, "view.getViewTreeObserver() is another viewtreeobserver! replacing with the new one");
                        usingViewTreeObserver = view.getViewTreeObserver();
                    }

                    if (usingViewTreeObserver.isAlive()) {
                        usingViewTreeObserver.removeOnPreDrawListener(this);
                    } else {
                        Logger.e(TAG, "ViewTreeObserver not alive, could not remove onPreDrawListener! This will probably not end well");
                    }

                    boolean ret;
                    try {
                        ret = callback.onMeasured(view);
                    } catch (Exception e) {
                        Logger.i(TAG, "Exception in onMeasured", e);
                        throw e;
                    }

                    if (!ret) {
                        Logger.d(TAG, "waitForLayout requested a re-layout by returning false");
                    }

                    return ret;
                }
            });
        }
    }

    public static void setRoundItemBackground(View view) {
        view.setBackgroundResource(R.drawable.item_background);
    }

    public static List<View> findViewsById(ViewGroup root, int id) {
        List<View> views = new ArrayList<>();
        int childCount = root.getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = root.getChildAt(i);
            if (child instanceof ViewGroup) {
                views.addAll(findViewsById((ViewGroup) child, id));
            }

            if (child.getId() == id) {
                views.add(child);
            }
        }

        return views;
    }

    public static boolean removeFromParentView(View view) {
        if (view.getParent() instanceof ViewGroup && ((ViewGroup) view.getParent()).indexOfChild(view) >= 0) {
            ((ViewGroup) view.getParent()).removeView(view);
            return true;
        } else {
            return false;
        }
    }

    public static void fixSnackbarText(Context context, Snackbar snackbar) {
        ((TextView) snackbar.getView().findViewById(R.id.snackbar_text)).setTextColor(Color.WHITE);
        snackbar.setActionTextColor(getAttrColor(context, R.attr.colorAccent));
    }

    public static boolean isConnected(int type) {
        ConnectivityManager connectivityManager = (ConnectivityManager)
                application.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connectivityManager.getNetworkInfo(type);
        return networkInfo != null && networkInfo.isConnected();
    }

    public static void animateStatusBar(Window window, boolean in, final int originalColor, int duration) {
        ValueAnimator statusBar = ValueAnimator.ofFloat(in ? 0f : 0.5f, in ? 0.5f : 0f);
        statusBar.addUpdateListener(animation -> {
            float progress = (float) animation.getAnimatedValue();
            if (progress == 0f) {
                window.setStatusBarColor(originalColor);
            } else {
                int r = (int) ((1f - progress) * Color.red(originalColor));
                int g = (int) ((1f - progress) * Color.green(originalColor));
                int b = (int) ((1f - progress) * Color.blue(originalColor));
                window.setStatusBarColor(Color.argb(255, r, g, b));
            }
        });
        statusBar.setDuration(duration).setInterpolator(new LinearInterpolator());
        statusBar.start();
    }

    public static void animateViewScale(View view, boolean zoomOut, int duration) {
        ScaleAnimation scaleAnimation;
        final float normalScale = 1.0f;
        final float zoomOutScale = 0.8f;

        if (zoomOut) {
            scaleAnimation = new ScaleAnimation(
                    normalScale,
                    zoomOutScale,
                    normalScale,
                    zoomOutScale,
                    ScaleAnimation.RELATIVE_TO_SELF,
                    0.5f,
                    ScaleAnimation.RELATIVE_TO_SELF,
                    0.5f);
        } else {
            scaleAnimation = new ScaleAnimation(
                    zoomOutScale,
                    normalScale,
                    zoomOutScale,
                    normalScale,
                    ScaleAnimation.RELATIVE_TO_SELF,
                    0.5f,
                    ScaleAnimation.RELATIVE_TO_SELF,
                    0.5f);
        }

        scaleAnimation.setDuration(duration);
        scaleAnimation.setFillAfter(true);
        scaleAnimation.setInterpolator(new AccelerateDecelerateInterpolator());

        view.startAnimation(scaleAnimation);
    }

    public static Point getDisplaySize() {
        Point displaySize = new Point();
        WindowManager windowManager = (WindowManager) application.getSystemService(Activity.WINDOW_SERVICE);
        windowManager.getDefaultDisplay().getSize(displaySize);
        return displaySize;
    }

    public static StartActivityComponent extractStartActivityComponent(Context context) {
        if (context instanceof StartActivity) {
            return ((StartActivity) context).getComponent();
        } else if (context instanceof ContextWrapper) {
            return ((StartActivity) ((ContextWrapper) context)
                    .getBaseContext())
                    .getComponent();
        } else {
            throw new IllegalStateException(
                    "Unknown context wrapper " + context.getClass().getName());
        }
    }
}
