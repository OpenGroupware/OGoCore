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

public class OGoProjects extends OGoDataSource {

  public OGoProjects(EOEditingContext _ec, String _entityName) {
    super(_ec, _entityName);
  }
  
  /* fetches */

  public OGoResultSet fetchResultSet
    (String _fsname, int _limit, Object _contactId)
  {
    if (_fsname == null) _fsname = "default";

    EOFetchSpecification fs = 
      this.entity().fetchSpecificationNamed(_fsname).copy();
    fs.setFetchLimit(_limit);
    
    Number[] authIds = this.objectContext().authenticatedIDs();
    this.setQualifierBindings("authIds", authIds, "contactId", _contactId);
    
    this.setFetchSpecification(fs);
    
    return this.fetchResultSet();
  }
}
