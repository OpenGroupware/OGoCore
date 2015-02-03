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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.getobjects.eoaccess.EOAdaptorChannel;
import org.getobjects.eocontrol.EOGlobalID;
import org.getobjects.eocontrol.EOKeyGlobalID;
import org.getobjects.foundation.NSCompoundException;
import org.getobjects.foundation.NSException;
import org.getobjects.foundation.NSObject;
import org.getobjects.foundation.UString;
import org.opengroupware.logic.authz.OGoAccessDeniedException;
import org.opengroupware.logic.core.IOGoOperation;
import org.opengroupware.logic.core.OGoObjectContext;
import org.opengroupware.logic.db.OGoDatabase;

/**
 * This applies operations to the OGo database.
 * <p>
 * The main functionality of this object, besides grouping operations in a
 * database transaction,
 * is to collect requests for permission checks and requests for locks. Which
 * are then applied in a bulk way (read: efficiently).
 */
public class OGoOperationTransaction extends NSObject
  implements IOGoMasterTransactionListener
{
  // TBD: document more
  protected static final Log log = LogFactory.getLog("OGoOperationTransaction");
  
  protected OGoMasterTransaction tx;
  protected OGoObjectContext oc;
  protected OGoDatabase      db;
  protected IOGoOperation[]  ops;
  
  protected Date startDate; /* should be used for creation dates and such */
  protected Date endDate;
  
  /* processing state */
  protected Map<EOGlobalID, String> requestedPermissions;
  protected Map<String, Number>     requestedLocks;
  
  public OGoOperationTransaction(OGoObjectContext _oc, IOGoOperation[] _ops) {
    this(new OGoMasterTransaction(_oc), _ops);
  }
  public OGoOperationTransaction
    (final OGoMasterTransaction _tx, final IOGoOperation[] _ops)
  {
    super();

    this.tx  = _tx;
    this.oc  = _tx != null ? _tx.objectContext() : null;
    this.ops = _ops;
    this.db  = this.oc != null ? this.oc.oDatabase() : null;
    
    this.requestedPermissions = new HashMap<EOGlobalID, String>(16);
    this.requestedLocks       = new HashMap<String, Number>(16);
  }
  
  /* accessors */
  
  public OGoObjectContext objectContext() {
    return this.oc;
  }
  
  public IOGoOperation[] operations() {
    return this.ops;
  }
  
  public Date startDate() {
    return this.startDate;
  }
  public Date endDate() {
    return this.endDate;
  }
  public long duration() {
    return this.endDate != null
      ? (this.endDate.getTime() - this.startDate.getTime())
      : -1;
  }
  
  
  /* performing the operations */
  
  /**
   * Execute the IOGoOperation's attached to this object.
   * 
   * This first calls
   *   <code>prepareOperations()</code>
   * to prepare the operation objects for
   * running and to determine the required permissions.
   * It then runs fetchAndCheckPermissions to ensure that the current user
   * (LoginContext attached to the OGoObjectContext) is allowed to execute the
   * requested permissions.
   * <p>
   * Next an adaptor channel is opened and a database transaction is started,
   * the operations will receive a
   *   <code>transactionDidBeginInContext()</code>,
   * then a
   *   <code>runInContext()</code>
   * and finally a
   *   <code>transactionDidCommitInContext()</code>
   * or
   *   <code>transactionWillRollbackInContext()</code.
   * 
   * @return null on success or the Exception on error
   */
  public Exception run() {
    this.startDate = new Date();
    
    if (this.ops == null || this.ops.length == 0)
      return null; /* nothing to be done */
    
    Exception error = null;
    
    /* let the operations prepare for the transaction and request permissions */
    
    if ((error = this.prepareOperations(this.ops)) != null)
      return error;
    
    /* process permission requests */
    
    if ((error = this.fetchAndCheckPermissions()) != null)
      return error;
    
    
    /* open transaction when necessary */
    
    boolean didBegin = false;
    if (!this.tx.isInTransaction()) {
      error = this.tx.begin();
      if (error != null)
        return error;
      didBegin = true;
    }
    tx.addListener(this);
    
    final EOAdaptorChannel ac = this.tx.adaptorChannel();
    if (ac == null) { // cannot happen
      this.tx.rollback();
      return new NSException("could not open database connection");
    }
    
    try {
      /* let operations know that the SQL transaction started */

      if (error == null) {
        for (IOGoOperation op: this.ops) {
          if (op == null) continue;
          error = op.transactionDidBeginInContext(this, ac, this.oc);
          if (error != null) break;
        }
      }

      /* perform operations */

      if (error == null) {
        for (IOGoOperation op: this.ops) {
          if (op == null) continue;
          error = op.runInContext(this, ac, this.oc);
          if (error != null) break;
        }
      }

      /* commit on no-errors */

      if (error == null) {
        /* no error so far, try to commit */

        if (didBegin) {
          if ((error = this.tx.commit()) != null) {
            /* failed to commit */
            log.error("tx commit failed: " + this, error);
          }
        }
      }
      
      /* catch failed ops or failed commit */

      if (error != null && didBegin) {
        Exception rerror = this.tx.rollback();
        if (rerror != null)
          log.warn("database rollback failed", rerror);
      }
    }
    finally {
      /* this only kicks in on unexpected exceptions */
      if (this.tx.isInTransaction() && didBegin) {
        Exception rerror = this.tx.rollback(error);
        if (error == null)
          error = rerror;
        else
          log.warn("database rollback failed", rerror);
      }
    }
    
    
    /* after commits, notify the operations */
    
    if (didBegin && this.oc != null) {
      /* our operations modify the context, so we need to clean it */
      this.oc.reset();
    }
    
    this.endDate = new Date();
    return error;
  }
  
  
  /* IOGoMasterTransactionListener */
  
  public void transactionDidCommit(final OGoMasterTransaction _tx) {
    if (this.ops != null) {
      for (IOGoOperation op: this.ops) {
        if (op == null) continue;
        op.transactionDidCommitInContext(this, this.oc);
      }
    }
  }
  
  public void transactionWillRollback(final OGoMasterTransaction _tx) {
    if (this.ops != null) {
      EOAdaptorChannel ac = _tx != null ? tx.adaptorChannel() : null;
      for (IOGoOperation op: this.ops) {
        if (op == null) continue;
        op.transactionWillRollbackInContext(this, ac, this.oc);
      }
    }
  }
  
  
  
  /**
   * Let the operations prepare for the transactions. This usually makes them
   * register permission requests.
   * Note that we continue on errors so that we have an exhaustive list of
   * preconditions.
   * <p>
   * This method also evaluates permissions which are already cached in the
   * context.
   * 
   * @return an Exception on error, null if everything was awesome 
   */
  public Exception prepareOperations(final IOGoOperation[] _ops) {
    List<Exception> errors = null;
    
    for (IOGoOperation op: _ops) {
      if (op == null)
        continue;
      
      Exception error = op.prepareForTransactionInContext(this, this.oc);
      if (error != null) {
        if (errors == null) errors = new ArrayList<Exception>(4);
        errors.add(error);
      }
    }
    
    /* Check for cached permissions. We do this here because we want to
     * collect as many exceptions as possible w/o doing actual IO */
    
    if (this.oc != null && this.requestedPermissions.size() > 0) {
      Set<EOGlobalID> gids = /* copy keys so that we can modify the Map */
        new HashSet<EOGlobalID>(this.requestedPermissions.keySet());
      
      for (EOGlobalID gid: gids) {
        String perm = this.oc.permissionsForGlobalID(gid);
        if (perm == null)
          continue; /* need to fetch that later */
        
        /* OK, we have cached a permission for that GID, compare :-) */
        String requestedPerm = this.requestedPermissions.remove(gid);

        /* Remove all avail perms from the requested ones, so the remaining
         * set represents the set of missing permissions.
         * 
         * TBD: we do not associate permissions with operations/objects. So we
         * do not really know which object misses the permission? Which is
         * irrelevant for the transaction, since its performed completely or not
         * at all, but might be nice for error reporting.
         * 
         * TBD: we might also want to coalesce all missing permissions.
         */
        String missingPerm = UString.exceptCharacterSets(requestedPerm, perm);
        
        if (missingPerm != null && missingPerm.length() > 0) {
          /* some permissions are missing ... */
          Exception error = new OGoAccessDeniedException
            (this.oc.loginContext(), gid, requestedPerm, perm);
          
          if (errors == null) errors = new ArrayList<Exception>(4);
          errors.add(error);
        }
      }
    }
    
    /* OK, check whether we collected errors and return them */
    return NSCompoundException.exceptionForList
      ("errors during preparation of transaction", errors);
  }
  
  
  /**
   * This methods ensures that the permissions requested by the operations are
   * met by the current user.
   * <p>
   * To do so, it fetches the login-user's permissions on the global-ids using
   * OGoObjectContext.fetchPermissionsForGlobalIDs(). It then walks over each
   * GID and retrieves the permission from the OGoObjectContext. If it matches
   * the requested one, everything is fine, otherwise an
   * OGoAccessDeniedException is constructed.
   * <p>
   * On error the method returns either a single OGoAccessDeniedException or a
   * NSCompoundException if there was more than one error.
   * Note that we do not stop checking permissions on the first fail, hence the
   * calling code will usually see all issues (and can report them).
   * 
   * @return null if all permissions are available, an Exception otherwise
   */
  public Exception fetchAndCheckPermissions() {
    if (this.requestedPermissions==null || this.requestedPermissions.size()==0)
      return null;
    
    /* fetch permissions */
    // Note: those are not applied on objects
    
    final EOGlobalID[] gids =
      this.requestedPermissions.keySet().toArray(new EOGlobalID[0]);  
    oc.fetchPermissionsForGlobalIDs(gids);
    
    /* check permissions */
    
    List<Exception> errors = null;
    for (EOGlobalID gid: gids) {
      final String perm = this.oc.permissionsForGlobalID(gid);
      if (perm == null)
        log.warn("got no permissions for GID: " + gid);
      
      /* OK, we have cached a permission for that GID, compare :-) */
      final String requestedPerm = this.requestedPermissions.remove(gid);
      final String missingPerm   =
        UString.exceptCharacterSets(requestedPerm, perm);
      
      if (missingPerm != null && missingPerm.length() > 0) {
        /* some permissions are missing ... */
        final Exception error = new OGoAccessDeniedException
          (this.oc.loginContext(), gid, requestedPerm, perm);
        
        if (errors == null) errors = new ArrayList<Exception>(4);
        errors.add(error);
      }
    }
    
    /* OK, check whether we collected errors and return them */
    return NSCompoundException.exceptionForList
      ("insufficient permissions to run transaction", errors);
  }
  
  
  /**
   * Acquire locks requested by the operations using the
   * <code>requestLockOnGlobalID()</code>
   * method.
   * <p>
   * Note: currently not implemented.
   * 
   * @return null if everything went fine, the error otherwise
   */
  public Exception acquireLocks() {
    if (this.requestedLocks == null || this.requestedLocks.size() == 0)
      return null; /* nothing to be done */
    
    // TBD: explicit locking not implemented yet
    return null;
  }
  
  
  /* requests */
  
  /**
   * Usually called by IOGoOperation objects to ensure that the current user
   * of the OGoObjectContext has the given permission on the given global-id.
   * 
   * @param _perm - necessary permission set, eg 'e' or 'de'
   * @param _gid  - global-id of the object
   */
  public void requestPermissionOnGlobalID
    (final String _perm, final EOGlobalID _gid)
  {
    if (_gid == null || _perm == null || _perm.length() == 0)
      return; /* nothing to be done */
    
    String perm = this.requestedPermissions.get(_gid);
    this.requestedPermissions.put(_gid, UString.unionCharacterSets(perm,_perm));
  }

  /**
   * Usually called by IOGoOperation objects to ensure that the current user
   * of the OGoObjectContext has the given permission on the given global-id.
   * 
   * @param _perm   - necessary permission set, eg 'e' or 'de'
   * @param _entity - entity of object
   * @param _id     - primary key of the object
   */
  public void requestPermissionOnId
    (final String _perm, final String _entity, final Number _id)
  {
    if (_entity == null || _id == null || _perm == null || _perm.length() == 0)
      return; /* nothing to be done */
    
    final EOKeyGlobalID kgid =
      EOKeyGlobalID.globalIDWithEntityName(_entity, _id);
    this.requestPermissionOnGlobalID(_perm, kgid);
  }
  
  /**
   * Usually called by IOGoOperation objects to request an explicit database
   * LOCK on the given object. The LOCK will be valid for the transaction.
   * 
   * @param _perm - necessary permission set, eg 'e' or 'de'
   * @param _gid  - global-id of the object
   */
  public void requestLockOnGlobalID(final EOKeyGlobalID _gid) {
    if (_gid == null)
      return;
    
    // TBD: improve error handling for invalid input GIDs
    this.requestedLocks.put(_gid.entityName(), _gid.toNumber());
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
