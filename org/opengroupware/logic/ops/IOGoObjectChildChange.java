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
package org.opengroupware.logic.ops;

import org.opengroupware.logic.core.IOGoOperation;
import org.opengroupware.logic.db.OGoObject;

/**
 * IOGoObjectChildChange
 * <p>
 * An operation which manages updates/inserts/deletes to OGoContact owned
 * relationship tables, eg
 * <ul>
 *   <li>OGoContactChildChange
 *     <ul>
 *       <li>OGoAddressChange
 *       <li>OGoPhoneNumberChange
 *       <li>OGoEMailAddressChange
 *     </ul>
 *   </li>
 *   <li>OGoContactCommentChange
 * </ul>
 * 
 * @author helge
 */
public interface IOGoObjectChildChange extends IOGoOperation {

  /**
   * This is called by an OGoObjectChange operation in its
   *   <code>prepareForTransactionInContext()</code>
   * on all operations which are added to the contact-change as 'child changes'.
   * If it returns true, the OGoContactChange will bump the object_version (and
   * other versioning fields) of the contact record.
   * <p>
   * Remember that a single IOGoObjectChildChange operation can contain
   * changes which affect multiple records. 
   * 
   * @param _contact
   * @return true if the object has changed for the given master
   */
  public boolean hasChangesForMasterObject(final OGoObject _master);
  
}
