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
import org.getobjects.eocontrol.EOFetchSpecification;
import org.getobjects.eocontrol.EOKeyValueQualifier;
import org.getobjects.eocontrol.EOQualifier;
import org.getobjects.foundation.NSJavaRuntime;
import org.getobjects.foundation.NSKeyValueStringFormatter;
import org.getobjects.foundation.NSObject;
import org.opengroupware.logic.core.OGoObjectContext;
import org.opengroupware.logic.db.OGoACLEntry;
import org.opengroupware.logic.db.OGoDocument;
import org.opengroupware.logic.db.OGoPerson;
import org.opengroupware.logic.db.OGoProject;
import org.opengroupware.logic.db.OGoTask;

@SuppressWarnings("unused")
public class testctxauthzfetch {

  static String dburl = "jdbc:postgresql://127.0.0.1/OGo?user=OGo&password=OGo";

  public static void main(String[] args) {
    OGoObjectContext oc =
      OGoObjectContext.objectContextForURL(dburl, "test", "abc123", null);
    System.out.println("logged in as: " + oc.loginContext());
    
    //testContacts(oc);
    //testTasks(oc);
    //testProjects(oc);
    testDocs(oc);
  }


  @SuppressWarnings("unchecked")
  private static void testContacts(OGoObjectContext oc) {
    EOAccessDataSource   ds = oc.persons();
    EOFetchSpecification fs = (EOFetchSpecification)
      ds.entity().fetchSpecificationNamed("default").copy();

    //fs.setQualifier(new EOKeyValueQualifier("firstname", "Tina"));
    fs.setQualifier(new EOKeyValueQualifier("firstname", "Helge"));

    fs.setPrefetchingRelationshipKeyPaths
      (new String[] { "employments.company", "emails", "phones" });
      //(new String[] { "employments.company", "emails", "phones", "acl" });

    ds.setFetchSpecification(fs);

    List<OGoPerson> persons = ds.fetchObjects();

    System.out.println("fetched persons: #" + persons.size());
    if (persons != null) {

      /* show persons */
      for (OGoPerson person: persons)
        printPerson(oc, person);
    }
  }


  @SuppressWarnings("unchecked")
  private static void testTasks(OGoObjectContext oc) {
    EOAccessDataSource   ds = oc.tasks();
    EOFetchSpecification fs =
      ds.entity().fetchSpecificationNamed("default").copy();
    
    EOQualifier q;
    if (false) {
      /* fetch tasks created by me */
      q = new EOKeyValueQualifier("creatorId", oc.actorID());
    }
    else if (false) {
      /* fetch tasks created *for* me */
      q = new EOKeyValueQualifier("ownerId",
          EOQualifier.ComparisonOperation.CONTAINS,
          oc.authenticatedIDs());
    }
    else {
      /* fetch all tasks */
      q = null;
    }
    
    fs.setQualifier(q);

    if (false) {
      fs.setPrefetchingRelationshipKeyPaths
        (new String[] { "project" } );
    }

    ds.setFetchSpecification(fs);

    List<OGoTask> tasks = ds.fetchObjects();
    if (tasks != null) {
      System.out.println("fetched tasks: #" + tasks.size());
      for (OGoTask task: tasks) {
        String tfmt = "  %(title)s [%(id)s]: ";
        System.out.print(NSKeyValueStringFormatter.format(tfmt, task));
        System.out.println("'" + oc.permissionsForObject(task) + "'");
      }
    }
    else {
      Exception e = ds.consumeLastException();
      System.err.println("error fetching tasks: " + e);
      e.printStackTrace();
    }
  }


  @SuppressWarnings("unchecked")
  private static void testProjects(OGoObjectContext oc) {
    EOAccessDataSource   ds = oc.projects();
    EOFetchSpecification fs =
      ds.entity().fetchSpecificationNamed("default").copy();
    
    EOQualifier q;
    if (false) {
      /* fetch tasks created by me */
      q = new EOKeyValueQualifier("code", "P10001");
    }
    else
      q = null; /* fetch all */
    
    fs.setQualifier(q);
    ds.setFetchSpecification(fs);

    List<OGoProject> docs = ds.fetchObjects();
    if (docs != null) {
      System.out.println("fetched projects: #" + docs.size());
      for (OGoProject project: docs) {
        String tfmt = "  %(name)s '%(code)s' [%(id)s]: ";
        System.out.print(NSKeyValueStringFormatter.format(tfmt, project));
        System.out.println("'" + oc.permissionsForObject(project) + "'");
      }
    }
    else {
      Exception e = ds.consumeLastException();
      System.err.println("error fetching projects: " + e);
      e.printStackTrace();
    }
  }


