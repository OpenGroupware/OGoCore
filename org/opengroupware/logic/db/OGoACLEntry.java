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

public class OGoACLEntry extends OGoObject {
  
  protected Number objectId;    /* primary key of protected object */
  protected Number principalId; /* primary key of team or account  */
  public    String permissions; /* permissions set */

  public OGoACLEntry(EOEntity _entity) {
    super(_entity);
  }
  
  /* accessors */
  
  public void setObjectId(Number _id) {
    if (this.objectId == _id)
      return;
    
    if (this.objectId != null) {
      log.warn("attempt to reset objectId of ACL entry: " + this);
      return;
    }
    
    this.objectId = _id;
  }
  public Number objectId() {
    return this.objectId;
  }
  
  public void setPrincipalId(Number _id) {
    if (this.principalId == _id)
      return;
    
    if (this.principalId != null) {
      log.warn("attempt to reset principalId of ACL entry: " + this);
      return;
    }
    
    this.principalId = _id;
  }
  public Number principalId() {
    return this.principalId;
  }
}
