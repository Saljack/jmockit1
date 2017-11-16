/*
 * ASM: a very small and fast Java bytecode manipulation framework
 * Copyright (c) 2000-2011 INRIA, France Telecom
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. Neither the name of the copyright holders nor the names of its
 *    contributors may be used to endorse or promote products derived from
 *    this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF
 * THE POSSIBILITY OF SUCH DAMAGE.
 */
package mockit.external.asm;

import static mockit.external.asm.Frame.TypeMask.*;
import static mockit.external.asm.Opcodes.*;

/**
 * Information about the input and output stack map frames of a basic block.
 *
 * @author Eric Bruneton
 */
public final class Frame
{
   // Frames are computed in a two steps process: during the visit of each instruction, the state of the frame at the
   // end of current basic block is updated by simulating the action of the instruction on the previous state of this so
   // called "output frame". In visitMaxStack, a fix point algorithm is used to compute the "input frame" of each basic
   // block, i.e. the stack map frame at the beginning of the basic block, starting from the input frame of the first
   // basic block (which is computed from the method descriptor), and by using the previously computed output frames to
   // compute the input state of the other blocks.
   //
   // All output and input frames are stored as arrays of integers. Reference and array types are represented by an
   // index into a type table (which is not the same as the constant pool of the class, in order to avoid adding
   // unnecessary constants in the pool - not all computed frames will end up being stored in the stack map table).
   // This allows very fast type comparisons.
   //
   // Output stack map frames are computed relatively to the input frame of the basic block, which is not yet known when
   // output frames are computed. It is therefore necessary to be able to represent abstract types such as "the type at
   // position x in the input frame locals" or "the type at position x from the top of the input frame stack" or even
   // "the type at position x in the input frame, with y more (or less) array dimensions".
   // This explains the rather complicated type format used in output frames.
   //
   // This format is the following: DIM KIND VALUE (4, 4 and 24 bits). DIM is a signed number of array dimensions (from
   // -8 to 7). KIND is either BASE, LOCAL or STACK. BASE is used for types that are not relative to the input frame.
   // LOCAL is used for types that are relative to the input local variable types. STACK is used for types that are
   // relative to the input stack types. VALUE depends on KIND. For LOCAL types, it is an index in the input local
   // variable types. For STACK types, it is a position relatively to the top of input frame stack. For BASE types, it
   // is either one of the constants defined below, or for OBJECT and UNINITIALIZED types, a tag and an index in the
   // type table.
   //
   // Output frames can contain types of any kind and with a positive or negative dimension (and even unassigned types,
   // represented by 0 - which does not correspond to any valid type value). Input frames can only contain BASE types of
   // positive or null dimension. In all cases the type table contains only internal type names (array type descriptors
   // are forbidden - dimensions must be represented through the DIM field).
   //
   // The LONG and DOUBLE types are always represented by using two slots (LONG + TOP or DOUBLE + TOP), for local
   // variable types as well as in the operand stack. This is necessary to be able to simulate DUPx_y instructions,
   // whose effect would be dependent on the actual type values if types were always represented by a single slot in the
   // stack (and this is not possible, since actual type values are not always known - cf LOCAL and STACK type kinds).

   interface TypeMask
   {
      /**
       * Mask to get the dimension of a frame type. This dimension is a signed integer between -8 and 7.
       */
      int DIM = 0xF0000000;

      /**
       * Constant to be added to a type to get a type with one more dimension.
       */
      int ARRAY_OF = 0x10000000;

      /**
       * Constant to be added to a type to get a type with one less dimension.
       */
      int ELEMENT_OF = 0xF0000000;

      /**
       * Mask to get the kind of a frame type.
       *
       * @see #BASE
       * @see #LOCAL
       * @see #STACK
       */
      int KIND = 0xF000000;

      /**
       * Flag used for LOCAL and STACK types. Indicates that if this type happens to be a long or double type (during the
       * computations of input frames), then it must be set to TOP because the second word of this value has been reused to
       * store other data in the basic block. Hence the first word no longer stores a valid long or double value.
       */
      int TOP_IF_LONG_OR_DOUBLE = 0x800000;

      /**
       * Mask to get the value of a frame type.
       */
      int VALUE = 0x7FFFFF;

      /**
       * Mask to get the kind of base types.
       */
      int BASE_KIND = 0xFF00000;

      /**
       * Mask to get the value of base types.
       */
      int BASE_VALUE = 0xFFFFF;

      /**
       * Kind of the types that are not relative to an input stack map frame.
       */
      int BASE = 0x1000000;

