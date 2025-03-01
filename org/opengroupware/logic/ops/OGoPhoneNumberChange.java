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
import org.opengroupware.logic.db.OGoPhoneNumber;

public class OGoPhoneNumberChange extends OGoContactChildChange {

  public static OGoPhoneNumberChange changePerson(
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
    
    final OGoPhoneNumberChange cc = new OGoPhoneNumberChange(_oc, "Persons");
    cc.addChangeSet(_contactId, _update, _delete, _insert);
    return cc;
  }

  public OGoPhoneNumberChange(final OGoObjectContext _oc, final String _parent){
    super(_oc, lookupRelshipEntityName(_oc, _parent, "phones"), _parent);
  }
  
  public boolean addChangeSet(final OGoObject _eo) {
    return this.addChangeSet(_eo, "phones");
  }

  /**
   * Process key assignments. Walk over each contact and setup the proper
   * key numbering to avoid duplicate keys.
   * <p>
   * Note: email has different rules. (sequence/key separate from vCard label)
   */
  @Override
  protected Exception reworkKeySequences(final OGoOperationTransaction _tx) {
    for (Number contactId: this.contactIdToChildren.keySet()) {
      final Exception error = OGoPhoneNumber.reworkKeySequences(
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
    return new OGoPhoneNumber(this.baseEntity);
  }
}
