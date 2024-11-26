/*
  Copyright (C) 2007-2024 Helge Hess

  This file is part of OpenGroupware.org (OGo)

  OGo is free software; you can redistribute it and/or modify it under
  the terms of the GNU General Public License as published by the
  Free Software Foundation; either version 2, or (at your option) any
  later version.

  OGo is distributed in the hope that it will be useful, but WITHOUT ANY
  WARRANTY; without even the implied warranty of MERCHANTABILITY or
  FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public
  License for more details.

  You should have received a copy of the GNU General Public
  License along with OGo; see the file COPYING.  If not, write to the
  Free Software Foundation, 59 Temple Place - Suite 330, Boston, MA
  02111-1307, USA.
*/
package org.opengroupware.logic.db;

import java.io.File;
import java.util.List;
import java.util.Map;

import javax.security.auth.login.LoginContext;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.getobjects.eoaccess.EOAdaptor;
import org.getobjects.eoaccess.EODatabase;
import org.getobjects.foundation.NSClassLookupContext;
import org.opengroupware.logic.auth.OGoLoginModule;
import org.opengroupware.logic.blobs.IOGoBlobStore;
import org.opengroupware.logic.blobs.OGoFlatDirBlobStore;
import org.opengroupware.logic.blobs.OGoRangeDirBlobStore;

/**
 * OGoDatabase
 * <p>
 * Wraps a connection to the OGo database.
 * <p>
 * Note that the 'OGo' login process is managed by the Java JAAS subsystem. An
 * OGo database-login backend is implemented in the
 * OGoLoginModule class,
 * which also contains a convenience function to acquire a JAAS LoginContext.
 * 
 * <h4>Creating an OGoDatabase</h4>
 * <p>
 * An OGoDatabase needs an EOAdaptor with a proper EOModel, plus the path
 * to the filesystem folder which stores OGo documents and notes.
 * There is also a convenience method:
 * <pre>
 *   OGoDatabase db = OGoDatabase.databaseForURL(
 *     "jdbc:postgresql://127.0.0.1/OGoDB?user=OGo&password=OGo",
 *     "/var/lib/opengroupware.org/documents");
 *   
 *   // login
 *   LoginContext lc = OGoLoginModule.jaasLogin(db, "joe", "user");
 * </pre>
 */
