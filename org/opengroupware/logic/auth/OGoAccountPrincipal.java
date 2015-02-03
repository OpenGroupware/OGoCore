/*
  Copyright (C) 2008-2014 Helge Hess

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
package org.opengroupware.logic.auth;

import org.getobjects.foundation.NSObject;
import org.getobjects.foundation.UObject;

/**
 * Represents an OGo account in JAAS. The primary key of the OGo account is not
 * the login (which can be changed), but the database id (primary key).
 * <p>
 * TBD: How would that work with LDAP authentication? I think in the same way,
 * even an LDAP account needs a record in the OGo database, so that it can be
 * connected to all the other objects (and used in ACLs, etc etc).
 * 
 * @author helge
 */
public class OGoAccountPrincipal extends NSObject implements IOGoPrincipal {
  
  // TBD: also store a database ID?
  protected Number id;
  protected String login;
  protected String preservedPassword; // maybe we should at least encrypt it
  
  public OGoAccountPrincipal(Number _id, String _login) {
    this.id    = _id;
    this.login = _login;
  }
  
  /* accessors */

  public Number id() {
    return this.id;
  }

  public String getName() {
    return this.login;
  }
  
  public void setPreservedPassword(String _pwd) {
    this.preservedPassword = _pwd;
  }
  
  /* legacy */
  
  public String login() {
    return this.getName();
  }

  
  /* equality */
  
  @Override
  public boolean equals(Object _other) {
    if (_other == this) return true;
    if (_other == null) return false;
    
    if (_other instanceof OGoAccountPrincipal)
      return ((OGoAccountPrincipal)_other).isEqualToAccountPrincipal(this);
    
    return false;
  }
  
  public boolean isEqualToAccountPrincipal(OGoAccountPrincipal _other) {
    if (_other == this) return true;
    if (_other == null) return false;
    
    // TBD: compare some DB ID
    return this.id().equals(_other.id());
  }
  
  @Override
  public int hashCode() {
    return this.id != null ? this.id.hashCode() : -1;
  }
  

  /* description */

  @Override
  public void appendAttributesToDescription(final StringBuilder _d) {
    super.appendAttributesToDescription(_d);
    
    if (this.login != null && this.id != null) {
      _d.append(this.login);
      _d.append("=");
      _d.append(this.id);
    }
    else if (this.login != null) {
      _d.append(" login=");
      _d.append(this.login);
    }
    else if (this.id != null) {
      _d.append(" id=");
      _d.append(this.id);
    }
    else
      _d.append(" empty?!");
    
    if (UObject.isNotEmpty(this.preservedPassword))
      _d.append(" has-pwd");
  }
}
