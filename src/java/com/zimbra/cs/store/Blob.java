/*
 * ***** BEGIN LICENSE BLOCK *****
 * Zimbra Collaboration Suite Server
 * Copyright (C) 2005, 2007, 2008, 2010 Zimbra, Inc.
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

/*
 * Created on 2005. 6. 7.
 */
package com.zimbra.cs.store;

import java.io.File;

/**
 * @author jhahm
 * 
 * Represents a blob in blob store incoming directory.  An incoming blob
 * does not belong to any mailbox.  When a message is delivered to a mailbox,
 * message is saved in the incoming directory and a link to it is created
 * in the mailbox's directory.  The linked blob in mailbox directory
 * is represented by a MailboxBlob object.
 */
public class Blob {

    private File mFile;
    private String mPath;
    private short mVolumeId;
    private boolean mIsCompressed = false;
    private byte[] mData;
    private String mDigest;
    
    private int mRawSize; //can be 0 if not set

    public Blob(File file, short volumeId) {
        mFile = file;
        mPath = file.getAbsolutePath();
        mVolumeId = volumeId;
    }

    public File getFile() {
        return mFile;
    }

    public String getPath() {
    	return mPath;
    }

    public short getVolumeId() {
    	return mVolumeId;
    }
    
    public boolean isCompressed() {
        return mIsCompressed;
    }
    
    public String getDigest() {
        return mDigest;
    }
    
    /**
     * Returns the blob data if either (1) the blob size did not exceed the disk
     * streaming threshold or (2) the blob was compressed.  Otherwise returns
     * <tt>null</tt>.
     */
    public byte[] getInMemoryData() {
        return mData;
    }
    
    public int getRawSize() {
    	return mData != null ? mData.length : mRawSize;
    }
    
    public void setCompressed(boolean isCompressed) {
        mIsCompressed = isCompressed;
    }

    /**
     * Sets the in-memory data for this blob.
     */
    public void setInMemoryData(byte[] data) {
        mData = data;
    }
    
    public void setDigest(String digest) {
        mDigest = digest;
    }
    
    public void setRawSize(int rawSize) {
    	mRawSize = rawSize;
    }

    public String toString() {
        return String.format("path=%s, vol=%d, isCompressed=%b", mPath, mVolumeId, mIsCompressed);
    }
}
