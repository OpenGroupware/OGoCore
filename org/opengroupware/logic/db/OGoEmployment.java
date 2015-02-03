/*
  Copyright (C) 2007-2009 Helge Hess

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
 * OGoEmployment
 * <p>
 * Represents a connection between a company and a person.
 * Such a connection has a 'function' assigned,
 * and the connection can be time constrained (hence this object implements
 * IOGoTimeRangeObject).
 * 
 * @author helge
 */
public class OGoEmployment extends OGoObject implements IOGoTimeRangeObject {
  
  protected Number companyId;
  protected Number personId;
  protected String function;
  
  protected Date   startDate;
  protected Date   endDate;
  
  /* relationships */
  public    OGoCompany company;
  public    OGoPerson  person;
  
  
  public OGoEmployment(final EOEntity _entity) {
    super(_entity);
  }
  
  /* accessors */
  
  public void setCompanyId(final Number _id) {
    if (_id == null)
      log.warn("OGoEmployment: attempt to set companyId to null: " + this);
    this.companyId = _id;
  }
  public Number companyId() {
    return this.companyId;
  }
  
  public void setPersonId(final Number _id) {
    if (_id == null)
      log.warn("OGoEmployment: attempt to set personId to null: " + this);
    this.personId = _id;
  }
  public Number personId() {
    return this.personId;
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
  
  /**
   * Returns whether the objects time constraints match the given date. If the
   * object has no timerange, it always returns true.
   *  
   * @return true if the _date is in the objects timerange
   */
  public boolean isActiveAtDate(final Date _date) {
    if (this.startDate == null && this.endDate == null)
      return true;
    
    NSTimeRange r = this.timeRange();
    return r != null ? r.containsDate(_date) : null;
  }
  /**
   * Returns whether the objects time constraints match the current time. If the
   * object has no timerange, it always returns true.
   * Careful with using this, to be transactionally safe, you should use one
   * distinct timestamp with isActiveAtDate() instead of isCurrent()!
   *  
   * @return true if the _date is in the objects timerange
   */
  public boolean isCurrent() {
    if (this.startDate == null && this.endDate == null)
      return true;
    
    return this.isActiveAtDate(new Date());
  }
}
