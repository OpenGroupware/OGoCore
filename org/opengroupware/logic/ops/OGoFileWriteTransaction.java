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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.getobjects.foundation.NSException;
import org.getobjects.foundation.NSObject;
import org.getobjects.foundation.UData;
import org.getobjects.foundation.UString;

/**
 * OGoFileWriteTransaction
 * <p>
 * Wraps the work required to update filesystem files (eg document contents)
 * during a database operation. If the database operation rolls back, we need
 * to revert the files.
 * 
 * <p>
 * @author helge
 */
public class OGoFileWriteTransaction extends NSObject {
  protected static final Log log = LogFactory.getLog("OGoOperation");

  protected File fileWritten;
  protected File fileMoved;
  
  public OGoFileWriteTransaction(final File _f) {
    this.fileWritten = _f;
  }
  
  
  /* top level */
  
  public Exception performAtomicWrite(Object _content) {
    if (this.fileWritten != null && this.fileWritten.exists()) {
      /* move away old file for rollback */
      File f = this.fileWritten;
      File oldFile = new File(f.getAbsolutePath() + "-txwrite");
      if (!f.renameTo(oldFile)) {
        log.error("could not move old Note file: " + f + " to " + oldFile);
        return new NSException("could not move away old file!");
      }
      this.fileMoved = oldFile;
    }

    return this.writeContent(_content);
  }
  
  /* writing */
  
  public Exception writeContent(final Object _content) {
    return this.writeContentToFile(_content, this.fileWritten);
  }
  
  public Exception writeContentToFile(final Object _content, final File f) {
    Exception error = null;
    
    if (_content instanceof String)
      error = UString.writeToFile((String)_content, "utf-8", f, false);
    else if (_content instanceof byte[])
      error = UData.writeToFile((byte[])_content, f, false);
    else if (_content instanceof File) {
      File sourceFile = (File)_content;
      
      if (!sourceFile.exists()) {
        error = new NSException
          ("Content source file does not exist: " + sourceFile);
      }
      else
        error = copyfile(sourceFile, f);
    }
    else if (_content instanceof URL)
      error = copyurl((URL)_content, f);
    else if (_content != null)
      error = new NSException("Unexpected content object for document!");
    else
      error = new NSException("Got no content for document object!");
    
    return error;
  }
  
  
  /* transaction ops */

  public Exception rollback() {
    if (this.fileWritten != null) {
      /* a record was created */
      if (!this.fileWritten.delete()) {
        log.error("could not delete file written: " + this.fileWritten);
        return new NSException("could not delete Note file before rollback");
      }
    }
    
    if (this.fileMoved != null) {
      if (!this.fileMoved.renameTo(this.fileWritten)) {
        log.error("could not restored old Note file: " + this.fileMoved +
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
        return new NSException("could not delete old Note file in commit");
      }
    }
    return null;
  }


  /* helper methods */

  public static Exception copyfile(final File _in, final File _out) {
    // TBD: rewrite to use NIO
    try {
      final InputStream  in  = new FileInputStream(_in);
      final OutputStream out = new FileOutputStream(_out);
      
      final byte buf[] = new byte[4096];
      int len;

      while ((len = in.read(buf)) > 0)
        out.write(buf, 0, len);
      
      out.close();
      in.close();
    }
    catch (Exception e) {
      return e;
    }
    return null;
  }
  public static Exception copyurl(final URL _in, final File _out) {
    // TBD: rewrite to use NIO
    try {
      final InputStream  in  = _in.openStream();
      final OutputStream out = new FileOutputStream(_out);
      
      final byte buf[] = new byte[4096];
      int len;

      while ((len = in.read(buf)) > 0)
        out.write(buf, 0, len);
      
      out.close();
      in.close();
    }
    catch (Exception e) {
      return e;
    }
    return null;
  }

}
