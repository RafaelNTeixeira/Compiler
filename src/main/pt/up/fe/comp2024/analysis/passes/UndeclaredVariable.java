package pt.up.fe.comp2024.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2024.analysis.AnalysisVisitor;
import pt.up.fe.comp2024.ast.Kind;
import pt.up.fe.comp2024.ast.NodeUtils;
import pt.up.fe.specs.util.SpecsCheck;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Checks if the type of the expression in a return statement is compatible with the method return type.
 *
 * @author JBispo
 */
public class UndeclaredVariable extends AnalysisVisitor {

    private String currentMethod;
    List<List<String>> returnTypes = new ArrayList<>();

    private List<String> imports = new ArrayList<>();

    @Override
    public void buildVisitor() {
        addVisit(Kind.IMPORT_STATMENT, this::visitImportStatement);
        addVisit(Kind.METHOD_DECL, this::visitMethodDecl);
        addVisit(Kind.VAR_REF_EXPR, this::visitVarDecl);
        addVisit(Kind.RETURN_STMT, this::visitRetStatement);
        addVisit(Kind.ASSIGN_STMT, this::visitAssignStatement);
    }

    private Void visitAssignStatement(JmmNode assignStatm, SymbolTable symbolTable) {
        List<JmmNode> assignElements = assignStatm.getChildren();

        Boolean valid = true;

        // a = new A() -> aceita
        if ((assignElements.get(0).getKind().equals("VarRefExpr")) && (assignElements.get(1).getKind().equals("NewClass"))) valid = true;
        else if (assignElements.get(1).getKind().equals("ArrayInit")) {
            var isFirstVarArrayType = !assignElements.get(0).getParent().getParent().getChildren("Array").isEmpty();
            if (isFirstVarArrayType) {
                var arrayValuesTypeFirstVar = assignElements.get(0).getParent().getParent().getChildren("Array").get(0).getChildren().get(0);
                var arrayValuesTypeSecnVar = assignElements.get(1).getChildren().get(0);
                String compareValues = "";
                if (arrayValuesTypeSecnVar.getKind().equals("IntegerLiteral")) compareValues = "Integer";
                if (!arrayValuesTypeFirstVar.getKind().equals(compareValues)) valid = false;
            }
            else {
                valid = false;
            }
        }
        else if (!assignElements.get(0).getKind().equals(assignElements.get(1).getKind())) valid = false;
        // if var is Kind VarRefExpr need to get type of var


        if (!valid) {
            // Create error report
            var message = String.format("Incompatible assignment: '%s'", assignElements);
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(assignStatm),
                    NodeUtils.getColumn(assignStatm),
                    message,
                    null)
            );

        }

        return null;
    }

    private Void visitImportStatement(JmmNode importStatm, SymbolTable symbolTable) {
        List<JmmNode> importStatements = importStatm.getDescendants();
        for (JmmNode impStat : importStatements) {
            imports.add(impStat.get("name"));
        }
        return null;
    }

    private Void visitRetStatement(JmmNode returnStatm, SymbolTable symbolTable) {

        // return kind and name of elements that are being assigned
        List<JmmNode> returnElements = returnStatm.getDescendants();
        for (JmmNode returnElement : returnElements) {
            if (returnElement.hasAttribute("name")) {
                List<String> elementInfo = Arrays.asList(returnElement.getKind(), returnElement.get("name"));

                returnTypes.add(elementInfo);
            } else if (returnElement.hasAttribute("methodName")) {
                List<String> elementInfo = Arrays.asList(returnElement.getKind(), returnElement.get("methodName"));
                returnTypes.add(elementInfo);
            }
        }

        Boolean valid = true;

        // Check if returnTypes are the same
        for (int i = 0; i < returnTypes.size()-1; i++) {
            // in case of a function, check if params are the same
            if (returnTypes.get(0).get(0).equals("FunctionCall")) {
                if (!returnTypes.get(i+1).get(0).equals(returnTypes.get(i+2).get(0))) {
                    valid = false;
                }
                i++;
            }
            else {
                if (!returnTypes.get(i).get(0).equals(returnTypes.get(i + 1).get(0))) {
                    valid = false;
                }
            }
        }


        // Check if functionCall is calling var valid type
        if (returnElements.get(0).getKind().equals("FunctionCall")) {
            var importNames = symbolTable.getImports();
            var localVariablesCurrentMethod = symbolTable.getLocalVariables(currentMethod);
            var varThatCallsFunction = returnElements.get(1).get("name");
            String varThatCallsFunctionType = "";

            // Checks if in (Ex:) a.foo(), a is a local variable from the current method
            boolean isCalledVariableALocalVariable = false;
            for (var localVariable : localVariablesCurrentMethod) {
                for (var returnElement : returnElements) {
                    if (returnElement.hasAttribute("name")) {
                        if (localVariable.getName().equals(varThatCallsFunction)) {
                            isCalledVariableALocalVariable = true;
                            varThatCallsFunctionType = localVariable.getType().getName();
                            break;
                        }
                    }
                }
            }

            // Checks if in (Ex:) a.foo(), a comes from an import
            boolean isCalledVariableFromImport = false;

            if (isCalledVariableALocalVariable) {
                for (var importName : importNames) {
                    if (importName.equals(varThatCallsFunctionType)) {
                        isCalledVariableFromImport = true;
                        break;
                    }
                }
            }

            if (!isCalledVariableFromImport) {

                var methodCalled = returnElements.get(0).get("methodName");
                var varTypeSupposedToBeReceived = symbolTable.getParameters(methodCalled).get(0).getType().getName();

                var functionCallChildren = returnElements.get(0).getChildren();
                var varNameAddedToReturnMethod = functionCallChildren.get(functionCallChildren.size() - 1).get("name");
                String varTypeAddedToReturnMethod = "";
                var localVariables = symbolTable.getLocalVariables(currentMethod);

                // extrair tipo da variável a ser chamada na função de retorno
                for (var localVariable : localVariables) {
                    if (localVariable.getName().equals(varNameAddedToReturnMethod)) {
                        varTypeAddedToReturnMethod = localVariable.getType().getName();
                    }
                }

                String finalVarTypeAddedToReturnMethod = varTypeAddedToReturnMethod;

                if (symbolTable.getParameters(methodCalled).stream()
                        .noneMatch(param -> param.getType().getName().equals(finalVarTypeAddedToReturnMethod))) {
                    valid = false;
                }
            }
        }

        // Check if var in return exists
        else {
            for (var returnElement : returnTypes) {
                if (symbolTable.getParameters(currentMethod).stream()
                        .anyMatch(param -> param.getName().equals(returnElement.get(1)))) {
                    returnTypes.clear();
                    return null;
                } else if (symbolTable.getLocalVariables(currentMethod).stream()
                        .anyMatch(local -> local.getName().equals(returnElement.get(1)))) {
                    returnTypes.clear();
                    return null;
                } else if (symbolTable.getImports().stream()
                        .anyMatch(imp -> imp.equals(returnElement.get(1)))) {
                    returnTypes.clear();
                    return null;
                }
                valid = false;
            }
        }

        if (!valid) {
            // Create error report
            var message = String.format("Incompatible return types: '%s'", returnTypes);
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(returnStatm),
                    NodeUtils.getColumn(returnStatm),
                    message,
                    null)
            );
        }

        return null;
    }

    private Void visitMethodDecl(JmmNode method, SymbolTable table) {
        currentMethod = method.get("methodName");

        List<JmmNode> ifConditions = method.getChildren("IfCondition");
        boolean valid = true;
        JmmNode invalidIf = method;

       if (!ifConditions.isEmpty()) {
           var ewqeqw = "";
       }

       for (JmmNode ifCondition : ifConditions) {
           var operatorUsed = ifCondition.getChildren("BinaryExpr").get(0).get("op");
           if (operatorUsed.equals("+") || operatorUsed.equals("-") || operatorUsed.equals("*") || operatorUsed.equals("/")) {
               invalidIf = ifCondition;
               valid = false;
           }
       }

       if (!valid) {
           var message = String.format("Invalid If Condition: '%s'", ifConditions);
           addReport(Report.newError(
                   Stage.SEMANTIC,
                   NodeUtils.getLine(invalidIf),
                   NodeUtils.getColumn(invalidIf),
                   message,
                   null)
           );
       }

        return null;
    }

    private Void visitVarDecl(JmmNode varDecl, SymbolTable table) {
        SpecsCheck.checkNotNull(currentMethod, () -> "Expected current method to be set");

        // Check if exists a parameter or variable declaration with the same name as the variable reference
        var varRefName = varDecl.get("name");

        // Var is a field, return
        if (table.getFields().stream()
                .anyMatch(param -> param.getName().equals(varRefName))) {
            return null;
        }

        // Var is a parameter, return
        if (table.getParameters(currentMethod).stream()
                .anyMatch(param -> param.getName().equals(varRefName))) {
            return null;
        }

        // Var is a declared variable or imported package, return
        if (table.getLocalVariables(currentMethod).stream()
                .anyMatch(varDec -> varDec.getName().equals(varRefName)) ||
                table.getImports().stream().anyMatch(imp -> imp.equals(varRefName))
                ) {
            return null;
        }

        if (table.getImports().stream()
                .anyMatch(varDec -> varDec.equals(varRefName))) {
            return null;
        }

        // Create error report
        var message = String.format("Variable '%s' does not exist.", varRefName);
        addReport(Report.newError(
                Stage.SEMANTIC,
                NodeUtils.getLine(varDecl),
                NodeUtils.getColumn(varDecl),
                message,
                null)
        );

        return null;
    }


}
