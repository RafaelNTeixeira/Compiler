package pt.up.fe.comp2024.analysis.passes;

import org.antlr.v4.runtime.misc.Pair;
import pt.up.fe.comp.jmm.analysis.table.Symbol;
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

    private List<Pair<JmmNode, JmmNode>> functionsCalled = new ArrayList<>(); // Guarda o node da chamada feita a uma função e o node da declaração da variável que chama a função

    private List<Pair<String, List<Symbol>>> allLocalVariables = new ArrayList<>(); // Guarda o nome da função e a lista das suas variáveis locais

    @Override
    public void buildVisitor() {
        addVisit(Kind.IMPORT_STATMENT, this::visitImportStatement);
        addVisit(Kind.METHOD_DECL, this::visitMethodDecl);
        addVisit(Kind.VAR_DECL, this::visitVarDecl);
        addVisit(Kind.RETURN_STMT, this::visitRetStatement);
        addVisit(Kind.ASSIGN_STMT, this::visitAssignStatement);
    }

    private Void visitAssignStatement(JmmNode assignStatm, SymbolTable symbolTable) {
        List<JmmNode> assignElements = assignStatm.getChildren();
        var localsVar = symbolTable.getLocalVariables(currentMethod);

        Boolean valid = true;

        var arrayInits = new ArrayList<JmmNode>();
        for (var alignElement : assignElements) {
            if (alignElement.getKind().equals("ArrayInit")) {
                arrayInits.add(alignElement);
            }
        }
        // verificar se os valores dentro do array são do mesmo tipo
        for (int i = 0; i < arrayInits.size(); i++) {
            if (arrayInits.get(i).getChildren().size() > 1) {
                var firstElement = arrayInits.get(i).getChildren().get(i);
                var secondElement = arrayInits.get(i).getChildren().get(i + 1);

                if (!firstElement.getKind().equals(secondElement.getKind())) valid = false;
            }
        }

        // Lidar com arrays
        for (var localVar : localsVar) {
            // assignElement.get(0) é sempre igual à variável na qual guardamos valores
            if (localVar.getName().equals(assignElements.get(0).get("name"))) {
                // Se for do tipo array só pode dar assign a elementos do tipo array
                if (localVar.getType().isArray()) {
                    if (!assignElements.get(1).getKind().equals("ArrayInit")) {
                        valid = false;
                        break;
                    }
                }
                // Se não for do tipo array, não pode dar assign a elementos do tipo array
                else {
                    if (assignElements.get(1).getKind().equals("ArrayInit")) {
                        valid = false;
                        break;
                    }
                    // Ex: caso em que a = a[10] e 'a' é int
                    // se a variável for do tipo array
                    if (assignElements.get(1).getKind().equals("ArrayAccess")) {
                        // e tiver o mesmo nome que a variável da direita, dá erro (Variável esquerda nesta fase já não é array)
                        if (assignElements.get(1).getChildren().get(0).get("name").equals(assignElements.get(0).get("name"))) {
                            valid = false;
                            break;
                        }
                    }
                }
            }
        }

        // assign do tipo a = new A()
        var importNames = symbolTable.getImports();
        var foundImportName = true;
        if (assignElements.get(1).getKind().equals("NewClass")) {
            // se conter nome de qualquer import, assume-se como aceite. Ex: A a; B b; a = new B();
            for (var importName : importNames) {
                if (importName.equals(assignElements.get(1).get("className"))) {
                    break;
                }
                if (!foundImportName) {
                    valid = false;
                }
            }
        }
        // Para variáveis que dão assign a inteiros
        else if (assignElements.get(1).getKind().equals("IntegerLiteral")) {
            for (var localVar : localsVar) {
                if (localVar.getName().equals(assignElements.get(0).get("name"))) {
                    var expectedValueType = localVar.getType().getName();
                    if (!expectedValueType.equals("int")) {
                        valid = false;
                    }

                }
            }
        }
        // assign a uma variável (Ex: varLeft = varRight)
        else if (assignElements.get(1).getKind().equals("VarRefExpr")) {
            // pesquisa-se pela variável que recebe o valor
            for (var varLeft : localsVar) {
                if (assignElements.get(0).get("name").equals(varLeft.getName())) {
                    // pesquisa-se pela variável que dá o valor
                    for (var varRight : localsVar) {
                        if (assignElements.get(1).get("name").equals(varRight.getName())) {
                            // se tiverem tipos diferentes
                            if (!varLeft.getType().getName().equals(varRight.getType().getName())) {
                                // se a variável da direita for um objeto da classe
                                if (varRight.getType().getName().equals(symbolTable.getClassName())) {
                                    // e se a variável da esquerda for um import
                                    for (var importName : importNames) {
                                        if (varLeft.getType().getName().equals(importName)) {
                                            // se esse import não extender classe da variável da direita, dá erro
                                            if (!varLeft.getType().getName().equals(symbolTable.getSuper())) {
                                                valid = false;
                                            }
                                        }

                                    }
                                }
                            }
                        }
                    }
                }
            }
        }


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
        var localVariables = symbolTable.getLocalVariables(currentMethod);
        var parameters = symbolTable.getParameters(currentMethod);

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

                var functionCallChildren = returnElements.get(0).getChildren();
                var varNameAddedToReturnMethod = functionCallChildren.get(functionCallChildren.size() - 1).get("name");
                String varTypeAddedToReturnMethod = "";

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
        // se o return for de uma conta aritmética
        else if (returnElements.get(0).getKind().equals("BinaryExpr")) {
            for (var returnElement : returnElements) {
                // só é preciso analisar variáveis int. Ignora os operadores.
                if (!returnElement.getKind().equals("BinaryExpr")) {
                    // caso a variável no return esteja nas variáveis locais
                    for (var localVar : localVariables) {
                        // verifica se é variável local da função
                        if (localVar.getName().equals(returnElement.get("name"))) {
                            // se o valor for diferente de int é inválido
                            if (!localVar.getType().getName().equals("int")) valid = false;
                        }
                    }
                }
            }
        }
        // se o return for um acesso a um array
        else if (returnElements.get(0).getKind().equals("ArrayAccess")) {
            for (var child : returnElements.get(0).getChildren()) {
                // se encontrar a variável, queremos verificar se é do tipo array
                if (child.getKind().equals("VarRefExpr")) {
                    if (localVariables != null && !localVariables.isEmpty()) {
                        for (var localVar : localVariables) {
                            // se não for array, dá erro
                            if (!localVar.getType().isArray()) {
                                valid = false;
                            }
                        }
                    }
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
        List<JmmNode> whileConditions = method.getChildren("WhileLoop");
        boolean valid = true;
        JmmNode invalidIf = method;
        var methods = table.getMethods();
        var returnType = table.getReturnType(currentMethod);

        List<Symbol> localsList = table.getLocalVariables(currentMethod);
        Pair<String, List<Symbol>> pairLocals = new Pair<>(currentMethod, localsList);
        allLocalVariables.add(pairLocals);

        // Testar validade de uma condição if
       for (JmmNode ifCondition : ifConditions) {
           var operatorUsed = ifCondition.getChildren().get(0).getKind();
           // se conter BinaryExpr (conta aritmética sem operadores de comparação), é suposto dar erro
           if (operatorUsed.equals("BinaryExpr")) {
               invalidIf = ifCondition;
               valid = false;
               break;
           }
       }

       // Testar validade da condição de um loop while
        for (JmmNode whileCondition : whileConditions) {
            var operatorUsed = whileCondition.getChildren().get(0).getKind();
            // se conter BinaryExpr (conta aritmética sem operadores de comparação), é suposto dar erro
            if (operatorUsed.equals("BinaryExpr")) {
                invalidIf = whileCondition;
                valid = false;
                break;
            }
        }

       // Verificar se o método chamado existe
       // O Kind "Expression" pode conter o Kind "FunctionCall"
       var expressions = method.getChildren("Expression");
       for (var expression : expressions) {
           var functionCalls = expression.getChildren("FunctionCall");
           // se existir chamadas a funções
           if (!functionCalls.isEmpty()) {
               for (var functionCall : functionCalls) {
                   // verificar se o método chamado existe
                   for (var methodName : methods) {
                       if (methodName.equals(functionCall.get("methodName"))) {
                           valid = true;
                           break;
                       }
                       valid = false;
                   }
               }
           }
       }

       var methodChildren = method.getChildren();
       var assignments = method.getChildren("AssignStmt");

       // Ciclo para guardar FunctionCalls e variável que chama a função
       // Se existirem assignments
       if (!assignments.isEmpty()) {
           // percorremos os assigments
           for (var assignment : assignments) {
               // verificamos se existem chamadas a funções
               if (!assignment.getChildren("FunctionCall").isEmpty()) {
                   // Se existirem, percorremos todas as chamadas feitas a funções
                   for (var functionCall : assignment.getChildren("FunctionCall")) {
                       // percorremos as variáveis locais do método atual
                       for (var variableCallingFunction : assignment.getParent().getChildren("VarDecl")) {
                           var variableDecl = functionCall.getParent().getChildren("VarRefExpr").get(0).get("name");
                           // se encontrarmos a variável que chama a função
                           if (variableCallingFunction.get("name").equals(variableDecl)) {
                               // confirmar se a variável contém um tipo de valor
                               if (variableCallingFunction.getChildren().get(0).hasAttribute("value")) {
                                   // guardamos a function call e o número de parametros recebidos pela função
                                   Pair<JmmNode, JmmNode> pairFunc = new Pair<>(functionCall, variableCallingFunction);
                                   functionsCalled.add(pairFunc);
                                   //var varCallingFunctionType = variableCallingFunction.getChildren().get(0).get("value");
                               }
                               // se variável for um array
                               else if (!variableCallingFunction.getChildren("Array").isEmpty()) {
                                   Pair<JmmNode, JmmNode> pairFunc = new Pair<>(functionCall, variableCallingFunction);
                                   functionsCalled.add(pairFunc);
                               }
                           }
                       }
                   }
               }
           }
       }

       // Analisar se uma variável ao chamar uma função, esta recebe o tipo de retorno correto
        // percorremos as chamadas feitas a funções até agora
       for (Pair<JmmNode, JmmNode> functionCalled : functionsCalled) {
            // se coincidir com o método a analisar atualmente, estudamos esse método
            if (functionCalled.a.get("methodName").equals(currentMethod)) {
                var numVarArgsCalled = 0;
                for (var methodChild : methodChildren) {
                    // varargs está dentro do Kind Param
                    if (methodChild.getKind().equals("Param")) {
                        int parametersNumber = method.getChildren("Param").size();

                        if (!methodChild.getChildren("VarArgs").isEmpty()) numVarArgsCalled += 1;

                        // se existir pelo menos um parametro varargs
                        if (numVarArgsCalled > 0) {
                            boolean isLastParamVarArgs = !method.getChildren("Param").get(parametersNumber - 1).getChildren("VarArgs").isEmpty();
                            // só pode existir um parametro varargs nos parametros da função e tem que estar como último parâmetro dado, caso contrário dá erro
                            if (!isLastParamVarArgs || numVarArgsCalled > 1) {
                                valid = false;
                                break;
                            }
                            // procuramos pelo return statement para verificar o seu tipo
                            for (var child : methodChildren) {
                                if (child.getKind().equals("ReturnStmt")) {
                                    // variável que guarda o valor de return do método a analisar agora
                                    var variableThatCalledFunctionType = functionCalled.b.getChildren().get(0).get("value");
                                    // se os tipos de retorno forem diferentes, dá erro
                                    if (!returnType.getName().equals(variableThatCalledFunctionType)) valid = false;
                                }
                            }
                        }
                    }
                }
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
