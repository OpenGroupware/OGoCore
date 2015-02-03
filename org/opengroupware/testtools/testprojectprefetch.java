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
package org.opengroupware.testtools;

import java.util.Collection;
import java.util.List;

import org.getobjects.eoaccess.EOAccessDataSource;
import org.getobjects.eoaccess.EODatabaseDataSource;
import org.getobjects.eocontrol.EOFetchSpecification;
import org.getobjects.foundation.NSJavaRuntime;
import org.getobjects.foundation.NSObject;
import org.opengroupware.logic.core.OGoObjectContext;
import org.opengroupware.logic.db.OGoProject;

public class testprojectprefetch {

  static String dburl = "jdbc:postgresql://127.0.0.1/OGo?user=OGo&password=OGo";

  private static final String[] prefetchPathes = {
    //"notes", "tasks",
    "teams",     "teams.team",
    "persons",   "persons.person", "persons.person.employments",
    "companies", "companies.company"
  };

  @SuppressWarnings({ "unchecked", "rawtypes" })
  public static void main(String[] args) {
    OGoObjectContext oc =
      OGoObjectContext.objectContextForURL(dburl, "test", "abc123", null);
    System.out.println("logged in as: " + oc.loginContext());
    
    
    // not relevant for our issue
    oc.setAutoFetchPermissions(false);
    
    
    EOAccessDataSource ds = new EODatabaseDataSource(oc, "Projects");
    
    EOFetchSpecification fs = ds.entity().fetchSpecificationNamed("default");
    
    fs = fs.fetchSpecificationWithQualifierBindings
      ("authIds", oc.authenticatedIDs());

    fs.setPrefetchingRelationshipKeyPaths(prefetchPathes);
    
    ds.setFetchSpecification(fs);
    
    List<OGoProject> projects = ds.fetchObjects();
    if (projects != null) {

      System.out.println("fetched projects: " + projects.size());

      for (OGoProject p: projects) {
        Collection<NSObject> teams     = (Collection)p.valueForKey("teams");
        Collection<NSObject> persons   = (Collection)p.valueForKey("persons");
        Collection<NSObject> companies = (Collection)p.valueForKey("companies");

        System.out.println("" +
            NSJavaRuntime.stringValueForKey(p, "name") + ": ");

        if (teams != null) {
          System.out.println("  teams:     #" + teams.size());
        }

        if (persons != null) {
          System.out.println("  persons:   #" + persons.size());
        }

        if (companies != null) {
          System.out.println("  companies: #" + companies.size());
        }
      }
    }
    else
      System.err.println(ds.consumeLastException());
  }

}
