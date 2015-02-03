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
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.getobjects.eoaccess.EOAdaptorChannel;
import org.getobjects.eoaccess.EOEntity;
import org.getobjects.eocontrol.EOFetchSpecification;
import org.getobjects.eocontrol.EOKeyGlobalID;
import org.getobjects.foundation.NSJavaRuntime;
import org.getobjects.foundation.NSKeyValueCoding;
import org.getobjects.foundation.NSObject;
import org.getobjects.foundation.UObject;
import org.getobjects.foundation.UString;
import org.opengroupware.logic.db.OGoDatabase;
import org.opengroupware.logic.db.OGoObject;

/**
 * OGoProjectPermissionHandler
 * <p>
 * Project permissions are managed in a special n-m join table,
 * "project_company_assignments". The permissions mostly affect
 * documents contained in the project, though a lot of OGo objects depend
 * on the <code>r</code> permission on a project.
 * <br>
 * Technically the 'r' permission can be resolved with an EXISTS query on the
 * join table and doesn't require full permission resolution.
 * Not sure whether we should somehow make use of that.
 * <p>
 * Besides the project specific ACL, a project has an owner_id and a team_id
 * which imply certain permissions.
 * 
 * <p>
 * Permissions
 * <ul>
 *   <li><code>m</code> - account is a project manager (root on the project),
 *       implies all other permissions. A manager is the only one who can
 *       delete the project.
 *   <li><code>r</code> - account can read docs in the project (minimum),
 *     and they can read the project information
 *   <li><code>w</code> - account can edit *documents* in the project,
 *     and they can edit the project information
 *   <li><code>i</code> - account can add new documents
 *   <li><code>d</code> - account can delete documents from the project
 * </ul>
 *
 * <p>
 * Relevant attributes:
 * <ul>
 *   <li>ownerId
 *   <li>teamId
 *   <li>(parentProjectId)
 * </ul>
 * ACL:
 * <ul>
 *   <li>projectId
 *   <li>permissions
 *   <li>hasAccess
 * </ul>
 * 
 * <p>
 * @author helge
 */
