/*
 * ***** BEGIN LICENSE BLOCK *****
 * Zimbra Collaboration Suite Server
 * Copyright (C) 2007, 2010 Zimbra, Inc.
 * 
 * The contents of this file are subject to the Zimbra Public License
 * Version 1.3 ("License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the License at
 * http://www.zimbra.com/license.
 * 
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied.
 * ***** END LICENSE BLOCK *****
 */
package com.zimbra.cs.extension;

/**
 * Optional interface for Zimbra Extensions -- if ZimbraExtension instance inherits from this
 * interface, it will be called during boot
 */
public interface ZimbraExtensionPostInit {
    /**
     * Called at the end of the server boot process, after all server subsystems
     * are up and running.
     */
    public void postInit();

}
