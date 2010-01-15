/*
 * ***** BEGIN LICENSE BLOCK *****
 * Zimbra Collaboration Suite Server
 * Copyright (C) 2008, 2010 Zimbra, Inc.
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
package com.zimbra.cs.account.accesscontrol;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.zimbra.common.service.ServiceException;
import com.zimbra.cs.account.Account;
import com.zimbra.cs.account.AccountServiceException;
import com.zimbra.cs.account.Entry;
import com.zimbra.cs.account.NamedEntry;
import com.zimbra.cs.account.Provisioning;
import com.zimbra.cs.account.Provisioning.AccountBy;
import com.zimbra.cs.account.Provisioning.DistributionListBy;
import com.zimbra.soap.ZimbraSoapContext;

public class PermUtil {
    
    /**
     * Get ACEs with specified rights
     * 
     * @param target 
     * @param rights specified rights.  If null, all ACEs in the ACL will be returned.
     * @return ACEs with right specified in rights
     * @throws ServiceException
     */
    public static Set<ZimbraACE> getACEs(Entry target, Set<Right> rights) throws ServiceException {
        ZimbraACL acl = getACL(target); 
        if (acl != null)
            return acl.getACEs(rights);
        else
            return null;
    }
    
    public static Set<ZimbraACE> grantAccess(Entry target, Set<ZimbraACE> aces) throws ServiceException {
        for (ZimbraACE ace : aces)
            ZimbraACE.validate(ace);
        
        ZimbraACL acl = getACL(target); 
        Set<ZimbraACE> granted = null;
        
        if (acl == null) {
            acl = new ZimbraACL(aces);
            granted = acl.getACEs(null);
        } else {
            // make a copy so we don't interfere with others that are using the acl
            acl = acl.clone();
            granted = acl.grantAccess(aces); 
        }
        
        serialize(target, acl);
        return granted;
    }
    
    /** Removes the right granted to the specified id.  If the right 
     *  was not previously granted to the target, no error is thrown.
     */
    public static Set<ZimbraACE> revokeAccess(Entry target, Set<ZimbraACE> aces) throws ServiceException {
        ZimbraACL acl = getACL(target); 
        if (acl == null)
            return null;
        
        // make a copy so we don't interfere with others that are using the acl
        acl = acl.clone();
        Set<ZimbraACE> revoked = acl.revokeAccess(aces);
        serialize(target, acl);
        return revoked;
    }
    
    private static void serialize(Entry target, ZimbraACL acl) throws ServiceException {
        Map<String, Object> attrs = new HashMap<String, Object>();
        attrs.put(Provisioning.A_zimbraACE, acl.serialize());
        // modifyAttrs will erase cached ACL on the target
        Provisioning.getInstance().modifyAttrs(target, attrs);
    }
    

    private static final String ACL_CACHE_KEY = "ENTRY.ACL_CACHE";
    
    static ZimbraACL getACL(Entry entry) throws ServiceException {
        ZimbraACL acl = (ZimbraACL)entry.getCachedData(ACL_CACHE_KEY);
        if (acl != null)
            return acl;
        else {
            String[] aces = entry.getMultiAttr(Provisioning.A_zimbraACE);
            if (aces.length == 0)
                return null;
            else {
                acl = new ZimbraACL(aces, RightManager.getInstance());
                entry.setCachedData(ACL_CACHE_KEY, acl);
            }
        }
        return acl;
    }

    /*
     * 
     * lookupEmailAddress, lookupGranteeByName, lookupGranteeByZimbraId borrowed from FolderAction
     * and transplanted to work with ACL in accesscontrol package.
     * 
     */
    
    // orig: FolderAction.lookupEmailAddress
    public static NamedEntry lookupEmailAddress(String name) throws ServiceException {
        NamedEntry nentry = null;
        Provisioning prov = Provisioning.getInstance();
        nentry = prov.get(AccountBy.name, name);
        if (nentry == null)
            nentry = prov.get(DistributionListBy.name, name);
        return nentry;
    }
    
    // orig: FolderAction.lookupGranteeByName
    public static NamedEntry lookupGranteeByName(String name, GranteeType type, ZimbraSoapContext zsc) throws ServiceException {
        if (type == GranteeType.GT_AUTHUSER || type == GranteeType.GT_PUBLIC || type == GranteeType.GT_GUEST || type == GranteeType.GT_KEY)
            return null;

        Provisioning prov = Provisioning.getInstance();
        // for addresses, default to the authenticated user's domain
        if ((type == GranteeType.GT_USER || type == GranteeType.GT_GROUP) && name.indexOf('@') == -1) {
            Account authacct = prov.get(AccountBy.id, zsc.getAuthtokenAccountId(), zsc.getAuthToken());
            String authname = (authacct == null ? null : authacct.getName());
            if (authacct != null)
                name += authname.substring(authname.indexOf('@'));
        }

        NamedEntry nentry = null;
        if (name != null)
            switch (type) {
                case GT_USER:    nentry = lookupEmailAddress(name);                 break;
                case GT_GROUP:   nentry = prov.get(DistributionListBy.name, name);  break;
            }

        if (nentry != null)
            return nentry;
        switch (type) {
            case GT_USER:    throw AccountServiceException.NO_SUCH_ACCOUNT(name);
            case GT_GROUP:   throw AccountServiceException.NO_SUCH_DISTRIBUTION_LIST(name);
            default:  throw ServiceException.FAILURE("LDAP entry not found for " + name + " : " + type, null);
        }
    }

    // orig: FolderAction.lookupGranteeByZimbraId
    public static NamedEntry lookupGranteeByZimbraId(String zid, GranteeType type) {
        Provisioning prov = Provisioning.getInstance();
        try {
            switch (type) {
                case GT_USER:    return prov.get(AccountBy.id, zid);
                case GT_GROUP:   return prov.get(DistributionListBy.id, zid);
                case GT_GUEST:
                case GT_KEY:    
                case GT_AUTHUSER:
                case GT_PUBLIC:
                default:         return null;
            }
        } catch (ServiceException e) {
            return null;
        }
    }

}
