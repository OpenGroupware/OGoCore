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

import org.getobjects.eoaccess.EOEntity;

/**
 * OGoContactComment
 * <p>
 * Represents the comment which is attached to a contact. The comment is stored
 * separately from the record in the company_info table (mostly historic
 * reasons, it was better to store BLOBs in separate tables in old RDBMS).
 */
public class OGoContactComment extends OGoObjectComment
  implements IOGoContactChildObject
{
  
  public OGoContactComment(final EOEntity _entity) {
    super(_entity);
  }
  
  /* accessors */
  
  public void setCompanyId(final Number _id) {
    this.setObjectId(_id);
  }
  public Number companyId() {
    return this.objectId();
  }
}
