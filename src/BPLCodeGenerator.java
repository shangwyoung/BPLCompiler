import java.util.HashMap;
import java.util.ArrayList;

public class BPLCodeGenerator {
    private TreeNode root;
    private int labelNum;
    private HashMap<String, String> strMap;
    private String fp = "%rbx";
    private String sp = "%rsp";

    public BPLCodeGenerator(String inputFileName) throws BPLParserException, BPLTypeCheckerException, BPLCodeGeneratorException {
        BPLTypeChecker typeChecker = new BPLTypeChecker(inputFileName, false);
        typeChecker.runTypeChecker();
        this.root = typeChecker.getRoot();
        this.labelNum = 0;
        this.strMap = new HashMap<String, String>();
    }

    public TreeNode getRoot() {
        return this.root;
    }

    public void generate() throws BPLCodeGeneratorException, BPLTypeCheckerException{
        findDepths();
        //printTree(root, 0);
        header();
    }

    private void findDepths() {
        TreeNode declist = root.getChildren().get(0);
        while (declist.getKind() != TreeNodeKind.EMPTY) {
            TreeNode dec = declist.getChildren().get(1).getChildren().get(0);
            if (dec.getKind() == TreeNodeKind.VAR_DEC || dec.getKind() == TreeNodeKind.POINTER_VAR_DEC || dec.getKind() == TreeNodeKind.ARRAY_VAR_DEC) {
                dec.setDepth(0);
            } else {
                dec.setDepth(0);
                TreeNode paramList = dec.getChildren().get(2).getChildren().get(0);
                if (paramList.getKind() != TreeNodeKind.EMPTY && paramList.getKind() != TreeNodeKind.VOID) {
                    findDepthsParams(paramList, 1, 0);
                }
                int pos = findDepthsCompoundStmt(dec.getChildren().get(3), 2, 0);
                dec.setPosition(pos-1);
            }
            declist = declist.getChildren().get(0);
        }
    }

    private int findDepthsCompoundStmt(TreeNode compoundStmt, int depth, int position) {
        int pos = position;
        for (TreeNode t : compoundStmt.getChildren()) {
            if (t.getKind() == TreeNodeKind.LOCAL_DECS) {
                pos = findDepthsLocalDec(t, depth, pos);
            } else if (t.getKind() == TreeNodeKind.STATEMENT_LIST) {
                findDepthsStatementList(t, depth, pos);
            }
        }
        compoundStmt.setPosition(pos-position-1);
        return pos;
    }

    private void findDepthsStatementList(TreeNode statementList, int depth, int position) {
        while (statementList.getKind() != TreeNodeKind.EMPTY) {
            TreeNode stmt = statementList.getChildren().get(1).getChildren().get(0);
            if (stmt.getKind() == TreeNodeKind.COMPOUND_STMT) {
                findDepthsCompoundStmt(stmt, depth+1, position);
            } else if (stmt.getKind() == TreeNodeKind.IF_STMT || stmt.getKind() == TreeNodeKind.WHILE_STMT) {
                for (TreeNode t : stmt.getChildren()) {
                    if (t.getKind() == TreeNodeKind.STATEMENT) {
                        TreeNode s = t.getChildren().get(0);
                        if (s.getKind() == TreeNodeKind.COMPOUND_STMT) {
                            findDepthsCompoundStmt(s, depth+1, position);
                        }
                    }
                }
            }
            statementList = statementList.getChildren().get(0);
        }
    }

    private int findDepthsLocalDec(TreeNode localDecs, int depth, int position) {
        while (localDecs.getKind() != TreeNodeKind.EMPTY) {
            TreeNode varDec = localDecs.getChildren().get(1);
            varDec.setDepth(depth);
            if (varDec.getKind() == TreeNodeKind.ARRAY_VAR_DEC) {
                int arraypos = Integer.parseInt(varDec.getChildren().get(2).getValue())-1;
                varDec.setPosition(position + arraypos);
                position += arraypos + 1;
            } else {
                varDec.setPosition(position++);
            }
            localDecs = localDecs.getChildren().get(0);
        }
        return position;
    }

    private void findDepthsParams(TreeNode paramList, int depth, int position) {
        while (paramList.getKind() != TreeNodeKind.EMPTY) {
            TreeNode param = paramList.getChildren().get(1);
            param.setDepth(depth);
            param.setPosition(position++);
            paramList = paramList.getChildren().get(0);
        }
    }

