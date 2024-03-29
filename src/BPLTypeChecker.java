import java.util.HashMap;
import java.util.LinkedList;

public class BPLTypeChecker {
  private boolean debug;
  private HashMap<String, TreeNode> globalDecs;
  private BPLParser parser;
  private TreeNode root;

  public BPLTypeChecker(String inputFileName, boolean debug) throws BPLTypeCheckerException, BPLParserException {
    this.debug = debug;
    this.globalDecs = new HashMap<String, TreeNode>();
    parser = new BPLParser(inputFileName);
    root = null;

    root = parser.parse();
  }

  public void runTypeChecker() throws BPLTypeCheckerException {
    if (root != null) {
      findReferences(root.getChildren().get(0));
    }
  }

  public TreeNode getRoot() {
    return root;
  }

  private void findReferences(TreeNode declist) throws BPLTypeCheckerException {
    while (!isEmpty(declist)) {
      TreeNode dec = getDec(declist);
      if (isVarDec(dec)) {
        addVarDecToGlobalDecs(dec);
      } else {
        addFunDecToGlobalDecs(dec);
        findReferencesFunDec(dec);
      }
      declist = getDecList(declist);
    }
  }

  private void findReferencesFunDec(TreeNode dec) throws BPLTypeCheckerException {
    TreeNode funDec = dec.getChildren().get(0);
    Type funRtnType = findFunRtnType(funDec);
    LinkedList<TreeNode> localDecs = new LinkedList<TreeNode>();
    findReferencesParams(funDec, localDecs);
    TreeNode compStmt = funDec.getChildren().get(3);
    findReferencesCompoundStmt(compStmt, localDecs, funRtnType);
  }

  private Type findFunRtnType(TreeNode funDec) {
    TreeNodeKind type = funDec.getChildren().get(0).getChildren().get(0).getKind();
    Type t = Type.VOID;
    if (type == TreeNodeKind.INT) {
      t = Type.INT;
    } else if (type == TreeNodeKind.STR) {
      t = Type.STRING;
    }
    return t;
  }

  private void findReferencesCompoundStmt(TreeNode compStmt, LinkedList<TreeNode> localDecs, Type funRtnType) throws BPLTypeCheckerException {
    for (TreeNode t : compStmt.getChildren()) {
      if (t.getKind() == TreeNodeKind.LOCAL_DECS) {
        findReferencesLocalDecs(t, localDecs);
      } else if (t.getKind() == TreeNodeKind.STATEMENT_LIST) {
        findReferencesStmtList(t, localDecs, funRtnType);
      }
    }
  }

  private void findReferencesLocalDecs(TreeNode local_decs, LinkedList<TreeNode> localDecs) {
    while (!isEmpty(local_decs)) {
      TreeNode varDec = getDec(local_decs);
      addVarDecToLocalDecs(varDec, localDecs);
      local_decs = getDecList(local_decs);
    }
  }

  private void findReferencesStmtList(TreeNode stmtList, LinkedList<TreeNode> localDecs, Type funRtnType) throws BPLTypeCheckerException {
    while (!isEmpty(stmtList)) {
      TreeNode stmt = getDec(stmtList);
      findReferencesStmt(stmt, localDecs, funRtnType);
      stmtList = getDecList(stmtList);
    }
  }

  private void findReferencesStmt(TreeNode statement, LinkedList<TreeNode> localDecs, Type funRtnType) throws BPLTypeCheckerException {
    Type stmtType = Type.NONE;
    TreeNode stmt = statement.getChildren().get(0);
    if (stmt.getKind() == TreeNodeKind.EXPRESSION_STMT) {
      findReferencesExpStmt(stmt, localDecs);
    } else if (stmt.getKind() == TreeNodeKind.COMPOUND_STMT) {
      findReferencesCompoundStmt(stmt, localDecs, funRtnType);
    } else if (stmt.getKind() == TreeNodeKind.IF_STMT) {
      findReferencesIfStmt(stmt, localDecs, funRtnType);
    } else if (stmt.getKind() == TreeNodeKind.WHILE_STMT) {
      findReferencesWhileStmt(stmt, localDecs, funRtnType);
    } else if (stmt.getKind() == TreeNodeKind.RETURN_STMT) {
      stmtType = findReferencesReturnStmt(stmt, localDecs);
      if (debug) {
        System.out.println("Return statement assigned type " + stmtType + " on line " + stmt.getLine());
      }
      assertType(stmtType, funRtnType, statement.getLine());
    } else if (stmt.getKind() == TreeNodeKind.WRITE_STMT) {
      findReferencesWriteStmt(stmt, localDecs);
    }
  }

