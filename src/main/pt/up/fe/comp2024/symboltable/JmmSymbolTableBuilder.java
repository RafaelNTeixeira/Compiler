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

public class JmmSymbolTableBuilder {

    public static JmmSymbolTable build(JmmNode root) {
        var classDecl = root.getJmmChild(0);
        var importDecl = root.getChildren("ImportStatment"); // Nodes relacionados com declarações de imports
        var classDeclarations = root.getChildren("ClassDecl"); // Nodes relacionados com declarações de classes

        List <String> importNames = buildImports(importDecl);

        List <String> superAndClassNames = buildClassAndSuper(classDeclarations);
        String className = superAndClassNames.get(0);
        String superClassName = superAndClassNames.get(1);

        var methods = buildMethods(classDecl);
        var returnTypes = buildReturnTypes(classDecl);
        var params = buildParams(classDecl);
        var locals = buildLocals(classDecl);

        return new JmmSymbolTable(className, superClassName, methods, returnTypes, params, locals, importNames);
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

    private static Map<String, Type> buildReturnTypes(JmmNode classDecl) {
        // TODO: Simple implementation that needs to be expanded

        Map<String, Type> map = new HashMap<>();

        classDecl.getChildren(METHOD_DECL).stream()
                .forEach(method -> map.put(method.get("name"), new Type(TypeUtils.getIntTypeName(), false)));

        return map;
    }

    private static Map<String, List<Symbol>> buildParams(JmmNode classDecl) {
        // TODO: Simple implementation that needs to be expanded

        Map<String, List<Symbol>> map = new HashMap<>();

        var intType = new Type(TypeUtils.getIntTypeName(), false); // do tipo 'int'

        classDecl.getChildren(METHOD_DECL).stream()
                .forEach(method -> map.put(method.get("name"), Arrays.asList(new Symbol(intType, method.getJmmChild(1).get("name")))));

        return map;
    }

    private static Map<String, List<Symbol>> buildLocals(JmmNode classDecl) {
        // TODO: Simple implementation that needs to be expanded

        Map<String, List<Symbol>> map = new HashMap<>();


        classDecl.getChildren(METHOD_DECL).stream()
                .forEach(method -> map.put(method.get("name"), getLocalsList(method)));

        return map;
    }

    private static List<String> buildMethods(JmmNode classDecl) {

        return classDecl.getChildren(METHOD_DECL).stream()
                .map(method -> method.get("name"))
                .toList();
    }


    private static List<Symbol> getLocalsList(JmmNode methodDecl) {
        // TODO: Simple implementation that needs to be expanded

        var intType = new Type(TypeUtils.getIntTypeName(), false);

        return methodDecl.getChildren(VAR_DECL).stream()
                .map(varDecl -> new Symbol(intType, varDecl.get("name")))
                .toList();
    }

}
