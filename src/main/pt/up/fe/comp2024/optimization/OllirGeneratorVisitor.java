package pt.up.fe.comp2024.optimization;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.AJmmVisitor;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2024.ast.NodeUtils;
import pt.up.fe.comp2024.ast.TypeUtils;

import java.util.Objects;

import static pt.up.fe.comp2024.ast.Kind.*;

/**
 * Generates OLLIR code from JmmNodes that are not expressions.
 */
public class OllirGeneratorVisitor extends AJmmVisitor<Void, String> {

    private static final String SPACE = " ";
    private static final String ASSIGN = " :=";
    private final String END_STMT = ";\n";
    private final String NL = "\n";
    private final String L_BRACKET = " {\n";
    private final String R_BRACKET = "}\n";


    private final SymbolTable table;

    private final OllirExprGeneratorVisitor exprVisitor;

    public OllirGeneratorVisitor(SymbolTable table) {
        this.table = table;
        exprVisitor = new OllirExprGeneratorVisitor(table);
    }

    private boolean checkIfImport(String name) {
        for (var importID : table.getImports()) {
            if (importID.equals(name)) {
                return true;
            }
        }
        return false;
    }


    @Override
    protected void buildVisitor() {

        addVisit(PROGRAM, this::visitProgram);
        addVisit(CLASS_DECL, this::visitClass);
        addVisit(METHOD_DECL, this::visitMethodDecl);
        addVisit(PARAM, this::visitParam);
        addVisit(RETURN_STMT, this::visitReturn);
        addVisit(IMPORT_STATMENT, this::visitImport);
        addVisit(VAR_DECL, this::visitVarDecl);
        addVisit(ASSIGN_STMT, this::visitAssignStmt);
        addVisit(BINARY_EXPR, this::visitBinaryExpr);
        addVisit("FunctionCall", this::visitExpression);
        addVisit("NewClass", this::visitNewClass);
        addVisit("BinaryOp", this::visitBinaryOp);
        addVisit("IfCondition", this::visitIfCondition);

        setDefaultVisit(this::defaultVisit);
    }

    private String visitAssignStmt(JmmNode node, Void unused) {

        StringBuilder code = new StringBuilder();


        if (node.getChild(1).getKind().equals("BinaryExpr")){
            var str = visit(node.getChild(1));
            var type = OptUtils.toOllirType(node.getChild(0));
            if (str == ""){
                var child = node.getChild(1);
                code.append(node.getChild(0).get("name") + type);
                code.append(ASSIGN + type + SPACE);
                if (child.getChild(0).hasAttribute("name")){
                    code.append(child.getChild(0).get("name") + type + SPACE);
                }
                else{
                    code.append(child.getChild(0).get("value") + type + SPACE);
                }
                code.append(child.get("op") + type + SPACE);
                if (child.getChild(1).hasAttribute("name")){
                    code.append(child.getChild(1).get("name") + type);
                }
                else{
                    code.append(child.getChild(1).get("value") + type);
                }
                return code.toString();
            }
            code.append(str);
            code.append(node.getChild(0).get("name") + type);
            code.append(ASSIGN + type + SPACE);
            code.append(OptUtils.getCurrTemp() + type);
            return code.toString();
        }

        else if (node.getChild(1).getKind().equals("FunctionCall")){
            var type = OptUtils.toOllirType(node);
            var funcCallCode = visit(node.getChild(1));
            code.append(funcCallCode);
            code.append(node.getChild(0).get("name") + type + ASSIGN + type + SPACE + OptUtils.getCurrTemp() + type);
            return code.toString();

        }

        else if (node.getChild(1).getKind().equals("NewClass")){
            var type = OptUtils.toOllirType(node);
            var newClassCode = visit(node.getChild(1));
            code.append(newClassCode);
            code.append(node.getChild(0).get("name") + type);
            code.append(ASSIGN + type + SPACE);
            code.append(OptUtils.getCurrTemp() + type);
            return code.toString();

        }

        else if (node.getChild(1).getKind().equals("BinaryOp")){
            var currTemp = OptUtils.getNextTemp();
            var type = OptUtils.toOllirType(node);
            var binaryOpCode = visit(node.getChild(1));
            code.append(binaryOpCode);
            code.append(node.getChild(0).get("name") + type);
            code.append(ASSIGN + type + SPACE);
            code.append(currTemp + type);
            return code.toString();
        }

        else if (node.getChild(1).getKind().equals("Negate")){
            var child = node.getChild(1);
            var type = OptUtils.toOllirType(child.getChild(0));
            code.append(OptUtils.getTemp() + type + ASSIGN + type + SPACE);
            code.append(child.get("value") + type + SPACE);

            if (child.getChild(0).hasAttribute("value")){
                if (child.getChild(0).get("value").equals("true")){
                    code.append("1");
                }
                else if (child.getChild(0).get("value").equals("false")){
                    code.append("0");
                }
            }

            code.append(type + END_STMT);
            code.append(node.getChild(0).get("name") + type);
            code.append(ASSIGN + type + SPACE);
            code.append(OptUtils.getCurrTemp() + type);
            return code.toString();
        }

        else if (node.getChild(1).getKind().equals("NewArray")){
            var type = OptUtils.toOllirType(node.getChild(0));
            code.append(OptUtils.getTemp() + type + ASSIGN + type + SPACE);
            code.append(node.getChild(1).getChild(0).get("value") + type + END_STMT);

            code.append(node.getChild(0).get("name") + ".array" + type + ASSIGN + ".array" + type + SPACE);
            code.append("new(array, " + OptUtils.getCurrTemp() + type + ").array" + type);
            return code.toString();
        }


        var retType = OptUtils.toOllirType(node.getJmmChild(0));

        if (node.getChild(1).hasAttribute("name")){
            code.append(node.getChild(0).get("name"));
            code.append(retType + ASSIGN + retType + SPACE);
            code.append(node.getChild(1).get("name"));
        }
        else if (node.getChild(1).hasAttribute("value")){
            code.append(node.getChild(0).get("name"));
            code.append(retType + ASSIGN + retType + SPACE);
            code.append(node.getChild(1).get("value"));
        }

        code.append(retType);
        return code.toString();
    }