    private void header() throws BPLCodeGeneratorException, BPLTypeCheckerException {
        genCodeGlobalDecs(root.getChildren().get(0));
    }

    private void genCodeGlobalDecs(TreeNode declist) throws BPLCodeGeneratorException, BPLTypeCheckerException {
        System.out.printf("%s%10s%n", ".section", ".rodata");
        System.out.printf("%s%n", ".WriteIntString: .string \"%d \"");
        System.out.printf("%s%n", ".WritelnString: .string \"\\n\"");
        System.out.printf("%s%n", ".WriteStringString: .string \"%s \"");
        System.out.printf("%s%n", ".ReadIntString: .string \"%d\"");
        ArrayList<TreeNode> funDecs = new ArrayList<TreeNode>();
        TreeNode main = null;
        while (declist.getKind() != TreeNodeKind.EMPTY) {
            TreeNode dec = declist.getChildren().get(1).getChildren().get(0);
            if (dec.getKind() == TreeNodeKind.FUN_DEC) {
                if (dec.getChildren().get(1).getValue().equals("main")) {
                    main = dec;
                }
                funDecs.add(dec);
            } else if (dec.getKind() == TreeNodeKind.ARRAY_VAR_DEC) {
                String id = dec.getChildren().get(1).getValue();
                String arrayLen = dec.getChildren().get(2).getValue();
                System.out.printf("%s%n", ".comm " + id + ", " + 8* Integer.parseInt(arrayLen) + ", 32");
            } else {
                String id = dec.getChildren().get(1).getValue();
                System.out.printf("%s%n", ".comm " + id + ", 8, 32");
            }
            declist = declist.getChildren().get(0);
        }
        genCodeStringLiterals(root);
        System.out.printf("%s%n", ".text");

        if (main == null) {
            throw new BPLCodeGeneratorException("no main function declared");
        } else {
            System.out.printf("%s%n%n", ".global main ");
        }

        for (TreeNode f : funDecs) {
            genCodeFunction(f);
        }
    }

    private void genCodeFunction(TreeNode fun) throws BPLTypeCheckerException {
        String id = fun.getChildren().get(1).getValue();
        System.out.printf("%s%n", id + ":");

        // move stack pointer to frame pointer
        genRegReg("movq", sp, fp, "setup fp");

        // genCodeStatement, and allocate local variables
        genCodeCompStmt(fun.getChildren().get(3));

        // deallocate any remaining local variables
        genRegReg("movq", "%rbx", "%rsp", "deallocate any remaining local variables");

        // return
        gen("ret", "return from the function");
    }

    private void genCodeCompStmt(TreeNode compStmt) throws BPLTypeCheckerException {
        // allocate local variables
        int localVarOffset = 8*(compStmt.getPosition()+1);
        if (localVarOffset > 0) {
            genRegReg("sub", "$"+localVarOffset, "%rsp", "allocate local variables");
        }

        for (TreeNode t : compStmt.getChildren()) {
            if (t.getKind() == TreeNodeKind.STATEMENT_LIST) {
                genCodeStatementList(t);
            }
        }

        // deallocate temporary variables
        if (localVarOffset > 0) {
            genRegReg("add", "$"+localVarOffset, "%rsp", "deallocate local variables");
        }
    }

    private void genCodeStatementList(TreeNode stmtList) throws BPLTypeCheckerException {
        while (stmtList.getKind() != TreeNodeKind.EMPTY) {
            TreeNode stmt = stmtList.getChildren().get(1);
            genCodeStatement(stmt);
            stmtList = stmtList.getChildren().get(0);
        }
    }

    private void genCodeStatement(TreeNode statement) throws BPLTypeCheckerException {
        TreeNode stmt = statement.getChildren().get(0);
        if (stmt.getKind() == TreeNodeKind.EXPRESSION_STMT) {
            if (stmt.getChildren().size() > 0) {
                genCodeExpression(stmt.getChildren().get(0));
            }
        } else if (stmt.getKind() == TreeNodeKind.COMPOUND_STMT) {
            genCodeCompStmt(stmt);
        } else if (stmt.getKind() == TreeNodeKind.IF_STMT) {
            genCodeIfStmt(stmt);
        } else if (stmt.getKind() == TreeNodeKind.WHILE_STMT) {
            genCodeWhileStmt(stmt);
        } else if (stmt.getKind() == TreeNodeKind.RETURN_STMT) {
            genCodeReturn(stmt);
        } else if (stmt.getKind() == TreeNodeKind.WRITE_STMT) {
            genCodeWrite(stmt);
        }
    }