public class OGoDatabase extends EODatabase
  implements NSClassLookupContext
{
  protected static final Log log = LogFactory.getLog("OGoDatabase");
  
  /* shared, immutable state */
  protected IOGoBlobStore     docsStore;
  protected IOGoBlobStore     notesStore;
  protected IOGoBlobStore     defaultsStore;
  protected IOGoBlobStore     iconStore;
  
  /* construct */
  
  public OGoDatabase(final EOAdaptor _adaptor, final File LSAttachmentPath) {
    super(_adaptor, null /* class lookup */);
    
    //File LSAttachmentPath = ;
    if (LSAttachmentPath == null || !LSAttachmentPath.exists()) {
      log.error("blob storage does not exist: " + LSAttachmentPath);
    }
    else {
      this.docsStore     = new OGoRangeDirBlobStore(LSAttachmentPath);
      this.notesStore    = new OGoFlatDirBlobStore(LSAttachmentPath);
      this.defaultsStore = new OGoFlatDirBlobStore(LSAttachmentPath);
      this.iconStore     = new OGoFlatDirBlobStore(LSAttachmentPath,".picture");
    }
  }

  /**
   * Retrieves an EOAdaptor for the given database URL and initializes a new
   * OGoDatabase object with it.
   * 
   * @param _dbURL   - the JDBC URL to the OGo database
   * @param _docPath - the path where OGo BLOBs are stored
   * @return null if no adaptor could be initialized, or the OGoDatabase
   */
  public static OGoDatabase databaseForURL(String _dbURL, String _docPath) {
    EOAdaptor adaptor = dbAdaptorForURL(OGoDatabase.class, _dbURL);
    if (adaptor == null)
      return null;
    
    final File LSAttachmentPath = (_docPath == null)
      ? new File("/var/lib/opengroupware.org/documents")
      : new File(_docPath);
    
    return new OGoDatabase(adaptor, LSAttachmentPath);
  }
  
  
  /* BLOB store access */
  
  public IOGoBlobStore docsStore() {
    return this.docsStore;
  }
  public IOGoBlobStore notesStore() {
    return this.notesStore;
  }
  public IOGoBlobStore defaultsStore() {
    return this.defaultsStore;
  }
  public IOGoBlobStore iconStore() {
    return this.iconStore;
  }
  
  
  /* primary keys */
  
  protected int keyCount; // Note: nextPrimaryKey is synchronized
  protected int nextKey;
  
  /**
   * Generates a new primary key, from the global OGo primary key sequence. The
   * sequence generates keys in batches of 10, so calling this for a set of
   * objects is reasonably cheap.
   * 
   * @return a new, unqiue, primary key
   */
  public Number nextPrimaryKey() {
    // TBD: move to EOAccess/EOModel
    // TBD: this is a bit confusing! Actually we would need to count in the
    //      reverse. Eg if we called nextval('key_generator') and it returned
    //        37
    //      we actually consumed the values 28...37.
    // TBD: what we can't easily change this w/o compat issues?
    final int sequenceCount = 10; // specified in CREATE SEQUENCE of OGo Schema
    final String keySQL = "SELECT nextval('key_generator');";
    
    final EOAdaptor ad = this.adaptor();
    if (ad == null) {
      log.error("database has no adaptor?");
      return null;
    }

    Number key = null;
    
    synchronized (this) {
      if (this.keyCount > 0) { /* key available in cache */
        int ikey = this.nextKey;
        this.keyCount--;
        this.nextKey++;
        return Integer.valueOf(ikey);
      }
      
      // hola, SQL inside synchronized, not too good ...
      final List<Map<String, Object>> results = ad.performSQL(keySQL);
      if (results == null || results.size() != 1) {
        log.error("could not fetch new primary key: " + results);
        return null;
      }
      
      // TBD: wow, what a crap :-)
      if ((key = (Number)(results.get(0).values().iterator().next())) != null) {
        this.keyCount = (sequenceCount - 1 /* we consume one */);
        this.nextKey  = key.intValue() + 1;
      }
    }
    return key;
  }
  
  /**
   * This method is for grabbing a large number of primary keys in bulk
   * INSERTs.
   * 
   * @param _count - number of primary keys to grab
   * @return an array of primary keys, or null if _count smaller than 0
   */
  public Number[] grabPrimaryKeys(int _count) {
    if (_count < 0)
      return null;
    
    // TBD: optimize for bulk-grabs
    final Number[] pkeys = new Number[_count];
    for (_count--; _count >= 0; _count--) {
      if ((pkeys[_count] = this.nextPrimaryKey()) == null)
        return null; /* abort on a single failure */
    }
    return pkeys;
  }
  
  /* login */
  
  /**
   * Returns a OGoLoginContext for the given user/password. This just passes the
   * request on to the authenticator associated with this database instance.
   * <p>
   * Deprecated: use OGoLoginModule.jaasLogin instead.
   * 
   * @param _user - the login name
   * @param _pwd  - the password
   * @return an OGoLoginContext or null if the login failed
   */
  @Deprecated // use OGoLoginModule.jaasLogin instead
  public LoginContext login(String _user, String _pwd) {
    return OGoLoginModule.jaasLogin(this, _user, _pwd);
  }
  
  /* datasources */

  public OGoSessionLogs sessionLogs() {
    return (OGoSessionLogs)this.dataSourceForEntity("SessionLogs");
  }
  public OGoObjectLogs objectLogs() {
    return (OGoObjectLogs)this.dataSourceForEntity("ObjectLogs");
  }
  public OGoObjectLinks objectLinks() {
    return (OGoObjectLinks)this.dataSourceForEntity("ObjectLinks");
  }
  public OGoACLEntries aclEntries() {
    return (OGoACLEntries)this.dataSourceForEntity("ACLEntries");
  }
  
  public OGoAccounts accounts() {
    return (OGoAccounts)this.dataSourceForEntity("Accounts");
  }
  
  public OGoTeams teams() {
    return (OGoTeams)this.dataSourceForEntity("Teams");
  }
  
  public OGoPersons persons() {
    return (OGoPersons)this.dataSourceForEntity("Persons");
  }
  
  public OGoCompanies companies() {
    return (OGoCompanies)this.dataSourceForEntity("Companies");
  }
  
  public OGoEvents events() {
    return (OGoEvents)this.dataSourceForEntity("Events");
  }
  public OGoEvents events(String _fetchSpec, Object _bindings) {
    OGoEvents ds = this.events();
    ds.setFetchSpecificationByName(_fetchSpec);
    ds.setQualifierBindings(_bindings);
    return ds;
  }
  
  public OGoTasks tasks() {
    return (OGoTasks)this.dataSourceForEntity("Tasks");
  }
  
  public OGoProjects projects() {
    return (OGoProjects)this.dataSourceForEntity("Projects");
  }
  
  public OGoDocuments documents() {
    return (OGoDocuments)this.dataSourceForEntity("Documents");
  }
  public OGoNotes notes() {
    return (OGoNotes)this.dataSourceForEntity("Notes");
  }
}
