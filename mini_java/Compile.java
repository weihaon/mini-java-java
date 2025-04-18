package mini_java;

import java.util.*;



public class Compile {

  static boolean debug = false;

  static X86_64 file(TFile f) {

    X86_64 asm = new X86_64();
    // 生成代码
    CodeGenerator codeGen = new CodeGenerator(asm);
    codeGen.generateCode(f);
    
    return asm;

  }
}

/**
 * Code Generator - Converts typed Mini Java AST to x86-64 assembly code
 */
class CodeGenerator implements TVisitor {
  private X86_64 code;
  private int labelCounter;
  private Class_ currentClass;
  private Method currentMethod;
  private HashMap<String, String> stringConstants;
  private int stringCounter;
  private HashMap<String, Class_> classMap;

  public CodeGenerator(X86_64 code) {
    this.code =code ;
    this.labelCounter = 0;
    this.stringConstants = new HashMap<>();
    this.stringCounter = 0;
  }

  private String freshLabel() {
    return "L" + (labelCounter++);
  }
  private void initializeStandardLibrary() {
    // Create Object class
    Class_ object = new Class_("Object");
    classMap.put("Object", object);
    
    // Create System class
    Class_ systemClass = new Class_("System");
    classMap.put("System", systemClass);
    
    // Create String class
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
  private String addStringConstant(String s) {
    if (stringConstants.containsKey(s)) {
      return stringConstants.get(s);
    }
    String label = "string" + (stringCounter++);
    s = s.replace("\"", "\\\"");
    stringConstants.put(s, label);
    code.dlabel(label);
    code.string(s);
    return label;
  }



  /**
   * Computes attribute offsets for a class (single inheritance)
   * 1) If parent exists, recursively compute parent offsets first
   * 2) No duplicate attribute names allowed between parent and child
   * 3) Assign offsets to child attributes after parent's last offset
   */
  private void computeAttributeOffsets(Class_ c) {
    // 1) If parent exists, compute parent offsets first
    if (c.extends_ != null) {
      computeAttributeOffsets(c.extends_);
    }

    // 2) Find max offset+8 from parent, or default to 8 (skip descriptor pointer at +0)
    int offset = 8;
    if (c.extends_ != null) {
      // Find parent's max offset
      for (Attribute parentAttr : c.extends_.attributes.values()) {
        // Next available position is parent's offset + 8
        offset = Math.max(offset, parentAttr.ofs + 8);
      }
    }

    // 3) Check for duplicate names with parent; assign offsets to new attributes
    for (Attribute attr : c.attributes.values()) {
      // If offset not assigned yet, this is a new attribute in child class
      if (attr.ofs == -1) {
        attr.ofs = offset;
        offset += 8; // Assume 8 bytes for all attributes
      }
    }
  }


  /**
   * Computes local variable and parameter offsets for methods/constructors.
   * Returns required stack frame size (without alignment).
   *
   * @param m     Method (or null for constructor)
   * @param body  Method/constructor body
   * @return      Total bytes needed for local variables (without alignment)
   */
  private int computeLocalVarOffsets(Method m, TStmt body) {
    int localSize = 0;

    // 1) Assign offsets to parameters if they exist (starting at 24 to account for hidden this pointer)
    if (m.params.size() > 0) {
      int paramOffset = 24;
      for (Variable param : m.params) {
        param.ofs = paramOffset;
        paramOffset += 8;
      }
    }

    // 2) Collect local variables from method body
    LocalVarVisitor localVarVisitor = new LocalVarVisitor();
    body.accept(localVarVisitor);
 
    // 3) Assign offsets to local variables (from -8, -16, ...)
    int localOffset = -8;
    for (Variable var : localVarVisitor.localVars) {
      var.ofs = localOffset;
      localOffset -= 8;
    }

    // 4) Calculate total size needed for local variables
    // Final localOffset value represents how much space we used:
    // e.g., if we defined n variables => localOffset = -8 * n
    localSize = -localOffset - 8;
    // or simpler: localSize = localVarVisitor.localVars.size() * 8;

    return localSize;
  }

  /**
   * Generates class descriptor
   */
  private void generateClassDescriptor(TDClass tdc) {
    currentClass = tdc.c;

    // Calculate attribute offsets
    computeAttributeOffsets(currentClass);

    // Generate class descriptor
    code.dlabel(currentClass.name + "_descriptor");

    // Parent class descriptor pointer
    String superClass = (currentClass.extends_ != null) ? currentClass.extends_.name + "_descriptor" : "0";
    code.data(".quad " + superClass);

    // Method table
    for (Method m : currentClass.methods.values()) {
      code.data(".quad " + currentClass.name + "_" + m.name);
    }
  }

  /**
   * Generate code for all classes
   */
  public void generateCode(TFile file) {
    // Create class map
    classMap = new HashMap<>();
    initializeStandardLibrary();
    for (TDClass tdc : file.l) {
      classMap.put(tdc.c.name, tdc.c);
    }
    
    // Step 1: Generate all class descriptors
    code.dlabel("Object_descriptor");
    code.data(".quad 0");
    for (TDClass tdc : file.l) {
      generateClassDescriptor(tdc);
    }

    // Step 2: Generate code for all methods
    for (TDClass tdc : file.l) {
      currentClass = tdc.c;
      for (TDecl decl : tdc.l) {
        decl.accept(this);
      }
    }

    // Step 3: Generate runtime support functions
    generateRuntimeSupport();
  }

  /**
   * Generate runtime support functions
   */
  private void generateRuntimeSupport() {
    // Add my_malloc wrapper function
    code.label("my_malloc");
    code.pushq("%rbp");
    code.movq("%rsp", "%rbp");
    code.andq("$-16", "%rsp");  // 16-byte stack alignment
    code.call("malloc");
    code.movq("%rbp", "%rsp");
    code.popq("%rbp");
    code.ret();

    // instanceof implementation
    code.label("instanceof");
    code.pushq("%rbp");
    code.movq("%rsp", "%rbp");

    // 检查null
    code.testq("%rdi", "%rdi");
    code.je("instanceof_false");
    code.movq("(%rdi)", "%rax");  // 获取当前类描述符

    code.label("instanceof_loop");
    code.cmpq("%rsi", "%rax");    // 比较与目标类
    code.je("instanceof_true");
    code.movq("(%rax)", "%rax");   // 保留来访问父类
    code.testq("%rax", "%rax");   // 检查是否到达Object
    code.jnz("instanceof_loop");

    code.label("instanceof_false");
    code.movq(0, "%rax");
    code.leave();
    code.ret();

    code.label("instanceof_true");
    code.movq(1, "%rax");
    code.leave();
    code.ret();

    // Type cast checking
    code.label("checkcast");
    code.pushq("%rbp");
    code.movq("%rsp", "%rbp");

    // 检查null (null可以转换为任何类型)
    code.testq("%rdi", "%rdi");
    code.je("checkcast_ok");

    // 调用instanceof
    code.call("instanceof");
    code.testq("%rax", "%rax");
    code.jnz("checkcast_ok");

    // 类型转换失败
    code.movq("$cast_error", "%rdi");
    code.call("puts");
    code.movq(1, "%rdi");
    code.call("exit");

    code.label("checkcast_ok");
    code.movq("%rdi", "%rax");  // 返回原对象
    code.leave();
    code.ret();

    // Error messages
    code.dlabel("cast_error");
    code.string("Runtime error: invalid cast");

    code.dlabel("null_error");
    code.string("Runtime error: null pointer dereference");
    code.dlabel("int_format");
    code.string("%d");
    code.dlabel("string_format");
    code.string("%s");
    code.dlabel("true_str");
    code.string("true");
    code.dlabel("false_str");
    code.string("false");

    // Add String_equals method
    code.label("String_equals");
    code.pushq("%rbp");
    code.movq("%rsp", "%rbp");

    // 获取参数：this 指针和比较的字符串
    // this 指针在 16(%rbp)
    // 参数字符串在 24(%rbp)

    // 检查 this 是否为 null
    code.movq("16(%rbp)", "%rdi");
    code.testq("%rdi", "%rdi");
    String labelNotNull1 = freshLabel();
    code.jne(labelNotNull1);

    // null 指针错误
    code.movq("$null_error", "%rdi");
    code.call("puts");
    code.movq(1, "%rdi");
    code.call("exit");

    // this 不为 null
    code.label(labelNotNull1);

    // 检查参数是否为 null
    code.movq("24(%rbp)", "%rsi");
    code.testq("%rsi", "%rsi");
    String labelNotNull2 = freshLabel();
    code.jne(labelNotNull2);

    // 如果参数为 null，返回 false
    code.movq(0, "%rax");
    code.leave();
    code.ret();

    // 参数不为 null
    code.label(labelNotNull2);

    // 调用 strcmp 比较字符串
    code.movq("%rdi", "%rsi");  // 第一个字符串作为第二个参数
    code.movq("24(%rbp)", "%rdi");  // 第二个字符串作为第一个参数
    code.call("strcmp");

    // 检查 strcmp 的返回值
    code.testq("%rax", "%rax");
    String labelNotEqual = freshLabel();
    code.jne(labelNotEqual);

    // 字符串相等，返回 true
    code.movq(1, "%rax");
    code.leave();
    code.ret();

    // 字符串不相等，返回 false
    code.label(labelNotEqual);
    code.movq(0, "%rax");
    code.leave();
    code.ret();
  }

  private TType getExprType(TExpr expr) {
    if (expr instanceof TEcst) {
      TEcst cst = (TEcst) expr;
      if (cst.c instanceof Cbool) return new TTboolean();
      if (cst.c instanceof Cint) return new TTint();
      if (cst.c instanceof Cstring) return new TTclass(classMap.get("String")); // Use system String class
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
      return getExprType(((TEcast) expr).e);
    } else if (expr instanceof TEinstanceof) {
      return new TTboolean();
    } else if (expr instanceof TEbinop) {
      TEbinop binop = (TEbinop) expr;
      switch (binop.op) {
        case Badd:
          if (getExprType(binop.e1) instanceof TTclass &&
                  ((TTclass)getExprType(binop.e1)).c.name.equals("String")) {
            return new TTclass(classMap.get("String"));
          } else if (getExprType(binop.e2) instanceof TTclass &&
                  ((TTclass)getExprType(binop.e2)).c.name.equals("String")) {
            return new TTclass(classMap.get("String"));
          } else {
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
          return new TTclass(classMap.get("String"));
        default:
          return new TTvoid();
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
          System.err.println("Unknown unary operator type");
          System.exit(1);
      }
    } else if (expr instanceof TEnew) {
      return new TTclass(((TEnew) expr).cl);
    } else if (expr instanceof TEassignVar || expr instanceof TEassignAttr) {
      if (expr instanceof TEassignVar) {
        return ((TEassignVar) expr).x.ty;
      } else {
        return ((TEassignAttr) expr).a.ty;
      }
    }
    return new TTvoid();
  }


  // 访问者模式实现 - 类型
  @Override
  public void visit(TTvoid t) {}

  @Override
  public void visit(TTnull t) {}

  @Override
  public void visit(TTboolean t) {}

  @Override
  public void visit(TTint t) {}

  @Override
  public void visit(TTclass t) {}


  // 访问者模式实现 - 表达式
  @Override
  public void visit(TEcst e) {
    if (e.c instanceof Cbool) {
      Cbool c = (Cbool) e.c;
      code.movq(c.b ? 1 : 0, "%rax");
    } else if (e.c instanceof Cstring) {
      Cstring c = (Cstring) e.c;
      String label = addStringConstant(c.s);
      code.leaq(label + "(%rip)", "%rax");
    } else if (e.c instanceof Cint) {
      Cint c = (Cint) e.c;
      code.movq((int) c.i, "%rax");
    }
  }

  @Override
  public void visit(TEbinop e) {
    switch (e.op) {
      case Badd:
        // Check if string concatenation is involved
        TType t1 = getExprType(e.e1);
        TType t2 = getExprType(e.e2);

        if ((t1 instanceof TTclass && ((TTclass)t1).c.name.equals("String")) ||
                (t2 instanceof TTclass && ((TTclass)t2).c.name.equals("String"))) {

          // Allocate buffer
          code.movq(256, "%rdi");  // Assume max length of 256
          code.call("my_malloc");
          code.pushq("%rax");      // Save buffer pointer

          // Process first expression
          e.e1.accept(this);
          if (t1 instanceof TTint) {
            code.pushq("%rax");             // Save integer value
            code.movq(32, "%rdi");          // Allocate temporary buffer size
            code.call("my_malloc");         // Call my_malloc

            // Set up sprintf parameters
            code.movq("%rax", "%rdi");      // Temp buffer as target
            code.movq("$int_format", "%rsi"); // Format string
            code.popq("%rdx");              // Integer value as third parameter
            code.pushq("%rdi");             // Save buffer address again

            code.movq(0, "%rax");           // Required for sprintf
            code.call("sprintf");           // Call sprintf
          } else {
            // If string, copy directly
            code.movq("%rax", "%rsi");      // Source string
            code.popq("%rdi");              // Target buffer
            code.pushq("%rdi");             // Save buffer address again
            code.call("strcpy");            // Call strcpy
          }


          // Process second expression
          e.e2.accept(this);
          if (t2 instanceof TTint) {
            // If integer, convert to string and append
            code.pushq("%rax");             // Save integer value
            code.movq(32, "%rdi");          // Allocate temporary buffer size
            code.call("my_malloc");         // Call my_malloc

            // Set up sprintf parameters
            code.movq("%rax", "%rdi");      // Temp buffer as target
            code.movq("$int_format", "%rsi"); // Format string
            code.popq("%rdx");              // Integer value as third parameter
            code.pushq("%rdi");             // Save buffer address again

            code.movq(0, "%rax");           // Required for sprintf
            code.call("sprintf");           // Call sprintf

            // Append to main buffer
            code.popq("%rsi");
            code.popq("%rdi");              // Main buffer as target
            code.pushq("%rdi");             // Save main buffer again
            code.call("strcat");            // Append string
          } else {
            // If string, append directly
            code.movq("%rax", "%rsi");      // Source string
            code.popq("%rdi");              // Target buffer
            code.pushq("%rdi");             // Save buffer address again
            code.call("strcat");            // Call strcat
          }

          // Return result string
          code.popq("%rax");
          return;
        } else {
          // Number addition
          e.e2.accept(this);
          code.pushq("%rax");
          e.e1.accept(this);
          code.popq("%rdx");
          code.addq("%rdx", "%rax");
        }
        break;


      case Bsub:
        e.e2.accept(this);
        code.pushq("%rax");
        e.e1.accept(this);
        code.popq("%rdx");
        code.subq("%rdx", "%rax");
        break;

      case Bmul:
        e.e2.accept(this);
        code.pushq("%rax");
        e.e1.accept(this);
        code.popq("%rdx");
        code.imulq("%rdx", "%rax");
        break;

      case Bdiv:
        e.e2.accept(this);
        code.pushq("%rax");
        e.e1.accept(this);
        code.popq("%rcx");
        code.cqto();
        code.idivq("%rcx");
        break;

      case Beq:
        e.e2.accept(this);
        code.pushq("%rax");
        e.e1.accept(this);
        code.popq("%rdx");
        code.cmpq("%rdx", "%rax");
        code.sete("%al");
        code.movzbq("%al", "%rax");
        break;

      case Bneq:
        e.e2.accept(this);
        code.pushq("%rax");
        e.e1.accept(this);
        code.popq("%rdx");
        code.cmpq("%rdx", "%rax");
        code.setne("%al");
        code.movzbq("%al", "%rax");
        break;

      case Blt:
        e.e2.accept(this);
        code.pushq("%rax");
        e.e1.accept(this);
        code.popq("%rdx");
        code.cmpq("%rdx", "%rax");
        code.setl("%al");
        code.movzbq("%al", "%rax");
        break;

      case Ble:
        e.e2.accept(this);
        code.pushq("%rax");
        e.e1.accept(this);
        code.popq("%rdx");
        code.cmpq("%rdx", "%rax");
        code.setle("%al");
        code.movzbq("%al", "%rax");
        break;

      case Bgt:
        e.e2.accept(this);
        code.pushq("%rax");
        e.e1.accept(this);
        code.popq("%rdx");
        code.cmpq("%rdx", "%rax");
        code.setg("%al");
        code.movzbq("%al", "%rax");
        break;

      case Bge:
        e.e2.accept(this);
        code.pushq("%rax");
        e.e1.accept(this);
        code.popq("%rdx");
        code.cmpq("%rdx", "%rax");
        code.setge("%al");
        code.movzbq("%al", "%rax");
        break;

      case Band:
        String labelFalse = freshLabel();
        String labelEnd = freshLabel();

        e.e1.accept(this);
        code.testq("%rax", "%rax");
        code.je(labelFalse);

        e.e2.accept(this);
        code.testq("%rax", "%rax");
        code.je(labelFalse);

        code.movq(1, "%rax");
        code.jmp(labelEnd);

        code.label(labelFalse);
        code.movq(0, "%rax");

        code.label(labelEnd);
        break;

      case Bor:
        String labelTrue = freshLabel();
        String labelEnd2 = freshLabel();

        e.e1.accept(this);
        code.testq("%rax", "%rax");
        code.jne(labelTrue);

        e.e2.accept(this);
        code.testq("%rax", "%rax");
        code.je(labelEnd2);

        code.label(labelTrue);
        code.movq(1, "%rax");

        code.label(labelEnd2);
        break;
      case Bmod:
        // Calculate second expression (divisor)
        e.e2.accept(this);
        code.pushq("%rax");  // Save divisor

        // Calculate first expression (dividend)
        e.e1.accept(this);

        // Prepare division operation
        code.movq("%rax", "%rbx");  // Save dividend to rbx
        code.popq("%rcx");          // Get divisor into rcx

        // Sign extend dividend to rdx:rax
        code.movq("%rbx", "%rax");
        code.cqto();                 // Sign extend RAX to RDX:RAX

        // Perform division, remainder in rdx
        code.idivq("%rcx");
        code.movq("%rdx", "%rax");  // Move remainder to return register
        break;
    }
  }

  @Override
  public void visit(TEunop e) {
    e.e.accept(this);

    switch (e.op) {
      case Uneg:
        code.negq("%rax");
        break;

      case Unot:
        code.testq("%rax", "%rax");
        code.sete("%al");
        code.movzbq("%al", "%rax");
        break;
    }
  }

  @Override
  public void visit(TEthis e) {
    code.movq("16(%rbp)", "%rax");  // this指针在rbp+8
  }

  @Override
  public void visit(TEnull e) {
    code.movq(0, "%rax");
  }

  @Override
  public void visit(TEvar e) {
//    if(e.x.ofs == -1) {
//      System.err.println("Error: Variable " + e.x.name + " has not been assigned a valid offset!");
//      System.exit(1);
//    }
    code.movq(e.x.ofs + "(%rbp)", "%rax"); //对应Calculator_constructor: movq %rsp, %rbp movq -1(%rbp), %rax
  }

  @Override
  public void visit(TEassignVar e) {
    e.e.accept(this);
    TType t=getExprType(e.e);
    e.x.ty=t;
    code.movq("%rax", e.x.ofs + "(%rbp)"); //对应Calculator_constructor:	movq %rax, -1(%rbp)
  }

  @Override
  public void visit(TEattr e) {
    e.e.accept(this);

    // Check for null pointer
    code.testq("%rax", "%rax");
    String labelNotNull = freshLabel();
    code.jne(labelNotNull);

    // Handle null pointer error
    code.movq("$null_error", "%rdi");
    code.call("puts");
    code.movq(1, "%rdi");
    code.call("exit");

    code.label(labelNotNull);
    code.movq(e.a.ofs + "(%rax)", "%rax");
  }

  @Override
  public void visit(TEassignAttr e) {
    // Evaluate right-hand side and save
    e.e2.accept(this);
    code.pushq("%rax");

    // Evaluate left-hand side object
    e.e1.accept(this);
    TType t=getExprType(e.e2);
    e.a.ty=t;

    // Check for null pointer
    code.testq("%rax", "%rax");
    String labelNotNull = freshLabel();
    code.jne(labelNotNull);

    // Handle null pointer error
    code.movq("$null_error", "%rdi");
    code.call("puts");
    code.movq(1, "%rdi");
    code.call("exit");

    code.label(labelNotNull);
    code.popq("%rdx");  // Restore right-hand value
    code.movq("%rdx", e.a.ofs + "(%rax)");
    code.movq("%rdx", "%rax");  // Assignment expression value is the right-hand value
  }

  @Override
  public void visit(TEnew e) {
    // Calculate object size
    int size = 8; // Class descriptor pointer
    int max_offset = 0;
    for (Attribute attr : e.cl.attributes.values()) {
      if(attr.ofs>max_offset){
        max_offset=attr.ofs;
      }
    }
    size=max_offset+size;

    // Call malloc
    code.movq(size, "%rdi");
    code.call("my_malloc");  // Use wrapper function

    // Set class descriptor pointer
    code.movq("$" + e.cl.name + "_descriptor", "(%rax)");

    // If constructor has parameters, save object pointer
    if (e.cl.constructor!=null) {
      code.pushq("%rax");

      // Calculate and push parameters (right to left)
      for (int i = e.l.size() - 1; i >= 0; i--) {
        e.l.get(i).accept(this);
        code.pushq("%rax");
      }

      // Push this pointer
      code.movq((e.l.size() * 8) + "(%rsp)", "%rax");
      code.pushq("%rax");

      // Call constructor
      code.call(e.cl.name + "_constructor");

      // Clean stack and restore object pointer
      int stackSize = (e.l.size() + 1) * 8;
      code.addq("$" + String.valueOf(stackSize), "%rsp");
      code.popq("%rax");
    }
  }

  @Override
  public void visit(TEcall e) {
    // Calculate and push parameters (right to left)
    for (int i = e.l.size() - 1; i >= 0; i--) {
      e.l.get(i).accept(this);
      code.pushq("%rax");
    }

    // Calculate object pointer
    e.e.accept(this);

    // Check for null pointer
    code.testq("%rax", "%rax");
    String labelNotNull = freshLabel();
    code.jne(labelNotNull);

    // Handle null pointer error
    code.movq("$null_error", "%rdi");
    code.call("puts");
    code.movq(1, "%rdi");
    code.call("exit");

    code.label(labelNotNull);
    code.pushq("%rax"); // this pointer

    Class_ targetClass = ((TTclass)getExprType(e.e)).c;
    if(targetClass.name.equals("String") && e.m.name.equals("equals")){
      code.movq("$" + targetClass.name + "_descriptor", "%rcx");
    }
    else{
      code.movq("(%rax)", "%rcx");
    }

    TType objType = getExprType(e.e);
    if (!(objType instanceof TTclass)) {
      System.err.println("Error: Receiver is not a class type.");
      System.exit(1);
    }

    int targetOffset = 8; // Skip parent pointer
    int stackSize = (e.l.size() + 1) * 8;
    while (targetClass != null) {
      targetOffset=8;
      for (Method m : targetClass.methods.values()) {
        if (m.name.equals(e.m.name)) {
          // Found method, call it
          code.callstar(targetOffset + "(%rcx)");
          code.addq("$" + String.valueOf(stackSize), "%rsp");
          return;
        }
        targetOffset += 8;
      }
      code.movq("(%rcx)", "%rcx");
      targetClass = targetClass.extends_;
    }
  }

  @Override
  public void visit(TEcast e) {
    // Check if e.e is null
    if (e.e == null) {
      System.err.println("Error: TEcast expression is null.");
      System.exit(1);
    }

    // Evaluate the expression to be cast
    e.e.accept(this);

    // Handle type casting
    if (e.ty instanceof TTint) {
      // If target type is int, no special handling needed
      // Since all values are already 64-bit integers in our implementation
      return;
    } else if (e.ty instanceof TTclass) {
      // Type casting to class type
      TTclass targetClass = (TTclass) e.ty;

      // Check if class descriptor exists
      if (targetClass.c == null) {
        System.err.println("Error: Target class descriptor is missing.");
        System.exit(1);
      }
      // When the expression being cast is a String
      TType t = getExprType(e.e);
      if(t instanceof TTclass && ((TTclass)t).c.name.equals("String")){
        return;
      }
      // Save object pointer
      code.pushq("%rax");

      // Prepare parameters
      code.movq("%rax", "%rdi");
      code.movq("$" + targetClass.c.name + "_descriptor", "%rsi");

      // Call checkcast
      code.call("checkcast");

      // Restore stack
      code.addq("$8", "%rsp");
    } else if (e.ty instanceof TTboolean) {
      // Cast to boolean, just ensure value is 0 or 1
      code.andq("$1", "%rax");
    } else {
      System.err.println("Error: Unsupported cast type.");
      System.exit(1);
    }
  }


  @Override
  public void visit(TEinstanceof e) {
    e.e.accept(this);

    // Prepare parameters
    TType t = getExprType(e.e);
    if(t instanceof TTclass && ((TTclass)t).c.name.equals("String")){
      return;
    }
    code.movq("%rax", "%rdi");
    code.movq("$" + ((TTclass)e.ty).c.name + "_descriptor", "%rsi");

    // Call instanceof
    code.call("instanceof");
  }

  @Override
  public void visit(TEprint e) {
    // Generate code for the expression, result in %rax
    e.e.accept(this);

    TType t = getExprType(e.e);
    if (t instanceof TTclass && ((TTclass)t).c.name.equals("String")) {
      // Print string
      code.movq("%rax", "%rsi");      // String pointer as second parameter
      code.movq("$string_format", "%rdi");  // Format string as first parameter
      code.movq(0, "%rax");           // Required for printf
      code.call("printf");
    } else if (t instanceof TTint) {
      // Print integer
      code.movq("%rax", "%rsi");
      code.movq("$int_format", "%rdi");
      code.movq(0, "%rax");
      code.call("printf");
    }
    // Print expression value is void
    code.movq(0, "%rax");
  }

  // 访问者模式实现 - 语句
  @Override
  public void visit(TSexpr s) {
    if (s.e != null) {
      s.e.accept(this);
    }
  }

  @Override
  public void visit(TSvar s) {
    if (s.e != null) {
      s.e.accept(this);
      code.movq("%rax", s.v.ofs + "(%rbp)");
    }
  }

  @Override
  public void visit(TSif s) {
    String labelElse = freshLabel();
    String labelEnd = freshLabel();

    // Evaluate condition
    s.e.accept(this);
    code.testq("%rax", "%rax");
    code.je(labelElse);

    // Then branch
    s.s1.accept(this);
    code.jmp(labelEnd);

    // Else branch
    code.label(labelElse);
    if (s.s2 != null) {
      s.s2.accept(this);
    }

    code.label(labelEnd);
  }

  @Override
  public void visit(TSreturn s) {
    if (s.e != null) {
      s.e.accept(this);
    }
    TType t = getExprType(s.e);
    if(t instanceof TTvoid){
      code.movq(0, "%rax");
    }
    code.leave();
    code.ret();
  }

  @Override
  public void visit(TSblock s) {
    for (TStmt stmt : s.l) {
      stmt.accept(this);
    }
  }

  @Override
  public void visit(TSfor s) {
    String labelStart = freshLabel();
    String labelBody = freshLabel();
    String labelIncr = freshLabel();
    String labelEnd = freshLabel();

    // Initialization
    if (s.s1 != null) {
      s.s1.accept(this);
    }

    // Loop start
    code.label(labelStart);

    // Condition check
    if (s.e != null) {
      s.e.accept(this);
      code.testq("%rax", "%rax");
      code.jne(labelBody);
      code.jmp(labelEnd);
    }

    // Loop body
    code.label(labelBody);
    s.s3.accept(this);

    // Increment part
    code.label(labelIncr);
    if (s.s3 != null) {
      s.s2.accept(this);
    }

    // Jump back to loop start
    code.jmp(labelStart);

    // Loop end
    code.label(labelEnd);
  }

  // 访问者模式实现 - 声明
  @Override
  public void visit(TDconstructor d) {
    // Generate constructor label
    code.label(currentClass.name + "_constructor");

    // Function prologue
    code.pushq("%rbp");
    code.movq("%rsp", "%rbp");

    // Allocate space for local variables
    int frameSize = computeLocalVarOffsets(currentClass.constructor, d.s);
    if (frameSize > 0) {
      code.subq("$" + frameSize, "%rsp");
    }

    // Constructor body
    d.s.accept(this);

    // Add default return if not explicitly returned
    code.leave();
    code.ret();
  }

  @Override
  public void visit(TDmethod d) {
    if (d.m == null) {
      System.err.println("Error: TDmethod.m is null for method declaration in class " + currentClass.name);
      System.exit(1);
    }
    // If d.m is main and class name is Main, generate main function
    if (d.m.name.equals("main") && currentClass.name.equals("Main")) {
      code.globel(".global main");
      code.label("main");
    }
    currentMethod = d.m;

    // Generate method label
    code.label(currentClass.name + "_" + currentMethod.name);

    // Function prologue
    code.pushq("%rbp");
    code.movq("%rsp", "%rbp");

    // Allocate space for local variables
    int frameSize = computeLocalVarOffsets(currentMethod, d.s);

    if (frameSize > 0) {
      code.subq("$" + frameSize, "%rsp");
    }
    // Method body
    d.s.accept(this);
    if (d.m.name.equals("main") && currentClass.name.equals("Main")) {
      code.movq(0, "%rax");
    }

    // Add default return if not explicitly returned
    if (currentMethod.type instanceof TTvoid) {
      code.leave();
      code.ret();
    }
  }




  // Helper class: Local variable collector
  private class LocalVarVisitor implements TVisitor {
    Set<Variable> localVars = new HashSet<>();

    @Override
    public void visit(TSvar s) {
      localVars.add(s.v);
      if( s.e != null) {
        s.e.accept(this);
      }
    }

    // Default implementation for other visitor methods, recursively traversing the AST
    @Override public void visit(TTvoid t) {}
    @Override public void visit(TTnull t) {}
    @Override public void visit(TTboolean t) {}
    @Override public void visit(TTint t) {}
    @Override public void visit(TTclass t) {}
    @Override public void visit(TEcst e) {}
    @Override public void visit(TEbinop e) {
      e.e1.accept(this);
      e.e2.accept(this);
    }
    @Override public void visit(TEunop e) {
      e.e.accept(this);
    }
    @Override public void visit(TEthis e) {}
    @Override public void visit(TEnull e) {}
    @Override public void visit(TEvar e) {}
    @Override public void visit(TEassignVar e) {
      e.e.accept(this);
    }
    @Override public void visit(TEattr e) {
      e.e.accept(this);
    }
    @Override public void visit(TEassignAttr e) {
      e.e1.accept(this);
      e.e2.accept(this);
    }
    @Override public void visit(TEnew e) {
      for (TExpr expr : e.l) {
        expr.accept(this);
      }
    }
    @Override public void visit(TEcall e) {
      e.e.accept(this);
      for (TExpr expr : e.l) {
        expr.accept(this);
      }
    }
    @Override public void visit(TEcast e) {
      e.e.accept(this);
    }
    @Override public void visit(TEinstanceof e) {
      e.e.accept(this);
    }
    @Override public void visit(TEprint e) {
      e.e.accept(this);
    }
    @Override public void visit(TSexpr s) {
      if (s.e != null) {
        s.e.accept(this);
      }
    }
    @Override public void visit(TSif s) {
      s.e.accept(this);
      s.s1.accept(this);
      if (s.s2 != null) s.s2.accept(this);
    }
    @Override public void visit(TSreturn s) {
      if (s.e != null) s.e.accept(this);
    }
    @Override public void visit(TSblock s) {
      for (TStmt stmt : s.l) {
        stmt.accept(this);
      }
    }
    @Override public void visit(TSfor s) {
      if (s.s1 != null) s.s1.accept(this);
      if (s.e != null) s.e.accept(this);
      s.s2.accept(this);
      if (s.s3 != null) s.s3.accept(this);
    }
    @Override public void visit(TDconstructor d) {}
    @Override public void visit(TDmethod d) {}
  }
}