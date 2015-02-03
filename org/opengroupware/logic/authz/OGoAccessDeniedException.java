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
package org.opengroupware.logic.authz;

import javax.security.auth.login.LoginContext;

import org.getobjects.eocontrol.EOGlobalID;
import org.getobjects.foundation.NSException;
import org.getobjects.foundation.UString;

/**
 * OGoAccessDeniedException
 * <p>
 * Used to represent missing permissions.
 * 
 * @author helge
 */
public class OGoAccessDeniedException extends NSException {
  private static final long serialVersionUID = 1L;
  
  protected LoginContext lc;
  protected EOGlobalID   gid;
  protected String requestedPermissions;
  protected String availablePermissions;

  public OGoAccessDeniedException
    (LoginContext _lc, EOGlobalID _gid, String _requested, String _avail)
  {
    super("access denied");
    this.lc  = _lc;
    this.gid = _gid;
    this.requestedPermissions = _requested;
    this.availablePermissions = _avail;
  }
  
  /* accessors */
  
  public LoginContext loginContext() {
    return this.lc;
  }
  
  public EOGlobalID globalID() {
    return this.gid;
  }

  public String requestedPermissions() {
    return this.requestedPermissions;
  }
  
  public String availablePermissions() {
    return this.availablePermissions;
  }
  
  public String missingPermissions() {
    /* remove the available perms from the requested ones */
    return UString.exceptCharacterSets
      (this.requestedPermissions, this.availablePermissions);
  }
  
  /* convenience Go support */
  
  public int httpStatus() {
    return 403;
  }

  /* description */

  @Override
  public void appendAttributesToDescription(StringBuilder _d) {
    super.appendAttributesToDescription(_d);
    
    _d.append(" needs='" + this.missingPermissions() + '\'');
    
    if (this.requestedPermissions != null)
      _d.append(" asked='" + this.requestedPermissions + '\'');
    if (this.availablePermissions != null)
      _d.append(" have='" + this.availablePermissions + '\'');
  }
}
