grammar Javamm;

@header {
    package pt.up.fe.comp2024;
}

EQUALS : '=' ;
SEMI : ';' ;
LCURLY : '{' ;
RCURLY : '}' ;
LPAREN : '(' ;
RPAREN : ')' ;
LSQUARE : '[' ;
RSQUARE : ']' ;
MUL : '*' ;
DIV : '/' ;
ADD : '+' ;
SUB : '-' ;
NOT : '!' ;
AND : '&&' ;
OR : '||' ;
LESS : '<' ;
LE : '<=' ;
GREATER : '>' ;
GE : '>=' ;

CLASS : 'class' ;
EXTENDS : 'extends' ;
INT : 'int' ;
BOOLEAN : 'boolean' ;
STRING : 'String' ;
FLOAT : 'float' ;
DOUBLE : 'double' ;
VOID : 'void' ;
STATIC : 'static' ;
MAIN : 'main' ;
PUBLIC : 'public' ;
RETURN : 'return' ;
IMPORT : 'import' ;
IF : 'if' ;
ELSE : 'else' ;
ELSEIF : 'else if' ;
WHILE : 'while' ;
FOR : 'for' ;

INTEGER : ([0] | [1-9][0-9]*) ;
ID : [a-zA-Z_$][a-zA-Z$_0-9]* ;
BOOL : ('false' | 'true');

WS : [ \t\n\r\f]+ -> skip ;
LINE_COMMENT : '//' .*? [\n\r] -> skip ;
MULTI_LINE_COMMENT : '/*' .*? '*/' -> skip ;

program
    : stmt+ EOF
    | (importDeclaration)* classDecl EOF
    ;

importDeclaration
    : IMPORT value+=ID ('.' value+=ID)* SEMI #ImportStatment
    ;

classDecl
    : CLASS name=ID
        (EXTENDS name=ID)?
        LCURLY
        (varDecl)* (methodDecl)*
        RCURLY
    ;

varDecl
    : type name=ID SEMI
    ;

type
    : type LSQUARE RSQUARE #Array //
    | value= INT '...' #VarArgs //
    | value= BOOLEAN #Boolean //
    | value= INT #Integer //
    | value= STRING #String //
    | value= DOUBLE #Double //
    | value= FLOAT #Float //
    | value= VOID #Void //
    | value= ID #Var //
    ;

methodDecl locals[boolean isPublic=false]
    : (PUBLIC {$isPublic=true;})?
        type name=ID
        LPAREN param? (',' param)* RPAREN
        LCURLY varDecl* stmt* RCURLY
    ;

mainDecl
    : (PUBLIC)? STATIC VOID MAIN LPAREN STRING LSQUARE RSQUARE arg=ID RPAREN
        LCURLY varDecl* stmt* RCURLY
    ;

param
    : type name=ID
    ;

stmt
    : expr SEMI #Expression //
    | LCURLY stmt* RCURLY #Brackets //
    | IF LPAREN expr RPAREN stmt (ELSEIF LPAREN expr RPAREN stmt)* (ELSE stmt)? #IfCondition //
    | WHILE LPAREN expr RPAREN stmt #WhileLoop //
    | expr EQUALS expr SEMI #AssignStmt //
    | RETURN expr SEMI #ReturnStmt //
    | ID EQUALS expr SEMI #AssignVar //
    | ID EQUALS LSQUARE expr RSQUARE EQUALS expr SEMI #AssignArray //
    ;

expr
    : value= NOT expr #Negate //
    | LPAREN expr RPAREN #Parentesis
    | name=ID LPAREN (expr (',' expr)* )? RPAREN #FunctionCall //
    | expr '.' name=ID LPAREN (expr (',' expr)* )? RPAREN #FunctionCall //
    | 'new' INT LSQUARE expr RSQUARE #NewArray //
    | 'new' name=ID LPAREN (expr (',' expr)* )? RPAREN #NewClass // Pode ser feita assim?
    | expr LSQUARE expr RSQUARE #ArrayAccess //
    | LSQUARE ( expr ( ',' expr )* )? RSQUARE #ArrayInit //
    | expr '.' 'length' #Length //
    | expr op=(MUL | DIV) expr #BinaryExpr //
    | expr op=(ADD | SUB) expr #BinaryExpr //
    | expr op=('<=' | '<' | '>' | '>=' | '==' | '!=' | '+=' | '-=' | '*=' | '/=') expr #BinaryOp //
    | expr op=(AND | OR) expr #BinaryOp //
    | value=INTEGER #IntegerLiteral //
    | value=BOOL #Bolean //
    | name=ID #VarRefExpr //
    ;
