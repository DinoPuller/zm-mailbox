/*
 * ***** BEGIN LICENSE BLOCK *****
 * Zimbra Collaboration Suite Server
 * Copyright (C) 2005, 2006, 2007, 2010 Zimbra, Inc.
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

package com.zimbra.cs.service.admin;

import java.util.Map;

import com.zimbra.cs.account.AccountServiceException;
import com.zimbra.cs.account.DistributionList;
import com.zimbra.cs.account.Provisioning;
import com.zimbra.cs.account.Provisioning.DistributionListBy;
import com.zimbra.common.service.ServiceException;
import com.zimbra.common.util.ZimbraLog;
import com.zimbra.common.soap.AdminConstants;
import com.zimbra.common.soap.Element;
import com.zimbra.soap.ZimbraSoapContext;

public class ModifyDistributionList extends AdminDocumentHandler {

    /**
     * must be careful and only allow access to domain if domain admin
     */
    public boolean domainAuthSufficient(Map context) {
        return true;
    }

    public Element handle(Element request, Map<String, Object> context) throws ServiceException {

        ZimbraSoapContext lc = getZimbraSoapContext(context);
        Provisioning prov = Provisioning.getInstance();

        String id = request.getAttribute(AdminConstants.E_ID);
        Map<String, Object> attrs = AdminService.getAttrs(request);
	    
        DistributionList distributionList = prov.get(DistributionListBy.id, id);
        if (distributionList == null)
            throw AccountServiceException.NO_SUCH_DISTRIBUTION_LIST(id);

        if (!canAccessEmail(lc, distributionList.getName()))
            throw ServiceException.PERM_DENIED("can not access dl");

        // pass in true to checkImmutable
        prov.modifyAttrs(distributionList, attrs, true);
        
        ZimbraLog.security.info(ZimbraLog.encodeAttrs(
                  new String[] {"cmd", "ModifyDistributionList","name", distributionList.getName()}, attrs));	    

        Element response = lc.createElement(AdminConstants.MODIFY_DISTRIBUTION_LIST_RESPONSE);
        GetDistributionList.doDistributionList(response, distributionList);
        return response;
    }
}
