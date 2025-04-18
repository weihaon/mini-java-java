package mini_java;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.HashMap;
import java.util.Set;


class TransferPT implements Visitor {
    // Storage for conversion results
    private TFile tfile;
    private LinkedList<TDClass> classes = new LinkedList<>();

    // Current class and declarations being processed
    private Class_ currentClass;
    private LinkedList<TDecl> currentDecls;
    private PDecl currentMethod;

    // Class mapping table
    private HashMap<String, Class_> classMap = new HashMap<>();

    // Current type, expression and statement being processed
    private TType currentType;
    private TExpr currentExpr;
    private TStmt currentStmt;
    // Variable environment for tracking local variables
    private HashMap<String, Variable> localVars = new HashMap<>();

    // Method to find variables (first local variables, then member variables)
    // Recursively search for attributes in a class and its parent classes
    // Get the type of an expression
    private void initializeStandardLibrary() {
        // Create System class
        Class_ object = new Class_("Object");
        classMap.put("Object", object);
        Class_ systemClass = new Class_("System");
        classMap.put("System", systemClass);
        Class_ stringClass = new Class_("String");
        stringClass.extends_=object;
        classMap.put("String", stringClass);
        // Add equals method to String class with String parameter
        LinkedList<Variable> equalParam = new LinkedList<>();
        equalParam.add(new Variable("s", new TTclass(new Class_("String"))));
        Method equalsMethod = new Method("equals", new TTboolean(), equalParam);
        stringClass.methods.put("equals", equalsMethod);


        // Create PrintStream class
        Class_ printStreamClass = new Class_("PrintStream");
        classMap.put("PrintStream", printStreamClass);

        // Add print and println methods to PrintStream class
        LinkedList<Variable> stringParam = new LinkedList<>();
        stringParam.add(new Variable("s", new TTclass(new Class_("String"))));

        Method printMethod = new Method("print", new TTvoid(), stringParam);
        printStreamClass.methods.put("print", printMethod);


        // Add out attribute to System class
        Attribute outAttr = new Attribute("out", new TTclass(printStreamClass));
        systemClass.attributes.put("out", outAttr);
    }
    private boolean judgeReturnStatement(TStmt stmt,TType ReturnType) {
        if (stmt == null) {
            return false;
        }

        if (stmt instanceof TSreturn) {
            if(!isCompatibleType(getExprType(((TSreturn) stmt).e),ReturnType)){
                return true;
            }
            return false;
        }

        if (stmt instanceof TSblock) {
            TSblock block = (TSblock) stmt;
            for (TStmt s : block.l) {
                if (judgeReturnStatement(s,ReturnType)) {
                    return true;
                }
            }
            return false;
        }

        if (stmt instanceof TSif) {
            TSif ifStmt = (TSif) stmt;

            return judgeReturnStatement(ifStmt.s1,ReturnType) ||
                    judgeReturnStatement(ifStmt.s2,ReturnType);


        }

        if (stmt instanceof TSfor) {
            TSfor forStmt = (TSfor) stmt;
            // Check initialization statement, iteration statement and loop body
            return judgeReturnStatement(forStmt.s1,ReturnType) ||
                    judgeReturnStatement(forStmt.s2,ReturnType) ||
                    judgeReturnStatement(forStmt.s3,ReturnType);
        }

        // Other types of statements won't contain return statements
        return false;
    }
    private boolean hasReturnStatement(TStmt stmt) {
        if (stmt == null) {
            return false;
        }

        if (stmt instanceof TSreturn) {
            if(isCompatibleType(getExprType(((TSreturn) stmt).e),new TTvoid())){// Return type is void
                return false;
            }
            return true;
        }

        if (stmt instanceof TSblock) {
            TSblock block = (TSblock) stmt;
            for (TStmt s : block.l) {
                if (hasReturnStatement(s)) {
                    return true;
                }
            }
            return false;
        }

        if (stmt instanceof TSif) {
            TSif ifStmt = (TSif) stmt;
            if(hasReturnStatement(ifStmt.s1)&&hasReturnStatement(ifStmt.s2)){
                return true;
            }

            return false;

        }



        // Other types of statements won't contain return statements
        return false;
    }
    private TType getExprType(TExpr expr) {
        if (expr instanceof TEcst) {
            TEcst cst = (TEcst) expr;
            if (cst.c instanceof Cbool) return new TTboolean();
            if (cst.c instanceof Cint) return new TTint();
            if (cst.c instanceof Cstring) return new TTclass(classMap.get("String")); // Assuming there's a string type
        } else if (expr instanceof TEvar) {
            return ((TEvar) expr).x.ty;
        } else if (expr instanceof TEattr) {
            return ((TEattr) expr).a.ty;
        } else if (expr instanceof TEcall) {
            return ((TEcall) expr).m.type;
        } else if (expr instanceof TEthis) {
            return new TTclass(currentClass);
        } else if (expr instanceof TEnull) {
            return new TTnull();
        } else if (expr instanceof TEcast) {
            return ((TEcast) expr).ty;
        } else if (expr instanceof TEinstanceof) {
            return new TTboolean(); // instanceof returns boolean value
        } else if (expr instanceof TEbinop) {
            TEbinop binop = (TEbinop) expr;
            switch (binop.op) {
                case Badd:
                    // If one is String type, return String type
                    if(getExprType(binop.e1) instanceof TTclass && ((TTclass)getExprType(binop.e1)).c.name.equals("String")){
                        return new  TTclass(classMap.get("String"));
                    }
                    else if(getExprType(binop.e2) instanceof TTclass && ((TTclass)getExprType(binop.e2)).c.name.equals("String")){
                        return new TTclass(classMap.get("String"));
                    }
                    else{
                        return new TTint();
                    }
                case Bsub:
                case Bmul:
                case Bdiv:
                case Bmod:
                    return new TTint();
                case Blt:
                case Ble:
                case Bgt:
                case Bge:
                case Beq:
                case Bneq:
                case Band:
                case Bor:
                    return new TTboolean();
                case Badd_s:
                    return new  TTclass(classMap.get("String"));
                default:
                    return new TTvoid(); // Unknown operator
            }
        } else if (expr instanceof TEunop) {
            TEunop unop = (TEunop) expr;
            switch (unop.op) {
                case Uneg:
                    return new TTint();
                case Unot:
                case Upreinc:
                case Upostinc:
                case Upredec:
                case Upostdec:
                    return new TTboolean();
                case Ustring_of_int:
                    return new TTclass(classMap.get("String"));
                default:
                    // instanceof can only be used with class types, error
                    if(currentMethod instanceof PDmethod){
                        PDmethod method = (PDmethod) currentMethod;
                        System.err.println("file.java:"+method.x.loc);
                        System.err.println("in method "+method.x.id+" "+"Type not found");
                    }
                    else if(currentMethod instanceof PDconstructor){
                        PDconstructor constructor = (PDconstructor) currentMethod;
                        System.err.println("file.java:"+constructor.x.loc);
                        System.err.println("in constructor "+constructor.x.id+" "+"Type not found");
                    }
                    else{
                        PDattribute obj = (PDattribute) currentMethod;
                        System.err.println("file.java:"+obj.x.loc);
                        System.err.println("in attribute "+obj.x.id+" "+"Type not found");
                    }


                    // Force stop
                    System.exit(1);
            }
        } else if (expr instanceof TEnew) {
            return new TTclass(((TEnew) expr).cl);
        } else if (expr instanceof TEassignVar || expr instanceof TEassignAttr) {
            // The type of assignment expression is the type of the right-side expression
            if (expr instanceof TEassignVar) {
                return ((TEassignVar) expr).x.ty;
            } else {
                return ((TEassignAttr) expr).a.ty;
            }
        }

        return new TTvoid();
    }
    private Method lookupMethod(Class_ cls, String name) {
        if (cls == null) {
            return null;
        }

        // Look for method in current class
        Method method = cls.methods.get(name);
        if (method != null) {
            return method;
        }

        // Recursively look in parent class
        return lookupMethod(cls.extends_, name);
    }

