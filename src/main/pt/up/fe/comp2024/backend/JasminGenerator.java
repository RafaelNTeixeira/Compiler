package pt.up.fe.comp2024.backend;

import org.specs.comp.ollir.*;
import org.specs.comp.ollir.tree.TreeNode;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.specs.util.classmap.FunctionClassMap;
import pt.up.fe.specs.util.exceptions.NotImplementedException;
import pt.up.fe.specs.util.utilities.StringLines;

import javax.lang.model.type.NullType;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Generates Jasmin code from an OllirResult.
 * <p>
 * One JasminGenerator instance per OllirResult.
 */
public class JasminGenerator {

    private static final String NL = "\n";
    private static final String TAB = "   ";

    private final OllirResult ollirResult;

    List<Report> reports;

    String code;

    Method currentMethod;

    Field currentField;

    ClassUnit currentClassUnit;
    private final FunctionClassMap<TreeNode, String> generators;

    public JasminGenerator(OllirResult ollirResult) {
        this.ollirResult = ollirResult;

        reports = new ArrayList<>();
        code = null;
        currentMethod = null;

        this.generators = new FunctionClassMap<>();
        generators.put(ClassUnit.class, this::generateClassUnit);
        generators.put(Method.class, this::generateMethod);
        generators.put(Field.class, this::generateField);
        generators.put(AssignInstruction.class, this::generateAssign);
        generators.put(PutFieldInstruction.class, this::generatePutField);
        generators.put(GetFieldInstruction.class, this::generateGetField);
        generators.put(CallInstruction.class, this::generateCall);
        generators.put(SingleOpInstruction.class, this::generateSingleOp);
        generators.put(LiteralElement.class, this::generateLiteral);
        generators.put(Operand.class, this::generateOperand);
        generators.put(BinaryOpInstruction.class, this::generateBinaryOp);
        generators.put(ReturnInstruction.class, this::generateReturn);
    }

    public List<Report> getReports() {
        return reports;
    }

    public String build() {

        // This way, build is idempotent
        if (code == null) {
            code = generators.apply(ollirResult.getOllirClass());
        }

        return code;
    }


    private String generateClassUnit(ClassUnit classUnit) {

        var code = new StringBuilder();
        currentClassUnit = classUnit;

        // generate class name
        var className = ollirResult.getOllirClass().getClassName();
        code.append(".class ").append(className).append(NL).append(NL);

        if(ollirResult.getOllirClass().getSuperClass() != null) {
            var superName = ollirResult.getOllirClass().getSuperClass();
            code.append(".super ").append(superName).append(NL).append(NL);
        } else {
            code.append(".super java/lang/Object").append(NL).append(NL);
        }

        for (var field : ollirResult.getOllirClass().getFields()){
            code.append(generators.apply(field));
        }
        code.append(NL);

        // generate a single constructor method
        var initDefaultConstructor = """
                ;default constructor
                .method public <init>()V
                    aload_0
                    """;

        var endDefaultConstructor ="""
                    return
                .end method
                """;
        code.append(initDefaultConstructor);
        code.append(TAB).append(" ").append("invokespecial ");
        if (ollirResult.getOllirClass().getSuperClass() != null) {
            var superName = ollirResult.getOllirClass().getSuperClass();
            code.append(superName);
        } else {
            code.append("java/lang/Object");
        }
        code.append("/<init>()V").append(NL);
        code.append(endDefaultConstructor);

        // generate code for all other methods
        for (var method : ollirResult.getOllirClass().getMethods()) {

            // Ignore constructor, since there is always one constructor
            // that receives no arguments, and has been already added
            // previously
            if (method.isConstructMethod()) {
                continue;
            }

            code.append(generators.apply(method));
        }
        currentClassUnit = null;
        return code.toString();
    }

    private String generateField(Field field) {
        var code = new StringBuilder();
        currentField = field;

        var fieldName = field.getFieldName();
        String jasminType = getJasminType(field.getFieldType());

        var modifier = field.getFieldAccessModifier();
        String modifierName;
        modifierName = switch (modifier) {
            case PUBLIC -> "public";
            case PRIVATE -> "private";
            case PROTECTED -> "protected";
            case DEFAULT -> "";
        };

        code.append(".field ").append(modifierName).append(" ");

        if (field.isStaticField()) {
            code.append("static ");
        }

        if (field.isFinalField()) {
            code.append("final ");
        }

        currentField = null;
        code.append(fieldName).append(" ").append(jasminType);

        if (field.isInitialized()) {
            code.append(" = ").append(field.getInitialValue());
        }
        code.append(NL);
        return code.toString();
    }

    private static String getJasminType(Type type) {
        String jasminType;
        String typeName = type.getTypeOfElement().name();

        jasminType = switch (typeName) {
            case "INT32" -> "I";
            case "BOOLEAN" -> "Z";
            case "ARRAYREF" -> "[";
            case "OBJECTREF", "CLASS", "THIS" -> "L";
            case "STRING" -> "Ljava/lang/String;";
            case "VOID" -> "V";
            default -> throw new IllegalArgumentException("Unsupported type: " + typeName);
        };
        return jasminType;
    }


