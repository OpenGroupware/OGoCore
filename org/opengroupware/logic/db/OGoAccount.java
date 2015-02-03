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

import java.security.Principal;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.getobjects.eoaccess.EOEntity;
import org.getobjects.eocontrol.EODataSource;
import org.getobjects.eocontrol.EOFetchSpecification;
import org.getobjects.foundation.UString;

/**
 * OGoAccount
 * <p>
 * An account in the system. We keep them separate from OGoPerson's because
 * accounts might be in an entirely different datasource.
 * <p>
 * Login fields:
 * <ul>
 *   <li>login
 *   <li>password
 *   <li>isLocked
 *   <li>canChangePassword
 * </ul> 
 */
public class OGoAccount extends OGoContact
  implements Principal
{
  protected static final Log log = LogFactory.getLog("OGoAccounts");
  
  /* fields */
  protected String login;
  
  /* cached relationships */
  protected List<OGoTeam> teams;
  protected Number[]      teamIDs;
  protected Number[]      authIDs;
  
  /* ctor */

  public OGoAccount(final EOEntity _entity) {
    super(_entity);
  }
  
  /* accessors */
  
  public void setName(final String _login) { // TBD: necessary?
    this.login = _login;
  }
  public String getName() { // JAAS
    return this.login;
  }

  public void setLogin(final String _login) {
    // this is the property bound in the EOModel!
    this.login = _login;
  }
  public String login() {
    return this.login;
  }
  
  /* teams and auth ids */
  
  public OGoTeams qualifiedTeamDataSource() {
    OGoTeams teamsDS = this.oDatabase().teams();
    
    EOFetchSpecification fs = 
      teamsDS.entity().fetchSpecificationNamed("teamsForAccountID");
    fs = fs.fetchSpecificationWithQualifierBindings(this);
    
    teamsDS.setFetchSpecification(fs);
    return teamsDS;
  }
  
  // TODO: if we want to support recursive teams, we need to do some work here
  
  @SuppressWarnings("unchecked")
  public List<OGoTeam> teams() {
    if (log.isDebugEnabled()) log.debug("fetch teams for: " + this.id);
    
    if (this.teams == null) {
      // TBD: expire caches
      this.teams = this.qualifiedTeamDataSource().fetchObjects();
      this.teamIDs = null; /* keep those consistent */
    }
    return this.teams;
  }
  
  @SuppressWarnings("unchecked")
  public Number[] teamIDs() {
    // TBD: expire caches
    if (this.teamIDs != null)
      return this.teamIDs;
    
    if (log.isDebugEnabled()) log.debug("fetch team ids for: " + this.id);
    
    // TODO: if we have the teams, extract the IDs from that
    List<OGoTeam> lTeams = this.teams;
    if (lTeams == null) {
      EODataSource         ds = this.qualifiedTeamDataSource();
      EOFetchSpecification fs = ds.fetchSpecification();
      fs.setFetchAttributeNames(new String[] { "id" });
    
      lTeams = ds.fetchObjects();
    }
    
    /* extract IDs (if List supports KVC this is a simple vFK("id") ... */
    if (lTeams != null) {
      this.teamIDs = new Integer[lTeams.size()];
      for (int i = 0; i < lTeams.size(); i++)
        this.teamIDs[i] = lTeams.get(i).id();
    }
    return this.teamIDs;
  }

  /*
   * This method returns all the 'authentication entities' the account belongs
   * to. This is the id of the account itself plus all the IDs of the groups
   * he belongs to.
   * The array is used in many qualifiers which need to check whether the login
   * may access an object. 
   */
  public Number[] authIDs() {
    if (this.authIDs != null)
      return this.authIDs;
    
    Number[] lTeamIDs = this.teamIDs();

    this.authIDs = 
      new Integer[(lTeamIDs != null) ? (lTeamIDs.length + 1) : 1];
    
    this.authIDs[0] = this.id();
    System.arraycopy(lTeamIDs, 0, this.authIDs, 1, lTeamIDs.length);
    
    return this.authIDs;
  }
  
  public String authIdsAsStringList() {
    Number[] ids = this.authIDs();
    if (ids == null) return null;
    return UString.componentsJoinedByString(ids, ",");
  }
  
  
  /* events */
  
  @SuppressWarnings("unchecked")
  public List<OGoEvent> fetchUpcomingEvents(final int _limit) {
    OGoEvents ds = this.oDatabase().events("upcomingEvents", this);
    ds.fetchSpecification().setFetchLimit(_limit);
    return ds.fetchObjects();
  }
}