  private void findReferencesExpStmt(TreeNode expStmt, LinkedList<TreeNode> localDecs) throws BPLTypeCheckerException {
    if (expStmt.getChildren().size() == 0) {
      return;
    }
    findReferencesExpression(expStmt.getChildren().get(0), localDecs);
  }

  private void findReferencesIfStmt(TreeNode ifStmt, LinkedList<TreeNode> localDecs, Type funRtnType) throws BPLTypeCheckerException {
    Type conditionType = findReferencesExpression(ifStmt.getChildren().get(0), localDecs);
    if (debug) {
      System.out.println("If Condition assigned type " + conditionType + " on line " + ifStmt.getLine());
    }
    assertType(conditionType, Type.INT, ifStmt.getLine());
    findReferencesStmt(ifStmt.getChildren().get(1), localDecs, funRtnType);
    if (ifStmt.getChildren().size() > 2) {
      findReferencesStmt(ifStmt.getChildren().get(2), localDecs, funRtnType);
    }
  }

  private void findReferencesWhileStmt(TreeNode whileStmt, LinkedList<TreeNode> localDecs, Type funRtnType) throws BPLTypeCheckerException {
    Type conditionType = findReferencesExpression(whileStmt.getChildren().get(0), localDecs);
    if (debug) {
      System.out.println("While Condition assigned type " + conditionType + " on line " + whileStmt.getLine());
    }
    assertType(conditionType, Type.INT, whileStmt.getLine());
    findReferencesStmt(whileStmt.getChildren().get(1), localDecs, funRtnType);
  }

  private Type findReferencesReturnStmt(TreeNode returnStmt, LinkedList<TreeNode> localDecs) throws BPLTypeCheckerException {
    if (returnStmt.getChildren().size() == 0) {
      return Type.VOID;
    }
    Type rtnType = findReferencesExpression(returnStmt.getChildren().get(0), localDecs);
    return rtnType;
  }

  private void findReferencesWriteStmt(TreeNode writeStmt, LinkedList<TreeNode> localDecs) throws BPLTypeCheckerException {
    if (writeStmt.getChildren().size() == 0) {
      return;
    }
    Type expType = findReferencesExpression(writeStmt.getChildren().get(0), localDecs);
    Type[] expected = {Type.INT, Type.STRING};
    assertType(expType, expected, writeStmt.getLine());
  }

  private Type findReferencesExpression(TreeNode exp, LinkedList<TreeNode> localDecs) throws BPLTypeCheckerException {
    Type expressionType = Type.NONE;
    if (exp.getChildren().get(0).getKind() == TreeNodeKind.ASSIGN_EXP) {
      expressionType = findReferencesAssignExp(exp.getChildren().get(0), localDecs);
    } else {
      expressionType = findReferencesCompExp(exp.getChildren().get(0), localDecs);
    }
    exp.setType(expressionType);

    return expressionType;
  }

  private Type findReferencesCompExp(TreeNode compExp, LinkedList<TreeNode> localDecs) throws BPLTypeCheckerException {
    Type compExpType = Type.NONE;
    if (compExp.getChildren().size() == 1) {
      compExpType = findReferencesE(compExp.getChildren().get(0), localDecs);
    } else {
      Type E1Type = findReferencesE(compExp.getChildren().get(0), localDecs);
      assertType(E1Type, Type.INT, compExp.getLine());
      Type E2Type = findReferencesE(compExp.getChildren().get(2), localDecs);
      assertType(E2Type, Type.INT, compExp.getLine());
      if (debug) {
        System.out.println(compExp.getChildren().get(1).getChildren().get(0).getValue() + " assigned Type " + Type.INT + " on line " + compExp.getLine());
      }
      compExpType = Type.INT;
    }
    return compExpType;
  }

  private Type findReferencesE(TreeNode E, LinkedList<TreeNode> localDecs) throws BPLTypeCheckerException {
    Type EType = Type.NONE;
    if (E.getChildren().size() == 1) {
      EType = findReferencesT(E.getChildren().get(0), localDecs);
    } else {
      Type E1 = findReferencesE(E.getChildren().get(0), localDecs);
      assertType(E1, Type.INT, E.getLine());
      Type T = findReferencesT(E.getChildren().get(2), localDecs);
      assertType(T, Type.INT, E.getLine());
      if (debug) {
        System.out.println(E.getChildren().get(1).getChildren().get(0).getValue() + " assigned Type " + Type.INT + " on line " + E.getLine());
      }
      EType = Type.INT;
    }
    return EType;
  }

