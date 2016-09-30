/*
 * ***** BEGIN LICENSE BLOCK *****
 * Zimbra Collaboration Suite Server
 * Copyright (C) 2016 Synacor, Inc.
 *
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software Foundation,
 * version 2 of the License.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with this program.
 * If not, see <https://www.gnu.org/licenses/>.
 * ***** END LICENSE BLOCK *****
 */
package com.zimbra.cs.imap;

import com.zimbra.client.ZFolder;

public class RemoteImapFolderStore implements ImapFolderStore {
    private transient ZFolder folder;

    public RemoteImapFolderStore(ZFolder folder) {
        this.folder = folder;
    }

    @Override
    public String getId() {
        return folder.getId();
    }

    @Override
    public int getUIDValidity() {
        return ImapFolder.getUIDValidity(folder);
    }
}