    private void genCodeIfStmt(TreeNode ifStmt) throws BPLTypeCheckerException {
        TreeNode exp = ifStmt.getChildren().get(0);
        TreeNode stmt1 = null;
        TreeNode stmt2 = null;

        if (ifStmt.getChildren().size() > 1) {
            stmt1 = ifStmt.getChildren().get(1);
        }

        if (ifStmt.getChildren().size() > 2) {
            stmt2 = ifStmt.getChildren().get(2);
        }

        String label1 = ".L" + nextLabelNum();
        String label2 = ".L" + nextLabelNum();
        String label3 = ".L" + nextLabelNum();

        System.out.printf("%s:%n", label1);
        genCodeExpression(exp);
        genRegReg("cmpl", "$0", "%eax", "Test if condition is False");
        genReg("je", label2, "Jump to " + label2 + " if False");

        if (stmt1 != null) {
            genCodeStatement(stmt1);
        }
        genReg("jmp", label3, "Jump to " + label3 + " after True Statement");

        System.out.printf("%s:%n", label2);
        if (stmt2 != null) {
            genCodeStatement(stmt2);
        }

        System.out.printf("%s:%n", label3);

    }

    private void genCodeWhileStmt(TreeNode whileStmt) throws BPLTypeCheckerException{
        TreeNode exp = whileStmt.getChildren().get(0);
        TreeNode stmt = null;

        if (whileStmt.getChildren().size() > 1) {
            stmt = whileStmt.getChildren().get(1);
        }
        String label1 = ".L" + nextLabelNum();
        String label2 = ".L" + nextLabelNum();

        System.out.printf("%s:%n", label1);
        genCodeExpression(exp);
        genRegReg("cmpl", "$0", "%eax", "Test if condition is False");
        genReg("je", label2, "Jump to " + label2 + " if False");
        if (stmt != null) {
            genCodeStatement(stmt);
        }
        genReg("jmp", label1, "Jump back to " + label1);
        System.out.printf("%s:%n", label2);
    }

    private void genCodeReturn(TreeNode retStmt) throws BPLTypeCheckerException{
        if (retStmt.getChildren().size() > 0) {
            genCodeExpression(retStmt.getChildren().get(0));
        }
    }

    private void genCodeWrite(TreeNode writeStmt) throws BPLTypeCheckerException {
        if (writeStmt.getChildren().size() == 0) {
            genCodeWriteln();
        } else {
            genCodeWriteExp(writeStmt.getChildren().get(0));
        }
    }

    private void genCodeWriteExp(TreeNode exp) throws BPLTypeCheckerException {
        genCodeExpression(exp);
        if (exp.getType() == Type.INT) {
            genRegReg("movl", "%eax", "%esi", "move value to print");
            genRegReg("movq", "$.WriteIntString", "%rdi", "set printf string");
            genRegReg("movl", "$0", "%eax", "clear return value");
            call("printf");
        } else if (exp.getType() == Type.STRING) {
            genRegReg("movq", "%rax", "%rsi", "setting up string to print");
            genRegReg("movq", "$.WriteStringString", "%rdi", "set printf string");
            genRegReg("movl", "$0", "%eax", "clear return value");
            call("printf");
        }
    }

    private void genCodeExpression(TreeNode exp) throws BPLTypeCheckerException {
        if (exp.getChildren().get(0).getKind() == TreeNodeKind.ASSIGN_EXP) {
            genCodeAssignExp(exp.getChildren().get(0));
        } else {
            genCodeCompExp(exp.getChildren().get(0));
        }
    }

    private void genCodeAssignExp(TreeNode assignExp) throws BPLTypeCheckerException {
        TreeNode var = assignExp.getChildren().get(0);
        TreeNode exp = assignExp.getChildren().get(1);
        genCodeExpression(exp);
        genCodeVar(var);
    }