  private Type findReferencesT(TreeNode T, LinkedList<TreeNode> localDecs) throws BPLTypeCheckerException {
    Type TType = Type.NONE;
    if (T.getChildren().size() == 1) {
      TType = findReferencesF(T.getChildren().get(0), localDecs);
    } else {
      Type T1 = findReferencesT(T.getChildren().get(0), localDecs);
      assertType(T1, Type.INT, T.getLine());
      Type F = findReferencesF(T.getChildren().get(2), localDecs);
      assertType(F, Type.INT, T.getLine());
      if (debug) {
        System.out.println(T.getChildren().get(1).getChildren().get(0).getValue() + " assigned Type " + Type.INT + " on line " + T.getLine());
      }
      TType = Type.INT;
    }
    return TType;
  }

  private Type findReferencesF(TreeNode F, LinkedList<TreeNode> localDecs) throws BPLTypeCheckerException {
    Type FType = Type.NONE;
    if (F.getKind() == TreeNodeKind.NEG_F) {
      Type F1 = findReferencesF(F.getChildren().get(0), localDecs);
      assertType(F1, Type.INT, F.getLine());
      FType = F1;
    } else {
      if (F.getKind() == TreeNodeKind.ADDRESS_F || F.getKind() == TreeNodeKind.DEREF_F) {
        FType = findReferencesFactor(F, localDecs);
      } else {
        FType = findReferencesFactor(F.getChildren().get(0), localDecs);
      }
    }
    return FType;
  }

  private Type findReferencesFactor(TreeNode factor, LinkedList<TreeNode> localDecs) throws BPLTypeCheckerException {
    Type factorType = Type.NONE;
    TreeNode fac = factor.getChildren().get(0);
    if (factor.getKind() == TreeNodeKind.ARRAY_FACTOR) {
      Type idType = findReferencesID(factor.getChildren().get(0), localDecs, factor.getChildren().get(0).getValue());
      Type[] expected = {Type.INT_ARRAY, Type.STRING_ARRAY};
      assertType(idType, expected, factor.getLine());
      Type expType = findReferencesExpression(factor.getChildren().get(1), localDecs);
      assertType(expType, Type.INT, factor.getLine());
      factorType = idType;
      if (idType == Type.INT_ARRAY) {
        factorType = Type.INT;
      } else if (idType == Type.STRING_ARRAY) {
        factorType = Type.STRING;
      }

      if (debug) {
        System.out.println(factor.getChildren().get(0).getValue() + "[<expression>]" + " assigned Type " + factorType + " on line " + factor.getLine());
      }


    } else if (factor.getKind() == TreeNodeKind.ADDRESS_F) {
      factorType = findReferencesFactor(fac, localDecs);
      Type[] expected = {Type.INT, Type.STRING};
      assertType(factorType, expected, factor.getLine());

      if (factorType == Type.INT) {
        factorType = Type.INT_ADDRESS;
      } else if (factorType == Type.STRING) {
        factorType = Type.STRING_ADDRESS;
      }
      factor.setValue(factor.getChildren().get(0).getValue());
      if (debug) {
        System.out.println("&" + factor.getValue() + " assigned Type " + factorType + " on line " + factor.getLine());
      }

    } else if (factor.getKind() == TreeNodeKind.DEREF_F) {
      factorType = findReferencesFactor(fac, localDecs);
      Type[] expected = {Type.INT_PTR, Type.STRING_PTR};
      assertType(factorType, expected, factor.getLine());
      if (factorType == Type.INT_PTR) {
        factorType = Type.INT;
      } else if (factorType == Type.STRING_PTR) {
        factorType = Type.STRING;
      }
      factor.setValue(factor.getChildren().get(0).getValue());
      if (debug) {
        System.out.println("*" + factor.getValue() + " assigned Type " + factorType + " on line " + factor.getLine());
      }

    } else if (fac.getKind() == TreeNodeKind.ID) {
      factorType = findReferencesID(fac, localDecs, fac.getValue());
      factor.setValue(fac.getValue());
    } else if (fac.getKind() == TreeNodeKind.EXPRESSION) {
      factorType = findReferencesExpression(fac, localDecs);
    } else if (fac.getKind() == TreeNodeKind.FUN_CALL) {
      Type funRtnType = findReferencesID(fac.getChildren().get(0), localDecs, fac.getChildren().get(0).getValue());
      findReferencesArgs(fac.getChildren().get(1), localDecs, fac.getChildren().get(0).getDec());
      factorType = funRtnType;
    } else if (fac.getKind() == TreeNodeKind.NUM) {
      factorType = Type.INT;
    } else if (fac.getKind() == TreeNodeKind.STR) {
      factorType = Type.STRING;
    } else if (fac.getKind() == TreeNodeKind.READ) {
      factorType = Type.INT;
    }
    return factorType;
  }

