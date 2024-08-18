package xyz.wagyourtail.zson.jvmdg.j5.stub;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodNode;
import xyz.wagyourtail.jvmdg.version.Modify;
import xyz.wagyourtail.jvmdg.version.Ref;

public class J_L_Throwable {

    @Modify(
        ref = @Ref(
            value = "java/lang/Throwable",
            member = "addSuppressed",
            desc = "(Ljava/lang/Throwable;)V"
        )
    )
    public static void addSuppressed(MethodNode mn, int idx) {
        mn.instructions.set(mn.instructions.get(idx), new InsnNode(Opcodes.POP2));
    }

}