      /**
       * Base kind of the base reference types. The BASE_VALUE of such types is an index into the type table.
       */
      int OBJECT = BASE | 0x700000;

      /**
       * Base kind of the uninitialized base types. The BASE_VALUE of such types in an index into the type table (the Item
       * at that index contains both an instruction offset and an internal class name).
       */
      int UNINITIALIZED = BASE | 0x800000;

      /**
       * Kind of the types that are relative to the local variable types of an input stack map frame. The value of such
       * types is a local variable index.
       */
      int LOCAL = 0x2000000;

      /**
       * Kind of the the types that are relative to the stack of an input stack map frame. The value of such types is a
       * position relatively to the top of this stack.
       */
      int STACK = 0x3000000;

      /**
       * The TOP type. This is a BASE type.
       */
      int TOP = BASE | 0;

      /**
       * The BOOLEAN type. This is a BASE type mainly used for array types.
       */
      int BOOLEAN = BASE | 9;

      /**
       * The BYTE type. This is a BASE type mainly used for array types.
       */
      int BYTE = BASE | 10;

      /**
       * The CHAR type. This is a BASE type mainly used for array types.
       */
      int CHAR = BASE | 11;

      /**
       * The SHORT type. This is a BASE type mainly used for array types.
       */
      int SHORT = BASE | 12;

      /**
       * The INTEGER type. This is a BASE type.
       */
      int INTEGER = BASE | 1;

      /**
       * The FLOAT type. This is a BASE type.
       */
      int FLOAT = BASE | 2;

      /**
       * The DOUBLE type. This is a BASE type.
       */
      int DOUBLE = BASE | 3;

      /**
       * The LONG type. This is a BASE type.
       */
      int LONG = BASE | 4;

      /**
       * The NULL type. This is a BASE type.
       */
      int NULL = BASE | 5;

      /**
       * The UNINITIALIZED_THIS type. This is a BASE type.
       */
      int UNINITIALIZED_THIS = BASE | 6;
   }

   /**
    * The stack size variation corresponding to each JVM instruction. This stack variation is equal to the size of the
    * values produced by an instruction, minus the size of the values consumed by this instruction.
    */
   public static final int[] SIZE = new int[202];
   static {
      String s =
         "EFFFFFFFFGGFFFGGFFFEEFGFGFEEEEEEEEEEEEEEEEEEEEDEDEDDDDD" +
         "CDCDEEEEEEEEEEEEEEEEEEEEBABABBBBDCFFFGGGEDCDCDCDCDCDCDCDCD" +
         "CDCEEEEDDDDDDDCDCDCEFEFDDEEFFDEDEEEBDDBBDDDDDDCCCCCCCCEFED" +
         "DDCDCDEEEEEEEEEEFEEEEEEDDEEDDEE";
      int[] b = SIZE;

      for (int i = 0; i < b.length; ++i) {
         b[i] = s.charAt(i) - 'E';
      }
   }

   /**
    * The label (i.e. basic block) to which these input and output stack map frames correspond.
    */
   final Label owner;

   /**
    * The input stack map frame locals.
    */
   int[] inputLocals;

   /**
    * The input stack map frame stack.
    */
   int[] inputStack;

   /**
    * The output stack map frame locals.
    */
   private int[] outputLocals;

   /**
    * The output stack map frame stack.
    */
   private int[] outputStack;

   /**
    * Relative size of the output stack. The exact semantics of this field depends on the algorithm that is used.
    * <p/>
    * When only the maximum stack size is computed, this field is the size of the output stack relatively to the top of
    * the input stack.
    * <p/>
    * When the stack map frames are completely computed, this field is the actual number of types in
    * {@link #outputStack}.
    */
   private int outputStackTop;

   /**
    * Number of types that are initialized in the basic block.
    *
    * @see #initializations
    */
   private int initializationCount;

   /**
    * The types that are initialized in the basic block. A constructor invocation on an UNINITIALIZED or
    * UNINITIALIZED_THIS type must replace <i>every occurrence</i> of this type in the local variables and in the
    * operand stack. This cannot be done during the first phase of the algorithm since, during this phase, the local
    * variables and the operand stack are not completely computed. It is therefore necessary to store the types on
    * which constructors are invoked in the basic block, in order to do this replacement during the second phase of the
    * algorithm, where the frames are fully computed. Note that this array can contain types that are relative to input
    * locals or to the input stack (see below for the description of the algorithm).
    */
   private int[] initializations;

   Frame(Label owner) {
      this.owner = owner;
   }

