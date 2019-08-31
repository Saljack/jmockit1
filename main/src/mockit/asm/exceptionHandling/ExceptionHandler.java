package mockit.asm.exceptionHandling;

import javax.annotation.*;

import mockit.asm.controlFlow.*;
import mockit.asm.util.*;

/**
 * Information about an exception handler block.
 */
final class ExceptionHandler
{
   /**
    * Beginning of the exception handler's scope (inclusive).
    */
   @Nonnull Label start;

   /**
    * End of the exception handler's scope (exclusive).
    */
   @Nonnull Label end;

   /**
    * Beginning of the exception handler's code.
    */
   @Nonnull final Label handler;

   /**
    * Internal name of the type of exceptions handled by this handler, or <tt>null</tt> to catch any exceptions.
    */
   @Nullable private final String desc;

   /**
    * Constant pool index of the internal name of the type of exceptions handled by this handler, or <tt>0</tt> to catch
    * any exceptions.
    */
   @Nonnegative private final int type;

   /**
    * Next exception handler block info.
    */
   @Nullable ExceptionHandler next;

   ExceptionHandler(@Nonnull Label start, @Nonnull Label end, @Nonnull Label handler, @Nullable String desc, @Nonnegative int type) {
      this.start = start;
      this.end = end;
      this.handler = handler;
      this.desc = desc;
      this.type = type;
   }

   @Nonnull
   String getCatchTypeDesc() { return desc == null ? "java/lang/Throwable" : desc; }

   void put(@Nonnull ByteVector out) {
      out.putShort(start.position).putShort(end.position);
      out.putShort(handler.position).putShort(type);
   }
}