package mockit.asm;

import javax.annotation.*;

import mockit.asm.constantPool.*;

final class SignatureWriter extends AttributeWriter
{
   @Nonnegative private final int signatureIndex;

   SignatureWriter(@Nonnull ConstantPoolGeneration cp, @Nonnull String signature) {
      super(cp, "Signature");
      signatureIndex = cp.newUTF8(signature);
   }

   @Nonnegative @Override
   protected int getSize() { return 8; }

   @Override
   protected void put(@Nonnull ByteVector out) {
      super.put(out);
      out.putShort(signatureIndex);
   }
}
