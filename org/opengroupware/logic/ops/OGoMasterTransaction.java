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
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.getobjects.eoaccess.EOAdaptor;
import org.getobjects.eoaccess.EOAdaptorChannel;
import org.getobjects.foundation.NSException;
import org.getobjects.foundation.NSObject;
import org.opengroupware.logic.core.OGoObjectContext;
import org.opengroupware.logic.db.OGoDatabase;

/**
 * This manages a larger transaction on the OGo database.
 * <p>
 * Whats the difference between an OGoOperationTransaction and an
 * OGoMasterTransaction?
 * Basically the master transaction manages the database channel and the
 * database transaction, while
 * the operation transaction is responsible for batching SQL operations
 * (eg permission requests or bulk updates).
 * <p>
 * For many operations you just need to use OGoOperationTransaction which will
 * autocreate a private OGoMasterTransaction on demand.
 * But if you need to run multiple operations which depend on each other, you
 * can setup a wrapping master transaction to collect them in a single
 * database transaction.
 * The usual example is creating complex objects, eg contacts. In this case
 * you first need to create the 'contact' records, programatically retrieve the
 * new contact IDs and then create addresses, phone numbers etc using that ID.
 */
public class OGoMasterTransaction extends NSObject {
  protected static final Log log = LogFactory.getLog("OGoOperationTransaction");
  
  protected OGoObjectContext oc;
  protected OGoDatabase      db;

  protected Date startDate;
  protected Date endDate;
  
  /* tx state */
  protected EOAdaptor        adaptor;
  protected EOAdaptorChannel adChannel; 
  protected List<IOGoMasterTransactionListener> listeners;

  public OGoMasterTransaction(final OGoObjectContext _oc) {
    super();
    
    this.oc  = _oc;
    this.db  = this.oc.oDatabase();
  }

  
  /* accessors */
  
  public OGoObjectContext objectContext() {
    return this.oc;
  }

  public Date startDate() {
    return this.startDate;
  }
  public Date endDate() {
    return this.endDate;
  }
  
  public long duration() {
    if (this.startDate == null)
      return -1;
    
    if (this.endDate == null)
      return -1; // TBD: should return 'now' - startDate?
    
    return this.endDate.getTime() - this.startDate.getTime();
  }
  
  
  public boolean isInTransaction() {
    return this.adChannel != null;
  }
  public EOAdaptor adaptor() {
    return this.adaptor;
  }
  public EOAdaptorChannel adaptorChannel() {
    return this.adChannel;
  }
  
  
  /* begin/end TX */
  
  /**
   * Retrieves a channel from the database' adaptor and starts a transaction
   * using begin().
   * 
   * @return null if everything was awesome-O, an error otherwise
   */
  public Exception begin() {
    if (this.adaptor != null || this.adChannel != null)
      return new NSException("TX already started: " + this.startDate);
    
    this.adaptor = this.db.adaptor();
    if ((this.adChannel = this.adaptor.openChannelFromPool()) == null) {
      this.adaptor = null;
      return new NSException("TX got no channel from adaptor: " + this.adaptor);
    }
    
    this.startDate = new Date();
    this.endDate   = null;
    
    Exception error = this.adChannel.begin();
    if (error != null) {
      this.adaptor.releaseAfterError(this.adChannel, error);
      this.adChannel = null;
      this.adaptor   = null;
      this.startDate = null;
      return error;
    }
    
    return null; /* everything is fine */
  }
  
  /**
   * Important: if the commit fails with an error, you should(/must) call
   * rollback() to properly teardown the transaction (release channels etc).
   * <p>
   * This method only teardowns the transaction if the commit() was successful.
   * 
   * @return null if everything was fine, an error otherwise
   */
  public Exception commit() {
    if (this.adChannel == null)
      return new NSException("master-tx commit: no transaction in progress");
    
    Exception error = this.adChannel.commit();
    if (error != null)
      return error; // Note: the caller MUST call rollback to teardown!
    
    this.endDate = new Date();
    
    /* teardown */
    
    this.adaptor.releaseChannel(this.adChannel);
    this.adChannel = null;
    this.adaptor   = null;
    
    /* notify operations on commit (makes them throw away rollback state) */
    
    if (this.listeners != null) {
      for (IOGoMasterTransactionListener listener: this.listeners)
        listener.transactionDidCommit(this);
      this.listeners = null;
    }
    
    return error;
  }
  
  public Exception rollback() {
    return this.rollback(null /* regular rollback, no previous error */);
  }
  
  public Exception rollback(final Exception _causingError) {
    if (this.adChannel == null)
      return new NSException("master-tx rollback: no transaction in progress");
    
    /* finalize embedded operations */
    
    if (this.listeners != null) {
      for (final IOGoMasterTransactionListener listener: this.listeners)
        listener.transactionWillRollback(this);
      this.listeners = null;
    }
    
    /* perform rollback */
    
    Exception error = this.adChannel.rollback();
    
    this.endDate = new Date();
    
    /* teardown */
    
    if (_causingError != null)
      this.adaptor.releaseAfterError(this.adChannel, _causingError);
    else
      this.adaptor.releaseChannel(this.adChannel);
    
    this.adChannel = null;
    this.adaptor   = null;
    
    return error;
  }
  
  public void addListener(final IOGoMasterTransactionListener _listener) {
    if (_listener == null)
      return;
    
    if (this.listeners == null)
      this.listeners = new ArrayList<IOGoMasterTransactionListener>(16);
    
    this.listeners.add(_listener);
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
    
    if (this.startDate != null) {
      if (this.endDate != null) {
        _d.append(" time=");
        _d.append(this.duration());
        _d.append("ms");
      }
      else {
        _d.append(" started=");
        _d.append(this.startDate);
      }
    }
    
    if (this.adChannel != null) {
      _d.append(" ch=");
      _d.append(this.adChannel);
    }
  }
}
