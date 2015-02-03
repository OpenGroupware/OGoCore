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
package org.opengroupware.format;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.FieldPosition;
import java.text.Format;
import java.text.ParsePosition;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.getobjects.appserver.core.WOMessage;
import org.getobjects.foundation.NSKeyValueCoding;
import org.opengroupware.logic.db.OGoAddress;

/**
 * GMapsAddressFormat
 * <p>
 * Takes an OGoAddress object and returns a GoogleMaps URL for that.
 * 
 * @author helge
 */
public class GMapsAddressFormat extends Format {
  private static final long serialVersionUID = 1L;
  protected final static Log log = LogFactory.getLog("GMapsAddressFormat");
  
  protected String mapsPrefix;
  
  public static final GMapsAddressFormat defaultFormat =
    new GMapsAddressFormat("http://maps.google.de/maps?f=q&hl=de");
  
  public GMapsAddressFormat(String _prefix) {
    super();
    this.mapsPrefix = _prefix;
  }
  
  @Override
  public StringBuffer format
    (Object _object, StringBuffer _sb, FieldPosition pos)
  {
    /*
     * http://maps.google.de/maps
     * ?f=q&
     * hl=de&
     * geocode=&
     * q=Universit%C3%A4tsplatz+12,+39104+Magdeburg&
     * sll=51.124213,10.546875&
     * sspn=14.93921,29.882813&
     * ie=UTF8&
     * z=16&
     * iwloc=addr&
     * om=1
     */
    if (!(_object instanceof OGoAddress)) {
      log.warn("given object is not an OGoAddress: " + _object);
      return null;
    }
    
    String q = mapsQueryForAddress((OGoAddress)_object);
    if (q == null) {
      log.info("got no address to generate a URL for.");
      return _sb;
    }
    
    String charset = WOMessage.defaultURLEncoding();
    try {
      q = URLEncoder.encode(q, charset);
    }
    catch (UnsupportedEncodingException e) {
      log.error("could not encode form parameters due to charset", e);
    }
    if (q == null || q.length() == 0)
      return null;
    
    /* append URL */
    
    _sb.append(this.mapsPrefix);
    _sb.append("&ie=");
    _sb.append(charset);
    _sb.append("&q=");
    _sb.append(q);
    _sb.append("&z=7"); // zoom (7=north germany, 8=SA, 6=all Germany)
    
    return _sb;
  }
  
  /**
   * Generates a Google Maps query string for a given address object. The
   * address should expose those KVC keys:
   * <ul>
   *   <li>street
   *   <li>city
   *   <li>zip
   *   <li>country
   * </ul> 
   * 
   * @param _adr - the object representing the address
   * @return a String object containing the query or null
   */
  public static String mapsQueryForAddress(NSKeyValueCoding _adr) {
    /* q=Universit%C3%A4tsplatz+12,+39104+Magdeburg */
    if (_adr == null)
      return null;
    
    StringBuilder gq = new StringBuilder(256);
    
    String v = (String)_adr.valueForKey("street");
    if (v != null && v.length() > 0) {
      if (gq.length() > 0) gq.append(",");
      gq.append(v);
    }
    if (((v = (String)_adr.valueForKey("city")) != null) && v.length() > 0) {
      if (gq.length() > 0) gq.append(",");
      String v2 = (String)_adr.valueForKey("zip");
      if (v2 != null && v2.length() > 0) { // DE specific?
        gq.append(v2);
        gq.append(' ');
      }
      gq.append(v);
    }
    if (((v = (String)_adr.valueForKey("country")) != null) && v.length() > 0) {
      if (gq.length() > 0) gq.append(",");
      gq.append(v);
    }
    
    return gq.length() > 0 ? gq.toString() : null;
  }

  @Override
  public Object parseObject(String source, ParsePosition pos) {
    return null;
  }

}
