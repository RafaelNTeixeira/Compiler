package pt.up.fe.comp2024.symboltable;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp2024.ast.TypeUtils;
import pt.up.fe.specs.util.exceptions.NotImplementedException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class JmmSymbolTable implements SymbolTable {

    private final String className;
    private final String superClassName;
    private final List<Symbol> fields;
    private final List<String> methods;
    private final Map<String, Type> returnTypes;
    private final Map<String, List<Symbol>> params;
    private final Map<String, List<Symbol>> locals;
    private final List<String> imports;

    public JmmSymbolTable(String className,
                          String superClassName,
                          List<Symbol> fields,
                          List<String> methods,
                          Map<String, Type> returnTypes,
                          Map<String, List<Symbol>> params,
                          Map<String, List<Symbol>> locals,
                          List<String> imports) {
        this.className = className;
        this.superClassName = superClassName;
        this.fields = fields;
        this.methods = methods;
        this.returnTypes = returnTypes;
        this.params = params;
        this.locals = locals;
        this.imports = imports;
    }

    @Override
    public List<String> getImports() {
        if (imports == null) return new ArrayList<String>();
        return imports;
    }

    @Override
    public String getClassName() {
        if (className == null) return "";
        return className;
    }

    @Override
    public String getSuper() {
        if (superClassName == null) return "";
        return superClassName;
    }

    @Override
    public List<Symbol> getFields() {
        if (fields == null) return new ArrayList<Symbol>();
        return fields;
    }

    @Override
    public List<String> getMethods() {
        if (methods == null) return new ArrayList<String>();
        return methods;
    }

    @Override
    public Type getReturnType(String methodSignature) {
        return returnTypes.get(methodSignature);
    }

    @Override
    public List<Symbol> getParameters(String methodSignature) {
        List<Symbol> content = params.get(methodSignature);
        if (content == null) content = new ArrayList<Symbol>();
        return content;
    }

    @Override
    public List<Symbol> getLocalVariables(String methodSignature) {
        List<Symbol> content = locals.get(methodSignature);
        if (content == null) content = new ArrayList<Symbol>();
        return content;
    }

}
