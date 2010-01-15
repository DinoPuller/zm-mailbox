/*
 * ***** BEGIN LICENSE BLOCK *****
 * Zimbra Collaboration Suite Server
 * Copyright (C) 2006, 2007, 2009, 2010 Zimbra, Inc.
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
package com.zimbra.cs.dav.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.servlet.http.HttpServletResponse;

import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.methods.ByteArrayRequestEntity;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.dom4j.io.XMLWriter;

import com.zimbra.common.service.ServiceException;
import com.zimbra.cs.dav.DavContext;
import com.zimbra.cs.dav.DavException;
import com.zimbra.cs.dav.DavProtocol;

/**
 * Base class for DAV methods.
 * 
 * @author jylee
 *
 */
public abstract class DavMethod {
	public abstract String getName();
	public abstract void handle(DavContext ctxt) throws DavException, IOException, ServiceException;
	
	public void checkPrecondition(DavContext ctxt) throws DavException {
	}
	
	public void checkPostcondition(DavContext ctxt) throws DavException {
	}
	
	public String toString() {
		return "DAV method " + getName();
	}
	
	public String getMethodName() {
		return getName();
	}
	
	protected static final int STATUS_OK = HttpServletResponse.SC_OK;
	
	protected void sendResponse(DavContext ctxt) throws IOException {
		if (ctxt.isResponseSent())
			return;
		HttpServletResponse resp = ctxt.getResponse();
		resp.setStatus(ctxt.getStatus());
		String compliance = ctxt.getDavCompliance();
		if (compliance != null)
			resp.setHeader(DavProtocol.HEADER_DAV, compliance);
		if (ctxt.hasResponseMessage()) {
			resp.setContentType(DavProtocol.DAV_CONTENT_TYPE);
			DavResponse respMsg = ctxt.getDavResponse();
			respMsg.writeTo(resp.getOutputStream());
		}
		ctxt.responseSent();
	}
	
	public HttpMethod toHttpMethod(DavContext ctxt, String targetUrl) throws IOException, DavException {
		if (ctxt.hasRequestMessage()) {
			PostMethod method = new PostMethod(targetUrl) {
				public String getName() { return getMethodName(); }
			};
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			XMLWriter writer = new XMLWriter(baos);
			writer.write(ctxt.getRequestMessage());
			ByteArrayRequestEntity reqEntry = new ByteArrayRequestEntity(baos.toByteArray());
			method.setRequestEntity(reqEntry);
			return method;
		}
    	return new GetMethod(targetUrl) {
    		public String getName() { return getMethodName(); }
    	};
	}
}
