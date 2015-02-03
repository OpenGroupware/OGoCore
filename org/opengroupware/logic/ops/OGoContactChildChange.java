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
package org.opengroupware.logic.ops;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.getobjects.eoaccess.EOAdaptorChannel;
import org.getobjects.eoaccess.EOAdaptorOperation;
import org.getobjects.eoaccess.EODatabase;
import org.getobjects.eoaccess.EOEntity;
import org.getobjects.eoaccess.EORelationship;
import org.getobjects.eocontrol.EOFetchSpecification;
import org.getobjects.eocontrol.EOQualifier;
import org.getobjects.eocontrol.EOSortOrdering;
import org.getobjects.foundation.NSException;
import org.getobjects.foundation.NSJavaRuntime;
import org.getobjects.foundation.UObject;
import org.getobjects.foundation.UString;
import org.opengroupware.logic.core.OGoObjectContext;
import org.opengroupware.logic.db.IOGoContactChildObject;
import org.opengroupware.logic.db.OGoObject;

/**
 * OGoContactChildChange
 * <p>
 * This is an abstract wrapper for operations which work on objects attached to
 * contacts, eg
 * <ul>
 *   <li>OGoAddressChange
 *   <li>OGoPhoneNumberChange
 *   <li>OGoEMailAddressChange
 *   <li>OGoContactExtValueChange
 * </ul>
 * Note that this object can take changes for multiple contacts! (to allow for
 * batch updates).
 * 
 * @author helge
 */