    private Attribute lookupAttribute(Class_ cls, String name) {
        if (cls == null) {
            return null;
        }

        Attribute attr = cls.attributes.get(name);
        if (attr != null) {
            return attr;
        }

        // Recursively look in parent class
        return lookupAttribute(cls.extends_, name);
    }



    // Check type compatibility
    private boolean isCompatibleType(TType childType, TType parentType) {
        // Same types are directly compatible
        if (childType.getClass() == parentType.getClass()) {
            // For class types, need to check class compatibility
            if (childType instanceof TTclass && parentType instanceof TTclass) {
                TTclass childClass = (TTclass) childType;
                TTclass parentClass = (TTclass) parentType;

                // Check if it's the same class or a subclass
                Class_ current = childClass.c;
                while (current != null) {
                    if (current.name == parentClass.c.name) {
                        return true;
                    }
                    current = current.extends_;
                }
                return false;
            }
            else{
                return true;
            }
        }

        // Special case: null type can be assigned to any reference type
        if (childType instanceof TTnull) {
            return true;
        }

        // Other cases are incompatible
        return false;
    }


    private static boolean checkCyclicInheritance(Class_ cls) {
        Set<Class_> visited = new HashSet<>();
        Class_ current = cls;

        while (current != null) {
            if (!visited.add(current)) {
                return true;
            }
            current = current.extends_;
        }
            return false;

    }

