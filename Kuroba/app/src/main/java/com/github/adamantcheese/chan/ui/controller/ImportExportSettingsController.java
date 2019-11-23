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

import android.app.AlertDialog;
import android.content.Context;
import android.net.Uri;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.StartActivity;
import com.github.adamantcheese.chan.core.di.component.activity.StartActivityComponent;
import com.github.adamantcheese.chan.core.presenter.ImportExportSettingsPresenter;
import com.github.adamantcheese.chan.core.repository.ImportExportRepository;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.ui.settings.LinkSettingView;
import com.github.adamantcheese.chan.ui.settings.SettingsController;
import com.github.adamantcheese.chan.ui.settings.SettingsGroup;
import com.github.adamantcheese.chan.utils.AndroidUtils;
import com.github.adamantcheese.chan.utils.Logger;
import com.github.k1rakishou.fsaf.FileChooser;
import com.github.k1rakishou.fsaf.FileManager;
import com.github.k1rakishou.fsaf.callback.FileChooserCallback;
import com.github.k1rakishou.fsaf.callback.FileCreateCallback;
import com.github.k1rakishou.fsaf.file.ExternalFile;

import org.jetbrains.annotations.NotNull;

import javax.inject.Inject;

import static com.github.adamantcheese.chan.utils.AndroidUtils.getApplicationLabel;

