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
package org.opengroupware.logic.db;

import org.getobjects.foundation.NSObject;

/**
 * OGoObjectHasPermTrampoline
 * <p>
 * Checks whether a given OGoObject has the supplied permissions.
 * Sample:<pre>
 * object.hasPermission.w
 * object.hasPermission.lbhM</pre>
 * 
 * @author helge
 */
public class OGoObjectHasPermTrampoline extends NSObject {
  
  protected OGoObject object;
  
  public OGoObjectHasPermTrampoline(OGoObject _object) {
    this.object = _object;
  }
  
  /* accessors */
  
  @Override
  public void takeValueForKey(Object _value, String _key) {
    // do nothing
  }

  @Override
  public Object valueForKey(String _key) {
    if (this.object == null)
      return null;
    
    String perms = this.object.appliedPermissions();
    if (perms == null) /* no permissions applied */
      return null;
    
    int len = _key.length();
    if (len == 0) /* no permissions requested */
      return Boolean.TRUE;
    
    for (int i = 0; i < len; i++) {
      char c = _key.charAt(i);
      if (perms.indexOf(c) < 0) /* perms does not contain the requested char */
        return Boolean.FALSE;
    }
    
    return Boolean.TRUE; /* all checks passed */
  }

  @Override
  public void appendAttributesToDescription(StringBuilder _d) {
    super.appendAttributesToDescription(_d);
    
    if (this.object != null) {
      _d.append(" on=");
      _d.append(this.object.toString());
    }
    else
      _d.append(" no-object");
  }
  
}