    TransferPT(PFile f) {
        initializeStandardLibrary();
        // First pass: collect all class definitions
        for (PClass c : f.l) {
            Class_ cls = new Class_(c.name.id);
            Class_ ex= classMap.get(c.name.id);
            if(ex!=null){
                // Class already defined, error
                System.err.println("file.java:"+c.name.loc);
                System.err.println("Class "+c.name.id+" already defined");
                System.exit(1);

            }
            classMap.put(c.name.id, cls);

        }

        // Second pass: process inheritance relationships
        for (PClass c : f.l) {
            Class_ cls = classMap.get(c.name.id);
            if (c.ext != null) {
                cls.extends_ = classMap.get(c.ext.id);
                if(cls.extends_==null){
                    // Parent class not found, error
                    System.err.println("file.java:"+c.name.loc);
                    System.err.println("For class "+c.name.id+",Parent class not found, error");
                    System.exit(1);
                }
                // If inheriting from String class, error
                if(cls.extends_.name.equals("String")){
                    // Inheriting from String class, error
                    System.err.println("file.java:"+c.name.loc);
                    System.err.println("For class "+c.name.id+",Inheriting from String class, error");
                    System.exit(1);
                }
                if(checkCyclicInheritance(cls)){
                    // Cyclic inheritance detected, error
                    System.err.println("file.java:"+c.name.loc);
                    System.err.println("For class "+c.name.id+",Cyclic inheritance detected, error");
                    System.exit(1);
                }
            }else{
                if(!cls.name.equals("Object")){
                    cls.extends_=classMap.get("Object");
                }
                else{
                    // Object class cannot be defined
                    System.err.println("file.java:"+c.name.loc);
                    System.err.println("For class "+c.name.id+",Object class cannot be defined");
                    System.exit(1);
                }
            }
        }

        // Third pass: process class members and methods
        for (PClass c : f.l) {
            currentClass = classMap.get(c.name.id);
            currentDecls = new LinkedList<>();

            // First collect all attributes and method signatures
            for (PDecl d : c.l) {
                if (d instanceof PDattribute) {
                    PDattribute attr = (PDattribute) d;
                    TType type = convertType(attr.ty);
                    Attribute attribute = new Attribute(attr.x.id, type);
                    // Check for duplicate attribute definitions
                    if (currentClass.attributes.containsKey(attr.x.id)) {
                        // Duplicate attribute definition
                        System.err.println("file.java:"+((PDattribute) d).x.loc);
                        System.err.println("For decl "+((PDattribute) d).x.id+",Duplicate attribute definition, error");
                        System.exit(1);
                    }
                    currentClass.attributes.put(attr.x.id, attribute);

                } else if (d instanceof PDmethod) {
                    PDmethod method = (PDmethod) d;
                    TType returnType = convertType(method.ty);

                    LinkedList<Variable> params = new LinkedList<>();
                    if(method.l!=null) {
                        for (PParam p : method.l) {
                            TType paramType = convertType(p.ty);
                            params.add(new Variable(p.x.id, paramType));
                        }
                        // Check for duplicate parameter definitions
                        Set<String> paramNames = new HashSet<>();
                        for (Variable param : params) {
                            if (!paramNames.add(param.name)) {
                                // Duplicate parameter definition
                                System.err.println("file.java:"+method.x.loc);
                                System.err.println("For method "+method.x.id+",Duplicate parameter definition, error");
                                System.exit(1);
                            }
                        }
                    }
                    Method m = new Method(method.x.id, returnType, params);
                    if (currentClass.methods.containsKey(method.x.id)) {
                        // Duplicate method definition
                        System.err.println("file.java:"+method.x.loc);
                        System.err.println("For method "+method.x.id+",Duplicate method definition, error");
                        System.exit(1);
                    }
                    currentClass.methods.put(method.x.id, m);

                } else if (d instanceof PDconstructor) {
                    if(currentClass.constructor!=null){
                        // Duplicate constructor definition
                        System.err.println("file.java:"+((PDconstructor) d).x.loc);
                        System.err.println("For constructor "+((PDconstructor) d).x.id+",Duplicate constructor definition, error");
                        System.exit(1);
                    }
                    PDconstructor constructor = (PDconstructor) d;

                    // Check if constructor name matches class name
                    if (!constructor.x.id.equals(currentClass.name)) {
                        // Constructor name doesn't match class name, error
                        System.err.println("file.java:"+constructor.x.loc);
                        System.err.println("For constructor "+constructor.x.id+",Constructor name doesn't match class name, error");
                        System.exit(1);
                    }
                    LinkedList<Variable> params = new LinkedList<>();
                    if(constructor.l!=null) {
                        for (PParam p : constructor.l) {
                            TType paramType = convertType(p.ty);
                            params.add(new Variable(p.x.id, paramType));
                        }
                        // Check for duplicate parameter definitions
                        Set<String> paramNames = new HashSet<>();
                        for (Variable param : params) {
                            if (!paramNames.add(param.name)) {
                                // Duplicate parameter definition
                                System.err.println("file.java:"+constructor.x.loc);
                                System.err.println("For constructor "+constructor.x.id+",Duplicate parameter definition, error");
                                System.exit(1);
                            }
                        }
                    }

                    currentClass.constructor= new Method(constructor.x.id, new TTvoid(), params);

                }
            }

        }
        // Fourth pass: check inheritance relationships for member variables and methods
        for (PClass c : f.l) {
            Class_ cls = classMap.get(c.name.id);

            // Check inheritance relationships
            if (cls.extends_ != null) {
                // Check if member variables duplicate with parent class
                for (String attrName : cls.attributes.keySet()) {
                    if (lookupAttribute(cls, attrName) != null) {
                        // Check if types are the same
                        if(!isCompatibleType(cls.attributes.get(attrName).ty,lookupAttribute(cls, attrName).ty)){
                            // Types are different, error
                            System.err.println("file.java:"+c.name.loc);
                            System.err.println("Types of attribute "+attrName+" are different from parent, error");
                            System.exit(1);
                        }


                    }
                }

                // Check if overridden methods have consistent signatures
                for (String methodName : cls.methods.keySet()) {
                    if (lookupMethod(cls.extends_, methodName) != null) {
                        Method childMethod = cls.methods.get(methodName);
                        Method parentMethod =lookupMethod(cls.extends_, methodName);

                        // Check return type
                        if (!isCompatibleType(childMethod.type, parentMethod.type)) {
                            // Return type not compatible, error
                            System.err.println("file.java:"+c.name.loc);
                            System.err.println("For method "+methodName+",Return type not compatible with parent, error");
                            System.exit(1);

                        }
                        // Check parameter count
                        if (childMethod.params.size() != parentMethod.params.size()) {
                            // Parameter count mismatch, error
                            System.err.println("file.java:"+c.name.loc);
                            System.err.println("For method "+methodName+",Parameter count mismatch with parent, error");
                            System.exit(1);

                        } else {
                            // Check parameter types
                            for (int i = 0; i < childMethod.params.size(); i++) {
                                if (!isCompatibleType(childMethod.params.get(i).ty, parentMethod.params.get(i).ty)) {// Parameter names can be different
                                    // Parameter type not compatible, error
                                    System.err.println("file.java:"+c.name.loc);
                                    System.err.println("For method "+methodName+",Parameter type not compatible with parent, error");
                                    System.exit(1);
                                }

                            }
                        }
                    }
                }
            }
        }


// Fifth pass: process method bodies and constructors
        for (PClass c : f.l) {
            currentClass = classMap.get(c.name.id);
            currentDecls = new LinkedList<>();

            // Process method bodies and constructors
            for (PDecl d : c.l) {
                d.accept(this);
            }

            classes.add(new TDClass(currentClass, currentDecls));
        }
        classes.add((new TDClass(classMap.get("String"),new LinkedList<>())));
        tfile = new TFile(classes);
    }

