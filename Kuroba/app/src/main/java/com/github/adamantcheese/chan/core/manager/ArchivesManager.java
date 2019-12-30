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
package com.github.adamantcheese.chan.core.manager;

import androidx.annotation.NonNull;

import com.github.adamantcheese.chan.core.model.orm.Board;
import com.github.adamantcheese.chan.core.site.Site;
import com.github.adamantcheese.chan.core.site.SiteActions;
import com.github.adamantcheese.chan.ui.layout.ArchivesLayout;

import java.util.ArrayList;
import java.util.List;

import static com.github.adamantcheese.chan.utils.AndroidUtils.showToast;

public class ArchivesManager
        implements SiteActions.ArchiveRequestListener {
    private List<Archives> archivesList;

    public ArchivesManager(Site s) {
        s.actions().archives(this);
    }

    public List<ArchivesLayout.PairForAdapter> domainsForBoard(Board b) {
        List<ArchivesLayout.PairForAdapter> result = new ArrayList<>();
        if (archivesList == null) return result;
        for (Archives a : archivesList) {
            for (String code : a.boards) {
                if (code.equals(b.code)) {
                    result.add(new ArchivesLayout.PairForAdapter(a.name, a.domain));
                    break;
                }
            }
        }
        return result;
    }

    @Override
    public void onArchivesReceived(List<Archives> archives) {
        archivesList = archives;
    }

    @Override
    public void onCouldNotReceiveArchives(@NonNull String message) {
        showToast("Couldn't receive archives: reason = " + message);
        archivesList = new ArrayList<>();
    }

    public static class Archives {
        public String name = "";
        public String domain = "";
        public List<String> boards;
    }
}
