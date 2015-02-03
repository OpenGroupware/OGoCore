/*
  Copyright (C) 2008 Helge Hess

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
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.getobjects.eoaccess.EODatabase;
import org.getobjects.eoaccess.EOEntity;
import org.getobjects.eocontrol.EOKeyGlobalID;
import org.getobjects.foundation.NSKeyValueCoding;
import org.getobjects.foundation.NSObject;
import org.opengroupware.logic.db.OGoDatabase;

/**
 * OGoGenericPermissionHandler
 * <p>
 * Handler which works with arbitary objects/EOEntities by scanning model
 * information.
 * <p>
 * Currently this always returns 'dirw' (delete, insert, read, write).
 * 
 * @author helge
 */
public class OGoGenericPermissionHandler extends NSObject
  implements IOGoPermissionHandler
{
  protected static final Log log = LogFactory.getLog("OGoAuthz");

  protected EOEntity entity;
  
  public OGoGenericPermissionHandler(final EOEntity _entity) {
    this.entity = _entity;
  }
  public OGoGenericPermissionHandler(EODatabase _db, String _entityName) {
    this(_db != null ? _db.entityNamed(_entityName) : null);
  }
  
  /* operations */

  public boolean process
    (final OGoAuthzFetchContext _ac, final EOKeyGlobalID _kgid,
     final NSKeyValueCoding _object, final Object _objectInfo)
  {
    if (_kgid == null)
      return false; /* permission not resolved */
    
    // TBD: scan EOEntity for attributes which are relevant for permission
    //      processing, work on them
    // Support:
    // - owned objects (owner_id)
    _ac.recordPermissionsForGlobalID("dirw", _kgid);
    
    return true; /* permission resolved */
  }

  public Map<EOKeyGlobalID, Object> fetchInfosForGlobalIDs
    (OGoAuthzFetchContext _ac, OGoDatabase _db, Collection<EOKeyGlobalID> _gids)
  {
    if (_gids == null || _gids.size() == 0) return null;

    // TBD: scan EOEntity for attributes which are relevant for permission
    //      processing, then fetch those
    
    return new HashMap<EOKeyGlobalID, Object>(1);
  }
}