    public TFile getResult() {
        return tfile;
    }

    // Helper method for type conversion
    private TType convertType(PType type) {
        if (type == null) {
            return new TTvoid();
        }

        TType prevType = currentType;
        currentType = null;

        type.accept(this);
        TType result = currentType;

        currentType = prevType;
        return result;
    }

    // Helper method for expression conversion
    private TExpr convertExpr(PExpr expr) {
        if (expr == null) {
            return null;
        }

        TExpr prevExpr = currentExpr;
        currentExpr = null;

        expr.accept(this);
        TExpr result = currentExpr;

        currentExpr = prevExpr;
        return result;
    }

    // Helper method for statement conversion
    private TStmt convertStmt(PStmt stmt) {
        if (stmt == null) {
            return null;
        }

        TStmt prevStmt = currentStmt;

        currentStmt = null;

        stmt.accept(this);
        TStmt result = currentStmt;// Save previous statement

        currentStmt = prevStmt;
        return result;
    }

    @Override
    public void visit(PTboolean t) {
        currentType = new TTboolean();
    }

    @Override
    public void visit(PTint t) {
        currentType = new TTint();
    }

    @Override
    public void visit(PTident t) {
        Class_ cls = classMap.get(t.x.id);
        if (cls != null) {
            currentType = new TTclass(cls);
        } else {
            // Handle case when class is not found
            System.err.println("Class not found: " + t.x.id);
            currentType = new TTvoid(); // Default to void type
        }
    }

    @Override
    public void visit(Cbool c) {
        currentExpr = new TEcst(c);
    }

    @Override
    public void visit(Cstring c) {
        currentExpr = new TEcst(c);
    }

    @Override
    public void visit(Cint c) {
        currentExpr = new TEcst(c);
    }


    @Override
    public void visit(PEcst e) {
        e.c.accept(this);
    }

