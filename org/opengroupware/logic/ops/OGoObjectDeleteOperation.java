/*
  Copyright (C) 2008 Helge Hess

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
import org.getobjects.eoaccess.EOEntity;
import org.getobjects.eocontrol.EOFetchSpecification;
import org.getobjects.eocontrol.EOQualifier;
import org.getobjects.foundation.NSCompoundException;
import org.getobjects.foundation.NSException;
import org.opengroupware.logic.core.OGoObjectContext;
import org.opengroupware.logic.db.OGoObject;

/**
 * OGoObjectDeleteOperation
 * <p>
 * This OGoOperation deletes OGoObject objects.
 * 
 * <p>
 * TBD: implement me! :-)
 * 
 * <p>
 * @author helge
 */
public class OGoObjectDeleteOperation extends OGoOperation {

  protected EOEntity baseEntity; /* eg Persons or Companies */
  
  protected Date   now;
  protected Number actorId;
  
  protected Map<Number, Number> idToBaseVersion;

  public OGoObjectDeleteOperation(OGoObjectContext _oc, final String _ename) {
    super(_oc);
    this.baseEntity = _oc.oDatabase().entityNamed(_ename);
    
    this.idToBaseVersion = new HashMap<Number, Number>(4);
  }
  
  
  /* enqueue update request */

  /**
   * Enqueues the record with the primary key _id for deletion. If a _version is
   * given, it will be validated prior attempting to delete the record.
   * 
   * @param _id      - the primary key of the contact
   * @param _version - the object_version the change relies on, or null
   * @param _c       - the actual changes
   */
  public void deleteObject(final Number _id, final Number _version) {
    if (_id == null) {
      log.warn("got no contact-id for delete?!");
      return;
    }
    
    this.idToBaseVersion.put(_id, _version /* can be null */);
  }
  
  /* EO objects */

  /**
   * Enqueues the object with _eo for deletion. If _force is false, the _version
   * will be validated prior attempting to delete the record.
   * 
   * @param _eo    - the EO object to be deleted
   * @param _force - if true, the version is ignored
   */
  public boolean delete(final OGoObject _eo, final boolean _force) {
    if (_eo == null)
      return false;
    
    if (_eo.isReadOnly()) {
      log.error("attempt to update readonly object: " + _eo);
      return false;
    }
    
    final Number pkey = _eo.id();
    if (pkey == null) {
      log.error("contact has no primary key, cannot add: " + _eo);
      return false;
    }
    
    /* first add EO changes */
    
    // TBD: use attributesUsedForLocking instead?
    this.deleteObject(pkey, _force ? null : _eo.baseVersion());
    
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
  
  
  /* prepare */

  @SuppressWarnings("unchecked")
  @Override
  public Exception prepareForTransactionInContext
    (final OGoOperationTransaction _tx, final OGoObjectContext _ctx)
  {
    if (this.idToBaseVersion == null || this.idToBaseVersion.size() == 0)
      return null; /* nothing to do */
    
    this.now     = _tx.startDate();
    this.actorId = _ctx.actorID();
    
    
    /* fetch EO objects (eg OGoPerson's) which we want to delete */
    
    EOFetchSpecification fs = new EOFetchSpecification(this.baseEntity.name(),
        EOQualifier.parse("id IN %@", this.idToBaseVersion.keySet()),
        null);
    // Note: locking makes no sense, we are not inside a transaction at this
    //       stage. prepare is just for preliminary setup.
    // fs.setLocksObjects(true); /* sure, why not? */
    
    final List<OGoObject> objects =
      _tx.objectContext().objectsWithFetchSpecification(fs);
    if (objects == null) {
      Exception error = _tx.objectContext().consumeLastException();
      return error != null ? error : new NSException("could not fetch objs");
    }
    
    
    /* check whether all objects were found */
    
    if (objects.size() < this.idToBaseVersion.size())
      return new NSException("some objects could not be found"); // TBD
    
    
    /* compare revisions, apply our changes and request permission */
    
    final List<Exception> revErrors = new ArrayList<Exception>(4);
    for (OGoObject eo: objects) {
      Number pkey = eo.id();
      Number base = this.idToBaseVersion.get(pkey);
      if (base == null)
        continue;
      
      Number current = (Number)eo.valueForKey("objectVersion");
      if (current == null) current = 0;
      
      if (!base.equals(current)) {
        log.warn("object was modified: " + eo);
        revErrors.add(new OGoVersionMismatchException(eo, current, base));
      }
      
      /* request delete permission */
      
      _tx.requestPermissionOnId("d", this.baseEntity.name(), pkey);
      
      /* apply core deletes */

    }
    if (revErrors.size() > 0)
      return NSCompoundException.exceptionForList("version mismatch",revErrors);

    
    /* prepare child deletes */
    // TBD
    
    
    return null /* everything is fine */;
  }

  
  /* do it */
  
  @Override
  public Exception runInContext
    (OGoOperationTransaction _tx, EOAdaptorChannel _ch, OGoObjectContext _ctx)
  {
    if (this.idToBaseVersion == null || this.idToBaseVersion.size() == 0)
      return null; /* Nuffin' to do */
    
    
    List<EOAdaptorOperation> ops    = new ArrayList<EOAdaptorOperation>(16);
    List<EOAdaptorOperation> logOps = new ArrayList<EOAdaptorOperation>(16);
    
    /* create delete operations */
    
    // TBD
    
    ops.addAll(logOps);
    
    
    /* perform core operations */
    
    Exception error = _ch.performAdaptorOperations(ops);
    if (error != null)
      return error;

    
    /* perform child changes */
    // TBD
    
    return new NSException("not implemented");
  }
}
