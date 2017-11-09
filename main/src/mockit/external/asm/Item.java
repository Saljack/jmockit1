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

import static mockit.external.asm.ClassWriter.*;

/**
 * A constant pool item. Constant pool items can be created with the 'newXXX' methods in the {@link ClassWriter} class.
 *
 * @author Eric Bruneton
 */
final class Item
{
   /**
    * Index of this item in the constant pool.
    */
   final int index;

   /**
    * Type of this constant pool item. A single class is used to represent all constant pool item types, in order to
    * minimize the bytecode size of this package. The value of this field is one of {@link ConstantPoolItemType#INT},
    * {@link ConstantPoolItemType#LONG}, {@link ConstantPoolItemType#FLOAT}, {@link ConstantPoolItemType#DOUBLE}, {@link ConstantPoolItemType#UTF8},
    * {@link ConstantPoolItemType#STR}, {@link ConstantPoolItemType#CLASS}, {@link ConstantPoolItemType#NAME_TYPE}, {@link ConstantPoolItemType#FIELD},
    * {@link ConstantPoolItemType#METH}, {@link ConstantPoolItemType#IMETH}, {@link ConstantPoolItemType#MTYPE}, {@link ConstantPoolItemType#INDY}.
    * <p/>
    * MethodHandle constant 9 variations are stored using a range of 9 values from {@link ClassWriter#HANDLE_BASE} + 1
    * to {@link ClassWriter#HANDLE_BASE} + 9.
    * <p/>
    * Special Item types are used for Items that are stored in the ClassWriter {@link ClassWriter#typeTable}, instead of
    * the constant pool, in order to avoid clashes with normal constant pool items in the ClassWriter constant pool's
    * hash table. These special item types are {@link ClassWriter#TYPE_NORMAL}, {@link ClassWriter#TYPE_UNINIT} and
    * {@link ClassWriter#TYPE_MERGED}.
    */
   int type;

   /**
    * Value of this item, for an integer item.
    */
   int intVal;

   /**
    * Value of this item, for a long item.
    */
   long longVal;

   /**
    * First part of the value of this item, for items that do not hold a
    * primitive value.
    */
   String strVal1;

   /**
    * Second part of the value of this item, for items that do not hold a
    * primitive value.
    */
   String strVal2;

   /**
    * Third part of the value of this item, for items that do not hold a
    * primitive value.
    */
   String strVal3;

   /**
    * The hash code value of this constant pool item.
    */
   int hashCode;

   /**
    * Link to another constant pool item, used for collision lists in the
    * constant pool's hash table.
    */
   Item next;

   /**
    * Constructs an uninitialized {@link Item}.
    */
   Item() { index = 0; }

   /**
    * Constructs an uninitialized {@link Item} for constant pool element at given position.
    *
    * @param index index of the item to be constructed.
    */
   Item(int index) {
      this.index = index;
   }

   /**
    * Constructs a copy of the given item.
    *
    * @param index index of the item to be constructed.
    * @param i     the item that must be copied into the item to be constructed.
    */
   Item(int index, Item i) {
      this.index = index;
      type = i.type;
      intVal = i.intVal;
      longVal = i.longVal;
      strVal1 = i.strVal1;
      strVal2 = i.strVal2;
      strVal3 = i.strVal3;
      hashCode = i.hashCode;
   }

   /**
    * Sets this item to an integer item.
    *
    * @param intVal the value of this item.
    */
   void set(int intVal) {
      type = ConstantPoolItemType.INT;
      this.intVal = intVal;
      hashCode = 0x7FFFFFFF & (type + intVal);
   }

   /**
    * Sets this item to a long item.
    *
    * @param longVal the value of this item.
    */
   void set(long longVal) {
      type = ConstantPoolItemType.LONG;
      this.longVal = longVal;
      hashCode = 0x7FFFFFFF & (type + (int) longVal);
   }

   /**
    * Sets this item to a float item.
    *
    * @param floatVal the value of this item.
    */
   void set(float floatVal) {
      type = ConstantPoolItemType.FLOAT;
      intVal = Float.floatToRawIntBits(floatVal);
      hashCode = 0x7FFFFFFF & (type + (int) floatVal);
   }

