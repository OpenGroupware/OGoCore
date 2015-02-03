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
package org.opengroupware.logic.authz;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.getobjects.eoaccess.EOAccessDataSource;
import org.getobjects.eoaccess.EOAdaptorDataSource;
import org.getobjects.eoaccess.EODatabase;
import org.getobjects.eoaccess.EOEntity;
import org.getobjects.eocontrol.EOGlobalID;
import org.getobjects.eocontrol.EOKeyGlobalID;
import org.getobjects.foundation.NSKeyValueCoding;
import org.getobjects.foundation.NSObject;
import org.getobjects.foundation.UString;
import org.opengroupware.logic.core.OGoObjectContext;
import org.opengroupware.logic.db.OGoDatabase;

/**
 * OGoAuthzFetchContext
 * <p>
 * Object used to process the permissions of OGo objects stored in an
 * editing context. The permission resolution itself is handled by
 * IOGoPermissionHandler objects. Each OGo entity is assigned to a specific
 * permission handler class.
 * 
 * <p>
 * This object and the whole process is quite complicated since the permission
 * of an OGo object can depend on permissions of other objects.
 * And then the whole process needs to be very efficient since it needs to be
 * performed very often. Which is why we group fetch requests and run them in
 * a bulk way.
 * 
 * <p>
 * @author helge
 */
public class OGoAuthzFetchContext extends NSObject {
  protected static final Log log = LogFactory.getLog("OGoAuthzFetchContext");
  static boolean doDebug = true;
  
  protected OGoObjectContext oCtx;
  protected Number[]         authIds;
  protected Number[]         personAuthIds;
  
  protected Map<EOGlobalID, String>            gidToPermission;
  protected Map<String, IOGoPermissionHandler> entityToHandler;
  
  protected Map<EOGlobalID, Object>  gidToFragment;
  protected Map<EOGlobalID, String>  gidToACLPermission;
  protected Map<EOGlobalID, Boolean> gidHasACL;
  
  /* ACLs required to resolve the object */
  protected Set<EOKeyGlobalID> requestedACLs;
  /* ACLs to fetch only if there is a fetch above */
  protected Set<EOKeyGlobalID> optionalACLs;
  
  /* GIDs where we need to fetch the required security attributes */
  protected Map<IOGoPermissionHandler, Set<EOKeyGlobalID>> fetchGlobalIDs;

  /* if an object depends on the permissions of another one, it puts the
   * other's GID into this queue.
   */
  protected Set<EOKeyGlobalID> requestedGlobalIDs;

  public static final String noPermission = "";
  
  
  /* constructor */

  /**
   * Setup a new OGoAuthzFetchContext.
   * <p>
   * This is invoked by fetchPermissionsForGlobalIDs() in OGoObjectContext.
   * 
   * @param _ctx - the associated OGoObjectContext (used for fetching things)
   * @param _g2p - the gidToPermission Map which is filled by this handler
   */
  public OGoAuthzFetchContext
    (final OGoObjectContext _ctx, final Map<EOGlobalID, String> _g2p)
  {
    super();
    
    this.oCtx            = _ctx;
    this.authIds         = _ctx.authenticatedIDs();
    this.personAuthIds   = this.authIds; // TBD: fix me, this is an optimization
    this.gidToPermission = _g2p;
    this.entityToHandler = defaultEntityToHandler;
    
    /* prepare state */
    this.requestedGlobalIDs = new HashSet<EOKeyGlobalID>(128);
    this.requestedACLs      = new HashSet<EOKeyGlobalID>(128);
    this.optionalACLs       = new HashSet<EOKeyGlobalID>(128);
    this.gidToFragment      = new HashMap<EOGlobalID, Object>(16);
    this.gidToACLPermission = new HashMap<EOGlobalID, String>(128);
    this.gidHasACL          = new HashMap<EOGlobalID, Boolean>(128);
    this.fetchGlobalIDs     =
      new HashMap<IOGoPermissionHandler, Set<EOKeyGlobalID>>(16);
  }
  
  
  /* processing permissions */
  
