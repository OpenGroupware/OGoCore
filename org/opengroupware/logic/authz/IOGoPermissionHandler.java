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

import org.getobjects.eocontrol.EOKeyGlobalID;
import org.getobjects.foundation.NSKeyValueCoding;
import org.opengroupware.logic.db.OGoDatabase;

public interface IOGoPermissionHandler {

  /**
   * Process the permissions of the given global-id. If the available
   * information is insufficient, request necessary data from the authz
   * context.
   * <p>
   * This object is invoked in the scan phase of the authz context. It
   * returns <code>true</code> if it was able to resolve the permission
   * given the available data and <code>false</code> if it needs additional
   * information.
   * 
   * @param _ac    - the authorization context which manages the auth fetch
   * @param kgid   - the EOKeyGlobalID of the OGo object
   * @param object - the fetched EO, when available in the editing context 
   * @param objectInfo - the fetched extra info, when available in the auth ctx
   * @return true if the permission was resolved, false otherwise
   */
  public boolean process
    (OGoAuthzFetchContext _ac, EOKeyGlobalID kgid,
     NSKeyValueCoding object, Object objectInfo);
  
  /**
   * If the context has insufficient information to derive the permission of
   * a global-ID the authz-context will give the handler the chance to fetch
   * the attributes necessary.
   * <p>
   * The handler can return any information it needs, the context itself does
   * not look into it. The Object returned will be passed into the process
   * method.
   * @param _ac TODO
   * @param _db TODO
   * @param _gids - the global-IDs which we need the information fore
   * 
   * @return auth relevant information on the GIDs
   */
  public Map<EOKeyGlobalID, Object> fetchInfosForGlobalIDs
    (OGoAuthzFetchContext _ac, OGoDatabase _db, Collection<EOKeyGlobalID> _gids);
}
