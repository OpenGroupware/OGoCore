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

import java.io.File;
import java.util.Date;
import java.util.Map;

import org.getobjects.eoaccess.EOAdaptorChannel;
import org.getobjects.eoaccess.EOAdaptorOperation;
import org.getobjects.eoaccess.EOEntity;
import org.getobjects.eocontrol.EOKeyGlobalID;
import org.getobjects.foundation.NSException;
import org.getobjects.foundation.UMap;
import org.opengroupware.logic.blobs.IOGoBlobStore;
import org.opengroupware.logic.core.OGoObjectContext;


/**
 * OGoDocumentObjectInsert
 * <p>
 * Adds a note to a contact, event and/or project.
 * 
 * <p>
 * Example:<pre>
 *   OGoNotesInsert newNote = new OGoNotesInsert(oc);
 *   newNote.contentAsString = "Hello World!";
 *   newNote.companyGID      =
 *     EOKeyGlobalID.globalIDWithEntityName("Persons", 10000);
 *   
 *   Exception error = oc.performOperations(newNote);</pre>
 * 
 * <p>
 * @author helge
 */
public class OGoDocumentObjectInsert extends OGoOperation {
  // FIXME: delete notes on rollback (works?!)
  // TBD: add 'parent folder'
  
  protected EOEntity baseEntity;
  
  public Object content;
  public Date   creationDate;
  
  /* connections */
  public EOKeyGlobalID projectGID;
  public EOKeyGlobalID eventGID;
  public EOKeyGlobalID companyGID;
  
  /* record */
  public Map<String, Object> record;
  public Number id;
  
  /* file, transaction state */
  protected OGoFileWriteTransaction fileTx;
  

  public OGoDocumentObjectInsert(final OGoObjectContext _oc, String _ename) {
    super(_oc);
    
    this.baseEntity = _oc.oDatabase().entityNamed(_ename);
  }
  
  
  /* accessors */
  
  /**
   * Returns the assigned primary key of the note.
   */
  public Number id() {
    return this.id;
  }

  public void setContent(final Object _s) {
    this.content = _s;
  }
  public Object content() {
    return this.content;
  }
  
  public void setContentAsString(final String _s) {
    this.content = _s;
  }
  public String contentAsString() {
    if (this.content == null)
      return null;
    if (this.content instanceof String)
      return (String)this.content;
    
    log.error("cannot convert content to String: " + this.content);
    return null;
  }
  
  public void setPersonId(final Number _id) {
    this.companyGID = _id != null
      ? EOKeyGlobalID.globalIDWithEntityName("Persons", _id)
      : null;
  }
  public Number personId() {
    if (this.companyGID == null)
      return null;
    if (!this.companyGID.entityName().equals("Persons"))
      return null;
    return this.companyGID.toNumber();
  }
  
  public void setCompanyId(final Number _id) {
    this.companyGID = _id != null
      ? EOKeyGlobalID.globalIDWithEntityName("Companies", _id)
      : null;
  }
  public Number companyId() {
    if (this.companyGID == null)
      return null;
    if (!this.companyGID.entityName().equals("Companies"))
      return null;
    return this.companyGID.toNumber();
  }

  public void setTeamId(final Number _id) {
    this.companyGID = _id != null
      ? EOKeyGlobalID.globalIDWithEntityName("Teams", _id)
      : null;
  }
  public Number teamId() {
    if (this.companyGID == null)
      return null;
    if (!this.companyGID.entityName().equals("Teams"))
      return null;
    return this.companyGID.toNumber();
  }

  public void setEventId(final Number _id) {
    this.eventGID = _id != null
      ? EOKeyGlobalID.globalIDWithEntityName("Events", _id)
      : null;
  }
  public Number eventId() {
    return this.eventGID != null ? this.eventGID.toNumber() : null;
  }

  public void setProjectId(final Number _id) {
    this.projectGID = _id != null
      ? EOKeyGlobalID.globalIDWithEntityName("Projects", _id)
      : null;
  }
  public Number projectId() {
    return this.projectGID != null ? this.projectGID.toNumber() : null;
  }
  
  
  /* prepare */
  
  private static final Number zero = new Integer(0);
  private static final Number one  = new Integer(1);
  
