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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.getobjects.eocontrol.EOEditingContext;
import org.getobjects.foundation.UString;
import org.postgresql.util.UnixCrypt;

public class OGoAccounts extends OGoContactDataSource {
  protected static final Log log = LogFactory.getLog("OGoAccounts");

  public OGoAccounts(EOEditingContext _ec, String _entityName) {
    super(_ec, _entityName);
  }
  
  /* operations */
  
  public OGoAccount login(String _login, String _pwd) {
    // TBD: write a JAAS module, use it for auth
    if (this.objectContext() == null) {
      log.fatal("missing database ...");
      return null;
    }
    if (_login == null || _login.length() == 0) {
      log.fatal("got no login name to perform login ...");
      return null;
    }
    if (_pwd == null) _pwd = "";
    
    /* fetch stored password */
    
    OGoObject p = (OGoObject)
      this.find("cryptedPassword", "login", _login);
    if (p == null) {
      log.error("did not find user in database: '" + _login + "'");
      return null;
    }
    String pwd = (String)p.valueForKey("password");
    if (log.isDebugEnabled()) log.debug("stored hash: " + pwd);
    
    String pwdHash = null;

    if (pwd == null)
      pwdHash = null;
    else if (pwd.startsWith("{md5}"))
      pwdHash = "{md5}" + UString.md5HashForString(_pwd);
    else {
      /* crypt password */
      pwdHash = UnixCrypt.crypt(pwd, _pwd);
    }
    
    /* lookup account */
    
    OGoAccount account = (OGoAccount) 
      this.find("login", "login", _login, "password", pwdHash);
    if (account == null)
      log.error("could not login user '" + _login + "' with provided pwd.");
    
    return account;
  }
}
