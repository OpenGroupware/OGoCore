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

import org.getobjects.foundation.UMap;
import org.opengroupware.logic.core.IOGoOperation;
import org.opengroupware.logic.core.OGoObjectContext;
import org.opengroupware.logic.ops.OGoContactInsert;
import org.opengroupware.logic.ops.OGoMasterTransaction;
import org.opengroupware.logic.ops.OGoOperationTransaction;

public class testcomplexcontactadd {

  static String dburl = "jdbc:postgresql://127.0.0.1/OGo2?user=OGo&password=OGo";

  public static void main(String[] args) {
    OGoObjectContext oc =
      OGoObjectContext.objectContextForURL(dburl, "test", "abc123", null);
    if (oc == null) {
      System.err.println("could not login: " + dburl);
      System.exit(1);
    }
    
    System.out.println("logged in as: " + oc.loginContext());

    testcomplexcontactadd runner = new testcomplexcontactadd();
    runner.run(oc);
  }

  public void run(OGoObjectContext _oc) {
    OGoMasterTransaction mtx = new OGoMasterTransaction(_oc);
    Exception error;
    
    System.out.println("master-tx: " + mtx);
    
    if ((error = mtx.begin()) != null) {
      System.err.println("could not begin transaction: " + error);
      System.exit(2);
    }
    
    System.out.println("  did begin tx: " + mtx);
    
    try {
      
      OGoContactInsert op = new OGoContactInsert(_oc, "Persons");
      // in an import we would loop and add many, many
      op.add("lastname", "Mouse", "firstname", "Mickey",
          "addresses", new Object[] {
            UMap.create("key", "private",
                "street", "MickeyStreet 1", "city", "Mousehouse")
          },
          "phones", new Object[] {
            UMap.create("key", "01_tel", "value", "+123-4566")
          }
      );
      op.add("lastname", "Mouse", "firstname", "Minnie",
          "addresses", new Object[] {
            UMap.create("key", "private",
                "street", "MickeyStreet 1", "city", "Mousehouse")
          },
          "phones", new Object[] {
            UMap.create("key", "01_tel", "value", "+123-4566-03")
          }
      );

      OGoOperationTransaction tx = new OGoOperationTransaction(mtx, 
          new IOGoOperation[] { op });
      
      if ((error = tx.run()) != null)
        System.err.println("ERROR: " + error);
      
      // TBD
    }
    finally {
      if ((error = mtx.rollback()) != null) {
        System.err.println("test: could not rollback transaction: " + error);
        System.exit(10);
      }
    }
  }
}
