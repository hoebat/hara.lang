package hara.lang.compiler;

import hara.lang.data.List;
import java.util.concurrent.atomic.AtomicLong;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class Compiler {

    private static final AtomicLong CLASS_COUNTER = new AtomicLong(0);

    public static class Result {
        public final String className;
        public final byte[] bytes;
        public Result(String c, byte[] b) { className = c; bytes = b; }
    }

    @SuppressWarnings("rawtypes")
    public Result compile(hara.lang.data.List expression) {
        hara.lang.data.List body = (hara.lang.data.List) expression.nth(2);

        String className = "hara/lang/compiler/CompiledFunction" + CLASS_COUNTER.incrementAndGet();
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);

        // Class signature
        cw.visit(Opcodes.V11, Opcodes.ACC_PUBLIC, className,
                null,
                "java/lang/Object", new String[]{"hara/lang/base/I$Fn"});

        // Default constructor
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(1, 1);
        mv.visitEnd();

        // getArg1 method
        mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "getArg1", "()Ljava/util/function/Function;", null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitInsn(Opcodes.ARETURN);
        mv.visitMaxs(1, 1);
        mv.visitEnd();

        // apply(Object) method
        mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "apply", "(Ljava/lang/Object;)Ljava/lang/Object;", null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitTypeInsn(Opcodes.INSTANCEOF, "[Ljava/lang/Object;");
        org.objectweb.asm.Label l1 = new org.objectweb.asm.Label();
        mv.visitJumpInsn(Opcodes.IFEQ, l1);
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitTypeInsn(Opcodes.CHECKCAST, "[Ljava/lang/Object;");
        mv.visitInsn(Opcodes.ICONST_0);
        mv.visitInsn(Opcodes.AALOAD);
        mv.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Long");
        org.objectweb.asm.Label l2 = new org.objectweb.asm.Label();
        mv.visitJumpInsn(Opcodes.GOTO, l2);
        mv.visitLabel(l1);
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Long");
        mv.visitLabel(l2);
        mv.visitVarInsn(Opcodes.ASTORE, 2);
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitVarInsn(Opcodes.ALOAD, 2);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, className, "apply", "(Ljava/lang/Long;)Ljava/lang/Long;", false);
        mv.visitInsn(Opcodes.ARETURN);
        mv.visitMaxs(3, 3);
        mv.visitEnd();

        // apply(Long) method
        mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "apply", "(Ljava/lang/Long;)Ljava/lang/Long;", null, null);
        mv.visitCode();

        hara.lang.data.Symbol op = (hara.lang.data.Symbol) body.nth(0);
        Object arg1 = body.nth(1);
        Object arg2 = body.nth(2);

        if (arg1 instanceof hara.lang.data.Symbol && arg2 instanceof Number) {
            mv.visitVarInsn(Opcodes.ALOAD, 1);
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Long", "longValue", "()J", false);
            mv.visitLdcInsn(((Number) arg2).longValue());
        } else if (arg1 instanceof Number && arg2 instanceof hara.lang.data.Symbol) {
            mv.visitLdcInsn(((Number) arg1).longValue());
            mv.visitVarInsn(Opcodes.ALOAD, 1);
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Long", "longValue", "()J", false);
        } else {
            throw new CompilerException("Unsupported expression format");
        }

        switch (op.getName()) {
            case "+":
                mv.visitInsn(Opcodes.LADD);
                break;
            case "-":
                mv.visitInsn(Opcodes.LSUB);
                break;
            case "*":
                mv.visitInsn(Opcodes.LMUL);
                break;
            case "/":
                mv.visitInsn(Opcodes.LDIV);
                break;
        }

        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;", false);

        mv.visitInsn(Opcodes.ARETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();

        cw.visitEnd();
        return new Result(className, cw.toByteArray());
    }
}
