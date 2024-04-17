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
    private static final String ASSIGN = ":=";
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


    @Override
    protected void buildVisitor() {

        addVisit(PROGRAM, this::visitProgram);
        addVisit(CLASS_DECL, this::visitClass);
        addVisit(METHOD_DECL, this::visitMethodDecl);
        addVisit(PARAM, this::visitParam);
        addVisit(RETURN_STMT, this::visitReturn);
        addVisit(ASSIGN_STMT, this::visitAssignStmt);
        addVisit(IMPORT_STATMENT, this::visitImport);
        addVisit(VAR_DECL, this::visitVarDecl);
        addVisit(ASSIGN_STMT, this::visitAssign);

        setDefaultVisit(this::defaultVisit);
    }

    private String visitAssignStmt(JmmNode node, Void unused) {

        var lhs = exprVisitor.visit(node.getJmmChild(0));
        var rhs = exprVisitor.visit(node.getJmmChild(1));

        StringBuilder code = new StringBuilder();

        // code to compute the children
        code.append(lhs.getComputation());
        code.append(rhs.getComputation());

        // code to compute self
        // statement has type of lhs
        Type thisType = TypeUtils.getExprType(node.getJmmChild(0), table);
        String typeString = OptUtils.toOllirType(thisType);


        code.append(lhs.getCode());
        code.append(SPACE);

        code.append(ASSIGN);
        code.append(typeString);
        code.append(SPACE);

        code.append(rhs.getCode());

        code.append(END_STMT);

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
        code.append(OptUtils.toOllirType(retType));
        code.append(SPACE);

        code.append(expr.getCode());

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
            code.append("(args.array.String).V " + L_BRACKET + "ret.V ;" + NL + R_BRACKET + NL);
            return code.toString();
        }

        // param
        //var paramCode = visit(node.getJmmChild(1));
        //code.append("(" + paramCode + ")");
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
                code.append(result + ";" + NL);
            }
            else if(child.getKind().equals("Expression")){
                code.append("invokestatic(");
                code.append(child.getChild(0).getChild(0).get("name") + ", ");
                code.append("\"" + child.getChild(0).get("methodName") + "\", ");
                code.append(child.getChild(0).getChild(1).get("name"));
                var type = OptUtils.toOllirOpType(child.getChild(0).getChild(1));
                code.append(type + ")");

                var typeFunc = OptUtils.toOllirOpType(child.getChild(0));
                code.append(typeFunc);

                code.append(";" + NL);

            }
        }

        // rest of its children stmts
        int afterParam = methodChildren.size() - 1;
        var returner = methodChildren.get(afterParam);
        if (Objects.equals(returner.getChild(0).getKind(), "BinaryExpr")){
            var type = OptUtils.toOllirOpType(returner.getChild(0));
            code.append("temp0" + type + " :=" + type);
            code.append(" " + returner.getChild(0).getChild(0).get("name") + type);
            code.append(returner.getChild(0).get("op") + type);
            code.append(" " + returner.getChild(0).getChild(1).get("name") + type);
            code.append(";" + NL);
        }
        code.append("ret" + retType + " ");
        if (returner.getChild(0).hasAttribute("name")) {
            code.append(returner.getChild(0).get("name") + retType + ";");
        }
        else if (returner.getChild(0).hasAttribute("value")){
            code.append(returner.getChild(0).get("value") + retType + ";");
        }else {
            code.append("tmp0" + retType + ";");
        }

        code.append(NL);
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
        code.append(node.get("ID"));
        code.append(";\n");

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


    private String visitAssign(JmmNode node, Void unused) {

        StringBuilder code = new StringBuilder();

        code.append(node.getChild(0).get("name"));
        var retType = OptUtils.toOllirType(node.getJmmChild(0));
        code.append(retType + " :=" + retType + " ");
        if (node.getChild(1).hasAttribute("name")) code.append(node.getChild(1).get("name"));
        else if (node.getChild(1).hasAttribute("value")) code.append(node.getChild(1).get("value"));
        code.append(retType);

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
