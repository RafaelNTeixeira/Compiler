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

public class astOpValidator extends AnalysisVisitor {
    private String currentMethod;
    private final List<JmmNode> methods = new ArrayList<JmmNode>();
    List<List<String>> returnTypes = new ArrayList<>();
    private List<String> imports = new ArrayList<>();
    private List<Pair<JmmNode, JmmNode>> functionsCalled = new ArrayList<>(); // Guarda o node da chamada feita a uma função e o node da declaração da variável que chama a função
    private List<Pair<String, List<Symbol>>> allLocalVariables = new ArrayList<>(); // Guarda o nome da função e a lista das suas variáveis locais

    @Override
    public void buildVisitor() {
        addVisit("Program", this::visitProgram);
        addVisit(Kind.IMPORT_STATMENT, this::visitImportStatement);
        addVisit(Kind.METHOD_DECL, this::visitMethodDecl);
        addVisit(Kind.VAR_DECL, this::visitVarDecl);
        addVisit(Kind.RETURN_STMT, this::visitRetStatement);
        addVisit(Kind.ASSIGN_STMT, this::visitAssignStatement);
        addVisit(Kind.CLASS_DECL, this::visitClassDecl);
        addVisit("Array", this::visitArray);
        addVisit("Integer", this::visitInt);
        addVisit("Boolean", this::visitBoolean);
        addVisit("String", this::visitString);
        addVisit("Void", this::visitVoid);
        addVisit("Var", this::visitVar);
        addVisit("VarArgs", this::visitVarArgs);
        addVisit("ImportDeclaration", this::visitImports);
        addVisit("FunctionCall", this::visitExpression);
        addVisit("IfCondition", this::visitIfConditions);
        addVisit("WhileLoop", this::visitWhileLoops);
        addVisit("ArrayInit", this::visitArrayInit);
        addVisit("NewClass", this::visitNewClass);
        addVisit("Expression", this::visitExpression);
        addVisit("ArrayAccess", this::visitArrayAccess);
        addVisit("Length", this::visitLength);
        addVisit("IntegerLiteral", this::visitIntegerLiteral);
        addVisit("Bolean", this::visitBolean);
        addVisit("Negate", this::visitNegate);
        addVisit("NewArray", this::visitNewArray);
        addVisit("BinaryExpr", this::visitBinaryExpr);
        addVisit("BinaryOp", this::visitBinaryOp);
        addVisit("Param", this::visitParam);
        addVisit("Length", this::visitLength);
    }

    private Void visitProgram(JmmNode programNode, SymbolTable symbolTable) {
        var methodsNodes = programNode.getDescendants("MethodDecl");
        for (var method : methodsNodes) {
            methods.add(method);
        }
        return null;
    }

