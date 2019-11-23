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
package com.github.adamantcheese.chan.ui.controller;

import android.content.Context;
import android.widget.Toast;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.database.DatabaseManager;
import com.github.adamantcheese.chan.core.di.component.activity.StartActivityComponent;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.ui.helper.RefreshUIMessage;
import com.github.adamantcheese.chan.ui.settings.BooleanSettingView;
import com.github.adamantcheese.chan.ui.settings.IntegerSettingView;
import com.github.adamantcheese.chan.ui.settings.LinkSettingView;
import com.github.adamantcheese.chan.ui.settings.SettingsController;
import com.github.adamantcheese.chan.ui.settings.SettingsGroup;
import com.github.adamantcheese.chan.ui.settings.StringSettingView;

import org.greenrobot.eventbus.EventBus;

import javax.inject.Inject;

public class BehaviourSettingsController extends SettingsController {

    @Inject
    DatabaseManager databaseManager;

    public BehaviourSettingsController(Context context) {
        super(context);
    }

    @Override
    protected void injectDependencies(StartActivityComponent component) {
        component.inject(this);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        navigation.setTitle(R.string.settings_screen_behavior);

        setupLayout();
        rebuildPreferences();
    }

    private void rebuildPreferences() {
        populatePreferences();
        buildPreferences();
    }

    private void populatePreferences() {
        requiresUiRefresh.clear();
        groups.clear();
        requiresRestart.clear();


        // General group
        {
            SettingsGroup general = new SettingsGroup(R.string.settings_group_general);

            general.add(new BooleanSettingView(this,
                    ChanSettings.autoRefreshThread,
                    R.string.setting_auto_refresh_thread, 0));

            requiresRestart.add(general.add(new BooleanSettingView(this,
                    ChanSettings.controllerSwipeable,
                    R.string.setting_controller_swipeable, 0)));

            requiresRestart.add(general.add(new BooleanSettingView(this,
                    ChanSettings.fullUserRotationEnable,
                    R.string.setting_full_screen_rotation, 0)));

            general.add(new BooleanSettingView(this,
                    ChanSettings.alwaysOpenDrawer,
                    R.string.settings_always_open_drawer, 0));

            setupClearThreadHidesSetting(general);

            groups.add(general);
        }

        // Reply group
        {
            SettingsGroup reply = new SettingsGroup(R.string.settings_group_reply);

            reply.add(new BooleanSettingView(this, ChanSettings.postPinThread,
                    R.string.setting_post_pin, 0));

            reply.add(new StringSettingView(this, ChanSettings.postDefaultName,
                    R.string.setting_post_default_name, R.string.setting_post_default_name));

            groups.add(reply);
        }

        // Post group
        {
            SettingsGroup post = new SettingsGroup(R.string.settings_group_post);

            requiresUiRefresh.add(post.add(new BooleanSettingView(this,
                    ChanSettings.textOnly,
                    R.string.setting_text_only, R.string.setting_text_only_description)));

            requiresUiRefresh.add(post.add(new BooleanSettingView(this,
                    ChanSettings.revealTextSpoilers,
                    R.string.settings_reveal_text_spoilers,
                    R.string.settings_reveal_text_spoilers_description)));

            requiresUiRefresh.add(post.add(new BooleanSettingView(this,
                    ChanSettings.anonymize,
                    R.string.setting_anonymize, 0)));

            requiresUiRefresh.add(post.add(new BooleanSettingView(this,
                    ChanSettings.anonymizeIds,
                    R.string.setting_anonymize_ids, 0)));

            requiresUiRefresh.add(post.add(new BooleanSettingView(this,
                    ChanSettings.showAnonymousName,
                    R.string.setting_show_anonymous_name, 0)));

            post.add(new BooleanSettingView(this,
                    ChanSettings.repliesButtonsBottom,
                    R.string.setting_buttons_bottom, 0));

            post.add(new BooleanSettingView(this,
                    ChanSettings.volumeKeysScrolling,
                    R.string.setting_volume_key_scrolling, 0));

            post.add(new BooleanSettingView(this,
                    ChanSettings.tapNoReply,
                    R.string.setting_tap_no_rely, 0));

            post.add(new BooleanSettingView(this,
                    ChanSettings.enableLongPressURLCopy,
                    R.string.settings_image_long_url,
                    R.string.settings_image_long_url_description));

            post.add(new BooleanSettingView(this,
                    ChanSettings.openLinkConfirmation,
                    R.string.setting_open_link_confirmation, 0));
            post.add(new BooleanSettingView(this,
                    ChanSettings.openLinkBrowser,
                    R.string.setting_open_link_browser, 0));

            //postingTimeoutSetting = addPostingTimeoutSetting();
            //post.add(postingTimeoutSetting);

            groups.add(post);
        }

        // Proxy group
        {
            SettingsGroup proxy = new SettingsGroup(R.string.settings_group_proxy);

            requiresRestart.add(proxy.add(new BooleanSettingView(this, ChanSettings.proxyEnabled,
                    R.string.setting_proxy_enabled, 0)));

            requiresRestart.add(proxy.add(new StringSettingView(this, ChanSettings.proxyAddress,
                    R.string.setting_proxy_address, R.string.setting_proxy_address)));

            requiresRestart.add(proxy.add(new IntegerSettingView(this, ChanSettings.proxyPort,
                    R.string.setting_proxy_port, R.string.setting_proxy_port)));

            groups.add(proxy);
        }
    }

    private void setupClearThreadHidesSetting(SettingsGroup post) {
        post.add(new LinkSettingView(this, R.string.setting_clear_thread_hides, 0, v -> {
            // TODO: don't do this here.
            databaseManager.runTask(
                    databaseManager.getDatabaseHideManager().clearAllThreadHides());
            Toast.makeText(context, R.string.setting_cleared_thread_hides, Toast.LENGTH_LONG)
                    .show();
            EventBus.getDefault().post(new RefreshUIMessage("clearhides"));
        }));
    }
}
