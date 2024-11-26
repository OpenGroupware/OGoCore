/*
  Copyright (C) 2007-2024 Helge Hess

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.getobjects.eoaccess.EOAdaptorChannel;
import org.getobjects.eoaccess.EOAdaptorOperation;
import org.getobjects.eoaccess.EOEntity;
import org.getobjects.eoaccess.EORawSQLValue;
import org.getobjects.eocontrol.EOAndQualifier;
import org.getobjects.eocontrol.EOKey;
import org.getobjects.eocontrol.EOKeyValueQualifier;
import org.getobjects.eocontrol.EOQualifier;
import org.getobjects.eocontrol.EOQualifier.ComparisonOperation;
import org.getobjects.foundation.NSException;
import org.getobjects.foundation.NSObject;
import org.opengroupware.logic.core.IOGoOperation;
import org.opengroupware.logic.core.OGoObjectContext;
import org.opengroupware.logic.db.IOGoObject;

/**
 * OGoOperation
 * <p>
 * Represents an operation to be perform in the OGo database, that is creations,
 * updates, deletes, etc.
 * <p>
 * In OGo such operations usually require additional things to be done, eg if
 * an object is changed, we usually want to track the change in the Log table.
 * Or if an object is deleted, we probably want to delete depending objects
 * (or even reject the delete if it still has connected objects).
 * <p>
 * Also check IOGoOperation for details.
 * Note that operations are usually run inside an OGoOperationTransaction,
 * which maintains the SQL transaction and aggregates permission fetches.
 * 
 * @author helge
 */
public abstract class OGoOperation extends NSObject implements IOGoOperation {
  protected static final Log log = LogFactory.getLog("OGoOperation");
  protected static final Log childLog = LogFactory.getLog("OGoChildOperations");
  
  protected OGoObjectContext oc; // TBD: do we actually need this???
  
  public OGoOperation(final OGoObjectContext _oc) {
    super();
    this.oc = _oc;
  }
  
  
  /* top-level invocation (do not use with multiple ops!!!) */
  
  /**
   * This runs _one_ operation (plus its children) on the OGoObjectContext
   * assigned to this op.
   * <p>
   * Its just a convenience method to invoke a single operation,
   * if you want to perform a set of operations, use
   * OGoObjectContext.performOperations() instead!
   * 
   * @return null if the operation was performed fine, the error otherwise
   */
  public Exception perform() {
    if (this.oc == null)
      return new NSException("OGoOperation has no OGoObjectContext assigned!");
    return this.oc.performOperations(this);
  }
  
  
  /* performing the operation */
  
  public Exception runInContext
    (OGoOperationTransaction _tx, EOAdaptorChannel _ch, OGoObjectContext _ctx)
  {
    // docs in IOGoOperation
    return null;
  }
  
  
  /* default implementations (see IOGoOperation for docs) */
  
  public Exception prepareForTransactionInContext
    (final OGoOperationTransaction _tx, final OGoObjectContext _ctx)
  {
    /* Per default: nothing to be done. Subclasses probably want to request
     * permissions and such.
     */
    return null;
  }
  
  public Exception transactionDidBeginInContext
    (OGoOperationTransaction _tx, EOAdaptorChannel _ch, OGoObjectContext _ctx)
  {
    // docs in IOGoOperation
    return null;
  }

  public Exception transactionWillRollbackInContext
    (OGoOperationTransaction _tx, EOAdaptorChannel _ch, OGoObjectContext _ctx)
  {
    // docs in IOGoOperation
    return null;
  }
  
  public Exception transactionDidCommitInContext
    (final OGoOperationTransaction _tx, final OGoObjectContext _ctx)
  {
    // docs in IOGoOperation
    return null;
  }
  
  
  /* helper */

  /**
   * Returns the changes for an update operation. Note that this always fills
   * in objectVersion and dbStatus because the object might have changed due
   * to a related object (and only tracking attributes need to be updated).
   * 
   * @param _object - object to be checked for changes
   * @param _e      - entity of the object
   * @return a Map containing the changes for an AdaptorUpdateOperator
   */
  public Map<String, Object> changesForObject(IOGoObject _object, EOEntity _e) {
    Map<String, Object> values = _object.snapshot();
    //System.err.println("VALUES: " + values);
    
    values = _object.changesFromSnapshot(values);
    //System.err.println("CHANGES: " + values);
    
    if (values == null) values = new HashMap<String, Object>(2);
    
    final EOEntity entity = _e != null ? _e : _object.entity();
    if (entity != null) {
      if (entity.attributeNamed("objectVersion") != null)
        values.put("objectVersion", new EORawSQLValue("object_version + 1"));
      if (entity.attributeNamed("dbStatus") != null)
        values.put("dbStatus", "updated");
    }
    
    return values;
  }
  
