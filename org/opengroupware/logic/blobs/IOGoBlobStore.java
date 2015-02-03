/*
  Copyright (C) 2007 Helge Hess

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
package org.opengroupware.logic.blobs;

import java.io.File;

public interface IOGoBlobStore {

  // TBD: this ties us to a filesystem store, ie we could not store documents
  //      in a database BLOB. Whats the way to go? Return a URL (which could
  //      then use a custom scheme for BLOBs?)
  /**
   * Returns a File object which represents the location the OGoDocumentObject
   * with the given id should be stored.
   * 
   * Document objects are folders, documents and notes (plus the variants
   * "comments" and "attachments").
   * 
   * @param _id - the ID of the note or document
   * @param _containerId - the ID of the folder which contains the note,
   *                       can be null
   * @param _ext - the file extension, eg "gif" or "txt" (Notes use "txt")
   */
  public File blobFileForId(Number _id, Number _containerId, String _ext);
  
}