   /**
    * Returns the output frame local variable type at the given index.
    *
    * @param local the index of the local that must be returned.
    */
   private int get(int local) {
      if (outputLocals == null || local >= outputLocals.length) {
         // This local has never been assigned in this basic block, so it is still equal to its value in the input
         // frame.
         return LOCAL | local;
      }

      int type = outputLocals[local];

      if (type == 0) {
         // This local has never been assigned in this basic block, so it is still equal to its value in the input
         // frame.
         type = outputLocals[local] = LOCAL | local;
      }

      return type;
   }

   /**
    * Sets the output frame local variable type at the given index.
    *
    * @param local the index of the local that must be set.
    * @param type  the value of the local that must be set.
    */
   private void set(int local, int type) {
      // Creates and/or resizes the output local variables array if necessary.
      if (outputLocals == null) {
         outputLocals = new int[10];
      }

      int n = outputLocals.length;

      if (local >= n) {
         int[] t = new int[Math.max(local + 1, 2 * n)];
         System.arraycopy(outputLocals, 0, t, 0, n);
         outputLocals = t;
      }

      // Sets the local variable.
      outputLocals[local] = type;
   }

   /**
    * Pushes a new type onto the output frame stack.
    *
    * @param type the type that must be pushed.
    */
   private void push(int type) {
      // Creates and/or resizes the output stack array if necessary.
      if (outputStack == null) {
         outputStack = new int[10];
      }

      int n = outputStack.length;

      if (outputStackTop >= n) {
         int[] t = new int[Math.max(outputStackTop + 1, 2 * n)];
         System.arraycopy(outputStack, 0, t, 0, n);
         outputStack = t;
      }

      // Pushes the type on the output stack.
      outputStack[outputStackTop++] = type;

      // Updates the maximum height reached by the output stack, if needed.
      int top = owner.inputStackTop + outputStackTop;

      if (top > owner.outputStackMax) {
         owner.outputStackMax = top;
      }
   }

   /**
    * Pushes a new type onto the output frame stack.
    *
    * @param cp   the constant pool to which this type belongs.
    * @param desc the descriptor of the type to be pushed. Can also be a method
    *             descriptor (in this case this method pushes its return type onto the output frame stack).
    */
   private void push(ConstantPoolGeneration cp, String desc) {
      int type = type(cp, desc);

      if (type != 0) {
         push(type);

         if (type == LONG || type == DOUBLE) {
            push(TOP);
         }
      }
   }

   /**
    * Returns the int encoding of the given type.
    *
    * @param cp   the constant pool to which this type belongs.
    * @param desc a type descriptor.
    * @return the int encoding of the given type.
    */
   private static int type(ConstantPoolGeneration cp, String desc) {
      int index = desc.charAt(0) == '(' ? desc.indexOf(')') + 1 : 0;
      String t;

      switch (desc.charAt(index)) {
         case 'V':
            return 0;
         case 'Z':
         case 'C':
         case 'B':
         case 'S':
         case 'I':
            return INTEGER;
         case 'F':
            return FLOAT;
         case 'J':
            return LONG;
         case 'D':
            return DOUBLE;
         case 'L':
            // Stores the internal name, not the descriptor!
            t = desc.substring(index + 1, desc.length() - 1);
            return OBJECT | cp.addType(t);
         // case '[':
         default:
            // Extracts the dimensions and the element type.
            int data;
            int dims = index + 1;

            while (desc.charAt(dims) == '[') {
               ++dims;
            }

            switch (desc.charAt(dims)) {
               case 'Z':
                  data = BOOLEAN;
                  break;
               case 'C':
                  data = CHAR;
                  break;
               case 'B':
                  data = BYTE;
                  break;
               case 'S':
                  data = SHORT;
                  break;
               case 'I':
                  data = INTEGER;
                  break;
               case 'F':
                  data = FLOAT;
                  break;
               case 'J':
                  data = LONG;
                  break;
               case 'D':
                  data = DOUBLE;
                  break;
               // case 'L':
               default:
                  // Stores the internal name, not the descriptor.
                  t = desc.substring(dims + 1, desc.length() - 1);
                  data = OBJECT | cp.addType(t);
            }

            return (dims - index) << 28 | data;
      }
   }

   /**
    * Pops a type from the output frame stack and returns its value.
    *
    * @return the type that has been popped from the output frame stack.
    */
   private int pop() {
      if (outputStackTop > 0) {
         return outputStack[--outputStackTop];
      }

      // If the output frame stack is empty, pops from the input stack.
      //noinspection UnnecessaryParentheses
      return STACK | -(--owner.inputStackTop);
   }

