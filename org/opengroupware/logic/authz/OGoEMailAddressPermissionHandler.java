/*
  Copyright (C) 2007-2014 Helge Hess

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
package org.opengroupware.logic.authz;

import java.util.Map;

import org.getobjects.foundation.NSKeyValueCoding;

/**
 * OGoEMailAddressPermissionHandler
 * <p>
 * Contact email addresses are currently classified by their 'label' property
 * which is a CSV like 'WORK;PREF'. If no label is set the email will be hidden
 * except for 'r'/'w' contacts.
 * 
 * <p>
 * VCard Types
 * <ul>
 *   <li>INTERNET
 *   <li>X400
 *   <li>HOME
 *   <li>WORK
 *   <li>PREF
 *   <li>OTHER
 *   <li>X-
 * </ul>
 * 
 * <p>
 * Permissions
 * <ul>
 *   <li><code>r</code> - email can be shown
 *   <li><code>w</code> - email can be changed, implies 'r'
 * </ul>
 * 
 * <p>
 * Relevant contact permissions
 * <ul>
 *   <li><code>r</code> - email address is 'r'
 *   <li><code>w</code> - email address is 'rw'
 *   <li><code>b</code> - WORK labels visible (business contact data)
 *   <li><code>p</code> - HOME labels visible (private contact data)
 *   <li><code>s</code> - (may send private messages)
 * </ul>
 * 
 * <p>
 * @author helge
 */
public class OGoEMailAddressPermissionHandler
  extends OGoContactOwnedObjectPermissionHandler
{
  // TBD: allow for restricted 'write' permissions?
  public static final IOGoPermissionHandler companyEMail =
    new OGoEMailAddressPermissionHandler();
  public static final IOGoPermissionHandler personEMail =
    new OGoEMailAddressPermissionHandler();
  
  public OGoEMailAddressPermissionHandler() {
    super();
  }

  @SuppressWarnings("rawtypes")
  @Override
  public String objectPermissionForContactPermission
    (String _contactPerm, NSKeyValueCoding _object, Object _info)
  {
    // TBD: now we need to derive the subobject permissions based on the
    //      contact permissions, eg '' for private items if the user has
    //      no access
    // for now, we just copy the parent permissions
    if (_contactPerm == null)
      return null;
    
    int count = _contactPerm.length();
    if (count == 0) /* no permissions on contact, no permission on object */
      return OGoAuthzFetchContext.noPermission;

    /* scan */
    
    if (_contactPerm.indexOf('w') >= 0)
      return "rw"; // 'w' implies 'r'
    if (_contactPerm.indexOf('r') >= 0)
      return "r";

    if (_object == null && _info == null)
      return null; /* need more info */
    
    String label = _object != null ? (String)_object.valueForKey("label"):null;
    if (label == null && _info != null)
      label = (String)((Map)_info).get("label");
    
    if (label != null) {
      label = label.toUpperCase();
      if ((_contactPerm.indexOf('b') >= 0) && label.contains("WORK")) 
        return "r";
      if ((_contactPerm.indexOf('p') >= 0) && label.contains("HOME")) 
        return "r";
    }
    
    return OGoAuthzFetchContext.noPermission;
  }
}
