/*
  Copyright (C) 2007-2014 Helge Hess

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
package org.opengroupware.logic.core;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.security.auth.Subject;
import javax.security.auth.login.LoginContext;

import org.getobjects.eoaccess.EOAccessDataSource;
import org.getobjects.eoaccess.EOActiveDataSource;
import org.getobjects.eoaccess.EOAdaptor;
import org.getobjects.eoaccess.EOAdaptorDataSource;
import org.getobjects.eoaccess.EODatabase;
import org.getobjects.eoaccess.EODatabaseContext;
import org.getobjects.eoaccess.EODatabaseDataSource;
import org.getobjects.eoaccess.EOEnterpriseObject;
import org.getobjects.eoaccess.EOEntity;
import org.getobjects.eocontrol.EOAndQualifier;
import org.getobjects.eocontrol.EOEditingContext;
import org.getobjects.eocontrol.EOFetchSpecification;
import org.getobjects.eocontrol.EOGlobalID;
import org.getobjects.eocontrol.EOKeyValueQualifier;
import org.getobjects.eocontrol.EONotQualifier;
import org.getobjects.eocontrol.EOObjectStore;
import org.getobjects.eocontrol.EOObjectTrackingContext;
import org.getobjects.eocontrol.EOQualifier;
import org.getobjects.eocontrol.EOSortOrdering;
import org.getobjects.foundation.NSException;
import org.getobjects.foundation.NSJavaRuntime;
import org.getobjects.foundation.UList;
import org.getobjects.foundation.UMap;
import org.getobjects.foundation.UObject;
import org.getobjects.foundation.UString;
import org.opengroupware.logic.auth.IOGoPrincipal;
import org.opengroupware.logic.auth.OGoAccountPrincipal;
import org.opengroupware.logic.auth.OGoLoginModule;
import org.opengroupware.logic.auth.OGoTeamPrincipal;
import org.opengroupware.logic.authz.OGoAuthzFetchContext;
import org.opengroupware.logic.db.OGoACLEntries;
import org.opengroupware.logic.db.OGoAccounts;
import org.opengroupware.logic.db.OGoCompanies;
import org.opengroupware.logic.db.OGoDataSource;
import org.opengroupware.logic.db.OGoDatabase;
import org.opengroupware.logic.db.OGoDocuments;
import org.opengroupware.logic.db.OGoEvents;
import org.opengroupware.logic.db.OGoNotes;
import org.opengroupware.logic.db.OGoObject;
import org.opengroupware.logic.db.OGoObjectLinks;
import org.opengroupware.logic.db.OGoObjectLogs;
import org.opengroupware.logic.db.OGoPersons;
import org.opengroupware.logic.db.OGoProjects;
import org.opengroupware.logic.db.OGoResultSet;
import org.opengroupware.logic.db.OGoSessionLogs;
import org.opengroupware.logic.db.OGoTasks;
import org.opengroupware.logic.db.OGoTeams;
import org.opengroupware.logic.ops.OGoOperationTransaction;

/**
 * OGoObjectContext
 * <p>
 * The idea behind this EOEditingContext subclass is that after a fetch we have
 * all the fetched objects in one common place and can then begin to work on
 * that object graph to perform authorization operations in an efficient way.
 * <br>
 * For example we might have fetched a set of persons, a set of companies and
 * a set of projects. After we did the fetch, we can walk over all of them and
 * process the fields required for authentication. Blanking out values in
 * contact records or fetching ACLs in a bulk way.
 * <p>
 * Note that fetches should still contain basic authentication limits, eg only
 * fetch contacts which belong to you or are public. Otherwise a subsequent
 * authentication step might *remove* the fetch results because other
 * constraints apply (eg an ACL). This is mostly relevant for OFFSET/LIMIT
 * fetches (because the LIMIT might only give you objects which you are not
 * allowed to access).
 * 
 * <h4>Creating an OGoObjectContext</h4>
 * <p>
 * In a Go web application an OGoObjectContext is often created in the Context
 * objects, that is, it has the live time of a single HTTP request.
 * Its not recommended to preserve the context for longer, as the information
 * might get out of date and consume a significant amount of RAM.
 * <br>
 * To create an OGoObjectContext you need to acquire a JAAS LoginContext and
 * an EODatabase object.
 * <pre>
 *   EODatabase db = OGoDatabase.databaseForURL(
 *     "jdbc:postgresql://127.0.0.1/OGoDB?user=OGo&password=OGo",
 *     "/var/lib/opengroupware.org/documents");
 *   
 *   LoginContext      lc = OGoLoginModule.jaasLogin(db, "joe", "user");
 *   EODatabaseContext dc = new EODatabaseContext(db);
 *   OGoObjectContext  oc = new OGoObjectContext(dc, lc);
 * </pre>
 * 
 * <p>
 * THREAD: An object/editing context is for use in one thread only. Be careful.
 * 
 * <p>
 * @author helge
 * @see OGoLoginSetPrincipal
 */
