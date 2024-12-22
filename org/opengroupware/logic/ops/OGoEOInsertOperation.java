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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.getobjects.eoaccess.EOAdaptorChannel;
import org.getobjects.eoaccess.EOAdaptorOperation;
import org.getobjects.eoaccess.EOEnterpriseObject;
import org.getobjects.eoaccess.EOEntity;
import org.getobjects.eoaccess.EOJoin;
import org.getobjects.eoaccess.EORelationship;
import org.getobjects.foundation.NSCompoundException;
import org.getobjects.foundation.NSException;
import org.getobjects.foundation.NSJavaRuntime;
import org.getobjects.foundation.NSObject;
import org.getobjects.foundation.UList;
import org.getobjects.foundation.UMap;
import org.getobjects.foundation.UObject;
import org.opengroupware.logic.core.IOGoOperation;
import org.opengroupware.logic.core.OGoObjectContext;
import org.opengroupware.logic.db.IOGoObject;
import org.opengroupware.logic.db.OGoDatabase;

/**
 * A generic insert operation which works on top of the model.
 * <p>
 * Can deal with child operations (need to apply primary keys of parent objects
 * on those).
 *
 * <p>
 * @author helge
 */
public class OGoEOInsertOperation extends OGoCompoundOperation
  implements IOGoInsertOperation
{
  protected IOGoInsertOperationDelegate delegate;
  protected EOEntity                    baseEntity;
  protected List<EOEnterpriseObject>    objects;
  protected List<List<IOGoInsertPatch>> childPatches; // same idx as 'objects'
  
  protected Date   now;
  protected Number actorId;
  protected Map<String, IOGoInsertOperation> childOpGrouper;
  
  public OGoEOInsertOperation(OGoObjectContext _oc, final EOEntity _entity) {
    super(_oc);
    this.baseEntity   = _entity;
    this.objects      = new ArrayList<EOEnterpriseObject>(16);
    this.childPatches = new ArrayList<List<IOGoInsertPatch>>(16);
  }
  public OGoEOInsertOperation(final OGoObjectContext _oc, final String _ename) {
    this(_oc, _oc != null ? _oc.oDatabase().entityNamed(_ename) : null);
  }
  
  
  /* accessors */
  
  public EOEnterpriseObject get(final int _idx) {
    return this.objects != null ? this.objects.get(_idx) : null;
  }
  
  public List<EOEnterpriseObject> objects() {
    return this.objects;
  }

  public Date now() {
    return this.now;
  }
  public long timeSinceNow() {
    return this.now != null ? new Date().getTime() - this.now.getTime() : null;
  }
  
  
  /* adding objects to be inserted */
  
  /**
   * This method adds an EO to the insert List. The EO is scanned for its
   * relationships and if such are set, appropriate child operations are
   * generated.
   * 
   * @param _eo - EO to INSERT (no primary key is set)
   * @return the index in the insert queue (tbd: useful for what?)
   */
  public int add(final EOEnterpriseObject _eo) {
    final int idx = this.primaryAddEO(_eo);
    if (idx < 0) return idx;
    
    if (!this.willAddRelationshipsOfEO(_eo, idx))
      return -1;
    
    this.addRelationshipsOfEO(_eo, idx);
    
    this.didAddRelationshipsOfEO(_eo, idx);
    
    return idx;
  }
    
  /**
   * This method adds an EO to the insert List. The EO is not scanned for
   * relships or anything.
   * 
   * @param _eo - EO to INSERT (no primary key is set)
   * @return the index in the insert queue
   */
  public int primaryAddEO(final EOEnterpriseObject _eo) {
    if (_eo == null)
      return -1;
    
    this.objects.add(_eo);
    this.childPatches.add(new ArrayList<IOGoInsertPatch>(8));
    int idx = this.objects.size() - 1;
    
    return idx;
  }
  
  /**
   * Scans the EO object for relationships configured in the baseEntity.
   * 
   * For example if the Persons entity has the relationships 'acl', 'comment'
   * and 'addresses', the method will query the _eo for each of the keys.
   * If a key returns a value, that is added as a child change using either
   * addToManyRelationshipOfEO() or addRelationshipOfEO().
   * 
   * @param _eo  - the EO to be scanned
   * @param _idx - the index of the EO, we need it to add the child changes
   */
  @SuppressWarnings("rawtypes")
  public void addRelationshipsOfEO(final EOEnterpriseObject _eo, int _idx) {
    if (_eo == null)
      return;
    
    // TBD: scan which relationships are set
    final EORelationship[] relships = this.baseEntity.relationships();
    if (relships == null)
      return;
    
    for (EORelationship relship: relships) {
      final Object v = _eo.valueForKey(relship.name());
      if (v == null)
        continue; /* nothing to insert */
      
      if (relship.isToMany())
        this.addToManyRelationshipOfEO(_eo, _idx, relship, (Collection)v);
      else
        this.addRelationshipOfEO(_eo, _idx, relship, v);
    }
  }
  
  /**
   * This just calls addRelationshipOfEO() for each of the collection values.
   * 
   * @param _eo      - the EO
   * @param _idx     - index of the EO in the change queue
   * @param _relship - toMany relationship to be processed
   * @param _values  - values of the relationship
   */
  @SuppressWarnings("rawtypes")
  public void addToManyRelationshipOfEO
    (final EOEnterpriseObject _eo, final int _idx,
     final EORelationship _relship, Collection _values)
  {
    if (_values == null)
      return;
    
    for (Object v: _values)
      this.addRelationshipOfEO(_eo, _idx, _relship, v);
  }

  /**
   * Register a related child object for an INSERT. Note that the child can be
   * an EO or a simple Map.
   * <p>
   * The method first creates a child operation using
   * operationForRelationship() (the default implementation returns one
   * OGoEOInsertOperation per relationship name).
   * It then adds the <code>_child</code> to that operation using add().
   * Finally it creates a PostInsertPatch for the child and adds this to the
   * object patch queue at the given object <code>_idx</code>
   * 
   * @param _eo      - base object
   * @param _idx     - index of object in operation (add ops there)
   * @param _relship - associated relationship
   * @param _child   - child EO, or Map, or a String (mapped to 'value')
   * @return null on success, an error Exception on fail
   */
  @SuppressWarnings("unchecked")
  public Exception addRelationshipOfEO
    (final EOEnterpriseObject _eo, final int _idx,
     final EORelationship _relship, Object _child)
  {
    if (_child == null)
      return null; // nothing to add
    
    if (_child instanceof String)
      _child = UMap.create("value", _child);
    
    /* Fill operation, this gives us an index which we can use to refer to the
     * child object.
     */
    
    final IOGoInsertOperation op = this.operationForRelationship(_relship);
    int childIdx = -1;
    
    if (_child instanceof Map)
      childIdx = op.add((Map<String, Object>)_child);
    else if (_child instanceof EOEnterpriseObject)
      childIdx = op.add((EOEnterpriseObject)_child);
    else
      return new NSException("unexpected child object: " + _child);
    
    if (childIdx < 0) {
      log.error("could not enqueue operation for child: " + _child);
      return new NSException("could not enqueue child operation");
    }
    
    /* register child operation+idx */
    
    final PostInsertPatch patch = new PostInsertPatch(_relship, op, childIdx);
    this.childPatches.get(_idx).add(patch);
    return null; /* we are done */
  }
  
  /**
   * Returns an operation object which maintains INSERTs for the given
   * relationship. We use just one operation per relationship.
   * 
   * @param _relship - the relationship
   * @return an insert operation
   */
  public IOGoInsertOperation operationForRelationship(EORelationship _relship) {
    if (_relship == null)
      return null;
    
    if (this.childOpGrouper == null)
      this.childOpGrouper = new HashMap<String, IOGoInsertOperation>(8);
    
    IOGoInsertOperation op = this.childOpGrouper.get(_relship.name());
    if (op == null) {
      op = this.newOperationForRelationship(_relship);
      this.childOpGrouper.put(_relship.name(), op);
    }
    if (op != null) {
      if (this.childOperations == null)
        this.childOperations = new HashSet<IOGoOperation>(16);
      if (!this.childOperations.contains(op))
        this.childOperations.add(op);
    }
    return op;
  }
  
  /**
   * Subclasses can override this method if they want to instantiate specific
   * operations for specific relationships (or destination entities).
   * The default implementation creates a new OGoEOInsertOperation.
   * 
   * @param _rs - the relationship (eg query the destinationEntity)
   * @return an IOGoInsertOperation
   */
  public IOGoInsertOperation newOperationForRelationship(EORelationship _rs) {
    final EOEntity e     = _rs.destinationEntity();
    final String   ename = e.name();
    
    // TBD: fix this hardcoding
    if (ename.equals("Companies") ||
        ename.equals("Persons")   ||
        ename.equals("Teams"))
      return new OGoContactInsert(this.oc, ename);
    
    if (ename.equals("Projects"))
      return new OGoProjectInsert(this.oc);
    
    return new OGoEOInsertOperation(this.oc, e);
  }
  
  
  /* adding Maps */
  
  /**
   * Add the given values as a new record. Note that the values can contain
   * relationships, eg:<pre>
   *   { lastname = Duck; firstname = Donald;
   *     phones = ( { value = "+49-391-6623-0"; } ) }</pre>
   * 
   * @param _values - a set of values to be inserted
   * @return index of record in the operation, or -1 if something failed
   */
  @SuppressWarnings("unchecked")
  public int add(Map<String, Object> _values) {
    boolean didCopyMap = false;
    
    if (_values == null || _values.size() == 0)
      return -1;
    
    /* let the delegate patch the record */
    
    if (this.delegate != null) {
      _values = new HashMap<String, Object>(_values);
      didCopyMap = true;
    }
    _values = this.willProcessMap(_values);
    
    /* extract relationships (remove from Map, move to relshipValues) */
    
    Map<String, Object> relshipValues = null;
    final EORelationship[] relships = this.baseEntity.relationships();
    if (relships != null) {
      for (EORelationship relship: relships) {
        final String rn = relship.name();
        if (rn == null || !_values.containsKey(rn))
          continue; /* nothing to insert */
        
        if (!didCopyMap) {
          _values = new HashMap<String, Object>(_values);
          didCopyMap = true;
        }
        final Object v = _values.remove(relship.name());
        
        if (relshipValues == null)
          relshipValues = new HashMap<String, Object>(relships.length);
        relshipValues.put(relship.name(), v);
      }
    }
    
    /* create and add base object */
    
    EOEnterpriseObject baseObject = this.createObject();
    if (baseObject == null)
      return -1;
    baseObject.takeValuesFromDictionary(_values);
    
    int idx = this.primaryAddEO(baseObject);
    if (idx < 0)
      return idx;
    
    /* add relationships */
    
    if (relshipValues != null) {
      for (String relshipName: relshipValues.keySet()) {
        EORelationship relship = this.baseEntity.relationshipNamed(relshipName);
        Object v = relshipValues.get(relshipName);
        
        if (relship.isToMany()) {
          Collection<Object> cv;
          
          if (v.getClass().isArray())
            cv = UList.asList(v);
          else if (!(v instanceof Collection)) {
            cv = new ArrayList<Object>(1);
            cv.add(v);
          }
          else
            cv = (Collection<Object>)v;
          
          this.addToManyRelationshipOfEO(baseObject, idx, relship, cv);
        }
        else
          this.addRelationshipOfEO(baseObject, idx, relship, v);
      }
    }
    
    /* we are done */
    return idx;
  }
  
  /**
   * Add the given values as a new record. Note that the values can contain
   * relationships, eg:<pre>
   *   op.add("lastname", "Duck", "firstname", "Donald",
   *          "phones", phonesArray);</pre>
   * 
   * @param _args - key/values pairs which form the record to be inserted
   * @return index of record in the operation, or -1 if something failed
   */
  @SuppressWarnings("unchecked")
  public void add(final Object... _args) {
    this.add(UMap.createArgs(_args));
  }
  

  /* prepare */

  @Override
  public Exception prepareForTransactionInContext
    (final OGoOperationTransaction _tx, final OGoObjectContext _oc)
  {
    this.now     = _tx.startDate();
    this.actorId = _oc.actorID();
    
    // TBD: need to apply 'actorId' as owner etc!
    // TBD: what about permissions? eg allow creation of 'public contacts'
    //      only for a specific role?
    
    /* prepare and validate objects */
    
    List<Exception> errors = null;
    for (final EOEnterpriseObject eo: this.objects) {
      Exception error;
      
      if ((error = this.prepareObjectForInsert(eo, _tx, _oc)) == null)
        error = eo.validateForInsert(); // also does fillups
      // TBD: fixup creation/moddates with *our* now to make them consistent
      
      if (error != null) { /* validation error */
        if (errors == null)
          errors = new ArrayList<Exception>(16);
        errors.add(error);
      }
    }
    
    if (errors == null || errors.size() == 0)
      return null /* everything is fine */;
    
    return NSCompoundException.exceptionForList("failed to prepare", errors);
  }
  
  /**
   * Called by prepareForTransactionInContext() before validateForInsert() is 
   * called on the EO. It allows you to prefill standard properties.
   * It is too late to add relationships.
   * 
   * @param _eo  - EO object to be prepared
   * @param _tx  - transaction
   * @param _oc - object context
   * @return null if preparation was OK, the error otherwise
   */
  public Exception prepareObjectForInsert
    (final EOEnterpriseObject _eo,
     final OGoOperationTransaction _tx, final OGoObjectContext _oc)
  {
    // TBD: apply actor, apply objectVersion etc?
    if (this.baseEntity.attributeNamed("objectVersion") != null)
      _eo.takeValueForKey(one, "objectVersion");
    if (this.baseEntity.attributeNamed("dbStatus") != null)
      _eo.takeValueForKey("inserted", "dbStatus");
    
    if (this.baseEntity.attributeNamed("creationDate") != null)
      _eo.takeValueForKey(this.now, "creationDate");
    if (this.baseEntity.attributeNamed("lastModified") != null)
      _eo.takeValueForKey(this.now, "lastModified");
    
    if (this.baseEntity.attributeNamed("ownerId") != null) {
      if (UObject.isEmpty(_eo.valueForKey("ownerId")))
        _eo.takeValueForKey(this.actorId, "ownerId");
    }
    if (this.baseEntity.attributeNamed("creatorId") != null) {
      if (UObject.isEmpty(_eo.valueForKey("creatorId")))
        _eo.takeValueForKey(this.actorId, "creatorId");
    }
    /*
     * Documents: "versionCount",   zero,
     */
    return null;
  }
  private final static Number one = Integer.valueOf(1);
  
  
  /* do it */
  
  @Override
  public Exception runInContext
    (OGoOperationTransaction _tx, EOAdaptorChannel _ch, OGoObjectContext _oc)
  {
    Exception error;
    int       count;
    
    if (this.objects == null || (count = this.objects.size()) == 0)
      return null; /* nothing to be done */
    
    
    /* First request the primary keys and apply them. We request the keys
     * pretty late, so that we don't consume them unnecessarily in case the
     * operation fails in validation.
     * 
     * This section fetches the keys and applies them on the EOs AND their
     * patch operations.
     */
    
    final String pkeyName = this.baseEntity.primaryKeyAttributeNames()[0];
    if (pkeyName == null)
      return new NSException("baseEntity has no primary key attribute?");
    
    Number[] pkeys = null;
    for (int i = 0; i < count; i++) {
      final EOEnterpriseObject eo = this.objects.get(i);
      
      if (UObject.isEmpty(eo.valueForKey(pkeyName))) {
        if (pkeys == null) {
          pkeys = this.oc.oDatabase().grabPrimaryKeys(count);
          if (pkeys == null)
            return new NSException("could not create primary keys ...");
        }
        eo.takeValueForKey(pkeys[i], pkeyName); /* apply primary key */
      }
      
      for (final IOGoInsertPatch patch: this.childPatches.get(i)) {
        if ((error = patch.apply(_tx, eo)) != null)
          return error;
      }
      
      if ((error = this.fixupObjectWithPrimaryKey(eo, _tx, _oc)) != null) {
        log.error("object fixup failed", error);
        return error;
      }
    }
    
    
    /* All our EOs got their primary keys assigned and the
     * child operations got notified of (patched with) the
     * primary keys (foreign keys for the children).
     * 
     * Next we prepare all child operations. This will validate them for
     * insert, which should now succeed with the proper primary key.
     */
    error = this.prepareChildOpsForTransactionInContext(_tx, _oc);
    if (error != null)
      return error;
    // TBD: what about permission requests?
    // TBD: send this?: this.transactionDidBeginInContext(_tx, _ch, _ctx)
    
    
    /* now we can perform the adaptor ops for the base objects ... */
    
    EOAdaptorOperation[] ops =
      buildAdaptorOperations(this.baseEntity, this.objects);
    if (ops == null)
      return new NSException("could not create adaptor operations");
    
    if ((error = _ch.performAdaptorOperations(ops)) != null)
      return error;
    
    
    /* next we need to fill in the obj_info table */
    
    ops = buildObjInfoInsertOps(_oc.oDatabase(), this.baseEntity,this.objects);
    if (ops != null) {
      if ((error = _ch.performAdaptorOperations(ops)) != null) {
        log.error("could not insert obj_info ops", error);
        return error;
      }
    }
    
    
    /* And finally we trigger the suboperations. They already got the primary
     * keys patched in.
     */
    if ((error = super.runChildOpsInContext(_tx, _ch, _oc)) != null)
      return error;
    
    
    /* finish ;-) */
    return null; /* everything went fine!! */
  }
  
  /**
   * Derives EOAdaptorOperation objects from the source objects.
   * 
   * @return an array of EOAdaptorOperation's, or null if something failed
   */
  public static EOAdaptorOperation[] buildAdaptorOperations
    (final EOEntity _entity, List<EOEnterpriseObject> _objects)
  {
    if (_objects == null)
      return null;
    
    final int count = _objects.size();
    final EOAdaptorOperation ops[] = new EOAdaptorOperation[count];
    
    for (int i = 0; i < count; i++) {
      ops[i] = buildAdaptorOperationForObject(_entity, _objects.get(i));
      if (ops[i] == null)
        return null;
    }
    
    return ops;
  }
  
  public static EOAdaptorOperation buildAdaptorOperationForObject
    (final EOEntity _entity, final EOEnterpriseObject _eo)
  {
    final EOAdaptorOperation op = new EOAdaptorOperation(_entity);
    op.setAdaptorOperator(EOAdaptorOperation.AdaptorInsertOperator);
    // TBD: do the class properties include relationships
    op.setChangedValues(_eo.valuesForKeys(_entity.classPropertyNames()));
    return op;
  }
  
  @SuppressWarnings("unchecked")
  public static EOAdaptorOperation[] buildObjInfoInsertOps
    (OGoDatabase _db, final EOEntity _entity, List<EOEnterpriseObject> _objects)
  {
    // TBD: we need plain SQL 'raw' adaptor operations
    if (_objects == null || _entity == null || _db == null)
      return null;
    
    final String   pkey       = _entity.primaryKeyAttributeNames()[0];
    final EOEntity infoEntity = _db.entityNamed("PrimaryKeyTypes");
    
    int count = _objects.size();
    List<EOAdaptorOperation> ops = new ArrayList<EOAdaptorOperation>(count);

    for (int i = 0; i < count; i++) {
      final EOEnterpriseObject eo = _objects.get(i);
      final Object pkeyValue = eo.valueForKey(pkey);
      if (pkeyValue == null) continue;
      
      String eType = (eo instanceof IOGoObject)
        ? ((IOGoObject)eo).entityNameInOGo5()
        : (String)eo.valueForKey("entityNameInOGo5");
      if (eType == null) continue;
      
      /* blacklist is in LSDBObjectNewCommand.m */
      if (eType.equals("CompanyValue")) continue;
      if (eType.endsWith("Assignment")) continue;
      
      final EOAdaptorOperation op = new EOAdaptorOperation(infoEntity);
      op.setAdaptorOperator(EOAdaptorOperation.AdaptorInsertOperator);
      op.setChangedValues(UMap.create(
          "objectId", pkeyValue, "objectType", eType));
      ops.add(op);
    }
    
    count = ops.size();
    return count > 0 ? ops.toArray(new EOAdaptorOperation[count]) : null;
  }
  
  /**
   * This is called after the freshly aquired primary key got pushed to the
   * EO object. A subclass can use this to derive object fields from the
   * primary key, for example the 'number' of a contact (eg OGo28373).
   * 
   * @param _eo - EO object to be prepared
   * @param _tx - transaction
   * @param _oc - object context
   * @return null if preparation was OK, the error otherwise
   */
  public Exception fixupObjectWithPrimaryKey
    (final EOEnterpriseObject _eo,
     final OGoOperationTransaction _tx, final OGoObjectContext _oc)
  {
    return null;
  }
  
  /* support */
  
  /**
   * Create a fresh instance to be inserted.
   * 
   * @return a fresh EOEnterpriseObject object
   */
  @SuppressWarnings("rawtypes")
  public EOEnterpriseObject createObject() {
    if (this.baseEntity == null) {
      log.error("missing base entity: " + this);
      return null;
    }
    
    final Class clazz = this.oc.database().classForEntity(this.baseEntity);
    if (clazz == null) {
      log.error("did not find class for entity!");
      return null;
    }
    
    final EOEnterpriseObject eo = (EOEnterpriseObject)
      NSJavaRuntime.NSAllocateObject(clazz, EOEntity.class, this.baseEntity);
    
    return eo;
  }
  
  
  /* callbacks */

  public void didAddRelationshipsOfEO(EOEnterpriseObject _eo, int _idx) {
    if (this.delegate != null)
      this.delegate.operationDidAddRelationshipsOfEO(this, _eo, _idx);
  }

  public boolean willAddRelationshipsOfEO(EOEnterpriseObject _eo, int _idx) {
    if (this.delegate == null)
      return true;
    
    return this.delegate.operationWillAddRelationshipsOfEO(this, _eo, _idx);
  }

  public Map<String, Object> willProcessMap(Map<String, Object> _record) {
    if (this.delegate == null)
      return _record;
    
    return this.delegate.operationWillProcessMap(this, _record);
  }
  
  
  /* description */

  @Override
  public void appendAttributesToDescription(final StringBuilder _d) {
    if (this.baseEntity == null)
      _d.append(" no-entity");
    else {
      _d.append(" entity=");
      _d.append(this.baseEntity.name());
    }
    
    if (this.actorId != null) {
      _d.append(" actor=");
      _d.append(this.actorId);
    }

    if (this.now != null) {
      _d.append(" time=");
      _d.append(this.now);
    }
    
    super.appendAttributesToDescription(_d);
  }
  
  
  /* patch object */
  
  static class PostInsertPatch extends NSObject implements IOGoInsertPatch {
    public EORelationship      relationship;
    public IOGoInsertOperation insertOperation;
    public int                 insertOperationIndex;
    
    public PostInsertPatch
      (EORelationship _relship, IOGoInsertOperation _op, int _idx)
    {
      this.relationship         = _relship;
      this.insertOperation      = _op;
      this.insertOperationIndex = _idx;
    }
    
    /**
     * This method loops over the EOJoin's of the EORelationship,
     * retrieves the 'source value' (usually the primary key) from the _eo
     * base object,
     * and applies the value on the child object using the joins target key.
     * 
     * @param _tx - the current operation transaction
     * @param _eo - the base ("master") object which probably got a primary key
     * @return null if everything went fine, an exception otherwise
     */
    public Exception apply(OGoOperationTransaction _tx, EOEnterpriseObject _eo){
      if (this.relationship == null)
        return new NSException("patch misses relationship");
      
      final EOEnterpriseObject eo =
        this.insertOperation.get(this.insertOperationIndex);
      
      for (final EOJoin join: this.relationship.joins()) {
        /* extract source value from EO */
        final Object srcValue = _eo.valueForKey(join.sourceAttribute().name());
        if (srcValue == null)
          return new NSException("could not extract join src value: " + join);
        
        /* extract target key */
        final String destKey = join.destinationAttribute().name();
        if (destKey == null)
          return new NSException("could not extract join dest key: " + join);
        
        /* apply */
        // TBD: maybe it would be better to let the child operation apply a
        //      patch on itself?
        eo.takeValueForKey(srcValue, destKey);
      }
      
      return null; /* we are done */
    }
  }
  
}
