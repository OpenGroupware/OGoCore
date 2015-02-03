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

import java.util.Calendar;
import java.util.Collection;
import java.util.Date;

import org.getobjects.eoaccess.EOEntity;
import org.getobjects.eocontrol.EOAndQualifier;
import org.getobjects.eocontrol.EOKeyValueQualifier;
import org.getobjects.eocontrol.EONotQualifier;
import org.getobjects.eocontrol.EOOrQualifier;
import org.getobjects.eocontrol.EOQualifier;
import org.getobjects.eocontrol.EOQualifierEvaluation;
import org.getobjects.foundation.UObject;

/**
 * OGoCompany
 * <p>
 * Represents a company in the OGo database. This is stored in the
 * 'enterprise' table and is represented by the 'Enterprise' entity
 * in the Objective-C implementation!
 * 
 * <p>
 * Per default an OGo company has those phone numbers types:
 * <ul>
 *   <li>01_tel
 *   <li>02_tel
 *   <li>10_fax
 * </ul>
 * And those address types:
 * <ul>
 *   <li>ship
 *   <li>bill
 * </ul>
 * <p>
 * @author helge
 */
public class OGoCompany extends OGoContact implements Comparable<OGoCompany> {
  
  protected String name;
  protected Date   birthday;
  protected Date   dayofdeath;

  public OGoCompany(final EOEntity _entity) {
    super(_entity);
  }

  /* accessors */
  
  public void setIsCompany(final boolean _flag) {
    if (!_flag)
      log().error("attempt to remove isCompany flag: " +this);
  }
  public boolean isCompany() {
    return true;
  }

  public void setBirthday(final Object _v) {
    if (_v instanceof Date)
      this.birthday = (Date)_v;
    else if (_v instanceof Calendar)
      this.birthday = ((Calendar)_v).getTime();
    else if (_v == null || UObject.isEmpty(_v))
      this.birthday = null;
    else
      log.error("attempt to set invalid value for birthday key: " + _v);
  }
  public Object birthday() {
    return this.birthday;
  }
  
  public void setDayofdeath(final Object _v) {
    if (_v instanceof Date)
      this.dayofdeath = (Date)_v;
    else if (_v instanceof Calendar)
      this.dayofdeath = ((Calendar)_v).getTime();
    else if (_v == null || UObject.isEmpty(_v))
      this.dayofdeath = null;
    else
      log.error("attempt to set invalid value for dayofdeath key: " + _v);
  }
  public Object dayofdeath() {
    return this.dayofdeath;
  }
  
  public void setName(final String _value) {
    if (this.name == _value)
      return;
    
    if (this.name != null && UObject.isEmpty((_value))) {
      log.error("attempt to set empty value for 'name' key: " + _value +
          " (value is: " + this.name + ")");
      return;
    }
    
    this.name = _value;
  }
  public String name() {
    return this.name;
  }

  
  /* employments */
  
  @SuppressWarnings("unchecked")
  public Collection<OGoEmployment> currentEmployments() {
    // you can also do this in the template, eg using:
    //   <wo:for filter="endDate IS NULL OR endDate > $now"
    final EOQualifierEvaluation q = new EOOrQualifier(
        endDateNull,
        new EOKeyValueQualifier("endDate",
            EOQualifier.ComparisonOperation.GREATER_THAN, new Date()));
    return this.filterRelationship("employments", q);
  }
  @SuppressWarnings("unchecked")
  public Collection<OGoEmployment> previousEmployments() {
    final EOQualifierEvaluation q = new EOAndQualifier(
        endDateNotNull,
        new EOKeyValueQualifier("endDate",
            EOQualifier.ComparisonOperation.LESS_THAN_OR_EQUAL, new Date()));
    return this.filterRelationship("employments", q);
  }

  /* birth and death */
  