public class OGoObjectContext extends EOEditingContext {
  // TBD: rebase to EOObjectTrackingContext?
  
  final protected LoginContext     loginContext;
  protected Map<EOGlobalID,String> gidToPermission;
  protected boolean                autoFetchPermissions;
  protected boolean                autoApplyPermissions;

  protected Number   actorID;
  protected Number[] authenticatedIDs;

  public OGoObjectContext(EOObjectStore _parentStore, LoginContext _login) {
    super(_parentStore);
    
    // TBD: forbid init w/o a valid subject?
    this.loginContext = _login;
    if (this.loginContext == null)
      log.warn("initialized OGoObjectContext w/o a LoginContext!");
    else {
      Subject subject = this.loginContext.getSubject();
      if (subject == null)
        log.warn("LoginContext has no Subject: " + this.loginContext);
      else if (subject.getPrincipals().size() == 0)
        log.warn("LoginContext Subject has no Principals: "+ this.loginContext);
    }
    
    this.gidToPermission = new HashMap<EOGlobalID, String>(128);
    this.autoFetchPermissions = true;
    this.autoApplyPermissions = true;
  }
  
  /* authentication */
  
  /**
   * Returns the JAAS authentication context. We need to store it (not use the
   * Subject), so that we can logout a user.
   * 
   * @return the JAAS LoginContext
   */
  public LoginContext loginContext() {
    return this.loginContext;
  }
  /**
   * Returns the JAAS Subject. The Subject contains the principals authorized
   * in the JAAS login process. Those include {@link OGoTeamPrincipal} and
   * {@link OGoAccountPrincipal} objects.
   * 
   * @return the authenticated Subject, or null
   */
  public Subject subject() {
    return this.loginContext != null ? this.loginContext.getSubject() : null;
  }
  
  /**
   * Returns the database ID of the primary JAAS Principal.
   * 
   * @return primary key of primary login account, or null
   */
  public Number actorID() {
    if (this.actorID != null)
      return this.actorID;
    
    Subject subject = this.subject();
    if (subject == null) return null;
    
    Collection<OGoAccountPrincipal> principals = 
      subject.getPrincipals(OGoAccountPrincipal.class);
    if (principals == null)
      return null;
    
    // TBD: improve me, add support for delegation (distinguish primary
    //      account from 'delegates', hm, would be distinct OGoDelegatePrincipal
    //      objects?)
    for (OGoAccountPrincipal account: principals) {
      if (this.actorID == null)
        this.actorID = account.id();
      else if (this.actorID.equals(account.id())) {
        log().warn("multiple account principals with the same ID in Subject: " +
            subject);
      }
      else
        log().warn("multiple account principals in Subject: " + subject);
    }
    return this.actorID;
  }
  
  /**
   * Returns the primary keys of the OGo accounts/teams which got authenticated.
   * Its often used in queries to check permissions on the DB server.
   * 
   * @return array of primary keys of authenticated IDs
   */
  public Number[] authenticatedIDs() {
    if (this.authenticatedIDs != null)
      return this.authenticatedIDs;
    
    Subject subject = this.subject();
    if (subject == null) return null;
    
    Collection<IOGoPrincipal> principals = 
      subject.getPrincipals(IOGoPrincipal.class);
    if (principals == null) principals = new ArrayList<IOGoPrincipal>(1);
    
    // TBD: also check some database-ID against the principals (so that a
    //      single subject can contain tokens of different databases)
    int i = 0, count = principals.size();
    this.authenticatedIDs = new Number[count];
    for (IOGoPrincipal p: principals) {
      this.authenticatedIDs[i] = p.id();
      i++;
    }
    return this.authenticatedIDs;
  }
  
  
  /* authorization */
  
