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

import org.getobjects.eoaccess.EOEntity;
import org.opengroupware.logic.core.OGoObjectContext;

/**
 * OGoContactRelationshipInsert
 * <p>
 * This OGoOperation performs creates company_assignment objects, those are:
 * <ul>
 *   <li>TeamMemberships      (OGoTeamMembership) [view: team_membership]
 *   <li>Employments          (OGoEmployment)     [view: employment]
 *   <li>CompanyRelationships (OGoCompanyRelationship) [view: company_hierarchy]
 * </ul>
 * 
 * <p>
 * Example (add an new contact):<pre>
 *   OGoContactRelationshipInsert op =
 *     new OGoContactRelationshipInsert(oc, "Employments");
 *   
 *   op.add("companyId", 28373, "personId", 10000, "function", "Ruler");
 *   
 *   Exception error = oc.performOperations(operation);</pre>
 */
public class OGoContactRelationshipInsert extends OGoEOInsertOperation {

  public OGoContactRelationshipInsert(OGoObjectContext _oc, EOEntity _entity) {
    super(_oc, _entity);
  }

  public OGoContactRelationshipInsert(OGoObjectContext _oc, String _ename) {
    super(_oc, _ename);
  }

}
