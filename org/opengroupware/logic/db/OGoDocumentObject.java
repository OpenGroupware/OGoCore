/*
  Copyright (C) 2007-2009 Helge Hess

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

import java.io.File;
import java.util.Date;

import org.getobjects.eoaccess.EOEntity;
import org.getobjects.foundation.UString;
import org.opengroupware.logic.blobs.IOGoBlobStore;

/**
 * OGoDocumentObject
 * <p>
 * Both, notes and documents are stored in the 'document' table in OGo and
 * thereby share a lot of behaviour.
 * The biggest difference between a note and a document is that top-level notes
 * are stored in a different location. Note that document can also have the
 * is_note flag!
 * 
 * <h4>Permissions</h4>
 * <p>
 * In short: if a note/document is assigned to a project, the user must have
 * read access on the project.
 * If not, the user must either have access to the appointment or to the contact
 * the note is associated with.
 * <br>
 * Plus the whole document ACL setup (user must have read access for the whole
 * file system hierarchy down to the note/document).
 * 
 * <p>
 * @author helge
 */
public abstract class OGoDocumentObject extends OGoObject {
  
  protected Number     projectId;
  public    String     filename; // empty allowed for root-folder
  public    String     fileext;
  protected Date       creationDate;
  public    Date       lastModified;
  
  /* permissions/hierarchy */
  public    Number     parentId; // parent folder/note/etc
  public    Number     ownerId;
  public    Number     creatorId;
  public    OGoPerson  owner;
  public    OGoPerson  creator;
  
  /* connected documents/notes */
  public    Number     eventId;
  public    Number     contactId;
  public    OGoPerson  person;
  public    OGoCompany company;
  public    OGoTeam    team;

  public OGoDocumentObject(final EOEntity _entity) {
    super(_entity);
  }
  
  
  /* accessors */
  
  public void setProjectId(final Number _id) {
    if (this.projectId == _id)
      return;
    
    if (this.projectId != null && this.projectId != _id) {
      log.warn("attempt to change project-id of document (" + this.projectId +
          "=>" + _id + ": " + this);
    }    
    this.projectId = _id;
  }
  public Number projectId() {
    return this.projectId;
  }
  
  public void setFilenameWithExt(final String _name) {
    if (_name == null) {
      this.filename = null;
      this.fileext  = null;
    }
    else {
      int idx = _name.indexOf('.'); // yes, forward search, we store .tar.gz
      if (idx < 0) {
        this.filename = _name;
        this.fileext = null;
      }
      else {
        this.filename = _name.substring(0, idx);
        this.fileext  = _name.substring(idx + 1);
      }
    }
  }
  public String filenameWithExt() {
    if (this.filename != null && this.fileext != null)
      return this.filename + "." + this.fileext;
    
    if (this.filename != null)
      return this.filename;
    
    if (this.fileext != null) // kinda invalid?
      return this.fileext;
    
    return null;
  }
  
  public void setCreationDate(final Date _date) {
    if (this.creationDate == _date)
      return;
    
    if (this.creationDate != null) {
      log.warn("attempt to change creationDate of a document (" + this.id+")");
      return;
    }
    this.creationDate = _date;
  }
  public Date creationDate() {
    return this.creationDate;
  }
  
  /* content */
  
  public String contentEncoding() {
    return "utf-8"; // TBD: make configurable
  }
  public String contentAsString() {
    return UString.loadFromFile(this.blobFile(), this.contentEncoding());
  }
  
  /**
   * Returns the IOGoBlobStore responsible for this object. The store is used
   * to map the document object to the proper File object.
   * 
   * @return an IOGoBlobStore
   */
  public IOGoBlobStore blobStore() {
    return null;
  }
  
  /**
   * Returns a File object which points to the file containing the BLOB
   * associated with this document. The File is located by asking the
   * {@link IOGoBlobStore}t.
   * 
   * @return a File object pointing to the proper BLOB
   */
  public File blobFile() {
    IOGoBlobStore bs = this.blobStore();
    if (bs == null) {
      log.error("missing IOGoBlobStore object, cannot determine file of note");
      return null;
    }
    
    File f = bs.blobFileForId(this.id, this.projectId, this.fileext);
    if (f == null)
      log.error("could not locate BLOB of document object: " + this);
    return f;
  }
  
  
  /* type */
  
  /**
   * Returns the "type" of a document object. Its one of:
   * <ul>
   *   <li>Folder     - an object with is_folder=1 (isFolder)
   *   <li>Link       - an object with is_obj_link=1 (isLink)
   *   <li>Comment    - a Note (is_note=1) attached to a non-folder
   *   <li>Attachment - a Document (is_note=0) attached to a non-folder
   *   <li>Note       - an object with is_note=1
   *   <li>Document   - any object which does not match the above :-)
   * </ul>
   * 
   * @returns a String containing the type
   */
  public String documentObjectType() {
    // TBD: should we expose is_index_doc as a separate type? I guess not.
    if (this.isComment())    return "Comment";
    if (this.isAttachment()) return "Attachment";
    if (this.isNote())       return "Note";
    if (this.isFolder())     return "Folder";
    if (this.isLink())       return "Link";
    return "Document";
  }
  
  public boolean isComment() {
    /* overridden in OGoNote */
    return false;
  }
  public boolean isAttachment() {
    /* overridden in OGoDocument */
    return false;
  }
  public boolean isFolder() {
    /* overridden in OGoDocument */
    return false;
  }
  public boolean isLink() {
    /* overridden in OGoDocument */
    return false;
  }
  public boolean isNote() {
    /* overridden in OGoDocument */
    return false;
  }  
  
  /* events */
  
  public boolean isConnectedToEvent() {
    return this.valueForKey("eventId") != null;
  }
  
  
  /* contacts */
  
  public boolean isConnectedToContact() {
    return this.valueForKey("contactId") != null;
  }
  
  public boolean isConnectedToPerson() {
    return this.valueForKey("person") != null;
  }
  public boolean isConnectedToCompany() {
    return this.valueForKey("company") != null;
  }
  public boolean isConnectedToTeam() {
    return this.valueForKey("team") != null;
  }
  
  /**
   * This returns the contact object connected to this note or document. It
   * works by checking the various relationships (person => company => team).
   * 
   * @return an OGoContact or null if none is associated 
   */
  public OGoContact contact() {
    OGoContact contact = (OGoContact)this.valueForKey("person");
    if (contact != null) return contact;

    contact = (OGoContact)this.valueForKey("company");
    if (contact != null) return contact;

    contact = (OGoContact)this.valueForKey("team");
    if (contact != null) return contact;
    
    return null;
  }
}