    private void genCodeVar(TreeNode var) throws BPLTypeCheckerException {
        TreeNode id = var.getChildren().get(0);
        TreeNode varDec = id.getDec();
        if (var.getKind() == TreeNodeKind.ARRAY_VAR) {
            genReg("push", "%rax", "push right hand value on stack");
            TreeNode exp = var.getChildren().get(1);
            genCodeExpression(exp);
            genRegReg("imul", "$8", "%rax", "calculating offset");
            genReg("push", "%rax", "push array offset on stack");
            genCodeID(id);
            genReg("pop", "%rdi", "retrieving offset from stack");
            genRegReg("addq", "%rax", "%rdi", "calculating element address");
            genReg("pop", "%rax", "retrieve right hand value from stack");
            genRegReg("movq", "%rax", "0(%rdi)", "assigning array value");
        } else if (var.getKind() == TreeNodeKind.POINTER_VAR) {
          genReg("push", "%rax", "push right hand value on stack");
          genCodeID(id);
          genReg("pop", "%rdi", "retrieving offset from stack");
          genRegReg("movq", "%rdi", "0(%rax)", "assigning pointer value");

        } else {
            if (varDec.getDepth() == 0) {
                genRegReg("mov", "%rax", id.getValue() , "perform global variable assignment");
            } else if (varDec.getDepth() == 1) {
                int offset = 16 + (8 * varDec.getPosition());
                genRegReg("mov", "%rax", offset+"(%rbx)" , "perform param variable assignment");
            } else {
                int offset = -8 + (-8 * varDec.getPosition());
                genRegReg("mov", "%rax", offset+"(%rbx)" , "perform local variable assignment");
            }
        }
    }

    private void genCodeCompExp(TreeNode compExp) throws BPLTypeCheckerException {
        if (compExp.getChildren().size() == 1) {
            genCodeE(compExp.getChildren().get(0));
        } else {
            genCodeE(compExp.getChildren().get(0));
            genReg("push", "%rax", "saving left operand on stack");
            genCodeE(compExp.getChildren().get(2));
            genRegReg("cmpl", "%eax", "0(%rsp)", "perform comparison");
            genCodeRelop(compExp.getChildren().get(1));
        }
    }

    private void genCodeRelop(TreeNode relop) {
        String op = relop.getChildren().get(0).getValue();
        String lab1 = ".L" + nextLabelNum();
        String lab2 = ".L" + nextLabelNum();
        if (op.equals("<=")) {
            genReg("jg", lab2, "");
        } else if (op.equals("<")) {
            genReg("jge", lab2, "");
        } else if (op.equals("==")) {
            genReg("jne", lab2, "");
        } else if (op.equals("!=")) {
            genReg("je", lab2, "");
        } else if (op.equals(">")) {
            genReg("jle", lab2, "");
        } else if (op.equals(">=")) {
            genReg("jl", lab2, "");
        }
        genRegReg("movl", "$1", "%eax", "comparison evals to true");
        genReg("jmp", lab1, "");
        System.out.printf("%s:%n", lab2);
        genRegReg("movl", "$0", "%eax", "comparison evals to false");
        System.out.printf("%s:%n", lab1);
        genRegReg("addq", "$8", "%rsp", "popping value on the stack");
    }

    private void genCodeE(TreeNode E) throws BPLTypeCheckerException {
        if (E.getChildren().size() == 1) {
            genCodeT(E.getChildren().get(0));
        } else {
            genCodeT(E.getChildren().get(2));
            genReg("push", "%rax", "saving left operand on stack");
            genCodeE(E.getChildren().get(0));
            genCodeAddop(E.getChildren().get(1));
        }
    }

    private void genCodeAddop(TreeNode op) {
        if (op.getChildren().get(0).getValue().equals("+")) {
            genRegReg("addl", "0(%rsp)", "%eax", "performing addition");
        } else {
            genRegReg("subl", "0(%rsp)", "%eax", "performing subtraction");
        }
        genRegReg("addq", "$8", "%rsp", "popping value on the stack");
    }

    private void genCodeT(TreeNode T) throws BPLTypeCheckerException {
        if (T.getChildren().size() == 1) {
            genCodeF(T.getChildren().get(0));
        } else {
            String op = T.getChildren().get(1).getChildren().get(0).getValue();
            if (op.equals("/") || op.equals("%")) {
                genCodeF(T.getChildren().get(2));
                genRegReg("movl", "%eax", "%ebp", "put divisor into ebp");
                genCodeT(T.getChildren().get(0));
                genRegReg("movl", "%eax", "%eax", "put dividend into eax");
                gen("cltq", "sign-extend to all of rax");
                gen("cqto", "sign-extend to rdx");
                genReg("idivl", "%ebp", "perform division");
                if (op.equals("%")) {
                    genRegReg("movl", "%edx", "%eax", "put remainder into eax");
                }
            } else {
                genCodeT(T.getChildren().get(0));
                genReg("push", "%rax", "saving left operand on stack");
                genCodeF(T.getChildren().get(2));
                genRegReg("imul", "0(%rsp)", "%eax", "performing multiplication");
                genRegReg("addq", "$8", "%rsp", "popping value on the stack");
            }
        }
    }