   /**
    * Pops the given number of types from the output frame stack.
    *
    * @param elements the number of types that must be popped.
    */
   private void pop(int elements) {
      if (outputStackTop >= elements) {
         outputStackTop -= elements;
      }
      else {
         // If the number of elements to be popped is greater than the number of elements in the output stack, clear
         // it, and pops the remaining elements from the input stack.
         owner.inputStackTop -= elements - outputStackTop;
         outputStackTop = 0;
      }
   }

   /**
    * Pops a type from the output frame stack.
    *
    * @param desc the descriptor of the type to be popped. Can also be a method
    *             descriptor (in this case this method pops the types corresponding to the method arguments).
    */
   private void pop(String desc) {
      char c = desc.charAt(0);

      if (c == '(') {
         pop((Type.getArgumentsAndReturnSizes(desc) >> 2) - 1);
      }
      else if (c == 'J' || c == 'D') {
         pop(2);
      }
      else {
         pop(1);
      }
   }

   /**
    * Adds a new type to the list of types on which a constructor is invoked in the basic block.
    *
    * @param var a type on a which a constructor is invoked.
    */
   private void init(int var) {
      // Creates and/or resizes the initializations array if necessary.
      if (initializations == null) {
         initializations = new int[2];
      }

      int n = initializations.length;

      if (initializationCount >= n) {
         int[] t = new int[Math.max(initializationCount + 1, 2 * n)];
         System.arraycopy(initializations, 0, t, 0, n);
         initializations = t;
      }

      // Stores the type to be initialized.
      initializations[initializationCount++] = var;
   }

   /**
    * Replaces the given type with the appropriate type if it is one of the types on which a constructor is invoked in
    * the basic block.
    *
    * @param cp the constant pool to which this label belongs.
    * @param t  a type
    * @return t or, if t is one of the types on which a constructor is invoked
    * in the basic block, the type corresponding to this constructor.
    */
   private int init(String classDesc, ConstantPoolGeneration cp, int t) {
      int s;

      if (t == UNINITIALIZED_THIS) {
         s = OBJECT | cp.addType(classDesc);
      }
      else if ((t & (DIM | BASE_KIND)) == UNINITIALIZED) {
         String type = cp.getInternalName(t & BASE_VALUE);
         s = OBJECT | cp.addType(type);
      }
      else {
         return t;
      }

      for (int j = 0; j < initializationCount; ++j) {
         int u = initializations[j];
         int dim = u & DIM;
         int kind = u & KIND;

         if (kind == LOCAL) {
            u = dim + inputLocals[u & VALUE];
         }
         else if (kind == STACK) {
            u = dim + inputStack[inputStack.length - (u & VALUE)];
         }

         if (t == u) {
            return s;
         }
      }

      return t;
   }

   /**
    * Initializes the input frame of the first basic block from the method descriptor.
    *
    * @param cp        the constant pool to which this label belongs.
    * @param access    the access flags of the method to which this label belongs.
    * @param args      the formal parameter types of this method.
    * @param maxLocals the maximum number of local variables of this method.
    */
   void initInputFrame(String classDesc, ConstantPoolGeneration cp, int access, Type[] args, int maxLocals) {
      inputLocals = new int[maxLocals];
      inputStack = new int[0];
      int i = 0;

      if ((access & Access.STATIC) == 0) {
         int inputLocal = Access.isConstructor(access) ? UNINITIALIZED_THIS : OBJECT | cp.addType(classDesc);
         inputLocals[i++] = inputLocal;
      }

      for (int j = 0; j < args.length; ++j) {
         int t = type(cp, args[j].getDescriptor());
         inputLocals[i++] = t;

         if (t == LONG || t == DOUBLE) {
            inputLocals[i++] = TOP;
         }
      }

      while (i < maxLocals) {
         inputLocals[i++] = TOP;
      }
   }

   /**
    * Simulates the action of a IINC instruction on the output stack frame.
    */
   void executeIINC(int varIndex) {
      set(varIndex, INTEGER);
   }

   /**
    * Simulates the action of a LOOKUPSWITCH or TABLESWITCH instruction on the output stack frame.
    */
   void executeSWITCH() {
      pop(1);
   }

   /**
    * Simulates the action of a BIPUSH, SIPUSH, or NEWARRAY instruction on the output stack frame.
    *
    * @param opcode  the opcode of the instruction.
    * @param operand the operand of the instruction, if any.
    */
   void executeINT(int opcode, int operand) {
      switch (opcode) {
         case BIPUSH:
         case SIPUSH:
            push(INTEGER);
            break;
         case NEWARRAY:
            executeNewArray(operand);
      }
   }