  /**
   * Prepare note insert.
   * <p>
   * This creates a proper Note record.
   * 
   * <p>
   * The following permissions are requested:
   * <ul>
   *   <li>'i' on the projectGID, if there was one
   *   <li>'l' on the eventGID,   if there was one
   *   <li>'l' on the contactGID, if there was one
   * </ul>
   */
  @SuppressWarnings("unchecked")
  @Override
  public Exception prepareForTransactionInContext
    (final OGoOperationTransaction _tx, final OGoObjectContext _oc)
  {
    final Date   now  = _tx.startDate();
    final Number me   = _oc.actorID();
    
    /* setup default values which we can derive w/o a fetch */
    this.record = UMap.create(
        "object_version", one,
        "versionCount",   zero,
        "dbStatus",       "inserted",
        "creationDate",   now,
        "lastModified",   now,
        "creatorId",      me,
        "ownerId",        me,
        "fileext",        "txt",
        
        /* hackish, those are not mapped in the model */
        "is_folder",      zero,
        "is_object_link", zero,
        "is_index_doc",   zero,
        "is_note",        one
        );
    
    
    if (this.creationDate != null) {
      /* if the user selects a different date for this note */
      record.put("creationDate", this.creationDate);
    }
    
    
    /* if its associated with a project, we must have insert perms on it */
    if (this.projectGID != null) {
      _tx.requestPermissionOnGlobalID("i", this.projectGID);
      this.record.put("projectId", this.projectGID.toNumber());
    }
    
    /* for events and contacts we are fine with 'l' (list) permissions */
    // TBD: we might want to fill objectlink?
    if (this.eventGID != null) {
      _tx.requestPermissionOnGlobalID("l", this.eventGID);
      this.record.put("eventId", this.eventGID.toNumber());
    }
    if (this.companyGID != null) {
      // TBD: there is also a "contact" VARCHAR field which we could fill, I
      //      suppose its deprecated
      _tx.requestPermissionOnGlobalID("l", this.companyGID);
      this.record.put("companyId", this.companyGID.toNumber());
    }
    
    if (this.content == null)
      return new NSException("missing content for document object");

    return null;
  }
  
  /* do it */
  
  @Override
  public Exception runInContext
    (OGoOperationTransaction _tx, EOAdaptorChannel _ch, OGoObjectContext _ctx)
  {
    // TBD: this only works for notes.
    // TBD: - real documents need document_editing records (+ doc_version?)
    // TBD: - folders have no content
    // TBD: enhance to support note updates?
    final Number pkey = _ctx.oDatabase().nextPrimaryKey();
    if (pkey == null)
      return new NSException("Could not create new primary key!");
    
    this.record.put("id",       pkey);
    this.record.put("filename", "note-" + pkey); /* include date? */
    
    EOAdaptorOperation op = new EOAdaptorOperation(this.baseEntity);
    op.setAdaptorOperator(EOAdaptorOperation.AdaptorInsertOperator);
    op.setChangedValues(this.record);
    
    // subject
    // size
    
    //System.err.println("INSERT: " + record);
    //System.err.println("    OP: " + op);
    
    Exception error = _ch.performAdaptorOperation(op);
    if (error != null)
      return error;
    
    /* write file */
    
    final IOGoBlobStore bs = _ctx.oDatabase().notesStore();
    File f = bs.blobFileForId(pkey, null, (String)this.record.get("fileext"));
    
    this.fileTx = new OGoFileWriteTransaction(f);
    if ((error = this.fileTx.performAtomicWrite(this.content)) != null)
      return error;
    
    /* store results */
    this.id = pkey;
    return null;
  }
  
  @Override
  public Exception transactionWillRollbackInContext
    (OGoOperationTransaction _tx, EOAdaptorChannel _ch, OGoObjectContext _ctx)
  {
    /* Rollback changes in filesystem (more or less relies on global database
     * locks being applied in the DB transaction)
     */
    if (this.fileTx != null) {
      final Exception error = this.fileTx.rollback();
      if (error != null) return error;
    }
    
    return super.transactionWillRollbackInContext(_tx, _ch, _ctx);
  }

  @Override
  public Exception transactionDidCommitInContext
    (final OGoOperationTransaction _tx, final OGoObjectContext _ctx)
  {
    if (this.fileTx != null) {
      final Exception error = this.fileTx.commit();
      if (error != null) return error;
    }
    
    return super.transactionDidCommitInContext(_tx, _ctx);
  }

  
}