   /**
    * Sets this item to a double item.
    *
    * @param doubleVal the value of this item.
    */
   void set(double doubleVal) {
      type = ConstantPoolItemType.DOUBLE;
      longVal = Double.doubleToRawLongBits(doubleVal);
      hashCode = 0x7FFFFFFF & (type + (int) doubleVal);
   }

   /**
    * Sets this item to an item that do not hold a primitive value.
    *
    * @param type    the type of this item.
    * @param strVal1 first part of the value of this item.
    * @param strVal2 second part of the value of this item.
    * @param strVal3 third part of the value of this item.
    */
   void set(int type, String strVal1, String strVal2, String strVal3) {
      this.type = type;
      this.strVal1 = strVal1;
      this.strVal2 = strVal2;
      this.strVal3 = strVal3;

      switch (type) {
         case ConstantPoolItemType.CLASS:
            intVal = 0;     // intVal of a class must be zero, see visitInnerClass
            // fall through
         case ConstantPoolItemType.UTF8:
         case ConstantPoolItemType.STR:
         case ConstantPoolItemType.MTYPE:
         case TYPE_NORMAL:
            hashCode = 0x7FFFFFFF & (type + strVal1.hashCode());
            return;
         case ConstantPoolItemType.NAME_TYPE: {
            hashCode = 0x7FFFFFFF & (type + strVal1.hashCode() * strVal2.hashCode());
            return;
         }
         // ClassWriter.FIELD:
         // ClassWriter.METH:
         // ClassWriter.IMETH:
         // ClassWriter.HANDLE_BASE + 1..9
         default:
            hashCode = 0x7FFFFFFF & (type + strVal1.hashCode() * strVal2.hashCode() * strVal3.hashCode());
      }
   }

   /**
    * Sets the item to an InvokeDynamic item.
    *
    * @param name     invokedynamic's name.
    * @param desc     invokedynamic's desc.
    * @param bsmIndex zero based index into the class attribute BootstrapMethods.
    */
   void set(String name, String desc, int bsmIndex) {
      type = ConstantPoolItemType.INDY;
      longVal = bsmIndex;
      strVal1 = name;
      strVal2 = desc;
      hashCode = 0x7FFFFFFF & (ConstantPoolItemType.INDY + bsmIndex * strVal1.hashCode() * strVal2.hashCode());
   }

   /**
    * Sets the item to a BootstrapMethod item.
    *
    * @param position position in byte in the class attribute BootstrapMethods.
    * @param hashCode hashcode of the item. This hashcode is processed from the
    *                 hashcode of the bootstrap method and the hashcode of all bootstrap arguments.
    */
   void set(int position, int hashCode) {
      type = BSM;
      intVal = position;
      this.hashCode = hashCode;
   }

   /**
    * Indicates if the given item is equal to this one. <i>This method assumes that the two items have the same
    * {@link #type}</i>.
    *
    * @param i the item to be compared to this one. Both items must have the same {@link #type}.
    * @return <tt>true</tt> if the given item if equal to this one, <tt>false</tt> otherwise.
    */
   boolean isEqualTo(Item i) {
      switch (type) {
         case ConstantPoolItemType.UTF8:
         case ConstantPoolItemType.STR:
         case ConstantPoolItemType.CLASS:
         case ConstantPoolItemType.MTYPE:
         case TYPE_NORMAL:
            return i.strVal1.equals(strVal1);
         case TYPE_MERGED:
         case ConstantPoolItemType.LONG:
         case ConstantPoolItemType.DOUBLE:
            return i.longVal == longVal;
         case ConstantPoolItemType.INT:
         case ConstantPoolItemType.FLOAT:
            return i.intVal == intVal;
         case TYPE_UNINIT:
            return i.intVal == intVal && i.strVal1.equals(strVal1);
         case ConstantPoolItemType.NAME_TYPE:
            return i.strVal1.equals(strVal1) && i.strVal2.equals(strVal2);
         case ConstantPoolItemType.INDY: {
            return i.longVal == longVal && i.strVal1.equals(strVal1) && i.strVal2.equals(strVal2);
         }
         // case ClassWriter.FIELD:
         // case ClassWriter.METH:
         // case ClassWriter.IMETH:
         // case ClassWriter.HANDLE_BASE + 1..9
         default:
            return i.strVal1.equals(strVal1) && i.strVal2.equals(strVal2) && i.strVal3.equals(strVal3);
      }
   }
}
