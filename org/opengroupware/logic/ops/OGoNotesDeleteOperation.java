/*
  Copyright (C) 2008-2014 Helge Hess

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
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.getobjects.eoaccess.EOAdaptorChannel;
import org.getobjects.eoaccess.EOAdaptorOperation;
import org.getobjects.eoaccess.EOEntity;
import org.getobjects.eocontrol.EOFetchSpecification;
import org.getobjects.eocontrol.EOKeyValueQualifier;
import org.getobjects.eocontrol.EOQualifier.ComparisonOperation;
import org.getobjects.foundation.NSException;
import org.getobjects.foundation.UObject;
import org.opengroupware.logic.blobs.IOGoBlobStore;
import org.opengroupware.logic.core.OGoObjectContext;
import org.opengroupware.logic.db.OGoNote;
import org.opengroupware.logic.db.OGoResultSet;

/**
 * OGoNotesDeleteOperation
 * <p>
 * Deletes a note object.
 * 
 * <p>
 * Example:<pre>
 *   OGoNotesDeleteOperation delNotes = new OGoNotesDeleteOperation(oc);
 *   delNotes.deleteNote(selectedNote);
 *   
 *   Exception error = oc.performOperations(newNote);</pre>
 * 
 * <p>
 * @author helge
 */
public class OGoNotesDeleteOperation extends OGoOperation {
  
  protected EOEntity     baseEntity;
  protected Map<Number, Boolean> notesToDelete;
  
  /* transaction state */
  protected List<Number> delNoteIds;
  protected Collection<OGoFileDeleteTransaction> fileDeletes;
  

  public OGoNotesDeleteOperation(final OGoObjectContext _oc) {
    super(_oc);
    
    this.baseEntity    = _oc.oDatabase().entityNamed("Notes");
    this.notesToDelete = new HashMap<Number, Boolean>(4);
    
  }

  /* adding notes */
  
  public void deleteNote(final Number _noteId) {
    if (_noteId == null)
      return;
    
    this.notesToDelete.put(_noteId, true /* recursive */);
  }
  
  public void deleteNote(final OGoNote _note) {
    if (_note != null)
      this.deleteNote(_note.id());
  }
  
  /* operation */

  /**
   * Prepare note delete.
   * <p>
   * This checks the required permissions.
   * 
   * <p>
   * The following permissions are requested:
   * <ul>
   *   <li>'w' on the projectGID, if there was one (Note: yes, not 'd'!)
   *   <li>'l' on the eventGID,   if there was one
   *   <li>'l' on the contactGID, if there was one
   * </ul>
   * Means: you can delete own notes, even if you have no write access to the
   *        contact itself.
   */
  @SuppressWarnings("unchecked")
  @Override
  public Exception prepareForTransactionInContext
    (final OGoOperationTransaction _tx, final OGoObjectContext _oc)
  {
    if (this.notesToDelete == null || this.notesToDelete.size() == 0)
      return null; /* nothing to be done */
    
    // final Date   now  = _tx.startDate();
    // final Number me   = _oc.actorID();
    
    /* first fetch the notes, to check for child objects */
    
    final EOFetchSpecification fs = new EOFetchSpecification("Notes",null,null);
    fs.setQualifier("id IN %@", this.notesToDelete.keySet());
    fs.setFetchesReadOnly(true);
    fs.setPrefetchingRelationshipKeyPaths(new String[] {
      "comments", "attachments"
    });
    final OGoResultSet notes = oc.doFetch(fs, 10);
    if (notes.hasError())
      return notes.error();
    
    if (notes.size() < this.notesToDelete.size()) {
      log.warn("found less objects than requested for deletion: " + notes);
      return new NSException("did not find all note objects to be deleted!");
    }
    
    /* check for child objects, and request permissions */
    
    this.delNoteIds  = new ArrayList<Number>(notes.size());
    this.fileDeletes = new ArrayList<OGoFileDeleteTransaction>(notes.size());
    
    return this.prepareNotesForDeletion(_tx, _oc, notes);
  }
  