    private void genCodeF(TreeNode F) throws BPLTypeCheckerException {
        if (F.getKind() == TreeNodeKind.NEG_F) {
            genCodeF(F.getChildren().get(0));
            genReg("neg", "%eax", "negating the value");
        } else {
            if (F.getKind() == TreeNodeKind.ADDRESS_F || F.getKind() == TreeNodeKind.DEREF_F) {
                genCodeFactor(F);
            } else {
                genCodeFactor(F.getChildren().get(0));
            }
        }
    }

    private void genCodeFactor(TreeNode factor) throws BPLTypeCheckerException {
        TreeNode fac = factor.getChildren().get(0);
        if (factor.getKind() == TreeNodeKind.ARRAY_FACTOR) {
            genCodeID(factor.getChildren().get(0));
            genReg("push", "%rax", "Save the starting address of array on stack");
            genCodeExpression(factor.getChildren().get(1));
            genRegReg("imul", "$8", "%rax", "multiply index by 8");
            genReg("pop", "%rsi", "Retrieving base address of array");
            genRegReg("add", "%rax", "%rsi", "address of desired array index");
            genRegReg("movl", "0(%rsi)", "%eax", "obtaining array index value");
        } else if (factor.getKind() == TreeNodeKind.ADDRESS_F) {
            TreeNode f = factor.getChildren().get(0).getChildren().get(0);
            TreeNode f2 = factor.getChildren().get(0);
            if (f2.getKind() != TreeNodeKind.ARRAY_FACTOR && f.getKind() == TreeNodeKind.ID) {
                TreeNode dec = f.getDec();
                if (dec.getDepth() == 0) {
                    String label = dec.getValue();
                    genRegReg("leaq", label, "%rax", "putting global variable reference into ac");
                } else if (dec.getDepth() == 1) {
                    int offset = 16 + 8*(dec.getPosition());
                    genRegReg("leaq", offset+"(%rbx)", "%rax", "putting param variable reference into ac");
                } else {
                    int offset = -8 + (-8*dec.getPosition());
                    genRegReg("leaq", offset+"(%rbx)", "%rax", "putting local variable reference into ac");
                }
            } else if (f2.getKind() == TreeNodeKind.ARRAY_FACTOR) {
              TreeNode dec = f.getDec();
              TreeNode exp = f2.getChildren().get(1);
              genCodeExpression(exp);
              genRegReg("imul", "$8", "%rax", "multiply index by 8");

              if (dec.getDepth() == 0) {
                String label = f.getValue();
                genRegReg("leaq", label, "%rdi", "putting global array variable reference into ac");
              } else if (dec.getDepth() == 1) {
                  int offset = 16 + 8*(dec.getPosition());
                  genRegReg("movq", offset+"(%rbx)", "%rdi", "putting param array variable value into ac");
              } else {
                  int offset = -8 + (-8*dec.getPosition());
                  genRegReg("leaq", offset+"(%rbx)", "%rdi", "putting local array variable value into ac");
              }
              genRegReg("addq", "%rdi", "%rax", "adding offset to base address of array ");

            } else {
                throw new BPLTypeCheckerException("Invalid type to reference");
            }

        } else if (factor.getKind() == TreeNodeKind.DEREF_F) {
            genCodeFactor(factor.getChildren().get(0));
            genRegReg("movq", "0(%rax)", "%rax", "dereference value");
        } else if (fac.getKind() == TreeNodeKind.ID) {
            genCodeID(fac);
        } else if (fac.getKind() == TreeNodeKind.EXPRESSION) {
            genCodeExpression(fac);

        } else if (fac.getKind() == TreeNodeKind.FUN_CALL) {
            int numArgs = genCodeArgs(fac.getChildren().get(1).getChildren().get(0));
            genReg("push", "%rbx", "pushing the frame pointer");
            call(fac.getChildren().get(0).getValue());
            genReg("pop", "%rbx", "retrieving the frame pointer");
            genRegReg("add", "$"+(numArgs*8), "%rsp", "removing args from the stack");

        } else if (fac.getKind() == TreeNodeKind.NUM) {
            genCodeNum(fac);
        } else if (fac.getKind() == TreeNodeKind.STR) {
            genCodeStr(fac);
        } else if (fac.getKind() == TreeNodeKind.READ) {

        }
    }