   /**
    * Simulates the action of a conditional/unconditional jump instruction on the output stack frame.
    *
    * @param opcode the opcode of the instruction.
    */
   void executeJUMP(int opcode) {
      switch (opcode) {
         case IFEQ:
         case IFNE:
         case IFLT:
         case IFGE:
         case IFGT:
         case IFLE:
         case IFNULL:
         case IFNONNULL:
            pop(1);
            break;
         case IF_ICMPEQ:
         case IF_ICMPNE:
         case IF_ICMPLT:
         case IF_ICMPGE:
         case IF_ICMPGT:
         case IF_ICMPLE:
         case IF_ACMPEQ:
         case IF_ACMPNE:
            pop(2);
            break;
         case JSR:
            throw new RuntimeException("JSR is not supported with computeFrames option");
      }
   }

   /**
    * Simulates the action of the given instruction on the output stack frame.
    *
    * @param opcode the opcode of the instruction.
    * @param arg    the operand of the instruction, if any.
    */
   void execute(int opcode, int arg) {
      int t1, t2, t3, t4;

      switch (opcode) {
         case NOP:
         case INEG:
         case LNEG:
         case FNEG:
         case DNEG:
         case I2B:
         case I2C:
         case I2S:
         case GOTO:
         case RETURN:
            break;
         case ACONST_NULL:
            push(NULL);
            break;
         case ICONST_M1:
         case ICONST_0:
         case ICONST_1:
         case ICONST_2:
         case ICONST_3:
         case ICONST_4:
         case ICONST_5:
         case ILOAD:
            push(INTEGER);
            break;
         case LCONST_0:
         case LCONST_1:
         case LLOAD:
            push(LONG);
            push(TOP);
            break;
         case FCONST_0:
         case FCONST_1:
         case FCONST_2:
         case FLOAD:
            push(FLOAT);
            break;
         case DCONST_0:
         case DCONST_1:
         case DLOAD:
            push(DOUBLE);
            push(TOP);
            break;
         case ALOAD:
            push(get(arg));
            break;
         case IALOAD:
         case BALOAD:
         case CALOAD:
         case SALOAD:
            pop(2);
            push(INTEGER);
            break;
         case LALOAD:
         case D2L:
            pop(2);
            push(LONG);
            push(TOP);
            break;
         case FALOAD:
            pop(2);
            push(FLOAT);
            break;
         case DALOAD:
         case L2D:
            pop(2);
            push(DOUBLE);
            push(TOP);
            break;
         case AALOAD:
            pop(1);
            t1 = pop();
            push(ELEMENT_OF + t1);
            break;
         case ISTORE:
         case FSTORE:
         case ASTORE:
            executeSingleWordStore(arg);
            break;
         case LSTORE:
         case DSTORE:
            executeDoubleWordStore(arg);
            break;
         case IASTORE:
         case BASTORE:
         case CASTORE:
         case SASTORE:
         case FASTORE:
         case AASTORE:
            pop(3);
            break;
         case LASTORE:
         case DASTORE:
            pop(4);
            break;
         case POP:
         case IRETURN:
         case FRETURN:
         case ARETURN:
         case ATHROW:
         case MONITORENTER:
         case MONITOREXIT:
            pop(1);
            break;
         case POP2:
         case LRETURN:
         case DRETURN:
            pop(2);
            break;
         case DUP:
            t1 = pop();
            push(t1);
            push(t1);
            break;
         case DUP_X1:
            t1 = pop();
            t2 = pop();
            push(t1);
            push(t2);
            push(t1);
            break;
         case DUP_X2:
            t1 = pop();
            t2 = pop();
            t3 = pop();
            push(t1);
            push(t3);
            push(t2);
            push(t1);
            break;
         case DUP2:
            t1 = pop();
            t2 = pop();
            push(t2);
            push(t1);
            push(t2);
            push(t1);
            break;
         case DUP2_X1:
            t1 = pop();
            t2 = pop();
            t3 = pop();
            push(t2);
            push(t1);
            push(t3);
            push(t2);
            push(t1);
            break;
         case DUP2_X2:
            t1 = pop();
            t2 = pop();
            t3 = pop();
            t4 = pop();
            push(t2);
            push(t1);
            push(t4);
            push(t3);
            push(t2);
            push(t1);
            break;
         case SWAP:
            t1 = pop();
            t2 = pop();
            push(t1);
            push(t2);
            break;
         case IADD:
         case ISUB:
         case IMUL:
         case IDIV:
         case IREM:
         case IAND:
         case IOR:
         case IXOR:
         case ISHL:
         case ISHR:
         case IUSHR:
         case L2I:
         case D2I:
         case FCMPL:
         case FCMPG:
            pop(2);
            push(INTEGER);
            break;
         case LADD:
         case LSUB:
         case LMUL:
         case LDIV:
         case LREM:
         case LAND:
         case LOR:
         case LXOR:
            pop(4);
            push(LONG);
            push(TOP);
            break;
         case FADD:
         case FSUB:
         case FMUL:
         case FDIV:
         case FREM:
         case L2F:
         case D2F:
            pop(2);
            push(FLOAT);
            break;
         case DADD:
         case DSUB:
         case DMUL:
         case DDIV:
         case DREM:
            pop(4);
            push(DOUBLE);
            push(TOP);
            break;
         case LSHL:
         case LSHR:
         case LUSHR:
            pop(3);
            push(LONG);
            push(TOP);
            break;
         case I2L:
         case F2L:
            pop(1);
            push(LONG);
            push(TOP);
            break;
         case I2F:
            pop(1);
            push(FLOAT);
            break;
         case I2D:
         case F2D:
            pop(1);
            push(DOUBLE);
            push(TOP);
            break;
         case F2I:
         case ARRAYLENGTH:
            pop(1);
            push(INTEGER);
            break;
         case LCMP:
         case DCMPL:
         case DCMPG:
            pop(4);
            push(INTEGER);
            break;
         case RET:
            throw new RuntimeException("RET is not supported with computeFrames option");
      }
   }

