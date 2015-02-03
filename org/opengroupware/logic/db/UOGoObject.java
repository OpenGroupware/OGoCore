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
    sequence = sequence == null ? int1 : new Integer(sequence.intValue() + 1);
    keyTypeToSequence.put(keyType, sequence);
    return result;
  }
  
  public static final Integer int1 = new Integer(1);

}