public class ImportExportSettingsController extends SettingsController implements
        ImportExportSettingsPresenter.ImportExportSettingsCallbacks {
    private static final String TAG = "ImportExportSettingsController";
    public static final String EXPORT_FILE_NAME = getApplicationLabel() + "_exported_settings.json";

    @Inject
    FileManager fileManager;
    @Inject
    FileChooser fileChooser;
    @Inject
    ImportExportRepository importExportRepository;

    private ImportExportSettingsPresenter presenter;

    @Nullable
    private OnExportSuccessCallbacks callbacks;

    private LoadingViewController loadingViewController;

    public ImportExportSettingsController(Context context, @NonNull OnExportSuccessCallbacks callbacks) {
        super(context);

        this.callbacks = callbacks;
        this.loadingViewController = new LoadingViewController(context, true);
    }

    @Override
    protected void injectDependencies(StartActivityComponent component) {
        component.inject(this);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        navigation.setTitle(R.string.settings_import_export);

        presenter = new ImportExportSettingsPresenter(importExportRepository, this);

        setupLayout();
        populatePreferences();
        buildPreferences();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (presenter != null) {
            presenter.onDestroy();
        }

        callbacks = null;
    }

    protected void setupLayout() {
        view = inflateRes(R.layout.settings_layout);
        content = view.findViewById(R.id.scrollview_content);
    }

    private void populatePreferences() {
        // Import/export settings group
        {
            SettingsGroup group = new SettingsGroup(context.getString(R.string.import_or_export_settings));

            group.add(new LinkSettingView(this,
                    context.getString(R.string.export_settings),
                    context.getString(R.string.export_settings_to_a_file),
                    v -> onExportClicked()));

            group.add(new LinkSettingView(this,
                    context.getString(R.string.import_settings),
                    context.getString(R.string.import_settings_from_a_file),
                    v -> onImportClicked()));

            groups.add(group);
        }
    }

    private void onExportClicked() {
        boolean localThreadsLocationIsSAFBacked = !ChanSettings.localThreadsLocationUri.get().isEmpty();
        boolean savedFilesLocationIsSAFBacked = !ChanSettings.saveLocationUri.get().isEmpty();

        if (localThreadsLocationIsSAFBacked || savedFilesLocationIsSAFBacked) {
            showSomeBaseDirectoriesWillBeResetToDefaultDialog(
                    localThreadsLocationIsSAFBacked,
                    savedFilesLocationIsSAFBacked
            );
            return;
        }

        showCreateNewOrOverwriteDialog();
    }

    private void showSomeBaseDirectoriesWillBeResetToDefaultDialog(
            boolean localThreadsLocationIsSAFBacked,
            boolean savedFilesLocationIsSAFBacked
    ) {
        if (!localThreadsLocationIsSAFBacked && !savedFilesLocationIsSAFBacked) {
            throw new IllegalStateException("Both variables are false, wtf?");
        }

        String localThreadsString = localThreadsLocationIsSAFBacked
                ? context.getString(R.string.import_or_export_warning_local_threads_base_dir)
                : "";
        String andString = localThreadsLocationIsSAFBacked && savedFilesLocationIsSAFBacked
                ? context.getString(R.string.import_or_export_warning_and)
                : "";
        String savedFilesString = savedFilesLocationIsSAFBacked
                ? context.getString(R.string.import_or_export_warning_saved_files_base_dir)
                : "";

        String message = context.getString(
                R.string.import_or_export_warning_super_long_message,
                localThreadsString,
                andString,
                savedFilesString
        );

        AlertDialog alertDialog = new AlertDialog.Builder(context)
                .setTitle(context.getString(R.string.import_or_export_warning))
                .setMessage(message)
                .setPositiveButton(R.string.ok, (dialog, which) -> {
                    dialog.dismiss();
                    showCreateNewOrOverwriteDialog();
                })
                .create();

        alertDialog.show();
    }

    /**
     * SAF is kinda horrible so it cannot be used to overwrite a file that already exist on the disk
     * (or at some network location). When trying to do so, a new file with appended "(1)" at the
     * end will appear. That's why there are two methods (one for overwriting an existing file and
     * the other one for creating a new file) instead of one that does everything.
     */
    private void showCreateNewOrOverwriteDialog() {
        int positiveButtonId = R.string.import_or_export_dialog_positive_button_text;
        int negativeButtonId = R.string.import_or_export_dialog_negative_button_text;

        AlertDialog alertDialog = new AlertDialog.Builder(context)
                .setTitle(R.string.import_or_export_dialog_title)
                .setPositiveButton(positiveButtonId, (dialog, which) -> {
                    overwriteExisting();
                })
                .setNegativeButton(negativeButtonId, (dialog, which) -> {
                    createNew();
                })
                .create();

        alertDialog.show();
    }

    /**
     * Opens an existing file (any file) for overwriting with the settings.
     */
    private void overwriteExisting() {
        fileChooser.openChooseFileDialog(new FileChooserCallback() {
            @Override
            public void onResult(@NotNull Uri uri) {
                onFileChosen(uri, false);
            }

            @Override
            public void onCancel(@NotNull String reason) {
                showMessage(reason);
            }
        });
    }

    /**
     * Creates a new file with the default name (that can be changed in the file chooser) with the
     * settings. Cannot be used for overwriting an old settings file (when trying to do so a new file
     * with appended "(1)" at the end will appear, e.g. "test (1).txt")
     */
    private void createNew() {
        fileChooser.openCreateFileDialog(EXPORT_FILE_NAME, new FileCreateCallback() {
            @Override
            public void onResult(@NotNull Uri uri) {
                onFileChosen(uri, true);
            }

            @Override
            public void onCancel(@NotNull String reason) {
                showMessage(reason);
            }
        });
    }

    private void onFileChosen(Uri uri, boolean isNewFile) {
        // We use SAF here by default because settings importing/exporting does not depend on the
        // Kuroba default directory location. There is just no need to use old java files.
        ExternalFile externalFile = fileManager.fromUri(uri);
        if (externalFile == null) {
            String message = "onFileChosen() fileManager.fromUri() returned null, uri = " + uri;

            Logger.d(TAG, message);
            showMessage(message);
            return;
        }

        navigationController.presentController(loadingViewController);
        presenter.doExport(externalFile, isNewFile);
    }

    private void onImportClicked() {
        fileChooser.openChooseFileDialog(new FileChooserCallback() {
            @Override
            public void onResult(@NotNull Uri uri) {
                ExternalFile externalFile = fileManager.fromUri(uri);
                if (externalFile == null) {
                    String message = "onImportClicked() fileManager.fromUri() returned null, uri = " + uri;

                    Logger.d(TAG, message);
                    showMessage(message);
                    return;
                }

                navigationController.presentController(loadingViewController);
                presenter.doImport(externalFile);
            }

            @Override
            public void onCancel(@NotNull String reason) {
                showMessage(reason);
            }
        });
    }

    @Override
    public void onSuccess(ImportExportRepository.ImportExport importExport) {
        // called on background thread
        if (context instanceof StartActivity) {
            AndroidUtils.runOnUiThread(() -> {
                if (importExport == ImportExportRepository.ImportExport.Import) {
                    ((StartActivity) context).restartApp();
                } else {
                    clearAllChildControllers();
                    showMessage(context.getString(R.string.successfully_exported_text));

                    if (callbacks != null) {
                        callbacks.finish();
                    }
                }
            });
        }
    }


    @Override
    public void onError(String message) {
        // may be called on background thread
        AndroidUtils.runOnUiThread(() -> {
            clearAllChildControllers();
            showMessage(message);
        });
    }

    private void showMessage(String message) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show();
    }

    private void clearAllChildControllers() {
        if (loadingViewController.shown) {
            loadingViewController.stopPresenting();
        }
    }

    public interface OnExportSuccessCallbacks {
        void finish();
    }
}
