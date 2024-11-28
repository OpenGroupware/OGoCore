/*
  Copyright (C) 2007-2024 Helge Hess

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
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.getobjects.eoaccess.EOEntity;
import org.getobjects.foundation.NSException;
import org.getobjects.foundation.UString;

/**
 * Represents a phone number attached to a person or company. Phone numbers
 * are stored in the 'telephone' table.
 * <p>
 * Note that we allow the user to store invalid phone numbers.
 * <p>
 * OGo Key Types:
 * <ul>
 *   <li>tel         (WORK), sequence start: 01
 *   <li>tel_funk    (WORK), sequence start: 03
 *   <li>tel_private (HOME), sequence start: 05
 *   <li>fax         (WORK), sequence start: 10
 *   <li>fax_private (HOME), sequence start: 15
 *   <li>pager       (WORK), sequence start: 30
 *   <li>other1      (WORK), sequence start: 31
 *   <li>other2      (WORK), sequence start: 32
 * </ul>
 * <p>
 * OGo Key Values
 * <ul>
 *   <li>01_tel (WORK)
 *   <li>02_tel (WORK)
 *   <li>03_tel_funk (WORK)
 *   <li>05_tel_private (HOME)
 *   <li>10_fax (WORK)
 *   <li>15_fax_private (HOME)
 *   <li>30_pager (WORK)
 *   <li>31_other1 (WORK)
 *   <li>32_other2 (WORK)
 * </ul>
 * <p>
 * <em>Important!</em>
 * The keys must be unique for a record since OGo often accesses them using a
 * dictionary keyed on the key (hence the name ;-)
 * <p>
 * vCard samples:
 * <ul>
 *   <li>V:FAX,MODEM,VIDEO
 * </ul>
 */
