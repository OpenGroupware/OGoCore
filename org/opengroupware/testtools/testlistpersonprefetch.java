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

import java.util.Collection;
import java.util.List;

import org.getobjects.eoaccess.EOAccessDataSource;
import org.getobjects.eoaccess.EODatabaseDataSource;
import org.getobjects.eocontrol.EOFetchSpecification;
import org.getobjects.eocontrol.EOKeyValueQualifier;
import org.getobjects.foundation.NSJavaRuntime;
import org.getobjects.foundation.NSObject;
import org.opengroupware.logic.core.OGoObjectContext;
import org.opengroupware.logic.db.OGoPerson;

public class testlistpersonprefetch {

  static String dburl = "jdbc:postgresql://127.0.0.1/OGo?user=OGo&password=OGo";

  @SuppressWarnings({ "unchecked", "rawtypes" })
  public static void main(String[] args) {
    OGoObjectContext oc =
      OGoObjectContext.objectContextForURL(dburl, "test", "abc123", null);
    System.out.println("logged in as: " + oc.loginContext());
    
    
    EOAccessDataSource ds = new EODatabaseDataSource(oc, "Persons");
    
    EOFetchSpecification fs = ds.entity().fetchSpecificationNamed("default");
    
    //fs.setQualifier(new EOKeyValueQualifier("firstname", "Tina"));
    fs.setQualifier(new EOKeyValueQualifier("firstname", "Helge"));
    
    fs.setPrefetchingRelationshipKeyPaths
      (new String[] { "employments.company", "emails", "phones" });
    
    ds.setFetchSpecification(fs);
    
    List<OGoPerson> persons = ds.fetchObjects();
    System.out.println("fetched persons: " + persons.size());
        
    for (OGoPerson person: persons) {
      Collection<NSObject> employments = (Collection)person.valueForKey
        ("employments");
      if (employments == null)
        continue;
      
      System.out.println("" +
          NSJavaRuntime.stringValueForKey(person, "firstname") + " " +
          NSJavaRuntime.stringValueForKey(person, "lastname") + ": ");
      
      /* emails */

      Collection<NSObject> emails = (Collection)person.valueForKey("emails");
      if (emails == null)
        System.out.println("  @ no emails");
      else {
        for (NSObject value: emails) {
          if (value.isEmpty())
            continue;
          
          System.out.println("  @" + value.valueForKey("key") +
              ": " + value.valueForKey("value") + " [" +
              value.valueForKey("label") + "]");
        }
      }
      
      
      /* phones */
      
      Collection<NSObject> phones = (Collection)person.valueForKey("phones");
      if (emails == null)
        System.out.println("  no phones");
      else {
        for (NSObject value: phones) {
          if (value.isEmpty())
            continue;
          
          System.out.println("  Tel: " + value.valueForKey("key") +
              ": " + value.valueForKey("value") + " [" +
              value.valueForKey("info") + "]");
        }
      }

      
      /* employments */

      //System.out.println("L: " + employments);
      //System.out.println("L: " + employments.getClass());
      
      for (NSObject employment: employments) {
        //System.out.println("  " + e);
        System.out.println("    " + employment.valueForKeyPath("company.name")+
            " (" + employment.valueForKey("function") + ")");
      }
    }
  }

}
