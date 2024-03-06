package pt.up.fe.comp2024.symboltable;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2024.ast.Kind;
import pt.up.fe.comp2024.ast.TypeUtils;
import pt.up.fe.specs.util.SpecsCheck;

import java.util.*;
import java.util.stream.Collectors;

import static pt.up.fe.comp2024.ast.Kind.*;
import static pt.up.fe.comp2024.ast.TypeUtils.*;

public class JmmSymbolTableBuilder {

    public static JmmSymbolTable build(JmmNode root) {
        var classDecl = root.getJmmChild(0);
        var importDecl = root.getChildren("ImportStatment"); // Nodes relacionados com declarações de imports
        var classDeclarations = root.getChildren("ClassDecl"); // Nodes relacionados com declarações de classes
        var fieldDeclarations = root.getDescendants("VarDecl"); // Retira tudo o que exista de VarDecl
        var methodDeclarations = root.getDescendants("MethodDecl"); // Retira tudo o que exista de declarações de métodos (funções)

        List <String> importNames = buildImports(importDecl);

        List <String> superAndClassNames = buildClassAndSuper(classDeclarations);
        String className = superAndClassNames.get(0);
        String superClassName = superAndClassNames.get(1);

        List <Symbol> fieldNames = buildFields(fieldDeclarations);

        var methods = buildMethods(classDecl);
        var returnTypes = buildReturnTypes(methodDeclarations);
        var params = buildParams(methodDeclarations);
        var locals = buildLocals(methodDeclarations);

        return new JmmSymbolTable(className, superClassName, fieldNames, methods, returnTypes, params, locals, importNames);
    }

    private static List <String> buildImports(List<JmmNode> importDecl) {
        List <String> importNames = new ArrayList<>();
        if (!importDecl.isEmpty()) {
            for (JmmNode importNode : importDecl) { // percorre todos os nodes relacionados com declarações de imports
                SpecsCheck.checkArgument(IMPORT_STATMENT.check(importNode), () -> "Expected an import declaration: " + importNode);
                for (String attribute : importNode.getAttributes()) { // percorre todos os atributos de um node
                    if (attribute.equals("importName")) { // encontra o atributo importName definido na gramática
                        importNames.add(attribute); // guarda-o
                    }
                }
            }
        }
        return importNames;
    }

    private static List <String> buildClassAndSuper(List<JmmNode> classDeclarations) {
        String className = "";
        String superClassName = "";
        if (!classDeclarations.isEmpty()) {
            for (JmmNode classNode : classDeclarations) { // percorre todos os nodes relacionados com declarações de classes
                SpecsCheck.checkArgument(CLASS_DECL.check(classNode), () -> "Expected a class declaration: " + classNode);
                for (String attribute : classNode.getAttributes()) { // percorre todos os atributos de um node
                    if (attribute.equals("className")) { // encontra o atributo className definido na gramática
                        className = classNode.get("className");
                    }
                    else if (attribute.equals("extendedClass")) { // encontra o atributo extendedClass definido na gramatica
                        superClassName = classNode.get("extendedClass");
                    }
                }
            }
        }
        List<String> result = new ArrayList<>();
        result.add(className);
        result.add(superClassName);
        return result;
    }

    private static List <Symbol> buildFields(List<JmmNode> fieldDeclarations) {
        List<Symbol> fields = new ArrayList<>();
        Boolean isArray = false;
        Symbol symbol = null;
        Type type = null;
        String fieldName = "";
        for (JmmNode fieldNode : fieldDeclarations) {
            if (fieldNode.getParent().getKind().equals("ClassDecl")) {
                for (JmmNode valueField : fieldNode.getChildren()) {
                    if (valueField.toString().equals("Array")) {
                        isArray = true;
                    }
                    fieldName = valueField.getParent().get("name");
                    switch (valueField.getKind()) {
                        case "Boolean":
                            type = new Type("boolean", isArray);
                            symbol = new Symbol(type, fieldName);
                            isArray = false;
                            break;
                        case "Integer":
                            type = new Type("int", isArray);
                            symbol = new Symbol(type, fieldName);
                            isArray = false;
                            break;
                        case "Float":
                            type = new Type("float", isArray);
                            symbol = new Symbol(type, fieldName);
                            isArray = false;
                            break;
                        case "Double":
                            type = new Type("double", isArray);
                            symbol = new Symbol(type, fieldName);
                            isArray = false;
                            break;
                        case "String":
                            type = new Type("String", isArray);
                            symbol = new Symbol(type, fieldName);
                            isArray = false;
                            break;
                        case "Void":
                            type = new Type("void", isArray);
                            symbol = new Symbol(type, fieldName);
                            isArray = false;
                            break;
                        case "Id":
                            type = new Type("id", isArray);
                            symbol = new Symbol(type, fieldName);
                            isArray = false;
                            break;
                        default:
                            type = new Type(fieldName, isArray);
                            symbol = new Symbol(type, fieldName);
                            isArray = false;
                    }
                    fields.add(symbol);
                }
            }
        }
        return fields;
    }