  /**
   * Fetches the permissions of all objects in the OGoObjectContext.
   */
  public Map<EOGlobalID,String> processPermissions() {
    this.processPermissionsOfGlobalIDs
      (this.oCtx.globalIDsForObjects(this.oCtx.registeredObjects().toArray()));
    return this.gidToPermission;
  }
  
  /**
   * Processes the permissions of the given global-ids.
   * 
   * @param _gids - the global-ids to check
   * @return true if all GIDs could be checked, false otherwise
   */
  public boolean processPermissionsOfGlobalIDs(final EOGlobalID[] _gids) {
    // returns true if anything was resolved
    if (_gids == null || _gids.length == 0)
      return false;
    
    final boolean debugOn = log.isDebugEnabled();

    if (debugOn) log.debug("process objects: #" + _gids.length);
    
    /* setup initial set of pending objects */
    
    final Set<EOKeyGlobalID> pending = new HashSet<EOKeyGlobalID>(_gids.length);
    for (int i = _gids.length - 1; i >= 0; i--) {
      if (!(_gids[i] instanceof EOKeyGlobalID)) {
        log.error("unexpected GID: " + _gids[i]);
        continue;
      }
      
      pending.add((EOKeyGlobalID)_gids[i]);
    }
    
    int maxFetchIterations = 10;
    for (int fetchIteration = 1; pending.size() > 0 && 
         fetchIteration <= maxFetchIterations; fetchIteration++)
    {
      if (fetchIteration > maxFetchIterations) {
        log.error("stopping processing, max fetch iteration count reached: " +
          maxFetchIterations + ", pending: " + pending);
        break;
      }
      
      for (int iteration = 1; pending.size() > 0 && 
           iteration<8; iteration++)
      {
        if (debugOn) {
          log.debug("  * start iteration " + fetchIteration + "/" + iteration +
              ", #" + pending.size() + " pending:");
        }

        /* Scan pending GIDs and check whether we can resolve anything. Collect
         * requests for additional data.
         */
        Set<EOKeyGlobalID> resolvedGlobalIDs = this.prescanGlobalIDs(pending);

        if (debugOn) {
          String s;
          log.debug("  = prescan done, resolved: #" + resolvedGlobalIDs.size() +
              ", pending: #" + pending.size());
          
          s = "#" + this.requestedGlobalIDs.size();
          if (this.requestedGlobalIDs.size() < 10)
            s += ": " + this.requestedGlobalIDs;
          log.debug("    dependencies: " + s);
          
          s = "handler#" + this.fetchGlobalIDs.size();
          if (this.fetchGlobalIDs.size() < 10)
            s += ": " + this.fetchGlobalIDs;
          log.debug("    fetch-info:   " + s);
          
          log.debug("    fetch-acl:    #" + this.requestedACLs.size() +
              " / consider: #" + this.optionalACLs.size());
        }
        
        /* add requested GIDs to new pending list */
        final int sizeBefore = pending.size();
        pending.addAll(this.requestedGlobalIDs);
        this.requestedGlobalIDs.clear();
        
        /* check whether we added GIDs, if so, reiterate even if no
         * objects got resolved! (to add fetch requests for the new GID)
         */
        final boolean newStuffGotRequested = sizeBefore < pending.size();
        
        /* Check whether we resolved or requested anything. If not, we need to
         * fetch things to allow the handlers to proceed.
         */
        if ((resolvedGlobalIDs == null || resolvedGlobalIDs.size() == 0)
            && !newStuffGotRequested)
        {
          break;
        }
        
        
        /* remove resolved records from 'pending' list */
        pending.removeAll(resolvedGlobalIDs);
      }
      
      
      /* perform necessary fetches */
      
      if (pending.size() > 0) {
      
        if (this.requestedACLs.size() > 0) {
          /* In case we must fetch ACLs, fetch the optional ones in one run,
           * otherwise delay fetch of optional ACLs until they are actually
           * needed.
           */
          this.requestedACLs.addAll(this.optionalACLs);
        }
        
        /* clear optional fetch requests */
        this.optionalACLs.clear();
        
        if (this.requestedACLs.size() == 0 && this.fetchGlobalIDs.size() == 0) {
          log.error("no fetch requests issued, but objects still unprocessed:" +
              pending);
          break;
        }
        
        if (this.requestedACLs.size() > 0) {
          /* this makes the fetched ACLs available to the next iteration */
          final Exception error =
            this.fetchACLsForGlobalIDs(this.requestedACLs);
          
          if (error == null) {
            /* we fetched them :-), remove the request */
            this.requestedACLs.clear();
          }
          else {
            log.error("error fetching ACLs for global ids: " +
                this.requestedACLs);
          }
        }
        
        if (this.fetchGlobalIDs.size() > 0) {
          this.fetchRequestedAuthInfos(this.fetchGlobalIDs);
          this.fetchGlobalIDs.clear();
        }
      }
    }
    
    if (pending.size() > 0)
      log.error("could not fetch permissions of all objects:\n  " + pending);
    
    return pending.size() == 0;
  }
  
