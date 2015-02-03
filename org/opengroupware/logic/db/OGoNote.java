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
 * OGoNote
 * <p>
 * Represents a Note record in the system. Notes can be attached to
 * appointments, persons, companies, projects.
 * <p>
 * The content of a note is stored in the filesystem and can be retrieved
 * using the 'contentAsString' method of the superclass.
 * 
 * @author helge
 */
public class OGoNote extends OGoDocumentObject {

  public OGoNote(final EOEntity _entity) {
    super(_entity);
  }
  
  
  /* accessors */
  
  public void setIsNote(final boolean _flag) {
    if (!_flag)
      log().warn("OGoNote: called setIsNote(false) ...");
  }
  public boolean isNote() {
    return true;
  }

  
  /* types */
  
  /**
   * Checks whether the object is a comment. A comment is a note attached to
   * some non-folder object (another note, a document or a link).
   */
  public boolean isComment() {
    if (this.valueForKey("parentId") == null) {
      /* hack, works around some relationship prefetch issue */
      return false;
    }
    
    Object parent = this.valueForKey("parentNote");
    
    //System.err.println("PARENT OF " + this + "\n  IS: " + parent +
    //    "\n  WITH: " + this.valueForKey("parentId"));
    
    if (parent instanceof OGoNote) /* implied by having a value? */
      return true; /* note on notes are comments */
    
    if (parent == null) {    
      if ((parent = this.valueForKey("parentDocument")) != null) {
        /* Parent is a document, a folder or a link. If the parent is a folder,
         * its a note, otherwise its a comment
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
      log().error("missing database object, cannot determine file of note");
      return null;
    }
    
    return db.notesStore();
  }


  /* legacy */
  
  @Override
  public String entityNameInOGo5() {
    return "Note";
  }
}