    private String visitReturn(JmmNode node, Void unused) {
        String methodName = node.getAncestor(METHOD_DECL).map(method -> method.get("methodName")).orElseThrow();
        Type retType = table.getReturnType(methodName);

        StringBuilder code = new StringBuilder();

        var expr = OllirExprResult.EMPTY;

        if (node.getNumChildren() > 0) {
            expr = exprVisitor.visit(node.getJmmChild(0));
        }

        code.append(expr.getComputation());
        code.append("ret");
        if(retType.isArray()){
            code.append(".array");
        }
        code.append(OptUtils.toOllirType(retType));
        code.append(SPACE);

        if(retType.isArray()){
            code.append(node.getChild(0).get("name"));
            code.append(".array");
            code.append(OptUtils.toOllirType(retType));
        }

        else {
            code.append(expr.getCode());
        }
        if (node.getChild(0).getKind().equals("Bolean")){
            if (node.getChild(0).get("value").equals("true")){
                code.append("1" + OptUtils.toOllirType(retType));
            }
            else{
                code.append("0" + OptUtils.toOllirType(retType));
            }
        }

        code.append(END_STMT);

        return code.toString();
    }


    private String visitParam(JmmNode node, Void unused) {

        var typeCode = OptUtils.toOllirType(node.getJmmChild(0));
        var id = node.get("name");

        String code = id + typeCode;

        return code;
    }