  protected void fetchRequestedAuthInfos
    (final Map<IOGoPermissionHandler, Set<EOKeyGlobalID>> _handlerToGIDs)
  {
    /* Our fetches are grouped by handler, which is great as it allows us to do
     * bulk fetches :-)
     */

    final OGoDatabase db = this.oCtx != null
      ? (OGoDatabase)this.oCtx.database() : null; 
    
    for (final IOGoPermissionHandler handler: _handlerToGIDs.keySet()) {
      final Set<EOKeyGlobalID> gids = _handlerToGIDs.get(handler);
      if (gids == null || gids.size() == 0)
        continue;
      
      if (log.isDebugEnabled()) {
        log.debug("  will fetch infos for #" + gids.size() + " gids using: " +
            handler);
      }
      
      final Map<EOKeyGlobalID, Object> results =
        handler.fetchInfosForGlobalIDs(this, db, gids);
      if (results == null) {
        log.error("failed to fetch auth-infos using handler: " + handler +
            "\n  gids: " + gids);
        continue;
      }
      
      /* remove keys (whether we returned infos or not) */
      gids.clear();
      
      /* add to info map */
      this.gidToFragment.putAll(results);
    }
  }
  
  /**
   * This method fetches object_acl records for all global-ids which are passed
   * in as a parameter. Only records of the authz-ctx' authids are fetched and
   * then compressed into a single permission.
   * 
   * @param _gids - the GIDs we want to have the ACLs for
   * @return a Map containing the GIDs as keys and their permission as the value
   */
  @SuppressWarnings("unchecked")
  public Exception fetchACLsForGlobalIDs(final Collection<EOKeyGlobalID> _gids){
    if (_gids == null)
      return null;
    
    final int gidCount = _gids.size();
    
    if (gidCount == 0)
      return null; /* everything ok */
    
    /* Extract primary key values from global-ids. This works because pkeys
     * are unique across tables in OGo.
     * Also: we set the minimum access-level for each gid in the map, so that
     * we can be sure that entries w/o ACEs do get the no-access permission. 
     */
    final Map<Number, EOKeyGlobalID> pkeyToGlobalID =
      new HashMap<Number, EOKeyGlobalID>(gidCount + 1);
    final Collection<Number> pkeysToProcess = new HashSet<Number>(gidCount); 
    
    for (final EOKeyGlobalID gid: _gids) {
      /* default access */
      this.gidToACLPermission.put(gid, noPermission);
      this.gidHasACL.put(gid, Boolean.FALSE);
      
      /* extract key */
      final Number oid = (Number)gid.toNumber();
      pkeyToGlobalID.put(oid, gid);
      
      /* fetch this */
      pkeysToProcess.add(oid);
    }
    
    /* setup fetch environment */
    
    final EODatabase db = this.oCtx != null ? this.oCtx.database() : null; 
    final EOEntity   aclEntity = db.entityNamed("ACLEntries");
    EOAccessDataSource ads = new EOAdaptorDataSource(db.adaptor(), aclEntity);
    
    List<Map<String, Object>> results = ads.fetchObjects
      ("authzFetch", "authIds", this.authIds, "ids", pkeysToProcess);
    if (results == null) return ads.consumeLastException();
    
    for (int i = results.size() - 1; i >= 0; i--) {
      final Map<String, Object> ace   = results.get(i);
      final String perms = (String)ace.get("permissions");
      final Number oid   = (Number)ace.get("object_id"); // watch for Go changes
      if (perms == null || oid == null) {
        log.warn("found ACE w/o oid or permissions: " + ace);
        continue;
      }
      
      EOKeyGlobalID gid = pkeyToGlobalID.get(oid);

      /* there where results for the GID, remember this fact */
      this.gidHasACL.put(gid, true);
      pkeysToProcess.remove(oid);
      
      /* process permissions */
      
      final int permsLen = perms.length();
      if (permsLen == 0) continue; /* perm empty, won't add anything */
      
      final String currentPerms = this.gidToACLPermission.get(gid);
      
      this.gidToACLPermission.put
        (gid, UString.unionCharacterSets(perms, currentPerms));
    }
    
    
    /* fetch counts */
    if (pkeysToProcess.size() > 0) {
      // TBD: Could we do this in some kind of join with the fetch above?
      //      Probably not, but maybe we could make a union.
      //      We also have the option to do the GROUP BY in memory at the
      //      expense of having to transfer all ACEs of an ACL.
      
      if (log.isDebugEnabled())
        log.debug("      fetch-acl-count: #" + pkeysToProcess.size());
      
      results = ads.fetchObjects("authzCountFetch", "ids", pkeysToProcess);
      if (results == null) return ads.consumeLastException();
      
      for (int i = results.size() - 1; i >= 0; i--) {
        final Map<String, Object> objectAclCount = results.get(i);
        final Number        oid = (Number)objectAclCount.get("object_id");
        final EOKeyGlobalID gid = pkeyToGlobalID.get(oid);
        this.gidHasACL.put(gid, true);
      }
    }
    
    return null; /* everything OK */
  }
  
