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
package org.opengroupware.logic.ops;

import org.getobjects.eoaccess.EOEnterpriseObject;
import org.getobjects.eoaccess.EORelationship;
import org.getobjects.foundation.NSObject;
import org.getobjects.foundation.UObject;
import org.opengroupware.logic.core.OGoObjectContext;
import org.opengroupware.logic.db.OGoObject;

/**
 * OGoContactChange
 * <p>
 * This OGoOperation performs changes on OGoContact objects, that is,
 * persons, companies and teams.
 * 
 * <p>
 * As a specialty this object maintains a list of 'childChanges' which contains
 * changes to objects owned by a contact, eg addresses, phone numbers, comments
 * or email addresses.
 * This is necessary because the contact object_version must be bumped when the
 * child object is changed.
 * Note: a single 'child change object' can hold hold changes for multiple
 * parent objects! 
 * 
 * <p>
 * Example (add an email address to contact with id 10000):<pre>
 *   OGoContactChange operation = new OGoContactChange(oc, "Persons");
 *   operation.addChanges(10000, null, null); // no direct changes
 *   
 *   operation.addChildChange(OGoEMailAddressChange.changePerson(oc, 10000,
 *     null, null, UMap.create("value", "dd@dd.org"));
 *   
 *   Exception error = oc.performOperations(operation);</pre>
 * 
 * It looks a bit difficult, but the setup is required to efficiently perform
 * bulk updates.
 * 
 * <p>
 * @author helge
 */
public class OGoContactChange extends OGoObjectUpdateOperation {

  public OGoContactChange(final OGoObjectContext _oc, final String _ename) {
    super(_oc, _ename);
  }
  
  
  /* enqueue update request */

  @Override
  public boolean addChangesInRelationshipsOfEO(final OGoObject _eo) {
    if (_eo == null)
      return false;
    
    if (_eo.isReadOnly()) {
      log.error("attempt to update readonly object: " + _eo);
      return false;
    }

    // what a hack, fix this static setup
    // TBD: move name of change operation class to model and instantiate it
    //      dynamically
    
    if (!this.addRelshipChanges(_eo, "emails"))      return false;
    if (!this.addRelshipChanges(_eo, "phones"))      return false;
    if (!this.addRelshipChanges(_eo, "addresses"))   return false;
    if (!this.addRelshipChanges(_eo, "comment"))     return false;
    if (!this.addRelshipChanges(_eo, "extraValues")) return false;
    
    return true;
  }
  
  @Override
  public boolean addRelshipChanges(final OGoObject _eo, String _relkey) {
    // Note: we do not catch deletes!
    if (_eo == null)
      return false;
    
    /* check whether there is anything to update/insert */
    
    final Object r = _eo.valueForKey(_relkey);
    if (r == null || UObject.isEmpty(r)) return true; /* nothing to do */
    
    /* lookup change operation for given relationship */
    
    final EORelationship relship = this.baseEntity.relationshipNamed(_relkey);
    if (relship == null) {
      log.error("did not find relationship '" + _relkey + "' in: " +
          this.baseEntity);
      return false;
    }
    
    IOGoObjectChildChange change = (OGoContactChildChange)
      this.relshipChanges.get(_relkey);
    if (change == null) {
      change = this.newChangeForRelationship(_eo, relship);
      this.relshipChanges.put(_relkey, change);
      this.addChildChange(change);
    }

    if (change instanceof OGoContactChildChange)
      return ((OGoContactChildChange)change).addChangeSet(_eo, _relkey);
    
    /* collect updates/inserts */
    
    if (change instanceof OGoContactCommentChange) {
      final OGoContactCommentChange ccc = (OGoContactCommentChange)change;
        
      if (r instanceof String)
        ccc.addChangeSet(_eo.id(), (String)r);
      else
        ccc.addChangeSet(_eo.id(),(String)((NSObject)r).valueForKey("value"));
      return true;
    }
    
    log.error("could not process relationship '" + _relkey + "': " + _eo);
    return false;
  }
  
  @Override
  public IOGoObjectChildChange newChangeForRelationship
    (final EOEnterpriseObject _eo, final EORelationship _relship)
  {
    final String src = this.baseEntity.name();
    
    if (_relship.name().equals("emails"))
      return new OGoEMailAddressChange(this.oc, src);
    
    if (_relship.name().equals("extraValues"))
      return new OGoContactExtValueChange(this.oc, src);
    
    if (_relship.name().equals("phones"))
      return new OGoPhoneNumberChange(this.oc, src);
    if (_relship.name().equals("addresses"))
      return new OGoAddressChange(this.oc, src);

    if (_relship.name().equals("comment"))
      return new OGoContactCommentChange(this.oc, src);

    return null;
  }
  
}
