package com.habu;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.math.BigDecimal;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

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

  ArrayList<Object> noArgs() {
    args.clear();
    return args;
  }

  @Test
  void testApp() {
    Binder.scanImport("com.habu.*");
    assertTrue(true);
  }

  @Test
  void testObjsAndNulls() { // null args should resolve to the object overload
    try {
      assertFalse((boolean) Binder.call(Tester.class, "trueIfInt", singleArg(null)));
    } catch (Exception ex) {
      fail(ex.getMessage());
    }
  }

  @Test
  void testConstruction() {
    Class<?> testerClass = Binder.forNameOrNull(Binder.getFullClassName("Tester"));
    try {
      assertTrue(Binder.call(testerClass, "Tester", noArgs()) != null);
    } catch (Exception ex) {
      fail(ex.getMessage());
    }
  }

  @Test
  void testEnum() { // null args should resolve to the object overload
    assertTrue(Binder.getField(EnumTester.class, "OK") == EnumTester.OK);
    assertTrue(Binder.getFieldOrInnerClass(EnumTester.class, "OK") == EnumTester.OK);
  }

  @Test
  void testCallNull() {
    try {
      assertTrue((boolean) Binder.call(Tester.class, "trueIfInt", singleArg(5)));
    } catch (Exception ex) {
      fail(ex.getMessage());
    }
  }

  @Test
  void testInnerConstructionAndRecasts() {
    Boolean success = true;
    Tester t = new Tester();

    try {
      Binder.call(t, "InnerNoInt", singleArg(new BigDecimal(1.1)));
    } catch (Exception ex) {
      success = false;
    }
    Binder.setRecastBigDecimals(false); // should result in an error (null value) now
    try {
      Binder.call(t, "InnerNoInt", singleArg(new BigDecimal(1.1)));
      success = false;
    } catch (Exception ex) {
      ;
    }
    Binder.setRecastBigDecimals(true);
    // passing an int should result in an error (null value)
    Object inner = Binder.getMethod(t, "InnerNoInt", singleArg(1));
    assertTrue(inner == null && success);
  }

  @Test
  void callStaticInnerMethod() {
    args.clear();
    Class<?> staticInner = (Class<?>) Binder.getFieldOrInnerClass(Tester.class, "StaticInner");
    try {
      assertTrue((boolean) Binder.call(staticInner, "callMe", noArgs()));
    } catch (Exception ex) {
      fail(ex.getMessage());
    }
  }

  @Test
  void varArgsTest() {
    try {
      Binder.call(Tester.class, "varArgMethod", singleArg(new ArrayList<>()));
      assertTrue(Tester.id == Tester.OBJARR);
    } catch (Exception ex) {
      fail(ex.getMessage());
    }
  }

  @Test
  void importInnerClass() {
    String innerSimpleName = "InnerToImport";
    Binder.scanImport("com.habu.Tester.InnerToImport");
    assertTrue(Binder.simpleToFullNames.containsKey(innerSimpleName));
  }

}
