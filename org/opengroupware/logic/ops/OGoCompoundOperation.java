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
import java.util.Collection;
import java.util.List;

import org.getobjects.eoaccess.EOAdaptorChannel;
import org.getobjects.foundation.NSCompoundException;
import org.opengroupware.logic.core.IOGoOperation;
import org.opengroupware.logic.core.OGoObjectContext;

public abstract class OGoCompoundOperation extends OGoOperation {
  
  protected Collection<IOGoOperation> childOperations;

  public OGoCompoundOperation(final OGoObjectContext _oc) {
    super(_oc);
  }

  /* performing the operation */
  
  public Exception runInContext
    (OGoOperationTransaction _tx, EOAdaptorChannel _ch, OGoObjectContext _ctx)
  {
    Exception error = super.runInContext(_tx, _ch, _ctx);
    if (error != null) return error;

    return this.runChildOpsInContext(_tx, _ch, _ctx);
  }

  public Exception runChildOpsInContext
    (OGoOperationTransaction _tx, EOAdaptorChannel _ch, OGoObjectContext _ctx)
  {
    if (this.childOperations == null)
      return null;
    
    /* we stop at the first error */
    for (IOGoOperation op: this.childOperations) {
      Exception error = op.runInContext(_tx, _ch, _ctx);
      if (error != null) return error;
    }
    return null;
  }
  
  
  /* default implementations */
  
  public Exception prepareForTransactionInContext
    (OGoOperationTransaction _tx, OGoObjectContext _ctx)
  {
    Exception error = super.prepareForTransactionInContext(_tx, _ctx);
    if (error != null) return error;

    return this.prepareChildOpsForTransactionInContext(_tx, _ctx);
  }
  public Exception prepareChildOpsForTransactionInContext
    (OGoOperationTransaction _tx, OGoObjectContext _ctx)
  {
    if (this.childOperations == null)
      return null;
    
    List<Exception> errors = null;
    for (IOGoOperation op: this.childOperations) {
      Exception error = op.prepareForTransactionInContext(_tx, _ctx);
      if (error != null) {
        if (errors == null) errors = new ArrayList<Exception>(6);
        errors.add(error);
      }
    }
    if (errors == null)
      return null;
    
    return NSCompoundException.exceptionForList("prepare ops failed", errors);
  }
  
  public Exception transactionDidBeginInContext
    (OGoOperationTransaction _tx, EOAdaptorChannel _ch, OGoObjectContext _ctx)
  {
    Exception error = super.transactionDidBeginInContext(_tx, _ch, _ctx);
    if (error != null) return error;
    
    if (this.childOperations == null)
      return null;

    List<Exception> errors = null;
    for (IOGoOperation op: this.childOperations) {
      error = op.transactionDidBeginInContext(_tx, _ch, _ctx);
      if (error != null) {
        if (errors == null) errors = new ArrayList<Exception>(6);
        errors.add(error);
      }
    }
    if (errors == null)
      return null;
    
    return NSCompoundException.exceptionForList("didBegin failed", errors);
  }

  public Exception transactionWillRollbackInContext
    (OGoOperationTransaction _tx, EOAdaptorChannel _ch, OGoObjectContext _ctx)
  {
    if (this.childOperations != null) {
      List<Exception> errors = null;
      for (IOGoOperation op: this.childOperations) {
        Exception error = op.transactionWillRollbackInContext(_tx, _ch, _ctx);
        if (error != null) {
          if (errors == null) errors = new ArrayList<Exception>(6);
          errors.add(error);
        }
      }
      if (errors != null) {
        return NSCompoundException
          .exceptionForList("willRollback failed", errors);
      }
    }
    return super.transactionWillRollbackInContext(_tx, _ch, _ctx);
  }
  
  public Exception transactionDidCommitInContext
    (OGoOperationTransaction _tx, OGoObjectContext _ctx)
  {
    if (this.childOperations != null) {
      List<Exception> errors = null;
      for (IOGoOperation op: this.childOperations) {
        Exception error = op.transactionDidCommitInContext(_tx, _ctx);
        if (error != null) {
          if (errors == null) errors = new ArrayList<Exception>(6);
          errors.add(error);
        }
      }
      if (errors != null)
        return NSCompoundException.exceptionForList("didCommit failed", errors);
    }
    return super.transactionDidCommitInContext(_tx, _ctx);
  }
}
