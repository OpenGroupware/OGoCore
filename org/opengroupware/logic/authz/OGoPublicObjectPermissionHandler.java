/*
  Copyright (C) 2007 Helge Hess

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

public class OGoPublicObjectPermissionHandler extends NSObject
  implements IOGoPermissionHandler
{
  protected static final Log log = LogFactory.getLog("OGoAuthz");

  public static final IOGoPermissionHandler defaultHandler =
    new OGoPublicObjectPermissionHandler();

  public boolean process
    (OGoAuthzFetchContext _ac, EOKeyGlobalID kgid,
     NSKeyValueCoding object, Object objectInfo)
  {
    _ac.recordPermissionsForGlobalID
      ("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ", kgid);
    return true;
  }

  public Map<EOKeyGlobalID, Object> fetchInfosForGlobalIDs
    (OGoAuthzFetchContext _ac, OGoDatabase _db, Collection<EOKeyGlobalID> _gids)
  {
    return null;
  }
  
}