  /**
   * Returns a properly filled EOAdaptorOperation. The qualifier is set to the
   * object, and checks the objectVersion if this is requested.
   * 
   * @param _object       - the object to update
   * @param _entity       - the entity of the object
   * @param _baseRevision - the revision the change is based on, or null 
   * @return
   */
  public EOAdaptorOperation updateOperation
    (final IOGoObject _object, final EOEntity _entity, Number _baseRevision)
  {
    if (_object == null)
      return null;
    
    EOQualifier q = new EOKeyValueQualifier("id", _object.id());
    if (_baseRevision != null) {
      q = new EOAndQualifier
        (q, new EOKeyValueQualifier("objectVersion", _baseRevision));
    }
    
    Map<String, Object> values = this.changesForObject(_object, _entity);
    
    /* setup adaptor operation and add to queue */
    
    EOAdaptorOperation op = new EOAdaptorOperation(_entity);
    op.setAdaptorOperator(EOAdaptorOperation.AdaptorUpdateOperator);
    op.setQualifier(q);
    op.setChangedValues(values);
    
    return op;
  }
  
  /**
   * Create an insert operation for the given (new) object. This retrieves a
   * new primary key and fills the tracking attributes.
   * 
   * @param _object - some OGoObject which is going to be inserted into the DB
   * @param _e - the entity of the object
   * @return an EOAdaptorOperation representing the change, or null on error
   */
  public EOAdaptorOperation insertOperation(IOGoObject _object, EOEntity _e) {
    if (_object == null)
      return null;
    
    /* retrieve a primary key for the new object */
    
    Number newId = this.oc.oDatabase().nextPrimaryKey();
    if (newId == null) {
      log.error("could not retrieve new object id!");
      return null;
    }
    _object.setId(newId);
    
    /* extract values to insert */

    EOEntity entity = _e != null ? _e : _object.entity();
    if (entity == null) {
      log.error("got no entity for object insert: " + _object);
      return null;
    }

    Map<String, Object> values = _object.valuesForKeys(_e.classPropertyNames());
    if (values == null) values = new HashMap<String, Object>(2);

    if (entity.attributeNamed("objectVersion") != null)
      values.put("objectVersion", Integer.valueOf(1));
    if (entity.attributeNamed("dbStatus") != null)
      values.put("dbStatus", "inserted");
    
    /* setup adaptor operation and add to queue */

    EOAdaptorOperation op = new EOAdaptorOperation(_e);
    op.setAdaptorOperator(EOAdaptorOperation.AdaptorInsertOperator);
    op.setChangedValues(values);
    
    return op;
  }
  
  private static EOKey<Number> idKey = new EOKey<Number>("id");
  
  /**
   * Creates a list of delete-EOAdaptorOperations for the given set of ids.
   * 
   * @param _entity     - the entity of the objects to delete
   * @param _deletedIds - the primary keys of the objects to delete
   * @return null or the set of ops to delete the objects
   */
  public List<EOAdaptorOperation> deleteOperations
    (final EOEntity _entity, final Collection<Number> _deletedIds)
  {
    if (_deletedIds == null || _deletedIds.size() == 0)
      return null;
    
    List<EOAdaptorOperation> ops;
    if (false /* delete using IN */) {
      /* this raises an error because the affected rows are considered */
      EOAdaptorOperation op = new EOAdaptorOperation(_entity);
      op.setAdaptorOperator(EOAdaptorOperation.AdaptorDeleteOperator);
      op.setQualifier(new EOKeyValueQualifier(idKey,
          ComparisonOperation.CONTAINS, /* id IN deletedIds */
          _deletedIds));
      
      ops = new ArrayList<EOAdaptorOperation>(1);
      ops.add(op);
    }
    else { /* delete each entry separately */
      ops = new ArrayList<EOAdaptorOperation>(_deletedIds.size());
      for (Number deletedId: _deletedIds) {
        EOAdaptorOperation op = new EOAdaptorOperation(_entity);
        op.setAdaptorOperator(EOAdaptorOperation.AdaptorDeleteOperator);
        op.setQualifier(new EOKeyValueQualifier("id" , deletedId));
        ops.add(op);
      }
    }
    return ops;
  }
  
  
  /* description */

  @Override
  public void appendAttributesToDescription(final StringBuilder _d) {
    super.appendAttributesToDescription(_d);
    
    if (this.oc == null)
      _d.append(" no-oc");
    else {
      _d.append(" oc=");
      _d.append(this.oc);
    }
  }
}
