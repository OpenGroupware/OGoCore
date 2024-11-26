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

import java.util.Map;

/**
 * Collection of utility functions which do not fit elsewhere (w/o introducing
 * artifical class hierarchies or spamming the base class ...).
 *
 * <p>
 * @author helge
 */
public class UOGoObject {
  
  private UOGoObject() {} // do not instantiate

  /**
   * Helper method for creating type keys, eg bill01, ship03 etc.
   * 
   * @param keyTypeToSequence - Map used to track the sequence for a key
   * @param keyType - the type to generate a key for, eg 'bill'
   * @return a the next possible index, starting with 0
   */
  public static int nextKey
    (final Map<String, Number> keyTypeToSequence, final String keyType)
  {
    Number sequence = keyTypeToSequence.get(keyType);
    final int result = sequence != null ? sequence.intValue() : 0;
    sequence = sequence == null ? int1 : Integer.valueOf(sequence.intValue() + 1);
    keyTypeToSequence.put(keyType, sequence);
    return result;
  }
  
  public static final Integer int1 = Integer.valueOf(1);

}
