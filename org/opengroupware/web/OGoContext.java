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

import java.util.Calendar;

import javax.security.auth.Subject;
import javax.security.auth.login.LoginContext;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.getobjects.appserver.core.WOApplication;
import org.getobjects.appserver.core.WOContext;
import org.getobjects.appserver.core.WORequest;
import org.getobjects.appserver.publisher.GoUser;
import org.getobjects.appserver.publisher.IGoUser;
import org.getobjects.eoaccess.EODatabaseContext;
import org.opengroupware.logic.core.OGoObjectContext;
import org.opengroupware.logic.db.OGoAccount;
import org.opengroupware.logic.db.OGoDatabase;

public class OGoContext extends WOContext {
  protected static final Log log = LogFactory.getLog("OGoContext");
  
  transient protected OGoDatabase      database;
  transient protected OGoObjectContext objectContext;
  protected Calendar now;

  public OGoContext(WOApplication _app, WORequest _rq) {
    super(_app, _rq);
    this.now = Calendar.getInstance(this.timezone(), this.locale());
  }

  /* accessors */
  
  public OGoApplication oApplication() {
    // TBD: eliminate when possible (currently unused)
    return (OGoApplication)this.application();
  }
  
  public OGoSession oSession() {
    return (this.hasSession()) ? (OGoSession)this.session() : null;
  }
  
  public OGoDatabase db() {
    if (this.database == null){
      this.database = 
        ((IOGoDatabaseProvider)this.application()).databaseForContext(this);
    }
    return this.database;
  }
  
  /* login */
  
  public OGoAccount loginAccount() {
    // FIXME: should this be attached to 'activeUser'?
    if (!this.hasSession())
      return null;
    
    return this.oSession().loginAccount();
  }

  public Object loginId() {
    // FIXME: should this be attached to 'activeUser'?
    OGoAccount acc = this.loginAccount();
    return acc != null ? acc.id : null;
  }

  /* accessors */
  
  /**
   * Returns a reference date which is kept constant across the HTTP
   * transaction. This can be used as a stable timereference for formatting
   * times (eg determining today).
   */
  public Calendar now() {
    return this.now;
  }
  
  /**
   * Returns the OGoObjectContext for this transaction.
   * 
   * @return an OGoObjecContext or null if none could be created
   */
  public OGoObjectContext objectContext() {
    if (this.objectContext != null)
      return this.objectContext;
    
    /* setup database context */
    
    final EODatabaseContext dc = new EODatabaseContext(this.db());
    this.objectContext = new OGoObjectContext(dc, this.loginContext());
    return this.objectContext;
  }
  
  public Subject loginSubject() {
    IGoUser ju = this.activeUser();
    return (ju instanceof GoUser) ? ((GoUser)ju).getSubject() : null;
  }
  public LoginContext loginContext() {
    IGoUser ju = this.activeUser();
    return (ju instanceof GoUser) ? ((GoUser)ju).getLoginContext() : null;
  }
}