    private static Map<String, Type> buildReturnTypes(List<JmmNode> methodDeclarations) {
        Map<String, Type> returnTypes = new HashMap<>();
        Type type = null;
        Boolean isArray = false;

        for (JmmNode methodNode : methodDeclarations) {
            for (JmmNode node : methodNode.getChildren()) {
                if (node.getKind().equals("Array")) {
                    isArray = true;
                    var value = node.getChildren().get(0).get("value");
                    type = new Type(getTypeName(value), isArray);
                    returnTypes.put(node.getKind(), type);
                    isArray = false;

                }
                if (node.hasAttribute("value")) {
                    type = new Type(getTypeName(node.get("value")), isArray);
                    returnTypes.put(node.getKind(), type);
                }
            }
        }

        return returnTypes;
    }

    private static Map<String, List<Symbol>> buildParams(List<JmmNode> methodDeclarations) {
        Map<String, List<Symbol>> paramNames = new HashMap<>();


        Type type = null;
        Boolean isArray = false;

        for (JmmNode methodNode : methodDeclarations) {
            List<Symbol> symbols = new ArrayList<Symbol>();
            Symbol symbol = null;
            for (JmmNode node : methodNode.getChildren("Param")) {
                if (node.hasAttribute("methodName")) {
                    if (node.get("methodName").equals("main")) {
                        isArray = true;
                        JmmNode mainNode = node.getChildren().get(0);
                        var value = mainNode.getChildren().get(0).get("value");
                        type = new Type(getTypeName(value), isArray);
                        symbol = new Symbol(type, mainNode.getParent().get("name"));
                    }
                }
                if (node.getKind().equals("Array")) {
                    isArray = true;
                    var value = node.getChildren().get(0).get("value");
                    type = new Type(getTypeName(value), isArray);
                    symbol = new Symbol(type, node.getChildren("name").get(0).toString());
                }
                if (node.hasAttribute("name")) {
                    var value = node.getChildren().get(0).get("value");
                    type = new Type(getTypeName(value), isArray);
                    symbol = new Symbol(type, node.get("name"));
                }
                isArray = false;
                symbols.add(symbol);
            }
            paramNames.put(methodNode.get("methodName"), symbols);

        }

        return paramNames;
    }

    private static Map<String, List<Symbol>> buildLocals(List<JmmNode> methodDeclarations) {
        Map<String, List<Symbol>> localNames = new HashMap<>();
        List<Symbol> symbols = new ArrayList<Symbol>();
        Symbol symbol = null;
        Type type = null;
        Boolean isArray = false;

        for (JmmNode methods : methodDeclarations) {
            symbols = getLocalsList(methods);
            if (!symbols.isEmpty()) {
                localNames.put(methods.get("methodName"), symbols);
            }
        }
        return localNames;
    }

    private static List<String> buildMethods(JmmNode classDecl) {
        return classDecl.getChildren(METHOD_DECL).stream()
                .map(method -> method.get("methodName"))
                .toList();
    }


    private static List<Symbol> getLocalsList(JmmNode methods) {
        List<Symbol> symbols = new ArrayList<Symbol>();
        Boolean isArray = false;
        Type type = null;
        Symbol symbol = null;

        for (JmmNode locals : methods.getChildren("VarDecl")) {
            JmmNode localIsArray = locals.getChildren().get(0);
            if (localIsArray.toString().equals("Array")) {
                isArray = true;
                var value = localIsArray.getChildren().get(0).get("value");
                type = new Type(getTypeName(value), isArray);
                symbol = new Symbol(type, locals.get("name"));

            } else {
                var value = locals.getChildren().get(0).get("value");
                type = new Type(getTypeName(value), isArray);
                symbol = new Symbol(type, locals.get("name"));
            }
            isArray = false;
            symbols.add(symbol);
        }

        return symbols;
    }

}