public abstract class OGoContactChildChange extends OGoOperation
  implements IOGoObjectChildChange /* hasChangesForMasterObject */
{

  protected EOEntity baseEntity;
  protected EOEntity parentEntity;
  
  protected Date   now;
  protected Number actorId;
  
  /* change requests */
  protected Set<Number> contactIds;
  protected Map<Number, Map<String, Object>>       idToUpdates;
  protected Set<Number> deletedIds;
  protected Map<Number, List<Number>>              contactIdToDeletes;
  protected Map<Number, List<Map<String, Object>>> contactIdToInserts;
  
  /* fetches */
  protected Map<Number, List<IOGoContactChildObject>> contactIdToChildren;
  protected Map<Number, List<IOGoContactChildObject>> contactIdToNewObjects;
  protected Map<Number, List<IOGoContactChildObject>> contactIdToUpdatedObjects;
  

  public OGoContactChildChange
    (OGoObjectContext _oc, final String _entityName, final String _parentName)
  {
    super(_oc);
    this.baseEntity = _oc.oDatabase().entityNamed(_entityName);
    this.parentEntity = _oc.oDatabase().entityNamed(_parentName);
    
    // Note: TreeMap because we can have mixed setups (Integer or Long keys)
    //       which compare fine, but have different hashes
    //  TBD: I'm not completely happy with all this.
    
    this.contactIds         = new HashSet<Number>(4);
    this.deletedIds         = new HashSet<Number>(4);
    this.idToUpdates        = new TreeMap<Number,Map<String,Object>>
      (PrimaryKeyComparator.sharedComparator);
    this.contactIdToDeletes = new TreeMap<Number, List<Number>>
      (PrimaryKeyComparator.sharedComparator);
    this.contactIdToInserts = new TreeMap<Number, List<Map<String,Object>>>
      (PrimaryKeyComparator.sharedComparator);

    this.contactIdToNewObjects =
      new TreeMap<Number, List<IOGoContactChildObject>>
        (PrimaryKeyComparator.sharedComparator);
    this.contactIdToUpdatedObjects =
      new TreeMap<Number, List<IOGoContactChildObject>>
        (PrimaryKeyComparator.sharedComparator);
  }
  
  /**
   * Lookup the name of the relationship entity. Example:<pre>
   *   lookupRelshipEntityName(oc, "Persons", "addresses")</pre>
   * This will return PersonAddresses.
   * 
   * @param _oc     - the OGoObjectContext
   * @param _parent - the name of the parent entity, eg Persons
   * @param _key    - the name of the relationship, eg 'phones'
   * @return the name of the relationship entity
   */
  public static String lookupRelshipEntityName
    (final OGoObjectContext _oc, final String _parent, final String _key)
  {
    final EODatabase db = _oc.database();
    if (db == null) return null;
    EOEntity e = db.entityNamed(_parent);
    if (e == null) return null;
    final EORelationship r = e.relationshipNamed(_key);
    if (r == null) return null;
    e = r.destinationEntity();
    if (e == null) return null;
    return e.name();
  }  
  
  /* adding changes */
  
  /**
   * Registers a changeset for the contact with the given primary key. A child
   * object might have changed, it might have been added or deleted.
   * 
   * @param _contactId - primary key of contact to register a change for
   * @param _update    - child-object id to child changeset
   * @param _delete    - child objects to delete from the given contact
   * @param _insert    - child objects to add for the given contact
   */
  public void addChangeSet(Number _contactId,
      final Map<Number, Map<String, Object>> _update,
      List<Number> _delete,
      final List<Map<String, Object>> _insert)
  {
    _contactId = UObject.intOrLongValue(_contactId); // support Rhino
    _delete    = UObject.listOfIntsOrLongs(_delete); // support Rhino
    // TBD: support Rhino keys in _update ...
    if (_contactId == null)
      return;
    
    if (_update != null && _update.size() > 0) {
      /* Note: we don't need the contactId, we just use the child object id */
      // TBD: intOrLongValue on update key?!
      this.idToUpdates.putAll(_update);
      this.contactIds.add(_contactId);
    }
    if (_delete != null && _delete.size() > 0) {
      this.contactIdToDeletes.put(_contactId, _delete); // TBD: superflous?
      this.contactIds.add(_contactId);
      this.deletedIds.addAll(_delete);
    }
    
    this.addInserts(_contactId, _insert);
  }
  
  public void addInserts(Number _contactId, List<Map<String, Object>> _insert) {
    if (_insert == null || _insert.size() == 0)
      return;
    
    this.contactIdToInserts.put(_contactId, _insert);
    this.contactIds.add(_contactId);
  }
  
  @SuppressWarnings("unchecked")
  public boolean addChangeSet(final OGoObject _eo, final String _relship) {
    if (_eo == null)
      return false;
    
    final Object v = _eo.valueForKey(_relship);
    if (UObject.isEmpty(v)) return true; // nothin' to do
    
    EORelationship relship = this.parentEntity.relationshipNamed(_relship);
    if (relship == null) {
      log.error("did not find relationship '" + _relship + "' in " +
          this.parentEntity);
      return false;
    }
    
    final Collection<OGoObject> rv = (Collection<OGoObject>)v;
    
    final Map<Number, Map<String, Object>> updates =
      new HashMap<Number, Map<String,Object>>(rv.size());
    List<Map<String, Object>> inserts = new ArrayList<Map<String,Object>>(2);

    for (final OGoObject child: rv) {
      if (!child.hasChanges())
        continue; /* did not change */

      if (child.isNew())
        inserts.add(child.valuesForKeys(child.entity().classPropertyNames()));
      else
        updates.put(child.id(), child.changesFromSnapshot(child.snapshot()));
    }

    if (updates.size() == 0 && (inserts == null || inserts.size() == 0))
      return true; /* nothing to do */

    /* add updates/inserts to child operation */

    this.addChangeSet(_eo.id(), updates, null, inserts);
    return true;
  }
  
  
  /* checking for changes */
  
  /**
   * Returns true if this child object contains changes for the master object
   * with the primary key _pkey.
   * 
   * @param _pkey - primary key of the master object (eg OGoPerson object)
   * @return true if this change objects has changes for the master
   */
  public boolean hasChangesForContactId(Number _pkey) {
    _pkey = UObject.intOrLongValue(_pkey); // support Rhino
    if (_pkey == null) {
      log.warn("invalid key object ...");
      return false;
    }
    
    if (childLog.isDebugEnabled()) {
      childLog.debug("has changes for contact " + _pkey +
          " (" + _pkey.getClass() + ")?");
    
      childLog.debug("  contactIdToDeletes: " + this.contactIdToDeletes);
    }
    
    if (this.contactIdToDeletes.containsKey(_pkey))
      return true;
    if (this.contactIdToNewObjects.containsKey(_pkey))
      return true;
    if (this.contactIdToUpdatedObjects.containsKey(_pkey))
      return true;

    childLog.debug("  no changes for id: " + _pkey);
    return false;
  }
  public boolean hasChangesForMasterObject(final OGoObject _contact) {
    return (_contact != null)
      ? this.hasChangesForContactId(_contact.id())
      : false;
  }
  
  
  /* prepare */

  @SuppressWarnings("unchecked")
  @Override
  public Exception prepareForTransactionInContext
    (final OGoOperationTransaction _tx, final OGoObjectContext _oc)
  {
    if (this.contactIds == null || this.contactIds.size() == 0)
      return null; /* nothing to do (even INSERTs require a contact-id!) */
   
    Exception error = null;
    this.now     = _tx.startDate();
    this.actorId = _oc.actorID();
    
    
    /* Fetch child objects (emails,phones,addresses) for all requested contacts
     * by the companyId.
     */
    
    List<IOGoContactChildObject> children = null;
    if (this.contactIds != null) {
      EOFetchSpecification fs = new EOFetchSpecification(
        this.baseEntity.name(),
        EOQualifier.parse("companyId IN %@", this.contactIds),
        EOSortOrdering.create("key", "ASC"));
    
      children =_tx.objectContext().objectsWithFetchSpecification(fs);
      if (children == null) {
        error = _tx.objectContext().consumeLastException();
        return error != null ? error : new NSException("could not fetch objs");
      }
    }    
    
    /* Group the resulting child-objects by companyId,
     * apply changes (takeValuesFromDictionary()),
     * request necessary permissions.
     */
    
    this.contactIdToChildren =
      new HashMap<Number, List<IOGoContactChildObject>>(this.contactIds.size());
    if (children != null) {
      for (IOGoContactChildObject child: children) {
        final Number contactId =
          UObject.intOrLongValue(child.valueForKey("companyId"));

        /* add fetched object to grouping map (all of them) */

        List<IOGoContactChildObject> list =
          this.contactIdToChildren.get(contactId);
        if (list == null) {
          list = new ArrayList<IOGoContactChildObject>(4);
          this.contactIdToChildren.put(contactId, list);
        }
        list.add(child);

        /* remove from update requests */

        final Number pkey = child.id();
        final Map<String, Object> changes = this.idToUpdates.remove(pkey);
        if (changes != null) {
          /* apply update */
          child.takeValuesFromDictionary(changes);
        }
      }
    }
    
    /* check whether all updates could be satisfied (fetched) */
    
    if (this.idToUpdates != null && this.idToUpdates.size() > 0) {
      /* Note: we intentionally do not track delete ids, if they are gone,
       * they are gone ...
       */
      log.warn("could not fetch all requested child objects: " + 
          this.idToUpdates);
      return new NSException("404 - did not find all update objects"); // TBD
    }
    
    /* request permissions on deleted objects */
    
    if (this.contactIdToDeletes != null) {
      for (final Number contactId: this.contactIdToDeletes.keySet()) {
        /* Note: we just ask for the contact permission, not for the subobject,
         *       this should be faster.
         * Implies: we do not support per-address ACLs (though certain types
         *          are already protected by the contact).
         */
        _tx.requestPermissionOnId("w", this.parentEntity.name(), contactId);
      }
    }
    
    /* create new objects (for INSERTs) */
    
    if ((error = this.createNewObjects(_tx)) != null)
      return null;
    
    /* rework key sequences, this might change otherwise unchanged records! */
    
    if ((error = this.reworkKeySequences(_tx)) != null)
      return error;
    
    /* scan for changes */

    if (children != null) {
      for (final IOGoContactChildObject child: children) {
        if (!child.hasChanges())
          continue;

        final Number contactId = (Number)child.valueForKey("companyId");

        List<IOGoContactChildObject> list;
        if ((list = this.contactIdToUpdatedObjects.get(contactId)) == null) {
          list = new ArrayList<IOGoContactChildObject>(4);
          this.contactIdToUpdatedObjects.put(contactId, list);
        }
        list.add(child);

        // TBD: we could probably optimize that a bit by checking the contacts
        //      only since child objects have no own write permissions (though
        //      this might change?)
        // TBD: we should only do this if we *know* that the object has been
        //      changed!
        _tx.requestPermissionOnId("w", this.baseEntity.name(), child.id());
      }
    }
    
    /* return */
    
    return error;
  }
  
  /**
   * Called by prepareForTransactionInContext() to create OGoContactChildObject
   * objects (eg OGoAddress, OGoPhoneNumber, etc) for INSERTs.
   * <p>
   * This first gets the object value record (from contactIdToInserts,
   * then creates a new EO object
   * and
   * applies the changes on the EO object (takeValuesFromDictionary).
   * It also ensures that the <code>companyId</code> of the child object is
   * properly setup.
   * <p>
   * The method also requests the <code>w</code> (write) permission on the
   * associated contact.
   * 
   * @param _tx - the OGoOperationTransaction
   * @return null if everything was one, an Exception otherwise
   */
  protected Exception createNewObjects(final OGoOperationTransaction _tx) {
    if (this.contactIdToInserts == null)
      return null; /* nothing to be done */
    
    for (final Number contactId: this.contactIdToInserts.keySet()) {
      final List<Map<String, Object>> changes =
        this.contactIdToInserts.remove(contactId);
      if (changes == null || changes.size() == 0)
        continue;

      /* setup list of inserts (the list will be converted to ops in run...) */

      List<IOGoContactChildObject> list =
        this.contactIdToNewObjects.get(contactId);
      if (list == null) {
        list = new ArrayList<IOGoContactChildObject>(4);
        this.contactIdToNewObjects.put(contactId, list);
      }

      /* create fresh Java EO objects and apply changes */

      for (final Map<String, Object> change: changes) {
        if (change == null || change.size() == 0)
          continue;
        
        /* request write permission on contact */
        _tx.requestPermissionOnId("w", this.parentEntity.name(), contactId);

        // System.err.println("NEW VALUES: " + change);
        final IOGoContactChildObject child = this.createObject();
        child.takeValuesFromDictionary(change);
        child.takeValueForKey(contactId, "companyId");
        // System.err.println("  CHILD: " + child);
        list.add(child);
      }
    }
    
    return null; /* everything is fine */
  }
  
  /**
   * Process key assignments. Walk over each contact and setup the proper
   * key numbering to avoid duplicate keys.
   * <p>
   * This is called by prepareForTransactionInContext().
   * <p>
   * Note: email has different rules. (sequence/key separate from vCard label)
   * @param _tx 
   */
  protected Exception reworkKeySequences(final OGoOperationTransaction _tx) {
    return null;
  }
  
  
  /* do it */
  
  /**
   * This method builds and executes the EOAdaptorOperations required to
   * apply the requested changes in this sequence:
   * <ul>
   *   <li>deletes
   *   <li>updates
   *   <li>inserts
   * </ul>
   */
  @Override
  public Exception runInContext
    (OGoOperationTransaction _tx, EOAdaptorChannel _ch, OGoObjectContext _ctx)
  {
    // TBD: shouldn't we build the adaptor ops in one of the prepare methods?
    //      => probably not in the very first pass, because the permission
    //         checks might fail
    final List<EOAdaptorOperation> ops = new ArrayList<EOAdaptorOperation>(16);
    
    List<EOAdaptorOperation> delOps =
      this.deleteOperations(this.baseEntity, this.deletedIds);
    if (delOps != null) ops.addAll(delOps); delOps = null;
    
    // TBD: fix this crap (datastructures are not exactly great)
    for (final List<IOGoContactChildObject> children:
         this.contactIdToUpdatedObjects.values())
    {
      for (final IOGoContactChildObject child: children)
        ops.add(this.updateOperation(child, this.baseEntity, null));
    }
    
    // TBD: fix this crap (TBD: what exactly is the crap here ? :-)
    for (final List<IOGoContactChildObject> children:
         this.contactIdToNewObjects.values())
    {
      for (final IOGoContactChildObject child: children)
        ops.add(this.insertOperation(child, this.baseEntity));
    }

    final Exception error = _ch.performAdaptorOperations(ops);
    if (error != null) return error;
    
    return null /* everything is fine */;
  }
  
  
  /* support */
  
  /**
   * Create a fresh instance of the child object for objects to be inserted.
   * 
   * @return a fresh OGoContactChildObject object
   */
  @SuppressWarnings("rawtypes")
  public IOGoContactChildObject createObject() {
    final Class clazz = this.oc.database().classForEntity(this.baseEntity);
    if (clazz == null) {
      log.error("did not find class for entity!");
      return null;
    }
    
    final IOGoContactChildObject eo = (IOGoContactChildObject)
      NSJavaRuntime.NSAllocateObject(clazz, EOEntity.class, this.baseEntity);
    
    return eo;
  }

  
  /* description */

  @Override
  public void appendAttributesToDescription(final StringBuilder _d) {
    if (this.baseEntity != null)
      _d.append(" base=" + this.baseEntity.name());
    if (this.parentEntity != null)
      _d.append(" parent=" + this.parentEntity.name());
    
    if (this.contactIds != null) {
      _d.append(" contactids=" +
          UString.componentsJoinedByString(this.contactIds, ","));
    }
    if (this.idToUpdates != null)
      _d.append(" #id2up=" + this.idToUpdates.size());
    if (this.deletedIds != null) {
      _d.append(" deleted=" +
          UString.componentsJoinedByString(this.deletedIds, ","));
    }
    
    super.appendAttributesToDescription(_d);
  }
  
  
  public static class PrimaryKeyComparator implements Comparator<Number> {
    public static final PrimaryKeyComparator sharedComparator =
      new PrimaryKeyComparator();

    public int compare(final Number _o1, final Number _o2) {
      if (_o1 == _o2) return 0;
      else if (_o1 == null) return -1;
      else if (_o2 == null) return 1;
      else {
        long l1 = _o1.longValue(), l2 = _o2.longValue();
        if (l1 < l2) return -1;
        else if (l1 > l2) return 1;
        return 0;
      }
    }
    
  }
}
