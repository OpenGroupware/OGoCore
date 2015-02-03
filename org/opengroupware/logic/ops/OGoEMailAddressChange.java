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
import org.opengroupware.logic.db.OGoObject;

/**
 * OGoEMailAddressChange
 * <p>
 * Used to change email addresses of a contact. Email addresses are stored in
 * the company_value table (also used for extended attributes).
 * This object takes care of proper address key ordering. For example, if you
 * delete the object representing 'email1', 'email2' will become 'email1' etc. 
 * 
 * <p>
 * Example (add an email address to contact with id 10000):<pre>
 *   OGoContactChange operation = new OGoContactChange(oc, "Persons");
 *   operation.addChanges(10000, null, null); // no direct changes
 *   
 *   operation.addChildChange(OGoEMailAddressChange.changePerson(oc, 10000,
 *     null, // no emails to update (pkey to values)
 *     null, // no emails to delete (would be an array of pkeys)
 *     // FIXME: must be an array!
 *     UMap.create("value", "dd@dd.org")); // email to CREATE
 *   
 *   Exception error = oc.performOperations(operation);</pre>
 * 
 * It looks a bit difficult, but the setup is required to efficiently perform
 * bulk updates.
 *
 * @author helge
 */
public class OGoEMailAddressChange extends OGoContactChildChange {
  
  /**
   * Factory method to create an OGoEMailAddressChange for the given
   * parameters.
   * 
   * @param _oc        - OGoObjectContext with login user
   * @param _contactId - pkey of contact to change
   * @param _update    - emails to update (pkey=>change-dict)
   * @param _delete    - emails to delete
   * @param _insert    - emails to add
   * @return an OGoEMailAddressChange, or null if no operation is necessary
   */
  public static OGoEMailAddressChange changePerson(
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
    
    final OGoEMailAddressChange cc =
      new OGoEMailAddressChange(_oc, "Persons");
    cc.addChangeSet(_contactId, _update, _delete, _insert);
    return cc;
  }

  
  public OGoEMailAddressChange
    (final OGoObjectContext _oc, final String _parent)
  {
    super(_oc, lookupRelshipEntityName(_oc, _parent, "emails"), _parent);
  }
  
  public boolean addChangeSet(final OGoObject _eo) {
    return this.addChangeSet(_eo, "emails");
  }

  /**
   * Process key assignments. Walk over each contact and setup the proper
   * key numbering to avoid duplicate keys. (email1, email2, email3 etc)
   * <p>
   * Note: email has different rules. (sequence/key separate from vCard label)
   */
  @Override
  protected Exception reworkKeySequences(final OGoOperationTransaction _tx) {
    for (final Number contactId: this.contactIdToChildren.keySet()) {
      int sequence = 0;
      
      /* Walk over existing items. Those are properly ordered in the fetch */
      
      List<IOGoContactChildObject> list =
        this.contactIdToChildren.get(contactId);
      for (IOGoContactChildObject item: list) {
        /* if the item is deleted, it does not consume a slot anymore */
        if (this.deletedIds != null && this.deletedIds.contains(item.id()))
          continue;
        
        sequence++;
        item.takeValueForKey("email" + sequence, "key");
      }
      
      /* next process new items */

      if ((list = this.contactIdToNewObjects.get(contactId)) != null) {
        for (IOGoContactChildObject item: list) {
          sequence++;
          item.takeValueForKey("email" + sequence, "key");
        }
      }
    }
    return null;
  }
}