    private String generateMethod(Method method) {

        // set method
        currentMethod = method;

        var code = new StringBuilder();

        // calculate modifier
        var modifier = method.getMethodAccessModifier() != AccessModifier.DEFAULT ?
                method.getMethodAccessModifier().name().toLowerCase() + " " :
                "";

        var methodName = method.getMethodName();

        code.append("\n.method ").append(modifier);

        if (method.isStaticMethod()) code.append("static ");
        if (method.isFinalMethod()) code.append("final ");

        code.append(methodName).append("(");

        if (methodName.equals("main")) {
            code.append("[Ljava/lang/String;");
        } else {
            for(var element : method.getParams()){
                var eleType = getJasminType(element.getType());
                code.append(eleType);
            }
        }

        code.append(")");

        var returnType = getJasminType(method.getReturnType());
        code.append(returnType).append(NL);



        // Add limits
        code.append(TAB).append(".limit stack 99").append(NL);
        code.append(TAB).append(".limit locals 99").append(NL);

        for (var inst : method.getInstructions()) {
            var instCode = StringLines.getLines(generators.apply(inst)).stream()
                    .collect(Collectors.joining(NL + TAB, TAB, NL));

            code.append(instCode);
        }

        code.append(".end method\n");

        // unset method
        currentMethod = null;

        return code.toString();
    }

    private String generateAssign(AssignInstruction assign) {
        var code = new StringBuilder();

        // generate code for loading what's on the right
        code.append(generators.apply(assign.getRhs()));

        // store value in the stack in destination
        var lhs = assign.getDest();

        if (!(lhs instanceof Operand)) {
            throw new NotImplementedException(lhs.getClass());
        }

        var operand = (Operand) lhs;

        // get register
        var reg = currentMethod.getVarTable().get(operand.getName()).getVirtualReg();

        var storeTypeName = assign.getTypeOfAssign().getTypeOfElement().name();
        String storeType;
        storeType = switch (storeTypeName) {
            case "INT32", "BOOLEAN" -> "istore ";
            case "ARRAYREF", "STRING", "OBJECTREF", "CLASS", "THIS" -> "astore ";
            case "VOID" -> "store ";
            default -> throw new IllegalArgumentException("Unsupported type: " + storeTypeName);
        };

        code.append(storeType).append(reg).append(NL);

        return code.toString();
    }

    private String generatePutField(PutFieldInstruction putFieldInstruction) {
        var code = new StringBuilder();
        var type = getJasminType(putFieldInstruction.getValue().getType());
        var spec1 = this.currentClassUnit.getClassName();
        var spec2 = putFieldInstruction.getField().getName().toLowerCase();

        var object = putFieldInstruction.getObject();
        code.append(generators.apply(object));

        var value = putFieldInstruction.getValue();
        code.append(generators.apply(value));

        code.append("putfield ").append(spec1).append("/").append(spec2).append(" ").append(type).append(NL);
        return code.toString();
    }

    private String generateGetField(GetFieldInstruction getFieldInstruction) {
        var code = new StringBuilder();

        var type = getJasminType(getFieldInstruction.getFieldType());
        var spec1 = this.currentClassUnit.getClassName();
        var spec2 = getFieldInstruction.getField().getName().toLowerCase();

        var object = getFieldInstruction.getObject();
        code.append(generators.apply(object));

        code.append("getfield ").append(spec1).append("/").append(spec2).append(" ").append(type).append(NL);
        return code.toString();
    }


    private String generateCall(CallInstruction callInstruction){
        String code;
        // nestes todos ainda está tudo em código que precisa de ser melhorado
        code = switch (callInstruction.getInvocationType()) {
            case invokevirtual -> getVirtualCall(callInstruction);
            case invokeinterface -> getInterfaceCall(callInstruction);
            case invokespecial -> getSpecialCall(callInstruction);
            case invokestatic -> getStaticCall(callInstruction);
            case NEW -> getNewCall(callInstruction);
            case arraylength -> getLengthCall(callInstruction);
            case ldc -> getLdcCall(callInstruction);
        };

        return code;
    }

    private String getVirtualCall(CallInstruction callInstruction) {
        var code = new StringBuilder();
        var className = ollirResult.getOllirClass().getClassName();
        code.append(generators.apply(callInstruction.getArguments().get(0)));

        for (var staticElement : callInstruction.getOperands()) {
            code.append(generators.apply(staticElement));
        }

        code.append("invokevirtual ");
        for (var importClass : ollirResult.getOllirClass().getImports()) {
            if (importClass.endsWith(className)) {
                className.replaceAll("\\.", "/");
            }
        }
        code.append(className).append("/");

        // ver o que é este elemento
        var methodName = ((LiteralElement) callInstruction.getMethodName()).getLiteral().replace("\"", "");
        code.append(methodName);
        // Argumentos
        code.append("(");
        for (var element :callInstruction.getOperands()){
            code.append(getJasminType(element.getType()));
        }
        code.append(")");
        var retType = getJasminType(callInstruction.getReturnType());
        code.append(retType).append(NL);

        return code.toString();
    }

