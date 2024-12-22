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
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.getobjects.eocontrol.EOKeyGlobalID;
import org.getobjects.foundation.NSKeyValueCoding;
import org.getobjects.foundation.NSObject;
import org.opengroupware.logic.db.OGoDatabase;

/**
 * Simple class which gives 'rw' access if the account has access to an
 * associated project.
 * 
 * <p>
 * @author helge
 */
public class OGoProjectOwnedObjectPermissionHandler extends NSObject
  implements IOGoPermissionHandler
{
  protected static final Log log = LogFactory.getLog("OGoAuthz");

  public static final IOGoPermissionHandler defaultHandler =
    new OGoProjectOwnedObjectPermissionHandler();

  @SuppressWarnings("rawtypes")
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

    Number projectId = null;
    if (_object != null)
      projectId = (Number)_object.valueForKey("projectId");
    if (_info != null) {
      if (projectId == null)
        projectId = (Number)((Map)_info).get("project_id"); // raw fetches
    }
    
    Boolean canAccessProject =
      _ac.contextHasAccessToProjectWithPrimaryKey(projectId);
    
    EOKeyGlobalID projectGlobalID;
    
    if (canAccessProject == null) {
      projectGlobalID = EOKeyGlobalID.globalIDWithEntityName
        ("Projects", new Object[] { projectId });
      
      if (log.isDebugEnabled()) {
        log.debug("        request: need perms of project: " 
            + _gid + " / " + projectGlobalID);
      }
      _ac.registerObjectDependency(this, _gid, projectGlobalID);
      return false;
    }

    // TBD: rather simplistic ...
    _ac.recordPermissionsForGlobalID(canAccessProject ? "rw" : "", _gid);
    return true;
  }

  public Map<EOKeyGlobalID, Object> fetchInfosForGlobalIDs
    (OGoAuthzFetchContext _ac, OGoDatabase _db, Collection<EOKeyGlobalID> _gids)
  {
    // TBD: fetch the relevant information
    log.error("fetch of project subobject not implement: " + _gids);
    return null;
  }
}
