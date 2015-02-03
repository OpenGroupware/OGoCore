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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.getobjects.eoaccess.EOAccessDataSource;
import org.getobjects.eoaccess.EOActiveRecord;
import org.getobjects.eoaccess.EODatabase;
import org.getobjects.eoaccess.EOEnterpriseObject;
import org.getobjects.eoaccess.EOEntity;
import org.getobjects.eoaccess.EOJoin;
import org.getobjects.eoaccess.EORelationship;
import org.getobjects.eocontrol.EODataSource;
import org.getobjects.eocontrol.EOFetchSpecification;
import org.getobjects.eocontrol.EOKeyValueQualifier;
import org.getobjects.eocontrol.EOQualifier;
import org.getobjects.foundation.NSJavaRuntime;
import org.getobjects.foundation.NSObject;
import org.opengroupware.logic.db.OGoDatabase;
import org.opengroupware.logic.db.OGoPerson;

public class testlistpersonjoin {

  static String dburl = "jdbc:postgresql://127.0.0.1/OGo?user=OGo&password=OGo";

  @SuppressWarnings("unchecked")
  public static void main(String[] args) {
    OGoDatabase db = OGoDatabase.databaseForURL(dburl, null);

    EOAccessDataSource ds = db.persons();
    ds.setFetchSpecificationByName("default");
    
    List<OGoPerson> persons = ds.fetchObjects();
    System.out.println("fetched persons: " + persons.size());
    
    if (false) {
    for (OGoPerson person: persons) {
      System.out.println("" +
          NSJavaRuntime.stringValueForKey(person, "firstname") + " " +
          NSJavaRuntime.stringValueForKey(person, "lastname"));
    }
    }
    
   
    ManyToManyRelationshipHelper h = new ManyToManyRelationshipHelper
      (db, "Employments", "Persons", "Companies");
    
    h.doWork(persons);
    
    for (OGoPerson person: persons) {
      System.out.println("" +
          NSJavaRuntime.stringValueForKey(person, "firstname") + " " +
          NSJavaRuntime.stringValueForKey(person, "lastname") + ": " +
          person.valueForKey("employments"));
    }
  }
  
  public static class ManyToManyRelationshipHelper extends NSObject {
    
    EODatabase db;
    EOEntity joinEntity;
    EOEntity sourceEntity;
    EOEntity targetEntity;
    
    public ManyToManyRelationshipHelper
      (EODatabase _db, String _join, String _src, String _dest)
    {
      this.db = _db;
      this.joinEntity   = this.db.entityNamed(_join);
      this.sourceEntity = this.db.entityNamed(_src);
      this.targetEntity = this.db.entityNamed(_dest);
    }
    
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void doWork(List _sourceObjects) {
      // TBD: should we do fetches in batches?
      if (_sourceObjects == null || _sourceObjects.size() == 0)
        return;
      
      EORelationship sourceRel = null, targetRel = null;
      for (EORelationship rel: this.joinEntity.relationships()) {
        EOEntity dest = rel.destinationEntity();

        if (sourceRel == null && dest == this.sourceEntity)
          sourceRel = rel;
        if (targetRel == null && dest == this.targetEntity)
          targetRel = rel;
        if (sourceRel != null && targetRel != null)
          break;
      }
      EOJoin sourceJoin = sourceRel.joins()[0];
      EOJoin targetJoin = targetRel.joins()[0];
      
      System.out.println("FROM: " + sourceRel);
      System.out.println("TO:   " + targetRel);
      
      /*
       * Steps:
       * 1. extract primary keys from source list
       * 2. fetch join-table records for those keys
       * 3. fetch targets
       * 4. reconnect targets to source using join records
       */
      
      String sourceAttrName  = sourceJoin.destinationAttribute().name();
      String targetAttrName  = targetJoin.destinationAttribute().name();
      
      List<Object> sourceKeys = new ArrayList<Object>(_sourceObjects.size());
      Map<Object, EOEnterpriseObject> sourceMap =
        new HashMap<Object, EOEnterpriseObject>(_sourceObjects.size());
      
      for (int i = _sourceObjects.size() - 1; i >= 0; i--) {
        EOEnterpriseObject o = (EOEnterpriseObject)_sourceObjects.get(i);
        Object key = o.valueForKey(sourceAttrName);
        sourceKeys.add(key);
        sourceMap.put(key, o);
      }
      
      System.out.println("source " + sourceAttrName + ": " + sourceKeys);
      
      
      /* fetch mappings from inner table */
      /*
       * We are now fetching objects because they contain useful information
       * anyways. Would be more efficient to do raw SQL if there are raw
       * n:m tables. But I think we have none in OGo.
       */
      
      String sourceAttrInJoin = sourceJoin.sourceAttribute().name();
      String targetAttrInJoin = targetJoin.sourceAttribute().name();
      
      EOQualifier q = new EOKeyValueQualifier
        (sourceAttrInJoin,EOQualifier.ComparisonOperation.CONTAINS, sourceKeys);
      EOFetchSpecification fs = new EOFetchSpecification
        (this.joinEntity.name(), q, null);
      
      EODataSource joinDS = db.dataSourceForEntity(this.joinEntity);
      joinDS.setFetchSpecification(fs);
      
      List<EOActiveRecord> mappings = joinDS.fetchObjects();
      
      /* process mappings of inner table */
      
      Map<Object, List<EOEnterpriseObject>> targetKeyToSourceObjects =
        new HashMap<Object, List<EOEnterpriseObject>>(16);
      
      for (EOActiveRecord record: mappings) {
        EOEnterpriseObject source =
          sourceMap.get(record.valueForKey(sourceAttrInJoin));
        if (source == null) {
          System.err.println("ERROR: did not find source for key: " + record);
          continue;
        }
        
        source.addObjectToBothSidesOfRelationshipWithKey(record, "employments");
        
        // until here its a regular relationship prefill!
        // List prefetchingRelationshipKeyPaths
        // setPrefetchingRelationshipKeyPaths(NSArray
        
        
        Object targetKey = record.valueForKey(targetAttrInJoin);
        if (targetKey == null) {
          // TBD: could be valid in some situations?
          continue;
        }
        
        List<EOEnterpriseObject> sourceObjects =
          targetKeyToSourceObjects.get(targetKey);
        if (sourceObjects == null) {
          // TBD: make this configurable/tunable (eg in person<->company we
          //      usually have just one, while person<->tag might be 10)
          sourceObjects = new ArrayList<EOEnterpriseObject>(4);
          targetKeyToSourceObjects.put(targetKey, sourceObjects);
        }
        
        sourceObjects.add(source);
      }
      System.out.println("MAP: " + targetKeyToSourceObjects);
      
      
      /* fetch the target objects */
      
      q = new EOKeyValueQualifier
        (targetAttrName,
         EOQualifier.ComparisonOperation.CONTAINS,
         targetKeyToSourceObjects.keySet());
    
      fs = new EOFetchSpecification(this.targetEntity.name(), q, null);
    
      EODataSource ds = db.dataSourceForEntity(this.targetEntity);
      ds.setFetchSpecification(fs);
      
      System.out.println("FETCH: " + ds.fetchObjects());
    }
  }
  
}
