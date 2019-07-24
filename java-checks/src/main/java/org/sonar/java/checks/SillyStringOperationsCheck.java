/*
 * SonarQube Java
 * Copyright (C) 2012-2019 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.java.checks;

import java.util.Arrays;
import java.util.List;

import javax.annotation.CheckForNull;

import org.sonar.check.Rule;
import org.sonar.java.checks.helpers.ConstantUtils;
import org.sonar.java.checks.helpers.ExpressionsHelper;
import org.sonar.java.checks.methods.AbstractMethodDetection;
import org.sonar.java.matcher.MethodMatcher;
import org.sonar.java.matcher.TypeCriteria;
import org.sonar.plugins.java.api.semantic.Symbol;
import org.sonar.plugins.java.api.tree.ExpressionTree;
import org.sonar.plugins.java.api.tree.IdentifierTree;
import org.sonar.plugins.java.api.tree.MemberSelectExpressionTree;
import org.sonar.plugins.java.api.tree.MethodInvocationTree;
import org.sonar.plugins.java.api.tree.Tree.Kind;

@Rule(key = "S2121")
public class SillyStringOperationsCheck extends AbstractMethodDetection {

  private static final String STRING = "java.lang.String";
  private static final MethodMatcher STRING_LENGTH =
    MethodMatcher.create().typeDefinition(STRING).name("length").withoutParameter();

  @Override
	protected List<MethodMatcher> getMethodInvocationMatchers() {
		return Arrays.asList(
      MethodMatcher.create().typeDefinition(STRING).name("contains").addParameter(TypeCriteria.subtypeOf("java.lang.CharSequence")),
      MethodMatcher.create().typeDefinition(STRING).name("compareTo").addParameter("String"),
      MethodMatcher.create().typeDefinition(STRING).name("compareToIgnoreCase").addParameter("String"),
      MethodMatcher.create().typeDefinition(STRING).name("endsWith").addParameter("String"),
      MethodMatcher.create().typeDefinition(STRING).name("indexOf").addParameter("String"),
      MethodMatcher.create().typeDefinition(STRING).name("indexOf").addParameter("String").addParameter("int"),
      MethodMatcher.create().typeDefinition(STRING).name("lastIndexOf").addParameter("String"),
      MethodMatcher.create().typeDefinition(STRING).name("lastIndexOf").addParameter("String").addParameter("int"),
      MethodMatcher.create().typeDefinition(STRING).name("matches").addParameter("String"),
      MethodMatcher.create().typeDefinition(STRING).name("replace").addParameter("char").addParameter("char"),
      MethodMatcher.create().typeDefinition(STRING).name("replace").addParameter("CharSequence").addParameter("CharSequence"),
      MethodMatcher.create().typeDefinition(STRING).name("replaceAll").addParameter("String").addParameter("String"),
      MethodMatcher.create().typeDefinition(STRING).name("replaceFirst").addParameter("String").addParameter("String"),
      MethodMatcher.create().typeDefinition(STRING).name("split").addParameter("String"),
      MethodMatcher.create().typeDefinition(STRING).name("split").addParameter("String").addParameter("int"),
      MethodMatcher.create().typeDefinition(STRING).name("startsWith").addParameter("String"),
      MethodMatcher.create().typeDefinition(STRING).name("startsWith").addParameter("String").addParameter("int"),
      MethodMatcher.create().typeDefinition(STRING).name("substring").addParameter("int"),
      MethodMatcher.create().typeDefinition(STRING).name("substring").addParameter("int").addParameter("int")
    );
	}

  @Override
  protected void onMethodInvocationFound(MethodInvocationTree tree) {
    if (!hasSemantic()) {
      return;
    }
    boolean issue = false;
    String method = tree.symbol().name();
    switch (method) {
      case "contains":
      case "compareTo":
      case "compareToIgnoreCase":
      case "endsWith":
      case "indexOf":
      case "lastIndexOf":
      case "matches":
      case "split":
      case "startsWith":
        if (tree.methodSelect().is(Kind.MEMBER_SELECT)) {
          issue = isSameString(((MemberSelectExpressionTree) tree.methodSelect()).expression(), tree.arguments().get(0));
        }
        break;
      case "replaceAll":
      case "replaceFirst":
        if (tree.methodSelect().is(Kind.MEMBER_SELECT)) {
          ExpressionTree str = ((MemberSelectExpressionTree) tree.methodSelect()).expression();
          issue = isSameString(str, tree.arguments().get(0)) && isSameString(str, tree.arguments().get(1));
        }
        break;
      case "substring":
        if (tree.methodSelect().is(Kind.MEMBER_SELECT)) {
          if (tree.arguments().size() == 1) {
            issue = isZero(tree.arguments().get(0)) || isStringLength(symbol(((MemberSelectExpressionTree) tree.methodSelect()).expression()), tree.arguments().get(0));
          } else {
            issue = isZero(tree.arguments().get(0)) && isStringLength(symbol(((MemberSelectExpressionTree) tree.methodSelect()).expression()), tree.arguments().get(1));
          }
        }
        break;
    }
    if (issue) {
      reportIssue(tree, String.format("Remove this \"%s\" call; it has predictable results.", method));
    }
  }

  private static boolean isSameString(ExpressionTree tree, ExpressionTree other) {
    return isSameIdentifier(tree, other) || isSameLiteral(tree, other);
  }

  private static boolean isSameIdentifier(ExpressionTree tree, ExpressionTree other) {
    Symbol str = symbol(tree);
    Symbol aString = symbol(other);
    return str != null && aString != null && str.equals(aString);
  }

  private static boolean isSameLiteral(ExpressionTree tree, ExpressionTree other) {
    String str = ExpressionsHelper.getConstantValueAsString(tree).value();
    String aString = ExpressionsHelper.getConstantValueAsString(other).value();
    return str != null && aString != null && str.equals(aString);
  }

  private static boolean isZero(ExpressionTree tree) {
    Integer value = ConstantUtils.resolveAsIntConstant(tree);
    return value != null && value == 0;
  }

  private static boolean isStringLength(Symbol str, ExpressionTree tree) {
    if (tree.is(Kind.METHOD_INVOCATION)) {
      MethodInvocationTree invocation = (MethodInvocationTree) tree;
      if (STRING_LENGTH.matches(invocation) && invocation.methodSelect().is(Kind.MEMBER_SELECT)) {
        Symbol aString = symbol(((MemberSelectExpressionTree) invocation.methodSelect()).expression());
        return aString != null && str.equals(aString);
      }
    }
    return false;
  }

  @CheckForNull
  private static Symbol symbol(ExpressionTree tree) {
    if (tree.is(Kind.IDENTIFIER)) {
      return ((IdentifierTree) tree).symbol();
    }
    return null;
  }
}