public class OGoProjectPermissionHandler extends NSObject
  implements IOGoPermissionHandler
{
  // TBD: do we need to resolve parent_project_id? (better not?)
  
  protected static final Log log = LogFactory.getLog("OGoAuthz");

  public static final IOGoPermissionHandler defaultHandler =
    new OGoProjectPermissionHandler();
  
  protected static final String ownerPermissions = "mrwid";
  protected static final String teamPermissions  = "r";
  protected static final String mPermissions     = "mrwid";
  
  public static String resolveCompoundPermissions(String _perm) {
    if (_perm == null)
      return null;
    int count = _perm.length();
    if (count == 0)
      return _perm; /* no permissions */
    
    if (_perm.indexOf('m') >= 0) /* 'w' implies 'r' */
      return UString.unionCharacterSets(_perm, mPermissions);
    
    return _perm;
  }

  @SuppressWarnings({ "rawtypes", "unchecked" })
  public boolean process
    (OGoAuthzFetchContext _ac, EOKeyGlobalID _gid,
     NSKeyValueCoding _object, Object _info)
  {
    if (_object == null && _info == null) {
      if (log.isDebugEnabled())
        log.debug("process project-gid w/o object/info: " + _gid);
    
      /* we do NOT have the info for this GID and need to fetch it */
      _ac.requestFetchOfInfo(this, _gid);
    
      return false; /* pending, need to fetch info */
    }
    
    
    /* first we check for the owner, which always has 'm' access */
    
    Number ownerId = null, teamId = null;
    if (_object != null) {
      ownerId = (Number)_object.valueForKey("ownerId");
      teamId  = (Number)_object.valueForKey("teamId");
    }
    if (_info != null) {
      if (ownerId == null)
        ownerId = (Number)((Map)_info).get("owner_id"); // raw fetches
      if (teamId == null) 
        teamId = (Number)((Map)_info).get("team_id"); // raw fetches
    }
    
    if (_ac.contextHasPrincipalId(ownerId)) {
      if (log.isDebugEnabled())
        log.debug("detected ownership on project: " + _gid);
      
      _ac.recordPermissionsForGlobalID(ownerPermissions, _gid);
      return true; /* ok, found it */
    }
    
    
    /* in all other cases, we need the ACL information */
    
    String aclPerm = null;
    if (_object != null) {
      Collection teamACL   = (Collection)_object.valueForKey("teams");
      Collection personACL = (Collection)_object.valueForKey("persons");
      if (teamACL != null && personACL != null) {
        /* the relevant ACL relationships got fetched */
        aclPerm = OGoAuthzFetchContext.noPermission;
        
        aclPerm = UString.unionCharacterSets
          (aclPerm, this.extractPermissionsOfProjectACL(_ac, teamACL));
        aclPerm = UString.unionCharacterSets
          (aclPerm, this.extractPermissionsOfProjectACL(_ac, personACL));

        if (log.isDebugEnabled()) {
          log.debug("project EO contained ACL permissions ('" + aclPerm + 
              "'): " + _gid);
        }
      }
    }
    
    if (aclPerm == null) {
      /* ACL could not be extracted from object, check info */
      
      if (_info == null) {
        /* we need to fetch the ACLs */
        if (log.isDebugEnabled())
          log.debug("requested fetch of project-info(need ACL): " + _gid);
        
        _ac.requestFetchOfInfo(this, _gid);
        return false;
      }
      
      // System.err.println("CHECK INFO: " + _info);
      
      aclPerm = (String)((Map<String, Object>)_info).get("aclPermissions");
    }
    
    if (aclPerm == null) {
      log.warn("missing ACL permission in project info: " + _info);
      aclPerm = OGoAuthzFetchContext.noPermission;
    }
    
    
    /* add team-permissions */
    
    if (teamId != null && _ac.contextHasPrincipalId(teamId))
      aclPerm = UString.unionCharacterSets(aclPerm, teamPermissions);
    
    
    /* thats it */
    _ac.recordPermissionsForGlobalID(resolveCompoundPermissions(aclPerm), _gid);
    return true;
  }
  
  /* process ACL */
  
  protected String extractPermissionsOfProjectACL
    (OGoAuthzFetchContext _ac, Collection<OGoObject> _acl)
  {
    if (_ac == null || _acl == null)
      return null;

    String perm = null;
    for (OGoObject ace: _acl) {
      if (!NSJavaRuntime.boolValueForKey(ace, "hasAccess"))
        continue; /* just an attached record, not an ACE */
      
      if (!_ac.contextHasPrincipalId((Number)ace.valueForKey("companyId")))
        continue; /* we do not qualify for this ACE */
      
      /* found an ACE, add the permissions to our set */
      
      perm = UString.unionCharacterSets
        (perm, (String)ace.valueForKey("permissions"));
    }
    return perm;
  }
  
  
  /* fetching */

  @SuppressWarnings("unchecked")
  public Map<EOKeyGlobalID, Object> fetchInfosForGlobalIDs
    (OGoAuthzFetchContext _ac, OGoDatabase _db, Collection<EOKeyGlobalID> _gids)
  {
    if (_gids == null || _gids.size() == 0) return null;
    boolean debugOn = log.isDebugEnabled();
    
    // TBD: why do we need to return all the data? We could evaluate the perms
    //      inline?!
    
    /* find entities and fetch-specs */
    
    EOEntity projectsEntity = _db.entityNamed("Projects");
    EOEntity aclEntity      = _db.entityNamed("ProjectsToCompany");
    
    EOFetchSpecification pFS =
      projectsEntity.fetchSpecificationNamed("authzFetch");
    EOFetchSpecification aFS =
      aclEntity.fetchSpecificationNamed("authzFetch");
    
    /* prepare result set */
    
    Map<EOKeyGlobalID, Object> infos =
      new HashMap<EOKeyGlobalID, Object>(_gids.size());
    
    
    /* Extract primary key values from global-ids.
     * Also: we set the minimum access-level for each gid in the map, so that
     * we can be sure that entries w/o ACEs do get the no-access permission. 
     */
    Map<Number, EOKeyGlobalID> pkeyToGlobalID =
      new HashMap<Number, EOKeyGlobalID>(_gids.size());
    for (EOKeyGlobalID gid: _gids) /* extract key */
      pkeyToGlobalID.put(gid.toNumber(), gid);
    
    Collection<Number> pkeys = pkeyToGlobalID.keySet();
    if (debugOn) log.debug("fetch project-infos: #" + pkeys.size());
    
    /* qualify fetch-specs */
    
    pFS = pFS.fetchSpecificationWithQualifierBindings
      ("ids", pkeys, "authIds", _ac.authIds);
    
  
    /* prepare channel and fetch */
    
    EOAdaptorChannel channel = _db.adaptor().openChannelFromPool();
    try {
      /* Note: the query already filters all projects, so we need to check
       *       the requested pkeys and if they are not in the resultset,
       *       we have no access.
       * Note: this returns EORecordMap's! (cannot be enhanced)
       */
      List<Map<String,Object>> projects =
        channel.selectAttributes(null, pFS, false, projectsEntity);
      Collection<Number> aclKeys = null;
      
      for (Map<String,Object> project: projects) {
        Number        pkey = (Number)project.get("project_id");
        EOKeyGlobalID pgid = pkeyToGlobalID.get(pkey);
        
        /* Check owner inline, if we have the owner, we don't need to check
         * anything else, he has always full access
         */
        Number ownerId = (Number)project.get("owner_id");
        if (_ac.contextHasPrincipalId(ownerId)) {
          if (debugOn) log.debug("  detected ownership: " + pkey);
          _ac.recordPermissionsForGlobalID(ownerPermissions, pgid);
          pkeys.remove(pkey); /* optimization to avoid rechecks below */
          continue;
        }
        
        /* we need a copy, so that we can modify it */
        project = new HashMap<String, Object>(project);
        
        /* attach empty-default-ACL */
        project.put("aclPermissions", OGoAuthzFetchContext.noPermission);
        
        if (aclKeys == null)
          aclKeys = new ArrayList<Number>(pkeys.size());
        aclKeys.add(pkey);
        
        infos.put(pgid, project);
      }
      
      
      /* Loop over requested pkeys and check whether they are in the result
       * set (otherwise we have no access).
       */
      if (aclKeys == null || aclKeys.size() == 0) {
        /* no other keys in result set*/
        // TBD: does this conflict with teamId => readonly?
        if (debugOn) log.debug("  no acl keys found, all forbidden: " + pkeys);
        for (Number pkey: pkeys) {
          EOKeyGlobalID pgid = pkeyToGlobalID.get(pkey);
          _ac.recordPermissionsForGlobalID
            (OGoAuthzFetchContext.noPermission, pgid);
        }
      }
      else {
        for (Number pkey: pkeys) {
          if (!aclKeys.contains(pkey)) {
            /* the requested pkey is not in our resultset */
            if (debugOn) log.debug("  acl key not found, forbidden: " + pkey);
            EOKeyGlobalID pgid = pkeyToGlobalID.get(pkey);
            _ac.recordPermissionsForGlobalID
              (OGoAuthzFetchContext.noPermission, pgid);
          }
        }
      }
      
      
      /* fetch ACLs */

      if (aclKeys != null && aclKeys.size() > 0) {
        if (debugOn) log.debug("  check ACLs: " + aclKeys);
        aFS = aFS.fetchSpecificationWithQualifierBindings
          ("ids", aclKeys, "authIds", _ac.authIds);
      
        List<Map<String,Object>> acls =
          channel.selectAttributes(null, aFS, false, aclEntity);
        for (Map<String,Object> ace: acls) {
          if (!UObject.boolValue(ace.get("has_access"))) {
            if (debugOn) log.debug("  skip non-ACE ...");
            continue; /* an attached record, not an ACE (should not happen) */
          }
          
          if (!_ac.contextHasPrincipalId((Number)ace.get("company_id"))) {
            if (debugOn) log.debug("  ACE not covered: " + ace);
            continue; /* should be covered by the fetch, but who knows */
          }
          
          EOKeyGlobalID      pgid = pkeyToGlobalID.get(ace.get("project_id"));
          Map<String,Object> info = (Map<String,Object>)infos.get(pgid);
          if (info == null) {
            log.warn("no info fetched for project: " + pgid);
            info = new HashMap<String, Object>(2);
            infos.put(pgid, info);
          }
          
          /* combine permissions */
          
          String perm = (String)info.get("aclPermissions");
          if (perm == null) {
            log.warn("info has no acl permissions: " + info);
            perm = OGoAuthzFetchContext.noPermission;
          }
          if (debugOn)
            log.debug("  join  ACE: " + pgid + " => base '" + perm + "'");
          
          perm = UString.unionCharacterSets
            (perm, (String)ace.get("access_right")); // raw
          info.put("aclPermissions", perm);
          
          if (debugOn) log.debug("  found ACE: " + pgid + " => '" + perm + "'");
        }
        
      }
      
      // TBD: evaluate infos inline. Hm. Would this give us anything? Its done
      //      by the next auth-iteration anyways
      // System.err.println("I: " + infos);
    }
    finally {
      if (channel != null)
        _db.adaptor().releaseChannel(channel);
    }
    
    return infos;
  }
}
