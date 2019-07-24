class A {
  void f(String str, String other) {

    "".contains("");     // Noncompliant
    str.contains(str);   // Noncompliant
    str.contains(other);
  }
}
