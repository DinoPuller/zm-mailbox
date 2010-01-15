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
package com.zimbra.cs.mailbox;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;

import javax.mail.MessagingException;

import com.zimbra.common.service.ServiceException;
import com.zimbra.common.util.ByteUtil;
import com.zimbra.common.util.ZimbraLog;
import com.zimbra.cs.account.Provisioning;
import com.zimbra.cs.mime.ParsedMessage;
import com.zimbra.cs.store.Blob;
import com.zimbra.cs.store.StoreManager;
import com.zimbra.cs.store.Volume;

/**
 * Reads blob data from an <tt>InputStream</tt> and either retains it in
 * memory or stores it directly to disk, depending on whether the size
 * exceeds {@link Provisioning#A_zimbraMailDiskStreamingThreshold}.
 */
public class IncomingBlob {
    
    private byte[] mData;
    private Blob mBlobOnDisk;
    
    private IncomingBlob() {
    }
    
    /**
     * Returns the blob data if either (1) the blob size did not exceed the disk
     * streaming threshold or (2) the blob was compressed.  Otherwise returns
     * <tt>null</tt>.
     */
    public byte[] getInMemoryData() {
        return mData;
    }

    /**
     * Returns the <tt>Blob</tt> object, or <tt>null</tt> if the blob size did not
     * exceed the disk streaming threshold.
     */
    public Blob getBlob() {
        return mBlobOnDisk;
    }
    
    /**
     * Delete the Blob file or relinquish memory buffer
     */
    public void delete() throws ServiceException {
        try {
            if (mBlobOnDisk != null) {
                StoreManager.getInstance().delete(mBlobOnDisk);
                mBlobOnDisk = null;
            } else {
                mData = null;
            }
        } catch (Exception e) {
            throw ServiceException.FAILURE("Unable to delete incoming message.", e);
        }
    }
    /**
     * Creates a new <tt>ParsedMessage</tt> that references either
     * the in-memory data or the blob on disk, depending on the state of this
     * <tt>IncomingBlob</tt>.
     */
    public ParsedMessage createParsedMessage(Long receivedDate, boolean indexAttachments)
    throws ServiceException, MessagingException {
        ParsedMessage pm = null;
        
        if (mData != null) {
            pm = new ParsedMessage(mData, receivedDate, indexAttachments);
        } else {
            try {
                pm = new ParsedMessage(mBlobOnDisk.getFile(), receivedDate, true);
                pm.setRawDigest(mBlobOnDisk.getDigest());
            } catch (IOException e) {
                throw ServiceException.FAILURE("Unable to parse message.", e);
            }
        }
        return pm;
    }

    public ParsedMessage createParsedMessage(boolean indexAttachments)
    throws ServiceException, MessagingException {
        return createParsedMessage(null, indexAttachments);
    }
    
    /**
     * Creates a new <tt>IncomingBlob</tt>.
     * 
     * @param in the blob data stream
     * @param sizeHint estimate of the size of the data
     * @param diskThreshold messages larger than this size will be streamed to disk
     */
    public static IncomingBlob create(InputStream in, int sizeHint, int diskThreshold)
    throws ServiceException {
        if (in == null) {
            return null;
        }
        Volume volume = Volume.getCurrentMessageVolume();
        if (diskThreshold == Integer.MAX_VALUE) {
            // Don't exceed Integer.MAX_VALUE when reading (diskThreshold + 1) bytes
            diskThreshold--;
        }
        
        byte[] data = null;
        Blob blob = null;
        
        try {
            if (sizeHint <= diskThreshold) {
                // Try to read the message into memory, up to the threshold
                ZimbraLog.lmtp.debug("Reading message into memory.  sizeHint=%d", sizeHint);
                data = ByteUtil.readInput(in, sizeHint, diskThreshold + 1);
                
                if (data.length > diskThreshold) {
                    // More data available.  Stream to disk instead.
                    ZimbraLog.lmtp.info(
                        "Message with size hint %d exceeded threshold of %d.  Streaming message to disk.",
                        sizeHint, diskThreshold);
                    ByteArrayInputStream firstChunk = new ByteArrayInputStream(data); 
                    SequenceInputStream jointStream = new SequenceInputStream(firstChunk, in);
                    blob = StoreManager.getInstance().storeIncoming(jointStream, diskThreshold, null, volume.getId());
                    if (blob.getFile().length() == 0) {
                        throw new MessagingException("Empty message not allowed for "
                            + blob.getFile().getPath());
                    }
                    ZimbraLog.lmtp.debug("Wrote message to %s.", blob.getPath());
                    data = null;
                }
            } else {
                ZimbraLog.lmtp.debug("Streaming message of size %d to disk.", sizeHint);
                blob = StoreManager.getInstance().storeIncoming(in, sizeHint, null, volume.getId());
                if (blob.getFile().length() == 0) {
                    throw new MessagingException("Empty message not allowed for "
                        + blob.getFile().getPath());
                }
                ZimbraLog.lmtp.debug("Wrote message to %s.", blob.getPath());
            }
            
            IncomingBlob incoming = new IncomingBlob();
            if (blob != null) {
                incoming.mBlobOnDisk = blob;
                incoming.mData = blob.getInMemoryData();
            } else {
                incoming.mData = data;
            }
            return incoming;
        } catch (IOException e) {
            throw ServiceException.FAILURE("Unable to process incoming message.", e);
        } catch (MessagingException e) {
            throw ServiceException.FAILURE("Unable to process incoming message.", e);
        }
    }
}