  private void findReferencesArgs(TreeNode args, LinkedList<TreeNode> localDecs, TreeNode funDec) throws BPLTypeCheckerException {
    TreeNode argList = args.getChildren().get(0);
    TreeNode params = funDec.getChildren().get(2);
    TreeNode paramList = null;
    Type paramType = Type.NONE;
    if (!areParams(params)) {
      paramList = new TreeNode(TreeNodeKind.EMPTY, -1, null);
    } else {
      paramList = params.getChildren().get(0);
    }

    while (!isEmpty(argList) && !isEmpty(paramList)) {
      TreeNode param = getParam(paramList);
      paramType = getParamType(param);
      TreeNode exp = argList.getChildren().get(1);
      Type expType = findReferencesExpression(exp, localDecs);
      assertType(expType, paramType, args.getLine());

      argList = argList.getChildren().get(0);
      paramList = getParamList(paramList);
    }

    if (!isEmpty(argList) || !isEmpty(paramList)) {
      throw new BPLTypeCheckerException("TypeChecker Error: Incorrect number of arguments to " + funDec.getChildren().get(1).getValue());
    }
  }

  private Type getParamType(TreeNode param) {
    TreeNodeKind type = param.getChildren().get(0).getChildren().get(0).getKind();
    Type paramType = Type.NONE;

    if (param.getKind() == TreeNodeKind.POINTER_PARAM) {
      if (type == TreeNodeKind.INT) {
        paramType = Type.INT_PTR;
      } else if (type == TreeNodeKind.STR) {
        paramType = Type.STRING_PTR;
      }
    } else if (param.getKind() == TreeNodeKind.ARRAY_PARAM) {
      if (type == TreeNodeKind.INT) {
        paramType = Type.INT_ARRAY;
      } else if (type == TreeNodeKind.STR) {
        paramType = Type.STRING_ARRAY;
      }
    } else {
      if (type == TreeNodeKind.INT) {
        paramType = Type.INT;
      } else if (type == TreeNodeKind.STR) {
        paramType = Type.STRING;
      }
    }
    return paramType;
  }

  private Type findReferencesAssignExp(TreeNode assignExp, LinkedList<TreeNode> localDecs) throws BPLTypeCheckerException {
    Type varType = findReferencesVar(assignExp.getChildren().get(0), localDecs);
    Type expType = findReferencesExpression(assignExp.getChildren().get(1), localDecs);

    if (varType == Type.INT_PTR) {
      assertType(expType, Type.INT_ADDRESS, assignExp.getLine());

    } else if (varType == Type.STRING_PTR) {
      assertType(expType, Type.STRING_ADDRESS, assignExp.getLine());
    } else {
      assertType(expType, varType, assignExp.getLine());
      assignExp.setType(expType);
    }
    return expType;
  }

  private Type findReferencesVar(TreeNode var, LinkedList<TreeNode> localDecs) throws BPLTypeCheckerException {
    Type varType = Type.NONE;
    TreeNode ID = var.getChildren().get(0);
    String id = ID.getValue();
    varType = findReferencesID(var, localDecs, id);
    ID.setDec(var.getDec());
    if (var.getKind() == TreeNodeKind.ARRAY_VAR) {
      Type expType = findReferencesExpression(var.getChildren().get(1), localDecs);
      assertType(expType, Type.INT, var.getLine());

      if (varType == Type.INT_ARRAY) {
        varType = Type.INT;
      } else if (varType == Type.STRING_ARRAY) {
        varType = Type.STRING;
      }

      if (debug) {
        System.out.println( id + "[<expression>]" + " assigned Type " + varType + " on line " + var.getLine());
      }

    } else if (var.getKind() == TreeNodeKind.POINTER_VAR) {
      if (varType == Type.INT_PTR) {
        varType = Type.INT;
      } else if (varType == Type.STRING_PTR) {
        varType = Type.STRING;
      }
      if (debug) {
        System.out.println("*" + id + " assigned Type " + varType + " on line " + var.getLine());
      }
    }
    var.setType(varType);
    return varType;
  }

