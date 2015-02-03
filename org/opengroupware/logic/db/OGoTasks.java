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
package org.opengroupware.logic.db;

import org.getobjects.eocontrol.EOEditingContext;
import org.getobjects.eocontrol.EOFetchSpecification;
import org.getobjects.eocontrol.EOKeyValueQualifier;
import org.getobjects.eocontrol.EOQualifier;
import org.opengroupware.logic.core.OGoObjectContext;

public class OGoTasks extends OGoCalDataSource {

  public OGoTasks(final EOEditingContext _ec, final String _entityName) {
    super(_ec, _entityName);
  }
  
  public static final EOQualifier pendingTasksQualifier = EOQualifier.parse(
    "status = '00_created' OR status = '20_processing'");
  
  public static final EOQualifier doneTasksQualifier = EOQualifier.parse(
    "status = '25_done' OR "+
    "(status = '30_archived' AND NOT (completionDate = null))");
  
  public static final EOQualifier archivedTasksQualifier =
    new EOKeyValueQualifier("status", "30_archived");
  
  
  /* fetches */

  /**
   * This fetches all OGoTask objects which are connected to some other object
   * using a an OGoObjectLink with key 'InReply'.
   * 
   * @param _id - the pkey of the other object, usually an Integer primary key
   * @return an OGoResultSet containing OGoTask objects
   */
  public OGoResultSet fetchTasksInReplyToId(final Object _id) {
    // TBD: make OGoResultSet a generic, like OGoResultSet<OGoTask>
    EOFetchSpecification fs =
      this.entity().fetchSpecificationNamed("inReplyToId");
    if (fs == null) {
      log().error("did not find 'inReplyToId' fetch specification in entity: " +
          this.entity());
      return null;
    }
    
    OGoObjectContext oc = this.objectContext();
    Number[] authIds = oc != null ? oc.authenticatedIDs() : null;
    fs = fs.fetchSpecificationWithQualifierBindings
      ("authIds", authIds, "id", _id);
    this.setFetchSpecification(fs);
    
    return this.fetchResultSet();
  }
}