  /**
   * This method tries to resolve the permissions of the given GIDs in-memory
   * and invokes the per-entity handlers to make them register their
   * requirements.
   * The method does just one pass, so you might want to call the method
   * multiple times if it managed to resolve objects.
   * 
   * @param _gids - set of global-ids to check
   * @return the set of global-ids which could be resolved successfully 
   */
  public Set<EOKeyGlobalID> prescanGlobalIDs(final Set<EOKeyGlobalID> _gids) {
    if (_gids == null || _gids.size() == 0)
      return null;
    
    final boolean debugOn = log.isDebugEnabled();
    
    /* GIDs which could be resolved during this run */
    Set<EOKeyGlobalID> resolvedGlobalIDs = new HashSet<EOKeyGlobalID>(128);
    
    if (debugOn) log.debug("    scan objects: #" + _gids.size());
    
    for (final EOGlobalID gid: _gids) {
      final EOKeyGlobalID kgid = (EOKeyGlobalID)gid;
      
      /* first check cache */
      
      final String permission = this.gidToPermission.get(gid);
      if (permission != null) {
        /* we already have permissions for this object :-) */
        
        if (debugOn) {
          log.debug("      done: permission '" + permission + "' is cached: " +
              gid);
        }
        
        /* mark as resolved (because it was in the requested input?!) */
        resolvedGlobalIDs.add(kgid);
        continue;
      }
      
      /* next we retrieve the cached object and info for the handler */
      
      final NSKeyValueCoding object = 
        (NSKeyValueCoding)this.oCtx.objectForGlobalID(gid);
      final Object objectInfo = this.gidToFragment.get(gid);
      
      
      /* OK, we have the object or the fetched object info, process it */
      
      
      final boolean didResolve;
      IOGoPermissionHandler handler = this.permissionHandlerForGlobalID(kgid);
      if (debugOn) log.debug("      process " + gid + " using " + handler);
      
      if (handler == null) {
        log.warn("found no permission handler for object: " + kgid);
        this.recordPermissionsForGlobalID(noPermission, kgid);
        didResolve = true;
      }
      else {      
        didResolve = handler.process(this, kgid, object, objectInfo);
      }
      
      /* post */
      
      if (didResolve) {
        resolvedGlobalIDs.add(kgid);
        if (debugOn) log.debug("        handler resolved permission: " + kgid);
        // TBD: we could also remove temporary state? (ACL perms etc?)
      }
      else if (debugOn)
        if (debugOn) log.debug("        object still pending: " + kgid);
    }
    
    return resolvedGlobalIDs;
  }
  
