/*
  Copyright (C) 2007-2014 Helge Hess

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
package org.opengroupware.web;

import javax.security.auth.Subject;
import javax.security.auth.login.LoginContext;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.getobjects.appserver.core.WOSession;
import org.getobjects.eoaccess.EODatabaseContext;
import org.opengroupware.logic.auth.OGoAccountPrincipal;
import org.opengroupware.logic.db.OGoAccount;
import org.opengroupware.logic.db.OGoDatabase;

public class OGoSession extends WOSession {
  // TBD: logout when 'terminate' is called? would be nice I guess
  protected static final Log log = LogFactory.getLog("OGoSession");
  
  transient protected OGoDatabase       database;
  transient protected EODatabaseContext dbCtx;
  protected LoginContext loginContext;

  public OGoSession() {
  }
  
  /* accessors */
  
  public void setLoginContext(LoginContext _lc) {
    this.loginContext = _lc;
  }
  public LoginContext loginContext() {
    return this.loginContext;
  }
  
  public Subject loginSubject() {
    return this.loginContext != null ? this.loginContext.getSubject() : null;
  }
  
  // TBD: DB is conceptually attached to the application object?
  // except: we want to provide access to multiple OGo DBs from one app-object? 
  public void setDatabase(final OGoDatabase _db) {
    this.database = _db;
    this.dbCtx    = _db != null ? new EODatabaseContext(_db) : null;
  }
  public OGoDatabase database() {
    return this.database;
  }
  
  public EODatabaseContext dbContext() {
    return this.dbCtx;
  }
  
  public OGoAccount loginAccount() {
    final Subject subject = this.loginSubject();
    if (subject == null)
      return null;
    
    /* find uid */
    final Number uid = this.loginId();
    if (uid == null)
      return null;

    // TBD: cache this?
    return (OGoAccount)this.database.accounts().findById(uid);
  }
  
  public Number loginId() {
    final Subject subject = this.loginSubject();
    if (subject == null) return null;
    
    /* find uid */
    for (OGoAccountPrincipal p:subject.getPrincipals(OGoAccountPrincipal.class))
      return p.id();
    
    return null;
  }
  public String loginName() {
    final Subject subject = this.loginSubject();
    if (subject == null) return null;
    
    /* find uid */
    for (OGoAccountPrincipal p:subject.getPrincipals(OGoAccountPrincipal.class))
      return p.getName();
    
    return null;
  }
}
