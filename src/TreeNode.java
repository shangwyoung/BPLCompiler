import java.util.ArrayList;

public class TreeNode {
  private TreeNodeKind kind;
  private int line;
  private String value;
  private ArrayList<TreeNode> children;
  private TreeNode declaration;
  private Type type;
  private int depth;
  private int position;

  public TreeNode(TreeNodeKind kind, int line, String value) {
    this.kind = kind;
    this.line = line;
    this.value = value;
    this.children = new ArrayList<TreeNode>();
    this.declaration = null;
    this.type = null;
    this.depth = -1;
    this.position = -1;
  }

  public void setDepth(int d) {
    this.depth = d;
  }

  public int getDepth() {
    return this.depth;
  }

  public void setPosition(int p) {
    this.position = p;
  }

  public int getPosition() {
    return this.position;
  }

  public void setType(Type type) {
    this.type = type;
  }

  public Type getType() {
    return this.type;
  }

  public String getValue() {
    return this.value;
  }

  public void setValue(String val) {
    if (this.value != null) {
      System.out.println(this + " value overwritten!");
    }
    this.value = val;
  }

  public void setDec(TreeNode t) {
    this.declaration = t;
  }

  public TreeNode getDec() {
    return this.declaration;
  }

  public ArrayList<TreeNode> getChildren() {
    return this.children;
  }

  public void addChild(TreeNode t) {
    children.add(t);
  }

  public int getLine() {
    return this.line;
  }

  public TreeNodeKind getKind() {
    return this.kind;
  }

  public void setLine(int line) {
    this.line = line;
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof TreeNode)) { return false; }
    if (obj == this) { return true; }

    TreeNode t = (TreeNode) obj;
    return (this.kind == t.kind) && this.value.equals(t.value) && (this.line == t.line);
  }

  @Override
  public String toString() {
    boolean isDec = (this.declaration != null);
    String s = this.kind + ", string: " + this.value + ", line: " + this.line + " type: " + this.type + " dec: " + isDec + " depth: "+ this.depth + " pos: " + this.position + " ";
    return s;
  }
}
