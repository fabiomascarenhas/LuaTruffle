package org.jlua.main.translator;

import com.oracle.truffle.api.frame.FrameDescriptor;
import org.jlua.main.nodes.LuaConstantNode;
import org.jlua.main.nodes.LuaExpressionNode;
import org.jlua.main.nodes.LuaNode;
import org.jlua.main.nodes.LuaStatementNode;
import org.jlua.main.nodes.local.LuaWriteLocalVariableNodeFactory;
import org.jlua.main.nodes.operations.arithmetic.*;
import org.jlua.main.nodes.operations.relational.*;
import org.jlua.main.nodes.statements.LuaBlockNode;
import org.jlua.main.nodes.statements.LuaIfNode;
import org.jlua.main.nodes.statements.LuaReturnNode;
import org.jlua.main.nodes.statements.LuaWhileDoNode;
import org.jlua.main.runtime.LuaContext;
import org.jlua.main.runtime.LuaNull;
import org.luaj.vm2.LuaNil;
import org.luaj.vm2.ast.*;
import org.luaj.vm2.ast.Stat.LocalAssign;

import java.util.List;

/**
 * Created by Lucas Allan Amorim on 2014-09-08.
 */

public class Translator extends Visitor {

    private FrameDescriptor frameDescriptor;
    private LuaContext context;

    public Translator(LuaContext context) {
        frameDescriptor = new FrameDescriptor();
        this.context = context;
    }

    // TODO needs a good cleaup
    public Object translate(Object object) {
        if (object instanceof Chunk){
            return visitChunk((Chunk) object);
        } else if (object instanceof Block) {
            return visitBlock((Block) object);
        } else if (object instanceof Stat.IfThenElse) {
            return visitIfThenElse((Stat.IfThenElse) object);
        } else if (object instanceof Exp.BinopExp) {
            return visitBinoExp((Exp.BinopExp) object);
        } else if (object instanceof Stat.Return) {
            return visitReturn((Stat.Return) object);
        } else if (object instanceof Exp.Constant) {
            return visitConstant((Exp.Constant) object);
        } else if (object instanceof Stat.FuncDef) {
            return visitFuncDef((Stat.FuncDef) object);
        } else if (object instanceof Stat.FuncCallStat) {
            return visitFuncCallStat((Stat.FuncCallStat) object);
        } else if (object instanceof Exp.ParensExp) {
            return visitParensExp((Exp.ParensExp) object);
        } else if (object instanceof  Stat.WhileDo ) {
            return visitWhileDo((Stat.WhileDo) object);
        } else if (object instanceof Stat.LocalAssign) {
            return visitLocalAssign((Stat.LocalAssign) object);
        } else {
            System.err.println("Needs be handled: " + object.getClass().getName());
            return null;
        }

    }

    private Object visitWhileDo(Stat.WhileDo whileDo) {
        LuaExpressionNode expression = (LuaExpressionNode) translate(whileDo.exp);
        LuaBlockNode whileBlock = (LuaBlockNode) translate(whileDo.block);
        return new LuaWhileDoNode(expression, whileBlock);
    }

    public Object visitIfThenElse(Stat.IfThenElse ifThenElse) {

        LuaExpressionNode expression = (LuaExpressionNode) translate(ifThenElse.ifexp);
        LuaBlockNode elseBlock = null;
        LuaBlockNode ifBlock = null;

        if (ifThenElse.elseblock != null) {
            LuaStatementNode[] elseBlocks = new LuaStatementNode[ifThenElse.elseblock.stats.size()];
            for (int i = 0; i < ifThenElse.elseblock.stats.size(); i++) {
                elseBlocks[i] = (LuaStatementNode) translate(ifThenElse.elseblock.stats.get(i));
            }
            elseBlock = new LuaBlockNode(elseBlocks);
        }
        if (ifThenElse.ifblock != null) {
            LuaStatementNode[] ifBlocks = new LuaStatementNode[ifThenElse.ifblock.stats.size()];
            for (int i = 0; i < ifThenElse.ifblock.stats.size(); i++) {
                ifBlocks[i] = (LuaStatementNode) translate(ifThenElse.ifblock.stats.get(i));
            }
            ifBlock = new LuaBlockNode(ifBlocks);
        }

        // TODO Handle elseIfs
        return new LuaIfNode(expression, elseBlock, ifBlock);
    }
    @Override
    public void visit(Stat.Assign assign) {
        visitLuaNode(assign);

        for (Object ob : assign.vars){

            Exp.NameExp var = (Exp.NameExp) ob;
            var.accept(this);
            System.err.println("Var Assignment: " + var.name.name + " " + var.name.variable);

        }
    }