    private int genCodeArgs(TreeNode argList) throws BPLTypeCheckerException{
        int numArgs = 0;
        ArrayList<TreeNode> args = new ArrayList<TreeNode>();
        while (argList.getKind() != TreeNodeKind.EMPTY) {
            numArgs++;
            TreeNode exp = argList.getChildren().get(1);
            args.add(exp);
            argList = argList.getChildren().get(0);
        }

        for (int i = args.size()-1; i >= 0; i--) {
            TreeNode exp = args.get(i);
            genCodeExpression(exp);
            genReg("push", "%rax", "pushing argument");
        }

        return numArgs;
    }

    private void genCodeID(TreeNode id) {
        TreeNode dec = id.getDec();
        String label = id.getValue();
        if (dec.getDepth() == 0) {
            if (dec.getKind() == TreeNodeKind.ARRAY_VAR_DEC) {
                genRegReg("leaq", label, "%rax", "putting global array base address into ac");
            } else {
                genRegReg("movq", label, "%rax", "putting global variable value into ac");
            }

        } else if (dec.getDepth() == 1) {
            int offset = 16 + 8*(dec.getPosition());
            genRegReg("movq", offset+"(%rbx)", "%rax", "putting param variable value into ac");
        } else {
            int offset = -8 + (-8*dec.getPosition());
            if (dec.getKind() == TreeNodeKind.ARRAY_VAR_DEC) {
                genRegReg("leaq", offset+"(%rbx)", "%rax", "putting local array base address into ac");
            } else {
                genRegReg("movq", offset+"(%rbx)", "%rax", "putting local variable value into ac");
            }
        }
    }

    private void genCodeStr(TreeNode str) {
        String string = str.getValue();
        String label = strMap.get(string);
        genRegReg("movq", "$"+label, "%rax", "putting string value" + " into ac");

    }

    private void genCodeNum(TreeNode num) {
        String numValue = num.getValue();
        genRegReg("movq", "$"+numValue, "%rax", "putting " + numValue + " into ac");
    }

    private void genCodeWriteln() {
        genRegReg("movl", "$0", "%eax", "Set eax to 0");
        genRegReg("movq", "$.WritelnString", "%rdi", "");
        call("printf");
    }

    private void call(String fun) {
        System.out.printf("\t %s %10s%n", "call " + fun, "");
    }

    private void genRegReg(String opcode, String r1, String r2, String comment) {
        System.out.printf("\t %4s %4s, %4s %10s #%s%n", opcode, r1, r2, "", comment);
    }

    private void genReg(String opcode, String r, String comment) {
        System.out.printf("\t %4s %4s %10s #%s%n", opcode, r, "", comment);
    }

    private void gen(String opcode, String comment) {
        System.out.printf("\t %4s %10s #%s%n", opcode, "", comment);
    }

    private void genCodeStringLiterals(TreeNode root) {
        if (root.getKind() == TreeNodeKind.STR && root.getValue() != null) {
            String label = ".S" + nextLabelNum();
            strMap.put(root.getValue(), label);
            System.out.printf("%s%n", label + ": .string \"" + root.getValue() + "\"");
        }
        for (TreeNode child : root.getChildren()) {
            genCodeStringLiterals(child);
        }
    }

    private int nextLabelNum() {
        return labelNum++;
    }

    public static void main(String[] args) {
    //BPLCodeGenerator codeGenerator = new BPLCodeGenerator("sample_programs/P1.bpl");
        try {
            BPLCodeGenerator codeGenerator = new BPLCodeGenerator(args[0]);
            codeGenerator.generate();
        } catch(Exception e) {
            System.err.println(e);
            System.exit(1);
        }
        /*catch (BPLCodeGeneratorException e) {
            e.printStackTrace();
            System.exit(1);
        } catch (BPLTypeCheckerException e) {
            e.printStackTrace();
            System.exit(1);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        } */


        //printTree(codeGenerator.getRoot(), 0);

    }

    public static void printTree(TreeNode root, int spaces) {
        for (int i = 0; i < spaces; i++) {
            System.out.print("    ");
        }
        System.out.print(root);
        System.out.println("{");

        for (TreeNode child : root.getChildren()) {
            printTree(child, spaces+1);
        }

        for (int i = 0; i < spaces; i++) {
            System.out.print("    ");
        }
        System.out.println("}");
    }
}