    private String visitMethodDecl(JmmNode node, Void unused) {

        StringBuilder code = new StringBuilder(".method ");

        boolean isPublic = NodeUtils.getBooleanAttribute(node, "isPublic", "false");
        boolean isStatic = NodeUtils.getBooleanAttribute(node, "isStatic", "false");

        var name = node.get("methodName");
        if (isPublic || Objects.equals(name, "main")) {
            code.append("public ");
        }

        if (isStatic || Objects.equals(name, "main")) {
            code.append("static ");
        }

        // name
        code.append(name);


        if (Objects.equals(name, "main")){
            code.append("(args.array.String).V " + L_BRACKET);

            for (var child: node.getChildren()){
                if (child.getKind().equals("AssignStmt")) {
                    var result = visit(child);
                    code.append(result + END_STMT);
                }
                else if(child.getKind().equals("Expression")){
                    var funcCall = visit(child.getChild(0));

                    code.append(funcCall);
                }
                else if(child.getKind().equals("IfCondition")){
                    var ifConditionResult = visit(child);
                    code.append(ifConditionResult);
                }
            }
            code.append("ret.V ;" + NL + R_BRACKET + NL);
            return code.toString();
        }

        // param
        var paramCode = "";
        var methodChildren = node.getChildren();
        if (!methodChildren.isEmpty()) {
            code.append("(");
            boolean firstParam = true;
            for (JmmNode child : methodChildren) {
                if (child.getKind().equals("Param")) {
                    if (!firstParam) {
                        code.append(", ");
                    }
                    paramCode = visit(child);
                    code.append(paramCode);
                    firstParam = false;
                }
            }
            code.append(")");
        }

        // type
        var retType = OptUtils.toOllirType(node.getJmmChild(0));
        code.append(retType);
        code.append(L_BRACKET);

        for (var child: node.getChildren()){
            if (child.getKind().equals("AssignStmt")) {
                var result = visit(child);
                code.append(result + END_STMT);
            }
            else if(child.getKind().equals("Expression")){
                var funcCall = visit(child.getChild(0));
                code.append(funcCall);

            }
        }

        // rest of its children stmts
        int afterParam = methodChildren.size() - 1;
        var returner = methodChildren.get(afterParam);
        if (Objects.equals(returner.getChild(0).getKind(), "BinaryExpr")){
            var retCode = visit(returner.getChild(0));
            code.append(retCode);
        }
        else if (Objects.equals(returner.getChild(0).getKind(), "FunctionCall")){
            var type = OptUtils.toOllirType(node);
            code.append(OptUtils.getTemp() + type);
            code.append(ASSIGN + type + SPACE);
            var retCode = visit(returner.getChild(0));
            var len = type.length();
            String typeComp = retCode.substring(retCode.length() - len);
            if (!typeComp.equals(type + END_STMT)) {
                String newCode = retCode.substring(0, retCode.length() - 4);
                code.append(newCode + type + END_STMT);
            }
            else code.append(retCode);
        }
        //code.append("ret" + retType + SPACE);
        var str = visit(returner);
        code.append(str);


        code.append(R_BRACKET);
        code.append(NL);

        return code.toString();
    }


    private String visitClass(JmmNode node, Void unused) {

        StringBuilder code = new StringBuilder();

        code.append(table.getClassName());
        if (node.hasAttribute("extendedClass")){
            code.append(" extends " + node.get("extendedClass"));
        }

        code.append(L_BRACKET);

        code.append(NL);
        var needNl = true;

        for (var child : node.getChildren()) {
            var result = visit(child);

            if (METHOD_DECL.check(child) && needNl) {
                code.append(NL);
                needNl = false;
            }

            code.append(result);
        }

        code.append(buildConstructor());
        code.append(R_BRACKET);

        return code.toString();
    }

    private String buildConstructor() {

        return ".construct " + table.getClassName() + "().V {\n" +
                "invokespecial(this, \"<init>\").V;\n" +
                "}\n";
    }


    private String visitProgram(JmmNode node, Void unused) {

        StringBuilder code = new StringBuilder();

        node.getChildren().stream()
                .map(this::visit)
                .forEach(code::append);

        return code.toString();
    }


    private String visitImport(JmmNode node, Void unused) {

        StringBuilder code = new StringBuilder();

        code.append("import ");
        var first = 0;

        if (node.hasAttribute("importName")) {
            for (var value : node.getObjectAsList("importName")) {
                if (first == 0) {
                    code.append(value);
                    first++;
                    continue;
                }
                code.append("." + value);
            }
        }


        code.append(END_STMT);

        return code.toString();
    }


    private String visitVarDecl(JmmNode node, Void unused) {

        StringBuilder code = new StringBuilder();
        JmmNode pai = node.getJmmParent();
        JmmNode id = node.getJmmChild(0);

        if (pai.getKind().equals("ClassStmt")) {
            code.append(".field public ");
        }
        if (id.getKind().equals("Array")) {
            code.append(node.get("name"));
            code.append(".array");

            code.append(";\n");
        }
        else { // working for boolean
            code.append(".field public ");
            code.append(node.get("name"));
            var retType = OptUtils.toOllirType(node.getJmmChild(0));
            code.append(retType);
            code.append(";\n");
        }

        return code.toString();
    }


