/*
  Copyright (C) 2008 Helge Hess

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
package org.opengroupware.logic.ops;

import java.io.File;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.getobjects.foundation.NSException;
import org.getobjects.foundation.NSObject;

/**
 * OGoFileDeleteTransaction
 * <p>
 * Wraps the work required to delete filesystem files (eg document contents)
 * during a database operation. If the database operation rolls back, we need
 * to revert the files.
 * 
 * <p>
 * @author helge
 */
public class OGoFileDeleteTransaction extends NSObject {
  protected static final Log log = LogFactory.getLog("OGoOperation");

  protected File fileMoved;   // temporary name
  protected File fileWritten; // the name of the original file
  
  public OGoFileDeleteTransaction(final File _f) {
    this.fileWritten = _f;
  }
  
  
  /* top level */
  
  public Exception performAtomicDelete() {
    if (!this.fileWritten.exists())
      return null; // nothing to be done
    
    /* move away old file for rollback */
    final File oldFile = new File(this.fileWritten.getAbsolutePath() +"-txdel");
    if (!this.fileWritten.renameTo(oldFile)) {
      log.error("could not move old Note file: " + this.fileWritten + 
                " to " + oldFile);
      return new NSException("could not move away old file!");
    }
    else
      this.fileMoved   = oldFile;
    return null;
  }
  
  /* transaction ops */

  public Exception rollback() {
    if (this.fileMoved != null) {
      if (!this.fileMoved.renameTo(this.fileWritten)) {
        log.error("could not restore old Note file: " + this.fileMoved +
            " to " + this.fileWritten);
        return new NSException("could not restore Note file before rollback");
      }
    }
    
    return null;
  }
  
  public Exception commit() {
    if (this.fileMoved != null) {
      if (!this.fileMoved.delete()) {
        log.error("could not delete old Note file: " + this.fileMoved +
            " to " + this.fileWritten);
        return new NSException("could not delete Note file during commit");
      }
    }
    return null;
  }

}
