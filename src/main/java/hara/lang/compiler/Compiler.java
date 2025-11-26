package hara.lang.compiler;

import hara.lang.data.List;
import java.util.concurrent.atomic.AtomicLong;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class Compiler {

    private static final AtomicLong CLASS_COUNTER = new AtomicLong(0);

    @SuppressWarnings("rawtypes")
    public byte[] compile(hara.lang.data.List expression) {
        String className = "hara/lang/compiler/CompiledFunction" + CLASS_COUNTER.incrementAndGet();
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);

        // Class signature
        cw.visit(Opcodes.V11, Opcodes.ACC_PUBLIC, className,
                "Ljava/lang/Object;Ljava/util/function/Function<Ljava/lang/Long;Ljava/lang/Long;>;",
                "java/lang/Object", new String[]{"java/util/function/Function"});

        // Default constructor
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(1, 1);
        mv.visitEnd();

        // apply(Object) method (bridge method for type erasure)
        mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_BRIDGE | Opcodes.ACC_SYNTHETIC,
                "apply", "(Ljava/lang/Object;)Ljava/lang/Object;", null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Long");
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, className, "apply", "(Ljava/lang/Long;)Ljava/lang/Long;", false);
        mv.visitInsn(Opcodes.ARETURN);
        mv.visitMaxs(2, 2);
        mv.visitEnd();

        // apply(Long) method
        mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "apply", "(Ljava/lang/Long;)Ljava/lang/Long;", null, null);
        mv.visitCode();

        // This is where the (+ x 1) logic goes.
        // x is the first argument (a Long) at index 1.
        mv.visitVarInsn(Opcodes.ALOAD, 1); // Load the Long object
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Long", "longValue", "()J", false); // Unbox to long
        mv.visitInsn(Opcodes.LCONST_1); // Push 1L onto the stack
        mv.visitInsn(Opcodes.LADD); // Add them
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;", false); // Box it back to Long

        mv.visitInsn(Opcodes.ARETURN); // Return the Long object
        mv.visitMaxs(3, 2); // max stack, max locals
        mv.visitEnd();

        cw.visitEnd();
        return cw.toByteArray();
    }
}
