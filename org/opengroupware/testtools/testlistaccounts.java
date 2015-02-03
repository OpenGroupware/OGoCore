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
package org.opengroupware.testtools;

import java.util.List;

import org.getobjects.eoaccess.EODatabaseDataSource;
import org.getobjects.eocontrol.EODataSource;
import org.getobjects.foundation.NSJavaRuntime;
import org.opengroupware.logic.core.OGoObjectContext;
import org.opengroupware.logic.db.OGoAccount;

public class testlistaccounts {

  static String dburl = "jdbc:postgresql://127.0.0.1/OGo?user=OGo&password=OGo";

  @SuppressWarnings("unchecked")
  public static void main(String[] args) {
    OGoObjectContext oc =
      OGoObjectContext.objectContextForURL(dburl, "test", "abc123", null);
    System.out.println("logged in as: " + oc.loginContext());

    EODataSource ds = new EODatabaseDataSource(oc, "Accounts");
    
    for (OGoAccount account: (List<OGoAccount>)ds.fetchObjects()) {
      System.out.println(account.getName() + ": " +
          NSJavaRuntime.stringValueForKey(account, "firstname") + " " +
          NSJavaRuntime.stringValueForKey(account, "lastname"));
    }
  }

}