  @SuppressWarnings("rawtypes")
  public String processACLOfObject(EOGlobalID _gid, Collection _objectACL) {
    if (_objectACL != null) {
      /* Object has the ACL relationship fetched! So we can directly check the
       * setup. */
      final List<String> perms = new ArrayList<String>(8);
      for (final Object aceO: _objectACL) {
        final NSKeyValueCoding ace = (NSKeyValueCoding)aceO;
        if (this.contextHasPrincipalId((Number)ace.valueForKey("principalId")))
          perms.add((String)ace.valueForKey("permissions"));
      }
      return this.unionPermissions(perms);
    }
    
    /* null means we need to fetch the ACL! */
    return _gid != null ? this.gidToACLPermission.get(_gid) : null;
  }
  
  /**
   * Checks whether login-ctx has authenticated the given id. It could be a
   * team id or a person(/account) id. If you just need to check person ids
   * (eg 'ownerId' like fields) use the contextHasAccountId() method. 
   * 
   * @param  _id - the primary key to be checked
   * @return true if this ID is authenticated, false otherwise
   */
  public boolean contextHasPrincipalId(final Number _id) {
    if (_id == null || this.authIds == null)
      return false;
    
    for (final Number p: this.authIds) {
      if (p != null && p.equals(_id))
        return true;
    }
    return false;
  }
  /**
   * Checks whether login-ctx has authenticated the given account id.
   * 
   * @param  _id - the person contact's primary key to be checked
   * @return true if this ID is authenticated, false otherwise
   */
  public boolean contextHasAccountId(final Number _id) {
    if (_id == null || this.personAuthIds == null)
      return false;
    
    for (final Number p: this.personAuthIds) {
      if (p != null && p.equals(_id))
        return true;
    }
    return false;
  }
  
  /**
   * Returns whether the account has minimum access to the project with the
   * given ID.
   * 
   * @param _id - the primary key of the project
   * @return true if the account has access, false if not and null if we need to
   *   fetch the permissions
   */
  public Boolean contextHasAccessToProjectWithPrimaryKey(final Number _id) {
    if (_id == null)
      return true; /* sure, we have access to the 'null' project ;-) */
    
    // TBD: here we could just fetch the 'myProjects' lists?!
    // => but this would be more expensive for documents *with a project*, in
    //    this case we need to fetch the whole project-permission

    /*
     * This could be optimized. We don't need the full project permission, we
     * just need to know whether a project_company_assignment entry 'EXISTS'.
     * The idea is that the context could be given the active project list,
     * which in turn might be cached by the caller.
     */
    final EOKeyGlobalID projectGID = EOKeyGlobalID.globalIDWithEntityName
      ("Projects", new Object[] { _id });
    
    final String projectPermission = this.gidToPermission.get(projectGID);
    if (projectPermission != null)
      return (noPermission.equals(projectPermission)) ? false : true;
    
    return null; /* means: don't know yet, fetch the project-permissions! */
  }
  
  /**
   * Checks the gidToPermission cache for the given entity/id combination. If
   * no permission set is stored, null is returned. If it contains 'r' (read),
   * true is returned.
   * 
   * @param _entityName
   * @param _id
   * @return null on cache miss, true if we have 'r' access, false otherwise
   */
  public String permissionsForObject(String _entityName, final Number _id) {
    if (_id == null) {
      log.error("invoked hasReadAccessToObject() w/o an id, entity: " +
          _entityName);
      return null; // TBD: what to return?
    }
    
    final EOKeyGlobalID gid = EOKeyGlobalID.globalIDWithEntityName
      (_entityName, new Object[] { _id });
    
    return this.gidToPermission.get(gid);
  }
  