  private Type findReferencesID(TreeNode var, LinkedList<TreeNode> localDecs, String id) throws BPLTypeCheckerException {
    Type varType = Type.NONE;
    TreeNode reference = findLocalReference(id, localDecs);
    if (reference == null) {
      reference = findGlobalReference(id);
    }
    if (reference == null) {
      throw new BPLTypeCheckerException("TypeChecker Error: Variable " + id + " referenced before assignment.");
    }
    var.setDec(reference);
    varType = findVarType(reference);
    if (debug) {
      System.out.println(var.getKind() + " " + id + " on line " + var.getLine() + " linked to declaration " + reference.getKind() + " on line " + reference.getLine());
      if (reference.getKind() == TreeNodeKind.FUN_DEC) {
        System.out.println("Function Call " + id + " assigned return Type " + varType + " on line " + var.getLine());
      } else {
        System.out.println(var.getKind() + " " + id + " assigned Type " + varType + " on line " + var.getLine());
      }
    }
    if (var.getKind() != TreeNodeKind.ID) {
      var.setValue(id);
    }
    return varType;
  }

  private Type findVarType(TreeNode reference) {
    Type refType = Type.NONE;
    if (reference.getKind() == TreeNodeKind.ARRAY_VAR_DEC) {
      if (reference.getChildren().get(0).getChildren().get(0).getKind() == TreeNodeKind.INT) {
        refType = Type.INT_ARRAY;
      } else if (reference.getChildren().get(0).getChildren().get(0).getKind() == TreeNodeKind.STR) {
        refType = Type.STRING_ARRAY;
      }
    } else if (reference.getKind() == TreeNodeKind.POINTER_VAR_DEC) {
      if (reference.getChildren().get(0).getChildren().get(0).getKind() == TreeNodeKind.INT) {
        refType = Type.INT_PTR;
      } else if (reference.getChildren().get(0).getChildren().get(0).getKind() == TreeNodeKind.STR) {
        refType = Type.STRING_PTR;
      }
    } else if (reference.getKind() == TreeNodeKind.FUN_DEC) {
      if (reference.getChildren().get(0).getChildren().get(0).getKind() == TreeNodeKind.INT) {
        refType = Type.INT;
      } else if (reference.getChildren().get(0).getChildren().get(0).getKind() == TreeNodeKind.STR) {
        refType = Type.STRING;
      } else if (reference.getChildren().get(0).getChildren().get(0).getKind() == TreeNodeKind.VOID) {
        refType = Type.VOID;
      }
    } else if (reference.getKind() == TreeNodeKind.ARRAY_PARAM) {
      if (reference.getChildren().get(0).getChildren().get(0).getKind() == TreeNodeKind.INT) {
        refType = Type.INT_ARRAY;
      } else if (reference.getChildren().get(0).getChildren().get(0).getKind() == TreeNodeKind.STR) {
        refType = Type.STRING_ARRAY;
      }
    } else if (reference.getKind() == TreeNodeKind.POINTER_PARAM) {
      if (reference.getChildren().get(0).getChildren().get(0).getKind() == TreeNodeKind.INT) {
        refType = Type.INT_PTR;
      } else if (reference.getChildren().get(0).getChildren().get(0).getKind() == TreeNodeKind.STR) {
        refType = Type.STRING_PTR;
      }
    } else {
      if (reference.getChildren().get(0).getChildren().get(0).getKind() == TreeNodeKind.INT) {
        refType = Type.INT;
      } else if (reference.getChildren().get(0).getChildren().get(0).getKind() == TreeNodeKind.STR) {
        refType = Type.STRING;
      }
    }
    return refType;
  }

  private TreeNode findLocalReference(String id, LinkedList<TreeNode> localDecs) {
    for (TreeNode t : localDecs) {
      if (getDecId(t).equals(id)) {
        return t;
      }
    }
    return null;
  }

  private TreeNode findGlobalReference(String id) {
    if (globalDecs.containsKey(id)) {
      return globalDecs.get(id);
    }
    return null;
  }

