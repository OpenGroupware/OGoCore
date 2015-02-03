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


/**
 * IOGoMasterTransactionListener
 * <p>
 * The OGoMasterTransaction maintains a set of listeners which it will notify
 * on commits/rollback.
 * 
 * <p>
 * @author helge
 */
public interface IOGoMasterTransactionListener {

  /**
   * This is called after a transaction was successfully committed to the
   * database (the EOAdaptorChannel.commit() did succeed).
   * 
   * @param _tx - the OGoMasterTransaction
   */
  public void transactionDidCommit(final OGoMasterTransaction _tx);
  
  /**
   * This is called right before a transaction will be rolled back. This allows
   * the listener to restore pre-begin state (which is not tracked in the
   * database, eg created documents BLOBs).
   * <p>
   * Listeners should be prepared to receive this multiple times (ie, remember
   * when they rolled back something and do not do this twice).
   * 
   * @param _tx - the OGoMasterTransaction
   */
  public void transactionWillRollback(final OGoMasterTransaction _tx);
  
}
