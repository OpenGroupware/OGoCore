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

import java.io.File;
import java.util.List;

import org.getobjects.eoaccess.EOAccessDataSource;
import org.getobjects.eoaccess.EODatabaseDataSource;
import org.getobjects.eocontrol.EOFetchSpecification;
import org.getobjects.foundation.UList;
import org.getobjects.foundation.UString;
import org.opengroupware.logic.core.OGoObjectContext;
import org.opengroupware.logic.db.OGoNote;

public class testlistpersondocs {

  static String dburl = "jdbc:postgresql://127.0.0.1/OGo?user=OGo&password=OGo";

  @SuppressWarnings({"unchecked", "rawtypes"})
  public static void main(String[] args) {
    /* list */
    
    OGoObjectContext oc =
      OGoObjectContext.objectContextForURL(dburl, "donald", "abc123",
          new File("/var/lib/opengroupware.org/documents"));

    System.out.println("logged in as: " + oc.loginContext());
    Number[] authIds = oc.authenticatedIDs();
    System.out.println("  auth: " +
        UString.componentsJoinedByString(authIds, ","));
    
    /* fetch all my project-ids */
    
    EOAccessDataSource ds = new EODatabaseDataSource(oc, "Projects");
    
    List projects = ds.fetchObjects("myProjectIds", "authIds", authIds);
    if (projects == null || projects.size() == 0) {
      System.out.println("got no projects");
      System.exit(0);
    }
    
    List projectIds = UList.valuesForKey(projects, "project_id");
    System.out.println("project-ids: " +
        UString.componentsJoinedByString(projectIds, ", "));
    
    
    /* fetch documents for a person */
    
    int personId = 10160;
    
    EOAccessDataSource notesDS;
    notesDS = new EODatabaseDataSource(oc, "Notes");
    
    notesDS.setFetchSpecificationByName("default");
    
    // TBD: should the fetch ensure that we have read access on the given
    //      contact?
    EOFetchSpecification fs =
      notesDS.entity().fetchSpecificationNamed("notesForContact");
    fs = fs.fetchSpecificationWithQualifierBindings
      ("id", personId, "authenticatedProjects", projectIds);

    fs.setPrefetchingRelationshipKeyPaths(new String[] { "project" });

    System.out.println("fetch spec: " + fs);
    
    
    notesDS.setFetchSpecification(fs);
    List<OGoNote> notes = notesDS.fetchObjects();
    if (notes != null) {
      for (OGoNote note: notes) {
        System.out.println("note: " + note);
      }
    }
    else {
      System.err.println("FETCH ERROR: " + notesDS.consumeLastException());
    }
  
    oc.printRegisteredObjects(System.out);
  }

}