  /**
   * Checks the gidToPermission cache for the given entity/id combination. If
   * no permission set is stored, null is returned. If it contains 'r' (read),
   * true is returned.
   * 
   * @param _entityName
   * @param _id
   * @return null on cache miss, true if we have 'r' access, false otherwise
   */
  public Boolean hasReadAccessToObject(String _entityName, final Number _id) {
    if (_id == null) {
      log.error("invoked hasReadAccessToObject() w/o an id, entity: " +
          _entityName);
      return null; // TBD: what to return?
    }
    
    final EOKeyGlobalID gid = EOKeyGlobalID.globalIDWithEntityName
      (_entityName, new Object[] { _id });
    
    final String permissions = this.gidToPermission.get(gid);
    if (permissions == null)
      return null; /* means: don't know yet, fetch the project-permissions! */
    
    if (permissions.length() == 0)
      return false;
    
    return permissions.indexOf('r') >= 0 ? true : false;
  }
  
  /**
   * Combines the given permission sets into a single one.
   * Example: 'lr', 'r', 'rw' will return 'lrw'.
   * 
   * @param _perms - a collection of permission sets
   * @return the combined set of permission characters
   */
  public String unionPermissions(final Collection<String> _perms) {
    int size;
    if (_perms == null || (size = _perms.size()) == 0)
      return noPermission;
    
    /* shortcut */
    if (size == 1 && _perms instanceof List<?>)
      return ((List<String>)_perms).get(0);
    
    // TBD: make this stuff sane
    final Set<Character> a = new HashSet<Character>(8);
    for (final String perms: _perms) {
      for (char permission: perms.toCharArray())
        a.add(permission);
    }
    
    if ((size = a.size()) == 0)
      return noPermission;
    
    final StringBuilder sb = new StringBuilder(size);
    for (Character c: a)
      sb.append(c.charValue());
    return sb.toString();
  }
  
   
  /**
   * Returns the permission handler which is considered responsible for the
   * given gid.
   * <p>
   * This is called by prescanGlobalIDs().
   * 
   * @param _gid - the EOGlobalID to process
   * @return the permission handler, or null
   */
  public IOGoPermissionHandler permissionHandlerForGlobalID(EOGlobalID _gid) {
    if (_gid == null)
      return null;
    
    /* check entity */
    
    final String entityName = (_gid instanceof EOKeyGlobalID)
      ? ((EOKeyGlobalID)_gid).entityName()
      : (String)_gid.valueForKey("entityName");
    if (entityName == null) {
      log.warn("cannot determine entity of GID: " + _gid);
      return null;
    }
    
    /* find handler for entity */
    
    IOGoPermissionHandler handler = this.entityToHandler.get(entityName);
    if (handler != null)
      return handler;
    
    /* did not find a handler */
    
    if (log.isInfoEnabled())
      log.info("did not find permission handler for entity: " + entityName);
    
    final EOEntity entity = this.oCtx.database().entityNamed(entityName);
    if (entity != null) {
      if ((handler = new OGoGenericPermissionHandler(entity)) != null)
        this.entityToHandler.put(entityName, handler);
    }
    
    return handler;
  }
  
  
  /* callbacks for handler */
  
  /**
   * Registers a global-id for a fetch. The context will group GIDs for a fetch.
   * 
   * @param _requester - the handler which needs the info 
   * @param _gid - the global-id which we need the info for
   */
  public void requestFetchOfInfo
    (final IOGoPermissionHandler _requester, final EOKeyGlobalID _gid)
  {
    Set<EOKeyGlobalID> gids = this.fetchGlobalIDs.get(_requester);
    if (gids == null) {
      gids = new HashSet<EOKeyGlobalID>(64);
      this.fetchGlobalIDs.put(_requester, gids);
    }
    gids.add(_gid);
  }

  public void registerObjectDependency
    (final IOGoPermissionHandler _requester,
     final EOKeyGlobalID _sourceGlobalID, final EOKeyGlobalID _requiredGlobalID)
  {
    this.requestedGlobalIDs.add(_requiredGlobalID);
  }