   /**
    * Simulates the action of an LCD instruction on the output stack frame.
    *
    * @param cp   the constant pool to which this label belongs.
    * @param item the operand of the instructions.
    */
   void executeLDC(ConstantPoolGeneration cp, Item item) {
      switch (item.type) {
         case ConstantPoolItemType.INT:
            push(INTEGER);
            break;
         case ConstantPoolItemType.LONG:
            push(LONG);
            push(TOP);
            break;
         case ConstantPoolItemType.FLOAT:
            push(FLOAT);
            break;
         case ConstantPoolItemType.DOUBLE:
            push(DOUBLE);
            push(TOP);
            break;
         case ConstantPoolItemType.CLASS:
            push(OBJECT | cp.addType("java/lang/Class"));
            break;
         case ConstantPoolItemType.STR:
            push(OBJECT | cp.addType("java/lang/String"));
            break;
         case ConstantPoolItemType.MTYPE:
            push(OBJECT | cp.addType("java/lang/invoke/MethodType"));
            break;
         // case ConstantPoolItemType.HANDLE_BASE + [1..9]:
         default:
            push(OBJECT | cp.addType("java/lang/invoke/MethodHandle"));
      }
   }

   /**
    * Simulates the action of a NEW, ANEWARRAY, CHECKCAST or INSTANCEOF instruction on the output stack frame.
    *
    * @param opcode the opcode of the instruction.
    * @param codeLength the operand of the instruction, if any.
    * @param cp     the constant pool to which this instruction belongs.
    * @param item   the operand of the instruction.
    */
   void executeTYPE(int opcode, int codeLength, ConstantPoolGeneration cp, Item item) {
      switch (opcode) {
         case NEW:
            push(UNINITIALIZED | cp.addUninitializedType(item.strVal1, codeLength));
            break;
         case ANEWARRAY:
            executeANewArray(cp, item);
            break;
         case CHECKCAST:
            executeCheckCast(cp, item);
            break;
         case INSTANCEOF:
            pop(1);
            push(INTEGER);
      }
   }

   /**
    * Simulates the action of a MULTIANEWARRAY instruction on the output stack frame.
    *
    * @param dims the number of dimensions of the array.
    * @param cp   the constant pool to which this instruction belongs.
    * @param arrayType the type of the array elements.
    */
   void executeMULTIANEWARRAY(int dims, ConstantPoolGeneration cp, Item arrayType) {
      pop(dims);
      push(cp, arrayType.strVal1);
   }

   /**
    * Simulates the action of the given instruction on the output stack frame.
    *
    * @param opcode the opcode of the instruction.
    * @param cp     the constant pool to which this instruction belongs.
    * @param item   the operand of the instruction.
    */
   void execute(int opcode, ConstantPoolGeneration cp, Item item) {
      switch (opcode) {
         case GETSTATIC:
            push(cp, item.strVal3);
            break;
         case PUTSTATIC:
            pop(item.strVal3);
            break;
         case GETFIELD:
            pop(1);
            push(cp, item.strVal3);
            break;
         case PUTFIELD:
            pop(item.strVal3);
            pop();
            break;
         case INVOKEVIRTUAL:
         case INVOKESPECIAL:
         case INVOKESTATIC:
         case INVOKEINTERFACE:
            executeInvoke(cp, opcode, item);
            break;
         case INVOKEDYNAMIC:
            pop(item.strVal2);
            push(cp, item.strVal2);
      }
   }

