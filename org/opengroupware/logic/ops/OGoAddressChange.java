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
import org.opengroupware.logic.db.IOGoContactChildObject;
import org.opengroupware.logic.db.OGoAddress;
import org.opengroupware.logic.db.OGoObject;

/**
 * OGoAddressChange
 * <p>
 * Needs to assign proper keytype sequence numbers. Sets of
 * insert/update/delete addresses, eg:
 * bill, bill02, bill03 etc. ('type' column of 'address' table).
 * 
 * <p>
 * Example (add an address to contact with id 10000):<pre>
 *   OGoContactChange operation = new OGoContactChange(oc, "Persons");
 *   operation.addChanges(10000, null, null); // no direct changes
 *   
 *   operation.addChildChange(OGoAddressChange.changePerson(oc, 10000,
 *     null, null, UMap.create("name1", "OGo", "street", "Uniplatz 12"));
 *   
 *   Exception error = oc.performOperations(operation);</pre>
 * Note that you need to have an OGoContactChange as a parent operation
 * because the child change will also affect the parent record (eg bump the
 * object_version field).
 * 
 * <p>
 * @author helge
 */
public class OGoAddressChange extends OGoContactChildChange {

  /**
   * Factory method to create an OGoAddressChange for the given
   * parameters.
   * 
   * @param _oc        - OGoObjectContext with login user
   * @param _contactId - pkey of contact to change
   * @param _update    - addresses to update (pkey=>change-dict)
   * @param _delete    - addresses to delete
   * @param _insert    - addresses to add
   * @return an OGoAddressChange, or null if no operation is necessary
   */
  public static OGoAddressChange changePerson(
      final OGoObjectContext _oc, final Number _contactId,
      final Map<Number, Map<String, Object>> _update,
      final List<Number>                     _delete,
      final List<Map<String, Object>>        _insert)
  {
    if (_contactId == null) {
      log.warn("got no contact id!");
      return null;
    }
    if ((_update == null || _update.size() == 0) &&
        (_delete == null || _delete.size() == 0) &&
        (_insert == null || _insert.size() == 0))
      return null; /* nothing to be done */
    
    final OGoAddressChange cc = new OGoAddressChange(_oc, "Persons");
    cc.addChangeSet(_contactId, _update, _delete, _insert);
    return cc;
  }

  public OGoAddressChange(final OGoObjectContext _oc, final String _parent) {
    super(_oc, lookupRelshipEntityName(_oc, _parent, "addresses"), _parent);
  }

  public boolean addChangeSet(final OGoObject _eo) {
    return this.addChangeSet(_eo, "addresses");
  }

  /**
   * Process key assignments. Walk over each contact and setup the proper
   * key numbering to avoid duplicate keys.
   * This method is called by prepareForTransactionInContext().
   * <p>
   * The implementation for OGoAddresses creates keys like:
   * 'bill', 'bill1', 'bill2' etc.
   * It requires that the contact-IDs are properly ordered.
   * <p>
   * Note: email has different rules. (sequence/key separate from vCard label)
   */
  @Override
  protected Exception reworkKeySequences(final OGoOperationTransaction _tx) {
    for (final Number contactId: this.contactIdToChildren.keySet()) {
      final Exception error = OGoAddress.reworkKeySequences(
          this.contactIdToChildren.get(contactId),
          this.contactIdToNewObjects.get(contactId),
          this.deletedIds);
      
      if (error != null)
        return error;
    }
    return null;
  }
  
  @Override
  public IOGoContactChildObject createObject() {
    return new OGoAddress(this.baseEntity);
  }
}
