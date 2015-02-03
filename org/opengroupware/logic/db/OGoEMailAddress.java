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

import java.util.Arrays;
import java.util.regex.Pattern;

import org.getobjects.eoaccess.EOEntity;
import org.getobjects.foundation.UList;
import org.getobjects.foundation.UString;

/**
 * OGoEMailAddress
 * <p>
 * Represents an email address attached to a person. EMails are stored in the
 * company_value table (name starts with 'email' and the value type is '3').
 * <p>
 * We allow the user to store invalid email addresses. 
 * <p>
 * vCard labels are stored directly in the 'label' field.
 */
public class OGoEMailAddress extends OGoObject
  implements IOGoVCardLabeledObject, IOGoContactChildObject
{
  
  protected String key;   /* email1, email2 */
  protected String value; /* hh@skyrix.com */
  protected String label; /* WORK;PREF (separated by semicolon!) */
  
  public OGoEMailAddress(final EOEntity _entity) {
    super(_entity);
  }
  
  /* accessors */
  
  public void setKey(final String _key) {
    if (key != null && !_key.startsWith("email"))
      log.warn("setting key of email address w/o 'email' prefix: " + key);
    this.key = _key;
  }
  public String key() {
    return this.key;
  }
  
  public void setValue(final String _value) {
    if (this.value == _value)
      return;
    
    if (_value == null) {
      this.value = null;
      return;
    }
    
    this.value = _value.trim();
    if (this.value.length() == 0) this.value = null;
  }
  public String value() {
    return this.value;
  }
  
  public void setLabel(final String _value) {
    if (this.label == _value)
      return;
    
    if (_value == null) {
      this.label = null;
      return;
    }
    
    this.label = _value.trim();
    if (this.label.length() == 0) this.label = null;
  }
  public String label() {
    return this.label;
  }
  
  public void setType(final int _type) {
    if (_type != 3)
      log.warn("attempt to set type of email address to " + _type);
  }
  public int type() {
    return 3; /* always 3 */
  }
  
  
  /* values */
  
  /**
   * Note: we return true even if an info is set. This just checks the
   * number value.
   */
  @Override
  public boolean isEmpty() {
    return this.value == null || this.value.trim().length() == 0;
  }
  
  public boolean isValueValid() {
    if (this.value == null || this.value.length() == 0)
      return false;
    
    return emailPattern.matcher(this.value).matches();
  }
  
  
  /* permissions */
  
  public void applyPermissions(String _perms) {
    // TBD: complete me
    
    if (_perms.length() == 0) {
      // TBD: use a special 'forbidden' value
      this.key   = null;
      this.value = null;
      this.label = null;
    }
  }
  
  
  /* labels */
  
  private static String[] vCardLabels = { "WORK", "HOME", "OTHER" };
  private static String[] workLabels = { "WORK" };
  
  public boolean isPreferred() {
    return this.label != null ? (this.label.indexOf("PREF") != -1) : false;
  }
  
  public String primaryVCardLabel() {
    if (this.label == null || this.label.length() == 0)
      return vCardLabels[0];
    if (this.label.equals("PREF"))
      return vCardLabels[0];
    
    String[] types = this.label.split("\\;");
    if (types == null || types.length == 0)
      return vCardLabels[0];
    
    /* this isn't PREF, we checked it above */
    if (types.length == 1)
      return types[0];

    for (String vCardLabel: vCardLabels) {
      if (UList.contains(types, vCardLabel))
        return vCardLabel;
    }
    
    /* look for the first non PREF label */
    for (String type: types) {
      if (!type.equals("PREF"))
        return type;
    }

    /* only PREF labels? ;-) */
    return vCardLabels[0];
  }
  
  public String[] vCardLabels() {
    if (this.label == null || this.label.length() == 0)
      return workLabels;
    
    String[] ls = this.label.split("\\;");
    Arrays.sort(ls);
    return ls;
  }
  public String vCardLabelsAsString() {
    return UString.componentsJoinedByString(this.vCardLabels(), ",");
  }
  
  public void setVCardLabels(String[] _labels) {
    /* Note: internally we use ';' as the separator, in the frontend we use
     *       a comma (',').
     */
    String internalFormat = UString.componentsJoinedByString(_labels, ";"); 
    this.takeValueForKey(internalFormat, "label");
  }
  public void setVCardLabelsAsString(String _labels) {
    this.setVCardLabels(_labels != null ? _labels.split(",") : null);
  }
  
  
  /* browser links */

  /**
   * Returns a link suitable for use in an HTML anker.
   * 
   * @return a browser link, like "mailto:info@skyrix.de"
   */
  public String asBrowserLink() {
    String v = this.value();
    if (v == null) return null;
    
    v = v.trim();
    v = UString.stringByEncodingURLComponent(v, null /* utf-8 */);
    return v.length() > 0 ? ("mailto:" + v) : null;
  }
  
  
  /* validation */
  
  /* Note: strictly speaking an address w/o an @ is still a valid address! */
  public static final String emailRegEx = "(\\w+)@(\\w+\\.)(\\w+)(\\.\\w+)*";
  public static final Pattern emailPattern = Pattern.compile(emailRegEx);
}
