/*
  Copyright (C) 2008-2024 Helge Hess

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
package org.opengroupware.logic.ops;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.getobjects.eoaccess.EOEnterpriseObject;
import org.getobjects.eoaccess.EOEntity;
import org.getobjects.foundation.NSJavaRuntime;
import org.getobjects.foundation.UObject;
import org.opengroupware.logic.core.OGoObjectContext;
import org.opengroupware.logic.db.IOGoContactChildObject;

/**
 * OGoContactInsert
 * <p>
 * This OGoOperation performs creates OGoContact objects, that is,
 * persons, companies and teams.
 * 
 * <p>
 * Example (add an new contact):<pre>
 *   OGoContactInsert op = new OGoContactInsert(oc, "Persons");
 *   op.add("firstname", "Donald", "lastname", "Duck");
 *   Exception error = oc.performOperations(operation);</pre>
 */
public class OGoContactInsert extends OGoEOInsertOperation {
  // TBD: ensure that a comment is created
  // TBD: ensure that the configured set of addresses,phones,mails is created

  public OGoContactInsert(final OGoObjectContext _oc, final String _ename) {
    super(_oc, _ename);
  }
  
  
  /**
   * Create a fresh instance of to be inserted.
   * 
   * @return a fresh OGoContactChildObject object
   */
  @Override
  @SuppressWarnings("rawtypes")
  public EOEnterpriseObject createObject() {
    final Class clazz = this.oc.database().classForEntity(this.baseEntity);
    if (clazz == null) {
      log.error("did not find class for entity!");
      return null;
    }
    
    final EOEnterpriseObject eo = (EOEnterpriseObject)
      NSJavaRuntime.NSAllocateObject(clazz, EOEntity.class, this.baseEntity);
    
    return eo;
  }
  
  /**
   * Helper method to process child objects (addresses, phones etc) for the
   * convienience methods. It allows arrays or collections of Maps or
   * {@link IOGoContactChildObject}'s.
   * 
   * @param list_     - Collection to be filled
   * @param _children - children to process
   * @param _entity   - entity of children (eg "Addresses")
   */
  @SuppressWarnings({ "rawtypes", "unchecked" })
  public void addNewChildObjectsToList
    (List<IOGoContactChildObject> list_, Object _children, EOEntity _entity)
  {
    if (_children == null)
      return;
    
    if (_children instanceof Collection) {
      final Collection childCol = (Collection)_children;

      Class clazz = null;
      
      for (final Object child: childCol) {
        if (child instanceof Map) {
          if (clazz == null)
            clazz = this.oc.database().classForEntity(_entity);
          
          final IOGoContactChildObject eo = (IOGoContactChildObject)
            NSJavaRuntime.NSAllocateObject(clazz, EOEntity.class, _entity);
          eo.takeValuesFromDictionary((Map)child);
          list_.add(eo);
        }
        else if (child instanceof IOGoContactChildObject)
          list_.add((IOGoContactChildObject)child);
        else {
          log.error("unexpected child object (" + _entity + "): " +
              child);
        }
      }
    }
    else if (_children instanceof Map[]) {
      final Class clazz = this.oc.database().classForEntity(_entity);
      final Map[] childMaps = (Map[])_children;
      
      for (final Map record: childMaps) {
        final IOGoContactChildObject eo = (IOGoContactChildObject)
          NSJavaRuntime.NSAllocateObject(clazz, EOEntity.class, _entity);
        eo.takeValuesFromDictionary(record);
        list_.add(eo);
      }
    }
    else if (_children instanceof IOGoContactChildObject[]) {
      final IOGoContactChildObject[] childEOs = 
        (IOGoContactChildObject[])_children;
      for (IOGoContactChildObject child: childEOs)
        list_.add(child);
    }
    else {
      log.error("unexpected child collection object (" + _entity + "): " +
          _children);
    }
  }

  @Override
  public Exception prepareObjectForInsert
    (final EOEnterpriseObject _eo,
     final OGoOperationTransaction _tx, final OGoObjectContext _ctx)
  {
    Exception error = super.prepareObjectForInsert(_eo, _tx, _ctx);
    if (error != null) return error;
    
    if (this.baseEntity.attributeNamed("contactId") != null) {
      if (this.actorId.intValue() != 10000) { /* not for root */
        if (UObject.isEmpty(_eo.valueForKey("contactId")))
          _eo.takeValueForKey(this.actorId, "contactId");
      }
    }
    
    /* per default, create private contacts */
    if (_eo.valueForKey("isPrivate") == null)
      _eo.takeValueForKey(one, "contactId");
    /* per default, create readonly contacts */
    if (_eo.valueForKey("isReadOnlyFlag") == null)
      _eo.takeValueForKey(one, "isReadOnlyFlag");
    
    if (this.baseEntity.attributeNamed("isAccount") != null) {
      /* per default, create readonly contacts */
      Object v = _eo.valueForKey("isAccount");
      if (v == null)
        _eo.takeValueForKey(zero, "isAccount");
      else if (UObject.boolValue(v)) {
        /* ensure that isLocked is set to some non-NULL value */
        if ((v = _eo.valueForKey("isLocked")) == null)
          _eo.takeValueForKey(zero, "isLocked");
      }
    }
    
    String en = this.baseEntity.name();
    if (en.equals("Persons"))   _eo.takeValueForKey(one, "isPerson");
    if (en.equals("Teams"))     _eo.takeValueForKey(one, "isTeam");
    if (en.equals("Companies")) _eo.takeValueForKey(one, "isCompany");

    return null;
  }
  private final static Number zero = Integer.valueOf(0);
  private final static Number one = Integer.valueOf(1);

  @Override
  public Exception fixupObjectWithPrimarKey
    (final EOEnterpriseObject _eo,
     final OGoOperationTransaction _tx, final OGoObjectContext _ctx)
  {
    final Object n = _eo.valueForKey("number");
    if (n == null)
      _eo.takeValueForKey("OGo" + _eo.valueForKey("id"), "number");
    return null;
  }
}
