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

import java.io.File;

import org.getobjects.foundation.NSObject;

/**
 * A non-versions OGo filemanager class which doesn't support meta data and
 * stores the files as-is in the filesystem.
 * <p> 
 * This one directly maps project files to some Unix filesystem hierarchy.
 * Folders and files are not represented by database records.
 * It provides no versioning nor searchable attributes nor ACLs.
 * <p>
 * FIXME: This doesn't actually do anything yet, right?
 * 
 * @author helge
 */
public class OGoFileSystemFileManager extends NSObject
  implements IOGoFileManager
{

  protected File root;
  
  public OGoFileSystemFileManager(final File _root) {
    this.root = _root;
  }
  
}
