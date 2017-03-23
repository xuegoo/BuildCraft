package buildcraft.lib.expression.node.binary;

import buildcraft.lib.expression.NodeInliningHelper;
import buildcraft.lib.expression.api.IExpressionNode.INodeBoolean;
import buildcraft.lib.expression.node.value.NodeConstantBoolean;

public class NodeBinaryBoolean implements INodeBoolean {
    @FunctionalInterface
    public interface BiBooleanPredicate {
        boolean apply(boolean left, boolean right);
    }

    private final INodeBoolean left, right;
    private final BiBooleanPredicate func;
    private final String op;

    public NodeBinaryBoolean(INodeBoolean left, INodeBoolean right, BiBooleanPredicate func, String op) {
        this.left = left;
        this.right = right;
        this.func = func;
        this.op = op;
    }

    @Override
    public boolean evaluate() {
        return func.apply(left.evaluate(), right.evaluate());
    }

    @Override
    public INodeBoolean inline() {
        return NodeInliningHelper.tryInline(this, left, right, (l, r) -> new NodeBinaryBoolean(l, r, func, op), //
            (l, r) -> NodeConstantBoolean.get(func.apply(l.evaluate(), r.evaluate())));
    }

    @Override
    public String toString() {
        return "(" + left + ") " + op + " (" + right + ")";
    }
}
