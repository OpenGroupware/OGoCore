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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.getobjects.eoaccess.EOAdaptorChannel;
import org.getobjects.eoaccess.EOAdaptorOperation;
import org.getobjects.eoaccess.EODatabase;
import org.getobjects.eoaccess.EOEntity;
import org.getobjects.eoaccess.EORelationship;
import org.getobjects.eocontrol.EOFetchSpecification;
import org.getobjects.eocontrol.EOQualifier;
import org.getobjects.foundation.NSException;
import org.opengroupware.logic.core.OGoObjectContext;
import org.opengroupware.logic.db.OGoContactComment;
import org.opengroupware.logic.db.OGoObject;

/**
 * OGoContactCommentChange
 * <p>
 * Operation to change the comment of a contact. It should be attached to an
 * OGoContactChange operation.
 * <p>
 * Example:
 * 
 * <pre>
 * contactChange.addChildChange(OGoContactCommentChange.changePerson(_oc, 10000,
 *     &quot;this is root&quot;));
 * </pre>
 * 
 * @author helge
 */
public class OGoContactCommentChange extends OGoOperation implements
    IOGoObjectChildChange {

  protected EOEntity                       baseEntity;
  protected Map<Number, String>            contactIdToValue;
  protected Map<Number, OGoContactComment> contactIdToValueObject;

  /**
   * Factory method to create an OGoContactCommentChange for the given
   * contact/comment.
   * 
   * @param _oc
   *          - OGoObjectContext with login user
   * @param _contactId
   *          - pkey of contact to change
   * @param _comment
   *          - the new comment (possibly null)
   * @return an OGoContactCommentChange, or null if no operation is necessary
   */
  public static OGoContactCommentChange changePerson(
      final OGoObjectContext _oc, final Number _contactId, final String _comment) {
    OGoContactCommentChange cc = new OGoContactCommentChange(_oc,
        "PersonComments");
    cc.addChangeSet(_contactId, _comment);
    return cc;
  }

  public OGoContactCommentChange(OGoObjectContext _oc, String _entityName) {
    super(_oc);
    this.baseEntity = _oc.oDatabase().entityNamed(
        lookupRelshipEntityName(_oc, _entityName, "comment"));

    this.contactIdToValue = new HashMap<Number, String>(4);
    this.contactIdToValueObject = new HashMap<Number, OGoContactComment>(4);
  }

  /**
   * Lookup the name of the relationship entity. Example:
   * 
   * <pre>
   * lookupRelshipEntityName(oc, &quot;Persons&quot;, &quot;addresses&quot;)
   * </pre>
   * 
   * This will return PersonAddresses.
   * 
   * @param _oc
   *          - the OGoObjectContext
   * @param _parent
   *          - the name of the parent entity, eg Persons
   * @param _key
   *          - the name of the relationship, eg 'phones'
   * @return the name of the relationship entity
   */
  public static String lookupRelshipEntityName(final OGoObjectContext _oc,
      final String _parent, final String _key) {
    final EODatabase db = _oc.database();
    if (db == null)
      return null;
    EOEntity e = db.entityNamed(_parent);
    if (e == null)
      return null;
    final EORelationship r = e.relationshipNamed(_key);
    if (r == null)
      return null;
    e = r.destinationEntity();
    if (e == null)
      return null;
    return e.name();
  }

  /* accessors */

  public void addChangeSet(final Number _contactId, final String _comment) {
    if (_contactId == null)
      return;

    /* Note: comment can be null if the intention is to reset the comment! */
    this.contactIdToValue.put(_contactId, _comment);
  }

  /* IOGoContactChildChange */

  public boolean hasChangesForMasterObject(final OGoObject _contact) {
    if (_contact == null)
      return false;

    return this.contactIdToValueObject.containsKey(_contact.id());
  }

  /* prepare */

  @SuppressWarnings("unchecked")
  @Override
  public Exception prepareForTransactionInContext(
      final OGoOperationTransaction _tx, final OGoObjectContext _ctx) {
    if (this.contactIdToValue == null || this.contactIdToValue.size() == 0)
      return null; /* nothing to do */

    Exception error = null;

    /* fetch comments */

    EOFetchSpecification fs = new EOFetchSpecification(this.baseEntity.name(),
        EOQualifier.parse("companyId IN %@", this.contactIdToValue.keySet()),
        null);

    final List<OGoContactComment> children = _tx.objectContext()
        .objectsWithFetchSpecification(fs);
    if (children == null) {
      error = _tx.objectContext().consumeLastException();
      return error != null ? error : new NSException("could not fetch objs");
    }

    /* find changed comments */

    for (final OGoContactComment child : children) {
      final Number contactId = child.companyId();
      if (!this.contactIdToValue.containsKey(contactId))
        continue; /* should never happen */

      /* Note: this can be null! (if the user reset the comment) */
      final String newComment = this.contactIdToValue.remove(contactId);
      child.setValue(newComment);
      if (child.hasChanges()) {
        this.contactIdToValueObject.put(contactId, child);
        _tx.requestPermissionOnId("w", this.baseEntity.name(), child.id());
      }
    }

    /* check whether all comments could be found */

    if (this.contactIdToValue.size() > 0) {
      /* be tolerant, create new comments when necessary */
      for (final Number contactId : this.contactIdToValue.keySet()) {
        final OGoContactComment child = new OGoContactComment(this.baseEntity);
        child.setCompanyId(contactId.intValue());
        child.setValue(this.contactIdToValue.get(contactId));

        this.contactIdToValueObject.put(contactId, child);

        // TBD: request write permission on contactId
        // _tx.requestPermissionOnId("w", this.baseEntity.name(), child.id());
      }
    }

    return null;
  }

  /* do it */

  @Override
  public Exception runInContext(OGoOperationTransaction _tx,
      EOAdaptorChannel _ch, OGoObjectContext _ctx) {
    if (this.contactIdToValueObject == null
        || this.contactIdToValueObject.size() == 0)
      return null; /* nothing to be done */

    List<EOAdaptorOperation> upOps = new ArrayList<EOAdaptorOperation>(16);
    List<EOAdaptorOperation> addOps = new ArrayList<EOAdaptorOperation>(16);

    for (OGoContactComment child : this.contactIdToValueObject.values()) {
      if (child.isNew())
        addOps.add(this.insertOperation(child, this.baseEntity));
      else
        upOps.add(this.updateOperation(child, this.baseEntity, null /* rev */));
    }
    upOps.addAll(addOps);

    Exception error = _ch.performAdaptorOperations(upOps);
    if (error != null)
      return error;

    return null /* everything is fine */;
  }

}