    private String visitBinaryExpr(JmmNode node, Void unused){
        StringBuilder code = new StringBuilder();
        String retType = OptUtils.toOllirOpType(node);
        String leftStr = "";
        String rightStr = "";

        JmmNode left = node.getChild(0);
        JmmNode right = node.getChild(1);

        if (left.getKind().equals("Parentesis")) leftStr = visit(left.getChild(0));
        else leftStr = visit(left);

        if (right.getKind().equals("Parentesis")) rightStr = visit(right.getChild(0));
        else rightStr = visit(right);


        if (Objects.equals(leftStr, "") && Objects.equals(rightStr, "")) {
            if (node.getParent().getKind().equals("AssignStmt")){
                return "";
            }
            code.append(OptUtils.getTemp() + retType);
            code.append(ASSIGN + retType + SPACE);

            if (node.getChild(0).hasAttribute("value")) {
                code.append(node.getChild(0).get("value") + retType + SPACE);
            }
            else if (node.getChild(0).hasAttribute("name")){
                code.append(node.getChild(0).get("name") + retType + SPACE);
            }

            code.append(node.get("op") + retType + SPACE);

            if (node.getChild(1).hasAttribute("value")) {
                code.append(node.getChild(1).get("value") + retType + END_STMT);
            }
            else if (node.getChild(1).hasAttribute("name")){
                code.append(node.getChild(1).get("name") + retType + END_STMT);
            }
        }

        else if (Objects.equals(leftStr, "")){
            var currTemp = OptUtils.getCurrTemp();
            code.append(leftStr + rightStr);
            code.append(OptUtils.getTemp() + retType);
            code.append(ASSIGN + retType + SPACE);


            if (node.getChild(0).hasAttribute("value")) {
                code.append(node.getChild(0).get("value") + retType + SPACE);
            }
            else if (node.getChild(0).hasAttribute("name")){
                code.append(node.getChild(0).get("name") + retType + SPACE);
            }

            code.append(node.get("op") + retType + SPACE);
            code.append(currTemp + retType + END_STMT);

        }

        else if (Objects.equals(rightStr, "")){
            var currTemp = OptUtils.getCurrTemp();
            code.append(leftStr + rightStr);
            code.append(OptUtils.getTemp() + retType);
            code.append(ASSIGN + retType + SPACE);
            code.append(currTemp + retType + SPACE);
            code.append(node.get("op") + retType + SPACE);

            if (node.getChild(1).hasAttribute("value")) {
                code.append(node.getChild(1).get("value") + retType + END_STMT);
            }
            else if (node.getChild(1).hasAttribute("name")){
                code.append(node.getChild(1).get("name") + retType + END_STMT);
            }
        }

        else{
            var currTemp = OptUtils.getCurrTemp();
            var prevTemp = OptUtils.getPrevTemp();
            code.append(leftStr + rightStr);
            code.append(OptUtils.getTemp() + retType);
            code.append(ASSIGN + retType + SPACE);
            code.append(prevTemp + retType + SPACE);
            code.append(node.get("op") + retType + SPACE);
            code.append(currTemp + retType + END_STMT);
        }

        return code.toString();
    }


    private String visitBinaryOp(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();
        String retType = OptUtils.toOllirOpType(node);
        String leftStr = "";
        String rightStr = "";

        JmmNode left = node.getChild(0);
        JmmNode right = node.getChild(1);

        if (node.get("op").equals("&&")) {

            if (left.getKind().equals("Parentesis")) leftStr = visit(left.getChild(0));

            if (right.getKind().equals("Parentesis")) rightStr = visit(right.getChild(0));

            if (Objects.equals(leftStr, "") && Objects.equals(rightStr, "")) {

                code.append("if(");

                if (node.getChild(0).get("value").equals("true")) {
                    code.append(1 + retType);
                } else if (node.getChild(0).get("value").equals("false")) {
                    code.append(0 + retType);
                }

                code.append(") goto ");
                code.append("true_0" + END_STMT); //MUDAR

                code.append(OptUtils.getTemp() + retType);
                code.append(ASSIGN + retType + SPACE);

                if (node.getChild(0).get("value").equals("true")) {
                    code.append(0 + retType + END_STMT);
                } else if (node.getChild(0).get("value").equals("false")) {
                    code.append(1 + retType + END_STMT);
                }

                code.append("goto " + "end_0" + END_STMT); //MUDAR
                code.append("true_0:" + NL); // MUDAR

            }

            var currTemp = OptUtils.getCurrTemp();

            String temp = "";
            if (!right.getKind().equals("Bolean")) {
                temp = visit(right);
            }

            code.append(temp);
            code.append(currTemp + retType);
            code.append(ASSIGN + retType + SPACE);

            if (temp.equals("")) {
                if (node.getChild(0).get("value").equals("true")) {
                    code.append(1 + retType);
                } else if (node.getChild(0).get("value").equals("false")) {
                    code.append(0 + retType);
                }
            } else {
                code.append(OptUtils.getCurrTemp() + retType + END_STMT);
            }
            code.append("end_0:" + NL);
        }

        else{
            code.append("if(");

            if (left.hasAttribute("name")){
                code.append(left.get("name") + ".i32 ");
            }
            else {
                code.append(left.get("value") + OptUtils.toOllirType(left) + SPACE);
            }
            code.append(node.get("op") + retType + SPACE);

            if (right.hasAttribute("name")){
                code.append(right.get("name") + ".i32 ");
            }
            else {
                code.append(right.get("value") + OptUtils.toOllirType(right) + ")" + SPACE);
            }
            code.append("goto true_0" + END_STMT);



            var temp = OptUtils.getTemp();
            code.append(temp + retType);
            code.append(ASSIGN + retType + SPACE);
            code.append("0" + retType + END_STMT);

            code.append("goto end_0" + END_STMT);
            code.append("true_0:" + NL);

            code.append(temp + retType);
            code.append(ASSIGN + retType + SPACE);
            code.append("1" + retType + END_STMT);

            code.append("end_0:" + NL);

        }

        return code.toString();
    }

