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

import java.util.List;

import org.getobjects.eoaccess.EOEntity;
import org.getobjects.eocontrol.EOFetchSpecification;

/**
 * OGoTeam
 * <p>
 * A team is a group of accounts. That simple :-)
 * <p>
 * A team can be a 'location' team (like 'Berlin'), which is just a special
 * type marker to group people living in the same city.
 * 
 * @author helge
 */
public class OGoTeam extends OGoContact implements Comparable<OGoTeam> {
  // TBD: should be an java.security.acl.Group?
  // Note: 'name' for group name clashes with Principal getName() [the login]
  
  /* fields */
  public String login; /* the unique ID, usually generated  */
  public String name;  /* the name of the team              */
  public String email; /* an email associated with the team */

  /* cached relationships */
  protected List<OGoAccount> accounts;
  
  /* ctor */
  
  public OGoTeam(final EOEntity _entity) {
    super(_entity);
  }
  
  
  /* accessors */

  public void setIsTeam(final boolean _flag) {
    if (!_flag)
      log().error("attempt to remove isTeam flag: " + this);
  }
  public boolean isTeam() { // TBD: object result to make it balanced?
    return true;
  }

  
  /* relationships */
  
  public OGoAccounts qualifiedAccountDataSource() {
    OGoAccounts ds = this.oDatabase().accounts();
    
    EOFetchSpecification fs = 
      ds.entity().fetchSpecificationNamed("accountsForTeamID");
    fs = fs.fetchSpecificationWithQualifierBindings(this);
    
    ds.setFetchSpecification(fs);
    return ds;
  }
  
  // TODO: if we want to support recursive teams, we need to do some work here
  
  @SuppressWarnings("unchecked")
  public List<OGoAccount> accounts() {
    if (log.isDebugEnabled()) log.debug("fetch accounts for: " + this.id);
    
    if (this.accounts == null)
      this.accounts = this.qualifiedAccountDataSource().fetchObjects();
    return this.accounts;
  }

  
  /* compare */
  
  public int compareTo(final OGoTeam _other) {
    if (_other == this) return 0;
    if (_other == null) return 1;
    
    String mn = (String)this.valueForKey("name");
    String on = (String)_other.valueForKey("name");
    if (mn == on)   return 0;
    if (on == null) return 1;
    if (mn == null) return -1;
    
    return mn.compareTo(on);
  }
  

  /* legacy */
  
  @Override
  public String entityNameInOGo5() {
    return "Team";
  }
}
