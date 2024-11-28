/*
  Copyright (C) 2008-2024 Helge Hess

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

import java.security.Principal;
import java.nio.file.attribute.GroupPrincipal; // replacement for Group?
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Vector;

import org.getobjects.foundation.NSObject;

/**
 * Represents an OGo team (a group) in JAAS. It also supports being a JAAS
 * Group.
 * <p>
 * Note: in OGo/J teams can be nested in the database.
 * 
 * <p>
 * @author helge
 */
public class OGoTeamPrincipal extends NSObject
  implements IOGoPrincipal, GroupPrincipal
{
  // TBD: Not exactly sure whether this is right. I think the ACL package needs
  //      all members of the team to do its evaluations. We just fill in the
  //      accounts we authorized.
  
  // TBD: also store a database ID?
  protected Number id;
  protected String name;
  protected Collection<Principal> members;
  
  public OGoTeamPrincipal(final Number _id, final String _name) {
    this.id    = _id;
    this.name = _name;
  }

  /* accessors */

  public Number id() {
    return this.id;
  }

  public String getName() {
    if (this.name == null && this.id != null)
      return "OGo" + this.id;
    
    return this.name;
  }
  
  
  /* being a Group */
  
  public boolean addMember(final Principal _user) {
    if (_user == null)
      return false;
    
    if (this.members == null)
      this.members = new ArrayList<Principal>(16);
    else if (this.members.contains(_user))
      return false;
    
    this.members.add(_user);
    return true;
  }

  public boolean isMember(Principal _member) {
    if (_member == null || this.members == null)
      return false;
    
    return this.members.contains(_member);
  }

  public Enumeration<? extends Principal> members() {
    // TBD: better way to convert an Iterator to an Enumeration?
    Vector<Principal> v = new Vector<Principal>(this.members);
    return v.elements();
  }

  public boolean removeMember(final Principal _member) {
    if (_member == null || this.members == null)
      return false;
    
    return this.members.remove(_member);
  }
  
  
  /* equality */
  
  @Override
  public boolean equals(final Object _other) {
    if (_other == this) return true;
    if (_other == null) return false;
    
    if (_other instanceof OGoTeamPrincipal)
      return ((OGoTeamPrincipal)_other).isEqualToTeamPrincipal(this);
    
    return false;
  }
  
  public boolean isEqualToTeamPrincipal(final OGoTeamPrincipal _other) {
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
    
    if (this.name != null && this.id != null) {
      _d.append(this.name);
      _d.append("=");
      _d.append(this.id);
    }
    else if (this.name != null) {
      _d.append(" login=");
      _d.append(this.name);
    }
    else if (this.id != null) {
      _d.append(" id=");
      _d.append(this.id);
    }
    else
      _d.append(" empty?!");
    
    if (this.members != null) {
      _d.append(" members=#");
      _d.append(this.members.size());
    }
  }
}
