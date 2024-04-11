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
        addVisit("Array", this::visitArray);
        addVisit("Integer", this::visitInt);
        addVisit("Boolean", this::visitBoolean);
        addVisit("String", this::visitString);
        addVisit("Void", this::visitVoid);
        addVisit("Var", this::visitVar);
        addVisit("VarArgs", this::visitVarArgs);
        addVisit("ImportStatment", this::visitImports);
        addVisit("Expression", this::visitFunctionCall);
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
    }
    /*
    private Void example(JmmNode Node, SymbolTable symbolTable) {
        boolean valid = true;

        if (!valid) {
            // Create error report
            var message = String.format("Invalid: '%s'");
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(Node),
                    NodeUtils.getColumn(Node),
                    message,
                    null)
            );
        }

        return null;
    }
    */

    private Void visitBinaryOp(JmmNode binaryOpNode, SymbolTable symbolTable) {
        boolean valid = true;

        var varRefExpressions = binaryOpNode.getDescendants("VarRefExpr");
        int countBoolConsts = 0;
        int countIntConsts = binaryOpNode.getChildren("IntegerLiteral").size();

        // contar valores booleanos numa VarRefExpr
        for (var var : varRefExpressions) {
            if (var.get("name").equals("true") || var.get("name").equals("false")) {
                countBoolConsts++;
            }
            else {
                // se forem variáveis locais
                for (var locarVar : symbolTable.getLocalVariables(currentMethod)) {
                    if (locarVar.getName().equals(var.get("name"))) {
                        if (locarVar.getType().getName().equals("boolean")) {
                            countBoolConsts++;
                        }
                    }
                }
                // se forem parametros
                for (var param : symbolTable.getParameters(currentMethod)) {
                    if (param.getName().equals(var.get("name"))) {
                        if (param.getType().getName().equals("boolean")) {
                            countBoolConsts++;
                        }
                    }
                }
            }
        }

        // contar valores inteiros numa VarRefExpr
        for (var var : varRefExpressions) {
            // se forem variáveis locais
            for (var locarVar : symbolTable.getLocalVariables(currentMethod)) {
                if (locarVar.getName().equals(var.get("name"))) {
                    if (locarVar.getType().getName().equals("int")) {
                        countIntConsts++;
                    }
                }
            }
            // se forem parametros
            for (var param : symbolTable.getParameters(currentMethod)) {
                if (param.getName().equals(var.get("name"))) {
                    if (param.getType().getName().equals("int")) {
                        countIntConsts++;
                    }
                }
            }
        }

        if (countIntConsts > 0) {
            // operador && não funciona com ints
            if (binaryOpNode.get("op").equals("&&")) valid = false;
        }

        if (countBoolConsts > 0 && countIntConsts == 0) {
            // operador < só funciona com ints
            if (binaryOpNode.get("op").equals("<")) valid = false;
        }

        if ((countBoolConsts > 0 && countIntConsts > 0)) {
            if (binaryOpNode.get("op").equals("<")) valid = false;
            else if (binaryOpNode.get("op").equals("&&")) valid = false;
        }

        /*
        // Se a operação for do tipo <, no caso de ter uma VarRefExpr e um IntegerLiteral, ver se VarRefExpr é do tipo int
        if (binaryOpNode.get("op").equals("<")) {
            if (varRefExpressions.size() == 1 && integerLiterals.size() == 1) {
                if (valid) {
                    // Como só é uma posso fazer get
                    var varRefExpr = varRefExpressions.get(0);
                    // ver se é variável local e do tipo int
                    for (var locarVar : symbolTable.getLocalVariables(currentMethod)) {
                        if (locarVar.getName().equals(varRefExpr.get("name"))) {
                            if (!locarVar.getType().getName().equals("int")) {
                                valid = false;
                                break;
                            }
                        }
                    }
                    // ver se é parametro da função atual e do tipo int
                    for (var param : symbolTable.getParameters(currentMethod)) {
                        if (param.getName().equals(varRefExpr.get("name"))) {
                            if (!param.getType().getName().equals("int")) {
                                valid = false;
                                break;
                            }
                        }
                    }
                }
            }
        }
        */


        if (!valid) {
            // Create error report
            var message = String.format("Invalid binary operation: '%s'");
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
            for (var localVar : symbolTable.getLocalVariables(currentMethod)) {
                if (localVar.getName().equals(variable.get("name"))) {
                    // varExpr só podem ter elementos inteiros, não booleanos
                    if (!localVar.getType().getName().equals("int")) {
                        valid = false;
                        break;
                    }
                }
            }
            // verificar se é parametro da função atual
            for (var param : symbolTable.getParameters(currentMethod)) {
                if (param.getName().equals(variable.get("name"))) {
                    // varExpr só podem ter elementos inteiros, não booleanos
                    if (!param.getType().getName().equals("int")) {
                        valid = false;
                        break;
                    }
                }
            }
        }

        if (!valid) {
            // Create error report
            var message = String.format("Invalid binary expression (Not type int): '%s'");
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
            var message = String.format("Invalid new array construction: '%s'");
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
            if (negatedVar.get("name").equals("true") || negatedVar.get("name").equals("false")) {
                isConstTrueOrFalse = true;
            }
            // se é constante true ou false, já é validado. Se não for...
            if (!isConstTrueOrFalse) {
                // verificar se é variável local
                if (symbolTable.getLocalVariables(currentMethod) != null) {
                    for (var localVar : symbolTable.getLocalVariables(currentMethod)) {
                        if (localVar.getName().equals(negatedVar.get("name"))) {
                            if (!localVar.getType().getName().equals("boolean")) {
                                valid = false;
                                break;
                            }
                        }
                    }
                }

                // verificar se é parametro
                if (symbolTable.getParameters(currentMethod) != null) {
                    for (var param : symbolTable.getParameters(currentMethod)) {
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

        if (!valid) {
            // Create error report
            var message = String.format("Invalid negation: '%s'");
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
        var varUsedOnNode = lengthNode.getChildren().get(0);
        // Verificar se length é chamada em variáveis locais
        for (var localVar : symbolTable.getLocalVariables(currentMethod)) {
            if (localVar.getName().equals(varUsedOnNode.get("name"))) {
                if (!localVar.getType().isArray()) {
                    valid = false;
                }
            }
        }

        // Verificar se length é chamada em parametros da função atual
        for (var param : symbolTable.getParameters(currentMethod)) {
            if (param.getName().equals(varUsedOnNode.get("name"))) {
                if (!param.getType().isArray()) {
                    valid = false;
                }
            }
        }


        if (!valid) {
            // Create error report
            var message = String.format("Invalid length function call: '%s'");
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
        // Verificar se é uma constante
        if (!indexNode.getKind().equals("IntegerLiteral")) {
            // Verificar se é uma variável local
            for (var localVar : symbolTable.getLocalVariables(currentMethod)) {
                if (localVar.getName().equals(indexNode.get("name"))) {
                    if (!localVar.getType().getName().equals("int") || localVar.getType().isArray()) {
                        valid = false;
                    }
                }
            }
            // Verificar se é um parametro de função atual
            for (var param : symbolTable.getParameters(currentMethod)) {
                if (param.getName().equals(indexNode.get("name"))) {
                    if (!param.getType().getName().equals("int") || param.getType().isArray()) {
                        valid = false;
                    }
                }
            }
        }

        if (!valid) {
            // Create error report
            var message = String.format("Invalid array access: '%s'");
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

    // Se estiver a causar problemas apagar
    private Void visitExpression(JmmNode expressionNode, SymbolTable symbolTable) {
        boolean valid = true;

        // Se faz uma chamada a uma função
        if (expressionNode.getChildren().get(0).getKind().equals("FunctionCall")) {
            var methodNameCalled = expressionNode.getChildren().get(0).get("methodName");
            String localVarTypeThatCalledFunction = "";
            boolean methodFound =  false;
            boolean localVarFound =  false;
            boolean paramFound =  false;
            boolean cameFromImport = false;

            // Verificar se a variável que chama o método existe nas variáveis locais
            var varThatCalledMethod = expressionNode.getChildren().get(0).getChildren("VarRefExpr").get(0);
            for (var localVar : symbolTable.getLocalVariables(currentMethod)) {
                if (localVar.getName().equals(varThatCalledMethod.get("name"))) {
                    localVarFound = true;
                    localVarTypeThatCalledFunction = localVar.getType().getName();
                    break;
                }
            }
            if (localVarFound) valid = false;

            // Verificar se a variável que chama o método existe nos parâmetros
            for (var param : symbolTable.getParameters(currentMethod)) {
                if (param.getName().equals(varThatCalledMethod.get("name"))) {
                    paramFound = true;
                    break;
                }
            }
            if (paramFound) valid = false;

            for (var importName : symbolTable.getImports()) {
                // Verificar se a variável que chama o método tem um tipo importado
                if (importName.equals(localVarTypeThatCalledFunction)) {
                    cameFromImport = true;
                    valid = true;
                    break;
                }
                // Verificar se a variável que chama o método tem o tipo da classe que extende uma superclasse
                else if (localVarTypeThatCalledFunction.equals(symbolTable.getClassName())) {
                    if (importName.equals(symbolTable.getSuper())) {
                        cameFromImport = true;
                        valid = true;
                        break;
                    }
                }
            }

            // se a variável local que chamou a função possui o tipo do import, assume-se que a função chamada já existe
            if (!cameFromImport) {
                // Verificar se o método chamado existe
                for (var method : symbolTable.getMethods()) {
                    if (methodNameCalled.equals(method)) {
                        methodFound = true;
                        break;
                    }
                }
                if (!methodFound) valid = false;
            }
        }

        if (!valid) {
            // Create error report
            var message = String.format("Invalid expression: '%s'");
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

        // verificar se classe é importada, se sim, está certo
        // assign do tipo a = new A()
        var elementsAssignmentNode = newClassNode.getParent().getChildren();
        var importNames = symbolTable.getImports();
        var foundImportName = true;
        if (elementsAssignmentNode.get(1).getKind().equals("NewClass")) {
            // se conter nome de qualquer import, assume-se como aceite. Ex: A a; B b; a = new B();
            for (var importName : importNames) {
                if (importName.equals(elementsAssignmentNode.get(1).get("className"))) {
                    break;
                }
                if (!foundImportName) {
                    valid = false;
                }
            }
        }


        if (!valid) {
            // Create error report
            var message = String.format("Invalid New Class: '%s'");
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
        var varStoring = arrayInitNode.getParent().getChildren("VarRefExpr").get(0);
        // Se for uma variável local

        for (var localVar : symbolTable.getLocalVariables(currentMethod)) {
            // se a variável que está a guardar for array é aceite
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
                // Isto é válido?
                if (localVar.getType().getName().equals("boolean")) {
                    for (var valueGiven : valuesGiven) {
                        if (!valueGiven.get("name").equals("true") && !valueGiven.get("name").equals("false")) {
                            valid = false;
                            break;
                        }
                        type = "boolean";
                    }
                }
            }
        }

        if (!valid) {
            // Create error report
            var message = String.format("Invalid new array creation: '%s'");
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
            for (var localVar : symbolTable.getLocalVariables(currentMethod)) {
                if (localVar.getName().equals(operatorUsed.get("name"))) {
                    // se não for do tipo booleano dá erro
                    if (!localVar.getType().getName().equals("boolean")) {
                        valid = false;
                        break;
                    }
                }
            }
        }

        if (!valid) {
            // Create error report
            var message = String.format("Invalid while condition: '%s'");
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
        boolean diffOperationInsideIf = false;
        boolean diffTypesInsideIf = false;
        int binaryExprCounter = 0;
        int binaryOpCounter = 0;

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
                }
                else if (operation.getKind().equals("BinaryOp")) {
                    binaryOpCounter++;
                }
            }
        }

        if (!valid) {
            // Create error report
            var message = String.format("Invalid if condition: '%s'");
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

    private Void visitFunctionCall(JmmNode functionCallNode, SymbolTable symbolTable) {
        boolean valid = true;
        var methods = symbolTable.getMethods();

        // do tipo a.bar();
        // percorrer por todos os métodos e verificar se numa FunctionCall o método existe
        for (var methodName : methods) {
            if (methodName.equals(functionCallNode.get("methodName"))) {
                valid = true;
                break;
            }
            valid = false;
        }

        if (!valid) {
            // Create error report
            var message = String.format("Call to non existent method: '%s'");
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(functionCallNode),
                    NodeUtils.getColumn(functionCallNode),
                    message,
                    null)
            );
        } else {
            functionCallNode.put("type", functionCallNode.get("value"));
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
        var localsVar = symbolTable.getLocalVariables(currentMethod);
        var importNames = symbolTable.getImports();

        boolean valid = true;

        // Lidar com arrays
        for (var localVar : localsVar) {
            // assignElement.get(0) é sempre igual à variável na qual guardamos valores
            if (localVar.getName().equals(assignElements.get(0).get("name"))) {
                // Se for do tipo array só pode dar assign a elementos do tipo array
                if (localVar.getType().isArray()) {
                    // Testar para quando o assign é feito com uma chamada a uma função que retorna um array
                    if (assignElements.get(1).getKind().equals("FunctionCall")) {
                        var functionCallReturn = symbolTable.getReturnType(assignElements.get(1).get("methodName"));
                        var retType = functionCallReturn.getName();
                        var isRetArray = functionCallReturn.isArray();
                        // como estamos a verificar se uma variável array recebe um array na chamada a uma função, se no retorno não receber array dá erro
                        if (!isRetArray) {
                            valid = false;
                            break;
                        }
                        // se os tipos forem diferentes, dá erro
                        if (!retType.equals(localVar.getType().getName())) {
                            valid = false;
                            break;
                        }
                    }
                    // Testar para quando o assign é feito com a uma array
                    else if (!assignElements.get(1).getKind().equals("ArrayInit") && !assignElements.get(1).getKind().equals("NewArray")) {
                        valid = false;
                        break;
                    }
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
                        if (assignElements.get(1).getChildren().get(0).get("name").equals(assignElements.get(0).get("name"))) {
                            valid = false;
                            break;
                        }
                    }
                }
            }
        }

        // Para variáveis que dão assign a inteiros
        if (assignElements.get(1).getKind().equals("IntegerLiteral")) {
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
        boolean valid = true;

        for (JmmNode returnElement : returnElements) {
            if (returnElement.hasAttribute("name")) {
                List<String> elementInfo = Arrays.asList(returnElement.getKind(), returnElement.get("name"));
                returnTypes.add(elementInfo);
            } else if (returnElement.hasAttribute("methodName")) {
                List<String> elementInfo = Arrays.asList(returnElement.getKind(), returnElement.get("methodName"));
                returnTypes.add(elementInfo);
            }
        }

        boolean found = false;
        // Verificar se as variáveis de retorno existem
        for (var returnElement : returnElements) {
            found = false;
            // se for variável
            if (returnElement.getKind().equals("VarRefExpr")) {
                if (localVariables != null) {
                    // procura nas variáveis locais se existe
                    for (var localVar : localVariables) {
                        if (localVar.getName().equals(returnElement.get("name"))) {
                            found = true;
                            break;
                        }
                    }
                }

                // se já encontrou, verifica se a próxima variável no return, se existir, é declarada
                // se a função não tiver parametros, não vale a pena verificá-los
                if (found || parameters == null) continue;
                // procura nos parametros da função atual se a variável existe
                for (var param : parameters) {
                    if (param.getName().equals(returnElement.get("name"))) {
                        found = true;
                        break;
                    }
                }
                if (found) break;
                // se não existir, dá erro
                valid = false;
            }
        }

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

        // se o return for de uma conta aritmética
        if (returnElements.get(0).getKind().equals("BinaryExpr")) {
            for (var returnElement : returnElements) {
                // só é preciso analisar variáveis int. Ignora os operadores.
                if (!returnElement.getKind().equals("BinaryExpr")) {
                    // caso a variável no return esteja nas variáveis locais
                    for (var localVar : localVariables) {
                        // verifica se é variável local da função
                        if (localVar.getName().equals(returnElement.get("name"))) {
                            // se o valor for diferente de int ou se a operação aritmética for realizar com um array, é inválido
                            if (!localVar.getType().getName().equals("int") || localVar.getType().isArray()) valid = false;
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

       // Analisar se uma variável ao chamar uma função recebe o tipo de retorno correto
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
                                    var variableThatCalledFunctionKind = functionCalled.b.getChildren().get(0).getKind();
                                    // se a variável que chamou a função é um inteiro, o return de um vargars tem que ser um array access
                                    if (variableThatCalledFunctionKind.equals("Integer")) {
                                        // tipo da variável que guarda o valor de return do método a analisar agora
                                        var variableThatCalledFunctionType = functionCalled.b.getChildren().get(0).get("value");

                                        // se os tipos de retorno forem diferentes ou se não for um array access, dá erro
                                        if (!returnType.getName().equals(variableThatCalledFunctionType)) valid = false;
                                        if (!child.getChildren().get(0).getKind().equals("ArrayAccess")) valid = false;
                                    }
                                    // se a variável que chamou a função é um array, o return de um vargars pode o ser parâmetro do varargs
                                    else if (variableThatCalledFunctionKind.equals("Array")) {
                                        boolean found = false;
                                        for (var returnElement : child.getChildren()) {
                                            // se o retorno for o parametro vargars (parametro é automaticamento array)
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
                                        if (!found) valid = false;
                                    }
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
            if (varDecl.getChildren().get(0).getKind().equals("Array")) {
                varDecl.put("type", varDecl.getChildren().get(0).getChildren().get(0).get("value"));
            }
            else {
                varDecl.put("type", varDecl.getChildren().get(0).get("value"));
            }
            return null;
        }

        // Var is a parameter, return
        if (table.getParameters(currentMethod).stream()
                .anyMatch(param -> param.getName().equals(varRefName))) {
            if (varDecl.getChildren().get(0).getKind().equals("Array")) {
                varDecl.put("type", varDecl.getChildren().get(0).getChildren().get(0).get("value"));
            }
            else {
                varDecl.put("type", varDecl.getChildren().get(0).get("value"));
            }
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