    private Void visitClassDecl(JmmNode classDeclNode, SymbolTable symbolTable) {
        boolean valid = true;

        // verificar se existem fields repetidos
        if (symbolTable.getFields() != null) {
            var fields = symbolTable.getFields();
            for (int i = 0; i < fields.size(); i++) {
                for (int j = i + 1; j < fields.size(); j++) {
                    if (fields.get(i) != null && fields.get(j) != null) {
                        if (fields.get(i).getName().equals(fields.get(j).getName())) {
                            valid = false;
                            break;
                        }
                    }
                }
            }
        }

        if (!valid) {
            // Create error report
            var message = String.format("Repeated field: '%s'.", classDeclNode);
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(classDeclNode),
                    NodeUtils.getColumn(classDeclNode),
                    message,
                    null)
            );
        }

        return null;
    }

    private Void visitParam(JmmNode paramNode, SymbolTable symbolTable) {
        if (paramNode.get("name").equals("args")) {
            paramNode.put("type", "main");
        }
        else {
            if (!paramNode.getChildren("Array").isEmpty()) {
                var arrayNode = paramNode.getChildren("Array").get(0);
                paramNode.put("type", arrayNode.getChildren().get(0).get("value"));
                paramNode.put("isArray", "true");
            }
            else {
                paramNode.put("type", paramNode.getChildren().get(0).get("value"));
            }
        }
        return null;
    }

    private Void visitBinaryOp(JmmNode binaryOpNode, SymbolTable symbolTable) {
        boolean valid = true;

        var varRefExpressions = binaryOpNode.getDescendants("VarRefExpr");
        int countBoolConsts = 0;
        int countIntConsts = binaryOpNode.getChildren("IntegerLiteral").size();

        // contar valores booleanos numa VarRefExpr
        for (var var : varRefExpressions) {
            if (var.hasAttribute("name")) {
                if (var.get("name").equals("true") || var.get("name").equals("false")) {
                    countBoolConsts++;
                }
            }
            else {
                // se forem variáveis locais
                if (symbolTable.getLocalVariables(currentMethod) != null) {
                    for (var locarVar : symbolTable.getLocalVariables(currentMethod)) {
                        if (var.hasAttribute("name")) {
                            if (locarVar.getName().equals(var.get("name"))) {
                                if (locarVar.getType().getName().equals("boolean")) {
                                    countBoolConsts++;
                                }
                            }
                        }
                    }
                }
                // se forem parametros
                if (symbolTable.getParameters(currentMethod) != null) {
                    for (var param : symbolTable.getParameters(currentMethod)) {
                        if (var.hasAttribute("name")) {
                            if (param.getName().equals(var.get("name"))) {
                                if (param.getType().getName().equals("boolean")) {
                                    countBoolConsts++;
                                }
                            }
                        }
                    }
                }
            }
        }

        // contar valores inteiros numa VarRefExpr
        for (var var : varRefExpressions) {
            // se forem variáveis locais
            if (symbolTable.getLocalVariables(currentMethod) != null) {
                for (var locarVar : symbolTable.getLocalVariables(currentMethod)) {
                    if (var.hasAttribute("name")) {
                        if (locarVar.getName().equals(var.get("name"))) {
                            if (locarVar.getType().getName().equals("int")) {
                                countIntConsts++;
                            }
                        }
                    }
                }
            }
            // se forem parametros
            if (symbolTable.getParameters(currentMethod) != null) {
                for (var param : symbolTable.getParameters(currentMethod)) {
                    if (var.hasAttribute("name")) {
                        if (param.getName().equals(var.get("name"))) {
                            if (param.getType().getName().equals("int")) {
                                countIntConsts++;
                            }
                        }
                    }
                }
            }
        }

        if (countIntConsts > 0) {
            // operador && não funciona com ints
            if (binaryOpNode.hasAttribute("op")) {
                if (binaryOpNode.get("op").equals("&&")) valid = false;
            }
        }

        if (countBoolConsts > 0 && countIntConsts == 0) {
            // operador < só funciona com ints
            if (binaryOpNode.hasAttribute("op")) {
                if (binaryOpNode.get("op").equals("<")) valid = false;
            }
        }

        if ((countBoolConsts > 0 && countIntConsts > 0)) {
            if (binaryOpNode.hasAttribute("op")) {
                if (binaryOpNode.get("op").equals("<")) valid = false;
                else if (binaryOpNode.get("op").equals("&&")) valid = false;
            }
        }

        if (!valid) {
            // Create error report
            var message = String.format("Invalid binary operation: '%s'", binaryOpNode);
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(binaryOpNode),
                    NodeUtils.getColumn(binaryOpNode),
                    message,
                    null)
            );
        }

        return null;
    }

    private Void visitBinaryExpr(JmmNode binaryExprNode, SymbolTable symbolTable) {
        boolean valid = true;

        var allVariables = binaryExprNode.getDescendants("VarRefExpr");

        // Se for uma operação aritmética entre variáveis, verificar se são do tipo int
        for (var variable : allVariables) {
            // verificar se é variável local
            if (symbolTable.getLocalVariables(currentMethod) != null) {
                for (var localVar : symbolTable.getLocalVariables(currentMethod)) {
                    if (variable.hasAttribute("name")) {
                        if (localVar.getName().equals(variable.get("name"))) {
                            // varExpr só podem ter elementos inteiros, não booleanos
                            if (!localVar.getType().getName().equals("int")) {
                                valid = false;
                                break;
                            }
                        }
                    }
                }
            }
            // verificar se é parametro da função atual
            for (var param : symbolTable.getParameters(currentMethod)) {
                if (variable.hasAttribute("name")) {
                    if (param.getName().equals(variable.get("name"))) {
                        // varExpr só podem ter elementos inteiros, não booleanos
                        if (!param.getType().getName().equals("int")) {
                            valid = false;
                            break;
                        }
                    }
                }
            }
        }

        if (!valid) {
            // Create error report
            var message = String.format("Invalid binary expression (Not type int): '%s'", binaryExprNode);
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(binaryExprNode),
                    NodeUtils.getColumn(binaryExprNode),
                    message,
                    null)
            );
        }

        return null;
    }

    private Void visitNewArray(JmmNode newArrayNode, SymbolTable symbolTable) {
        boolean valid = true;

        // Verificar se for dado um valor dentro de [], como em new int[2], só é dado 1 valor e do tipo int
        if (newArrayNode.getChildren("IntegerLiteral").size() > 1 || newArrayNode.getChildren("IntegerLiteral").isEmpty()) {
            valid = false;
        }

        if (!valid) {
            // Create error report
            var message = String.format("Invalid new array construction: '%s'", newArrayNode);
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(newArrayNode),
                    NodeUtils.getColumn(newArrayNode),
                    message,
                    null)
            );
        }

        return null;
    }

    private Void visitNegate(JmmNode negateNode, SymbolTable symbolTable) {
        boolean valid = true;

        // Negate só pode ser utilizado em Booleanos
        // se for uma variável negada ou booleano negado
        if (negateNode.getChildren().get(0).getKind().equals("VarRefExpr")) {
            var negatedVar = negateNode.getChildren().get(0);
            boolean isConstTrueOrFalse = false;
            // verificar se é constante true ou false
            if (negatedVar.hasAttribute("name")) {
                if (negatedVar.get("name").equals("true") || negatedVar.get("name").equals("false")) {
                    isConstTrueOrFalse = true;
                }
            }
            // Se é constante true ou false, já é validado. Se não for...
            if (!isConstTrueOrFalse) {
                // verificar se é variável local
                if (symbolTable.getLocalVariables(currentMethod) != null) {
                    for (var localVar : symbolTable.getLocalVariables(currentMethod)) {
                        if (negatedVar.hasAttribute("name")) {
                            if (localVar.getName().equals(negatedVar.get("name"))) {
                                if (!localVar.getType().getName().equals("boolean")) {
                                    valid = false;
                                    break;
                                }
                            }
                        }
                    }
                }

                // verificar se é parametro
                if (symbolTable.getParameters(currentMethod) != null) {
                    for (var param : symbolTable.getParameters(currentMethod)) {
                        if (negatedVar.hasAttribute("name")) {
                            if (param.getName().equals(negatedVar.get("name"))) {
                                if (!param.getType().getName().equals("boolean")) {
                                    valid = false;
                                    break;
                                }
                            }
                        }
                    }
                }
            }
        }

        if (!valid) {
            // Create error report
            var message = String.format("Invalid negation: '%s'", negateNode);
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(negateNode),
                    NodeUtils.getColumn(negateNode),
                    message,
                    null)
            );
        }

        return null;
    }

    private Void visitBolean(JmmNode boleanNode, SymbolTable symbolTable) {
        boleanNode.put("type", "boolean");
        return null;
    }

    private Void visitIntegerLiteral(JmmNode integerLiteralNode, SymbolTable symbolTable) {
        integerLiteralNode.put("type", "int");
        return null;
    }

    private Void visitLength(JmmNode lengthNode, SymbolTable symbolTable) {
        boolean valid = true;

        // Verificar se é utilizado em array types. Se não for, dá erro

        if (!lengthNode.getChildren().isEmpty()) {
            var varUsedOnNode = lengthNode.getChildren().get(0);
            // Verificar se length é chamada em variáveis locais
            if (symbolTable.getLocalVariables(currentMethod) != null) {
                for (var localVar : symbolTable.getLocalVariables(currentMethod)) {
                    if (varUsedOnNode.hasAttribute("name")) {
                        if (localVar.getName().equals(varUsedOnNode.get("name"))) {
                            if (!localVar.getType().isArray()) {
                                valid = false;
                            }
                        }
                    }
                }
            }
            if (valid) {
                // Verificar se length é chamada em parametros da função atual
                if (symbolTable.getParameters(currentMethod) != null) {
                    for (var param : symbolTable.getParameters(currentMethod)) {
                        if (varUsedOnNode.hasAttribute("name")) {
                            if (param.getName().equals(varUsedOnNode.get("name"))) {
                                if (!param.getType().isArray()) {
                                    valid = false;
                                }
                            }
                        }
                    }
                }
            }
            if (valid) {
                // Verificar se length é chamada em fields da classe
                if (symbolTable.getFields() != null) {
                    for (var field : symbolTable.getFields()) {
                        if (varUsedOnNode.hasAttribute("name")) {
                            if (field.getName().equals(varUsedOnNode.get("name"))) {
                                if (!field.getType().isArray()) {
                                    valid = false;
                                }
                            }
                        }
                    }
                }
            }
        }

        if (!valid) {
            // Create error report
            var message = String.format("Invalid length function call: '%s'", lengthNode);
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(lengthNode),
                    NodeUtils.getColumn(lengthNode),
                    message,
                    null)
            );
        }

        return null;
    }

    private Void visitArrayAccess(JmmNode arrayAccessNode, SymbolTable symbolTable) {
        boolean valid = true;

        // Verificar se valor dentro de [] é int
        var indexNode = arrayAccessNode.getChildren().get(1);
        // Se for um array access com uma conta aritmética
        if (indexNode.getKind().equals("BinaryExpr")) {
            if (!indexNode.getChildren("Length").isEmpty()) {
                valid = true;
            }
        }
        // Verificar se é uma constante
        else if (!indexNode.getKind().equals("IntegerLiteral")) {
            // Verificar se é uma variável local
            if (symbolTable.getLocalVariables(currentMethod) != null) {
                for (var localVar : symbolTable.getLocalVariables(currentMethod)) {
                    if (indexNode.hasAttribute("name")) {
                        if (localVar.getName().equals(indexNode.get("name"))) {
                            if (!localVar.getType().getName().equals("int") || localVar.getType().isArray()) {
                                valid = false;
                            }
                        }
                    }
                }
            }
            // Verificar se é um parametro da função atual
            if (symbolTable.getParameters(currentMethod) != null) {
                for (var param : symbolTable.getParameters(currentMethod)) {
                    if (indexNode.hasAttribute("name")) {
                        if (param.getName().equals(indexNode.get("name"))) {
                            if (!param.getType().getName().equals("int") || param.getType().isArray()) {
                                valid = false;
                            }
                        }
                    }
                }
            }
            // Verificar se é um field da classe
            if (symbolTable.getFields() != null) {
                for (var field : symbolTable.getFields()) {
                    if (indexNode.hasAttribute("name")) {
                        if (field.getName().equals(indexNode.get("name"))) {
                            if (!field.getType().getName().equals("int") || field.getType().isArray()) {
                                valid = false;
                            }
                        }
                    }
                }
            }
        }

        if (!valid) {
            // Create error report
            var message = String.format("Invalid array access: '%s'", arrayAccessNode);
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(arrayAccessNode),
                    NodeUtils.getColumn(arrayAccessNode),
                    message,
                    null)
            );
        }
        return null;
    }

    private Void visitExpression(JmmNode expressionNode, SymbolTable symbolTable) {
        boolean valid = true;

        // Se faz uma chamada a uma função
        if (!expressionNode.getChildren().isEmpty()) {
            if (expressionNode.getChildren().get(0).getKind().equals("FunctionCall")) {
                if (expressionNode.getChildren().get(0).hasAttribute("methodName")) {
                    var methodNameCalled = expressionNode.getChildren().get(0).get("methodName");
                    String varTypeThatCalledFunction = "";
                    boolean methodFound = false;
                    boolean cameFromImport = false;
                    boolean foundMethodCalled = false;

                    // Verificar se a variável que chama o método existe nas variáveis locais
                    if (!expressionNode.getChildren().isEmpty() && !expressionNode.getChildren().get(0).getChildren("VarRefExpr").isEmpty()) {
                        var varThatCalledMethod = expressionNode.getChildren().get(0).getChildren("VarRefExpr").get(0);
                        if (symbolTable.getLocalVariables(currentMethod) != null) {
                            for (var localVar : symbolTable.getLocalVariables(currentMethod)) {
                                if (varThatCalledMethod.hasAttribute("name")) {
                                    if (localVar.getName().equals(varThatCalledMethod.get("name"))) {
                                        varTypeThatCalledFunction = localVar.getType().getName();
                                        break;
                                    }
                                }
                            }
                        }
                        // Verificar se a variável que chama o método existe nos parâmetros
                        if (symbolTable.getParameters(currentMethod) != null) {
                            for (var param : symbolTable.getParameters(currentMethod)) {
                                if (varThatCalledMethod.hasAttribute("name")) {
                                    if (param.getName().equals(varThatCalledMethod.get("name"))) {
                                        varTypeThatCalledFunction = param.getType().getName();
                                        break;
                                    }
                                }
                            }
                        }
                        // Verificar se a variável que chama o método existe nos fields
                        if (symbolTable.getFields() != null) {
                            for (var field : symbolTable.getFields()) {
                                if (varThatCalledMethod.hasAttribute("name")) {
                                    if (field.getName().equals(varThatCalledMethod.get("name"))) {
                                        varTypeThatCalledFunction = field.getType().getName();
                                        break;
                                    }
                                }
                            }
                        }
                        // Verificar se a variável que chama o método existe nos imports
                        if (symbolTable.getImports() != null) {
                            for (var importName : symbolTable.getImports()) {
                                // Verificar se a variável que chama o método tem um tipo importado
                                if (importName.equals(varTypeThatCalledFunction) || (varThatCalledMethod.hasAttribute("name") && importName.equals(varThatCalledMethod.get("name")))) {
                                    cameFromImport = true;
                                    break;
                                }
                                // Verificar se a variável que chama o método tem o tipo da classe que extende uma superclasse, sendo esta importada
                                else if (varTypeThatCalledFunction.equals(symbolTable.getClassName())) {
                                    if (!symbolTable.getSuper().isEmpty()) {
                                        if (importName.equals(symbolTable.getSuper())) {
                                            cameFromImport = true;
                                            break;
                                        }
                                    }
                                }
                                else if (this.methods != null) {
                                    for (var method : this.methods) {
                                        if (method.hasAttribute("methodName")) {
                                            if (method.get("methodName").equals(methodNameCalled)) {
                                                foundMethodCalled = true;
                                                break;
                                            }
                                        }
                                    }
                                }
                                if (!foundMethodCalled) valid = false;
                            }
                        }
                    }

                    if (!foundMethodCalled) {
                        // se a variável local que chamou a função possui o tipo do import, assume-se que a função chamada já existe
                        if (!cameFromImport) {
                            // Verificar se o método chamado existe
                            if (symbolTable.getMethods() != null) {
                                for (var method : symbolTable.getMethods()) {
                                    if (methodNameCalled.equals(method)) {
                                        methodFound = true;
                                        break;
                                    }
                                }
                                if (!methodFound) valid = false;
                            }
                        }
                    }
                }
            }
        }

        // Se for um Kind FunctionCall
        if (expressionNode.getKind().equals("FunctionCall")) {
            if (this.methods != null) {
                for (var method : this.methods) {
                    if (method.get("methodName").equals(expressionNode.get("methodName"))) {
                        if (!method.hasAttribute("type")) continue;
                        expressionNode.put("type", method.get("type"));
                        break;
                    }
                }
            }

            // verificar se os tipos dados como parametros são válidos
            for (int i = 1; i < expressionNode.getChildren().size(); i++) {
                var paramGiven = expressionNode.getChildren().get(i);
                String paramGivenType = "";

                if (paramGiven.getKind().equals("IntegerLiteral")) {
                    paramGivenType = "int";
                }
                else if (paramGiven.getKind().equals("VarRefExpr")) {
                    if (paramGiven.hasAttribute("name")) {
                        if (paramGiven.get("name").equals("true") || paramGiven.get("name").equals("false")) {
                            paramGivenType = "boolean";
                        }
                    }
                }
                else if (paramGiven.getKind().equals("Bolean")) {
                    paramGivenType = "boolean";
                }

                // procurar pelo valor dado caso ainda não tenha sido encontrado
                if (paramGivenType.isEmpty()) {
                    // procurar pelo tipo do valor dado nas locals
                    if (symbolTable.getLocalVariables(currentMethod) != null) {
                        for (var localVar : symbolTable.getLocalVariables(currentMethod)) {
                            if (paramGiven.hasAttribute("name")) {
                                if (localVar.getName().equals(paramGiven.get("name"))) {
                                    paramGivenType = localVar.getType().getName();
                                }
                            }
                        }
                    }
                }
                if (paramGivenType.isEmpty()) {
                    // procurar pelo tipo do valor dado nos fields
                    if (symbolTable.getFields() != null) {
                        for (var field : symbolTable.getFields()) {
                            if (paramGiven.hasAttribute("name")) {
                                if (field.getName().equals(paramGiven.get("name"))) {
                                    paramGivenType = field.getType().getName();
                                }
                            }
                        }
                    }
                }
                if (paramGivenType.isEmpty()) {
                    // procurar pelo tipo do valor dado nos parametros
                    if (symbolTable.getParameters(currentMethod) != null) {
                        for (var param : symbolTable.getParameters(currentMethod)) {
                            if (paramGiven.hasAttribute("name")) {
                                if (param.getName().equals(paramGiven.get("name"))) {
                                    paramGivenType = param.getType().getName();
                                }
                            }
                        }
                    }
                }

                if (paramGivenType.isEmpty() && !paramGiven.getKind().equals("BinaryExpr")) {
                    valid = false;
                    break;
                }

                // procurar pela função
                for (var method : this.methods) {
                    if (method.hasAttribute("methodName") && expressionNode.hasAttribute("methodName")) {
                        if (method.get("methodName").equals(expressionNode.get("methodName"))) {
                            var hasVarArgsDescendents = !method.getDescendants("VarArgs").isEmpty();
                            if (!hasVarArgsDescendents) {
                                if (!method.getChildren("Param").isEmpty()) {
                                    var expectedParam = method.getChildren("Param").get(i - 1);
                                    var expectedParamSize = method.getChildren("Param").size();

                                    // Verificar se dá o número correto de parametros à função chamada
                                    if (expectedParamSize != expressionNode.getChildren().size() - 1) valid = false;

                                    if (expectedParam.getChildren("VarArgs").isEmpty()) {
                                        if (expectedParam.hasAttribute("type")) {
                                            if (!expectedParam.get("type").equals(paramGivenType)) {
                                                valid = false;
                                            }
                                        }
                                    }
                                }
                                else if (expressionNode.getChildren().size() - 1 > 0) valid = false; // Significa que deu parametros a uma função que não recebe argumentos
                            }
                            break;
                        }
                    }
                }
                var paramExpected = "";
            }

            for (var functionElement : expressionNode.getChildren()) {
                // retirar tipo dos elementos e inseri-lo nos nodes que constituem a chamada à função
                if (functionElement.getKind().equals("VarRefExpr")) {
                    if (symbolTable.getImports() != null) {
                        for (var importName : symbolTable.getImports()) {
                            if (functionElement.hasAttribute("name")) {
                                if (importName.equals(functionElement.get("name"))) {
                                    functionElement.put("type", importName);
                                    break;
                                }
                            }
                        }
                    }
                    if (symbolTable.getLocalVariables(currentMethod) != null) {
                        for (var localVar : symbolTable.getLocalVariables(currentMethod)) {
                            if (functionElement.hasAttribute("name")) {
                                if (localVar.getName().equals(functionElement.get("name"))) {
                                    functionElement.put("type", localVar.getType().getName());
                                    break;
                                }
                            }
                        }
                    }
                    if (symbolTable.getParameters(currentMethod) != null) {
                        for (var param : symbolTable.getParameters(currentMethod)) {
                            if (functionElement.hasAttribute("name")) {
                                if (param.getName().equals(functionElement.get("name"))) {
                                    functionElement.put("type", param.getType().getName());
                                    break;
                                }
                            }
                        }
                    }
                    if (symbolTable.getFields() != null) {
                        for (var field : symbolTable.getFields()) {
                            if (functionElement.hasAttribute("name")) {
                                if (field.getName().equals(functionElement.get("name"))) {
                                    functionElement.put("type", field.getType().getName());
                                    break;
                                }
                            }
                        }
                    }
                }
            }
        }

        if (!valid) {
            // Create error report
            var message = String.format("Invalid expression: '%s'", expressionNode);
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(expressionNode),
                    NodeUtils.getColumn(expressionNode),
                    message,
                    null)
            );
        }

        return null;
    }

    private Void visitNewClass(JmmNode newClassNode, SymbolTable symbolTable) {
        boolean valid = true;

        var varLeft = newClassNode.getParent().getChildren().get(0);
        var varLeftType = "";

        // verificar o tipo da varLeft
        // se for variável local
        if (symbolTable.getLocalVariables(currentMethod) != null) {
            for (var localVar : symbolTable.getLocalVariables(currentMethod)) {
                if (varLeft.hasAttribute("name")) {
                    if (localVar.getName().equals(varLeft.get("name"))) {
                        varLeftType = localVar.getType().getName();
                    }
                }
            }
        }
        // se for parametro da função atual
        if (varLeftType.isEmpty()) {
            if (symbolTable.getParameters(currentMethod) != null) {
                for (var param : symbolTable.getParameters(currentMethod)) {
                    if (varLeft.hasAttribute("name")) {
                        if (param.getName().equals(varLeft.get("name"))) {
                            varLeftType = param.getType().getName();
                        }
                    }
                }
            }
        }
        // se for field da classe
        if (varLeftType.isEmpty()) {
            if (symbolTable.getFields() != null) {
                for (var field : symbolTable.getFields()) {
                    if (varLeft.hasAttribute("name")) {
                        if (field.getName().equals(varLeft.get("name"))) {
                            varLeftType = field.getType().getName();
                        }
                    }
                }
            }
        }
        // se for do tipo import
        if (varLeftType.isEmpty()) {
            if (symbolTable.getImports() != null) {
                for (var importName : symbolTable.getImports()) {
                    if (varLeft.hasAttribute("name")) {
                        if (importName.equals(varLeft.get("name"))) {
                            varLeftType = importName;
                        }
                    }
                }
            }
        }
        // não está declarada
        if (varLeftType.isEmpty()) valid = false;

        boolean found = false;
        // variável da esquerda é declaração do import
        if (symbolTable.getImports() != null) {
            for (var importName : symbolTable.getImports()) {
                if (!varLeftType.isEmpty()) {
                    if (importName.equals(varLeftType)) {
                        found = true;
                        break;
                    }
                }
            }
        }
        // se for do tipo da classe
        if (symbolTable.getClassName() != null) {
            if (varLeftType.equals(symbolTable.getClassName())) {
                found = true;
            }
        }
        if (!found) valid = false;

        if (!valid) {
            // Create error report
            var message = String.format("Invalid New Class: '%s'", newClassNode);
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(newClassNode),
                    NodeUtils.getColumn(newClassNode),
                    message,
                    null)
            );
        }

        return null;
    }

    private Void visitArrayInit(JmmNode arrayInitNode, SymbolTable symbolTable) {
        boolean valid = true;
        String type = "";

        // Ex: new int[2]
        // verificar se os atributos dentro de [] são válidos
        var valuesGiven = arrayInitNode.getChildren();
        if (!arrayInitNode.getParent().getChildren("VarRefExpr").isEmpty()) {
            var varStoring = arrayInitNode.getParent().getChildren("VarRefExpr").get(0);
            // Se for uma variável local
            if (symbolTable.getLocalVariables(currentMethod) != null) {
                for (var localVar : symbolTable.getLocalVariables(currentMethod)) {
                    // se a variável que está a guardar for array é aceite
                    if (!varStoring.hasAttribute("name")) continue;
                    if (localVar.getName().equals(varStoring.get("name")) && localVar.getType().isArray()) {
                        // se esta for do tipo int
                        if (localVar.getType().getName().equals("int")) {
                            for (var valueGiven : valuesGiven) {
                                if (!valueGiven.getKind().equals("IntegerLiteral")) {
                                    valid = false;
                                    break;
                                }
                                type = "int";
                            }
                        }
                        if (localVar.getType().getName().equals("boolean")) {
                            for (var valueGiven : valuesGiven) {
                                if (valueGiven.hasAttribute("name")) {
                                    if (!valueGiven.get("name").equals("true") && !valueGiven.get("name").equals("false")) {
                                        valid = false;
                                        break;
                                    }
                                }
                                type = "boolean";
                            }
                        }
                    }
                }
            }
        }

        if (!valid) {
            // Create error report
            var message = String.format("Invalid new array creation: '%s'", arrayInitNode);
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(arrayInitNode),
                    NodeUtils.getColumn(arrayInitNode),
                    message,
                    null)
            );
        }
        else {
            arrayInitNode.put("type", type);
            arrayInitNode.put("isArray", "true");
        }

        return null;
    }

    private Void visitWhileLoops(JmmNode whileNode, SymbolTable symbolTable) {
        boolean valid = true;

        var operatorUsed = whileNode.getChildren().get(0);
        var operatorUsedKind = operatorUsed.getKind();
        // se conter BinaryExpr (conta aritmética sem operadores de comparação), é suposto dar erro
        if (operatorUsedKind.equals("BinaryExpr")) {
            valid = false;
        }
        // a condição é feita com uma variável
        else if (operatorUsedKind.equals("VarRefExpr")) {
            // procuramos pela variável utilizada para estudar o seu tipo
            if (symbolTable.getLocalVariables(currentMethod) != null) {
                for (var localVar : symbolTable.getLocalVariables(currentMethod)) {
                    if (operatorUsed.hasAttribute("name")) {
                        if (localVar.getName().equals(operatorUsed.get("name"))) {
                            // se não for do tipo booleano dá erro
                            if (!localVar.getType().getName().equals("boolean")) {
                                valid = false;
                                break;
                            }
                        }
                    }
                }
            }
        }

        if (!valid) {
            // Create error report
            var message = String.format("Invalid while condition: '%s'", whileNode);
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(whileNode),
                    NodeUtils.getColumn(whileNode),
                    message,
                    null)
            );
        }

        return null;
    }

    private Void visitIfConditions(JmmNode ifConditionNode, SymbolTable symbolTable) {
        boolean valid = true;
        int binaryExprCounter = 0;
        int binaryOpCounter = 0;

        if (!ifConditionNode.getChildren().isEmpty()) {
            var operatorUsed = ifConditionNode.getChildren().get(0).getKind();
            // se conter BinaryExpr (conta aritmética sem operadores de comparação), é suposto dar erro
            if (operatorUsed.equals("BinaryExpr")) {
                valid = false;
            }
            // se for uma operação de comparação
            else if (operatorUsed.equals("BinaryOp")) {
                var operations = ifConditionNode.getDescendants();
                var operationNode = ifConditionNode.getChildren("BinaryOp").get(0);

                for (var operation : operations) {
                    if (operation.getKind().equals("BinaryExpr")) {
                        binaryExprCounter++;
                    } else if (operation.getKind().equals("BinaryOp")) {
                        binaryOpCounter++;
                    }
                }
            }
        }

        if (!valid) {
            // Create error report
            var message = String.format("Invalid if condition: '%s'", ifConditionNode);
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(ifConditionNode),
                    NodeUtils.getColumn(ifConditionNode),
                    message,
                    null)
            );
        }

        return null;
    }

    private Void visitImports(JmmNode importNode, SymbolTable symbolTable) {
        importNode.put("type", importNode.get("ID"));
        return null;
    }
    private Void visitVarArgs(JmmNode varArgNode, SymbolTable symbolTable) {
        varArgNode.put("type", varArgNode.get("value"));
        return null;
    }


    private Void visitVar(JmmNode varNode, SymbolTable symbolTable) {
        varNode.put("type", varNode.get("value"));
        return null;
    }

    private Void visitVoid(JmmNode stringNode, SymbolTable symbolTable) {
        stringNode.put("type", "void");
        return null;
    }

    private Void visitString(JmmNode stringNode, SymbolTable symbolTable) {
        stringNode.put("type", "String");
        return null;
    }

    private Void visitArray(JmmNode arrayNode, SymbolTable symbolTable) {
        arrayNode.put("type", "int[]");
        return null;
    }

    private Void visitInt(JmmNode intNode, SymbolTable symbolTable) {
        intNode.put("type", "int");
        return null;
    }

    private Void visitBoolean(JmmNode booleanNode, SymbolTable symbolTable) {
        booleanNode.put("type", "boolean");
        return null;
    }

    private Void visitAssignStatement(JmmNode assignStatm, SymbolTable symbolTable) {
        List<JmmNode> assignElements = assignStatm.getChildren();
        List<Symbol> localsVar;
        if (symbolTable.getLocalVariables(currentMethod) != null) {
            localsVar = symbolTable.getLocalVariables(currentMethod);
        }
        else {
            localsVar = new ArrayList<Symbol>();
        }
        var importNames = symbolTable.getImports();

        boolean valid = true;
        boolean found = false;
        boolean isDeclared = false;

        // verificar se à esquerda tem variável
        if (!assignStatm.getChildren().isEmpty()) {
            if (!assignStatm.getChildren().get(0).getKind().equals("VarRefExpr")) {
                valid = false;
            }
        }

        // verificar se no caso de variáveis, estas são declaradas
        if (symbolTable.getLocalVariables(currentMethod) != null) {
            var localVariables = symbolTable.getLocalVariables(currentMethod);
            if (localVariables != null) {
                for (var localVar : localVariables) {
                    if (!assignElements.isEmpty()) {
                        if (assignElements.get(0).getKind().equals("VarRefExpr")) {
                            if (assignElements.get(0).hasAttribute("name")) {
                                if (localVar.getName().equals(assignElements.get(0).get("name"))) {
                                    assignStatm.getChildren().get(0).put("type", localVar.getType().getName());
                                    isDeclared = true;
                                    break;
                                }
                            }
                        } else if (assignElements.get(0).getKind().equals("IntegerLiteral")) {
                            if (assignElements.get(0).hasAttribute("name")) {
                                if (localVar.getName().equals(assignElements.get(0).get("name"))) {
                                    assignStatm.getChildren().get(0).put("type", "int");
                                    isDeclared = true;
                                    break;
                                }
                            }
                        }
                    }
                }
            }
        }
        var params = symbolTable.getParameters(currentMethod);
        if (params != null) {
            for (var param : params) {
                if (assignElements.get(0).getKind().equals("VarRefExpr")) {
                    if (assignElements.get(0).hasAttribute("name")) {
                        if (param.getName().equals(assignElements.get(0).get("name"))) {
                            assignStatm.getChildren().get(0).put("type", param.getType().getName());
                            isDeclared = true;
                            break;
                        }
                    }
                }
                else if (assignElements.get(0).getKind().equals("IntegerLiteral")) {
                    if (assignElements.get(0).hasAttribute("name")) {
                        if (param.getName().equals(assignElements.get(0).get("name"))) {
                            assignStatm.getChildren().get(0).put("type", "int");
                            isDeclared = true;
                            break;
                        }
                    }
                }
            }
        }
        var fields = symbolTable.getFields();
        if (fields != null) {
            for (var field : fields) {
                if (assignElements.get(0).getKind().equals("VarRefExpr")) {
                    if (assignElements.get(0).hasAttribute("name")) {
                        if (field.getName().equals(assignElements.get(0).get("name"))) {
                            assignStatm.getChildren().get(0).put("type", field.getType().getName());
                            isDeclared = true;
                            break;
                        }
                    }
                }
                else if (assignElements.get(0).getKind().equals("IntegerLiteral")) {
                    if (assignElements.get(0).hasAttribute("name")) {
                        if (field.getName().equals(assignElements.get(0).get("name"))) {
                            assignStatm.getChildren().get(0).put("type", "int");
                            isDeclared = true;
                            break;
                        }
                    }
                }
            }
        }

        if (!isDeclared) valid = false;

        // Lidar com arrays
        if (localsVar != null) {
            for (var localVar : localsVar) {
                // assignElement.get(0) é sempre igual à variável na qual guardamos valores
                if (assignElements.get(0).hasAttribute("name")) {
                    if (localVar.getName().equals(assignElements.get(0).get("name"))) {
                        assignStatm.getChildren().get(0).put("type", localVar.getType().getName());
                        // Se for do tipo array só pode dar assign a elementos do tipo array
                        if (localVar.getType().isArray()) {
                            assignStatm.getChildren().get(0).put("isArray", "true");
                            assignStatm.put("isArray", "true");
                            // Testar para quando o assign é feito com uma chamada a uma função que retorna um array
                            if (assignElements.get(1).getKind().equals("FunctionCall")) {
                                var functionCallReturn = symbolTable.getReturnType(assignElements.get(1).get("methodName"));
                                var retType = functionCallReturn.getName();
                                var isRetArray = functionCallReturn.isArray();
                                // como verificamos se uma variável array recebe um array na chamada a uma função, se no retorno não receber array dá erro
                                if (!isRetArray) {
                                    valid = false;
                                    break;
                                }
                                // se os tipos forem diferentes, dá erro
                                if (!retType.equals(localVar.getType().getName())) {
                                    valid = false;
                                    break;
                                }
                                assignStatm.put("type", retType);
                            }
                            // Testar para quando o assign é feito com um array
                            else if (!assignElements.get(1).getKind().equals("ArrayInit") && !assignElements.get(1).getKind().equals("NewArray")) {
                                valid = false;
                                break;
                            }
                            assignStatm.put("type", localVar.getType().getName());
                        }
                        // Se não for do tipo array, não pode dar assign a elementos do tipo array
                        else {
                            if (assignElements.get(1).getKind().equals("ArrayInit") || assignElements.get(1).getKind().equals("NewArray")) {
                                valid = false;
                                break;
                            }
                            // Ex: caso em que a = a[10] e 'a' é do tipo int
                            // se a variável for do tipo array
                            if (assignElements.get(1).getKind().equals("ArrayAccess")) {
                                // e tiver o mesmo nome que a variável da direita, dá erro (Variável esquerda nesta fase já não é array)
                                if (assignElements.get(1).getChildren().get(0).hasAttribute("name") && assignElements.get(0).hasAttribute("name")) {
                                    if (assignElements.get(1).getChildren().get(0).get("name").equals(assignElements.get(0).get("name"))) {
                                        valid = false;
                                        break;
                                    }
                                }
                            }
                            assignStatm.put("type", localVar.getType().getName());
                        }
                    }
                }
            }
        }

        // Para variáveis que dão assign a inteiros
        if (assignElements.get(1).getKind().equals("IntegerLiteral")) {
            if (localsVar != null) {
                for (var localVar : localsVar) {
                    if (assignElements.get(0).hasAttribute("name")) {
                        if (localVar.getName().equals(assignElements.get(0).get("name"))) {
                            var expectedValueType = localVar.getType().getName();
                            if (!expectedValueType.equals("int")) {
                                valid = false;
                            }
                        }
                    }
                }
            }
        }
        // assign a uma variável (Ex: varLeft = varRight) ou constante booleana (varLeft = constant)
        else if (assignElements.get(1).getKind().equals("VarRefExpr")) {
            // pesquisa-se pela variável que recebe o valor
            if (localsVar != null) {
                for (var varLeft : localsVar) {
                    if (assignElements.get(0).get("name").equals(varLeft.getName())) {
                        // se for constante booleana
                        if (assignElements.get(1).hasAttribute("name")) {
                            if (assignElements.get(1).get("name").equals("true") || assignElements.get(1).get("name").equals("false")) {
                                if (!varLeft.getType().getName().equals("boolean")) {
                                    valid = false;
                                    break;
                                }
                            }
                        }

                        // pesquisa-se pela variável que dá o valor
                        for (var varRight : localsVar) {
                            if (assignElements.get(1).hasAttribute("name")) {
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
                                        } else {
                                            // verificar se ambas as variáveis provêm de imports
                                            var foundImportLeft = false;
                                            var foundImportRight = false;
                                            if (symbolTable.getImports() != null) {
                                                for (var importName : symbolTable.getImports()) {
                                                    if (varLeft.getType().getName().equals(importName))
                                                        foundImportLeft = true;
                                                    else if (varRight.getType().getName().equals(importName))
                                                        foundImportRight = true;
                                                    if (foundImportLeft && foundImportRight) break;
                                                }
                                            }
                                            if ((foundImportLeft && !foundImportRight) || (!foundImportLeft && foundImportRight)) {
                                                valid = false;
                                                break;
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
        // se for uma chamada a uma função verifica se o elemento que chama a função existe
        else if (assignElements.get(1).getKind().equals("FunctionCall")) {
            if (!assignElements.get(1).getChildren().isEmpty()) {
                var varThatCalledFunction = assignElements.get(1).getChildren().get(0);
                // se não for do tipo this, verifica-se se a variável existe no ficheiro
                if (varThatCalledFunction.hasAttribute("name")) {
                    if (!varThatCalledFunction.get("name").equals("this")) {
                        // verificar se variável que chamou a função existe na classe ou nos imports
                        if (symbolTable.getImports() != null) {
                            for (var importName : symbolTable.getImports()) {
                                if (importName.equals(varThatCalledFunction.get("name"))) {
                                    found = true;
                                    break;
                                }
                            }
                        }
                        if (!found) {
                            if (symbolTable.getLocalVariables(currentMethod) != null) {
                                for (var localVar : symbolTable.getLocalVariables(currentMethod)) {
                                    if (localVar.getName().equals(varThatCalledFunction.get("name"))) {
                                        found = true;
                                        break;
                                    }
                                }
                            }
                        }
                        if (!found) {
                            if (symbolTable.getParameters(currentMethod) != null) {
                                for (var param : symbolTable.getParameters(currentMethod)) {
                                    if (param.getName().equals(varThatCalledFunction.get("name"))) {
                                        found = true;
                                        break;
                                    }
                                }
                            }
                        }
                    }
                    // se for o caso do this, temos de verificar se a função chamada existe na classe
                    else {
                        if (symbolTable.getMethods() != null) {
                            for (var methodName : symbolTable.getMethods()) {
                                if (methodName.equals(assignElements.get(1).get("methodName"))) {
                                    found = true;
                                    break;
                                }
                            }
                        }
                    }
                }
            }
            if (!found) valid = false;
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
        boolean valid = true;
        List<JmmNode> importStatements = importStatm.getDescendants();
        List<String> imports = symbolTable.getImports();

        // verificar se existem imports duplicados
        for (int i = 0; i < symbolTable.getImports().size(); i++) {
            for (int j = i + 1; j < symbolTable.getImports().size(); j++) {
                if (imports.get(i).equals(imports.get(j))) {
                    valid = false;
                    break;
                }
            }
        }

        for (JmmNode impStat : importStatements) {
            imports.add(impStat.get("name"));
        }

        if (!valid) {
            // Create error report
            var message = String.format("Repeated import: '%s'", importStatm);
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(importStatm),
                    NodeUtils.getColumn(importStatm),
                    message,
                    null)
            );
        }

        return null;
    }

    private Void visitRetStatement(JmmNode returnStatm, SymbolTable symbolTable) {
        // return kind and name of elements that are being assigned
        List<JmmNode> returnElements = returnStatm.getDescendants();
        List<Symbol> localVariables;
        if (symbolTable.getLocalVariables(currentMethod) != null) {
            localVariables = symbolTable.getLocalVariables(currentMethod);
        }
        else {
            localVariables = new ArrayList<Symbol>();
        }
        var parameters = symbolTable.getParameters(currentMethod);
        boolean valid = true;

        if (!returnStatm.getDescendants("VarArgs").isEmpty()) valid = false;

        // se for variável ou array o primeiro elemento é válido
        var arrayAccessNodes = returnStatm.getDescendants("ArrayAccess");
        for (var arrayAccessNode : arrayAccessNodes) {
            if (!arrayAccessNode.getChildren().get(0).getKind().equals("ArrayInit") && !arrayAccessNode.getChildren().get(0).getKind().equals("VarRefExpr")) valid = false;
        }

        // não pode conter varargs num return
        if (methods != null) {
            for (var method : methods) {
                if (!returnStatm.getChildren("FunctionCall").isEmpty()) {
                    // se encontrar a função chamada
                    if (returnStatm.getChildren("FunctionCall").get(0).hasAttribute("methodName")) {
                        if (method.get("methodName").equals(returnStatm.getChildren("FunctionCall").get(0).get("methodName"))) {
                            boolean hasVarArgs = !method.getDescendants("VarArgs").isEmpty();
                            if (hasVarArgs) valid = false;
                            break;
                        }
                    }
                }
            }
        }

        if (returnElements != null) {
            for (JmmNode returnElement : returnElements) {
                if (returnElement.hasAttribute("name")) {
                    List<String> elementInfo = Arrays.asList(returnElement.getKind(), returnElement.get("name"));
                    returnTypes.add(elementInfo);
                } else if (returnElement.hasAttribute("methodName")) {
                    List<String> elementInfo = Arrays.asList(returnElement.getKind(), returnElement.get("methodName"));
                    returnTypes.add(elementInfo);
                }
            }
        }

        boolean found = false;

        var funcType = returnStatm.getParent().getChildren().get(0);
        boolean isArray = false;
        String funcTypeExpected = "";
        if (funcType.getKind().equals("Array")) {
            isArray = true;
            funcTypeExpected = "int";
        }
        else if (funcType.hasAttribute("value")) {
            funcTypeExpected = funcType.get("value");
        }

        // Verificar se as variáveis de retorno existem e se coincidem com o tipo esperado de retorno da função
        if (returnElements != null) {
            for (var returnElement : returnElements) {
                found = false;
                // se for variável
                var hasArrayAccess = !returnStatm.getDescendants("ArrayAccess").isEmpty();
                if (returnElement.getKind().equals("VarRefExpr")) {
                    if (localVariables != null && !localVariables.isEmpty()) {
                        // procura nas variáveis locais se existe
                        for (var localVar : localVariables) {
                            if (returnElement.hasAttribute("name")) {
                                if (localVar.getName().equals(returnElement.get("name"))) {
                                    found = true;
                                    if (!localVar.getType().getName().equals(funcTypeExpected)) valid = false;
                                    if (hasArrayAccess && isArray) valid = false;
                                    // se o return não tiver um array access, estudam-se tipos de return array/função que espera resultado em array
                                    if (!hasArrayAccess) {
                                        if ((localVar.getType().isArray() && !isArray) || (!localVar.getType().isArray() && isArray))
                                            valid = false;
                                    }
                                    break;
                                }
                            }
                        }
                    }

                    // se já encontrou, verifica se a próxima variável no return, se existir, é declarada
                    // se a função não tiver parametros, é escusado verificá-los
                    if (found || parameters == null) continue;
                    // procura nos parametros da função atual se a variável existe
                    for (var param : parameters) {
                        if (returnElement.hasAttribute("name")) {
                            if (param.getName().equals(returnElement.get("name"))) {
                                found = true;
                                if (!hasArrayAccess) {
                                    if ((param.getType().isArray() && !isArray) || (!param.getType().isArray() && isArray))
                                        valid = false;
                                }
                                if (!param.getType().getName().equals(funcTypeExpected)) valid = false;
                                break;
                            }
                        }
                    }
                    if (!found) {
                        if (symbolTable.getFields() != null) {
                            for (var field : symbolTable.getFields()) {
                                if (returnElement.hasAttribute("name")) {
                                    if (field.getName().equals(returnElement.get("name"))) {
                                        found = true;
                                        if (!hasArrayAccess) {
                                            if ((field.getType().isArray() && !isArray) || (!field.getType().isArray() && isArray))
                                                valid = false;
                                        }
                                        if (!field.getType().getName().equals(funcTypeExpected)) valid = false;
                                        break;
                                    }
                                }
                            }
                        }
                    }
                    if (!found) {
                        if (symbolTable.getImports() != null) {
                            for (var importName : symbolTable.getImports()) {
                                if (returnElement.hasAttribute("name")) {
                                    if (importName.equals(returnElement.get("name"))) {
                                        found = true;
                                        break;
                                    }
                                }
                            }
                        }
                    }
                    if (!found) {
                        if (returnElement.hasAttribute("name")) {
                            if (returnElement.hasAttribute("name")) {
                                if (returnElement.get("name").equals("true") || returnElement.get("name").equals("false")) {
                                    found = true;
                                    if (!funcTypeExpected.equals("boolean")) valid = false;
                                    break;
                                }
                            }
                        }
                    }

                    if (found) break;
                    // se não existir, dá erro
                    valid = false;
                }
            }
        }

        // Se o return for uma chamada a uma função. Verificar se o valor recebido é o tipo especificado da função atual
        JmmNode calledFunction = null;
        JmmNode currentFunction = null;
        List<JmmNode> paramsGiven = new ArrayList<JmmNode>();
        var numberParamsGiven = 0;
        if (!returnElements.isEmpty()) {
            if (returnElements.get(0).getKind().equals("FunctionCall")) {
                if (methods != null) {
                    // extraio o JmmNode da função chamada para analisar o valor de retorno desta
                    for (var method : methods) {
                        if (returnElements.get(0).hasAttribute("methodName") && method.hasAttribute("methodName")) {
                            if (returnElements.get(0).get("methodName").equals(method.get("methodName"))) {
                                calledFunction = method;
                                break;
                            }
                        }
                    }
                    // extraio o JmmNode da função atual, onde se faz a chamada de outra função
                    for (var method : methods) {
                        if (method.hasAttribute("methodName")) {
                            if (method.get("methodName").equals(currentMethod)) {
                                currentFunction = method;
                                break;
                            }
                        }
                    }
                }
                if (calledFunction != null && currentFunction != null) {
                    // se a função chamada não retornar o mesmo tipo da função atual, dá erro
                    if (calledFunction.getChildren().get(0).hasAttribute("type") && currentFunction.getChildren().get(0).hasAttribute("type")) {
                        if (!calledFunction.getChildren().get(0).get("type").equals(currentFunction.getChildren().get(0).get("type"))) {
                            valid = false;
                        }
                    }
                    // se for dado um número errado de argumentos, tem que dar erro
                    for (var returnElement : returnElements) {
                        if (!returnElement.getKind().equals("FunctionCall")) {
                            paramsGiven.add(returnElement);
                            numberParamsGiven++;
                        }
                    }
                    var paramsExpected = calledFunction.getChildren("Param");
                    var numParamsExpected = paramsExpected.size();
                    // Subtrai-se 1 porque corresponde à variável que chama a função. Ex: a variável 'a' em a.foo(b) -> no contador só deve contar 'b'
                    if ((numberParamsGiven - 1) != numParamsExpected) valid = false;

                    // se for dado um elemento na chamada da função inválido tem que dar erro
                    if (valid) {
                        for (int i = 0; i < numParamsExpected; i++) {
                            Symbol varUsed = null;
                            // percorrer variáveis locais e parametros para descobrir o tipo da variável a que se deu assign
                            if (symbolTable.getLocalVariables(currentMethod) != null) {
                                for (var localVar : symbolTable.getLocalVariables(currentMethod)) {
                                    // i + 1 para avançar a variável 'a' em a.foo(b)
                                    if (paramsGiven.get(i + 1).hasAttribute("name")) {
                                        if (localVar.getName().equals(paramsGiven.get(i + 1).get("name"))) {
                                            varUsed = localVar;
                                            break;
                                        }
                                    }
                                }
                            }
                            if (symbolTable.getParameters(currentMethod) != null) {
                                for (var param : symbolTable.getParameters(currentMethod)) {
                                    // i + 1 para avançar a variável 'a' em a.foo(b)
                                    if (paramsGiven.get(i + 1).hasAttribute("name")) {
                                        if (param.getName().equals(paramsGiven.get(i + 1).get("name"))) {
                                            varUsed = param;
                                            break;
                                        }
                                    }
                                }
                            }

                            // não encontrou a variável dada como argumento, dá erro
                            if (varUsed == null) {
                                valid = false;
                                break;
                            }

                            if (paramsExpected.get(i).hasAttribute("type")) {
                                if (!varUsed.getType().getName().equals(paramsExpected.get(i).get("type"))) {
                                    returnStatm.put("type", paramsExpected.get(i).get("type"));
                                    valid = false;
                                    break;
                                }
                            }
                        }
                    }
                }
            }
            // se o return for de uma conta aritmética
            else if (returnElements.get(0).getKind().equals("BinaryExpr")) {
                for (var returnElement : returnElements) {
                    // só é preciso analisar variáveis int. Ignora os operadores.
                    if (!returnElement.getKind().equals("BinaryExpr")) {
                        if (returnElement.getKind().equals("IntegerLiteral")) continue;
                        // caso a variável no return esteja nas variáveis locais
                        if (localVariables != null && !localVariables.isEmpty()) {
                            for (var localVar : localVariables) {
                                // verifica se é variável local da função
                                if (returnElement.hasAttribute("name")) {
                                    if (localVar.getName().equals(returnElement.get("name"))) {
                                        // se o valor for diferente de int ou se a operação aritmética for realizar com um array, é inválido
                                        if (!localVar.getType().getName().equals("int") || localVar.getType().isArray()) {
                                            valid = false;
                                        } else returnStatm.put("type", "int");
                                    }
                                }
                            }
                        }
                        // caso esteja nos fields
                        if (valid) {
                            if (symbolTable.getFields() != null) {
                                for (var field : symbolTable.getFields()) {
                                    // verifica se é field da classe
                                    if (returnElement.hasAttribute("name")) {
                                        if (field.getName().equals(returnElement.get("name"))) {
                                            // se o valor for diferente de int ou se a operação aritmética for realizar com um array, é inválido
                                            if (!field.getType().getName().equals("int") || field.getType().isArray()) {
                                                returnStatm.put("type", "int");
                                                valid = false;
                                            } else returnStatm.put("type", "int");
                                        }
                                    }
                                }
                            }
                        }
                        // caso esteja nos parametros da função atual
                        if (valid) {
                            if (symbolTable.getParameters(currentMethod) != null) {
                                for (var param : symbolTable.getParameters(currentMethod)) {
                                    // verifica se é parametro da função atual
                                    if (returnElement.hasAttribute("name")) {
                                        if (param.getName().equals(returnElement.get("name"))) {
                                            // se o valor for diferente de int ou se a operação aritmética for realizar com um array, é inválido
                                            if (!param.getType().getName().equals("int") || param.getType().isArray()) {
                                                returnStatm.put("type", "int");
                                                valid = false;
                                            } else returnStatm.put("type", "int");
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            // se o return for um acesso a um array
            else if (returnElements.get(0).getKind().equals("ArrayAccess")) {
                // verificar se existe varArgs
                boolean varArgsExists = false;
                if (methods != null) {
                    for (var method : methods) {
                        if (method.get("methodName").equals(currentMethod)) {
                            // se tiver parametros
                            if (!method.getChildren("Param").isEmpty()) {
                                for (var param : method.getChildren("Param")) {
                                    if (!param.getChildren("VarArgs").isEmpty()) {
                                        varArgsExists = true;
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }

                var arrayVar = returnElements.get(1);
                Symbol arrayVarSymbol = null;
                Symbol arrayIndexSymbol = null;
                var arrayIndex = returnElements.get(2);
                boolean foundArrayDecl = false;
                boolean foundArrayIndex = false;

                // verificar se existe o arrayVar no ficheiro
                if (symbolTable.getLocalVariables(currentMethod) != null) {
                    for (var localVar : symbolTable.getLocalVariables(currentMethod)) {
                        if (arrayVar.hasAttribute("name")) {
                            if (localVar.getName().equals(arrayVar.get("name"))) {
                                foundArrayDecl = true;
                                arrayVarSymbol = localVar;
                            }
                        }
                    }
                }
                // se for varargs, é como se fosse array
                if (!foundArrayDecl) {
                    if (symbolTable.getParameters(currentMethod) != null) {
                        for (var param : symbolTable.getParameters(currentMethod)) {
                            if (arrayVar.hasAttribute("name")) {
                                if (param.getName().equals(arrayVar.get("name"))) {
                                    foundArrayDecl = true;
                                    arrayVarSymbol = param;
                                }
                            }
                        }
                    }
                }
                // se for varargs, é como se fosse array
                if (!foundArrayDecl) {
                    if (symbolTable.getFields() != null) {
                        for (var field : symbolTable.getFields()) {
                            if (arrayVar.hasAttribute("name")) {
                                if (field.getName().equals(arrayVar.get("name"))) {
                                    foundArrayDecl = true;
                                    arrayVarSymbol = field;
                                }
                            }
                        }
                    }
                }
                if (!foundArrayDecl && !varArgsExists) valid = false;

                // se array está declarado, verificar se tem o tipo correto
                if (foundArrayDecl) {
                    // se não for do tipo array, falha
                    if (!arrayVarSymbol.getType().isArray() && !varArgsExists) {
                        valid = false;
                    }
                }

                // procuramos o valor do tipo dado dentro de []
                if (valid) {
                    // se for uma variável
                    if (arrayIndex.getKind().equals("VarRefExpr")) {
                        // verificar se existe a variável index no ficheiro
                        if (symbolTable.getLocalVariables(currentMethod) != null) {
                            for (var localVar : symbolTable.getLocalVariables(currentMethod)) {
                                if (arrayIndex.hasAttribute("name")) {
                                    if (localVar.getName().equals(arrayIndex.get("name"))) {
                                        foundArrayIndex = true;
                                        arrayIndexSymbol = localVar;
                                    }
                                }
                            }
                        }
                        if (!foundArrayIndex) {
                            if (symbolTable.getParameters(currentMethod) != null) {
                                for (var param : symbolTable.getParameters(currentMethod)) {
                                    if (arrayIndex.hasAttribute("name")) {
                                        if (param.getName().equals(arrayIndex.get("name"))) {
                                            foundArrayIndex = true;
                                            arrayIndexSymbol = param;
                                        }
                                    }
                                }
                            }
                        }
                        if (!foundArrayIndex) {
                            if (symbolTable.getFields() != null) {
                                for (var field : symbolTable.getFields()) {
                                    if (arrayIndex.hasAttribute("name")) {
                                        if (field.getName().equals(arrayIndex.get("name"))) {
                                            foundArrayIndex = true;
                                            arrayIndexSymbol = field;
                                        }
                                    }
                                }
                            }
                        }
                        if (!foundArrayIndex) valid = false;
                        if (arrayIndexSymbol != null) {
                            if (arrayIndexSymbol.getType().isArray()) valid = false;
                            if (!arrayIndexSymbol.getType().getName().equals("int")) valid = false;
                        }
                    }
                }
            }
            // Check if var in return exists
            else {
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
                if (returnTypes != null) {
                    for (var returnElement : returnTypes) {
                        if (symbolTable.getParameters(currentMethod) != null && symbolTable.getParameters(currentMethod).stream()
                                .anyMatch(param -> param.getName().equals(returnElement.get(1)))) {
                            returnTypes.clear();
                            return null;
                        } else if (symbolTable.getLocalVariables(currentMethod) != null && symbolTable.getLocalVariables(currentMethod).stream()
                                .anyMatch(local -> local.getName().equals(returnElement.get(1)))) {
                            returnTypes.clear();
                            return null;
                        } else if (symbolTable.getImports() != null && symbolTable.getImports().stream()
                                .anyMatch(imp -> imp.equals(returnElement.get(1)))) {
                            returnTypes.clear();
                            return null;
                        } else if (symbolTable.getFields() != null && symbolTable.getFields().stream()
                                .anyMatch(field -> field.getName().equals(returnElement.get(1)))) {
                            returnTypes.clear();
                            return null;
                        } else if (returnElement.get(1).equals("true") || returnElement.get(1).equals("false")) {
                            for (var method : methods) {
                                if (method.get("methodName").equals(currentMethod)) {
                                    if (method.getChildren().get(0).hasAttribute("value")) {
                                        var expectedFuncRetType = method.getChildren().get(0).get("value");
                                        if (!expectedFuncRetType.equals("boolean")) valid = false;
                                    }
                                }
                            }
                            if (valid) return null;
                        }
                        valid = false;
                    }
                }
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

        boolean valid = true;

        var arrayAccessNodes = method.getDescendants("ArrayAccess");

        for (var arrayAccessNode : arrayAccessNodes) {
            if (!arrayAccessNode.getChildren().get(0).getKind().equals("ArrayInit") && !arrayAccessNode.getChildren().get(0).getKind().equals("VarRefExpr")) valid = false;
        }

        // verificar se existem métodos repetidos
        for (int i = 0; i < methods.size(); i++) {
            for (int j = i + 1; j < methods.size(); j++) {
                if (methods.get(i).hasAttribute("methodName") && methods.get(j).hasAttribute("methodName")) {
                    if (methods.get(i).get("methodName").equals(methods.get(j).get("methodName"))) {
                        valid = false;
                        break;
                    }
                }
            }
        }

        // verificar se existem parametros repetidos
        if (table.getParameters(currentMethod) != null) {
            for (int i = 0; i < table.getParameters(currentMethod).size(); i++) {
                for (int j = i + 1; j < table.getParameters(currentMethod).size(); j++) {
                    if (table.getParameters(currentMethod).get(i).getName().equals(table.getParameters(currentMethod).get(j).getName())) {
                        valid = false;
                        break;
                    }
                }
            }
        }

        // verificar se existe uma variável com o nome reservado length
        if (table.getLocalVariables(currentMethod) != null) {
            for (var localVar : table.getLocalVariables(currentMethod)) {
                if (localVar.getName().equals("length")) {
                    valid = false;
                    break;
                }
            }
        }
        if (table.getFields() != null) {
            for (var field : table.getFields()) {
                if (field.getName().equals("length")) {
                    valid = false;
                    break;
                }
            }
        }

        // verificar se tem mais que um return
        var returnNodes = method.getDescendants("ReturnStmt");
        if (returnNodes.size() > 1) valid = false;

        // verificar se n tem return no tipo void
        if (!method.getDescendants("Void").isEmpty()) {
            if (!returnNodes.isEmpty()) valid = false;
        }
        else {
            if (!currentMethod.equals("main")) {
                if (returnNodes.isEmpty()) valid = false;
            }
        }

        // verificar se o return não é o último return existente
        var descendents = method.getDescendants();
        // se não for a função main
        if (!currentMethod.equals("main")) {
            // se não conter o tipo void
            if (method.getChildren("Void").isEmpty()) {
                // o último node tem que ser um return
                if (!method.getChildren().get(method.getChildren().size()-1).getKind().equals("ReturnStmt")) valid = false;
            }
        }

        // verificar se tem return para o caso de uma função que precise de retornar um tipo
        if (method.getChildren().get(0).hasAttribute("value")) {
            int numReturns = method.getDescendants("ReturnStmt").size();
            if (method.getChildren().get(0).hasAttribute("value")) {
                String funcRetType = method.getChildren().get(0).get("value");
                if (funcRetType.equals("int") || funcRetType.equals("boolean")) {
                    if (numReturns == 0) valid = false;
                }
            }
        }

        // validar a estrutura do método main
        if (currentMethod.equals("main")) {
            // só pode ter um node Param como node filho (args)
            if (method.getChildren("Param").size() > 1) valid = false;

            var mainParam = method.getChildren("Param").get(0);
            var paramIsArray = !mainParam.getChildren("Array").isEmpty();
            // args tem que ser array senão dá erro
            if (!paramIsArray) {
                valid = false;
            }
            else {
                // Se o parâmetro for array
                var arrayKind = mainParam.getChildren("Array").get(0);
                // args tem que ser do tipo String
                if (!arrayKind.getChildren().get(0).getKind().equals("String")) {
                    valid = false;
                }
            }

            // se conter this. é inválido já que é static
            var functionCalls = method.getDescendants("VarRefExpr");
            if (!functionCalls.isEmpty()) {
                for (var functionCall : functionCalls) {
                    if (functionCall.hasAttribute("name")) {
                        if (functionCall.get("name").equals("this")) {
                            valid = false;
                            break;
                        }
                    }
                }
            }

            // Se o método main utilizar um field num assign, dá erro por ser static
            if (table.getFields() != null) {
                var assigns = method.getDescendants("AssignStmt");
                if (!assigns.isEmpty()) {
                    for (var assignStmt : assigns) {
                        var leftVar = assignStmt.getChildren().get(0);
                        var rightVar = assignStmt.getChildren().get(1);
                        boolean leftVarIsLocal = false;
                        boolean rightVarIsLocal = false;

                        if (table.getLocalVariables(currentMethod) != null) {
                            for (var localVar : table.getLocalVariables(currentMethod)) {
                                if (leftVar.hasAttribute("name")) {
                                    if (localVar.getName().equals(leftVar.get("name"))) {
                                        leftVarIsLocal = true;
                                    }
                                }
                                if (rightVar.hasAttribute("name")) {
                                    if (localVar.getName().equals(rightVar.get("name"))) {
                                        rightVarIsLocal = true;
                                    }
                                }
                            }
                        }

                        for (var field : table.getFields()) {
                            if (leftVar.hasAttribute("name")) {
                                if (field.getName().equals(leftVar.get("name"))) {
                                    if (!leftVarIsLocal) valid = false; // se não for inicializada como variável local, dá erro por tentar aceder a um field
                                }
                            }
                            if (rightVar.hasAttribute("name")) {
                                if (field.getName().equals(rightVar.get("name"))) {
                                    if (!rightVarIsLocal) valid = false;
                                }
                            }
                            if (!valid) break;
                        }
                        if (!valid) break;
                    }
                }
            }
        }

        var returnType = table.getReturnType(currentMethod);
        List<Symbol> localsList;
        if (table.getLocalVariables(currentMethod) != null) {
            localsList = table.getLocalVariables(currentMethod);
        }
        else {
            localsList = new ArrayList<Symbol>();
        }
        Pair<String, List<Symbol>> pairLocals = new Pair<>(currentMethod, localsList);
        allLocalVariables.add(pairLocals);

        // verificar se recebe tipo de retorno esperado
        if (!currentMethod.equals("main")) {
            if (method.getChildren("Void").isEmpty()) {
                var returnNode = method.getDescendants("ReturnStmt");
                if (returnNode.size() > 1) valid = false;
                else {
                    var returnNodeKind = returnNode.get(0).getChildren().get(0);
                    if (returnNodeKind.getKind().equals("LiteralInteger")) {
                        if (!returnType.getName().equals("int")) valid = false;
                    }
                    else if (returnNodeKind.getKind().equals("BinaryExpr")) {
                        if (!returnType.getName().equals("int")) valid = false;
                    }
                    else if (returnNodeKind.getKind().equals("BinaryOp")) {
                        if (!returnType.getName().equals("boolean")) valid = false;
                    }
                    else if (returnNodeKind.getKind().equals("VarRefExpr")) {
                        // descobrir o tipo da variável
                        if (table.getParameters(currentMethod) != null) {
                            for (var param : table.getParameters(currentMethod)) {
                                if (returnNodeKind.hasAttribute("name")) {
                                    if (param.getName().equals(returnNodeKind.get("name"))) {
                                        if (!returnType.getName().equals(param.getType().getName())) {
                                            valid = false;
                                            break;
                                        }
                                    }
                                }
                            }
                        }
                        if (table.getLocalVariables(currentMethod) != null) {
                            for (var localVar : table.getLocalVariables(currentMethod)) {
                                if (returnNodeKind.hasAttribute("name")) {
                                    if (localVar.getName().equals(returnNodeKind.get("name"))) {
                                        if (!returnType.getName().equals(localVar.getType().getName())) {
                                            valid = false;
                                            break;
                                        }
                                    }
                                }
                            }
                        }
                        if (table.getFields() != null) {
                            for (var field : table.getFields()) {
                                if (returnNodeKind.hasAttribute("name")) {
                                    if (field.getName().equals(returnNodeKind.get("name"))) {
                                        if (!returnType.getName().equals(field.getType().getName())) {
                                            valid = false;
                                            break;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            // se for função do tipo void
            else {
                // Se tiver returnNode, falha. Ex: return; seria aceite, mas não é registado pela nossa gramática daí,
                // se tiver mais que um node ReturnStmt, é um return com valores, por isso tem que falhar
                var returnNode = method.getDescendants("ReturnStmt");
                if (!returnNode.isEmpty()) valid = false;
            }
        }

        if (method.get("methodName").equals("main")) {
            method.put("type", "main");
        }
        else if (method.getChildren().get(0).getKind().equals("Array")) {
            var nodeArray = method.getChildren().get(0);
            method.put("type", nodeArray.getChildren().get(0).get("value"));
            method.put("isArray", "true");
        }
        else {
            if (method.getChildren().get(0).hasAttribute("value")) {
                method.put("type", method.getChildren().get(0).get("value"));
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
                        if (!assignment.getParent().getChildren("VarDecl").isEmpty()) {
                            for (var variableCallingFunction : assignment.getParent().getChildren("VarDecl")) {
                                if (!functionCall.getParent().getChildren("VarRefExpr").isEmpty()) {
                                    if (functionCall.getParent().getChildren("VarRefExpr").get(0).hasAttribute("name")) {
                                        var variableDecl = functionCall.getParent().getChildren("VarRefExpr").get(0).get("name");
                                        // se encontrarmos a variável que chama a função
                                        if (variableCallingFunction.get("name").equals(variableDecl)) {
                                            // confirmar se a variável contém um tipo de valor
                                            if (!variableCallingFunction.getChildren().isEmpty()) {
                                                if (variableCallingFunction.getChildren().get(0).hasAttribute("value")) {
                                                    // guardamos a function call e o número de parametros recebidos pela função
                                                    Pair<JmmNode, JmmNode> pairFunc = new Pair<>(functionCall, variableCallingFunction);
                                                    functionsCalled.add(pairFunc);
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
                    }
                }
            }
        }

        // Analisar se uma variável ao chamar uma função recebe o tipo de retorno correto
        // percorremos as chamadas feitas a funções até agora
        for (Pair<JmmNode, JmmNode> functionCalled : functionsCalled) {
            // se coincidir com o método a analisar atualmente, estudamos esse método
            if (functionCalled.a.get("methodName").equals(currentMethod)) {

                // verificar se foi dado o número correto de parametros varargs
                var numVarArgsCalled = 0;

                for (var methodChild : methodChildren) {
                    // varargs está dentro do Kind Param
                    if (methodChild.getKind().equals("Param")) {
                        int parametersNumber = method.getChildren("Param").size();

                        var varArgsNodes = method.getDescendants("VarArgs");
                        numVarArgsCalled = varArgsNodes.size();

                        // se existir pelo menos um parametro varargs
                        if (numVarArgsCalled > 0) {

                            // verificar se foi dado o tipo correto de parametros ao varargs
                            var numParamsGiven = 0;
                            if (!functionCalled.a.getChildren().isEmpty()) {
                                for (var node : functionCalled.a.getChildren()) {
                                    if (node.hasAttribute("name")) {
                                        if (node.get("name").equals("this")) continue;
                                    }
                                    numParamsGiven++;
                                }
                            }
                            var numParamsExpected = method.getChildren("Param").size();
                            // se receber menos que os esperados, dá erro
                            if (numParamsExpected > numParamsGiven) {
                                valid = false;
                                break;
                            }

                            if (!method.getChildren().isEmpty()) {
                                boolean isLastParamVarArgs = !method.getChildren().get(parametersNumber).getChildren("VarArgs").isEmpty();
                                // só pode existir um parametro varargs nos parametros da função e tem que estar como último parâmetro dado, caso contrário dá erro
                                if (!isLastParamVarArgs || numVarArgsCalled > 1) {
                                    valid = false;
                                    break;
                                }
                            }
                            // procuramos pelo return statement para verificar o seu tipo
                            for (var child : methodChildren) {
                                if (child.getKind().equals("ReturnStmt")) {
                                    if (!functionCalled.b.getChildren().isEmpty()) {
                                        var variableThatCalledFunctionKind = functionCalled.b.getChildren().get(0).getKind();
                                        // se a variável que chamou a função é um inteiro, o return de um vargars tem que ser um array access
                                        if (variableThatCalledFunctionKind.equals("Integer")) {
                                            // tipo da variável que guarda o valor de return do método a analisar agora
                                            if (functionCalled.b.getChildren().get(0).hasAttribute("value")) {
                                                var variableThatCalledFunctionType = functionCalled.b.getChildren().get(0).get("value");

                                                // se os tipos de retorno forem diferentes ou se não for um array access, dá erro
                                                if (!returnType.getName().equals(variableThatCalledFunctionType))
                                                    valid = false;
                                                if (!child.getChildren().isEmpty()) {
                                                    if (!child.getChildren().get(0).getKind().equals("ArrayAccess"))
                                                        valid = false;
                                                }
                                            }
                                        }
                                        // se a variável que chamou a função é um array, o return de um vargars pode o ser parâmetro do varargs
                                        else if (variableThatCalledFunctionKind.equals("Array")) {
                                            boolean found = false;
                                            if (!child.getChildren().isEmpty()) {
                                                for (var returnElement : child.getChildren()) {
                                                    // se o retorno for o parametro vargars (parametro é automaticamento array)
                                                    if (table.getParameters(currentMethod) != null) {
                                                        for (var param : table.getParameters(currentMethod)) {
                                                            if (found) break;
                                                            if (returnElement.hasAttribute("name")) {
                                                                if (returnElement.get("name").equals(param.getName())) {
                                                                    found = true;
                                                                    break;
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                            if (!found) valid = false;
                                        }
                                    }
                                }
                            }
                            method.put("isVarArgs", "true");
                        }
                        // se não conter varargs
                        else {
                            // verificar se foi dado o tipo correto de parametros
                            int numParamsGiven;
                            if (functionCalled.a.getChildren().isEmpty()) {
                                numParamsGiven = 0;
                            }
                            else {
                                numParamsGiven = functionCalled.a.getNumChildren() - 1;
                                var numParamsExpected = method.getChildren("Param").size();
                                // se receber menos que os esperados, dá erro
                                if (numParamsExpected != numParamsGiven) {
                                    valid = false;
                                    break;
                                }
                            }
                        }
                    }
                }
            }
        }

        if (!valid) {
            var message = String.format("Invalid method: '%s'", method);
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(method),
                    NodeUtils.getColumn(method),
                    message,
                    null)
            );
        }

        return null;
    }

    private Void visitVarDecl(JmmNode varDecl, SymbolTable table) {

        // verificar se o ‘id’ da declaração existe
        boolean correctID = false;

        if (!varDecl.getDescendants("VarArgs").isEmpty()) {
            var message = String.format("Can´t use varargs on locals or fields '%s'.", varDecl);
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(varDecl),
                    NodeUtils.getColumn(varDecl),
                    message,
                    null)
            );
        }

        if (varDecl.getChildren().get(0).getKind().equals("Array")) {
            var arrayNode = varDecl.getChildren().get(0);
            if (arrayNode.getChildren().get(0).hasAttribute("value")) {
                if (arrayNode.getChildren().get(0).get("value").equals("int")) {
                    correctID = true;
                }
            }
        }

        var idVarDecl = varDecl.getChildren().get(0);
        if (idVarDecl.hasAttribute("value")) {
            if (idVarDecl.get("value").equals("int") || idVarDecl.get("value").equals("boolean")) {
                correctID = true;
            }

            if (!correctID) {
                if (table.getImports() != null) {
                    for (var importName : table.getImports()) {
                        if (importName.equals(idVarDecl.get("value"))) {
                            correctID = true;
                            break;
                        }
                    }
                }
            }

            if (!correctID) {
                if (table.getClassName().equals(idVarDecl.get("value"))) correctID = true;
            }
        }

        if (!correctID) {
            var message = String.format("Invalid id declaration '%s'.", varDecl);
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(varDecl),
                    NodeUtils.getColumn(varDecl),
                    message,
                    null)
            );
        }

        // verificar se existem variáveis repetidas
        if (varDecl.getParent().getKind().equals("MethodDecl")) {
            if (table.getLocalVariables(currentMethod) != null) {
                var localVariables = table.getLocalVariables(currentMethod);
                if (localVariables != null && !localVariables.isEmpty()) {
                    for (int i = 0; i < localVariables.size(); i++) {
                        for (int j = i + 1; j < localVariables.size(); j++) {
                            if (localVariables.get(i).equals(localVariables.get(j))) {
                                var message = String.format("Repeated variable '%s'.", varDecl);
                                addReport(Report.newError(
                                        Stage.SEMANTIC,
                                        NodeUtils.getLine(varDecl),
                                        NodeUtils.getColumn(varDecl),
                                        message,
                                        null)
                                );
                            }
                        }
                    }
                }
            }
        }

        // Check if exists a parameter or variable declaration with the same name as the variable reference
        var varRefName = varDecl.get("name");

        // Var is a field, return
        if (table.getFields().stream()
                .anyMatch(param -> param.getName().equals(varRefName))) {
            if (varDecl.getChildren().get(0).getKind().equals("Array")) {
                if (varDecl.getChildren().get(0).getChildren().get(0).hasAttribute("value")) {
                    varDecl.put("type", varDecl.getChildren().get(0).getChildren().get(0).get("value"));
                }
            }
            else {
                if (varDecl.getChildren().get(0).hasAttribute("value")) {
                    varDecl.put("type", varDecl.getChildren().get(0).get("value"));
                }
            }
            return null;
        }

        // Var is a parameter, return
        if (table.getParameters(currentMethod).stream()
                .anyMatch(param -> param.getName().equals(varRefName))) {
            if (varDecl.getChildren().get(0).getKind().equals("Array")) {
                if (varDecl.getChildren().get(0).getChildren().get(0).hasAttribute("value")) {
                    varDecl.put("type", varDecl.getChildren().get(0).getChildren().get(0).get("value"));
                }
            }
            else {
                if (varDecl.getChildren().get(0).hasAttribute("value")) {
                    varDecl.put("type", varDecl.getChildren().get(0).get("value"));
                }
            }
            return null;
        }

        // Var is a declared variable or imported package, return
        if (table.getLocalVariables(currentMethod) != null) {
            if (table.getLocalVariables(currentMethod).stream()
                    .anyMatch(varDec -> varDec.getName().equals(varRefName)) ||
                    table.getImports().stream().anyMatch(imp -> imp.equals(varRefName))
            ) {
                if (varDecl.getChildren().get(0).getKind().equals("Array")) {
                    if (varDecl.getChildren().get(0).getChildren().get(0).hasAttribute("value")) {
                        varDecl.put("type", varDecl.getChildren().get(0).getChildren().get(0).get("value"));
                    }
                } else if (varDecl.getChildren().get(0).getKind().equals("Var")) {
                    if (varDecl.getChildren().get(0).hasAttribute("value")) {
                        varDecl.put("type", varDecl.getChildren().get(0).get("value"));
                    }
                } else {
                    if (varDecl.getChildren().get(0).hasAttribute("value")) {
                        varDecl.put("type", varDecl.getChildren().get(0).get("value"));
                    }
                }
                return null;
            }
        }

        if (table.getImports() != null) {
            if (table.getImports().stream()
                    .anyMatch(varDec -> varDec.equals(varRefName))) {
                return null;
            }
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