    public LuaBlockNode visitChunk(Chunk chunk) {
        return visitBlock(chunk.block);
    }

    public LuaBlockNode visitBlock(Block block){
        //visit(block.scope);
        LuaBlockNode blockNode = null;

        if ( block.stats != null ) {
            LuaStatementNode blocks[] = new LuaStatementNode[block.stats.size()];

            for (int i = 0, n = block.stats.size(); i < n; i++) {
                Object returnedBlock = translate(block.stats.get(i));
                if (returnedBlock instanceof LuaStatementNode) {
                    blocks[i] = (LuaStatementNode) returnedBlock;
                } else {
                    handleUnknown(returnedBlock);
                }
            }

            blockNode = new LuaBlockNode(blocks);
        }
        return blockNode;
    }

    @Override
    public void visit(Stat.Break aBreak) {
        visitLuaNode(aBreak);
    }

    @Override
    public void visit(Stat.FuncCallStat funcCallStat) {
        visitLuaNode(funcCallStat);
    }

    @Override
    public void visit(Stat.FuncDef funcDef) {
        System.err.println(funcDef.name.name.name);
        funcDef.body.accept(this);
        visitLuaNode(funcDef);
    }

    public Object visitFuncDef(Stat.FuncDef funcDef) {

        return null;
    }

    public Object visitFuncCallStat(Stat.FuncCallStat funcCallStat) {
        return null;
    }

    @Override
    public void visit(Stat.GenericFor genericFor) {
        visitLuaNode(genericFor);
    }

    public Object visitLocalAssign(LocalAssign localAssign) {

        // For now, returns only the first assignment
        for(int i = 0; i< localAssign.values.size(); i++) {
            LuaExpressionNode luaExpressionNode = (LuaExpressionNode) translate(localAssign.values.get(i));
            return LuaWriteLocalVariableNodeFactory.create(luaExpressionNode, frameDescriptor.findOrAddFrameSlot(localAssign.names.get(i)));
        }

        return null;
    }

    @Override
    public void visit(Stat.LocalFuncDef localFuncDef) {
        localFuncDef.accept(this);
        localFuncDef.body.accept(this);
        visitLuaNode(localFuncDef);
    }

    @Override
    public void visit(Stat.NumericFor numericFor) {
        numericFor.accept(this);
        numericFor.block.accept(this);
        numericFor.initial.accept(this);
        numericFor.step.accept(this);
        numericFor.limit.accept(this);
        visitLuaNode(numericFor);
    }

    @Override
    public void visit(Stat.RepeatUntil repeatUntil) {
        System.err.println("Visitor: " + repeatUntil.getClass().getName());
        visitLuaNode(repeatUntil);
    }

    public LuaReturnNode visitReturn(Stat.Return aReturn) {
        //System.out.println(aReturn.nreturns()); //might have more than one return
        Object objectReturned = translate(aReturn.values.get(0));
        return new LuaReturnNode((LuaNode) objectReturned);
    }

    @Override
    public void visit(Stat.WhileDo whileDo) {
        visitLuaNode(whileDo);
    }

    @Override
    public void visit(FuncBody funcBody) {
        funcBody.block.accept(this);
        funcBody.parlist.accept(this);
     }

    @Override
    public void visit(FuncArgs funcArgs) {
        funcArgs.accept(this);
        for (Object ob : funcArgs.exps){
            handleUnknown(ob);
        }
    }

    @Override
    public void visit(TableField tableField) {
        tableField.accept(this);
        tableField.index.accept(this);
        tableField.rhs.accept(this);
    }

    @Override
    public void visit(Exp.AnonFuncDef anonFuncDef) {
        visitLuaNode(anonFuncDef);
    }

    public void visit(Exp.BinopExp binopExp) {
        System.err.println(binopExp.op);
    }