   private void executeSingleWordStore(int arg) {
      int type1 = pop();
      set(arg, type1);
      executeStore(arg);
   }

   private void executeStore(int arg) {
      if (arg > 0) {
         int type2 = get(arg - 1);

         // If type2 is of kind STACK or LOCAL we cannot know its size!
         if (type2 == LONG || type2 == DOUBLE) {
            set(arg - 1, TOP);
         }
         else if ((type2 & KIND) != BASE) {
            set(arg - 1, type2 | TOP_IF_LONG_OR_DOUBLE);
         }
      }
   }

   private void executeDoubleWordStore(int arg) {
      pop(1);

      int type1 = pop();
      set(arg, type1);
      set(arg + 1, TOP);

      executeStore(arg);
   }

   private void executeInvoke(ConstantPoolGeneration cp, int opcode, Item item) {
      pop(item.strVal3);

      if (opcode != INVOKESTATIC) {
         int var = pop();

         if (opcode == INVOKESPECIAL && item.strVal2.charAt(0) == '<') {
            init(var);
         }
      }

      push(cp, item.strVal3);
   }

   private void executeNewArray(int arg) {
      pop();

      switch (arg) {
         case ArrayElementType.BOOLEAN:
            push(ARRAY_OF | BOOLEAN);
            break;
         case ArrayElementType.CHAR:
            push(ARRAY_OF | CHAR);
            break;
         case ArrayElementType.BYTE:
            push(ARRAY_OF | BYTE);
            break;
         case ArrayElementType.SHORT:
            push(ARRAY_OF | SHORT);
            break;
         case ArrayElementType.INT:
            push(ARRAY_OF | INTEGER);
            break;
         case ArrayElementType.FLOAT:
            push(ARRAY_OF | FLOAT);
            break;
         case ArrayElementType.DOUBLE:
            push(ARRAY_OF | DOUBLE);
            break;
         // case ArrayElementType.LONG:
         default:
            push(ARRAY_OF | LONG);
            break;
      }
   }

   private void executeANewArray(ConstantPoolGeneration cp, Item item) {
      String s = item.strVal1;
      pop();

      if (s.charAt(0) == '[') {
         push(cp, '[' + s);
      }
      else {
         push(ARRAY_OF | OBJECT | cp.addType(s));
      }
   }

   private void executeCheckCast(ConstantPoolGeneration cp, Item item) {
      String s = item.strVal1;
      pop();

      if (s.charAt(0) == '[') {
         push(cp, s);
      }
      else {
         push(OBJECT | cp.addType(s));
      }
   }

   /**
    * Merges the input frame of the given basic block with the input and output frames of this basic block.
    * Returns <tt>true</tt> if the input frame of the given label has been changed by this operation.
    *
    * @param cp    the constant pool to which this label belongs.
    * @param frame the basic block whose input frame must be updated.
    * @param edge  the kind of the {@link Edge} between this label and 'label'. See {@link Edge#info}.
    * @return <tt>true</tt> if the input frame of the given label has been changed by this operation.
    */
   boolean merge(String classDesc, ConstantPoolGeneration cp, Frame frame, int edge) {
      boolean changed = false;
      int i, s, dim, kind, t;

      int nLocal = inputLocals.length;
      int nStack = inputStack.length;

      if (frame.inputLocals == null) {
         frame.inputLocals = new int[nLocal];
         changed = true;
      }

      for (i = 0; i < nLocal; ++i) {
         if (outputLocals != null && i < outputLocals.length) {
            s = outputLocals[i];

            if (s == 0) {
               t = inputLocals[i];
            }
            else {
               dim = s & DIM;
               kind = s & KIND;

               if (kind == BASE) {
                  t = s;
               }
               else {
                  if (kind == LOCAL) {
                     t = dim + inputLocals[s & VALUE];
                  }
                  else {
                     t = dim + inputStack[nStack - (s & VALUE)];
                  }

                  if ((s & TOP_IF_LONG_OR_DOUBLE) != 0 && (t == LONG || t == DOUBLE)) {
                     t = TOP;
                  }
               }
            }
         }
         else {
            t = inputLocals[i];
         }

         if (initializations != null) {
            t = init(classDesc, cp, t);
         }

         changed |= merge(cp, t, frame.inputLocals, i);
      }

      if (edge > 0) {
         for (i = 0; i < nLocal; ++i) {
            t = inputLocals[i];
            changed |= merge(cp, t, frame.inputLocals, i);
         }

         if (frame.inputStack == null) {
            frame.inputStack = new int[1];
            changed = true;
         }

         changed |= merge(cp, edge, frame.inputStack, 0);
         return changed;
      }

      int nInputStack = inputStack.length + owner.inputStackTop;

      if (frame.inputStack == null) {
         frame.inputStack = new int[nInputStack + outputStackTop];
         changed = true;
      }

      for (i = 0; i < nInputStack; ++i) {
         t = inputStack[i];

         if (initializations != null) {
            t = init(classDesc, cp, t);
         }

         changed |= merge(cp, t, frame.inputStack, i);
      }

      for (i = 0; i < outputStackTop; ++i) {
         s = outputStack[i];
         dim = s & DIM;
         kind = s & KIND;

         if (kind == BASE) {
            t = s;
         }
         else {
            if (kind == LOCAL) {
               t = dim + inputLocals[s & VALUE];
            }
            else {
               t = dim + inputStack[nStack - (s & VALUE)];
            }

            if ((s & TOP_IF_LONG_OR_DOUBLE) != 0 && (t == LONG || t == DOUBLE)) {
               t = TOP;
            }
         }

         if (initializations != null) {
            t = init(classDesc, cp, t);
         }

         changed |= merge(cp, t, frame.inputStack, nInputStack + i);
      }

      return changed;
   }

