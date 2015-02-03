/*
  Copyright (C) 2007-2014 Helge Hess

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
package org.opengroupware.logic.core;

import org.getobjects.foundation.NSObject;
import org.opengroupware.logic.db.OGoDatabase;

/**
 * A versioned OGo filemanager class which stores the meta data of the files
 * in the database.
 * <p> 
 * This filemanager stores file metadata in the database ('doc' table and
 * companions). Its a versioned storage with per file ACLs and properties.
 * The BLOBs of the files are stored using the mechanisms implemented in the
 * 'blobs' package.
 * <p>
 * FIXME: This doesn't actually do anything yet, right?
 * 
 * @author helge
 */
public class OGoDatabaseFileManager extends NSObject
  implements IOGoFileManager
{
  protected OGoDatabase db;
  protected int projectId;
  
  public OGoDatabaseFileManager(final OGoDatabase _db, final int _projectId) {
    this.db        = _db;
    this.projectId = _projectId;
  }

  
}
