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

import org.getobjects.eoaccess.EOEnterpriseObject;
import org.opengroupware.logic.core.OGoObjectContext;

/**
 * OGoProjectInsert
 * <p>
 * Creates a new project record.
 * 
 * <p>
 * Project Properties:
 * <ul>
 *   <li>id
 *   <li>objectVersion
 *   <li>code
 *   <li>ownerId
 *   <li>teamId
 *   <li>name
 *   <li>startDate
 *   <li>endDate
 *   <li>status (00_sleeping, 05_processing, 10_out_of_date, 30_archived)
 *   <li>type (col: kind)  (OGoSx: 00_invoiceProject, 05_historyProject)
 *   <li>isCompanyProject [is_fake]
 *   <li>blobStorageUrl
 * </ul>
 * 
 * <p>
 * Relationships:
 * <ul>
 *   <li>notes
 *   <li>documents
 *   <li>tasks
 *   <li>owner
 *   <li>team
 *   <li>teams     (ProjectTeams)
 *   <li>persons   (ProjectPersons [hasAccess,permissions,companyId,info])
 *   <li>companies (ProjectCompanies)
 * </ul>
 *
 * <p>
 * @author helge
 */
public class OGoProjectInsert extends OGoEOInsertOperation {

  public OGoProjectInsert(final OGoObjectContext _oc) {
    super(_oc, _oc.database().entityNamed("Projects"));
  }

  // TBD: autocreate the required document hierarchy
  
  @Override
  public Exception fixupObjectWithPrimaryKey
    (final EOEnterpriseObject _eo,
     final OGoOperationTransaction _tx, final OGoObjectContext _ctx)
  {
    Object n = _eo.valueForKey("number");
    if (n == null)
      _eo.takeValueForKey("OGo" + _eo.valueForKey("id"), "number");
    return null;
  }
}
