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

import java.util.List;
import java.util.Map;

import org.opengroupware.logic.core.OGoObjectContext;
import org.opengroupware.logic.db.OGoObject;

/**
 * OGoContactExtValueChange
 * <p>
 * An OGoContactChildChange object which can be used to create, update and
 * delete 'extended attributes' attached to a contact (eg person or company).
 * 
 * <p>
 * Example (add an email address to contact with id 10000):<pre>
 *   OGoContactChange operation = new OGoContactChange(oc, "Persons");
 *   operation.addChanges(10000, null, null); // no direct changes
 *   
 *   operation.addChildChange(OGoContactExtValueChange.changePerson(oc, 10000,
 *     null, // no attrs to update (pkey to values)
 *     null, // no attrs to delete (would be an array of pkeys)
 *     // FIXME: must be an array!
 *     UMap.create(
 *       "key",      "VIP",
 *       "value",    "yes",
 *       "category", "CustomerInfo",
 *       "type",     1, // 1=string, 3=email, ...
 *       "isEnum",   0
 *   ));
 *   
 *   Exception error = oc.performOperations(operation);</pre>
 * 
 * It looks a bit difficult, but the setup is required to efficiently perform
 * bulk updates.
*
 * @author helge
 */
public class OGoContactExtValueChange extends OGoContactChildChange {
  // TBD: complete me

  /**
   * Factory method to create an OGoContactExtValueChange for the given
   * parameters.
   * 
   * @param _oc        - OGoObjectContext with login user
   * @param _contactId - pkey of contact to change
   * @param _update    - emails to update (pkey=>change-dict)
   * @param _delete    - emails to delete
   * @param _insert    - emails to add
   * @return an OGoEMailAddressChange, or null if no operation is necessary
   */
  public static OGoContactExtValueChange changePerson(
      final OGoObjectContext _oc, final Number _contactId,
      final Map<Number, Map<String, Object>> _update,
      final List<Number> _delete,
      final List<Map<String, Object>> _insert)
  {
    if (_contactId == null) {
      log.warn("got no contact id!");
      return null;
    }
    if ((_update == null || _update.size() == 0) &&
        (_delete == null || _delete.size() == 0) &&
        (_insert == null || _insert.size() == 0))
      return null; /* nothing to be done */
    
    final OGoContactExtValueChange cc =
      new OGoContactExtValueChange(_oc, "Persons");
    cc.addChangeSet(_contactId, _update, _delete, _insert);
    
    childLog.debug("New OGoContactExtValueChange: " + cc);
    return cc;
  }
  
  /**
   * Factory method to create an OGoContactExtValueChange for the given
   * parameters.
   * 
   * @param _oc        - OGoObjectContext with login user
   * @param _contactId - pkey of contact to change
   * @param _update    - emails to update (pkey=>change-dict)
   * @param _delete    - emails to delete
   * @param _insert    - emails to add
   * @return an OGoEMailAddressChange, or null if no operation is necessary
   */
  public static OGoContactExtValueChange changeCompany(
      final OGoObjectContext _oc, final Number _contactId,
      final Map<Number, Map<String, Object>> _update,
      final List<Number> _delete,
      final List<Map<String, Object>> _insert)
  {
    if (_contactId == null) {
      log.warn("got no contact id!");
      return null;
    }
    if ((_update == null || _update.size() == 0) &&
        (_delete == null || _delete.size() == 0) &&
        (_insert == null || _insert.size() == 0))
      return null; /* nothing to be done */
    
    final OGoContactExtValueChange cc =
      new OGoContactExtValueChange(_oc, "Companies");
    cc.addChangeSet(_contactId, _update, _delete, _insert);
    return cc;
  }
  
  public OGoContactExtValueChange
    (final OGoObjectContext _oc, final String _parent)
  {
    super(_oc, lookupRelshipEntityName(_oc, _parent, "extraValues"), _parent);
  }
  
  public boolean addChangeSet(final OGoObject _eo) {
    return this.addChangeSet(_eo, "extraValues");
  }
}
