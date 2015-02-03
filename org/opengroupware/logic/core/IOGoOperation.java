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
package org.opengroupware.logic.core;

import org.getobjects.eoaccess.EOAdaptorChannel;
import org.getobjects.foundation.NSKeyValueCoding;
import org.getobjects.foundation.NSKeyValueCodingAdditions;
import org.opengroupware.logic.ops.OGoOperationTransaction;

/**
 * An OGo operation object. Operations usually performs changes in the database,
 * eg update the address of a contact record or add a new event.
 * <p>
 * They roughly compare to the Logic command objects in Objective-C OGo.
 * <p>
 * To execute operations, you would construct them and then call the
 * performOperations()/performOperationsArgs() method of the OGoObjectContext.
 * The OGoObjectContext will then instantiate an OGoOperationTransaction
 * with the ops and call run() on it. 
 * 
 * @author helge
 */
public interface IOGoOperation
  extends NSKeyValueCodingAdditions, NSKeyValueCoding
{

  /**
   * This is the primary function which executes the operation in the database.
   * Do as little as possible in this method, prepare as much as you can in
   *   <code>prepareForTransactionInContext()</code>
   * to keep the transaction short.
   * 
   * @param _tx  - the OGoOperationTransaction
   * @param _ch  - the open EOAdaptorChannel for you convenience
   * @param _ctx - the OGoObjectContext
   * @return null if everything went fine, an Exception to abort the TX
   */
  public Exception runInContext
    (OGoOperationTransaction _tx, EOAdaptorChannel _ch, OGoObjectContext _ctx);

  
  /**
   * This is the first method which gets called before the database update
   * transaction is started. The operation can ensure its parameters make
   * sense and, more importantly, request the OGoOperationTransaction to
   * ensure proper permissions.
   * The latter can be done by calling
   *   <code>OGoOperationTransaction.requestPermissionOnId()</code>
   * or
   *   <code>OGoOperationTransaction.requestPermissionOnGlobalID()</code>
   * 
   * @param _tx  - the OGoObjectTransaction
   * @param _ctx - the OGoObjectContext
   * @return null if everything went fine, an Exception to abort the TX
   */
  public Exception prepareForTransactionInContext
    (OGoOperationTransaction _tx, OGoObjectContext _ctx);
  
  
  /**
   * This method is called after the OGoObjectTransaction openend the database
   * channel and started a database transaction (BEGIN TRANSACTION).
   * It can be used to lock objects from inside the operation, or other stuff
   * which requires a transaction.
   * Note that preparations which do NOT require a transaction should be done
   * in prepareForTransactionInContext() to keep the database transaction as
   * short as possible.
   * 
   * @param _tx  - the OGoObjectTransaction
   * @param _ctx - the OGoObjectContext
   * @return null if everything went fine, an Exception to abort the TX
   */
  public Exception transactionDidBeginInContext
    (OGoOperationTransaction _tx, EOAdaptorChannel _ch, OGoObjectContext _ctx);
  
  /**
   * Called after all operations registered with the OGoObjectTransaction got
   * executed successfully.
   * This can be used to post change notifications or cleanup used temporary
   * objects (eg temporary files).
   * 
   * @param _tx  - the OGoObjectTransaction
   * @param _ctx - the OGoObjectContext
   * @return null if everything went fine, an Exception to abort the TX
   */
  public Exception transactionDidCommitInContext
    (OGoOperationTransaction _tx, OGoObjectContext _ctx);
  
  /**
   * Called when one of the operations registered with the OGoObjectTransaction
   * failed.
   * Should be used to cleanup temporary state, and do external rollback
   * necessary (eg restore delete filesystem files). (the database SQL rollback
   * will be done by the OGoObjectTransaction)
   * 
   * @param _tx  - the OGoObjectTransaction
   * @param _ctx - the OGoObjectContext
   * @return null if everything went fine, an Exception to abort the TX
   */
  public Exception transactionWillRollbackInContext
    (OGoOperationTransaction _tx, EOAdaptorChannel _ch, OGoObjectContext _ctx);
}
