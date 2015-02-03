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
 * OGoObjectComment
 * <p>
 * Represents the comment which is attached to an OGo Object. The comment is
 * often stored separately from the record in the company_info table (mostly
 * historic reasons, it was better to store BLOBs in separate tables in old
 * RDBMS).
 */
public class OGoObjectComment extends OGoObject {
  
  protected Number objectId;
  protected String value;

  public OGoObjectComment(final EOEntity _entity) {
    super(_entity);
  }
  
  /* accessors */
  
  public void setObjectId(final Number _id) {
    if (_id == this.objectId)
      return;
    
    int vi = _id != null ? _id.intValue() : 0;
    if (vi < 1 && (this.objectId != null && this.objectId.intValue() > 0)) {
      log.error("attempt to set invalid company id: " + _id);
      return;
    }
    this.objectId = _id;
  }
  public Number objectId() {
    return this.objectId;
  }
  
  public void setValue(final String _value) {
    this.value = _value != null ? _value.trim() : null;
  }
  public String value() {
    return this.value;
  }
  
  
  /* validation */

  @Override
  public Exception validateForInsert() {
    if (this.objectId == null)
      log.warn("missing objectId in comment:" + this);
    
    return super.validateForInsert();
  }
  
  
  /* values */
  
  /**
   * Note: we return true even if an info is set. This just checks the
   * number value.
   */
  @Override
  public boolean isEmpty() {
    return this.value == null || this.value.trim().length() == 0;
  }
  
  public String contentAsString() {
    return this.value();
  }
  
  /* permissions */
  
  public void applyPermissions(final String _perms) {
    if (_perms.length() == 0) {
      // TBD: use a special 'forbidden' value
      this.value     = null;
      this.objectId = -1;
    }
  }
}
