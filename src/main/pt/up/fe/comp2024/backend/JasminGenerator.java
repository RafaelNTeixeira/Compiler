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
import java.util.Set;
import java.util.TreeSet;
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

    int lim_stack;
    int cur_stack;
    int call_arg;

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
        generators.put(UnaryOpInstruction.class, this::generateUnaryOp);
        generators.put(BinaryOpInstruction.class, this::generateBinaryOp);
        generators.put(GotoInstruction.class, this::generateGoto);
        generators.put(CondBranchInstruction.class, this::generateBranch);
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
        //var modifier = ollirResult.getOllirClass().getClassAccessModifier();
        code.append(".class ").append("public ").append(className).append(NL);

        if(ollirResult.getOllirClass().getSuperClass() != null) {
            var superName = ollirResult.getOllirClass().getSuperClass();

            if (superName.equals("this")) code.append(className);

            else {
                for (var importClass : ollirResult.getOllirClass().getImports()) {
                    if (importClass.endsWith(superName)) {
                        var test = importClass.replace(".", "/");
                        code.append(".super ").append(test).append(NL);
                    }
                }
            }

        } else {
            code.append(".super java/lang/Object").append(NL);
        }

        for (var field : ollirResult.getOllirClass().getFields()){
            code.append(generators.apply(field));
        }
        code.append(NL);

        // generate a single constructor method
        var initDefaultConstructor = """
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
            if (superName.equals("this")) code.append(className);
            else {
                for (var importClass : ollirResult.getOllirClass().getImports()) {
                    if (importClass.endsWith(superName)) {
                        var test = importClass.replace(".", "/");
                        code.append(test);
                    }
                }
            }
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

        private String getJasminType(Type type) {
        String jasminType;
        String typeName = type.getTypeOfElement().name();

        jasminType = switch (typeName) {
            case "INT32" -> "I";
            case "BOOLEAN" -> "Z";
            case "ARRAYREF" -> getArrayType(type);
            case "OBJECTREF" ->  getObjectType(type);
            case "CLASS", "THIS" -> "L";
            case "STRING" -> "Ljava/lang/String;";
            case "VOID" -> "V";
            default -> throw new IllegalArgumentException("Unsupported type: " + typeName);
        };
        return jasminType;
    }

    private String getObjectType(Type type) {
        var code = new StringBuilder();
        var name = ((ClassType) type).getName();
        //var className = ollirResult.getOllirClass().getClassName();
        code.append("L").append(name).append(";");

        return code.toString();
    }

    private String getArrayType(Type type){
        var code = new StringBuilder();
        var name = ((ArrayType) type).getElementType().toString();
        var jtype = switch (name){
            case "INT32" -> "I";
            case "BOOLEAN" -> "Z";
            case "STRING" -> "Ljava/lang/String;";
            case "VOID" -> "V";
            default -> throw new IllegalStateException("Unexpected value: " + name);
        };
        code.append("[").append(jtype);
        return code.toString();
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
        code.append(TAB).append(".limit stack 20").append(NL);
        cur_stack = 0;
        lim_stack = 0;
        //code.append(TAB).append(".limit stack ").append(lim_stack).append(NL);
        code.append(TAB).append(".limit locals ").append(getLocalNumber(method)).append(NL);

        var instCodeFull = new StringBuilder();

        for (var inst : method.getInstructions()) {
            var instCode = StringLines.getLines(generators.apply(inst)).stream()
                    .collect(Collectors.joining(NL + TAB, TAB, NL));
            instCodeFull.append(instCode);
            // fazer if para o pop
            if (inst.getInstType() == InstructionType.CALL
                    && ((CallInstruction) inst).getReturnType().getTypeOfElement() != ElementType.VOID) {
                instCodeFull.append(TAB).append("pop").append(NL);
                cur_stack--;
            }
        }

        code.append(instCodeFull);

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
        int reg;

        if (!(lhs instanceof Operand)) {
            var cla = lhs.getClass().getName();
            reg = currentMethod.getVarTable().get(cla).getVirtualReg();
            //throw new NotImplementedException(lhs.getClass());
        } else {
            var operand = (Operand) lhs;
            // get register
            reg = currentMethod.getVarTable().get(operand.getName()).getVirtualReg();
        }

        var storeTypeName = assign.getTypeOfAssign().getTypeOfElement().name();
        String storeType;
        if(assign.getDest() instanceof ArrayOperand){
            code.append(getAssignArray(assign, reg));
        } else {
            if (reg > 3) {
                storeType = switch (storeTypeName) {
                    case "INT32", "BOOLEAN" -> "istore ";
                    case "ARRAYREF", "STRING", "OBJECTREF", "THIS", "CLASS" -> "astore ";
                    case "VOID" -> "store ";
                    default -> throw new IllegalArgumentException("Unsupported type: " + storeTypeName);
                };
            } else {
                storeType = switch (storeTypeName) {
                    case "INT32", "BOOLEAN" -> "istore_";
                    case "ARRAYREF", "STRING", "OBJECTREF", "THIS", "CLASS" -> "astore_";
                    case "VOID" -> "store_";
                    default -> throw new IllegalArgumentException("Unsupported type: " + storeTypeName);
                };
            }

            int nStack;
            nStack = switch (storeTypeName) {
                case "INT32", "BOOLEAN" -> 3;
                case "ARRAYREF", "STRING", "OBJECTREF", "THIS", "CLASS", "VOID" -> 1;
                default -> throw new IllegalArgumentException("Unsupported type: " + storeTypeName);
            };
            cur_stack -= nStack;

            code.append(storeType).append(reg).append(NL);

        }
        return code.toString();
    }

    //var muito bem esta função
    private String getAssignArray(AssignInstruction assign, int reg) {
        var code = new StringBuilder();
        incrementStackNumber(1);

        if(reg > 3) code.append("aload ").append(reg);
        else code.append("aload_").append(reg);
        code.append(NL);

        code.append(generators.apply(( (ArrayOperand) assign.getDest()).getIndexOperands().get(0)));
        code.append(generators.apply(assign.getRhs()));
        code.append(generators.apply(assign.getDest()));
        return code.toString();
    }

    private String generatePutField(PutFieldInstruction putFieldInstruction) {
        cur_stack -= 2;
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
        call_arg = 0;
        String code;
        // nestes todos ainda está tudo em código que precisa de ser melhorado
        code = switch (callInstruction.getInvocationType()) {
            case invokevirtual -> getVirtualCall(callInstruction);
            case invokeinterface -> getInterfaceCall(callInstruction);
            case invokespecial -> getSpecialCall(callInstruction);
            case invokestatic -> getStaticCall(callInstruction);
            case NEW -> {
                if (callInstruction.getReturnType().getTypeOfElement() == ElementType.OBJECTREF) {
                    yield getNewCall(callInstruction);
                } else {
                    yield getNewArrayCall(callInstruction);
                }
            }
            case arraylength -> getLengthCall(callInstruction);
            case ldc -> getLdcCall(callInstruction);
        };

        cur_stack -= call_arg;
        return code;
    }

    private String getVirtualCall(CallInstruction callInstruction) {
        var code = new StringBuilder();
        var className = ollirResult.getOllirClass().getClassName();
        code.append(generators.apply(callInstruction.getOperands().get(0)));

        call_arg = 1;

        for (var argument : callInstruction.getArguments()) {
            code.append(generators.apply(argument));
            call_arg++;
        }

        code.append("invokevirtual ");

        // imports
        var first = (Operand) callInstruction.getOperands().get(0);
        var firstName = ((ClassType) first.getType()).getName();
        if (firstName.equals("this")){
            code.append(className);
        } else {
            for (var importClass : ollirResult.getOllirClass().getImports()) {
                if (importClass.endsWith(className)) {
                    firstName.replaceAll("\\.", "/");
                }
            }
            code.append(firstName).append("/");
        }

        // ver o que é este elemento
        var methodName = ((LiteralElement) callInstruction.getMethodName()).getLiteral().replace("\"", "");
        code.append(methodName);
        // Argumentos
        code.append("(");
        for (var element :callInstruction.getArguments()){
            code.append(getJasminType(element.getType()));
        }

        code.append(")");
        var retType = getJasminType(callInstruction.getReturnType());
        code.append(retType).append(NL);

        call_arg = retType.equals("V") ? call_arg : call_arg - 1;

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
        call_arg = 0;

        for (var argument : callInstruction.getArguments()) {
            call_arg++;
            code.append(generators.apply(argument));
        }

        code.append("invokestatic ");
        var first = (Operand) callInstruction.getOperands().get(0);
        var firstName = first.getName();

        //classes importadas

        if (firstName.equals("this")){
            code.append(className);
        }
        else {
            for (var importClass : ollirResult.getOllirClass().getImports()) {
                if (importClass.endsWith(firstName)) {
                    firstName.replaceAll("\\.", "/");
                }
            }
            code.append(firstName);
        }

        code.append("/");
        var methodName = ((LiteralElement) callInstruction.getMethodName()).getLiteral().replace("\"", "");
        code.append(methodName);
        // Argumentos
        code.append("(");
        for (var agr :callInstruction.getArguments()){
            code.append(getJasminType(agr.getType()));
        }
        code.append(")");
        var retType = getJasminType(callInstruction.getReturnType());
        code.append(retType).append(NL);

        call_arg = retType.equals("V") ? call_arg : call_arg - 1;

        return code.toString();
    }

    private String getSpecialCall(CallInstruction callInstruction) {
        var code = new StringBuilder();

        code.append(generators.apply(callInstruction.getOperands().get(0)));

        code.append("invokespecial ");
        var className = ollirResult.getOllirClass().getClassName();
        var element = callInstruction.getOperands().get(0).getType();

        if (element.getTypeOfElement() == ElementType.THIS) {
            code.append(ollirResult.getOllirClass().getSuperClass());
        } else {
            var first = (Operand) callInstruction.getOperands().get(0);
            var firstName = ((ClassType) first.getType()).getName();
            for (var importClass : ollirResult.getOllirClass().getImports()) {
                if (importClass.endsWith(className)) {
                    firstName.replaceAll("\\.", "/");
                }
            }
            code.append(firstName);
        }

        code.append("/<init>(");
        for (var agr :callInstruction.getArguments()){
            code.append(getJasminType(agr.getType()));
        }
        code.append(")");
        var retType = getJasminType(callInstruction.getReturnType());
        code.append(retType).append(NL);

        call_arg = retType.equals("V") ? call_arg : call_arg - 1;

        return code.toString();
    }

    private String getNewCall(CallInstruction callInstruction) {
        var code = new StringBuilder();
        call_arg = -1;
        for (var agr : callInstruction.getArguments()){
            call_arg++;
            code.append(generators.apply(agr));
        }

        var first = (Operand) callInstruction.getOperands().get(0);
        var firstName = ((ClassType) first.getType()).getName();
        var className = ollirResult.getOllirClass().getClassName();

        if (callInstruction.getReturnType().getTypeOfElement().name().equals("OBJECTREF")) {
            for (var importClass : ollirResult.getOllirClass().getImports()) {
                if (importClass.endsWith(className)) {
                    firstName.replaceAll("\\.", "/");
                }
            }
        }
        code.append("new ").append(firstName).append(NL);
        code.append("dup").append(NL);

        return code.toString();
    }

    private String getNewArrayCall(CallInstruction callInstruction){
        var code = new StringBuilder();
        call_arg = -1;

        for (var agr : callInstruction.getArguments()) {
            call_arg++;
            code.append(generators.apply(agr));
        }
        code.append("newarray int").append(NL);
        return code.toString();
    }

    private String getLengthCall(CallInstruction callInstruction) {
        var code = new StringBuilder();
        /*
        if(!callInstruction.getArguments().isEmpty()) {
            code.append(generators.apply(callInstruction.getArguments().get(0)));
        }*/
        code.append(generators.apply(callInstruction.getCaller()));
        code.append("arraylength").append(NL);

        return code.toString();
    }

    private String getLdcCall(CallInstruction callInstruction) {
        var code = new StringBuilder();
        code.append(generators.apply(callInstruction.getArguments().get(0))).append(NL);

        return code.toString();
    }

    private String generateGoto(GotoInstruction gotoInstruction) {
        var code = new StringBuilder();
        code.append("goto ").append(gotoInstruction.getLabel()).append(NL);
        return code.toString();
    }

    private String generateBranch(CondBranchInstruction condBranchInstruction) {
        var code = new StringBuilder();
        var condition = condBranchInstruction.getCondition().getInstType();
        String result = new String();

        switch (condition){
            case UNARYOPER -> {
                result = getUnaryoperBranch((UnaryOpInstruction) condBranchInstruction.getCondition());
            }
            case BINARYOPER -> {
                result = getBinaryoperBranch((BinaryOpInstruction) condBranchInstruction.getCondition());
            }
            default -> {
                code.append(generators.apply(condBranchInstruction.getCondition())).append(TAB);
                code.append("ifne ");
                cur_stack--;
            }

        }
        code.append(result);
        code.append(condBranchInstruction.getLabel()).append(NL);


        return code.toString();
    }

    private String getUnaryoperBranch(UnaryOpInstruction unaryOpInstruction){
        var code = new StringBuilder();
        if(unaryOpInstruction.getOperation().getOpType() == OperationType.NOTB){
            code.append(generators.apply(unaryOpInstruction.getOperand())).append(TAB);
            code.append("ifeq ");
            cur_stack--;
        }
        return code.toString();
    }

    private String getBinaryoperBranch(BinaryOpInstruction binaryOpInstruction){
        var code = new StringBuilder();
        Integer literalOp = null;
        switch (binaryOpInstruction.getOperation().getOpType()){
            case LTH -> {
                if(binaryOpInstruction.getLeftOperand() instanceof LiteralElement){
                    literalOp = Integer.parseInt(((LiteralElement) binaryOpInstruction.getLeftOperand()).getLiteral());
                    code.append(generators.apply(binaryOpInstruction.getRightOperand())).append(TAB);
                    code.append("ifgt ");
                    cur_stack--;
                }
                if(binaryOpInstruction.getRightOperand() instanceof LiteralElement){
                    literalOp = Integer.parseInt(((LiteralElement) binaryOpInstruction.getRightOperand()).getLiteral());
                    code.append(generators.apply(binaryOpInstruction.getLeftOperand())).append(TAB);
                    code.append("iflt ");
                    cur_stack--;
                }
                if(literalOp != null && literalOp == 0){
                    code.append(generators.apply(binaryOpInstruction.getLeftOperand()))
                            .append(generators.apply(binaryOpInstruction.getRightOperand())).append(TAB);
                    code.append("if_icmplt ");
                    cur_stack -= 2;
                }
            }

            case GTE -> {
                if(binaryOpInstruction.getLeftOperand() instanceof LiteralElement){
                    literalOp = Integer.parseInt(((LiteralElement) binaryOpInstruction.getLeftOperand()).getLiteral());
                    code.append(generators.apply(binaryOpInstruction.getRightOperand())).append(TAB);
                    code.append("ifge ");
                    cur_stack--;
                }

                if(binaryOpInstruction.getRightOperand() instanceof LiteralElement){
                    literalOp = Integer.parseInt(((LiteralElement) binaryOpInstruction.getRightOperand()).getLiteral());
                    code.append(generators.apply(binaryOpInstruction.getLeftOperand())).append(TAB);
                    code.append("ifle ");
                    cur_stack--;
                }
                if(literalOp != null && literalOp == 0){
                    code.append(generators.apply(binaryOpInstruction.getLeftOperand()))
                            .append(generators.apply(binaryOpInstruction.getRightOperand())).append(TAB);
                    code.append("if_icmpge ");
                    cur_stack -= 2;
                }
            }

            case ANDB -> {
                code.append(generators.apply(binaryOpInstruction));
                code.append("ifne ");
                cur_stack--;
            }

            default -> code.append("");
        }
        return code.toString();
    }

    private String generateSingleOp(SingleOpInstruction singleOp) {
        return generators.apply(singleOp.getSingleOperand());
    }

    private String generateLiteral(LiteralElement literal) {
        incrementStackNumber(1);
        var code = new StringBuilder();
        if (literal.getType().getTypeOfElement() != ElementType.INT32 && literal.getType().getTypeOfElement() != ElementType.BOOLEAN) {
            return "ldc " + literal.getLiteral() + NL;
        } else {
            var value = Integer.parseInt(literal.getLiteral());
            if (this.between(value, -1, 5)) code.append("iconst_");
            else if (this.between(value, -128, 127)) code.append("bipush ");
            else if (this.between(value, -32768, 32767)) code.append("sipush ");
            else code.append("ldc ");

            if (value == -1) code.append("m1");
            else code.append(value);

            code.append(NL);

        }
        return code.toString();
    }

    private String generateOperand(Operand operand) {
        incrementStackNumber(1);
        // get register
        var reg = currentMethod.getVarTable().get(operand.getName()).getVirtualReg();

        if (reg > 3){
            switch (operand.getType().getTypeOfElement().name()) {
                case "INT32", "BOOLEAN" -> {
                    return "iload " + reg + NL;
                }
                case "ARRAYREF", "OBJECTREF", "CLASS", "STRING" -> {
                    return "aload " + reg + NL;
                }
                case "THIS" -> {
                    return "aload_" + reg + NL;
                }
            }
        } else {
            switch (operand.getType().getTypeOfElement().name()) {
                case "INT32", "BOOLEAN" -> {
                    return "iload_" + reg + NL;
                }
                case "ARRAYREF", "OBJECTREF", "CLASS", "STRING", "THIS" -> {
                    return "aload_" + reg + NL;
                }
            }
        }

        return "";
    }

    private String getOperantionType(OperationType operationType){
        return switch (operationType){
            case ADD -> "iadd";
            case MUL -> "imul";
            case SUB -> "isub";
            case DIV -> "idiv";
            case ANDB -> "iand";
            case NOTB -> "ixor";
            case LTH -> "if_icmplt";
            case GTE -> "if_icmpte";
            default -> null;
        };
    }

    private String generateUnaryOp(UnaryOpInstruction unaryOpInstruction) {
        var code = new StringBuilder();
        code.append(generators.apply(unaryOpInstruction.getOperand()));
        if(unaryOpInstruction.getOperation().getOpType().equals(OperationType.NOTB)) {
            code.append("iconst_1").append(NL); //alterar
        }
        var opType = unaryOpInstruction.getOperation().getOpType();
        code.append(getOperantionType(opType)).append(NL);
        return code.toString();
    }

    private String generateBinaryOp(BinaryOpInstruction binaryOp) {
        cur_stack --;
        //Fazer aqui as alterações para a iinc
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
            case ANDB -> "iand";
            case NOTB -> "ifeq";
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

    private String getLocalNumber(Method method){
        var code = new StringBuilder();

        var number = method.getVarTable().values().size();

        if(!method.isStaticMethod()) number++;

        code.append(number);
        return code.toString();
    }

    private void incrementStackNumber(int n){
        cur_stack += n;
        lim_stack = Math.max(lim_stack, cur_stack);
    }

    private boolean between(int value, int lower, int upper) {
        return value <= upper && value >= lower;
    }

}
