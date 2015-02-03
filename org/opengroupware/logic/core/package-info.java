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
/**
 * Core OGo classes. What else to say ;-)
 * 
 * <h4>OGoObjectContext</h4>
 * TBD
 * 
 * 
 * <h4>IOGoOperation</h4>
 * TBD
 * 
 * 
 * <h4>IOGoFileManager</h4>
 * The interface provides the API for a backend storage of a project, currently
 * either OGoFileSystemFileManager or OGoDatabaseFileManager.
 * 
 * 
 * <h4>OGoFileSystemFileManager implements IOGoFileManager</h4>
 * This one directly maps project files to some Unix filesystem hierarchy.
 * Folders and files are not represented by database records.
 * It provides no versioning nor searchable attributes nor ACLs.
 * 
 * 
 * <h4>OGoDatabaseFileManager implements IOGoFileManager</h4>
 * This filemanager stores file metadata in the database ('doc' table and
 * companions). Its a versioned storage with per file ACLs and properties.
 * The BLOBs of the files are stored using the mechanisms implemented in the
 * 'blobs' package.
 *
 * <p>
 * @author helge
 */
package org.opengroupware.logic.core;
