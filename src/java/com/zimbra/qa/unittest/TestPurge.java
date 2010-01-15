/*
 * ***** BEGIN LICENSE BLOCK *****
 * Zimbra Collaboration Suite Server
 * Copyright (C) 2007, 2009, 2010 Zimbra, Inc.
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
package com.zimbra.qa.unittest;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import com.zimbra.common.service.ServiceException;
import com.zimbra.common.util.Constants;
import com.zimbra.cs.account.Account;
import com.zimbra.cs.account.Provisioning;
import com.zimbra.cs.account.ldap.LdapUtil;
import com.zimbra.cs.mailbox.Flag;
import com.zimbra.cs.mailbox.Folder;
import com.zimbra.cs.mailbox.MailItem;
import com.zimbra.cs.mailbox.Mailbox;
import com.zimbra.cs.mailbox.Message;
import com.zimbra.cs.mailbox.MailServiceException.NoSuchItemException;

import junit.framework.TestCase;

public class TestPurge extends TestCase {

    private static final String USER_NAME = "user4";
    private static final String NAME_PREFIX = TestPurge.class.getSimpleName();
    
    private String mOriginalSystemTrashLifetime;
    private String mOriginalSystemJunkLifetime;
    private String mOriginalSystemMessageLifetime;
    
    private String mOriginalUserInboxReadLifetime;
    private String mOriginalUserInboxUnreadLifetime;
    private String mOriginalUserSentLifetime;
    private String mOriginalUserTrashLifetime;
    private String mOriginalUserJunkLifetime;
    
    private String mOriginalUseChangeDateForTrash;
    
    long mPurgedTimestamp = System.currentTimeMillis() - (2 * Constants.MILLIS_PER_MONTH);
    long mLaterCutoff = mPurgedTimestamp + Constants.MILLIS_PER_HOUR;
    long mMiddleTimestamp = mLaterCutoff + Constants.MILLIS_PER_HOUR;
    long mEarlierCutoff = mMiddleTimestamp + Constants.MILLIS_PER_HOUR; 
    long mKeptTimestamp = mEarlierCutoff + Constants.MILLIS_PER_HOUR;
    
    public void setUp()
    throws Exception {
        cleanUp();
        
        Account account = TestUtil.getAccount(USER_NAME);
        mOriginalSystemTrashLifetime = account.getAttr(Provisioning.A_zimbraMailTrashLifetime);
        mOriginalSystemJunkLifetime = account.getAttr(Provisioning.A_zimbraMailSpamLifetime);
        mOriginalSystemMessageLifetime = account.getAttr(Provisioning.A_zimbraMailMessageLifetime);
        
        mOriginalUserInboxReadLifetime = account.getAttr(Provisioning.A_zimbraPrefInboxReadLifetime);
        mOriginalUserInboxUnreadLifetime = account.getAttr(Provisioning.A_zimbraPrefInboxUnreadLifetime);
        mOriginalUserSentLifetime = account.getAttr(Provisioning.A_zimbraPrefSentLifetime);
        mOriginalUserTrashLifetime = account.getAttr(Provisioning.A_zimbraPrefTrashLifetime);
        mOriginalUserJunkLifetime = account.getAttr(Provisioning.A_zimbraPrefJunkLifetime);
        
        mOriginalUseChangeDateForTrash =
            account.getAttr(Provisioning.A_zimbraMailPurgeUseChangeDateForTrash);
    }
    
    /**
     * Tests the user retention policy for the <tt>Inbox</tt> folder.
     */
    public void testInbox()
    throws Exception {
        // Set retention policy
        Account account = TestUtil.getAccount(USER_NAME);
        Map<String, Object> attrs = new HashMap<String, Object>();
        attrs.put(Provisioning.A_zimbraPrefInboxUnreadLifetime, "24h");
        attrs.put(Provisioning.A_zimbraPrefInboxReadLifetime, "16h");
        Provisioning.getInstance().modifyAttrs(account, attrs);
        
        // Insert messages
        String prefix = NAME_PREFIX + " testInbox ";
        Mailbox mbox = TestUtil.getMailbox(USER_NAME);
        Message purgedUnread = TestUtil.addMessage(mbox, Mailbox.ID_FOLDER_INBOX, prefix + "purgedUnread",
            System.currentTimeMillis() - (25 * Constants.MILLIS_PER_HOUR));
        Message keptUnread = TestUtil.addMessage(mbox, Mailbox.ID_FOLDER_INBOX, prefix + "keptUnread",
            System.currentTimeMillis() - (18 * Constants.MILLIS_PER_HOUR));
        Message purgedRead = TestUtil.addMessage(mbox, Mailbox.ID_FOLDER_INBOX, prefix + "purgedRead",
            System.currentTimeMillis() - (18 * Constants.MILLIS_PER_HOUR));
        Message keptRead = TestUtil.addMessage(mbox, Mailbox.ID_FOLDER_INBOX, prefix + "keptRead",
            System.currentTimeMillis() - (15 * Constants.MILLIS_PER_HOUR));
        
        // Mark read/unread and refresh
        purgedUnread = alterUnread(purgedUnread, true);
        keptUnread = alterUnread(keptUnread, true);
        purgedRead = alterUnread(purgedRead, false);
        keptRead = alterUnread(keptRead, false);
        
        // Run purge and verify results
        mbox.purgeMessages(null);
        assertFalse("purgedUnread was kept", messageExists(purgedUnread.getId()));
        assertTrue("keptUnread was purged", messageExists(keptUnread.getId()));
        assertFalse("purgedRead was kept", messageExists(purgedRead.getId()));
        assertTrue("keptRead was purged", messageExists(keptRead.getId()));
    }

    /**
     * Tests the user retention policy for the <tt>Sent</tt> folder.
     */
    public void testSent()
    throws Exception {
        // Set retention policy
        Account account = TestUtil.getAccount(USER_NAME);
        Map<String, Object> attrs = new HashMap<String, Object>();
        attrs.put(Provisioning.A_zimbraPrefSentLifetime, "24h");
        Provisioning.getInstance().modifyAttrs(account, attrs);
        
        // Insert messages
        String prefix = NAME_PREFIX + " testSent ";
        Mailbox mbox = TestUtil.getMailbox(USER_NAME);
        Message purged = TestUtil.addMessage(mbox, Mailbox.ID_FOLDER_SENT, prefix + "purged",
            System.currentTimeMillis() - (25 * Constants.MILLIS_PER_HOUR));
        Message kept = TestUtil.addMessage(mbox, Mailbox.ID_FOLDER_SENT, prefix + "kept",
            System.currentTimeMillis() - (18 * Constants.MILLIS_PER_HOUR));
        
        // Run purge and verify results
        mbox.purgeMessages(null);
        assertFalse("purged was kept", messageExists(purged.getId()));
        assertTrue("kept was purged", messageExists(kept.getId()));
    }
    
    /**
     * Confirms that a shorter user trash lifetime setting overrides the
     * system setting.
     */
    public void testTrashUser()
    throws Exception {
        // Use the item date for purge.
        TestUtil.setAccountAttr(USER_NAME, Provisioning.A_zimbraMailPurgeUseChangeDateForTrash, LdapUtil.LDAP_FALSE);
        
        // Set retention policy
        Account account = TestUtil.getAccount(USER_NAME);
        Map<String, Object> attrs = new HashMap<String, Object>();
        attrs.put(Provisioning.A_zimbraPrefTrashLifetime, "24h");
        attrs.put(Provisioning.A_zimbraMailTrashLifetime, "48h");
        Provisioning.getInstance().modifyAttrs(account, attrs);
        
        // Insert messages
        String prefix = NAME_PREFIX + " testTrashUser ";
        Mailbox mbox = TestUtil.getMailbox(USER_NAME);
        Message purged = TestUtil.addMessage(mbox, Mailbox.ID_FOLDER_TRASH, prefix + "purged",
            System.currentTimeMillis() - (36 * Constants.MILLIS_PER_HOUR));
        Message kept = TestUtil.addMessage(mbox, Mailbox.ID_FOLDER_TRASH, prefix + "kept",
            System.currentTimeMillis() - (16 * Constants.MILLIS_PER_HOUR));
        
        // Run purge and verify results
        mbox.purgeMessages(null);
        assertFalse("purged was kept", messageExists(purged.getId()));
        assertTrue("kept was purged", messageExists(kept.getId()));
    }
    
    /**
     * Confirms that a shorter system trash lifetime setting overrides the
     * user setting.
     */
    public void testTrashSystem()
    throws Exception {
        // Use the item date for purge.
        TestUtil.setAccountAttr(USER_NAME, Provisioning.A_zimbraMailPurgeUseChangeDateForTrash, LdapUtil.LDAP_FALSE);

        // Set retention policy
        Account account = TestUtil.getAccount(USER_NAME);
        Map<String, Object> attrs = new HashMap<String, Object>();
        attrs.put(Provisioning.A_zimbraPrefTrashLifetime, "48h");
        attrs.put(Provisioning.A_zimbraMailTrashLifetime, "24h");
        Provisioning.getInstance().modifyAttrs(account, attrs);
        
        // Insert messages
        String prefix = NAME_PREFIX + " testTrashUser ";
        Mailbox mbox = TestUtil.getMailbox(USER_NAME);
        Message purged = TestUtil.addMessage(mbox, Mailbox.ID_FOLDER_TRASH, prefix + "purged",
            System.currentTimeMillis() - (36 * Constants.MILLIS_PER_HOUR));
        Message kept = TestUtil.addMessage(mbox, Mailbox.ID_FOLDER_TRASH, prefix + "kept",
            System.currentTimeMillis() - (16 * Constants.MILLIS_PER_HOUR));
        
        // Run purge and verify results
        mbox.purgeMessages(null);
        assertFalse("purged was kept", messageExists(purged.getId()));
        assertTrue("kept was purged", messageExists(kept.getId()));
    }
    
    /**
     * Confirms that a shorter user Junk lifetime setting overrides the
     * system setting.
     */
    public void testJunkUser()
    throws Exception {
        // Set retention policy
        Account account = TestUtil.getAccount(USER_NAME);
        Map<String, Object> attrs = new HashMap<String, Object>();
        attrs.put(Provisioning.A_zimbraPrefJunkLifetime, "24h");
        attrs.put(Provisioning.A_zimbraMailSpamLifetime, "48h");
        Provisioning.getInstance().modifyAttrs(account, attrs);
        
        // Insert messages
        String prefix = NAME_PREFIX + " testJunkUser ";
        Mailbox mbox = TestUtil.getMailbox(USER_NAME);
        Message purged = TestUtil.addMessage(mbox, Mailbox.ID_FOLDER_SPAM, prefix + "purged",
            System.currentTimeMillis() - (36 * Constants.MILLIS_PER_HOUR));
        Message kept = TestUtil.addMessage(mbox, Mailbox.ID_FOLDER_SPAM, prefix + "kept",
            System.currentTimeMillis() - (16 * Constants.MILLIS_PER_HOUR));
        
        // Run purge and verify results
        mbox.purgeMessages(null);
        assertFalse("purged was kept", messageExists(purged.getId()));
        assertTrue("kept was purged", messageExists(kept.getId()));
    }
    
    /**
     * Confirms that a shorter system Junk lifetime setting overrides the
     * user setting.
     */
    public void testJunkSystem()
    throws Exception {
        // Set retention policy
        Account account = TestUtil.getAccount(USER_NAME);
        Map<String, Object> attrs = new HashMap<String, Object>();
        attrs.put(Provisioning.A_zimbraPrefJunkLifetime, "48h");
        attrs.put(Provisioning.A_zimbraMailSpamLifetime, "24h");
        Provisioning.getInstance().modifyAttrs(account, attrs);
        
        // Insert messages
        String prefix = NAME_PREFIX + " testJunkUser ";
        Mailbox mbox = TestUtil.getMailbox(USER_NAME);
        Message purged = TestUtil.addMessage(mbox, Mailbox.ID_FOLDER_SPAM, prefix + "purged",
            System.currentTimeMillis() - (36 * Constants.MILLIS_PER_HOUR));
        Message kept = TestUtil.addMessage(mbox, Mailbox.ID_FOLDER_SPAM, prefix + "kept",
            System.currentTimeMillis() - (16 * Constants.MILLIS_PER_HOUR));
        
        // Run purge and verify results
        mbox.purgeMessages(null);
        assertFalse("purged was kept", messageExists(purged.getId()));
        assertTrue("kept was purged", messageExists(kept.getId()));
    }
    
    /**
     * Tests the user retention policy for all messages.
     */
    public void testAll()
    throws Exception {
        // Set retention policy
        Account account = TestUtil.getAccount(USER_NAME);
        Map<String, Object> attrs = new HashMap<String, Object>();
        attrs.put(Provisioning.A_zimbraMailMessageLifetime, "40d");
        Provisioning.getInstance().modifyAttrs(account, attrs);
        
        // Insert messages
        String prefix = NAME_PREFIX + " testAll ";
        Mailbox mbox = TestUtil.getMailbox(USER_NAME);
        Folder folder = mbox.createFolder(null, "/" + NAME_PREFIX, (byte) 0, MailItem.TYPE_UNKNOWN);
        Message purged = TestUtil.addMessage(mbox, folder.getId(), prefix + "purged",
            System.currentTimeMillis() - (41 * Constants.MILLIS_PER_DAY));
        Message kept = TestUtil.addMessage(mbox, folder.getId(), prefix + "kept",
            System.currentTimeMillis() - (39 * Constants.MILLIS_PER_DAY));
        
        // Run purge and verify results
        mbox.purgeMessages(null);
        assertFalse("purged was kept", messageExists(purged.getId()));
        assertTrue("kept was purged", messageExists(kept.getId()));
    }
    
    /**
     * Tests the safeguard for the mailbox-wide message retention policy.
     */
    public void testAllSafeguard()
    throws Exception {
        // Set retention policy
        Account account = TestUtil.getAccount(USER_NAME);
        Map<String, Object> attrs = new HashMap<String, Object>();
        attrs.put(Provisioning.A_zimbraMailMessageLifetime, "1h");
        Provisioning.getInstance().modifyAttrs(account, attrs);
        
        // Insert messages
        String prefix = NAME_PREFIX + " testAllSafeguard ";
        Mailbox mbox = TestUtil.getMailbox(USER_NAME);
        Folder folder = mbox.createFolder(null, "/" + NAME_PREFIX, (byte) 0, MailItem.TYPE_UNKNOWN);
        Message purged = TestUtil.addMessage(mbox, folder.getId(), prefix + "purged",
            System.currentTimeMillis() - (32 * Constants.MILLIS_PER_DAY));
        Message kept = TestUtil.addMessage(mbox, folder.getId(), prefix + "kept",
            System.currentTimeMillis() - (30 * Constants.MILLIS_PER_DAY));
        
        // Run purge and verify results
        mbox.purgeMessages(null);
        assertFalse("purged was kept", messageExists(purged.getId()));
        assertTrue("kept was purged", messageExists(kept.getId()));
    }
    
    /**
     * Confirms that messages are purged from trash based on the value of
     * <tt>zimbraMailPurgeUseChangeDateForTrash<tt>.  See bug 19702 for more details.
     */
    public void testTrashChangeDate()
    throws Exception {
        // Set retention policy
        TestUtil.setAccountAttr(USER_NAME, Provisioning.A_zimbraPrefTrashLifetime, "24h");
        TestUtil.setAccountAttr(USER_NAME, Provisioning.A_zimbraMailPurgeUseChangeDateForTrash, LdapUtil.LDAP_TRUE);
        
        // Insert message
        String subject = NAME_PREFIX + " testChangeDate";
        Mailbox mbox = TestUtil.getMailbox(USER_NAME);
        Message kept = TestUtil.addMessage(mbox, Mailbox.ID_FOLDER_INBOX, subject,
            System.currentTimeMillis() - (36 * Constants.MILLIS_PER_HOUR));
        mbox.move(null, kept.getId(), MailItem.TYPE_MESSAGE, Mailbox.ID_FOLDER_TRASH);
        
        // Validate dates
        long cutoff = System.currentTimeMillis() - Constants.MILLIS_PER_DAY;
        assertTrue("Unexpected message date: " + kept.getDate(),
            kept.getDate() < cutoff);
        assertTrue("Unexpected change date: " + kept.getChangeDate(),
            kept.getChangeDate() > cutoff);
        
        // Run purge and verify results
        mbox.purgeMessages(null);
        assertTrue("kept was purged", messageExists(kept.getId()));
    }
    
    private Message alterUnread(Message msg, boolean unread)
    throws Exception {
        Mailbox mbox = TestUtil.getMailbox(USER_NAME);
        mbox.alterTag(null, msg.getId(), msg.getType(), Flag.ID_FLAG_UNREAD, unread);
        return mbox.getMessageById(null, msg.getId());
    }
    
    private boolean messageExists(int id)
    throws Exception {
        Mailbox mbox = TestUtil.getMailbox(USER_NAME);
        try {
            mbox.getMessageById(null, id);
        } catch (ServiceException e) {
            assertTrue("Unexpected exception type: " + e, e instanceof NoSuchItemException);
            return false;
        }
        return true;
    }
    
    public void tearDown()
    throws Exception {
        cleanUp();
        
        Account account = TestUtil.getAccount(USER_NAME);
        Map<String, Object> attrs = new HashMap<String, Object>();
        attrs.put(Provisioning.A_zimbraMailTrashLifetime, mOriginalSystemTrashLifetime);
        attrs.put(Provisioning.A_zimbraMailSpamLifetime, mOriginalSystemJunkLifetime);
        attrs.put(Provisioning.A_zimbraMailMessageLifetime, mOriginalSystemMessageLifetime);
        attrs.put(Provisioning.A_zimbraPrefInboxReadLifetime, mOriginalUserInboxReadLifetime);
        attrs.put(Provisioning.A_zimbraPrefInboxUnreadLifetime, mOriginalUserInboxUnreadLifetime);
        attrs.put(Provisioning.A_zimbraPrefSentLifetime, mOriginalUserSentLifetime);
        attrs.put(Provisioning.A_zimbraPrefTrashLifetime, mOriginalUserTrashLifetime);
        attrs.put(Provisioning.A_zimbraPrefJunkLifetime, mOriginalUserJunkLifetime);
        attrs.put(Provisioning.A_zimbraMailPurgeUseChangeDateForTrash, mOriginalUseChangeDateForTrash);
        Provisioning.getInstance().modifyAttrs(account, attrs);
    }
    
    private void cleanUp()
    throws Exception {
        TestUtil.deleteTestData(USER_NAME, NAME_PREFIX);
    }
}
