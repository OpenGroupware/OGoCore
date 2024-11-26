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
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.getobjects.eoaccess.EOAdaptorChannel;
import org.getobjects.eoaccess.EOAdaptorOperation;
import org.getobjects.eoaccess.EOEntity;
import org.getobjects.eoaccess.EORawSQLValue;
import org.getobjects.eocontrol.EOGlobalID;
import org.getobjects.eocontrol.EOKeyGlobalID;
import org.getobjects.eocontrol.EOKeyValueQualifier;
import org.getobjects.eocontrol.EOQualifier;
import org.opengroupware.logic.authz.OGoTaskPermissionHandler;
import org.opengroupware.logic.core.OGoObjectContext;
import org.opengroupware.logic.db.OGoTask;

/**
 * Apply a new task status in a bulk way.
 * <p>
 * Possible Status values:
 * <ul>
 *   <li>00_created    (task has been created, but is not yet accepted)
 *   <li>02_rejected   (task has been rejected)
 *   <li>20_processing (task has been accepted)
 *   <li>25_done       (task is done)
 *   <li>30_archived   (task is archived)
 * </ul>
 * <p>
 * How to use:<pre>
 *   OGoTasksStateChange operation = new OGoTasksStateChange(oc);
 *   operation.addTask(taskGlobalID, "20_processing");</pre>
 * 
 * <p>
 * Permissions
 * <ul>
 *   <li>A to change to 20_processing
 *   <li>a to change to 30_archived
 *   <li>w for everything else
 * </ul> 
 * 
 * <p>
 * @see OGoTaskPermissionHandler
 * @see OGoTask
 * 
 * @author helge
 */
public class OGoTasksStateChange extends OGoOperation {
  
  protected EOEntity baseEntity;
  
  protected Map<String, List<EOGlobalID>> stateToTasks;
  protected Date   now;
  protected Number actorId;

  public OGoTasksStateChange(final OGoObjectContext _oc) {
    super(_oc);
    
    this.baseEntity = _oc.oDatabase().entityNamed("Tasks");
  }
  
  /* accessors */
  
  public void addTask(final EOGlobalID _gid, final String _newStatus) {
    if (_gid == null || _newStatus == null)
      return;
    
    if (this.stateToTasks == null)
      this.stateToTasks = new HashMap<String, List<EOGlobalID>>(4);
    
    List<EOGlobalID> gids = this.stateToTasks.get(_newStatus);
    if (gids == null) {
      gids = new ArrayList<EOGlobalID>(16);
      this.stateToTasks.put(_newStatus, gids);
    }
    
    gids.add(_gid);
  }
  public void addTask(final OGoTask _object, final String _newStatus) {
    if (_object != null)
      this.addTask(this.oc.globalIDForObject(_object), _newStatus);
  }
  public void addTask(final Number _pkey, final String _newStatus) {
    if (_pkey == null) {
      this.addTask
        (EOKeyGlobalID.globalIDWithEntityName("Tasks", _pkey), _newStatus);
    }
  }
  
  
  /* prepare */

  @Override
  public Exception prepareForTransactionInContext
    (final OGoOperationTransaction _tx, final OGoObjectContext _oc)
  {
    this.now     = _tx.startDate();
    this.actorId = _oc.actorID();
    
    /* request permissions */
    
    for (String newStatus: this.stateToTasks.keySet()) {
      final List<EOGlobalID> gids = this.stateToTasks.get(newStatus);
      final String requiredPerm;
      
      if (newStatus.equals("20_processing"))
        requiredPerm = "A";
      else if (newStatus.equals("30_archived"))
        requiredPerm = "a";
      else
        requiredPerm = "w";
      
      for (EOGlobalID gid: gids) {
        /* hm, we should differentiate by status?! */
        _tx.requestPermissionOnGlobalID(requiredPerm, gid);
      }
    }
    
    return null /* everything went fine */;
  }
  
  
  /* do it */
  
  @Override
  public Exception runInContext
    (OGoOperationTransaction _tx, EOAdaptorChannel _ch, OGoObjectContext _ctx)
  {
    if (this.stateToTasks == null || this.stateToTasks.size() == 0)
      return null; /* nothing to be done */
    
    List<EOAdaptorOperation> ops    = new ArrayList<EOAdaptorOperation>(16);
    List<EOAdaptorOperation> logOps = new ArrayList<EOAdaptorOperation>(16);
    
    /* collect ops */
    
    for (String newStatus: this.stateToTasks.keySet()) {
      final List<EOGlobalID> gids = this.stateToTasks.get(newStatus);
      if (gids == null || gids.size() == 0)
        continue;
      
      EOQualifier q = new EOKeyValueQualifier(
          "id", EOQualifier.ComparisonOperation.CONTAINS, gids);
      // TBD: extend qualifier?
      // TBD: split up status change depending on previous status!
      //      => done=>processing is different to created=>processing
      //      we can do this w/o fetching
      // Note: we do not check the previous objectVersion?!
      
      final Map<String, Object> values = new HashMap<String, Object>(4);
      values.put("status", newStatus);
      
      values.put("objectVersion", new EORawSQLValue("object_version + 1"));
      values.put("lastModified",  
                 Integer.valueOf((int)(this.now.getTime()/1000)));
      values.put("dbStatus",      "updated");
      
      /* apply status specific changes */
      
      if (newStatus.equals("25_done")) {
        values.put("percentComplete", int100);
        values.put("completionDate",  this.now);
      }
      else if (newStatus.equals("20_processing")) {
        values.put("percentComplete", int0);
        values.put("completionDate",  null);
      }
      else if (newStatus.equals("00_created")) {
        values.put("percentComplete", int0);
        values.put("completionDate",  null);
      }
      
      /* setup adaptor operation and add to queue */
      
      EOAdaptorOperation op = new EOAdaptorOperation(this.baseEntity);
      op.setAdaptorOperator(EOAdaptorOperation.AdaptorUpdateOperator);
      op.setQualifier(q);
      op.setChangedValues(values);
      
      ops.add(op);
    }
    
    ops.addAll(logOps);
    
    /* perform ops */
    
    Exception error = _ch.performAdaptorOperations(ops);
    if (error != null)
      return error;
    
    // TBD: comments/logs?!
    
    return null /* everything is fine */;
  }
  
  private static final Integer int0   = Integer.valueOf(0);
  private static final Integer int100 = Integer.valueOf(100);
}
