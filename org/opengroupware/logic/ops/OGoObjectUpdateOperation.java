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

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.getobjects.eoaccess.EOAdaptorChannel;
import org.getobjects.eoaccess.EOAdaptorOperation;
import org.getobjects.eoaccess.EOEnterpriseObject;
import org.getobjects.eoaccess.EOEntity;
import org.getobjects.eoaccess.EORelationship;
import org.getobjects.eocontrol.EOFetchSpecification;
import org.getobjects.eocontrol.EOQualifier;
import org.getobjects.foundation.NSCompoundException;
import org.getobjects.foundation.NSException;
import org.getobjects.foundation.UObject;
import org.opengroupware.logic.core.OGoObjectContext;
import org.opengroupware.logic.db.OGoObject;

/**
 * OGoEOUpdateOperation
 * <p>
 * This OGoOperation performs changes on OGoObject objects.
 * 
 * <p>
 * As a specialty this object maintains a list of 'childChanges' which contains
 * changes to objects owned by a contact, eg addresses, phone numbers, comments
 * or email addresses.
 * This is necessary because the contact object_version must be bumped when the
 * child object is changed.<br>
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
public class OGoObjectUpdateOperation extends OGoOperation {

  protected EOEntity baseEntity; /* eg Persons or Companies */
  
  protected Date   now;
  protected Number actorId;
  
  protected Map<Number, Number>              idToBaseVersion;
  protected Map<Number, Map<String, Object>> idToChangeSet;
  protected Map<Number, EOEnterpriseObject>  idToContact;
  protected List<IOGoObjectChildChange>      childChanges;
  protected List<OGoObject>                  changedObjects;
  
  protected Map<String, Object> relshipChanges;

  public OGoObjectUpdateOperation(OGoObjectContext _oc, String _ename) {
    super(_oc);
    this.baseEntity = _oc.oDatabase().entityNamed(_ename);
    
    this.idToBaseVersion = new HashMap<Number, Number>(4);
    this.idToChangeSet   = new HashMap<Number, Map<String,Object>>(4);
    this.idToContact     = new HashMap<Number, EOEnterpriseObject>(4);
    this.childChanges    = new ArrayList<IOGoObjectChildChange>(4);
    
    this.relshipChanges = new HashMap<String, Object>(4);
  }
  
  
  /* enqueue update request */

  /**
   * Enqueues the change record _c for the contact primary key _id. If a
   * _version is given, it will be validated prior attempting to change the
   * record.
   * 
   * @param _id      - the primary key of the contact
   * @param _version - the object_version the change relies on, or null
   * @param _c       - the actual changes
   */
  public void addChanges(Number _id, Number _version, Map<String, Object> _c) {
    if (_id == null) {
      log.warn("got no contact-id for changes?!");
      return;
    }
    if (_c == null) // we MUST enqueue the object for relship changes
      _c = new HashMap<String, Object>(1);
    
    if (_version != null)
      this.idToBaseVersion.put(_id, _version);
    
    this.idToChangeSet.put(_id, _c);
  }
  
  /**
   * This adds an address/telephone/... change operation to the contact change.
   * Those are relationships which are directly attached to the contact record,
   * ie modify its object_version.
   * <p>
   * The OGoContactChange object will trigger the
   *   <code>prepareForTransactionInContext()</code>
   * and
   *   <code>runInContext()</code>
   * on the children, do not add them directly to the transaction.
   * 
   * @param _change - the change operation, eg OGoAddressChange
   */
  public void addChildChange(final IOGoObjectChildChange _change) {
    if (_change != null)
      this.childChanges.add(_change);
  }
  
  
  /* EO objects */

  public boolean add(final OGoObject _eo, final boolean _force) {
    if (_eo == null)
      return false;
    
    if (_eo.isReadOnly()) {
      log.error("attempt to update readonly object: " + _eo);
      return false;
    }
    
    Number pkey = _eo.id();
    if (pkey == null) {
      log.error("contact has no primary key, cannot add: " + _eo);
      return false;
    }
    
    /* first add EO changes */
    
    // TBD: use attributesUsedForLocking instead?
    this.addChanges(pkey, _force ? null : _eo.baseVersion(),
        _eo.changesFromSnapshot(_eo.snapshot()));
    
    /* then scan relationships */
    
    return this.addChangesInRelationshipsOfEO(_eo);
  }
  
  public boolean addChangesInRelationshipsOfEO(final OGoObject _eo) {
    if (_eo == null)
      return false;
    
    if (_eo.isReadOnly()) {
      log.error("attempt to update readonly object: " + _eo);
      return false;
    }
    return true;
  }
  
  public boolean addRelshipChanges(final OGoObject _eo, String _relkey) {
    // Note: we do not catch deletes!
    if (_eo == null)
      return false;
    
    /* check whether there is anything to update/insert */
    
    Object r = _eo.valueForKey(_relkey);
    if (r == null || UObject.isEmpty(r)) return true; /* nothing to do */
    
    /* lookup change operation for given relationship */
    
    EORelationship relship = this.baseEntity.relationshipNamed(_relkey);
    if (relship == null) {
      log.error("did not find relationship '" + _relkey + "' in: " +
          this.baseEntity);
      return false;
    }
    
    IOGoObjectChildChange change = (IOGoObjectChildChange)
      this.relshipChanges.get(_relkey);
    if (change == null) {
      change = this.newChangeForRelationship(_eo, relship);
      this.relshipChanges.put(_relkey, change);
      this.addChildChange(change);
    }

    log.error("could not process relationship '" + _relkey + "': " + _eo);
    return false;
  }
  
  public IOGoObjectChildChange newChangeForRelationship
    (final EOEnterpriseObject _eo, final EORelationship _relship)
  {
    return null;
  }
  
  
  /* prepare */

  @SuppressWarnings("unchecked")
  @Override
  public Exception prepareForTransactionInContext
    (OGoOperationTransaction _tx, OGoObjectContext _ctx)
  {
    final boolean childDebugOn = childLog.isDebugEnabled();
    
    if (this.idToChangeSet == null || this.idToChangeSet.size() == 0)
      return null; /* nothing to do */
    
    this.now     = _tx.startDate();
    this.actorId = _ctx.actorID();
    
    
    /* fetch EO objects (eg OGoPerson's) we have changes for */
    
    EOFetchSpecification fs = new EOFetchSpecification(this.baseEntity.name(),
        EOQualifier.parse("id IN %@", this.idToChangeSet.keySet()),
        null);
    if (childDebugOn)
      childLog.debug("base id query: " + fs);
    
    // Note: locking makes no sense, we are not inside a transaction at this
    //       stage. prepare is just for preliminary setup.
    // fs.setLocksObjects(true); /* sure, why not? */
    
    List<OGoObject> objects =
      _tx.objectContext().objectsWithFetchSpecification(fs);
    if (objects == null) {
      Exception error = _tx.objectContext().consumeLastException();
      return error != null ? error : new NSException("could not fetch objs");
    }
    
    
    /* check whether all objects were found */
    
    if (objects.size() < this.idToChangeSet.size())
      return new NSException("some objects could not be found"); // TBD
    
    
    /* compare revisions, apply our changes and request permission */
    
    List<Exception> revErrors = new ArrayList<Exception>(4);
    for (OGoObject eo: objects) {
      Number pkey = eo.id();
      Number base = this.idToBaseVersion.get(pkey);
      
      if (base != null) { 
        Number current = (Number)eo.valueForKey("objectVersion");
        if (current == null) current = 0;

        if (!base.equals(current)) {
          log.warn("object was modified: " + eo);
          revErrors.add(new OGoVersionMismatchException(eo, current, base));
        }
      }
      
      /* request write permission */
      
      _tx.requestPermissionOnId("w", this.baseEntity.name(), pkey);
      
      /* apply core changes */
      
      eo.takeValuesFromDictionary(this.idToChangeSet.get(pkey));
    }
    if (revErrors.size() > 0)
      return NSCompoundException.exceptionForList("version mismatch",revErrors);

    
    /* prepare child changes */
    
    if (this.childChanges != null) {
      for (IOGoObjectChildChange change: this.childChanges) {
        Exception error = change.prepareForTransactionInContext(_tx, _ctx);
        if (error != null)
          return error;
      }
    }
    
    
    /* check for changes */
    
    if (childDebugOn)
      childLog.debug("detect changes in objects: " + objects.size());
    
    this.changedObjects = new ArrayList<OGoObject>(16);
    for (final OGoObject eo: objects) {
      /* the object itself was changed */
      if (eo.hasChanges()) {
        if (childDebugOn)
          childLog.debug("base was changed: " + eo);
        this.changedObjects.add(eo);
        continue;
      }
      
      /* now walk over the child changes */
      for (final IOGoObjectChildChange change: this.childChanges) {
        if (change.hasChangesForMasterObject(eo)) {
          if (childDebugOn)
            childLog.debug("has changes for base: "+ change + "\n  base: "+eo);
          
          this.changedObjects.add(eo);
          break; /* just the inner loop */
        }
        else if (childDebugOn)
          childLog.debug("does not match base: " + eo + "\n  child: " + change);
      }
    }
    
    if (this.childChanges.size() > 0 && this.changedObjects.size() == 0)
      log.warn("there are child changes, but no changed objects?!");
    
    return null /* everything is fine */;
  }

  
  /* do it */
  
  @Override
  public Exception runInContext
    (OGoOperationTransaction _tx, EOAdaptorChannel _ch, OGoObjectContext _ctx)
  {
    if (this.changedObjects == null || this.changedObjects.size() == 0) {
      log.info("detected no changed objects, not running any SQL ...");
      return null; /* Nuffin' to do */
    }
    
    
    List<EOAdaptorOperation> ops    = new ArrayList<EOAdaptorOperation>(16);
    List<EOAdaptorOperation> logOps = new ArrayList<EOAdaptorOperation>(16);
    
    /* create update operations */
    
    for (OGoObject eo: this.changedObjects) {
      ops.add(this.updateOperation(eo, 
          this.baseEntity,
          this.idToBaseVersion.get(eo.id())));
      // TBD: logOps
    }
    
    ops.addAll(logOps);
    
    
    /* perform core operations */
    
    Exception error = _ch.performAdaptorOperations(ops);
    if (error != null)
      return error;

    
    /* perform child changes */
    
    if (this.childChanges != null) {
      for (IOGoObjectChildChange change: this.childChanges) {
        if ((error = change.runInContext(_tx, _ch, _ctx)) != null)
          return error;
      }
    }
    
    return null; /* everything is excellent */
  }

  
  /* description */

  @Override
  public void appendAttributesToDescription(final StringBuilder _d) {
    if (this.baseEntity != null)
      _d.append(" base=" + this.baseEntity.name());
    
    if (this.changedObjects != null)
      _d.append(" #changes=" + this.changedObjects.size());
    else
      _d.append(" no-changes");
    
    if (this.childChanges != null)
      _d.append(" #childchanges=" + this.childChanges.size());
    
    super.appendAttributesToDescription(_d);
  }
}
