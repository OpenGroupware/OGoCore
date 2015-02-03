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
package org.opengroupware.logic.db;

import java.io.File;
import java.util.Date;

import org.getobjects.eoaccess.EOEntity;
import org.getobjects.foundation.NSTimeRange;
import org.getobjects.foundation.UObject;
import org.opengroupware.logic.core.IOGoFileManager;
import org.opengroupware.logic.core.OGoDatabaseFileManager;
import org.opengroupware.logic.core.OGoFileSystemFileManager;

/**
 * OGoProject
 * <p>
 * An OGo project is a collection of contacts, tasks, documents, etc. A better
 * term is <code>workspace</code>.
 * Though the term is application specific. Eg some also call it a 'Case' or
 * a 'Process'. Really depends, its 'just' an object which lumps together a
 * set of links plus the storage of documents.
 * We stick with the term 'project' for historic reasons ...
 *
 * <h4>Permissions</h4>
 * <p>
 * When objects get associated with a project, the permission setup of the
 * project often affects the object. Basically a connected object cannot
 * override the permission settings of the project (exception: contacts).
 * <p>
 * The basic permissions are:
 * <ul>
 *   <li><code>r</code> - may read items
 *   <li><code>w</code> - may write items
 *   <li><code>i</code> - may create items
 *   <li><code>d</code> - may delete items
 *   <li><code>m</code> - manager, access to everything
 * </ul>
 * 
 * <p>
 * @author helge
 */
public class OGoProject extends OGoObject implements IOGoTimeRangeObject {
  
  public    Number object_version; /* NULL for old OGo projects */
  
  protected String name; /* arbitary name for the project, not unique */
  protected String code; /* unique code of the project */
  
  protected int    ownerId;
  protected Number teamId;  /* could be a 'team' project */
  
  public String  blobStorageUrl; /* selects the document backend to be used */
  
  // 00_sleeping, 05_processing, 10_out_of_date, 30_archived
  public String  status;
  // 00_invoiceProject, 05_historyProject
  public String  kind;
  
  protected Date startDate;
  protected Date endDate;
  // isCompanyProject (is_fake)
  
  public OGoProject(final EOEntity _entity) {
    super(_entity);
  }
  
  
  /* accessors */
  
  public void setName(final String _value) {
    this.name = _value;
  }
  public String name() {
    return this.name;
  }
  
  public void setCode(final String _value) {
    this.code = _value;
  }
  public String code() {
    return this.code;
  }
  
  public void setStartDate(final Object _date) {
    this.startDate = UObject.dateValue(_date);
  }
  public Object startDate() {
    return this.startDate;
  }
  
  public void setEndDate(final Object _date) {
    this.endDate = UObject.dateValue(_date);
  }
  public Object endDate() {
    return this.endDate;
  }
  
  public void setOwnerId(final Number _value) {
    this.ownerId = _value != null ? _value.intValue() : -1;
  }
  public Number ownerId() {
    return this.ownerId;
  }
  
  public void setTeamId(final Number _value) {
    this.teamId = _value;
  }
  public Number teamId() {
    return this.teamId;
  }
  
  
  /* Calendaring */
  
  public void setTimeRange(final NSTimeRange _range) {
    this.setStartDate(_range != null ? _range.fromDate() : null);
    this.setEndDate  (_range != null ? _range.toDate()   : null);
  }
  public NSTimeRange timeRange() {
    // TBD: cache?
    final Date sd = (Date)this.startDate();
    final Date ed = (Date)this.endDate();
    if (sd == null && ed == null) return null;
    return new NSTimeRange(sd, ed);
  }

  /**
   * Returns whether the objects time constraints match the given date. If the
   * object has no timerange, it always returns true.
   *  
   * @return true if the _date is in the objects timerange
   */
  public boolean isActiveAtDate(final Date _date) {
    if (this.startDate == null && this.endDate == null)
      return true;
    if (_date == null)
      return false;
    
    NSTimeRange r = this.timeRange();
    return r != null ? r.containsDate(_date) : true;
  }
  /**
   * Returns whether the objects time constraints match the current time. If the
   * object has no timerange, it always returns true.
   *  
   * @return true if the _date is in the objects timerange
   */
  public boolean isCurrent() {
    if (this.startDate == null && this.endDate == null)
      return true;
    
    return this.isActiveAtDate(new Date());
  }
  
  /* permissions */
  
  /**
   * Applies contact field restrictions.
   * <ul>
   *   <li><code>r</code> - may read items
   *   <li><code>w</code> - may write items
   *   <li><code>i</code> - may create items
   *   <li><code>d</code> - may delete items
   *   <li><code>m</code> - manager, access to everything
   * </ul>
   */
  public void applyPermissions(final String _perms) {
    /* 'r' and 'w' imply access to everything */
    for (int i = _perms.length() - 1; i >= 0; i--) {
      switch (_perms.charAt(i)) {
        case 'r':
          if (_perms.indexOf('w') < 0 && _perms.indexOf('m') < 0) {
            /* remove snapshot, we are read-only */
            this.snapshot = null;
          }
          return;
        case 'w':
        case 'm':
          return;
      }
    }
    
    /* remove snapshot, we are read-only */
    this.snapshot = null;
    
    /* remove the other stuff */
    this.name = null;
    this.code = null;
    this.blobStorageUrl = null;
    // keep ownerId,teamId?
  }
  
  
  /* file manager */
  
  /**
   * Returns an {@link IOGoFileManager} object for the project. There are
   * different types of storage backends.
   * Which one is used is selected using the schema of the
   * 'blobStorageUrl'.
   * 
   * @return an {@link IOGoFileManager} object
   */
  public IOGoFileManager createFileManager() {
    if (this.blobStorageUrl == null ||
        this.blobStorageUrl.startsWith("skyrix://")) {
      return new OGoDatabaseFileManager(this.oDatabase(), this.id().intValue());
    }
    
    if (this.blobStorageUrl.startsWith("file://")) {
      /* Note: I don't think OGo uses real URL parsing ... */
      String path = this.blobStorageUrl.substring(7);
      File   file = new File(path);
      if (file == null || !file.isDirectory()) {
        log().error("filesystem project storage URL is invalid: " +
            this.blobStorageUrl);
        return null;
      }
      
      return new OGoFileSystemFileManager(file);
    }
    
    log().error("unknown/unsupported storage URL: " + this.blobStorageUrl);
    return null;
  }


  /* legacy */
  
  @Override
  public String entityNameInOGo5() {
    return "Project";
  }
}
