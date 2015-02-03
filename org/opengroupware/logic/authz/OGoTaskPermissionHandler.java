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
import org.getobjects.foundation.NSKeyValueCoding;
import org.getobjects.foundation.NSObject;
import org.opengroupware.logic.db.OGoDatabase;

/**
 * OGoTaskPermissionHandler
 * <p>
 * Permissions for tasks.
 * <ul>
 *   <li>if the task has a project and the account has access to that project,
 *       the account has 'lrwaAd' access
 *   <li>if you are either the creator or the executant, you have
 *       'lrwaA' access. Note that the executant can be a team. 
 * </ul>
 * 
 * <p>
 * Permissions
 * <ul>
 *   <li><code>l</code> - task title is visibile
 *   <li><code>r</code> - all details can be shown 
 *   <li><code>w</code> - task can be edited
 *   <li><code>a</code> - task can be archived (put into 30_archived state)
 *   <li><code>d</code> - task can be deleted
 *   <li><code>A</code> - task can be accepted (put into 20_processing state)
 * </ul>
 * 
 * <p>
 * Relevant attributes:
 * <ul>
 *   <li>projectId
 *   <li>creatorId
 *   <li>ownerId (executant_id column)
 *   <li>isOwnerTeam (is_team_job)
 * </ul>
 * 
 * <p>
 * Fetch Info<br />
 * The fetch info is a Map.
 * 
 * <p>
 * @author helge
 */
public class OGoTaskPermissionHandler extends NSObject
  implements IOGoPermissionHandler
{
  protected static final Log log = LogFactory.getLog("OGoAuthz");

  public static final IOGoPermissionHandler defaultHandler =
    new OGoTaskPermissionHandler();

  protected static final String creatorPermission = "lrwadA";
  protected static final String ownerPermission   = "lrwaA";
  protected static final String projectPermission = "lr";
  
  @SuppressWarnings("rawtypes")
  public boolean process
    (OGoAuthzFetchContext _ac, EOKeyGlobalID _gid,
     NSKeyValueCoding _object, Object _info)
  {
    if (_object == null && _info == null) {
      if (log.isDebugEnabled())
        log.debug("process task-gid w/o object/info: " + _gid);
      
      /* we do NOT have the info for this GID and need to fetch it */
      _ac.requestFetchOfInfo(this, _gid);
      
      return false; /* pending, need to fetch info */
    }
    
    
    /* first check the creator (most rights, check first) */
    
    Number creatorId = null;
    if (_object != null)
      creatorId = (Number)_object.valueForKey("creatorId");
    if (creatorId == null && _info != null) 
      creatorId = (Number)((Map)_info).get("creator_id"); // raw fetches
    
    if (_ac.contextHasPrincipalId(creatorId)) {
      if (log.isDebugEnabled())
        log.debug("detected creator on task: " + _gid);
      _ac.recordPermissionsForGlobalID(creatorPermission, _gid);
      return true; /* ok, found it */
    }
    
    
    /* Then check the owner (aka executant). The owner can be a team! It doesn't
     * matter for our check.
     */

    Number ownerId = null;
    if (_object != null)
      ownerId = (Number)_object.valueForKey("ownerId");
    if (ownerId == null && _info != null) 
      ownerId = (Number)((Map)_info).get("executant_id"); // raw fetches
    
    if (_ac.contextHasAccountId(ownerId)) {
      if (log.isDebugEnabled())
        log.debug("detected ownership on task: " + _gid);
      _ac.recordPermissionsForGlobalID(ownerPermission, _gid);
      return true; /* ok, found it */
    }
    
    
    /* finally check the project */

    Number projectId = null;
    if (_object != null)
      projectId = (Number)_object.valueForKey("projectId");
    if (projectId == null && _info != null) 
      projectId = (Number)((Map)_info).get("project_id"); // raw fetches
    
    if (projectId != null) {
      Boolean canAccessProject =
        _ac.contextHasAccessToProjectWithPrimaryKey(projectId);
      
      if (canAccessProject == null) {
        /* We have a project id, but do not know project access yet. Tell the
         * authz context that we need to know.
         */
        EOKeyGlobalID projectGID = EOKeyGlobalID.globalIDWithEntityName
          ("Projects", new Object[] { projectId });
        _ac.registerObjectDependency(this, _gid, projectGID);
        
        if (log.isDebugEnabled())
          log.debug("requested project access for task: " + _gid);
        return false; /* need info */
      }
      
      /* Note that the permissions do NOT depend on the project permission
       * flags. It just whether the user has access to the project which implies
       * some minimal task permissions.
       */
      String perm = canAccessProject.booleanValue()
        ? projectPermission : OGoAuthzFetchContext.noPermission;
      
      if (log.isDebugEnabled())
        log.debug("    DONE: found project permissions: " + projectId);
        
      _ac.recordPermissionsForGlobalID(perm, _gid);
      return true; /* ok, found it */
    }
    
    if (log.isDebugEnabled())
      log.debug("task has no associated project => no permission: " + _gid);

    
    /* OK, user has no permissions on the given task */
    _ac.recordPermissionsForGlobalID(OGoAuthzFetchContext.noPermission, _gid);
    return true;
  }
  
  public Map<EOKeyGlobalID, Object> fetchInfosForGlobalIDs
    (OGoAuthzFetchContext _ac, OGoDatabase _db, Collection<EOKeyGlobalID> _gids)
  {
    if (_gids == null || _gids.size() == 0) return null;
    boolean debugOn = log.isDebugEnabled();

    
    /* extract primary keys */

    Map<Number, EOKeyGlobalID> pkeyToGlobalID =
      new HashMap<Number, EOKeyGlobalID>(_gids.size());
    for (EOKeyGlobalID gid: _gids) /* extract key */
      pkeyToGlobalID.put(gid.toNumber(), gid);

    
    /* lookup entity/fetchspec */
    
    EOEntity entity = _db.entityNamed("Tasks");
    if (entity == null) {
      log.error("could not locate Tasks entity");
      return null;
    }
    
    EOFetchSpecification fs = entity.fetchSpecificationNamed("authzFetch");
    if (fs == null) {
      log.error("did not find 'authzFetch' specification");
      return null;
    }
    
    fs = fs.fetchSpecificationWithQualifierBindings
      ("ids", _gids, "authIds", _ac.authIds);
    

    /* prepare result set */
    
    Map<EOKeyGlobalID, Object> infos =
      new HashMap<EOKeyGlobalID, Object>(_gids.size());
    

    /* perform fetch */
    
    EOAdaptorChannel channel = _db.adaptor().openChannelFromPool();
    try {
      List<Map<String,Object>> tasks =
        channel.selectAttributes(null, fs, false, entity);
      
      for (Map<String,Object> taskRow: tasks) {
        Number        pkey = (Number)taskRow.get("job_id");
        EOKeyGlobalID pgid = pkeyToGlobalID.get(pkey);
        
        /* Check owner inline, if we have the owner, we don't need to check
         * anything else, he has always full access
         */
        Number ownerId = (Number)taskRow.get("owner_id");
        if (_ac.contextHasPrincipalId(ownerId)) {
          if (debugOn) log.debug("  detected ownership: " + pkey);
          _ac.recordPermissionsForGlobalID(ownerPermission, pgid);
          continue;
        }
        
        infos.put(pgid, taskRow);
      }
    }
    finally {
      if (channel != null)
        _db.adaptor().releaseChannel(channel);
    }

    return infos;
  }
  
}