  public void setAutoFetchPermissions(final boolean _flag) {
    this.autoFetchPermissions = _flag;
  }
  public boolean autoFetchPermissions() {
    return this.autoFetchPermissions;
  }
  
  
  /* access to underlying database */
  
  public EODatabase database() {
    final EOObjectStore rs = this.rootObjectStore();
    return (rs instanceof EODatabaseContext)
      ? ((EODatabaseContext)rs).database() : null;
  }
  public OGoDatabase oDatabase() {
    final EODatabase db = this.database();
    return (db instanceof OGoDatabase) ? (OGoDatabase)db : null;
  }
  
  
  /* permission handling */
  
  public String permissionsForGlobalID(final EOGlobalID _gid) {
    return _gid != null ? this.gidToPermission.get(_gid) : null;
  }
  
  public String permissionsForObject(final Object _o) {
    return this.permissionsForGlobalID(this.globalIDForObject(_o));
  }

  // hh(2024-11-29): Not actually used anywhere?
  public List<?> listByRemovingForbiddenObjects(final List<?> _objects) {
    if (_objects == null)
      return null;
    
    final int  count   = _objects.size();
    final List<Object> newList = new ArrayList<Object>(count); 
    
    for (int i = 0; i < count; i++) {
      final Object o = _objects.get(i);
      if (o == null)
        continue;
      
      if (o instanceof OGoObject) {
        if (((OGoObject)o).isForbidden())
          continue;
      }
      
      newList.add(o);
    }
    return newList;
  }

  
  /**
   * This method is overridden to perform automatic permission checks on the
   * result set returned by the parent method.
   */
  @SuppressWarnings("rawtypes")
  @Override
  public List objectsWithFetchSpecification
    (final EOFetchSpecification _fs, final EOObjectTrackingContext _ec)
  {
    final boolean debugPerf = perflog.isDebugEnabled();
    if (debugPerf) perflog.debug("OC: objectsWithFetchSpecification() ...");
    
    /* make editing context fetch out objects */
    
    final List results = super.objectsWithFetchSpecification(_fs, _ec);
    if (debugPerf) perflog.debug("OC:   got: " +
        (results != null ? results.size() : ""));
    
    if (_fs == null || !_fs.fetchesRawRows()) {
      if (this.autoFetchPermissions && results != null && results.size() > 0)
        this.processPermissionsAfterFetch(_fs, results);
    }
    else
      log.info("not processing permissions on raw-rows fetch.");
    
    /* Note: We intentionally do NOT remove results. Even 403 results are
     *       relevant, eg to detect fetches which reached a LIMIT.
     */
    if (debugPerf) perflog.debug("OC: done: " +
        (results != null ? results.size() : ""));
    return results;
  }
  
  /**
   * This method derives or fetches the permissions of the objects contained in
   * the 'results' list, or if a prefetch was done as part of the query, the
   * permissions of the whole context.
   * The fetched permissions are cached in the 'gidToPermission' Map of the
   * context.
   * <p>
   * To perform the permission fetch the object creates and triggers an
   * OGoAuthzFetchContext object which is responsible to perform the fetch
   * as efficiently as possible.
   * <p>
   * If the 'autoApplyPermissions' option is set (default), the fetched
   * permissions will then get applied on the objects. Eg this could remove
   * fields of an object if the user has no permission to see those. 
   * 
   * @param _fs - the associated fetch specification (can be null)
   * @param results - the objects to be checked (null = all objects are checked)
   */
  @SuppressWarnings("rawtypes")
  public void processPermissionsAfterFetch
    (final EOFetchSpecification _fs, final List results)
  {
    final boolean debugPerf = perflog.isDebugEnabled();
    if (debugPerf) perflog.debug("OC: processPermissionsAfterFetch() ...");

    /* determine objects to check */
    
    Collection checkObjects; 
    String[] pre = _fs != null ? _fs.prefetchingRelationshipKeyPaths() : null;
    if (results == null || (pre != null && pre.length > 0)) {
      /* There where prefetches of other objects, so we recheck all objects in
       * the context. */
      checkObjects = this.registeredObjects();
    }
    else {
      /* We had no prefetches, so our result set is exactly what we added to the
       * editing context. */
      checkObjects = results;
    }
    
    /* fetch permissions */

    this.fetchPermissionsForGlobalIDs
      (this.globalIDsForObjects(checkObjects.toArray()));
    
    /* apply permissions */

    if (this.autoApplyPermissions) {
      if (debugPerf) perflog.debug("OC:   apply perms ...");
      for (Object eo: checkObjects) {
        if (!(eo instanceof OGoObject)) {
          log.warn("cannot apply permissions on object: " + eo);
          continue;
        }

        ((OGoObject)eo).enforcePermissions(this.permissionsForObject(eo));
      }
      if (debugPerf) perflog.debug("OC:   did apply perms.");
    }
  }
  