  @SuppressWarnings("rawtypes")
  protected Exception prepareNotesForDeletion
    (final OGoOperationTransaction _tx, final OGoObjectContext _oc,
     final List<OGoNote> _notes)
  {
    if (_notes == null || _notes.size() == 0)
      return null;

    final IOGoBlobStore bs = _oc.oDatabase().notesStore();
    for (OGoNote note: _notes) {
      //final boolean doRecurse = this.notesToDelete.get(note.id());
      Collection c;
      
      // TBD: support doRecurse
      if ((c = (Collection)note.valueForKey("attachments")) != null) {
        if (c.size() > 0)
          return new NSException("note still has attachments!");
      }

      if ((c = (Collection)note.valueForKey("comments")) != null) {
        if (c.size() > 0)
          return new NSException("note still has comments!");
      }
      
      /* request permissions */
      
      _tx.requestPermissionOnGlobalID("d", _oc.globalIDForObject(note));
      
      Number v;
      
      if ((v = note.projectId()) != null)
        _tx.requestPermissionOnId("w", "Projects", v);
      
      if (UObject.isNotEmpty((v = (Number)note.valueForKey("eventId"))))
        _tx.requestPermissionOnId("l", "Events", v);
      
      if (UObject.isNotEmpty((v = (Number)note.valueForKey("contactId")))) {
        // hm. could be a company, a person, etc. what to request?
        // TBD
      }
      
      /* add to delete queue */
      
      if (this.delNoteIds.contains(note.id()))
        continue; /* already in queue */

      final File f = bs.blobFileForId(note.id(), null, (String)note.fileext);
      if (f == null) {
        log.warn("got no blob-file path for note: " + note);
        return new NSException("got no blob-file path for note: " + note);
      }
      
      this.delNoteIds.add(note.id());
      this.fileDeletes.add(new OGoFileDeleteTransaction(f));
    }
    
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
    if (this.delNoteIds == null || this.delNoteIds.size() == 0)
      return null; /* nothing to be done */
    
    /* first attempt to delete in database */

    EOAdaptorOperation op = new EOAdaptorOperation(this.baseEntity);
    op.setAdaptorOperator(EOAdaptorOperation.AdaptorDeleteOperator);
    op.setQualifier(new EOKeyValueQualifier("id",
        ComparisonOperation.CONTAINS, this.delNoteIds));
    
    Exception error = _ch.performAdaptorOperation(op);
    if (error != null) return error;
    
    /* next delete the associated filesystem files */
    
    if (this.fileDeletes != null) {
      for (final OGoFileDeleteTransaction tx: this.fileDeletes) {
        if ((error = tx.performAtomicDelete()) != null)
          return error;
      }
    }
    
    return null;
  }
  
  @Override
  public Exception transactionWillRollbackInContext
    (OGoOperationTransaction _tx, EOAdaptorChannel _ch, OGoObjectContext _ctx)
  {
    /* Rollback changes in filesystem (more or less relies on global database
     * locks being applied in the DB transaction)
     */

    Exception lastError = null;
    if (this.fileDeletes != null) {
      for (final OGoFileDeleteTransaction tx: this.fileDeletes) {
        final Exception error = tx.rollback();
        if (error != null) lastError = error;
      }
    }
    if (lastError != null)
      return lastError;
    
    return super.transactionWillRollbackInContext(_tx, _ch, _ctx);
  }

  @Override
  public Exception transactionDidCommitInContext
    (final OGoOperationTransaction _tx, final OGoObjectContext _ctx)
  {
    if (this.fileDeletes != null) {
      for (final OGoFileDeleteTransaction tx: this.fileDeletes) {
        final Exception error = tx.commit();
        if (error != null) return error;
      }
    }
    
    return super.transactionDidCommitInContext(_tx, _ctx);
  }
}