    @Override
    public void visit(PEbinop e) {
        TExpr left = convertExpr(e.e1);
        TExpr right = convertExpr(e.e2);

        // Get types of operands
        TType leftType = getExprType(left);
        TType rightType = getExprType(right);

        // Check if operand types are compatible with the operator
        boolean isValid = false;
        String errorMsg = null;

        switch (e.op) {
            case Badd:
                // Addition: requires both operands to be integers, or at least one to be a string
                if (leftType instanceof TTint && rightType instanceof TTint) {
                    // int + int = int
                    isValid = true;
                } else if ((leftType instanceof TTclass && ((TTclass)leftType).c.name.equals("String")) ||
                        (rightType instanceof TTclass && ((TTclass)rightType).c.name.equals("String"))) {
                    // If at least one operand is String
                    // Check if the other operand is String or int
                    boolean leftOK = (leftType instanceof TTint) ||
                            (leftType instanceof TTclass && ((TTclass)leftType).c.name.equals("String"));
                    boolean rightOK = (rightType instanceof TTint) ||
                            (rightType instanceof TTclass && ((TTclass)rightType).c.name.equals("String"));

                    if (leftOK && rightOK) {
                        isValid = true;
                    } else {
                        errorMsg = "String concatenation only works with String or int";
                    }
                } else {
                    errorMsg = "Addition requires integer operands or at least one String operand";
                }
                break;
            case Bsub:
            case Bmul:
            case Bdiv:
            case Bmod:
                // Arithmetic operations: require both operands to be integers
                if (leftType instanceof TTint && rightType instanceof TTint) {
                    isValid = true;
                } else {
                    errorMsg = "Arithmetic operations require integer operands";
                }
                break;

            case Blt:
            case Ble:
            case Bgt:
            case Bge:
                // Comparison operations: require both operands to be integers
                if (leftType instanceof TTint && rightType instanceof TTint) {
                    isValid = true;
                } else {
                    errorMsg = "Comparison operations require integer operands";
                }
                break;
            case Beq:
            case Bneq:
                // Equality comparison: requires compatible types
                // 1. Same basic types can be compared
                if (leftType.getClass() == rightType.getClass()) {
                    isValid = true;
                }
                // 2. null can be compared with any reference type
                else if ((leftType instanceof TTnull && rightType instanceof TTclass) ||
                        (rightType instanceof TTnull && leftType instanceof TTclass)) {
                    isValid = true;
                }
                // 3. Compatible class types can be compared
                else if (leftType instanceof TTclass && rightType instanceof TTclass) {
                    if (isCompatibleType(leftType, rightType) || isCompatibleType(rightType, leftType)) {
                        isValid = true;
                    } else {
                        errorMsg = "Incompatible class types in equality comparison";
                    }
                } else {
                    errorMsg = "Incompatible types in equality comparison";
                }
                break;

            case Band:
            case Bor:
                // Logical operations: require both operands to be boolean
                if (leftType instanceof TTboolean && rightType instanceof TTboolean) {
                    isValid = true;
                } else {
                    errorMsg = "Logical operations require boolean operands";
                }
                break;
            case Badd_s:
                // String concatenation: at least one operand must be a string, the other can be any type
                // Note: In actual implementation, we need more complex logic to handle string concatenation
                // Simplified here: if one operand is a string type, consider it valid
                if (leftType instanceof TTclass) {
                    // One is a String class
                    TTclass leftClass = (TTclass) leftType;
                    if(leftClass.c.name.equals("String")){
                        isValid = true;
                    }
                }
                if(rightType instanceof TTclass){
                    TTclass rightClass = (TTclass) rightType;
                    if(rightClass.c.name.equals("String")){
                        isValid = true;
                    }}
                else {
                    errorMsg = "String concatenation requires at least one string operand";
                }
                break;

            default:
                errorMsg = "Unknown binary operator";
                break;
        }
        if (!isValid) {
            // Invalid operation, error
            // Print error message
            //System.err.println("file.java:"+currentDecls);
            //judge kind of currentMethod
            if(currentMethod instanceof PDmethod){
                PDmethod method = (PDmethod) currentMethod;
                System.err.println("file.java:"+method.x.loc);
                System.err.println("in method "+method.x.id+" "+errorMsg);
            }
            else if(currentMethod instanceof PDconstructor){
                PDconstructor constructor = (PDconstructor) currentMethod;
                System.err.println("file.java:"+constructor.x.loc);
                System.err.println("in constructor "+constructor.x.id+" "+errorMsg);
            }
            else{
                PDattribute obj = (PDattribute) currentMethod;
                System.err.println("file.java:"+obj.x.loc);
                System.err.println("in attribute "+obj.x.id+" "+errorMsg);
            }

            System.exit(1);
        } else {
            // Operation is valid, create expression
            currentExpr = new TEbinop(e.op, left, right);
        }
    }


    @Override
    public void visit(PEunop e) {
        TExpr expr = convertExpr(e.e);

        // Get operand type
        TType exprType = getExprType(expr);

        // Check if operand type is compatible with the operator
        boolean isValid = false;
        String errorMsg = null;

        switch (e.op) {
            case Uneg:  // -e
                // Negation operator requires integer operand
                if (exprType instanceof TTint) {
                    isValid = true;
                } else {
                    errorMsg = "Negation operator requires integer operand";
                }
                break;

            case Unot:  // !e
                // Logical NOT operator requires boolean operand
                if (exprType instanceof TTboolean) {
                    isValid = true;
                } else {
                    errorMsg = "Logical NOT operator requires boolean operand";
                }
                break;

            case Upreinc:  // ++e
            case Upostinc: // e++
            case Upredec:  // --e
            case Upostdec: // e--
                // Increment/decrement operators require integer operand that can be modified
                if (exprType instanceof TTint) {
                    // Check if operand can be modified (variable or attribute)

                    isValid = true;

                } else {
                    errorMsg = "Increment/decrement operator requires integer operand";
                }
                break;

            case Ustring_of_int:  // Convert integer to string
                // Integer to string conversion requires integer operand
                if (exprType instanceof TTint) {
                    isValid = true;
                } else {
                    errorMsg = "String conversion requires integer operand";
                }
                break;

            default:
                errorMsg = "Unknown unary operator";
                break;
        }

        if (!isValid) {
            // Invalid operation, error
            if(currentMethod instanceof PDmethod){
                PDmethod method = (PDmethod) currentMethod;
                System.err.println("file.java:"+method.x.loc);
                System.err.println("in method "+method.x.id+" "+errorMsg);
            }
            else if(currentMethod instanceof PDconstructor){
                PDconstructor constructor = (PDconstructor) currentMethod;
                System.err.println("file.java:"+constructor.x.loc);
                System.err.println("in constructor "+constructor.x.id+" "+errorMsg);
            }
            else{
                PDattribute obj = (PDattribute) currentMethod;
                System.err.println("file.java:"+obj.x.loc);
                System.err.println("in attribute "+obj.x.id+" "+errorMsg);
            }
          
            System.exit(1);
        } else {
            // Operation is valid, create expression
            currentExpr = new TEunop(e.op, expr);
        }
    }

