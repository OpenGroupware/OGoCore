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
package org.opengroupware.logic.blobs;

import java.io.File;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.getobjects.foundation.NSObject;

public class OGoFlatDirBlobStore extends NSObject implements IOGoBlobStore {
  protected static final Log log = LogFactory.getLog("OGoBlobStore");
  
  protected File   root;
  protected String idSuffix;
  
  public OGoFlatDirBlobStore(final File _root) {
    super();
    this.root = _root;
    this.idSuffix = "";
  }
  public OGoFlatDirBlobStore(final File _root, final String _idSuffix) {
    this(_root);
    this.idSuffix = _idSuffix; // TBD: example!
  }

  /* accessors */
  
  public File root() {
    return this.root;
  }
  
  public File blobFileForId(Number _id, Number _containerId, String _ext) {
    if (_id == null)
      return null;
    
    final StringBuilder sb = new StringBuilder(128);
    sb.append(_id);
    sb.append(this.idSuffix);
    if (_ext != null && _ext.length() > 0) {
      sb.append('.');
      sb.append(_ext);
    }
    
    return new File(this.root, sb.toString());
  }
}
