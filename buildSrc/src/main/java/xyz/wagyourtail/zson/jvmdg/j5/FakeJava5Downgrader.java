package xyz.wagyourtail.zson.jvmdg.j5;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.MethodNode;
import xyz.wagyourtail.jvmdg.util.Function;
import xyz.wagyourtail.jvmdg.version.VersionProvider;
import xyz.wagyourtail.zson.jvmdg.j5.stub.J_L_Throwable;

import java.util.Iterator;
import java.util.Set;

public class FakeJava5Downgrader extends VersionProvider {

    public FakeJava5Downgrader() {
        super(Opcodes.V1_8, Opcodes.V1_5);
    }

    @Override
    public void init() {
        stub(J_L_Throwable.class);
    }

    @Override
    public ClassNode otherTransforms(ClassNode clazz, Set<ClassNode> extra, Function<String, ClassNode> getReadOnly, Set<String> warnings) {
        // remove all stack map data
        for (MethodNode method : clazz.methods) {
            if (method.instructions != null) {
                Iterator<AbstractInsnNode> it = method.instructions.iterator();
                while (it.hasNext()) {
                    AbstractInsnNode insn = it.next();
                    if (insn instanceof FrameNode) {
                        it.remove();
                    }
                }
            }
        }
        return super.otherTransforms(clazz, extra, getReadOnly, warnings);
    }
}