    @Override
    public void visit(PEthis e) {
        currentExpr = new TEthis();
    }

    @Override
    public void visit(PEnull e) {
        currentExpr = new TEnull();
    }

    @Override
    public void visit(PEident e) {
        // Look for local variable
        Variable var = localVars.get(e.id.id);
        if (var != null) {// One exception
            currentExpr = new TEvar(var);
            return;
        }
        Attribute attr = lookupAttribute(currentClass, e.id.id);
        if(attr!=null){
            currentExpr = new TEattr(new TEthis(),attr);
            return;
        }
        // System is an exception
        if(e.id.id.equals("System")){
            var =new Variable("System",new TTclass(classMap.get("System")));
            currentExpr = new TEvar(var);

        }
        // Check if there's a corresponding class
        else {
            // Local variable not found, error
            System.err.println("file.java:"+e.id.loc);
            System.err.println("Local variable "+e.id.id+" not found, error");
            System.exit(1);
        }

    }

    @Override
    public void visit(PEassignIdent e) {
        TExpr expr = convertExpr(e.e);
        TType exprType = getExprType(expr);

        // Look for local variable
        Variable var = localVars.get(e.id.id);
        if (var != null) {
            // Check type compatibility
            if (isCompatibleType(exprType, var.ty)) {
                currentExpr = new TEassignVar(var, expr);
            } else {

                // Type not compatible
                System.err.println("file.java:"+e.id.loc);
                System.err.println("Variable "+e.id.id+" type not compatible, error");
                System.exit(1);
            }
            return;
        }


        // Look for attribute
        Attribute attr = lookupAttribute(currentClass, e.id.id);
        if (attr != null) {
            // Check type compatibility
            if (isCompatibleType(exprType, attr.ty)) {
                currentExpr = new TEassignAttr(new TEthis(), attr, expr);
            } else {

                // Type not compatible
                System.err.println("file.java:"+e.id.loc);
                System.err.println("Attribute "+e.id.id+" type not compatible, error");
                System.exit(1);
            }
            return;
        }
        else{
            // Handle case when identifier is not found
            System.err.println("file.java:"+e.id.loc);
            System.err.println("Identifier "+e.id.id+" not found, error");
            System.exit(1);
        }

        currentExpr = expr; // Default to return right-side expression
    }

    @Override
    public void visit(PEdot e) {
        TExpr expr = convertExpr(e.e);
        TType exprType = getExprType(expr);



        // Assume expr's type is TTclass
        if (exprType instanceof TTclass){
            TTclass classType = (TTclass) exprType;
            Attribute attr = lookupAttribute(classType.c, e.id.id);
            if (attr != null) {
                currentExpr = new TEattr(expr, attr);
            } else {

                // Error handling, member not found
                System.err.println("file.java:"+e.id.loc);
                System.err.println("Error handling, member not found: " + e.id.id + " in class " + classType.c.name);
                System.exit(1);

            }

        }
        else{
            // Class type not found, error
            System.err.println("file.java:"+e.id.loc);
            System.err.println("Class type "+e.id.id+" not found, error");
            System.exit(1);
        }

    }

    @Override
    public void visit(PEassignDot e) {
        TExpr obj = convertExpr(e.e1);
        TExpr val = convertExpr(e.e2);
        TType exprType = getExprType(obj);
        if (exprType instanceof TTclass){
            TTclass classType = (TTclass) exprType;
            Attribute attr = lookupAttribute(classType.c, e.id.id);
            if (attr != null) {
                currentExpr = new TEassignAttr(obj, attr, val);
            } else {
                // Error handling, member not found
                System.err.println("file.java:"+e.id.loc);
                System.err.println("Error handling, member "+e.id.id+" not found");
                System.exit(1);
            }

        }
        else{
            // Class type not found, error
            System.err.println("file.java:"+e.id.loc);
            System.err.println("Class type "+e.id.id+" not found, error");
            System.exit(1);
        }
    }

    @Override
    public void visit(PEnew e) {
        // Look up class
        Class_ cls = classMap.get(e.c.id);
        if (cls == null) {
            // Class not found, error
            System.err.println("file.java:"+e.c.loc);
            System.err.println("Class "+e.c.id+" not found, error");
            System.exit(1);
            return;
        }

        // Convert arguments
        LinkedList<TExpr> args = new LinkedList<>();
        LinkedList<TType> argTypes = new LinkedList<>();
        for (PExpr arg : e.l) {
            TExpr targ = convertExpr(arg);
            args.add(targ);
            argTypes.add(getExprType(targ));
        }

        // Find matching constructor
        boolean constructorFound = false;
        Method constructor = cls.constructor;

        // Check parameter count
        if (constructor.params.size() != args.size()) {
            // Parameter count mismatch, error
            System.err.println("file.java:"+e.c.loc);
            System.err.println("for "+cls.name+",constructor  count mismatch, error");
            System.exit(1);
        }

        // Check parameter types
        boolean paramsMatch = true;
        for (int i = 0; i < args.size(); i++) {
            if (!isCompatibleType(argTypes.get(i), constructor.params.get(i).ty)) {
                // Parameter type mismatch, error
                System.err.println("file.java:"+e.c.loc);
                System.err.println("for "+cls.name+",constructor  count mismatch, error");
                System.exit(1);
            }
        }
        currentExpr=new TEnew(cls, args);
    }

