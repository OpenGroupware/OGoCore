/*
  Copyright (C) 2008 Helge Hess

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

import java.io.IOException;

import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;

import org.opengroupware.logic.auth.OGoDefaultLoginConfig;
import org.opengroupware.logic.auth.OGoTokenCallback;
import org.opengroupware.logic.db.OGoDatabase;

public class testjaaslogin implements CallbackHandler {
  
  static String dburl = "jdbc:postgresql://127.0.0.1/OGo?user=OGo&password=OGo";

  public static void main(String[] args) {
    OGoDatabase db = OGoDatabase.databaseForURL(dburl, null);
    System.out.println("DB: " + db);

    new testjaaslogin().run(db);
    
    /*
    OGoLoginContext lc;
    
    lc = db.login("test", "thats not a password");
    if (lc != null && lc.isValid())
      System.err.println("hm, login succeeded with incorrect password?!");
    else
      System.out.println("login with incorrect pwd failed: " + lc);

    lc = db.login("test", "abc123");
    if (lc != null && lc.isValid()) {
      System.out.println("login succeeded: " + lc);
      lc.logout();
    }
    else
      System.err.println("could not login: " + lc);
      */
  }
  
  static final String[] credentials = {
    "test", "thats not a password",
    "test", "abc123" // assume that exists in the DB
  };
  int testIdx;
  
  public void run(OGoDatabase _db) {
    OGoDefaultLoginConfig cfg = new OGoDefaultLoginConfig(_db);
    
    for (this.testIdx = 0; this.testIdx < credentials.length; this.testIdx+=2) {
      LoginContext jlc = null;
      Subject subject = new Subject();
      
      try {
        jlc = new LoginContext(
            "OGo",   /* application     */
            subject, /* subject */
            this,    /* CallbackHandler */
            cfg      /* configuration */);
      }
      catch (LoginException e) {
        System.err.println("LoginContext setup failed " + e.getClass() + ": " +
            e.getMessage());
      }
      if (jlc == null)
        continue;
      
      /* login */
      
      try {
        jlc.login();
      }
      catch (LoginException e) {
        System.err.println("login failed " + e.getClass() + ": " +
            e.getMessage());
        continue;
      }
      
      /* did login */
      
      System.out.println("did login!");
      
      /* logout */
      
      try {
        jlc.logout();
      }
      catch (LoginException e) {
        System.err.println("logout failed " + e.getClass() + ": " +
            e.getMessage());
      }
    }
  }

  public void handle(Callback[] _callbacks)
    throws IOException, UnsupportedCallbackException
  {
    System.out.println("JAAS callbacks: #" + _callbacks.length);

    for(Callback cb: _callbacks) {
      if (cb instanceof NameCallback) {
        NameCallback nc = (NameCallback)cb;
        System.out.println("  name-cb (" + nc.getPrompt() + ")");
        nc.setName(credentials[this.testIdx]);
      }
      else if (cb instanceof PasswordCallback) {
        PasswordCallback pc = (PasswordCallback)cb;
        System.out.println("  pwd-cb (" + pc.getPrompt() + ")");
        pc.setPassword(credentials[this.testIdx + 1].toCharArray());
      }
      else if (cb instanceof OGoTokenCallback) {
        System.out.println("  token-cb");
      }
      else {
        System.out.println("unknown CB: " + cb.getClass());
      }
    }
  }
}
