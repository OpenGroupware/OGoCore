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
 * This package contains classes to manage the storage of OGo database "BLOBS",
 * that is documents and notes.
 * <p>
 * Do not mix this app with storage handlers for projects. This is just about
 * "database projects" or other OGo database objects which need storage (eg notes
 * are not necessarily attached to projects).
 * <p>
 * Storage handlers for projects are done in the 'core' interface IOGoFileManager,
 * ie OGoDatabaseFileManager vs OGoFileSystemFileManager.
 * 
 * @author helge
 */
package org.opengroupware.logic.blobs;
