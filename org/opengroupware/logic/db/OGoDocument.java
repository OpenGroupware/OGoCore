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

import org.getobjects.eoaccess.EOEntity;
import org.getobjects.foundation.NSJavaRuntime;
import org.opengroupware.logic.blobs.IOGoBlobStore;

/**
 * OGoDocument
 * <p>
 * Represents a document stored in a database filesystem. This class is NOT
 * used for filesystem projects.
 * 
 * @author helge
 */
public class OGoDocument extends OGoDocumentObject {
  
  protected boolean isFolder;
  protected boolean isLink;
  public    Number  versionCount;

  public OGoDocument(EOEntity _entity) {
    super(_entity);
  }
  
  /* accessors */
  
  public void setIsFolder(boolean _flag) {
    if (this.isFolder && !_flag)
      log().warn("removing folder flag of document: " + this);
    else if (this.isLink && _flag)
      log().warn("adding folder flag to link: " + this);
    this.isFolder = _flag;
  }
  public boolean isFolder() {
    return this.isFolder;
  }
  
  public void setIsLink(boolean _flag) {
    if (this.isLink && !_flag)
      log().warn("removing link flag of document: " + this);
    else if (this.isFolder && _flag)
      log().warn("adding link flag to folder: " + this);
    this.isLink = _flag;
  }
  public boolean isLink() {
    return this.isLink;
  }
  
  /**
   * Checks whether the object is an attachment. An attachment is a document
   * attached to some non-folder object (a note, another document or a link).
   */
  public boolean isAttachment() {
    if (this.valueForKey("parentId") == null) {
      /* hack, works around some relationship prefetch issue */
      return false;
    }
    
    Object parent = this.valueForKey("parentNote");
    if (parent instanceof OGoNote) /* implied by having a value? */
      return true; /* documents on notes are attachments */
    
    if (parent == null) {    
      if ((parent = this.valueForKey("parentDocument")) != null) {
        /* Parent is a document, a folder or a link. If the parent is a folder,
         * its a regular document, otherwise its an attachment.
         */
        return !(NSJavaRuntime.boolValueForKey(parent, "isFolder"));
      }
    }
    
    return false; /* its a note, not a comment */
  }

  /* content */

  @Override
  public IOGoBlobStore blobStore() {
    OGoDatabase db = this.oDatabase();
    if (db == null) {
      log.error("missing database object, cannot determine file of document");
      return null;
    }
    
    return db.docsStore();
  }


  /* legacy */
  
  @Override
  public String entityNameInOGo5() {
    return "Doc";
  }
}
