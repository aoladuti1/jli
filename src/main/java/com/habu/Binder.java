package com.habu;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ClassInfoList;
import io.github.classgraph.ScanResult;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * This class handles storage and caching of {@code public}
 * class info of Java import strings, along with
 * reflection-based method / constructor calls, and field getting / setting.
 * By default, when resolving which method / constructor overload to get or
 * call,
 * passed {@link java.lang.Number Number} or Number-subclass arguments
 * will be recast for widening conversions (including to {@code char}
 * parameters).
 * {@link java.math.BigDecimal BigDecimal} type Numbers are an opt-out exception
 * to this
 * (see {@link #setRecastBigDecimals(boolean)}). BigDecimals which are whole
 * numbers will be
 * primarily treated as {@code Integer} arguments.
 * For Java functions whhich take an {@code Object[]} parameter,
 * if there is not a more suitable function found,
 * {@link java.util.List List} type (or subclass) arguments will be converted to
 * {@code Object[]} arrays (by calling {@link java.util.List#toArray()}).
 * This class provides no means of accessing non-{@code public} entities.
 *
 * <p>Extra notes: 1. Enum constants are treated as fields.
 * 2. Passing {@code Class}-type arguments to public Binder methods is
 * synonymous
 * with passing a static class (or its code-defined information) in the form of
 * an argument.
 * TODO: Make an instance class.
 */
public class Binder {

  /* Contains a HashMap of (className, (executableName, Executable)) */
  private class ExecutableStore { // for caching executables
    private HashMap<String, HashMap<String, List<Executable>>> table = new HashMap<>();

    /**
     * Get HashMap of executables stored in {@code className}.
     *
     * @param className the class name
     * @return a HashMap of executables stored in {@code className}
     */
    HashMap<String, List<Executable>> in(String className) {
      return table.get(className);
    }

    /**
     * Store {@code className} inside the internal 
     * (className, (executableName, {@link java.lang.reflect.Executable Executable})) HashMap.
     *
     * @param className the class name to store (put)
     */
    void registerClass(String className) {
      if (!table.containsKey(className)) {
        table.put(className, new HashMap<>());
      }
    }

    /**
     * Returns {@code true} if {@code className} is already registered, or {@code false} otherwise.
     *
     * @param className the class name
     * @return {@code true} if {@code className} is already registered, or {@code false} otherwise
     */
    boolean contains(String className) {
      return table.containsKey(className);
    }

  }

  // TODO: delete when Binder becomes an instance class in future
  private Binder() {}

  private static final Binder imp = new Binder();
  private static HashMap<String, Object> scanNames = new HashMap<>();
  protected static ExecutableStore constructorStore = imp.new ExecutableStore();
  protected static ExecutableStore methodStore = imp.new ExecutableStore();
  static HashMap<String, String> simpleToFullNames = new HashMap<>();
  private static boolean recastingBigDecs = true;

  /**
   * If {@code recast} is {@code false},
   * then no attempt will be made to match passed {@link java.lang.BigDecimal
   * BigDecimal}
   * or BigDecimal-subclass types with other {@link java.lang.Number Number}
   * types when using reflection to resolve function overloads. (This is not
   * default behaviour.)
   *
   * @param recast if {@code false}, turns off BigDecimal recasting. Otherwise,
   *               turns it on.
   */
  // TODO: ensure this STAYS method static
  public static void setRecastBigDecimals(boolean recast) {
    recastingBigDecs = recast;
  }

  /**
   * Returns {@code true} if attempts will be made to match
   * {@link java.lang.BigDecimal BigDecimal} arguments with other
   * {@link java.lang.Number Number} types when resolving overloads.
   * Returns {@code false} otherwise.
   *
   * @return whether or not attempts are being made to match
   *         BigDecimal type args to other Number params.
   * 
   */
  public static boolean isRecastingBigDecimals() {
    return recastingBigDecs;
  }
  
  // TODO: add specific simpleName Argument
  private static void registerClass(ClassInfo ci) {
    registerClass(ci.getName(), ci.getSimpleName(), ci.isEnum());
  }

  private static void registerClass(Class<?> clazz, String className) {
    registerClass(className, clazz.getSimpleName(), clazz.isEnum());
  }

  private static void registerClass(String className, String simpleClassName, boolean isEnum) {
    methodStore.registerClass(className);
    if (!isEnum) {
      constructorStore.registerClass(className);
    }
    simpleToFullNames.put(simpleClassName, className);
  }

  // If argument o is not a class, this method calls getClass(), otherwise returns o
  // TODO: make private (if possible)
  protected static Class<?> tryGetClass(Object o) {
    Class<?> ret = o.getClass();
    return (ret.equals(Class.class)) ? (Class<?>) o : ret;
  }

  private static String replaceLast(String string, String from, String to) {
    int lastIndex = string.lastIndexOf(from);
    if (lastIndex < 0) {
      return string;
    }
    String tail = string.substring(lastIndex).replaceFirst(from, to);
    return string.substring(0, lastIndex) + tail;
  }

  // will try and find inner classes to store class info by replacing '.' with '$'
  private static ScanResult resolveForInnerClasses(ScanResult res, String importString) {
    if (!res.getPackageInfo().isEmpty()) {
      return res;
    }
    String findClassString = importString;
    do {
      if (!findClassString.contains(".")) {
        break;
      }
      res = new ClassGraph()
          .enableSystemJarsAndModules()
          .acceptClasses(findClassString)
          .scan();
      findClassString = replaceLast(findClassString, ".", "\\$");
    } while (res.getPackageInfo().isEmpty());
    return res;
  }

  private static boolean wasImported(String importString) {
    return scanNames.containsKey(importString);
  }

  /**
   * Stores public, relevant class info by taking a String that mirrors a Java
   * import statement
   * and reading the class info without loading the class itself
   * (e.g. importString.equals("java.util.ArrayList")).
   * The wildcard character (*) is valid for packages/classes, but not for inner
   * classes.
   * Only {@code public} classes may be imported.
   * Only {@code public static} inner classes may be imported.
   * TODO: have a smarter wasImported function for wildcards
   * TODO: add a class alias argument for a manual simpleClassName
   *
   * @param importString the import string
   * @return true if the scan was successful, false if it was a failure
   * 
   */
  public static boolean scanImport(String importString) {
    if (!wasImported(importString)) {
      ScanResult res;
      if (importString.endsWith("*")) {
        res = new ClassGraph()
            .enableSystemJarsAndModules()
            .acceptPackagesNonRecursive(
                importString.substring(0, importString.lastIndexOf('.')))
            .scan();
      } else {
        res = new ClassGraph()
            .enableSystemJarsAndModules()
            .acceptPackagesNonRecursive(importString)
            .scan();
        res = resolveForInnerClasses(res, importString);
      }
      ClassInfoList ciList = res.getAllStandardClasses();
      if (ciList.isEmpty()) {
        return false;
      }
      scanNames.put(importString, null);
      for (ClassInfo ci : ciList) {
        // registers static or instant outer classes, but only static inner classes
        if (ci.isInnerClass()) {
          if (ci.isStatic()) {
            registerClass(ci.getName(), ci.getSimpleName(), ci.isEnum());
          }
        } else {
          registerClass(ci);
        }
      }
      res.close();
    }
    return true;
  }

  /**
   * Return the class name of the imported class associated with {@code simpleClassName},
   * (e.g. {@code simpleClassName="Example"} may return {@code "hello.hi.Example"}).
   *
   * @param simpleClassName the simple class name
   * @return the class name
   */
  public static String getFullClassName(String simpleClassName) {
    return simpleToFullNames.get(simpleClassName);
  }

  // TODO delete
  public static boolean classImported(String simpleClassName) {
    return classRegistered(getFullClassName(simpleClassName));
  }

  private static boolean classRegistered(String className) {
    return methodStore.contains(className);
  }

  private static boolean callsLoaded(String className) {
    HashMap<String, List<Executable>> conStore = constructorStore.in(className);
    return methodStore.in(className).size() > 0
        || conStore != null && conStore.size() > 0;
  }

  private static void storeFunctions(Class<?> clazz) {
    String className = clazz.getName();
    if (!classRegistered(className)) {
      registerClass(clazz, className);
    }
    if (!callsLoaded(className)) {
      storeMethods(clazz, className);
      if (!clazz.isEnum()) {
        storeConstructors(clazz, className);
      }
    }
  }

  private static void storeMethods(Class<?> clazz, String className) {
    for (Class<?> upperClazz = clazz; upperClazz != null; upperClazz = upperClazz.getSuperclass()) {
      for (Method m : upperClazz.getMethods()) {
        HashMap<String, List<Executable>> smlTable = methodStore.in(className);
        String miName = m.getName();
        if (smlTable.containsKey(miName)) {
          smlTable.get(miName).add(m);
        } else {
          List<Executable> smList = new ArrayList<Executable>();
          smList.add(m);
          smlTable.put(miName, smList);
        }
      }
    }
  }

  private static void storeConstructors(Class<?> clazz, String className) {
    for (Constructor<?> c : clazz.getConstructors()) {
      HashMap<String, List<Executable>> clTable = constructorStore.in(className);
      String conName = c.getName();
      if (clTable.containsKey(className)) {
        clTable.get(className).add(c);
      } else {
        List<Executable> coList = new ArrayList<Executable>();
        coList.add(c);
        clTable.put(conName, coList);
      }
    }
  }

  /**
   * Retrieve the {@code Method} which is the closest match for the provided name
   * and arguments.
   * Returns {@code null} if no suitable method is found.
   *
   * @param o          the object instance or class which contains the desired
   *                   method
   * @param methodName the name of the method
   * @param passedArgs the arguments to try against method parameters
   * @return the best matching method, or {@code null} if there wasn't one
   */
  public static Method getMethod(
      Object o, String methodName, List<Object> passedArgs) {
    Class<?> clazz = tryGetClass(o);
    storeFunctions(clazz);
    List<Executable> methodOptions = methodStore.in(clazz.getName()).get(methodName);
    if (methodOptions == null) {
      return null;
    } else {
      return (Method) getBestMatch(methodOptions, passedArgs);
    }
  }

  // calls scoreMatch to get the best matching executable based on passedArgs
  // Returns null if no good match found
  private static Executable getBestMatch(
      List<Executable> options, List<Object> passedArgs) {
    Executable bestMatch = null;
    int highScore = 0;
    for (Executable e : options) {
      int newScore = scoreMatch(e, passedArgs);
      if (newScore > highScore) {
        highScore = newScore;
        bestMatch = e;
      }
    }
    return bestMatch;
  }

  private static boolean classIsStatic(Class<?> clazz) {
    return Modifier.isStatic(clazz.getModifiers());
  }

  // TODO: Write test to assert the exception is thrown
  private static Object newInnerInstance(
      Object outerInstance, Class<?> inner, List<Object> passedArgs)
      throws InstantiationException, IllegalAccessException,
      IllegalArgumentException, InvocationTargetException {
    if (!classIsStatic(inner)) {
      if (outerInstance.getClass().equals(Class.class)) {
        throw new InvocationTargetException(// -- calling a non-static inner from a static outer --
          null, "Error: attempting to construct an inner class from a static outer class");
      }
      List<Object> augArgs = new ArrayList<>();
      augArgs.add(outerInstance);
      augArgs.addAll(passedArgs);
      return newInstance(inner, augArgs);
    } else {
      return newInstance(inner, passedArgs);
    }
  }

  /**
   * Call a method, constructor, or inner class constructor,
   * selected based on the suitability of the arguments in {@code passedArgs} with 
   * the parameters of {@code caller.functionName()}, and
   * return the result. If {@code functionName} matches the
   * {@link java.lang.Class#getSimpleName() simplified} {@code caller} class name,
   * this method will call the best-fitting constructor of the {@code caller}.
   * The same principle applies if {@code functionName} matches the name of an
   * inner class. (Be sure not to attempt construction of a non-static inner class
   * from
   * a static ({@code Class}-type) {@code caller} object)
   *
   * @param caller       the object instance or class
   * @param functionName the name of the function (a simple class name for
   *                     constructors)
   * @param passedArgs   the arguments to resolve and pass to the method
   * @return the result of the function call, or {@code null} if the matched
   *         method is returns null
   * @throws IllegalAccessException      if a {@code Constructor} object
   *                                     is enforcing Java language access control
   *                                     and the underlying
   *                                     constructor is inaccessible.
   * @throws IllegalArgumentException    if the number of actual
   *                                     and formal parameters differ; if an
   *                                     unwrapping
   *                                     conversion for primitive arguments fails;
   *                                     or if,
   *                                     after possible unwrapping, a parameter
   *                                     value
   *                                     cannot be converted to the corresponding
   *                                     formal
   *                                     parameter type by a method invocation
   *                                     conversion; if
   *                                     this constructor pertains to an enum
   *                                     class.
   * @throws InstantiationException      if the class that declares the
   *                                     underlying constructor represents an
   *                                     abstract class.
   * @throws InvocationTargetException   if an underlying constructor
   *                                     throws an exception.
   * @throws ExceptionInInitializerError if the initialization provoked
   *                                     by this method fails.
   */
  public static Object call(
      Object caller, String functionName, List<Object> passedArgs)
      throws InstantiationException, IllegalAccessException,
      IllegalArgumentException, InvocationTargetException {
    Class<?> clazz = tryGetClass(caller);
    Class<?> inner = getInnerClass(clazz, functionName);
    if (clazz.getSimpleName().equals(functionName)) {
      return newInstance(clazz, passedArgs);
    } else {
      inner = getInnerClass(clazz, functionName);
      if (inner != null) {
        return newInnerInstance(caller, inner, passedArgs);
      } else {
        return invoke(caller, getMethod(caller, functionName, passedArgs), passedArgs);
      }
    }
  }

  /**
   * Return the class matching the name {@code className}.
   *
   * @param className the full class name
   * @return the class matching {@code className}
   */
  public static Class<?> forNameOrNull(String className) {
    try {
      return Class.forName(className);
    } catch (Exception ex) {
      return null;
    }
  }

  /**
   * Call a method and return the result.
   *
   * @param caller     the object which calls {@code method}
   * @param method     the method to call
   * @param passedArgs the arguments to pass to {@code method}
   * @return the result of the method call
   * @throws IllegalAccessException      if this {@code Method} object
   *                                     is enforcing Java language access control
   *                                     and the underlying
   *                                     method is inaccessible.
   * @throws IllegalArgumentException    if the method is an
   *                                     instance method and the specified object
   *                                     argument
   *                                     is not an instance of the class or
   *                                     interface
   *                                     declaring the underlying method (or of a
   *                                     subclass
   *                                     or implementor thereof); if the number of
   *                                     actual
   *                                     and formal parameters differ; if an
   *                                     unwrapping
   *                                     conversion for primitive arguments fails;
   *                                     or if,
   *                                     after possible unwrapping, a parameter
   *                                     value
   *                                     cannot be converted to the corresponding
   *                                     formal
   *                                     parameter type by a method invocation
   *                                     conversion.
   * @throws InvocationTargetException   if the underlying method
   *                                     throws an exception.
   * @throws NullPointerException        if the specified object is null
   *                                     and the method is an instance method.
   * @throws ExceptionInInitializerError if the initialization
   *                                     provoked by this method fails.
   */
  public static Object invoke(
      Object caller, Method method, List<Object> passedArgs) 
      throws IllegalAccessException, InvocationTargetException {
    if (method != null) {
      return method.invoke(caller, fitArgsToFunction(passedArgs, method));
    } else {
      throw new NullPointerException("Method is null / unmatched method based on passedArgs");
    }
  }

  /**
   * Returns the {@link java.lang.reflect.Constructor Constructor} of {@code clazz} 
   * which is the closest match for the arguments {@code passedArgs}, or
   * {@code null} if no suitable constructor is found.
   *
   * @param clazz      the class which contains the desired
   *                   method
   * @param passedArgs the arguments to attempt matching against constructor parameters
   * @return the best matching constructor, or {@code null} if there wasn't one
   */
  public static Constructor<?> getConstructor(Class<?> clazz, List<Object> passedArgs) {
    String className = clazz.getName();
    storeFunctions(clazz);
    return (Constructor<?>) getBestMatch(
        constructorStore.in(className).get(className), passedArgs);
  }

  /**
   * Returns the {@link java.lang.reflect.Constructor Constructor} 
   * which is the closest match for the provided name and arguments {@code passedArgs}, or
   * {@code null} if no suitable constructor is found.
   *
   * @param simpleClassName the simple name of the class 
   *                        (e.g. {@code simpleClassName="Example"} 
   *                        may return {@code "hello.hi.Example"})
   * @param passedArgs the arguments to try against method parameters
   * @return the best matching method, or {@code null} if there wasn't one
   */
  // TODO: Delete
  public static Constructor<?> getConstructor(String simpleClassName, List<Object> passedArgs) {
    if (!simpleToFullNames.containsKey(simpleClassName)) {
      try {
        return getConstructor(Class.forName(getFullClassName(simpleClassName)), passedArgs);
      } catch (Exception ex) {
        ex.printStackTrace();
        return null; // error
      }
    } else {
      return null; // error - class not imported
    }
  }

  /**
   * Returns a newly created instance of {@code clazz} generated from the best matching constructor
   * for the arguments in {@code passedArgs}.
   *
   * @param clazz      class to instatniate
   * @param passedArgs arguments for the constructor
   * @return a new instance of {@code clazz} from the best-matching constructor
   *         arguments in {@code passedArgs}
   * @throws IllegalAccessException      if this {@code Constructor} object
   *                                     is enforcing Java language access control
   *                                     and the underlying
   *                                     constructor is inaccessible.
   * @throws IllegalArgumentException    if the number of actual
   *                                     and formal parameters differ; if an
   *                                     unwrapping
   *                                     conversion for primitive arguments fails;
   *                                     or if,
   *                                     after possible unwrapping, a parameter
   *                                     value
   *                                     cannot be converted to the corresponding
   *                                     formal
   *                                     parameter type by a method invocation
   *                                     conversion; if
   *                                     this constructor pertains to an enum
   *                                     class.
   * @throws InstantiationException      if the class that declares the
   *                                     underlying constructor represents an
   *                                     abstract class.
   * @throws InvocationTargetException   if the underlying constructor
   *                                     throws an exception.
   * @throws ExceptionInInitializerError if the initialization provoked
   *                                     by this method fails.
   */
  public static Object newInstance(Class<?> clazz, List<Object> passedArgs)
      throws InstantiationException, IllegalAccessException,
      IllegalArgumentException, InvocationTargetException {
    Constructor<?> bestMatch = getConstructor(clazz, passedArgs);
    if (bestMatch != null) {
      return bestMatch.newInstance(fitArgsToFunction(passedArgs, bestMatch));
    }
    throw new IllegalArgumentException(
        "Cannot match a constructor for " + clazz.getName() + " to passedArgs");
  }

  private static Class<?> getInnerClass(Object outer, String innerClassName) {
    for (Class<?> innerClazz : tryGetClass(outer).getDeclaredClasses()) {
      if (innerClazz.getSimpleName().equals(innerClassName)) {
        return innerClazz;
      }
    }
    return null; // Inner class not found
  }

  /**
   * Get the value of a field for {@code o} based on the passed {@code fieldName} String.
   * Enum constants are treated as fields and can be accessed via this method.
   *
   * @param o         object to pull a field from
   * @param fieldName name of the field
   * @return the field associated to {@code fieldName} or {@code null} if there isn't one
   */
  public static Object getField(Object o, String fieldName) {
    try {
      Class<?> clazz = tryGetClass(o);
      Field field = clazz.getField(fieldName);
      if (field.isEnumConstant()) {
        return clazz.getMethod("valueOf", String.class).invoke(null, fieldName);
      } else {
        return field.get(o);
      }
    } catch (Exception ex) {
      ex.printStackTrace();
      return null; // Field not found
    }
  }

  /**
   * Get the value of a field or static inner class for {@code o}
   * based on the passed {@code fieldName}.
   * Enum constants are treated as fields and can be accessed via this method.
   *
   * @param o         object to pull a field / inner class from
   * @param fieldName name of the field / inner class
   * @return the field / inner class
   */
  public static Object getFieldOrInnerClass(Object o, String fieldName) {
    try {
      Class<?> innerClazz = getInnerClass(o, fieldName);
      if (innerClazz != null) {
        return innerClazz;
      } else {
        return getField(o, fieldName);
      }
    } catch (Exception ex) {
      ex.printStackTrace();
      return null; // Field not found
    }
  }

  /**
   * x.
   *
   * @param o x
   * @param fieldName x
   * @param value x
   */
  // TODO: delete - one random object mutation method seems out of place
  public static void setField(Object o, String fieldName, Object value) {
    try {
      tryGetClass(o).getField(fieldName).set(o, value);
    } catch (Exception ex) {
      ex.printStackTrace();
    }
  }

  // 0 == err otherwase score is the return value - 1
  private static int scoreForObjectsAndNulls(Object arg, Class<?> paramClass) {
    if (arg == null) {
      if (paramClass.isPrimitive()) {
        return 0;
      } else if (paramClass.equals(Object.class)) {
        return 3;
      } else {
        return 2;
      }
    }
    if (paramClass.equals(Object.class)) {
      return 2;
    } else {
      return 1;
    }
  }

  // score == abs (1) == return this score , 0 = continue;
  private static int argBasicCheck(Executable c, List<Object> passedArgs) {
    if (c.getParameterCount() != passedArgs.size()) {
      return -1;
    } else if (c.getParameterCount() == 0 && passedArgs.size() == 0) {
      return 1;
    } else {
      return 0;
    }
  }

  // assigns a score on how close arguments for a method matches
  // each particular overload
  private static int scoreMatch(Executable c, List<Object> passedArgs) {
    int ret = argBasicCheck(c, passedArgs);
    if (Math.abs(ret) == 1) {
      return ret; // 1 pt: no args and no params | -1 pt: bad match (arg and param count mismatch)
    }
    for (int i = 0; i < passedArgs.size(); i++) {
      Object currentArg = passedArgs.get(i);
      Class<?> paramClass = c.getParameterTypes()[i];
      int nullAndObjectScore = scoreForObjectsAndNulls(currentArg, paramClass);
      if (nullAndObjectScore == 0) {
        return -1; // bad match
      } else {
        if (nullAndObjectScore > 1) {
          ret += nullAndObjectScore - 1; // 1 pt: null arg, | 2 pts: null arg to Object param
          continue;
        }
      }
      Class<?> argClass = currentArg.getClass();
      if (argClass.equals(paramClass)) {
        ret += 6; // 6 pts: same class
      } else if (paramClass.isAssignableFrom(argClass)) {
        ret += 5; // subclass
      } else if (paramClass.equals(Object[].class) && List.class.isAssignableFrom(argClass)) {
        ret += 4; // converting list to arr
      } else {
        int numRankScore = NumRank.scoreMatch(currentArg, argClass, paramClass);
        if (numRankScore == 0) {
          return -1; // bad match
        } else {
          ret += numRankScore; // see NumRank.scoreMatch()
        }
      }
    }
    return ret;
  }

  /**
   * Convert argument Number type args to the target parameter Number type if
   * necessary.
   * Convert list arguments to Object[] arguments if necessary.
   * TODO: convert 1-length String to char
   *
   * @param args arguments to be passed to the method
   * @param e    target method / constructor
   * @return an Object array of all arguments, altered or otherwise
   */
  @SuppressWarnings("unchecked")
  protected static Object[] fitArgsToFunction(List<Object> args, Executable e) {
    Object[] ret = new Object[args.size()];
    Class<?>[] paramClasses = e.getParameterTypes();
    for (int i = 0; i < ret.length; i++) {
      Object currentArg = args.get(i);
      if (currentArg == null) {
        ret[i] = null;
      } else {
        Class<?> argClass = args.get(i).getClass();
        Class<?> paramClass = paramClasses[i];
        if (Number.class.isAssignableFrom(args.get(i).getClass())) {
          ret[i] = fitNumberToFunction((Number) args.get(i), paramClass);
        } else if (List.class.isAssignableFrom(argClass) && paramClass.equals(Object[].class)) {
          ret[i] = ((List<Object>) currentArg).toArray();
        } else {
          ret[i] = currentArg;
        }
      }
    }
    return ret;
  }

  // TypeCast numArg baased on the numerical ranking of currentParamClass
  private static Object fitNumberToFunction(Number numArg, Class<?> currentParamClass) {
    switch (NumRank.rank(currentParamClass)) {
      case BYTE:
        return numArg.byteValue();
      case SHORT:
        return numArg.shortValue();
      case CHAR:
        return (char) numArg.intValue();
      case INT:
        return numArg.intValue();
      case LONG:
        return numArg.longValue();
      case FLOAT:
        return numArg.floatValue();
      case DOUBLE:
        return numArg.doubleValue();
      default:
        return numArg;
    }
  }

}