  /**
   * Tells the authz context that an ACL is *required* to determine the
   * permissions of an object.
   * 
   * @param _requester - the handler which needs the ACL
   * @param _gid - the global-id which we need the ACL for
   */
  public void requestFetchOfACL
    (final IOGoPermissionHandler _requester, final EOKeyGlobalID _gid)
  {
    this.requestedACLs.add(_gid);
  }
  
  /**
   * To give the authz context a hint that the ACL of an object might be
   * required. The context will fetch the ACL of the GID if there are also
   * *required* ACLs.
   * 
   * @param _requester - the permission handler which would like to have the ACL
   * @param _gid - the global-id we need the ACL for
   */
  public void considerFetchOfACL
    (final IOGoPermissionHandler _requester, final EOKeyGlobalID _gid)
  {
    this.optionalACLs.add(_gid);
  }
  
  /**
   * The handler calls this method if it has successfully determined the
   * permissions of an object.
   * 
   * @param _perms a string representing the permissions (eg "rw")
   * @param _gid the global-id which has the given permissions
   */
  public void recordPermissionsForGlobalID(String _perms, EOGlobalID _gid) {
    this.gidToPermission.put(_gid, _perms);
  }

  /* default handler mapping */

  protected static final Map<String, IOGoPermissionHandler>
    defaultEntityToHandler;
  
  protected static final IOGoPermissionHandler sharedContactPermissionHandler =
    new OGoContactPermissionHandler();
  protected static final IOGoPermissionHandler sharedContactOwnedPermHandler =
    new OGoContactOwnedObjectPermissionHandler();
  
  protected static final Object[] defaultEntityToHandlerList = {
    /* this should be somehow attached to the model? yes! */
    "Persons",           sharedContactPermissionHandler,
    "Accounts",          sharedContactPermissionHandler,
    "Teams",             sharedContactPermissionHandler,
    "Companies",         sharedContactPermissionHandler,
    
    "PersonPhones",      OGoPhoneNumberPermissionHandler.personPhone,
    "PersonEMails",      OGoEMailAddressPermissionHandler.personEMail,
    "PersonAddresses",   OGoAddressPermissionHandler.personAddress,
    "CompanyPhones",     OGoPhoneNumberPermissionHandler.companyPhone,
    "CompanyEMails",     OGoEMailAddressPermissionHandler.companyEMail,
    "CompanyAddresses",  OGoAddressPermissionHandler.companyAddress,
    "PersonComments",    sharedContactOwnedPermHandler,
    "CompanyComments",   sharedContactOwnedPermHandler,
    "TeamComments",      sharedContactOwnedPermHandler,
    
    "Tasks",             OGoTaskPermissionHandler.defaultHandler,
    "Projects",          OGoProjectPermissionHandler.defaultHandler,
    "Documents",         OGoDocumentPermissionHandler.defaultHandler,
    "Notes",             OGoDocumentPermissionHandler.defaultHandler,

    // TBD: fix this, should have a proper, own association
    "ProjectPersons",    OGoProjectOwnedObjectPermissionHandler.defaultHandler,
    "ProjectTeams",      OGoProjectOwnedObjectPermissionHandler.defaultHandler,
    "ProjectCompanies",  OGoProjectOwnedObjectPermissionHandler.defaultHandler,
    "ProjectsToCompany", OGoProjectOwnedObjectPermissionHandler.defaultHandler,
    
    // TBD
    "ACLEntries",        OGoPublicObjectPermissionHandler.defaultHandler,
    "TeamMemberships",   OGoPublicObjectPermissionHandler.defaultHandler,
    "Employments",       OGoPublicObjectPermissionHandler.defaultHandler
  };

  static {
    defaultEntityToHandler = new HashMap<String, IOGoPermissionHandler>(32);
    for (int i = 0; i < defaultEntityToHandlerList.length; i += 2) {
      defaultEntityToHandler.put(
          (String)defaultEntityToHandlerList[i], 
          (IOGoPermissionHandler)defaultEntityToHandlerList[i + 1]);
    }
  }

}