  /**
   * Instantiates a new OGoAuthzFetchContext and uses it to fetch the
   * permissions for the given global-IDs into this OGoObjectContext.
   * <p>
   * This is called by OGoOperationTransaction.fetchAndCheckPermissions() and
   * by processPermissionsAfterFetch().
   * 
   * @param _gids - the EOGlobalIDs to fetch permissions for
   */
  public void fetchPermissionsForGlobalIDs(final EOGlobalID[] _gids) {
    if (_gids == null || _gids.length == 0)
      return;
    
    OGoAuthzFetchContext authzContext =
      new OGoAuthzFetchContext(this, this.gidToPermission);
    authzContext.processPermissionsOfGlobalIDs(_gids);
    authzContext = null; /* help the GC */
    
    // TBD: we might want to run autoapply here and locate the objects using the
    //      gid
  }

  /**
   * This applies operations to the OGo database. It uses the
   * OGoOperationTransaction to maintain the process, check that class for
   * details.
   * <p>
   * After the operation the permission cache is cleared.
   * 
   * @param _ops - an array of operations to be performed
   * @return an Exception on error, or null if everything went fine
   */
  public Exception performOperationsArgs(final IOGoOperation[] _ops) {
    if (_ops == null || _ops.length == 0)
      return null; /* nothing to be done */
    
    final OGoOperationTransaction tx = new OGoOperationTransaction(this, _ops);
    final Exception error = tx.run();
    
    if (this.gidToPermission != null) {
      /* we assume that changes might change permissions ... */
      this.gidToPermission.clear();
    }
    
    return error;
  }
  /**
   * This applies operations to the OGo database. It uses the
   * OGoOperationTransaction to maintain the process, check that class for
   * details.
   * <p>
   * After the operation the permission cache is cleared.
   * 
   * @param _ops - the operations to be performed
   * @return an Exception on error, or null if everything went fine
   */
  public Exception performOperations(IOGoOperation... _ops) {
    return this.performOperationsArgs(_ops);
  }
  
  
  /* datasource factory */

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
  public OGoDataSource teamMemberships() {
    return (OGoDataSource)this.dataSourceForEntity("TeamMemberships");
  }
  
  public OGoPersons persons() {
    return (OGoPersons)this.dataSourceForEntity("Persons");
  }
  
  public OGoCompanies companies() {
    return (OGoCompanies)this.dataSourceForEntity("Companies");
  }
  public OGoDataSource employments() {
    return (OGoDataSource)this.dataSourceForEntity("Employments");
  }
  
