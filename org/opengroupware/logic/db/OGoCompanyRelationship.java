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
package org.opengroupware.logic.db;

import java.util.Date;

import org.getobjects.eoaccess.EOEntity;
import org.getobjects.foundation.NSTimeRange;
import org.getobjects.foundation.UObject;

/**
 * OGoCompanyRelationship
 * <p>
 * Represents a connection between a company and another company. Eg it could
 * be a subsidiary (function=Subsidiary).
 * 
 * @author helge
 */
public class OGoCompanyRelationship extends OGoObject
  implements IOGoTimeRangeObject
{
  
  protected Number parentId;
  protected Number companyId;
  protected String function;
  
  protected Date startDate;
  protected Date endDate;
  
  public OGoCompanyRelationship(final EOEntity _entity) {
    super(_entity);
  }
  
  /* accessors */
  
  public void setParentId(final Number _id) {
    if (_id == null)
      log.warn("attempt to set parentId to null");
    this.parentId = _id;
  }
  public Number parentId() {
    return this.parentId;
  }
  
  public void setCompanyId(final Number _id) {
    if (_id == null)
      log.warn("attempt to set companyId to null");
    this.companyId = _id;
  }
  public Number companyId() {
    return this.companyId;
  }
  
  public void setFunction(final String _function) {
    if (_function == this.function)
      return;
    
    this.function = _function != null ? _function.trim() : null;
  }
  public String function() {
    return this.function;
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

  /* time ranges */
  
  public void setTimeRange(final NSTimeRange _range) {
    this.startDate = _range != null ? _range.fromDate() : null;
    this.endDate   = _range != null ? _range.toDate()   : null;
  }
  public NSTimeRange timeRange() {
    if (this.startDate == null && this.endDate == null) return null;
    return new NSTimeRange(this.startDate, this.endDate);
  }
}