    @Override
    public void visit(PEcall e) {
        TExpr obj;
        if (e.e instanceof PEthis) {
            obj = new TEthis();
        } else {
            obj = convertExpr(e.e);
        }
        TType exprType=getExprType(obj);

        if (exprType instanceof TTclass){
            TTclass classType = (TTclass) exprType;
            Method method = lookupMethod(classType.c, e.id.id);

            if (method == null) {
                // Method not found, error
                System.err.println("file.java:"+e.id.loc);
                System.err.println("Method "+e.id.id+" not found, error");
                System.exit(1);
            }

            LinkedList<TExpr> args = new LinkedList<>();
            for (PExpr arg : e.l) {
                args.add(convertExpr(arg));
            }
            if(((TTclass) exprType).c.name.equals("PrintStream")) {
                currentExpr = new TEprint(args.get(0));
            }
            else{
                currentExpr = new TEcall(obj, method, args);
            }


            return;


        }
        // Class type not found, error
        System.err.println("file.java:"+e.id.loc);
        System.err.println("Class type "+e.id.id+" not found, error");
        System.exit(1);

    }

    @Override
    public void visit(PEcast e) {
        TExpr expr = convertExpr(e.e);
        TType type = convertType(e.ty);
        // Check if types are compatible
        if(isCompatibleType(getExprType(expr),type) || isCompatibleType(type,getExprType(expr))){
            currentExpr = new TEcast(type, expr);
        }
        else{
            if(currentMethod instanceof PDmethod){
                PDmethod method = (PDmethod) currentMethod;
                System.err.println("file.java:"+method.x.loc);
                System.err.println("in method "+method.x.id+" "+"Types not compatible, error");
            }
            else if(currentMethod instanceof PDconstructor){
                PDconstructor constructor = (PDconstructor) currentMethod;
                System.err.println("file.java:"+constructor.x.loc);
                System.err.println("in constructor "+constructor.x.id+" "+"Types not compatible, error");
            }
            else{
                PDattribute obj = (PDattribute) currentMethod;
                System.err.println("file.java:"+obj.x.loc);
                System.err.println("in attribute "+obj.x.id+" "+"Types not compatible, error");
            }



            System.exit(1);
        }
    }

    @Override
    public void visit(PEinstanceof e) {
        TExpr expr = convertExpr(e.e);
        TType type = convertType(e.ty);
        // Check if types are compatible
        if((getExprType(expr) instanceof TTclass || getExprType(expr) instanceof TTnull) && type instanceof TTclass){
            if(isCompatibleType(getExprType(expr),type) || isCompatibleType(type,getExprType(expr))){
                currentExpr = new TEinstanceof(expr, type);
            }
            else{
                if(currentMethod instanceof PDmethod){
                    PDmethod method = (PDmethod) currentMethod;
                    System.err.println("file.java:"+method.x.loc);
                    System.err.println("for method "+method.x.id+",instanceof inheritance relationship not satisfied");
                }
                else if(currentMethod instanceof PDconstructor){
                    PDconstructor constructor = (PDconstructor) currentMethod;
                    System.err.println("file.java:"+constructor.x.loc);
                    System.err.println("for constructor "+constructor.x.id+",instanceof inheritance relationship not satisfied");
                }
                else{
                    PDattribute obj = (PDattribute) currentMethod;
                    System.err.println("file.java:"+obj.x.loc);
                    System.err.println("for attribute "+obj.x.id+",instanceof inheritance relationship not satisfied");
                }



                System.exit(1);
            }
        }

        else{
            // instanceof can only be used with class types, error
            if(currentMethod instanceof PDmethod){
                PDmethod method = (PDmethod) currentMethod;
                System.err.println("file.java:"+method.x.loc);
                System.err.println("for method "+method.x.id+",Expression before instanceof is not a class or null, error");
            }
            else if(currentMethod instanceof PDconstructor){
                PDconstructor constructor = (PDconstructor) currentMethod;
                System.err.println("file.java:"+constructor.x.loc);
                System.err.println("for constructor "+constructor.x.id+",Expression before instanceof is not a class or null, error");
            }
            else{
                PDattribute obj = (PDattribute) currentMethod;
                System.err.println("file.java:"+obj.x.loc);
                System.err.println("for attribute "+obj.x.id+",Expression before instanceof is not a class or null, error");
            }
            System.exit(1);
        }



    }

    @Override
    public void visit(PSexpr s) {
        TExpr expr = convertExpr(s.e);
        currentStmt = new TSexpr(expr);
    }

