/*
  Copyright (C) 2007-2008 Helge Hess

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

import org.getobjects.eoaccess.EOEntity;
import org.getobjects.foundation.UObject;

/**
 * OGoProjectMembership
 * <p>
 * Represents a connection between a person/company and a project.
 * <p>
 * The connection can be some arbitrary connection,
 * or it can be an ACL entry which denotes which person can access
 * the project.
 * 
 * @author helge
 */
public class OGoProjectMembership extends OGoObject {
  
  public Number projectId;
  public Number companyId;
  public String permissions; // eg 'rwid' or 'm'
  public String info;
  protected boolean hasAccess;
  
  public OGoProjectMembership(EOEntity _entity) {
    super(_entity);
  }
  
  /* accessors */
  
  public void setHasAccess(Object _value) {
    this.hasAccess = UObject.boolValue(_value);
  }
  public Object hasAccess() {
    return this.hasAccess;
  }

  /* permissions */
  
  public void applyPermissions(String _perms) {
    if (_perms.length() == 0) {
      // TBD: use a special 'forbidden' value
      //System.err.println("RESET PERM: '" + _perms + "': " + this);
      this.projectId   = null;
      this.companyId   = null;
      this.permissions = null;
      this.hasAccess   = false;
      this.info        = null;
    }
  }
}