  private void addVarDecToLocalDecs(TreeNode varDec, LinkedList<TreeNode> localDecs) {
    localDecs.addFirst(varDec);

    if (debug) {
      System.out.println("Added " + varDec.getKind() + " " + getDecType(varDec) + " " + getDecId(varDec) + " to Local Declarations on line " + varDec.getLine());
    }
  }

  private void findReferencesParams(TreeNode funDec, LinkedList<TreeNode> localDecs) {
    TreeNode params = funDec.getChildren().get(2);

    if (!areParams(params)) {
      return;
    }

    TreeNode paramList = params.getChildren().get(0);
    while (!isEmpty(paramList)) {
      TreeNode param = getParam(paramList);
      addParamToLocalDec(param, localDecs);
      paramList = getParamList(paramList);
    }

  }

  private TreeNode getParamList(TreeNode paramList) {
    return paramList.getChildren().get(0);
  }

  private void addParamToLocalDec(TreeNode param, LinkedList<TreeNode> localDecs) {
    localDecs.addFirst(param);

    if (debug) {
      System.out.println("Added " + param.getKind() + " " + getParamTypeString(param) + " " + getParamId(param) + " to Local Declarations on line " + param.getLine());
    }
  }

  private String getParamId(TreeNode param) {
    return param.getChildren().get(1).getValue();
  }

  private String getParamTypeString(TreeNode param) {
    return param.getChildren().get(0).getChildren().get(0).getValue();
  }

  private TreeNode getParam(TreeNode paramList) {
    return paramList.getChildren().get(1);
  }

  private boolean areParams(TreeNode params) {
    TreeNode paramList = params.getChildren().get(0);
    if (paramList.getKind() == TreeNodeKind.EMPTY || paramList.getKind() == TreeNodeKind.VOID) {
      return false;
    }
    return true;
  }

  private void addFunDecToGlobalDecs(TreeNode dec) throws BPLTypeCheckerException {
    TreeNode funDec = dec.getChildren().get(0);
    if (globalDecs.containsKey(getDecId(funDec))) {
      throw new BPLTypeCheckerException("TypeChecker Error: Variable " + getDecId(funDec) + " already assigned.");
    }
    globalDecs.put(getDecId(funDec), funDec);

    if (debug) {
      System.out.println("Added " + funDec.getKind() + " " + getDecType(funDec) + " " + getDecId(funDec) + " to Global Declarations on line " + funDec.getLine());
    }
  }

  private void addVarDecToGlobalDecs(TreeNode dec) throws BPLTypeCheckerException {
    TreeNode varDec = dec.getChildren().get(0);
    if (globalDecs.containsKey(getDecId(varDec))) {
      throw new BPLTypeCheckerException("TypeChecker Error: Variable " + getDecId(varDec) + " already assigned.");
    }
    globalDecs.put(getDecId(varDec), varDec);

    if (debug) {
      System.out.println("Added " + varDec.getKind() + " " + getDecType(varDec) + " " + getDecId(varDec) + " to Global Declarations on line " + varDec.getLine());
    }
  }

  private String getDecType(TreeNode dec) {
    return dec.getChildren().get(0).getChildren().get(0).getKind().toString();
  }

  private String getDecId(TreeNode dec) {
    TreeNode id = dec.getChildren().get(1);
    return id.getValue();
  }

  private boolean isVarDec(TreeNode dec) {
    TreeNodeKind decKind = dec.getChildren().get(0).getKind();
    return decKind == TreeNodeKind.VAR_DEC || decKind == TreeNodeKind.POINTER_VAR_DEC || decKind == TreeNodeKind.ARRAY_VAR_DEC;
  }

  private boolean isEmpty(TreeNode t) {
    return t.getKind() == TreeNodeKind.EMPTY;
  }

  private TreeNode getDec(TreeNode t) {
    return t.getChildren().get(1);
  }

  private TreeNode getDecList(TreeNode t) {
    return t.getChildren().get(0);
  }

  private void assertType(Type type, Type expected, int line) throws BPLTypeCheckerException {
    if (type != expected) {
      throw new BPLTypeCheckerException("TypeChecker Error: Expected " + expected + " but got " + type + " on line " + line);
    }
  }

  private void assertType(Type type, Type[] expected, int line) throws BPLTypeCheckerException {
    String expectedTypes = "";
    for (Type t : expected) {
      if (type == t) {
        return;
      }
      expectedTypes = expectedTypes + t + ",";
    }
    throw new BPLTypeCheckerException("TypeChecker Error: Expected " + expectedTypes + " but got " + type + " on line " + line);
  }
}