    @Override
    public void visit(PSvar s) {
        TType type = convertType(s.ty);
        Variable var = new Variable(s.x.id, type);
        // Check for duplicate local variable definitions
        if (localVars.containsKey(s.x.id)) {
            // Duplicate local variable definition, error
            System.err.println("file.java:"+s.x.loc);
            System.err.println("for variable "+s.x.id+",Duplicate local variable definition, error");
            System.exit(1);
        }
        localVars.put(s.x.id, var);

        TExpr init = null;
        if (s.e != null) {
            init = convertExpr(s.e);
        }
        // Check if initialization expression type is compatible with variable type
        if (init != null) {
            TType initType = getExprType(init);
            if (!isCompatibleType(initType, type)) {
                // Initialization expression type not compatible with variable type, error
                System.err.println("file.java:"+s.x.loc);
                System.err.println("for variable "+s.x.id+",Initialization expression type not compatible with variable type, error");
                System.exit(1);
            }
        }

        currentStmt = new TSvar(var, init);
    }

    @Override
    public void visit(PSif s) {
        // Save current local variable environment
        HashMap<String, Variable> savedVars = new HashMap<>(localVars);
        TExpr cond = convertExpr(s.e);
        TStmt thenStmt = convertStmt(s.s1);
        localVars = savedVars;
        TStmt elseStmt = convertStmt(s.s2);
        localVars = savedVars;

        currentStmt = new TSif(cond, thenStmt, elseStmt);
    }

    @Override
    public void visit(PSreturn s) {
        TExpr expr = null;
        if (s.e != null) {
            expr = convertExpr(s.e);
        }

        currentStmt = new TSreturn(expr);
    }

    @Override
    public void visit(PSblock s) {
        LinkedList<TStmt> stmts = new LinkedList<>();

        // Save current local variable environment
        HashMap<String, Variable> savedVars = new HashMap<>(localVars);
        if(s.l!=null) {

            for (PStmt stmt : s.l) {
                stmts.add(convertStmt(stmt));
            }
        }

        // Restore local variable environment
        localVars = savedVars;

        currentStmt = new TSblock(stmts);
    }

    @Override
    public void visit(PSfor s) {
        // Save current local variable environment
        HashMap<String, Variable> savedVars = new HashMap<>(localVars);

        TStmt init = convertStmt(s.s1);
        TExpr cond = convertExpr(s.e);
        TStmt step = convertStmt(s.s2);
        TStmt body = convertStmt(s.s3);

        // Restore local variable environment
        localVars = savedVars;

        currentStmt = new TSfor(cond, init, step, body);
    }

    @Override
    public void visit(PDattribute s) {// Process global attributes
        currentMethod=s;
        if(s.ty instanceof PTident){
            Class_ cls = classMap.get(((PTident) s.ty).x.id);
            if (cls != null) {
                TType type = convertType(s.ty);
                //currentDecls.add(new TDattribute(type, s.x));
            }
            else{
                // Class not found, error
                System.err.println("file.java:"+s.x.loc);
                System.err.println("Class "+((PTident) s.ty).x.id+" not found, error");
                System.exit(1);
            }
        }
        else{
            TType type = convertType(s.ty);
            //currentDecls.add(new TDattribute(type, s.x));
        }

    }

    @Override
    public void visit(PDconstructor s) {
        // Clear local variable environment
        localVars.clear();
        currentMethod=s;
        Method method = currentClass.constructor;


        // Process parameters
        for (Variable var : method.params) {
            localVars.put(var.name, var);
        }


        // Process constructor body
        TStmt body = convertStmt(s.s);
        // Check if constructor body contains return statements
        if (hasReturnStatement(body)) {
            // Constructor body contains return statement, error
            System.err.println("file.java:"+s.x.loc);
            System.err.println("for constructor "+s.x.id+",Constructor body contains return statement, error");
            System.exit(1);
        }


        currentDecls.add(new TDconstructor(method.params, body));
    }


    @Override
    public void visit(PDmethod s) {
        // Clear local variable environment
        localVars.clear();
        currentMethod=s;

        // Get previously created method object
        Method method = currentClass.methods.get(s.x.id);


        for (Variable var : method.params) {
            localVars.put(var.name, var);
        }

        // Process method body
        TStmt body = convertStmt(s.s);
        TType returnType = method.type;
        if( returnType instanceof TTvoid ) {
            if(hasReturnStatement(body)){
                // Method body contains return statement, error
                System.err.println("file.java:"+s.x.loc);
                System.err.println("for method "+s.x.id+",Method body contains return statement, error");
                System.exit(1);
            }
        }
        else{
            if(hasReturnStatement(body)){
                if(judgeReturnStatement(body,returnType)) {
                    // Return statement type doesn't match return type, error
                    System.err.println("file.java:"+s.x.loc);
                    System.err.println("for method "+s.x.id+",Return statement type doesn't match return type, error");
                    System.exit(1);
                }
            }
            else{
                // Method body missing return statement, error
                System.err.println("file.java:"+s.x.loc);
                System.err.println("for method "+s.x.id+",Method body missing return statement, error");
                System.exit(1);
            }

        }


        // Check if return statements match return type

        currentDecls.add(new TDmethod(method,  body));
    }


}