public class OGoPhoneNumber extends OGoObject
  implements IOGoVCardLabeledObject, IOGoContactChildObject
{
  protected Number companyId;
  
  protected String key;   /* 01_tel, 10_fax ('type' column) */
  protected String value; /* 2838712-18271 */
  protected String info;  /* some string */
  protected String realNumber;
  
  public String keyType; /* this is not a mapped field, its used internally */

  public OGoPhoneNumber(EOEntity _entity) {
    super(_entity);
  }

  /* accessors */
  
  public void setCompanyId(final Number _pkey) {
    if (_pkey == null && this.companyId != null)
      this.log().warn("attempt to reset companyId of phone: " + this);
    
    this.companyId = _pkey;
  }
  public Number companyId() {
    return this.companyId;
  }
  
  public void setKey(final String _key) {
    this.key = _key;
  }
  public String key() {
    return this.key;
  }
  
  public void setValue(final String _value) {
    this.value = _value != null ? _value.trim() : null;
  }
  public String value() {
    return this.value;
  }
  
  public void setInfo(final String _value) {
    this.info = _value != null ? _value.trim() : null;
  }
  public String info() {
    return this.info;
  }
  
  public void setRealNumber(final String _value) {
    this.realNumber = _value != null ? _value.trim() : null;
  }
  public String realNumber() {
    return this.realNumber;
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
    
    return phonePattern.matcher(this.value).matches();
  }
  
  
  /* permissions */
  
  public void applyPermissions(String _perms) {
    if (_perms.length() == 0) {
      // TBD: use a special 'forbidden' value
      this.key   = null;
      this.value = null;
      this.info  = null;
    }
  }

  
  /* vCard type */
  
  public boolean isPreferred() {
    return this.key != null ? (this.key.indexOf("PREF") != -1) : false;
  }

  /**
   * Assigns a specific key type to the phone number. The key type is something
   * like 'tel' or 'fax'. A full key adds a number in front, eg '01_tel' or
   * '10_fax'.
   * 
   * @param _keyType - the type of the key, eg 'tel' or 'fax'
   */
  public void setKeyType(final String _keyType) {
    this.keyType = _keyType;
  }
  public void setKeyUsingKeyTypeAndSequence(final String _keyType, int _seq) {
    this.takeValueForKey(keyUsingTypeAndSequence(_keyType, _seq), "key");
  }
  public static String keyUsingTypeAndSequence(String _keyType, int _seq) {
    if (_keyType == null)
      return null;
    
    /* 01_tel, 30_pager */
    _seq = OGoPhoneNumber.startIndexForKeyType(_keyType) + _seq;
    
    final StringBuilder sb = new StringBuilder(32);
    if (_seq < 10) sb.append('0');
    sb.append(_seq);
    sb.append('_');
    sb.append(_keyType);
    return sb.toString();
  }
  
  /**
   * Returns the type of phone number key. The key is something like 01_tel
   * or 10_fax, the type is just the 'tel' or the 'fax' part.
   * 
   * @return the type of the phone key
   */
  public String keyType() {
    if (this.keyType != null)
      return this.keyType;
    
    String k = this.key;
    if (k == null || k.length() == 0)
      return "tel"; /* makes no sense to return no default? */
    
    if (Character.isDigit(k.charAt(0))) {
      // 01_tel => tel
      int idx = k.indexOf('_');
      k = idx > 0 ? k.substring(idx + 1) : k;
    }
    return k;
  }
  
  /**
   * Returns 'WORK', 'HOME' or 'OTHER' depending on the type of the address.
   * <p>
   * OGo can store arbitary vCard types with a V: prefix, eg:
   * <pre>V:OTHER,PREF,WORK</pre>
   * or it uses one of its predefined keys:
   * <ul>
   *   <li>01_tel (WORK)
   *   <li>02_tel (WORK)
   *   <li>03_tel_funk (WORK)
   *   <li>05_tel_private (HOME)
   *   <li>10_fax (WORK)
   *   <li>15_fax_private (HOME)
   *   <li>30_pager (WORK)
   *   <li>31_other1 (WORK)
   *   <li>32_other2 (WORK)
   * </ul>
   */
  public String primaryVCardLabel() {
    // TBD: improve
    if (this.key == null)
      return "WORK";
    
    if (this.key.startsWith("V:")) {
      // TBD: isn't that stored in the info field?
      String labels = this.key.toUpperCase();
      if (labels.contains("WORK"))
        return "WORK";
      if (labels.contains("HOME"))
        return "HOME";
      
      return "OTHER";
    }
    
    /* standard OGo type (01_tel, 10_fax) */
    
    if (this.key.contains("_private"))
      return "HOME";
    
    return "WORK";
  }
  
  public String[] vCardLabels() {
    return vCardLabelsForKeyType(this.keyType());
  }
  public String vCardLabelsAsString() {
    return UString.componentsJoinedByString(this.vCardLabels(), ",");
  }
  
  public void setVCardLabels(String[] _labels) {
    this.setKeyType(OGoPhoneNumber.keyTypeForVCardLabels(_labels));
  }
  public void setVCardLabelsAsString(String _labels) {
    this.setVCardLabels(_labels != null ? _labels.split(",") : null);
  }
  
  /**
   * Map labels to an internal OGo key type. The type is just the suffix of the
   * key, it usually needs to be prefixed with the phone sequence
   * (eg tel=>01_tel).
   * <p>
   * Mappings ("PREF" is ignored)
   * <ul>
   *   <li>PREF       => tel (just PREF, nothing else)
   *   <li>WORK       => tel
   *   <li>HOME       => tel_private
   *   <li>FAX        => fax
   *   <li>CELL       => tel_funk
   *   <li>PAGER      => pager
   *   <li>WORK,VOICE => tel
   *   <li>HOME,VOICE => tel_private
   *   <li>WORK,FAX   => fax
   *   <li>HOME,FAX   => fax_private
   *   <li>WORK,CELL  => tel_funk
   *   <li>HOME,CELL  => tel_funk_private ***
   *   <li>WORK,PAGER => pager
   * </ul>
   * 
   * @param _labels
   * @return
   */
  public static String keyTypeForVCardLabels(String[] _labels) {
    if (_labels == null || _labels.length == 0)
      return null;
    
    /* inefficient, but hey! ;-) */
    Set<String> keys = new HashSet<String>(_labels.length);
    for (int i = (_labels.length - 1); (i >= 0); i--) {
      if (_labels[i] != null)
        keys.add(_labels[i].toUpperCase());
    }
    keys.remove("PREF");
    
    String type = null;
    int count = keys.size();
    if (count == 0) /* was just PREF (or null ...) */
      type = "tel";
    else if (count < 3) {
      if (keys.contains("FAX")) {
        if (count == 1)                 type = "fax";
        else if (keys.contains("WORK")) type = "fax";
        else if (keys.contains("HOME")) type = "fax_private";
      }
      else if (keys.contains("CELL")) {
        if (count == 1)                  type = "tel_funk";
        else if (keys.contains("WORK"))  type = "tel_funk";
        else if (keys.contains("VOICE")) type = "tel_funk";
        else if (keys.contains("HOME"))  type = "tel_funk_private";
      }
      else if (keys.contains("PAGER")) {
        if (count == 1)                 type = "pager";
        else if (keys.contains("WORK")) type = "pager";
      }
      else if (keys.contains("VOICE")) {
        if (count == 1)                 type = "tel";
        else if (keys.contains("WORK")) type = "tel";
        else if (keys.contains("HOME")) type = "tel_private";
      }
      else if (count == 1) {
        /* some options are covered above, but for consistency */
        if      (keys.contains("WORK"))  type = "tel";
        else if (keys.contains("VOICE")) type = "tel";
        else if (keys.contains("HOME"))  type = "tel_private";
        else if (keys.contains("CELL"))  type = "tel_funk";
        else if (keys.contains("FAX"))   type = "fax";
        else if (keys.contains("PAGER")) type = "pager";
      }
    }
    
    if (type == null)
      type = "V:" + UString.componentsJoinedByString(keys.toArray(), ",");
    
    return type;
  }
  
  public static String[] vCardLabelsForKeyType(String _type) {
    if (_type == null)
      return null;
    
    if (_type.startsWith("V:")) {
      /* eg: V:WORK,CELL,PREF */
      String[] ls = _type.substring(2).split(",");
      Arrays.sort(ls);
      return ls;
    }
    
    if (_type.equals("tel"))              return labels_tel;
    if (_type.equals("tel_funk"))         return labels_tel_funk;
    if (_type.equals("tel_funk_private")) return labels_tel_funk_private;
    if (_type.equals("tel_private"))      return labels_tel_private;
    if (_type.equals("fax"))              return labels_fax;
    if (_type.equals("fax_private"))      return labels_fax_private;
    if (_type.equals("pager"))            return labels_pager;
    if (_type.equals("other1"))           return labels_other1;
    if (_type.equals("other2"))           return labels_other2;

    /* error, unexpected key (can happen if the user mapped additional ones) */
    log.warn("unexpected internal phone type, returned as X-OGo-type: "+_type);
    return new String[] { "X-OGo-" + _type };
  }
  
  /* OK, this one is tricky. Should we explicitly add 'VOICE'? Evolution
   * and Mozilla do render 'VOICE', but Apple Addressbook and Kontact do
   * not.
   * We'll got for the Apple variants.
   * 
   * Important: keep that sorted, alphabetically.
   */
  private static final String[] labels_tel              = { "WORK" };
  private static final String[] labels_tel_funk         = { "CELL" };
  private static final String[] labels_tel_private      = { "HOME" };
  private static final String[] labels_fax              = { "FAX", "WORK" };
  private static final String[] labels_fax_private      = { "FAX", "HOME" };
  private static final String[] labels_pager            = { "PAGER" };
  private static final String[] labels_other1           = { "OTHER" };
  private static final String[] labels_other2           = { "OTHER" };
  private static final String[] labels_tel_funk_private = { "CELL", "HOME" };
  
  /**
   * Returns where keys for a given type _should_ start counting. This is for
   * improved OGo compatibility.
   * 
   * @param  _type - an internal type like 'tel'
   * @return -1 if the _type is null, or the start index otherwise (1 and up)
   */
  public static int startIndexForKeyType(String _type) {
    if (_type == null)
      return -1;
    
    if (_type.equals("tel"))         return  1;
    if (_type.equals("tel_funk"))    return  3;
    if (_type.equals("tel_private")) return  5;
    if (_type.equals("fax"))         return 10;
    if (_type.equals("fax_private")) return 15;
    if (_type.equals("pager"))       return 30;
    if (_type.equals("other1"))      return 31;
    if (_type.equals("other2"))      return 32;
    
    return 100;
  }
  
  
  public static Exception reworkKeySequences
    (List<IOGoContactChildObject> _existingObjects,
     List<IOGoContactChildObject> _newObjects,
     Collection<Number> _deletedIds)
  {
    Map<String, Number> keyTypeToSequence = new HashMap<String, Number>(8);
    
    /* Walk over existing items. Those are properly ordered in the fetch */

    if (_existingObjects != null) {
      for (IOGoContactChildObject item: _existingObjects) {
        /* if the item is deleted, it does not consume a slot anymore */
        if (_deletedIds != null && _deletedIds.contains(item.id()))
          continue;

        OGoPhoneNumber typed = (OGoPhoneNumber)item;

        String keyType = typed.keyType();
        if (keyType == null) {
          log.error("item has no keyType: " + item);
          return new NSException("item has no keytype"); // TBD
        }

        Number sequence = keyTypeToSequence.get(keyType);
        typed.setKeyUsingKeyTypeAndSequence
          (keyType, sequence != null ? sequence.intValue() : 0);

        sequence = sequence == null
        ? UOGoObject.int1 : Integer.valueOf(sequence.intValue()+1);
        keyTypeToSequence.put(keyType, sequence);
      }
    }
    
    /* next process new items */

    if (_newObjects != null) {
      for (IOGoContactChildObject item: _newObjects) {
        OGoPhoneNumber typed = (OGoPhoneNumber)item;

        String keyType = typed.keyType();
        if (keyType == null) {
          log.error("new item has no keyType: " + item);
          return new NSException("item has no keytype"); // TBD
        }

        typed.setKeyUsingKeyTypeAndSequence
          (keyType, UOGoObject.nextKey(keyTypeToSequence, keyType));
      }
    }
    
    return null;
  }
  
  
  /* browser links */
  
  /**
   * Returns a value suitable for technical processing, that is, most chars
   * removed etc.
   * 
   * @return the clean value
   */
  public String processingValue() {
    String v = this.value();
    if (v == null) return null;
    
    // TBD: check what else
    // TBD: can we sanitize the prefix? (add +49 etc) Probably not.
    v = v.trim();
    v = v.replace(" ", "").replace("\t", "").replace("/", "").replace("-", "");
    
    return (v.length() > 0) ? v : null;
  }
  
  /**
   * Returns a link suitable for use in an HTML anker.
   * 
   * @return a browser link, like "tel:+491234567"
   */
  public String asBrowserLink() {
    String v = this.processingValue();
    v = UString.stringByEncodingURLComponent(v, null /* utf-8 */);
    return v != null ? ("tel:" + v) : null;
  }
  
  
  /* validation */
  
  /* Note: strictly speaking an address w/o an @ is still a valid address! */
  public static final String phoneRegEx = "^[\\+]?[\\d\\s\\-\\(\\)]{3,}";
  public static final Pattern phonePattern = Pattern.compile(phoneRegEx);


  /* legacy */
  
  @Override
  public String entityNameInOGo5() {
    return "Telephone";
  }
}