  public OGoEvents events() {
    return (OGoEvents)this.dataSourceForEntity("Events");
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
  
  
  @SuppressWarnings("rawtypes")
  public EOAccessDataSource dataSourceForEntity(final String _ename) {
    final EODatabase db = this.database();
    if (db == null)
      return null;
    
    final EOEntity entity = db.entityNamed(_ename);
    if (entity == null)
      return null;
    
    final Class dsClass = db.dataSourceClassForEntity(entity);
    if (dsClass == null)
      return null;
    
    EOAccessDataSource ds = null;
    
    if (EOActiveDataSource.class.isAssignableFrom(dsClass)) {
      ds = (EOAccessDataSource)NSJavaRuntime.NSAllocateObject(dsClass,
          new Class[]  { EODatabase.class, String.class },
          new Object[] { db, _ename });
    }
    else if (EODatabaseDataSource.class.isAssignableFrom(dsClass)) {
      /* its preferable not to use this facility */
      ds = (EOAccessDataSource)NSJavaRuntime.NSAllocateObject(dsClass,
          new Class[]  { EOEditingContext.class, String.class },
          new Object[] { this, _ename });
    }
    else if (EOAdaptorDataSource.class.isAssignableFrom(dsClass)) {
      ds = (EOAccessDataSource)NSJavaRuntime.NSAllocateObject(dsClass,
          new Class[]  { EOAdaptor.class, EOEntity.class },
          new Object[] { db.adaptor(), entity });
    }
    else {
      log.warn("unexpected datasource class: " + dsClass);
      ds = (EOAccessDataSource)NSJavaRuntime.NSAllocateObject(dsClass);
    }
    
    if (ds == null)
      log.error("could not allocate datasource: " + dsClass);
    
    return ds;
  }
  
  
  /* more convenient fetches */
  
  /**
   * Performs a set of fetch specifications and processes the permissions of the
   * results in a bulk way to improve the performance.
   * 
   * @param _fs - the fetch specifications to perform
   * @return an array of {@link OGoResultSet}s
   */
  public OGoResultSet[] doFetch(final EOFetchSpecification[] _fs) {
    if (_fs == null)
      return null;
    
    final boolean wasPermOn = this.autoFetchPermissions();
    final OGoResultSet[] results = new OGoResultSet[_fs.length];
    
    if (_fs.length == 0)
      return results;
    
    if (_fs.length == 1) {
      results[0] = this.doFetch(_fs[0], 0);
      return results;
    }
    
    /* perform multi-fetch, turn off permissions to improve efficiency */
    // TBD: we could/should open a transaction?
    
    this.setAutoFetchPermissions(false);
    try {
      for (int i = 0; i < _fs.length; i++)
        results[i] = this.doFetch(_fs[i], 0);
    }
    finally {
      if (wasPermOn) {
        /* process permissions when required */
        log.debug("CHECK PERMS ...");
        this.processPermissionsAfterFetch(null, null);
        log.debug("DID CHECK PERMS.");
        this.setAutoFetchPermissions(true);
      }
    }
    // TBD: retries! (needs: perm-processing)
    // => retries happen when the limit was hit before perm processing
    
    return results;
  }
  
  /**
   * Performs the given fetch specifications and returns the results as an
   * {@link OGoResultSet} object.
   * 
   * @param _fs - the fetch specification to perform
   * @param _retryCount TODO
   * @return an {@link OGoResultSet}
   */
  @SuppressWarnings({"unchecked", "rawtypes"})
  public OGoResultSet doFetch(EOFetchSpecification _fs, int _retryCount) {
    if (_fs == null)
      return null;
    
    List<EOEnterpriseObject> forbidden = null;
    EOFetchSpecification org = null;
    
    final int limit = _fs != null ? _fs.fetchLimit() : 0;
    Exception error = null;
    List      results  = null;
    boolean   hitLimit = false;
    
    int fetchCount = 0;
    do {
      /* exclude forbidden objects in next fetch iteration */
      
      log.debug("OGoObjectContext.doFetch() ...");
      
      if (forbidden != null && forbidden.size() > 0) {
        log().info("retrying limited-fetch w/o forbidden objects ...");
        Collection<Number> forbiddenIds = UList.valuesForKey(forbidden, "id");
        forbidden.clear();
        
        EOQualifier q = new EOKeyValueQualifier
            ("id", EOQualifier.ComparisonOperation.CONTAINS, forbiddenIds);
        q = new EONotQualifier(q);
        
        if (org == null) {
          org = _fs;
          _fs = new EOFetchSpecification(_fs); /* copy */
        }
        _fs.conjoinQualifier(q);
      }
      
      /* perform fetch */
      
      log.debug("OGoObjectContext.objectsWithFetchSpecification() ...");
      if ((results = this.objectsWithFetchSpecification(_fs)) == null) {
        error = this.consumeLastException();
        break;
      }
      /* detect whether we hit a limit */
      hitLimit = (results != null) && (limit > 0) && (results.size() == limit);
      
      
      /* enforce check of permissions to check whether we should retry */
      
      if (_retryCount > 0) {
        log.debug("check perms ...");
        if (!this.autoFetchPermissions())
          this.processPermissionsAfterFetch(_fs, results);

        for (final Object resultObject: results) {
          if (((OGoObject)resultObject).isForbidden()) {
            if (forbidden == null)
              forbidden = new ArrayList<EOEnterpriseObject>(16);
            forbidden.add((OGoObject)resultObject);
          }
        }
        if (forbidden != null) results.removeAll(forbidden);
        log.debug("did check perms.");
      }
      
      fetchCount++;
    }
    while (hitLimit && (fetchCount <= _retryCount) &&
           (forbidden != null && forbidden.size() > 0));
    
    return (error != null)
      ? new OGoResultSet(error)
      : new OGoResultSet(results, limit, hitLimit, 
                         (_retryCount > 0) || this.autoFetchPermissions());
  }
  
  /**
   * Conveniently perform a fetch.
   * <p>
   * Special arguments
   * <ul>
   *   <li>limit     (int)
   *   <li>offset    (int)
   *   <li>prefetch  (String[])
   *   <li>distinct  (bool)
   *   <li>qualifier (EOQualifier)
   *   <li>orderings (EOSortOrdering[])
   *   <li>attributes (String[])
   * </ul>
   * You cannot use those special keys as bindings with this method.
   * <p>
   * All the other arguments are treated as bind-values.
   * 
   * <p>
   * Example<pre>
   *   OGoResultSet rs = oc.doFetch("Persons::default", "limit", 10,
   *                                "qualifier", 
   *                                  "name LIKE 'Duck*' AND id < $x",
   *                                "x", 12000);</pre>
   * 
   * @param _command - an entity(::default) or entity::fetchspec name
   * @param _args    - arguments
   * @return an {@link OGoResultSet}
   */
  public OGoResultSet doFetch(final String _command, Object... _args) {
    final EOFetchSpecification fs = 
      this.buildFetchSpecification(_command, _args);
    if (fs == null)
      return new OGoResultSet(new NSException("Could not resolve command"));
    
    /* perform fetch */
    return this.doFetch(fs, 0 /* retry count */);
  }
  
  public Number doFetchTotal(final String _command, Object... _args) {
    EOFetchSpecification fs = this.buildFetchSpecification(_command, _args);
    if (fs == null)
      return null;
    
    EOFetchSpecification cfs = fs.fetchSpecificationForCount();
    if (cfs == null) {
      log.warn("Could not derive fetch-spec for count from: " + fs);
      // expensive
      OGoResultSet rs = this.doFetch(fs, 0 /* retry count */);
      return rs.size();
    }

    OGoResultSet rs = this.doFetch(cfs, 0 /* retry count */);
    return (Number)UObject.extractValue(rs);
  }
  
  /**
   * Conveniently locate an object for a primary key.
   * <p>
   * Example<pre>
   *   Object contact = pc.find("Persons", 10000);</pre>
   * 
   * @param _command - an entity(::default) or entity::fetchspec name
   * @param _id      - a primary key
   * @return an object, or null if none matched
   */
  public Object find(final String _command, final Number _id) {
    if (_command == null || _id == null)
      return null; // TBD: determine entity for IDs?
    
    final EOFetchSpecification fs = this.buildFetchSpecification(_command,
        new Object[] { "qualifier", new EOKeyValueQualifier("id", _id) });
    if (fs == null) return null;
    fs.setFetchLimit(1);
    
    final OGoResultSet rs = this.doFetch(fs, 0 /* retry count */);
    return (rs != null && rs.size() > 0) ? rs.get(0) : null;
  }
  
  /**
   * Conveniently locate an object which has a specific value set for some key.
   * Usually used with UNIQUE columns, eg a person 'number'.
   * <p>
   * Example<pre>
   *   Object contact = pc.find("Persons", "number", "A1000");</pre>
   * 
   * @param _command - an entity(::default) or entity::fetchspec name
   * @param _key     - some unique key
   * @param _value   - value for the key
   * @return an object, or null if none matched
   */
  public Object find(final String _command, final String _key, Object _value) {
    if (_command == null || _key == null)
      return null; // TBD: determine entity for IDs?
    
    final EOFetchSpecification fs;
    if (_value instanceof EOQualifier && _key.equals("qualifier")) {
      // workaround overloading issue :-)
      // this catches: oc.find("abc", "qualifier", q)
      // which triggers this method
      fs = this.buildFetchSpecification(_command,
          new Object[] { "qualifier", _value });
    }
    else {
      fs = this.buildFetchSpecification(_command,
        new Object[] { "qualifier", new EOKeyValueQualifier(_key, _value) });
    }
    if (fs == null) return null;
    fs.setFetchLimit(1);
    
    final OGoResultSet rs = this.doFetch(fs, 0 /* retry count */);
    return (rs != null && rs.size() > 0) ? rs.get(0) : null;
  }

  /**
   * Conveniently fetch a single object. This is like doFetch(), but sets the
   * fetch limit to 1.
   * <p>
   * Special arguments
   * <ul>
   *   <li>limit - this cannot be set, but neither used as a bind variable
   *   <li>prefetch  (String[])
   *   <li>distinct  (bool)
   *   <li>qualifier (EOQualifier)
   *   <li>orderings (EOSortOrdering[])
   *   <li>attributes (String[])
   * </ul>
   * You cannot use those special keys as bindings with this method.
   * <p>
   * All the other arguments are treated as bind-values.
   * 
   * <p>
   * Example<pre>
   *   OGoPerson rs = (OGoPerson)oc.find("Persons::default",
   *                                     "qualifier", "id = $id",
   *                                     id, 10000);</pre>
   * 
   * @param _command - an entity(::default) or entity::fetchspec name
   * @param _args    - arguments
   * @return an object or null if the object could not be found
   */
  public Object find(final String _command, Object... _args) {
    final EOFetchSpecification fs = 
      this.buildFetchSpecification(_command, _args);
    if (fs == null) return null;
    fs.setFetchLimit(1);
    
    /* perform fetch */
    final OGoResultSet rs = this.doFetch(fs, 0 /* retry count */);
    return (rs != null && rs.size() > 0) ? rs.get(0) : null;
  }
  
  /**
   * Conveniently creates a fetch specification for a fetch.
   * <p>
   * Special arguments
   * <ul>
   *   <li>limit     (int)
   *   <li>offset    (int)
   *   <li>prefetch  (String[])
   *   <li>distinct  (bool)
   *   <li>qualifier (EOQualifier)
   *   <li>orderings (EOSortOrdering[])
   *   <li>attributes (String[])
   * </ul>
   * You cannot use those special keys as bindings with this method.
   * <p>
   * Example<pre>
   *   EOFetchSpecification fs = oc.specify(
   *      "Persons::default", "limit", 10,
   *      "qualifier", "name LIKE 'Duck*'");</pre>
   * 
   * @param _command - an entity(::default) or entity::fetchspec name
   * @param _args    - arguments
   * @return the EOFetchSpecification representing the arguments
   */
  public EOFetchSpecification specify(final String _command, Object... _args) {
    return this.buildFetchSpecification(_command, _args);
  }
  
  /**
   * Conveniently creates a fetch specification for a fetch. This is called by
   * the find/doFetch methods.
   * <p>
   * Special arguments
   * <ul>
   *   <li>limit     (int)
   *   <li>offset    (int)
   *   <li>prefetch  (String[])
   *   <li>distinct  (bool)
   *   <li>qualifier (EOQualifier)
   *   <li>orderings (EOSortOrdering[])
   *   <li>attributes (String[])
   * </ul>
   * You cannot use those special keys as bindings with this method.
   * <p>
   * All the other arguments are treated as bind-values. Example:
   * <pre>
   *   fs = oc.buildFetchSpecification("Persons",
   *     "qualifier", "lastname = $lastname",
   *     "lastname", "Duck"
   *   );</pre>
   * 
   * @param _command - an entity(::default) or entity::fetchspec name
   * @param _args    - arguments
   * @return the EOFetchSpecification representing the arguments
   */
  @SuppressWarnings({"unchecked", "rawtypes"})
  public EOFetchSpecification buildFetchSpecification
    (final String _command, final Object[] _args)
  {
    // TBD: split in fetchspec generator and fetch
    if (_command == null)
      return null;
    
    final EODatabase db = this.database();
    if (db == null)
      return null;
    
    /* split command name and lookup entity */
    
    final EOEntity entity;
    final String   fname;
    int idx = _command.indexOf("::");
    if (idx > 0) {
      entity = db.entityNamed(_command.substring(0, idx));
      fname = _command.substring(idx + 2);
    }
    else {
      entity = db.entityNamed(_command);
      fname = null;
    }
    if (entity == null) {
      log().warn("did not find entity of command: " + _command);
      return null;
    }
    
    /* determine fetch spec AND clone it */
    
    EOFetchSpecification fs;
    if (fname == null) {
      fs = entity.fetchSpecificationNamed("default");
      if (fs == null) /* no default fetchspec, create a fresh one */
        fs = new EOFetchSpecification(entity.name(), null, null);
      else
        fs = new EOFetchSpecification(fs); /* clone */
    }
    else {
      fs = entity.fetchSpecificationNamed(fname);
      if (fs == null) {
        log().warn("did not find specified fetch specification: " + fname);
        return null;
      }
      fs = new EOFetchSpecification(fs); /* clone */
    }
    
    /* process arguments */
    
    Map<String, Object> args = UMap.createArgs(_args);
    
    /* process internal arguments */
    
    if (args != null && args.size() > 0) {
      Object v;
      
      if ((v = args.remove("limit")) != null)
        fs.setFetchLimit(UObject.intValue(v));
      if ((v = args.remove("offset")) != null)
        fs.setFetchOffset(UObject.intValue(v));
      
      if ((v = args.remove("prefetch")) != null) {
        if (v instanceof String)
          v = UString.componentsSeparatedByString((String)v, ",", true, true);
        fs.setPrefetchingRelationshipKeyPaths((String[])v);
      }
      
      if ((v = args.remove("distinct")) != null)
        fs.setUsesDistinct(UObject.boolValue(v));

      if ((v = args.remove("attributes")) != null) {
        if (v instanceof String) {
          fs.setFetchAttributeNames(
              UString.componentsSeparatedByString((String)v, ",", true, true));
        }
        else if (v instanceof String[])
          fs.setFetchAttributeNames((String[])v);
        else if (v instanceof Collection) {
          fs.setFetchAttributeNames(
              (String[])((Collection)v).toArray(new String[0]));
        }
        else {
          log.error("cannot process attributes key: " + v);
          return null;
        }
      }

      if ((v = args.remove("qualifier")) != null) {
        EOQualifier q = null;
        
        if (v instanceof EOQualifier)
          q = (EOQualifier)v;
        else if (v instanceof EOQualifier[])
          q = new EOAndQualifier((EOQualifier[])v);
        else
          q = EOQualifier.parse(v.toString());

        fs.setQualifier(q.and(fs.qualifier()));
      }
      
      if ((v = args.remove("orderings")) == null)
        v = args.remove("ordering");
      if (v == null)
        v = args.remove("orderby");
      if (v != null) {
        EOSortOrdering[] sos;
        
        if (v instanceof EOSortOrdering[])
          sos = (EOSortOrdering[])v;
        else if (v instanceof EOSortOrdering)
          sos = new EOSortOrdering[] { (EOSortOrdering)v };
        else
          sos = EOSortOrdering.parse(v.toString());
        fs.setSortOrderings(sos);
      }
    }
    
    /* remaining args are bindings */

    final Number[] aids = this.authenticatedIDs();
    if (aids == null) {
      log().error("missing authenticated IDs: " + this);
      return null;
    }
    
    if (args == null)
      args = UMap.create("authIds", aids);
    else
      args.put("authIds", aids);
    
    return fs.fetchSpecificationWithQualifierBindings(args);
  }
  

  /* convenience method for tools */
  
  /**
   * Factory function to create a properly initialized OGoObjectContext. The
   * OGo user is authenticated uses JAAS by calling the OGoDatabase.login()
   * method.
   * 
   * @param _url     - database URL, eg jdbc:postgresql://127.0.0.1/OGo?user=OGo
   * @param _login   - OGo login
   * @param _pwd     - OGo password
   * @param _docPath - location of OGo files, eg /var/lib/ogo/
   * @return null if the login failed, ot the initialized OGoObjectContext
   */
  public static OGoObjectContext objectContextForURL
    (String _url, final String _login, final String _pwd, File _docPath)
  {
    EOAdaptor    adaptor = EODatabase.dbAdaptorForURL(OGoDatabase.class, _url);
    OGoDatabase  db = new OGoDatabase(adaptor, _docPath);
    LoginContext lc = OGoLoginModule.jaasLogin(db, _login, _pwd);
    
    if (lc == null || lc.getSubject() == null) {
      db.dispose();
      return null;
    }
    
    return new OGoObjectContext(new EODatabaseContext(db), lc);
  }


  /* description */

  @Override
  public void appendAttributesToDescription(final StringBuilder _d) {
    super.appendAttributesToDescription(_d);
    
    if (this.actorID != null) {
      _d.append(" actor=");
      _d.append(this.actorID);
    }
    if (this.authenticatedIDs != null) {
      _d.append(" authids=");
      _d.append(UString.componentsJoinedByString(this.authenticatedIDs, ","));
    }
    
    if (this.gidToPermission != null) {
      _d.append(" #perms=");
      _d.append(this.gidToPermission.size());
    }
    
    if (this.autoFetchPermissions)
      _d.append(" auto-fetch");
    if (this.autoApplyPermissions)
      _d.append(" auto-apply");
    
    if (this.loginContext == null)
      _d.append(" no-loginctx?");
  }
}