  @SuppressWarnings("unchecked")
  private static void testDocs(OGoObjectContext oc) {
    EOAccessDataSource   ds = oc.documents();
    EOFetchSpecification fs =
      ds.entity().fetchSpecificationNamed("default").copy();
    
    EOQualifier q;
    if (true) {
      /* fetch tasks created by me */
      q = new EOKeyValueQualifier("isFolder", Integer.valueOf(0));
    }
    else
      q = null; /* fetch all */
    
    fs.setQualifier(q);
    ds.setFetchSpecification(fs);

    List<OGoDocument> docs = ds.fetchObjects();
    if (docs != null) {
      System.out.println("fetched documents: #" + docs.size());
      for (OGoDocument document: docs) {
        String tfmt =
          "  %(filename)s.%(fileext)s " +
          "'%(subject)s' [%(id)s in %(projectId)s]: ";
        System.out.print(NSKeyValueStringFormatter.format(tfmt, document));
        System.out.println("'" + oc.permissionsForObject(document) + "'");
      }
    }
    else {
      Exception e = ds.consumeLastException();
      System.err.println("error fetching documents: " + e);
      e.printStackTrace();
    }
  }
  
  
  /* printing */
  
  @SuppressWarnings({ "unchecked", "rawtypes" })
  public static void printPerson(OGoObjectContext oc, OGoPerson person) {
    Collection<NSObject> employments = (Collection)person.valueForKey
      ("employments");
    Collection<OGoACLEntry> acl = (Collection)person.valueForKey("acl");
    
    String tfmt = "\n---\n%(firstname)s %(lastname)s [%(id)s]:";
    System.out.println(NSKeyValueStringFormatter.format(tfmt, person));
    
    String perm = oc.permissionsForObject(person);
    if (perm != null)
      System.out.println("  PERMISSION: '" + perm + "'");
    else
      System.out.println("  NO-PERM");
    
    System.out.print  ("  setup:     ");
    
    boolean isPublic = !NSJavaRuntime.boolValueForKey(person, "isPrivate");
    if (!isPublic) {
      System.out.print(" private[");
      System.out.print(person.valueForKey("ownerId"));
      System.out.print("]");
    }
    else
      System.out.print(" public");
    
    if (NSJavaRuntime.boolValueForKey(person, "isReadOnly"))
      System.out.print(" readonly");

    if (acl == null)
      System.out.print(" no-acl");
    else
      System.out.print(" acl=#" + acl.size());
    
    System.out.println("");
    
    
    /* print ACL */
    
    if (acl != null) {
      String fmt =
        "    [%(id)s] '%(permissions)s' for=%(principalId)s on=%(objectId)s";
      if (isPublic)
        System.out.println("  UNUSED ACL:");
      else
        System.out.println("  ACL:");
      
      for (OGoACLEntry ace: acl) {
        System.out.println(NSKeyValueStringFormatter.format(fmt, ace));
      }
    }
    

    /* emails */

    Collection<NSObject> emails = (Collection)person.valueForKey("emails");
    if (emails == null)
      System.out.println("  @ no emails");
    else {
      for (NSObject value: emails) {
        if (value.isEmpty())
          continue;

        String fmt = "  @%(key)s: %(value)s [%(label)s]";
        System.out.print(NSKeyValueStringFormatter.format(fmt, value));
        System.out.println(" P:'" + oc.permissionsForObject(value) + "'");
      }
    }


    /* phones */

    Collection<NSObject> phones = (Collection)person.valueForKey("phones");
    if (phones == null)
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

    if (employments != null) {
      for (NSObject employment: employments) {
        //System.out.println("  " + e);
        String efmt = "    %(company.name)s (%(function)s)";
        System.out.print(NSKeyValueStringFormatter.format(efmt, employment));
        
        String companyPermission =
          oc.permissionsForObject(employment.valueForKey("company"));
        if (companyPermission == null)
          System.out.print(" NO PERM");
        else
          System.out.print(" PERM[" + companyPermission + "]");
        
        System.out.println();
      }
    }
  }

}