   /**
    * Merges the type at the given index in the given type array with the given type.
    * Returns <tt>true</tt> if the type array has been modified by this operation.
    *
    * @param cp    the constant pool to which this label belongs.
    * @param t     the type with which the type array element must be merged.
    * @param types an array of types.
    * @param index the index of the type that must be merged in 'types'.
    * @return <tt>true</tt> if the type array has been modified by this operation.
    */
   private static boolean merge(ConstantPoolGeneration cp, int t, int[] types, int index) {
      int u = types[index];

      if (u == t) {
         // If the types are equal, merge(u,t)=u, so there is no change.
         return false;
      }

      if ((t & ~DIM) == NULL) {
         if (u == NULL) {
            return false;
         }

         t = NULL;
      }

      if (u == 0) {
         // If types[index] has never been assigned, merge(u,t)=t.
         types[index] = t;
         return true;
      }

      int v;

      if ((u & BASE_KIND) == OBJECT || (u & DIM) != 0) {
         // If u is a reference type of any dimension.
         if (t == NULL) {
            // If t is the NULL type, merge(u,t)=u, so there is no change.
            return false;
         }
         else if ((t & (DIM | BASE_KIND)) == (u & (DIM | BASE_KIND))) {
            // If t and u have the same dimension and same base kind.
            if ((u & BASE_KIND) == OBJECT) {
               // If t is also a reference type, and if u and t have the same dimension
               // merge(u,t) = dim(t) | common parent of the element types of u and t.
               v = (t & DIM) | OBJECT | cp.getMergedType(t & BASE_VALUE, u & BASE_VALUE);
            }
            else {
               // If u and t are array types, but not with the same element type,
               // merge(u,t) = dim(u) - 1 | java/lang/Object.
               int dim = ELEMENT_OF + (u & DIM);
               v = dim | OBJECT | cp.addType("java/lang/Object");
            }
         }
         else if ((t & BASE_KIND) == OBJECT || (t & DIM) != 0) {
            // If t is any other reference or array type, the merged type is min(uDim, tDim) | java/lang/Object,
            // where uDim is the array dimension of u, minus 1 if u is an array type with a primitive element type
            // (and similarly for tDim).
            int tDim = (((t & DIM) == 0 || (t & BASE_KIND) == OBJECT) ? 0 : ELEMENT_OF) + (t & DIM);
            int uDim = (((u & DIM) == 0 || (u & BASE_KIND) == OBJECT) ? 0 : ELEMENT_OF) + (u & DIM);
            v = Math.min(tDim, uDim) | OBJECT | cp.addType("java/lang/Object");
         }
         else {
            // If t is any other type, merge(u,t)=TOP.
            v = TOP;
         }
      }
      else if (u == NULL) {
         // If u is the NULL type, merge(u,t)=t, or TOP if t is not a reference type.
         v = (t & BASE_KIND) == OBJECT || (t & DIM) != 0 ? t : TOP;
      }
      else {
         // If u is any other type, merge(u,t)=TOP whatever t.
         v = TOP;
      }

      if (u != v) {
         types[index] = v;
         return true;
      }

      return false;
   }
}
