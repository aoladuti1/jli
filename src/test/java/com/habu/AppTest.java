package com.habu;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.ArrayList;


/**
 * Unit test for simple App.
 */
class AppTest {
  /**
   * Rigorous Test.
   */

  static ArrayList<Object> args = new ArrayList<>();

  ArrayList<Object> singleArg(Object o) {
    args.clear();
    args.add(o);
    return args;
  } 

  @Test
  void testApp() {
    Binder.scanImport("com.habu.*");
    assertTrue(true);    
  }

  @Test
  void testObjsAndNulls() { // null args should resolve to the object overload
    assertFalse((boolean) Binder.call(Tester.class, "trueIfInt", singleArg(null)));
  }

  @Test 
  void testConstruction() {
    try {
      assertTrue(Binder.call(Class.forName(Binder.getFullClassName("Tester")), null, args) != null);
    } catch (Exception ex) {
      ;
    }
  }

  @Test
  void testEnum() { // null args should resolve to the object overload
    assertTrue(Binder.getField(EnumTester.class, "OK") == EnumTester.OK);
  }

  @Test
  void testCallNull() {
    assertTrue((boolean) Binder.call(Tester.class, "trueIfInt", singleArg(5)));
  }

  @Test
  void testInnerConstructionAndRecasts() {
    Tester t = new Tester();
    Object inner1 = Binder.call(t, "InnerNoInt", singleArg(1));
    Object inner2 = Binder.call(t, "InnerNoInt", singleArg(new BigDecimal(1.1)));
    Binder.setRecastBigDecimals(false);
    Object inner3 = Binder.call(t, "InnerNoInt", singleArg(new BigDecimal(1.1)));
    Binder.setRecastBigDecimals(true);
    assertTrue(inner1 == null && inner2 != null && inner3 == null);
  }
}

