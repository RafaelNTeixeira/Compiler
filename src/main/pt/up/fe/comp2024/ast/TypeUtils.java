package pt.up.fe.comp2024.ast;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;

public class TypeUtils {

    private static final String BOOLEAN_TYPE_NAME = "boolean";
    private static final String INT_TYPE_NAME = "int";
    private static final String STRING_TYPE_NAME = "String";
    private static final String DOUBLE_TYPE_NAME = "double";
    private static final String FLOAT_TYPE_NAME = "float";
    private static final String VOID_TYPE_NAME = "void";
    private static final String ID_TYPE_NAME = "id";

    public static String getTypeName(String type) {
        switch (type) {
            case "boolean":
                return BOOLEAN_TYPE_NAME;
            case "int":
                return INT_TYPE_NAME;
            case "String":
                return STRING_TYPE_NAME;
            case "double":
                return DOUBLE_TYPE_NAME;
            case "float":
                return FLOAT_TYPE_NAME;
            case "void":
                return VOID_TYPE_NAME;
            case "id":
                return ID_TYPE_NAME;
            default:
                return type;
        }
    }
    public static String getIntTypeName() {
        return INT_TYPE_NAME;
    }


    /**
     * Gets the {@link Type} of an arbitrary expression.
     *
     * @param expr
     * @param table
     * @return
     */
    public static Type getExprType(JmmNode expr, SymbolTable table) {
        // TODO: Simple implementation that needs to be expanded

        var kind = Kind.fromString(expr.getKind());

        Type type = switch (kind) {
            case BINARY_EXPR -> getBinExprType(expr);
            case VAR_REF_EXPR -> getVarExprType(expr, table);
            case INTEGER_LITERAL -> new Type(INT_TYPE_NAME, false);
            default -> throw new UnsupportedOperationException("Can't compute type for expression kind '" + kind + "'");
        };

        return type;
    }

    private static Type getBinExprType(JmmNode binaryExpr) {
        // TODO: Simple implementation that needs to be expanded

        String operator = binaryExpr.get("op");

        return switch (operator) {
            case "+", "*" -> new Type(INT_TYPE_NAME, false);
            default ->
                    throw new RuntimeException("Unknown operator '" + operator + "' of expression '" + binaryExpr + "'");
        };
    }


    private static Type getVarExprType(JmmNode varRefExpr, SymbolTable table) {
        // TODO: Simple implementation that needs to be expanded
        return new Type(INT_TYPE_NAME, false);
    }


    /**
     * @param sourceType
     * @param destinationType
     * @return true if sourceType can be assigned to destinationType
     */
    public static boolean areTypesAssignable(Type sourceType, Type destinationType) {
        // TODO: Simple implementation that needs to be expanded
        return sourceType.getName().equals(destinationType.getName());
    }
}