  public boolean didOpenForBusiness() {
    Object v = this.birthday();
    if (v == null)
      return true; /* no birthday recorded, assume the person is born */
    
    if (v instanceof Date) {
      Date now = new Date();
      return ((Date)v).before(now);
    }
    if (v instanceof Calendar) {
      Date now = new Date();
      return ((Calendar)v).getTime().before(now);
    }
    
    log.error("unexpected value in birthday field: " + v);
    return false;
  }
  public boolean isClosed() {
    Object v = this.dayofdeath();
    if (v == null)
      return false; /* did not die yet */
    
    if (v instanceof Date) {
      Date now = new Date();
      return ((Date)v).before(now);
    }
    if (v instanceof Calendar) {
      Date now = new Date();
      return ((Calendar)v).getTime().before(now);
    }
    
    log.error("unexpected value in dayofdeath field: " + v);
    return false;
  }
  public boolean isInBusiness() {
    if (!this.didOpenForBusiness()) return false;
    return !this.isClosed();
  }
  
  /**
   * Returns the age of the company, derived from the birthday and from the
   * day of death, if the company closed.
   * 
   * @return the age of the company, or -1 if no birthday is set
   */
  public int age() {
    // TBD: the code needs to be checked for edge conditions (eg birthdays! ;-)
    Calendar scal;
    Calendar ecal;
    Date start; // TBD: remove those
    Date end;
    Object v = this.birthday();
    if (v == null)
      return -1; /* no birthday recorded, can't calculate age */
    
    if (v instanceof Date)
      start = (Date)v;
    else
      start = ((Calendar)v).getTime();
    
    v = this.dayofdeath();
    if (v instanceof Date)
      end = (Date)v;
    else if (v instanceof Calendar)
      end = ((Calendar)v).getTime();
    else
      end = new Date();
    
    if (end.before(start))
      return -1; /* end before start?? */
    
    // TBD: need locale/timezone!? Hm. Not necessarily. Maybe for timezone
    //      switches the calc is wrong - if the timezone definition changed
    //      for the birthday.
    scal = Calendar.getInstance();
    ecal = Calendar.getInstance();
    scal.setTime(start);
    ecal.setTime(end);
    
    /* normalize */
    scal.set(Calendar.HOUR,   0);
    scal.set(Calendar.MINUTE, 0);
    scal.set(Calendar.SECOND, 0);
    ecal.set(Calendar.HOUR,   0);
    ecal.set(Calendar.MINUTE, 0);
    ecal.set(Calendar.SECOND, 0);
    
    /* compare years */
    int syear = scal.get(Calendar.YEAR);
    int eyear = ecal.get(Calendar.YEAR);
    if (syear == eyear) return 0; /* newborn */
    if (syear >  eyear) return -1; /* invalid */
    
    int age = eyear - syear;
    scal.set(Calendar.YEAR, eyear); // move day/month to same year
    if (scal.after(ecal))
      age--; /* did not have birthday yet */
    
    return age;
  }
  
  
  /* addresses, convenience accessors, be prepared for other/missing types! */
  
  public OGoAddress shipAddress() {
    return this.addressWithKey("ship");
  }
  public OGoAddress billAddress() {
    return this.addressWithKey("bill");
  }
  public OGoAddress shipOrBillAddress() {
    OGoAddress adr = this.shipAddress();
    return UObject.isNotEmpty(adr) ? adr : this.billAddress();
  }  
  public OGoAddress billOrShipAddress() {
    OGoAddress adr = this.billAddress();
    return UObject.isNotEmpty(adr) ? adr : this.shipAddress();
  }
  
  
  /* compare */
  
  public int compareTo(final OGoCompany _other) {
    if (_other == this) return 0;
    if (_other == null) return 1;
    
    String mn = (String)this.valueForKey("name");
    String on = (String)_other.valueForKey("name");
    if (mn == on)   return 0;
    if (on == null) return 1;
    if (mn == null) return -1;
    
    int res = mn.compareTo(on);
    if (res != 0) return res;

    /* same name!, compare number */
    mn = (String)this.valueForKey("number");
    on = (String)_other.valueForKey("number");
    if (mn == on)   return 0;
    if (on == null) return 1;
    if (mn == null) return -1;
    return mn.compareTo(on);
  }
  
  
  /* legacy */
  
  @Override
  public String entityNameInOGo5() {
    return "Enterprise";
  }

  
  /* misc */
  
  private static final EOQualifier endDateNull =
    new EOKeyValueQualifier("endDate", null);
  private static final EOQualifier endDateNotNull =
    new EONotQualifier(endDateNull);
}