    private String getInterfaceCall(CallInstruction callInstruction) {
        var code = new StringBuilder();
        code.append("invokeinterface ");

        return code.toString();
    }

    private String getStaticCall(CallInstruction callInstruction) {
        var code = new StringBuilder();
        var className = ollirResult.getOllirClass().getClassName();
        for (var staticElement : callInstruction.getOperands()) {
            code.append(generators.apply(staticElement));
        }

        code.append("invokestatic ");

        //classes importadas
        for (var importClass : ollirResult.getOllirClass().getImports()) {
            if (importClass.endsWith(className)) {
                className.replaceAll("\\.", "/");
            }
        }
        code.append(className).append("/");
        // ver o que é este elemento
        var methodName = ((LiteralElement) callInstruction.getMethodName()).getLiteral().replace("\"", "");
        code.append(methodName);
        // Argumentos
        code.append("(");
        for (var agr :callInstruction.getArguments()){
            code.append(generators.apply(agr));
        }
        code.append(")");
        var retType = getJasminType(callInstruction.getReturnType());
        code.append(retType).append(NL);

        return code.toString();
    }

    private String getSpecialCall(CallInstruction callInstruction) {
        var code = new StringBuilder();

        code.append("invokespecial ");
        var className = ollirResult.getOllirClass().getClassName();
        if (callInstruction.getArguments().isEmpty()
                || callInstruction.getArguments().get(0).getType().getTypeOfElement().equals(ElementType.THIS)){
            code.append(className);
        } // else é ir buscar aos imports
        else {
            for (var importClass : ollirResult.getOllirClass().getImports()) {
                if (importClass.endsWith(className)) {
                    className.replaceAll("\\.", "/");
                }
            }
            code.append(className);
        }
        code.append("/<init>(");
        for (var agr :callInstruction.getArguments()){
            code.append(generators.apply(agr));
        }
        code.append(")");
        var retType = getJasminType(callInstruction.getReturnType());
        code.append(retType).append(NL);

        return code.toString();
    }

    private String getNewCall(CallInstruction callInstruction) {
        var code = new StringBuilder();
        for (var agr : callInstruction.getArguments()){
            code.append(generators.apply(agr));
        }
        if (callInstruction.getReturnType().getTypeOfElement().name().equals("OBJECTREF")) {
            var className = ollirResult.getOllirClass().getClassName();
            code.append("new ").append(className).append(NL);
            code.append("dup").append(NL);
        }

        return code.toString();
    }

    private String getLengthCall(CallInstruction callInstruction) {
        var code = new StringBuilder();
        code.append(generators.apply(callInstruction.getArguments().get(0)));
        code.append("arraylenght").append(NL);

        return code.toString();
    }

    private String getLdcCall(CallInstruction callInstruction) {
        var code = new StringBuilder();
        code.append(generators.apply(callInstruction.getArguments().get(0))).append(NL);

        return code.toString();
    }

    private String generateSingleOp(SingleOpInstruction singleOp) {
        return generators.apply(singleOp.getSingleOperand());
    }

    private String generateLiteral(LiteralElement literal) {
        return "ldc " + literal.getLiteral() + NL;
    }

    private String generateOperand(Operand operand) {
        // get register
        var reg = currentMethod.getVarTable().get(operand.getName()).getVirtualReg();

        switch (operand.getType().getTypeOfElement().name()) {
            case "INT32", "BOOLEAN" -> {
                return "iload " + reg + NL;
            }
            case "ARRAYREF", "OBJECTREF", "CLASS", "STRING" -> {
                return "aload " + reg + NL;
            }
            case "THIS" -> {
                return "aload_0" + NL;
            }
        }
        return "";
    }

    private String generateBinaryOp(BinaryOpInstruction binaryOp) {
        var code = new StringBuilder();

        // load values on the left and on the right
        code.append(generators.apply(binaryOp.getLeftOperand()));
        code.append(generators.apply(binaryOp.getRightOperand()));

        // apply operation
        var op = switch (binaryOp.getOperation().getOpType()) {
            case ADD -> "iadd";
            case MUL -> "imul";
            case DIV -> "idiv";
            case SUB -> "isub";
            default -> throw new NotImplementedException(binaryOp.getOperation().getOpType());
        };

        code.append(op).append(NL);

        return code.toString();
    }

    private String generateReturn(ReturnInstruction returnInst) {
        var code = new StringBuilder();
        if (returnInst.hasReturnValue()) code.append(generators.apply(returnInst.getOperand()));

        var returnTypeName = returnInst.getElementType().name();
        String returnType;

        returnType = switch (returnTypeName) {
            case "INT32", "BOOLEAN" -> "ireturn";
            case "ARRAYREF", "STRING", "OBJECTREF", "CLASS", "THIS" -> "areturn";
            case "VOID" -> "return";
            default -> throw new IllegalArgumentException("Unsupported type: " + returnTypeName);
        };

        code.append(returnType).append(NL);

        return code.toString();
    }

}
