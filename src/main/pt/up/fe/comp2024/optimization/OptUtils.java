package pt.up.fe.comp2024.optimization;

import org.specs.comp.ollir.Instruction;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2024.ast.NodeUtils;
import pt.up.fe.specs.util.exceptions.NotImplementedException;

import java.util.List;
import java.util.Optional;

import static pt.up.fe.comp2024.ast.Kind.TYPE;

public class OptUtils {
    private static int tempNumber = -1;

    public static String getTemp() {

        return getTemp("tmp");
    }

    public static String getTemp(String prefix) {

        return prefix + getNextTempNum();
    }

    public static int getNextTempNum() {

        tempNumber += 1;
        return tempNumber;
    }

    public static String getCurrTemp() {

        return getCurrTemp("tmp");
    }

    public static String getCurrTemp(String prefix) {

        return prefix + getCurrTempNum();
    }

    public static int getCurrTempNum() {

        return tempNumber;
    }

    public static String getPrevTemp() {

        return getPrevTemp("tmp");
    }

    public static String getPrevTemp(String prefix) {

        return prefix + getPrevTempNum();
    }

    public static int getPrevTempNum() {

        return tempNumber -1;
    }

    public static String getNextTemp() {

        return getNextTemp("tmp");
    }

    public static String getNextTemp(String prefix) {

        return prefix + getNextTempNum_();
    }

    public static int getNextTempNum_() {

        return tempNumber +1;
    }




    public static String toOllirType(JmmNode typeNode) {

        //TYPE.checkOrThrow(typeNode);
        String typeName;
        if (typeNode.hasAttribute("type")) typeName = typeNode.get("type");
        else if (typeNode.hasAttribute("name")){
            if (typeNode.get("name").equals("true") || typeNode.get("name").equals("false")) typeName = "bool";
            else typeName = typeNode.get("name");
        }
        else {
            typeName = typeNode.get("value");
        }

        return toOllirType(typeName);
    }

    public static String toOllirType(Type type) {
        return toOllirType(type.getName());
    }

    private static String toOllirType(String typeName) {

        String type = "." + switch (typeName) {
            case "int" -> "i32";
            case "String" -> "String";
            case "boolean" -> "bool";
            case "void" -> "V";
            case "array" -> "array.i32";
            case "int[]" -> "array.i32";
            default -> typeName;
        };

        return type;
    }

    public static String toOllirOpType(JmmNode jmmOperator) {

        if (jmmOperator.hasAttribute("op")) {
            return switch (jmmOperator.get("op")) {
                case "+", "-", "*", "/" -> ".i32";
                case "<", "<=", ">", ">=", "&&", "!" -> ".bool";
                default -> ".V";
            };
        }
        else {
            return ".V";
        }
    }

}
