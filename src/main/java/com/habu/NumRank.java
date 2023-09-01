package com.habu;

import java.math.BigDecimal;

/**
 * This class exists for classifying wrapped class and primitive types as BYTE SHORT CHAR INT etc. 
 * so that method overloads can be accurately selected by the JPI.
 */
enum NumRank {
  NAN,
  BYTE,
  SHORT,
  CHAR,
  INT,
  LONG,
  FLOAT,
  DOUBLE;

  static final BigDecimal BYTEMAX = new BigDecimal(Byte.MAX_VALUE);
  static final BigDecimal BYTEMIN = new BigDecimal(Byte.MIN_VALUE);
  static final BigDecimal SHORTMAX = new BigDecimal(Short.MAX_VALUE);
  static final BigDecimal SHORTMIN = new BigDecimal(Short.MIN_VALUE);
  static final BigDecimal INTMAX = new BigDecimal(Integer.MAX_VALUE);
  static final BigDecimal INTMIN = new BigDecimal(Integer.MIN_VALUE);
  static final BigDecimal LONGMAX = new BigDecimal(Long.MAX_VALUE);
  static final BigDecimal LONGMIN = new BigDecimal(Long.MIN_VALUE);
  static final BigDecimal FLOATMAX = new BigDecimal(Float.MAX_VALUE);
  static final BigDecimal FLOATNEGMAX = new BigDecimal(-Float.MAX_VALUE);
  static final BigDecimal DOUBLEMAX = new BigDecimal(Double.MAX_VALUE);
  static final BigDecimal DOUBLENEGMAX = new BigDecimal(-Double.MAX_VALUE);

  private static NumRank primitiveRank(Class<?> argClass) {
    if (argClass.equals(byte.class)) {
      return BYTE;
    } else if (argClass.equals(short.class)) {
      return SHORT;
    } else if (argClass.equals(char.class)) {
      return CHAR;
    } else if (argClass.equals(int.class)) {
      return INT;
    } else if (argClass.equals(long.class)) {
      return LONG;
    } else if (argClass.equals(float.class)) {
      return FLOAT;
    } else if (argClass.equals(double.class)) {
      return DOUBLE;
    } else {
      return NAN;
    }
  }

  private static NumRank wrapperRank(Class<?> argClass) {
    if (argClass.equals(Byte.class)) {
      return BYTE;
    } else if (argClass.equals(Short.class)) {
      return SHORT;
    } else if (argClass.equals(Character.class)) {
      return CHAR;
    } else if (argClass.equals(Integer.class)) {
      return INT;
    } else if (argClass.equals(Long.class)) {
      return LONG;
    } else if (argClass.equals(Float.class)) {
      return FLOAT;
    } else if (argClass.equals(Double.class)) {
      return DOUBLE;
    } else {
      return NAN;
    }
  }

  static boolean isWhole(BigDecimal bd) {
    return bd.stripTrailingZeros().scale() <= 0;
  }

  static boolean inByteRange(BigDecimal bd) {
    return bd.compareTo(BYTEMAX) <= 0 && bd.compareTo(BYTEMIN) >= 0;
  }

  static boolean inShortRange(BigDecimal bd) {
    return bd.compareTo(SHORTMAX) <= 0 && bd.compareTo(SHORTMIN) >= 0;
  }

  static boolean inIntRange(BigDecimal bd) {
    return bd.compareTo(INTMAX) <= 0 && bd.compareTo(INTMIN) >= 0;
  }

  static boolean inLongRange(BigDecimal bd) {
    return bd.compareTo(LONGMAX) <= 0 && bd.compareTo(LONGMIN) >= 0;
  }

  static boolean inFloatRange(BigDecimal bd) {
    return bd.compareTo(FLOATMAX) <= 0 && bd.compareTo(FLOATNEGMAX) >= 0;
  }

  static boolean inDoubleRange(BigDecimal bd) {
    return bd.compareTo(DOUBLEMAX) <= 0 && bd.compareTo(DOUBLENEGMAX) >= 0;
  }

  /**
   * Return the numerical type of a class as a NumRank constant. 
   * For instance, {@code Integer} and {@code int} classes are both considered {@code NumRank.INT}.
   *
   * @param clazz the class to rank
   * @return the numerical type of {@code clazz} as a NumRank constant.
   */
  static NumRank rank(Class<?> clazz) {
    return (clazz.isPrimitive()) ? primitiveRank(clazz) : wrapperRank(clazz);
  }

  /**
   * Return the NumRank constant corresponding to the accepted number range in which the 
   * value of the BigDecimal falls, or NAN if none match.
   *
   * @param bd the BigDecimal object
   * @return the numerical type as a NumRank constant
   */
  private static NumRank rank(BigDecimal bd) {
    if (isWhole(bd)) {
      if (inByteRange(bd)) {
        return BYTE;
      } else if (inShortRange(bd)) {
        return SHORT;
      } else if (inIntRange(bd)) {
        return INT;
      } else if (inLongRange(bd)) {
        return LONG;
      }
    }
    if (inFloatRange(bd)) {
      return FLOAT;
    } else if (inDoubleRange(bd)) {
      return DOUBLE;
    } else {
      return NAN; 
    } // error
  }

  // coupled w JPI
  static int scoreMatch(Object argObj, Class<?> argClass, Class<?> paramClass) {
    NumRank argRank;
    NumRank paramRank = rank(paramClass);
    boolean bigDecPassed = BigDecimal.class.isAssignableFrom(argClass)
                        && Binder.isRecastingBigDecimals();
    argRank = bigDecPassed ? rank((BigDecimal) argObj) : rank(argClass);
    if (argRank == NAN) {
      return 0; 
    }
    if (argRank == paramRank) {
      return 4; // 4 points for equivalent numeric types
    } else if (paramRank.compareTo(argRank) > 0) {
      if (paramRank != CHAR) {
        return 3; // 3 points for widening conversions
      } else if (paramClass.isPrimitive()) {
        return 2; // 2 points for whole number to char implicit conversions
      }
    } else if (paramRank == CHAR && paramClass.isPrimitive()
            && argRank.compareTo(INT) <= 0) {
      return 2; // 2 points for integer to char implicit conversions
    }
    return 0;
  }
}
