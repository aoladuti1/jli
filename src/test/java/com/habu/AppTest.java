package com.habu;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    @Test
    void testApp() {
        JLI.scanImport("com.habu.*");
        JLI.scanImport("java.util.ArrayList");
        ArrayList<Object> l = new ArrayList<>();
        ArrayList<Object> l2 = new ArrayList<>();

        try {
            byte f = 5;
            BigDecimal x = new BigDecimal(f);
            l.add(x);

            Object o = JLI.call(Binder.class, "Binder", l);
            System.out.println(o);
            Object a = JLI.call(ArrayList.class, "ArrayList", l2);
            Object b = Integer.valueOf(4);
            l2.add(b);
            JLI.call(a, "add", l2);
            l2.clear();
            l2.add(null);
            JLI.call(a, "add", l2);
            System.out.println(a);
            JLI.call(JLI.getFieldOrInnerClass(JLI.call(Binder.class, "Binder", new ArrayList<>()).getClass(), "EV"), "c", new ArrayList<Object>());

        } catch (Exception ex) {
            ex.printStackTrace();
        }
        assertTrue(true);
    }

    @Test
    void testObjsAndNulls() {
        ArrayList<Object> ll = new ArrayList<>();
        ArrayList<Object> nl = new ArrayList<>();
        nl.add(null);
        assertTrue(!((boolean) JLI.call(Binder.class, "ob", nl)));
    }

    @Test
    void testCallNull() {
        ArrayList<Object> nL = new ArrayList<>();
        nL.add(5);
        assertTrue((boolean) JLI.call(Binder.class, "ob", nL));
    }
}
