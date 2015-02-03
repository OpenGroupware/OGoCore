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
package org.opengroupware.testtools;

import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;

import org.opengroupware.logic.auth.OGoLoginModule;
import org.opengroupware.logic.db.OGoDatabase;

public class testlogin {
  
  static String dburl = "jdbc:postgresql://127.0.0.1/OGo?user=OGo&password=OGo";

  public static void main(String[] args) {
    OGoDatabase db = OGoDatabase.databaseForURL(dburl, null);
    System.out.println("DB: " + db);

    LoginContext lc;
    
    lc = OGoLoginModule.jaasLogin(db, "test", "thats not a password");
    if (lc != null && lc.getSubject() != null)
      System.err.println("hm, login succeeded with incorrect password?!");
    else
      System.out.println("login with incorrect pwd failed: " + lc);

    lc = OGoLoginModule.jaasLogin(db, "test", "abc123");
    if (lc != null && lc.getSubject() != null) {
      System.out.println("login succeeded: " + lc);
      try {
        lc.logout();
      }
      catch (LoginException e) {
        System.err.println("logout failed: " + e);
      }
    }
    else
      System.err.println("could not login: " + lc);
  }

}
