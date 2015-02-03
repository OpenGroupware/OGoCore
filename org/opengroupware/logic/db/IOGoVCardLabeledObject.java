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

/**
 * IOGoVCardLabeledObject
 * <p>
 * Interface for objects which can be represented as vCard components. That is,
 * addresses, emails and phone numbers.
 * Import for OGo operations is the vCard 'label', which denotes the type of
 * the object (eg WORK or HOME address).
 * 
 * <p>
 * Implemented by:
 * <ul>
 *   <li>OGoAddress
 *   <li>OGoEMailAddress
 *   <li>OGoPhoneNumber
 * </ul>
 * 
 * <p>
 * @author helge
 */
public interface IOGoVCardLabeledObject {

  /**
   * Returns the primary vCard label of the object, eg:
   * 'WORK', 'HOME' or 'OTHER'.
   */
  public String   primaryVCardLabel();
  
  /**
   * Returns all vCard labels attached to the object. This can be simple
   * ones like 'WORK' or arrays like [ 'PARCEL', 'WORK' ], [ 'HOME', 'FAX' ].
   */
  public String[] vCardLabels();

  /**
   * Returns all vCard labels attached to the object. This can be simple
   * ones like 'WORK' or CSVs like "PARCEL,WORK", "HOME,FAX".
   */
  public String vCardLabelsAsString();

  /**
   * Assigns the given vCard labels to the object.
   * 
   * @param _labels - an array of Strings, eg [ 'FAX', 'WORK' ]
   */
  public void setVCardLabels(String[] _labels);
  
  /**
   * Splits the _labels string at the comma (',') and then calls setVCardLabels
   * with the resulting array.
   * 
   * @param _labels - a vCard label string, eg 'FAX,WORK'
   */
  public void setVCardLabelsAsString(String _labels);
  
}