    private String visitIfCondition(JmmNode node, Void unused){
        StringBuilder code = new StringBuilder();

        code.append("if(");

        if (node.getChild(0).hasAttribute("name")){
            code.append(node.getChild(0).get("name") + ".bool)");

        }
        code.append(SPACE + "goto if_0" + END_STMT);

        var ifResult = visit(node.getChild(1).getChild(0).getChild(0));
        code.append(ifResult);
        code.append("goto endif_0" + END_STMT);
        code.append("if_0:" + NL);
        var elseResult = visit(node.getChild(2).getChild(0).getChild(0));
        code.append(elseResult);
        code.append("endif_0:" + NL);

        return code.toString();
    }





    private String visitExpression(JmmNode node, Void unused){
        StringBuilder code = new StringBuilder();

        if (node.getParent().getKind().equals("AssignStmt")){
            var type = OptUtils.toOllirType(node.getParent());
            code.append(OptUtils.getTemp() + type);
            code.append(ASSIGN + type + SPACE);
        }
        else if (node.getParent().getKind().equals("BinaryExpr") || node.getParent().getKind().equals("BinaryOp")){
            var type = OptUtils.toOllirOpType(node.getParent());
            code.append(OptUtils.getTemp() + type);
            code.append(ASSIGN + type + SPACE);
        }

        boolean virtual = false;
        boolean hasTemp = false;
        if (node.getChild(1).getKind().equals("BinaryExpr") || node.getChild(1).getKind().equals("BinaryOp") || node.getChild(1).getKind().equals("NewClass")){
            var str = visit(node.getChild(1));
            code.append(str);
            hasTemp = true;
        }

        if (node.getChild(0).get("name").equals("this")){
            code.append("invokevirtual(");
            node.getChild(0).put("value", table.getClassName());
            virtual=true;

        }
        else{
            if (checkIfImport(node.getChild(0).get("name"))){
                code.append("invokestatic(");
            }
            else {
                code.append("invokevirtual(");
                virtual = true;
            }
        }


        code.append(node.getChild(0).get("name"));
        if (virtual){
            var type = OptUtils.toOllirType(node.getChild(0));
            code.append(type);
        }
        code.append(", " + "\"" + node.get("methodName") + "\"");
        if (node.getChildren().size() > 1){
            int first = 0;
            for (var child : node.getChildren()){
                if (first == 0){
                    first++;
                    continue;
                }

                String type;
                if (hasTemp){
                    code.append(", " + OptUtils.getCurrTemp());
                    type = OptUtils.toOllirOpType(child);
                }
                else if (child.hasAttribute("name")) {
                    code.append(", " + child.get("name"));
                    type = OptUtils.toOllirType(child);
                }
                else {
                    code.append(", " + child.get("value"));
                    type = OptUtils.toOllirType(child);
                }
                code.append(type);

            }
        }
        code.append(")");
        var typeFunc = ".V";
        if (node.getParent().hasAttribute("type")){
            typeFunc = OptUtils.toOllirType(node.getParent());
        }
        if (node.getParent().hasAttribute("op")){
            typeFunc = OptUtils.toOllirOpType(node.getParent());
        }
        code.append(typeFunc);

        code.append(END_STMT);

        return code.toString();
    }


    private String visitNewClass(JmmNode node, Void unused){
        StringBuilder code = new StringBuilder();

        var type = OptUtils.toOllirType(node.getParent());
        code.append(OptUtils.getTemp() + type);
        code.append(ASSIGN + type + SPACE);
        code.append("new(" + node.get("className") + ")" + type + END_STMT);
        code.append("invokespecial(" + OptUtils.getCurrTemp() + type + ", " + "\"\").V" + END_STMT);

        return code.toString();
    }




    /**
     * Default visitor. Visits every child node and return an empty string.
     *
     * @param node
     * @param unused
     * @return
     */
    private String defaultVisit(JmmNode node, Void unused) {

        for (var child : node.getChildren()) {
            visit(child);
        }

        return "";
    }
}
