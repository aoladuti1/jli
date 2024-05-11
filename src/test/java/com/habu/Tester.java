package com.habu;

/*** . */
public class Tester {

  public static final int OBJ = 1;
  public static final int BYTE = 2;
  public static final int SHORT = 3;
  public static final int CHAR = 4;
  public static final int INT = 5;
  public static final int LONG = 6;
  public static final int FLOAT = 7;
  public static final int DOUBLE = 8;
  public static final int OBJARR = 9;

  public static int id = -1;

  /*** . */
  public static class StaticInner {
    public static int id = -1;

    public static boolean callMe() {
      return true;
    }
    
  }

  /** . */
  public static class InnerToImport {}

  class InnerNoInt {
    public InnerNoInt(int o) throws NullPointerException {
      throw new RuntimeException();
    }

    public InnerNoInt(float o) {}
  }

  public Tester() {
    id = 0;
  }

  public static void methodOverload(Object o) {
    id = OBJ;
  }

  public static void methodOverload(byte o) {
    id = BYTE;
  }

  public static void methodOverload(Short o) {
    id = SHORT;
  }

  public static void methodOverload(char o) {
    id = INT;
  }

  public static void methodOverload(Integer o) {
    id = INT;
  }

  public static void methodOverload(long o) {
    id = LONG;
  }

  public static void methodOverload(Float o) {
    id = FLOAT;
  }

  public static void methodOverload(double o) {
    id = DOUBLE;
  }

  public static void methodOverload(Object[] o) {
    id = OBJARR;
  }

  public static void varArgMethod(Object... o) {
    id = OBJARR;
  }

  public static void twoArgTest(int a, int b) {
    id = INT;
  }

  public static void twoArgTest(float a, float b) {
    id = FLOAT;
  }

  public static boolean trueIfInt(Object o) {
    return false;
  }

  public static boolean trueIfInt(int o) {
    return true;
  }

}