    public Object visitBinoExp(Exp.BinopExp binopExp) {
        LuaExpressionNode left = (LuaExpressionNode) translate( binopExp.lhs);
        LuaExpressionNode right = (LuaExpressionNode) translate(binopExp.rhs);

        switch (binopExp.op) {
            case 13:
                return LuaAddNodeFactory.create(left, right);
            case 24:
               return LuaEqualsNodeFactory.create(left, right);
            case 61:
                return LuaEqualsNodeFactory.create(left, right);
            case 63:
                return LuaGreaterThanNodeFactory.create(left, right);
            case 62:
                return LuaGreaterOrEqualsNodeFactory.create(left, right);
            case 26:
                return LuaLessOrEqualsNodeFactory.create(left, right);
            case 25:
                return LuaLessThanNodeFactory.create(left, right);
            case 15:
                return LuaMultiplicationNodeFactory.create(left, right);
            case 16:
                return LuaDivisionOperationFactory.create(left, right);
            case 14:
                return LuaSubtractionNodeFactory.create(left, right);
            case 18:
                return LuaExponentiationNodeFactory.create(left, right);
        }
        throw new UnsupportedOperationException(String.valueOf(binopExp.op));
    }

    @Override
    public void visit(Exp.Constant constant) {
        System.err.println(visitConstant(constant).toString());
    }


    public LuaConstantNode visitConstant(Exp.Constant constant){
        if (constant.value.typename().equals("number")){
            return new LuaConstantNode(constant.value.checklong());
        } else if (constant.value.typename().equals("nil")) {
            return new LuaConstantNode(LuaNull.SINGLETON);
        } else if (constant.value.typename().equals("string")){
            return new LuaConstantNode(constant.value.toString());
        }
        // needs to handle others lua types
        throw  new UnsupportedOperationException(constant.value.typename());
    }

    @Override
    public void visit(Exp.FieldExp fieldExp) {
        visitLuaNode(fieldExp);
    }

    @Override
    public void visit(Exp.FuncCall funcCall) {
        visitLuaNode(funcCall);
    }

    @Override
    public void visit(Exp.IndexExp indexExp) {
        visitLuaNode(indexExp);
    }

    @Override
    public void visit(Exp.MethodCall methodCall) {
        visitLuaNode(methodCall);
    }

    @Override
    public void visit(Exp.NameExp nameExp) {
        visitLuaNode(nameExp);
    }

    public Object visitParensExp(Exp.ParensExp parensExp) {
        return translate(parensExp.exp);
    }

    @Override
    public void visit(Exp.UnopExp unopExp) {
        visitLuaNode(unopExp);
    }

    @Override
    public void visit(Exp.VarargsExp varargsExp) {
        visitLuaNode(varargsExp);
    }

    @Override
    public void visit(ParList parList) {
        for (Object ob : parList.names){
            handleUnknown(ob);
        }
    }

    @Override
    public void visit(TableConstructor tableConstructor) {
        visitLuaNode(tableConstructor);
    }

    @Override
    public void visitVars(List list) {
        System.err.println("List size: " + list.size());
    }


    @Override
    public void visit(Name name) {
        System.out.println("Visit Name: " + name.name);
    }

    @Override
    public void visit(String s) {
        System.out.println("String Name: " + s);
    }

    @Override
    public void visit(NameScope nameScope) {
        visitLuaNode(nameScope);
    }

    @Override
    public void visit(Stat.Goto aGoto) {
        visitLuaNode(aGoto);
    }

    @Override
    public void visit(Stat.Label label) {
        visitLuaNode(label);
    }

    private void visitLuaNode(Stat node) {
        System.err.println("Visitor: " +node.getClass().getName() + " Line: " + node.beginLine);
    }

    private Object visitNode(Object ob) {
        if (ob instanceof Exp.Constant) {
            return visitConstant((Exp.Constant)ob);
        } else if (ob instanceof Name) {
            return ((Name) ob).name;
        }
        return null;
    }

    private void visitLuaNode(Exp node){
        System.err.println("Visitor: " +node.getClass().getName() + " Line: " + node.beginLine);
    }

    private void visitLuaNode(NameScope node){
        if (node != null) {
            System.err.println("Visitor: " + node.getClass().getName());
        }
    }

    private void handleUnknown(Object ob) {
        if (ob instanceof Exp){
            ((Exp) ob).accept(this);
        } else if (ob instanceof Stat) {
            ((Stat) ob).accept(this);
        } else if (ob instanceof Name) {
            visit((Name) ob);
        } else {
            System.out.println("FuncArgs - Unknown type: " +ob.getClass().getName());
        }
    }

}
