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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.getobjects.foundation.NSObject;

public class OGoRangeDirBlobStore extends NSObject implements IOGoBlobStore {
  protected static final Log log = LogFactory.getLog("OGoBlobStore");

  protected static final boolean UseFoldersForIDRanges = true;
  protected File root;
  protected int  rangeSize;
  
  public OGoRangeDirBlobStore(final File _root) {
    super();
    this.root      = _root;
    this.rangeSize = 1000; /* default OGo range size */
  }

  /* accessors */
  
  public File root() {
    return this.root;
  }
  
  public File blobFileForId(Number _id, Number _containerId, String _ext) {
    if (_id == null)
      return null;
    
    File container = null;

    /* container */
    
    if (_containerId != null) {
      container = new File(this.root, _containerId.toString());
      if (!container.exists()) {
        if (!container.mkdir()) {
          /* probably a permission setup issue */
          log.error("could not create BLOB directory for container '" +
              _containerId + "': " + container + 
              " (check filesystem permissions!)");
          return null;
        }
      }
    }
    else
      container = this.root;
    
    /* folder ranges */
    
    if (UseFoldersForIDRanges) {
      long rangeId = _id.longValue();
      rangeId = rangeId - (rangeId % this.rangeSize);
      
      container = new File(container, Long.toString(rangeId));
      if (!container.exists()) {
        if (!container.mkdir()) {
          /* probably a permission setup issue */
          log.error("could not create BLOB directory for id-range '" +
              rangeId + "': " + container + 
              " (check filesystem permissions!)");
          return null;
        }
      }
    }
    
    /* filename */

    final StringBuilder sb = new StringBuilder(128);
    sb.append(_id);
    if (_ext != null && _ext.length() > 0) {
      sb.append('.');
      sb.append(_ext);
    }
    
    return new File(container, sb.toString());
  }
}
